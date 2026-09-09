import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { ApiFailure, type Definition, type HostedApi } from "../api/hosted";
import { Definitions } from "./Definitions";

const result: Definition = {
  objectId: "mock-object",
  workspaceRevision: "9007199254740993",
  source: "{}",
  sourceDigest: "mock",
  state: "draft",
  format: "JSON",
  projection: {
    kind: "ready-to-publish",
    diagnostics: [],
    model: {
      id: "mock-types",
      revision: "1",
      logical: { entityTypes: [], relations: [] },
      bindings: [
        {
          id: "mock-binding",
          engine: "postgresql",
          documents: [{ id: "first" }, { id: "second" }],
        },
      ],
    },
  },
};
it("requires live maintainer eligibility, every document policy and explicit disclosure before publication", async () => {
  const user = userEvent.setup();
  const get = vi.fn().mockResolvedValue({ definitions: [], canPublish: true });
  const put = vi.fn().mockResolvedValue(result);
  const post = vi
    .fn()
    .mockResolvedValue({ ...result, state: "published", workspaceRevision: "9007199254740994" });
  render(
    <Definitions api={{ get, put, post } as unknown as HostedApi} enabled changed={vi.fn()} />,
  );
  await user.click(screen.getByLabelText("Native definition source", { exact: true }));
  await user.paste("{}");
  await user.click(screen.getByRole("button", { name: "Save immutable draft" }));
  expect(put.mock.calls[0][1].format).toBe("JSON");
  const publish = await screen.findByRole("button", { name: "Publish definition" });
  expect(publish).toBeDisabled();
  await user.selectOptions(screen.getByLabelText("mock-binding / first export policy"), "deny");
  await user.click(
    screen.getByLabelText("I reviewed complete-document disclosure and every document policy."),
  );
  expect(publish).toBeDisabled();
  await user.selectOptions(
    screen.getByLabelText("mock-binding / second export policy"),
    "protected-self-contained",
  );
  expect(publish).toBeEnabled();
  await user.click(publish);
  expect(post.mock.calls[0][1].expectedRevision).toBe("9007199254740993");
  expect(post.mock.calls[0][1].exportPolicies).toHaveLength(2);
});
it("keeps conflict refusal explicit and never claims a failed save succeeded", async () => {
  const user = userEvent.setup();
  const put = vi.fn().mockRejectedValue(new ApiFailure(409, "WORKSPACE_CONFLICT"));
  render(
    <Definitions
      api={
        {
          get: vi.fn().mockResolvedValue({ definitions: [], canPublish: false }),
          put,
        } as unknown as HostedApi
      }
      enabled
      changed={vi.fn()}
    />,
  );
  await user.click(screen.getByLabelText("Native definition source", { exact: true }));
  await user.paste("{}");
  await user.click(screen.getByRole("button", { name: "Save immutable draft" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Reload the authoritative revision");
  expect(screen.queryByText(/workspace revision/)).not.toBeInTheDocument();
  expect(put).toHaveBeenCalledOnce();
});
it("blocks a new save after uncertain delivery until the workspace is reconciled", async () => {
  const user = userEvent.setup();
  const put = vi.fn().mockRejectedValue(new ApiFailure(0, "NETWORK_UNCERTAIN"));
  render(
    <Definitions
      api={
        {
          get: vi.fn().mockResolvedValue({ definitions: [], canPublish: false }),
          put,
        } as unknown as HostedApi
      }
      enabled
      changed={vi.fn()}
    />,
  );
  await user.click(screen.getByLabelText("Native definition source", { exact: true }));
  await user.paste("{}");
  await user.click(screen.getByRole("button", { name: "Save immutable draft" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("NETWORK_UNCERTAIN");
  expect(screen.getByRole("button", { name: "Save immutable draft" })).toBeDisabled();
});
it("replays the original immutable save after response loss without creating another object or request", async () => {
  const user = userEvent.setup();
  const put = vi
    .fn()
    .mockRejectedValueOnce(new ApiFailure(0, "NETWORK_UNCERTAIN"))
    .mockResolvedValue(result);
  const changed = vi.fn();
  render(
    <Definitions
      api={
        {
          get: vi.fn().mockResolvedValue({ definitions: [], canPublish: false }),
          put,
        } as unknown as HostedApi
      }
      enabled
      changed={changed}
    />,
  );
  await user.click(screen.getByLabelText("Native definition source", { exact: true }));
  await user.paste("{}");
  await user.click(screen.getByRole("button", { name: "Save immutable draft" }));
  const retry = await screen.findByRole("button", { name: "Retry original command" });
  expect(put).toHaveBeenCalledOnce();
  expect(changed).not.toHaveBeenCalled();
  expect(screen.getByRole("button", { name: "New draft" })).toBeDisabled();
  expect(screen.getByLabelText("Native definition source", { exact: true })).toBeDisabled();
  await user.click(retry);
  expect(put).toHaveBeenCalledTimes(2);
  expect(put.mock.calls[1]).toEqual(put.mock.calls[0]);
  expect(put.mock.calls[1][1]).toEqual({
    expectedRevision: "0",
    requestId: expect.any(String),
    format: "JSON",
    source: "{}",
  });
  expect(await screen.findByText(/workspace revision 9007199254740993/)).toBeVisible();
  expect(screen.queryByRole("button", { name: "Retry original command" })).not.toBeInTheDocument();
  expect(changed).toHaveBeenCalledOnce();
});
it("retains the exact publication policies after an unreadable success response", async () => {
  const user = userEvent.setup();
  const post = vi
    .fn()
    .mockRejectedValueOnce(new ApiFailure(200, "RESPONSE_UNAVAILABLE"))
    .mockResolvedValue({ ...result, state: "published", workspaceRevision: "9007199254740994" });
  render(
    <Definitions
      api={
        {
          get: vi.fn().mockResolvedValue({ definitions: [], canPublish: true }),
          put: vi.fn().mockResolvedValue(result),
          post,
        } as unknown as HostedApi
      }
      enabled
      changed={vi.fn()}
    />,
  );
  await user.click(screen.getByLabelText("Native definition source", { exact: true }));
  await user.paste("{}");
  await user.click(screen.getByRole("button", { name: "Save immutable draft" }));
  await user.selectOptions(
    await screen.findByLabelText("mock-binding / first export policy"),
    "deny",
  );
  await user.selectOptions(
    screen.getByLabelText("mock-binding / second export policy"),
    "protected-self-contained",
  );
  await user.click(
    screen.getByLabelText("I reviewed complete-document disclosure and every document policy."),
  );
  await user.click(screen.getByRole("button", { name: "Publish definition" }));
  await user.click(await screen.findByRole("button", { name: "Retry original command" }));
  expect(post).toHaveBeenCalledTimes(2);
  expect(post.mock.calls[1]).toEqual(post.mock.calls[0]);
  expect(await screen.findByText(/workspace revision 9007199254740994 · published/)).toBeVisible();
});
