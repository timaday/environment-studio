import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import { useV3ProfileCatalog } from "./useV3ProfileCatalog";

// Independently invented wire fixtures; stored publication is not runtime authority.
const id = "70000000-0000-0000-0000-000000000003";
const digest = "d".repeat(64);
const definition = { objectId: "70000000-0000-0000-0000-000000000002", workspaceRevision: "2" };
const entry = {
  objectId: id,
  workspaceRevision: "3",
  nativeId: "portable",
  nativeRevision: "1",
  sourceDigest: digest,
  contentDigest: digest,
  state: "draft",
  definition,
};
const revision = {
  objectId: id,
  workspaceRevision: "3",
  sourceDigest: digest,
  format: "JSON",
  source: "{}",
  schemaVersion: "3",
  compilerVersion: "profile-compiler-v3",
  definition,
  state: "draft",
  projection: {
    kind: "structurally-valid",
    contentDigest: digest,
    diagnostics: [],
    model: {
      schemaVersion: "3",
      id: "portable",
      revision: "1",
      logicalDefinitionDigest: digest,
      entities: [{ id: "first", type: "item", label: "First item", requiredInputs: ["id"] }],
      relations: [],
    },
  },
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  cleanup();
  for (const api of owners) api.clear();
  owners.length = 0;
});
async function setup() {
  const fetcher = vi.fn<typeof fetch>().mockImplementation(async (path) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: new Date(Date.now() + 28_800_000).toISOString(),
      });
    if (path === "/api/v3/profiles") return json({ profiles: [entry] });
    if (path === `/api/v3/profiles/${id}/revisions/3`) return json(revision);
    throw new Error("Unexpected catalogue fixture route");
  });
  const api = new HostedApi(fetcher);
  owners.push(api);
  await api.session();
  const hook = renderHook(({ enabled }) => useV3ProfileCatalog(api, enabled), {
    initialProps: { enabled: true },
  });
  return { ...hook, fetcher, api };
}
it("loads explicitly and reads exactly the selected immutable revision without mutation", async () => {
  const { result, fetcher } = await setup();
  expect(result.current.profiles).toBeNull();
  expect(fetcher.mock.calls).toHaveLength(1);
  await act(() => result.current.load());
  expect(result.current.profiles).toEqual([entry]);
  expect(result.current.selected).toBeNull();
  await act(() => result.current.select(id));
  expect(result.current.selected).toEqual(revision);
  expect(fetcher.mock.calls.map(([path]) => path)).toEqual([
    "/api/v1/session",
    "/api/v3/profiles",
    `/api/v3/profiles/${id}/revisions/3`,
  ]);
  expect(
    fetcher.mock.calls.every(([, options]) => !options?.method || options.method === "GET"),
  ).toBe(true);
});
it("distinguishes empty catalogue from a failed read and retries only explicitly", async () => {
  const { result, fetcher } = await setup();
  fetcher.mockResolvedValueOnce(json({ profiles: [] }));
  await act(() => result.current.load());
  expect(result.current.profiles).toEqual([]);
  fetcher.mockRejectedValueOnce(new TypeError("Synthetic unavailable transport"));
  await act(() => result.current.load());
  expect(result.current.profiles).toBeNull();
  expect(result.current.error).toBe("NETWORK_UNCERTAIN");
  expect(fetcher.mock.calls).toHaveLength(3);
  await act(() => result.current.load());
  expect(result.current.profiles).toEqual([entry]);
});
it("refuses selection outside the loaded catalogue without a read", async () => {
  const { result, fetcher } = await setup();
  await act(() => result.current.select(id));
  expect(fetcher.mock.calls).toHaveLength(1);
  expect(result.current.selected).toBeNull();
  await act(() => result.current.load());
  await act(() => result.current.select("70000000-0000-0000-0000-000000000004"));
  expect(fetcher.mock.calls).toHaveLength(2);
  expect(result.current.error).toBe("INVALID_REQUEST");
});
it.each(["nativeId", "nativeRevision", "sourceDigest", "contentDigest", "state", "definition"])(
  "refuses catalogue/detail mismatch in %s",
  async (field) => {
    const { result, fetcher } = await setup();
    const changed = {
      ...entry,
      [field]:
        field === "definition"
          ? { ...definition, workspaceRevision: "9" }
          : field === "state"
            ? "published"
            : field.includes("Digest")
              ? "a".repeat(64)
              : field === "nativeRevision"
                ? "9"
                : "different",
    };
    fetcher.mockResolvedValueOnce(json({ profiles: [changed] }));
    await act(() => result.current.load());
    await act(() => result.current.select(id));
    expect(result.current.selected).toBeNull();
    expect(result.current.error).toContain("CONFLICT");
  },
);
it("retires a pending selection on reload and ignores its late response", async () => {
  const { result, fetcher } = await setup();
  await act(() => result.current.load());
  let release: ((value: Response) => void) | undefined;
  fetcher.mockImplementationOnce(
    () =>
      new Promise<Response>((resolve) => {
        release = resolve;
      }),
  );
  let reading: Promise<void> | undefined;
  act(() => {
    reading = result.current.select(id);
  });
  await act(() => result.current.load());
  if (!release || !reading) throw new Error("Missing pending test read");
  await act(async () => {
    release?.(json(revision));
    await reading;
  });
  expect(result.current.selected).toBeNull();
  expect(result.current.profiles).toEqual([entry]);
  expect(result.current.busy).toBe(false);
});
it("clears content while inactive and does not restore it on reentry", async () => {
  const { result, rerender, fetcher } = await setup();
  await act(() => result.current.load());
  await act(() => result.current.select(id));
  rerender({ enabled: false });
  expect(result.current.selected).toBeNull();
  expect(result.current.profiles).toBeNull();
  await act(() => result.current.load());
  expect(fetcher.mock.calls).toHaveLength(3);
  rerender({ enabled: true });
  expect(result.current.profiles).toBeNull();
  expect(fetcher.mock.calls).toHaveLength(3);
});
it("retires original ownership on session-required response", async () => {
  const { result, fetcher } = await setup();
  await act(() => result.current.load());
  fetcher.mockResolvedValueOnce(json({ code: "SESSION_REQUIRED" }, 401));
  await act(() => result.current.select(id));
  expect(result.current.profiles).toBeNull();
  expect(result.current.selected).toBeNull();
  expect(result.current.error).toBe("SESSION_REQUIRED");
  await act(() => result.current.load());
  expect(fetcher.mock.calls).toHaveLength(3);
});
