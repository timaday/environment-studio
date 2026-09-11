import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary } from "../api/hostedV3Types";
import { type TargetCommandInput, useV3TargetCommands } from "./useV3TargetCommands";

// Independently invented typed intent. No application/database shape is copied.
const id = "78000000-0000-0000-0000-000000000001";
const entity = { kind: "existing" as const, handle: "78000000-0000-0000-0000-000000000002" };
const fresh = { kind: "fresh" as const, slotId: "new-item", typeId: "mock-type" };
const plan: PlanSummary = {
  planId: id,
  revision: "2",
  definition: { objectId: "78000000-0000-0000-0000-000000000003", workspaceRevision: "1" },
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
const placement = {
  entity: fresh,
  documentId: "mock-document",
  projectionId: "mock-projection",
  parent: {
    kind: "existing" as const,
    documentId: "mock-document",
    sourceDigest: "d".repeat(64),
    elementIndex: "0",
  },
};
const create = {
  kind: "create" as const,
  entity: fresh,
  fields: { input: { kind: "unresolved" as const } },
  references: { link: { kind: "to" as const, target: entity } },
};
const inputs: TargetCommandInput[] = [
  { kind: "upsert-entity", decision: create, placements: [placement] },
  {
    kind: "upsert-entity",
    decision: {
      kind: "retain",
      entity,
      fields: { input: { kind: "keep-observed" } },
      references: {},
    },
    placements: [],
  },
  { kind: "upsert-entity", decision: { kind: "remove", entity }, placements: [] },
  {
    kind: "batch-upsert",
    changes: [{ decision: create, placements: [placement] }],
    containment: [],
  },
  {
    kind: "replace-draft",
    draft: { entities: [create], containment: [], placements: [placement] },
  },
  { kind: "forget-entity-decision", entity: fresh },
  {
    kind: "bind-reference",
    entity: fresh,
    relationId: "link",
    state: { kind: "to", target: entity },
  },
  {
    kind: "move-containment",
    decision: { relationId: "contains", parent: entity, child: fresh },
    placements: [placement],
  },
  { kind: "discard" },
];
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  cleanup();
  for (const api of owners) api.clear();
  owners.length = 0;
});
async function setup() {
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path) =>
    path === "/api/v1/session"
      ? json({
          authenticated: true,
          csrfHeaderName: "X-CSRF",
          csrfToken: "mock-token",
          idleTimeoutSeconds: 1800,
          absoluteExpiresAt: "2099-01-01T00:00:00Z",
        })
      : json({ planId: id, revision: "3" }),
  );
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const props = { plan, enabled: true };
  return {
    ...renderHook(({ plan, enabled }) => useV3TargetCommands(api, plan, enabled), {
      initialProps: props,
    }),
    transport,
    props,
  };
}
it.each(inputs)("submits only explicit structural intent $kind", async (input) => {
  const { result, transport } = await setup();
  expect(transport.mock.calls).toHaveLength(1);
  await act(() => result.current.submit(input));
  expect(transport.mock.calls).toHaveLength(2);
  expect(transport.mock.calls[1][0]).toBe(`/api/v3/plans/${id}/commands`);
  expect(JSON.parse(String(transport.mock.calls[1][1]?.body))).toEqual({
    ...input,
    expectedRevision: "2",
    requestId: expect.any(String),
  });
  expect(result.current.receipt).toEqual({ planId: id, revision: "3" });
});
it("refuses profile composition on the target edit path", async () => {
  const { result, transport } = await setup();
  await act(() =>
    result.current.submit({
      kind: "compose-profile",
      profile: { objectId: id, workspaceRevision: "1" },
      previewDigest: "d".repeat(64),
      selectedRoots: ["one"],
      decisions: [{ kind: "cancel", slotId: "one" }],
    } as unknown as TargetCommandInput),
  );
  expect(transport.mock.calls).toHaveLength(1);
  expect(result.current.error).toBe("INVALID_REQUEST");
});

it("pins ownership metadata even when a caller supplies different revision/request fields", async () => {
  const { result, transport } = await setup();
  await act(() =>
    result.current.submit({
      ...inputs[0],
      expectedRevision: "999",
      requestId: id,
    } as unknown as TargetCommandInput),
  );
  const sent = JSON.parse(String(transport.mock.calls[1][1]?.body));
  expect(sent.expectedRevision).toBe("2");
  expect(sent.requestId).not.toBe(id);
});
it("keeps a detached structural command for exact replay after context change", async () => {
  const { result, transport, props, rerender } = await setup();
  const mutable = {
    kind: "upsert-entity" as const,
    decision: {
      ...create,
      fields: { input: { kind: "entered" as const, text: "Invented first value" } },
    },
    placements: [{ ...placement, parent: { ...placement.parent } }],
  };
  transport.mockRejectedValueOnce(new TypeError("Synthetic lost response"));
  await act(() => result.current.submit(mutable));
  mutable.decision.fields.input.text = "Changed after submission";
  mutable.placements[0].parent.elementIndex = "1";
  rerender({
    ...props,
    plan: { ...plan, planId: "78000000-0000-0000-0000-000000000009", revision: "7" },
  });
  await act(() => result.current.submit({ kind: "discard" }));
  expect(transport.mock.calls).toHaveLength(2);
  await act(() => result.current.retry());
  expect(transport.mock.calls).toHaveLength(3);
  expect(transport.mock.calls[2][0]).toBe(transport.mock.calls[1][0]);
  expect(transport.mock.calls[2][1]?.body).toBe(transport.mock.calls[1][1]?.body);
  expect(
    JSON.parse(String(transport.mock.calls[2][1]?.body)).placements[0].parent.elementIndex,
  ).toBe("0");
  expect(result.current.receipt).toEqual({ planId: id, revision: "3" });
});
it.each([
  { kind: "batch-upsert", changes: [], containment: [] },
  { kind: "bind-reference", entity, relationId: "link", state: { kind: "to" } },
  {
    kind: "move-containment",
    decision: { relationId: "contains", parent: entity, child: fresh },
    placements: [{ ...placement, parent: { ...placement.parent, elementIndex: "00" } }],
  },
])("refuses malformed structural intent before dispatch", async (input) => {
  const { result, transport } = await setup();
  await act(() => result.current.submit(input as TargetCommandInput));
  expect(transport.mock.calls).toHaveLength(1);
  expect(result.current.error).toBe("INVALID_REQUEST");
});
