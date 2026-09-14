import { render, screen } from "@testing-library/react";
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
  render(<V3PlanInspection state={state} versionSelector={null} openDefinitions={vi.fn()} />);
  expect(
    screen.getByText("Current physical counts unavailable · no observation captured."),
  ).toBeVisible();
  expect(screen.queryByText(/Current physical: 0 documents/)).not.toBeInTheDocument();
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
  render(<V3PlanInspection state={state} versionSelector={null} openDefinitions={vi.fn()} />);
  expect(screen.getByRole("combobox", { name: "Published v3 definition" })).toHaveValue(
    "50000000-0000-0000-0000-000000000002",
  );
  expect(screen.getByRole("combobox", { name: "Binding" })).toHaveValue("mock-pg");
  expect(screen.getByRole("combobox", { name: "Configured destination" })).toHaveValue(
    "mock-postgres",
  );
  expect(screen.getByRole("button", { name: "Create plan" })).toBeEnabled();
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
  render(<V3PlanInspection state={state} versionSelector={null} openDefinitions={vi.fn()} />);
  expect(screen.getByRole("button", { name: "Raw" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Placeholders" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Formatted" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Load document comparison" })).toBeDisabled();
});
