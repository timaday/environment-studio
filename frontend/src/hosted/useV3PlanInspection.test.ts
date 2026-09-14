import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import native from "../../../fixtures/native-v3/definition.json";
import { HostedApi } from "../api/hosted";
import { useV3PlanInspection } from "./useV3PlanInspection";

const id = "50000000-0000-0000-0000-000000000001";
const digest = "a".repeat(64);
const definitionId = "50000000-0000-0000-0000-000000000002";
const model = JSON.parse(JSON.stringify(native), (_key, value) =>
  typeof value === "number" ? String(value) : value,
);
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
function v3Definition() {
  return {
    objectId: definitionId,
    workspaceRevision: "2",
    sourceDigest: "c".repeat(64),
    source: JSON.stringify(native),
    format: "JSON",
    schemaVersion: "3",
    compilerVersion: "native-compiler-v3",
    state: "published",
    publication: {
      digest: "d".repeat(64),
      sourceRevision: "1",
      exportPolicies: model.bindings
        .flatMap((binding: { id: string; documents: { id: string }[] }) =>
          binding.documents.map((document) => ({
            bindingId: binding.id,
            documentId: document.id,
            content: "protected-self-contained",
          })),
        )
        .sort(
          (
            a: { bindingId: string; documentId: string },
            b: { bindingId: string; documentId: string },
          ) =>
            a.bindingId === b.bindingId
              ? a.documentId.localeCompare(b.documentId)
              : a.bindingId.localeCompare(b.bindingId),
        ),
    },
    projection: {
      kind: "historical-ready",
      model,
      logicalDigest: digest,
      bindingDigests: Object.fromEntries(
        model.bindings.map((binding: { id: string }) => [binding.id, "b".repeat(64)]),
      ),
      mechanisms: {
        "xml-path-v1": "1",
        "xml-span-v1": "1",
        "generic-graph-v1": "1",
        "native-compiler-v3": "1",
        "derived-graph-v1": "1",
      },
      diagnostics: [],
    },
  };
}
function v3DefinitionSummary() {
  return {
    objectId: definitionId,
    workspaceRevision: "2",
    nativeId: "mock-tiles",
    nativeRevision: "1",
    sourceDigest: "c".repeat(64),
    state: "published",
    compilationKind: "historical-ready",
    logicalDigest: digest,
  };
}
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
    if (path === `/api/v3/plans/${id}/views/entities`) {
      const request = JSON.parse(String(options?.body));
      return json({
        revision: request.revision,
        total: 0,
        offset: request.offset,
        nextOffset: null,
        items: [],
      });
    }
    if (path === `/api/v3/plans/${id}/views/bindings`) {
      const request = JSON.parse(String(options?.body));
      return json({
        revision: request.revision,
        total: 0,
        offset: request.offset,
        nextOffset: null,
        items: [],
      });
    }
    if (path === `/api/v3/plans/${id}/views/binding-locations`) {
      const request = JSON.parse(String(options?.body));
      return json({
        revision: request.revision,
        total: 0,
        offset: request.offset,
        nextOffset: null,
        items: [],
      });
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
it("creates a v3 plan from an owned published v3 definition", async () => {
  let created = false;
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path, options) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (path === "/api/v3/plans/current")
      return created
        ? json({
            ...summary,
            revision: "1",
            definition: { objectId: definitionId, workspaceRevision: "2" },
            observedDestination: null,
          })
        : json({ code: "NOT_FOUND" }, 404);
    if (path === "/api/v3/definitions") return json({ definitions: [v3DefinitionSummary()] });
    if (path === `/api/v3/definitions/${definitionId}`) return json(v3Definition());
    if (path === "/api/v1/destinations")
      return json({
        destinations: [
          {
            id: "mock-postgres",
            engine: "postgresql",
            host: "127.0.0.1",
            port: 5432,
            database: "mockdb",
          },
        ],
      });
    if (path === "/api/v3/plans" && options?.method === "POST") {
      const command = JSON.parse(String(options.body));
      expect(command).toMatchObject({
        expectedRevision: "0",
        definition: { objectId: definitionId, workspaceRevision: "2" },
        bindingId: "mock-pg",
        destinationId: "mock-postgres",
      });
      created = true;
      return json({ planId: id, revision: "1" });
    }
    if (path === `/api/v3/plans/${id}`)
      return json({
        ...summary,
        revision: "1",
        definition: { objectId: definitionId, workspaceRevision: "2" },
        observedDestination: null,
      });
    throw new Error(`Unexpected invented test route ${path}`);
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const hook = renderHook(() => useV3PlanInspection(api, true));
  await waitFor(() => expect(hook.result.current.phase).toBe("absent"));
  expect(transport.mock.calls.map(([path]) => path)).toContain("/api/v3/definitions");
  expect(transport.mock.calls.map(([path]) => path)).not.toContain("/api/v2/definitions");

  await act(() => hook.result.current.chooseDefinition(definitionId));
  expect(hook.result.current.definition?.objectId).toBe(definitionId);
  act(() => hook.result.current.chooseBinding("mock-pg"));
  act(() => hook.result.current.chooseDestination("mock-postgres"));
  await act(() => hook.result.current.createPlan());
  await waitFor(() => expect(hook.result.current.plan?.planId).toBe(id));
  expect(hook.result.current.plan?.definition).toEqual({
    objectId: definitionId,
    workspaceRevision: "2",
  });
  expect(hook.result.current.inventory).toBeNull();
});

