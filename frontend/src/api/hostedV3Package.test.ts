import { afterEach, expect, it, vi } from "vitest";
import { ApiFailure, HostedApi } from "./hosted";
import { HostedV3Api } from "./hostedV3";

const planId = "12000000-0000-0000-0000-000000000001";
const revision = "4";
const inputFingerprint = "b".repeat(64);
const session = {
  authenticated: true,
  csrfHeaderName: "X-CSRF-TOKEN",
  csrfToken: "mock-csrf",
  idleTimeoutSeconds: 1800,
  absoluteExpiresAt: "2099-01-01T00:00:00Z",
} as const;
const owners: HostedApi[] = [];

afterEach(() => {
  for (const api of owners) api.clear();
  owners.length = 0;
});

function json(value: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(value), { status, headers });
}
async function client(response: Response) {
  const transport = vi
    .fn<typeof fetch>()
    .mockResolvedValueOnce(json(session))
    .mockResolvedValueOnce(response);
  const owner = new HostedApi(transport);
  owners.push(owner);
  await owner.session();
  return { transport, api: new HostedV3Api(owner) };
}

it("downloads an unqualified guarded package candidate without treating it as export authority", async () => {
  const zip = new Uint8Array([80, 75, 3, 4, 0, 0]);
  const { api, transport } = await client(
    new Response(zip, {
      status: 200,
      headers: {
        "Cache-Control": "no-store",
        "Content-Type": "application/zip",
        "Content-Disposition": 'attachment; filename="environment-studio-guarded-package.zip"',
        "X-Environment-Studio-Qualified": "false",
      },
    }),
  );
  const result = await api.guardedPackageCandidate(planId, { revision, inputFingerprint });
  expect([...new Uint8Array(result.bytes)]).toEqual([...zip]);
  expect(result.filename).toBe("environment-studio-guarded-package.zip");
  expect(result.qualified).toBe(false);
  expect(result.contentType).toBe("application/zip");
  const [, request] = transport.mock.calls[1];
  expect(request?.method).toBe("POST");
  expect(request?.headers).toMatchObject({
    "X-CSRF-TOKEN": "mock-csrf",
    "Content-Type": "application/json",
  });
  expect(JSON.parse(String(request?.body))).toEqual({ revision, inputFingerprint });
});

it("calls the binary transport without rebinding browser fetch", async () => {
  const receivers: unknown[] = [];
  const zip = new Uint8Array([80, 75, 3, 4]);
  const transport = async function (
    this: unknown,
    input: Parameters<typeof fetch>[0],
  ): Promise<Response> {
    receivers.push(this);
    if (input === "/api/v1/session") return json(session);
    return new Response(zip, {
      status: 200,
      headers: {
        "Cache-Control": "no-store",
        "Content-Type": "application/zip",
        "Content-Disposition": 'attachment; filename="environment-studio-guarded-package.zip"',
        "X-Environment-Studio-Qualified": "false",
      },
    });
  } as typeof fetch;
  const owner = new HostedApi(transport);
  owners.push(owner);
  await owner.session();
  await new HostedV3Api(owner).guardedPackageCandidate(planId, { revision, inputFingerprint });
  expect(receivers).toEqual([undefined, undefined]);
});

it("refuses malformed package requests and successful replies that imply qualification", async () => {
  {
    const { api } = await client(new Response(new Uint8Array([1]), { status: 200 }));
    await expect(
      api.guardedPackageCandidate(planId, { revision: "0", inputFingerprint }),
    ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  }
  {
    const { api } = await client(
      new Response(new Uint8Array([1]), {
        status: 200,
        headers: {
          "Cache-Control": "no-store",
          "Content-Type": "application/zip",
          "Content-Disposition": 'attachment; filename="environment-studio-guarded-package.zip"',
          "X-Environment-Studio-Qualified": "true",
        },
      }),
    );
    await expect(
      api.guardedPackageCandidate(planId, { revision, inputFingerprint }),
    ).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
  }
});

it("preserves closed v3 refusal codes from the package endpoint", async () => {
  const { api } = await client(
    new Response(null, {
      status: 409,
      headers: { "Content-Length": "0", "X-Environment-Studio-Code": "EXPORT_UNAVAILABLE" },
    }),
  );
  await expect(api.guardedPackageCandidate(planId, { revision, inputFingerprint })).rejects.toEqual(
    new ApiFailure(409, "EXPORT_UNAVAILABLE"),
  );
});
