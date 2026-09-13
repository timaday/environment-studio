import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary } from "../api/hostedV3Types";
import { type TargetValueInput, useV3TargetValues } from "./useV3TargetValues";

// Independently invented wire fixtures; no database or publication qualification.
const id = "76000000-0000-0000-0000-000000000001";
const entity = { kind: "existing" as const, handle: "76000000-0000-0000-0000-000000000002" };
const plan: PlanSummary = {
  planId: id,
  revision: "2",
  definition: { objectId: "76000000-0000-0000-0000-000000000003", workspaceRevision: "1" },
  bindingId: "mock-binding",
  destinationId: "mock-destination",
  currentCounts: { documents: 1, entities: 1, relations: 0 },
  targetCounts: { documents: 0, entities: 0, relations: 0 },
  inspectionValid: true,
  targetComplete: false,
  exportAvailable: false,
  blockers: [],
  observedDestination: {
    engine: "postgresql",
    identity: { systemIdentifier: "1", databaseOid: "2", databaseName: "invented_db" },
    observationFingerprint: "d".repeat(64),
    evidenceValid: true,
  },
  currentComputedCounts: null,
  targetComputedCounts: null,
};
const input: TargetValueInput = {
  entity,
  fieldId: "input",
  state: { kind: "entered", text: "Invented text" },
};
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  cleanup();
  for (const api of owners) api.clear();
  owners.length = 0;
});
async function setup() {
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (String(path).endsWith("/commands")) return json({ planId: id, revision: "3" });
    throw new Error("Unexpected invented target value route");
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const props: { api: HostedApi; plan: PlanSummary | null; enabled: boolean } = {
    api,
    plan,
    enabled: true,
  };
  const hook = renderHook(
    ({ api, plan, enabled }: typeof props) => useV3TargetValues(api, plan, enabled),
    { initialProps: props },
  );
  return { ...hook, props, transport };
}
const commands = (transport: ReturnType<typeof vi.fn<typeof fetch>>) =>
  transport.mock.calls.filter(([path]) => String(path).endsWith("/commands"));
it.each<TargetValueInput["state"]>([
  { kind: "entered", text: "" },
  { kind: "entered", text: "Invented 🧪 value" },
  { kind: "keep-observed" },
  { kind: "absent" },
  { kind: "unresolved" },
])(
  "submits explicit field state %j without materializing or substituting values",
  async (state) => {
    const { result, transport } = await setup();
    expect(commands(transport)).toHaveLength(0);
    await act(() => result.current.submit({ ...input, state }));
    const sent = commands(transport);
    expect(sent).toHaveLength(1);
    expect(JSON.parse(String(sent[0][1]?.body))).toEqual({
      kind: "bind-field",
      expectedRevision: "2",
      requestId: expect.any(String),
      entity,
      fieldId: "input",
      state,
    });
    expect(result.current.receipt).toEqual({ planId: id, revision: "3" });
    expect(result.current.pending).toBeNull();
    expect(transport.mock.calls).toHaveLength(2);
  },
);
it("retains original immutable command across context changes and requires explicit exact retry", async () => {
  const { result, transport, rerender, props } = await setup();
  transport.mockRejectedValueOnce(new TypeError("Synthetic response loss"));
  const mutable = { ...input, state: { kind: "entered" as const, text: "First invented value" } };
  await act(() => result.current.submit(mutable));
  expect(result.current.pending?.command.state).toEqual(mutable.state);
  mutable.state.text = "Changed after submission";
  rerender({ ...props, enabled: false });
  await act(() => result.current.retry());
  rerender({
    ...props,
    plan: { ...plan, planId: "76000000-0000-0000-0000-000000000004", revision: "8" },
  });
  act(() => result.current.clear());
  await act(() => result.current.submit(input));
  expect(commands(transport)).toHaveLength(1);
  expect(result.current.pending?.plan.planId).toBe(id);
  await act(() => result.current.retry());
  const sent = commands(transport);
  expect(sent).toHaveLength(2);
  expect(sent[1][0]).toBe(sent[0][0]);
  expect(sent[1][1]?.body).toBe(sent[0][1]?.body);
  expect(JSON.parse(String(sent[1][1]?.body)).state.text).toBe("First invented value");
  expect(result.current.receipt).toEqual({ planId: id, revision: "3" });
});

