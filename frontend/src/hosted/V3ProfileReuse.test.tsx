import { act, cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { HostedApi } from "../api/hosted";
import type { PlanSummary } from "../api/hostedV3Types";
import { V3ProfileReuse } from "./V3ProfileReuse";

// Independently invented wire fixture. Publication here is test-only, not compiler evidence.
const id = "74000000-0000-0000-0000-000000000001";
const profile = { objectId: "74000000-0000-0000-0000-000000000002", workspaceRevision: "2" };
const digest = "d".repeat(64);
const plan: PlanSummary = {
  planId: id,
  revision: "2",
  definition: { objectId: "74000000-0000-0000-0000-000000000003", workspaceRevision: "2" },
  bindingId: "mock-binding",
  destinationId: "mock-destination",
  currentCounts: { documents: 2, entities: 3, relations: 2 },
  targetCounts: { documents: 0, entities: 0, relations: 0 },
  inspectionValid: true,
  targetComplete: true,
  exportAvailable: false,
  blockers: [],
  observedDestination: {
    engine: "postgresql",
    identity: { systemIdentifier: "1", databaseOid: "2", databaseName: "invented_db" },
    observationFingerprint: digest,
    evidenceValid: true,
  },
  currentComputedCounts: null,
  targetComputedCounts: null,
};
const model = {
  schemaVersion: "3",
  id: "mock-profile",
  revision: "1",
  logicalDefinitionDigest: digest,
  entities: [1, 2, 3].map((n) => ({
    id: `slot-${n}`,
    type: "item",
    label: `Neutral ${n}`,
    requiredInputs: ["id"],
  })),
  relations: [{ type: "link", from: "slot-1", to: "slot-2" }],
};
const source = {
  ...profile,
  sourceDigest: digest,
  contentDigest: digest,
  format: "JSON",
  source: JSON.stringify(model),
  schemaVersion: "3",
  compilerVersion: "profile-compiler-v3",
  definition: plan.definition,
  state: "published",
  publication: { digest, sourceRevision: "1" },
  projection: { kind: "structurally-valid", contentDigest: digest, diagnostics: [], model },
};
const inventory = [1, 2, 3].map((n) => ({
  entity: { kind: "existing", handle: `75000000-0000-0000-0000-00000000000${n}` },
  typeId: "item",
  fields: [
    { fieldId: "id", present: true, masked: false, value: `Invented ${n}` },
    { fieldId: "private", present: true, masked: true, value: null },
  ],
}));
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const owners: HostedApi[] = [];
afterEach(() => {
  cleanup();
  for (const api of owners) api.clear();
  owners.length = 0;
});
async function setup(size = 3) {
  const selectedModel = {
    ...model,
    entities: Array.from({ length: size }, (_, i) => ({
      id: `slot-${i + 1}`,
      type: "item",
      label: `Neutral ${i + 1}`,
      requiredInputs: ["id"],
    })),
  };
  let applied = false;
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path, options) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 1800,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (path === "/api/v3/profiles")
      return json({
        profiles: [
          {
            ...profile,
            nativeId: model.id,
            nativeRevision: model.revision,
            sourceDigest: digest,
            contentDigest: digest,
            state: "published",
            definition: plan.definition,
          },
        ],
      });
    if (path === `/api/v3/profiles/${profile.objectId}/revisions/2`) {
      const { contentDigest: _, ...wire } = source;
      return json({
        ...wire,
        source: JSON.stringify(selectedModel),
        projection: { ...wire.projection, model: selectedModel },
      });
    }
    if (path === `/api/v3/plans/${id}`)
      return json({ ...plan, revision: applied ? "3" : "2", targetComplete: !applied });
    if (String(path).endsWith("/views/entities"))
      return json({ revision: "2", total: 3, offset: 0, nextOffset: null, items: inventory });
    if (String(path).endsWith("/profile-previews")) {
      const body = JSON.parse(String(options?.body));
      const all = body.selection.kind === "all";
      const included = selectedModel.entities.slice(0, all ? size : 2).map((e) => ({
        slotId: e.id,
        typeId: e.type,
        label: e.label,
        requiredInputs: e.requiredInputs,
      }));
      const items =
        body.section === "included"
          ? included
          : body.section === "dependencies" && !all
            ? [
                {
                  slotId: "slot-2",
                  causedBy: "slot-1",
                  relationId: "link",
                  reason: "declared-reuse-target",
                },
              ]
            : body.section === "relations"
              ? [{ relationId: "link", fromSlot: "slot-1", toSlot: "slot-2" }]
              : [];
      return json({
        revision: "2",
        section: body.section,
        total: items.length,
        offset: 0,
        nextOffset: null,
        items,
        previewDigest: digest,
        affectedDerivations: [],
        pins: {
          planId: id,
          revision: "2",
          observationFingerprint: digest,
          profile,
          publicationDigest: digest,
          selectedRoots: all
            ? selectedModel.entities.map((e) => e.id).sort()
            : [...body.selection.roots].sort(),
          rootsDigest: digest,
          closureDigest: digest,
        },
      });
    }
    if (String(path).endsWith("/commands")) {
      applied = true;
      return json({ planId: id, revision: "3" });
    }
    throw new Error("Unexpected invented reuse journey route");
  });
  const api = new HostedApi(transport);
  owners.push(api);
  await api.session();
  const back = vi.fn();
  const props = {
    api,
    plan,
    active: true,
    back,
    refreshPlan: vi.fn<() => Promise<void>>().mockResolvedValue(),
  };
  const rendered = render(<V3ProfileReuse {...props} />);
  return { ...rendered, props, transport, back, user: userEvent.setup() };
}
async function select(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Load profiles and inspected items" }));
  await user.click(await screen.findByRole("button", { name: /mock-profile.*Saved revision 2/ }));
  await screen.findByRole("checkbox", { name: /^Neutral 1(?![0-9])/ });
}
it("retains the original uncertain apply across reentry and retries only its exact command", async () => {
  const { user, transport, props, rerender } = await setup();
  await select(user);
  await user.click(screen.getByRole("radio", { name: /Selected parts/ }));
  await user.click(screen.getByRole("checkbox", { name: /^Neutral 1(?![0-9])/ }));
  await user.click(screen.getByRole("button", { name: "Preview selection" }));
  await screen.findByText("1 selected · 1 required dependency · 2 included");
  for (const slot of [1, 2]) {
    await user.selectOptions(screen.getByLabelText(`Action for Neutral ${slot}`), "use-existing");
    await user.selectOptions(
      screen.getByLabelText(`Current item for Neutral ${slot}`),
      inventory[slot - 1].entity.handle,
    );
  }
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Invented transport missing");
  let commandCount = 0;
  let release: ((response: Response) => void) | undefined;
  transport.mockImplementation((path, options) => {
    if (String(path).endsWith("/commands")) {
      commandCount++;
      if (commandCount === 1) return Promise.reject(new TypeError("Synthetic lost response"));
      return new Promise((resolve) => {
        release = resolve;
      });
    }
    return original(path, options);
  });
  await user.click(screen.getByRole("button", { name: "Apply to target plan" }));
  await screen.findByRole("heading", { name: "Apply not confirmed" });
  expect(screen.queryByRole("heading", { name: "Plan updated" })).not.toBeInTheDocument();
  rerender(<V3ProfileReuse {...props} active={false} />);
  rerender(
    <V3ProfileReuse
      {...props}
      plan={{ ...plan, planId: "74000000-0000-0000-0000-000000000004", revision: "8" }}
    />,
  );
  expect(commandCount).toBe(1);
  const retained = screen.getByRole("region", { name: "Preserved placement" });
  expect(within(retained).getByText("Neutral 1")).toBeVisible();
  expect(within(retained).getByText("Neutral 2")).toBeVisible();
  expect(within(retained).queryByText("Neutral 3")).not.toBeInTheDocument();
  expect(within(retained).queryByRole("combobox")).not.toBeInTheDocument();
  expect(screen.getByText(/Original plan revision 2/)).toBeVisible();
  await user.click(screen.getByRole("button", { name: "Retry apply" }));
  expect(screen.getByRole("button", { name: "Retry apply" })).toBeDisabled();
  expect(screen.getByRole("heading", { name: "Apply not confirmed" })).toBeVisible();
  const sent = transport.mock.calls.filter(([path]) => String(path).endsWith("/commands"));
  expect(sent).toHaveLength(2);
  expect(sent[1][0]).toBe(`/api/v3/plans/${id}/commands`);
  expect(sent[1][1]?.body).toBe(sent[0][1]?.body);
  if (!release) throw new Error("Retry was not held");
  await act(async () => {
    release?.(json({ planId: id, revision: "3" }));
  });
  await screen.findByRole("heading", { name: "Reuse applied to plan revision 3" });
  expect(screen.queryByRole("button", { name: "Retry apply" })).not.toBeInTheDocument();
});
it("previews dependencies, requires explicit placements and reports the actual applied incomplete plan", async () => {
  const { user, transport } = await setup();
  await select(user);
  await user.click(screen.getByRole("radio", { name: /Selected parts/ }));
  await user.click(screen.getByRole("checkbox", { name: /Neutral 1/ }));
  expect(transport.mock.calls.filter(([p]) => String(p).endsWith("/commands"))).toHaveLength(0);
  await user.click(screen.getByRole("button", { name: "Preview selection" }));
  await screen.findByText("1 selected · 1 required dependency · 2 included");
  expect(screen.getByRole("button", { name: "Apply to target plan" })).toBeDisabled();
  for (const [slot, target] of [
    [1, 3],
    [2, 1],
  ]) {
    await user.selectOptions(screen.getByLabelText(`Action for Neutral ${slot}`), "use-existing");
    await user.selectOptions(
      screen.getByLabelText(`Current item for Neutral ${slot}`),
      inventory[target - 1].entity.handle,
    );
  }
  expect(screen.getByText("Choices entered · Not applied")).toBeVisible();
  expect(transport.mock.calls.filter(([p]) => String(p).endsWith("/commands"))).toHaveLength(0);
  await user.click(screen.getByRole("button", { name: "Apply to target plan" }));
  await screen.findByRole("heading", { name: "Reuse applied to plan revision 3" });
  await screen.findByText("Target incomplete");
  expect(screen.getByText("Export unavailable")).toBeVisible();
  const receipt = screen.getByRole("region", { name: "Applied placement" });
  expect(within(receipt).queryByText("Neutral 3")).not.toBeInTheDocument();
  expect(within(receipt).queryByRole("combobox")).not.toBeInTheDocument();
  expect(within(receipt).getByText("Required by Neutral 1 · link")).toBeVisible();
  expect(within(receipt).getAllByText("Type: item")).toHaveLength(2);
  expect(within(receipt).getAllByText("Required inputs: id")).toHaveLength(2);
  const sent = transport.mock.calls.filter(([p]) => String(p).endsWith("/commands"));
  expect(sent).toHaveLength(1);
  expect(JSON.parse(String(sent[0][1]?.body))).toMatchObject({
    kind: "compose-profile",
    expectedRevision: "2",
    profile,
    selectedRoots: ["slot-1"],
    decisions: [
      { kind: "use-existing", slotId: "slot-1", target: inventory[2].entity },
      { kind: "use-existing", slotId: "slot-2", target: inventory[0].entity },
    ],
  });
});
it("keeps whole reuse available and invalidates placement when selection changes", async () => {
  const { user, transport } = await setup();
  await select(user);
  await user.click(screen.getByRole("button", { name: "Preview selection" }));
  await screen.findByText("3 selected · 0 required dependencies · 3 included");
  await user.selectOptions(screen.getByLabelText("Action for Neutral 1"), "create");
  await user.type(screen.getByLabelText("Target identifier for Neutral 1"), "new-first");
  await user.click(screen.getByRole("button", { name: "Change selection" }));
  expect(screen.queryByRole("button", { name: "Apply to target plan" })).not.toBeInTheDocument();
  await user.click(screen.getByRole("radio", { name: /Selected parts/ }));
  expect(screen.getByRole("button", { name: "Preview selection" })).toBeDisabled();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(transport.mock.calls.filter(([p]) => String(p).endsWith("/commands"))).toHaveLength(0);
});

