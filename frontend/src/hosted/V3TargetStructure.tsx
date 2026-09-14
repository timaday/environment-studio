import { useCallback, useEffect, useMemo, useState } from "react";
import { failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Definitions } from "../api/hostedV3Definitions";
import { HostedV3Physical } from "../api/hostedV3Physical";
import type { PlanSummary } from "../api/hostedV3Types";
import { type TargetCommandInput, useV3TargetCommands } from "./useV3TargetCommands";
import { WorkflowPlanContext } from "./WorkflowPlanContext";
import "./V3TargetStructure.css";

type Definition = Awaited<ReturnType<HostedV3Definitions["definitionRevision"]>>;
type Model = Definition["projection"]["model"];
type EntityType = Model["logical"]["entityTypes"][number];
type Relation = Model["logical"]["relations"][number];
type Binding = Model["bindings"][number];
type Projection = Binding["documents"][number]["entities"][number];
type EntityPage = Awaited<ReturnType<HostedV3Physical["entities"]>>["response"];
type EntityItem = EntityPage["items"][number];
type DraftPage = Awaited<ReturnType<HostedV3Physical["draft"]>>["response"];
type PlacementPage = Awaited<ReturnType<HostedV3Physical["placements"]>>["response"];
type EntityRef =
  | EntityItem["entity"]
  | { readonly kind: "fresh"; readonly slotId: string; readonly typeId: string };
type FieldMode = "keep-observed" | "entered" | "unresolved" | "absent";
type ReferenceMode = "keep-observed" | "to" | "unresolved" | "absent";
type FieldCommand =
  | { readonly kind: "keep-observed" }
  | { readonly kind: "entered"; readonly text: string }
  | { readonly kind: "unresolved" }
  | { readonly kind: "absent" };
type ReferenceCommand =
  | { readonly kind: "keep-observed" }
  | { readonly kind: "to"; readonly target: EntityRef }
  | { readonly kind: "unresolved" }
  | { readonly kind: "absent" };
type PlacementChoice = PlacementPage["items"][number];

type LoadState = Readonly<{
  busy: boolean;
  definition: Definition | null;
  current: EntityPage | null;
  draft: DraftPage | null;
  error: string;
}>;
const emptyLoad: LoadState = {
  busy: false,
  definition: null,
  current: null,
  draft: null,
  error: "",
};
const pageSize = 20;
const slotPattern = /^[a-z][a-z0-9.-]{0,63}$/;

function entityKey(entity: EntityRef): string {
  return entity.kind === "existing" ? `existing:${entity.handle}` : `fresh:${entity.slotId}`;
}
function fieldValue(item: EntityItem, id: string): string | null {
  return item.fields.find((field) => field.fieldId === id)?.value ?? null;
}
function entityType(model: Model | null, typeId: string): EntityType | null {
  return model?.logical.entityTypes.find((type) => type.id === typeId) ?? null;
}
function identityLabel(model: Model | null, item: EntityItem): string {
  const type = entityType(model, item.typeId);
  const identity = type ? fieldValue(item, type.identity.field) : null;
  const fallback = item.entity.kind === "existing" ? item.entity.handle : item.entity.slotId;
  return identity
    ? `${type?.label ?? item.typeId} · ${identity}`
    : `${type?.label ?? item.typeId} · ${fallback}`;
}
function relationTargets(model: Model | null, typeId: string): readonly Relation[] {
  return (
    model?.logical.relations.filter(
      (relation) => relation.kind === "reference" && relation.fromType === typeId,
    ) ?? []
  );
}
function projectionsFor(
  model: Model | null,
  bindingId: string,
  typeId: string,
): readonly (Projection & { readonly documentId: string })[] {
  const binding = model?.bindings.find((item) => item.id === bindingId);
  return (
    binding?.documents.flatMap((document) =>
      document.entities
        .filter((projection) => projection.type === typeId)
        .map((projection) => ({ ...projection, documentId: document.id })),
    ) ?? []
  );
}
function defaultFieldMode(
  field: EntityType["fields"][number],
  current?: EntityItem["fields"][number],
): FieldMode {
  if (current?.present === false && field.required) return "unresolved";
  return "keep-observed";
}
function fieldCommand(mode: FieldMode, text: string): FieldCommand {
  if (mode === "entered") return { kind: "entered", text };
  if (mode === "absent") return { kind: "absent" };
  if (mode === "unresolved") return { kind: "unresolved" };
  return { kind: "keep-observed" };
}
function referenceCommand(mode: ReferenceMode, target: EntityRef | null): ReferenceCommand | null {
  if (mode === "to") return target ? { kind: "to", target } : null;
  if (mode === "absent") return { kind: "absent" };
  if (mode === "unresolved") return { kind: "unresolved" };
  return { kind: "keep-observed" };
}
function freshRef(slotId: string, typeId: string): EntityRef {
  return { kind: "fresh", slotId, typeId };
}
function placementKey(item: PlacementChoice): string {
  return `${item.documentId}:${item.sourceDigest}:${item.elementIndex}`;
}
function commandLabel(mode: FieldMode | ReferenceMode): string {
  return mode === "keep-observed"
    ? "Keep observed"
    : mode === "entered"
      ? "Enter value"
      : mode === "to"
        ? "Choose target"
        : mode === "absent"
          ? "Set absent"
          : "Leave unresolved";
}

