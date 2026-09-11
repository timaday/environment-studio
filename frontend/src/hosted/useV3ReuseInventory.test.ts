import { act, renderHook } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary } from "../api/hostedV3Types";
import { useV3ReuseInventory } from "./useV3ReuseInventory";

// Independently invented wire fixtures; no database or publication qualification.
const id = "72000000-0000-0000-0000-000000000001";
const plan: PlanSummary = {
  planId: id,
  revision: "2",
  definition: { objectId: "72000000-0000-0000-0000-000000000002", workspaceRevision: "2" },
  bindingId: "mock-binding",
  destinationId: "mock-destination",
  currentCounts: { documents: 1, entities: 101, relations: 0 },
  targetCounts: { documents: 0, entities: 0, relations: 0 },
  inspectionValid: true,
  targetComplete: false,
  exportAvailable: false,
  blockers: [],
  observedDestination: {
    engine: "postgresql",
    identity: { systemIdentifier: "1", databaseOid: "2", databaseName: "mock" },
    observationFingerprint: "c".repeat(64),
    evidenceValid: true,
  },
  currentComputedCounts: null,
  targetComputedCounts: null,
};
const row = (n: number) => ({
  entity: { kind: "existing", handle: `73000000-0000-0000-0000-${String(n).padStart(12, "0")}` },
  typeId: "mock-type",
  fields: [
    { fieldId: "empty", present: true, masked: false, value: "" },
    { fieldId: "absent", present: false, masked: false, value: null },
    { fieldId: "hidden", present: true, masked: true, value: null },
    { fieldId: "input", present: true, masked: false, value: "Invented current value" },
  ],
});
const rows = Array.from({ length: 101 }, (_, n) => row(n));
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  for (const api of owners) api.clear();
  owners.length = 0;
});
async function setup() {
  const fetcher = vi.fn<typeof fetch>().mockImplementation(async (path, options) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (path === `/api/v3/plans/${id}`) return json(plan);
    if (path === `/api/v3/plans/${id}/views/entities`) {
      const { offset, limit } = JSON.parse(String(options?.body));
      const end = Math.min(offset + limit, rows.length);
      return json({
        revision: "2",
        total: rows.length,
        offset,
        nextOffset: end < rows.length ? end : null,
        items: rows.slice(offset, end),
      });
    }
    throw new Error("Unexpected invented inventory route");
  });
  const api = new HostedApi(fetcher);
  owners.push(api);
  await api.session();
  type Props = { value: PlanSummary | null; enabled: boolean; owner?: HostedApi };
  const initialProps: Props = { value: plan, enabled: true };
  const renderedCounts: Array<number | null> = [];
  const hook = renderHook(
    ({ value, enabled, owner }: Props) => {
      const state = useV3ReuseInventory(owner ?? api, value, enabled);
      renderedCounts.push(state.items?.length ?? null);
      return state;
    },
    { initialProps },
  );
  return { ...hook, fetcher, api, renderedCounts };
}
it("explicitly loads all pages, retains field distinctions and verifies freshness without mutation", async () => {
  const { result, fetcher } = await setup();
  expect(result.current.items).toBeNull();
  expect(fetcher).toHaveBeenCalledTimes(1);
  await act(() => result.current.load());
  expect(result.current.items).toEqual(rows);
  expect(fetcher.mock.calls.map(([path]) => path)).toEqual([
    "/api/v1/session",
    `/api/v3/plans/${id}/views/entities`,
    `/api/v3/plans/${id}/views/entities`,
    `/api/v3/plans/${id}`,
  ]);
  expect(
    fetcher.mock.calls
      .filter(([, options]) => options?.method === "POST")
      .map(([, options]) => JSON.parse(String(options?.body))),
  ).toEqual([
    { revision: "2", side: "current", offset: 0, limit: 100 },
    { revision: "2", side: "current", offset: 100, limit: 100 },
  ]);
});
it.each(["duplicate", "total", "revision", "fresh-reference"])(
  "refuses an inconsistent %s tail without exposing the first page",
  async (kind) => {
    const { result, fetcher } = await setup();
    const original = fetcher.getMockImplementation();
    if (!original) throw new Error("Invented transport missing");
    fetcher.mockImplementation(async (path, options) => {
      if (String(path).endsWith("/entities") && JSON.parse(String(options?.body)).offset === 100) {
        return json({
          revision: kind === "revision" ? "3" : "2",
          total: kind === "total" ? 102 : 101,
          offset: 100,
          nextOffset: null,
          items:
            kind === "total"
              ? [row(100), row(101)]
              : [
                  kind === "duplicate"
                    ? row(0)
                    : kind === "fresh-reference"
                      ? {
                          ...row(100),
                          entity: { kind: "fresh", slotId: "unobserved", typeId: "mock-type" },
                        }
                      : row(100),
                ],
        });
      }
      return original(path, options);
    });
    await act(() => result.current.load());
    expect(result.current.items).toBeNull();
    expect(result.current.error).not.toBe("");
  },
);
it("refuses a changed final plan even when page revisions and counts match", async () => {
  const { result, fetcher } = await setup();
  const original = fetcher.getMockImplementation();
  if (!original) throw new Error("Invented transport missing");
  fetcher.mockImplementation((path, options) =>
    path === `/api/v3/plans/${id}`
      ? Promise.resolve(json({ ...plan, inspectionValid: false }))
      : original(path, options),
  );
  await act(() => result.current.load());
  expect(result.current.items).toBeNull();
  expect(result.current.error).toContain("CONFLICT");
});
it("retires a held read on inactive context and does not restore values on re-entry", async () => {
  const { result, fetcher, rerender } = await setup();
  let release!: (value: Response) => void;
  fetcher.mockImplementationOnce(
    () =>
      new Promise<Response>((resolve) => {
        release = resolve;
      }),
  );
  let reading!: Promise<void>;
  act(() => {
    reading = result.current.load();
  });
  rerender({ value: plan, enabled: false });
  await act(async () => {
    release(
      json({ revision: "2", total: 101, offset: 0, nextOffset: 100, items: rows.slice(0, 100) }),
    );
    await reading;
  });
  expect(result.current.items).toBeNull();
  expect(fetcher).toHaveBeenCalledTimes(2);
  rerender({ value: plan, enabled: true });
  expect(result.current.items).toBeNull();
  await act(() => result.current.load());
  expect(result.current.items).toEqual(rows);
});
it("clears loaded values on plan replacement and requires valid observed evidence", async () => {
  const { result, rerender, fetcher } = await setup();
  await act(() => result.current.load());
  rerender({ value: { ...plan, revision: "3", inspectionValid: false }, enabled: true });
  expect(result.current.items).toBeNull();
  const before = fetcher.mock.calls.length;
  await act(() => result.current.load());
  expect(fetcher).toHaveBeenCalledTimes(before);
  const observed = plan.observedDestination;
  if (!observed) throw new Error("Invented observation missing");
  rerender({
    value: { ...plan, observedDestination: { ...observed, evidenceValid: false } },
    enabled: true,
  });
  await act(() => result.current.load());
  expect(fetcher).toHaveBeenCalledTimes(before);
});
it("clears values on a failed reload and retires the session on SESSION_REQUIRED", async () => {
  const { result, fetcher } = await setup();
  await act(() => result.current.load());
  fetcher.mockRejectedValueOnce(new TypeError("Invented transport loss"));
  await act(() => result.current.load());
  expect(result.current.items).toBeNull();
  expect(result.current.error).toBe("NETWORK_UNCERTAIN");
  fetcher.mockResolvedValueOnce(json({ code: "SESSION_REQUIRED" }, 401));
  await act(() => result.current.load());
  const count = fetcher.mock.calls.length;
  await act(() => result.current.load());
  expect(fetcher).toHaveBeenCalledTimes(count);
  expect(result.current.items).toBeNull();
});

