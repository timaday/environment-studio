import { expect } from "@playwright/test";
import * as D from "../src/api/hostedV3Decoding";
import * as P from "../src/api/hostedV3PhysicalDecoding";

type Request = (method: string, path: string, body?: unknown, status?: number) => Promise<unknown>;

/** Existing independently invented profiles-v3 workflow; actual session HTTP only. */
export async function checkValuesAndValidation(request: Request, planPath: string) {
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
  const before = D.validationSummary(
    await request("POST", `${planPath}/validations`, { revision: "2" }),
  );
  expect(before.targetComplete).toBe(false);
  expect(before.computedRuleCount).toBeNull();
  const entered = {
    kind: "bind-field",
    expectedRevision: "2",
    requestId: crypto.randomUUID(),
    entity: selected.entity,
    fieldId: "tone",
    state: { kind: "entered", text: "beta" },
  };
  // Materialization does not silently select an item for mutation.
  await request("POST", `${planPath}/materializations`, { revision: "2" });
  await request("POST", `${planPath}/commands`, entered, 422);
  expect(D.summary(await request("GET", planPath)).revision).toBe("2");
  const fields = Object.fromEntries(
    ["id", "tone", "finish", "secret", "optional", "extra"].map((id) => [
      id,
      { kind: id === "tone" ? "unresolved" : "keep-observed" },
    ]),
  );
  const retained = D.ack(
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
  );
  expect(retained.revision).toBe("3");
  const pending = P.bindings(
    await request("POST", `${planPath}/views/bindings`, {
      revision: "3",
      entity: selected.entity,
      offset: 0,
      limit: 100,
    }),
  );
  expect(pending.items.find((item) => item.fieldId === "tone")?.target.state).toBe("unresolved");
  const command = { ...entered, expectedRevision: "3", requestId: crypto.randomUUID() };
  const receipt = D.ack(await request("POST", `${planPath}/commands`, command));
  expect(receipt.revision).toBe("4");
  expect(D.ack(await request("POST", `${planPath}/commands`, command))).toEqual(receipt);
  const bindings = P.bindings(
    await request("POST", `${planPath}/views/bindings`, {
      revision: "4",
      entity: selected.entity,
      offset: 0,
      limit: 100,
    }),
  );
  const tone = bindings.items.find((item) => item.fieldId === "tone");
  expect(tone?.current).toEqual({ state: "value", text: "alpha" });
  expect(tone?.target).toEqual({ state: "value", text: "beta" });
  expect(tone?.change).toBe("changed");
  const secret = bindings.items.find((item) => item.fieldId === "secret");
  expect(secret?.current).toEqual({ state: "masked" });
  expect(secret?.target).toEqual({ state: "masked" });
  expect(secret?.change).toBe("unresolved");
  for (const side of ["current", "target"] as const) {
    const page = P.entities(
      await request("POST", `${planPath}/views/entities`, {
        revision: "4",
        side,
        offset: 0,
        limit: 100,
      }),
    );
    expect(page.total).toBe(3);
    expect(
      page.items.filter((item) => JSON.stringify(item.entity) !== JSON.stringify(selected.entity)),
    ).toEqual(
      original.items.filter(
        (item) => JSON.stringify(item.entity) !== JSON.stringify(selected.entity),
      ),
    );
    if (side === "current") expect(page.items).toEqual(original.items);
  }
  const validation = D.validationSummary(
    await request("POST", `${planPath}/validations`, { revision: "4" }),
  );
  expect(validation.targetComplete).toBe(true);
  expect(validation.exportAvailable).toBe(false);
  expect(
    validation.checks.filter((check) => check.outcome === "UNKNOWN").map((check) => check.check),
  ).toEqual(["CLIENT_CAPABILITY", "CONTENT_POLICY", "REVIEW"]);
  expect(validation.checks.filter((check) => check.outcome === "PASS")).toHaveLength(7);
  expect(validation.computedRuleCount).toBe(2);
  const rows = [];
  for (let offset = 0; offset < 2; offset++) {
    const page = D.validationPageResponse(
      await request("POST", `${planPath}/validations`, {
        revision: "4",
        section: "computed-rules",
        inputFingerprint: validation.inputFingerprint,
        offset,
        limit: 1,
      }),
    );
    expect(page.total).toBe(2);
    expect(page.inputFingerprint).toBe(validation.inputFingerprint);
    expect(page.items).toHaveLength(1);
    expect(page.nextOffset).toBe(offset === 0 ? 1 : null);
    rows.push(...page.items);
  }
  expect(
    rows.map((row) => ({ value: row.source?.value, actual: row.actual, outcome: row.outcome })),
  ).toEqual([
    { value: "alpha", actual: "1", outcome: "PASS" },
    { value: "beta", actual: "1", outcome: "PASS" },
  ]);
  await request(
    "POST",
    `${planPath}/validations`,
    {
      revision: "4",
      section: "computed-rules",
      inputFingerprint: before.inputFingerprint,
      offset: 0,
      limit: 1,
    },
    409,
  );
  expect(D.summary(await request("GET", planPath)).revision).toBe("4");
}
