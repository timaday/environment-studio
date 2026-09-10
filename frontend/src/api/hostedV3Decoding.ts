import { ApiFailure } from "./hosted";

// Closed decoders mirror the reviewed OpenAPI and plan-command/workflow schemas.
// They validate browser data shape, never server authority or qualification.
type Decoder<T> = (value: unknown) => T;
type Shape = Readonly<Record<string, Decoder<unknown>>>;
type Decoded<S extends Shape> = { readonly [K in keyof S]: ReturnType<S[K]> };
export function invalid(): never {
  throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
}
function literal<const T extends string | number | boolean | null>(expected: T): Decoder<T> {
  return (value) => (value === expected ? expected : invalid());
}
function choice<const T extends readonly string[]>(...values: T): Decoder<T[number]> {
  return (value) =>
    typeof value === "string" && values.includes(value) ? (value as T[number]) : invalid();
}
const boolean: Decoder<boolean> = (value) => (typeof value === "boolean" ? value : invalid());
function integer(min: number, max: number): Decoder<number> {
  return (value) =>
    typeof value === "number" &&
    Number.isSafeInteger(value) &&
    !Object.is(value, -0) &&
    value >= min &&
    value <= max
      ? value
      : invalid();
}
function string(min: number, max: number, pattern?: RegExp, excluded?: RegExp): Decoder<string> {
  return (value) => {
    if (typeof value !== "string") return invalid();
    let length = 0;
    for (const _character of value) {
      if (++length > max) return invalid();
    }
    return length >= min &&
      length <= max &&
      (!pattern || pattern.test(value)) &&
      !excluded?.test(value)
      ? value
      : invalid();
  };
}
function array<T>(
  decode: Decoder<T>,
  min: number,
  max: number,
  unique: boolean,
): Decoder<readonly T[]> {
  return (value) => {
    if (!Array.isArray(value) || value.length < min || value.length > max) return invalid();
    const result = Array.from(value, decode);
    if (unique && new Set(result.map((item) => JSON.stringify(item))).size !== result.length)
      return invalid();
    return Object.freeze(result);
  };
}
function tuple<const T extends readonly Decoder<unknown>[]>(
  ...decoders: T
): Decoder<{ readonly [K in keyof T]: ReturnType<T[K]> }> {
  return (value) => {
    if (!Array.isArray(value) || value.length !== decoders.length) return invalid();
    return Object.freeze(decoders.map((decode, i) => decode(value[i]))) as {
      readonly [K in keyof T]: ReturnType<T[K]>;
    };
  };
}
function record(value: unknown): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) return invalid();
  const proto = Object.getPrototypeOf(value);
  if (proto !== Object.prototype && proto !== null) return invalid();
  return value as Record<string, unknown>;
}
function object<R extends Shape, O extends Shape>(
  required: R,
  optional: O,
): Decoder<Decoded<R> & Partial<Decoded<O>>> {
  return (value) => {
    const input = record(value);
    if (
      Object.keys(input).some(
        (key) => !Object.hasOwn(required, key) && !Object.hasOwn(optional, key),
      )
    )
      return invalid();
    const output: Record<string, unknown> = {};
    for (const [key, decode] of Object.entries(required)) {
      if (!Object.hasOwn(input, key)) return invalid();
      output[key] = decode(input[key]);
    }
    for (const [key, decode] of Object.entries(optional))
      if (Object.hasOwn(input, key)) output[key] = decode(input[key]);
    return Object.freeze(output) as Decoded<R> & Partial<Decoded<O>>;
  };
}
function dictionary<T>(
  decode: Decoder<T>,
  pattern: RegExp,
  max: number,
): Decoder<Readonly<Record<string, T>>> {
  return (value) => {
    const input = record(value);
    if (Object.keys(input).length > max) return invalid();
    const output: Record<string, T> = Object.create(null);
    for (const [key, item] of Object.entries(input)) {
      if (!pattern.test(key)) return invalid();
      output[key] = decode(item);
    }
    return Object.freeze(output);
  };
}
function union<const T extends readonly Decoder<unknown>[]>(
  ...decoders: T
): Decoder<ReturnType<T[number]>> {
  return (value) => {
    for (const decode of decoders) {
      try {
        return decode(value) as ReturnType<T[number]>;
      } catch (error) {
        if (!(error instanceof ApiFailure)) throw error;
      }
    }
    return invalid();
  };
}

