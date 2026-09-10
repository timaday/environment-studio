import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary } from "../api/hostedV3Types";
import { useV3ProfileCapture } from "./useV3ProfileCapture";

// Independently invented wire-only fixtures; no actual publication is claimed.
const id = "60000000-0000-0000-0000-000000000001";
const digest = "c".repeat(64);
const plan: PlanSummary = {
  planId: id,
  revision: "2",
  definition: { objectId: "60000000-0000-0000-0000-000000000002", workspaceRevision: "2" },
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
    observationFingerprint: digest,
    evidenceValid: true,
  },
  currentComputedCounts: null,
  targetComputedCounts: null,
};
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  for (const api of owners) api.clear();
  owners.length = 0;
});
const handle = (n: number) => `61000000-0000-0000-0000-${String(n).padStart(12, "0")}`;
const row = (n: number) => ({
  entity: { kind: "existing", handle: handle(n) },
  typeId: "mock-type",
  fields: [
    {
      fieldId: "input",
      present: true,
      masked: false,
      value: "Do not retain this invented observation",
    },
  ],
});
const input = {
  profileId: "mock-capture",
  profileRevision: "1",
  mappings: Array.from({ length: 101 }, (_, n) => ({
    entity: { kind: "existing" as const, handle: handle(n) },
    slotId: `slot-${n}`,
    label: `Neutral slot ${n}`,
  })),
};
const model = {
  schemaVersion: "3",
  id: "mock-capture",
  revision: "1",
  logicalDefinitionDigest: digest,
  entities: input.mappings.map((m) => ({
    id: m.slotId,
    type: "mock-type",
    label: m.label,
    requiredInputs: [],
  })),
  relations: [],
};
const source = `${JSON.stringify(model)}\r\n`;
const captured = { revision: "2", definition: plan.definition, format: "json", source };
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
    if (path === `/api/v3/plans/${id}`) return json(plan);
    if (path === `/api/v3/plans/${id}/profile-captures`) return json(captured);
    if (String(path).startsWith("/api/v3/profiles/") && options?.method === "PUT")
      return json({
        objectId: String(path).split("/").at(-1),
        workspaceRevision: "1",
        sourceDigest: digest,
        format: "JSON",
        source,
        schemaVersion: "3",
        compilerVersion: "profile-compiler-v3",
        definition: plan.definition,
        state: "draft",
        projection: { kind: "structurally-valid", model, contentDigest: digest, diagnostics: [] },
      });
    if (path === `/api/v3/plans/${id}/views/entities`) {
      const { offset, limit } = JSON.parse(String(options?.body));
      const count = Math.min(limit, 101 - offset);
      return json({
        revision: "2",
        total: 101,
        offset,
        nextOffset: offset + count < 101 ? offset + count : null,
        items: Array.from({ length: count }, (_, n) => row(offset + n)),
      });
    }
    throw new Error("Unexpected invented capture route");
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const hook = renderHook(
    ({
      value,
      enabled,
      owner,
    }: {
      value: PlanSummary | null;
      enabled: boolean;
      owner?: HostedApi;
    }) => useV3ProfileCapture(owner ?? api, value, enabled),
    {
      initialProps: { value: plan, enabled: true } as {
        value: PlanSummary | null;
        enabled: boolean;
        owner?: HostedApi;
      },
    },
  );
  return { ...hook, transport, api };
}
it("loads every original physical page only on explicit entry and retains no observed values", async () => {
  const { result, transport } = await setup();
  expect(transport.mock.calls.filter(([p]) => String(p).includes("/views/"))).toHaveLength(0);
  await act(() => result.current.load());
  expect(result.current.error).toBe("");
  expect(result.current.entities).toHaveLength(101);
  expect(result.current.entities?.[100]).toEqual({
    entity: { kind: "existing", handle: handle(100) },
    typeId: "mock-type",
  });
  expect(JSON.stringify(result.current.entities)).not.toContain("Do not retain");
  expect(
    transport.mock.calls
      .filter(([p]) => String(p).endsWith("/views/entities"))
      .map(([, options]) => JSON.parse(String(options?.body))),
  ).toEqual([
    { revision: "2", side: "current", offset: 0, limit: 100 },
    { revision: "2", side: "current", offset: 100, limit: 100 },
  ]);
});
it("captures explicit complete neutral mappings and saves exact returned source only on a separate action", async () => {
  const { result, transport } = await setup();
  await act(() => result.current.load());
  act(() => result.current.configure(input));
  await act(() => result.current.capture());
  expect(result.current.captured).toEqual(captured);
  expect(transport.mock.calls.filter(([, o]) => o?.method === "PUT")).toHaveLength(0);
  await act(() => result.current.save());
  expect(result.current.saved?.workspaceRevision).toBe("1");
  const saves = transport.mock.calls.filter(([, o]) => o?.method === "PUT");
  expect(saves).toHaveLength(1);
  expect(JSON.parse(String(saves[0][1]?.body))).toEqual({
    expectedRevision: "0",
    requestId: expect.any(String),
    format: "JSON",
    source,
    definition: plan.definition,
  });
  const requests = transport.mock.calls.filter(([p]) => String(p).endsWith("/profile-captures"));
  expect(JSON.parse(String(requests[0][1]?.body))).toEqual({ revision: "2", ...input });
});
it.each(["mixed-total", "duplicate", "fresh"])(
  "refuses %s across physical pages without retaining a partial inventory",
  async (kind) => {
    const { result, transport } = await setup();
    const original = transport.getMockImplementation();
    if (!original) throw new Error("Missing test transport");
    transport.mockImplementation(async (path, options) => {
      if (
        String(path).endsWith("/views/entities") &&
        JSON.parse(String(options?.body)).offset === 100
      ) {
        if (kind === "mixed-total")
          return json({
            revision: "2",
            total: 102,
            offset: 100,
            nextOffset: null,
            items: [row(100), row(101)],
          });
        return json({
          revision: "2",
          total: 101,
          offset: 100,
          nextOffset: null,
          items: [
            kind === "duplicate"
              ? row(0)
              : { ...row(100), entity: { kind: "fresh", slotId: "new-slot", typeId: "mock-type" } },
          ],
        });
      }
      return original(path, options);
    });
    await act(() => result.current.load());
    expect(result.current.entities).toBeNull();
    expect(result.current.error).not.toBe("");
  },
);
it.each(["omitted", "foreign", "duplicate-slot"])(
  "rejects %s mappings before capture dispatch",
  async (kind) => {
    const { result, transport } = await setup();
    await act(() => result.current.load());
    const mappings = [...input.mappings];
    if (kind === "omitted") mappings.pop();
    else
      mappings[100] =
        kind === "foreign"
          ? { ...mappings[100], entity: { kind: "existing", handle: handle(999) } }
          : { ...mappings[100], slotId: mappings[0].slotId };
    act(() => result.current.configure({ ...input, mappings }));
    await act(() => result.current.capture());
    expect(result.current.captured).toBeNull();
    expect(result.current.error).not.toBe("");
    expect(
      transport.mock.calls.filter(([p]) => String(p).endsWith("/profile-captures")),
    ).toHaveLength(0);
  },
);
it.each([0, 200, 403, 413, 503])(
  "preserves exact save after %s across hide and plan replacement, with no recapture or duplicate dispatch",
  async (status) => {
    const { result, transport, rerender } = await setup();
    await act(() => result.current.load());
    act(() => result.current.configure(input));
    await act(() => result.current.capture());
    const original = transport.getMockImplementation();
    if (!original) throw new Error("Missing test transport");
    let saves = 0;
    transport.mockImplementation(async (path, options) => {
      if (options?.method === "PUT" && ++saves === 1) {
        if (status === 0) throw new TypeError("Invented response loss");
        if (status === 200) return json({ unexpected: true });
        return json(
          { code: status === 403 ? "FORBIDDEN" : status === 413 ? "TOO_LARGE" : "UNAVAILABLE" },
          status,
        );
      }
      return original(path, options);
    });
    await act(async () => {
      await Promise.all([result.current.save(), result.current.save()]);
    });
    expect(result.current.pending).toBe(true);
    const calls = transport.mock.calls.length;
    act(() => result.current.configure({ ...input, profileId: "replacement" }));
    await act(() => result.current.load());
    expect(transport).toHaveBeenCalledTimes(calls);
    rerender({ value: null, enabled: false });
    expect(result.current.pending).toBe(true);
    rerender({ value: { ...plan, revision: "3" }, enabled: true });
    await act(() => result.current.retry());
    const requests = transport.mock.calls.filter(([, options]) => options?.method === "PUT");
    expect(requests).toHaveLength(2);
    expect(requests[1][0]).toBe(requests[0][0]);
    expect(requests[1][1]?.body).toBe(requests[0][1]?.body);
    expect(result.current.pending).toBe(false);
    expect(result.current.saved?.source).toBe(source);
    expect(
      transport.mock.calls.filter(([p]) => String(p).endsWith("/profile-captures")),
    ).toHaveLength(1);
  },
);
it.each(["input", "hide", "plan", "session"])(
  "discards held capture after %s changes",
  async (change) => {
    const { result, transport, rerender, api } = await setup();
    await act(() => result.current.load());
    act(() => result.current.configure(input));
    let release!: (r: Response) => void;
    const held = new Promise<Response>((resolve) => {
      release = resolve;
    });
    const original = transport.getMockImplementation();
    if (!original) throw new Error("Missing test transport");
    transport.mockImplementation((path, options) =>
      String(path).endsWith("/profile-captures") ? held : original(path, options),
    );
    let work!: Promise<void>;
    act(() => {
      work = result.current.capture();
    });
    await waitFor(() => expect(result.current.busy).toBe(true));
    if (change === "input")
      act(() => result.current.configure({ ...input, profileId: "new-capture" }));
    else if (change === "hide") rerender({ value: plan, enabled: false });
    else if (change === "plan") rerender({ value: { ...plan, revision: "3" }, enabled: true });
    else api.clear();
    await act(async () => {
      release(json(captured));
      await work;
    });
    expect(result.current.captured).toBeNull();
    expect(result.current.busy).toBe(false);
    if (change === "session") {
      expect(result.current.entities).toBeNull();
      expect(result.current.input).toBeNull();
      expect(result.current.saved).toBeNull();
      expect(result.current.pending).toBe(false);
      act(() => result.current.configure(input));
      expect(result.current.input).toBeNull();
    }
  },
);
it("rejects same-revision observation invalidation and capture definition substitution", async () => {
  const { result, transport } = await setup();
  await act(() => result.current.load());
  act(() => result.current.configure(input));
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Missing test transport");
  transport.mockImplementation(async (path, options) =>
    path === `/api/v3/plans/${id}`
      ? json({ ...plan, inspectionValid: false })
      : original(path, options),
  );
  await act(() => result.current.capture());
  expect(result.current.captured).toBeNull();
  expect(result.current.error).toContain("CONFLICT");
  transport.mockImplementation(async (path, options) =>
    String(path).endsWith("/profile-captures")
      ? json({ ...captured, definition: { ...plan.definition, workspaceRevision: "3" } })
      : original(path, options),
  );
  await act(() => result.current.capture());
  expect(result.current.captured).toBeNull();
  expect(result.current.error).toContain("CONFLICT");
});
it("reports invalid mapping input as a request error and clears an earlier capture", async () => {
  const { result } = await setup();
  await act(() => result.current.load());
  act(() => result.current.configure(input));
  await act(() => result.current.capture());
  expect(result.current.captured).not.toBeNull();
  act(() => result.current.configure({ ...input, profileId: "" }));
  expect(result.current.captured).toBeNull();
  expect(result.current.error).toContain("INVALID_REQUEST");
});
it.each(["hide", "session"])(
  "settles a held save after %s without losing ownership or restoring a retired session",
  async (change) => {
    const { result, transport, rerender, api } = await setup();
    await act(() => result.current.load());
    act(() => result.current.configure(input));
    await act(() => result.current.capture());
    const original = transport.getMockImplementation();
    if (!original) throw new Error("Missing test transport");
    let release!: () => void;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    transport.mockImplementation(async (path, options) => {
      if (options?.method === "PUT") await held;
      return original(path, options);
    });
    let work!: Promise<void>;
    act(() => {
      work = result.current.save();
      result.current.configure({ ...input, profileId: "replacement" });
    });
    expect(result.current.captured?.source).toBe(source);
    if (change === "hide") rerender({ value: null, enabled: false });
    else api.clear();
    await act(async () => {
      release();
      await work;
    });
    expect(result.current.pending).toBe(false);
    expect(result.current.busy).toBe(false);
    if (change === "hide") expect(result.current.saved?.source).toBe(source);
    else {
      expect(result.current.saved).toBeNull();
      expect(result.current.captured).toBeNull();
      expect(result.current.entities).toBeNull();
    }
  },
);
it("allows a new explicit save after definite precommit refusal and never retries automatically", async () => {
  const { result, transport } = await setup();
  await act(() => result.current.load());
  act(() => result.current.configure(input));
  await act(() => result.current.capture());
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Missing test transport");
  let saves = 0;
  transport.mockImplementation(async (path, options) =>
    options?.method === "PUT" && ++saves === 1
      ? json({ code: "CAPACITY" }, 429)
      : original(path, options),
  );
  await act(() => result.current.save());
  expect(result.current.pending).toBe(false);
  expect(saves).toBe(1);
  await act(() => result.current.retry());
  expect(saves).toBe(1);
  await act(() => result.current.save());
  const requests = transport.mock.calls.filter(([, o]) => o?.method === "PUT");
  expect(requests).toHaveLength(2);
  expect(requests[1][0]).not.toBe(requests[0][0]);
  expect(result.current.saved?.workspaceRevision).toBe("1");
});
it("never installs a held acknowledgement after replacement by another API owner", async () => {
  const { result, transport, rerender } = await setup();
  await act(() => result.current.load());
  act(() => result.current.configure(input));
  await act(() => result.current.capture());
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Missing test transport");
  let release!: () => void;
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  transport.mockImplementation(async (path, options) => {
    if (options?.method === "PUT") await held;
    return original(path, options);
  });
  let work!: Promise<void>;
  act(() => {
    work = result.current.save();
  });
  const next = new HostedApi(transport);
  owners.push(next);
  await next.session();
  rerender({ value: plan, enabled: true, owner: next });
  await act(async () => {
    release();
    await work;
  });
  expect(result.current.saved).toBeNull();
  expect(result.current.captured).toBeNull();
  expect(result.current.pending).toBe(false);
  await act(() => result.current.load());
  expect(result.current.entities).toHaveLength(101);
});
