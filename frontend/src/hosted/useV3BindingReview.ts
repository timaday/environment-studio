import { useEffect, useMemo, useState } from "react";
import { ApiFailure, failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Api } from "../api/hostedV3";
import { entityRef, summary } from "../api/hostedV3Decoding";
import {
  assertPhysicalPagesCompatible,
  type BindingsRequest,
  HostedV3Physical,
} from "../api/hostedV3Physical";
import type { PlanSummary } from "../api/hostedV3Types";

type Page = Awaited<ReturnType<HostedV3Physical["bindings"]>>;
export type BindingReviewItem = Page["response"]["items"][number];
type State = Readonly<{ items: readonly BindingReviewItem[] | null; busy: boolean; error: string }>;
const empty: State = { items: null, busy: false, error: "" };
/** Complete current/target field evidence for one explicit reference; no commands or disclosure. */
export function useV3BindingReview(
  api: HostedApi,
  plan: PlanSummary | null,
  entity: BindingsRequest["entity"] | null,
  enabled: boolean,
) {
  const context = JSON.stringify(plan),
    selection = JSON.stringify(entity);
  const owner = useMemo(
    () => ({
      api,
      context,
      selection,
      enabled,
      active: false,
      reading: false,
      generation: 0,
      physical: new HostedV3Physical(api),
      plans: new HostedV3Api(api),
    }),
    [api, context, selection, enabled],
  );
  const [view, setView] = useState({ owner, state: empty });
  useEffect(() => {
    owner.active = owner.enabled;
    owner.generation++;
    setView({ owner, state: empty });
    return () => {
      owner.active = false;
      owner.generation++;
    };
  }, [owner]);
  const current = (token: number) => owner.active && owner.generation === token;
  async function load() {
    if (!owner.active || owner.reading || !plan || !entity) return;
    const token = ++owner.generation;
    owner.reading = true;
    setView({ owner, state: { ...empty, busy: true } });
    try {
      const captured = summary(plan),
        reference = entityRef(entity);
      const items: BindingReviewItem[] = [],
        fields = new Set<string>(),
        tokens = new Set<string>();
      let first: Page | null = null,
        offset: number | null = 0;
      while (offset !== null) {
        const page = await owner.physical.bindings(captured.planId, {
          revision: captured.revision,
          entity: reference,
          offset,
          limit: 100,
        });
        if (!current(token)) return;
        if (first) assertPhysicalPagesCompatible(first, page);
        else first = page;
        for (const item of page.response.items) {
          if (
            fields.has(item.fieldId) ||
            tokens.has(item.token) ||
            !item.token.endsWith(`:${item.fieldId}]]`)
          )
            throw new ApiFailure(409, "CONFLICT");
          fields.add(item.fieldId);
          tokens.add(item.token);
          items.push(item);
        }
        offset = page.response.nextOffset;
      }
      const fresh = await owner.plans.summary(captured.planId);
      if (!current(token)) return;
      if (JSON.stringify(fresh) !== JSON.stringify(captured)) throw new ApiFailure(409, "CONFLICT");
      setView({ owner, state: { ...empty, items: Object.freeze(items) } });
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
