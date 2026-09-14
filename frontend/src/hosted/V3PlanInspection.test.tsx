import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import type { useV3PlanInspection } from "./useV3PlanInspection";
import { V3PlanInspection } from "./V3PlanInspection";

function stateFixture(
  patch: Partial<ReturnType<typeof useV3PlanInspection>>,
): ReturnType<typeof useV3PlanInspection> {
  return {
    phase: "idle",
    plan: null,
    definitions: null,
    definition: null,
    destinations: null,
    binding: "",
    destination: "",
    creating: false,
    pendingCreate: null,
    inventory: null,
    selected: "",
    mode: "raw",
    consent: false,
    reading: false,
    current: null,
    target: null,
    bindingRail: [],
    error: "",
    refresh: vi.fn(),
    chooseDefinition: vi.fn(),
    chooseBinding: vi.fn(),
    chooseDestination: vi.fn(),
    createPlan: vi.fn(),
    retryCreate: vi.fn(),
    select: vi.fn(),
    setMode: vi.fn(),
    setConsent: vi.fn(),
    load: vi.fn(),
    ...patch,
  };
}

it("does not report unobserved physical counts as a measured empty graph", () => {
  const state = stateFixture({
    phase: "loaded",
    plan: {
      planId: "50000000-0000-0000-0000-000000000001",
      revision: "1",
      definition: { objectId: "50000000-0000-0000-0000-000000000002", workspaceRevision: "2" },
      bindingId: "mock",
      destinationId: "mock",
      currentCounts: { documents: 0, entities: 0, relations: 0 },
      targetCounts: { documents: 0, entities: 0, relations: 0 },
      inspectionValid: false,
      targetComplete: false,
      exportAvailable: false,
      blockers: ["MISSING_OBSERVATION"],
      observedDestination: null,
      currentComputedCounts: null,
      targetComputedCounts: null,
    },
    inventory: null,
    selected: "",
    mode: "raw",
    consent: false,
    reading: false,
    current: null,
    target: null,
    bindingRail: [],
    error: "",
    refresh: vi.fn(),
    select: vi.fn(),
    setMode: vi.fn(),
    setConsent: vi.fn(),
    load: vi.fn(),
  });
  render(
    <V3PlanInspection
      api={{} as never}
      state={state}
      versionSelector={null}
      openDefinitions={vi.fn()}
      inspectionUiEnabled
    />,
  );
  expect(
    screen.getByText("Current physical counts unavailable · no observation captured."),
  ).toBeVisible();
  expect(screen.queryByText(/Current physical: 0 documents/)).not.toBeInTheDocument();
});

it("hides later workflow actions until a plan exists", () => {
  const state = stateFixture({
    phase: "absent",
    definitions: [],
    destinations: [],
  });
  render(
    <V3PlanInspection
      api={{} as never}
      state={state}
      versionSelector={null}
      openDefinitions={vi.fn()}
      inspectionUiEnabled
      reuseProfile={vi.fn()}
      editValues={vi.fn()}
      validatePlan={vi.fn()}
      exportPlan={vi.fn()}
      captureProfile={vi.fn()}
    />,
  );
  expect(screen.queryByRole("button", { name: "Reuse profile" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Define values" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Validate plan" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Export package" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Capture profile" })).not.toBeInTheDocument();
});

it("hides target actions until current inspection is valid", () => {
  const state = stateFixture({
    phase: "loaded",
    plan: {
      planId: "50000000-0000-0000-0000-000000000001",
      revision: "1",
      definition: { objectId: "50000000-0000-0000-0000-000000000002", workspaceRevision: "2" },
      bindingId: "mock",
      destinationId: "mock",
      currentCounts: { documents: 0, entities: 0, relations: 0 },
      targetCounts: { documents: 0, entities: 0, relations: 0 },
      inspectionValid: false,
      targetComplete: false,
      exportAvailable: false,
      blockers: ["INSPECTION_REQUIRED"],
      observedDestination: null,
      currentComputedCounts: null,
      targetComputedCounts: null,
    },
  });
  render(
    <V3PlanInspection
      api={{ post: vi.fn() } as never}
      state={state}
      versionSelector={null}
      openDefinitions={vi.fn()}
      inspectionUiEnabled
      reuseProfile={vi.fn()}
      editValues={vi.fn()}
      validatePlan={vi.fn()}
      exportPlan={vi.fn()}
      captureProfile={vi.fn()}
    />,
  );
  expect(screen.getByRole("heading", { name: "Inspect current PostgreSQL" })).toBeVisible();
  expect(screen.queryByRole("button", { name: "Reuse profile" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Define values" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Validate plan" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Export package" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Capture profile" })).not.toBeInTheDocument();
});