it("stores a PostgreSQL JDBC target and selects the returned destination", async () => {
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path, options) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (path === "/api/v3/plans/current") return json({ code: "NOT_FOUND" }, 404);
    if (path === "/api/v1/destinations" && options?.method === "POST") {
      const command = JSON.parse(String(options.body));
      expect(command).toMatchObject({
        jdbcUrl: "jdbc:postgresql://mock-db.invalid:5432/mock_database",
      });
      expect(command).not.toHaveProperty("username");
      expect(command).not.toHaveProperty("password");
      return json(
        {
          destination: {
            id: "pg-owned",
            engine: "postgresql",
            host: "mock-db.invalid",
            port: 5432,
            database: "mock_database",
          },
        },
        201,
      );
    }
    if (path === "/api/v3/definitions") return json({ definitions: [v3DefinitionSummary()] });
    if (path === "/api/v1/destinations") return json({ destinations: [] });
    throw new Error(`Unexpected invented test route ${path}`);
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const hook = renderHook(() => useV3PlanInspection(api, true));
  await waitFor(() => expect(hook.result.current.phase).toBe("absent"));
  act(() =>
    hook.result.current.setConnectionUrl("jdbc:postgresql://mock-db.invalid:5432/mock_database"),
  );
  await act(() => hook.result.current.addDestination());
  expect(hook.result.current.destination).toBe("pg-owned");
  expect(hook.result.current.connectionUrl).toBe("");
  expect(hook.result.current.destinations).toEqual([
    {
      id: "pg-owned",
      engine: "postgresql",
      host: "mock-db.invalid",
      port: 5432,
      database: "mock_database",
    },
  ]);
});

it("keeps the verified inspection view visible while plan refresh is in flight", async () => {
  const { result, transport } = await setup();
  act(() => {
    result.current.select("mock-a");
    result.current.setConsent(true);
  });
  await act(() => result.current.load());
  expect(result.current.current?.text).toBe('<mock value="before"/>\r\n');
  transport.mockClear();
  let release!: (value: Response) => void;
  transport.mockReturnValueOnce(
    new Promise<Response>((resolve) => {
      release = resolve;
    }),
  );
  let pending!: Promise<void>;
  act(() => {
    pending = result.current.refresh();
  });
  await waitFor(() => expect(result.current.refreshing).toBe(true));
  expect(result.current.phase).toBe("loaded");
  expect(result.current.plan?.planId).toBe(id);
  expect(result.current.inventory?.documents).toHaveLength(2);
  expect(result.current.current?.text).toBe('<mock value="before"/>\r\n');

  await act(async () => {
    release(json(summary));
    await pending;
  });
  expect(result.current.refreshing).toBe(false);
  expect(result.current.phase).toBe("loaded");
});

it("keeps the document panes visible while a same-document reload is in flight", async () => {
  const { result, transport } = await setup();
  act(() => {
    result.current.select("mock-a");
    result.current.setConsent(true);
  });
  await act(() => result.current.load());
  expect(result.current.current?.text).toBe('<mock value="before"/>\r\n');
  expect(result.current.target?.text).toBe('<mock value="after"/>\r\n');
  transport.mockClear();
  let release!: (value: Response) => void;
  transport.mockReturnValueOnce(
    new Promise<Response>((resolve) => {
      release = resolve;
    }),
  );
  let pending!: Promise<void>;
  act(() => {
    pending = result.current.load();
  });
  await waitFor(() => expect(result.current.reading).toBe(true));
  expect(result.current.current?.text).toBe('<mock value="before"/>\r\n');
  expect(result.current.target?.text).toBe('<mock value="after"/>\r\n');

  await act(async () => {
    release(json(document()));
    await pending;
  });
  expect(result.current.reading).toBe(false);
  expect(result.current.current?.text).toBe('<mock value="before"/>\r\n');
});

