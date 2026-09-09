import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { ApiFailure, HostedApi } from "../api/hosted";
import { HostedWorkspace } from "./HostedWorkspace";

afterEach(() => vi.restoreAllMocks());

it("retains an uncertain definition command across workspace navigation", async () => {
  vi.spyOn(HostedApi.prototype, "session").mockResolvedValue({
    authenticated: true,
    csrfHeaderName: "X-CSRF",
    csrfToken: "mock-token",
    idleTimeoutSeconds: 1800,
    absoluteExpiresAt: new Date(Date.now() + 28_800_000).toISOString(),
  });
  vi.spyOn(HostedApi.prototype, "get").mockImplementation(async <T,>(path: string): Promise<T> => {
    if (path === "/api/v2/definitions") return { definitions: [], canPublish: false } as T;
    if (path === "/api/v1/destinations") return { destinations: [] } as T;
    throw new ApiFailure(404, "NOT_FOUND");
  });
  const put = vi
    .spyOn(HostedApi.prototype, "put")
    .mockRejectedValue(new ApiFailure(0, "NETWORK_UNCERTAIN"));
  const user = userEvent.setup();
  render(
    <HostedWorkspace
      capabilities={{
        mode: "hosted",
        definitionWorkspaceEnabled: true,
        inspectionEnabled: false,
        inspectionUiEnabled: false,
        inspectionApiConfigured: false,
        exportEnabled: false,
        blockers: [],
      }}
    />,
  );
  await user.click(await screen.findByRole("button", { name: "Definitions" }));
  await user.click(screen.getByLabelText("Native definition source", { exact: true }));
  await user.paste("{}");
  await user.click(screen.getByRole("button", { name: "Save immutable draft" }));
  expect(await screen.findByRole("button", { name: "Retry original command" })).toBeVisible();
  await user.click(screen.getByRole("button", { name: "Plans" }));
  await user.click(screen.getByRole("button", { name: "Definitions" }));
  expect(screen.getByRole("button", { name: "Save immutable draft" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "Retry original command" })).toBeVisible();
  expect(put).toHaveBeenCalledOnce();
});
