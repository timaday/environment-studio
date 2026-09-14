import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary } from "../api/hostedV3Types";
import { V3TargetStructure } from "./V3TargetStructure";

const digest = "d".repeat(64);
const plan: PlanSummary = {
  planId: "41000000-0000-4000-8000-000000000001",
  revision: "2",
  definition: { objectId: "42000000-0000-4000-8000-000000000001", workspaceRevision: "2" },
  bindingId: "mock-pg",
  destinationId: "postgresql-pilot",
  currentCounts: { documents: 2, entities: 3, relations: 2 },
  targetCounts: { documents: 0, entities: 0, relations: 0 },
  inspectionValid: true,
  exportAvailable: false,
  targetComplete: false,
  blockers: [],
  observedDestination: {
    engine: "postgresql",
    identity: { systemIdentifier: "1", databaseOid: "2", databaseName: "appdb" },
    observationFingerprint: digest,
    evidenceValid: true,
  },
  currentComputedCounts: null,
  targetComputedCounts: null,
};
const model = {
  schemaVersion: "3",
  id: "mock-tiles",
  revision: "1",
  logical: {
    entityTypes: [
      {
        id: "glyph",
        label: "Glyph",
        fields: [
          {
            id: "tag",
            valueType: "text",
            required: true,
            classification: "structural",
            sensitivity: "public",
            readable: true,
            editable: false,
          },
          {
            id: "tone",
            valueType: "text",
            required: true,
            classification: "environment",
            sensitivity: "public",
            readable: true,
            editable: true,
          },
        ],
        identity: { field: "tag", scope: "type", normalization: "exact" },
      },
      {
        id: "palette",
        label: "Palette",
        fields: [
          {
            id: "tag",
            valueType: "text",
            required: true,
            classification: "structural",
            sensitivity: "public",
            readable: true,
            editable: false,
          },
        ],
        identity: { field: "tag", scope: "type", normalization: "exact" },
      },
    ],
    relations: [
      {
        id: "uses",
        fromType: "glyph",
        toType: "palette",
        kind: "reference",
        minimum: "1",
        maximum: "1",
        includeTargetOnReuse: true,
      },
    ],
    rules: [],
    operationCapabilities: ["retain-entity", "create-entity", "remove-entity", "bind-field"],
    computedTypes: [],
    derivations: [],
    cooccurrences: [],
    computedRules: [],
  },
  bindings: [
    {
      id: "mock-pg",
      engine: "postgresql",
      storage: "text",
      schema: "mock_pg",
      table: "mock_tiles",
      keyColumn: "mock_key",
      xmlColumn: "mock_xml",
      keyType: "int64",
      documents: [
        {
          id: "glyph-sheet",
          key: "1",
          entities: [
            {
              id: "glyphs",
              type: "glyph",
              path: [{ namespaceUri: "urn:mock", localName: "items" }],
              fields: [],
              references: [],
            },
          ],
        },
      ],
    },
  ],
};
const definition = {
  objectId: plan.definition.objectId,
  workspaceRevision: "2",
  sourceDigest: digest,
  format: "JSON",
  source: "{}",
  schemaVersion: "3",
  compilerVersion: "native-compiler-v3",
  state: "published",
  projection: {
    kind: "historical-ready",
    model,
    logicalDigest: digest,
    bindingDigests: { "mock-pg": digest },
    mechanisms: {
      "xml-path-v1": "1",
      "xml-span-v1": "1",
      "generic-graph-v1": "1",
      "native-compiler-v3": "1",
      "derived-graph-v1": "1",
    },
    diagnostics: [],
  },
  publication: {
    digest,
    sourceRevision: "1",
    exportPolicies: [
      { bindingId: "mock-pg", documentId: "glyph-sheet", content: "protected-self-contained" },
    ],
  },
};
const alpha = {
  entity: { kind: "existing", handle: "43000000-0000-4000-8000-000000000001" },
  typeId: "glyph",
  fields: [
    { fieldId: "tag", present: true, masked: false, value: "alpha" },
    { fieldId: "tone", present: true, masked: false, value: "blue" },
  ],
};
const palette = {
  entity: { kind: "existing", handle: "43000000-0000-4000-8000-000000000002" },
  typeId: "palette",
  fields: [{ fieldId: "tag", present: true, masked: false, value: "shared" }],
};
function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { "cache-control": "no-store" } });
}
function requestPath(input: RequestInfo | URL): string {
  const raw = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
  return raw.startsWith("http://") || raw.startsWith("https://") ? new URL(raw).pathname : raw;
}
afterEach(cleanup);

