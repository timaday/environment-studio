import * as D from "./hostedV3Decoding";

const expectedRevision = D.string(1, 1024, /^(0|[1-9][0-9]*)(?![\s\S])/u);
const format = D.choice("JSON", "YAML");
function source(value: unknown): string {
  if (
    typeof value !== "string" ||
    value.length > 1048576 ||
    new TextEncoder().encode(value).length > 1048576
  )
    return D.invalid();
  for (const character of value) {
    const point = character.codePointAt(0);
    if (point !== undefined && point >= 0xd800 && point <= 0xdfff) return D.invalid();
  }
  return value;
}
const model = D.object(
  {
    schemaVersion: D.literal("3"),
    id: D.declaredId,
    revision: D.revision,
    logicalDefinitionDigest: D.digest,
    entities: D.array(
      D.object(
        {
          id: D.declaredId,
          type: D.declaredId,
          label: D.string(1, 128),
          requiredInputs: D.array(D.declaredId, 0, Number.MAX_SAFE_INTEGER, true),
        },
        {},
      ),
      1,
      20000,
      false,
    ),
    relations: D.array(
      D.object({ type: D.declaredId, from: D.declaredId, to: D.declaredId }, {}),
      0,
      50000,
      false,
    ),
  },
  {},
);
const common = {
  objectId: D.uuid,
  workspaceRevision: D.revision,
  sourceDigest: D.digest,
  format,
  source,
  schemaVersion: D.literal("3"),
  compilerVersion: D.literal("profile-compiler-v3"),
  definition: D.publicationRef,
  projection: D.object(
    {
      kind: D.literal("structurally-valid"),
      model,
      contentDigest: D.digest,
      diagnostics: D.array(D.invalid, 0, 0, false),
    },
    {},
  ),
};
export const profile = D.union(
  D.object({ ...common, state: D.literal("draft") }, {}),
  D.object(
    {
      ...common,
      state: D.literal("published"),
      publication: D.object(
        {
          digest: D.digest,
          sourceRevision: D.revision,
        },
        {},
      ),
    },
    {},
  ),
);
export const list = D.object(
  {
    profiles: D.array(
      D.object(
        {
          objectId: D.uuid,
          workspaceRevision: D.revision,
          nativeId: D.declaredId,
          nativeRevision: D.revision,
          sourceDigest: D.digest,
          state: D.choice("draft", "published"),
          contentDigest: D.digest,
          definition: D.publicationRef,
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
export const save = D.object(
  { expectedRevision, requestId: D.uuid, format, source, definition: D.publicationRef },
  {},
);
export const publish = D.object({ expectedRevision, requestId: D.uuid }, {});
export const preparedSave = D.object({ objectId: D.uuid, command: save }, {});
export const preparedPublication = D.object({ objectId: D.uuid, command: publish }, {});