it("renders v3 published definition plan creation controls", () => {
  const createPlan = vi.fn();
  const state = stateFixture({
    phase: "absent",
    definitions: [
      {
        objectId: "50000000-0000-0000-0000-000000000002",
        workspaceRevision: "2",
        nativeId: "mock-tiles",
        nativeRevision: "1",
        state: "published",
        compilationKind: "historical-ready",
        logicalDigest: "a".repeat(64),
      },
    ],
    definition: {
      objectId: "50000000-0000-0000-0000-000000000002",
      workspaceRevision: "2",
      sourceDigest: "c".repeat(64),
      source: "{}",
      format: "JSON",
      schemaVersion: "3",
      compilerVersion: "native-compiler-v3",
      state: "published",
      publication: { digest: "d".repeat(64), sourceRevision: "1", exportPolicies: [] },
      projection: {
        kind: "historical-ready",
        model: {
          schemaVersion: "3",
          id: "mock-tiles",
          revision: "1",
          logical: { entityTypes: [], relations: [], rules: [], operationCapabilities: [] },
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
              documents: [],
            },
          ],
        },
        logicalDigest: "a".repeat(64),
        bindingDigests: { "mock-pg": "b".repeat(64) },
        mechanisms: {
          "xml-path-v1": "1",
          "xml-span-v1": "1",
          "generic-graph-v1": "1",
          "native-compiler-v3": "1",
          "derived-graph-v1": "1",
        },
        diagnostics: [],
      },
    } as unknown as NonNullable<ReturnType<typeof useV3PlanInspection>["definition"]>,
    destinations: [
      {
        id: "mock-postgres",
        engine: "postgresql",
        host: "127.0.0.1",
        port: 5432,
        database: "mockdb",
      },
    ],
    binding: "mock-pg",
    destination: "mock-postgres",
    createPlan,
  });
  render(
    <V3PlanInspection
      api={{} as never}
      state={state}
      versionSelector={null}
      openDefinitions={vi.fn()}
      inspectionUiEnabled
    />,
  );
  expect(screen.getByRole("combobox", { name: "Published v3 definition" })).toHaveValue(
    "50000000-0000-0000-0000-000000000002",
  );
  expect(screen.getByRole("combobox", { name: "Binding" })).toHaveValue("mock-pg");
  expect(screen.getByRole("combobox", { name: "PostgreSQL connection target" })).toHaveValue(
    "mock-postgres",
  );
  expect(screen.getByRole("button", { name: "Continue to current inspection" })).toBeEnabled();
});

it("offers all three document modes without loading before disclosure", () => {
  const state = stateFixture({
    phase: "loaded",
    plan: {
      planId: "50000000-0000-0000-0000-000000000001",
      revision: "2",
      definition: { objectId: "50000000-0000-0000-0000-000000000002", workspaceRevision: "2" },
      bindingId: "mock",
      destinationId: "mock",
      currentCounts: { documents: 1, entities: 1, relations: 0 },
      targetCounts: { documents: 1, entities: 1, relations: 0 },
      inspectionValid: true,
      targetComplete: true,
      exportAvailable: false,
      blockers: [],
      observedDestination: {
        engine: "postgresql",
        identity: { systemIdentifier: "7", databaseOid: "8", databaseName: "mock" },
        observationFingerprint: "a".repeat(64),
        evidenceValid: true,
      },
      currentComputedCounts: { nodes: 0, memberships: 0, cooccurrences: 0 },
      targetComputedCounts: { nodes: 0, memberships: 0, cooccurrences: 0 },
    },
    inventory: {
      revision: "2",
      documents: [
        {
          documentId: "mock-a",
          currentDigest: "a".repeat(64),
          targetDigest: "b".repeat(64),
          changed: true,
        },
      ],
    },
    selected: "mock-a",
    mode: "raw",
    consent: false,
    reading: false,
    current: null,
    target: null,
    bindingRail: [],
    error: "",
    refresh: vi.fn(),
    select: vi.fn(),
    setMode: vi.fn(),
    setConsent: vi.fn(),
    load: vi.fn(),
  });
  render(
    <V3PlanInspection
      api={{} as never}
      state={state}
      versionSelector={null}
      openDefinitions={vi.fn()}
      inspectionUiEnabled
    />,
  );
  expect(screen.getByRole("button", { name: "Raw" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Placeholders" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Formatted" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Load document comparison" })).toBeDisabled();
});

it("searches the complete document inventory without concealing global change counts or loading content", async () => {
  const state = stateFixture({
    inventory: {
      revision: "2",
      documents: Array.from({ length: 120 }, (_, index) => ({
        documentId: `mock-document-${index.toString().padStart(3, "0")}`,
        currentDigest: "a".repeat(64),
        targetDigest: "b".repeat(64),
        changed: index % 2 === 0,
      })),
    },
    selected: "mock-document-000",
  });
  render(
    <V3PlanInspection
      api={{} as never}
      state={state}
      versionSelector={null}
      openDefinitions={vi.fn()}
      inspectionUiEnabled
    />,
  );
  await userEvent.type(screen.getByRole("searchbox", { name: "Find a document" }), "119");
  expect(screen.getByText("1 of 120 documents shown")).toBeVisible();
  expect(screen.getByText("120 documents · 60 changed · 0 unknown")).toBeVisible();
  expect(screen.getByRole("button", { name: "mock-document-119 · Unchanged" })).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "mock-document-000 · Changed" }),
  ).not.toBeInTheDocument();
  expect(state.load).not.toHaveBeenCalled();
  expect(state.select).not.toHaveBeenCalled();
});

