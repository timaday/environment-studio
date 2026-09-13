import type { ReactNode } from "react";
import type { useV3PlanInspection } from "./useV3PlanInspection";

export function V3PlanInspection({
  state,
  versionSelector,
  openDefinitions,
  captureProfile,
  reuseProfile,
}: {
  state: ReturnType<typeof useV3PlanInspection>;
  versionSelector: ReactNode;
  openDefinitions: () => void;
  captureProfile?: () => void;
  reuseProfile?: () => void;
}) {
  const plan = state.plan;
  const documents = state.inventory?.documents;
  const canRead = Boolean(state.selected && state.consent && !state.reading);
  return (
    <section className="hosted-panel v3-plans" aria-label="Native v3 plan inspection">
      <h1>Plans</h1>
      <p>Resume your session plan and inspect current and target documents.</p>
      <div className="v3-plan-selection">
        {versionSelector}
        <button
          type="button"
          className="primary"
          disabled={state.phase === "loading"}
          onClick={() => void state.refresh()}
        >
          Resume current plan / refresh
        </button>
      </div>
      {state.phase === "loading" && <p role="status">Loading the current Native v3 plan…</p>}
      {state.phase === "absent" && (
        <div className="v3-plan-notice">
          <svg
            aria-hidden="true"
            className="v3-plan-info-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
          >
            <circle cx="12" cy="12" r="10" />
            <path d="M12 10v7m0-11v1" />
          </svg>
          <div>
            <p>No current Native v3 plan in this session.</p>
            <p>Saved definitions are separate from session plans.</p>
          </div>
          <button type="button" onClick={openDefinitions}>
            Open definitions
          </button>
        </div>
      )}
      {state.error && (
        <p role="alert">
          {state.error} Refresh the plan before relying on earlier document context.
        </p>
      )}
      {plan && (
        <section className="v3-plan-context" aria-label="Current plan context">
          <p>
            Plan {plan.planId} · revision {plan.revision}
          </p>
          <p>
            Definition {plan.definition.objectId} · revision {plan.definition.workspaceRevision} ·
            binding {plan.bindingId} · destination {plan.destinationId}
          </p>
          <p>
            Observation {plan.inspectionValid ? "valid" : "missing or expired"} · Target{" "}
            {plan.targetComplete ? "complete" : "incomplete"}
          </p>
          {plan.observedDestination ? (
            <p>
              Current physical: {plan.currentCounts.documents} documents ·{" "}
              {plan.currentCounts.entities} entities · {plan.currentCounts.relations} relations.
            </p>
          ) : (
            <p>Current physical counts unavailable · no observation captured.</p>
          )}
          {plan.currentComputedCounts && (
            <p>
              Current computed: {plan.currentComputedCounts.nodes} nodes ·{" "}
              {plan.currentComputedCounts.memberships} memberships ·{" "}
              {plan.currentComputedCounts.cooccurrences} co-occurrences.
            </p>
          )}
          {!plan.currentComputedCounts && <p>Current computed counts unavailable.</p>}
          {plan.targetComplete && (
            <p>
              Target physical: {plan.targetCounts.documents} documents ·{" "}
              {plan.targetCounts.entities} entities · {plan.targetCounts.relations} relations.
            </p>
          )}
          {plan.targetComputedCounts ? (
            <p>
              Target computed: {plan.targetComputedCounts.nodes} nodes ·{" "}
              {plan.targetComputedCounts.memberships} memberships ·{" "}
              {plan.targetComputedCounts.cooccurrences} co-occurrences.
            </p>
          ) : (
            <p>Target computed counts unavailable.</p>
          )}
          {plan.observedDestination && (
            <details>
              <summary>Observed destination evidence</summary>
              <pre>{JSON.stringify(plan.observedDestination, null, 2)}</pre>
            </details>
          )}
          {plan.blockers.length > 0 && <p>Backend blockers: {plan.blockers.join(", ")}</p>}
        </section>
      )}
      {reuseProfile && (
        <button type="button" onClick={reuseProfile}>
          Reuse profile
        </button>
      )}
      {captureProfile && (
        <button type="button" onClick={captureProfile}>
          Capture profile
        </button>
      )}
      <section className="v3-plan-documents" aria-labelledby="v3-documents-heading">
        <h2 id="v3-documents-heading">Document comparison</h2>
        {documents && (
          <p>
            {documents.length} documents · {documents.filter((d) => d.changed === true).length}{" "}
            changed · {documents.filter((d) => d.changed === null).length} unknown
          </p>
        )}
        {documents?.length === 0 && <p>No documents in the complete returned inventory.</p>}
        <div className="v3-plan-document-controls">
          <label>
            Document
            <select
              value={state.selected}
              disabled={!documents?.length}
              onChange={(e) => state.select(e.target.value)}
            >
              <option value="">No document selected</option>
              {documents?.map((d) => (
                <option key={d.documentId} value={d.documentId}>
                  {d.documentId} ·{" "}
                  {d.changed === null ? "Unknown" : d.changed ? "Changed" : "Unchanged"}
                </option>
              ))}
            </select>
          </label>
          <fieldset>
            <legend>View</legend>
            <div className="v3-plan-modes">
              {(["raw", "formatted"] as const).map((mode) => (
                <button
                  type="button"
                  key={mode}
                  disabled={!state.selected}
                  aria-pressed={state.mode === mode}
                  onClick={() => state.setMode(mode)}
                >
                  {mode === "raw" ? "Raw" : "Formatted"}
                </button>
              ))}
            </div>
          </fieldset>
        </div>
        <label className="v3-plan-consent">
          <input
            type="checkbox"
            checked={state.consent}
            disabled={!state.selected}
            onChange={(e) => state.setConsent(e.target.checked)}
          />
          I understand complete documents may include unmapped or sensitive values.
        </label>
        {!plan || !documents ? (
          <p>Resume a plan with an observation before loading documents.</p>
        ) : (
          <>
            <p>
              Raw preserves exact characters. Formatted is a display projection. Placeholder
              comparison requires the concrete binding rail and mapped locations.
            </p>
            <button type="button" disabled={!canRead} onClick={() => void state.load()}>
              Load document comparison
            </button>
          </>
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
      </section>
    </section>
  );
}
