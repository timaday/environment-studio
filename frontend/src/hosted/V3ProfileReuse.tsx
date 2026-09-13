import { useEffect, useMemo, useState } from "react";
import { failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Api } from "../api/hostedV3";
import type { ProfileRevision } from "../api/hostedV3Profiles";
import type { PlanSummary } from "../api/hostedV3Types";
import { type PlacementChoice, ReusePlacement } from "./ReusePlacement";
import { ReusePlacementSummary } from "./ReusePlacementSummary";
import { ReuseProfileSource } from "./ReuseProfileSource";
import { ReuseRecovery } from "./ReuseRecovery";
import { useV3ProfileCatalog } from "./useV3ProfileCatalog";
import { type ReuseCommand, useV3ProfileReuse } from "./useV3ProfileReuse";
import { type ReuseInventoryItem, useV3ReuseInventory } from "./useV3ReuseInventory";
import { WorkflowPlanContext } from "./WorkflowPlanContext";
import "./V3ProfileCapture.css";
import "./V3ProfileReuse.css";

type Preview = NonNullable<ReturnType<typeof useV3ProfileReuse>["preview"]>;
type Applied = Readonly<{
  plan: PlanSummary;
  source: ProfileRevision;
  preview: Preview;
  decisions: ReuseCommand["decisions"];
  inventory: readonly ReuseInventoryItem[];
}>;
const pageSize = 20;
const portable = /^[a-z][a-z0-9.-]{0,63}$/;
export function V3ProfileReuse({
  api,
  plan,
  active,
  back,
  refreshPlan,
}: {
  api: HostedApi;
  plan: PlanSummary | null;
  active: boolean;
  back: () => void;
  refreshPlan: () => Promise<void>;
}) {
  const ready = Boolean(active && plan?.inspectionValid && plan.observedDestination?.evidenceValid);
  const catalog = useV3ProfileCatalog(api, ready);
  const inventory = useV3ReuseInventory(api, plan, ready);
  const reuse = useV3ProfileReuse(api, plan, active);
  const client = useMemo(() => new HostedV3Api(api), [api]);
  const [mode, setMode] = useState<"all" | "selected">("all");
  const [roots, setRoots] = useState<readonly string[]>([]);
  const [choices, setChoices] = useState<Record<string, PlacementChoice>>({});
  const [offset, setOffset] = useState(0);
  const [placementOffset, setPlacementOffset] = useState(0);
  const [submitted, setSubmitted] = useState<Applied | null>(null);
  const [fresh, setFresh] = useState<PlanSummary | null>(null);
  const [readError, setReadError] = useState("");
  const [preparing, setPreparing] = useState(false);
  const [preparationError, setPreparationError] = useState("");
  const context = JSON.stringify(plan);
  const loadOwner = useMemo(
    () => ({ api, context, enabled: active, active: false, reading: false }),
    [api, context, active],
  );
  useEffect(() => {
    loadOwner.active = loadOwner.enabled;
    setPreparing(false);
    setPreparationError("");
    return () => {
      loadOwner.active = false;
    };
  }, [loadOwner]);
  useEffect(() => {
    void active;
    void context;
    setMode("all");
    setRoots([]);
    setChoices({});
    setOffset(0);
    setPlacementOffset(0);
  }, [active, context]);
  const receipt = reuse.receipt;
  useEffect(() => {
    setFresh(null);
    setReadError("");
    if (!receipt || !active) return;
    let current = true;
    void client
      .summary(receipt.planId)
      .then((value) => {
        if (current) setFresh(value);
      })
      .catch((error) => {
        if (current) setReadError(failureMessage(error));
      });
    return () => {
      current = false;
    };
  }, [client, receipt, active]);
  useEffect(() => {
    if (!reuse.pending && !reuse.receipt && !reuse.busy) setSubmitted(null);
  }, [reuse.pending, reuse.receipt, reuse.busy]);
  const locked = reuse.busy || reuse.pending || Boolean(receipt);
  const busy = locked || catalog.busy || inventory.busy;
  const source = receipt || reuse.pending ? (submitted?.source ?? null) : catalog.selected;
  const model = source?.projection.model;
  const preview = reuse.preview;
  const selectedRoots =
    preview?.pins.selectedRoots ??
    (mode === "all" ? (model?.entities.map((e) => e.id) ?? []) : roots);
  function configure(nextMode = mode, nextRoots = roots) {
    if (!source) return;
    if (nextMode === "selected" && nextRoots.length === 0) {
      reuse.clearSelection();
      return;
    }
    reuse.configure({
      profile: { objectId: source.objectId, workspaceRevision: source.workspaceRevision },
      selection: nextMode === "all" ? { kind: "all" } : { kind: "selected", roots: nextRoots },
    });
  }
  async function load() {
    if (!ready || busy || !loadOwner.active || loadOwner.reading) return;
    loadOwner.reading = true;
    setRoots([]);
    setChoices({});
    setOffset(0);
    try {
      await catalog.load();
      if (!loadOwner.active) return;
      await inventory.load();
    } finally {
      loadOwner.reading = false;
    }
  }
  async function prepare() {
    if (!ready || !plan || busy || preparing || !loadOwner.active || loadOwner.reading) return;
    loadOwner.reading = true;
    setPreparing(true);
    setPreparationError("");
    try {
      const result = await client.materialize(plan.planId, { revision: plan.revision });
      if (!loadOwner.active) return;
      if (!result.complete)
        setPreparationError(
          `Target preview ${result.state.toLowerCase()}. ${result.diagnostics.join(", ")}`,
        );
      await refreshPlan();
    } catch (error) {
      if (loadOwner.active) setPreparationError(failureMessage(error));
    } finally {
      loadOwner.reading = false;
      if (loadOwner.active) setPreparing(false);
    }
  }
  const decisions = useMemo<ReuseCommand["decisions"] | null>(() => {
    if (!preview || !inventory.items) return null;
    const result: ReuseCommand["decisions"][number][] = [];
    for (const item of preview.included) {
      const choice = choices[item.slotId];
      if (choice?.kind === "cancel") result.push({ kind: "cancel", slotId: item.slotId });
      else if (choice?.kind === "create" && portable.test(choice.value))
        result.push({ kind: "create", slotId: item.slotId, targetSlotId: choice.value });
      else if (choice?.kind === "use-existing") {
        const target = inventory.items.find(
          (row) =>
            row.entity.kind === "existing" &&
            row.entity.handle === choice.value &&
            row.typeId === item.typeId,
        );
        if (!target) return null;
        result.push({ kind: "use-existing", slotId: item.slotId, target: target.entity });
      } else return null;
    }
    return result;
  }, [choices, preview, inventory.items]);
  function apply() {
    if (!plan || !source || !preview || !decisions || !inventory.items || busy) return;
    setSubmitted({ plan, source, preview, decisions, inventory: inventory.items });
    void reuse.apply(decisions);
  }
  const shownPlan = receipt || reuse.pending ? (fresh ?? submitted?.plan ?? null) : plan;
  const stage = receipt ? 3 : preview || reuse.pending ? 2 : 1;
  return (
    <section className="capture-workspace reuse-workspace" aria-label="Reuse profile workspace">
      <WorkflowPlanContext plan={shownPlan} label="Reuse plan context" />
      <p>
        <button type="button" className="text-button" onClick={back}>
          Plans
        </button>{" "}
        / Reuse profile structure
      </p>
      <h1>
        {receipt
          ? "Plan updated"
          : reuse.pending
            ? "Apply not confirmed"
            : "Reuse profile structure"}
      </h1>
      <p>
        {receipt
          ? "The reuse profile structure has been applied to this plan."
          : reuse.pending
            ? "The plan may already be updated. Confirm the same operation in this session."
            : "Choose reusable structure. Environment values are excluded."}
      </p>
      {reuse.pending && submitted && (
        <div className="reuse-recovery-context">
          <div className="reuse-recovery-notice" role={reuse.error ? "alert" : "status"}>
            <strong>
              {reuse.busy ? "Waiting for confirmation…" : "The response was unavailable."}
            </strong>
            <p>
              The plan may already be updated. Retry the same operation in this session to confirm
              its result.
            </p>
          </div>
          <p>
            Original plan revision {submitted.plan.revision}. Editing is unavailable until the
            result is known.
          </p>
          <p>Retry always uses the original plan and selections.</p>
        </div>
      )}
      <ol className="capture-steps" aria-label="Reuse progress">
        {["Select", "Review placement", "Applied"].map((name, i) => (
          <li key={name} aria-current={stage === i + 1 ? "step" : undefined}>
            <span>{i + 1}</span>
            {name}
          </li>
        ))}
      </ol>
      {!receipt && !reuse.pending && (!ready || !plan?.targetComplete) ? (
        <section className="reuse-panel reuse-prerequisite">
          <h2>{ready ? "Prepare the target preview" : "An inspected plan is required"}</h2>
          <p>
            {ready
              ? "Reuse needs a complete target preview. Prepare it before choosing a profile."
              : "Create or resume a plan and complete its inspection before reusing structure."}
          </p>
          {ready && (
            <>
              <p>
                If required values or structure are missing, return to the plan to complete them.
              </p>
              <button
                type="button"
                className="primary"
                disabled={preparing}
                onClick={() => void prepare()}
              >
                Prepare target preview
              </button>
            </>
          )}
          <button type="button" onClick={back}>
            Back to plan
          </button>
          {preparing && <p role="status">Preparing the target preview…</p>}
          {preparationError && <p role="alert">{preparationError}</p>}
        </section>
      ) : (
        <div className={`reuse-layout${receipt ? " reuse-applied" : ""}`}>
          <aside className="reuse-panel">
            <h2>
              {receipt ? "Source profile" : reuse.pending ? "Selected profile" : "Saved profiles"}
            </h2>
            {source && (receipt || reuse.pending) ? (
              <ReuseProfileSource source={source} />
            ) : (
              <>
                <button
                  type="button"
                  disabled={!ready || busy || Boolean(preview)}
                  onClick={() => void load()}
                >
                  Load profiles and inspected items
                </button>
                {catalog.profiles?.map((entry) => (
                  <button
                    key={entry.objectId}
                    type="button"
                    className="reuse-profile-choice"
                    aria-pressed={source?.objectId === entry.objectId}
                    disabled={busy || Boolean(preview)}
                    onClick={() => {
                      setRoots([]);
                      setMode("all");
                      setChoices({});
                      setOffset(0);
                      void catalog.select(entry.objectId);
                    }}
                  >
                    <strong>{entry.nativeId}</strong>
                    <span>
                      Profile revision {entry.nativeRevision} · Saved revision{" "}
                      {entry.workspaceRevision} ·{" "}
                      {entry.state === "published" ? "Published" : "Draft"}
                    </span>
                  </button>
                ))}
                {source && <ReuseProfileSource source={source} heading={false} />}
              </>
            )}
            <p className="reuse-source-help">
              Reusable identifiers travel with the profile; environment values are excluded.
            </p>
          </aside>
          {receipt && submitted ? (
            <section className="reuse-panel" aria-label="Applied placement">
              <h2>Reuse applied to plan revision {receipt.revision}</h2>
              <p>The selected structure is in this plan. Review target values before validation.</p>
              <p>
                {submitted.preview.pins.selectedRoots.length} selected ·{" "}
                {submitted.preview.dependencies.length} required{" "}
                {submitted.preview.dependencies.length === 1 ? "dependency" : "dependencies"}
              </p>
              <p>Environment values were not copied from the profile.</p>
              <ReusePlacementSummary
                preview={submitted.preview}
                decisions={submitted.decisions}
                inventory={submitted.inventory}
              />
              {fresh && (
                <div className="reuse-outcome">
                  <strong>{fresh.targetComplete ? "Target complete" : "Target incomplete"}</strong>
                  <h3>{fresh.exportAvailable ? "Export available" : "Export unavailable"}</h3>
                  <p>
                    {fresh.exportAvailable
                      ? "Return to the plan to review the current export decision."
                      : "The plan is not ready for export. Complete required target work and validation."}
                  </p>
                  {fresh.blockers.length > 0 && (
                    <p>Backend blockers: {fresh.blockers.join(", ")}</p>
                  )}
                </div>
              )}
              {!fresh && (
                <p role="status">
                  {readError
                    ? `Current plan status unavailable: ${readError}`
                    : "Reading current plan status…"}
                </p>
              )}
              <details>
                <summary>Receipt details</summary>
                <dl>
                  <dt>Original plan</dt>
                  <dd>{receipt.planId}</dd>
                  <dt>Original plan revision</dt>
                  <dd>{submitted.plan.revision}</dd>
                  {fresh && (
                    <>
                      <dt>Current plan revision</dt>
                      <dd>{fresh.revision}</dd>
                    </>
                  )}
                </dl>
              </details>
              <button type="button" className="primary" onClick={back}>
                Back to plan
              </button>
            </section>
          ) : reuse.pending && submitted ? (
            <ReuseRecovery
              preview={submitted.preview}
              decisions={submitted.decisions}
              inventory={submitted.inventory}
              busy={reuse.busy}
              retry={() => void reuse.retry()}
              back={back}
            />
          ) : (
            <>
              <section className="reuse-panel">
                <h2>Choose what to reuse</h2>
                {model && (
                  <>
                    <fieldset disabled={busy || Boolean(preview)} className="reuse-scope">
                      <legend>Choose reuse scope</legend>
                      {(["all", "selected"] as const).map((value) => (
                        <label key={value}>
                          <input
                            type="radio"
                            name="reuse-scope"
                            checked={mode === value}
                            onChange={() => {
                              setMode(value);
                              setRoots([]);
                              setChoices({});
                              configure(value, []);
                            }}
                          />
                          {value === "all" ? "Whole profile" : "Selected parts"}
                        </label>
                      ))}
                    </fieldset>
                    <p>{model.entities.length} items in this profile</p>
                    <ul className="reuse-items">
                      {model.entities.slice(offset, offset + pageSize).map((item) => {
                        const dependency = preview?.dependencies.find((d) => d.slotId === item.id);
                        return (
                          <li key={item.id}>
                            <label>
                              <input
                                type="checkbox"
                                checked={selectedRoots.includes(item.id) || Boolean(dependency)}
                                disabled={busy || mode === "all" || Boolean(preview)}
                                onChange={(e) => {
                                  const next = e.target.checked
                                    ? [...roots, item.id]
                                    : roots.filter((id) => id !== item.id);
                                  setRoots(next);
                                  setChoices({});
                                  configure(mode, next);
                                }}
                              />
                              <span>
                                <strong>{item.label}</strong>
                                <small>
                                  Reusable identifier: {item.id} · Type: {item.type}
                                </small>
                                <small>
                                  Required inputs:{" "}
                                  {item.requiredInputs.length
                                    ? item.requiredInputs.join(", ")
                                    : "None declared"}
                                </small>
                                {dependency && (
                                  <small>
                                    Required by{" "}
                                    {model.entities.find((e) => e.id === dependency.causedBy)
                                      ?.label ?? dependency.causedBy}{" "}
                                    · {dependency.relationId}
                                  </small>
                                )}
                              </span>
                            </label>
                          </li>
                        );
                      })}
                    </ul>
                    {model.entities.length > pageSize && (
                      <nav className="capture-page" aria-label="Profile item pages">
                        <button
                          type="button"
                          disabled={offset === 0 || busy}
                          onClick={() => setOffset(offset - pageSize)}
                        >
                          Previous profile items
                        </button>
                        <span>
                          Items {offset + 1}–{Math.min(offset + pageSize, model.entities.length)} of{" "}
                          {model.entities.length}
                        </span>
                        <button
                          type="button"
                          disabled={offset + pageSize >= model.entities.length || busy}
                          onClick={() => setOffset(offset + pageSize)}
                        >
                          Next profile items
                        </button>
                      </nav>
                    )}
                    {preview && (
                      <p>
                        {preview.pins.selectedRoots.length} selected · {preview.dependencies.length}{" "}
                        required {preview.dependencies.length === 1 ? "dependency" : "dependencies"}{" "}
                        · {preview.included.length} included
                      </p>
                    )}
                  </>
                )}
              </section>
              <section className="reuse-panel">
                <h2>{preview ? "Review placement" : "What happens next"}</h2>
                {preview ? (
                  <>
                    <p>Choose how each included item maps to the target plan.</p>
                    {preview.included
                      .slice(placementOffset, placementOffset + pageSize)
                      .map((item) => (
                        <ReusePlacement
                          key={item.slotId}
                          item={item}
                          inventory={inventory.items ?? []}
                          value={choices[item.slotId] ?? { kind: "", value: "" }}
                          busy={busy}
                          onChange={(choice) =>
                            setChoices((before) => ({ ...before, [item.slotId]: choice }))
                          }
                        />
                      ))}
                    {preview.included.length > pageSize && (
                      <nav className="capture-page" aria-label="Placement pages">
                        <button
                          type="button"
                          disabled={placementOffset === 0 || busy}
                          onClick={() => setPlacementOffset(placementOffset - pageSize)}
                        >
                          Previous placements
                        </button>
                        <span>
                          Items {placementOffset + 1}–
                          {Math.min(placementOffset + pageSize, preview.included.length)} of{" "}
                          {preview.included.length}
                        </span>
                        <button
                          type="button"
                          disabled={placementOffset + pageSize >= preview.included.length || busy}
                          onClick={() => setPlacementOffset(placementOffset + pageSize)}
                        >
                          Next placements
                        </button>
                      </nav>
                    )}
                    <p>
                      {preview.conflicts.length
                        ? `${preview.conflicts.length} preview conflicts`
                        : "No preview conflicts"}
                    </p>
                    {preview.conflicts.length > 0 && (
                      <pre>{JSON.stringify(preview.conflicts, null, 2)}</pre>
                    )}
                    {preview.relations.map((relation) => (
                      <p key={`${relation.relationId}:${relation.fromSlot}:${relation.toSlot}`}>
                        Required relation:{" "}
                        {model?.entities.find((e) => e.id === relation.fromSlot)?.label ??
                          relation.fromSlot}{" "}
                        →{" "}
                        {model?.entities.find((e) => e.id === relation.toSlot)?.label ??
                          relation.toSlot}{" "}
                        · {relation.relationId}
                      </p>
                    ))}
                    <details>
                      <summary>Affected computed groups</summary>
                      <p>
                        {preview.affectedDerivations.length
                          ? preview.affectedDerivations.join(", ")
                          : "None reported"}
                      </p>
                    </details>
                    <p>Environment values are excluded. Review target values after applying.</p>
                    <p>
                      {decisions
                        ? "Choices entered · Not applied"
                        : "Choose an action for every included item."}
                    </p>
                    <button
                      type="button"
                      className="primary"
                      disabled={!decisions || busy || preview.conflicts.length > 0}
                      onClick={apply}
                    >
                      Apply to target plan
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => {
                        setChoices({});
                        setPlacementOffset(0);
                        configure();
                      }}
                    >
                      Change selection
                    </button>
                  </>
                ) : (
                  <>
                    <p>
                      Preview the required dependencies, then choose where the selected structure
                      belongs.
                    </p>
                    <p>Preview has not been requested.</p>
                    <button
                      type="button"
                      className="primary"
                      disabled={
                        !ready ||
                        !source ||
                        source.state !== "published" ||
                        !inventory.items ||
                        busy ||
                        (mode === "selected" && !roots.length)
                      }
                      onClick={() => {
                        setChoices({});
                        setPlacementOffset(0);
                        configure();
                        void reuse.previewSelection();
                      }}
                    >
                      Preview selection
                    </button>
                  </>
                )}
                <button type="button" onClick={back}>
                  Back to plan
                </button>
              </section>
            </>
          )}
        </div>
      )}
      {!reuse.pending && (reuse.busy || catalog.busy || inventory.busy) && (
        <p role="status">Loading reuse information…</p>
      )}
      {!reuse.pending && (catalog.error || inventory.error || reuse.error) && (
        <p role="alert">{catalog.error || inventory.error || reuse.error}</p>
      )}
      <details className="reuse-help">
        <summary>Reuse help</summary>
        <p>
          Choose a whole published profile or selected parts. Preview includes required
          dependencies. Applying changes the target plan; it does not provision a host, execute SQL
          or publish a profile. Unselected structure is preserved.
        </p>
      </details>
    </section>
  );
}
