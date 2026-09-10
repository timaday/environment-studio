import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiFailure, failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Api } from "../api/hostedV3";
import { HostedV3Physical } from "../api/hostedV3Physical";
import type { PlanSummary } from "../api/hostedV3Types";

type Inventory = Awaited<ReturnType<HostedV3Physical["documents"]>>["response"];
type Document = Awaited<ReturnType<HostedV3Physical["document"]>>["response"];
type State = Readonly<{
  phase: "idle" | "loading" | "absent" | "loaded" | "error";
  plan: PlanSummary | null;
  inventory: Inventory | null;
  selected: string;
  mode: "raw" | "formatted";
  consent: boolean;
  reading: boolean;
  current: Document | null;
  target: Document | null;
  error: string;
}>;
const empty: State = {
  phase: "idle",
  plan: null,
  inventory: null,
  selected: "",
  mode: "raw",
  consent: false,
  reading: false,
  current: null,
  target: null,
  error: "",
};

/** Read-only state; complete document authority remains with each server request. */
export function useV3PlanInspection(api: HostedApi, enabled: boolean) {
  const client = useMemo(() => new HostedV3Api(api), [api]);
  const physical = useMemo(() => new HostedV3Physical(api), [api]);
  const owner = useMemo(
    () => ({ client, physical, enabled, active: false, generation: 0 }),
    [client, physical, enabled],
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
  const refresh = useCallback(async () => {
    if (!owner.active || !owner.enabled) return;
    const token = ++owner.generation;
    replace({ ...empty, phase: "loading" });
    let found = false;
    try {
      const plan = await client.current();
      if (!current(token)) return;
      found = true;
      const inventory =
        plan.observedDestination === null
          ? null
          : (await physical.documents(plan.planId, { revision: plan.revision })).response;
      if (!current(token)) return;
      await verify(plan);
      if (!current(token)) return;
      replace({ ...empty, phase: "loaded", plan, inventory });
    } catch (error) {
      if (!current(token)) return;
      const absent =
        !found && error instanceof ApiFailure && error.status === 404 && error.code === "NOT_FOUND";
      replace({
        ...empty,
        phase: absent ? "absent" : "error",
        error: absent ? "" : failureMessage(error),
      });
    }
  }, [owner, client, physical, current, replace, verify]);
  useEffect(() => {
    owner.active = true;
    replace(empty);
    if (owner.enabled) void refresh();
    return () => {
      owner.active = false;
      owner.generation++;
      latest.current = empty;
    };
  }, [owner, refresh, replace]);
  function clear(patch: Partial<State>) {
    owner.generation++;
    replace({
      ...latest.current,
      current: null,
      target: null,
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
  function mode(value: "raw" | "formatted") {
    if (!owner.active) return;
    clear({ mode: value });
  }
  function consent(value: boolean) {
    if (!owner.active) return;
    clear({ consent: value });
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
    replace({ ...captured, current: null, target: null, reading: true, error: "" });
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
      const target =
        !captured.plan.targetComplete || row.targetDigest === null
          ? null
          : (await physical.document(captured.plan.planId, { ...request, side: "target" }))
              .response;
      if (!current(token)) return;
      await verify(captured.plan);
      if (!current(token)) return;
      replace({ ...captured, current: original, target, reading: false, error: "" });
    } catch (error) {
      if (current(token))
        replace({
          ...captured,
          current: null,
          target: null,
          reading: false,
          error: failureMessage(error),
        });
    }
  }
  return { ...state, refresh, select, setMode: mode, setConsent: consent, load };
}
