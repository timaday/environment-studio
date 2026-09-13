import { mkdir } from "node:fs/promises";
import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// Actual original PUT with response delivery loss; explicit UI retry, no payload substitution.
// Independently invented workflow fixtures only; reports and credentials stay in private RAM.
test("maps returned inventory, captures separately and confirms a lost save through explicit retry", async ({
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
  const before = await request("GET", planPath);

  await page.getByRole("combobox", { name: "Model version" }).selectOption("3");
  await expect(page.getByRole("region", { name: "Current plan context" })).toContainText(
    created.planId,
  );
  await page.getByRole("button", { name: "Capture profile" }).click();
  await page.getByRole("button", { name: "Load inspected structure" }).click();
  await expect(page.getByText("0 of 3 mappings entered")).toBeVisible();
  await expect(page.getByRole("button", { name: "Capture for review" })).toBeDisabled();
  await page.getByLabel("Profile identifier", { exact: true }).fill("mock-profile");
  await page.getByLabel("Profile revision", { exact: true }).fill("1");
  for (let number = 1; number <= 3; number++) {
    if (info.project.name === "narrow" && number > 1)
      await page.getByText(`Inspected item ${number} · item`, { exact: true }).click();
    await page
      .getByRole("textbox", { name: `Reusable identifier ${number}`, exact: true })
      .fill(`slot-${number}`);
    await page
      .getByRole("textbox", { name: `Label ${number}`, exact: true })
      .fill(`Neutral ${number}`);
    if (info.project.name === "narrow" && number > 1)
      await page
        .locator(".capture-row-toggle")
        .nth(number - 1)
        .click();
  }
  await expect(page.getByText("3 of 3 mappings entered")).toBeVisible();
  async function inspect(state: string) {
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    const sizes = await page
      .locator(
        ".capture-workspace button:visible, .capture-workspace input:visible, .capture-workspace summary:visible, .capture-menu:visible, .capture-session summary:visible",
      )
      .evaluateAll((items) =>
        items.map((item) => ({
          kind: item.tagName,
          className: item.className,
          width: item.getBoundingClientRect().width,
          height: item.getBoundingClientRect().height,
        })),
      );
    expect(sizes.filter(({ width, height }) => width < 44 || height < 44)).toEqual([]);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    const capture = process.env.ES_CAPTURE_RENDERER_IMAGES;
    if (capture) {
      if (
        !/^\/home\/tim\/\.tmp\/es-v3-capture-renderer-browser[0-9]+-20260911\/captures$/.test(
          capture,
        )
      )
        throw new Error("INVALID_CAPTURE_DIRECTORY");
      await mkdir(capture, { recursive: true });
      // Only authorized independently invented profile content; session details remain closed.
      await expect(page.locator(".capture-session")).not.toHaveAttribute("open");
      await page.screenshot({
        path: `${capture}/${info.project.name}-${state}.png`,
        fullPage: true,
      });
    }
  }
  if (info.project.name === "narrow") await page.locator(".capture-row-toggle").nth(1).click();
  await page.getByRole("textbox", { name: "Reusable identifier 2", exact: true }).fill("slot-1");
  await expect(
    page.getByRole("textbox", { name: "Reusable identifier 2", exact: true }),
  ).toHaveAttribute("aria-invalid", "true");
  await expect(
    page.getByRole("textbox", { name: "Reusable identifier 2", exact: true }),
  ).toHaveAccessibleDescription(
    "This identifier is also used by item 1. Enter a unique identifier.",
  );
  if (info.project.name === "narrow") {
    await page.locator(".capture-row-toggle").nth(1).click();
    await expect(page.locator(".capture-row-toggle").nth(1)).toContainText("Duplicate identifier");
  }
  await expect(page.getByRole("alert")).toContainText("2 items share reusable identifiers");
  await expect(page.getByRole("button", { name: "Capture for review" })).toBeDisabled();
  await inspect("duplicate");
  if (info.project.name === "narrow") await page.locator(".capture-row-toggle").nth(1).click();
  await page.getByRole("textbox", { name: "Reusable identifier 2", exact: true }).fill("slot-2");
  await expect(page.getByRole("alert")).toHaveCount(0);
  if (info.project.name === "narrow") await page.locator(".capture-row-toggle").nth(1).click();
  await inspect("mapping");
  const captureResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith("/profile-captures") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Capture for review" }).click();
  const captured = await (await captureResponse).json();
  await expect(page.getByText("Captured · Not saved", { exact: true })).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Profile identifier", exact: true })).toHaveCount(
    0,
  );
  expect((await request("GET", "/api/v3/profiles")).profiles).toEqual([]);
  expect(await request("GET", planPath)).toEqual(before);
  for (const forbidden of ["MOCK-DOC-SECRET", "MockV3-Password", '"alpha"', '"beta"'])
    expect(captured.source.includes(forbidden)).toBe(false);

  await inspect("review");
  type Saved = { state: string; workspaceRevision: string; source: string };
  let resolveDelivery:
    | ((value: { saved: Saved; originalCommand: unknown; originalPath: string }) => void)
    | undefined;
  const delivered = new Promise<{ saved: Saved; originalCommand: unknown; originalPath: string }>(
    (resolve) => {
      resolveDelivery = resolve;
    },
  );
  await page.route(/\/api\/v3\/profiles\/[0-9a-f-]+$/, async (route) => {
    if (route.request().method() !== "PUT") return route.continue();
    const originalCommand = route.request().postDataJSON();
    const originalPath = new URL(route.request().url()).pathname;
    // Real original PUT reaches the actual fixture server; only delivery is lost.
    const response = await route.fetch({ maxRetries: 0 });
    expect(response.status()).toBe(200);
    const saved: Saved = await response.json();
    await route.abort("failed");
    if (!resolveDelivery) throw new Error("Delivery observer unavailable");
    resolveDelivery({ saved, originalCommand, originalPath });
  });
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  const { saved, originalCommand, originalPath } = await delivered;
  await expect(page.getByRole("heading", { name: "Save not confirmed" })).toBeVisible();
  await expect(page.getByText("Captured · Not saved", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Edit mappings" })).toHaveCount(0);
  await expect(page.getByRole("alert")).toHaveCount(1);
  expect(saved.state).toBe("draft");
  expect(saved.workspaceRevision).toBe("1");
  expect(saved.source).toBe(captured.source);
  expect(await request("GET", `${originalPath}/revisions/1`)).toEqual(saved);
  await page.getByRole("button", { name: "Back to plan", exact: true }).click();
  await page.getByRole("button", { name: "Capture profile", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Save not confirmed" })).toBeVisible();
  await expect(page.getByText("Neutral 1", { exact: true })).toBeVisible();
  await inspect("recovery");
  // Check reflow at 320 CSS pixels without claiming real browser zoom was measured.
  const viewport = page.viewportSize();
  await page.setViewportSize({ width: 320, height: 844 });
  await inspect("recovery-reflow320");
  if (viewport) await page.setViewportSize(viewport);
  async function tabTo(target: ReturnType<typeof page.getByRole>) {
    for (let step = 0; step < 80; step++) {
      if (await target.evaluate((element) => element === document.activeElement)) return;
      await page.keyboard.press("Tab");
    }
    throw new Error("KEYBOARD_TARGET_UNREACHABLE");
  }
  const sourceToggle = page.getByText("View captured source (JSON)", { exact: true });
  await tabTo(sourceToggle);
  await page.keyboard.press("Enter");
  await expect(sourceToggle.locator("..")).toHaveAttribute("open");
  await page.keyboard.press("Enter");
  await expect(sourceToggle.locator("..")).not.toHaveAttribute("open");
  await page.unroute(/\/api\/v3\/profiles\/[0-9a-f-]+$/);
  const retry = page.getByRole("button", { name: "Retry save", exact: true });
  await tabTo(retry);
  const focus = await retry.evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      visible: element.matches(":focus-visible"),
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
    };
  });
  expect(focus.visible).toBe(true);
  expect(focus.outlineStyle).not.toBe("none");
  expect(parseFloat(focus.outlineWidth)).toBeGreaterThan(0);
  await settled();
  const replayResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === originalPath && response.request().method() === "PUT",
  );
  await page.keyboard.press("Enter");
  const response = await replayResponse;
  expect(response.request().postDataJSON()).toEqual(originalCommand);
  expect(response.status()).toBe(200);
  expect(await response.json()).toEqual(saved);
  await expect(page.getByRole("heading", { name: "Draft saved", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Save not confirmed" })).toHaveCount(0);
  expect(await request("GET", `${originalPath}/revisions/1`)).toEqual(saved);
  await inspect("saved");
  await settled();
  // Actual logout closes the owner after UI-confirmed exact replay.
  await page.getByRole("button", { name: "Back to plan", exact: true }).click();
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
  expect((await page.request.get("http://127.0.0.1:18446/control/checks")).ok()).toBe(true);
});
