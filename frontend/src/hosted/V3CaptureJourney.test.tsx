import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary } from "../api/hostedV3Types";
import { HostedWorkspace } from "./HostedWorkspace";
import { VersionedPlans } from "./VersionedPlans";

// Independently invented wire fixture, no production fallback or real model.
const id = "70000000-0000-0000-0000-000000000001";
const digest = "a".repeat(64);
const plan: PlanSummary = {
  planId: id,
  revision: "2",
  definition: { objectId: "70000000-0000-0000-0000-000000000002", workspaceRevision: "2" },
  bindingId: "mock-binding",
  destinationId: "mock-destination",
  currentCounts: { documents: 0, entities: 1, relations: 0 },
  targetCounts: { documents: 0, entities: 0, relations: 0 },
  inspectionValid: true,
  targetComplete: false,
  exportAvailable: false,
  blockers: [],
  observedDestination: {
    engine: "postgresql",
    identity: { systemIdentifier: "1", databaseOid: "2", databaseName: "mock" },
    observationFingerprint: digest,
    evidenceValid: true,
  },
  currentComputedCounts: null,
  targetComputedCounts: null,
};
const entity = { kind: "existing", handle: "71000000-0000-0000-0000-000000000001" };
const model = {
  schemaVersion: "3",
  id: "portable",
  revision: "1",
  logicalDefinitionDigest: digest,
  entities: [{ id: "first", label: "First item", type: "item", requiredInputs: ["id"] }],
  relations: [],
};
const source = JSON.stringify({ ...model, revision: 1 });
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  cleanup();
  for (const api of owners) api.clear();
  owners.length = 0;
  vi.unstubAllGlobals();
});
async function setup(observed = true, count = 1, shell = false) {
  const current = {
    ...plan,
    currentCounts: { ...plan.currentCounts, entities: count },
    inspectionValid: observed,
    observedDestination: observed ? plan.observedDestination : null,
  };
  const inventory = Array.from({ length: count }, (_, index) => ({
    entity: {
      kind: "existing",
      handle: `71000000-0000-0000-0000-${String(index + 1).padStart(12, "0")}`,
    },
    typeId: "item",
    fields: [
      { fieldId: "id", present: true, masked: false, value: "Invented donor value must stay out" },
    ],
  }));
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path, options) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: new Date(Date.now() + 28_800_000).toISOString(),
      });
    if (path === "/api/v1/plans/current") return json({ code: "NOT_FOUND" }, 404);
    if (path === "/api/v2/definitions") return json({ definitions: [], canPublish: false });
    if (path === "/api/v1/destinations") return json({ destinations: [] });
    if (path === "/api/v3/plans/current" || path === `/api/v3/plans/${id}`) return json(current);
    if (path === `/api/v3/plans/${id}/views/documents`)
      return json({ revision: "2", documents: [] });
    if (path === `/api/v3/plans/${id}/views/entities`)
      return json({ revision: "2", offset: 0, total: count, nextOffset: null, items: inventory });
    if (path === `/api/v3/plans/${id}/profile-captures`)
      return json({ revision: "2", definition: plan.definition, format: "json", source });
    if (String(path).startsWith("/api/v3/profiles/") && options?.method === "PUT")
      return json({
        objectId: String(path).split("/").at(-1),
        workspaceRevision: "1",
        sourceDigest: digest,
        format: "JSON",
        source,
        schemaVersion: "3",
        compilerVersion: "profile-compiler-v3",
        definition: plan.definition,
        state: "draft",
        projection: { kind: "structurally-valid", model, contentDigest: digest, diagnostics: [] },
      });
    throw new Error("Unexpected invented capture journey route");
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  if (shell) {
    vi.stubGlobal("fetch", transport);
    render(
      <HostedWorkspace
        capabilities={{
          mode: "hosted",
          definitionWorkspaceEnabled: true,
          inspectionEnabled: false,
          inspectionUiEnabled: false,
          inspectionApiConfigured: false,
          exportEnabled: false,
          blockers: [],
        }}
      />,
    );
    await screen.findByRole("heading", { name: "Plans" });
  } else {
    render(
      <VersionedPlans
        api={api}
        inspectionUiEnabled={false}
        active
        definitionVersion={0}
        openDefinitions={vi.fn()}
        versionChanged={vi.fn()}
      />,
    );
  }
  const user = userEvent.setup();
  await user.selectOptions(screen.getByRole("combobox", { name: "Model version" }), "3");
  await screen.findByText(new RegExp(`Plan ${id}`));
  return { user, transport };
}
it("requires explicit complete mappings, captures for review, and saves only on a separate action", async () => {
  const { user, transport } = await setup();
  await user.click(screen.getByRole("button", { name: "Capture profile" }));
  await user.click(screen.getByRole("button", { name: "Load inspected structure" }));
  await screen.findByLabelText("Reusable identifier 1");
  expect(screen.getByRole("button", { name: "Capture for review" })).toBeDisabled();
  expect(screen.queryByText("Invented donor value must stay out")).not.toBeInTheDocument();
  await user.type(screen.getByLabelText("Profile identifier"), "portable");
  await user.type(screen.getByLabelText("Profile revision"), "1");
  await user.type(screen.getByLabelText("Reusable identifier 1"), "first");
  await user.type(screen.getByLabelText("Label 1"), "First item");
  await user.click(screen.getByRole("button", { name: "Capture for review" }));
  await screen.findByText("Captured · Not saved");
  expect(transport.mock.calls.filter(([, options]) => options?.method === "PUT")).toHaveLength(0);
  const capture = transport.mock.calls.find(([path]) => String(path).endsWith("/profile-captures"));
  expect(JSON.parse(String(capture?.[1]?.body))).toMatchObject({
    revision: "2",
    profileId: "portable",
    profileRevision: "1",
    mappings: [{ entity, slotId: "first", label: "First item" }],
  });
  await user.click(screen.getByRole("button", { name: "Save draft" }));
  await screen.findByRole("heading", { name: "Draft saved" });
  await waitFor(() =>
    expect(transport.mock.calls.filter(([, options]) => options?.method === "PUT")).toHaveLength(1),
  );
  const saved = transport.mock.calls.find(([, options]) => options?.method === "PUT");
  expect(JSON.parse(String(saved?.[1]?.body))).toMatchObject({
    expectedRevision: "0",
    source,
    definition: plan.definition,
  });
  expect(screen.queryByRole("button", { name: "Save draft" })).not.toBeInTheDocument();
});

