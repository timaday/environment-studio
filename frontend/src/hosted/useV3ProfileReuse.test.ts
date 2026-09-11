import { act, renderHook } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary, PreviewRequest } from "../api/hostedV3Types";
import { type ReuseInput, useV3ProfileReuse } from "./useV3ProfileReuse";

// Independently invented wire fixtures; no actual publication/readiness asserted.
const id = "70000000-0000-0000-0000-000000000001";
const digest = "d".repeat(64);
const profile = { objectId: "70000000-0000-0000-0000-000000000003", workspaceRevision: "1" };
const plan: PlanSummary = {
  planId: id,
  revision: "2",
  definition: { objectId: "70000000-0000-0000-0000-000000000002", workspaceRevision: "1" },
  bindingId: "mock-binding",
  destinationId: "mock-destination",
  currentCounts: { documents: 2, entities: 102, relations: 0 },
  targetCounts: { documents: 2, entities: 102, relations: 0 },
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
const slot = (n: number) => `slot-${String(n).padStart(3, "0")}`;
const input: ReuseInput = { profile, selection: { kind: "selected", roots: [slot(0)] } };
const included = Array.from({ length: 101 }, (_, n) => ({
  slotId: slot(n),
  typeId: "mock-type",
  label: `Neutral ${n}`,
  requiredInputs: ["input"],
}));
const decisions = included.map((s) => ({
  kind: "create" as const,
  slotId: s.slotId,
  targetSlotId: `new-${s.slotId}`,
}));
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  for (const api of owners) api.clear();
  owners.length = 0;
});
function page(body: PreviewRequest) {
  const items =
    body.section === "included"
      ? included
      : body.section === "dependencies"
        ? included.slice(1).map((s) => ({
            slotId: s.slotId,
            causedBy: slot(0),
            relationId: "mock-link",
            reason: "required-reference",
          }))
        : [];
  const end = Math.min(items.length, body.offset + body.limit);
  return {
    revision: "2",
    section: body.section,
    total: items.length,
    offset: body.offset,
    nextOffset: end < items.length ? end : null,
    items: items.slice(body.offset, end),
    previewDigest: digest,
    affectedDerivations: ["mock-derived"],
    pins: {
      planId: id,
      revision: "2",
      observationFingerprint: digest,
      profile,
      publicationDigest: digest,
      selectedRoots:
        body.selection.kind === "selected"
          ? [...body.selection.roots].sort()
          : included.map((s) => s.slotId),
      rootsDigest: digest,
      closureDigest: digest,
    },
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
    if (path === `/api/v3/plans/${id}`) return json(plan);
    if (path === `/api/v3/plans/${id}/profile-previews`)
      return json(page(JSON.parse(String(options?.body))));
    if (path === `/api/v3/plans/${id}/commands`) return json({ planId: id, revision: "3" });
    throw new Error("Unexpected invented reuse route");
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const hook = renderHook(
    ({ value, enabled }: { value: PlanSummary | null; enabled: boolean }) =>
      useV3ProfileReuse(api, value, enabled),
    {
      initialProps: { value: plan, enabled: true } as {
        value: PlanSummary | null;
        enabled: boolean;
      },
    },
  );
  return { ...hook, api, transport };
}
const calls = (transport: ReturnType<typeof vi.fn<typeof fetch>>, suffix: string) =>
  transport.mock.calls.filter(([path]) => String(path).endsWith(suffix));
it("loads every preview section on explicit entry without mutating", async () => {
  const { result, transport } = await setup();
  act(() => result.current.configure(input));
  expect(calls(transport, "/profile-previews")).toHaveLength(0);
  await act(() => result.current.previewSelection());
  expect(result.current.error).toBe("");
  expect(result.current.preview?.included).toEqual(included);
  expect(result.current.preview?.dependencies).toHaveLength(100);
  expect(result.current.preview?.relations).toEqual([]);
  expect(result.current.preview?.conflicts).toEqual([]);
  expect(result.current.preview?.affectedDerivations).toEqual(["mock-derived"]);
  expect(
    calls(transport, "/profile-previews").map(([, o]) => {
      const b = JSON.parse(String(o?.body));
      return [b.section, b.offset];
    }),
  ).toEqual([
    ["included", 0],
    ["included", 100],
    ["dependencies", 0],
    ["relations", 0],
    ["conflicts", 0],
  ]);
  expect(calls(transport, "/commands")).toHaveLength(0);
});
it("uses whole-profile normalized roots and explicit decisions for a separate compose receipt", async () => {
  const { result, transport } = await setup();
  act(() => result.current.configure({ profile, selection: { kind: "all" } }));
  await act(() => result.current.previewSelection());
  await act(() => result.current.apply(decisions));
  const sent = calls(transport, "/commands");
  expect(sent).toHaveLength(1);
  expect(JSON.parse(String(sent[0][1]?.body))).toEqual({
    kind: "compose-profile",
    expectedRevision: "2",
    requestId: expect.any(String),
    profile,
    previewDigest: digest,
    selectedRoots: included.map((s) => s.slotId),
    decisions,
  });
  expect(result.current.receipt).toEqual({ planId: id, revision: "3" });
  expect(result.current.preview).toBeNull();
});
it("does not apply omitted, duplicate or foreign slot decisions", async () => {
  const { result, transport } = await setup();
  act(() => result.current.configure(input));
  await act(() => result.current.previewSelection());
  for (const choices of [
    decisions.slice(1),
    [...decisions.slice(1), decisions[1]],
    [...decisions.slice(1), { kind: "cancel" as const, slotId: "foreign" }],
  ]) {
    await act(() => result.current.apply(choices));
    expect(result.current.error).toBe("INVALID_REQUEST");
  }
  expect(calls(transport, "/commands")).toHaveLength(0);
});
it("clears mixed preview pages instead of retaining the first successful section", async () => {
  const { result, transport } = await setup();
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Missing mock implementation");
  transport.mockImplementation(async (path, options) => {
    if (String(path).endsWith("/profile-previews")) {
      const body = JSON.parse(String(options?.body));
      const p = page(body);
      return json(body.section === "relations" ? { ...p, previewDigest: "e".repeat(64) } : p);
    }
    return original(path, options);
  });
  act(() => result.current.configure(input));
  await act(() => result.current.previewSelection());
  expect(result.current.preview).toBeNull();
  expect(result.current.error).toContain("CONFLICT");
});
it("retains possibly committed refusal for exact original-plan replay across context changes", async () => {
  const { result, transport, rerender } = await setup();
  act(() => result.current.configure(input));
  await act(() => result.current.previewSelection());
  transport.mockResolvedValueOnce(json({ code: "FORBIDDEN" }, 403));
  await act(() => result.current.apply(decisions));
  expect(result.current.pending).toBe(true);
  rerender({
    value: { ...plan, planId: "70000000-0000-0000-0000-000000000004", revision: "9" },
    enabled: true,
  });
  act(() => result.current.configure({ profile, selection: { kind: "all" } }));
  await act(() => result.current.retry());
  const sent = calls(transport, "/commands");
  expect(sent).toHaveLength(2);
  expect(sent[1][0]).toBe(sent[0][0]);
  expect(sent[1][1]?.body).toBe(sent[0][1]?.body);
  expect(result.current.receipt).toEqual({ planId: id, revision: "3" });
});
it.each(["publication", "affected", "observation", "total", "duplicate", "foreign-relation"])(
  "refuses inconsistent %s evidence without a usable partial preview",
  async (fault) => {
    const { result, transport } = await setup();
    const original = transport.getMockImplementation();
    if (!original) throw new Error("Missing mock implementation");
    transport.mockImplementation(async (path, options) => {
      if (!String(path).endsWith("/profile-previews")) return original(path, options);
      const body = JSON.parse(String(options?.body));
      const value = page(body);
      if (body.section === "included" && body.offset === 100) {
        if (fault === "publication")
          return json({ ...value, pins: { ...value.pins, publicationDigest: "e".repeat(64) } });
        if (fault === "affected") return json({ ...value, affectedDerivations: [] });
        if (fault === "observation")
          return json({
            ...value,
            pins: { ...value.pins, observationFingerprint: "e".repeat(64) },
          });
        if (fault === "total")
          return json({ ...value, total: 102, items: [included[99], included[100]] });
        if (fault === "duplicate") return json({ ...value, items: [included[0]] });
      }
      if (fault === "foreign-relation" && body.section === "relations")
        return json({
          ...value,
          total: 1,
          items: [{ relationId: "mock-link", fromSlot: slot(0), toSlot: "outside" }],
        });
      return json(value);
    });
    act(() => result.current.configure(input));
    await act(() => result.current.previewSelection());
    expect(result.current.preview).toBeNull();
    expect(result.current.error).not.toBe("");
    await act(() => result.current.apply(decisions));
    expect(calls(transport, "/commands")).toHaveLength(0);
  },
);
it.each(["revision", "definition", "observation", "counts", "missing"])(
  "withholds the complete preview when final summary changes %s",
  async (fault) => {
    const { result, transport } = await setup();
    const original = transport.getMockImplementation();
    if (!original) throw new Error("Missing mock implementation");
    transport.mockImplementation(async (path, options) => {
      if (path !== `/api/v3/plans/${id}`) return original(path, options);
      if (fault === "missing") return json({ code: "NOT_FOUND" }, 404);
      return json({
        ...plan,
        ...(fault === "revision"
          ? { revision: "3" }
          : fault === "definition"
            ? { definition: { ...plan.definition, workspaceRevision: "2" } }
            : fault === "counts"
              ? { targetCounts: { ...plan.targetCounts, entities: 103 } }
              : {
                  observedDestination: {
                    ...plan.observedDestination,
                    observationFingerprint: "e".repeat(64),
                  },
                }),
      });
    });
    act(() => result.current.configure(input));
    await act(() => result.current.previewSelection());
    expect(result.current.preview).toBeNull();
    expect(result.current.error).not.toBe("");
  },
);
it.each(["configure", "disable", "plan", "unmount"])(
  "retires a held preview on %s without subsequent reads",
  async (event) => {
    const { result, transport, rerender, unmount } = await setup();
    let release!: (response: Response) => void;
    transport.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          release = resolve;
        }),
    );
    act(() => result.current.configure(input));
    let reading!: Promise<void>;
    act(() => {
      reading = result.current.previewSelection();
    });
    if (event === "configure")
      act(() => result.current.configure({ profile, selection: { kind: "all" } }));
    else if (event === "disable") rerender({ value: plan, enabled: false });
    else if (event === "plan") rerender({ value: { ...plan, revision: "3" }, enabled: true });
    else unmount();
    await act(async () => {
      release(json(page({ ...input, revision: "2", section: "included", offset: 0, limit: 100 })));
      await reading;
    });
    expect(calls(transport, "/profile-previews")).toHaveLength(1);
    expect(result.current.preview).toBeNull();
  },
);
it.each([403, 413, "malformed"])(
  "keeps exact command after uncertain %s acknowledgement and blocks a replacement",
  async (status) => {
    const { result, transport } = await setup();
    act(() => result.current.configure(input));
    await act(() => result.current.previewSelection());
    transport.mockResolvedValueOnce(
      typeof status === "number"
        ? json({ code: status === 403 ? "FORBIDDEN" : "RESOURCE_LIMIT" }, status)
        : json({ planId: id }),
    );
    await act(() => result.current.apply(decisions));
    expect(result.current.pending).toBe(true);
    act(() => result.current.configure({ profile, selection: { kind: "all" } }));
    await act(() => result.current.apply(decisions));
    expect(calls(transport, "/commands")).toHaveLength(1);
    await act(() => result.current.retry());
    const sent = calls(transport, "/commands");
    expect(sent).toHaveLength(2);
    expect(sent[1][1]?.body).toBe(sent[0][1]?.body);
    expect(result.current.pending).toBe(false);
  },
);
it("retires all input and replay after original-session loss", async () => {
  const { result, transport } = await setup();
  act(() => result.current.configure(input));
  await act(() => result.current.previewSelection());
  transport.mockResolvedValueOnce(json({ code: "SESSION_REQUIRED" }, 401));
  await act(() => result.current.apply(decisions));
  expect(result.current.pending).toBe(false);
  expect(result.current.input).toBeNull();
  expect(result.current.preview).toBeNull();
  act(() => result.current.configure(input));
  await act(() => result.current.retry());
  await act(() => result.current.previewSelection());
  expect(calls(transport, "/commands")).toHaveLength(1);
});
it("detaches selection and explicit mixed decisions without importing values or replacing siblings", async () => {
  const { result, transport } = await setup();
  const mutable = {
    profile: { ...profile },
    selection: { kind: "selected" as const, roots: [slot(0)] },
  };
  act(() => result.current.configure(mutable));
  mutable.selection.roots[0] = "outside";
  mutable.profile.workspaceRevision = "99";
  await act(() => result.current.previewSelection());
  const chosen = [
    {
      kind: "use-existing" as const,
      slotId: slot(0),
      target: { kind: "existing" as const, handle: "71000000-0000-0000-0000-000000000001" },
    },
    ...included.slice(1).map((s) => ({ kind: "cancel" as const, slotId: s.slotId })),
  ];
  await act(() => result.current.apply(chosen));
  const body = JSON.parse(String(calls(transport, "/commands")[0][1]?.body));
  expect(body.selectedRoots).toEqual([slot(0)]);
  expect(body.profile).toEqual(profile);
  expect(body.decisions).toEqual(chosen);
  expect(Object.keys(body).sort()).toEqual([
    "decisions",
    "expectedRevision",
    "kind",
    "previewDigest",
    "profile",
    "requestId",
    "selectedRoots",
  ]);
});
it("completes multiple pages of dependencies, relations and conflicts without dropping positive rows", async () => {
  const { result, transport } = await setup();
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Missing mock implementation");
  const slots = Array.from({ length: 201 }, (_, n) => ({
    slotId: slot(n),
    typeId: "mock-type",
    label: `Neutral ${n}`,
    requiredInputs: [],
  }));
  const deps = slots.slice(1).map((s) => ({
    slotId: s.slotId,
    causedBy: slot(0),
    relationId: "mock-link",
    reason: "required-reference",
  }));
  const relations = Array.from({ length: 205 }, (_, n) => ({
    relationId: `link-${n}`,
    fromSlot: slot(0),
    toSlot: slot(1),
  }));
  const conflicts = Array.from({ length: 201 }, (_, n) => ({
    code: "ENTITY_COUNT",
    slotId: null,
    relationId: null,
    ruleId: `rule-${n}`,
  }));
  transport.mockImplementation(async (path, options) => {
    if (!String(path).endsWith("/profile-previews")) return original(path, options);
    const body: PreviewRequest = JSON.parse(String(options?.body));
    const items = { included: slots, dependencies: deps, relations, conflicts }[body.section];
    const end = Math.min(items.length, body.offset + body.limit);
    return json({
      ...page(body),
      total: items.length,
      items: items.slice(body.offset, end),
      nextOffset: end < items.length ? end : null,
    });
  });
  act(() => result.current.configure(input));
  await act(() => result.current.previewSelection());
  expect(result.current.error).toBe("");
  expect(result.current.preview?.included).toEqual(slots);
  expect(result.current.preview?.dependencies).toEqual(deps);
  expect(result.current.preview?.relations).toEqual(relations);
  expect(result.current.preview?.conflicts).toEqual(conflicts);
  expect(calls(transport, "/profile-previews")).toHaveLength(11);
  expect(calls(transport, "/commands")).toHaveLength(0);
});
it("clears configured selection when preview discovers the session has ended", async () => {
  const { result, transport } = await setup();
  act(() => result.current.configure(input));
  transport.mockResolvedValueOnce(json({ code: "SESSION_REQUIRED" }, 401));
  await act(() => result.current.previewSelection());
  expect(result.current.input).toBeNull();
  expect(result.current.preview).toBeNull();
  act(() => result.current.configure(input));
  await act(() => result.current.previewSelection());
  expect(calls(transport, "/profile-previews")).toHaveLength(1);
});

