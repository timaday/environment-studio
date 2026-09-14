import { useState } from "react";
import type { useV3PlanInspection } from "./useV3PlanInspection";
import { V3DocumentNavigator } from "./V3DocumentNavigator";
import "./V3DocumentComparison.css";

type ComparisonState = Pick<
  ReturnType<typeof useV3PlanInspection>,
  | "plan"
  | "inventory"
  | "selected"
  | "mode"
  | "consent"
  | "reading"
  | "current"
  | "target"
  | "bindingRail"
  | "select"
  | "setMode"
  | "setConsent"
  | "load"
>;

export function V3DocumentComparison({ state }: { state: ComparisonState }) {
  const { plan } = state;
  const documents = state.inventory?.documents;
  const [mapping, setMapping] = useState("");
  const selectedMapping =
    state.bindingRail.find(
      (item) => `${JSON.stringify(item.entity)}:${item.fieldId}` === mapping,
    ) ?? state.bindingRail[0];
  const canRead = Boolean(state.selected && state.consent && !state.reading);
  return (
    <section className="v3-plan-documents" aria-labelledby="v3-documents-heading">
      <div className="v3-comparison-heading">
        <div>
          <h2 id="v3-documents-heading">Document comparison</h2>
          <p>
            Review XML / CLOB records across the plan. Filtering does not change validation scope.
          </p>
        </div>
        {documents && (
          <p>
            {documents.length} documents · {documents.filter((d) => d.changed === true).length}{" "}
            changed · {documents.filter((d) => d.changed === null).length} unknown
          </p>
        )}
      </div>
      <div className="v3-comparison-workbench">
        <V3DocumentNavigator
          documents={documents}
          selected={state.selected}
          select={state.select}
        />
        <div className="v3-comparison-detail">
          <div className="v3-comparison-toolbar">
            <div className="v3-comparison-identity">
              <h3>{state.selected || "No document selected"}</h3>
              <p>
                {plan
                  ? `Plan revision ${plan.revision} · read-only comparison`
                  : "No observation loaded"}
              </p>
            </div>
            <fieldset>
              <legend className="sr-only">View</legend>
              <div className="v3-plan-modes">
                {(["raw", "placeholders", "formatted"] as const).map((mode) => (
                  <button
                    type="button"
                    key={mode}
                    disabled={!state.selected}
                    aria-pressed={state.mode === mode}
                    onClick={() => state.setMode(mode)}
                  >
                    {mode === "raw"
                      ? "Raw"
                      : mode === "placeholders"
                        ? "Placeholders"
                        : "Formatted"}
                  </button>
                ))}
              </div>
            </fieldset>
          </div>
          <p className="v3-comparison-mode-note">
            {state.mode === "raw"
              ? "Raw preserves exact characters. No formatting or edits are applied."
              : state.mode === "placeholders"
                ? "Mapping projection only. Concrete current and target values remain visible in the binding rail."
                : "Formatted is a display projection only. Exported XML is unchanged by this view."}
          </p>
          <div className="v3-comparison-disclosure">
            <label className="v3-plan-consent">
              <input
                type="checkbox"
                checked={state.consent}
                disabled={!state.selected}
                onChange={(e) => state.setConsent(e.target.checked)}
              />
              I understand complete documents may include unmapped or sensitive values.
            </label>
            <button type="button" disabled={!canRead} onClick={() => void state.load()}>
              Load document comparison
            </button>
          </div>
          {(!plan || !documents) && (
            <p>Resume a plan with an observation before loading documents.</p>
          )}
          {state.reading && (
            <p role="status">Loading the selected revision and checking plan context…</p>
          )}
          <div className="v3-plan-panes">
            {(
              [
                ["Current", state.current],
                ["Target", state.target],
              ] as const
            ).map(([label, value]) => (
              <section key={label} aria-label={`${label} XML`}>
                <h3>{label}</h3>
                {value ? (
                  <>
                    <p>
                      {value.documentId} · revision {value.revision} · {value.mode}
                    </p>
                    <p>
                      {value.exact ? "Exact characters" : "Display projection only"} ·{" "}
                      {value.redacted ? "Redacted" : "Concrete document"}
                    </p>
                    {value.unmappedConcreteMayRemain && <p>Unmapped concrete values may remain.</p>}
                    {value.omissions.length > 0 && <p>Omissions: {value.omissions.join(", ")}</p>}
                    {/* biome-ignore lint/a11y/noNoninteractiveTabindex: Keyboard users must scroll the read-only XML pane. */}
                    <pre tabIndex={0}>{value.text}</pre>
                  </>
                ) : (
                  <div className="v3-plan-empty-document">
                    <svg
                      aria-hidden="true"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.5"
                    >
                      <path d="M5 2h10l5 5v15H5zM15 2v6h5M8 12h9m-9 4h9" />
                    </svg>
                    <p>
                      {!plan
                        ? label === "Current"
                          ? "No observed document available."
                          : "No target document available."
                        : label === "Target" && !plan.targetComplete
                          ? "Target unavailable · not evidence of unchanged content."
                          : "Select a document, confirm disclosure and load."}
                    </p>
                  </div>
                )}
              </section>
            ))}
          </div>
          {state.mode === "placeholders" && state.current && (
            <section className="v3-binding-rail" aria-labelledby="v3-binding-rail-heading">
              <h3 id="v3-binding-rail-heading">Binding rail</h3>
              <p>
                Placeholder tokens are labels only. Current and target values remain visible here
                for the selected document.
              </p>
              {state.bindingRail.length > 0 && (
                <label>
                  Selected mapping
                  <select
                    value={
                      selectedMapping
                        ? `${JSON.stringify(selectedMapping.entity)}:${selectedMapping.fieldId}`
                        : ""
                    }
                    onChange={(event) => setMapping(event.target.value)}
                  >
                    {state.bindingRail.map((item) => (
                      <option
                        key={`${JSON.stringify(item.entity)}:${item.fieldId}`}
                        value={`${JSON.stringify(item.entity)}:${item.fieldId}`}
                      >
                        {item.token} · {item.change}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {state.bindingRail.length === 0 ? (
                <p>No mapped placeholder locations were returned for this document.</p>
              ) : (
                <div className="v3-binding-rail-list">
                  {(selectedMapping ? [selectedMapping] : []).map((item) => (
                    <article key={`${JSON.stringify(item.entity)}:${item.fieldId}`}>
                      <div>
                        <strong>{item.fieldId}</strong>
                        <span>{item.typeId}</span>
                      </div>
                      <code>{item.token}</code>
                      <dl>
                        <div>
                          <dt>Current</dt>
                          <dd>{item.current}</dd>
                        </div>
                        <div>
                          <dt>Target</dt>
                          <dd>{item.target}</dd>
                        </div>
                        <div>
                          <dt>Status</dt>
                          <dd>{item.change}</dd>
                        </div>
                        <div>
                          <dt>Selected document locations</dt>
                          <dd>
                            Current {item.currentLocations} · Target {item.targetLocations}
                          </dd>
                        </div>
                      </dl>
                    </article>
                  ))}
                </div>
              )}
            </section>
          )}
        </div>
      </div>
    </section>
  );
}