it("does not start retired inventory after a held catalogue crosses leave, context change and reentry", async () => {
  const { user, transport, props, rerender } = await setup();
  const original = transport.getMockImplementation();
  if (!original) throw new Error("Invented transport missing");
  let release: ((response: Response) => void) | undefined;
  let held = true;
  const replacement = { ...plan, planId: "74000000-0000-0000-0000-000000000004" };
  transport.mockImplementation((path, options) => {
    if (path === "/api/v3/profiles" && held) {
      held = false;
      return new Promise<Response>((resolve) => {
        release = resolve;
      });
    }
    if (path === `/api/v3/plans/${replacement.planId}`) return Promise.resolve(json(replacement));
    return original(path, options);
  });
  await user.click(screen.getByRole("button", { name: "Load profiles and inspected items" }));
  rerender(<V3ProfileReuse {...props} active={false} />);
  rerender(<V3ProfileReuse {...props} plan={replacement} active={true} />);
  if (!release) throw new Error("Held catalogue not started");
  const response = await original("/api/v3/profiles");
  await act(async () => {
    release?.(response);
  });
  expect(
    transport.mock.calls.filter(([path]) => String(path).endsWith("/views/entities")),
  ).toHaveLength(0);
  await user.click(screen.getByRole("button", { name: "Load profiles and inspected items" }));
  await screen.findByRole("button", { name: /mock-profile.*Saved revision 2/ });
  expect(
    transport.mock.calls
      .filter(([path]) => String(path).endsWith("/views/entities"))
      .map(([path]) => path),
  ).toEqual([`/api/v3/plans/${replacement.planId}/views/entities`]);
});

