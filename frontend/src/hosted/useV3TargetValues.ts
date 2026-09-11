import type { HostedApi } from "../api/hosted";
import type { PlanCommand, PlanSummary } from "../api/hostedV3Types";
import { useV3TargetCommands } from "./useV3TargetCommands";

type BindField = Extract<PlanCommand, { kind: "bind-field" }>;
export type TargetValueInput = Pick<BindField, "entity" | "fieldId" | "state">;
/** Narrow field-input adapter over the original-owner target command lifecycle. */
export function useV3TargetValues(api: HostedApi, plan: PlanSummary | null, enabled: boolean) {
  const commands = useV3TargetCommands(api, plan, enabled);
  const pending =
    commands.pending?.command.kind === "bind-field"
      ? Object.freeze({ plan: commands.pending.plan, command: commands.pending.command })
      : null;
  return {
    ...commands,
    pending,
    submit: (input: TargetValueInput) => commands.submit({ ...input, kind: "bind-field" }),
  };
}