it.each([
  [400, "INVALID_REQUEST"],
  [404, "NOT_FOUND"],
  [409, "CONFLICT"],
  [409, "STALE_PREVIEW"],
  [429, "CAPACITY"],
] as const)("releases confirmed refusal %s:%s without automatic retry", async (status, code) => {
  const { result, transport } = await setup();
  transport.mockResolvedValueOnce(json({ code }, status));
  await act(() => result.current.submit(input));
  expect(result.current.pending).toBeNull();
  expect(result.current.receipt).toBeNull();
  expect(result.current.error).toContain(code);
  await act(() => result.current.retry());
  expect(commands(transport)).toHaveLength(1);
  await act(() => result.current.submit({ ...input, state: { kind: "unresolved" } }));
  expect(commands(transport)).toHaveLength(2);
});
it.each([403, 413, 500, "malformed", "foreign"])(
  "retains uncertain %s result without inventing success",
  async (fault) => {
    const { result, transport } = await setup();
    transport.mockResolvedValueOnce(
      typeof fault === "number"
        ? json(
            {
              code:
                fault === 403 ? "FORBIDDEN" : fault === 413 ? "RESOURCE_LIMIT" : "INTERNAL_ERROR",
            },
            fault,
          )
        : json(
            fault === "foreign"
              ? { planId: "76000000-0000-0000-0000-000000000009", revision: "3" }
              : { planId: id },
          ),
    );
    await act(() => result.current.submit(input));
    expect(result.current.pending).not.toBeNull();
    expect(result.current.receipt).toBeNull();
    await act(() => result.current.submit(input));
    expect(commands(transport)).toHaveLength(1);
  },
);
it("rejects malformed local input before dispatch and never infers a blank state", async () => {
  const { result, transport } = await setup();
  await act(() =>
    result.current.submit({ ...input, state: { kind: "entered" } } as TargetValueInput),
  );
  expect(result.current.error).toBe("INVALID_REQUEST");
  expect(commands(transport)).toHaveLength(0);
});
it("retires values on session refusal and cannot replay after reentry", async () => {
  const { result, transport, props, rerender } = await setup();
  transport.mockResolvedValueOnce(json({ code: "SESSION_REQUIRED" }, 401));
  await act(() => result.current.submit(input));
  expect(result.current.pending).toBeNull();
  expect(result.current.error).toBe("SESSION_REQUIRED");
  rerender({ ...props, enabled: false });
  rerender(props);
  await act(() => result.current.retry());
  await act(() => result.current.submit(input));
  expect(commands(transport)).toHaveLength(1);
});
it("blocks overlapping submissions while a response is held and retains its acknowledgement across navigation", async () => {
  const { result, transport, props, rerender } = await setup();
  let release: ((response: Response) => void) | undefined;
  transport.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        release = resolve;
      }),
  );
  let original: Promise<void> | undefined;
  act(() => {
    original = result.current.submit(input);
  });
  expect(result.current.busy).toBe(true);
  await act(() => result.current.submit(input));
  await act(() => result.current.retry());
  act(() => result.current.clear());
  expect(commands(transport)).toHaveLength(1);
  rerender({ ...props, enabled: false });
  rerender({ ...props, plan: { ...plan, revision: "8" } });
  if (!release) throw new Error("Command not held");
  await act(async () => {
    release?.(json({ planId: id, revision: "3" }));
    await original;
  });
  expect(result.current.receipt).toEqual({ planId: id, revision: "3" });
  expect(result.current.pending).toBeNull();
});
it("does not accept stale submit callbacks after context retirement", async () => {
  const { result, transport, props, rerender } = await setup();
  const retired = result.current.submit;
  rerender({ ...props, plan: { ...plan, revision: "8" } });
  await act(() => retired(input));
  expect(commands(transport)).toHaveLength(0);
});
it.each(["invalid", "missing", "inactive"])(
  "requires %s inspection prerequisites without an automatic request",
  async (fault) => {
    const { result, transport, props, rerender } = await setup();
    rerender({
      ...props,
      enabled: fault !== "inactive",
      plan: fault === "missing" ? null : { ...plan, inspectionValid: fault !== "invalid" },
    });
    await act(() => result.current.submit(input));
    expect(commands(transport)).toHaveLength(0);
  },
);
it("retires held work on API replacement without publishing old values or acknowledgement", async () => {
  const { result, transport, props, rerender } = await setup();
  let release: ((response: Response) => void) | undefined;
  transport.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        release = resolve;
      }),
  );
  let original: Promise<void> | undefined;
  act(() => {
    original = result.current.submit(input);
  });
  const replacement = new HostedApi(vi.fn<typeof fetch>());
  owners.push(replacement);
  rerender({ ...props, api: replacement });
  expect(result.current.pending).toBeNull();
  expect(result.current.busy).toBe(false);
  if (!release) throw new Error("Command not held");
  await act(async () => {
    release?.(json({ planId: id, revision: "3" }));
    await original;
  });
  expect(result.current.receipt).toBeNull();
  expect(result.current.pending).toBeNull();
});

