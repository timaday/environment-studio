import Ajv2020 from "ajv/dist/2020";
import { afterEach, expect, it, vi } from "vitest";
import planSchema from "../../../docs/contracts/openapi-plans-v3.json";
import commandSchema from "../../../schemas/plan-command-v1.schema.json";
import workflowSchema from "../../../schemas/plan-workflow-v3.schema.json";
import { HostedApi } from "./hosted";
import {
  assertPreviewCompatible,
  assertValidationCompatible,
  HostedV3Api,
  prepareCommand,
} from "./hostedV3";
import type * as T from "./hostedV3Types";

// Independently invented wire fixtures, schema checked below. These are client
// shape tests, not compiler, XML observation or publication qualification.
const planId = "10000000-0000-0000-0000-000000000001";
const operationId = "10000000-0000-0000-0000-000000000002";
const objectId = "10000000-0000-0000-0000-000000000003";
const requestId = "10000000-0000-0000-0000-000000000004";
const handle = "10000000-0000-0000-0000-000000000005";
const revision = "9007199254740993";
const digest = "a".repeat(64);
const reference = { objectId, workspaceRevision: "1" };
const ack = { planId, revision };
const operation = {
  planId,
  operationId,
  phase: "succeeded",
  code: "SUCCEEDED",
  cleanup: "complete",
  installedRevision: revision,
} as const;
const summary = {
  planId,
  revision,
  definition: reference,
  bindingId: "mock",
  destinationId: "mock-db",
  currentCounts: { documents: 1, entities: 1, relations: 0 },
  targetCounts: { documents: 0, entities: 0, relations: 0 },
  inspectionValid: true,
  targetComplete: false,
  exportAvailable: false,
  blockers: ["INCOMPLETE_TARGET"],
  observedDestination: {
    engine: "postgresql",
    identity: { systemIdentifier: "7", databaseOid: "8", databaseName: "mock" },
    observationFingerprint: digest,
    evidenceValid: true,
  },
  currentComputedCounts: { nodes: 0, memberships: 0, cooccurrences: 0 },
  targetComputedCounts: null,
} as const;
const create: T.CreatePlan = {
  expectedRevision: "0",
  requestId,
  definition: reference,
  bindingId: "mock",
  destinationId: "mock-db",
};
const reserve: T.ReserveInspection = {
  expectedRevision: revision,
  requestId,
  discardDraftOnSuccess: true,
};
const capture: T.CaptureRequest = {
  revision,
  profileId: "mock-profile",
  profileRevision: "1",
  mappings: [{ entity: { kind: "existing", handle }, slotId: "slot-a", label: "Independent mock" }],
};
const captureResult = { revision, definition: reference, format: "json", source: "{}" } as const;
const pins = {
  planId,
  revision,
  observationFingerprint: digest,
  profile: reference,
  publicationDigest: digest,
  selectedRoots: ["slot-a"],
  rootsDigest: digest,
  closureDigest: digest,
};
const previewRequest: T.PreviewRequest = {
  revision,
  profile: reference,
  selection: { kind: "selected", roots: ["slot-a"] },
  section: "included",
  offset: 0,
  limit: 1,
};
const previews: T.PreviewResponse[] = [
  {
    revision,
    total: 1,
    offset: 0,
    nextOffset: null,
    items: [
      { slotId: "slot-a", typeId: "sample", label: "Independent mock", requiredInputs: ["name"] },
    ],
    previewDigest: digest,
    pins,
    section: "included",
    affectedDerivations: ["by-name"],
  },
  {
    revision,
    total: 1,
    offset: 0,
    nextOffset: null,
    items: [
      { slotId: "slot-b", causedBy: "slot-a", relationId: "link", reason: "declared-reuse-target" },
    ],
    previewDigest: digest,
    pins,
    section: "dependencies",
    affectedDerivations: ["by-name"],
  },
  {
    revision,
    total: 1,
    offset: 0,
    nextOffset: null,
    items: [{ relationId: "link", fromSlot: "slot-a", toSlot: "slot-b" }],
    previewDigest: digest,
    pins,
    section: "relations",
    affectedDerivations: ["by-name"],
  },
  {
    revision,
    total: 1,
    offset: 0,
    nextOffset: null,
    items: [{ code: "ENTITY_COUNT", slotId: null, relationId: null, ruleId: "count" }],
    previewDigest: digest,
    pins,
    section: "conflicts",
    affectedDerivations: ["by-name"],
  },
];
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
const validation = {
  revision,
  inputFingerprint: digest,
  checks: checks.map((check) => ({ check, outcome: "UNKNOWN", inputFingerprint: digest })),
  applicationRules: [{ ruleId: "count", outcome: "PASS" }],
  targetComplete: false,
  computedRuleCount: null,
  exportAvailable: false,
};
const rule = {
  kind: "COOCCURRENCE",
  declaration: "link",
  source: { computedType: "bucket", derivation: "by-name", value: "mock\\key\n🦉" },
  actual: "0",
  minimum: "0",
  maximum: "999999999999999999999999999999",
  outcome: "PASS",
} as const;
const page = {
  revision,
  inputFingerprint: digest,
  targetComplete: true,
  total: 64000,
  offset: 63999,
  nextOffset: null,
  items: [rule],
} as const;
const pageRequest: T.ValidationPageRequest = {
  revision,
  section: "computed-rules",
  inputFingerprint: digest,
  offset: 63999,
  limit: 1,
};
const materialization = { revision, state: "COMPLETE", complete: true, diagnostics: [] } as const;
const session = () =>
  new Response(
    JSON.stringify({
      authenticated: true,
      csrfHeaderName: "X-CSRF-TOKEN",
      csrfToken: "independent-csrf",
      idleTimeoutSeconds: 1800,
      absoluteExpiresAt: new Date(Date.now() + 28800000).toISOString(),
    }),
  );