it("navigates changed documents in inventory order without treating unknown documents as unchanged", async () => {
  const state = stateFixture({
    inventory: {
      revision: "2",
      documents: [
        {
          documentId: "mock-a",
          currentDigest: "a".repeat(64),
          targetDigest: "b".repeat(64),
          changed: true,
        },
        { documentId: "mock-b", currentDigest: "a".repeat(64), targetDigest: null, changed: null },
        {
          documentId: "mock-c",
          currentDigest: "a".repeat(64),
          targetDigest: "b".repeat(64),
          changed: true,
        },
      ],
    },
    selected: "mock-a",
  });
  render(
    <V3PlanInspection
      api={{} as never}
      state={state}
      versionSelector={null}
      openDefinitions={vi.fn()}
      inspectionUiEnabled
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "Next changed document" }));
  expect(state.select).toHaveBeenCalledWith("mock-c");
  expect(state.load).not.toHaveBeenCalled();
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Document status" }),
    "unknown",
  );
  expect(screen.getByRole("button", { name: "mock-b · Unknown" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Next changed document" })).toBeDisabled();
});

it("keeps concrete current and target values inspectable when choosing a placeholder mapping", async () => {
  const entity = { kind: "existing" as const, handle: "50000000-0000-0000-0000-000000000003" };
  const state = stateFixture({
    mode: "placeholders",
    selected: "mock-a",
    current: {
      revision: "2",
      side: "current",
      documentId: "mock-a",
      mode: "placeholders",
      text: "<mock/>",
      exact: false,
      redacted: false,
      unmappedConcreteMayRemain: false,
      omissions: [],
    },
    bindingRail: [
      {
        entity,
        typeId: "mock-type",
        fieldId: "mock-tone",
        token: "[[value:mock-tone]]",
        change: "changed",
        current: "before-tone",
        target: "after-tone",
        currentLocations: 2,
        targetLocations: 2,
      },
      {
        entity,
        typeId: "mock-type",
        fieldId: "mock-finish",
        token: "[[value:mock-finish]]",
        change: "changed",
        current: "before-finish",
        target: "after-finish",
        currentLocations: 1,
        targetLocations: 3,
      },
    ],
  });
  render(
    <V3PlanInspection
      api={{} as never}
      state={state}
      versionSelector={null}
      openDefinitions={vi.fn()}
      inspectionUiEnabled
    />,
  );
  expect(screen.getByText("before-tone")).toBeVisible();
  expect(screen.getByText("after-tone")).toBeVisible();
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Selected mapping" }),
    `${JSON.stringify(entity)}:mock-finish`,
  );
  expect(screen.getByText("before-finish")).toBeVisible();
  expect(screen.getByText("after-finish")).toBeVisible();
  expect(screen.getByText("Current 1 · Target 3")).toBeVisible();
  expect(screen.queryByText("before-tone")).not.toBeInTheDocument();
  expect(state.load).not.toHaveBeenCalled();
});
