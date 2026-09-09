import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { ApiFailure, type Definition, HostedApi, type Plan } from "../api/hosted";
import { DocumentComparison } from "./Documents";
import { HostedWorkspace } from "./HostedWorkspace";
import { Inspection } from "./Inspection";
import { Plans } from "./Plans";

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});
const plan: Plan = {
  planId: "mock-plan",
  revision: "1",
  definition: { objectId: "mock-definition", workspaceRevision: "2" },
  bindingId: "mock-binding",
  destinationId: "mock-destination",
  currentCounts: { documents: 2, entities: 0, relations: 0 },
  targetCounts: { documents: 0, entities: 0, relations: 0 },
  inspectionValid: false,
  targetComplete: false,
  exportAvailable: false,
  blockers: ["INSPECTION_REQUIRED"],
};
const definition: Definition = {
  objectId: "mock-definition",
  workspaceRevision: "2",
  source: "{}",
  sourceDigest: "mock-digest",
  state: "published",
  format: "json",
  projection: {
    kind: "ready-to-publish",
    diagnostics: [],
    model: {
      id: "mock",
      revision: "1",
      logical: { entityTypes: [], relations: [] },
      bindings: [{ id: "mock-binding", engine: "postgresql", documents: [] }],
    },
  },
};
const discard =
  "Successful inspection may replace current configuration and discard target changes.";
const disclose =
  "Show the complete selected document, including unchanged or unmapped content and readable secrets.";

it("loads the filtered selected document only after new disclosure consent", async () => {
  const post = vi.fn().mockImplementation(async (path, body) =>
    path.endsWith("/documents")
      ? {
          revision: "1",
          documents: [
            { documentId: "alpha", changed: null },
            { documentId: "beta", changed: null },
          ],
        }
      : {
          revision: "1",
          documentId: body.documentId,
          side: body.side,
          mode: body.mode,
          text: "mock",
          exact: true,
          redacted: false,
          omissions: [],
        },
  );
  render(<DocumentComparison api={{ post } as unknown as HostedApi} plan={plan} />);
  await screen.findByRole("option", { name: "beta · Unknown" });
  const user = userEvent.setup();
  await user.click(screen.getByLabelText(disclose));
  await user.type(screen.getByLabelText("Filter documents"), "beta");
  expect(screen.getByLabelText("Selected document")).toHaveValue("beta");
  expect(screen.getByLabelText(disclose)).not.toBeChecked();
  await user.click(screen.getByLabelText(disclose));
  await user.click(screen.getByRole("button", { name: "Load document comparison" }));
  expect(post.mock.calls.find(([path]) => path.endsWith("/document"))?.[1].documentId).toBe("beta");
});

it("can explicitly reload inventory after a refused initial view read", async () => {
  const post = vi
    .fn()
    .mockRejectedValueOnce(new ApiFailure(429, "CAPACITY"))
    .mockResolvedValue({ revision: "1", documents: [{ documentId: "recovered", changed: false }] });
  render(<DocumentComparison api={{ post } as unknown as HostedApi} plan={plan} />);
  await screen.findByRole("alert");
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Reload document inventory" }));
  expect(await screen.findByRole("option", { name: "recovered · Unchanged" })).toBeVisible();
  expect(post.mock.calls[1]).toEqual(post.mock.calls[0]);
  expect(screen.getByLabelText(disclose)).not.toBeChecked();
});

it("recovers status polling after one transient failure without submitting credentials", async () => {
  vi.useFakeTimers();
  const get = vi
    .fn()
    .mockRejectedValueOnce(new ApiFailure(0, "NETWORK_UNCERTAIN"))
    .mockResolvedValue({
      operationId: "mock-operation",
      planId: plan.planId,
      phase: "succeeded",
      code: "COMPLETE",
      cleanup: "complete",
    });
  const credentials = vi.fn();
  render(
    <Inspection
      api={{ get, credentials } as unknown as HostedApi}
      plan={{ ...plan, activeOperationId: "mock-operation" }}
      enabled
      refresh={vi.fn()}
    />,
  );
  await act(async () => vi.advanceTimersByTimeAsync(1000));
  await act(async () => vi.advanceTimersByTimeAsync(2000));
  expect(get).toHaveBeenCalledTimes(2);
  expect(credentials).not.toHaveBeenCalled();
});

