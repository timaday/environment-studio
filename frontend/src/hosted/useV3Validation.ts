import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiFailure, failureMessage, type HostedApi } from "../api/hosted";
import { assertValidationCompatible, HostedV3Api } from "../api/hostedV3";
import type { PlanSummary, ValidationPageResponse, ValidationSummary } from "../api/hostedV3Types";

type PageRequest = Readonly<{ offset: number; limit: number }>;
type State = Readonly<{
  summary: ValidationSummary | null;
  page: ValidationPageResponse | null;
  request: PageRequest | null;
  busy: boolean;
  error: string;
}>;
const empty: State = { summary: null, page: null, request: null, busy: false, error: "" };
/** Bounded rule navigation over complete backend validation; never an export authority. */
export function useV3Validation(api: HostedApi, plan: PlanSummary | null, enabled: boolean) {
  const owner = useMemo(
    () => ({
      client: new HostedV3Api(api),
      active: false,
      generation: 0,
      reading: false,
      retry: null as PageRequest | null,
    }),
    [api],
  );
  const [state, setState] = useState<State>(empty);
  const latest = useRef(state);
  const replace = useCallback((next: State) => {
    latest.current = next;
    setState(next);
  }, []);
  const context = JSON.stringify(plan);
  const current = (token: number) => owner.active && owner.generation === token;
  useEffect(() => {
    owner.active = true;
    replace(empty);
    return () => {
      owner.active = false;
      owner.generation++;
      owner.retry = null;
      latest.current = empty;
    };
  }, [owner, replace]);
  useEffect(() => {
    void context;
    void enabled;
    owner.generation++;
    owner.reading = false;
    owner.retry = null;
    replace(empty);
    return () => {
      owner.generation++;
    };
  }, [owner, context, enabled, replace]);
  function available() {
    return (
      owner.active && enabled && plan?.inspectionValid && plan.observedDestination?.evidenceValid
    );
  }
  function sessionEnded(error: unknown) {
    if (!(error instanceof ApiFailure) || error.code !== "SESSION_REQUIRED") return false;
    owner.active = false;
    owner.generation++;
    owner.reading = false;
    owner.retry = null;
    replace({ ...empty, error: failureMessage(error) });
    return true;
  }
  async function verify(capturedPlan: PlanSummary) {
    const fresh = await owner.client.summary(capturedPlan.planId);
    if (JSON.stringify(fresh) !== JSON.stringify(capturedPlan))
      throw new ApiFailure(409, "CONFLICT");
  }
  async function validate() {
    if (!available() || !plan) return;
    const capturedPlan = plan;
    const token = ++owner.generation;
    owner.reading = true;
    owner.retry = null;
    replace({ ...empty, busy: true });
    try {
      const summary = await owner.client.validate(capturedPlan.planId, {
        revision: capturedPlan.revision,
      });
      if (!current(token)) return;
      if (summary.targetComplete !== capturedPlan.targetComplete)
        throw new ApiFailure(409, "CONFLICT");
      await verify(capturedPlan);
      if (!current(token)) return;
      replace({ ...empty, summary });
    } catch (error) {
      if (current(token) && !sessionEnded(error))
        replace({ ...empty, error: failureMessage(error) });
    } finally {
      if (current(token)) owner.reading = false;
    }
  }
  async function readPage(offset: number, limit = 4) {
    const summary = latest.current.summary;
    if (!available() || !plan || owner.reading || !summary?.targetComplete) return;
    if (
      !Number.isInteger(offset) ||
      offset < 0 ||
      offset > 2147483647 ||
      !Number.isInteger(limit) ||
      limit < 1 ||
      limit > 100
    ) {
      replace({ ...latest.current, error: "INVALID_REQUEST" });
      return;
    }
    const capturedPlan = plan;
    const token = ++owner.generation;
    const request = Object.freeze({ offset, limit });
    owner.reading = true;
    owner.retry = null;
    replace({ ...empty, summary, request, busy: true });
    try {
      const page = await owner.client.validationRules(capturedPlan.planId, {
        revision: summary.revision,
        section: "computed-rules",
        inputFingerprint: summary.inputFingerprint,
        offset,
        limit,
      });
      if (!current(token)) return;
      assertValidationCompatible(summary, page);
      await verify(capturedPlan);
      if (!current(token)) return;
      replace({ ...empty, summary, page, request });
    } catch (error) {
      if (!current(token) || sessionEnded(error)) return;
      if (error instanceof ApiFailure && error.code === "RESOURCE_LIMIT" && error.status === 422) {
        owner.retry = request;
        replace({ ...empty, summary, request, error: failureMessage(error) });
      } else replace({ ...empty, error: failureMessage(error) });
    } finally {
      if (current(token)) owner.reading = false;
    }
  }
  async function retrySmaller(limit: number) {
    const retry = owner.retry;
    if (!retry || owner.reading || !Number.isInteger(limit) || limit < 1 || limit >= retry.limit)
      return;
    await readPage(retry.offset, limit);
  }
  return { ...state, validate, readPage, retrySmaller };
}
