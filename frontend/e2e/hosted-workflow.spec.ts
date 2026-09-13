import { readFile } from "node:fs/promises";
import AxeBuilder from "@axe-core/playwright";
import { expect, type Page, test } from "@playwright/test";

async function loseFirstAcknowledgement(
  page: Page,
  path: string,
  method: string,
  forbiddenAfterCommit = false,
) {
  const requests: string[] = [];
  await page.route(path, async (route) => {
    if (route.request().method() !== method) return route.continue();
    requests.push(route.request().postData() ?? "");
    if (requests.length === 1) {
      const response = await route.fetch();
      expect(response.ok()).toBe(true);
      // Real server operation has succeeded; inject only the lost acknowledgement.
      if (forbiddenAfterCommit)
        await route.fulfill({
          status: 403,
          contentType: "application/json",
          body: JSON.stringify({ code: "WORKSPACE_FORBIDDEN" }),
        });
      else await route.abort("failed");
    } else await route.continue();
  });
  return requests;
}

// Actual HTTPS/OIDC/session/workspace/plan adapters. Only capability enablement is
// overridden; the database observation is the explicit independently invented port.
test("maintainer saves, publishes, inspects and reviews an owned session plan", async ({
  page,
  request,
}) => {
  await request.get("http://127.0.0.1:18444/control/reset-clock");
  await request.get("http://127.0.0.1:18444/control/maintainer");
  await page.route("**/api/v1/capabilities", async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    await route.fulfill({
      response,
      json: { ...body, inspectionEnabled: true, inspectionUiEnabled: true },
    });
  });
  await page.goto("/");
  await page.getByRole("link", { name: "Sign in with OIDC" }).click();
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();
  await page.getByRole("button", { name: "Definitions", exact: true }).click();
  const invented = JSON.parse(await readFile("../fixtures/native-v2/definition.json", "utf8"));
  invented.logical.entityTypes[0].label = "Browser-Source-Canary";
  const source = JSON.stringify(invented);
  await page.getByLabel("Native definition source", { exact: true }).fill(source);
  const saves = await loseFirstAcknowledgement(page, "**/api/v2/definitions/*", "PUT", true);
  await page.getByRole("button", { name: "Save immutable draft" }).click();
  await expect(page.getByRole("button", { name: "Retry original command" })).toBeVisible();
  await page.getByRole("button", { name: "Plans", exact: true }).click();
  await page.getByRole("button", { name: "Definitions", exact: true }).click();
  await expect(page.getByRole("button", { name: "Save immutable draft" })).toBeDisabled();
  await page.getByRole("button", { name: "Retry original command" }).click();
  await expect(page.getByText(/workspace revision 1 · draft/)).toBeVisible();
  const publishedObject = await page
    .getByText(/workspace revision 1 · draft/)
    .locator("code")
    .innerText();
  expect(saves).toHaveLength(2);
  expect(saves[1]).toBe(saves[0]);
  const model = page.getByRole("tab", { name: "Model", exact: true });
  await model.focus();
  await page.keyboard.press("ArrowRight");
  await expect(page.getByRole("tab", { name: "Source", exact: true })).toBeFocused();
  await expect(page.getByLabel(/export policy/)).toHaveCount(4);
  for (const policy of await page.getByLabel(/export policy/).all())
    await policy.selectOption("deny");
  await page
    .getByLabel("I reviewed complete-document disclosure and every document policy.")
    .check();
  const publications = await loseFirstAcknowledgement(
    page,
    "**/api/v2/definitions/*/publish",
    "POST",
    true,
  );
  await page.getByRole("button", { name: "Publish definition", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry original command" })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Publish definition", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "Retry original command" }).click();
  expect(publications).toHaveLength(2);
  expect(publications[1]).toBe(publications[0]);
  await expect(page.getByText(/workspace revision 2 · published/)).toBeVisible();
  await page.getByRole("button", { name: "Plans", exact: true }).click();
  await page.getByLabel("Published definition", { exact: true }).selectOption(publishedObject);
  await page.getByLabel("Binding", { exact: true }).selectOption("mock-pg");
  await page.getByLabel("Configured destination", { exact: true }).selectOption("mock-destination");
  const creates = await loseFirstAcknowledgement(page, "**/api/v1/plans", "POST");
  await page.getByRole("button", { name: "Create plan", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry original plan command" })).toBeVisible();
  await page.getByRole("button", { name: "Definitions", exact: true }).click();
  await page.getByRole("button", { name: "Plans", exact: true }).click();
  await expect(page.getByRole("button", { name: "Create plan", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Retry original plan command" }).click();
  await expect(page.getByRole("region", { name: "Persistent plan context" })).toContainText(
    "Revision 1",
  );
  expect(creates).toHaveLength(2);
  expect(creates[1]).toBe(creates[0]);
  await page
    .getByLabel(
      "Successful inspection may replace current configuration and discard target changes.",
    )
    .check();
  const reservations = await loseFirstAcknowledgement(
    page,
    "**/api/v1/plans/*/inspections",
    "POST",
  );
  await page.getByRole("button", { name: "Reserve inspection" }).click();
  await expect(page.getByRole("button", { name: "Retry original reservation" })).toBeVisible();
  await expect(page.getByLabel("Database password")).toHaveCount(0);
  await page.getByRole("button", { name: "Definitions", exact: true }).click();
  await page.getByRole("button", { name: "Plans", exact: true }).click();
  await page.getByRole("button", { name: "Retry original reservation" }).click();
  expect(reservations).toHaveLength(2);
  expect(reservations[1]).toBe(reservations[0]);
  await page.getByLabel("Database password").fill("Mock-Navigation-Canary");
  await page.getByRole("button", { name: "Definitions", exact: true }).click();
  await expect(page.locator('input[name="password"]')).toHaveCount(0);
  await page.getByRole("button", { name: "Plans", exact: true }).click();
  await expect(page.getByLabel("Database password")).toHaveValue("");
  await page.getByLabel("Database username").fill("MockReader");
  await page.getByLabel("Database password").fill("Db-Password-Canary-𐀀");
  const inventories = await loseFirstAcknowledgement(
    page,
    "**/api/v1/plans/*/views/documents",
    "POST",
  );
  const inspected = page.waitForResponse(
    (response) => response.url().endsWith("/credentials") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Send credentials once" }).click();
  const observation = await inspected;
  expect(observation.status()).toBe(200);
  expect(await observation.json()).toMatchObject({ phase: "succeeded", cleanup: "complete" });
  await expect(page.getByLabel("Database password")).toHaveCount(0);
  await expect(page.getByRole("alert")).toContainText("NETWORK_UNCERTAIN");
  await page.getByRole("button", { name: "Reload document inventory" }).click();
  await expect(page.getByText(/2 documents · 0 changed · 0 unknown/)).toBeVisible();
  expect(inventories).toHaveLength(2);
  expect(inventories[1]).toBe(inventories[0]);
  await expect(page.getByRole("button", { name: "Placeholders", exact: true })).toBeDisabled();
  await page
    .getByLabel(
      "Show the complete selected document, including unchanged or unmapped content and readable secrets.",
    )
    .check();
  await page.getByRole("button", { name: "Load document comparison" }).click();
  await expect(page.getByRole("region", { name: "Current XML" })).toContainText("alpha");
  await expect(page.getByRole("region", { name: "Target XML" })).toContainText("alpha");
  await page.getByRole("button", { name: "Formatted", exact: true }).click();
  await page.getByRole("button", { name: "Load document comparison" }).click();
  await expect(page.getByRole("region", { name: "Target XML" })).toContainText(
    "Display projection only",
  );
  expect(
    await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length })),
  ).toEqual({ local: 0, session: 0 });
  // Clear document disclosure before accessibility/viewport inspection. No raw-input artifacts.
  await page
    .getByLabel(
      "Show the complete selected document, including unchanged or unmapped content and readable secrets.",
    )
    .uncheck();
  const accessibility = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
    .analyze();
  expect(accessibility.violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await request.get("http://127.0.0.1:18444/control/expire");
  await page.getByRole("button", { name: "Resume current plan / refresh" }).click();
  await expect(page.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Current XML" })).toHaveCount(0);
  const checks = await request.get("http://127.0.0.1:18444/control/checks");
  expect(checks.status()).toBe(200);
});

test("operator can save an owned draft but cannot publish through UI or HTTP", async ({
  page,
  request,
}) => {
  await request.get("http://127.0.0.1:18444/control/reset-clock");
  await request.get("http://127.0.0.1:18444/control/operator");
  await page.goto("/");
  await page.getByRole("link", { name: "Sign in with OIDC" }).click();
  await page.getByRole("button", { name: "Definitions", exact: true }).click();
  await page
    .getByLabel("Native definition source", { exact: true })
    .fill(await readFile("../fixtures/native-v2/definition.json", "utf8"));
  await page.getByRole("button", { name: "Save immutable draft" }).click();
  await expect(page.getByText(/workspace revision 1 · draft/)).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Publish definition", exact: true }),
  ).toBeDisabled();
  const refusal = await page.evaluate(async () => {
    const session = await (await fetch("/api/v1/session", { cache: "no-store" })).json();
    const list = await (await fetch("/api/v2/definitions", { cache: "no-store" })).json();
    const owned = list.definitions[0];
    const response = await fetch(`/api/v2/definitions/${owned.objectId}/publish`, {
      method: "POST",
      cache: "no-store",
      headers: { "Content-Type": "application/json", [session.csrfHeaderName]: session.csrfToken },
      body: JSON.stringify({
        expectedRevision: owned.workspaceRevision,
        requestId: crypto.randomUUID(),
        exportPolicies: [],
        completeDocumentDisclosure: true,
      }),
    });
    return { status: response.status, code: (await response.json()).code };
  });
  expect(refusal).toEqual({ status: 403, code: "WORKSPACE_FORBIDDEN" });
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
  await expect(page.getByLabel("Native definition source", { exact: true })).toHaveCount(0);
});