it("clears retained source at known absolute expiry without another request", async () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-09-09T12:00:00Z"));
  vi.spyOn(HostedApi.prototype, "session").mockResolvedValue({
    authenticated: true,
    csrfHeaderName: "X-CSRF",
    csrfToken: "mock-token",
    idleTimeoutSeconds: 1800,
    absoluteExpiresAt: "2026-09-09T12:00:01Z",
  });
  vi.spyOn(HostedApi.prototype, "get").mockImplementation(async <T,>(path: string): Promise<T> => {
    if (path === "/api/v2/definitions") return { definitions: [], canPublish: false } as T;
    if (path === "/api/v1/destinations") return { destinations: [] } as T;
    throw new ApiFailure(404, "NOT_FOUND");
  });
  render(
    <HostedWorkspace
      capabilities={{
        mode: "hosted",
        definitionWorkspaceEnabled: true,
        inspectionEnabled: true,
        inspectionUiEnabled: true,
        inspectionApiConfigured: true,
        exportEnabled: false,
        blockers: [],
      }}
    />,
  );
  await act(async () => {
    await Promise.resolve();
  });
  fireEvent.change(screen.getByLabelText("Native definition source", { exact: true }), {
    target: { value: "mock-expiry-source" },
  });
  await act(async () => vi.advanceTimersByTimeAsync(2000));
  expect(screen.queryByDisplayValue("mock-expiry-source")).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
});

it("retains the exact uncertain plan create command and locks its inputs", async () => {
  const post = vi
    .fn()
    .mockRejectedValueOnce(new ApiFailure(0, "NETWORK_UNCERTAIN"))
    .mockResolvedValue({ planId: plan.planId, revision: "1" });
  const get = vi.fn().mockImplementation(async (path) => {
    if (path === "/api/v2/definitions")
      return {
        definitions: [{ ...definition, nativeId: "mock", nativeRevision: "1" }],
        canPublish: true,
      };
    if (path === "/api/v1/destinations")
      return {
        destinations: [
          {
            id: "mock-destination",
            engine: "postgresql",
            host: "localhost",
            port: 1,
            database: "mock",
          },
        ],
      };
    if (path === "/api/v2/definitions/mock-definition") return definition;
    if (path === "/api/v1/plans/mock-plan")
      return { ...plan, currentCounts: { ...plan.currentCounts, documents: 0 } };
    throw new ApiFailure(404, "NOT_FOUND");
  });
  render(<Plans api={{ get, post } as unknown as HostedApi} inspectionUiEnabled />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "mock · workspace 2" });
  await user.selectOptions(
    screen.getByLabelText("Published definition", { exact: true }),
    "mock-definition",
  );
  await user.selectOptions(screen.getByLabelText("Binding", { exact: true }), "mock-binding");
  await user.selectOptions(
    screen.getByLabelText("Configured destination", { exact: true }),
    "mock-destination",
  );
  await user.click(screen.getByRole("button", { name: "Create plan" }));
  expect(screen.getByRole("button", { name: "Create plan" })).toBeDisabled();
  expect(screen.getByLabelText("Published definition", { exact: true })).toBeDisabled();
  const original = structuredClone(post.mock.calls[0]);
  await user.click(screen.getByRole("button", { name: "Retry original plan command" }));
  expect(post.mock.calls[1]).toEqual(original);
  expect(await screen.findByRole("region", { name: "Persistent plan context" })).toBeVisible();
});

