import { useEffect, useMemo, useState } from "react";
import { ApiFailure, failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Api, prepareCommand } from "../api/hostedV3";
import { summary } from "../api/hostedV3Decoding";
import type { Ack, PlanCommand, PlanSummary } from "../api/hostedV3Types";

type BindField = Extract<PlanCommand, { kind: "bind-field" }>;
export type TargetValueInput = Pick<BindField, "entity" | "fieldId" | "state">;
type Pending = Readonly<{ plan: PlanSummary; command: BindField }>;
type State = Readonly<{
  busy: boolean;
  pending: Pending | null;
  receipt: Ack | null;
  error: string;
}>;
const empty: State = { busy: false, pending: null, receipt: null, error: "" };

/** Explicit transient field decisions. The server alone decides mutation and export authority. */
export function useV3TargetValues(api: HostedApi, plan: PlanSummary | null, enabled: boolean) {
  const owner = useMemo(
    () => ({ client: new HostedV3Api(api), active: false, generation: 0, state: empty }),
    [api],
  );
  const [view, setView] = useState({ owner, state: empty });
  function replace(state: State) {
    owner.state = state;
    setView({ owner, state });
  }
  useEffect(() => {
    owner.active = true;
    owner.generation++;
    owner.state = empty;
    setView({ owner, state: empty });
    return () => {
      owner.active = false;
      owner.generation++;
      owner.state = empty;
    };
  }, [owner]);
  const context = JSON.stringify(plan);
  const presentation = useMemo(
    () => ({ owner, context, enabled, active: false }),
    [owner, context, enabled],
  );
  useEffect(() => {
    presentation.active = presentation.enabled;
    return () => {
      presentation.active = false;
    };
  }, [presentation]);
  useEffect(() => {
    void context;
    void enabled;
    if (!owner.state.pending && !owner.state.busy) {
      owner.state = empty;
      setView({ owner, state: empty });
    }
  }, [owner, context, enabled]);
  async function execute(pending: Pending) {
    if (!owner.active || !presentation.active || owner.state.busy) return;
    const generation = owner.generation;
    replace({ busy: true, pending, receipt: null, error: "" });
    try {
      const receipt = await owner.client.command(pending.plan.planId, pending.command);
      if (owner.active && generation === owner.generation) replace({ ...empty, receipt });
    } catch (error) {
      if (!owner.active || generation !== owner.generation) return;
      if (error instanceof ApiFailure && error.code === "SESSION_REQUIRED") {
        owner.active = false;
        replace({ ...empty, error: failureMessage(error) });
        return;
      }
      const refused =
        error instanceof ApiFailure &&
        [
          "400:INVALID_REQUEST",
          "404:NOT_FOUND",
          "409:CONFLICT",
          "409:STALE_PREVIEW",
          "429:CAPACITY",
        ].includes(`${error.status}:${error.code}`);
      replace({ ...empty, pending: refused ? null : pending, error: failureMessage(error) });
    }
  }
  async function submit(input: TargetValueInput) {
    if (
      !owner.active ||
      !presentation.active ||
      owner.state.busy ||
      owner.state.pending ||
      !plan?.inspectionValid ||
      !plan.observedDestination?.evidenceValid
    )
      return;
    let pending: Pending;
    try {
      const command = prepareCommand({
        ...input,
        kind: "bind-field",
        expectedRevision: plan.revision,
        requestId: crypto.randomUUID(),
      });
      if (command.kind !== "bind-field") throw new ApiFailure(0, "INVALID_REQUEST");
      pending = Object.freeze({ plan: summary(plan), command });
    } catch {
      replace({ ...empty, error: "INVALID_REQUEST" });
      return;
    }
    await execute(pending);
  }
  async function retry() {
    if (owner.state.pending) await execute(owner.state.pending);
  }
  function clear() {
    if (owner.active && presentation.active && !owner.state.pending && !owner.state.busy)
      replace(empty);
  }
  return { ...(view.owner === owner ? view.state : empty), submit, retry, clear };
}