it("keeps an unknown save distinct and retries the identical command only on explicit action", async () => {
  const { user, transport } = await setup();
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Missing original test transport");
  let writes = 0;
  let finish: (() => void) | undefined;
  transport.mockImplementation(async (path, options) => {
    if (options?.method === "PUT") {
      writes++;
      if (writes === 1) throw new TypeError("Synthetic response delivery loss");
      await new Promise<void>((resolve) => {
        finish = resolve;
      });
    }
    return original(path, options);
  });
  await user.click(screen.getByRole("button", { name: "Capture profile" }));
  await user.click(screen.getByRole("button", { name: "Load inspected structure" }));
  await screen.findByLabelText("Reusable identifier 1");
  await user.type(screen.getByLabelText("Profile identifier"), "portable");
  await user.type(screen.getByLabelText("Profile revision"), "1");
  await user.type(screen.getByLabelText("Reusable identifier 1"), "first");
  await user.type(screen.getByLabelText("Label 1"), "First item");
  await user.click(screen.getByRole("button", { name: "Capture for review" }));
  await screen.findByText("Captured · Not saved");
  await user.click(screen.getByRole("button", { name: "Save draft" }));
  await screen.findByRole("heading", { name: "Save not confirmed" });
  expect(screen.queryByText("Captured · Not saved")).not.toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Draft saved" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Edit mappings" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Save draft" })).not.toBeInTheDocument();
  expect(screen.getAllByRole("alert")).toHaveLength(1);
  expect(screen.getByText("First item")).toBeVisible();
  expect(writes).toBe(1);
  await user.click(screen.getByRole("button", { name: "Back to plan" }));
  await user.click(screen.getByRole("button", { name: "Capture profile" }));
  await screen.findByRole("heading", { name: "Save not confirmed" });
  expect(screen.getByText("First item")).toBeVisible();
  expect(writes).toBe(1);
  await user.click(screen.getByRole("button", { name: "Retry save" }));
  await waitFor(() => expect(writes).toBe(2));
  const retry = screen.getByRole("button", { name: "Retry save" });
  expect(retry).toBeDisabled();
  await user.click(retry);
  expect(writes).toBe(2);
  expect(screen.queryByRole("heading", { name: "Draft saved" })).not.toBeInTheDocument();
  const requests = transport.mock.calls.filter(([, options]) => options?.method === "PUT");
  expect(requests[1][0]).toBe(requests[0][0]);
  expect(requests[1][1]?.body).toBe(requests[0][1]?.body);
  if (!finish) throw new Error("Retry was not dispatched");
  finish();
  await screen.findByRole("heading", { name: "Draft saved" });
  expect(screen.queryByRole("button", { name: "Retry save" })).not.toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Save not confirmed" })).not.toBeInTheDocument();
});

