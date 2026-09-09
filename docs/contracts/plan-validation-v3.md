# Shared internal v3 validation

Explicit validateV3 and ViewAdmission.validationV3 evaluate the selected frozen
v3 plan through the existing shared owner. They add no HTTP route, persisted
validation, review approval, export authority or runtime qualification. V2
validation and ES-PLAN-INPUT-1 bytes remain unchanged.

Require an original live owned revision, successful inspection, no active
inspection/materialization and the same pinned ViewAdmission scratch. Direct
validateV3 reserves/runs/closes that admission. Before evaluating, resolve the
selected definition through Workspace.definitionV3 again and require complete
equality with the pinned publication. The real lookup must recheck current
compiler qualification. Unsupported/default/mismatched publication refuses;
historical readiness or selected digest alone is insufficient.

Freshly verify complete original content and any retained actual target through
ContentAdapter.verifyV3 using the same original cancellation flag. Default ports
refuse. No target materialization, draft mutation, credential reservation or export
occurs during validation. Missing target yields an explicit incomplete result,
never an empty complete target. Invalidated inspection refuses INSPECTION_REQUIRED
even when retained current remains readable through comparison. Before/after
each bounded phase and final publication, retain original ownership/revision/
inspection/generation checks. Cancellation or a late workspace/proof result cannot
renew scratch or return successful validation.

## Result and checks

The redacted immutable V3Validation contains inputFingerprint, all ten ordered
RequiredCheck results, applicationRules for every declared physical count rule,
and optional complete computedRules. Present computedRules may be an empty list
only for an actual complete target with no applicable rule checks. Absent means
target unavailable. targetComplete reflects that distinction. exportAvailable
is always false in this slice and cannot be supplied by a caller.

SCOPE, DEFINITION and DESTINATION pass only after current inspection, fresh
publication and full original proof checks above. MAPPING, VALUES and XML_FIDELITY
remain UNKNOWN without a complete target and pass only after actual complete
target re-verification. SEMANTICS is UNKNOWN without a complete target; otherwise
it reflects all declared physical count and complete computed count/cardinality
outcomes (FAIL dominates PASS). CLIENT_CAPABILITY, CONTENT_POLICY and REVIEW stay
UNKNOWN because their required evidence is not implemented here. Never infer a
PASS from no returned checks. A validation response is inspectable data, not an
authorization token that can be submitted back for export.

Physical rule outcomes are sorted by rule ID and are UNKNOWN without a target.
With a complete target, count actual final physical entities for each declaration
and apply exact inclusive bounds. Preserve every complete computed rule check in
the qualified engine's order, including source-key roles, actual/minimum/maximum
and PASS/FAIL. Current graph rule failures remain visible in computed current
views; they cannot stand in for missing target evidence.

The existing physical graph validator refuses a graph violating a physical count
rule during projection/materialization. DerivedTargetMaterializer also preserves
its DERIVED_RULE_FAILED refusal before target materialization, as specified in
plan-content-v3.md. Preserve both earlier refusals. Validation does not admit a
rejected graph merely to display its failure: no complete target means UNKNOWN.
Complete current computed rule failures remain inspectable in current views.
The evaluator defensively aggregates FAIL from every supplied complete rule; this
does not claim the existing materializer admits a failed target.

## Separate input identity

Use existing NativeWorkspaceDigests typed framing and SHA-256, domain
ES-PLAN-INPUT-3. Strings, booleans, lists, maps and BigInteger retain that established
encoding; there is no JSON framing or change to existing domains. Hash these exact
map fields:

- planId, revision, definitionPublication, definitionReference (existing objectId/
  workspaceRevision pair), profilePublications (sorted complete list), bindingId,
  bindingDigest, logicalDigest and destinationId;
- originalPin and decisionPin, each with revisionToken, logicalDigest, bindingId,
  bindingDigest and documentDigests as supplied by the verified v3 pins;
- draft using existing closed DraftEncoding, current and target source lists of
  documentId/sourceDigest sorted by document ID, and targetComplete boolean;
- documentPolicies using existing sorted publication-policy encoding;
- mechanisms using every checked v3 mechanism ID and decimal string version;
- versions: compiler=native-compiler-v3, parser=woodstox-7.2.2-xml10-fifth-edition-patch1,
  writer=structural-target-v1, rules=generic-graph-v1, derived=derived-graph-v1;
- physicalRules as the complete rule-ID→outcome-name map and computedRulesDigest.

For a complete target, computedRulesDigest is NativeWorkspaceDigests.hash with
domain ES-PLAN-DERIVED-RULES-3 over its complete ordered rule list. Each rule map
has kind (enum name ENTITY_COUNT or COOCCURRENCE), declaration, source (empty list
or one map with computedType/derivation/value), actual, minimum, maximum (decimal
strings), and outcome (enum name). Missing target uses the empty string, distinct
from the digest of an empty complete list. Stream/lazily frame each rule map under
the live flag rather than retain another complete value-bearing rule encoding.
Actual full XML/provenance re-verification precedes identity; hashes never replace
complete proof equality. Incidental strings/logs do not reveal values or source.

## Acceptance and remaining gates

Use independently invented actual XML/compiler/content and explicit test-only
publication witnesses. Current-only, complete unchanged/edited target, unresolved
target, every physical/computed rule and fresh lookup refusal must remain distinct.
Exercise original/target proof forgery, identity/Fresh provenance, changed values/
policies/mechanisms/decisions, repeated deterministic identity, stale revision,
failed reinspection and owner close while proof/lookup is held. Test a literal
independent digest oracle and unchanged v2 identity. No test publication witness
qualifies the actual compiler. Combined graph/provenance/recomputation/hash/retained
target and response resources, cancellation latency, versioned HTTP/encoding,
client/review/content evidence and operational availability remain open.
