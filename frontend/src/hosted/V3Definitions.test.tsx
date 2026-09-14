import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import native from "../../../fixtures/native-v3/definition.json";
import { HostedApi } from "../api/hosted";
import { VersionedDefinitions } from "./VersionedDefinitions";

const source = JSON.stringify(native);
const model = JSON.parse(JSON.stringify(native), (_key, value) =>
  typeof value === "number" ? String(value) : value,
);
const owners: HostedApi[] = [];
afterEach(() => {
  for (const owner of owners) owner.clear();
  owners.length = 0;
});
const json = (body: unknown) =>
  new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } });
function document(objectId: string, savedSource = source, savedFormat = "JSON") {
  return {
    objectId,
    workspaceRevision: "1",
    sourceDigest: "c".repeat(64),
    source: savedSource,
    format: savedFormat,
    schemaVersion: "3",
    compilerVersion: "native-compiler-v3",
    state: "draft",
    projection: {
      kind: "incomplete",
      model,
      logicalDigest: "a".repeat(64),
      bindingDigests: Object.fromEntries(
        model.bindings.map((b: { id: string }) => [b.id, "b".repeat(64)]),
      ),
      mechanisms: {
        "xml-path-v1": "1",
        "xml-span-v1": "1",
        "generic-graph-v1": "1",
        "native-compiler-v3": "1",
        "derived-graph-v1": "1",
      },
      diagnostics: [
        {
          phase: "publication",
          code: "MECHANISM_NOT_QUALIFIED",
          pointer: "",
          message: "Invented incomplete transport fixture.",
        },
      ],
    },
  };
}

function readyDocument(objectId: string, savedSource = source, savedFormat = "JSON") {
  const saved = document(objectId, savedSource, savedFormat);
  saved.projection = {
    ...saved.projection,
    kind: "historical-ready",
    diagnostics: [],
  };
  return saved;
}
function publishedDocument(
  objectId: string,
  exportPolicies: readonly {
    readonly bindingId: string;
    readonly documentId: string;
    readonly content: "deny" | "protected-self-contained";
  }[],
) {
  const saved = readyDocument(objectId);
  saved.workspaceRevision = "2";
  return {
    ...saved,
    state: "published",
    publication: {
      digest: "d".repeat(64),
      sourceRevision: "1",
      exportPolicies: [...exportPolicies].sort((a, b) =>
        a.bindingId === b.bindingId
          ? a.documentId.localeCompare(b.documentId)
          : a.bindingId.localeCompare(b.bindingId),
      ),
    },
  };
}

