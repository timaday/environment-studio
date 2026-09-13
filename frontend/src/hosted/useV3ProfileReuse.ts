import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiFailure, failureMessage, type HostedApi } from "../api/hosted";
import { assertPreviewCompatible, HostedV3Api, prepareCommand } from "../api/hostedV3";
import { previewRequest } from "../api/hostedV3Decoding";
import type {
  Ack,
  PlanCommand,
  PlanSummary,
  PreviewRequest,
  PreviewResponse,
} from "../api/hostedV3Types";

export type ReuseInput = Pick<PreviewRequest, "profile" | "selection">;
export type ReuseCommand = Extract<PlanCommand, { kind: "compose-profile" }>;
type Section = PreviewResponse["section"];
type Items<S extends Section> = Extract<PreviewResponse, { section: S }>["items"];
type Preview = Readonly<
  Pick<PreviewResponse, "pins" | "previewDigest" | "affectedDerivations"> & {
    included: Items<"included">;
    dependencies: Items<"dependencies">;
    relations: Items<"relations">;
    conflicts: Items<"conflicts">;
  }
>;
type Pending = Readonly<{ planId: string; command: ReuseCommand }>;
type State = Readonly<{
  input: ReuseInput | null;
  preview: Preview | null;
  receipt: Ack | null;
  busy: boolean;
  pending: boolean;
  error: string;
}>;
const empty: State = {
  input: null,
  preview: null,
  receipt: null,
  busy: false,
  pending: false,
  error: "",
};