it("retains inspected structure with explicit field/reference states and reads back the draft", async () => {
  const bodies: unknown[] = [];
  const refreshPlan = vi.fn();
  const transport = vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
    const pathname = requestPath(input);
    if (pathname === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (pathname === `/api/v3/definitions/${definition.objectId}/revisions/2`)
      return json(definition);
    if (pathname.endsWith("/views/entities"))
      return json({
        revision: "2",
        total: 2,
        offset: 0,
        nextOffset: null,
        items: [alpha, palette],
      });
    if (pathname.endsWith("/views/draft"))
      return json({
        revision: "3",
        total: 1,
        offset: 0,
        nextOffset: null,
        items: [
          {
            entity: alpha.entity,
            disposition: "retain",
            fields: [
              { fieldId: "tag", kind: "keep-observed", masked: false, value: null },
              { fieldId: "tone", kind: "unresolved", masked: false, value: null },
            ],
            references: [{ referenceId: "uses", kind: "keep-observed", target: null }],
            placements: [],
          },
        ],
      });
    if (pathname.endsWith("/commands")) {
      bodies.push(JSON.parse(String(init?.body)));
      return json({ planId: plan.planId, revision: "3" });
    }
    return json({ code: "NOT_FOUND" }, 404);
  });
  const api = new HostedApi(transport);
  await api.session();
  render(
    <V3TargetStructure api={api} plan={plan} active back={vi.fn()} refreshPlan={refreshPlan} />,
  );
  await screen.findByRole("button", { name: /Glyph · alpha/ });
  await userEvent.selectOptions(screen.getByLabelText("Target state for tone"), "unresolved");
  await userEvent.click(screen.getByRole("button", { name: "Save retained structure" }));
  await screen.findByText(/Structure saved for revision 3/);
  expect(bodies[0]).toMatchObject({
    kind: "upsert-entity",
    expectedRevision: "2",
    decision: {
      kind: "retain",
      entity: alpha.entity,
      fields: { tag: { kind: "keep-observed" }, tone: { kind: "unresolved" } },
      references: { uses: { kind: "keep-observed" } },
    },
    placements: [],
  });
  expect(
    within(screen.getByRole("region", { name: "Saved target draft" })).getByText("retain"),
  ).toBeVisible();
});