async function setupView() {
  const fetcher = vi.fn<typeof fetch>().mockImplementation(async (path) => {
    if (path === "/api/v1/session")
      return json({
        authenticated: true,
        csrfHeaderName: "X-CSRF-TOKEN",
        csrfToken: "mock-token",
        idleTimeoutSeconds: 900,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      });
    if (path === "/api/v2/definitions") return json({ definitions: [], canPublish: false });
    if (path === "/api/v3/definitions") return json({ definitions: [] });
    throw new Error("Unexpected fixture route");
  });
  const api = new HostedApi(fetcher);
  owners.push(api);
  await api.session();
  const view = render(<VersionedDefinitions api={api} enabled={true} changed={() => {}} />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByRole("combobox", { name: "Model version" }), "3");
  await screen.findByText("No saved definitions for this model version.");
  return { ...view, user, fetcher, api };
}
it("uploads exact source, saves through the typed API and inspects the acknowledged revision", async () => {
  const { user, fetcher } = await setupView();
  const exact = `${source}\r\n`;
  await user.upload(screen.getByLabelText("Upload definition"), new File([exact], "invented.json"));
  await waitFor(() =>
    expect(screen.getByRole("textbox", { name: /^Source$/ })).toHaveValue(`${source}\n`),
  );
  fetcher.mockImplementationOnce(async (path, options) => {
    const command = JSON.parse(String(options?.body));
    expect(command.source).toBe(exact);
    expect(command.expectedRevision).toBe("0");
    expect(command.format).toBe("JSON");
    expect(options?.method).toBe("PUT");
    return json(document(String(path).split("/").at(-1) ?? "", exact));
  });
  await user.click(screen.getByRole("button", { name: /^Save draft$/ }));
  expect(await screen.findByText("Saved revision 1 · draft · incomplete")).toBeVisible();
  await user.click(screen.getByRole("tab", { name: "Source" }));
  expect(screen.getByRole("tabpanel").querySelector("pre")?.textContent).toBe(exact);
  await user.click(screen.getByRole("tab", { name: "Diagnostics" }));
  expect(screen.getByText("MECHANISM_NOT_QUALIFIED")).toBeVisible();
  await user.click(screen.getByRole("tab", { name: "Model" }));
  await user.click(screen.getByText("Complete declared model"));
  expect(screen.getByRole("tabpanel").querySelector("pre")?.textContent).toBe(
    JSON.stringify(model, null, 2),
  );
  expect(screen.getByRole("button", { name: "Publish definition" })).toBeDisabled();
  expect(
    screen.getByText("Resolve publication diagnostics before publishing this draft."),
  ).toBeVisible();
});
it("publishes a historical-ready v3 definition with explicit document policies", async () => {
  const { user, fetcher } = await setupView();
  fireEvent.change(screen.getByRole("textbox", { name: /^Source$/ }), {
    target: { value: source },
  });
  let savedId = "";
  fetcher.mockImplementationOnce(async (path, options) => {
    expect(options?.method).toBe("PUT");
    savedId = String(path).split("/").at(-1) ?? "";
    return json(readyDocument(savedId));
  });
  await user.click(screen.getByRole("button", { name: /^Save draft$/ }));
  expect(await screen.findByText("Saved revision 1 · draft · historical-ready")).toBeVisible();

  const publish = screen.getByRole("button", { name: "Publish definition" });
  expect(publish).toBeDisabled();
  await user.selectOptions(
    screen.getByLabelText("mock-oracle / glyph-sheet export policy"),
    "deny",
  );
  await user.selectOptions(
    screen.getByLabelText("mock-oracle / palette-sheet export policy"),
    "deny",
  );
  await user.selectOptions(
    screen.getByLabelText("mock-pg / glyph-sheet export policy"),
    "protected-self-contained",
  );
  await user.selectOptions(screen.getByLabelText("mock-pg / palette-sheet export policy"), "deny");
  expect(publish).toBeDisabled();
  await user.click(
    screen.getByRole("checkbox", {
      name: "I reviewed complete-document disclosure and every document policy.",
    }),
  );
  await waitFor(() => expect(publish).toBeEnabled());

  fetcher.mockImplementationOnce(async (path, options) => {
    expect(path).toBe(`/api/v3/definitions/${savedId}/publish`);
    expect(options?.method).toBe("POST");
    const command = JSON.parse(String(options?.body));
    expect(command.expectedRevision).toBe("1");
    expect(command.exportPolicies).toEqual([
      { bindingId: "mock-oracle", documentId: "glyph-sheet", content: "deny" },
      { bindingId: "mock-oracle", documentId: "palette-sheet", content: "deny" },
      {
        bindingId: "mock-pg",
        documentId: "glyph-sheet",
        content: "protected-self-contained",
      },
      { bindingId: "mock-pg", documentId: "palette-sheet", content: "deny" },
    ]);
    return json(publishedDocument(savedId, command.exportPolicies));
  });
  await user.click(publish);
  expect(await screen.findByText("Saved revision 2 · published · historical-ready")).toBeVisible();
  expect(screen.getByText(/Published revision 2 from source revision 1/)).toBeVisible();
});

