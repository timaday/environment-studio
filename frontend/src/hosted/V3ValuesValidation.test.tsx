import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary } from "../api/hostedV3Types";
import { V3ValuesValidation } from "./V3ValuesValidation";

const id = "81000000-0000-0000-0000-000000000001";
const entity = { kind: "existing" as const, handle: "81000000-0000-0000-0000-000000000002" };
const digest = "a".repeat(64);
const checks = [
  "SCOPE",
  "DEFINITION",
  "MAPPING",
  "VALUES",
  "SEMANTICS",
  "XML_FIDELITY",
  "DESTINATION",
  "CLIENT_CAPABILITY",
  "CONTENT_POLICY",
  "REVIEW",
];
const plan: PlanSummary = {
  planId: id,
  revision: "3",
  definition: { objectId: "81000000-0000-0000-0000-000000000003", workspaceRevision: "2" },
  bindingId: "definition-binding",
  destinationId: "postgres-target",
  currentCounts: { documents: 1, entities: 1, relations: 0 },
  targetCounts: { documents: 1, entities: 1, relations: 0 },
  inspectionValid: true,
  targetComplete: true,
  exportAvailable: false,
  blockers: [],
  observedDestination: {
    engine: "postgresql",
    identity: { systemIdentifier: "7", databaseOid: "8", databaseName: "invented" },
    observationFingerprint: digest,
    evidenceValid: true,
  },
  currentComputedCounts: null,
  targetComputedCounts: null,
};
const draft = {
  revision: "3",
  total: 1,
  offset: 0,
  nextOffset: null,
  items: [
    {
      entity,
      disposition: "retain",
      fields: [
        { fieldId: "id", kind: "keep-observed", masked: false, value: null },
        { fieldId: "tone", kind: "unresolved", masked: false, value: null },
        { fieldId: "secret", kind: "keep-observed", masked: true, value: null },
      ],
      references: [],
      placements: [],
    },
  ],
};
const bindings = {
  revision: "3",
  total: 3,
  offset: 0,
  nextOffset: null,
  items: [
    {
      fieldId: "id",
      token: `[[value:${id}:id]]`,
      current: { state: "value", text: "one" },
      target: { state: "value", text: "one" },
      change: "unchanged",
      currentLocations: { state: "complete", total: 1 },
      targetLocations: { state: "complete", total: 1 },
    },
    {
      fieldId: "tone",
      token: `[[value:${id}:tone]]`,
      current: { state: "value", text: "alpha" },
      target: { state: "unresolved" },
      change: "unresolved",
      currentLocations: { state: "complete", total: 1 },
      targetLocations: { state: "complete", total: 1 },
    },
    {
      fieldId: "secret",
      token: `[[value:${id}:secret]]`,
      current: { state: "masked" },
      target: { state: "masked" },
      change: "unresolved",
      currentLocations: { state: "complete", total: 1 },
      targetLocations: { state: "complete", total: 1 },
    },
  ],
};
const summary = {
  revision: "3",
  inputFingerprint: digest,
  checks: checks.map((check) => ({
    check,
    outcome: ["CLIENT_CAPABILITY", "CONTENT_POLICY", "REVIEW"].includes(check) ? "UNKNOWN" : "PASS",
    inputFingerprint: digest,
  })),
  applicationRules: [],
  exportAvailable: false,
  targetComplete: true,
  computedRuleCount: 2,
};
const rulePage = {
  revision: "3",
  inputFingerprint: digest,
  targetComplete: true,
  total: 2,
  offset: 0,
  nextOffset: null,
  items: [
    {
      kind: "COOCCURRENCE",
      declaration: "neutral-rule",
      source: { computedType: "neutral-group", derivation: "membership", value: "alpha" },
      actual: "1",
      minimum: "0",
      maximum: "10",
      outcome: "PASS",
    },
    {
      kind: "COOCCURRENCE",
      declaration: "neutral-rule",
      source: { computedType: "neutral-group", derivation: "membership", value: "beta" },
      actual: "1",
      minimum: "0",
      maximum: "10",
      outcome: "PASS",
    },
  ],
};
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];

afterEach(() => {
  cleanup();
  for (const api of owners) api.clear();
  owners.length = 0;
});

async function setup(view: "values" | "validation") {
  let submitted = false;
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path, options) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (path === `/api/v3/plans/${id}`) return json({ ...plan, revision: submitted ? "4" : "3" });
    if (String(path).endsWith("/views/draft")) return json(draft);
    if (String(path).endsWith("/views/bindings")) return json(bindings);
    if (String(path).endsWith("/commands")) {
      submitted = true;
      return json({ planId: id, revision: "4" });
    }
    if (String(path).endsWith("/validations")) {
      const body = JSON.parse(String(options?.body));
      return json(body.section ? rulePage : summary);
    }
    throw new Error(`Unexpected invented values route ${path}`);
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const back = vi.fn();
  const refreshPlan = vi.fn<() => Promise<void>>().mockResolvedValue();
  const user = userEvent.setup();
  render(
    <V3ValuesValidation
      api={api}
      plan={plan}
      active={true}
      view={view}
      back={back}
      refreshPlan={refreshPlan}
    />,
  );
  return { user, transport, back, refreshPlan };
}

it("shows actual draft inventory, read-only comparison and submits one explicit target value", async () => {
  const { user, transport, refreshPlan } = await setup("values");
  await user.click(screen.getByRole("button", { name: "Load target values" }));
  await screen.findByRole("heading", { name: "Target values" });
  expect(screen.getByLabelText("Item selector")).toHaveDisplayValue(/81000000/);
  await user.click(await screen.findByRole("button", { name: "Load comparison" }));
  const row = await screen.findByRole("group", { name: "Field tone" });
  expect(within(row).getByText("Current value")).toBeVisible();
  expect(within(row).getByText("alpha")).toBeVisible();
  expect(within(row).getByText("Target unresolved")).toBeVisible();
  expect(screen.getByText("Comparison unavailable for masked values.")).toBeVisible();
  await user.selectOptions(screen.getByLabelText("Field to update"), "tone");
  await user.type(screen.getByLabelText("Target value text"), "beta");
  await user.click(screen.getByRole("button", { name: "Submit target value" }));
  const sent = transport.mock.calls.filter(([path]) => String(path).endsWith("/commands"));
  expect(JSON.parse(String(sent[0][1]?.body))).toMatchObject({
    kind: "bind-field",
    expectedRevision: "3",
    entity,
    fieldId: "tone",
    state: { kind: "entered", text: "beta" },
  });
  expect(await screen.findByText("Target value saved for this plan revision.")).toBeVisible();
  expect(refreshPlan).toHaveBeenCalled();
});

it("runs backend validation and pages compatible computed rules without export authority", async () => {
  const { user } = await setup("validation");
  await user.click(screen.getByRole("button", { name: "Run validation" }));
  await screen.findByText("Export remains unavailable.");
  expect(screen.getByText("CLIENT CAPABILITY")).toBeVisible();
  expect(screen.getAllByText("UNKNOWN")).toHaveLength(3);
  await user.click(screen.getByRole("button", { name: "Load computed rules" }));
  expect(await screen.findAllByText("neutral-rule")).toHaveLength(2);
  expect(screen.getByText("alpha")).toBeVisible();
  expect(screen.getByText("beta")).toBeVisible();
});
