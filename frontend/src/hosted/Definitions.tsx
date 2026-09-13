import { useEffect, useRef, useState } from "react";
import {
  ApiFailure,
  type Definition,
  type DefinitionList,
  type Diagnostic,
  failureMessage,
  type HostedApi,
} from "../api/hosted";

type WorkspaceMutation = {
  method: "PUT" | "POST";
  path: string;
  body: Readonly<Record<string, unknown>>;
};
export function Definitions({
  api,
  enabled,
  changed,
  onLocked,
}: {
  api: HostedApi;
  enabled: boolean;
  changed: () => void;
  onLocked?: (locked: boolean) => void;
}) {
  const [list, setList] = useState<DefinitionList>({ definitions: [], canPublish: false });
  const [selected, setSelected] = useState<Definition | null>(null);
  const [source, setSource] = useState("");
  const [format, setFormat] = useState("JSON");
  const [tab, setTab] = useState("Model");
  const [diagnostics, setDiagnostics] = useState<Diagnostic[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<WorkspaceMutation | null>(null);
  const mutating = useRef(false);
  const [policies, setPolicies] = useState<Record<string, string>>({});
  const [consent, setConsent] = useState(false);
  const epoch = useRef(0);
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);
  useEffect(() => {
    onLocked?.(busy || pending !== null);
  }, [busy, pending, onLocked]);
  useEffect(() => {
    let active = true;
    if (enabled)
      api
        .get<DefinitionList>("/api/v2/definitions")
        .then((result) => {
          if (active) setList(result);
        })
        .catch((e) => {
          if (active) setError(failureMessage(e));
        });
    return () => {
      active = false;
      epoch.current++;
    };
  }, [api, enabled]);
  async function load(id: string) {
    if (busy || pending) return;
    const token = ++epoch.current;
    setBusy(true);
    setError("");
    try {
      const result = await api.get<Definition>(`/api/v2/definitions/${id}`);
      if (token !== epoch.current) return;
      setSelected(result);
      setSource(result.source);
      setFormat(result.format);
      setDiagnostics(result.projection.diagnostics);
      setPolicies({});
      setConsent(false);
    } catch (e) {
      if (token === epoch.current) setError(failureMessage(e));
    } finally {
      if (token === epoch.current) setBusy(false);
    }
  }
  async function execute(command: WorkspaceMutation) {
    if (mutating.current) return;
    mutating.current = true;
    const token = ++epoch.current;
    setBusy(true);
    setError("");
    try {
      const result =
        command.method === "PUT"
          ? await api.put<Definition>(command.path, command.body)
          : await api.post<Definition>(command.path, command.body);
      if (token !== epoch.current) return;
      if (
        !result ||
        typeof result.source !== "string" ||
        typeof result.workspaceRevision !== "string" ||
        !result.projection ||
        !Array.isArray(result.projection.diagnostics) ||
        !Array.isArray(result.projection.model?.bindings)
      )
        throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
      setSelected(result);
      setSource(result.source);
      setFormat(result.format);
      setDiagnostics(result.projection.diagnostics);
      setPolicies({});
      setConsent(false);
      setPending(null);
      changed();
      // The command is acknowledged even if refreshing the separate list fails.
      try {
        const refreshed = await api.get<DefinitionList>("/api/v2/definitions");
        if (token === epoch.current) setList(refreshed);
      } catch (e) {
        if (token === epoch.current) setError(failureMessage(e));
      }
    } catch (e) {
      if (token === epoch.current) {
        setError(failureMessage(e));
        setDiagnostics(e instanceof ApiFailure ? e.diagnostics : []);
        const refused =
          e instanceof ApiFailure &&
          e.status >= 400 &&
          e.status < 500 &&
          // The post-service lease check can fail after an admitted commit.
          !(e.status === 403 && e.code === "WORKSPACE_FORBIDDEN") &&
          e.code !== "RESPONSE_UNAVAILABLE";
        setPending(refused ? null : command);
      }
    } finally {
      mutating.current = false;
      if (token === epoch.current) setBusy(false);
    }
  }
  function save() {
    if (busy || pending) return;
    return execute({
      method: "PUT",
      path: `/api/v2/definitions/${selected?.objectId ?? crypto.randomUUID()}`,
      body: {
        expectedRevision: selected?.workspaceRevision ?? "0",
        requestId: crypto.randomUUID(),
        format,
        source,
      },
    });
  }
  const documents =
    selected?.projection.model.bindings.flatMap((binding) =>
      binding.documents.map((document) => ({
        bindingId: binding.id,
        documentId: document.id,
        key: `${binding.id}/${document.id}`,
      })),
    ) ?? [];
  function publish() {
    if (!selected || busy || pending) return;
    return execute({
      method: "POST",
      path: `/api/v2/definitions/${selected.objectId}/publish`,
      body: {
        expectedRevision: selected.workspaceRevision,
        requestId: crypto.randomUUID(),
        exportPolicies: documents.map((document) => ({
          bindingId: document.bindingId,
          documentId: document.documentId,
          content: policies[document.key],
        })),
      },
    });
  }
  return (
    <section className="hosted-panel" aria-labelledby="definitions-heading">
      <h1 id="definitions-heading">Definitions</h1>
      <p>Maintainer settings · immutable native v2 drafts and explicit publication.</p>
      {!enabled ? (
        <p>Definition workspace unavailable: private storage is not configured.</p>
      ) : (
        <>
          <label>
            Saved definition
            <select
              disabled={busy || pending !== null}
              value={selected?.objectId ?? ""}
              onChange={(event) => {
                if (event.target.value) void load(event.target.value);
              }}
            >
              <option value="">Choose an owned definition</option>
              {list.definitions.map((item) => (
                <option key={item.objectId} value={item.objectId}>
                  {item.nativeId} · workspace {item.workspaceRevision} · {item.state}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            disabled={busy || pending !== null}
            onClick={() => {
              epoch.current++;
              setSelected(null);
              setSource("");
              setDiagnostics([]);
              setPolicies({});
              setError("");
              setBusy(false);
            }}
          >
            New draft
          </button>
          <label>
            Upload native definition
            <input
              type="file"
              accept=".json,.yaml,.yml"
              disabled={busy || pending !== null}
              onChange={async (event) => {
                const file = event.target.files?.[0];
                if (!file) return;
                const token = ++epoch.current;
                if (file.size > 1_048_576) {
                  setError("Source exceeds 1 MiB.");
                  return;
                }
                const text = await file.text();
                if (token !== epoch.current) return;
                setSource(text);
                setFormat(file.name.endsWith(".json") ? "JSON" : "YAML");
              }}
            />
          </label>
          <label>
            Source format
            <select
              disabled={busy || pending !== null}
              value={format}
              onChange={(event) => setFormat(event.target.value)}
            >
              <option value="JSON">JSON</option>
              <option value="YAML">YAML</option>
            </select>
          </label>
          <label>
            Native definition source
            <textarea
              disabled={busy || pending !== null}
              value={source}
              onChange={(event) => setSource(event.target.value)}
              rows={8}
              spellCheck={false}
            />
          </label>
          <button type="button" disabled={busy || pending !== null || !source} onClick={save}>
            Save immutable draft
          </button>
          {busy && <p role="status">Waiting for the authoritative workspace result…</p>}
          {pending && (
            <section aria-label="Unconfirmed workspace command">
              <p>
                The change may have completed. Retry to confirm its original result before making
                another change.
              </p>
              <button type="button" disabled={busy} onClick={() => void execute(pending)}>
                Retry original command
              </button>
            </section>
          )}
          {selected && (
            <p>
              Object <code>{selected.objectId}</code> · workspace revision{" "}
              {selected.workspaceRevision} · {selected.state} · {selected.projection.kind}
            </p>
          )}
          <h2>{diagnostics.length} diagnostics · complete result</h2>
          <ul>
            {diagnostics.map((d) => (
              <li key={JSON.stringify(d)}>
                {d.code} · {d.pointer || "Root"} · {d.message}
              </li>
            ))}
          </ul>
          <div role="tablist" aria-label="Saved definition view" className="segmented">
            {["Model", "Source", "Diagnostics"].map((name, i) => (
              <button
                type="button"
                role="tab"
                key={name}
                id={`saved-${name}`}
                aria-controls="saved-definition-panel"
                aria-selected={tab === name}
                tabIndex={tab === name ? 0 : -1}
                ref={(node) => {
                  tabs.current[i] = node;
                }}
                onClick={() => setTab(name)}
                onKeyDown={(event) => {
                  const index =
                    event.key === "ArrowRight"
                      ? (i + 1) % 3
                      : event.key === "ArrowLeft"
                        ? (i + 2) % 3
                        : event.key === "Home"
                          ? 0
                          : event.key === "End"
                            ? 2
                            : -1;
                  if (index >= 0) {
                    event.preventDefault();
                    setTab(["Model", "Source", "Diagnostics"][index]);
                    tabs.current[index]?.focus();
                  }
                }}
              >
                {name}
              </button>
            ))}
          </div>
          <div role="tabpanel" id="saved-definition-panel" aria-labelledby={`saved-${tab}`}>
            {!selected ? (
              <p>No saved model available. Rejected uploads do not create a revision.</p>
            ) : tab === "Source" ? (
              <pre>{selected.source}</pre>
            ) : tab === "Diagnostics" ? (
              <p>All {diagnostics.length} diagnostics remain visible above.</p>
            ) : (
              <>
                <h3>{selected.projection.model.id}</h3>
                <ul>
                  {selected.projection.model.logical.entityTypes.map((type) => (
                    <li key={type.id}>
                      {type.label} ({type.id})
                      <ul>
                        {type.fields.map((field) => (
                          <li key={field.id}>
                            {field.id} · {field.valueType}
                          </li>
                        ))}
                      </ul>
                    </li>
                  ))}
                </ul>
                <h3>Bindings</h3>
                <ul>
                  {selected.projection.model.bindings.map((binding) => (
                    <li key={binding.id}>
                      {binding.id} · {binding.engine} · {binding.documents.length} documents
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
          {selected?.state === "draft" && (
            <section aria-label="Definition publication">
              <h2>Publish immutable definition</h2>
              <p>
                {list.canPublish
                  ? "Maintainer authorization is available for this session."
                  : "Publication requires maintainer authorization. Draft editing remains available."}
              </p>
              <p>
                Protected self-contained publication permits complete original and target documents
                in a protected artifact, including unchanged or unmapped content and known secrets.
                Display masking does not change package bytes. Deny blocks export for that document.
              </p>
              {documents.map((document) => (
                <label key={document.key}>
                  {document.bindingId} / {document.documentId} export policy
                  <select
                    disabled={busy || pending !== null}
                    value={policies[document.key] ?? ""}
                    onChange={(event) =>
                      setPolicies({ ...policies, [document.key]: event.target.value })
                    }
                  >
                    <option value="">Choose explicitly</option>
                    <option value="deny">Deny</option>
                    <option value="protected-self-contained">Protected self-contained</option>
                  </select>
                </label>
              ))}
              <label>
                <input
                  type="checkbox"
                  disabled={busy || pending !== null}
                  checked={consent}
                  onChange={(event) => setConsent(event.target.checked)}
                />
                I reviewed complete-document disclosure and every document policy.
              </label>
              <button
                type="button"
                disabled={
                  busy ||
                  pending !== null ||
                  !list.canPublish ||
                  selected.projection.kind !== "ready-to-publish" ||
                  !consent ||
                  documents.some((d) => !policies[d.key]) ||
                  source !== selected.source
                }
                onClick={publish}
              >
                Publish definition
              </button>
            </section>
          )}
        </>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
