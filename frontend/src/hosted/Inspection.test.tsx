import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import type { HostedApi, Plan } from "../api/hosted";
import { Inspection } from "./Inspection";

const plan: Plan = {
  planId: "mock-plan",
  revision: "9007199254740993",
  definition: { objectId: "mock-definition", workspaceRevision: "2" },
  bindingId: "mock-binding",
  destinationId: "mock-destination",
  currentCounts: { documents: 0, entities: 0, relations: 0 },
  targetCounts: { documents: 0, entities: 0, relations: 0 },
  inspectionValid: false,
  targetComplete: false,
  exportAvailable: false,
  blockers: ["INSPECTION_REQUIRED"],
};
it("blocks inspection before capability approval without displaying credential fields", () => {
  render(<Inspection api={{} as HostedApi} plan={plan} enabled={false} refresh={vi.fn()} />);
  expect(screen.getByRole("button", { name: "Reserve inspection" })).toBeDisabled();
  expect(screen.queryByLabelText("Database password")).not.toBeInTheDocument();
});
it("reserves before credentials and clears both fields immediately on one-shot send", async () => {
  const user = userEvent.setup();
  const post = vi.fn().mockResolvedValue({
    planId: plan.planId,
    revision: plan.revision,
    operationId: "mock-operation",
  });
  const credentials = vi.fn().mockReturnValue(new Promise(() => {}));
  render(
    <Inspection
      api={{ post, credentials } as unknown as HostedApi}
      plan={plan}
      enabled
      refresh={vi.fn()}
    />,
  );
  expect(screen.getByRole("button", { name: "Reserve inspection" })).toBeDisabled();
  expect(post).not.toHaveBeenCalled();
  await user.click(
    screen.getByLabelText(
      "Successful inspection may replace current configuration and discard target changes.",
    ),
  );
  await user.click(screen.getByRole("button", { name: "Reserve inspection" }));
  expect(post.mock.calls[0][1].expectedRevision).toBe("9007199254740993");
  expect(post.mock.calls[0][1].discardDraftOnSuccess).toBe(true);
  await user.type(screen.getByLabelText("Database username"), "mock-reader");
  await user.type(screen.getByLabelText("Database password"), "mock-password");
  await user.click(screen.getByRole("button", { name: "Send credentials once" }));
  expect(screen.queryByDisplayValue("mock-reader")).not.toBeInTheDocument();
  expect(screen.queryByDisplayValue("mock-password")).not.toBeInTheDocument();
  expect(credentials).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole("button", { name: "Send credentials once" })).not.toBeInTheDocument();
});
