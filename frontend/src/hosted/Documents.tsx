import { useCallback, useEffect, useRef, useState } from "react";
import {
  type Documents,
  type DocumentView,
  failureMessage,
  type HostedApi,
  type Plan,
} from "../api/hosted";
export function DocumentComparison({ api, plan }: { api: HostedApi; plan: Plan }) {
  const [inventory, setInventory] = useState<Documents | null>(null);
  const [selected, setSelected] = useState("");
  const [filter, setFilter] = useState("");
  const [mode, setMode] = useState<"raw" | "formatted">("raw");
  const [consent, setConsent] = useState(false);
  const [current, setCurrent] = useState<DocumentView | null>(null);
  const [target, setTarget] = useState<DocumentView | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const generation = useRef(0);
  const readInventory = useCallback(() => {
    const token = ++generation.current;
    setInventory(null);
    setCurrent(null);
    setTarget(null);
    setBusy(true);
    setSelected("");
    setFilter("");
    setConsent(false);
    setError("");
    api
      .post<Documents>(`/api/v1/plans/${plan.planId}/views/documents`, { revision: plan.revision })
      .then((result) => {
        if (token !== generation.current || result.revision !== plan.revision) return;
        setInventory(result);
        setSelected(result.documents[0]?.documentId ?? "");
      })
      .catch((e) => {
        if (token === generation.current) setError(failureMessage(e));
      })
      .finally(() => {
        if (token === generation.current) setBusy(false);
      });
  }, [api, plan.planId, plan.revision]);
  useEffect(() => {
    readInventory();
    return () => {
      generation.current++;
    };
  }, [readInventory]);
  function clear() {
    generation.current++;
    setCurrent(null);
    setTarget(null);
    setError("");
    setBusy(false);
  }
  async function load() {
    if (!consent || !selected || busy || !shown.some((item) => item.documentId === selected))
      return;
    const token = ++generation.current;
    setBusy(true);
    setError("");
    setCurrent(null);
    setTarget(null);
    const view = (side: "current" | "target") =>
      api.post<DocumentView>(`/api/v1/plans/${plan.planId}/views/document`, {
        revision: plan.revision,
        side,
        documentId: selected,
        mode,
        completeDocumentDisclosure: true,
      });
    try {
      const original = await view("current");
      if (token !== generation.current || original.revision !== plan.revision) return;
      setCurrent(original);
      if (plan.targetComplete) {
        const proposed = await view("target");
        if (token !== generation.current || proposed.revision !== plan.revision) return;
        setTarget(proposed);
      }
    } catch (e) {
      if (token === generation.current) setError(failureMessage(e));
    } finally {
      if (token === generation.current) setBusy(false);
    }
  }
  const documents = inventory?.documents ?? [];
  const shown = documents.filter((document) =>
    document.documentId.toLowerCase().includes(filter.toLowerCase()),
  );
  return (
    <section className="hosted-panel" aria-labelledby="documents-heading">
      <h2 id="documents-heading">Configuration documents</h2>
      <p>
        {documents.length} documents · {documents.filter((d) => d.changed === true).length} changed
        · {documents.filter((d) => d.changed === null).length} unknown · {shown.length} shown
      </p>
      {!inventory && !error && <p role="status">Loading complete document inventory…</p>}
      <button type="button" disabled={busy} onClick={readInventory}>
        Reload document inventory
      </button>
      <label>
        Filter documents
        <input
          value={filter}
          onChange={(event) => {
            const next = event.target.value;
            const matches = documents.filter((item) =>
              item.documentId.toLowerCase().includes(next.toLowerCase()),
            );
            clear();
            setFilter(next);
            setSelected(
              matches.some((item) => item.documentId === selected)
                ? selected
                : (matches[0]?.documentId ?? ""),
            );
            setConsent(false);
          }}
        />
      </label>
      <label>
        Selected document
        <select
          value={selected}
          onChange={(event) => {
            clear();
            setSelected(event.target.value);
            setConsent(false);
          }}
        >
          {shown.map((document) => (
            <option key={document.documentId} value={document.documentId}>
              {document.documentId} ·{" "}
              {document.changed === null ? "Unknown" : document.changed ? "Changed" : "Unchanged"}
            </option>
          ))}
        </select>
      </label>
      <fieldset className="segmented">
        <legend>XML display mode</legend>
        <button
          type="button"
          aria-pressed={mode === "raw"}
          onClick={() => {
            clear();
            setMode("raw");
          }}
        >
          Raw
        </button>
        <button type="button" disabled aria-describedby="binding-rail-unavailable">
          Placeholders
        </button>
        <button
          type="button"
          aria-pressed={mode === "formatted"}
          onClick={() => {
            clear();
            setMode("formatted");
          }}
        >
          Formatted
        </button>
      </fieldset>
      <p id="binding-rail-unavailable">
        Placeholder comparison is unavailable until the concrete binding rail and mapped locations
        are provided. Raw and formatted views remain read-only.
      </p>
      <label>
        <input
          type="checkbox"
          checked={consent}
          onChange={(event) => {
            clear();
            setConsent(event.target.checked);
          }}
        />
        Show the complete selected document, including unchanged or unmapped content and readable
        secrets.
      </label>
      <button type="button" disabled={!selected || !consent || busy} onClick={load}>
        Load document comparison
      </button>
      {busy && <p role="status">Loading revision {plan.revision}…</p>}
      {error && <p role="alert">{error}</p>}
      <div className="xml-panes">
        {(
          [
            ["Current", current],
            ["Target", target],
          ] as const
        ).map(([label, value]) => (
          <section key={label} aria-label={`${label} XML`}>
            <h3>{label}</h3>
            <p>
              {selected || "No document selected"} · plan revision {plan.revision}
            </p>
            {value ? (
              <>
                <p>
                  {value.exact ? "Exact characters" : "Display projection only"} ·{" "}
                  {value.redacted ? "Redacted" : "Concrete document"} · {value.omissions.length}{" "}
                  omissions
                </p>
                {/* biome-ignore lint/a11y/noNoninteractiveTabindex: Keyboard users must scroll the read-only XML pane. */}
                <pre tabIndex={0}>{value.text}</pre>
              </>
            ) : (
              <p>
                {label === "Target" && !plan.targetComplete
                  ? "Target unavailable · not evidence of unchanged content."
                  : "Complete-document consent and load required."}
              </p>
            )}
          </section>
        ))}
      </div>
    </section>
  );
}
