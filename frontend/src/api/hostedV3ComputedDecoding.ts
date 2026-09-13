import * as D from "./hostedV3Decoding";

const id = D.declaredId;
const revision = D.revision;
const side = D.choice("current", "target");
const offset = D.integer(0, 2147483647);
const limit = D.integer(1, 100);
const scalar = (min: number) =>
  D.string(min, Number.MAX_SAFE_INTEGER, undefined, /[\ud800-\udfff]/u);
const decimal = D.string(1, Number.MAX_SAFE_INTEGER, /^(0|[1-9][0-9]*)(?![\s\S])/u);
const index = D.string(1, 10, /^(0|[1-9][0-9]*)(?![\s\S])/u);
const xmlKey = D.string(
  1,
  1048576,
  /^[\t\n\r\u0020-\ud7ff\ue000-\ufffd\u{10000}-\u{10ffff}]+(?![\s\S])/u,
);
function keyValue(value: unknown): string {
  // Schema maxLength counts scalars; the contract additionally bounds UTF16 units.
  if (typeof value !== "string" || value.length > 1048576) return D.invalid();
  return xmlKey(value);
}
export const key = D.object({ computedType: id, derivation: id, value: keyValue }, {});
export const selector = D.union(
  D.object({ kind: D.literal("node"), key }, {}),
  D.object(
    { kind: D.literal("membership"), relation: id, physical: D.entityRef, computed: key },
    {},
  ),
  D.object({ kind: D.literal("cooccurrence"), relation: id, source: key, target: key }, {}),
);
export const collectionRequest = D.object({ revision, side, offset, limit }, {});
export const contributorsRequest = D.object(
  { revision, side, selector, offset, limit, completeDocumentDisclosure: D.literal(true) },
  {},
);
const name = D.object({ namespaceUri: scalar(0), localName: scalar(1) }, {});
export const attribute = D.object(
  {
    documentId: id,
    sourceDigest: D.digest,
    elementIndex: index,
    name,
    qualifiedName: scalar(1),
    decodedValue: scalar(0),
    valueStart: D.integer(0, 1048576),
    valueEnd: D.integer(0, 1048576),
    quote: D.choice("'", '"'),
  },
  {},
);
const child = D.object({ parentElementIndex: index, element: name, discriminator: attribute }, {});
const location = D.object({ value: attribute, selector: D.union(child, D.literal(null)) }, {});
const origin = D.object(
  {
    documentId: id,
    projectionId: id,
    sourceDigest: D.digest,
    elementIndex: index,
    ancestry: D.array(index, 0, 128, false),
  },
  {},
);
const role = D.object({ field: id, location }, {});
export const contributor = D.object(
  { physical: D.entityRef, origin, roles: D.array(role, 1, 2, false) },
  {},
);
const contributorTotal = D.integer(1, 100000);
export const node = D.object({ key, contributorTotal }, {});
export const membership = D.object(
  { relation: id, physical: D.entityRef, computed: key, contributorTotal },
  {},
);
export const cooccurrence = D.object(
  { relation: id, source: key, target: key, contributorTotal },
  {},
);
const rule = D.object(
  {
    kind: D.choice("ENTITY_COUNT", "COOCCURRENCE"),
    declaration: id,
    source: D.union(key, D.literal(null)),
    actual: decimal,
    minimum: decimal,
    maximum: decimal,
    outcome: D.choice("PASS", "FAIL"),
  },
  {},
);
function page<T>(item: (value: unknown) => T) {
  return D.object(
    {
      revision,
      total: offset,
      offset,
      nextOffset: D.union(offset, D.literal(null)),
      items: D.array(item, 0, 100, false),
    },
    {},
  );
}
export const nodes = page(node);
export const memberships = page(membership);
export const cooccurrences = page(cooccurrence);
export const rules = page(rule);
export const contributors = page(contributor);
