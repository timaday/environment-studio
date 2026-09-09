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
