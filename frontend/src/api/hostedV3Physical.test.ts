import { createHash } from "node:crypto";
import Ajv2020 from "ajv/dist/2020";
import { afterEach, expect, it, vi } from "vitest";
import commandSchema from "../../../schemas/plan-command-v1.schema.json";
import viewSchema from "../../../schemas/plan-view-v1.schema.json";
import { HostedApi } from "./hosted";
import { HostedV3Api } from "./hostedV3";
import {
  assertPhysicalPagesCompatible,
  HostedV3Physical,
  rawLocationText,
} from "./hostedV3Physical";

// Independent wire examples; these do not establish actual server/XML authority.
const planId = "30000000-0000-0000-0000-000000000001";
const handle = "30000000-0000-0000-0000-000000000002";
const revision = "9007199254740993";
const existing = { kind: "existing", handle } as const;
const sibling = { kind: "existing", handle: "30000000-0000-0000-0000-000000000003" } as const;
const fresh = { kind: "fresh", slotId: "new-a", typeId: "sample" } as const;
const digest = "a".repeat(64);
const entity = {
  entity: existing,
  typeId: "sample",
  fields: [{ fieldId: "name", present: true, masked: false, value: "original" }],
};
const page = (
  items: readonly unknown[],
  total = items.length,
  offset = 0,
  nextOffset: number | null = null,
) => ({ revision, total, offset, nextOffset, items });
const inventory = {
  revision,
  documents: [{ documentId: "mock-a", currentDigest: digest, targetDigest: null, changed: null }],
};
const placement = { documentId: "mock-a", sourceDigest: digest, elementIndex: "2" };
const binding = {
  fieldId: "name",
  token: `[[value:${handle}:name]]`,
  current: { state: "value", text: "original" },
  target: { state: "value", text: "changed" },
  change: "changed",
  currentLocations: { state: "complete", total: 1 },
  targetLocations: { state: "complete", total: 1 },
};
const location = {
  documentId: "mock-a",
  sourceDigest: digest,
  projectionId: "sample-projection",
  elementIndex: "2",
  attribute: { namespaceUri: "", localName: "name" },
  span: { start: 10, end: 18 },
  role: "field",
  declarationId: "name",
};
const pageRequest = { revision, side: "current", offset: 0, limit: 1 } as const;
const documentRequest = {
  revision,
  side: "current",
  documentId: "mock-a",
  mode: "raw",
  completeDocumentDisclosure: true,
} as const;
const document = {
  revision,
  side: "current",
  documentId: "mock-a",
  mode: "raw",
  text: "<sample/>\r\n",
  exact: true,
  redacted: false,
  unmappedConcreteMayRemain: true,
  omissions: [],
};
const json = (value: unknown) => new Response(JSON.stringify(value));
const owners: HostedApi[] = [];
afterEach(() => {
  for (const owner of owners) owner.clear();
  owners.length = 0;
});
async function client(...responses: unknown[]) {
  const transport = vi.fn<typeof fetch>().mockResolvedValueOnce(
    json({
      authenticated: true,
      csrfHeaderName: "X-CSRF-TOKEN",
      csrfToken: "mock-csrf",
      idleTimeoutSeconds: 1800,
      absoluteExpiresAt: new Date(Date.now() + 28800000).toISOString(),
    }),
  );
  for (const response of responses) transport.mockResolvedValueOnce(json(response));
  const owner = new HostedApi(transport);
  owners.push(owner);
  await owner.session();
  return { api: new HostedV3Physical(owner), owner, transport };
}
it("rejects a masked physical field carrying concrete text", async () => {
  const { api } = await client({
    revision,
    total: 1,
    offset: 0,
    nextOffset: null,
    items: [
      {
        entity: existing,
        typeId: "sample",
        fields: [{ fieldId: "name", present: true, masked: true, value: "must-not-be-rendered" }],
      },
    ],
  });
  await expect(api.entities(planId, pageRequest)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
});
it("rejects another document side returned for explicit current Raw disclosure", async () => {
  const { api } = await client({ ...document, side: "target" });
  await expect(api.document(planId, documentRequest)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
});
const schemas = new Ajv2020({ strict: false });
schemas.addSchema(commandSchema).addSchema(viewSchema);
function shape(name: string, value: unknown) {
  const validate = schemas.compile({ $ref: `${viewSchema.$id}#/$defs/${name}` });
  expect(validate(value), JSON.stringify(validate.errors)).toBe(true);
}
it("validates independent complete requests and response fixtures against canonical schemas", () => {
  for (const [name, value] of Object.entries({
    revisionRequest: { revision },
    entitiesRequest: pageRequest,
    draftRequest: { revision, offset: 0, limit: 1 },
    placementsRequest: {
      revision,
      documentId: "mock-a",
      projectionId: "sample-projection",
      offset: 0,
      limit: 1,
    },
    bindingsRequest: { revision, entity: existing, offset: 0, limit: 1 },
    documentRequest,
    bindingLocationsRequest: {
      revision,
      entity: existing,
      fieldId: "name",
      side: "current",
      offset: 0,
      limit: 1,
      completeDocumentDisclosure: true,
    },
    documentsResponse: inventory,
    entitiesResponse: page([entity]),
    relationsResponse: page([{ relationId: "link", from: existing, to: sibling }]),
    draftResponse: page([
      { entity: existing, disposition: "retain", fields: [], references: [], placements: [] },
    ]),
    containmentResponse: page([{ relationId: "contains", parent: existing, child: fresh }]),
    placementsResponse: page([placement]),
    documentResponse: document,
    bindingsResponse: page([binding]),
    bindingLocationsResponse: page([location]),
  }))
    shape(name, value);
});
it("uses all nine exact routes and retains detached request scope without hidden disclosure", async () => {
  const replies = [
    inventory,
    page([entity]),
    page([{ relationId: "link", from: existing, to: sibling }]),
    page([]),
    page([]),
    page([placement]),
    page([binding]),
    document,
    page([location]),
  ];
  const { api, transport } = await client(...replies);
  const request = { ...pageRequest, offset: 0 as number };
  const results = [
    await api.documents(planId, { revision }),
    await api.entities(planId, request),
    await api.relations(planId, pageRequest),
    await api.draft(planId, { revision, offset: 0, limit: 1 }),
    await api.containment(planId, { revision, offset: 0, limit: 1 }),
    await api.placements(planId, {
      revision,
      documentId: "mock-a",
      projectionId: "sample-projection",
      offset: 0,
      limit: 1,
    }),
    await api.bindings(planId, { revision, entity: existing, offset: 0, limit: 1 }),
    await api.document(planId, documentRequest),
    await api.bindingLocations(planId, {
      revision,
      entity: existing,
      fieldId: "name",
      side: "current",
      offset: 0,
      limit: 1,
      completeDocumentDisclosure: true,
    }),
  ];
  request.offset = 1;
  expect(results[1].request).toEqual(pageRequest);
  expect(Object.isFrozen(results[1].request)).toBe(true);
  expect(results.map((value) => value.response)).toEqual(replies);
  expect(transport.mock.calls.slice(1).map(([url, init]) => [url, init?.method])).toEqual(
    [
      "documents",
      "entities",
      "relations",
      "draft",
      "containment",
      "placements",
      "bindings",
      "document",
      "binding-locations",
    ].map((name) => [`/api/v3/plans/${planId}/views/${name}`, "POST"]),
  );
  for (const [, init] of transport.mock.calls.slice(1))
    expect(init?.headers).toMatchObject({ "X-CSRF-TOKEN": "mock-csrf" });
  expect(transport.mock.calls.every(([url]) => !String(url).includes("Disclosure"))).toBe(true);
});
it("requires complete pages including the tail and beyond-end with no automatic continuation", async () => {
  const { api, transport } = await client(
    page([entity], 2, 0, 1),
    page([{ ...entity, entity: sibling }], 2, 1),
    page([], 2, 9),
  );
  const first = await api.entities(planId, pageRequest);
  expect(transport).toHaveBeenCalledTimes(2);
  const tail = await api.entities(planId, { ...pageRequest, offset: 1 });
  const beyond = await api.entities(planId, { ...pageRequest, offset: 9 });
  expect(() => assertPhysicalPagesCompatible(first, tail)).not.toThrow();
  expect(() => assertPhysicalPagesCompatible(first, beyond)).not.toThrow();
  for (const bad of [
    page([], 2, 0, 1),
    page([entity], 2, 0, null),
    page([entity], 2, 1, null),
    page([entity], 0),
    { ...page([entity]), revision: "1" },
  ]) {
    transport.mockResolvedValueOnce(json(bad));
    await expect(api.entities(planId, pageRequest)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
});
it("refuses page joins across side entity projection endpoint plan or total", async () => {
  const { api } = await client(
    page([entity]),
    page([entity]),
    page([entity]),
    page([entity], 2, 0, 1),
    page([]),
    page([]),
    page([]),
    page([]),
  );
  const first = await api.entities(planId, pageRequest),
    target = await api.entities(planId, { ...pageRequest, side: "target" });
  const foreign = await api.entities("30000000-0000-0000-0000-000000000004", pageRequest);
  const changedTotal = await api.entities(planId, pageRequest);
  for (const next of [target, foreign, changedTotal])
    expect(() => assertPhysicalPagesCompatible(first, next)).toThrow("CONFLICT");
  const a = await api.bindings(planId, { revision, entity: existing, offset: 0, limit: 1 });
  const b = await api.bindings(planId, { revision, entity: sibling, offset: 0, limit: 1 });
  expect(() => assertPhysicalPagesCompatible(a, b)).toThrow("CONFLICT");
  const c = await api.placements(planId, {
    revision,
    documentId: "mock-a",
    projectionId: "first",
    offset: 0,
    limit: 1,
  });
  const d = await api.placements(planId, {
    revision,
    documentId: "mock-a",
    projectionId: "second",
    offset: 0,
    limit: 1,
  });
  expect(() => assertPhysicalPagesCompatible(c, d)).toThrow("CONFLICT");
  expect(() => assertPhysicalPagesCompatible(a, c)).toThrow("CONFLICT");
});
it("retains field document and revision scope even for complete empty pages", async () => {
  const { api } = await client(
    page([]),
    page([]),
    { ...page([]), revision: "2" },
    page([]),
    page([]),
  );
  const request = {
    revision,
    entity: existing,
    fieldId: "name",
    side: "current",
    offset: 0,
    limit: 1,
    completeDocumentDisclosure: true,
  } as const;
  const first = await api.bindingLocations(planId, request);
  const field = await api.bindingLocations(planId, { ...request, fieldId: "other" });
  const changedRevision = await api.bindingLocations(planId, { ...request, revision: "2" });
  expect(() => assertPhysicalPagesCompatible(first, field)).toThrow("CONFLICT");
  expect(() => assertPhysicalPagesCompatible(first, changedRevision)).toThrow("CONFLICT");
  const placementRequest = {
    revision,
    documentId: "mock-a",
    projectionId: "first",
    offset: 0,
    limit: 1,
  };
  const a = await api.placements(planId, placementRequest);
  const b = await api.placements(planId, { ...placementRequest, documentId: "mock-b" });
  expect(() => assertPhysicalPagesCompatible(a, b)).toThrow("CONFLICT");
});
it("rejects missing required flags unknown fields and nonscalar text without invented defaults", async () => {
  const { api, transport } = await client();
  for (const key of ["exact", "redacted", "unmappedConcreteMayRemain", "omissions"] as const) {
    const bad: Record<string, unknown> = { ...document };
    delete bad[key];
    transport.mockResolvedValueOnce(json(bad));
    await expect(api.document(planId, documentRequest)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
  for (const bad of [
    { ...document, extra: true },
    { ...document, text: "\uD800" },
  ]) {
    transport.mockResolvedValueOnce(json(bad));
    await expect(api.document(planId, documentRequest)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
  const before = transport.mock.calls.length;
  for (const badId of ["../other", "30000000-0000-0000-0000-000000000001\n"])
    await expect(api.documents(badId, { revision })).rejects.toMatchObject({
      code: "INVALID_REQUEST",
    });
  await expect(
    api.documents(planId, { revision, reveal: true } as Parameters<typeof api.documents>[1]),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  expect(transport).toHaveBeenCalledTimes(before);
});
it("preserves missing target versus unchanged and refuses duplicate inventories", async () => {
  const { api } = await client(
    inventory,
    { revision, documents: [{ ...inventory.documents[0], targetDigest: digest, changed: false }] },
    { revision, documents: [{ ...inventory.documents[0], changed: false }] },
    { revision, documents: [...inventory.documents, ...inventory.documents] },
  );
  expect((await api.documents(planId, { revision })).response.documents[0].changed).toBeNull();
  expect((await api.documents(planId, { revision })).response.documents[0].changed).toBe(false);
  for (let i = 0; i < 2; i++)
    await expect(api.documents(planId, { revision })).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
});
it("preserves empty PUBLIC text masking absence and unavailable counts as distinct closed shapes", async () => {
  const values = [
    { state: "value", text: "" },
    { state: "masked" },
    { state: "absent" },
    { state: "unresolved" },
    { state: "unavailable" },
  ];
  const rows = values.map((current, i) => ({
    ...binding,
    fieldId: `field-${i}`,
    token: `[[value:${handle}:field-${i}]]`,
    current,
    target: { state: "unresolved" },
    change: "unresolved",
    currentLocations: { state: "complete", total: 0 },
    targetLocations: { state: "unavailable", code: "INCOMPLETE_TARGET" },
  }));
  shape("bindingsResponse", page(rows));
  const { api, transport } = await client(page(rows));
  const result = await api.bindings(planId, { revision, entity: existing, offset: 0, limit: 100 });
  expect(result.response.items.map((row) => row.current)).toEqual(values);
  for (const current of [
    { state: "masked", text: "leak" },
    { state: "masked", length: 4 },
    { state: "masked", digest },
    { state: "value" },
  ]) {
    transport.mockResolvedValueOnce(json(page([{ ...binding, current }])));
    await expect(
      api.bindings(planId, { revision, entity: existing, offset: 0, limit: 1 }),
    ).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  }
});
it("checks request offset families disclosure and exact revisions before any network action", async () => {
  const { api, transport } = await client();
  await expect(api.entities(planId, { ...pageRequest, offset: 50001 })).rejects.toMatchObject({
    code: "INVALID_REQUEST",
  });
  await expect(
    api.bindings(planId, { revision, entity: existing, offset: 257, limit: 1 }),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  await expect(
    api.document(planId, {
      ...documentRequest,
      completeDocumentDisclosure: false,
    } as unknown as Parameters<typeof api.document>[1]),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  await expect(
    api.bindingLocations(planId, {
      revision,
      entity: existing,
      fieldId: "name",
      side: "current",
      offset: 0,
      limit: 1,
    } as Parameters<typeof api.bindingLocations>[1]),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  for (const rev of ["0", "01", "1\n", "1".repeat(1025)])
    await expect(api.documents(planId, { revision: rev })).rejects.toMatchObject({
      code: "INVALID_REQUEST",
    });
  expect(transport).toHaveBeenCalledTimes(1);
  transport.mockResolvedValueOnce(json(page([location], 64000, 63999)));
  expect(
    (
      await api.bindingLocations(planId, {
        revision,
        entity: existing,
        fieldId: "name",
        side: "current",
        offset: 63999,
        limit: 1,
        completeDocumentDisclosure: true,
      })
    ).response.total,
  ).toBe(64000);
});
it("retains readable explicit drafts with all dispositions and disallows fabricated removed fields", async () => {
  const rows = [
    {
      entity: existing,
      disposition: "retain",
      fields: [{ fieldId: "name", kind: "keep-observed", masked: false, value: null }],
      references: [],
      placements: [],
    },
    {
      entity: fresh,
      disposition: "create",
      fields: [{ fieldId: "name", kind: "entered", masked: false, value: "" }],
      references: [{ referenceId: "link", kind: "to", target: sibling }],
      placements: [],
    },
    { entity: sibling, disposition: "remove", fields: [], references: [], placements: [] },
  ];
  shape("draftResponse", page(rows));
  const { api, transport } = await client(page(rows));
  expect((await api.draft(planId, { revision, offset: 0, limit: 100 })).response.items).toEqual(
    rows,
  );
  transport.mockResolvedValueOnce(json(page([{ ...rows[2], fields: rows[0].fields }])));
  await expect(api.draft(planId, { revision, offset: 0, limit: 100 })).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
});
it("keeps all three document modes explicit and selects UTF16 spans only in matching exact Raw context", async () => {
  const raw = "<r a='🦉'>\r\n <s name='v🦉'/>\r\n</r>";
  const second = "<other name='second'/>\r\n";
  const firstDigest = createHash("sha256").update(raw).digest("hex"),
    secondDigest = createHash("sha256").update(second).digest("hex");
  const docs = {
    revision,
    documents: [
      { documentId: "mock-a", currentDigest: firstDigest, targetDigest: null, changed: null },
      { documentId: "mock-b", currentDigest: secondDigest, targetDigest: null, changed: null },
    ],
  };
  const a = { ...document, text: raw },
    b = { ...document, documentId: "mock-b", text: second };
  const points = [
    { ...location, sourceDigest: firstDigest, span: { start: 22, end: 25 } },
    { ...location, documentId: "mock-b", sourceDigest: secondDigest, span: { start: 13, end: 19 } },
  ];
  shape("bindingLocationsResponse", page(points));
  const { api, transport } = await client(
    docs,
    a,
    b,
    page(points),
    { ...a, mode: "placeholders", exact: false, text: "<r name='[[value:mock]]'/>" },
    { ...a, mode: "formatted", exact: false, text: "<r>\n  <s/>\n</r>" },
  );
  const inventoryRead = await api.documents(planId, { revision }),
    rawA = await api.document(planId, documentRequest);
  const rawB = await api.document(planId, { ...documentRequest, documentId: "mock-b" });
  const locations = await api.bindingLocations(planId, {
    revision,
    entity: existing,
    fieldId: "name",
    side: "current",
    offset: 0,
    limit: 100,
    completeDocumentDisclosure: true,
  });
  expect(rawA.response.text).toBe(raw);
  expect(rawLocationText(inventoryRead, rawA, locations, 0)).toBe("v🦉");
  expect(rawLocationText(inventoryRead, rawB, locations, 1)).toBe("second");
  expect(() => rawLocationText(inventoryRead, rawA, locations, 1)).toThrow("CONFLICT");
  for (const mode of ["placeholders", "formatted"] as const) {
    const display = await api.document(planId, { ...documentRequest, mode });
    expect(() => rawLocationText(inventoryRead, display, locations, 0)).toThrow("CONFLICT");
  }
  // The raw-only rule is independent of a contradictory server `exact` flag.
  for (const mode of ["placeholders", "formatted"] as const) {
    transport.mockResolvedValueOnce(json({ ...a, mode, exact: true }));
    const display = await api.document(planId, { ...documentRequest, mode });
    expect(() => rawLocationText(inventoryRead, display, locations, 0)).toThrow("CONFLICT");
  }
  for (const call of transport.mock.calls.slice(2))
    expect(JSON.parse(String(call[1]?.body)).completeDocumentDisclosure).toBe(true);
  transport.mockResolvedValueOnce(json(page([{ ...points[0], sourceDigest: digest }])));
  const wrong = await api.bindingLocations(planId, {
    revision,
    entity: existing,
    fieldId: "name",
    side: "current",
    offset: 0,
    limit: 1,
    completeDocumentDisclosure: true,
  });
  expect(() => rawLocationText(inventoryRead, rawA, wrong, 0)).toThrow("CONFLICT");
  transport.mockResolvedValueOnce(json(page([{ ...points[0], span: { start: 23, end: 24 } }])));
  const split = await api.bindingLocations(planId, {
    revision,
    entity: existing,
    fieldId: "name",
    side: "current",
    offset: 0,
    limit: 1,
    completeDocumentDisclosure: true,
  });
  expect(() => rawLocationText(inventoryRead, rawA, split, 0)).toThrow("CONFLICT");
});
it("refuses wrong document echoes placements and reversed location spans", async () => {
  const { api, transport } = await client();
  for (const bad of [
    { ...document, documentId: "mock-b" },
    { ...document, mode: "formatted" },
    { ...document, revision: "1" },
  ]) {
    transport.mockResolvedValueOnce(json(bad));
    await expect(api.document(planId, documentRequest)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
  transport.mockResolvedValueOnce(json(page([{ ...placement, documentId: "mock-b" }])));
  await expect(
    api.placements(planId, {
      revision,
      documentId: "mock-a",
      projectionId: "sample-projection",
      offset: 0,
      limit: 1,
    }),
  ).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  transport.mockResolvedValueOnce(json(page([{ ...location, span: { start: 20, end: 19 } }])));
  await expect(
    api.bindingLocations(planId, {
      revision,
      entity: existing,
      fieldId: "name",
      side: "current",
      offset: 0,
      limit: 1,
      completeDocumentDisclosure: true,
    }),
  ).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
});
it("uses returned physical references and placement coordinates in explicit commands", async () => {
  const { api, owner, transport } = await client(
    page([
      entity,
      {
        ...entity,
        entity: sibling,
        fields: [{ fieldId: "name", present: true, masked: false, value: "unselected" }],
      },
    ]),
    page([placement]),
  );
  const plans = new HostedV3Api(owner);
  const current = await api.entities(planId, { ...pageRequest, limit: 100 });
  const coordinates = await api.placements(planId, {
    revision,
    documentId: "mock-a",
    projectionId: "sample-projection",
    offset: 0,
    limit: 100,
  });
  const selected = current.response.items[0].entity,
    unselected = current.response.items[1].entity;
  const requestId = "30000000-0000-0000-0000-000000000009";
  transport.mockResolvedValueOnce(json({ planId, revision: "9007199254740994" }));
  const changed = await plans.command(planId, {
    kind: "bind-field",
    expectedRevision: revision,
    requestId,
    entity: selected,
    fieldId: "name",
    state: { kind: "entered", text: "chosen" },
  });
  transport.mockResolvedValueOnce(json({ planId, revision: "9007199254740995" }));
  const linked = await plans.command(planId, {
    kind: "bind-reference",
    expectedRevision: changed.revision,
    requestId: "30000000-0000-0000-0000-000000000010",
    entity: selected,
    relationId: "link",
    state: { kind: "to", target: unselected },
  });
  const coordinate = coordinates.response.items[0];
  transport.mockResolvedValueOnce(json({ planId, revision: "9007199254740996" }));
  const created = await plans.command(planId, {
    kind: "upsert-entity",
    expectedRevision: linked.revision,
    requestId: "30000000-0000-0000-0000-000000000011",
    decision: {
      kind: "create",
      entity: fresh,
      fields: { name: { kind: "entered", text: "explicit new" } },
      references: {},
    },
    placements: [
      {
        entity: fresh,
        documentId: coordinate.documentId,
        projectionId: coordinates.request.projectionId,
        parent: { kind: "existing", ...coordinate },
      },
    ],
  });
  transport.mockResolvedValueOnce(
    json({
      ...page([{ relationId: "link", from: selected, to: fresh }]),
      revision: created.revision,
    }),
  );
  const target = await api.relations(planId, {
    ...pageRequest,
    revision: created.revision,
    side: "target",
  });
  transport.mockResolvedValueOnce(
    json({
      ...page([
        {
          ...binding,
          current: { state: "absent" },
          target: { state: "value", text: "explicit new" },
          change: "added",
          currentLocations: { state: "unavailable", code: "CURRENT_ENTITY_ABSENT" },
        },
      ]),
      revision: created.revision,
    }),
  );
  expect(
    (
      await api.bindings(planId, {
        revision: created.revision,
        entity: target.response.items[0].to,
        offset: 0,
        limit: 100,
      })
    ).response.items[0].target,
  ).toEqual({ state: "value", text: "explicit new" });
  const bodies = transport.mock.calls.slice(3, 6).map((call) => JSON.parse(String(call[1]?.body)));
  expect(bodies[0].entity).toEqual(existing);
  expect(bodies[1].state.target).toEqual(sibling);
  expect(bodies[2].placements[0].parent).toEqual({ kind: "existing", ...placement });
  expect(current.response.items[1].fields[0].value).toBe("unselected");
  expect(
    bodies.every(
      (body) => JSON.stringify(body.entity ?? body.decision.entity) !== JSON.stringify(sibling),
    ),
  ).toBe(true);
});
it("retains explicit resource/incomplete refusals and rejects late results after logout", async () => {
  const { api, owner, transport } = await client();
  for (const code of ["RESOURCE_LIMIT", "INCOMPLETE_TARGET"]) {
    transport.mockResolvedValueOnce(new Response(JSON.stringify({ code }), { status: 409 }));
    await expect(api.entities(planId, pageRequest)).rejects.toMatchObject({ code });
  }
  expect(transport).toHaveBeenCalledTimes(3);
  let finish: ((value: Response) => void) | undefined;
  transport.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const pending = api.document(planId, documentRequest),
    refused = expect(pending).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  owner.clear();
  expect(transport.mock.calls[3][1]?.signal?.aborted).toBe(true);
  finish?.(json(document));
  await refused;
});
it("keeps long placement coordinates exact and complete zero-location pages distinct from malformed output", async () => {
  const index = "9".repeat(1024);
  const { api, transport } = await client(
    page([{ ...placement, elementIndex: index }]),
    page([], 0, 2147483647),
  );
  expect(
    (
      await api.placements(planId, {
        revision,
        documentId: "mock-a",
        projectionId: "sample-projection",
        offset: 0,
        limit: 1,
      })
    ).response.items[0].elementIndex,
  ).toBe(index);
  const request = {
    revision,
    entity: existing,
    fieldId: "name",
    side: "current" as const,
    offset: 2147483647,
    limit: 1,
    completeDocumentDisclosure: true as const,
  };
  expect((await api.bindingLocations(planId, request)).response.items).toEqual([]);
  for (const invalid of [
    { ...page([]), total: 1 },
    { ...page([]), nextOffset: 0 },
    { ...page([]), items: [{ ...location, attribute: { localName: "name" } }] },
  ]) {
    transport.mockResolvedValueOnce(json(invalid));
    await expect(api.bindingLocations(planId, { ...request, offset: 0 })).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
});