it("retains an uncertain v3 save and locks upload and version changes until exact replay", async () => {
  const { user, fetcher } = await setupView();
  fireEvent.change(screen.getByRole("textbox", { name: /^Source$/ }), {
    target: { value: source },
  });
  fetcher.mockRejectedValueOnce(new Error("Invented response loss"));
  await user.click(screen.getByRole("button", { name: /^Save draft$/ }));
  await screen.findByRole("button", { name: "Retry original save" });
  expect(
    screen.queryByText("No saved definitions for this model version."),
  ).not.toBeInTheDocument();
  const original = fetcher.mock.calls.at(-1);
  expect(screen.getByRole("combobox", { name: "Model version" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "Upload definition" })).toBeDisabled();
  expect(screen.getByRole("textbox", { name: /^Source$/ })).toBeDisabled();
  fetcher.mockImplementationOnce(async (path, options) => {
    expect(path).toBe(original?.[0]);
    expect(options?.body).toBe(original?.[1]?.body);
    return json(document(String(path).split("/").at(-1) ?? ""));
  });
  await user.click(screen.getByRole("button", { name: "Retry original save" }));
  await screen.findByText("Saved revision 1 · draft · incomplete");
  expect(screen.getByRole("combobox", { name: "Model version" })).toBeEnabled();
});
it("requires replacement consent and keeps existing edits when file selection is cancelled", async () => {
  const { user } = await setupView();
  fireEvent.change(screen.getByRole("textbox", { name: /^Source$/ }), {
    target: { value: "unsaved" },
  });
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  try {
    await user.upload(
      screen.getByLabelText("Upload definition"),
      new File([source], "invented.json"),
    );
    expect(confirm).toHaveBeenCalledOnce();
    expect(screen.getByRole("textbox", { name: /^Source$/ })).toHaveValue("unsaved");
    confirm.mockReturnValue(true);
    await user.upload(
      screen.getByLabelText("Upload definition"),
      new File([source], "invented.json"),
    );
    await waitFor(() =>
      expect(screen.getByRole("textbox", { name: /^Source$/ })).toHaveValue(source),
    );
  } finally {
    confirm.mockRestore();
  }
});

it("does not claim the workspace is empty after a saved revision outlives list-refresh failure", async () => {
  const { user, fetcher } = await setupView();
  fireEvent.change(screen.getByRole("textbox", { name: /^Source$/ }), {
    target: { value: source },
  });
  fetcher.mockImplementationOnce(async (path) =>
    json(document(String(path).split("/").at(-1) ?? "")),
  );
  fetcher.mockRejectedValueOnce(new Error("Invented refresh failure"));
  await user.click(screen.getByRole("button", { name: /^Save draft$/ }));
  await screen.findByText("Saved revision 1 · draft · incomplete");
  expect(
    screen.queryByText("No saved definitions for this model version."),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("combobox", { name: "Saved definition" })).not.toHaveTextContent(
    "No saved definitions",
  );
  expect(screen.queryByRole("button", { name: "Retry original save" })).not.toBeInTheDocument();
});

it("replaces duplicate diagnostics with exactly the next acknowledged revision", async () => {
  const { user, fetcher } = await setupView();
  fireEvent.change(screen.getByRole("textbox", { name: /^Source$/ }), {
    target: { value: source },
  });
  fetcher.mockImplementationOnce(async (path) => {
    const saved = document(String(path).split("/").at(-1) ?? "");
    const entry = saved.projection.diagnostics[0];
    saved.projection.diagnostics = [entry, { ...entry }];
    return json(saved);
  });
  await user.click(screen.getByRole("button", { name: /^Save draft$/ }));
  await screen.findByText("Saved revision 1 · draft · incomplete");
  await user.click(screen.getByRole("tab", { name: "Diagnostics" }));
  expect(screen.getAllByText("MECHANISM_NOT_QUALIFIED")).toHaveLength(2);
  fireEvent.change(screen.getByRole("textbox", { name: /^Source$/ }), {
    target: { value: `${source}\n` },
  });
  fetcher.mockImplementationOnce(async (path) => {
    const saved = document(String(path).split("/").at(-1) ?? "", `${source}\n`);
    saved.workspaceRevision = "2";
    saved.projection.diagnostics = [
      {
        phase: "publication",
        code: "BINDING_NOT_QUALIFIED",
        pointer: "",
        message: "Invented replacement diagnostic.",
      },
    ];
    return json(saved);
  });
  await user.click(screen.getByRole("button", { name: /^Save draft$/ }));
  await screen.findByText("Saved revision 2 · draft · incomplete");
  expect(screen.queryAllByText("MECHANISM_NOT_QUALIFIED")).toHaveLength(0);
  expect(screen.getAllByText("BINDING_NOT_QUALIFIED")).toHaveLength(1);
});