it("keeps mappings across pages and refuses duplicate identifiers without a capture request", async () => {
  const { user, transport } = await setup(true, 21);
  await user.click(screen.getByRole("button", { name: "Capture profile" }));
  await user.click(screen.getByRole("button", { name: "Load inspected structure" }));
  await screen.findByLabelText("Reusable identifier 1");
  await user.type(screen.getByLabelText("Profile identifier"), "portable");
  await user.type(screen.getByLabelText("Profile revision"), "1");
  // Populate repetitive setup through real paste events; keep typed editing below.
  // This test's oracle is cross-page state and diagnostics, not per-key throughput.
  for (let number = 1; number <= 20; number++) {
    await user.click(screen.getByLabelText(`Reusable identifier ${number}`));
    await user.paste(`slot-${number}`);
    await user.click(screen.getByLabelText(`Label ${number}`));
    await user.paste(`Item ${number}`);
  }
  expect(screen.getByRole("button", { name: "Capture for review" })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "Next items" }));
  await user.type(screen.getByLabelText("Reusable identifier 21"), "slot-1");
  await user.type(screen.getByLabelText("Label 21"), "Item 21");
  expect(screen.getByRole("button", { name: "Capture for review" })).toBeDisabled();
  expect(screen.getByRole("alert")).toHaveTextContent("2 items share reusable identifiers");
  expect(screen.getByLabelText("Reusable identifier 21")).toHaveAttribute("aria-invalid", "true");
  expect(screen.getByLabelText("Reusable identifier 21")).toHaveAccessibleDescription(
    "This identifier is also used by item 1. Enter a unique identifier.",
  );
  await user.click(screen.getByRole("button", { name: "Previous items" }));
  expect(screen.getByLabelText("Reusable identifier 1")).toHaveAccessibleDescription(
    "This identifier is also used by item 21. Enter a unique identifier.",
  );
  await user.click(screen.getByRole("button", { name: "Next items" }));
  expect(transport.mock.calls.some(([path]) => String(path).endsWith("/profile-captures"))).toBe(
    false,
  );
  await user.clear(screen.getByLabelText("Reusable identifier 21"));
  await user.type(screen.getByLabelText("Reusable identifier 21"), "slot-21");
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.getByLabelText("Reusable identifier 21")).not.toHaveAttribute("aria-invalid");
  await user.click(screen.getByRole("button", { name: "Previous items" }));
  for (let number = 1; number <= 20; number++) {
    expect(screen.getByLabelText(`Reusable identifier ${number}`)).toHaveValue(`slot-${number}`);
    expect(screen.getByLabelText(`Label ${number}`)).toHaveValue(`Item ${number}`);
  }
  await user.click(screen.getByRole("button", { name: "Next items" }));
  expect(screen.getByLabelText("Reusable identifier 21")).toHaveValue("slot-21");
  expect(screen.getByLabelText("Label 21")).toHaveValue("Item 21");
  expect(screen.getByRole("button", { name: "Capture for review" })).toBeEnabled();
  expect(transport.mock.calls.some(([path]) => String(path).endsWith("/profile-captures"))).toBe(
    false,
  );
});

