import { act, renderHook } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary } from "../api/hostedV3Types";
import { useV3Validation } from "./useV3Validation";

// Independently invented protocol fixtures, not publication or actual model evidence.
const id = "80000000-0000-0000-0000-000000000001";
const digest = "e".repeat(64);
const plan: PlanSummary = {
  planId: id,
  revision: "2",
  definition: { objectId: "80000000-0000-0000-0000-000000000002", workspaceRevision: "1" },
  bindingId: "mock-binding",
  destinationId: "mock-destination",
  currentCounts: { documents: 1, entities: 1, relations: 0 },
  targetCounts: { documents: 1, entities: 1, relations: 0 },
  inspectionValid: true,
  targetComplete: true,
  exportAvailable: false,
  blockers: [],
  observedDestination: {
    engine: "postgresql",
    identity: { systemIdentifier: "1", databaseOid: "2", databaseName: "mock" },
    observationFingerprint: digest,
    evidenceValid: true,
  },
  currentComputedCounts: null,
  targetComputedCounts: null,
};
const checks = [
  "SCOPE",
  "DEFINITION",
  "MAPPING",
  "VALUES",
  "SEMANTICS",
  "XML_FIDELITY",
  "DESTINATION",
  "CLIENT_CAPABILITY",
  "CONTENT_POLICY",
  "REVIEW",
];
const summary = {
  revision: "2",
  inputFingerprint: digest,
  checks: checks.map((check, n) => ({
    check,
    outcome: ["PASS", "FAIL", "UNKNOWN", "ERROR"][n % 4],
    inputFingerprint: digest,
  })),
  applicationRules: [{ ruleId: "mock-count", outcome: "UNKNOWN" }],
  targetComplete: true,
  computedRuleCount: 64000,
  exportAvailable: false,
};
const row = (n: number) => ({
  kind: "COOCCURRENCE",
  declaration: "mock-rule",
  source: { computedType: "mock-group", derivation: "mock-derived", value: `invented-${n}\\\n🦉` },
  actual: String(n),
  minimum: "0",
  maximum: "99999999999999999999999999999999999",
  outcome: "PASS",
});
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  for (const api of owners) api.clear();
  owners.length = 0;
});
async function setup(currentPlan: PlanSummary = plan) {
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path, options) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (path === `/api/v3/plans/${id}`) return json(currentPlan);
    if (path === `/api/v3/plans/${id}/validations`) {
      const b = JSON.parse(String(options?.body));
      if (!b.section) return json(summary);
      const end = Math.min(64000, b.offset + b.limit);
      return json({
        revision: "2",
        inputFingerprint: digest,
        targetComplete: true,
        total: 64000,
        offset: b.offset,
        nextOffset: end < 64000 ? end : null,
        items: Array.from({ length: Math.max(0, end - b.offset) }, (_, n) => row(b.offset + n)),
      });
    }
    throw new Error("Unexpected invented validation route");
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const hook = renderHook(
    ({ value, enabled }: { value: PlanSummary | null; enabled: boolean }) =>
      useV3Validation(api, value, enabled),
    {
      initialProps: { value: currentPlan, enabled: true } as {
        value: PlanSummary | null;
        enabled: boolean;
      },
    },
  );
  return { ...hook, transport, api };
}
const requests = (t: ReturnType<typeof vi.fn<typeof fetch>>) =>
  t.mock.calls
    .filter(([p]) => String(p).endsWith("/validations"))
    .map(([, o]) => JSON.parse(String(o?.body)));
