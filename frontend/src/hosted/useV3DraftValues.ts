import { useEffect, useMemo, useState } from "react";
import { ApiFailure, failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Api } from "../api/hostedV3";
import { assertPhysicalPagesCompatible, HostedV3Physical } from "../api/hostedV3Physical";
import type { PlanSummary } from "../api/hostedV3Types";

type Page = Awaited<ReturnType<HostedV3Physical["draft"]>>;
export type DraftValueItem = Page["response"]["items"][number];
type State = Readonly<{
  page: Page["response"] | null;
  offset: number;
  busy: boolean;
  error: string;
}>;
const empty: State = { page: null, offset: 0, busy: false, error: "" };

/** Paged target draft reader for operator value entry. The server remains edit/export authority. */
export function useV3DraftValues(api: HostedApi, plan: PlanSummary | null, enabled: boolean) {
  const context = JSON.stringify(plan);
  const owner = useMemo(
    () => ({
      physical: new HostedV3Physical(api),
      plans: new HostedV3Api(api),
      active: false,
      reading: false,
      generation: 0,
      first: null as Page | null,
    }),
    [api],
  );
  const [view, setView] = useState({ owner, state: empty });
  useEffect(() => {
    void context;
    owner.active = enabled;
    owner.reading = false;
    owner.generation++;
    owner.first = null;
    setView({ owner, state: empty });
    return () => {
      owner.active = false;
      owner.generation++;
      owner.first = null;
    };
  }, [owner, enabled, context]);
  const current = (token: number) => owner.active && owner.generation === token;
  async function load(offset = 0) {
    if (
      !owner.active ||
      !enabled ||
      owner.reading ||
      !plan?.inspectionValid ||
      !plan.observedDestination?.evidenceValid
    )
      return;
    if (!Number.isInteger(offset) || offset < 0 || offset > 50000) {
      setView({ owner, state: { ...empty, error: "INVALID_REQUEST" } });
      return;
    }
    const captured = plan;
    const token = ++owner.generation;
    owner.reading = true;
    setView({ owner, state: { ...empty, offset, busy: true } });
    try {
      const page = await owner.physical.draft(captured.planId, {
        revision: captured.revision,
        offset,
        limit: 20,
      });
      if (!current(token)) return;
      if (owner.first) assertPhysicalPagesCompatible(owner.first, page);
      else owner.first = page;
      if (page.response.total > 20000) throw new ApiFailure(409, "CONFLICT");
      const fresh = await owner.plans.summary(captured.planId);
      if (!current(token)) return;
      if (JSON.stringify(fresh) !== JSON.stringify(captured)) throw new ApiFailure(409, "CONFLICT");
      setView({ owner, state: { ...empty, page: page.response, offset } });
    } catch (error) {
      if (!current(token)) return;
      if (error instanceof ApiFailure && error.code === "SESSION_REQUIRED") {
        owner.active = false;
        owner.generation++;
      }
      setView({ owner, state: { ...empty, error: failureMessage(error) } });
    } finally {
      if (current(token)) owner.reading = false;
    }
  }
  return { ...(view.owner === owner ? view.state : empty), load };
}
