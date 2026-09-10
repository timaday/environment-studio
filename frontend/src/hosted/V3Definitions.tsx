import { type ReactNode, useRef, useState } from "react";
import type { Diagnostic } from "../api/hosted";
import type { useV3Definitions } from "./useV3Definitions";

function DefinitionIcon({ kind }: { kind: "info" | "upload" | "file" }) {
  return (
    <svg
      aria-hidden="true"
      className={`definition-icon definition-icon-${kind}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
    >
      {kind === "info" ? (
        <>
          <circle cx="12" cy="12" r="10" />
          <path d="M12 10v7m0-11v1" />
        </>
      ) : kind === "upload" ? (
        <path d="M3 15v6h18v-6M12 16V3m-5 5 5-5 5 5" />
      ) : (
        <path d="M5 2h10l5 5v15H5zM15 2v6h5M8 12h9m-9 4h9" />
      )}
    </svg>
  );
}
function Diagnostics({ entries }: { entries: readonly Diagnostic[] }) {
  const occurrences = new Map<string, number>();
  return entries.length ? (
    <ul>
      {entries.map((d) => {
        const content = JSON.stringify(d);
        const occurrence = occurrences.get(content) ?? 0;
        occurrences.set(content, occurrence + 1);
        return (
          <li key={`${content}:${occurrence}`}>
            <strong>{d.code}</strong> · {d.pointer || "Root"} · {d.message}
          </li>
        );
      })}
    </ul>
  ) : (
    <p>No diagnostics in this saved revision.</p>
  );
}
export function V3Definitions({
  state,
  enabled,
  versionSelector,
}: {
  state: ReturnType<typeof useV3Definitions>;
  enabled: boolean;
  versionSelector: ReactNode;
}) {
  const [tab, setTab] = useState("Model");
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);
  const fileInput = useRef<HTMLInputElement | null>(null);
  const locked = !enabled || state.busy || state.pending;
  const selected = state.selected;
  const replace = () =>
    !state.dirty ||
    window.confirm("Replace the unsaved definition source? Your existing edits will be discarded.");
  const inventory = state.inventory;
  return (
    <section className="hosted-panel v3-definitions" aria-label="Native v3 definitions">
      <h1>Definitions</h1>
      <p>Save application definitions and inspect their declared model.</p>
      <div className="definition-selection">
        {versionSelector}
        <label>
          Saved definition
          <select
            value={selected?.objectId ?? ""}
            disabled={locked || !inventory?.definitions.length}
            onChange={(event) => {
              if (event.target.value && replace()) void state.load(event.target.value);
            }}
          >
            <option value="">
              {inventory === null
                ? "Definitions not loaded"
                : inventory.definitions.length
                  ? "Choose a saved definition"
                  : "No saved definitions"}
            </option>
            {inventory?.definitions.map((row) => (
              <option key={row.objectId} value={row.objectId}>
                {row.nativeId} · revision {row.workspaceRevision}
              </option>
            ))}
          </select>
        </label>
        <button
          className="definition-primary"
          type="button"
          disabled={locked}
          onClick={() => {
            if (replace()) state.newDefinition();
          }}
        >
          New definition
        </button>
      </div>
      {state.busy && (
        <p role="status" className="definition-notice">
          Waiting for the workspace result…
        </p>
      )}
      {!enabled && <p role="status">Definition workspace is unavailable.</p>}
      {!state.busy && inventory?.definitions.length === 0 && (
        <p className="definition-notice">
          <DefinitionIcon kind="info" />
          No saved definitions for this model version.
        </p>
      )}
      {state.error && (
        <div role="alert">
          <p>{state.error}</p>
          <p>
            The saved definition list may be out of date. Saved revision and editor are shown
            separately.
          </p>
          {state.diagnostics.length > 0 && <Diagnostics entries={state.diagnostics} />}
          {!state.pending && (
            <button type="button" disabled={locked} onClick={() => void state.refreshList()}>
              Refresh definitions
            </button>
          )}
        </div>
      )}
      {state.pending && (
        <section className="definition-notice" aria-label="Unconfirmed definition save">
          <p>
            The save may have completed. Retry to confirm its original result before making another
            change.
          </p>
          <button type="button" disabled={state.busy} onClick={() => void state.retry()}>
            Retry original save
          </button>
        </section>
      )}
      <section className="definition-source" aria-labelledby="v3-source-heading">
        <h2 id="v3-source-heading">Definition source</h2>
        <div className="definition-upload">
          <div>
            <button
              className="definition-upload-button"
              type="button"
              disabled={locked}
              onClick={() => fileInput.current?.click()}
            >
              <DefinitionIcon kind="upload" /> Upload definition
            </button>
            <input
              ref={fileInput}
              hidden
              style={{ display: "none" }}
              type="file"
              accept=".json,.yaml,.yml"
              tabIndex={-1}
              aria-label="Upload definition"
              disabled={locked}
              onChange={(event) => {
                const file = event.currentTarget.files?.[0];
                event.currentTarget.value = "";
                if (file && replace()) state.upload(file);
              }}
            />
            <p>JSON or YAML · up to 1 MiB</p>
          </div>
          <p>Upload a file or paste your source below.</p>
        </div>
        {state.fileError && <p role="alert">{state.fileError}</p>}
        {state.reading && <p role="status">Reading definition file…</p>}
        <div className="definition-editor">
          <label>
            Format
            <select
              value={state.format}
              disabled={locked}
              onChange={(e) => state.setFormat(e.target.value as "JSON" | "YAML")}
            >
              <option value="JSON">JSON</option>
              <option value="YAML">YAML</option>
            </select>
          </label>
          <label>
            Source
            <textarea
              value={state.source}
              disabled={locked}
              spellCheck={false}
              rows={6}
              onChange={(e) => state.setSource(e.target.value)}
            />
          </label>
        </div>
        <div className="definition-save">
          <button
            className="definition-primary"
            type="button"
            disabled={locked || state.reading || !state.source}
            onClick={() => void state.save()}
          >
            Save draft
          </button>
          <p>
            <DefinitionIcon kind="info" />
            {!state.source
              ? "Enter a definition to save a draft."
              : state.dirty
                ? "Unsaved source. Save a draft to compile and inspect it."
                : "Editor matches the saved revision."}
          </p>
        </div>
      </section>
      <section className="definition-saved" aria-label="Saved definition inspection">
        <div role="tablist" aria-label="Saved v3 definition view" className="definition-view-tabs">
          {["Model", "Source", "Diagnostics"].map((name, i, names) => (
            <button
              type="button"
              role="tab"
              key={name}
              id={`v3-saved-${name}`}
              aria-controls="v3-definition-panel"
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
                  setTab(names[index]);
                  tabs.current[index]?.focus();
                }
              }}
            >
              {name}
            </button>
          ))}
        </div>
        <div
          id="v3-definition-panel"
          role="tabpanel"
          aria-labelledby={`v3-saved-${tab}`}
          // biome-ignore lint/a11y/noNoninteractiveTabindex: Static tab panel needs keyboard focus for reading and scrolling, as in DefinitionInspector.
          tabIndex={0}
        >
          {!selected ? (
            <div className="definition-empty">
              <DefinitionIcon kind="file" />
              <p>Save a draft to inspect its model and diagnostics.</p>
            </div>
          ) : (
            <>
              <p>
                Saved revision {selected.workspaceRevision} · {selected.state} ·{" "}
                {selected.projection.kind}
              </p>
              {tab === "Source" ? (
                <pre>{selected.source}</pre>
              ) : tab === "Diagnostics" ? (
                <Diagnostics entries={selected.projection.diagnostics} />
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
                              {field.id} · {field.valueType} · {field.sensitivity}
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
                  <details>
                    <summary>Complete declared model</summary>
                    <pre>{JSON.stringify(selected.projection.model, null, 2)}</pre>
                  </details>
                </>
              )}
            </>
          )}
        </div>
        <p className="definition-publication">
          <DefinitionIcon kind="info" />
          Saving a draft does not make it available for plan use. Publication requires qualified
          validation.
        </p>
      </section>
    </section>
  );
}
