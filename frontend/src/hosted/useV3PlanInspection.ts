import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ApiFailure,
  type Destination,
  definitiveRefusal,
  failureMessage,
  type HostedApi,
} from "../api/hosted";
import { HostedV3Api } from "../api/hostedV3";
import { type DefinitionRevision, HostedV3Definitions } from "../api/hostedV3Definitions";
import { HostedV3Physical } from "../api/hostedV3Physical";
import type { CreatePlan, PlanSummary } from "../api/hostedV3Types";

type Inventory = Awaited<ReturnType<HostedV3Physical["documents"]>>["response"];
type Document = Awaited<ReturnType<HostedV3Physical["document"]>>["response"];
type EntityPage = Awaited<ReturnType<HostedV3Physical["entities"]>>["response"];
type BindingPage = Awaited<ReturnType<HostedV3Physical["bindings"]>>["response"];
type LocationPage = Awaited<ReturnType<HostedV3Physical["bindingLocations"]>>["response"];
type BindingLocation = LocationPage["items"][number];
type DocumentMode = "raw" | "placeholders" | "formatted";
export type BindingRailItem = Readonly<{
  entity: EntityPage["items"][number]["entity"];
  typeId: string;
  fieldId: string;
  token: string;
  change: BindingPage["items"][number]["change"];
  current: string;
  target: string;
  currentLocations: number;
  targetLocations: number;
  currentTotalLocations: number;
  targetTotalLocations: number;
  currentDocumentLocations: readonly BindingLocation[];
  targetDocumentLocations: readonly BindingLocation[];
}>;
type State = Readonly<{
  phase: "idle" | "loading" | "absent" | "loaded" | "error";
  plan: PlanSummary | null;
  definitions:
    | readonly {
        readonly objectId: string;
        readonly workspaceRevision: string;
        readonly nativeId: string;
        readonly nativeRevision: string;
        readonly state: "draft" | "published";
        readonly compilationKind: "incomplete" | "historical-ready";
        readonly logicalDigest: string;
      }[]
    | null;
  definition: DefinitionRevision | null;
  destinations: readonly Destination[] | null;
  binding: string;
  destination: string;
  connectionUrl: string;
  addingDestination: boolean;
  creating: boolean;
  pendingDestination: { requestId: string; jdbcUrl: string } | null;
  pendingCreate: CreatePlan | null;
  inventory: Inventory | null;
  selected: string;
  mode: DocumentMode;
  consent: boolean;
  reading: boolean;
  refreshing: boolean;
  current: Document | null;
  target: Document | null;
  bindingRail: readonly BindingRailItem[];
  error: string;
}>;
const empty: State = {
  phase: "idle",
  plan: null,
  definitions: null,
  definition: null,
  destinations: null,
  binding: "",
  destination: "",
  connectionUrl: "",
  addingDestination: false,
  creating: false,
  pendingDestination: null,
  pendingCreate: null,
  inventory: null,
  selected: "",
  mode: "raw",
  consent: false,
  reading: false,
  refreshing: false,
  current: null,
  target: null,
  bindingRail: [],
  error: "",
};

