import { useId, useState } from "react";

export type CaptureMapping = Readonly<{ slotId: string; label: string }>;
export function CaptureMappingRow({
  number,
  handle,
  typeId,
  value,
  error,
  busy,
  narrow,
  update,
}: {
  number: number;
  handle: string;
  typeId: string;
  value: CaptureMapping;
  error?: string;
  busy: boolean;
  narrow: boolean;
  update: (field: keyof CaptureMapping, value: string) => void;
}) {
  const [expanded, setExpanded] = useState(number === 1);
  const fieldsId = useId();
  return (
    <section className="capture-mapping">
      <div className="capture-mapping-top">
        {narrow ? (
          <button
            type="button"
            className="capture-row-toggle"
            aria-expanded={expanded}
            aria-controls={fieldsId}
            onClick={() => setExpanded((open) => !open)}
          >
            <span aria-hidden="true">{expanded ? "▾" : "▸"}</span>
            <span>
              {value.label || `Inspected item ${number}`} · {typeId}
              <span className="capture-entered-id">{value.slotId}</span>
              {error && <span className="capture-mapping-error">Duplicate identifier</span>}
            </span>
          </button>
        ) : (
          <span className="capture-inspected-type">{typeId}</span>
        )}
        <details className="capture-source-details">
          <summary>Source details</summary>
          <dl>
            <dt>Declared type</dt>
            <dd>{typeId}</dd>
            <dt>Source identifier</dt>
            <dd>{handle}</dd>
          </dl>
        </details>
      </div>
      <div id={fieldsId} className="capture-mapping-fields" hidden={narrow && !expanded}>
        <label>
          Reusable identifier
          <input
            aria-label={`Reusable identifier ${number}`}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${fieldsId}-error` : undefined}
            value={value.slotId}
            onChange={(e) => update("slotId", e.target.value)}
            maxLength={64}
            disabled={busy}
            autoComplete="off"
          />
          {error && (
            <small className="capture-mapping-error" id={`${fieldsId}-error`}>
              {error}
            </small>
          )}
        </label>
        <label>
          Label
          <input
            aria-label={`Label ${number}`}
            value={value.label}
            onChange={(e) => update("label", e.target.value)}
            maxLength={128}
            disabled={busy}
            autoComplete="off"
          />
        </label>
      </div>
    </section>
  );
}
