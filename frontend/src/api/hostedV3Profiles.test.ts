import Ajv from "ajv";
import { afterEach, expect, it, vi } from "vitest";
import workspaceSchema from "../../../docs/contracts/openapi-workspace-v3.json";
import profileSchema from "../../../schemas/profile-inspection-v3.schema.json";
import { HostedApi } from "./hosted";
import { HostedV3Api } from "./hostedV3";
import {
  HostedV3Profiles,
  prepareCapturedProfileSave,
  prepareProfilePublication,
  prepareProfileSave,
  type SaveProfile,
} from "./hostedV3Profiles";

// Independently invented wire fixtures. No installed publication is asserted.
const objectId = "20000000-0000-0000-0000-000000000001";
const definitionId = "20000000-0000-0000-0000-000000000002";
const requestId = "20000000-0000-0000-0000-000000000003";
const digest = "a".repeat(64);
const definition = { objectId: definitionId, workspaceRevision: "9007199254740993" };
const model = {
  schemaVersion: "3",
  id: "mock-profile",
  revision: "9007199254740993",
  logicalDefinitionDigest: digest,
  entities: [{ id: "slot-a", type: "sample", label: "Independent 🦉", requiredInputs: ["name"] }],
  relations: [],
};
const source = '{"schemaVersion":3,"revision":9007199254740993}\r\n';
const draft = {
  objectId,
  workspaceRevision: "1",
  sourceDigest: digest,
  format: "JSON",
  source,
  schemaVersion: "3",
  compilerVersion: "profile-compiler-v3",
  definition,
  state: "draft",
  projection: { kind: "structurally-valid", model, contentDigest: digest, diagnostics: [] },
};
const published = {
  ...draft,
  workspaceRevision: "2",
  state: "published",
  publication: { digest, sourceRevision: "1" },
};
const save: SaveProfile = { expectedRevision: "0", requestId, format: "JSON", source, definition };
const publish = { expectedRevision: "1", requestId };
const summary = {
  objectId,
  workspaceRevision: "2",
  nativeId: model.id,
  nativeRevision: model.revision,
  sourceDigest: digest,
  state: "published",
  contentDigest: digest,
  definition,
};
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  for (const owner of owners) owner.clear();
  owners.length = 0;
  vi.useRealTimers();
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
  return { api: new HostedV3Profiles(owner), owner, transport };
}
it("rejects a profile success that confuses historical structural data with readiness", async () => {
  const { api } = await client({ ...draft, projection: { ...draft.projection, kind: "ready" } });
  await expect(api.profile(objectId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
});
it("rejects a current revision returned for an exact historical request", async () => {
  const { api } = await client({ ...draft, workspaceRevision: "2" });
  await expect(api.profileRevision(objectId, "1")).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
});
it("validates independent fixtures against the workspace and full profile schemas", () => {
  const schemas = new Ajv({ strict: false });
  schemas.addFormat("uuid", /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  schemas.addSchema(
    profileSchema,
    "https://mock.invalid/schemas/profile-inspection-v3.schema.json",
  );
  schemas.addSchema({
    ...workspaceSchema,
    $id: "https://mock.invalid/docs/contracts/openapi-workspace-v3.json",
  });
  for (const [name, value] of Object.entries({
    SaveProfile: save,
    PublishProfile: publish,
    ProfileRevision: published,
    ProfileList: { profiles: [summary] },
  })) {
    const validate = schemas.compile({
      $ref: `https://mock.invalid/docs/contracts/openapi-workspace-v3.json#/components/schemas/${name}`,
    });
    expect(validate(value), JSON.stringify(validate.errors)).toBe(true);
  }
  const validate = schemas.compile({
    $ref: "https://mock.invalid/docs/contracts/openapi-workspace-v3.json#/components/schemas/ProfileRevision",
  });
  expect(validate(draft), JSON.stringify(validate.errors)).toBe(true);
});
it("uses five exact workspace routes and preserves source and immutable history", async () => {
  const { api, transport } = await client(
    { profiles: [summary] },
    published,
    draft,
    draft,
    published,
  );
  expect(await api.profiles()).toEqual({ profiles: [summary] });
  expect(await api.profile(objectId)).toEqual(published);
  expect(await api.profileRevision(objectId, "1")).toEqual(draft);
  expect(await api.saveProfile(prepareProfileSave(objectId, save))).toEqual(draft);
  expect(await api.publishProfile(prepareProfilePublication(objectId, publish))).toEqual(published);
  expect(transport.mock.calls.slice(1).map(([url, init]) => [url, init?.method])).toEqual([
    ["/api/v3/profiles", "GET"],
    [`/api/v3/profiles/${objectId}`, "GET"],
    [`/api/v3/profiles/${objectId}/revisions/1`, "GET"],
    [`/api/v3/profiles/${objectId}`, "PUT"],
    [`/api/v3/profiles/${objectId}/publish`, "POST"],
  ]);
  expect(JSON.parse(String(transport.mock.calls[4][1]?.body))).toEqual(save);
  expect(JSON.parse(String(transport.mock.calls[5][1]?.body))).toEqual(publish);
  expect(transport.mock.calls[4][1]?.headers).toMatchObject({ "X-CSRF-TOKEN": "mock-csrf" });
});
it("prepares capture as exact JSON source with its original reference and no automatic action", async () => {
  const { api, transport } = await client(draft);
  const capture = {
    revision: "99",
    definition: { ...definition },
    format: "json" as const,
    source,
  };
  const prepared = prepareCapturedProfileSave(objectId, "0", requestId, capture);
  capture.definition.workspaceRevision = "100";
  expect(prepared).toEqual({ objectId, command: save });
  expect(Object.isFrozen(prepared.command.definition)).toBe(true);
  expect(transport).toHaveBeenCalledTimes(1);
  const result = await api.saveProfile(prepared);
  expect(result.source).toBe(source);
  expect(result.state).toBe("draft");
  expect(transport).toHaveBeenCalledTimes(2);
});
it("retains exact save and publication replay identities after loss and later edits", async () => {
  const { api, transport } = await client();
  const command = { ...save, definition: { ...definition } };
  const prepared = prepareProfileSave(objectId, command);
  command.source = "changed";
  command.definition.workspaceRevision = "88";
  transport.mockRejectedValueOnce(new Error("synthetic response loss"));
  await expect(api.saveProfile(prepared)).rejects.toMatchObject({ code: "NETWORK_UNCERTAIN" });
  expect(transport).toHaveBeenCalledTimes(2);
  transport.mockResolvedValueOnce(json({ ...draft, workspaceRevision: "9" }));
  await api.profile(objectId);
  transport.mockResolvedValueOnce(json(draft));
  expect((await api.saveProfile(prepared)).workspaceRevision).toBe("1");
  expect(transport.mock.calls[1][1]?.body).toBe(transport.mock.calls[3][1]?.body);
  const publication = prepareProfilePublication(objectId, publish);
  transport.mockResolvedValueOnce(new Response("unreadable successful result"));
  await expect(api.publishProfile(publication)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
  transport.mockResolvedValueOnce(json({ ...draft, workspaceRevision: "10" }));
  await api.profile(objectId);
  transport.mockResolvedValueOnce(json(published));
  expect((await api.publishProfile(publication)).workspaceRevision).toBe("2");
  expect(transport.mock.calls[4][1]?.body).toBe(transport.mock.calls[6][1]?.body);
});
it("rejects malformed and cross-version models without fabricating publication", async () => {
  const bad = [
    { ...draft, schemaVersion: 3 },
    { ...draft, schemaVersion: "2" },
    { ...draft, compilerVersion: "native-compiler-v3" },
    { ...draft, state: "published" },
    { ...draft, publication: {} },
    { ...published, publication: { ...published.publication, exportPolicies: [] } },
    { ...draft, format: "json" },
    { ...draft, objectId: definitionId },
    { ...draft, extra: true },
    { ...draft, projection: { ...draft.projection, diagnostics: [{ code: "UNKNOWN" }] } },
    { ...draft, projection: { ...draft.projection, model: { ...model, bindings: [] } } },
    {
      ...draft,
      projection: {
        ...draft.projection,
        model: { ...model, entities: [{ ...model.entities[0], requiredInputs: ["name", "name"] }] },
      },
    },
    { ...draft, projection: { ...draft.projection, model: { ...model, entities: [] } } },
  ];
  const { api } = await client(...bad);
  for (const _value of bad)
    await expect(api.profile(objectId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  const invalidPublication = await client(draft);
  await expect(
    invalidPublication.api.publishProfile(prepareProfilePublication(objectId, publish)),
  ).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
});
it("validates source scalar UTF8 limits and exact request syntax before transport", async () => {
  const { api, transport } = await client();
  const maximum = "🦉".repeat(262144);
  expect(prepareProfileSave(objectId, { ...save, source: maximum }).command.source).toBe(maximum);
  for (const source of [`${maximum}x`, "\ud800", "\udfff"])
    expect(() => prepareProfileSave(objectId, { ...save, source })).toThrow("INVALID_REQUEST");
  for (const expectedRevision of ["01", "-1", "1\n", "1".repeat(1025)])
    expect(() => prepareProfileSave(objectId, { ...save, expectedRevision })).toThrow(
      "INVALID_REQUEST",
    );
  expect(
    prepareProfileSave(objectId, { ...save, expectedRevision: "1".repeat(1024) }).command
      .expectedRevision,
  ).toHaveLength(1024);
  await expect(api.profile("../definitions")).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  await expect(api.profileRevision(objectId, "0")).rejects.toMatchObject({
    code: "INVALID_REQUEST",
  });
  await expect(
    api.saveProfile({ objectId, command: { ...save, extra: true } } as Parameters<
      typeof api.saveProfile
    >[0]),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  expect(transport).toHaveBeenCalledTimes(1);
});
it("retains complete sorted bounded profile inventories and full physical model", async () => {
  const entries = Array.from({ length: 100 }, (_, i) => ({
    ...summary,
    objectId: `20000000-0000-0000-0000-${String(i).padStart(12, "0")}`,
  }));
  const { api } = await client(
    { profiles: [] },
    { profiles: entries },
    { profiles: [...entries].reverse() },
    { profiles: [summary, summary] },
    { profiles: [...entries, summary] },
  );
  expect((await api.profiles()).profiles).toHaveLength(0);
  expect((await api.profiles()).profiles).toHaveLength(100);
  for (let i = 0; i < 3; i++)
    await expect(api.profiles()).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  const full = {
    ...draft,
    projection: {
      ...draft.projection,
      model: {
        ...model,
        entities: Array.from({ length: 20000 }, (_, i) => ({
          ...model.entities[0],
          id: `slot-${i}`,
        })),
        relations: Array.from({ length: 50000 }, () => ({
          type: "contains",
          from: "slot-0",
          to: "slot-1",
        })),
      },
    },
  };
  const good = await client(full);
  const result = await good.api.profile(objectId);
  expect(result.projection.model.entities).toHaveLength(20000);
  expect(result.projection.model.relations).toHaveLength(50000);
  expect(Object.isFrozen(result.projection.model.entities[0].requiredInputs)).toBe(true);
});
it("preserves explicit 404/409/422/503 refusals and safe diagnostics", async () => {
  const { api, transport } = await client();
  for (const [status, code] of [
    [404, "NOT_FOUND"],
    [409, "CONFLICT"],
    [422, "REJECTED"],
    [503, "WORKSPACE_UNAVAILABLE"],
  ] as const) {
    const diagnostics = [
      {
        code: "DEFINITION_INCOMPLETE",
        severity: "ERROR",
        path: "$",
        message: "Qualification incomplete",
      },
    ];
    transport.mockResolvedValueOnce(json({ code, diagnostics }, status));
    await expect(
      api.publishProfile(prepareProfilePublication(objectId, publish)),
    ).rejects.toMatchObject({ status, code, diagnostics });
  }
  transport.mockResolvedValueOnce(
    new Response(null, {
      status: 503,
      headers: { "Content-Length": "0", "X-Environment-Studio-Code": "CAPACITY" },
    }),
  );
  await expect(api.profile(objectId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  expect(transport).toHaveBeenCalledTimes(6);
});
it("clears an in-flight source response with its shared session", async () => {
  const { api, owner, transport } = await client();
  let complete: ((value: Response) => void) | undefined;
  transport.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  const response = api.profile(objectId);
  const refused = expect(response).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  owner.clear();
  expect(transport.mock.calls[1][1]?.signal?.aborted).toBe(true);
  complete?.(json(draft));
  await refused;
});
it("refuses contradictory successful save results while retaining the original replay command", async () => {
  const mismatches = [
    { ...draft, source: "different" },
    { ...draft, format: "YAML" },
    { ...draft, definition: { ...definition, objectId } },
    { ...draft, definition: { ...definition, workspaceRevision: "8" } },
    published,
  ];
  const { api, transport } = await client(...mismatches, draft);
  const prepared = prepareProfileSave(objectId, save);
  for (const _reply of mismatches)
    await expect(api.saveProfile(prepared)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  expect(await api.saveProfile(prepared)).toEqual(draft);
  expect(new Set(transport.mock.calls.slice(1).map((call) => call[1]?.body)).size).toBe(1);
});
it("refuses publication of another source revision without discarding original replay identity", async () => {
  const { api, transport } = await client(
    { ...published, publication: { ...published.publication, sourceRevision: "8" } },
    published,
  );
  const prepared = prepareProfilePublication(objectId, publish);
  await expect(api.publishProfile(prepared)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
  expect(await api.publishProfile(prepared)).toEqual(published);
  expect(transport.mock.calls[1][1]?.body).toBe(transport.mock.calls[2][1]?.body);
});
it("chains captured source and published history into explicit whole and partial reuse", async () => {
  const planId = "20000000-0000-0000-0000-000000000004";
  const handle = "20000000-0000-0000-0000-000000000005";
  const { api, owner, transport } = await client();
  const plans = new HostedV3Api(owner);
  transport.mockResolvedValueOnce(json({ revision: "7", definition, format: "json", source }));
  const capture = await plans.capture(planId, {
    revision: "7",
    profileId: "mock-profile",
    profileRevision: "1",
    mappings: [{ entity: { kind: "existing", handle }, slotId: "slot-a", label: "Independent 🦉" }],
  });
  transport.mockResolvedValueOnce(json(draft));
  const saved = await api.saveProfile(
    prepareCapturedProfileSave(objectId, "0", requestId, capture),
  );
  expect(saved.state).toBe("draft");
  expect(transport).toHaveBeenCalledTimes(3);
  transport.mockResolvedValueOnce(json(published));
  const history = await api.profileRevision(objectId, "2");
  expect(history.state).toBe("published");
  const profile = { objectId: history.objectId, workspaceRevision: history.workspaceRevision };
  const roots = history.projection.model.entities.map((entity) => entity.id);
  const preview = {
    revision: "7",
    total: 1,
    offset: 0,
    nextOffset: null,
    section: "included",
    items: [
      { slotId: "slot-a", typeId: "sample", label: "Independent 🦉", requiredInputs: ["name"] },
    ],
    previewDigest: digest,
    affectedDerivations: ["by-name"],
    pins: {
      planId,
      revision: "7",
      observationFingerprint: digest,
      profile,
      publicationDigest: digest,
      selectedRoots: roots,
      rootsDigest: digest,
      closureDigest: digest,
    },
  };
  transport.mockResolvedValueOnce(json(preview));
  const whole = await plans.preview(planId, {
    revision: "7",
    profile,
    selection: { kind: "all" },
    section: "included",
    offset: 0,
    limit: 1,
  });
  transport.mockResolvedValueOnce(json(preview));
  const partial = await plans.preview(planId, {
    revision: "7",
    profile,
    selection: { kind: "selected", roots },
    section: "included",
    offset: 0,
    limit: 1,
  });
  expect(partial.previewDigest).toBe(whole.previewDigest);
  const command = {
    kind: "compose-profile" as const,
    expectedRevision: "7",
    requestId,
    profile,
    previewDigest: partial.previewDigest,
    selectedRoots: partial.pins.selectedRoots,
    decisions: [{ kind: "create" as const, slotId: "slot-a", targetSlotId: "new-a" }],
  };
  transport.mockResolvedValueOnce(json({ planId, revision: "8" }));
  expect((await plans.command(planId, command)).revision).toBe("8");
  expect(JSON.parse(String(transport.mock.calls[6][1]?.body))).toEqual(command);
  for (const call of transport.mock.calls.slice(4)) {
    expect(String(call[1]?.body)).not.toContain(source);
    expect(String(call[1]?.body)).not.toContain("logicalDefinitionDigest");
  }
});
