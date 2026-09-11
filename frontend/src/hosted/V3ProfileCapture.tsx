import { useEffect, useId, useMemo, useState } from "react";
import { captureRequest } from "../api/hostedV3Decoding";
import type { PlanSummary } from "../api/hostedV3Types";
import { CapturedProfileDetails } from "./CapturedProfileDetails";
import { type CaptureMapping, CaptureMappingRow } from "./CaptureMappingRow";
import { captureSummary } from "./captureSummary";
import { useNarrowLayout } from "./useNarrowLayout";
import "./V3ProfileCapture.css";
import type { useV3ProfileCapture } from "./useV3ProfileCapture";

type Capture = ReturnType<typeof useV3ProfileCapture>;
type Mapping = CaptureMapping;
const pageSize = 20;
const portable = /^[a-z][a-z0-9.-]{0,63}$/;
const complete = (value: Mapping | undefined) =>
  Boolean(
    value && portable.test(value.slotId) && value.label.length >= 1 && value.label.length <= 128,
  );

export function V3ProfileCapture({
  state,
  plan,
  active,
  back,
}: {
  state: Capture;
  plan: PlanSummary | null;
  active: boolean;
  back: () => void;
}) {
  const formId = useId();
  const narrow = useNarrowLayout();
  const [profileId, setProfileId] = useState("");
  const [profileRevision, setProfileRevision] = useState("");
  const [mappings, setMappings] = useState<Record<string, Mapping>>({});
  const [offset, setOffset] = useState(0);
  const [savePlan, setSavePlan] = useState<PlanSummary | null>(null);
  const [saveSource, setSaveSource] = useState<string | null>(null);
  const [recovering, setRecovering] = useState(false);
  const uncertain = state.pending && (recovering || Boolean(state.error));
  useEffect(() => {
    if (!state.pending) setRecovering(false);
    else if (state.error) setRecovering(true);
  }, [state.pending, state.error]);
  const context = JSON.stringify(plan);
  useEffect(() => {
    void context;
    void active;
    setProfileId("");
    setProfileRevision("");
    setMappings({});
    setOffset(0);
  }, [context, active]);
  const shownPlan = state.pending || state.saved ? savePlan : plan;
  const entities = state.entities;
  const mapped = entities?.filter((item) => complete(mappings[item.entity.handle])).length ?? 0;
  const identifierItems = useMemo(() => {
    const groups = new Map<string, number[]>();
    entities?.forEach((item, index) => {
      const identifier = mappings[item.entity.handle]?.slotId;
      if (!identifier || !portable.test(identifier)) return;
      const group = groups.get(identifier);
      if (group) group.push(index + 1);
      else groups.set(identifier, [index + 1]);
    });
    return groups;
  }, [entities, mappings]);
  const duplicateCount = [...identifierItems.values()].reduce(
    (count, group) => count + (group.length > 1 ? group.length : 0),
    0,
  );
  const input = useMemo(() => {
    if (!plan || !entities?.length) return null;
    if (entities.some((item) => !complete(mappings[item.entity.handle]))) return null;
    const values = entities.map((item) => ({
      entity: item.entity,
      ...mappings[item.entity.handle],
    }));
    if (new Set(values.map((m) => m.slotId)).size !== values.length) return null;
    try {
      return captureRequest({
        revision: plan.revision,
        profileId,
        profileRevision,
        mappings: values,
      });
    } catch {
      return null;
    }
  }, [plan, entities, mappings, profileId, profileRevision]);
  const summary = useMemo(() => {
    if (state.saved) return state.saved.projection.model;
    const source = state.pending ? saveSource : state.captured?.source;
    if (!source) return null;
    try {
      return captureSummary(source);
    } catch {
      return null;
    }
  }, [state.saved, state.captured, state.pending, saveSource]);
  function update(handle: string, field: keyof Mapping, value: string) {
    setMappings((before) => ({
      ...before,
      [handle]: { ...(before[handle] ?? { slotId: "", label: "" }), [field]: value },
    }));
  }
  function capture() {
    if (input) {
      state.configure(input);
      void state.capture();
    }
  }
  function save() {
    if (!state.pending) {
      setSavePlan(plan);
      setSaveSource(state.captured?.source ?? null);
    }
    void state.save();
  }
  const prepared = Boolean(state.captured || state.saved || state.pending);
  useEffect(() => {
    if (!prepared) {
      setSavePlan(null);
      setSaveSource(null);
    }
  }, [prepared]);
  const stage = state.saved ? 3 : prepared ? 2 : 1;
  const ready = Boolean(plan?.inspectionValid && plan.observedDestination);
  const observed = shownPlan?.observedDestination;
  const databaseName =
    observed?.engine === "postgresql"
      ? observed.identity.databaseName
      : observed?.engine === "oracle"
        ? observed.identity.conName
        : null;
  return (
    <section className="capture-workspace" aria-label="Capture profile workspace">
      {shownPlan && (
        <section className="capture-context" aria-label="Capture plan context">
          <div>
            <strong>
              {observed?.engine === "postgresql"
                ? "PostgreSQL"
                : observed?.engine === "oracle"
                  ? "Oracle"
                  : "Observed environment unavailable"}
            </strong>
            {databaseName && <span> · {databaseName}</span>}
            <p>
              Plan revision {shownPlan.revision} ·{" "}
              {shownPlan.inspectionValid ? "Inspection valid" : "Inspection required"}
            </p>
          </div>
          {!narrow && (
            <>
              <div>
                <p>Destination: {shownPlan.destinationId}</p>
                <p>Binding: {shownPlan.bindingId}</p>
              </div>
              <div>
                <p>Definition revision {shownPlan.definition.workspaceRevision}</p>
              </div>
            </>
          )}
          <details>
            <summary>Plan details</summary>
            <dl>
              <dt>Plan</dt>
              <dd>{shownPlan.planId}</dd>
              <dt>Definition</dt>
              <dd>
                {shownPlan.definition.objectId} · revision {shownPlan.definition.workspaceRevision}
              </dd>
              <dt>Binding</dt>
              <dd>{shownPlan.bindingId}</dd>
              <dt>Destination</dt>
              <dd>{shownPlan.destinationId}</dd>
            </dl>
          </details>
        </section>
      )}
      <div className="capture-layout">
        <div className="capture-main">
          <p>
            <button className="text-button" type="button" onClick={back}>
              Plans
            </button>{" "}
            / Capture profile
          </p>
          <h1>Capture profile</h1>
          <p>Capture reusable structure. Environment values are excluded.</p>
          <ol className="capture-steps" aria-label="Capture progress">
            {(narrow
              ? ["Map", "Review", "Saved"]
              : ["Mappings", "Captured review", "Saved draft"]
            ).map((name, index) => (
              <li key={name} aria-current={stage === index + 1 ? "step" : undefined}>
                <span>{index + 1}</span>
                {name}
              </li>
            ))}
          </ol>
          {!ready && !prepared ? (
            <section>
              <h2>An inspected plan is required</h2>
              <p>
                Create or resume a plan and complete its inspection before capturing reusable
                structure.
              </p>
              <button type="button" onClick={back}>
                Back to plan
              </button>
            </section>
          ) : !prepared ? (
            <>
              <div className="capture-profile-fields">
                <div>
                  <label htmlFor={`${formId}-id`}>Profile identifier</label>
                  <input
                    id={`${formId}-id`}
                    aria-describedby={`${formId}-id-help`}
                    value={profileId}
                    onChange={(e) => setProfileId(e.target.value)}
                    maxLength={64}
                    autoComplete="off"
                    disabled={state.busy}
                  />
                  <small id={`${formId}-id-help`}>
                    Portable name for reuse. Start with a lowercase letter; use lowercase letters,
                    digits, dots or hyphens (1–64 characters).
                  </small>
                </div>
                <div>
                  <label htmlFor={`${formId}-revision`}>Profile revision</label>
                  <input
                    id={`${formId}-revision`}
                    aria-describedby={`${formId}-revision-help`}
                    value={profileRevision}
                    onChange={(e) => setProfileRevision(e.target.value)}
                    maxLength={1024}
                    inputMode="numeric"
                    autoComplete="off"
                    disabled={state.busy}
                  />
                  <small id={`${formId}-revision-help`}>
                    Positive whole number, without leading zeros. Separate from the saved draft
                    revision.
                  </small>
                </div>
              </div>
              <section aria-label="Profile mappings">
                <h2>Map inspected items</h2>
                <p>Give every item a reusable identifier and a label.</p>
                {!entities ? (
                  <button type="button" disabled={state.busy} onClick={() => void state.load()}>
                    Load inspected structure
                  </button>
                ) : (
                  <>
                    <p>
                      {mapped} of {entities.length} mappings entered
                    </p>
                    {duplicateCount > 0 && (
                      <p role="alert" className="capture-mapping-error">
                        {duplicateCount} items share reusable identifiers. Give each item a unique
                        identifier.
                      </p>
                    )}
                    <div className="capture-mappings">
                      {!narrow && (
                        <div className="capture-mapping-heading" aria-hidden="true">
                          <span>Inspected item</span>
                          <span>Reusable identifier</span>
                          <span>Label</span>
                        </div>
                      )}
                      {entities.slice(offset, offset + pageSize).map((item, index) => {
                        const number = offset + index + 1;
                        const values = mappings[item.entity.handle] ?? { slotId: "", label: "" };
                        const other = identifierItems
                          .get(values.slotId)
                          ?.find((itemNumber) => itemNumber !== number);
                        return (
                          <CaptureMappingRow
                            key={item.entity.handle}
                            number={number}
                            handle={item.entity.handle}
                            typeId={item.typeId}
                            value={values}
                            error={
                              other === undefined
                                ? undefined
                                : `This identifier is also used by item ${other}. Enter a unique identifier.`
                            }
                            busy={state.busy}
                            narrow={narrow}
                            update={(field, value) => update(item.entity.handle, field, value)}
                          />
                        );
                      })}
                    </div>
                    {entities.length > pageSize ? (
                      <nav className="capture-page" aria-label="Mapping pages">
                        <button
                          type="button"
                          disabled={offset === 0 || state.busy}
                          onClick={() => setOffset(Math.max(0, offset - pageSize))}
                        >
                          Previous items
                        </button>
                        <p>
                          Items {offset + 1}–{Math.min(offset + pageSize, entities.length)} of{" "}
                          {entities.length}
                        </p>
                        <button
                          type="button"
                          disabled={offset + pageSize >= entities.length || state.busy}
                          onClick={() => setOffset(offset + pageSize)}
                        >
                          Next items
                        </button>
                      </nav>
                    ) : (
                      <p>Showing all {entities.length} items</p>
                    )}
                    <p>
                      Reusable identifiers travel with the profile; source identifiers stay with
                      this inspection.
                    </p>
                  </>
                )}
              </section>
              <div className="capture-actions">
                <p>{mapped ? "Mappings entered · Not captured" : "Not captured"}</p>
                <button
                  type="button"
                  className="primary"
                  disabled={!input || state.busy}
                  onClick={capture}
                >
                  Capture for review
                </button>
                <button type="button" onClick={back}>
                  Back to plan
                </button>
              </div>
            </>
          ) : summary ? (
            <>
              {uncertain && (
                <section className="capture-save-uncertain" role="alert">
                  <h2>Save not confirmed</h2>
                  <p>The save response was unavailable. The draft may already be saved.</p>
                  <p>Retry to confirm the same draft. Keep this session open.</p>
                </section>
              )}
              <section className={`capture-review${state.saved ? " capture-saved" : ""}`}>
                <div className="capture-state-heading">
                  {state.saved && (
                    <svg className="capture-saved-icon" viewBox="0 0 44 44" aria-hidden="true">
                      <circle cx="22" cy="22" r="22" fill="currentColor" />
                      <path d="m12 22 7 7 14-15" fill="none" stroke="#0b1220" strokeWidth="3" />
                    </svg>
                  )}
                  <h2>
                    {state.saved
                      ? "Draft saved"
                      : state.pending
                        ? "Captured profile"
                        : "Review captured profile"}
                  </h2>
                  {!state.saved && !state.pending && (
                    <span className="capture-state-badge" role="status">
                      Captured · Not saved
                    </span>
                  )}
                </div>
                {state.saved && <p role="status">{summary.id} is saved as an immutable draft.</p>}
                <dl className="capture-summary">
                  <div>
                    <dt>Profile identifier</dt>
                    <dd>{summary.id}</dd>
                  </div>
                  <div>
                    <dt>Profile revision</dt>
                    <dd>{summary.revision}</dd>
                  </div>
                  {state.saved && (
                    <div>
                      <dt>Saved draft revision</dt>
                      <dd>{state.saved.workspaceRevision}</dd>
                    </div>
                  )}
                  <div>
                    <dt>Summary</dt>
                    <dd>
                      {summary.entities.length} items · {summary.relations.length} relationships
                    </dd>
                  </div>
                </dl>
                {state.saved && (
                  <details>
                    <summary>Saved revision details</summary>
                    <dl>
                      <dt>Workspace object</dt>
                      <dd>{state.saved.objectId}</dd>
                      <dt>Draft revision</dt>
                      <dd>{state.saved.workspaceRevision}</dd>
                      <dt>Definition</dt>
                      <dd>
                        {state.saved.definition.objectId} · revision{" "}
                        {state.saved.definition.workspaceRevision}
                      </dd>
                    </dl>
                  </details>
                )}
                <CapturedProfileDetails
                  key={state.saved ? "saved" : "captured"}
                  model={summary}
                  narrow={narrow}
                  saved={Boolean(state.saved)}
                />
                <details>
                  <summary>
                    {state.saved ? "View saved source (JSON)" : "View captured source (JSON)"}
                  </summary>
                  <pre>
                    {state.saved?.source ?? (state.pending ? saveSource : state.captured?.source)}
                  </pre>
                </details>
              </section>
              {state.saved ? (
                <div className="capture-saved-actions">
                  <p>
                    Publication is separate. Once published, reuse the whole profile or selected
                    parts.
                  </p>
                  <button className="primary" type="button" onClick={back}>
                    Back to plan
                  </button>
                </div>
              ) : uncertain ? (
                <div className="capture-actions">
                  <p>Editing is unavailable until the save result is known.</p>
                  <button
                    type="button"
                    className="primary"
                    disabled={state.busy}
                    onClick={() => void state.retry()}
                  >
                    Retry save
                  </button>
                  <button type="button" onClick={back}>
                    Back to plan
                  </button>
                </div>
              ) : (
                <div className="capture-actions">
                  <p>Save explicitly to keep this profile as a draft.</p>
                  <button
                    type="button"
                    className="primary"
                    disabled={state.busy || state.pending}
                    onClick={save}
                  >
                    Save draft
                  </button>
                  <button
                    type="button"
                    disabled={state.busy || state.pending || !input}
                    onClick={() => {
                      if (input) state.configure(input);
                    }}
                  >
                    Edit mappings
                  </button>
                  <button type="button" onClick={back}>
                    Back to plan
                  </button>
                </div>
              )}
            </>
          ) : (
            <p role="alert">
              Captured summary unavailable. The original source has not been changed.
            </p>
          )}
          {state.busy && (
            <p role="status">{state.pending ? "Saving draft…" : "Loading capture information…"}</p>
          )}
          {state.error && !uncertain && <p role="alert">{state.error}</p>}
        </div>
        <aside className="capture-help">
          <details open={!narrow || undefined}>
            <summary>Capture and reuse help</summary>
            {!state.saved && (
              <>
                <h2>
                  {uncertain
                    ? "Confirm this draft"
                    : prepared
                      ? "Before saving"
                      : "What happens next"}
                </h2>
                <p>
                  {uncertain
                    ? "The original captured structure and destination are preserved. Retrying uses the same save."
                    : prepared
                      ? "Review labels, reusable identifiers and relationships. Saving creates an immutable draft revision."
                      : "Capture prepares the value-free profile for review. Saving is a separate action."}
                </p>
              </>
            )}
            <h2>Reuse later</h2>
            <p>
              {uncertain
                ? "Publication is separate. Reuse a whole published profile or selected parts."
                : "Reuse a whole published profile or selected parts. Unselected structure is preserved. Environment values are entered in the target plan."}
            </p>
          </details>
        </aside>
      </div>
    </section>
  );
}