it("validates explicitly and retains all backend outcomes without automatically reading rules", async () => {
  const { result, transport } = await setup();
  expect(requests(transport)).toEqual([]);
  await act(() => result.current.validate());
  expect(result.current.summary).toEqual(summary);
  expect(result.current.page).toBeNull();
  expect(requests(transport)).toEqual([{ revision: "2" }]);
});
it.each([null, 0])("keeps missing versus complete empty rule count %s distinct", async (count) => {
  const { result, transport } = await setup({ ...plan, targetComplete: count !== null });
  transport.mockResolvedValueOnce(
    json({ ...summary, targetComplete: count !== null, computedRuleCount: count }),
  );
  await act(() => result.current.validate());
  expect(result.current.summary?.computedRuleCount).toBe(count);
  expect(result.current.summary?.targetComplete).toBe(count !== null);
  if (count === null) {
    await act(() => result.current.readPage(0));
    expect(requests(transport)).toHaveLength(1);
  }
});
it("reads a default bounded page and exact tail beyond50000 without accumulating old rows", async () => {
  const { result, transport } = await setup();
  await act(() => result.current.validate());
  await act(() => result.current.readPage(0));
  expect(result.current.page?.items).toEqual([row(0), row(1), row(2), row(3)]);
  await act(() => result.current.readPage(63999));
  expect(result.current.page?.items).toEqual([row(63999)]);
  expect(result.current.page?.nextOffset).toBeNull();
  expect(requests(transport).at(-1)).toEqual({
    revision: "2",
    section: "computed-rules",
    inputFingerprint: digest,
    offset: 63999,
    limit: 4,
  });
});
it("retains a refused offset for explicit smaller-page recovery without old rows or automatic retries", async () => {
  const { result, transport } = await setup();
  await act(() => result.current.validate());
  await act(() => result.current.readPage(0));
  transport.mockResolvedValueOnce(json({ code: "RESOURCE_LIMIT" }, 422));
  await act(() => result.current.readPage(4, 4));
  expect(result.current.page).toBeNull();
  expect(result.current.summary).toEqual(summary);
  expect(result.current.request).toEqual({ offset: 4, limit: 4 });
  expect(requests(transport)).toHaveLength(3);
  await act(() => result.current.retrySmaller(1));
  expect(result.current.page?.items).toEqual([row(4)]);
  expect(requests(transport).at(-1)).toMatchObject({ offset: 4, limit: 1 });
});
it("discards a mismatched fingerprint rather than showing successful emptiness", async () => {
  const { result, transport } = await setup();
  await act(() => result.current.validate());
  transport.mockResolvedValueOnce(
    json({
      revision: "2",
      inputFingerprint: "f".repeat(64),
      targetComplete: true,
      total: 64000,
      offset: 64000,
      nextOffset: null,
      items: [],
    }),
  );
  await act(() => result.current.readPage(64000));
  expect(result.current.page).toBeNull();
  expect(result.current.summary).toBeNull();
  expect(result.current.error).not.toBe("");
});
it.each(["fingerprint", "total", "offset", "next", "incomplete"])(
  "refuses malformed or stale %s page evidence",
  async (fault) => {
    const { result, transport } = await setup();
    await act(() => result.current.validate());
    const value = {
      revision: "2",
      inputFingerprint: digest,
      targetComplete: true,
      total: 64000,
      offset: 0,
      nextOffset: 4,
      items: [row(0), row(1), row(2), row(3)],
    };
    transport.mockResolvedValueOnce(
      json({
        ...value,
        ...(fault === "fingerprint"
          ? { inputFingerprint: "f".repeat(64) }
          : fault === "total"
            ? { total: 64001 }
            : fault === "offset"
              ? { offset: 1 }
              : fault === "next"
                ? { nextOffset: 5 }
                : { targetComplete: false }),
      }),
    );
    await act(() => result.current.readPage(0));
    expect(result.current.page).toBeNull();
    expect(result.current.summary).toBeNull();
    expect(result.current.error).not.toBe("");
  },
);
it.each(["validate", "page"])(
  "withholds %s when final plan context changes at the same revision",
  async (phase) => {
    const { result, transport } = await setup();
    if (phase === "page") await act(() => result.current.validate());
    const original = transport.getMockImplementation();
    if (!original) throw new Error("Missing mock implementation");
    transport.mockImplementation(async (path, options) =>
      path === `/api/v3/plans/${id}`
        ? json({ ...plan, definition: { ...plan.definition, workspaceRevision: "9" } })
        : original(path, options),
    );
    await act(() => (phase === "page" ? result.current.readPage(0) : result.current.validate()));
    expect(result.current.page).toBeNull();
    expect(result.current.summary).toBeNull();
    expect(result.current.error).toContain("CONFLICT");
  },
);
it.each(["disable", "plan", "unmount", "revalidate"])(
  "retires a held page on %s without publishing its values",
  async (event) => {
    const { result, transport, rerender, unmount } = await setup();
    await act(() => result.current.validate());
    let release!: (r: Response) => void;
    transport.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          release = resolve;
        }),
    );
    let reading!: Promise<void>;
    act(() => {
      reading = result.current.readPage(0);
    });
    if (event === "disable") rerender({ value: plan, enabled: false });
    else if (event === "plan") rerender({ value: { ...plan, revision: "3" }, enabled: true });
    else if (event === "unmount") unmount();
    else await act(() => result.current.validate());
    await act(async () => {
      release(
        json({
          revision: "2",
          inputFingerprint: digest,
          targetComplete: true,
          total: 64000,
          offset: 0,
          nextOffset: 4,
          items: [row(0), row(1), row(2), row(3)],
        }),
      );
      await reading;
    });
    expect(result.current.page).toBeNull();
    if (event !== "revalidate" && event !== "unmount") expect(result.current.summary).toBeNull();
    else if (event === "revalidate") expect(result.current.summary).toEqual(summary);
    const count = requests(transport).length;
    if (event === "unmount") {
      await act(() => result.current.validate());
      expect(requests(transport)).toHaveLength(count);
    }
  },
);
it.each(["summary", "page", "retry"])(
  "retains no evidence after session loss during %s",
  async (phase) => {
    const { result, transport } = await setup();
    if (phase !== "summary") await act(() => result.current.validate());
    if (phase === "retry") {
      transport.mockResolvedValueOnce(json({ code: "RESOURCE_LIMIT" }, 422));
      await act(() => result.current.readPage(0));
    }
    transport.mockResolvedValueOnce(json({ code: "SESSION_REQUIRED" }, 401));
    await act(() =>
      phase === "summary"
        ? result.current.validate()
        : phase === "retry"
          ? result.current.retrySmaller(1)
          : result.current.readPage(0),
    );
    expect(result.current.summary).toBeNull();
    expect(result.current.page).toBeNull();
    expect(result.current.request).toBeNull();
    const count = requests(transport).length;
    await act(() => result.current.validate());
    await act(() => result.current.retrySmaller(1));
    expect(requests(transport)).toHaveLength(count);
  },
);
it.each([403, 413, 409, 500])(
  "does not offer size recovery for unrelated HTTP%s refusal",
  async (status) => {
    const { result, transport } = await setup();
    await act(() => result.current.validate());
    transport.mockResolvedValueOnce(json({ code: "RESOURCE_LIMIT" }, status));
    await act(() => result.current.readPage(0));
    expect(result.current.summary).toBeNull();
    expect(result.current.page).toBeNull();
    await act(() => result.current.retrySmaller(1));
    expect(requests(transport)).toHaveLength(2);
  },
);
it("offers only explicit strictly smaller limits and preserves offset through repeated resource refusals", async () => {
  const { result, transport } = await setup();
  await act(() => result.current.validate());
  transport.mockResolvedValueOnce(json({ code: "RESOURCE_LIMIT" }, 422));
  await act(() => result.current.readPage(50005, 4));
  for (const n of [0, 4, 5, 1.5, NaN]) await act(() => result.current.retrySmaller(n));
  expect(requests(transport)).toHaveLength(2);
  transport.mockResolvedValueOnce(json({ code: "RESOURCE_LIMIT" }, 422));
  await act(() => result.current.retrySmaller(2));
  expect(result.current.request).toEqual({ offset: 50005, limit: 2 });
  await act(() => result.current.retrySmaller(1));
  expect(result.current.page?.items).toEqual([row(50005)]);
  expect(
    requests(transport)
      .slice(1)
      .map((r) => [r.offset, r.limit]),
  ).toEqual([
    [50005, 4],
    [50005, 2],
    [50005, 1],
  ]);
});
it("keeps beyond-end emptiness distinct from absent target and rejects invalid coordinates without IO", async () => {
  const { result, transport } = await setup();
  await act(() => result.current.validate());
  await act(() => result.current.readPage(2147483647, 1));
  expect(result.current.page?.items).toEqual([]);
  expect(result.current.page?.total).toBe(64000);
  for (const [offset, limit] of [
    [-1, 4],
    [2147483648, 4],
    [0, 0],
    [0, 101],
    [0, 1.5],
  ])
    await act(() => result.current.readPage(offset, limit));
  expect(requests(transport)).toHaveLength(2);
});

