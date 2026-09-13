import { useId, useRef, useState } from "react";

export type DefinitionDiagnostic = Readonly<{
  phase: "parse" | "shape" | "semantic" | "publication";
  code: string;
  pointer: string;
  message: string;
}>;

export type DefinitionModel = Readonly<{
  entityTypes: readonly Readonly<{
    id: string;
    fields: readonly Readonly<{ id: string; valueType: string }>[];
  }>[];
  relations: readonly Readonly<{ id: string; fromType: string; toType: string }>[];
}>;

type ResultProjection = Readonly<{
  /** Exact decimal representation; never convert an authoritative revision to Number. */
  revision: `${bigint}`;
  source: string;
  diagnostics: readonly DefinitionDiagnostic[];
}>;

export type DefinitionInspectionState =
  | Readonly<{ kind: "unavailable" }>
  | Readonly<{ kind: "loading" }>
  | (ResultProjection & Readonly<{ kind: "rejected"; model?: never }>)
  | (ResultProjection & Readonly<{ kind: "incomplete"; model: DefinitionModel }>);

const tabs = ["Model", "Source", "Diagnostics"] as const;
type Tab = (typeof tabs)[number];

export function DefinitionInspector({ state }: { state: DefinitionInspectionState }) {
  const id = useId();
  const [tab, setTab] = useState<Tab>("Model");
  const controls = useRef<(HTMLButtonElement | null)[]>([]);
  if (state.kind === "loading" || state.kind === "unavailable") {
    return (
      <section
        className="definition-inspector"
        aria-label="Definition inspection"
        aria-busy={state.kind === "loading"}
      >
        <p role="status">
          {state.kind === "loading"
            ? "Loading definition result…"
            : "Definition result unavailable."}
        </p>
        <p>No compilation result is available to inspect.</p>
      </section>
    );
  }
  return (
    <section className="definition-inspector" aria-label="Definition inspection">
      <div className="panel-heading">
        <h2>Definition inspection</h2>
        <span>Revision {state.revision}</span>
      </div>
      <p className="mode-note" role="status">
        {state.kind === "rejected"
          ? "Rejected · no model available."
          : "Incomplete · publication blocked."}{" "}
        This result cannot authorize inspection, publication or export.
      </p>
      <section className="definition-blockers" aria-label="Compilation and publication blockers">
        <h3>{state.diagnostics.length} blockers · full result scope</h3>
        <ul>
          {state.diagnostics.map((diagnostic) => (
            <li key={JSON.stringify(diagnostic)}>
              <strong>{diagnostic.code}</strong>{" "}
              <span>
                {diagnostic.phase} · {diagnostic.pointer || "Root"}
              </span>
              <p>{diagnostic.message}</p>
            </li>
          ))}
        </ul>
      </section>
      <div className="segmented definition-tabs" role="tablist" aria-label="Definition view">
        {tabs.map((item, index) => (
          <button
            key={item}
            ref={(element) => {
              controls.current[index] = element;
            }}
            type="button"
            role="tab"
            id={`${id}-tab-${item}`}
            aria-controls={`${id}-panel`}
            aria-selected={tab === item}
            tabIndex={tab === item ? 0 : -1}
            onClick={() => setTab(item)}
            onKeyDown={(event) => {
              let next: number;
              switch (event.key) {
                case "ArrowRight":
                  next = (index + 1) % tabs.length;
                  break;
                case "ArrowLeft":
                  next = (index + tabs.length - 1) % tabs.length;
                  break;
                case "Home":
                  next = 0;
                  break;
                case "End":
                  next = tabs.length - 1;
                  break;
                default:
                  return;
              }
              event.preventDefault();
              setTab(tabs[next]);
              controls.current[next]?.focus();
            }}
          >
            {item}
          </button>
        ))}
      </div>
      <div
        role="tabpanel"
        id={`${id}-panel`}
        aria-labelledby={`${id}-tab-${tab}`}
        // biome-ignore lint/a11y/noNoninteractiveTabindex: Static tab panels need keyboard focus for reading and scrolling.
        tabIndex={0}
        className="definition-panel"
      >
        {tab === "Model" &&
          (state.kind === "rejected" ? (
            <p>No model is available for this rejected result.</p>
          ) : (
            <>
              <h3>Declared types and fields</h3>
              {state.model.entityTypes.length === 0 && <p>No entity types declared.</p>}
              <ul>
                {state.model.entityTypes.map((type) => (
                  <li key={type.id}>
                    <strong>{type.id}</strong>
                    {type.fields.length === 0 ? (
                      <p>No fields declared.</p>
                    ) : (
                      <ul>
                        {type.fields.map((field) => (
                          <li key={field.id}>
                            {field.id} · {field.valueType}
                          </li>
                        ))}
                      </ul>
                    )}
                  </li>
                ))}
              </ul>
              <h3>Declared relations</h3>
              {state.model.relations.length === 0 ? (
                <p>No relations declared.</p>
              ) : (
                <ul>
                  {state.model.relations.map((relation) => (
                    <li key={relation.id}>
                      {relation.id}: {relation.fromType} → {relation.toType}
                    </li>
                  ))}
                </ul>
              )}
            </>
          ))}
        {tab === "Source" && (
          <>
            <p>Original supplied source · session display only.</p>
            <pre>
              <code>{state.source}</code>
            </pre>
          </>
        )}
        {tab === "Diagnostics" && (
          <p>
            {state.diagnostics.length} diagnostics for revision {state.revision}. All corrective
            messages remain visible in the blocker list above.
          </p>
        )}
      </div>
    </section>
  );
}
