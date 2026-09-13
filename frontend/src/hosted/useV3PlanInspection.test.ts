import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import { useV3PlanInspection } from "./useV3PlanInspection";

const id = "50000000-0000-0000-0000-000000000001";
const digest = "a".repeat(64);
const summary = {
  planId: id,
  revision: "2",
  definition: { objectId: "50000000-0000-0000-0000-000000000002", workspaceRevision: "1" },
  bindingId: "mock-binding",
  destinationId: "mock-db",
  currentCounts: { documents: 2, entities: 2, relations: 0 },
  targetCounts: { documents: 2, entities: 2, relations: 0 },
  inspectionValid: true,
  targetComplete: true,
  exportAvailable: false,
  blockers: [],
  observedDestination: {
    engine: "postgresql",
    identity: {
      systemIdentifier: "7",
      databaseOid: "8",
      databaseName: "mock",
    },
    observationFingerprint: digest,
    evidenceValid: true,
  },
  currentComputedCounts: { nodes: 0, memberships: 0, cooccurrences: 0 },
  targetComputedCounts: { nodes: 0, memberships: 0, cooccurrences: 0 },
};
const inventory = {
  revision: "2",
  documents: [
    { documentId: "mock-a", currentDigest: digest, targetDigest: "b".repeat(64), changed: true },
    { documentId: "mock-b", currentDigest: digest, targetDigest: digest, changed: false },
  ],
};
const owners: HostedApi[] = [];
afterEach(() => {
  for (const api of owners) api.clear();
  owners.length = 0;
});
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
function document(side = "current", documentId = "mock-a", mode = "raw") {
  return {
    revision: "2",
    side,
    documentId,
    mode,
    text: side === "current" ? '<mock value="before"/>\r\n' : '<mock value="after"/>\r\n',
    exact: mode === "raw",
    redacted: false,
    unmappedConcreteMayRemain: true,
    omissions: [],
  };
}
async function setup() {
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path, options) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (path === "/api/v3/plans/current" || path === `/api/v3/plans/${id}`) return json(summary);
    if (path === `/api/v3/plans/${id}/views/documents`) return json(inventory);
    if (path === `/api/v3/plans/${id}/views/document`) {
      const request = JSON.parse(String(options?.body));
      return json(document(request.side, request.documentId, request.mode));
    }
    throw new Error("Unexpected invented test route");
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const hook = renderHook(({ enabled }) => useV3PlanInspection(api, enabled), {
    initialProps: { enabled: true },
  });
  await waitFor(() => expect(hook.result.current.phase).toBe("loaded"));
  return { ...hook, transport };
}
it("requires explicit disclosure, preserves complete inventory and displays only a verified pair", async () => {
  const { result, transport } = await setup();
  expect(result.current.inventory?.documents).toHaveLength(2);
  act(() => result.current.select("mock-a"));
  const calls = transport.mock.calls.length;
  await act(() => result.current.load());
  expect(transport).toHaveBeenCalledTimes(calls);
  act(() => result.current.setConsent(true));
  await act(() => result.current.load());
  expect(result.current.current?.text).toBe('<mock value="before"/>\r\n');
  expect(result.current.target?.text).toBe('<mock value="after"/>\r\n');
  const requests = transport.mock.calls.filter(([path]) =>
    String(path).endsWith("/views/document"),
  );
  expect(requests.map(([, options]) => JSON.parse(String(options?.body)))).toEqual([
    {
      revision: "2",
      documentId: "mock-a",
      mode: "raw",
      completeDocumentDisclosure: true,
      side: "current",
    },
    {
      revision: "2",
      documentId: "mock-a",
      mode: "raw",
      completeDocumentDisclosure: true,
      side: "target",
    },
  ]);
});
it.each(["document", "empty", "mode", "consent", "version", "refresh"])(
  "discards late document replies after %s changes",
  async (change) => {
    const { result, transport, rerender } = await setup();
    act(() => {
      result.current.select("mock-a");
      result.current.setConsent(true);
    });
    let resolve!: (value: Response) => void;
    transport.mockReturnValueOnce(
      new Promise<Response>((done) => {
        resolve = done;
      }),
    );
    let pending!: Promise<void>;
    act(() => {
      pending = result.current.load();
    });
    if (change === "document") act(() => result.current.select("mock-b"));
    if (change === "empty") act(() => result.current.select(""));
    if (change === "mode") act(() => result.current.setMode("formatted"));
    if (change === "consent") act(() => result.current.setConsent(false));
    if (change === "version") rerender({ enabled: false });
    if (change === "refresh") await act(() => result.current.refresh());
    await act(async () => {
      resolve(json(document()));
      await pending;
    });
    expect(result.current.current).toBeNull();
    expect(result.current.target).toBeNull();
    expect(
      transport.mock.calls.filter(([path]) => String(path).endsWith("/views/document")),
    ).toHaveLength(1);
  },
);
it("keeps both panes empty if target fails after current succeeds", async () => {
  const { result, transport } = await setup();
  act(() => {
    result.current.select("mock-a");
    result.current.setConsent(true);
  });
  transport
    .mockResolvedValueOnce(json(document()))
    .mockResolvedValueOnce(json({ code: "CONFLICT" }, 409));
  await act(() => result.current.load());
  expect(result.current.current).toBeNull();
  expect(result.current.target).toBeNull();
  expect(result.current.error).not.toBe("");
});
it("rejects a same-revision observation invalidation before revealing the pair", async () => {
  const { result, transport } = await setup();
  act(() => {
    result.current.select("mock-a");
    result.current.setConsent(true);
  });
  transport
    .mockResolvedValueOnce(json(document()))
    .mockResolvedValueOnce(json(document("target")))
    .mockResolvedValueOnce(json({ ...summary, inspectionValid: false }));
  await act(() => result.current.load());
  expect(result.current.current).toBeNull();
  expect(result.current.target).toBeNull();
  expect(result.current.error).not.toBe("");
});
it("does not turn an unavailable workspace into an absent plan or a legacy lookup", async () => {
  const { result, transport } = await setup();
  transport.mockClear();
  transport.mockResolvedValueOnce(json({ code: "UNAVAILABLE" }, 503));
  await act(() => result.current.refresh());
  expect(result.current.phase).toBe("error");
  expect(result.current.plan).toBeNull();
  expect(transport.mock.calls.map(([path]) => path)).toEqual(["/api/v3/plans/current"]);
});