it("distinguishes a verified empty inventory from unknown and rejects an oversized declared total", async () => {
  const { result, rerender, fetcher } = await setup();
  const emptyPlan = { ...plan, currentCounts: { ...plan.currentCounts, entities: 0 } };
  rerender({ value: emptyPlan, enabled: true });
  fetcher
    .mockResolvedValueOnce(
      json({ revision: "2", total: 0, offset: 0, nextOffset: null, items: [] }),
    )
    .mockResolvedValueOnce(json(emptyPlan));
  await act(() => result.current.load());
  expect(result.current.items).toEqual([]);
  const oversized = { ...plan, currentCounts: { ...plan.currentCounts, entities: 20001 } };
  rerender({ value: oversized, enabled: true });
  fetcher.mockResolvedValueOnce(
    json({ revision: "2", total: 20001, offset: 0, nextOffset: 100, items: rows.slice(0, 100) }),
  );
  await act(() => result.current.load());
  expect(result.current.items).toBeNull();
  expect(result.current.error).toContain("CONFLICT");
});
it("does not start a second load while a page is held and retires on unmount", async () => {
  const { result, fetcher, unmount } = await setup();
  let release!: (value: Response) => void;
  fetcher.mockImplementationOnce(
    () =>
      new Promise<Response>((resolve) => {
        release = resolve;
      }),
  );
  let reading!: Promise<void>;
  act(() => {
    reading = result.current.load();
  });
  await act(() => result.current.load());
  expect(fetcher).toHaveBeenCalledTimes(2);
  unmount();
  await act(async () => {
    release(
      json({ revision: "2", total: 101, offset: 0, nextOffset: 100, items: rows.slice(0, 100) }),
    );
    await reading;
  });
  expect(fetcher).toHaveBeenCalledTimes(2);
});