const pUuid = string(
  0,
  Number.MAX_SAFE_INTEGER,
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![\s\S])/u,
);
const pRevision = string(0, 1024, /^[1-9][0-9]*(?![\s\S])/u);
const pPublicationRef = object({ objectId: pUuid, workspaceRevision: pRevision }, {});
const pId = string(0, 64, /^[a-z][a-z0-9.-]{0,63}(?![\s\S])/u);
const pCreatePlan = object(
  {
    expectedRevision: literal("0"),
    requestId: pUuid,
    definition: pPublicationRef,
    bindingId: pId,
    destinationId: pId,
  },
  {},
);
const pReserveInspection = object(
  { expectedRevision: pRevision, requestId: pUuid, discardDraftOnSuccess: literal(true) },
  {},
);
const pAck = object({ planId: pUuid, revision: pRevision }, { operationId: pUuid });
const pCode = string(0, Number.MAX_SAFE_INTEGER, /^[A-Z][A-Z0-9_]{0,63}(?![\s\S])/u);
const pOperation = object(
  {
    operationId: pUuid,
    planId: pUuid,
    phase: choice("reserved", "running", "succeeded", "refused", "cancelled", "expired"),
    code: pCode,
    cleanup: choice("complete", "in-progress", "inconclusive"),
  },
  { installedRevision: pRevision },
);
const pCounts = object(
  { documents: integer(0, 128), entities: integer(0, 20000), relations: integer(0, 50000) },
  {},
);
const pObservedDestination = union(
  literal(null),
  object(
    {
      engine: literal("postgresql"),
      identity: object(
        {
          systemIdentifier: string(0, Number.MAX_SAFE_INTEGER, /^[1-9][0-9]{0,19}$/u),
          databaseOid: string(0, Number.MAX_SAFE_INTEGER, /^[1-9][0-9]{0,19}$/u),
          databaseName: string(
            1,
            128,
            // biome-ignore lint/suspicious/noControlCharactersInRegex: Contract excludes controls and unpaired surrogates.
            /^[^\u0000-\u001f\u007f-\u009f\ud800-\udfff]+$/u,
            /^[\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/u,
          ),
        },
        {},
      ),
      observationFingerprint: string(0, Number.MAX_SAFE_INTEGER, /^[a-f0-9]{64}$/u),
      evidenceValid: boolean,
    },
    {},
  ),
  object(
    {
      engine: literal("oracle"),
      identity: object(
        {
          dbid: string(0, Number.MAX_SAFE_INTEGER, /^[1-9][0-9]{0,19}$/u),
          dbUniqueName: string(
            1,
            128,
            // biome-ignore lint/suspicious/noControlCharactersInRegex: Contract excludes controls and unpaired surrogates.
            /^[^\u0000-\u001f\u007f-\u009f\ud800-\udfff]+$/u,
            /^[\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/u,
          ),
          conId: string(0, Number.MAX_SAFE_INTEGER, /^[1-9][0-9]{0,19}$/u),
          conUid: string(0, Number.MAX_SAFE_INTEGER, /^[1-9][0-9]{0,19}$/u),
          conName: string(
            1,
            128,
            // biome-ignore lint/suspicious/noControlCharactersInRegex: Contract excludes controls and unpaired surrogates.
            /^[^\u0000-\u001f\u007f-\u009f\ud800-\udfff]+$/u,
            /^[\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/u,
          ),
          pdbGuid: string(0, Number.MAX_SAFE_INTEGER, /^[a-f0-9]{32}$/u),
        },
        {},
      ),
      observationFingerprint: string(0, Number.MAX_SAFE_INTEGER, /^[a-f0-9]{64}$/u),
      evidenceValid: boolean,
    },
    {},
  ),
);
const pComputedCounts = object(
  { nodes: integer(0, 20000), memberships: integer(0, 50000), cooccurrences: integer(0, 50000) },
  {},
);
const pPlanSummary = object(
  {
    planId: pUuid,
    revision: pRevision,
    definition: pPublicationRef,
    bindingId: pId,
    destinationId: pId,
    currentCounts: pCounts,
    targetCounts: pCounts,
    inspectionValid: boolean,
    targetComplete: boolean,
    exportAvailable: literal(false),
    blockers: array(pCode, 0, 256, false),
    observedDestination: pObservedDestination,
    currentComputedCounts: union(literal(null), pComputedCounts),
    targetComputedCounts: union(literal(null), pComputedCounts),
  },
  { activeOperationId: pUuid },
);
const pMaterialization = object(
  {
    revision: pRevision,
    state: choice("COMPLETE", "INCOMPLETE", "REFUSED"),
    complete: boolean,
    diagnostics: array(string(1, 129, undefined), 0, 256, false),
  },
  {},
);
const cRevision = string(0, 1024, /^[1-9][0-9]*(?![\s\S])/u);
const cUuid = string(
  0,
  Number.MAX_SAFE_INTEGER,
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![\s\S])/u,
);
const cExisting = object({ kind: literal("existing"), handle: cUuid }, {});
const cFieldState = union(
  object({ kind: literal("unresolved") }, {}),
  object({ kind: literal("entered"), text: string(0, 1048576, undefined) }, {}),
  object({ kind: literal("keep-observed") }, {}),
  object({ kind: literal("absent") }, {}),
);
const cId = string(0, 64, /^[a-z][a-z0-9.-]{0,63}(?![\s\S])/u);
const cFresh = object({ kind: literal("fresh"), slotId: cId, typeId: cId }, {});
const cEntityRef = union(cExisting, cFresh);
const cReferenceState = union(
  object({ kind: literal("unresolved") }, {}),
  object({ kind: literal("to"), target: cEntityRef }, {}),
  object({ kind: literal("keep-observed") }, {}),
  object({ kind: literal("absent") }, {}),
);
const cEntityDecision = union(
  object(
    {
      kind: literal("retain"),
      entity: cExisting,
      fields: dictionary(cFieldState, /^[a-z][a-z0-9.-]{0,63}(?![\s\S])/u, 256),
      references: dictionary(cReferenceState, /^[a-z][a-z0-9.-]{0,63}(?![\s\S])/u, 256),
    },
    {},
  ),
  object(
    {
      kind: literal("create"),
      entity: cFresh,
      fields: dictionary(cFieldState, /^[a-z][a-z0-9.-]{0,63}(?![\s\S])/u, 256),
      references: dictionary(cReferenceState, /^[a-z][a-z0-9.-]{0,63}(?![\s\S])/u, 256),
    },
    {},
  ),
  object({ kind: literal("remove"), entity: cExisting }, {}),
);
const cContainment = object({ relationId: cId, parent: cEntityRef, child: cEntityRef }, {});
const cSha256 = string(0, Number.MAX_SAFE_INTEGER, /^[0-9a-f]{64}(?![\s\S])/u);
const cParent = union(
  object(
    {
      kind: literal("existing"),
      documentId: cId,
      sourceDigest: cSha256,
      elementIndex: string(0, Number.MAX_SAFE_INTEGER, /^(0|[1-9][0-9]{0,4})(?![\s\S])/u),
    },
    {},
  ),
  object({ kind: literal("fresh"), entity: cFresh }, {}),
);
const cPlacement = object(
  { entity: cEntityRef, documentId: cId, projectionId: cId, parent: cParent },
  {},
);
const cDraft = object(
  {
    entities: array(cEntityDecision, 0, 20000, false),
    containment: array(cContainment, 0, 20000, false),
    placements: array(cPlacement, 0, 20000, false),
  },
  {},
);
const cPublicationRef = object({ objectId: cUuid, workspaceRevision: cRevision }, {});
const cCompositionDecision = union(
  object({ kind: literal("create"), slotId: cId, targetSlotId: cId }, {}),
  object({ kind: literal("use-existing"), slotId: cId, target: cEntityRef }, {}),
  object({ kind: literal("cancel"), slotId: cId }, {}),
);
const cCommand = union(
  object(
    {
      kind: literal("replace-draft"),
      expectedRevision: cRevision,
      requestId: cUuid,
      draft: cDraft,
    },
    {},
  ),
  object(
    {
      kind: literal("batch-upsert"),
      expectedRevision: cRevision,
      requestId: cUuid,
      changes: array(
        object({ decision: cEntityDecision, placements: array(cPlacement, 0, 20000, false) }, {}),
        1,
        20000,
        false,
      ),
      containment: array(cContainment, 0, 20000, false),
    },
    {},
  ),
  object(
    {
      kind: literal("upsert-entity"),
      expectedRevision: cRevision,
      requestId: cUuid,
      decision: cEntityDecision,
      placements: array(cPlacement, 0, 20000, false),
    },
    {},
  ),
  object(
    {
      kind: literal("forget-entity-decision"),
      expectedRevision: cRevision,
      requestId: cUuid,
      entity: cEntityRef,
    },
    {},
  ),
  object(
    {
      kind: literal("bind-field"),
      expectedRevision: cRevision,
      requestId: cUuid,
      entity: cEntityRef,
      fieldId: cId,
      state: cFieldState,
    },
    {},
  ),
  object(
    {
      kind: literal("bind-reference"),
      expectedRevision: cRevision,
      requestId: cUuid,
      entity: cEntityRef,
      relationId: cId,
      state: cReferenceState,
    },
    {},
  ),
  object(
    {
      kind: literal("move-containment"),
      expectedRevision: cRevision,
      requestId: cUuid,
      decision: cContainment,
      placements: array(cPlacement, 0, 20000, false),
    },
    {},
  ),
  object(
    {
      kind: literal("compose-profile"),
      expectedRevision: cRevision,
      requestId: cUuid,
      profile: cPublicationRef,
      previewDigest: cSha256,
      selectedRoots: array(cId, 1, 20000, true),
      decisions: array(cCompositionDecision, 0, 20000, false),
    },
    {},
  ),
  object({ kind: literal("discard"), expectedRevision: cRevision, requestId: cUuid }, {}),
);
const wRevision = cRevision;
const wId = cId;
const wExisting = cExisting;
const wLabel = string(1, 128, undefined);
const wCaptureRequest = object(
  {
    revision: wRevision,
    profileId: wId,
    profileRevision: wRevision,
    mappings: array(object({ entity: wExisting, slotId: wId, label: wLabel }, {}), 1, 20000, false),
  },
  {},
);
const wPublicationRef = cPublicationRef;
const wCaptureResponse = object(
  {
    revision: wRevision,
    definition: wPublicationRef,
    format: literal("json"),
    source: string(0, Number.MAX_SAFE_INTEGER, undefined),
  },
  {},
);
const wSection = choice("included", "dependencies", "relations", "conflicts");
const wOffset = integer(0, 50000);
const wLimit = integer(1, 100);
const wPreviewRequest = object(
  {
    revision: wRevision,
    profile: wPublicationRef,
    selection: union(
      object({ kind: literal("all") }, {}),
      object({ kind: literal("selected"), roots: array(wId, 1, 20000, true) }, {}),
    ),
    section: wSection,
    offset: wOffset,
    limit: wLimit,
  },
  {},
);
const wSha256 = cSha256;
const wUuid = cUuid;
const wPreviewPins = object(
  {
    planId: wUuid,
    revision: wRevision,
    observationFingerprint: wSha256,
    profile: wPublicationRef,
    publicationDigest: wSha256,
    selectedRoots: array(wId, 1, 20000, true),
    rootsDigest: wSha256,
    closureDigest: wSha256,
  },
  {},
);
const wPreviewIncludedResponse = object(
  {
    revision: wRevision,
    total: integer(0, Number.MAX_SAFE_INTEGER),
    offset: wOffset,
    nextOffset: union(wOffset, literal(null)),
    items: array(
      object(
        { slotId: wId, typeId: wId, label: wLabel, requiredInputs: array(wId, 0, 256, false) },
        {},
      ),
      0,
      100,
      false,
    ),
    previewDigest: wSha256,
    pins: wPreviewPins,
    section: literal("included"),
    affectedDerivations: array(wId, 0, 32, true),
  },
  {},
);
const wPreviewDependenciesResponse = object(
  {
    revision: wRevision,
    total: integer(0, Number.MAX_SAFE_INTEGER),
    offset: wOffset,
    nextOffset: union(wOffset, literal(null)),
    items: array(
      object(
        {
          slotId: wId,
          causedBy: wId,
          relationId: wId,
          reason: choice("required-reference", "containment-parent", "declared-reuse-target"),
        },
        {},
      ),
      0,
      100,
      false,
    ),
    previewDigest: wSha256,
    pins: wPreviewPins,
    section: literal("dependencies"),
    affectedDerivations: array(wId, 0, 32, true),
  },
  {},
);
const wPreviewRelationsResponse = object(
  {
    revision: wRevision,
    total: integer(0, Number.MAX_SAFE_INTEGER),
    offset: wOffset,
    nextOffset: union(wOffset, literal(null)),
    items: array(object({ relationId: wId, fromSlot: wId, toSlot: wId }, {}), 0, 100, false),
    previewDigest: wSha256,
    pins: wPreviewPins,
    section: literal("relations"),
    affectedDerivations: array(wId, 0, 32, true),
  },
  {},
);
const wPreviewConflictsResponse = object(
  {
    revision: wRevision,
    total: integer(0, Number.MAX_SAFE_INTEGER),
    offset: wOffset,
    nextOffset: union(wOffset, literal(null)),
    items: array(
      union(
        object(
          {
            code: choice(
              "RELATION_CARDINALITY",
              "MULTIPLE_CONTAINMENT_PARENTS",
              "CONTAINMENT_PARENT_MISSING",
            ),
            slotId: union(wId, literal(null)),
            relationId: wId,
            ruleId: literal(null),
          },
          {},
        ),
        object(
          {
            code: choice("INVALID_TARGET_RELATION", "CONTAINMENT_CYCLE"),
            slotId: literal(null),
            relationId: literal(null),
            ruleId: literal(null),
          },
          {},
        ),
        object(
          {
            code: choice("ENTITY_COUNT"),
            slotId: literal(null),
            relationId: literal(null),
            ruleId: wId,
          },
          {},
        ),
      ),
      0,
      100,
      false,
    ),
    previewDigest: wSha256,
    pins: wPreviewPins,
    section: literal("conflicts"),
    affectedDerivations: array(wId, 0, 32, true),
  },
  {},
);
const wPreviewResponse = union(
  wPreviewIncludedResponse,
  wPreviewDependenciesResponse,
  wPreviewRelationsResponse,
  wPreviewConflictsResponse,
);
const wRevisionRequest = object({ revision: wRevision }, {});
const wValidationPageRequest = object(
  {
    revision: wRevision,
    section: literal("computed-rules"),
    inputFingerprint: wSha256,
    offset: integer(0, 2147483647),
    limit: wLimit,
  },
  {},
);
const wKey = object(
  {
    computedType: wId,
    derivation: wId,
    value: string(
      1,
      1048576,
      // biome-ignore lint/suspicious/noControlCharactersInRegex: XML text admits TAB, LF and CR only among controls.
      /^[\u0009\u000a\u000d\u0020-\ud7ff\ue000-\ufffd\u{10000}-\u{10ffff}]+(?![\s\S])/u,
    ),
  },
  {},
);
const wDecimal = string(0, Number.MAX_SAFE_INTEGER, /^(0|[1-9][0-9]*)(?![\s\S])/u);
const wRule = object(
  {
    kind: choice("ENTITY_COUNT", "COOCCURRENCE"),
    declaration: wId,
    source: union(wKey, literal(null)),
    actual: wDecimal,
    minimum: wDecimal,
    maximum: wDecimal,
    outcome: choice("PASS", "FAIL"),
  },
  {},
);
const wValidationPageResponse = object(
  {
    revision: wRevision,
    inputFingerprint: wSha256,
    targetComplete: literal(true),
    total: integer(0, Number.MAX_SAFE_INTEGER),
    offset: integer(0, 2147483647),
    nextOffset: union(integer(0, 2147483647), literal(null)),
    items: array(wRule, 0, 100, false),
  },
  {},
);
const wOutcome = choice("PASS", "FAIL", "UNKNOWN", "ERROR");
const wValidationSummaryResponse = object(
  {
    revision: wRevision,
    inputFingerprint: wSha256,
    checks: tuple(
      object({ check: literal("SCOPE"), outcome: wOutcome, inputFingerprint: wSha256 }, {}),
      object({ check: literal("DEFINITION"), outcome: wOutcome, inputFingerprint: wSha256 }, {}),
      object({ check: literal("MAPPING"), outcome: wOutcome, inputFingerprint: wSha256 }, {}),
      object({ check: literal("VALUES"), outcome: wOutcome, inputFingerprint: wSha256 }, {}),
      object({ check: literal("SEMANTICS"), outcome: wOutcome, inputFingerprint: wSha256 }, {}),
      object({ check: literal("XML_FIDELITY"), outcome: wOutcome, inputFingerprint: wSha256 }, {}),
      object({ check: literal("DESTINATION"), outcome: wOutcome, inputFingerprint: wSha256 }, {}),
      object(
        { check: literal("CLIENT_CAPABILITY"), outcome: wOutcome, inputFingerprint: wSha256 },
        {},
      ),
      object(
        { check: literal("CONTENT_POLICY"), outcome: wOutcome, inputFingerprint: wSha256 },
        {},
      ),
      object({ check: literal("REVIEW"), outcome: wOutcome, inputFingerprint: wSha256 }, {}),
    ),
    applicationRules: array(
      object({ ruleId: wId, outcome: wOutcome }, {}),
      0,
      Number.MAX_SAFE_INTEGER,
      false,
    ),
    exportAvailable: literal(false),
    targetComplete: boolean,
    computedRuleCount: union(integer(0, Number.MAX_SAFE_INTEGER), literal(null)),
  },
  {},
);

export const uuid = pUuid;
// Shared closed primitives; plan decoding remains unchanged.
export { array, choice, dictionary, integer, literal, object, string, union };
export const booleanValue = boolean;
export const existing = cExisting;
export const fresh = cFresh;
export const entityRef = cEntityRef;
export const placement = cPlacement;
export const revision = pRevision;
export const declaredId = pId;
export const digest = cSha256;
export const publicationRef = pPublicationRef;
export const createPlan = pCreatePlan;
export const reserveInspection = pReserveInspection;
export const ack = pAck;
export const operation = pOperation;
export const summary = pPlanSummary;
export const materialization = pMaterialization;
export const command = cCommand;
export const captureRequest = wCaptureRequest;
export const captureResponse = wCaptureResponse;
export const previewRequest = wPreviewRequest;
export const previewResponse = wPreviewResponse;
export const revisionRequest = wRevisionRequest;
export const validationPageRequest = wValidationPageRequest;
export const validationPageResponse = wValidationPageResponse;
export const validationSummary = wValidationSummaryResponse;