it("retries an uncertain reservation exactly and solicits credentials only after its acknowledgement", async () => {
  const post = vi
    .fn()
    .mockRejectedValueOnce(new ApiFailure(0, "NETWORK_UNCERTAIN"))
    .mockResolvedValue({ planId: plan.planId, revision: "1", operationId: "mock-operation" });
  const api = { post } as unknown as HostedApi;
  const view = render(<Inspection api={api} plan={plan} enabled refresh={vi.fn()} />);
  const user = userEvent.setup();
  await user.click(screen.getByLabelText(discard));
  await user.click(screen.getByRole("button", { name: "Reserve inspection" }));
  expect(screen.getByRole("button", { name: "Reserve inspection" })).toBeDisabled();
  expect(screen.queryByLabelText("Database password")).not.toBeInTheDocument();
  const original = structuredClone(post.mock.calls[0]);
  view.rerender(
    <Inspection api={api} plan={{ ...plan, revision: "2" }} enabled refresh={vi.fn()} />,
  );
  await user.click(screen.getByRole("button", { name: "Retry original reservation" }));
  expect(post.mock.calls[1]).toEqual(original);
  expect(screen.getByLabelText("Database password")).toBeVisible();
});

it("allows a new explicitly confirmed inspection after terminal confirmed cleanup", async () => {
  vi.useFakeTimers();
  const get = vi.fn().mockResolvedValue({
    operationId: "mock-operation",
    planId: plan.planId,
    phase: "succeeded",
    code: "COMPLETE",
    cleanup: "complete",
  });
  render(
    <Inspection
      api={{ get } as unknown as HostedApi}
      plan={{ ...plan, activeOperationId: "mock-operation" }}
      enabled
      refresh={vi.fn()}
    />,
  );
  await act(async () => vi.advanceTimersByTimeAsync(1000));
  fireEvent.click(screen.getByRole("button", { name: "Start another inspection" }));
  expect(screen.getByLabelText(discard)).not.toBeChecked();
  expect(screen.getByRole("button", { name: "Reserve inspection" })).toBeDisabled();
});

it("resumes a newly observed active operation without soliciting its credentials again", async () => {
  const api = { get: vi.fn().mockReturnValue(new Promise(() => {})) } as unknown as HostedApi;
  const refresh = vi.fn();
  const view = render(<Inspection api={api} plan={plan} enabled refresh={refresh} />);
  view.rerender(
    <Inspection
      api={api}
      plan={{ ...plan, activeOperationId: "mock-resumed" }}
      enabled
      refresh={refresh}
    />,
  );
  expect(screen.getByText("mock-resumed")).toBeVisible();
  expect(screen.queryByLabelText("Database password")).not.toBeInTheDocument();
});

it("ignores a late cancellation reply after a subsequent inspection was reserved", async () => {
  vi.useFakeTimers();
  let finishCancel!: (status: object) => void;
  const oldStatus = {
    operationId: "mock-operation",
    planId: plan.planId,
    phase: "succeeded",
    code: "COMPLETE",
    cleanup: "complete",
  };
  const post = vi.fn().mockImplementation(async (path) =>
    path.endsWith("/cancel")
      ? new Promise((resolve) => {
          finishCancel = resolve;
        })
      : { planId: plan.planId, revision: "1", operationId: "mock-next" },
  );
  const api = { post, get: vi.fn().mockResolvedValue(oldStatus) } as unknown as HostedApi;
  render(
    <Inspection
      api={api}
      plan={{ ...plan, activeOperationId: "mock-operation" }}
      enabled
      refresh={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Cancel operation" }));
  await act(async () => vi.advanceTimersByTimeAsync(1000));
  fireEvent.click(screen.getByRole("button", { name: "Start another inspection" }));
  fireEvent.click(screen.getByLabelText(discard));
  await act(async () => {
    fireEvent.click(screen.getByRole("button", { name: "Reserve inspection" }));
  });
  expect(screen.getByLabelText("Database password")).toBeVisible();
  await act(async () => finishCancel(oldStatus));
  expect(screen.getByLabelText("Database password")).toBeVisible();
  expect(screen.getByText("mock-next")).toBeVisible();
});