it("does not expose the first pane while the second response remains pending", async () => {
  const { result, transport } = await setup();
  act(() => {
    result.current.select("mock-a");
    result.current.setConsent(true);
  });
  let release!: (value: Response) => void;
  transport.mockResolvedValueOnce(json(document())).mockReturnValueOnce(
    new Promise<Response>((resolve) => {
      release = resolve;
    }),
  );
  let pending!: Promise<void>;
  act(() => {
    pending = result.current.load();
  });
  await waitFor(() =>
    expect(
      transport.mock.calls.filter(([path]) => String(path).endsWith("/views/document")),
    ).toHaveLength(2),
  );
  expect(result.current.current).toBeNull();
  expect(result.current.target).toBeNull();
  await act(async () => {
    release(json(document("target")));
    await pending;
  });
  expect(result.current.current?.text).toBe(document().text);
  expect(result.current.target?.text).toBe(document("target").text);
});

it("clears document consent and content when the no-document option is selected", async () => {
  const { result } = await setup();
  act(() => {
    result.current.select("mock-a");
    result.current.setConsent(true);
  });
  await act(() => result.current.load());
  expect(result.current.current).not.toBeNull();
  act(() => result.current.select(""));
  expect(result.current.selected).toBe("");
  expect(result.current.consent).toBe(false);
  expect(result.current.current).toBeNull();
  expect(result.current.target).toBeNull();
});
