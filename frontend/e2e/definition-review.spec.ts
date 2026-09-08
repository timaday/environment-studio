import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// Exercises independently invented, explicitly labelled demo data only.
test("operator can review definition source and blockers using the keyboard", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Export SQL" })).toBeDisabled();
  const definitions = page.getByRole("button", { name: "Definitions", exact: true });
  await definitions.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { name: "Inspect a definition" })).toBeVisible();
  await expect(page.getByText("Synthetic definition preview", { exact: true })).toBeVisible();
  const model = page.getByRole("tab", { name: "Model", exact: true });
  await model.focus();
  await page.keyboard.press("ArrowRight");
  await expect(page.getByRole("tab", { name: "Source", exact: true })).toBeFocused();
  await expect(page.getByRole("tabpanel")).toContainText('"schemaVersion"');
  await expect(
    page.getByRole("region", { name: "Compilation and publication blockers" }),
  ).toBeVisible();
  await page.keyboard.press("End");
  await expect(page.getByRole("tab", { name: "Diagnostics", exact: true })).toBeFocused();
  await expect(page.getByRole("tabpanel")).toContainText("revision 1");
  await page.keyboard.press("Home");
  await expect(model).toBeFocused();
  await expect(page.getByRole("tabpanel")).toContainText("Declared types and fields");
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
    .analyze();
  expect(results.violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({ path: test.info().outputPath("definition-review.png"), fullPage: true });
  await page.getByRole("button", { name: "Comparison", exact: true }).click();
  await expect(page.getByRole("button", { name: "Export SQL" })).toBeDisabled();
});