it("prepares a target only explicitly and waits for refreshed context before showing selection", async () => {
  const { user, transport, props, rerender } = await setup();
  rerender(<V3ProfileReuse {...props} plan={{ ...plan, targetComplete: false }} />);
  expect(
    screen.queryByRole("button", { name: "Load profiles and inspected items" }),
  ).not.toBeInTheDocument();
  expect(
    transport.mock.calls.filter(([p]) => String(p).endsWith("/materializations")),
  ).toHaveLength(0);
  transport.mockResolvedValueOnce(
    json({ revision: "2", state: "COMPLETE", complete: true, diagnostics: [] }),
  );
  await user.click(screen.getByRole("button", { name: "Prepare target preview" }));
  expect(props.refreshPlan).toHaveBeenCalledTimes(1);
  expect(
    screen.queryByRole("button", { name: "Load profiles and inspected items" }),
  ).not.toBeInTheDocument();
  const sent = transport.mock.calls.filter(([p]) => String(p).endsWith("/materializations"));
  expect(sent).toHaveLength(1);
  expect(JSON.parse(String(sent[0][1]?.body))).toEqual({ revision: "2" });
  rerender(<V3ProfileReuse {...props} />);
  expect(screen.getByRole("button", { name: "Load profiles and inspected items" })).toBeVisible();
});

