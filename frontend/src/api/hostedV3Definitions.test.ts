import Ajv from "ajv";
import { afterEach, expect, it, vi } from "vitest";
import workspaceSchema from "../../../docs/contracts/openapi-workspace-v3.json";
import inspectionSchema from "../../../schemas/definition-inspection-v3.schema.json";
import { HostedApi } from "./hosted";
import { HostedV3Api } from "./hostedV3";
import { discoverBinding, HostedV3Definitions } from "./hostedV3Definitions";
import { HostedV3Physical } from "./hostedV3Physical";

// All declarations and source strings are independently invented wire fixtures.
const objectId = "50000000-0000-0000-0000-000000000001";
const revision = "9007199254740993";
const name = (localName: string) => ({ namespaceUri: "", localName });
const field = {
  id: "name",
  valueType: "text",
  required: true,
  classification: "structural",
  sensitivity: "public",
  readable: true,
  editable: true,
};
const child = {
  element: name("property"),
  discriminatorAttribute: name("name"),
  discriminatorValue: "label",
  valueAttribute: name("value"),
};
const projection = {
  id: "items",
  type: "sample",
  path: [name("root"), name("item")],
  fields: [{ field: "name", attribute: name("name") }],
  references: [],
};
const model = {
  schemaVersion: "3",
  id: "invented",
  revision,
  logical: {
    entityTypes: [
      {
        id: "sample",
        label: "Sample",
        fields: [field],
        identity: { field: "name", scope: "type", normalization: "exact" },
      },
    ],
    relations: [],
    rules: [],
    operationCapabilities: ["retain-entity", "create-entity", "bind-field"],
    computedTypes: [],
    derivations: [],
    cooccurrences: [],
    computedRules: [],
  },
  bindings: [
    {
      id: "mock-pg",
      engine: "postgresql",
      storage: "text",
      schema: "mock",
      table: "records",
      keyColumn: "id",
      xmlColumn: "body",
      keyType: "text",
      documents: [{ id: "mock-a", key: "one", entities: [projection] }],
    },
  ],
};
const incomplete = {
  kind: "incomplete",
  model,
  logicalDigest: "a".repeat(64),
  bindingDigests: { "mock-pg": "b".repeat(64) },
  mechanisms: {
    "xml-path-v1": "1",
    "xml-span-v1": "1",
    "generic-graph-v1": "1",
    "native-compiler-v3": "1",
    "derived-graph-v1": "1",
  },
  diagnostics: [
    {
      phase: "publication",
      code: "MECHANISM_NOT_QUALIFIED",
      pointer: "",
      message: "Invented history fixture.",
    },
  ],
};
const document = {
  objectId,
  workspaceRevision: revision,
  sourceDigest: "c".repeat(64),
  format: "JSON",
  source: '{\r\n "schemaVersion": 3\r\n}\r\n',
  schemaVersion: "3",
  compilerVersion: "native-compiler-v3",
  state: "draft",
  projection: incomplete,
};
const owners: HostedApi[] = [];
afterEach(() => {
  for (const owner of owners) owner.clear();
  owners.length = 0;
});
async function client(...replies: unknown[]) {
  const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(
    new Response(
      JSON.stringify({
        authenticated: true,
        csrfHeaderName: "X-CSRF-TOKEN",
        csrfToken: "mock-csrf",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: new Date(Date.now() + 28800000).toISOString(),
      }),
    ),
  );
  for (const reply of replies) fetcher.mockResolvedValueOnce(new Response(JSON.stringify(reply)));
  const owner = new HostedApi(fetcher);
  owners.push(owner);
  await owner.session();
  return { api: new HostedV3Definitions(owner), owner, fetcher };
}
it("refuses another definition revision in the plan's pinned historical read", async () => {
  const { api } = await client({ ...document, workspaceRevision: "2" });
  await expect(api.definitionRevision(objectId, revision)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
});
it("refuses an ambiguous field declaration carrying both attribute and child-property mappings", async () => {
  const bad = structuredClone(document);
  Object.assign(bad.projection.model.bindings[0].documents[0].entities[0].fields[0], {
    childProperty: child,
  });
  const { api } = await client(bad);
  await expect(api.definition(objectId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
});
it("reads published policy tuples in binding then document order when IDs share a prefix", async () => {
  const bindings = ["a", "a-b"].map((id) => ({ ...model.bindings[0], id }));
  const historical = {
    ...document,
    state: "published",
    projection: {
      ...incomplete,
      kind: "historical-ready",
      diagnostics: [],
      bindingDigests: Object.fromEntries(bindings.map((binding) => [binding.id, "b".repeat(64)])),
      model: { ...model, bindings },
    },
    publication: {
      digest: "d".repeat(64),
      sourceRevision: "2",
      exportPolicies: bindings.map((binding) => ({
        bindingId: binding.id,
        documentId: "mock-a",
        content: "deny",
      })),
    },
  };
  const { api } = await client(historical);
  expect((await api.definition(objectId)).state).toBe("published");
});

const richModel = {
  ...model,
  logical: {
    ...model.logical,
    entityTypes: [
      {
        ...model.logical.entityTypes[0],
        fields: [field, { ...field, id: "group", classification: "environment", required: false }],
      },
    ],
    relations: [
      {
        id: "link",
        fromType: "sample",
        toType: "sample",
        kind: "reference",
        minimum: "0",
        maximum: "9".repeat(1024),
        includeTargetOnReuse: true,
      },
    ],
    rules: [
      {
        id: "samples",
        kind: "entity-count",
        type: "sample",
        minimum: "0",
        maximum: "9".repeat(1024),
      },
    ],
    operationCapabilities: [
      "move-relation",
      "bind-field",
      "remove-entity",
      "create-entity",
      "retain-entity",
    ],
    computedTypes: [{ id: "groups", label: "Groups" }],
    derivations: [
      {
        id: "by-group",
        sourceType: "sample",
        sourceField: "group",
        computedType: "groups",
        membershipRelation: "belongs",
      },
    ],
    cooccurrences: [
      {
        id: "together",
        fromDerivation: "by-group",
        toDerivation: "by-group",
        minimum: "0",
        maximum: "7",
      },
    ],
    computedRules: [
      { id: "groups-count", kind: "entity-count", type: "groups", minimum: "0", maximum: "8" },
    ],
  },
  bindings: ["pg", "ora"].map((engine) => ({
    ...model.bindings[0],
    id: `mock-${engine}`,
    engine: engine === "pg" ? "postgresql" : "oracle",
    storage: engine === "pg" ? "text" : "clob",
    documents: ["mock-a", "mock-empty"].map((id, i) => ({
      id,
      key: `mock-${i}`,
      entities: [
        {
          ...projection,
          id: i ? "empty-items" : "items",
          fields: [
            { field: "name", attribute: name("name") },
            { field: "group", childProperty: child },
          ],
          references: [{ relation: "link", attribute: name("peer") }],
        },
      ],
    })),
  })),
};
const rich = {
  ...document,
  projection: {
    ...incomplete,
    model: richModel,
    bindingDigests: { "mock-pg": "b".repeat(64), "mock-ora": "d".repeat(64) },
    mechanisms: { ...incomplete.mechanisms, "xml-child-property-v1": "1" },
  },
};
const summary = {
  objectId,
  workspaceRevision: revision,
  nativeId: model.id,
  nativeRevision: model.revision,
  sourceDigest: document.sourceDigest,
  state: "draft",
  compilationKind: "incomplete",
  logicalDigest: incomplete.logicalDigest,
};
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const schemas = new Ajv({ strict: false });
schemas.addFormat("uuid", /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
schemas.addSchema(
  inspectionSchema,
  "https://mock.invalid/schemas/definition-inspection-v3.schema.json",
);
schemas.addSchema({
  ...workspaceSchema,
  $id: "https://mock.invalid/docs/contracts/openapi-workspace-v3.json",
});
function shape(name: string, value: unknown) {
  const validate = schemas.compile({
    $ref: `https://mock.invalid/docs/contracts/openapi-workspace-v3.json#/components/schemas/${name}`,
  });
  expect(validate(value), JSON.stringify(validate.errors)).toBe(true);
}
it("decodes full physical and computed declarations with independent canonical schema parity", async () => {
  shape("DefinitionRevision", rich);
  shape("DefinitionList", { definitions: [summary] });
  const { api } = await client(rich, { ...rich, format: "YAML" });
  const result = await api.definition(objectId);
  expect(result.projection.model).toEqual(richModel);
  expect(result.projection.mechanisms).toEqual(rich.projection.mechanisms);
  expect(result.source).toBe(document.source);
  expect(Object.isFrozen(result.projection.model.bindings[0].documents[0].entities[0].fields)).toBe(
    true,
  );
  expect((await api.definition(objectId)).format).toBe("YAML");
});
it("uses exactly three versioned GET routes and reads pinned history after a newer current revision", async () => {
  const { api, fetcher } = await client(
    { definitions: [summary] },
    { ...rich, workspaceRevision: "9007199254740994" },
    rich,
  );
  await api.definitions();
  await api.definition(objectId);
  expect((await api.definitionRevision(objectId, revision)).workspaceRevision).toBe(revision);
  expect(fetcher.mock.calls.slice(1).map(([url, init]) => [url, init?.method])).toEqual([
    ["/api/v3/definitions", "GET"],
    [`/api/v3/definitions/${objectId}`, "GET"],
    [`/api/v3/definitions/${objectId}/revisions/${revision}`, "GET"],
  ]);
  for (const [, init] of fetcher.mock.calls.slice(1)) expect(init?.body).toBeUndefined();
});
it("requires a complete sorted unique inventory within the shared 100-object limit", async () => {
  const rows = Array.from({ length: 100 }, (_, i) => ({
    ...summary,
    objectId: `50000000-0000-0000-0000-${String(i + 1).padStart(12, "0")}`,
  }));
  const { api, fetcher } = await client({ definitions: rows }, { definitions: [] });
  expect((await api.definitions()).definitions).toEqual(rows);
  expect((await api.definitions()).definitions).toEqual([]);
  for (const definitions of [[summary, summary], [...rows].reverse(), [...rows, summary]]) {
    fetcher.mockResolvedValueOnce(json({ definitions }));
    await expect(api.definitions()).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  }
});
it("preserves incomplete diagnostics and explicit empty capabilities without adding mechanisms or readiness", async () => {
  const value = {
    ...document,
    projection: {
      ...incomplete,
      diagnostics: [...incomplete.diagnostics, ...incomplete.diagnostics],
      model: { ...model, logical: { ...model.logical, operationCapabilities: [] } },
    },
  };
  const { api, fetcher } = await client(value);
  const read = await api.definition(objectId);
  expect(read.projection.diagnostics).toEqual(value.projection.diagnostics);
  expect(read.projection.model.logical.operationCapabilities).toEqual([]);
  expect(Object.hasOwn(read.projection.mechanisms, "xml-child-property-v1")).toBe(false);
  for (const projection of [
    { ...incomplete, diagnostics: [] },
    { ...incomplete, kind: "historical-ready" },
    { ...incomplete, kind: "ready", diagnostics: [] },
    { ...incomplete, diagnostics: [{ ...incomplete.diagnostics[0], phase: "semantic" }] },
    { ...incomplete, diagnostics: [{ ...incomplete.diagnostics[0], code: "BAD\n" }] },
  ]) {
    fetcher.mockResolvedValueOnce(json({ ...document, projection }));
    await expect(api.definition(objectId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  }
});
it("keeps draft and published shapes closed with complete ordered export policies", async () => {
  const policies = richModel.bindings
    .flatMap((binding) =>
      binding.documents.map((document) => ({
        bindingId: binding.id,
        documentId: document.id,
        content: "deny",
      })),
    )
    .sort((a, b) =>
      a.bindingId < b.bindingId
        ? -1
        : a.bindingId > b.bindingId
          ? 1
          : a.documentId < b.documentId
            ? -1
            : a.documentId > b.documentId
              ? 1
              : 0,
    );
  const published = {
    ...rich,
    state: "published",
    projection: { ...rich.projection, kind: "historical-ready", diagnostics: [] },
    publication: { digest: "e".repeat(64), sourceRevision: "2", exportPolicies: policies },
  };
  shape("DefinitionRevision", published);
  const { api, fetcher } = await client(published);
  expect((await api.definition(objectId)).state).toBe("published");
  for (const value of [
    { ...published, state: "draft" },
    { ...rich, state: "published" },
    { ...published, projection: rich.projection },
    ...[policies.slice(1), [...policies].reverse(), [...policies, policies[0]]].map(
      (exportPolicies) => ({
        ...published,
        publication: { ...published.publication, exportPolicies },
      }),
    ),
  ]) {
    fetcher.mockResolvedValueOnce(json(value));
    await expect(api.definition(objectId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  }
});
it("discovers declarations from exact plan history even without observed entities then uses server placement coordinates", async () => {
  const planId = "50000000-0000-0000-0000-000000000099";
  const reference = { objectId, workspaceRevision: revision };
  const { api, owner, fetcher } = await client(rich, {
    revision,
    total: 1,
    offset: 0,
    nextOffset: null,
    items: [{ documentId: "mock-empty", sourceDigest: "e".repeat(64), elementIndex: "0" }],
  });
  const definition = await api.definitionRevision(objectId, revision);
  const discovery = discoverBinding(definition, reference, "mock-pg");
  expect(discovery).toEqual({
    definition: reference,
    bindingId: "mock-pg",
    engine: "postgresql",
    documents: [
      { documentId: "mock-a", projections: [{ projectionId: "items", typeId: "sample" }] },
      {
        documentId: "mock-empty",
        projections: [{ projectionId: "empty-items", typeId: "sample" }],
      },
    ],
  });
  const selected = discovery.documents[1];
  const physical = new HostedV3Physical(owner),
    plans = new HostedV3Api(owner);
  const placements = await physical.placements(planId, {
    revision,
    documentId: selected.documentId,
    projectionId: selected.projections[0].projectionId,
    offset: 0,
    limit: 1,
  });
  const fresh = {
    kind: "fresh" as const,
    typeId: selected.projections[0].typeId,
    slotId: "explicit",
  };
  fetcher.mockResolvedValueOnce(json({ planId, revision: "9007199254740994" }));
  await plans.command(planId, {
    kind: "upsert-entity",
    expectedRevision: revision,
    requestId: "50000000-0000-0000-0000-000000000005",
    decision: {
      kind: "create",
      entity: fresh,
      fields: { name: { kind: "entered", text: "chosen" } },
      references: {},
    },
    placements: [
      {
        entity: fresh,
        documentId: selected.documentId,
        projectionId: selected.projections[0].projectionId,
        parent: { kind: "existing", ...placements.response.items[0] },
      },
    ],
  });
  expect(JSON.parse(String(fetcher.mock.calls[3][1]?.body)).placements[0].parent.elementIndex).toBe(
    "0",
  );
  fetcher.mockResolvedValueOnce(
    json({ revision, total: 0, offset: 0, nextOffset: null, items: [] }),
  );
  expect((await physical.placements(planId, placements.request)).response.items).toEqual([]);
  expect(fetcher.mock.calls.every(([url]) => !String(url).includes("/views/entities"))).toBe(true);
  expect(() =>
    discoverBinding(definition, { ...reference, workspaceRevision: "2" }, "mock-pg"),
  ).toThrow("CONFLICT");
  expect(() => discoverBinding(definition, reference, "missing")).toThrow("CONFLICT");
});
it("refuses missing ambiguous or unknown declaration joins instead of selecting a default", async () => {
  const { api, fetcher } = await client();
  const values = [
    { ...richModel, bindings: [richModel.bindings[0], richModel.bindings[0]] },
    {
      ...richModel,
      logical: {
        ...richModel.logical,
        entityTypes: [{ ...richModel.logical.entityTypes[0], id: "other" }],
      },
    },
    {
      ...richModel,
      bindings: [
        {
          ...richModel.bindings[0],
          documents: [richModel.bindings[0].documents[0], richModel.bindings[0].documents[0]],
        },
      ],
    },
    {
      ...richModel,
      bindings: [
        {
          ...richModel.bindings[0],
          documents: [
            { ...richModel.bindings[0].documents[0], entities: [projection, projection] },
          ],
        },
      ],
    },
  ];
  for (const model of values) {
    fetcher.mockResolvedValueOnce(json({ ...rich, projection: { ...rich.projection, model } }));
    const decoded = await api.definition(objectId);
    expect(() =>
      discoverBinding(decoded, { objectId, workspaceRevision: revision }, "mock-pg"),
    ).toThrow("CONFLICT");
  }
});
it("rejects both or neither field mapping variants and preserves valid supplementary XML names", async () => {
  const { api, fetcher } = await client();
  for (const fields of [
    [{ field: "name" }],
    [{ field: "name", attribute: name("name"), childProperty: child }],
  ]) {
    const value = structuredClone(document);
    value.projection.model.bindings[0].documents[0].entities[0].fields =
      fields as typeof projection.fields;
    fetcher.mockResolvedValueOnce(json(value));
    await expect(api.definition(objectId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  }
  for (const localName of ["é", "\u{10000}", "\u{effff}x", "a-1"]) {
    const value = structuredClone(document);
    value.projection.model.bindings[0].documents[0].entities[0].path[0].localName = localName;
    shape("DefinitionRevision", value);
    fetcher.mockResolvedValueOnce(json(value));
    expect(
      (await api.definition(objectId)).projection.model.bindings[0].documents[0].entities[0].path[0]
        .localName,
    ).toBe(localName);
  }
  for (const localName of ["a:b", "1bad", "\u{f0000}", "\uD800", "name\n"]) {
    const value = structuredClone(document);
    value.projection.model.bindings[0].documents[0].entities[0].path[0].localName = localName;
    fetcher.mockResolvedValueOnce(json(value));
    await expect(api.definition(objectId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  }
});
it("retains exact scalar source at the UTF8 limit and refuses oversized malformed or foreign responses", async () => {
  const source = "🦉".repeat(262144);
  const { api, fetcher } = await client({ ...document, source });
  expect((await api.definition(objectId)).source).toBe(source);
  for (const value of [
    { ...document, source: `${source}x` },
    { ...document, source: "\uD800" },
    { ...document, objectId: "50000000-0000-0000-0000-000000000002" },
    { ...document, schemaVersion: 3 },
    { ...document, schemaVersion: "2" },
    { ...document, compilerVersion: "native-compiler-v2" },
    { ...document, ready: true },
  ]) {
    fetcher.mockResolvedValueOnce(json(value));
    await expect(api.definition(objectId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  }
  const before = fetcher.mock.calls.length;
  await expect(api.definition("../foreign")).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  for (const value of ["0", "01", "1\n", "1".repeat(1025)])
    await expect(api.definitionRevision(objectId, value)).rejects.toMatchObject({
      code: "INVALID_REQUEST",
    });
  expect(fetcher).toHaveBeenCalledTimes(before);
});
it("keeps safe workspace errors and rejects late history after original session clear", async () => {
  const { api, owner, fetcher } = await client();
  for (const [status, code] of [
    [404, "NOT_FOUND"],
    [503, "UNAVAILABLE"],
    [422, "REJECTED"],
  ] as const) {
    fetcher.mockResolvedValueOnce(json({ code }, status));
    await expect(api.definition(objectId)).rejects.toMatchObject({ code });
  }
  let finish: ((value: Response) => void) | undefined;
  fetcher.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const pending = api.definitionRevision(objectId, revision),
    refused = expect(pending).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  owner.clear();
  finish?.(json(rich));
  await refused;
});
it("requires every logical section and rejects malformed capabilities declarations and binding names", async () => {
  const { api, fetcher } = await client();
  const bad = [];
  for (const key of Object.keys(richModel.logical)) {
    const value = structuredClone(rich);
    delete (value.projection.model.logical as Record<string, unknown>)[key];
    bad.push(value);
  }
  const duplicate = structuredClone(rich);
  duplicate.projection.model.logical.operationCapabilities.push("bind-field");
  bad.push(duplicate);
  const excess = structuredClone(rich);
  excess.projection.model.logical.computedTypes = Array.from({ length: 33 }, (_, i) => ({
    id: `type-${i}`,
    label: "Invented",
  }));
  bad.push(excess);
  const sqlName = structuredClone(rich);
  sqlName.projection.model.bindings[0].table = "unexpected.qualified";
  bad.push(sqlName);
  const identity = structuredClone(rich);
  identity.projection.model.logical.entityTypes[0].identity.scope = "document";
  bad.push(identity);
  for (const value of bad) {
    fetcher.mockResolvedValueOnce(json(value));
    await expect(api.definition(objectId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  }
});
