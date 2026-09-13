import * as D from "./hostedV3Decoding";

// Complete physical shapes from the shared plan-view-v1 schema at fixed v3 routes.
// XML text has scalar Unicode values; /u leaves only unpaired surrogates here.
const text = D.string(0, Number.MAX_SAFE_INTEGER, undefined, /[\ud800-\udfff]/u);
const side = D.choice("current", "target");
const mode = D.choice("raw", "placeholders", "formatted");
const offset = D.integer(0, 50000);
const limit = D.integer(1, 100);
const nullValue = D.literal(null);
const no = D.literal(false);
const yes = D.literal(true);
const id = D.declaredId;
const revision = D.revision;
const paging = { revision, offset, limit };
const nonnegativeDecimal = (max: number) => D.string(1, max, /^(0|[1-9][0-9]*)(?![\s\S])/u);
export const revisionRequest = D.object({ revision }, {});
export const entitiesRequest = D.object({ revision, side, offset, limit }, {});
export const draftRequest = D.object(paging, {});
export const placementsRequest = D.object(
  { revision, documentId: id, projectionId: id, offset, limit },
  {},
);
export const bindingsRequest = D.object(
  { revision, entity: D.entityRef, offset: D.integer(0, 256), limit },
  {},
);
export const documentRequest = D.object(
  { revision, side, documentId: id, mode, completeDocumentDisclosure: yes },
  {},
);
export const locationsRequest = D.object(
  {
    revision,
    entity: D.entityRef,
    fieldId: id,
    side,
    offset: D.integer(0, 2147483647),
    limit,
    completeDocumentDisclosure: yes,
  },
  {},
);

function page<T>(
  item: (value: unknown) => T,
  maximumOffset = 50000,
  maximumTotal = Number.MAX_SAFE_INTEGER,
) {
  return D.object(
    {
      revision,
      total: D.integer(0, maximumTotal),
      offset: D.integer(0, maximumOffset),
      nextOffset: D.union(D.integer(0, maximumOffset), nullValue),
      items: D.array(item, 0, 100, false),
    },
    {},
  );
}
const documentEntry = D.union(
  D.object(
    { documentId: id, currentDigest: D.digest, targetDigest: nullValue, changed: nullValue },
    {},
  ),
  D.object(
    { documentId: id, currentDigest: D.digest, targetDigest: D.digest, changed: D.booleanValue },
    {},
  ),
);
export const documents = D.object(
  { revision, documents: D.array(documentEntry, 0, 128, false) },
  {},
);
const entityField = D.union(
  D.object({ fieldId: id, present: yes, masked: no, value: text }, {}),
  D.object({ fieldId: id, present: yes, masked: yes, value: nullValue }, {}),
  D.object({ fieldId: id, present: no, masked: D.booleanValue, value: nullValue }, {}),
);
export const entities = page(
  D.object({ entity: D.entityRef, typeId: id, fields: D.array(entityField, 0, 256, false) }, {}),
);
export const relations = page(D.object({ relationId: id, from: D.entityRef, to: D.entityRef }, {}));
const draftField = D.union(
  D.object({ fieldId: id, kind: D.literal("entered"), masked: no, value: text }, {}),
  D.object({ fieldId: id, kind: D.literal("entered"), masked: yes, value: nullValue }, {}),
  D.object(
    {
      fieldId: id,
      kind: D.choice("unresolved", "keep-observed", "absent"),
      masked: D.booleanValue,
      value: nullValue,
    },
    {},
  ),
);
const draftReference = D.union(
  D.object({ referenceId: id, kind: D.literal("to"), target: D.entityRef }, {}),
  D.object(
    { referenceId: id, kind: D.choice("unresolved", "keep-observed", "absent"), target: nullValue },
    {},
  ),
);
const decisionFields = {
  fields: D.array(draftField, 0, 256, false),
  references: D.array(draftReference, 0, 256, false),
  placements: D.array(D.placement, 0, 20000, false),
};
export const draft = page(
  D.union(
    D.object({ entity: D.existing, disposition: D.literal("retain"), ...decisionFields }, {}),
    D.object({ entity: D.fresh, disposition: D.literal("create"), ...decisionFields }, {}),
    D.object(
      {
        entity: D.existing,
        disposition: D.literal("remove"),
        fields: D.array(draftField, 0, 0, false),
        references: D.array(draftReference, 0, 0, false),
        placements: D.array(D.placement, 0, 0, false),
      },
      {},
    ),
  ),
);
export const containment = page(
  D.object({ relationId: id, parent: D.entityRef, child: D.entityRef }, {}),
);
export const placements = page(
  D.object({ documentId: id, sourceDigest: D.digest, elementIndex: nonnegativeDecimal(1024) }, {}),
);
export const document = D.object(
  {
    revision,
    documentId: id,
    side,
    mode,
    text,
    exact: D.booleanValue,
    redacted: D.booleanValue,
    unmappedConcreteMayRemain: D.booleanValue,
    omissions: D.array(
      D.string(1, 128, /^[A-Z][A-Z0-9_]*(?![\s\S])/u),
      0,
      Number.MAX_SAFE_INTEGER,
      false,
    ),
  },
  {},
);
const bindingValue = D.union(
  D.object({ state: D.literal("value"), text }, {}),
  D.object({ state: D.choice("masked", "absent", "unresolved", "unavailable") }, {}),
);
const locationCount = D.union(
  D.object({ state: D.literal("complete"), total: D.integer(0, 2147483647) }, {}),
  D.object(
    {
      state: D.literal("unavailable"),
      code: D.choice("CURRENT_ENTITY_ABSENT", "INCOMPLETE_TARGET"),
    },
    {},
  ),
);
export const bindings = page(
  D.object(
    {
      fieldId: id,
      token: D.string(
        0,
        Number.MAX_SAFE_INTEGER,
        /^\[\[value:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}:[a-z][a-z0-9.-]{0,63}\]\](?![\s\S])/u,
      ),
      current: bindingValue,
      target: bindingValue,
      change: D.choice("unchanged", "changed", "added", "removed", "unresolved"),
      currentLocations: locationCount,
      targetLocations: locationCount,
    },
    {},
  ),
  256,
  256,
);
export const location = D.object(
  {
    documentId: id,
    sourceDigest: D.digest,
    projectionId: id,
    elementIndex: nonnegativeDecimal(10),
    attribute: D.object(
      { namespaceUri: text, localName: D.string(1, Number.MAX_SAFE_INTEGER) },
      {},
    ),
    span: D.object({ start: D.integer(0, 1048576), end: D.integer(0, 1048576) }, {}),
    role: D.choice("field", "reference"),
    declarationId: id,
  },
  {},
);
export const locations = page(location, 2147483647, 2147483647);