const json = (value: unknown) => new Response(JSON.stringify(value));
const owners: HostedApi[] = [];
afterEach(() => {
  for (const owner of owners) owner.clear();
  owners.length = 0;
  vi.useRealTimers();
});
async function client(...responses: unknown[]) {
  const transport = vi.fn<typeof fetch>().mockResolvedValueOnce(session());
  for (const response of responses) transport.mockResolvedValueOnce(json(response));
  const owner = new HostedApi(transport);
  owners.push(owner);
  await owner.session();
  return { api: new HostedV3Api(owner), owner, transport };
}
const schemas = new Ajv2020({ strict: false });
schemas
  .addSchema(commandSchema)
  .addSchema(workflowSchema)
  .addSchema({ ...planSchema, $id: "urn:mock:plans-v3" });
function shape(schema: "plan" | "workflow" | "command", name: string, value: unknown) {
  const ref =
    schema === "plan"
      ? `urn:mock:plans-v3#/components/schemas/${name}`
      : schema === "workflow"
        ? `${workflowSchema.$id}#/$defs/${name}`
        : commandSchema.$id;
  const validate = schemas.compile({ $ref: ref });
  expect(validate(value), JSON.stringify(validate.errors)).toBe(true);
}

it("schema checks the independent request and response fixtures against the actual v3 contracts", () => {
  for (const [name, value] of Object.entries({
    CreatePlan: create,
    ReserveInspection: reserve,
    Ack: ack,
    Operation: operation,
    PlanSummary: summary,
    Materialization: materialization,
  }))
    shape("plan", name, value);
  for (const [name, value] of Object.entries({
    captureRequest: capture,
    captureResponse: captureResult,
    previewRequest,
    validationSummaryResponse: validation,
    validationPageRequest: pageRequest,
    validationPageResponse: page,
  }))
    shape("workflow", name, value);
  for (const value of previews) shape("workflow", "previewResponse", value);
});

