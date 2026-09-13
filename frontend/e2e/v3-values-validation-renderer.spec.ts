import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import * as D from "../src/api/hostedV3Decoding";
import * as P from "../src/api/hostedV3PhysicalDecoding";

// Real browser session/HTTP/SQLite prerequisite. Values and validation use actual UI dispatch.
// Independently invented workflow fixtures only; reports and credentials stay in private RAM.
test("enters one target value and validates backend outcomes without export authority", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Sign in with OIDC" }).click();
  const settled = async () =>
    expect((await page.request.get("http://127.0.0.1:18446/control/settled")).ok()).toBe(true);
  async function request(method: string, path: string, body?: unknown, status = 200) {
    await settled();
    const result = await page.evaluate(
      async ({ method, path, body }) => {
        const session = await (await fetch("/api/v1/session")).json();
        const response = await fetch(path, {
          method,
          headers: {
            "Content-Type": "application/json",
            [session.csrfHeaderName]: session.csrfToken,
          },
          ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        });
        const text = await response.text();
        return {
          status: response.status,
          cache: response.headers.get("cache-control"),
          value: text ? JSON.parse(text) : null,
        };
      },
      { method, path, body },
    );
    expect(result.status).toBe(status);
    expect(result.cache).toBe("no-store");
    return result.value;
  }
  const created = D.ack(
    await request(
      "POST",
      "/api/v3/plans",
      {
        expectedRevision: "0",
        requestId: crypto.randomUUID(),
        definition: { objectId: "00000000-0000-4000-8000-000000000919", workspaceRevision: "2" },
        bindingId: "mock-pg",
        destinationId: "mock-destination",
      },
      201,
    ),
  );
  const planPath = `/api/v3/plans/${created.planId}`;
  const reserved = await request(
    "POST",
    `${planPath}/inspections`,
    { expectedRevision: "1", requestId: crypto.randomUUID(), discardDraftOnSuccess: true },
    202,
  );
  const observed = await request("POST", `/api/v3/operations/${reserved.operationId}/credentials`, {
    username: "MockV3Reader",
    password: "MockV3-Password-𐀀",
  });
  expect(observed.phase).toBe("succeeded");
  const original = P.entities(
    await request("POST", `${planPath}/views/entities`, {
      revision: "2",
      side: "current",
      offset: 0,
      limit: 100,
    }),
  );
  const selected = original.items.find((item) =>
    item.fields.some((field) => field.fieldId === "id" && field.value === "one"),
  );
  expect(selected).toBeDefined();
  if (!selected) throw new Error("MOCK_ITEM_MISSING");
  const fields = Object.fromEntries(
    ["id", "tone", "finish", "secret", "optional", "extra"].map((id) => [
      id,
      { kind: id === "tone" ? "unresolved" : "keep-observed" },
    ]),
  );
  expect(
    D.ack(
      await request("POST", `${planPath}/commands`, {
        kind: "upsert-entity",
        expectedRevision: "2",
        requestId: crypto.randomUUID(),
        decision: {
          kind: "retain",
          entity: selected.entity,
          fields,
          references: { link: { kind: "keep-observed" } },
        },
        placements: [],
      }),
    ).revision,
  ).toBe("3");

  async function inspect(scope: string) {
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    const undersized = await page.locator(".capture-workspace:visible").evaluate((workspace) =>
      [...workspace.querySelectorAll<HTMLElement>("button, input, select, summary")]
        .filter((element) => element.getClientRects().length > 0)
        .map((element) => {
          const rect = element.getBoundingClientRect();
          return { tag: element.tagName, width: rect.width, height: rect.height };
        })
        .filter((rect) => rect.width < 44 || rect.height < 44),
    );
    expect(undersized).toEqual([]);
    const viewport = page.viewportSize();
    if (viewport) {
      await page.setViewportSize({ width: 320, height: viewport.height });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      await page.setViewportSize(viewport);
    }
    console.log(`VALUES_VALIDATION_RENDERER ${scope}`);
  }

  await page.getByRole("combobox", { name: "Model version" }).selectOption("3");
  await expect(page.getByRole("region", { name: "Current plan context" })).toContainText(
    "revision 3",
  );
  await page.getByRole("button", { name: "Define values", exact: true }).click();
  await page.getByRole("button", { name: "Load target values", exact: true }).click();
  await page.getByRole("button", { name: "Load comparison", exact: true }).click();
  await expect(page.getByRole("group", { name: "Field tone" })).toContainText("alpha");
  await expect(page.getByRole("group", { name: "Field tone" })).toContainText("Target unresolved");
  await inspect("values-loaded");
  await page.getByLabel("Field to update").selectOption("tone");
  await page.getByLabel("Target value text").fill("beta");
  const commandResponse = page.waitForResponse(
    (response) => response.url().endsWith("/commands") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Submit target value", exact: true }).click();
  const submitted = await commandResponse;
  expect(submitted.status()).toBe(200);
  await expect(page.getByText("Target value saved for this plan revision.")).toBeVisible();
  await expect.poll(async () => D.summary(await request("GET", planPath)).revision).toBe("4");
  await page.getByRole("button", { name: "Back to plan", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current plan context" })).toContainText(
    "revision 4",
  );
  await page.getByRole("button", { name: "Validate plan", exact: true }).click();
  await page.getByRole("button", { name: "Run validation", exact: true }).click();
  const validationWorkspace = page.locator(".validation-workspace:visible");
  await expect(validationWorkspace.getByText("Export remains unavailable.")).toBeVisible();
  await expect(validationWorkspace.getByText("CLIENT CAPABILITY")).toBeVisible();
  await expect(validationWorkspace.getByText("CONTENT POLICY")).toBeVisible();
  await expect(validationWorkspace.getByText("REVIEW", { exact: true })).toBeVisible();
  await inspect("validation-summary");
  await validationWorkspace
    .getByRole("button", { name: "Load computed rules", exact: true })
    .click();
  await expect(validationWorkspace.getByText("alpha", { exact: true })).toBeVisible();
  await expect(validationWorkspace.getByText("beta", { exact: true })).toBeVisible();
  await inspect("validation-rules");
  await page.getByRole("button", { name: "Back to plan", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current plan context" })).toContainText(
    "revision 4",
  );
  await page.getByRole("button", { name: "Export package", exact: true }).click();
  const exportWorkspace = page.locator(".export-workspace:visible");
  await exportWorkspace.getByRole("button", { name: "Check readiness", exact: true }).click();
  await expect(
    exportWorkspace.getByText("3 required checks block package generation."),
  ).toBeVisible();
  await expect(
    exportWorkspace.getByRole("button", { name: "Download package candidate", exact: true }),
  ).toBeDisabled();
  await expect(exportWorkspace.getByText(/Run SQL/i)).toHaveCount(0);
  await inspect("export-blocked");
  await page.getByRole("button", { name: "Back to plan", exact: true }).click();
  await settled();
  await page.getByRole("button", { name: "Log out", exact: true }).click();
  await expect(page.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
  expect((await page.request.get("http://127.0.0.1:18446/control/checks")).ok()).toBe(true);
});
