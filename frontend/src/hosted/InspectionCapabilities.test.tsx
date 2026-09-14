import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import App from "../App";
import { ApiFailure, HostedApi } from "../api/hosted";

afterEach(() => vi.restoreAllMocks());

const capabilities = {
  mode: "hosted",
  definitionWorkspaceEnabled: true,
  inspectionEnabled: false,
  inspectionUiEnabled: false,
  inspectionApiConfigured: true,
  exportEnabled: false,
  blockers: ["DATABASE_ADAPTERS_NOT_QUALIFIED"],
};

function respond(value: object) {
  vi.spyOn(HostedApi.prototype, "session").mockResolvedValue({
    authenticated: true,
    csrfHeaderName: "X-CSRF",
    csrfToken: "mock-token",
    idleTimeoutSeconds: 1800,
    absoluteExpiresAt: new Date(Date.now() + 28_800_000).toISOString(),
  });
  vi.spyOn(HostedApi.prototype, "get").mockImplementation(async <T,>(path: string): Promise<T> => {
    if (path === "/api/v1/capabilities") return value as T;
    if (path === "/api/v2/definitions") return { definitions: [], canPublish: false } as T;
    if (path === "/api/v3/definitions") return { definitions: [] } as T;
    if (path === "/api/v1/destinations") return { destinations: [] } as T;
    if (path === "/api/v1/plans/current")
      return {
        planId: "mock-plan",
        revision: "1",
        definition: { objectId: "mock-definition", workspaceRevision: "2" },
        bindingId: "mock-binding",
        destinationId: "mock-destination",
        currentCounts: { documents: 0, entities: 0, relations: 0 },
        targetCounts: { documents: 0, entities: 0, relations: 0 },
        inspectionValid: false,
        targetComplete: false,
        exportAvailable: false,
        blockers: ["INSPECTION_REQUIRED"],
      } as T;
    throw new ApiFailure(404, "NOT_FOUND");
  });
}

it.each([
  { inspectionUiEnabled: undefined },
  { inspectionApiConfigured: undefined },
  { inspectionUiEnabled: "true" },
  { inspectionApiConfigured: "true" },
  { inspectionEnabled: true },
])("refuses an ambiguous inspection capability response: %j", async (change) => {
  respond({ ...capabilities, ...change });
  render(<App />);
  expect(await screen.findByRole("heading", { name: "Workspace unavailable" })).toBeVisible();
  expect(screen.queryByRole("button", { name: "Reserve inspection" })).not.toBeInTheDocument();
});

it("configured API availability leaves the unqualified inspection UI disabled", async () => {
  respond(capabilities);
  const post = vi.spyOn(HostedApi.prototype, "post");
  render(<App />);
  await screen.findByRole("heading", { name: "PostgreSQL pilot workspace" });
  await userEvent
    .setup()
    .selectOptions(screen.getByRole("combobox", { name: "Compatibility mode" }), "2");
  const inspection = await screen.findByRole(
    "region",
    { name: "Inspect configuration" },
    { timeout: 10_000 },
  );
  expect(within(inspection).getByRole("button", { name: "Reserve inspection" })).toBeDisabled();
  expect(
    within(inspection).getByRole("checkbox", { name: /Successful inspection may replace/ }),
  ).toBeDisabled();
  expect(
    within(inspection).getByText("Browser inspection is not yet available in this deployment."),
  ).toBeVisible();
  expect(screen.queryByLabelText("Database password")).not.toBeInTheDocument();
  expect(post).not.toHaveBeenCalled();
});
