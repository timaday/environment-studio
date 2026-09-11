import { expect, it, vi } from "vitest";
import { HostedApi } from "./hosted";

const id = "00000000-0000-4000-8000-000000000919";
const diagnostic = {
  phase: "parse",
  code: "INVALID_SOURCE",
  pointer: "/source",
  message: "Invented source refusal. 𐀀",
};
const rejected = { kind: "rejected", diagnostics: [diagnostic] };
async function failure(
  body: unknown,
  path = `/api/v3/definitions/${id}`,
  method = "PUT",
  status = 422,
  raw = false,
) {
  const transport = vi
    .fn<typeof fetch>()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          authenticated: true,
          csrfHeaderName: "X-CSRF-TOKEN",
          csrfToken: "mock-token",
          idleTimeoutSeconds: 900,
          absoluteExpiresAt: "2099-01-01T00:00:00Z",
        }),
      ),
    )
    .mockResolvedValueOnce(new Response(raw ? String(body) : JSON.stringify(body), { status }));
  const api = new HostedApi(transport);
  try {
    await api.session();
    try {
      if (method === "PUT") await api.put(path, {});
      else if (method === "POST") await api.post(path, {});
      else await api.get(path);
      throw new Error("EXPECTED_REFUSAL");
    } catch (error) {
      return error;
    }
  } finally {
    api.clear();
  }
}

it("recognizes exact definition and profile semantic save rejections with ordered duplicate diagnostics", async () => {
  for (const family of ["definitions", "profiles"]) {
    for (const phase of ["parse", "shape", "semantic", "publication"]) {
      const diagnostics = [
        { ...diagnostic, phase },
        { ...diagnostic, phase },
      ];
      expect(
        await failure({ kind: "rejected", diagnostics }, `/api/v3/${family}/${id}`),
      ).toMatchObject({ status: 422, code: "REJECTED", diagnostics });
    }
  }
});
it("preserves inclusive diagnostic and safe-code limits", async () => {
  const diagnostics = Array.from({ length: 256 }, () => ({ ...diagnostic, code: "A".repeat(128) }));
  expect(await failure({ kind: "rejected", diagnostics })).toMatchObject({
    code: "REJECTED",
    diagnostics,
  });
});
it.each([
  null,
  [],
  {},
  { code: "REJECTED" },
  { kind: "rejected" },
  { kind: "rejected", diagnostics: [] },
  { kind: "rejected", diagnostics: Array(257).fill(diagnostic) },
  { ...rejected, extra: true },
  { ...rejected, code: "REJECTED" },
  { ...rejected, kind: "incomplete" },
  ...[
    null,
    [],
    {},
    { ...diagnostic, extra: true },
    { ...diagnostic, phase: "unknown" },
    { ...diagnostic, code: "bad" },
    { ...diagnostic, code: "BAD\n" },
    { ...diagnostic, code: "A".repeat(129) },
    { ...diagnostic, pointer: null },
    { ...diagnostic, pointer: "\ud800" },
    { ...diagnostic, message: "\udfff" },
    { ...diagnostic, message: 3 },
  ].map((item) => ({ kind: "rejected", diagnostics: [item] })),
])("does not turn malformed semantic envelopes into definite refusals %#", async (body) => {
  expect(await failure(body)).toMatchObject({
    status: 422,
    code: "RESPONSE_UNAVAILABLE",
    diagnostics: [],
  });
});
it("does not reclassify other routes, methods or postcommit-capable statuses", async () => {
  for (const [path, method] of [
    [`/api/v3/definitions/${id}/publish`, "POST"],
    [`/api/v3/definitions/${id}`, "POST"],
    [`/api/v3/definitions/${id}`, "GET"],
    [`/api/v3/plans/${id}`, "PUT"],
    [`/api/v1/definitions/${id}`, "PUT"],
    [`/api/v3/definitions/${id}/`, "PUT"],
    [`/api/v3/definitions/${id}?revision=1`, "PUT"],
    [`/api/v3/definitions/${id}\n`, "PUT"],
  ])
    expect(await failure(rejected, path, method)).toMatchObject({ code: "HTTP_422" });
  for (const [status, code] of [
    [403, "FORBIDDEN"],
    [413, "TOO_LARGE"],
    [503, "UNAVAILABLE"],
  ] as const)
    expect(await failure({ code }, undefined, "PUT", status)).toMatchObject({ status, code });
});

const rawDiagnostic = JSON.stringify(diagnostic);
it.each([
  `{"kind":"committed","kind":"rejected","diagnostics":[${rawDiagnostic}]}`,
  `{"kind":"rejected","diagnostics":[],"diagnostics":[${rawDiagnostic}]}`,
  `{"kind":"rejected","diagnostics":[{"phase":"invalid","phase":"parse","code":"INVALID_SOURCE","pointer":"","message":"Invented refusal."}]}`,
  String.raw`{"kind":"committed","k\u0069nd":"rejected","diagnostics":[{"phase":"parse","code":"INVALID_SOURCE","pointer":"","message":"Invented refusal."}]}`,
  String.raw`{"kind":"rejected","diagnostics":[{"phase":"parse","code":"INVALID_SOURCE","pointer":"","message":"first","m\u0065ssage":"second"}]}`,
  `{"kind":"rejected","kind":"rejected","diagnostics":[${rawDiagnostic}]}`,
])("keeps duplicate raw JSON members uncertain on both save families %#", async (wire) => {
  for (const family of ["definitions", "profiles"]) {
    expect(await failure(wire, `/api/v3/${family}/${id}`, "PUT", 422, true)).toMatchObject({
      status: 422,
      code: "RESPONSE_UNAVAILABLE",
      diagnostics: [],
    });
  }
});
it("preserves escaped names, JSON-looking diagnostic text, whitespace and repeated ordered entries", async () => {
  const quoted = {
    ...diagnostic,
    pointer: '/a/{"kind":"x"}/𐀀',
    message: 'Invented: "phase": {"kind":"a","kind":"b"} \\ end 𐀀',
  };
  const wire = ` \r\n {"diagnostics" : [${JSON.stringify(quoted)},${JSON.stringify(diagnostic)},${JSON.stringify(quoted)}], "k\\u0069nd" : "rejected"} \t `;
  expect(await failure(wire, undefined, "PUT", 422, true)).toMatchObject({
    code: "REJECTED",
    diagnostics: [quoted, diagnostic, quoted],
  });
});

it.each([
  "",
  '{"kind":"rejected","diagnostics":',
  `${JSON.stringify(rejected)} {}`,
  `${JSON.stringify(rejected)} trailing`,
  '{"kind":"rejected",,"diagnostics":[]}',
])("keeps malformed raw JSON uncertain before classification %#", async (wire) => {
  expect(await failure(wire, undefined, "PUT", 422, true)).toMatchObject({
    status: 422,
    code: "RESPONSE_UNAVAILABLE",
    diagnostics: [],
  });
});
