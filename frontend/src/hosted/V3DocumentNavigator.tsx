import { useState } from "react";
import type { useV3PlanInspection } from "./useV3PlanInspection";

type Documents = NonNullable<ReturnType<typeof useV3PlanInspection>["inventory"]>["documents"];
type Status = "all" | "changed" | "unchanged" | "unknown";
const statusLabel = (changed: boolean | null) =>
  changed === null ? "Unknown" : changed ? "Changed" : "Unchanged";

export function V3DocumentNavigator({
  documents,
  selected,
  select,
}: {
  documents: Documents | undefined;
  selected: string;
  select: (document: string) => void;
}) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<Status>("all");
  const shown = (documents ?? []).filter(
    (document) =>
      document.documentId.toLowerCase().includes(query.toLowerCase().trim()) &&
      (status === "all" || statusLabel(document.changed).toLowerCase() === status),
  );
  const changed = shown.filter((document) => document.changed === true);
  function navigate(direction: -1 | 1) {
    if (!changed.length) return;
    const index = shown.findIndex((document) => document.documentId === selected);
    const next =
      direction === 1
        ? (shown.slice(index + 1).find((document) => document.changed === true) ?? changed[0])
        : (shown
            .slice(0, index < 0 ? shown.length : index)
            .reverse()
            .find((document) => document.changed === true) ?? changed[changed.length - 1]);
    if (next) select(next.documentId);
  }
  return (
    <aside className="v3-document-navigator" aria-label="XML documents">
      <h3>XML documents</h3>
      <p>Complete returned inventory</p>
      <label>
        Find a document
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search document identity…"
        />
      </label>
      <label>
        Document status
        <select value={status} onChange={(event) => setStatus(event.target.value as Status)}>
          <option value="all">All documents</option>
          <option value="changed">Changed</option>
          <option value="unchanged">Unchanged</option>
          <option value="unknown">Unknown</option>
        </select>
      </label>
      {documents ? (
        <p role="status">
          {shown.length} of {documents.length} documents shown
        </p>
      ) : (
        <p>Inventory unavailable until observation.</p>
      )}
      {selected && !shown.some((document) => document.documentId === selected) && (
        <p>The selected document is outside this filter. Its comparison remains selected.</p>
      )}
      <section className="v3-document-change-navigation" aria-label="Changed documents">
        <button
          type="button"
          aria-label="Previous changed document"
          disabled={
            !changed.length || (changed.length === 1 && changed[0]?.documentId === selected)
          }
          onClick={() => navigate(-1)}
        >
          ← Previous
        </button>
        <button
          type="button"
          aria-label="Next changed document"
          disabled={
            !changed.length || (changed.length === 1 && changed[0]?.documentId === selected)
          }
          onClick={() => navigate(1)}
        >
          Next →
        </button>
      </section>
      <p className="v3-document-navigation-note">Navigate changed documents in this filter.</p>
      <button type="button" disabled={!selected} onClick={() => select("")}>
        Clear document selection
      </button>
      <div className="v3-document-list">
        {shown.map((document) => (
          <button
            type="button"
            key={document.documentId}
            aria-label={`${document.documentId} · ${statusLabel(document.changed)}`}
            aria-pressed={selected === document.documentId}
            onClick={() => select(document.documentId)}
          >
            <svg
              aria-hidden="true"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
            >
              <path d="M5 2h10l5 5v15H5zM15 2v6h5M8 12h9m-9 4h9" />
            </svg>
            <span>
              <strong>{document.documentId}</strong>
              <span
                className={`v3-document-status v3-document-status-${statusLabel(document.changed).toLowerCase()}`}
              >
                {statusLabel(document.changed)}
              </span>
            </span>
          </button>
        ))}
        {documents && !shown.length && (
          <p>
            {documents.length
              ? "No documents match these filters."
              : "No documents in the complete returned inventory."}
          </p>
        )}
      </div>
    </aside>
  );
}