export function V3TargetStructure({
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
  refreshPlan: () => Promise<void> | void;
}) {
  const definitions = useMemo(() => new HostedV3Definitions(api), [api]);
  const physical = useMemo(() => new HostedV3Physical(api), [api]);
  const commands = useV3TargetCommands(api, plan, active);
  const [load, setLoad] = useState<LoadState>(emptyLoad);
  const [currentOffset, setCurrentOffset] = useState(0);
  const [draftOffset, setDraftOffset] = useState(0);
  const [selectedKey, setSelectedKey] = useState("");
  const [mode, setMode] = useState<"retain" | "create">("retain");
  const [fieldModes, setFieldModes] = useState<Record<string, FieldMode>>({});
  const [fieldTexts, setFieldTexts] = useState<Record<string, string>>({});
  const [referenceModes, setReferenceModes] = useState<Record<string, ReferenceMode>>({});
  const [referenceTargets, setReferenceTargets] = useState<Record<string, string>>({});
  const [createType, setCreateType] = useState("");
  const [slotId, setSlotId] = useState("new-item");
  const [projectionIndex, setProjectionIndex] = useState(0);
  const [placements, setPlacements] = useState<PlacementPage | null>(null);
  const [placementOffset, setPlacementOffset] = useState(0);
  const [placementSelection, setPlacementSelection] = useState("");
  const [placementError, setPlacementError] = useState("");
  const [lastRevision, setLastRevision] = useState<string | null>(null);
  const eligible = Boolean(
    active && plan?.inspectionValid && plan.observedDestination?.evidenceValid,
  );
  const model = load.definition?.projection.model ?? null;
  const selected =
    load.current?.items.find((item) => entityKey(item.entity) === selectedKey) ??
    load.current?.items[0] ??
    null;
  const selectedType = selected ? entityType(model, selected.typeId) : null;
  const effectiveCreateType = createType || model?.logical.entityTypes[0]?.id || "";
  const createEntityType = entityType(model, effectiveCreateType);
  const createRef = effectiveCreateType ? freshRef(slotId, effectiveCreateType) : null;
  const createProjections = projectionsFor(model, plan?.bindingId ?? "", effectiveCreateType);
  const selectedProjection = createProjections[projectionIndex] ?? createProjections[0] ?? null;
  const currentRevision = lastRevision ?? plan?.revision ?? "";

  const loadDraft = useCallback(
    async (revision: string, offset = draftOffset) => {
      if (!plan || !eligible || !revision) return null;
      const result = await physical.draft(plan.planId, { revision, offset, limit: pageSize });
      setLoad((current) => ({ ...current, draft: result.response }));
      return result.response;
    },
    [draftOffset, eligible, physical, plan],
  );

  const loadAll = useCallback(
    async (offset = currentOffset) => {
      if (!plan || !eligible) return;
      setLoad((current) => ({ ...current, busy: true, error: "" }));
      try {
        const [definition, current] = await Promise.all([
          definitions.definitionRevision(
            plan.definition.objectId,
            plan.definition.workspaceRevision,
          ),
          physical.entities(plan.planId, {
            revision: plan.revision,
            side: "current",
            offset,
            limit: pageSize,
          }),
        ]);
        const draft = await physical
          .draft(plan.planId, { revision: plan.revision, offset: draftOffset, limit: pageSize })
          .catch(() => null);
        setLoad({
          busy: false,
          definition,
          current: current.response,
          draft: draft?.response ?? null,
          error: "",
        });
        setCurrentOffset(offset);
        setLastRevision(plan.revision);
        setSelectedKey((key) =>
          current.response.items.some((item) => entityKey(item.entity) === key)
            ? key
            : entityKey(
                current.response.items[0]?.entity ?? { kind: "fresh", slotId: "", typeId: "" },
              ),
        );
        setCreateType(
          (value) => value || definition.projection.model.logical.entityTypes[0]?.id || "",
        );
      } catch (error) {
        setLoad((current) => ({ ...current, busy: false, error: failureMessage(error) }));
      }
    },
    [currentOffset, definitions, draftOffset, eligible, physical, plan],
  );

  useEffect(() => {
    setLoad(emptyLoad);
    setSelectedKey("");
    setLastRevision(null);
    setPlacements(null);
    setPlacementSelection("");
    if (eligible) void loadAll(0);
  }, [eligible, loadAll]);

  async function afterReceipt(revision: string) {
    setLastRevision(revision);
    await loadDraft(revision, draftOffset);
  }
  async function returnToPlan() {
    await refreshPlan();
    back();
  }
  async function submit(input: TargetCommandInput) {
    const receipt = await commands.submit(input);
    if (receipt) await afterReceipt(receipt.revision);
  }
  function fieldMode(
    scope: string,
    field: EntityType["fields"][number],
    item?: EntityItem,
  ): FieldMode {
    return (
      fieldModes[`${scope}:${field.id}`] ??
      defaultFieldMode(
        field,
        item?.fields.find((row) => row.fieldId === field.id),
      )
    );
  }
  function fieldText(scope: string, fieldId: string): string {
    return fieldTexts[`${scope}:${fieldId}`] ?? "";
  }
  function referenceMode(scope: string, relationId: string, initial: ReferenceMode): ReferenceMode {
    return referenceModes[`${scope}:${relationId}`] ?? initial;
  }
  function targetFor(scope: string, relation: Relation): EntityRef | null {
    const key = referenceTargets[`${scope}:${relation.id}`];
    const candidates = load.current?.items.filter((item) => item.typeId === relation.toType) ?? [];
    return (
      candidates.find((item) => entityKey(item.entity) === key)?.entity ??
      candidates[0]?.entity ??
      null
    );
  }
  function buildFields(scope: string, type: EntityType, item?: EntityItem) {
    return Object.fromEntries(
      type.fields.map((field) => [
        field.id,
        fieldCommand(fieldMode(scope, field, item), fieldText(scope, field.id)),
      ]),
    );
  }
  function buildReferences(scope: string, typeId: string, initial: ReferenceMode) {
    const entries: [string, ReferenceCommand][] = [];
    for (const relation of relationTargets(model, typeId)) {
      const value = referenceCommand(
        referenceMode(scope, relation.id, initial),
        targetFor(scope, relation),
      );
      if (!value) return null;
      entries.push([relation.id, value]);
    }
    return Object.fromEntries(entries);
  }
  function retain() {
    if (!selected || !selectedType) return;
    const scope = entityKey(selected.entity);
    const references = buildReferences(scope, selected.typeId, "keep-observed");
    if (!references) return;
    void submit({
      kind: "upsert-entity",
      decision: {
        kind: "retain",
        entity: selected.entity,
        fields: buildFields(scope, selectedType, selected),
        references,
      },
      placements: [],
    } as TargetCommandInput);
  }
  function remove() {
    if (!selected) return;
    void submit({
      kind: "upsert-entity",
      decision: { kind: "remove", entity: selected.entity },
      placements: [],
    } as TargetCommandInput);
  }
  function forget(entity: EntityRef) {
    void submit({ kind: "forget-entity-decision", entity } as TargetCommandInput);
  }
  async function loadPlacements(offset = placementOffset) {
    if (!plan || !eligible || !selectedProjection) return;
    setPlacementError("");
    try {
      const result = await physical.placements(plan.planId, {
        revision: plan.revision,
        documentId: selectedProjection.documentId,
        projectionId: selectedProjection.id,
        offset,
        limit: pageSize,
      });
      setPlacements(result.response);
      setPlacementOffset(offset);
      setPlacementSelection((value) =>
        result.response.items.some((item) => placementKey(item) === value)
          ? value
          : placementKey(
              result.response.items[0] ?? { documentId: "", sourceDigest: "", elementIndex: "" },
            ),
      );
    } catch (error) {
      setPlacementError(failureMessage(error));
    }
  }
  function create() {
    if (!createEntityType || !createRef || !slotPattern.test(slotId)) return;
    const references = buildReferences(entityKey(createRef), effectiveCreateType, "unresolved");
    if (!references) return;
    const placement =
      placements?.items.find((item) => placementKey(item) === placementSelection) ?? null;
    void submit({
      kind: "upsert-entity",
      decision: {
        kind: "create",
        entity: createRef,
        fields: buildFields(entityKey(createRef), createEntityType),
        references,
      },
      placements:
        selectedProjection && placement
          ? [
              {
                entity: createRef,
                documentId: selectedProjection.documentId,
                projectionId: selectedProjection.id,
                parent: {
                  kind: "existing",
                  documentId: placement.documentId,
                  sourceDigest: placement.sourceDigest,
                  elementIndex: placement.elementIndex,
                },
              },
            ]
          : [],
    } as TargetCommandInput);
  }
  const busy = load.busy || commands.busy;
  return (
    <section
      className="capture-workspace target-structure-workspace"
      aria-label="Target structure workspace"
    >
      <WorkflowPlanContext plan={plan} label="Target structure plan context" />
      <header className="target-structure-header">
        <div>
          <button type="button" onClick={() => void returnToPlan()}>
            Back to plan
          </button>
          <h1>Model target structure</h1>
          <p>
            Choose explicit target structure. Environment values can stay unresolved until the
            Values step.
          </p>
        </div>
        <button
          type="button"
          onClick={() => void loadAll(currentOffset)}
          disabled={!eligible || busy}
        >
          Refresh returned inventory
        </button>
      </header>
      {!eligible && (
        <section className="target-panel" role="status">
          <h2>Inspection required</h2>
          <p>
            Publish a definition and complete read-only PostgreSQL inspection before editing target
            structure.
          </p>
        </section>
      )}
      {load.error && <p role="alert">{load.error}</p>}
      {commands.error && !commands.pending && <p role="alert">{commands.error}</p>}
      {commands.pending && (
        <section className="target-panel target-warning" aria-label="Structure save not confirmed">
          <h2>Save not confirmed</h2>
          <p>
            The original structural command is preserved. Retry sends the same request for the same
            plan revision.
          </p>
          <button
            type="button"
            onClick={() =>
              void commands.retry().then((receipt) => receipt && afterReceipt(receipt.revision))
            }
            disabled={commands.busy}
          >
            Retry original structural command
          </button>
        </section>
      )}
      {commands.receipt && (
        <p role="status">
          Structure saved for revision {commands.receipt.revision}. Draft readback is shown below.
        </p>
      )}
      <div className="target-layout">
        <section className="target-panel target-inventory" aria-label="Inspected current inventory">
          <h2>Current inventory</h2>
          <p>
            {load.current
              ? `${load.current.total} returned items · page starts at ${load.current.offset + 1}`
              : "Load the inspected inventory."}
          </p>
          <div role="tablist" aria-label="Target structure mode" className="target-tabs">
            <button
              type="button"
              role="tab"
              aria-selected={mode === "retain"}
              onClick={() => setMode("retain")}
            >
              Retain or remove current item
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === "create"}
              onClick={() => setMode("create")}
            >
              Create new target item
            </button>
          </div>
          {load.current?.items.map((item) => (
            <button
              key={entityKey(item.entity)}
              type="button"
              className="target-inventory-row"
              aria-pressed={selectedKey === entityKey(item.entity)}
              onClick={() => {
                setSelectedKey(entityKey(item.entity));
                setMode("retain");
              }}
            >
              <strong>{identityLabel(model, item)}</strong>
              <span>{item.typeId}</span>
            </button>
          ))}
          {load.current && <CurrentPageNav busy={busy} page={load.current} loadAll={loadAll} />}
        </section>
        <section className="target-panel target-editor" aria-label="Target structure editor">
          {mode === "retain" ? (
            selected && selectedType ? (
              <>
                <h2>Retain or remove current item</h2>
                <p>{identityLabel(model, selected)}</p>
                <div className="target-field-grid">
                  {selectedType.fields.map((field) => {
                    const scope = entityKey(selected.entity);
                    const current = selected.fields.find((row) => row.fieldId === field.id);
                    const selectedMode = fieldMode(scope, field, selected);
                    return (
                      <fieldset key={field.id}>
                        <legend>{field.id}</legend>
                        <p>
                          {field.classification} · {field.required ? "required" : "optional"} ·
                          current {current?.present ? (current.value ?? "masked") : "absent"}
                        </p>
                        <label>
                          Target state for {field.id}
                          <select
                            value={selectedMode}
                            onChange={(event) =>
                              setFieldModes((values) => ({
                                ...values,
                                [`${scope}:${field.id}`]: event.target.value as FieldMode,
                              }))
                            }
                          >
                            {(["keep-observed", "entered", "unresolved", "absent"] as const).map(
                              (value) => (
                                <option key={value} value={value}>
                                  {commandLabel(value)}
                                </option>
                              ),
                            )}
                          </select>
                        </label>
                        {selectedMode === "entered" && (
                          <label>
                            Entered value for {field.id}
                            <input
                              value={fieldText(scope, field.id)}
                              onChange={(event) =>
                                setFieldTexts((values) => ({
                                  ...values,
                                  [`${scope}:${field.id}`]: event.target.value,
                                }))
                              }
                            />
                          </label>
                        )}
                      </fieldset>
                    );
                  })}
                </div>
                <div className="target-field-grid">
                  {relationTargets(model, selected.typeId).map((relation) => {
                    const scope = entityKey(selected.entity);
                    const selectedMode = referenceMode(scope, relation.id, "keep-observed");
                    const candidates =
                      load.current?.items.filter((item) => item.typeId === relation.toType) ?? [];
                    return (
                      <fieldset key={relation.id}>
                        <legend>{relation.id}</legend>
                        <p>
                          Reference to {relation.toType} · {relation.minimum}..{relation.maximum}
                        </p>
                        <label>
                          Target reference for {relation.id}
                          <select
                            value={selectedMode}
                            onChange={(event) =>
                              setReferenceModes((values) => ({
                                ...values,
                                [`${scope}:${relation.id}`]: event.target.value as ReferenceMode,
                              }))
                            }
                          >
                            {(["keep-observed", "to", "unresolved", "absent"] as const).map(
                              (value) => (
                                <option key={value} value={value}>
                                  {commandLabel(value)}
                                </option>
                              ),
                            )}
                          </select>
                        </label>
                        {selectedMode === "to" && (
                          <label>
                            Referenced target item
                            <select
                              value={
                                referenceTargets[`${scope}:${relation.id}`] ??
                                entityKey(
                                  candidates[0]?.entity ?? {
                                    kind: "fresh",
                                    slotId: "",
                                    typeId: "",
                                  },
                                )
                              }
                              onChange={(event) =>
                                setReferenceTargets((values) => ({
                                  ...values,
                                  [`${scope}:${relation.id}`]: event.target.value,
                                }))
                              }
                            >
                              {candidates.map((item) => (
                                <option key={entityKey(item.entity)} value={entityKey(item.entity)}>
                                  {identityLabel(model, item)}
                                </option>
                              ))}
                            </select>
                          </label>
                        )}
                      </fieldset>
                    );
                  })}
                </div>
                <div className="target-actions">
                  <button className="primary" type="button" onClick={retain} disabled={busy}>
                    Save retained structure
                  </button>
                  <button type="button" onClick={remove} disabled={busy}>
                    Remove from target
                  </button>
                </div>
              </>
            ) : (
              <p>Select a current item to edit its target structure.</p>
            )
          ) : createEntityType ? (
            <>
              <h2>Create new target item</h2>
              <p>
                Fresh items use portable slot IDs. Exact database keys remain owned by the package
                and destination checks.
              </p>
              <div className="target-create-grid">
                <label>
                  Target type
                  <select
                    value={effectiveCreateType}
                    onChange={(event) => {
                      setCreateType(event.target.value);
                      setProjectionIndex(0);
                      setPlacements(null);
                      setPlacementSelection("");
                    }}
                  >
                    {model?.logical.entityTypes.map((type) => (
                      <option key={type.id} value={type.id}>
                        {type.label} · {type.id}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Portable slot ID
                  <input
                    value={slotId}
                    onChange={(event) => setSlotId(event.target.value)}
                    aria-invalid={!slotPattern.test(slotId)}
                  />
                </label>
              </div>
              {!slotPattern.test(slotId) && (
                <p role="alert">
                  Use a portable ID starting with a lowercase letter, followed by lowercase letters,
                  numbers, dots or hyphens.
                </p>
              )}
              <div className="target-field-grid">
                {createEntityType.fields.map((field) => {
                  const scope = createRef ? entityKey(createRef) : "fresh";
                  const selectedMode =
                    fieldModes[`${scope}:${field.id}`] ??
                    (field.required && field.classification === "structural"
                      ? "entered"
                      : "unresolved");
                  return (
                    <fieldset key={field.id}>
                      <legend>{field.id}</legend>
                      <p>
                        {field.classification} · {field.required ? "required" : "optional"}
                      </p>
                      <label>
                        Target state for {field.id}
                        <select
                          value={selectedMode}
                          onChange={(event) =>
                            setFieldModes((values) => ({
                              ...values,
                              [`${scope}:${field.id}`]: event.target.value as FieldMode,
                            }))
                          }
                        >
                          {(["entered", "unresolved", "absent"] as const).map((value) => (
                            <option key={value} value={value}>
                              {commandLabel(value)}
                            </option>
                          ))}
                        </select>
                      </label>
                      {selectedMode === "entered" && (
                        <label>
                          Entered value for {field.id}
                          <input
                            value={fieldText(scope, field.id)}
                            onChange={(event) =>
                              setFieldTexts((values) => ({
                                ...values,
                                [`${scope}:${field.id}`]: event.target.value,
                              }))
                            }
                          />
                        </label>
                      )}
                    </fieldset>
                  );
                })}
              </div>
              <div className="target-field-grid">
                {relationTargets(model, effectiveCreateType).map((relation) => {
                  const scope = createRef ? entityKey(createRef) : "fresh";
                  const selectedMode = referenceMode(scope, relation.id, "unresolved");
                  const candidates =
                    load.current?.items.filter((item) => item.typeId === relation.toType) ?? [];
                  return (
                    <fieldset key={relation.id}>
                      <legend>{relation.id}</legend>
                      <p>Reference to {relation.toType}</p>
                      <label>
                        Target reference for {relation.id}
                        <select
                          value={selectedMode}
                          onChange={(event) =>
                            setReferenceModes((values) => ({
                              ...values,
                              [`${scope}:${relation.id}`]: event.target.value as ReferenceMode,
                            }))
                          }
                        >
                          {(["unresolved", "to", "absent"] as const).map((value) => (
                            <option key={value} value={value}>
                              {commandLabel(value)}
                            </option>
                          ))}
                        </select>
                      </label>
                      {selectedMode === "to" && (
                        <label>
                          Referenced target item
                          <select
                            value={
                              referenceTargets[`${scope}:${relation.id}`] ??
                              entityKey(
                                candidates[0]?.entity ?? { kind: "fresh", slotId: "", typeId: "" },
                              )
                            }
                            onChange={(event) =>
                              setReferenceTargets((values) => ({
                                ...values,
                                [`${scope}:${relation.id}`]: event.target.value,
                              }))
                            }
                          >
                            {candidates.map((item) => (
                              <option key={entityKey(item.entity)} value={entityKey(item.entity)}>
                                {identityLabel(model, item)}
                              </option>
                            ))}
                          </select>
                        </label>
                      )}
                    </fieldset>
                  );
                })}
              </div>
              <section className="target-placement" aria-label="Placement selection">
                <h3>Placement</h3>
                {createProjections.length ? (
                  <>
                    <label>
                      Projection
                      <select
                        value={String(projectionIndex)}
                        onChange={(event) => {
                          setProjectionIndex(Number(event.target.value));
                          setPlacements(null);
                          setPlacementSelection("");
                        }}
                      >
                        {createProjections.map((projection, index) => (
                          <option
                            key={`${projection.documentId}:${projection.id}`}
                            value={String(index)}
                          >
                            {projection.documentId} / {projection.id}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button
                      type="button"
                      onClick={() => void loadPlacements(0)}
                      disabled={busy || !selectedProjection}
                    >
                      Load placement choices
                    </button>
                    {placementError && <p role="alert">{placementError}</p>}
                    {placements && (
                      <>
                        <label>
                          Parent placement
                          <select
                            value={placementSelection}
                            onChange={(event) => setPlacementSelection(event.target.value)}
                          >
                            {placements.items.map((item) => (
                              <option key={placementKey(item)} value={placementKey(item)}>
                                {item.documentId} · parent element {item.elementIndex}
                              </option>
                            ))}
                          </select>
                        </label>
                        <nav className="target-page" aria-label="Placement pages">
                          <button
                            type="button"
                            disabled={placements.offset === 0}
                            onClick={() =>
                              void loadPlacements(Math.max(0, placements.offset - pageSize))
                            }
                          >
                            Previous placements
                          </button>
                          <button
                            type="button"
                            disabled={placements.nextOffset === null}
                            onClick={() => void loadPlacements(placements.nextOffset ?? 0)}
                          >
                            Next placements
                          </button>
                        </nav>
                      </>
                    )}
                  </>
                ) : (
                  <p>No declared projection creates this type in the selected binding.</p>
                )}
              </section>
              <div className="target-actions">
                <button
                  className="primary"
                  type="button"
                  onClick={create}
                  disabled={busy || !slotPattern.test(slotId)}
                >
                  Save new item
                </button>
              </div>
            </>
          ) : (
            <p>Load a definition before creating new items.</p>
          )}
        </section>
        <section className="target-panel target-review" aria-label="Saved target draft">
          <h2>Saved target draft</h2>
          <p>
            {load.draft
              ? `${load.draft.total} decisions · revision ${load.draft.revision}`
              : "No target decisions read back yet."}
          </p>
          {load.draft?.items.map((item) => (
            <article key={entityKey(item.entity)}>
              <div>
                <strong>{item.disposition}</strong>
                <code>{entityKey(item.entity)}</code>
              </div>
              <p>
                {item.fields.length} fields · {item.references.length} references ·{" "}
                {item.placements.length} placements
              </p>
              <button type="button" onClick={() => forget(item.entity)} disabled={busy}>
                Forget this decision
              </button>
            </article>
          ))}
          {load.draft && (
            <DraftPageNav
              busy={busy}
              page={load.draft}
              currentRevision={currentRevision}
              loadDraft={loadDraft}
              setDraftOffset={setDraftOffset}
            />
          )}
        </section>
      </div>
    </section>
  );
}

function CurrentPageNav({
  busy,
  page,
  loadAll,
}: {
  busy: boolean;
  page: EntityPage;
  loadAll: (offset?: number) => Promise<void>;
}) {
  return (
    <nav className="target-page" aria-label="Current inventory pages">
      <button
        type="button"
        disabled={busy || page.offset === 0}
        onClick={() => void loadAll(Math.max(0, page.offset - pageSize))}
      >
        Previous current page
      </button>
      <button
        type="button"
        disabled={busy || page.nextOffset === null}
        onClick={() => void loadAll(page.nextOffset ?? 0)}
      >
        Next current page
      </button>
    </nav>
  );
}

function DraftPageNav({
  busy,
  page,
  currentRevision,
  loadDraft,
  setDraftOffset,
}: {
  busy: boolean;
  page: DraftPage;
  currentRevision: string;
  loadDraft: (revision: string, offset?: number) => Promise<DraftPage | null>;
  setDraftOffset: (offset: number) => void;
}) {
  return (
    <nav className="target-page" aria-label="Saved draft pages">
      <button
        type="button"
        disabled={busy || page.offset === 0}
        onClick={() => {
          const next = Math.max(0, page.offset - pageSize);
          setDraftOffset(next);
          void loadDraft(currentRevision, next);
        }}
      >
        Previous draft page
      </button>
      <button
        type="button"
        disabled={busy || page.nextOffset === null}
        onClick={() => {
          const next = page.nextOffset ?? 0;
          setDraftOffset(next);
          void loadDraft(currentRevision, next);
        }}
      >
        Next draft page
      </button>
    </nav>
  );
}
