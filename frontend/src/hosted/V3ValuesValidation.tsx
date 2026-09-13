import { useEffect, useState } from "react";
import { ApiFailure, failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Api, prepareCommand } from "../api/hostedV3";
import type { Ack, PlanCommand, PlanSummary } from "../api/hostedV3Types";
import { useV3BindingReview } from "./useV3BindingReview";
import { type DraftValueItem, useV3DraftValues } from "./useV3DraftValues";
import { useV3Validation } from "./useV3Validation";
import { WorkflowPlanContext } from "./WorkflowPlanContext";
import "./V3ValuesValidation.css";

type FieldState = Extract<PlanCommand, { kind: "bind-field" }>["state"];
type ValuePending = Readonly<{
  planId: string;
  command: Extract<PlanCommand, { kind: "bind-field" }>;
}>;
type ValueCommandState = Readonly<{
  busy: boolean;
  pending: ValuePending | null;
  receipt: Ack | null;
  error: string;
}>;
const emptyValueCommand: ValueCommandState = {
  busy: false,
  pending: null,
  receipt: null,
  error: "",
};

type Props = Readonly<{
  api: HostedApi;
  plan: PlanSummary | null;
  active: boolean;
  view: "values" | "validation";
  back: () => void;
  refreshPlan: () => Promise<void>;
}>;

function entityKey(entity: DraftValueItem["entity"]): string {
  return entity.kind === "existing"
    ? `existing:${entity.handle}`
    : `fresh:${entity.slotId}:${entity.typeId}`;
}
function entityLabel(item: DraftValueItem): string {
  if (item.entity.kind === "existing") return item.entity.handle;
  return `${item.entity.slotId} · ${item.entity.typeId}`;
}
function valueLabel(value: { readonly state: string; readonly text?: string }): string {
  if (value.state === "value") return value.text ?? "";
  if (value.state === "masked") return "Comparison unavailable";
  if (value.state === "unresolved") return "Target unresolved";
  if (value.state === "absent") return "Absent";
  return "Unavailable";
}
function fieldStateLabel(field: DraftValueItem["fields"][number]): string {
  if (field.kind === "entered")
    return field.masked ? "Entered masked value" : `Entered value ${field.value}`;
  if (field.kind === "keep-observed")
    return field.masked ? "Keep observed masked value" : "Keep observed value";
  if (field.kind === "absent") return "Set absent";
  return "Unresolved";
}
function checkLabel(value: string): string {
  return value.replaceAll("_", " ");
}