it("loads placement choices before creating a fresh target item", async () => {
  const bodies: unknown[] = [];
  const transport = vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
    const pathname = requestPath(input);
    if (pathname === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (pathname === `/api/v3/definitions/${definition.objectId}/revisions/2`)
      return json(definition);
    if (pathname.endsWith("/views/entities"))
      return json({ revision: "2", total: 1, offset: 0, nextOffset: null, items: [palette] });
    if (pathname.endsWith("/views/draft"))
      return json({ revision: "3", total: 0, offset: 0, nextOffset: null, items: [] });
    if (pathname.endsWith("/views/placements"))
      return json({
        revision: "2",
        total: 1,
        offset: 0,
        nextOffset: null,
        items: [{ documentId: "glyph-sheet", sourceDigest: digest, elementIndex: "0" }],
      });
    if (pathname.endsWith("/commands")) {
      bodies.push(JSON.parse(String(init?.body)));
      return json({ planId: plan.planId, revision: "3" });
    }
    return json({ code: "NOT_FOUND" }, 404);
  });
  const api = new HostedApi(transport);
  await api.session();
  render(<V3TargetStructure api={api} plan={plan} active back={vi.fn()} refreshPlan={vi.fn()} />);
  await screen.findByText("Create new target item");
  await userEvent.click(screen.getByRole("tab", { name: "Create new target item" }));
  await userEvent.clear(screen.getByLabelText("Portable slot ID"));
  await userEvent.type(screen.getByLabelText("Portable slot ID"), "new-glyph");
  await userEvent.selectOptions(screen.getByLabelText("Target type"), "glyph");
  await userEvent.selectOptions(screen.getByLabelText("Target state for tag"), "entered");
  await userEvent.type(screen.getByLabelText("Entered value for tag"), "gamma");
  await userEvent.selectOptions(screen.getByLabelText("Target state for tone"), "entered");
  await userEvent.type(screen.getByLabelText("Entered value for tone"), "violet");
  await userEvent.click(screen.getByRole("button", { name: "Load placement choices" }));
  await screen.findByText(/glyph-sheet · parent element 0/);
  await userEvent.click(screen.getByRole("button", { name: "Save new item" }));
  expect(bodies[0]).toMatchObject({
    kind: "upsert-entity",
    decision: {
      kind: "create",
      entity: { kind: "fresh", slotId: "new-glyph", typeId: "glyph" },
      fields: {
        tag: { kind: "entered", text: "gamma" },
        tone: { kind: "entered", text: "violet" },
      },
      references: { uses: { kind: "unresolved" } },
    },
    placements: [
      {
        entity: { kind: "fresh", slotId: "new-glyph", typeId: "glyph" },
        documentId: "glyph-sheet",
        projectionId: "glyphs",
        parent: {
          kind: "existing",
          documentId: "glyph-sheet",
          sourceDigest: digest,
          elementIndex: "0",
        },
      },
    ],
  });
});

it("shows a side-by-side relationship tree derived from the published definition", async () => {
  const transport = vi.fn<typeof fetch>().mockImplementation(async (input) => {
    const pathname = requestPath(input);
    if (pathname === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (pathname === `/api/v3/definitions/${definition.objectId}/revisions/2`)
      return json(definition);
    if (pathname.endsWith("/views/entities"))
      return json({
        revision: "2",
        total: 2,
        offset: 0,
        nextOffset: null,
        items: [alpha, palette],
      });
    if (pathname.endsWith("/views/draft"))
      return json({
        revision: "2",
        total: 1,
        offset: 0,
        nextOffset: null,
        items: [
          {
            entity: { kind: "fresh", slotId: "new-glyph", typeId: "glyph" },
            disposition: "create",
            fields: [],
            references: [{ referenceId: "uses", kind: "unresolved", target: null }],
            placements: [],
          },
        ],
      });
    return json({ code: "NOT_FOUND" }, 404);
  });
  const api = new HostedApi(transport);
  await api.session();
  render(<V3TargetStructure api={api} plan={plan} active back={vi.fn()} refreshPlan={vi.fn()} />);

  const map = await screen.findByRole("region", { name: "Definition relationship map" });
  expect(within(map).getByRole("heading", { name: "Relationship map" })).toBeVisible();
  expect(
    within(map).getByRole("region", { name: "Current returned structure tree" }),
  ).toBeVisible();
  expect(within(map).getByRole("region", { name: "Target draft structure tree" })).toBeVisible();
  expect(within(map).getByText("Glyph · alpha")).toBeVisible();
  expect(within(map).getByText("Glyph · new-glyph")).toBeVisible();
  expect(within(map).getByText("uses")).toBeVisible();
  expect(within(map).getAllByText("Glyph").length).toBeGreaterThan(0);
  expect(within(map).getAllByText("Palette").length).toBeGreaterThan(0);
  expect(within(map).getByText(/reference · 1\.\.1 · required for reuse preview/)).toBeVisible();
});