/** Read-only state; complete document authority remains with each server request. */
export function useV3PlanInspection(api: HostedApi, enabled: boolean, definitionVersion = 0) {
  const client = useMemo(() => new HostedV3Api(api), [api]);
  const physical = useMemo(() => new HostedV3Physical(api), [api]);
  const definitions = useMemo(() => new HostedV3Definitions(api), [api]);
  const owner = useMemo(
    () => ({ client, physical, definitions, api, enabled, active: false, generation: 0 }),
    [client, physical, definitions, api, enabled],
  );
  const [state, setState] = useState<State>(empty);
  const latest = useRef(state);
  const replace = useCallback((next: State) => {
    latest.current = next;
    setState(next);
  }, []);
  const current = useCallback(
    (token: number) => owner.active && owner.generation === token,
    [owner],
  );
  const verify = useCallback(
    async (plan: PlanSummary) => {
      const fresh = await client.summary(plan.planId);
      if (JSON.stringify(fresh) !== JSON.stringify(plan)) throw new ApiFailure(409, "CONFLICT");
    },
    [client],
  );
  const loadCreateInputs = useCallback(
    async (token: number) => {
      const [definitionList, destinationList] = await Promise.all([
        definitions.definitions(),
        owner.api.get<{ destinations: Destination[] }>("/api/v1/destinations"),
      ]);
      if (!current(token)) return;
      replace({
        ...empty,
        phase: "absent",
        definitions: definitionList.definitions,
        destinations: destinationList.destinations,
      });
    },
    [definitions, owner.api, current, replace],
  );
  const loadPlan = useCallback(
    async (token: number, plan: PlanSummary) => {
      const inventory =
        plan.observedDestination === null
          ? null
          : (await physical.documents(plan.planId, { revision: plan.revision })).response;
      if (!current(token)) return;
      await verify(plan);
      if (!current(token)) return;
      const prior = latest.current;
      const selectedStillAvailable = prior.selected
        ? inventory?.documents.some((document) => document.documentId === prior.selected) === true
        : false;
      const loadedPairStillCurrent =
        selectedStillAvailable &&
        prior.plan?.planId === plan.planId &&
        prior.plan.revision === plan.revision &&
        prior.current?.documentId === prior.selected &&
        prior.current.revision === plan.revision &&
        prior.current.mode === prior.mode &&
        (prior.target === null ||
          (prior.target.documentId === prior.selected &&
            prior.target.revision === plan.revision &&
            prior.target.mode === prior.mode));
      replace({
        ...empty,
        phase: "loaded",
        plan,
        inventory,
        selected: selectedStillAvailable ? prior.selected : "",
        mode: prior.mode,
        consent: selectedStillAvailable ? prior.consent : false,
        current: loadedPairStillCurrent ? prior.current : null,
        target: loadedPairStillCurrent ? prior.target : null,
        bindingRail: loadedPairStillCurrent ? prior.bindingRail : [],
      });
    },
    [physical, current, verify, replace],
  );
  const refresh = useCallback(async () => {
    if (!owner.active || !owner.enabled) return;
    const token = ++owner.generation;
    const before = latest.current;
    replace({
      ...before,
      phase: before.plan ? "loaded" : "loading",
      refreshing: true,
      creating: false,
      error: "",
    });
    let found = false;
    try {
      const plan = await client.current();
      if (!current(token)) return;
      found = true;
      await loadPlan(token, plan);
    } catch (error) {
      if (!current(token)) return;
      const absent =
        !found && error instanceof ApiFailure && error.status === 404 && error.code === "NOT_FOUND";
      if (absent) {
        try {
          await loadCreateInputs(token);
        } catch (createInputError) {
          if (current(token))
            replace({
              ...latest.current,
              phase: "error",
              refreshing: false,
              error: failureMessage(createInputError),
            });
        }
      } else {
        replace({
          ...latest.current,
          phase: "error",
          refreshing: false,
          error: failureMessage(error),
        });
      }
    }
  }, [owner, client, current, replace, loadPlan, loadCreateInputs]);
  useEffect(() => {
    owner.active = true;
    replace(empty);
    const observedDefinitionVersion = definitionVersion;
    if (owner.enabled && observedDefinitionVersion >= 0) void refresh();
    return () => {
      owner.active = false;
      owner.generation++;
      latest.current = empty;
    };
  }, [owner, refresh, replace, definitionVersion]);
  async function chooseDefinition(objectId: string) {
    const captured = latest.current;
    if (!owner.active || captured.creating || captured.pendingCreate) return;
    const token = ++owner.generation;
    replace({
      ...captured,
      definition: null,
      binding: "",
      destination: "",
      error: "",
      creating: Boolean(objectId),
    });
    if (!objectId) {
      replace({ ...captured, definition: null, binding: "", destination: "", error: "" });
      return;
    }
    try {
      const definition = await definitions.definition(objectId);
      if (!current(token)) return;
      replace({
        ...captured,
        definition,
        binding: "",
        destination: "",
        creating: false,
        error: "",
      });
    } catch (error) {
      if (current(token))
        replace({
          ...captured,
          definition: null,
          binding: "",
          destination: "",
          creating: false,
          error: failureMessage(error),
        });
    }
  }
  function chooseBinding(binding: string) {
    const captured = latest.current;
    if (!owner.active || captured.creating || captured.addingDestination || captured.pendingCreate)
      return;
    if (
      binding &&
      !captured.definition?.projection.model.bindings.some((item) => item.id === binding)
    )
      return;
    replace({ ...captured, binding, destination: "", error: "" });
  }
  function chooseDestination(destination: string) {
    const captured = latest.current;
    if (!owner.active || captured.creating || captured.addingDestination || captured.pendingCreate)
      return;
    if (destination && !captured.destinations?.some((item) => item.id === destination)) return;
    replace({ ...captured, destination, error: "" });
  }
  function setConnectionUrl(connectionUrl: string) {
    const captured = latest.current;
    if (!owner.active || captured.creating || captured.addingDestination || captured.pendingCreate)
      return;
    replace({ ...captured, connectionUrl, error: "" });
  }
  async function executeDestination(command: { requestId: string; jdbcUrl: string }) {
    const captured = latest.current;
    if (!owner.active || captured.addingDestination) return;
    const token = ++owner.generation;
    replace({ ...captured, addingDestination: true, pendingDestination: command, error: "" });
    try {
      const reply = await owner.api.post<{ destination: Destination }>(
        "/api/v1/destinations",
        command,
      );
      if (!current(token)) return;
      const destination = reply.destination;
      const existing = latest.current.destinations ?? [];
      replace({
        ...latest.current,
        destinations: [...existing.filter((item) => item.id !== destination.id), destination],
        destination: destination.id,
        connectionUrl: "",
        addingDestination: false,
        pendingDestination: null,
        error: "",
      });
    } catch (error) {
      if (current(token))
        replace({
          ...captured,
          addingDestination: false,
          pendingDestination: definitiveRefusal(error) ? null : command,
          error: failureMessage(error),
        });
    }
  }
  async function addDestination() {
    const captured = latest.current;
    if (!owner.active || captured.addingDestination || captured.pendingDestination) return;
    const jdbcUrl = captured.connectionUrl.trim();
    if (!jdbcUrl) return;
    await executeDestination({ requestId: crypto.randomUUID(), jdbcUrl });
  }
  async function retryDestination() {
    const command = latest.current.pendingDestination;
    if (command) await executeDestination(command);
  }
  async function executeCreate(command: CreatePlan) {
    const captured = latest.current;
    if (!owner.active || captured.creating) return;
    const token = ++owner.generation;
    replace({ ...captured, creating: true, pendingCreate: command, error: "" });
    let acknowledged = false;
    try {
      const ack = await client.create(command);
      acknowledged = true;
      if (!current(token)) return;
      const plan = await client.summary(ack.planId);
      if (!current(token)) return;
      await loadPlan(token, plan);
    } catch (error) {
      if (current(token))
        replace({
          ...captured,
          creating: false,
          pendingCreate: !acknowledged && !definitiveRefusal(error) ? command : null,
          error: failureMessage(error),
        });
    }
  }
  async function createPlan() {
    const captured = latest.current;
    if (
      captured.definition?.state !== "published" ||
      !captured.binding ||
      !captured.destination ||
      captured.creating ||
      captured.pendingCreate
    )
      return;
    const command: CreatePlan = {
      expectedRevision: "0",
      requestId: crypto.randomUUID(),
      definition: {
        objectId: captured.definition.objectId,
        workspaceRevision: captured.definition.workspaceRevision,
      },
      bindingId: captured.binding,
      destinationId: captured.destination,
    };
    await executeCreate(command);
  }
  async function retryCreate() {
    const command = latest.current.pendingCreate;
    if (command) await executeCreate(command);
  }
  function clear(patch: Partial<State>) {
    owner.generation++;
    replace({
      ...latest.current,
      current: null,
      target: null,
      bindingRail: [],
      reading: false,
      error: "",
      ...patch,
    });
  }
  function select(documentId: string) {
    if (
      !owner.active ||
      (documentId !== "" &&
        !latest.current.inventory?.documents.some((d) => d.documentId === documentId))
    )
      return;
    clear({ selected: documentId, consent: false });
  }
  function mode(value: DocumentMode) {
    if (!owner.active) return;
    clear({ mode: value });
  }
  function consent(value: boolean) {
    if (!owner.active) return;
    clear({ consent: value });
  }
  function bindingValue(value: BindingPage["items"][number]["current"]): string {
    return value.state === "value" ? value.text : value.state;
  }
  async function readAllEntities(plan: PlanSummary, side: "current" | "target") {
    const items: Array<EntityPage["items"][number]> = [];
    for (let offset: number | null = 0; offset !== null; ) {
      const page: EntityPage = (
        await physical.entities(plan.planId, { revision: plan.revision, side, offset, limit: 100 })
      ).response;
      items.push(...page.items);
      offset = page.nextOffset;
    }
    return items;
  }
  async function readAllBindings(plan: PlanSummary, entity: EntityPage["items"][number]["entity"]) {
    const items: Array<BindingPage["items"][number]> = [];
    for (let offset: number | null = 0; offset !== null; ) {
      const page: BindingPage = (
        await physical.bindings(plan.planId, {
          revision: plan.revision,
          entity,
          offset,
          limit: 100,
        })
      ).response;
      items.push(...page.items);
      offset = page.nextOffset;
    }
    return items;
  }
  async function readDocumentLocations(
    plan: PlanSummary,
    documentId: string,
    entity: EntityPage["items"][number]["entity"],
    fieldId: string,
    side: "current" | "target",
  ) {
    const locations: BindingLocation[] = [];
    for (let offset: number | null = 0; offset !== null; ) {
      const page: LocationPage = (
        await physical.bindingLocations(plan.planId, {
          revision: plan.revision,
          entity,
          fieldId,
          side,
          offset,
          limit: 100,
          completeDocumentDisclosure: true,
        })
      ).response;
      locations.push(...page.items.filter((item) => item.documentId === documentId));
      offset = page.nextOffset;
    }
    return Object.freeze(locations);
  }
  async function readBindingRail(plan: PlanSummary, documentId: string, targetAvailable: boolean) {
    const scoped = new Map<string, EntityPage["items"][number]>();
    for (const side of ["current", ...(targetAvailable ? ["target" as const] : [])] as const) {
      for (const item of await readAllEntities(plan, side)) {
        scoped.set(JSON.stringify(item.entity), item);
      }
    }
    const rail: BindingRailItem[] = [];
    for (const item of scoped.values()) {
      for (const binding of await readAllBindings(plan, item.entity)) {
        const currentDocumentLocations =
          binding.currentLocations.state === "complete" && binding.currentLocations.total > 0
            ? await readDocumentLocations(plan, documentId, item.entity, binding.fieldId, "current")
            : [];
        const targetDocumentLocations =
          targetAvailable &&
          binding.targetLocations.state === "complete" &&
          binding.targetLocations.total > 0
            ? await readDocumentLocations(plan, documentId, item.entity, binding.fieldId, "target")
            : [];
        const currentLocations = currentDocumentLocations.length;
        const targetLocations = targetDocumentLocations.length;
        if (currentLocations + targetLocations === 0) continue;
        rail.push({
          entity: item.entity,
          typeId: item.typeId,
          fieldId: binding.fieldId,
          token: binding.token,
          change: binding.change,
          current: bindingValue(binding.current),
          target: bindingValue(binding.target),
          currentLocations,
          targetLocations,
          currentTotalLocations:
            binding.currentLocations.state === "complete" ? binding.currentLocations.total : 0,
          targetTotalLocations:
            binding.targetLocations.state === "complete" ? binding.targetLocations.total : 0,
          currentDocumentLocations,
          targetDocumentLocations,
        });
      }
    }
    return Object.freeze(rail);
  }
  async function load() {
    const captured = latest.current;
    const row = captured.inventory?.documents.find((d) => d.documentId === captured.selected);
    if (
      !owner.active ||
      !enabled ||
      captured.reading ||
      !captured.plan ||
      !row ||
      !captured.consent
    )
      return;
    const token = ++owner.generation;
    replace({ ...captured, reading: true, error: "" });
    const request = {
      revision: captured.plan.revision,
      documentId: captured.selected,
      mode: captured.mode,
      completeDocumentDisclosure: true as const,
    };
    try {
      const original = (
        await physical.document(captured.plan.planId, { ...request, side: "current" })
      ).response;
      if (!current(token)) return;
      const targetAvailable = captured.plan.targetComplete && row.targetDigest !== null;
      const target = targetAvailable
        ? (await physical.document(captured.plan.planId, { ...request, side: "target" })).response
        : null;
      if (!current(token)) return;
      await verify(captured.plan);
      if (!current(token)) return;
      const bindingRail =
        captured.mode === "placeholders" || captured.mode === "raw"
          ? await readBindingRail(captured.plan, captured.selected, targetAvailable)
          : [];
      if (!current(token)) return;
      replace({ ...captured, current: original, target, bindingRail, reading: false, error: "" });
    } catch (error) {
      if (current(token))
        replace({
          ...captured,
          reading: false,
          error: failureMessage(error),
        });
    }
  }
  return {
    ...state,
    refresh,
    chooseDefinition,
    chooseBinding,
    chooseDestination,
    setConnectionUrl,
    addDestination,
    retryDestination,
    createPlan,
    retryCreate,
    select,
    setMode: mode,
    setConsent: consent,
    load,
  };
}