/** Complete read-only preview, followed by an explicit original-owner composition command. */
export function useV3ProfileReuse(api: HostedApi, plan: PlanSummary | null, enabled: boolean) {
  const owner = useMemo(
    () => ({
      client: new HostedV3Api(api),
      active: false,
      generation: 0,
      reading: false,
      saving: false,
      pending: null as Pending | null,
    }),
    [api],
  );
  const [state, setState] = useState<State>(empty);
  const latest = useRef(state);
  const replace = useCallback((next: State) => {
    latest.current = next;
    setState(next);
  }, []);
  const current = (token: number) => owner.active && owner.generation === token;
  const context = JSON.stringify(plan);
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
    void context;
    void enabled;
    owner.generation++;
    owner.reading = false;
    replace({ ...empty, pending: owner.pending !== null, busy: owner.saving });
    return () => {
      owner.generation++;
    };
  }, [owner, context, enabled, replace]);
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
  function configure(input: ReuseInput) {
    if (!available() || !plan) return;
    owner.generation++;
    owner.reading = false;
    try {
      const checked = previewRequest({
        ...input,
        revision: plan.revision,
        section: "included",
        offset: 0,
        limit: 100,
      });
      replace({
        ...empty,
        input: Object.freeze({ profile: checked.profile, selection: checked.selection }),
      });
    } catch {
      replace({ ...empty, error: "INVALID_REQUEST" });
    }
  }
  function clearSelection() {
    if (!available()) return;
    owner.generation++;
    owner.reading = false;
    replace(empty);
  }
  async function previewSelection() {
    const input = latest.current.input;
    if (
      !available() ||
      owner.reading ||
      !input ||
      !plan?.inspectionValid ||
      !plan.observedDestination?.evidenceValid
    )
      return;
    const capturedPlan = plan;
    const token = ++owner.generation;
    owner.reading = true;
    replace({ ...empty, input, busy: true });
    try {
      let first: PreviewResponse | null = null;
      const included: Items<"included">[number][] = [];
      const dependencies: Items<"dependencies">[number][] = [];
      const relations: Items<"relations">[number][] = [];
      const conflicts: Items<"conflicts">[number][] = [];
      for (const section of ["included", "dependencies", "relations", "conflicts"] as const) {
        let sectionFirst: PreviewResponse | null = null;
        let offset: number | null = 0;
        while (offset !== null) {
          const page = await owner.client.preview(capturedPlan.planId, {
            ...input,
            revision: capturedPlan.revision,
            section,
            offset,
            limit: 100,
          });
          if (!current(token)) return;
          if (first) assertPreviewCompatible(first, page);
          else first = page;
          if (sectionFirst) assertPreviewCompatible(sectionFirst, page);
          else sectionFirst = page;
          if (
            page.pins.observationFingerprint !==
            capturedPlan.observedDestination?.observationFingerprint
          )
            throw new ApiFailure(409, "CONFLICT");
          switch (page.section) {
            case "included":
              included.push(...page.items);
              break;
            case "dependencies":
              dependencies.push(...page.items);
              break;
            case "relations":
              relations.push(...page.items);
              break;
            case "conflicts":
              conflicts.push(...page.items);
              break;
          }
          offset = page.nextOffset;
        }
      }
      const slots = new Set(included.map((s) => s.slotId));
      if (
        !first ||
        slots.size !== included.length ||
        !first.pins.selectedRoots.every((s) => slots.has(s)) ||
        dependencies.some((d) => !slots.has(d.slotId) || !slots.has(d.causedBy)) ||
        relations.some((r) => !slots.has(r.fromSlot) || !slots.has(r.toSlot))
      )
        throw new ApiFailure(409, "CONFLICT");
      const fresh = await owner.client.summary(capturedPlan.planId);
      if (!current(token)) return;
      if (JSON.stringify(fresh) !== JSON.stringify(capturedPlan))
        throw new ApiFailure(409, "CONFLICT");
      replace({
        ...empty,
        input,
        preview: Object.freeze({
          pins: first.pins,
          previewDigest: first.previewDigest,
          affectedDerivations: first.affectedDerivations,
          included: Object.freeze(included),
          dependencies: Object.freeze(dependencies),
          relations: Object.freeze(relations),
          conflicts: Object.freeze(conflicts),
        }),
      });
    } catch (error) {
      if (current(token) && !sessionEnded(error))
        replace({ ...empty, input, error: failureMessage(error) });
    } finally {
      if (current(token)) owner.reading = false;
    }
  }
  async function execute(pending: Pending) {
    if (!owner.active || !enabled || owner.saving) return;
    owner.saving = true;
    owner.pending = pending;
    replace({
      ...latest.current,
      preview: null,
      receipt: null,
      busy: true,
      pending: true,
      error: "",
    });
    try {
      const receipt = await owner.client.command(pending.planId, pending.command);
      if (!owner.active) return;
      owner.pending = null;
      replace({ ...empty, receipt });
    } catch (error) {
      if (!owner.active || sessionEnded(error)) return;
      const refused =
        error instanceof ApiFailure &&
        [
          "400:INVALID_REQUEST",
          "404:NOT_FOUND",
          "409:CONFLICT",
          "409:STALE_PREVIEW",
          "429:CAPACITY",
        ].includes(`${error.status}:${error.code}`);
      if (refused) owner.pending = null;
      replace({ ...empty, pending: !refused, error: failureMessage(error) });
    } finally {
      owner.saving = false;
    }
  }
  async function apply(decisions: ReuseCommand["decisions"]) {
    const before = latest.current.preview;
    if (!available() || owner.reading || !plan || !before) return;
    try {
      const slots = new Set(decisions.map((d) => d.slotId));
      if (
        slots.size !== decisions.length ||
        slots.size !== before.included.length ||
        !before.included.every((s) => slots.has(s.slotId))
      )
        throw new ApiFailure(0, "INVALID_REQUEST");
      const command = prepareCommand({
        kind: "compose-profile",
        expectedRevision: before.pins.revision,
        requestId: crypto.randomUUID(),
        profile: before.pins.profile,
        previewDigest: before.previewDigest,
        selectedRoots: before.pins.selectedRoots,
        decisions,
      });
      if (command.kind !== "compose-profile") throw new ApiFailure(0, "INVALID_REQUEST");
      await execute(Object.freeze({ planId: before.pins.planId, command }));
    } catch (error) {
      if (owner.active) replace({ ...latest.current, error: failureMessage(error) });
    }
  }
  async function retry() {
    if (owner.pending) await execute(owner.pending);
  }
  return { ...state, configure, clearSelection, previewSelection, apply, retry };
}