function ValuesView({ api, plan, active, back, refreshPlan }: Omit<Props, "view">) {
  const draft = useV3DraftValues(api, plan, active);
  const [command, setCommand] = useState<ValueCommandState>(emptyValueCommand);
  const [selected, setSelected] = useState("");
  const [fieldId, setFieldId] = useState("");
  const [mode, setMode] = useState<FieldState["kind"]>("entered");
  const [entered, setEntered] = useState<Record<string, string>>({});
  const [savedReceipt, setSavedReceipt] = useState<Readonly<{
    planId: string;
    revision: string;
  }> | null>(null);
  const page = draft.page;
  const items = page?.items ?? [];
  const effectiveSelected = selected || (items[0] ? entityKey(items[0].entity) : "");
  const selectedItem = items.find((item) => entityKey(item.entity) === effectiveSelected) ?? null;
  const binding = useV3BindingReview(
    api,
    plan,
    selectedItem?.entity ?? null,
    active && Boolean(selectedItem),
  );
  const eligible = Boolean(plan?.inspectionValid && plan.observedDestination?.evidenceValid);
  useEffect(() => {
    if (selectedItem && !fieldId) setFieldId(selectedItem.fields[0]?.fieldId ?? "");
  }, [selectedItem, fieldId]);
  useEffect(() => {
    void plan?.planId;
    setCommand(emptyValueCommand);
  }, [plan?.planId]);
  const currentInputKey =
    selectedItem && fieldId ? `${entityKey(selectedItem.entity)}:${fieldId}` : "";
  const text = currentInputKey ? (entered[currentInputKey] ?? "") : "";
  async function executeValue(pending: ValuePending) {
    if (!active || command.busy) return;
    setCommand({ busy: true, pending, receipt: null, error: "" });
    try {
      const receipt = await new HostedV3Api(api).command(pending.planId, pending.command);
      setSavedReceipt({ planId: receipt.planId, revision: receipt.revision });
      setCommand({ ...emptyValueCommand, receipt });
      void refreshPlan();
    } catch (error) {
      const refused =
        error instanceof ApiFailure &&
        [
          "400:INVALID_REQUEST",
          "404:NOT_FOUND",
          "409:CONFLICT",
          "409:STALE_PREVIEW",
          "429:CAPACITY",
        ].includes(`${error.status}:${error.code}`);
      setCommand({
        ...emptyValueCommand,
        pending: refused ? null : pending,
        error: failureMessage(error),
      });
    }
  }
  function submit() {
    if (
      !selectedItem ||
      !fieldId ||
      !plan?.inspectionValid ||
      !plan.observedDestination?.evidenceValid ||
      command.pending ||
      command.busy
    )
      return;
    let state: FieldState;
    if (mode === "entered") state = { kind: "entered", text };
    else state = { kind: mode } as FieldState;
    try {
      const prepared = prepareCommand({
        kind: "bind-field",
        expectedRevision: plan.revision,
        requestId: crypto.randomUUID(),
        entity: selectedItem.entity,
        fieldId,
        state,
      });
      if (prepared.kind !== "bind-field") throw new ApiFailure(0, "INVALID_REQUEST");
      void executeValue({ planId: plan.planId, command: prepared });
    } catch {
      setCommand({ ...emptyValueCommand, error: "INVALID_REQUEST" });
    }
  }
  return (
    <section className="capture-workspace values-workspace" aria-label="Target values workspace">
      <WorkflowPlanContext plan={plan} label="Values plan context" />
      <header className="values-header">
        <div>
          <button type="button" onClick={back}>
            Back to plan
          </button>
          <h1>Environment values</h1>
          <p>
            Enter target-specific values. Current values are read-only and profiles remain
            value-free.
          </p>
        </div>
        <button type="button" onClick={() => void draft.load(0)} disabled={!eligible || draft.busy}>
          Load target values
        </button>
      </header>
      {!eligible && (
        <section className="values-panel values-prerequisite">
          <h2>Plan inspection required</h2>
          <p>Resume a plan with valid observation evidence before editing target values.</p>
        </section>
      )}
      {draft.error && <p role="alert">{draft.error}</p>}
      {command.error && !command.pending && <p role="alert">{command.error}</p>}
      {command.pending && (
        <section className="values-panel values-warning" aria-label="Save not confirmed">
          <h2>Save not confirmed</h2>
          <p>The original target value command is preserved. Retry sends the same command only.</p>
          {command.error && <p role="alert">{command.error}</p>}
          <button
            type="button"
            onClick={() => {
              if (command.pending) void executeValue(command.pending);
            }}
            disabled={command.busy}
          >
            Retry original value command
          </button>
        </section>
      )}
      {savedReceipt?.planId === plan?.planId && (
        <p role="status">Target value saved for this plan revision.</p>
      )}
      {page && (
        <div className="values-layout">
          <aside className="values-panel">
            <h2>Target values</h2>
            <p>
              {page.total} returned items · page starts at {page.offset + 1}
            </p>
            <label>
              Item selector
              <select
                value={effectiveSelected}
                onChange={(event) => {
                  setSelected(event.target.value);
                  setFieldId("");
                }}
              >
                {items.map((item) => (
                  <option key={entityKey(item.entity)} value={entityKey(item.entity)}>
                    {entityLabel(item)} · {item.disposition}
                  </option>
                ))}
              </select>
            </label>
            <nav className="values-page" aria-label="Draft value pages">
              <button
                type="button"
                disabled={draft.busy || page.offset === 0}
                onClick={() => void draft.load(Math.max(0, page.offset - 20))}
              >
                Previous page
              </button>
              <button
                type="button"
                disabled={draft.busy || page.nextOffset === null}
                onClick={() => void draft.load(page.nextOffset ?? 0)}
              >
                Next page
              </button>
            </nav>
            <details>
              <summary>Portable identifier details</summary>
              <p>
                Existing items use returned handles. Fresh items use profile slots and declared
                types. These identifiers are technical anchors, not labels inferred by the client.
              </p>
            </details>
          </aside>
          <section className="values-panel values-editor" aria-label="Selected item values">
            {selectedItem ? (
              <>
                <h2>
                  {selectedItem.disposition === "retain"
                    ? "Retained item"
                    : selectedItem.disposition === "create"
                      ? "New item"
                      : "Removed item"}
                </h2>
                <p>{entityLabel(selectedItem)}</p>
                <button type="button" onClick={() => void binding.load()} disabled={binding.busy}>
                  Load comparison
                </button>
                {binding.error && <p role="alert">{binding.error}</p>}
                <div className="values-fields">
                  {selectedItem.fields.map((field) => {
                    const row = binding.items?.find((item) => item.fieldId === field.fieldId);
                    return (
                      <fieldset key={field.fieldId} aria-label={`Field ${field.fieldId}`}>
                        <legend>{field.fieldId}</legend>
                        <span>{fieldStateLabel(field)}</span>
                        <dl>
                          <div>
                            <dt>Current value</dt>
                            <dd>{row ? valueLabel(row.current) : "Load pending"}</dd>
                          </div>
                          <div>
                            <dt>Target value</dt>
                            <dd>{row ? valueLabel(row.target) : fieldStateLabel(field)}</dd>
                          </div>
                          <div>
                            <dt>Status</dt>
                            <dd>{row?.change ?? "Draft"}</dd>
                          </div>
                        </dl>
                        {(field.masked ||
                          row?.current.state === "masked" ||
                          row?.target.state === "masked") && (
                          <p>Comparison unavailable for masked values.</p>
                        )}
                      </fieldset>
                    );
                  })}
                </div>
                <div className="values-entry">
                  <label>
                    Field to update
                    <select value={fieldId} onChange={(event) => setFieldId(event.target.value)}>
                      {selectedItem.fields.map((field) => (
                        <option key={field.fieldId} value={field.fieldId}>
                          {field.fieldId}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Target state
                    <select
                      value={mode}
                      onChange={(event) => setMode(event.target.value as FieldState["kind"])}
                    >
                      <option value="entered">Enter value</option>
                      <option value="keep-observed">Keep observed</option>
                      <option value="unresolved">Leave unresolved</option>
                      <option value="absent">Set absent</option>
                    </select>
                  </label>
                  {mode === "entered" && (
                    <label>
                      Target value text
                      <input
                        value={text}
                        onChange={(event) =>
                          setEntered((current) => ({
                            ...current,
                            [currentInputKey]: event.target.value,
                          }))
                        }
                      />
                    </label>
                  )}
                  <button
                    type="button"
                    className="primary"
                    onClick={submit}
                    disabled={command.busy || Boolean(command.pending) || !fieldId}
                  >
                    Submit target value
                  </button>
                </div>
              </>
            ) : (
              <p>Select a returned draft item to review its values.</p>
            )}
          </section>
        </div>
      )}
    </section>
  );
}

function ValidationView({ api, plan, active, back }: Omit<Props, "view" | "refreshPlan">) {
  const validation = useV3Validation(api, plan, active);
  const eligible = Boolean(plan?.inspectionValid && plan.observedDestination?.evidenceValid);
  return (
    <section
      className="capture-workspace values-workspace validation-workspace"
      aria-label="Validation workspace"
    >
      <WorkflowPlanContext plan={plan} label="Validation plan context" />
      <header className="values-header">
        <div>
          <button type="button" onClick={back}>
            Back to plan
          </button>
          <h1>Validate plan</h1>
          <p>
            Run backend validation before export. The application cannot override failed or unknown
            checks.
          </p>
        </div>
        <button
          type="button"
          onClick={() => void validation.validate()}
          disabled={!eligible || validation.busy}
        >
          Run validation
        </button>
      </header>
      {!eligible && (
        <section className="values-panel values-prerequisite">
          <h2>Plan inspection required</h2>
          <p>Resume a plan with valid target evidence before validation.</p>
        </section>
      )}
      {validation.error && <p role="alert">{validation.error}</p>}
      {validation.summary && (
        <div className="validation-layout">
          <section className="values-panel validation-summary">
            <h2>Validation summary</h2>
            <p>{validation.summary.targetComplete ? "Target complete" : "Target incomplete"}</p>
            <p>Export remains unavailable.</p>
            <div className="validation-checks">
              {validation.summary.checks.map((check) => (
                <article
                  key={check.check}
                  className={`validation-outcome ${check.outcome.toLowerCase()}`}
                >
                  <strong>{checkLabel(check.check)}</strong>
                  <span>{check.outcome}</span>
                </article>
              ))}
            </div>
          </section>
          <section className="values-panel validation-rules">
            <h2>Computed rule details</h2>
            <p>
              {validation.summary.computedRuleCount ?? "No complete target"} computed rules returned
              by backend validation.
            </p>
            <button
              type="button"
              disabled={!validation.summary.targetComplete || validation.busy}
              onClick={() => void validation.readPage(0, 4)}
            >
              Load computed rules
            </button>
            {validation.page && (
              <div className="validation-rule-list">
                {validation.page.items.map((rule) => (
                  <article
                    key={`${rule.kind}:${rule.declaration}:${rule.source ? JSON.stringify(rule.source) : "whole"}:${rule.actual}:${rule.minimum}:${rule.maximum}:${rule.outcome}`}
                  >
                    <strong>{rule.declaration}</strong>
                    <dl>
                      <div>
                        <dt>Kind</dt>
                        <dd>{rule.kind}</dd>
                      </div>
                      <div>
                        <dt>Source</dt>
                        <dd>{rule.source ? rule.source.value : "Whole rule"}</dd>
                      </div>
                      <div>
                        <dt>Actual</dt>
                        <dd>{rule.actual}</dd>
                      </div>
                      <div>
                        <dt>Allowed</dt>
                        <dd>
                          {rule.minimum}–{rule.maximum}
                        </dd>
                      </div>
                      <div>
                        <dt>Outcome</dt>
                        <dd>{rule.outcome}</dd>
                      </div>
                    </dl>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      )}
    </section>
  );
}

export function V3ValuesValidation(props: Props) {
  return props.view === "values" ? <ValuesView {...props} /> : <ValidationView {...props} />;
}
