import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiFailure, failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Api } from "../api/hostedV3";
import { captureRequest } from "../api/hostedV3Decoding";
import { assertPhysicalPagesCompatible, HostedV3Physical } from "../api/hostedV3Physical";
import {
  HostedV3Profiles,
  type PreparedProfileSave,
  type ProfileRevision,
  prepareCapturedProfileSave,
} from "../api/hostedV3Profiles";
import type { CaptureRequest, CaptureResponse, PlanSummary } from "../api/hostedV3Types";

type Entity = Readonly<{ entity: Readonly<{ kind: "existing"; handle: string }>; typeId: string }>;
export type CaptureInput = Omit<CaptureRequest, "revision">;
type State = Readonly<{
  entities: readonly Entity[] | null;
  input: CaptureRequest | null;
  captured: CaptureResponse | null;
  saved: ProfileRevision | null;
  busy: boolean;
  pending: boolean;
  error: string;
}>;
const empty: State = {
  entities: null,
  input: null,
  captured: null,
  saved: null,
  busy: false,
  pending: false,
  error: "",
};

/** Explicit capture entry and separate durable draft save. No visual/persistence fallback. */
export function useV3ProfileCapture(api: HostedApi, plan: PlanSummary | null, enabled: boolean) {
  const owner = useMemo(
    () => ({
      client: new HostedV3Api(api),
      physical: new HostedV3Physical(api),
      profiles: new HostedV3Profiles(api),
      active: false,
      generation: 0,
      reading: false,
      saving: false,
      pending: null as PreparedProfileSave | null,
    }),
    [api],
  );
  const contextKey = JSON.stringify(plan);
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
  useEffect(() => {
    owner.active = true;
    replace(empty);
    return () => {
      owner.active = false;
      owner.generation++;
      owner.pending = null;
      latest.current = empty;
    };
  }, [owner, replace]);
  useEffect(() => {
    // Presentation/context invalidate reads, but never replace a dispatched save.
    void contextKey;
    void enabled;
    owner.generation++;
    owner.reading = false;
    replace({
      ...empty,
      pending: owner.pending !== null,
      busy: owner.saving,
      saved: latest.current.saved,
    });
    return () => {
      owner.generation++;
    };
  }, [owner, contextKey, enabled, replace]);
  function available() {
    return owner.active && enabled && !owner.saving && !owner.pending;
  }
  function sessionEnded(error: unknown) {
    if (!(error instanceof ApiFailure) || error.code !== "SESSION_REQUIRED") return false;
    owner.active = false;
    owner.generation++;
    owner.pending = null;
    owner.reading = false;
    owner.saving = false;
    replace({ ...empty, error: failureMessage(error) });
    return true;
  }
  async function verify(capturedPlan: PlanSummary) {
    const fresh = await owner.client.summary(capturedPlan.planId);
    if (JSON.stringify(fresh) !== JSON.stringify(capturedPlan))
      throw new ApiFailure(409, "CONFLICT");
  }
  async function load() {
    if (!available() || owner.reading || !plan?.inspectionValid || !plan.observedDestination)
      return;
    const capturedPlan = plan;
    const token = ++owner.generation;
    owner.reading = true;
    replace({ ...empty, busy: true });
    try {
      const entities: Entity[] = [];
      const handles = new Set<string>();
      let first: Awaited<ReturnType<HostedV3Physical["entities"]>> | null = null;
      let offset: number | null = 0;
      while (offset !== null) {
        const page = await owner.physical.entities(capturedPlan.planId, {
          revision: capturedPlan.revision,
          side: "current",
          offset,
          limit: 100,
        });
        if (!current(token)) return;
        if (
          page.response.total > 20000 ||
          page.response.total !== capturedPlan.currentCounts.entities
        )
          throw new ApiFailure(409, "CONFLICT");
        if (first) assertPhysicalPagesCompatible(first, page);
        else first = page;
        for (const item of page.response.items) {
          if (item.entity.kind !== "existing" || handles.has(item.entity.handle))
            throw new ApiFailure(409, "CONFLICT");
          handles.add(item.entity.handle);
          entities.push(Object.freeze({ entity: item.entity, typeId: item.typeId }));
        }
        offset = page.response.nextOffset;
      }
      if (!first || entities.length !== first.response.total) throw new ApiFailure(409, "CONFLICT");
      await verify(capturedPlan);
      if (!current(token)) return;
      replace({ ...empty, entities: Object.freeze(entities) });
    } catch (error) {
      if (current(token) && !sessionEnded(error))
        replace({ ...empty, error: failureMessage(error) });
    } finally {
      if (current(token)) owner.reading = false;
    }
  }
  function configure(input: CaptureInput) {
    if (!available() || !plan) return;
    owner.generation++;
    owner.reading = false;
    const next = {
      ...latest.current,
      input: null,
      captured: null,
      saved: null,
      busy: false,
      error: "",
    };
    try {
      replace({ ...next, input: captureRequest({ ...input, revision: plan.revision }) });
    } catch (error) {
      replace({
        ...next,
        error: failureMessage(
          error instanceof ApiFailure ? new ApiFailure(0, "INVALID_REQUEST") : error,
        ),
      });
    }
  }
  async function capture() {
    const before = latest.current;
    if (!available() || owner.reading || !plan || !before.entities || !before.input) return;
    const capturedPlan = plan;
    const token = ++owner.generation;
    owner.reading = true;
    replace({ ...before, captured: null, saved: null, busy: true, error: "" });
    try {
      const mappings = before.input.mappings;
      const handles = new Set(mappings.map((m) => m.entity.handle));
      if (
        before.input.revision !== plan.revision ||
        mappings.length !== before.entities.length ||
        handles.size !== mappings.length ||
        new Set(mappings.map((m) => m.slotId)).size !== mappings.length ||
        !before.entities.every((e) => handles.has(e.entity.handle))
      )
        throw new ApiFailure(0, "INVALID_REQUEST");
      const value = await owner.client.capture(plan.planId, before.input);
      if (!current(token)) return;
      if (JSON.stringify(value.definition) !== JSON.stringify(capturedPlan.definition))
        throw new ApiFailure(409, "CONFLICT");
      await verify(capturedPlan);
      if (!current(token)) return;
      replace({ ...before, captured: value, saved: null, busy: false, error: "" });
    } catch (error) {
      if (current(token) && !sessionEnded(error))
        replace({
          ...before,
          captured: null,
          saved: null,
          busy: false,
          error: failureMessage(error),
        });
    } finally {
      if (current(token)) owner.reading = false;
    }
  }
  async function execute(command: PreparedProfileSave) {
    if (!owner.active || !enabled || owner.saving) return;
    owner.saving = true;
    owner.pending = command;
    replace({ ...latest.current, pending: true, busy: true, error: "" });
    try {
      const value = await owner.profiles.saveProfile(command);
      if (!owner.active) return;
      owner.pending = null;
      replace({ ...latest.current, saved: value, pending: false, busy: false, error: "" });
    } catch (error) {
      if (!owner.active) return;
      if (!sessionEnded(error)) {
        const refused =
          error instanceof ApiFailure &&
          [
            "400:INVALID_REQUEST",
            "404:NOT_FOUND",
            "409:CONFLICT",
            "422:REJECTED",
            "429:CAPACITY",
          ].includes(`${error.status}:${error.code}`);
        owner.pending = refused ? null : command;
        replace({
          ...latest.current,
          pending: !refused,
          busy: false,
          error: failureMessage(error),
        });
      }
    } finally {
      owner.saving = false;
    }
  }
  async function save() {
    const before = latest.current;
    if (!available() || owner.reading || !before.captured || before.saved) return;
    try {
      await execute(
        prepareCapturedProfileSave(crypto.randomUUID(), "0", crypto.randomUUID(), before.captured),
      );
    } catch (error) {
      if (owner.active) replace({ ...latest.current, error: failureMessage(error) });
    }
  }
  async function retry() {
    if (owner.pending) await execute(owner.pending);
  }
  return { ...state, load, configure, capture, save, retry };
}