it("requires valid inspection before revealing mapping controls", async () => {
  const { user, transport } = await setup(false);
  await user.click(screen.getByRole("button", { name: "Capture profile" }));
  expect(screen.getByRole("heading", { name: "An inspected plan is required" })).toBeVisible();
  expect(screen.queryByLabelText("Profile identifier")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Load inspected structure" }),
  ).not.toBeInTheDocument();
  expect(transport.mock.calls.some(([path]) => String(path).endsWith("/views/entities"))).toBe(
    false,
  );
  await user.click(screen.getByRole("button", { name: "Back to plan" }));
  expect(screen.getByRole("heading", { name: "Plans" })).toBeVisible();
});

it("invalidates unsaved mapping work when leaving capture without implicitly capturing or saving", async () => {
  const { user, transport } = await setup();
  await user.click(screen.getByRole("button", { name: "Capture profile" }));
  await user.click(screen.getByRole("button", { name: "Load inspected structure" }));
  await screen.findByLabelText("Reusable identifier 1");
  await user.type(screen.getByLabelText("Profile identifier"), "portable");
  await user.type(screen.getByLabelText("Reusable identifier 1"), "first");
  await user.click(screen.getByRole("button", { name: "Back to plan" }));
  await user.click(screen.getByRole("button", { name: "Capture profile" }));
  expect(screen.getByLabelText("Profile identifier")).toHaveValue("");
  expect(screen.queryByLabelText("Reusable identifier 1")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Load inspected structure" })).toBeEnabled();
  expect(
    transport.mock.calls.some(
      ([path, options]) => String(path).endsWith("/profile-captures") || options?.method === "PUT",
    ),
  ).toBe(false);
});

it("keeps navigation and session details accessible in the compact capture shell", async () => {
  vi.stubGlobal("matchMedia", () => ({
    matches: true,
    addEventListener() {},
    removeEventListener() {},
  }));
  const { user } = await setup(true, 2, true);
  await user.click(screen.getByRole("button", { name: "Capture profile" }));
  const menu = screen.getByRole("button", { name: "Workspace menu" });
  expect(menu).toHaveAttribute("aria-expanded", "false");
  expect(document.getElementById(menu.getAttribute("aria-controls") ?? "")).toHaveAttribute(
    "hidden",
  );
  await user.click(menu);
  expect(menu).toHaveAttribute("aria-expanded", "true");
  expect(screen.getByRole("button", { name: "Log out" })).toBeVisible();
  await user.click(menu);
  expect(screen.getByText("Session details").closest("details")).not.toHaveAttribute("open");
  await user.click(screen.getByText("Session details"));
  expect(screen.getByText("Session details").closest("details")).toHaveAttribute("open");
  await user.click(screen.getByRole("button", { name: "Load inspected structure" }));
  await screen.findByLabelText("Reusable identifier 1");
  const second = screen.getByText("Inspected item 2 · item");
  expect(second.closest("button")).toHaveAttribute("aria-expanded", "false");
  await user.click(second);
  expect(second.closest("button")).toHaveAttribute("aria-expanded", "true");
  await user.type(screen.getByLabelText("Reusable identifier 2"), "second");
  await user.click(second);
  expect(second.closest("button")).toHaveAttribute("aria-expanded", "false");
  expect(screen.getByText("second")).toBeVisible();
  await user.click(second);
  expect(screen.getByLabelText("Reusable identifier 2")).toHaveValue("second");
});
