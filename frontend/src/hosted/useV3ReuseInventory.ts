import { useEffect, useMemo, useState } from "react";
import { ApiFailure, failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Api } from "../api/hostedV3";
import { assertPhysicalPagesCompatible, HostedV3Physical } from "../api/hostedV3Physical";
import type { PlanSummary } from "../api/hostedV3Types";

type Page = Awaited<ReturnType<HostedV3Physical["entities"]>>;
export type ReuseInventoryItem = Page["response"]["items"][number];
type State = Readonly<{
  items: readonly ReuseInventoryItem[] | null;
  busy: boolean;
  error: string;
}>;
const empty: State = { items: null, busy: false, error: "" };

/** Current values support explicit placement only; no profile or command is constructed. */
export function useV3ReuseInventory(api: HostedApi, plan: PlanSummary | null, enabled: boolean) {
  const context = JSON.stringify(plan);
  const owner = useMemo(() => {
    // Retired callbacks keep this owner; a replacement context never reactivates it.
    void context;
    void enabled;
    return {
      physical: new HostedV3Physical(api),
      plans: new HostedV3Api(api),
      active: false,
      reading: false,
      generation: 0,
    };
  }, [api, context, enabled]);
  const [state, setState] = useState({ owner, value: empty });
  useEffect(() => {
    owner.active = enabled;
    owner.reading = false;
    owner.generation++;
    setState({ owner, value: empty });
    return () => {
      owner.active = false;
      owner.generation++;
    };
  }, [owner, enabled]);
  const current = (token: number) => owner.active && owner.generation === token;
  async function load() {
    if (
      !owner.active ||
      !enabled ||
      owner.reading ||
      !plan?.inspectionValid ||
      !plan.observedDestination?.evidenceValid
    )
      return;
    const captured = plan;
    const token = ++owner.generation;
    owner.reading = true;
    setState({ owner, value: { ...empty, busy: true } });
    try {
      const items: ReuseInventoryItem[] = [];
      const handles = new Set<string>();
      let first: Page | null = null;
      let offset: number | null = 0;
      while (offset !== null) {
        const page = await owner.physical.entities(captured.planId, {
          revision: captured.revision,
          side: "current",
          offset,
          limit: 100,
        });
        if (!current(token)) return;
        if (page.response.total > 20000 || page.response.total !== captured.currentCounts.entities)
          throw new ApiFailure(409, "CONFLICT");
        if (first) assertPhysicalPagesCompatible(first, page);
        else first = page;
        for (const item of page.response.items) {
          if (item.entity.kind !== "existing" || handles.has(item.entity.handle))
            throw new ApiFailure(409, "CONFLICT");
          handles.add(item.entity.handle);
          items.push(item);
        }
        offset = page.response.nextOffset;
      }
      const fresh = await owner.plans.summary(captured.planId);
      if (!current(token)) return;
      if (JSON.stringify(fresh) !== JSON.stringify(captured)) throw new ApiFailure(409, "CONFLICT");
      setState({ owner, value: { ...empty, items: Object.freeze(items) } });
    } catch (error) {
      if (!current(token)) return;
      if (error instanceof ApiFailure && error.code === "SESSION_REQUIRED") {
        owner.active = false;
        owner.generation++;
      }
      setState({ owner, value: { ...empty, error: failureMessage(error) } });
    } finally {
      if (current(token)) owner.reading = false;
    }
  }
  return { ...(state.owner === owner ? state.value : empty), load };
}
