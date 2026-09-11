import { afterEach, describe, expect, it, vi } from "vitest";
import { HostedApi } from "./hosted";

afterEach(() => vi.useRealTimers());
const sessionResponse = (idleTimeoutSeconds = 1800) =>
  new Response(
    JSON.stringify({
      authenticated: true,
      csrfHeaderName: "X-CSRF-TOKEN",
      csrfToken: "mock-token",
      idleTimeoutSeconds,
      absoluteExpiresAt: new Date(Date.now() + 28_800_000).toISOString(),
    }),
  );

describe("hosted request authority", () => {
  it("allows public capability discovery before a session exists", async () => {
    const transport = vi.fn().mockResolvedValue(new Response('{"mode":"hosted"}'));
    const api = new HostedApi(transport);
    await expect(api.get("/api/v1/capabilities")).resolves.toEqual({ mode: "hosted" });
    expect(transport).toHaveBeenCalledOnce();
  });
  it("keeps CSRF in memory and clears authority on session expiry", async () => {
    const transport = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            authenticated: true,
            csrfHeaderName: "X-CSRF-TOKEN",
            csrfToken: "independent-token",
            idleTimeoutSeconds: 1800,
            absoluteExpiresAt: new Date(Date.now() + 28_800_000).toISOString(),
          }),
        ),
      )
      .mockResolvedValueOnce(new Response("{}", { status: 401 }));
    const expired = vi.fn();
    const api = new HostedApi(transport, expired);
    await api.session();
    await expect(api.post("/api/v1/plans", {})).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledOnce();
    expect(transport.mock.calls[1][1].headers["X-CSRF-TOKEN"]).toBe("independent-token");
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    await expect(api.post("/api/v1/plans", {})).rejects.toMatchObject({ status: 401 });
    expect(transport).toHaveBeenCalledTimes(2);
  });
  it("never retries a credential submission after transport uncertainty", async () => {
    const transport = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            authenticated: true,
            csrfHeaderName: "X-CSRF-TOKEN",
            csrfToken: "mock-token",
            idleTimeoutSeconds: 1800,
            absoluteExpiresAt: new Date(Date.now() + 28_800_000).toISOString(),
          }),
        ),
      )
      .mockRejectedValueOnce(new Error("do not expose transport detail"));
    const api = new HostedApi(transport, vi.fn());
    await api.session();
    await expect(
      api.credentials("operation", "mock-reader", "mock-password"),
    ).rejects.toMatchObject({ code: "NETWORK_UNCERTAIN" });
    await expect(
      api.credentials("operation", "mock-reader", "mock-password"),
    ).rejects.toMatchObject({ code: "CREDENTIALS_ALREADY_SENT" });
    expect(transport).toHaveBeenCalledTimes(2);
  });
  it("expires local state at idle deadline even while status polls succeed", async () => {
    vi.useFakeTimers();
    const transport = vi
      .fn()
      .mockResolvedValueOnce(sessionResponse(2))
      .mockImplementation(async () => new Response("{}"));
    const expired = vi.fn();
    const api = new HostedApi(transport, expired);
    await api.session();
    await vi.advanceTimersByTimeAsync(1000);
    await api.get("/api/v1/operations/mock-operation");
    await vi.advanceTimersByTimeAsync(1000);
    expect(expired).toHaveBeenCalledOnce();
    await expect(api.post("/api/v1/plans", {})).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
    expect(transport).toHaveBeenCalledTimes(2);
  });
  it("refuses a late successful mutation response after local authority is cleared", async () => {
    let complete!: (response: Response) => void;
    const pending = new Promise<Response>((resolve) => {
      complete = resolve;
    });
    const transport = vi.fn().mockResolvedValueOnce(sessionResponse()).mockReturnValueOnce(pending);
    const api = new HostedApi(transport, vi.fn());
    await api.session();
    const result = api.post("/api/v1/plans", {});
    api.clear();
    complete(new Response('{"planId":"late-mock"}'));
    await expect(result).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
    expect(transport.mock.calls[1][1].signal.aborted).toBe(true);
  });
  it("cannot reinstall a session from an initial response after unmount clearance", async () => {
    let complete!: (response: Response) => void;
    const transport = vi.fn().mockReturnValue(
      new Promise<Response>((resolve) => {
        complete = resolve;
      }),
    );
    const api = new HostedApi(transport, vi.fn());
    const loading = api.session();
    api.clear();
    complete(sessionResponse());
    await expect(loading).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
    await expect(api.post("/api/v1/plans", {})).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  });
});

