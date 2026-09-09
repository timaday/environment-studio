# Native definition v3 — authoritative derived declarations

Status: contract for the [approved derived direction](../product/derived-graph-decision.md).
Runtime v3 compilation, publication and plan support are unimplemented and
unqualified. A schema-valid document has no publication or execution authority.
This version is explicit opt-in; [v2](native-definition-v2.md) keeps its original
meaning, mechanisms, digest domains and historical codecs.

## Closed declarations

The required root properties are `schemaVersion: "3"`, `id`, positive integer
`revision`, `logical` and `bindings`. Source limits, duplicate-key handling,
strict Unicode, integer meaning and safe diagnostics follow
[definition compilation](definition-compilation.md). The source schema is
`schemas/definition-v3.schema.json`; the inspection schema uses canonical decimal
strings for integers, as in v2. Inspection is a display codec, not a second import
format or permission to trust a browser's compilation result.

The required `logical` properties are the four v2 physical declarations
`entityTypes`, `relations`, `rules`, `operationCapabilities`, plus the following
four arrays. Every listed object property is required; objects are closed.
There are no defaults, arbitrary expressions or extension bags.

| Array | Item properties | Meaning |
| --- | --- | --- |
| `computedTypes` | `id`, `label` | Separate computed type; no fields, XML projection or physical identity declaration |
| `derivations` | `id`, `sourceType`, `sourceField`, `computedType`, `membershipRelation` | Distinct values of one physical field, with directed physical-to-computed membership |
| `cooccurrences` | `id`, `fromDerivation`, `toDerivation`, `minimum`, `maximum` | Directed pairs contributed by the same physical occurrence; `id` is the relation ID |
| `computedRules` | `id`, `kind: "entity-count"`, `type`, `minimum`, `maximum` | Count instances of the specified computed type in the complete graph |

All four arrays may be explicitly empty. At most 32 computed types, 32 derivations
and 32 co-occurrences are allowed. IDs use the existing ASCII ID syntax; labels
are 1–128 Unicode code points. Counts use the bounded arbitrary-precision integer
contract: minimum >= 0, maximum >= 1 and minimum <= maximum. No maximum is an
allocation request or an override of the graph limits.

Physical and computed type IDs are disjoint. Each computed type has exactly one
derivation; each derivation names a known physical type, one of its fields and a
known computed type. Derivation IDs are unique. The field must be explicitly
`public`, readable and `text`; structural/environment classification does not
grant disclosure. Reject internal, secret, unknown, unreadable or non-text inputs
before evaluating any value, group, count, edge or value-dependent hash.

Physical relation IDs, membership relation IDs and co-occurrence IDs form one
unique relation namespace. Physical and computed count-rule IDs form one unique
rule namespace. Co-occurrence endpoints must name known derivations on the same
physical source type. Both endpoints may name the same derivation. Membership
has no uploaded cardinality: a required source field contributes exactly one
edge in a complete graph; an optional field contributes zero or one.

The four physical arrays and every binding retain v2 semantics, including
[child-property locators](child-property-v1.md). Physical relation endpoints,
count rules, projections, references, containment and planner operations may
name physical types/relations only. Computed types cannot be projected, created,
retained, removed, assigned fields or used as ordinary reference targets.
Derived relations never imply a physical edit or profile dependency.

Semantic errors reject with no checked model. Recognized, semantically valid
declarations requiring an unavailable mechanism return incomplete; unsupported
vocabulary rejects. A missing readable mapping remains physical publication
incompleteness, whereas an ineligible derivation input is a semantic error.
Compilation cannot establish actual source presence, matching-child cardinality,
value validity or runtime resource feasibility without complete observations.

## Compatibility bytes and mechanism ownership

Use the exact framing algorithm from v2: strict UTF-8 strings, mathematical
integers, booleans, arrays and objects sorted by unsigned UTF-8 key bytes, with no
nulls. Reject malformed Unicode before framing; no replacement-character encoding.
All IDs and capability tokens are ASCII, so their lexical and unsigned UTF-8
orders agree. The v3 logical normalization sorts physical arrays as v2 does,
sorts each added array by `id`, and removes only physical and computed type labels.
Every other declared logical property remains in the normalized object.

The v3 logical digest is SHA-256 of UTF-8 `ES-LOGICAL-3`, a zero byte and the
framed closed object `{logical, derivedSemantics}`. `logical` is the normalization
above. `derivedSemantics` is this exact server-owned object, never uploaded data:

```json
{
  "version": 1,
  "profileMode": "physical-only-v3",
  "maxDerivations": 32,
  "maxCooccurrences": 32,
  "maxTotalNodes": 20000,
  "maxTotalEdges": 50000,
  "maxContributorLinks": 100000,
  "maxIdentityUtf8Bytes": 8388608
}
```

`version: 1` pins the equality, disclosure, absence, membership, co-occurrence,
provenance, recomputation and validation semantics of
[derived-graph-v1](derived-graph-v1.md). Those rules and the bounds are normative.
Changing their meaning requires a new compatibility contract/version, not an
unannounced registry or deployment setting. Native ID/revision, labels, source
format, actual values, contributors and physical bindings do not enter the
logical digest. Even a v3 definition with empty derived arrays has a v3 digest;
it is never treated as a v2 definition with the same hash.

For each binding, normalize exactly as v2, including closed direct/child locators
and ordered paths. Hash UTF-8 `ES-BINDING-3`, zero byte, then the framed object
`{logicalDigest, mechanisms, binding}`. Exclude only the binding's metadata `id`;
retain semantic document/projection IDs. `mechanisms` is that binding's exact
required vector, containing integer versions:

| Mechanism | Version | Qualification boundary |
| --- | --- | --- |
| `native-compiler-v3` | 1 | Closed v3 semantics and compatibility |
| `xml-path-v1` | 1 | Physical selectors |
| `xml-span-v1` | 1 | Qualified physical XML mechanism |
| `generic-graph-v1` | 1 | Existing physical graph rules |
| `derived-graph-v1` | 1 | Independent computed graph and contributors |
| `xml-child-property-v1` | 1, only when this binding declares child fields | Direct-child selection and writing |

The checked definition records the union of its bindings' exact vectors. Adding
a mechanism to the server registry does not add it to unrelated binding digests.
Required versions are not availability assertions. The server keeps v3
availability disabled until all required paths are qualified; an internal
compiler or graph test cannot enable hosted publication, inspection or export.
Database/client, destination, policy, cleanup and deployment evidence remain
separate from compiler readiness.

## Version boundaries and acceptance

An existing v2 source, snapshot, profile, published revision or plan never acquires
derivations. Explicit v3 source, compilation and new publication are required.
Keep existing historical codecs and replay bytes unchanged. Workspace storage,
publication digests, HTTP responses, plan pins and guarded package codecs need
their own closed versioned extension before v3 can enter those paths. None is
authorized by merely changing a `schemaVersion` field or widening an old allowlist.
[Profile v3](profile-v3.md) defines a separate portable contract and digest domain.

Required compiler evidence includes unknown/extra vocabulary, disjoint namespaces,
missing or multiply assigned computed types, ineligible fields, mixed source
co-occurrences and computed declarations in physical paths. Independent digest
oracles must show order/label/revision invariance, logical changes on semantic
changes, binding-only changes on physical selectors, exact per-binding dependency
vectors, and unchanged v2 goldens. These are compiler/contract tests; runtime
acceptance belongs to the derived graph and integration contracts.
