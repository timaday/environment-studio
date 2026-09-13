import Ajv2020 from "ajv/dist/2020";
import { afterEach, expect, it, vi } from "vitest";
import commandSchema from "../../../schemas/plan-command-v1.schema.json";
import reviewSchema from "../../../schemas/plan-review-v3.schema.json";
import { HostedApi } from "./hosted";
import { HostedV3Api, prepareReview } from "./hostedV3";
import type * as T from "./hostedV3Types";

const planId = "a0000000-0000-4000-8000-000000000001";
const requestId = "a0000000-0000-4000-8000-000000000002";
const request: T.ReviewRequest = {
  expectedRevision: "9007199254740993",
  requestId,
  inputFingerprint: "a".repeat(64),
  destinationId: "mock-destination",
  artifactIntent: "protected-self-contained",
};
const receipt = { planId, revision: request.expectedRevision };
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  for (const owner of owners) owner.clear();
  owners.length = 0;
  vi.useRealTimers();
});
async function client(remaining = 28800000) {
  const transport = vi.fn<typeof fetch>().mockResolvedValueOnce(
    json({
      authenticated: true,
      csrfHeaderName: "X-CSRF-TOKEN",
      csrfToken: "mock-review-csrf",
      idleTimeoutSeconds: 1800,
      absoluteExpiresAt: new Date(Date.now() + remaining).toISOString(),
    }),
  );
  const owner = new HostedApi(transport);
  owners.push(owner);
  await owner.session();
  return { api: new HostedV3Api(owner), owner, transport };
}
const schemas = new Ajv2020({ strict: true }).addSchema(commandSchema).addSchema(reviewSchema);
const requestShape = schemas.compile({ $ref: `${reviewSchema.$id}#/$defs/request` });
const receiptShape = schemas.compile({ $ref: `${reviewSchema.$id}#/$defs/response` });

it("posts the exact schema-valid review and returns only its unchanged revision receipt", async () => {
  expect(requestShape(request)).toBe(true);
  expect(receiptShape(receipt)).toBe(true);
  const { api, transport } = await client();
  transport.mockResolvedValueOnce(json(receipt));
  const retained = prepareReview(request);
  const result = await api.review(planId, retained);
  expect(result).toEqual(receipt);
  expect(Object.isFrozen(result)).toBe(true);
  expect(retained).not.toBe(request);
  expect(Object.isFrozen(retained)).toBe(true);
  expect(transport).toHaveBeenCalledTimes(2);
  const [path, options] = transport.mock.calls[1];
  expect(path).toBe(`/api/v3/plans/${planId}/reviews`);
  expect(options).toMatchObject({
    method: "POST",
    credentials: "same-origin",
    cache: "no-store",
    redirect: "error",
  });
  expect(new Headers(options?.headers).get("X-CSRF-TOKEN")).toBe("mock-review-csrf");
  expect(JSON.parse(options?.body as string)).toEqual(request);
});

it("keeps maximum decimal revisions exact without expanding the body budget", async () => {
  const maximum = { ...request, expectedRevision: "9".repeat(1024), destinationId: "a".repeat(64) };
  const { api, transport } = await client();
  transport.mockResolvedValueOnce(json({ planId, revision: maximum.expectedRevision }));
  await expect(api.review(planId, maximum)).resolves.toEqual({
    planId,
    revision: maximum.expectedRevision,
  });
  expect(requestShape(maximum)).toBe(true);
  expect(new TextEncoder().encode(transport.mock.calls[1][1]?.body as string).length).toBeLessThan(
    16384,
  );
});

it("rejects malformed or authority-bearing requests before any network work", async () => {
  const { api, transport } = await client();
  const candidates: unknown[] = [null, [], {}, Object.create(request)];
  for (const change of [
    { expectedRevision: "0" },
    { expectedRevision: "02" },
    { expectedRevision: 2 },
    { expectedRevision: "9".repeat(1025) },
    { expectedRevision: "2\n" },
    { requestId: requestId.toUpperCase() },
    { requestId: "1-1-1-1-1" },
    { inputFingerprint: "A".repeat(64) },
    { inputFingerprint: `${"a".repeat(64)}\n` },
    { destinationId: "mock_invalid" },
    { destinationId: "a".repeat(65) },
    { destinationId: "\ud800" },
    { artifactIntent: "masked-preview" },
    { artifactIntent: null },
    { policies: [] },
    { outcome: "PASS" },
    { xml: "independent-mock" },
    { mutation: {} },
  ])
    candidates.push({ ...request, ...change });
  for (const key of Object.keys(request)) {
    const partial = { ...request } as Record<string, unknown>;
    delete partial[key];
    candidates.push(partial);
  }
  for (const value of candidates) {
    expect(() => prepareReview(value as T.ReviewRequest)).toThrowError("INVALID_REQUEST");
    await expect(api.review(planId, value as T.ReviewRequest)).rejects.toMatchObject({
      status: 0,
      code: "INVALID_REQUEST",
    });
  }
  for (const invalidPlan of ["../mock", planId.toUpperCase(), `${planId}\n`, ""]) {
    await expect(api.review(invalidPlan, request)).rejects.toMatchObject({
      status: 0,
      code: "INVALID_REQUEST",
    });
  }
  expect(transport).toHaveBeenCalledTimes(1);
});

