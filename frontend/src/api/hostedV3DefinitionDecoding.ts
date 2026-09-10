import * as D from "./hostedV3Decoding";

const id = D.declaredId,
  revision = D.revision,
  boolean = D.booleanValue;
const many = Number.MAX_SAFE_INTEGER;
const text = (min = 0, max = many) => D.string(min, max, undefined, /[\ud800-\udfff]/u);
const label = text(1, 128);
const minimum = D.string(1, 1024, /^(0|[1-9][0-9]*)(?![\s\S])/u);
const maximum = revision;
const sqlId = D.string(1, 128, /^[A-Za-z_][A-Za-z0-9_]{0,127}(?![\s\S])/u);
const localName = D.string(
  1,
  128,
  /^[A-Z_a-z\u00c0-\u00d6\u00d8-\u00f6\u00f8-\u02ff\u0370-\u037d\u037f-\u1fff\u200c-\u200d\u2070-\u218f\u2c00-\u2fef\u3001-\ud7ff\uf900-\ufdcf\ufdf0-\ufffd\u{10000}-\u{effff}](?:[A-Z_a-z\u00c0-\u00d6\u00d8-\u00f6\u00f8-\u02ff\u0370-\u037d\u037f-\u1fff\u200c-\u200d\u2070-\u218f\u2c00-\u2fef\u3001-\ud7ff\uf900-\ufdcf\ufdf0-\ufffd\u{10000}-\u{effff}\-.0-9\u00b7]|[\u0300-\u036f]|[\u203f-\u2040])*(?![\s\S])/u,
);
const name = D.object({ namespaceUri: text(0, 2048), localName }, {});
const field = D.object(
  {
    id,
    valueType: D.choice("text", "integer", "boolean", "uri"),
    required: boolean,
    classification: D.choice("environment", "structural"),
    sensitivity: D.choice("public", "internal", "secret", "unknown"),
    readable: boolean,
    editable: boolean,
  },
  {},
);
const entityType = D.object(
  {
    id,
    label,
    fields: D.array(field, 1, many, false),
    identity: D.object(
      { field: id, scope: D.literal("type"), normalization: D.literal("exact") },
      {},
    ),
  },
  {},
);
const relation = D.object(
  {
    id,
    fromType: id,
    toType: id,
    kind: D.choice("reference", "containment"),
    minimum,
    maximum,
    includeTargetOnReuse: boolean,
  },
  {},
);
const rule = D.object({ id, kind: D.literal("entity-count"), type: id, minimum, maximum }, {});
const childProperty = D.object(
  {
    element: name,
    discriminatorAttribute: name,
    discriminatorValue: text(0, 1048576),
    valueAttribute: name,
  },
  {},
);
const fieldMapping = D.union(
  D.object({ field: id, attribute: name }, {}),
  D.object({ field: id, childProperty }, {}),
);
const projection = D.object(
  {
    id,
    type: id,
    path: D.array(name, 1, many, false),
    fields: D.array(fieldMapping, 0, many, false),
    references: D.array(D.object({ relation: id, attribute: name }, {}), 0, many, false),
  },
  {},
);
const binding = D.object(
  {
    id,
    engine: D.choice("postgresql", "oracle"),
    storage: D.choice("text", "clob"),
    schema: sqlId,
    table: sqlId,
    keyColumn: sqlId,
    xmlColumn: sqlId,
    keyType: D.choice("text", "int64"),
    documents: D.array(
      D.object({ id, key: text(1, 256), entities: D.array(projection, 1, many, false) }, {}),
      1,
      many,
      false,
    ),
  },
  {},
);
export const model = D.object(
  {
    schemaVersion: D.literal("3"),
    id,
    revision,
    logical: D.object(
      {
        entityTypes: D.array(entityType, 1, many, false),
        relations: D.array(relation, 0, many, false),
        rules: D.array(rule, 0, many, false),
        operationCapabilities: D.array(
          D.choice(
            "retain-entity",
            "create-entity",
            "remove-entity",
            "bind-field",
            "move-relation",
          ),
          0,
          many,
          true,
        ),
        computedTypes: D.array(D.object({ id, label }, {}), 0, 32, false),
        derivations: D.array(
          D.object(
            { id, sourceType: id, sourceField: id, computedType: id, membershipRelation: id },
            {},
          ),
          0,
          32,
          false,
        ),
        cooccurrences: D.array(
          D.object({ id, fromDerivation: id, toDerivation: id, minimum, maximum }, {}),
          0,
          32,
          false,
        ),
        computedRules: D.array(rule, 0, many, false),
      },
      {},
    ),
    bindings: D.array(binding, 1, many, false),
  },
  {},
);
function source(value: unknown): string {
  if (
    typeof value !== "string" ||
    value.length > 1048576 ||
    new TextEncoder().encode(value).length > 1048576
  )
    return D.invalid();
  return text()(value);
}
const diagnostic = D.object(
  {
    phase: D.literal("publication"),
    code: D.string(1, 128, /^[A-Z][A-Z0-9_]{0,127}(?![\s\S])/u),
    pointer: text(),
    message: text(),
  },
  {},
);
const projectionCommon = {
  model,
  logicalDigest: D.digest,
  bindingDigests: D.dictionary(D.digest, /^[a-z][a-z0-9.-]{0,63}(?![\s\S])/u, many),
  mechanisms: D.object(
    {
      "xml-path-v1": revision,
      "xml-span-v1": revision,
      "generic-graph-v1": revision,
      "native-compiler-v3": revision,
      "derived-graph-v1": revision,
    },
    { "xml-child-property-v1": revision },
  ),
};
const incomplete = D.object(
  {
    ...projectionCommon,
    kind: D.literal("incomplete"),
    diagnostics: D.array(diagnostic, 1, many, false),
  },
  {},
);
const ready = D.object(
  {
    ...projectionCommon,
    kind: D.literal("historical-ready"),
    diagnostics: D.array(diagnostic, 0, 0, false),
  },
  {},
);
const common = {
  objectId: D.uuid,
  workspaceRevision: revision,
  sourceDigest: D.digest,
  format: D.choice("JSON", "YAML"),
  source,
  schemaVersion: D.literal("3"),
  compilerVersion: D.literal("native-compiler-v3"),
};
export const definition = D.union(
  D.object({ ...common, projection: D.union(incomplete, ready), state: D.literal("draft") }, {}),
  D.object(
    {
      ...common,
      projection: ready,
      state: D.literal("published"),
      publication: D.object(
        {
          digest: D.digest,
          sourceRevision: revision,
          exportPolicies: D.array(
            D.object(
              {
                bindingId: id,
                documentId: id,
                content: D.choice("deny", "protected-self-contained"),
              },
              {},
            ),
            1,
            20000,
            false,
          ),
        },
        {},
      ),
    },
    {},
  ),
);
export const list = D.object(
  {
    definitions: D.array(
      D.object(
        {
          objectId: D.uuid,
          workspaceRevision: revision,
          nativeId: id,
          nativeRevision: revision,
          sourceDigest: D.digest,
          state: D.choice("draft", "published"),
          compilationKind: D.choice("incomplete", "historical-ready"),
          logicalDigest: D.digest,
        },
        {},
      ),
      0,
      100,
      false,
    ),
  },
  {},
);