it("preserves explicit fresh references and generates a distinct request ID for a later submission", async () => {
  const { result, transport } = await setup();
  const fresh = { kind: "fresh" as const, slotId: "new-item", typeId: "mock-type" };
  await act(() =>
    result.current.submit({ ...input, entity: fresh, state: { kind: "unresolved" } }),
  );
  act(() => result.current.clear());
  expect(result.current.receipt).toBeNull();
  await act(() =>
    result.current.submit({ ...input, entity: fresh, state: { kind: "entered", text: "" } }),
  );
  const sent = commands(transport).map(([, options]) => JSON.parse(String(options?.body)));
  expect(sent).toHaveLength(2);
  expect(sent[0].entity).toEqual(fresh);
  expect(sent[1].entity).toEqual(fresh);
  expect(sent[0].requestId).not.toBe(sent[1].requestId);
});
it("does not restore state after unmount with a held original command", async () => {
  const { result, transport, unmount } = await setup();
  let release: ((response: Response) => void) | undefined;
  transport.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        release = resolve;
      }),
  );
  let original: Promise<void> | undefined;
  act(() => {
    original = result.current.submit(input);
  });
  const retry = result.current.retry;
  unmount();
  if (!release) throw new Error("Command not held");
  await act(async () => {
    release?.(json({ planId: id, revision: "3" }));
    await original;
    await retry();
  });
  expect(commands(transport)).toHaveLength(1);
});

it("retires a saved retry callback on navigation but allows a new explicit callback to replay", async () => {
  const { result, transport, props, rerender } = await setup();
  transport.mockRejectedValueOnce(new TypeError("Synthetic loss"));
  await act(() => result.current.submit(input));
  const retired = result.current.retry;
  rerender({ ...props, enabled: false });
  rerender(props);
  await act(() => retired());
  expect(commands(transport)).toHaveLength(1);
  await act(() => result.current.retry());
  expect(commands(transport)).toHaveLength(2);
});
it("retains a detached original plan when caller-owned metadata changes", async () => {
  const { result, transport, props, rerender } = await setup();
  const mutable = { ...plan, destinationId: "original-destination" };
  rerender({ ...props, plan: mutable });
  transport.mockRejectedValueOnce(new TypeError("Synthetic loss"));
  await act(() => result.current.submit(input));
  mutable.destinationId = "replacement-destination";
  expect(result.current.pending?.plan.destinationId).toBe("original-destination");
  expect(Object.isFrozen(result.current.pending?.plan)).toBe(true);
});