it("rejects a foreign or changed-revision receipt instead of treating it as current review", async () => {
  const { api, transport } = await client();
  for (const value of [
    { ...receipt, planId: requestId },
    { ...receipt, revision: "9007199254740994" },
    { ...receipt, revision: "2" },
  ]) {
    expect(receiptShape(value)).toBe(true);
    transport.mockResolvedValueOnce(json(value));
    await expect(api.review(planId, request)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
  expect(transport).toHaveBeenCalledTimes(4);
});

it("accepts only the closed two-field receipt and never returns claimed authority", async () => {
  const { api, transport } = await client();
  const invalid: unknown[] = [
    {},
    null,
    [],
    { planId },
    { revision: receipt.revision },
    { ...receipt, revision: null },
    { ...receipt, revision: 2 },
    { ...receipt, revision: "09" },
    { ...receipt, revision: "1".repeat(1025) },
    { ...receipt, planId: planId.toUpperCase() },
    { ...receipt, operationId: requestId },
    { ...receipt, operationId: null },
    { ...receipt, inputFingerprint: request.inputFingerprint },
    { ...receipt, checks: [] },
    { ...receipt, exportAvailable: false },
  ];
  for (const value of invalid) {
    expect(receiptShape(value)).toBe(false);
    transport.mockResolvedValueOnce(json(value));
    await expect(api.review(planId, request)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
});

it("retains byte-identical explicit replay after uncertainty and later displayed edits", async () => {
  const source = { ...request, expectedRevision: "2" };
  const retained = prepareReview(source);
  const { api, transport } = await client();
  transport.mockRejectedValueOnce(new Error("independent mock network detail"));
  await expect(api.review(planId, retained)).rejects.toMatchObject({ code: "NETWORK_UNCERTAIN" });
  expect(transport).toHaveBeenCalledTimes(2);
  source.expectedRevision = "3";
  source.inputFingerprint = "b".repeat(64);
  source.destinationId = "mock-other";
  transport.mockResolvedValueOnce(json({ planId, revision: "2" }));
  await expect(api.review(planId, retained)).resolves.toEqual({ planId, revision: "2" });
  expect(transport.mock.calls[1][1]?.body).toBe(transport.mock.calls[2][1]?.body);
  expect(JSON.parse(transport.mock.calls[2][1]?.body as string)).toEqual({
    ...request,
    expectedRevision: "2",
  });
  expect(transport).toHaveBeenCalledTimes(3);
});

it("keeps explicit replay available after unreadable success without making a second call automatically", async () => {
  const { api, transport } = await client();
  const retained = prepareReview(request);
  transport.mockResolvedValueOnce(new Response("unreadable independent mock"));
  await expect(api.review(planId, retained)).rejects.toMatchObject({
    status: 200,
    code: "RESPONSE_UNAVAILABLE",
  });
  expect(transport).toHaveBeenCalledTimes(2);
  transport.mockResolvedValueOnce(json(receipt));
  await expect(api.review(planId, retained)).resolves.toEqual(receipt);
  expect(transport.mock.calls[1][1]?.body).toBe(transport.mock.calls[2][1]?.body);
});

it("correlates against its detached sent request when caller input changes during fetch", async () => {
  const source = { ...request };
  const { api, transport } = await client();
  transport.mockImplementationOnce(async () => {
    source.expectedRevision = "3";
    source.requestId = planId;
    return json(receipt);
  });
  await expect(api.review(planId, source)).resolves.toEqual(receipt);
  expect(JSON.parse(transport.mock.calls[1][1]?.body as string)).toEqual(request);
});

it("propagates owned refusals and exposes no unexpected response detail", async () => {
  const { api, transport } = await client();
  for (const [status, code] of [
    [409, "CONFLICT"],
    [422, "INCOMPLETE_TARGET"],
    [403, "DESTINATION_DENIED"],
  ] as const) {
    transport.mockResolvedValueOnce(json({ code }, status));
    await expect(api.review(planId, request)).rejects.toMatchObject({ status, code });
  }
  transport.mockResolvedValueOnce(
    new Response(null, {
      status: 429,
      headers: { "Content-Length": "0", "X-Environment-Studio-Code": "CAPACITY" },
    }),
  );
  await expect(api.review(planId, request)).rejects.toMatchObject({
    status: 429,
    code: "CAPACITY",
  });
  transport.mockResolvedValueOnce(json({ code: "mock private detail" }, 500));
  await expect(api.review(planId, request)).rejects.toMatchObject({
    status: 500,
    code: "HTTP_500",
  });
  expect(transport).toHaveBeenCalledTimes(6);
});

it("rejects late fetch or JSON receipts after the original session is cleared", async () => {
  for (const stage of ["fetch", "json"]) {
    const { api, owner, transport } = await client();
    let release!: (value: never) => void;
    const held = new Promise<never>((resolve) => {
      release = resolve;
    });
    if (stage === "fetch") transport.mockReturnValueOnce(held);
    else {
      const response = json(receipt);
      vi.spyOn(response, "json").mockReturnValueOnce(held);
      transport.mockResolvedValueOnce(response);
    }
    const pending = api.review(planId, request);
    await Promise.resolve();
    await Promise.resolve();
    owner.clear();
    release((stage === "fetch" ? json(receipt) : receipt) as never);
    await expect(pending).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
    expect(transport.mock.calls[1][1]?.signal?.aborted).toBe(true);
    expect(transport).toHaveBeenCalledTimes(2);
  }
});

it("absolute expiry rejects a late acknowledgement under the original request owner", async () => {
  vi.useFakeTimers();
  const { api, transport } = await client(1000);
  let release!: (value: Response) => void;
  transport.mockReturnValueOnce(
    new Promise<Response>((resolve) => {
      release = resolve;
    }),
  );
  const pending = api.review(planId, request);
  await vi.advanceTimersByTimeAsync(1001);
  release(json(receipt));
  await expect(pending).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  expect(transport.mock.calls[1][1]?.signal?.aborted).toBe(true);
  expect(transport).toHaveBeenCalledTimes(2);
});