it.each(["dependencies", "relations", "conflicts"] as const)(
  "refuses individually valid later %s pages whose total changed",
  async (section) => {
    const { result, transport } = await setup();
    const original = transport.getMockImplementation();
    if (!original) throw new Error("Missing mock implementation");
    const items = Array.from({ length: 101 }, (_, n) =>
      section === "dependencies"
        ? {
            slotId: slot((n % 100) + 1),
            causedBy: slot(0),
            relationId: `link-${n}`,
            reason: "required-reference",
          }
        : section === "relations"
          ? { relationId: `link-${n}`, fromSlot: slot(0), toSlot: slot(1) }
          : { code: "ENTITY_COUNT", slotId: null, relationId: null, ruleId: `rule-${n}` },
    );
    transport.mockImplementation(async (path, options) => {
      if (!String(path).endsWith("/profile-previews")) return original(path, options);
      const body: PreviewRequest = JSON.parse(String(options?.body));
      const value = page(body);
      if (body.section !== section) return json(value);
      // Each page is valid on its own: only the same-section total changes.
      return json({
        ...value,
        total: body.offset === 0 ? 102 : 101,
        nextOffset: body.offset === 0 ? 100 : null,
        items: items.slice(body.offset, body.offset + body.limit),
      });
    });
    act(() => result.current.configure(input));
    await act(() => result.current.previewSelection());
    expect(result.current.preview).toBeNull();
    expect(result.current.error).toContain("CONFLICT");
    expect(
      calls(transport, "/profile-previews").filter(
        ([, options]) => JSON.parse(String(options?.body)).section === section,
      ),
    ).toHaveLength(2);
    await act(() => result.current.apply(decisions));
    expect(calls(transport, "/commands")).toHaveLength(0);
  },
);
it("refuses a preview whose pages consistently share an observation different from the plan", async () => {
  const { result, transport } = await setup();
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Missing mock implementation");
  transport.mockImplementation(async (path, options) => {
    if (!String(path).endsWith("/profile-previews")) return original(path, options);
    const value = page(JSON.parse(String(options?.body)));
    return json({ ...value, pins: { ...value.pins, observationFingerprint: "f".repeat(64) } });
  });
  act(() => result.current.configure(input));
  await act(() => result.current.previewSelection());
  expect(result.current.preview).toBeNull();
  expect(result.current.error).toContain("CONFLICT");
  await act(() => result.current.apply(decisions));
  expect(calls(transport, "/commands")).toHaveLength(0);
});
