import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import type { HostedApi } from "../api/hosted";
import type { Operation, PlanSummary } from "../api/hostedV3Types";
import { V3Inspection } from "./V3Inspection";

const plan: PlanSummary = {
  planId: "50000000-0000-0000-0000-000000000001",
  revision: "1",
  definition: { objectId: "50000000-0000-0000-0000-000000000002", workspaceRevision: "2" },
  bindingId: "mock-pg",
  destinationId: "postgresql-pilot",
  currentCounts: { documents: 0, entities: 0, relations: 0 },
  targetCounts: { documents: 0, entities: 0, relations: 0 },
  inspectionValid: false,
  targetComplete: false,
  exportAvailable: false,
  blockers: ["INSPECTION_REQUIRED"],
  observedDestination: null,
  currentComputedCounts: null,
  targetComputedCounts: null,
};

const operation: Operation = {
  operationId: "50000000-0000-0000-0000-000000000003",
  planId: plan.planId,
  phase: "succeeded",
  code: "OK",
  cleanup: "complete",
  installedRevision: "2",
};

it("reserves v3 inspection only after explicit confirmation", async () => {
  const post = vi
    .fn()
    .mockResolvedValue({ planId: plan.planId, revision: "1", operationId: operation.operationId });
  render(
    <V3Inspection api={{ post } as unknown as HostedApi} plan={plan} enabled refresh={vi.fn()} />,
  );
  expect(screen.getByRole("button", { name: "Reserve read-only inspection" })).toBeDisabled();
  await userEvent.click(screen.getByRole("checkbox", { name: /may replace current observation/ }));
  await userEvent.click(screen.getByRole("button", { name: "Reserve read-only inspection" }));
  await screen.findByLabelText("Database username");
  expect(post).toHaveBeenCalledWith(`/api/v3/plans/${plan.planId}/inspections`, {
    expectedRevision: "1",
    requestId: expect.any(String),
    discardDraftOnSuccess: true,
  });
});

it("sends credentials through the v3 one-use endpoint and clears the form", async () => {
  const post = vi
    .fn()
    .mockResolvedValue({ planId: plan.planId, revision: "1", operationId: operation.operationId });
  const credentialsV3 = vi.fn().mockResolvedValue(operation);
  const refresh = vi.fn();
  render(
    <V3Inspection
      api={{ post, credentialsV3 } as unknown as HostedApi}
      plan={plan}
      enabled
      refresh={refresh}
    />,
  );
  await userEvent.click(screen.getByRole("checkbox", { name: /may replace current observation/ }));
  await userEvent.click(screen.getByRole("button", { name: "Reserve read-only inspection" }));
  await userEvent.type(await screen.findByLabelText("Database username"), "operator");
  await userEvent.type(screen.getByLabelText("Database password"), "temporary-secret");
  await userEvent.click(screen.getByRole("button", { name: "Send credentials once" }));
  await waitFor(() =>
    expect(credentialsV3).toHaveBeenCalledWith(
      operation.operationId,
      "operator",
      "temporary-secret",
    ),
  );
  expect(screen.queryByDisplayValue("operator")).not.toBeInTheDocument();
  expect(refresh).toHaveBeenCalled();
});