it("keeps document choice but clears loaded panes after a verified refreshed revision changes", async () => {
  const { result, transport } = await setup();
  act(() => {
    result.current.select("mock-a");
    result.current.setConsent(true);
  });
  await act(() => result.current.load());
  transport.mockReset();
  transport
    .mockResolvedValueOnce(json({ ...summary, revision: "3" }))
    .mockResolvedValueOnce(json({ ...inventory, revision: "3" }))
    .mockResolvedValueOnce(json({ ...summary, revision: "3" }));

  await act(() => result.current.refresh());

  expect(result.current.phase).toBe("loaded");
  expect(result.current.plan?.revision).toBe("3");
  expect(result.current.selected).toBe("mock-a");
  expect(result.current.consent).toBe(true);
  expect(result.current.current).toBeNull();
  expect(result.current.target).toBeNull();
});

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
it("does not turn an unavailable refresh into an absent plan or a legacy lookup", async () => {
  const { result, transport } = await setup();
  const visiblePlan = result.current.plan;
  transport.mockClear();
  transport.mockResolvedValueOnce(json({ code: "UNAVAILABLE" }, 503));
  await act(() => result.current.refresh());
  expect(result.current.phase).toBe("error");
  expect(result.current.refreshing).toBe(false);
  expect(result.current.plan).toBe(visiblePlan);
  expect(result.current.error).not.toBe("");
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

it("loads placeholder documents with a concrete binding rail", async () => {
  const { result, transport } = await setup();
  act(() => {
    result.current.select("mock-a");
    result.current.setMode("placeholders");
    result.current.setConsent(true);
  });
  const entity = { kind: "existing" as const, handle: "50000000-0000-0000-0000-000000000003" };
  const token = "[[value:50000000-0000-0000-0000-000000000003:mock-field]]";
  transport
    .mockResolvedValueOnce(json(document("current", "mock-a", "placeholders")))
    .mockResolvedValueOnce(json(document("target", "mock-a", "placeholders")))
    .mockResolvedValueOnce(json(summary))
    .mockResolvedValueOnce(
      json({
        revision: "2",
        total: 1,
        offset: 0,
        nextOffset: null,
        items: [{ entity, typeId: "mock-type", fields: [] }],
      }),
    )
    .mockResolvedValueOnce(
      json({
        revision: "2",
        total: 1,
        offset: 0,
        nextOffset: null,
        items: [{ entity, typeId: "mock-type", fields: [] }],
      }),
    )
    .mockResolvedValueOnce(
      json({
        revision: "2",
        total: 1,
        offset: 0,
        nextOffset: null,
        items: [
          {
            fieldId: "mock-field",
            token,
            current: { state: "value", text: "before" },
            target: { state: "value", text: "after" },
            change: "changed",
            currentLocations: { state: "complete", total: 1 },
            targetLocations: { state: "complete", total: 1 },
          },
        ],
      }),
    )
    .mockResolvedValueOnce(
      json({
        revision: "2",
        total: 1,
        offset: 0,
        nextOffset: null,
        items: [
          {
            documentId: "mock-a",
            sourceDigest: digest,
            projectionId: "mock-projection",
            elementIndex: "0",
            attribute: { namespaceUri: "", localName: "value" },
            span: { start: 13, end: 19 },
            role: "field",
            declarationId: "mock-field",
          },
        ],
      }),
    )
    .mockResolvedValueOnce(
      json({
        revision: "2",
        total: 1,
        offset: 0,
        nextOffset: null,
        items: [
          {
            documentId: "mock-a",
            sourceDigest: "b".repeat(64),
            projectionId: "mock-projection",
            elementIndex: "0",
            attribute: { namespaceUri: "", localName: "value" },
            span: { start: 13, end: 18 },
            role: "field",
            declarationId: "mock-field",
          },
        ],
      }),
    );
  await act(() => result.current.load());
  expect(result.current.current?.mode).toBe("placeholders");
  expect(result.current.target?.mode).toBe("placeholders");
  expect(result.current.bindingRail).toEqual([
    {
      entity,
      typeId: "mock-type",
      fieldId: "mock-field",
      token,
      change: "changed",
      current: "before",
      target: "after",
      currentLocations: 1,
      targetLocations: 1,
      currentTotalLocations: 1,
      targetTotalLocations: 1,
      currentDocumentLocations: [
        {
          documentId: "mock-a",
          sourceDigest: "a".repeat(64),
          projectionId: "mock-projection",
          elementIndex: "0",
          attribute: { namespaceUri: "", localName: "value" },
          span: { start: 13, end: 19 },
          role: "field",
          declarationId: "mock-field",
        },
      ],
      targetDocumentLocations: [
        {
          documentId: "mock-a",
          sourceDigest: "b".repeat(64),
          projectionId: "mock-projection",
          elementIndex: "0",
          attribute: { namespaceUri: "", localName: "value" },
          span: { start: 13, end: 18 },
          role: "field",
          declarationId: "mock-field",
        },
      ],
    },
  ]);
  expect(
    transport.mock.calls
      .filter(([path]) => String(path).endsWith("/views/document"))
      .map(([, options]) => JSON.parse(String(options?.body)).mode),
  ).toEqual(["placeholders", "placeholders"]);
  expect(
    transport.mock.calls.filter(([path]) => String(path).endsWith("/views/binding-locations")),
  ).toHaveLength(2);
});