it("refuses a successful legacy summary missing required v3 evidence", async () => {
  const { api } = await client({ planId, revision });
  await expect(api.current()).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
});

it("routes lifecycle and materialization only through v3 under the shared CSRF owner", async () => {
  const { api, transport } = await client(
    ack,
    summary,
    summary,
    { ...ack, operationId },
    operation,
    operation,
    operation,
    materialization,
  );
  await expect(api.create(create)).resolves.toEqual(ack);
  await expect(api.current()).resolves.toEqual(summary);
  await expect(api.summary(planId)).resolves.toEqual(summary);
  await expect(api.reserveInspection(planId, reserve)).resolves.toEqual({ ...ack, operationId });
  await expect(api.credentials(operationId, "mock-reader", "mock-password")).resolves.toEqual(
    operation,
  );
  await expect(api.operation(operationId)).resolves.toEqual(operation);
  await expect(api.cancel(operationId)).resolves.toEqual(operation);
  await expect(api.materialize(planId, { revision })).resolves.toEqual(materialization);
  const calls = transport.mock.calls.slice(1);
  expect(calls.map(([path]) => path)).toEqual([
    "/api/v3/plans",
    "/api/v3/plans/current",
    `/api/v3/plans/${planId}`,
    `/api/v3/plans/${planId}/inspections`,
    `/api/v3/operations/${operationId}/credentials`,
    `/api/v3/operations/${operationId}`,
    `/api/v3/operations/${operationId}/cancel`,
    `/api/v3/plans/${planId}/materializations`,
  ]);
  expect(calls.map(([, options]) => options?.method)).toEqual([
    "POST",
    "GET",
    "GET",
    "POST",
    "POST",
    "GET",
    "POST",
    "POST",
  ]);
  expect(calls.map(([, options]) => options?.body)).toEqual([
    JSON.stringify(create),
    undefined,
    undefined,
    JSON.stringify(reserve),
    JSON.stringify({ username: "mock-reader", password: "mock-password" }),
    undefined,
    "{}",
    JSON.stringify({ revision }),
  ]);
  for (const [, options] of calls) {
    expect(options).toMatchObject({
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
    });
    if (options?.method === "POST")
      expect(options.headers).toMatchObject({
        "X-CSRF-TOKEN": "independent-csrf",
        "Content-Type": "application/json",
      });
  }
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

it("captures and reads all preview sections with complete v3 pins even beyond the end", async () => {
  const beyond = { ...previews[0], offset: 50000, items: [] };
  const { api, transport } = await client(captureResult, ...previews, beyond);
  await expect(api.capture(planId, capture)).resolves.toEqual(captureResult);
  for (const result of previews)
    await expect(
      api.preview(planId, { ...previewRequest, section: result.section }),
    ).resolves.toEqual(result);
  const last = await api.preview(planId, { ...previewRequest, offset: 50000 });
  expect(last.affectedDerivations).toEqual(["by-name"]);
  assertPreviewCompatible(previews[0], last);
  expect(transport.mock.calls.slice(1).map(([path]) => path)).toEqual([
    `/api/v3/plans/${planId}/profile-captures`,
    ...Array(5).fill(`/api/v3/plans/${planId}/profile-previews`),
  ]);
  expect(transport.mock.calls[1][1]?.body).toBe(JSON.stringify(capture));
});

it("preserves null versus complete zero and all ten UNKNOWN checks without granting export", async () => {
  const zero = { ...validation, targetComplete: true, computedRuleCount: 0 };
  const { api } = await client(validation, zero);
  const missing = await api.validate(planId, { revision });
  expect(missing.targetComplete).toBe(false);
  expect(missing.computedRuleCount).toBeNull();
  const complete = await api.validate(planId, { revision });
  expect(complete.targetComplete).toBe(true);
  expect(complete.computedRuleCount).toBe(0);
  expect(complete.checks.map((row) => row.check)).toEqual(checks);
  expect(complete.checks.every((row) => row.outcome === "UNKNOWN")).toBe(true);
  expect(complete.exportAvailable).toBe(false);
});

it("reaches offset 63999, pins its complete total and recovers an oversized page only by an explicit smaller request", async () => {
  const { api, transport } = await client({
    ...validation,
    targetComplete: true,
    computedRuleCount: 64000,
  });
  const validated = await api.validate(planId, { revision });
  transport.mockResolvedValueOnce(new Response('{"code":"RESOURCE_LIMIT"}', { status: 422 }));
  await expect(api.validationRules(planId, { ...pageRequest, limit: 100 })).rejects.toMatchObject({
    code: "RESOURCE_LIMIT",
  });
  expect(transport).toHaveBeenCalledTimes(3);
  transport.mockResolvedValueOnce(json(page));
  const last = await api.validationRules(planId, pageRequest);
  assertValidationCompatible(validated, last);
  expect(last.items[0].source?.value).toBe("mock\\key\n🦉");
  expect(last.total).toBe(64000);
  expect(last.nextOffset).toBeNull();
  expect(transport.mock.calls[2][1]?.body).toBe(JSON.stringify({ ...pageRequest, limit: 100 }));
  expect(transport.mock.calls[3][1]?.body).toBe(JSON.stringify(pageRequest));
  await expect(api.preview(planId, { ...previewRequest, offset: 50001 })).rejects.toMatchObject({
    code: "INVALID_REQUEST",
  });
  expect(transport).toHaveBeenCalledTimes(4);
});

it("rejects mixed preview pages and validation totals/fingerprints instead of joining partial evidence", async () => {
  const { api } = await client(
    { ...validation, targetComplete: true, computedRuleCount: 64000 },
    page,
  );
  const validated = await api.validate(planId, { revision });
  const last = await api.validationRules(planId, pageRequest);
  for (const changed of [
    { ...last, total: 1 },
    { ...last, inputFingerprint: "b".repeat(64) },
    { ...last, revision: "2" },
  ])
    expect(() => assertValidationCompatible(validated, changed)).toThrow("CONFLICT");
  for (const changed of [
    { ...previews[0], previewDigest: "b".repeat(64) },
    { ...previews[0], affectedDerivations: [] },
    { ...previews[0], pins: { ...pins, publicationDigest: "b".repeat(64) } },
    { ...previews[0], total: 2 },
  ])
    expect(() => assertPreviewCompatible(previews[0], changed)).toThrow("CONFLICT");
  // Different sections may have different complete totals under the same preview.
  expect(() => assertPreviewCompatible(previews[0], { ...previews[1], total: 2 })).not.toThrow();
});

it("accepts every closed semantic command vocabulary and preserves explicit compose proof", async () => {
  const entity = { kind: "existing", handle } as const;
  const fresh = { kind: "fresh", slotId: "split-a", typeId: "sample" } as const;
  const decision = {
    kind: "create",
    entity: fresh,
    fields: { name: { kind: "entered", text: "explicit" } },
    references: { link: { kind: "to", target: entity } },
  } as const;
  const parent = {
    kind: "existing",
    documentId: "sheet",
    sourceDigest: digest,
    elementIndex: "0",
  } as const;
  const placement = { entity: fresh, documentId: "sheet", projectionId: "items", parent };
  const containment = { relationId: "contains", parent: entity, child: fresh };
  const common = { expectedRevision: revision, requestId };
  const commands: T.PlanCommand[] = [
    {
      ...common,
      kind: "replace-draft",
      draft: { entities: [decision], containment: [containment], placements: [placement] },
    },
    {
      ...common,
      kind: "batch-upsert",
      changes: [{ decision, placements: [placement] }],
      containment: [containment],
    },
    { ...common, kind: "upsert-entity", decision, placements: [placement] },
    { ...common, kind: "forget-entity-decision", entity },
    { ...common, kind: "bind-field", entity, fieldId: "name", state: { kind: "keep-observed" } },
    { ...common, kind: "bind-reference", entity, relationId: "link", state: { kind: "absent" } },
    { ...common, kind: "move-containment", decision: containment, placements: [placement] },
    {
      ...common,
      kind: "compose-profile",
      profile: reference,
      previewDigest: digest,
      selectedRoots: ["slot-a"],
      decisions: [
        { kind: "create", slotId: "slot-a", targetSlotId: "split-a" },
        { kind: "use-existing", slotId: "slot-b", target: entity },
        { kind: "cancel", slotId: "slot-c" },
      ],
    },
    { ...common, kind: "discard" },
  ];
  const { api, transport } = await client(...commands.map(() => ack));
  for (const value of commands) {
    shape("command", "", value);
    await expect(api.command(planId, value)).resolves.toEqual(ack);
  }
  for (let i = 0; i < commands.length; i++) {
    expect(transport.mock.calls[i + 1][0]).toBe(`/api/v3/plans/${planId}/commands`);
    expect(JSON.parse(transport.mock.calls[i + 1][1]?.body as string)).toEqual(commands[i]);
  }
});

it("detaches and freezes exact replay input, retaining old acknowledgement revisions without automatic retry", async () => {
  const source = {
    kind: "bind-field",
    expectedRevision: revision,
    requestId,
    entity: { kind: "existing", handle },
    fieldId: "name",
    state: { kind: "entered", text: "first" },
  } as const;
  const retained = prepareCommand(source);
  if (retained.kind !== "bind-field") throw new Error("MOCK_COMMAND_KIND");
  expect(Object.isFrozen(retained)).toBe(true);
  expect(Object.isFrozen(retained.state)).toBe(true);
  const { api, transport } = await client();
  transport.mockRejectedValueOnce(new Error("synthetic private detail"));
  await expect(api.command(planId, retained)).rejects.toMatchObject({ code: "NETWORK_UNCERTAIN" });
  expect(transport).toHaveBeenCalledTimes(2);
  transport.mockResolvedValueOnce(json({ ...ack, revision: "2" }));
  await expect(api.command(planId, retained)).resolves.toEqual({ ...ack, revision: "2" });
  expect(transport.mock.calls[1][1]?.body).toBe(transport.mock.calls[2][1]?.body);
  expect(transport.mock.calls[2][1]?.body).toContain(revision);
  expect(retained).not.toBe(source);
  expect(retained.state).not.toBe(source.state);
});

it("shares one-shot credential suppression across both families after uncertain delivery", async () => {
  for (const first of ["v1", "v3"]) {
    const { api, owner, transport } = await client();
    transport.mockRejectedValueOnce(new Error("mock uncertainty"));
    const send =
      first === "v1"
        ? () => owner.credentials(operationId, "mock-reader", "mock-password")
        : () => api.credentials(operationId, "mock-reader", "mock-password");
    await expect(send()).rejects.toMatchObject({ code: "NETWORK_UNCERTAIN" });
    await expect(
      owner.credentials(operationId, "mock-reader", "mock-password"),
    ).rejects.toMatchObject({ code: "CREDENTIALS_ALREADY_SENT" });
    await expect(
      api.credentials(operationId, "mock-reader", "mock-password"),
    ).rejects.toMatchObject({ code: "CREDENTIALS_ALREADY_SENT" });
    expect(transport).toHaveBeenCalledTimes(2);
  }
});

it("rejects late fetch and late JSON after clear, and aborts without a fallback request", async () => {
  for (const stage of ["fetch", "json"]) {
    const { api, owner, transport } = await client();
    let complete!: (value: never) => void;
    const pending = new Promise<never>((resolve) => {
      complete = resolve;
    });
    if (stage === "fetch") transport.mockReturnValueOnce(pending);
    else {
      const response = json(summary);
      vi.spyOn(response, "json").mockReturnValueOnce(pending);
      transport.mockResolvedValueOnce(response);
    }
    const reading = api.current();
    await Promise.resolve();
    await Promise.resolve();
    owner.clear();
    complete((stage === "fetch" ? json(summary) : summary) as never);
    await expect(reading).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
    expect(transport.mock.calls[1][1]?.signal?.aborted).toBe(true);
    expect(transport).toHaveBeenCalledTimes(2);
  }
});

it("status polling does not renew idle time", async () => {
  vi.useFakeTimers();
  const transport = vi
    .fn<typeof fetch>()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          authenticated: true,
          csrfHeaderName: "X-CSRF-TOKEN",
          csrfToken: "mock",
          idleTimeoutSeconds: 2,
          absoluteExpiresAt: new Date(Date.now() + 3000).toISOString(),
        }),
      ),
    )
    .mockResolvedValue(json(operation));
  const owner = new HostedApi(transport);
  owners.push(owner);
  await owner.session();
  const api = new HostedV3Api(owner);
  await vi.advanceTimersByTimeAsync(1000);
  await api.operation(operationId);
  await vi.advanceTimersByTimeAsync(1000);
  await expect(api.operation(operationId)).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  expect(transport).toHaveBeenCalledTimes(2);
});