it("preserves contracted no-body v3 refusal codes as definitive failures", async () => {
  const transport = vi
    .fn()
    .mockResolvedValueOnce(sessionResponse())
    .mockResolvedValueOnce(
      new Response(null, {
        status: 429,
        headers: { "Content-Length": "0", "X-Environment-Studio-Code": "CAPACITY" },
      }),
    );
  const api = new HostedApi(transport);
  await api.session();
  await expect(api.post("/api/v3/plans", {})).rejects.toMatchObject({
    status: 429,
    code: "CAPACITY",
  });
  api.clear();
});

it("only accepts closed refusal headers on the contracted family and empty framing", async () => {
  const scenarios = [
    { path: "/api/v3/plans", length: "0", code: "UNKNOWN_MOCK_CODE", body: null },
    { path: "/api/v3/plans", length: "0", code: "CAPACITY<script>", body: null },
    { path: "/api/v1/plans", length: "0", code: "CAPACITY", body: null },
    { path: "/api/v3/plans", length: undefined, code: "CAPACITY", body: null },
    { path: "/api/v3/plans", length: "0, 0", code: "CAPACITY", body: null },
    { path: "/api/v3/plans", length: "0", code: "CAPACITY", body: '{"code":"CONFLICT"}' },
  ];
  for (const scenario of scenarios) {
    const headers: Record<string, string> = { "X-Environment-Studio-Code": scenario.code };
    if (scenario.length !== undefined) headers["Content-Length"] = scenario.length;
    const transport = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(sessionResponse())
      .mockResolvedValueOnce(new Response(scenario.body, { status: 429, headers }));
    const api = new HostedApi(transport);
    await api.session();
    try {
      await expect(api.post(scenario.path, {})).rejects.toMatchObject({
        status: 429,
        code: "RESPONSE_UNAVAILABLE",
      });
      expect(transport).toHaveBeenCalledTimes(2);
    } finally {
      api.clear();
    }
  }
});

it("retains normal JSON errors and rejects incomplete success despite a refusal header", async () => {
  const transport = vi
    .fn<typeof fetch>()
    .mockResolvedValueOnce(sessionResponse())
    .mockResolvedValueOnce(
      new Response('{"code":"CONFLICT"}', {
        status: 409,
        headers: { "X-Environment-Studio-Code": "UNKNOWN_MOCK_CODE" },
      }),
    )
    .mockResolvedValueOnce(
      new Response(null, {
        status: 200,
        headers: { "Content-Length": "0", "X-Environment-Studio-Code": "CAPACITY" },
      }),
    );
  const api = new HostedApi(transport);
  await api.session();
  try {
    await expect(api.post("/api/v3/plans", {})).rejects.toMatchObject({
      status: 409,
      code: "CONFLICT",
    });
    await expect(api.get("/api/v3/plans/current")).rejects.toMatchObject({
      status: 200,
      code: "RESPONSE_UNAVAILABLE",
    });
  } finally {
    api.clear();
  }
});

it("cannot deliver a late empty-body refusal after the original session was cleared", async () => {
  let finish!: (body: string) => void;
  const response = new Response(null, {
    status: 429,
    headers: { "Content-Length": "0", "X-Environment-Studio-Code": "CAPACITY" },
  });
  vi.spyOn(response, "text").mockReturnValueOnce(
    new Promise<string>((resolve) => {
      finish = resolve;
    }),
  );
  const transport = vi
    .fn<typeof fetch>()
    .mockResolvedValueOnce(sessionResponse())
    .mockResolvedValueOnce(response);
  const api = new HostedApi(transport);
  await api.session();
  const pending = api.post("/api/v3/plans", {});
  await Promise.resolve();
  await Promise.resolve();
  api.clear();
  finish("");
  await expect(pending).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  expect(transport.mock.calls[1][1]?.signal?.aborted).toBe(true);
});

it.each(["completed", "failed"])(
  "retires a %s semantic refusal body with its original session",
  async (outcome) => {
    let finish!: (body: string) => void;
    let fail!: (reason: Error) => void;
    const response = new Response(null, { status: 422 });
    const read = vi.spyOn(response, "text").mockReturnValueOnce(
      new Promise<string>((resolve, reject) => {
        finish = resolve;
        fail = reject;
      }),
    );
    const transport = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(sessionResponse())
      .mockResolvedValueOnce(response);
    const api = new HostedApi(transport);
    await api.session();
    const pending = api.put("/api/v3/definitions/00000000-0000-4000-8000-000000000919", {});
    const retired = expect(pending).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
    await vi.waitFor(() => expect(read).toHaveBeenCalledOnce());
    api.clear();
    if (outcome === "completed")
      finish(
        '{"kind":"rejected","diagnostics":[{"phase":"parse","code":"INVALID_JSON","pointer":"","message":"Invented refusal."}]}',
      );
    else fail(new Error("Invented body failure"));
    await retired;
    expect(transport.mock.calls[1][1]?.signal?.aborted).toBe(true);
  },
);
