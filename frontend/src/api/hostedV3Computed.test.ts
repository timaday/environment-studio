import { createHash } from "node:crypto";
import Ajv2020 from "ajv/dist/2020";
import { afterEach, expect, it, vi } from "vitest";
import commands from "../../../schemas/plan-command-v1.schema.json";
import schema from "../../../schemas/plan-computed-view-v3.schema.json";
import { HostedApi } from "./hosted";
import {
  assertComputedPagesCompatible,
  assertContributorsForSelection,
  HostedV3Computed,
} from "./hostedV3Computed";

const planId = "40000000-0000-0000-0000-000000000001";
const revision = "9007199254740993";
const request = { revision, side: "current", offset: 0, limit: 1 } as const;
const key = { computedType: "group", derivation: "by-label", value: " mock 🦉 " };
const json = (value: unknown) => new Response(JSON.stringify(value));
const owners: HostedApi[] = [];
afterEach(() => {
  for (const owner of owners) owner.clear();
  owners.length = 0;
});
async function client(...replies: unknown[]) {
  const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(
    json({
      authenticated: true,
      csrfHeaderName: "X-CSRF-TOKEN",
      csrfToken: "mock-csrf",
      idleTimeoutSeconds: 1800,
      absoluteExpiresAt: new Date(Date.now() + 28800000).toISOString(),
    }),
  );
  for (const reply of replies) fetcher.mockResolvedValueOnce(json(reply));
  const owner = new HostedApi(fetcher);
  owners.push(owner);
  await owner.session();
  return { api: new HostedV3Computed(owner), owner, fetcher };
}
it("refuses a computed contributor key above the UTF16 bound before disclosure", async () => {
  const { api, fetcher } = await client({
    revision,
    total: 0,
    offset: 0,
    nextOffset: null,
    items: [],
  });
  await expect(
    api.contributors(planId, {
      ...request,
      completeDocumentDisclosure: true,
      selector: { kind: "node", key: { ...key, value: `${"🦉".repeat(524288)}x` } },
    }),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it("refuses a truncated computed node page instead of treating it as complete", async () => {
  const { api } = await client({ revision, total: 2, offset: 0, nextOffset: 1, items: [] });
  await expect(api.nodes(planId, request)).rejects.toMatchObject({ code: "RESPONSE_UNAVAILABLE" });
});

// Independently invented wire examples, not real application models or XML proof.
const physical = { kind: "existing", handle: "40000000-0000-0000-0000-000000000002" } as const;
const fresh = { kind: "fresh", typeId: "sample", slotId: "replacement" } as const;
const digest = "b".repeat(64);
const origin = {
  documentId: "mock-a",
  projectionId: "sample",
  sourceDigest: digest,
  elementIndex: "1",
  ancestry: ["0"],
};
const pin = {
  documentId: "mock-a",
  sourceDigest: digest,
  elementIndex: "1",
  name: { namespaceUri: "", localName: "label" },
  qualifiedName: "label",
  decodedValue: key.value,
  valueStart: 20,
  valueEnd: 29,
  quote: "'",
};
const role = { field: "label", location: { value: pin, selector: null } };
const contributor = { physical, origin, roles: [role] };
const node = { key, contributorTotal: 2 };
const member = { relation: "belongs", physical, computed: key, contributorTotal: 2 };
const pair = { relation: "with", source: key, target: key, contributorTotal: 2 };
const page = (
  items: readonly unknown[],
  total = items.length,
  offset = 0,
  nextOffset: number | null = null,
) => ({ revision, total, offset, nextOffset, items });
const contributorRequest = {
  ...request,
  selector: { kind: "node", key },
  completeDocumentDisclosure: true,
} as const;
const ajv = new Ajv2020({ strict: false });
ajv.addSchema(commands).addSchema(schema);
function shape(name: string, value: unknown) {
  const validate = ajv.compile({ $ref: `${schema.$id}#/$defs/${name}` });
  expect(validate(value), JSON.stringify(validate.errors)).toBe(true);
}

it("validates complete independent collection and contributor fixtures against canonical schemas", () => {
  const child = {
    ...contributor,
    roles: [
      {
        field: "label",
        location: {
          value: { ...pin, elementIndex: "2" },
          selector: {
            parentElementIndex: "1",
            element: { namespaceUri: "", localName: "property" },
            discriminator: {
              ...pin,
              elementIndex: "2",
              name: { namespaceUri: "", localName: "name" },
              qualifiedName: "name",
              decodedValue: "declared",
            },
          },
        },
      },
    ],
  };
  for (const [name, value] of Object.entries({
    collectionRequest: request,
    contributorsRequest: contributorRequest,
    nodesResponse: page([node]),
    membershipsResponse: page([member]),
    cooccurrencesResponse: page([pair]),
    rulesResponse: page([
      {
        kind: "ENTITY_COUNT",
        declaration: "range",
        source: null,
        actual: "2",
        minimum: "3",
        maximum: "999999999999999999999999",
        outcome: "FAIL",
      },
    ]),
    contributorsResponse: page([contributor, child, { ...contributor, roles: [role, role] }]),
  }))
    shape(name, value);
});
it("uses five fixed routes with exact scoped bodies and detaches disclosure selectors", async () => {
  const { api, fetcher } = await client(
    page([node]),
    page([member]),
    page([pair]),
    page([]),
    page([contributor]),
  );
  const results = [
    await api.nodes(planId, request),
    await api.memberships(planId, request),
    await api.cooccurrences(planId, request),
    await api.rules(planId, request),
  ];
  const value = { ...contributorRequest, selector: { kind: "node" as const, key: { ...key } } };
  const pending = api.contributors(planId, value);
  value.selector.key.value = "caller mutation";
  const disclosed = await pending;
  expect(disclosed.request.selector).toEqual(contributorRequest.selector);
  expect(Object.isFrozen(disclosed.request.selector)).toBe(true);
  expect(Object.isFrozen(disclosed.response.items[0].roles[0].location.value)).toBe(true);
  expect(fetcher.mock.calls.slice(1).map(([url, options]) => [url, options?.method])).toEqual(
    ["nodes", "memberships", "cooccurrences", "rules", "contributors"].map((route) => [
      `/api/v3/plans/${planId}/views/computed/${route}`,
      "POST",
    ]),
  );
  for (const [, options] of fetcher.mock.calls.slice(1))
    expect(options?.headers).toMatchObject({ "X-CSRF-TOKEN": "mock-csrf" });
  for (const result of results) expect(result.request).toEqual(request);
  expect(
    fetcher.mock.calls
      .slice(1, 5)
      .every(([, options]) => !String(options?.body).includes("Disclosure")),
  ).toBe(true);
});
it("preserves exact large keys through node selection and two-key contributor requests", async () => {
  const text = "🦉".repeat(524288);
  const huge = { ...key, value: text },
    other = { ...key, value: " \r\nA&\t🦉 " };
  const { api, fetcher } = await client(
    page([{ key: huge, contributorTotal: 1 }]),
    page([], 1, 2147483647),
    page([], 1, 2147483647),
  );
  const selected = await api.nodes(planId, request);
  expect(selected.response.items[0].key.value).toBe(text);
  const exact = {
    ...contributorRequest,
    selector: { kind: "node" as const, key: selected.response.items[0].key },
  };
  await api.contributors(planId, { ...exact, offset: 2147483647 });
  await api.contributors(planId, {
    ...contributorRequest,
    offset: 2147483647,
    selector: { kind: "cooccurrence", relation: "with", source: huge, target: huge },
  });
  expect(JSON.parse(String(fetcher.mock.calls[2][1]?.body)).selector.key.value).toBe(text);
  expect(JSON.parse(String(fetcher.mock.calls[3][1]?.body)).selector.target.value).toBe(text);
  const values = [
    other,
    { ...key, value: "e\u0301" },
    { ...key, value: "é" },
    { ...key, value: " Z " },
  ];
  fetcher.mockResolvedValueOnce(json(page(values.map((key) => ({ key, contributorTotal: 1 })))));
  expect(
    (await api.nodes(planId, { ...request, limit: 100 })).response.items.map((row) => row.key),
  ).toEqual(values);
});
it("refuses missing disclosure invalid XML keys aliases and out-of-range requests before transport", async () => {
  const { api, fetcher } = await client();
  for (const value of ["", "\uD800", "\u0000", "\uFFFE", "\u000B"])
    await expect(
      api.contributors(planId, {
        ...contributorRequest,
        selector: { kind: "node", key: { ...key, value } },
      }),
    ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  for (const invalid of [false, undefined, "true"])
    await expect(
      api.contributors(planId, {
        ...contributorRequest,
        completeDocumentDisclosure: invalid,
      } as unknown as Parameters<typeof api.contributors>[1]),
    ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  for (const invalid of [
    { ...request, offset: 2147483648 },
    { ...request, offset: -0 },
    { ...request, limit: 0 },
    { ...request, limit: 101 },
    { ...request, side: "original" },
    { ...request, requestId: planId },
  ])
    await expect(
      api.nodes(planId, invalid as Parameters<typeof api.nodes>[1]),
    ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  await expect(api.nodes("../foreign", request)).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it("keeps complete large and beyond-end pages and refuses wrong continuations without automatic paging", async () => {
  const { api, fetcher } = await client(page([node], 64000, 63999), page([], 64000, 2147483647));
  const last = await api.nodes(planId, { ...request, offset: 63999 });
  expect(fetcher).toHaveBeenCalledTimes(2);
  const beyond = await api.nodes(planId, { ...request, offset: 2147483647 });
  expect(() => assertComputedPagesCompatible(last, beyond)).not.toThrow();
  for (const bad of [
    page([], 1),
    page([node], 2),
    page([node], 2, 0, 2),
    page([node], 1, 1),
    { ...page([node]), revision: "2" },
    { ...page([]), total: 2147483648 },
  ]) {
    fetcher.mockResolvedValueOnce(json(bad));
    await expect(api.nodes(planId, request)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
});
it("keeps contributor pages scoped by complete selector side plan route revision and total", async () => {
  const { api, fetcher } = await client(page([]));
  const first = await api.contributors(planId, contributorRequest);
  for (const changed of [
    { ...contributorRequest, side: "target" as const },
    {
      ...contributorRequest,
      selector: { kind: "node" as const, key: { ...key, computedType: "other" } },
    },
    {
      ...contributorRequest,
      selector: { kind: "membership" as const, relation: "belongs", physical, computed: key },
    },
  ]) {
    fetcher.mockResolvedValueOnce(json(page([])));
    const next = await api.contributors(planId, changed);
    expect(() => assertComputedPagesCompatible(first, next)).toThrow("CONFLICT");
  }
  fetcher.mockResolvedValueOnce(json(page([])));
  const foreign = await api.contributors(
    "40000000-0000-0000-0000-000000000099",
    contributorRequest,
  );
  expect(() => assertComputedPagesCompatible(first, foreign)).toThrow("CONFLICT");
  fetcher.mockResolvedValueOnce(json({ ...page([]), revision: "2" }));
  const newer = await api.contributors(planId, { ...contributorRequest, revision: "2" });
  expect(() => assertComputedPagesCompatible(first, newer)).toThrow("CONFLICT");
  fetcher.mockResolvedValueOnce(json(page([])));
  const nodes = await api.nodes(planId, request);
  expect(() => assertComputedPagesCompatible(first, nodes)).toThrow("CONFLICT");
  fetcher.mockResolvedValueOnce(json(page([contributor])));
  const nonempty = await api.contributors(planId, contributorRequest);
  expect(() => assertComputedPagesCompatible(first, nonempty)).toThrow("CONFLICT");
});
it("correlates contributor totals and exact selected node membership and cooccurrence identities", async () => {
  const { api, fetcher } = await client(
    page([node]),
    page([contributor], 2, 0, 1),
    page([member]),
    page([contributor], 2, 0, 1),
    page([pair]),
    page([{ ...contributor, roles: [role, role] }], 2, 0, 1),
  );
  const nodes = await api.nodes(planId, request),
    fromNode = await api.contributors(planId, contributorRequest);
  expect(() => assertContributorsForSelection(nodes, 0, fromNode)).not.toThrow();
  const members = await api.memberships(planId, request);
  const fromMember = await api.contributors(planId, {
    ...contributorRequest,
    selector: {
      kind: "membership",
      relation: members.response.items[0].relation,
      physical: members.response.items[0].physical,
      computed: members.response.items[0].computed,
    },
  });
  expect(() => assertContributorsForSelection(members, 0, fromMember)).not.toThrow();
  const pairs = await api.cooccurrences(planId, request);
  const fromPair = await api.contributors(planId, {
    ...contributorRequest,
    selector: {
      kind: "cooccurrence",
      relation: pair.relation,
      source: pair.source,
      target: pair.target,
    },
  });
  expect(() => assertContributorsForSelection(pairs, 0, fromPair)).not.toThrow();
  expect(fromPair.response.items[0].roles).toEqual([role, role]);
  expect(() => assertContributorsForSelection(nodes, 0, fromMember)).toThrow("CONFLICT");
  expect(() => assertContributorsForSelection(nodes, 1, fromNode)).toThrow("CONFLICT");
  fetcher.mockResolvedValueOnce(json(page([contributor])));
  const incompleteTotal = await api.contributors(planId, contributorRequest);
  expect(() => assertContributorsForSelection(nodes, 0, incompleteTotal)).toThrow("CONFLICT");
});
it("retains Fresh identity child discriminators and duplicate ordered roles with exact decoded values", async () => {
  const childRole = {
    field: "label",
    location: {
      value: { ...pin, elementIndex: "2" },
      selector: {
        parentElementIndex: "1",
        element: { namespaceUri: "", localName: "property" },
        discriminator: { ...pin, elementIndex: "2", decodedValue: "selector" },
      },
    },
  };
  const target = { ...key, value: "second & value" },
    targetRole = {
      ...role,
      field: "other",
      location: { ...role.location, value: { ...pin, decodedValue: target.value } },
    };
  const { api, fetcher } = await client(
    page([{ ...contributor, physical: fresh, roles: [childRole] }]),
    page([{ ...contributor, roles: [role, targetRole] }]),
  );
  const membership = {
    ...contributorRequest,
    selector: { kind: "membership" as const, relation: "belongs", physical: fresh, computed: key },
  };
  const result = await api.contributors(planId, membership);
  expect(result.response.items[0].physical).toEqual(fresh);
  expect(result.response.items[0].roles[0].location.selector).toEqual(childRole.location.selector);
  const cooccurrence = {
    ...contributorRequest,
    selector: { kind: "cooccurrence" as const, relation: "with", source: key, target },
  };
  expect((await api.contributors(planId, cooccurrence)).response.items[0].roles).toEqual([
    role,
    targetRole,
  ]);
  fetcher.mockResolvedValueOnce(json(page([contributor])));
  await expect(api.contributors(planId, membership)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
  for (const roles of [[targetRole, role], [role], [role, role]]) {
    fetcher.mockResolvedValueOnce(json(page([{ ...contributor, roles }])));
    await expect(api.contributors(planId, cooccurrence)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
});
it("rejects missing or extra nested origin role and pin properties and reversed spans", async () => {
  const { api, fetcher } = await client();
  const layers = ["origin", "role", "location", "pin"] as const;
  for (const layer of layers) {
    const get = (row: typeof contributor): Record<string, unknown> =>
      layer === "origin"
        ? row.origin
        : layer === "role"
          ? row.roles[0]
          : layer === "location"
            ? row.roles[0].location
            : row.roles[0].location.value;
    for (const key of [...Object.keys(get(contributor)), "extraProof"]) {
      const row = structuredClone(contributor);
      const target = get(row);
      if (key === "extraProof") target[key] = "untrusted";
      else delete target[key];
      fetcher.mockResolvedValueOnce(json(page([row])));
      await expect(api.contributors(planId, contributorRequest)).rejects.toMatchObject({
        code: "RESPONSE_UNAVAILABLE",
      });
    }
  }
  for (const value of [
    { ...pin, valueStart: 30, valueEnd: 20 },
    { ...pin, decodedValue: "different" },
  ]) {
    fetcher.mockResolvedValueOnce(
      json(page([{ ...contributor, roles: [{ ...role, location: { value, selector: null } }] }])),
    );
    await expect(api.contributors(planId, contributorRequest)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
});
it("keeps complete FAIL rules and arbitrary-precision cardinalities separate from validation authority", async () => {
  const huge = "9".repeat(2048);
  const rows = [
    {
      kind: "ENTITY_COUNT",
      declaration: "minimum",
      source: null,
      actual: "2",
      minimum: huge,
      maximum: huge,
      outcome: "FAIL",
    },
    {
      kind: "COOCCURRENCE",
      declaration: "pair",
      source: key,
      actual: huge,
      minimum: "0",
      maximum: huge,
      outcome: "PASS",
    },
  ];
  shape("rulesResponse", page(rows));
  const { api, fetcher } = await client(page(rows));
  expect((await api.rules(planId, { ...request, limit: 100 })).response.items).toEqual(rows);
  for (const actual of [2, "02", "-1", "1\n"]) {
    fetcher.mockResolvedValueOnce(json(page([{ ...rows[0], actual }])));
    await expect(api.rules(planId, request)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
});
it("retains every equal-valued contributor across explicit pages through the last occurrence", async () => {
  const last = {
    ...contributor,
    origin: { ...origin, documentId: "mock-b", elementIndex: "3" },
    roles: [
      {
        ...role,
        location: { value: { ...pin, documentId: "mock-b", elementIndex: "3" }, selector: null },
      },
    ],
  };
  const { api, fetcher } = await client(
    page([node]),
    page([contributor], 2, 0, 1),
    page([last], 2, 1),
    page([], 2, 2),
  );
  const selected = await api.nodes(planId, request);
  const first = await api.contributors(planId, contributorRequest);
  expect(fetcher).toHaveBeenCalledTimes(3);
  const tail = await api.contributors(planId, { ...contributorRequest, offset: 1 });
  const end = await api.contributors(planId, { ...contributorRequest, offset: 2 });
  expect(() => assertComputedPagesCompatible(first, tail)).not.toThrow();
  expect(() => assertComputedPagesCompatible(first, end)).not.toThrow();
  expect(() => assertContributorsForSelection(selected, 0, tail)).not.toThrow();
  expect([...first.response.items, ...tail.response.items]).toEqual([contributor, last]);
  expect(tail.response.nextOffset).toBeNull();
});
it("preserves child value and selector lexical pins separately and refuses incomplete or reversed discriminators", async () => {
  const decoded = "A&B",
    raw = "<r><p name='selected' value='A&amp;B'/></r>";
  const sourceDigest = createHash("sha256").update(raw).digest("hex");
  const value = {
    ...pin,
    sourceDigest,
    elementIndex: "1",
    name: { namespaceUri: "", localName: "value" },
    qualifiedName: "value",
    decodedValue: decoded,
    valueStart: 29,
    valueEnd: 36,
  };
  const discriminator = {
    ...pin,
    sourceDigest,
    elementIndex: "1",
    name: { namespaceUri: "", localName: "name" },
    qualifiedName: "name",
    decodedValue: "selected",
    valueStart: 12,
    valueEnd: 20,
  };
  const selector = {
    parentElementIndex: "0",
    element: { namespaceUri: "", localName: "p" },
    discriminator,
  };
  const row = {
    ...contributor,
    origin: { ...origin, sourceDigest, elementIndex: "0", ancestry: [] },
    roles: [{ ...role, location: { value, selector } }],
  };
  shape("contributorsResponse", page([row]));
  const { api, fetcher } = await client(page([row]));
  const body = {
    ...contributorRequest,
    selector: { kind: "node" as const, key: { ...key, value: decoded } },
  };
  const result = (await api.contributors(planId, body)).response.items[0].roles[0].location;
  expect(raw.slice(result.value.valueStart, result.value.valueEnd)).toBe("A&amp;B");
  expect(result.value.decodedValue).toBe("A&B");
  expect(result.selector?.discriminator.decodedValue).toBe("selected");
  for (const property of Object.keys(selector)) {
    const bad = structuredClone(row);
    delete (bad.roles[0].location.selector as Record<string, unknown>)[property];
    fetcher.mockResolvedValueOnce(json(page([bad])));
    await expect(api.contributors(planId, body)).rejects.toMatchObject({
      code: "RESPONSE_UNAVAILABLE",
    });
  }
  fetcher.mockResolvedValueOnce(
    json(
      page([
        {
          ...row,
          roles: [
            {
              ...role,
              location: {
                value,
                selector: { ...selector, discriminator: { ...discriminator, valueStart: 21 } },
              },
            },
          ],
        },
      ]),
    ),
  );
  await expect(api.contributors(planId, body)).rejects.toMatchObject({
    code: "RESPONSE_UNAVAILABLE",
  });
});
it("preserves safe refusals and explicit smaller-page retry then discards a late JSON body", async () => {
  const { api, owner, fetcher } = await client();
  for (const code of ["RESOURCE_LIMIT", "INCOMPLETE_TARGET", "NOT_FOUND"]) {
    fetcher.mockResolvedValueOnce(new Response(JSON.stringify({ code }), { status: 409 }));
    await expect(
      api.contributors(planId, { ...contributorRequest, limit: 100 }),
    ).rejects.toMatchObject({ code });
  }
  expect(fetcher).toHaveBeenCalledTimes(4);
  fetcher.mockResolvedValueOnce(json(page([contributor])));
  expect((await api.contributors(planId, contributorRequest)).response.total).toBe(1);
  let controller: ReadableStreamDefaultController<Uint8Array> | undefined;
  const stream = new ReadableStream<Uint8Array>({
    start(value) {
      controller = value;
    },
  });
  fetcher.mockResolvedValueOnce(new Response(stream));
  const pending = api.nodes(planId, request),
    refused = expect(pending).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
  await Promise.resolve();
  await Promise.resolve();
  owner.clear();
  controller?.enqueue(new TextEncoder().encode(JSON.stringify(page([node]))));
  controller?.close();
  await refused;
});