it("keeps a completed preparation diagnostic in its own context and retires it before another plan", async () => {
  const { user, transport, props, rerender } = await setup();
  const incomplete = { ...plan, targetComplete: false };
  rerender(<V3ProfileReuse {...props} plan={incomplete} />);
  transport.mockResolvedValueOnce(
    json({ revision: "2", state: "INCOMPLETE", complete: false, diagnostics: ["MOCK_REQUIRED"] }),
  );
  await user.click(screen.getByRole("button", { name: "Prepare target preview" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("MOCK_REQUIRED");
  rerender(<V3ProfileReuse {...props} plan={{ ...incomplete }} back={vi.fn()} />);
  expect(screen.getByRole("alert")).toHaveTextContent("MOCK_REQUIRED");
  rerender(<V3ProfileReuse {...props} plan={incomplete} active={false} />);
  rerender(
    <V3ProfileReuse
      {...props}
      plan={{ ...incomplete, planId: "74000000-0000-0000-0000-000000000004" }}
    />,
  );
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Prepare target preview" })).toBeEnabled();
  expect(
    transport.mock.calls.filter(([path]) => String(path).endsWith("/materializations")),
  ).toHaveLength(1);
});

it("retains selected roots across source pages without previewing or inferring choices", async () => {
  const { user, transport } = await setup(21);
  await select(user);
  await user.click(screen.getByRole("radio", { name: /Selected parts/ }));
  await user.click(screen.getByRole("checkbox", { name: /^Neutral 1(?![0-9])/ }));
  await user.click(screen.getByRole("button", { name: "Next profile items" }));
  await user.click(screen.getByRole("checkbox", { name: /Neutral 21/ }));
  await user.click(screen.getByRole("button", { name: "Previous profile items" }));
  expect(screen.getByRole("checkbox", { name: /^Neutral 1(?![0-9])/ })).toBeChecked();
  await user.click(screen.getByRole("button", { name: "Next profile items" }));
  expect(screen.getByRole("checkbox", { name: /Neutral 21/ })).toBeChecked();
  expect(
    transport.mock.calls.filter(
      ([path]) => String(path).endsWith("/profile-previews") || String(path).endsWith("/commands"),
    ),
  ).toHaveLength(0);
});
it("keeps preparation unavailable without valid inspection and does not refresh after incomplete result becomes retired", async () => {
  const { user, transport, props, rerender } = await setup();
  rerender(
    <V3ProfileReuse {...props} plan={{ ...plan, inspectionValid: false, targetComplete: false }} />,
  );
  expect(screen.queryByRole("button", { name: "Prepare target preview" })).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "An inspected plan is required" })).toBeVisible();
  rerender(<V3ProfileReuse {...props} plan={{ ...plan, targetComplete: false }} />);
  let release: ((response: Response) => void) | undefined;
  transport.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        release = resolve;
      }),
  );
  await user.click(screen.getByRole("button", { name: "Prepare target preview" }));
  rerender(<V3ProfileReuse {...props} active={false} />);
  if (!release) throw new Error("Preparation not held");
  await act(async () => {
    release?.(
      json({ revision: "2", state: "INCOMPLETE", complete: false, diagnostics: ["MOCK_REQUIRED"] }),
    );
  });
  expect(props.refreshPlan).not.toHaveBeenCalled();
});