it.each(["plan", "presentation", "api", "session", "unmount"])(
  "retains no authority in saved load callbacks after %s retirement",
  async (kind) => {
    const { result, rerender, fetcher, api, unmount } = await setup();
    const retiredLoad = result.current.load;
    if (kind === "plan") {
      rerender({ value: { ...plan, revision: "3", inspectionValid: false }, enabled: true });
    } else if (kind === "presentation") {
      rerender({ value: plan, enabled: false });
      rerender({ value: plan, enabled: true });
    } else if (kind === "api") {
      const replacement = new HostedApi(fetcher);
      owners.push(replacement);
      await replacement.session();
      rerender({ value: plan, enabled: true, owner: replacement });
    } else if (kind === "session") api.clear();
    else unmount();
    const before = fetcher.mock.calls.length;
    await act(() => retiredLoad());
    expect(fetcher).toHaveBeenCalledTimes(before);
    expect(result.current.items).toBeNull();
    if (kind === "plan") {
      await act(() => result.current.load());
      expect(fetcher).toHaveBeenCalledTimes(before);
      rerender({ value: plan, enabled: true });
      await act(() => retiredLoad());
      expect(fetcher).toHaveBeenCalledTimes(before);
    }
    if (kind !== "session" && kind !== "unmount") {
      await act(() => result.current.load());
      expect(result.current.items).toEqual(rows);
      expect(fetcher).toHaveBeenCalledTimes(before + 3);
    }
  },
);

it("does not expose previous rows during the replacement render before effects clear state", async () => {
  const { result, rerender, renderedCounts } = await setup();
  await act(() => result.current.load());
  expect(result.current.items).toEqual(rows);
  const before = renderedCounts.length;
  rerender({ value: { ...plan, revision: "3", inspectionValid: false }, enabled: true });
  expect(renderedCounts.slice(before).length).toBeGreaterThan(0);
  expect(renderedCounts.slice(before).every((count) => count === null)).toBe(true);
});
