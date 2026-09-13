import { mkdir, readFile, writeFile } from "node:fs/promises";
import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// Actual schema3 HTTPS/OIDC/private workspace. Only invented source enters these tests.
test("uploads, saves and inspects a v3 definition with exact uncertain replay", async ({
  page,
}, info) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Sign in with OIDC" }).click();
  await page.getByRole("button", { name: "Definitions", exact: true }).click();
  await page.getByRole("combobox", { name: "Model version" }).selectOption("3");
  await expect(page.getByText("No saved definitions for this model version.")).toBeVisible();
  const area = page.getByRole("region", { name: "Native v3 definitions" });
  await expect(area.getByRole("button", { name: "Upload definition" })).toBeVisible();
  await expect(area.getByRole("button", { name: "Save draft", exact: true })).toBeDisabled();
  const capture = process.env.ES_DEFINITIONS_CAPTURE_DIR;
  if (capture) {
    if (!/^\/home\/tim\/\.tmp\/es-definitions-ui-browser[0-9]+-20260910\/captures$/.test(capture))
      throw new Error("INVALID_CAPTURE_DIRECTORY");
    await mkdir(capture, { recursive: true });
    // Capture only the empty workspace: never test source, uploaded files or credentials.
    await page.screenshot({ path: `${capture}/${info.project.name}.png`, fullPage: true });
  }
  const accessibility = (await new AxeBuilder({ page }).analyze()).violations;
  if (capture)
    await writeFile(
      `${capture}/${info.project.name}-accessibility.json`,
      JSON.stringify(
        accessibility.map((v) => ({
          id: v.id,
          impact: v.impact,
          nodes: v.nodes.map((n) => ({ target: n.target, summary: n.failureSummary })),
        })),
        null,
        2,
      ),
    );
  expect(accessibility).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  const targetSizes = await area.locator("button:visible, select:visible").evaluateAll((elements) =>
    elements.map((element) => {
      const rect = element.getBoundingClientRect();
      return { width: rect.width, height: rect.height };
    }),
  );
  expect(targetSizes.every((size) => size.width >= 44 && size.height >= 44)).toBe(true);
  if (info.project.name === "narrow") {
    await page.setViewportSize({ width: 320, height: 844 });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page.setViewportSize({ width: 390, height: 844 });
  }
  const source = `${await readFile("../fixtures/native-v3/definition.json", "utf8")}\r\n`;
  // Actual unmodified semantic422, followed by a corrected upload and save.
  const malformed = "{ Browser-Source-Canary independently-invented invalid JSON";
  const invalidPicker = page.waitForEvent("filechooser");
  await area.getByRole("button", { name: "Upload definition" }).click();
  await (await invalidPicker).setFiles({
    name: "invalid.json",
    mimeType: "application/json",
    buffer: Buffer.from(malformed),
  });
  await expect(area.getByRole("textbox", { name: "Source", exact: true })).toHaveValue(malformed);
  const rejectedResponse = page.waitForResponse(
    (response) =>
      response.request().method() === "PUT" &&
      /\/api\/v3\/definitions\/[^/]+$/.test(new URL(response.url()).pathname),
  );
  await area.getByRole("button", { name: "Save draft", exact: true }).click();
  const rejected = await rejectedResponse;
  expect(rejected.status()).toBe(422);
  expect((await rejected.json()).kind).toBe("rejected");
  await expect(area.getByRole("alert")).toContainText("REJECTED");
  await expect(area.getByRole("textbox", { name: "Source", exact: true })).toBeEnabled();
  await expect(area.getByRole("textbox", { name: "Source", exact: true })).toHaveValue(malformed);
  await expect(area.getByRole("button", { name: "Retry original save" })).toHaveCount(0);
  await expect(area.getByRole("button", { name: "Upload definition" })).toBeEnabled();

  const pickerOpened = page.waitForEvent("filechooser");
  await area.getByRole("button", { name: "Upload definition" }).click();
  const picker = await pickerOpened;
  page.once("dialog", async (dialog) => {
    expect(dialog.type()).toBe("confirm");
    expect(dialog.message()).toBe(
      "Replace the unsaved definition source? Your existing edits will be discarded.",
    );
    await dialog.accept();
  });
  await picker.setFiles({
    name: "invented.json",
    mimeType: "application/json",
    buffer: Buffer.from(source),
  });
  // The previous rejected source is nonempty: wait for the new file, not merely an enabled save.
  await expect(area.getByRole("textbox", { name: "Source", exact: true })).toHaveValue(
    source.replace(/\r\n/g, "\n"),
  );
  await expect(area.getByRole("button", { name: "Save draft", exact: true })).toBeEnabled();
  const commands: string[] = [];
  let objectPath = "";
  await page.route("**/api/v3/definitions/*", async (route) => {
    if (route.request().method() !== "PUT") return route.continue();
    commands.push(route.request().postData() ?? "");
    objectPath = new URL(route.request().url()).pathname;
    if (commands.length === 1) {
      const response = await route.fetch();
      expect(response.ok()).toBe(true);
      await route.fulfill({
        status: 403,
        contentType: "application/json",
        body: JSON.stringify({ code: "FORBIDDEN" }),
      });
    } else await route.continue();
  });
  await area.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(area.getByRole("button", { name: "Retry original save" })).toBeVisible();
  await expect(page.getByRole("combobox", { name: "Model version" })).toBeDisabled();
  await expect(area.getByRole("button", { name: "Upload definition" })).toBeDisabled();
  await page.getByRole("button", { name: "Plans", exact: true }).click();
  await page.getByRole("button", { name: "Definitions", exact: true }).click();
  await area.getByRole("button", { name: "Retry original save" }).click();
  await expect(area.getByText("Saved revision 1 · draft · incomplete")).toBeVisible();
  expect(commands).toHaveLength(2);
  expect(commands[1]).toBe(commands[0]);
  expect(JSON.parse(commands[0]).source).toBe(source);
  const sourceTab = area.getByRole("tab", { name: "Source", exact: true });
  await sourceTab.focus();
  await page.keyboard.press("Enter");
  expect(await area.getByRole("tabpanel").locator("pre").textContent()).toBe(source);
  await page.keyboard.press("ArrowRight");
  await expect(area.getByRole("tab", { name: "Diagnostics" })).toBeFocused();
  await expect(area.getByRole("tab", { name: "Diagnostics" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  await page.keyboard.press("Tab");
  await expect(area.getByRole("tabpanel")).toBeFocused();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  const observed = await page.evaluate(async (path) => {
    const response = await fetch(path);
    if (!response.ok) throw new Error("READBACK_FAILED");
    return response.json();
  }, objectPath);
  expect(observed.source).toBe(source);
  expect(observed.workspaceRevision).toBe("1");
  expect(
    await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length })),
  ).toEqual({ local: 0, session: 0 });
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
  await expect(page.getByRole("textbox", { name: /^Source$/ })).toHaveCount(0);
  await page.getByRole("link", { name: "Sign in with OIDC" }).click();
  await page.getByRole("button", { name: "Definitions", exact: true }).click();
  await page.getByRole("combobox", { name: "Model version" }).selectOption("3");
  await expect(page.getByRole("combobox", { name: "Saved definition" })).toBeEnabled();
  await page
    .getByRole("combobox", { name: "Saved definition" })
    .selectOption(objectPath.split("/").at(-1) ?? "");
  await expect(page.getByText("Saved revision 1 · draft · incomplete")).toBeVisible();
  await area.getByRole("button", { name: "New definition", exact: true }).click();
  // YAML document marker plus JSON flow syntax is valid YAML and not valid JSON.
  const yaml = `---\n${source}`;
  const yamlPickerOpened = page.waitForEvent("filechooser");
  await area.getByRole("button", { name: "Upload definition" }).click();
  await (await yamlPickerOpened).setFiles({
    name: "invented.yaml",
    mimeType: "application/yaml",
    buffer: Buffer.from(yaml),
  });
  await expect(area.getByRole("combobox", { name: "Format", exact: true })).toHaveValue("YAML");
  await area.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(area.getByText("Saved revision 1 · draft · incomplete")).toBeVisible();
  await area.getByRole("tab", { name: "Source", exact: true }).click();
  expect(await area.getByRole("tabpanel").locator("pre").textContent()).toBe(yaml);
  expect(JSON.parse(commands.at(-1) ?? "").format).toBe("YAML");
  const controlPort =
    process.env.ES_HOSTED_BROWSER_MODE === "definitions-v3-refusal" ? 18446 : 18444;
  expect((await page.request.get(`http://127.0.0.1:${controlPort}/control/checks`)).ok()).toBe(
    true,
  );
});
