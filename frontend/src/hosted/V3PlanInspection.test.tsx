import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import type { useV3PlanInspection } from "./useV3PlanInspection";
import { V3PlanInspection } from "./V3PlanInspection";

it("does not report unobserved physical counts as a measured empty graph", () => {
  const state: ReturnType<typeof useV3PlanInspection> = {
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
  };
  render(<V3PlanInspection state={state} versionSelector={null} openDefinitions={vi.fn()} />);
  expect(
    screen.getByText("Current physical counts unavailable · no observation captured."),
  ).toBeVisible();
  expect(screen.queryByText(/Current physical: 0 documents/)).not.toBeInTheDocument();
});

it("offers all three document modes without loading before disclosure", () => {
  const state: ReturnType<typeof useV3PlanInspection> = {
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
  };
  render(<V3PlanInspection state={state} versionSelector={null} openDefinitions={vi.fn()} />);
  expect(screen.getByRole("button", { name: "Raw" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Placeholders" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Formatted" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Load document comparison" })).toBeDisabled();
});
