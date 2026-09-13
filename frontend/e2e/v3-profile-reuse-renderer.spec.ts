import { mkdir } from "node:fs/promises";
import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// Real browser session/HTTP/SQLite prerequisite. Capture uses the actual API.
// Reuse command delivery is deliberately lost after the real server response; retry
// must retain its exact original URL/body. No response payload is fabricated.
// Independently invented workflow fixtures only; reports and credentials stay in private RAM.
test("selects partial reuse, reviews dependencies and applies explicit placement", async ({
  page,
}, info) => {
  await page.goto("/");
  const login = async () => page.getByRole("link", { name: "Sign in with OIDC" }).click();
  await login();
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
  expect((await request("GET", "/api/v3/profiles")).profiles).toEqual([]);
  const created = await request(
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
  );
  const planPath = `/api/v3/plans/${created.planId}`;
  const reserved = await request(
    "POST",
    `${planPath}/inspections`,
    {
      expectedRevision: "1",
      requestId: crypto.randomUUID(),
      discardDraftOnSuccess: true,
    },
    202,
  );
  const observed = await request("POST", `/api/v3/operations/${reserved.operationId}/credentials`, {
    username: "MockV3Reader",
    password: "MockV3-Password-𐀀",
  });
  expect(observed.phase).toBe("succeeded");
  expect(observed.installedRevision).toBe("2");
  let before = await request("GET", planPath);
  const mappings: { entity: unknown; slotId: string; label: string }[] = [];
  let offset: number | null = 0;
  while (offset !== null) {
    const inventory = await request("POST", `${planPath}/views/entities`, {
      revision: "2",
      side: "current",
      offset,
      limit: 2,
    });
    expect(inventory.total).toBe(3);
    for (const item of inventory.items) {
      const identity = item.fields.find(
        (field: { fieldId: string }) => field.fieldId === "id",
      )?.value;
      const index = ["one", "two", "three"].indexOf(identity) + 1;
      expect(index).toBeGreaterThan(0);
      mappings.push({ entity: item.entity, slotId: `slot-${index}`, label: `Neutral ${index}` });
    }
    offset = inventory.nextOffset;
    expect(mappings.length).toBeLessThanOrEqual(3);
  }
  expect(mappings).toHaveLength(3);
  const captured = await request("POST", `${planPath}/profile-captures`, {
    revision: "2",
    profileId: "mock-profile",
    profileRevision: "1",
    mappings,
  });
  expect(captured.format).toBe("json");
  expect(captured.definition).toEqual({
    objectId: "00000000-0000-4000-8000-000000000919",
    workspaceRevision: "2",
  });
  expect(captured.definition).toEqual(before.definition);
  const source = JSON.parse(captured.source);
  expect(source.schemaVersion).toBe("3");
  expect(source.entities).toHaveLength(3);
  for (const value of ['"alpha"', '"beta"', "MOCK-DOC-SECRET", "MockV3-Password"]) {
    expect(captured.source.includes(value)).toBe(false);
  }
  expect((await request("GET", "/api/v3/profiles")).profiles).toEqual([]);
  expect(await request("GET", planPath)).toEqual(before);
  const objectId = crypto.randomUUID();
  const profilePath = `/api/v3/profiles/${objectId}`;
  const command = {
    expectedRevision: "0",
    requestId: crypto.randomUUID(),
    format: "JSON",
    source: captured.source,
    definition: captured.definition,
  };
  const saved = await request("PUT", profilePath, command);
  expect(saved.objectId).toBe(objectId);
  expect(saved.workspaceRevision).toBe("1");
  expect(saved.state).toBe("draft");
  expect(saved.source).toBe(captured.source);
  expect(saved.definition).toEqual(captured.definition);
  expect(await request("GET", `${profilePath}/revisions/1`)).toEqual(saved);
  expect(
    (await page.request.post(`http://127.0.0.1:18446/control/reuse/${objectId}`)).status(),
  ).toBe(200);
  const published = await request("GET", `${profilePath}/revisions/2`);
  expect(published.state).toBe("published");
  expect(published.source).toBe(captured.source);
  await page.getByRole("combobox", { name: "Model version" }).selectOption("3");
  await expect(page.getByRole("region", { name: "Current plan context" })).toContainText(
    created.planId,
  );
  await page.getByRole("button", { name: "Reuse profile", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Prepare the target preview" })).toBeVisible();
  await page.getByRole("button", { name: "Prepare target preview" }).click();
  await expect(
    page.getByRole("button", { name: "Load profiles and inspected items" }),
  ).toBeVisible();
  before = await request("GET", planPath);
  expect(before.targetComplete).toBe(true);
  await page.getByRole("button", { name: "Load profiles and inspected items" }).click();
  await page.getByRole("button", { name: /mock-profile.*Saved revision 2/ }).click();
  await page.getByRole("radio", { name: "Selected parts" }).check();
  await page.getByRole("checkbox", { name: /Neutral 1/ }).check();
  async function inspect(state: string) {
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    const undersized = await page.locator(".reuse-workspace").evaluate((workspace) =>
      [...workspace.querySelectorAll<HTMLElement>("button, input, select, summary")]
        .filter((element) => element.getClientRects().length > 0)
        .map((element) => {
          const target = element.matches('input[type="checkbox"], input[type="radio"]')
            ? (element.closest("label") ?? element)
            : element;
          const rect = target.getBoundingClientRect();
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
    const output = process.env.ES_REUSE_RENDERER_IMAGES;
    if (output) {
      if (
        !/^\/home\/tim\/\.tmp\/es-v3-reuse-renderer-browser[0-9]+-20260911\/captures$/.test(output)
      )
        throw new Error("INVALID_CAPTURE_DIRECTORY");
      await mkdir(output, { recursive: true });
      await expect(page.locator(".capture-session")).not.toHaveAttribute("open");
      await page.screenshot({
        path: `${output}/${info.project.name}-${state}.png`,
        fullPage: true,
      });
    }
  }
  await inspect("selection");
  await page.getByRole("button", { name: "Preview selection" }).focus();
  await expect(page.getByRole("button", { name: "Preview selection" })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByText("1 selected · 1 required dependency · 2 included")).toBeVisible();
  await expect(page.getByRole("button", { name: "Apply to target plan" })).toBeDisabled();
  const current = await request("POST", `${planPath}/views/entities`, {
    revision: "2",
    side: "current",
    offset: 0,
    limit: 100,
  });
  await page.getByLabel("Action for Neutral 1", { exact: true }).selectOption("create");
  await expect(page.getByLabel("Target identifier for Neutral 1", { exact: true })).toHaveValue("");
  await expect(page.getByRole("button", { name: "Apply to target plan" })).toBeDisabled();
  await inspect("create");
  for (const [slot, identity] of [
    [1, "one"],
    [2, "two"],
  ] as const) {
    const item = current.items.find((row: { fields: { fieldId: string; value: unknown }[] }) =>
      row.fields.some((field) => field.fieldId === "id" && field.value === identity),
    );
    expect(item).toBeDefined();
    await page
      .getByLabel(`Action for Neutral ${slot}`, { exact: true })
      .selectOption("use-existing");
    await page
      .getByLabel(`Current item for Neutral ${slot}`, { exact: true })
      .selectOption(item.entity.handle);
  }
  expect(await request("GET", planPath)).toEqual(before);
  await inspect("placement");
  let attempts = 0;
  let originalBody: string | null = null;
  let originalPath = "";
  let delivered: ((value: { planId: string; revision: string }) => void) | undefined;
  const originalReceipt = new Promise<{ planId: string; revision: string }>((resolve) => {
    delivered = resolve;
  });
  await page.route(/\/api\/v3\/plans\/[0-9a-f-]+\/commands$/, async (route) => {
    attempts++;
    if (attempts === 1) {
      originalBody = route.request().postData();
      originalPath = route.request().url();
      const result = await route.fetch({ maxRetries: 0 });
      expect(result.status()).toBe(200);
      const receipt = await result.json();
      await route.abort("failed");
      if (!delivered) throw new Error("Original receipt observer missing");
      delivered(receipt);
    } else {
      expect(route.request().url()).toBe(originalPath);
      expect(route.request().postData()).toBe(originalBody);
      await route.continue();
    }
  });
  await page.getByRole("button", { name: "Apply to target plan" }).click();
  const receipt = await originalReceipt;
  expect(receipt.revision).toBe("3");
  await expect(page.getByRole("alert")).toContainText("The response was unavailable.");
  await inspect("recovery");
  await expect(page.getByRole("heading", { name: "Plan updated", exact: true })).toHaveCount(0);
  expect((await request("GET", planPath)).revision).toBe("3");
  console.log("MOCK_REUSE_LOSS", "original-status200 revision3 delivered-failed no-success-ui");
  await expect(page.getByRole("button", { name: "Retry apply", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Retry apply", exact: true }).click();
  await expect.poll(() => attempts).toBe(2);
  await expect(
    page.getByRole("heading", { name: "Reuse applied to plan revision 3" }),
  ).toBeVisible();
  await expect(page.getByText("Target incomplete", { exact: true })).toBeVisible();
  await expect(page.getByText("Export unavailable", { exact: true })).toBeVisible();
  const result = await request("GET", planPath);
  expect(result.revision).toBe("3");
  expect(result.targetComplete).toBe(false);
  expect(result.exportAvailable).toBe(false);
  await expect(
    page.getByRole("region", { name: "Applied placement" }).getByText("Neutral 3", { exact: true }),
  ).toHaveCount(0);
  await inspect("applied");
  await page.getByRole("button", { name: "Back to plan", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current plan context" })).toContainText(
    "revision 3",
  );
  await settled();
  await page.getByRole("button", { name: "Log out", exact: true }).click();
  await expect(page.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
  expect((await page.request.get("http://127.0.0.1:18446/control/checks")).ok()).toBe(true);
});