it.each(["UNAVAILABLE", "REJECTED"])(
  "clears validation evidence for422 %s instead of offering resource-size recovery",
  async (code) => {
    const { result, transport } = await setup();
    await act(() => result.current.validate());
    await act(() => result.current.readPage(0));
    transport.mockResolvedValueOnce(json({ code }, 422));
    await act(() => result.current.readPage(4));
    expect(result.current.summary).toBeNull();
    expect(result.current.page).toBeNull();
    expect(result.current.request).toBeNull();
    expect(result.current.error).toBe(code);
    const count = transport.mock.calls.length;
    await act(() => result.current.retrySmaller(1));
    expect(transport.mock.calls).toHaveLength(count);
    expect(result.current.page).toBeNull();
  },
);
it("preserves displayed validation evidence when an offset exceeds the wire maximum", async () => {
  const { result, transport } = await setup();
  await act(() => result.current.validate());
  await act(() => result.current.readPage(0));
  const previous = result.current;
  expect(previous.page?.items).toEqual([row(0), row(1), row(2), row(3)]);
  const count = transport.mock.calls.length;
  await act(() => result.current.readPage(2147483648));
  expect(transport.mock.calls).toHaveLength(count);
  expect(result.current.summary).toBe(previous.summary);
  expect(result.current.page).toBe(previous.page);
  expect(result.current.request).toBe(previous.request);
  expect(result.current.error).toBe("INVALID_REQUEST");
});