it("preserves explicit placement choices across pages without submitting", async () => {
  const { user, transport } = await setup(21);
  await select(user);
  await user.click(screen.getByRole("button", { name: "Preview selection" }));
  await screen.findByText("21 selected · 0 required dependencies · 21 included");
  await user.selectOptions(screen.getByLabelText("Action for Neutral 1"), "use-existing");
  await user.selectOptions(
    screen.getByLabelText("Current item for Neutral 1"),
    inventory[0].entity.handle,
  );
  await user.click(screen.getByRole("button", { name: "Next placements" }));
  await user.selectOptions(screen.getByLabelText("Action for Neutral 21"), "use-existing");
  await user.selectOptions(
    screen.getByLabelText("Current item for Neutral 21"),
    inventory[2].entity.handle,
  );
  await user.click(screen.getByRole("button", { name: "Previous placements" }));
  expect(screen.getByLabelText("Action for Neutral 1")).toHaveValue("use-existing");
  expect(screen.getByLabelText("Current item for Neutral 1")).toHaveValue(
    inventory[0].entity.handle,
  );
  await user.click(screen.getByRole("button", { name: "Next placements" }));
  expect(screen.getByLabelText("Current item for Neutral 21")).toHaveValue(
    inventory[2].entity.handle,
  );
  expect(screen.getByRole("button", { name: "Apply to target plan" })).toBeDisabled();
  expect(transport.mock.calls.filter(([path]) => String(path).endsWith("/commands"))).toHaveLength(
    0,
  );
});

it("requires an explicit portable identifier for a new item and sends only that choice", async () => {
  const { user, transport } = await setup();
  await select(user);
  await user.click(screen.getByRole("radio", { name: /Selected parts/ }));
  await user.click(screen.getByRole("checkbox", { name: /^Neutral 1(?![0-9])/ }));
  await user.click(screen.getByRole("button", { name: "Preview selection" }));
  await screen.findByText("1 selected · 1 required dependency · 2 included");
  await user.selectOptions(screen.getByLabelText("Action for Neutral 1"), "create");
  const identifier = screen.getByLabelText("Target identifier for Neutral 1");
  expect(identifier).toHaveValue("");
  await user.selectOptions(screen.getByLabelText("Action for Neutral 2"), "use-existing");
  await user.selectOptions(
    screen.getByLabelText("Current item for Neutral 2"),
    inventory[1].entity.handle,
  );
  expect(screen.getByRole("button", { name: "Apply to target plan" })).toBeDisabled();
  await user.type(identifier, "INVALID");
  expect(screen.getByRole("button", { name: "Apply to target plan" })).toBeDisabled();
  expect(transport.mock.calls.filter(([path]) => String(path).endsWith("/commands"))).toHaveLength(
    0,
  );
  await user.clear(identifier);
  await user.type(identifier, "new-item");
  await user.click(screen.getByRole("button", { name: "Apply to target plan" }));
  await screen.findByRole("heading", { name: "Reuse applied to plan revision 3" });
  const sent = transport.mock.calls.filter(([path]) => String(path).endsWith("/commands"));
  expect(sent).toHaveLength(1);
  expect(JSON.parse(String(sent[0][1]?.body)).decisions).toEqual([
    { kind: "create", slotId: "slot-1", targetSlotId: "new-item" },
    { kind: "use-existing", slotId: "slot-2", target: inventory[1].entity },
  ]);
});
