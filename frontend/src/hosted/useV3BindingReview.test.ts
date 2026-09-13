import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { BindingsRequest } from "../api/hostedV3Physical";
import type { PlanSummary } from "../api/hostedV3Types";
import { useV3BindingReview } from "./useV3BindingReview";

// Independently invented binding wire fixtures; no XML/database qualification.
const id = "77000000-0000-0000-0000-000000000001";
const entity = { kind: "existing" as const, handle: "77000000-0000-0000-0000-000000000002" };
const plan: PlanSummary = {
  planId: id,
  revision: "2",
  definition: { objectId: "77000000-0000-0000-0000-000000000003", workspaceRevision: "1" },
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
const fields = Array.from({ length: 101 }, (_, index) => {
  const fieldId = `field-${String(index).padStart(3, "0")}`;
  return {
    fieldId,
    token: `[[value:${entity.handle}:${fieldId}]]`,
    current: { state: "value" as const, text: index === 0 ? "" : "Invented value" },
    target: { state: "unresolved" as const },
    change: "unresolved" as const,
    currentLocations: { state: "complete" as const, total: 1 },
    targetLocations: { state: "unavailable" as const, code: "INCOMPLETE_TARGET" as const },
  };
});
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  cleanup();
  for (const api of owners) api.clear();
  owners.length = 0;
});
function page(request: BindingsRequest, items: readonly unknown[] = fields) {
  const end = Math.min(items.length, request.offset + request.limit);
  return {
    revision: request.revision,
    total: items.length,
    offset: request.offset,
    nextOffset: end < items.length ? end : null,
    items: items.slice(request.offset, end),
  };
}
async function setup() {
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path, options) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (String(path).endsWith("/views/bindings"))
      return json(page(JSON.parse(String(options?.body))));
    if (path === `/api/v3/plans/${id}`) return json(plan);
    throw new Error("Unexpected invented binding route");
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const props: {
    api: HostedApi;
    plan: PlanSummary | null;
    entity: BindingsRequest["entity"] | null;
    enabled: boolean;
  } = { api, plan, entity, enabled: true };
  const hook = renderHook(
    ({ api, plan, entity, enabled }: typeof props) =>
      useV3BindingReview(api, plan, entity, enabled),
    { initialProps: props },
  );
  return { ...hook, props, transport };
}
it("loads the complete bounded collection and preserves incomplete target and empty current text", async () => {
  const { result, transport } = await setup();
  expect(result.current.items).toBeNull();
  expect(transport.mock.calls).toHaveLength(1);
  await act(() => result.current.load());
  expect(result.current.items).toEqual(fields);
  expect(result.current.error).toBe("");
  const calls = transport.mock.calls.filter(([path]) => String(path).endsWith("/views/bindings"));
  expect(calls.map(([, options]) => JSON.parse(String(options?.body)))).toEqual([
    { revision: "2", entity, offset: 0, limit: 100 },
    { revision: "2", entity, offset: 100, limit: 100 },
  ]);
  expect(transport.mock.calls).toHaveLength(4);
});
it("does not publish a successful first page when the tail is refused", async () => {
  const { result, transport } = await setup();
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Mock transport missing");
  transport.mockImplementation(async (path, options) =>
    String(path).endsWith("/views/bindings") && JSON.parse(String(options?.body)).offset === 100
      ? json({ code: "CAPACITY" }, 429)
      : original(path, options),
  );
  await act(() => result.current.load());
  expect(result.current.items).toBeNull();
  expect(result.current.error).toContain("CAPACITY");
  expect(transport.mock.calls.some(([path]) => path === `/api/v3/plans/${id}`)).toBe(false);
});

