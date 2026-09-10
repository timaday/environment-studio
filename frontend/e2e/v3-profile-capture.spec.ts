import { expect, test } from "@playwright/test";

// Real browser session/HTTP/SQLite prerequisite. No rendered capture view or API interception.
// Independently invented workflow fixtures only; reports and credentials stay in private RAM.
test("captures value-free structure and separately saves an owned durable profile draft", async ({
  page,
}) => {
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
      const index = mappings.length + 1;
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
  expect(await request("PUT", profilePath, command)).toEqual(saved);
  expect((await request("GET", "/api/v3/profiles")).profiles).toHaveLength(1);
  await settled();
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
  await login();
  expect(await request("GET", profilePath)).toEqual(saved);
  expect(await request("PUT", profilePath, command)).toEqual(saved);
  await settled();
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
  expect((await page.request.get("http://127.0.0.1:18446/control/operator")).ok()).toBe(true);
  await login();
  await request("GET", profilePath, undefined, 404);
  expect((await request("GET", "/api/v3/profiles")).profiles).toEqual([]);
  expect(
    await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length })),
  ).toEqual({ local: 0, session: 0 });
  await settled();
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page.getByRole("link", { name: "Sign in with OIDC" })).toBeVisible();
  expect((await page.request.get("http://127.0.0.1:18446/control/checks")).ok()).toBe(true);
});