it("rejects malformed closed replies, inconsistent completion, and missing ownership/paging evidence", async () => {
  const badSummaries = [
    { ...summary, currentComputedCounts: undefined },
    { ...summary, targetComputedCounts: undefined },
    { ...summary, exportAvailable: true },
    { ...summary, extra: "authority" },
    { ...summary, currentComputedCounts: { nodes: 0, memberships: 0 } },
    {
      ...summary,
      observedDestination: {
        ...summary.observedDestination,
        identity: { ...summary.observedDestination.identity, databaseName: " " },
      },
    },
  ];
  for (const value of badSummaries) {
    const { api } = await client(value);
    await expect(api.current()).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  }
  const badPreviews = [
    { ...previews[0], affectedDerivations: undefined },
    { ...previews[0], pins: undefined },
    { ...previews[0], pins: { ...pins, closureDigest: undefined } },
    { ...previews[0], pins: { ...pins, revision: "2" } },
    { ...previews[0], pins: { ...pins, selectedRoots: ["wrong"] } },
    { ...previews[0], total: 2, nextOffset: null },
    { ...previews[0], items: [] },
    { ...previews[0], affectedDerivations: ["z", "a"] },
  ];
  for (const value of badPreviews) {
    const { api } = await client(value);
    await expect(api.preview(planId, previewRequest)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
  const badValidations = [
    { ...validation, computedRuleCount: 0 },
    { ...validation, targetComplete: true },
    { ...validation, checks: validation.checks.slice(1) },
    { ...validation, checks: [...validation.checks].reverse() },
    {
      ...validation,
      checks: validation.checks.map((row) => ({ ...row, inputFingerprint: "b".repeat(64) })),
    },
    { ...validation, computedRules: [] },
    {
      ...validation,
      applicationRules: [
        { ruleId: "z", outcome: "PASS" },
        { ruleId: "a", outcome: "PASS" },
      ],
    },
  ];
  for (const value of badValidations) {
    const { api } = await client(value);
    await expect(api.validate(planId, { revision })).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
  for (const value of [
    { ...page, inputFingerprint: "b".repeat(64) },
    { ...page, targetComplete: false },
    { ...page, total: 64001 },
    { ...page, offset: 0 },
    { ...page, items: [] },
    { ...page, items: [{ ...rule, source: { ...rule.source, extra: true } }] },
  ]) {
    const { api } = await client(value);
    await expect(api.validationRules(planId, pageRequest)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
  for (const value of [
    { ...materialization, complete: false },
    { ...materialization, diagnostics: ["unexpected"] },
    { ...materialization, state: "INCOMPLETE" },
  ]) {
    const { api } = await client(value);
    await expect(api.materialize(planId, { revision })).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
  const { api } = await client(
    ack,
    { ...operation, operationId: objectId },
    { ...summary, planId: objectId },
    { ...ack, planId: objectId },
  );
  await expect(api.reserveInspection(planId, reserve)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
  await expect(api.operation(operationId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  await expect(api.summary(planId)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  await expect(
    api.command(planId, { kind: "discard", expectedRevision: revision, requestId }),
  ).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
});

it("does not fall back on wrong-family/missing resources or invent a new command after a conflict", async () => {
  const { api, transport } = await client();
  transport.mockResolvedValueOnce(
    new Response(null, {
      status: 404,
      headers: { "Content-Length": "0", "X-Environment-Studio-Code": "NOT_FOUND" },
    }),
  );
  await expect(api.current()).rejects.toMatchObject({ status: 404, code: "NOT_FOUND" });
  transport.mockResolvedValueOnce(new Response('{"code":"CONFLICT"}', { status: 409 }));
  await expect(
    api.command(planId, { kind: "discard", expectedRevision: revision, requestId }),
  ).rejects.toMatchObject({ status: 409, code: "CONFLICT" });
  expect(transport).toHaveBeenCalledTimes(3);
});

it("rejects sparse command entries before serializing null rows or sending a request", async () => {
  type Decision = Extract<T.PlanCommand, { kind: "replace-draft" }>["draft"]["entities"][number];
  const draft = { entities: new Array<Decision>(1), containment: [], placements: [] };
  const value: T.PlanCommand = {
    kind: "replace-draft",
    expectedRevision: revision,
    requestId,
    draft,
  };
  const { api, transport } = await client(ack);
  await expect(api.command(planId, value)).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  expect(transport).toHaveBeenCalledTimes(1);
});

it("logout aborts pending v3 work and prevents a late response restoring the view", async () => {
  const { api, owner, transport } = await client();
  let finish!: (response: Response) => void;
  transport.mockReturnValueOnce(
    new Promise<Response>((resolve) => {
      finish = resolve;
    }),
  );
  const pending = api.current();
  transport.mockResolvedValueOnce(new Response(null, { status: 204 }));
  await owner.logout();
  expect(transport.mock.calls[2][0]).toBe("/api/v1/session/logout");
  expect(transport.mock.calls[1][1]?.signal?.aborted).toBe(true);
  finish(json(summary));
  await expect(pending).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  await expect(api.current()).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  expect(transport).toHaveBeenCalledTimes(3);
});

it("absolute expiry aborts v3 requests even after successful POSTs renew the idle allowance", async () => {
  vi.useFakeTimers();
  const transport = vi.fn<typeof fetch>().mockResolvedValueOnce(
    new Response(
      JSON.stringify({
        authenticated: true,
        csrfHeaderName: "X-CSRF-TOKEN",
        csrfToken: "mock",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: new Date(Date.now() + 2000).toISOString(),
      }),
    ),
  );
  const owner = new HostedApi(transport);
  owners.push(owner);
  await owner.session();
  const api = new HostedV3Api(owner);
  await vi.advanceTimersByTimeAsync(1000);
  transport.mockResolvedValueOnce(json(validation));
  await api.validate(planId, { revision });
  let finish!: (response: Response) => void;
  transport.mockReturnValueOnce(
    new Promise<Response>((resolve) => {
      finish = resolve;
    }),
  );
  const pending = api.current();
  await vi.advanceTimersByTimeAsync(1000);
  expect(transport.mock.calls[2][1]?.signal?.aborted).toBe(true);
  finish(json(summary));
  await expect(pending).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  await expect(api.operation(operationId)).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  expect(transport).toHaveBeenCalledTimes(3);
});

it("rejects malformed requests locally without a network request or a credential attempt", async () => {
  const { api, transport } = await client();
  await expect(
    api.create({ ...create, expectedRevision: "1" } as unknown as T.CreatePlan),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  await expect(api.summary(`${planId}/other`)).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  await expect(
    api.credentials("not-an-operation", "mock-reader", "mock-password"),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  await expect(
    api.capture(planId, { ...capture, mappings: [...capture.mappings, ...capture.mappings] }),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  await expect(
    api.preview(planId, {
      ...previewRequest,
      selection: { kind: "selected", roots: ["slot-a", "slot-a"] },
    }),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  await expect(api.validationRules(planId, { ...pageRequest, offset: -0 })).rejects.toMatchObject({
    code: "INVALID_REQUEST",
  });
  await expect(
    api.validationRules(planId, { ...pageRequest, offset: 2147483648 }),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  await expect(
    api.command(planId, {
      kind: "discard",
      expectedRevision: revision,
      requestId,
      authority: true,
    } as unknown as T.PlanCommand),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  expect(transport).toHaveBeenCalledTimes(1);
  transport.mockResolvedValueOnce(json(operation));
  await expect(api.credentials(operationId, "mock-reader", "mock-password")).resolves.toEqual(
    operation,
  );
});

it("retains exact capture revision and UTF8 byte limits without interpreting portable source", async () => {
  const exact = "🦉".repeat(262144);
  const { api } = await client(
    { ...captureResult, revision: "2" },
    { ...captureResult, source: exact },
    { ...captureResult, source: `${exact}x` },
  );
  await expect(api.capture(planId, capture)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
  await expect(api.capture(planId, capture)).resolves.toMatchObject({ source: exact });
  await expect(api.capture(planId, capture)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
});

it("keeps exact replay after an unreadable success and does not resend credentials", async () => {
  const retained = prepareCommand({ kind: "discard", expectedRevision: revision, requestId });
  const { api, transport } = await client();
  transport.mockResolvedValueOnce(new Response('{"planId":'));
  await expect(api.command(planId, retained)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
  expect(transport).toHaveBeenCalledTimes(2);
  transport.mockResolvedValueOnce(json(ack));
  await expect(api.command(planId, retained)).resolves.toEqual(ack);
  expect(transport.mock.calls[1][1]?.body).toBe(transport.mock.calls[2][1]?.body);
  transport.mockResolvedValueOnce(new Response('{"phase":'));
  await expect(api.credentials(operationId, "mock-reader", "mock-password")).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
  await expect(api.credentials(operationId, "mock-reader", "mock-password")).rejects.toMatchObject({
    code: "CREDENTIALS_ALREADY_SENT",
  });
  expect(transport).toHaveBeenCalledTimes(4);
});

it("preserves exact XML Unicode keys and decimal cardinalities without lossy coercion", async () => {
  for (const point of [9, 10, 13, 32, 0xd7ff, 0xe000, 0xfffd, 0x10000, 0x10ffff]) {
    const value = String.fromCodePoint(point);
    const { api } = await client({
      ...page,
      items: [{ ...rule, source: { ...rule.source, value } }],
    });
    await expect(api.validationRules(planId, pageRequest)).resolves.toMatchObject({
      items: [{ source: { value }, maximum: rule.maximum }],
    });
  }
  for (const point of [0, 8, 11, 12, 14, 31, 0xd800, 0xdfff, 0xfffe, 0xffff]) {
    const { api } = await client({
      ...page,
      items: [{ ...rule, source: { ...rule.source, value: String.fromCodePoint(point) } }],
    });
    await expect(api.validationRules(planId, pageRequest)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
  for (const actual of [0, "01", "-1", "1.0", "1\n"]) {
    const { api } = await client({ ...page, items: [{ ...rule, actual }] });
    await expect(api.validationRules(planId, pageRequest)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
});