it.each(["validate", "readPage"])(
  "does not reactivate a retired %s callback after leave and reentry",
  async (operation) => {
    const { result, transport, rerender } = await setup();
    await act(() => result.current.validate());
    const retired = result.current;
    rerender({ value: plan, enabled: false });
    rerender({ value: plan, enabled: true });
    await act(() => result.current.validate());
    const before = requests(transport).length;
    await act(() => (operation === "validate" ? retired.validate() : retired.readPage(0)));
    expect(requests(transport)).toHaveLength(before);
    expect(result.current.summary).toEqual(summary);
    expect(result.current.page).toBeNull();
    await act(() => result.current.readPage(0));
    expect(result.current.page?.items).toEqual([row(0), row(1), row(2), row(3)]);
  },
);

it("never renders old validation values under a replacement plan, including its first render", async () => {
  const { api, unmount } = await setup();
  unmount();
  const frames: { id: string; summary: unknown; page: unknown }[] = [];
  const hook = renderHook(
    ({ value }: { value: PlanSummary }) => {
      const state = useV3Validation(api, value, true);
      frames.push({ id: value.planId, summary: state.summary, page: state.page });
      return state;
    },
    { initialProps: { value: plan } },
  );
  await act(() => hook.result.current.validate());
  await act(() => hook.result.current.readPage(0));
  expect(hook.result.current.page?.items).toHaveLength(4);
  const replacement = { ...plan, planId: "80000000-0000-0000-0000-000000000009" };
  hook.rerender({ value: replacement });
  const next = frames.filter((frame) => frame.id === replacement.planId);
  expect(next.length).toBeGreaterThan(0);
  expect(next.every((frame) => frame.summary === null && frame.page === null)).toBe(true);
});