it.each([
  "duplicate-field",
  "token-field",
  "revision",
  "total",
  "oversized",
  "fresh-context",
  "masked-text",
])("refuses %s without retaining partial values", async (fault) => {
  const { result, transport } = await setup();
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Mock transport missing");
  transport.mockImplementation(async (path, options) => {
    if (path === `/api/v3/plans/${id}` && fault === "fresh-context")
      return json({ ...plan, revision: "3" });
    if (String(path).endsWith("/views/bindings")) {
      const request = JSON.parse(String(options?.body)) as BindingsRequest;
      if (request.offset === 100) {
        const value = page(request);
        if (fault === "duplicate-field") return json({ ...value, items: [fields[0]] });
        if (fault === "token-field")
          return json({
            ...value,
            items: [{ ...fields[100], token: `[[value:${entity.handle}:other]]` }],
          });
        if (fault === "revision") return json({ ...value, revision: "3" });
        if (fault === "total")
          return json({
            ...value,
            total: 102,
            items: [
              fields[100],
              { ...fields[100], fieldId: "extra", token: `[[value:${entity.handle}:extra]]` },
            ],
          });
        if (fault === "oversized") return json({ ...value, total: 257 });
        if (fault === "masked-text")
          return json({
            ...value,
            items: [
              { ...fields[100], current: { state: "masked", text: "Forbidden invented text" } },
            ],
          });
      }
    }
    return original(path, options);
  });
  await act(() => result.current.load());
  expect(result.current.items).toBeNull();
  expect(result.current.error).not.toBe("");
});
it("preserves all value and location distinctions for a fresh reference without requiring a complete target", async () => {
  const { result, transport, props, rerender } = await setup();
  const fresh = { kind: "fresh" as const, slotId: "new-item", typeId: "mock-type" };
  rerender({ ...props, entity: fresh });
  const variants = ["masked", "absent", "unresolved", "unavailable"] as const;
  const rows = variants.map((state, index) => ({
    ...fields[index],
    current: { state },
    target: { state },
    currentLocations: { state: "unavailable", code: "CURRENT_ENTITY_ABSENT" },
    targetLocations: { state: "complete", total: 0 },
  }));
  transport.mockResolvedValueOnce(
    json({ revision: "2", total: rows.length, offset: 0, nextOffset: null, items: rows }),
  );
  await act(() => result.current.load());
  expect(result.current.items).toEqual(rows);
  expect(JSON.parse(String(transport.mock.calls[1][1]?.body)).entity).toEqual(fresh);
});
it("distinguishes a verified zero collection from unknown and clears a previous result on reload failure", async () => {
  const { result, transport } = await setup();
  expect(result.current.items).toBeNull();
  transport.mockResolvedValueOnce(
    json({ revision: "2", total: 0, offset: 0, nextOffset: null, items: [] }),
  );
  await act(() => result.current.load());
  expect(result.current.items).toEqual([]);
  transport.mockRejectedValueOnce(new TypeError("Synthetic response loss"));
  await act(() => result.current.load());
  expect(result.current.items).toBeNull();
  expect(result.current.error).not.toBe("");
});
it.each(["plan", "entity", "inactive", "api", "unmount"])(
  "retires a held page on %s without continuing or restoring values",
  async (event) => {
    const { result, transport, props, rerender, unmount } = await setup();
    let release: ((response: Response) => void) | undefined;
    transport.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    );
    let reading: Promise<void> | undefined;
    act(() => {
      reading = result.current.load();
    });
    const retired = result.current.load;
    if (event === "plan") rerender({ ...props, plan: { ...plan, revision: "3" } });
    else if (event === "entity")
      rerender({ ...props, entity: { kind: "fresh", slotId: "new-item", typeId: "mock-type" } });
    else if (event === "inactive") rerender({ ...props, enabled: false });
    else if (event === "api") {
      const replacement = new HostedApi(vi.fn<typeof fetch>());
      owners.push(replacement);
      rerender({ ...props, api: replacement });
    } else unmount();
    if (!release) throw new Error("Page not held");
    await act(async () => {
      release?.(json(page({ revision: "2", entity, offset: 0, limit: 100 })));
      await reading;
      await retired();
    });
    expect(transport.mock.calls).toHaveLength(2);
    if (event !== "unmount") expect(result.current.items).toBeNull();
  },
);
it("prevents overlapping loads and publishes only after final summary resolves", async () => {
  const { result, transport } = await setup();
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Mock transport missing");
  let release: ((response: Response) => void) | undefined;
  transport.mockImplementation((path, options) =>
    path === `/api/v3/plans/${id}`
      ? new Promise((resolve) => {
          release = resolve;
        })
      : original(path, options),
  );
  let reading: Promise<void> | undefined;
  await act(async () => {
    reading = result.current.load();
    await Promise.resolve();
  });
  expect(result.current.items).toBeNull();
  expect(result.current.busy).toBe(true);
  await act(() => result.current.load());
  expect(transport.mock.calls).toHaveLength(4);
  if (!release) throw new Error("Final summary not held");
  await act(async () => {
    release?.(json(plan));
    await reading;
  });
  expect(result.current.items).toEqual(fields);
});
it("retires session-refused reads without exposing old values or automatically retrying", async () => {
  const { result, transport } = await setup();
  await act(() => result.current.load());
  expect(result.current.items).toEqual(fields);
  transport.mockResolvedValueOnce(json({ code: "SESSION_REQUIRED" }, 401));
  await act(() => result.current.load());
  expect(result.current.items).toBeNull();
  expect(result.current.error).toBe("SESSION_REQUIRED");
  const count = transport.mock.calls.length;
  await act(() => result.current.load());
  expect(transport.mock.calls).toHaveLength(count);
});
it.each(["plan", "entity", "disabled"])(
  "does not read without %s selection prerequisites",
  async (fault) => {
    const { result, transport, props, rerender } = await setup();
    rerender({
      ...props,
      plan: fault === "plan" ? null : plan,
      entity: fault === "entity" ? null : entity,
      enabled: fault !== "disabled",
    });
    await act(() => result.current.load());
    expect(transport.mock.calls).toHaveLength(1);
    expect(result.current.items).toBeNull();
  },
);
