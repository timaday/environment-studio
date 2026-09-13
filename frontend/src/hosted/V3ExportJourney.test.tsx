import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary } from "../api/hostedV3Types";
import { V3ExportJourney } from "./V3ExportJourney";

const planId = "13000000-0000-0000-0000-000000000001";
const digest = "c".repeat(64);
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
  planId,
  revision: "4",
  definition: { objectId: "13000000-0000-0000-0000-000000000002", workspaceRevision: "2" },
  bindingId: "mock-pg",
  destinationId: "mock-destination",
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
const session = {
  authenticated: true,
  csrfHeaderName: "X-CSRF-TOKEN",
  csrfToken: "csrf",
  idleTimeoutSeconds: 1800,
  absoluteExpiresAt: "2099-01-01T00:00:00Z",
};
const summary = (outcome: "PASS" | "UNKNOWN") => ({
  revision: "4",
  inputFingerprint: digest,
  targetComplete: true,
  exportAvailable: false,
  computedRuleCount: 0,
  applicationRules: [],
  checks: checks.map((check) => ({ check, outcome, inputFingerprint: digest })),
});
const owners: HostedApi[] = [];
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });

afterEach(() => {
  cleanup();
  for (const api of owners) api.clear();
  owners.length = 0;
  vi.unstubAllGlobals();
});

async function setup(outcome: "PASS" | "UNKNOWN") {
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path) => {
    if (path === "/api/v1/session") return json(session);
    if (path === `/api/v3/plans/${planId}`) return json(plan);
    if (String(path).endsWith("/validations")) return json(summary(outcome));
    if (String(path).endsWith("/package-candidates/guarded"))
      return new Response(new Uint8Array([80, 75, 3, 4]), {
        status: 200,
        headers: {
          "Cache-Control": "no-store",
          "Content-Type": "application/zip",
          "Content-Disposition": 'attachment; filename="environment-studio-guarded-package.zip"',
          "X-Environment-Studio-Qualified": "false",
        },
      });
    throw new Error(`Unexpected export route ${path}`);
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  vi.stubGlobal("URL", {
    ...URL,
    createObjectURL: vi.fn(() => "blob:package"),
    revokeObjectURL: vi.fn(),
  });
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  render(
    <V3ExportJourney
      api={api}
      plan={plan}
      active={true}
      back={vi.fn()}
      reviewDocuments={vi.fn()}
    />,
  );
  return { user: userEvent.setup(), transport };
}

it("keeps package download unavailable until backend readiness confirms a complete target", async () => {
  const { user, transport } = await setup("UNKNOWN");
  expect(screen.queryByText(/Run SQL/i)).toBeNull();
  expect(screen.getByRole("button", { name: "Download package candidate" })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "Check readiness" }));
  expect(
    await screen.findByText(
      "10 checks block release qualification; candidate remains unqualified.",
    ),
  ).toBeVisible();
  expect(screen.getByText("10 blockers")).toBeVisible();
  expect(screen.getByRole("button", { name: "Download package candidate" })).toBeEnabled();
  await user.click(screen.getByRole("button", { name: "Download package candidate" }));
  expect(await screen.findByText(/Unqualified candidate/i)).toBeVisible();
  expect(
    transport.mock.calls.some(([path]) => String(path).endsWith("/package-candidates/guarded")),
  ).toBe(true);
});

it("downloads only after an actual unqualified package response", async () => {
  const { user, transport } = await setup("PASS");
  await user.click(screen.getByRole("button", { name: "Check readiness" }));
  await screen.findByText("All checks passed for this revision.");
  expect(screen.getByText("0 blockers")).toBeVisible();
  await user.click(screen.getByRole("button", { name: "Download package candidate" }));
  expect(await screen.findByText(/Unqualified candidate/i)).toBeVisible();
  const request = transport.mock.calls.find(([path]) =>
    String(path).endsWith("/package-candidates/guarded"),
  );
  expect(request).toBeDefined();
  expect(JSON.parse(String(request?.[1]?.body))).toEqual({
    revision: "4",
    inputFingerprint: digest,
  });
  expect(URL.createObjectURL).toHaveBeenCalled();
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:package");
});
