import { mkdir } from "node:fs/promises";
import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// Independent two-document expectations from V3PlanDocumentHttpBoundaryTest.
const sheet =
  "<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x' next='two' secret='MOCK-DOC-SECRET' optional=''><prop key='extra' value='al&#112;ha'/><prop key='other' value='alpha'/></item><unmapped sample='alpha'/></items>";
const target =
  "<items><!-- mock -->\r\n<item id='one' tone='beta' finish='x' next='two' secret='MOCK-DOC-SECRET' optional=''><prop key='extra' value='gamma &amp; 𐀀'/><prop key='other' value='alpha'/></item><unmapped sample='alpha'/></items>";
const tail =
  "<items><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x' next='two'/></items>";

test("resumes actual v3 current and target documents with explicit disclosure", async ({
  page,
}, info) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Sign in with OIDC" }).click();
  await page.getByRole("combobox", { name: "Model version" }).selectOption("3");
  await expect(page.getByText("No current Native v3 plan in this session.")).toBeVisible();
  const capture = process.env.ES_PLANS_CAPTURE_DIR;
  if (capture) {
    if (!/^\/home\/tim\/\.tmp\/es-v3-plan-browser[0-9]+-20260910\/captures$/.test(capture))
      throw new Error("INVALID_CAPTURE_DIRECTORY");
    await mkdir(capture, { recursive: true });
    // Only capture no-plan state; populated test XML and credentials remain external/RAM.
    await page.screenshot({ path: `${capture}/${info.project.name}.png`, fullPage: true });
  }
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  const controls = page.getByRole("region", { name: "Native v3 plan inspection" });
  const sizes = await controls
    .locator("button, select, .v3-plan-consent")
    .evaluateAll((items) =>
      items.map((item) => ({
        width: item.getBoundingClientRect().width,
        height: item.getBoundingClientRect().height,
      })),
    );
  expect(sizes.every(({ width, height }) => width >= 44 && height >= 44)).toBe(true);
  console.log(
    `UI_PROBE_CONTROLS ${JSON.stringify({ count: sizes.length, minHeight: Math.min(...sizes.map((s) => s.height)) })}`,
  );
  const settled = async () =>
    expect((await page.request.get("http://127.0.0.1:18444/control/settled")).ok()).toBe(true);
  async function send(path: string, body: unknown, status = 200) {
    await settled();
    const response = await page.evaluate(
      async ({ path, body }) => {
        const session = await (await fetch("/api/v1/session")).json();
        const result = await fetch(path, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            [session.csrfHeaderName]: session.csrfToken,
          },
          body: JSON.stringify(body),
        });
        const text = await result.text();
        return { status: result.status, value: text ? JSON.parse(text) : null };
      },
      { path, body },
    );
    expect(response.status).toBe(status);
    return response.value;
  }
  const created = await send(
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
  const reserved = await send(
    `${planPath}/inspections`,
    { expectedRevision: "1", requestId: crypto.randomUUID(), discardDraftOnSuccess: true },
    202,
  );
  const observed = await send(`/api/v3/operations/${reserved.operationId}/credentials`, {
    username: "MockV3Reader",
    password: "MockV3-Password-𐀀",
  });
  expect(observed.phase).toBe("succeeded");
  expect(observed.installedRevision).toBe("2");
  await settled();
  await page.getByRole("button", { name: "Resume current plan / refresh" }).click();
  await expect(page.getByText("Observation valid · Target incomplete")).toBeVisible();
  await page.getByRole("combobox", { name: "Document", exact: true }).selectOption("sheet");
  await expect(page.getByRole("button", { name: "Load document comparison" })).toBeDisabled();
  const consent = page.getByRole("checkbox", {
    name: "I understand complete documents may include unmapped or sensitive values.",
  });
  await consent.check();
  await page.getByRole("button", { name: "Load document comparison" }).click();
  expect(await page.getByRole("region", { name: "Current XML" }).locator("pre").textContent()).toBe(
    sheet,
  );
  await expect(
    page.getByText("Target unavailable · not evidence of unchanged content."),
  ).toBeVisible();
  const entities = await send(`${planPath}/views/entities`, {
    revision: "2",
    side: "current",
    offset: 0,
    limit: 100,
  });
  const one = entities.items.find(
    (item: { entity: unknown; fields: { fieldId: string; value: string | null }[] }) =>
      item.fields.some((field) => field.fieldId === "id" && field.value === "one"),
  )?.entity;
  expect(one).toBeDefined();
  const fields = {
    id: { kind: "keep-observed" },
    finish: { kind: "keep-observed" },
    secret: { kind: "keep-observed" },
    optional: { kind: "keep-observed" },
    tone: { kind: "entered", text: "beta" },
    extra: { kind: "entered", text: "gamma & 𐀀" },
  };
  const changed = await send(`${planPath}/commands`, {
    expectedRevision: "2",
    requestId: crypto.randomUUID(),
    kind: "upsert-entity",
    decision: {
      kind: "retain",
      entity: one,
      fields,
      references: { link: { kind: "keep-observed" } },
    },
    placements: [],
  });
  expect(changed.revision).toBe("3");
  await settled();
  await page.getByRole("button", { name: "Resume current plan / refresh" }).click();
  await expect(page.getByText("Observation valid · Target complete")).toBeVisible();
  await expect(
    page.getByText("2 documents · 1 changed · 0 unknown", { exact: true }),
  ).toBeVisible();
  await expect(consent).not.toBeChecked();
  for (const [name, original, proposed] of [
    ["sheet", sheet, target],
    ["tail", tail, tail],
  ]) {
    await page.getByRole("combobox", { name: "Document", exact: true }).selectOption(name);
    await expect(consent).not.toBeChecked();
    await consent.check();
    await settled();
    await page.getByRole("button", { name: "Load document comparison" }).click();
    await expect(page.getByRole("region", { name: "Target XML" }).locator("pre")).toBeVisible();
    expect(
      await page.getByRole("region", { name: "Current XML" }).locator("pre").textContent(),
    ).toBe(original);
    expect(
      await page.getByRole("region", { name: "Target XML" }).locator("pre").textContent(),
    ).toBe(proposed);
  }
  await page.getByRole("button", { name: "Formatted", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current XML" }).locator("pre")).toHaveCount(0);
  await settled();
  await page.getByRole("button", { name: "Load document comparison" }).click();
  await expect(page.getByRole("region", { name: "Target XML" }).locator("pre")).toBeVisible();
  await expect(page.getByRole("region", { name: "Current XML" })).toContainText(
    "Display projection only",
  );
  await page.getByRole("region", { name: "Current XML" }).locator("pre").focus();
  await expect(page.getByRole("region", { name: "Current XML" }).locator("pre")).toBeFocused();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  if (info.project.name === "narrow") {
    await page.setViewportSize({ width: 320, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await expect(page.getByRole("button", { name: "Load document comparison" })).toBeVisible();
    await expect(page.getByRole("region", { name: "Target XML" })).toBeVisible();
    console.log("UI_PROBE_REFLOW_320 passed");
  }
  await page.getByRole("combobox", { name: "Document", exact: true }).selectOption("");
  await expect(consent).not.toBeChecked();
  await expect(consent).toBeDisabled();
  await expect(page.getByRole("region", { name: "Current XML" }).locator("pre")).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Target XML" }).locator("pre")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Load document comparison" })).toBeDisabled();
  expect(
    await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length })),
  ).toEqual({ local: 0, session: 0 });
  await settled();
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Current XML" })).toHaveCount(0);
  await page.getByRole("link", { name: "Sign in with OIDC" }).click();
  await page.getByRole("combobox", { name: "Model version" }).selectOption("3");
  await expect(page.getByText("No current Native v3 plan in this session.")).toBeVisible();
  expect((await page.request.get("http://127.0.0.1:18444/control/checks")).ok()).toBe(true);
});
