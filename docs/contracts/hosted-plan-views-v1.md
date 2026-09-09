# Hosted plan inspection and composition views — D06b3

This extends [the initial plan HTTP contract](hosted-plan-http-v1.md) and
[hosted plan authority](hosted-plans-v1.md). These eleven routes are implemented;
see [integration evidence](../evidence/d06b3-integration.md). The separate
[complete value mapping contract](hosted-plan-context-v1.md) adds two binding/location
routes and provenance-stable document tokens under the same admission rules. Their closed schemas
and boundary tests preceded implementation. They never accept source XML, a graph, validation PASS or an
export capability from the browser. Export/review/readback remain a subsequent
qualified route group.

## Shared request and response rules

All routes below use POST with the same live lease, ownership, Host/Origin/CSRF
and no-store enforcement as commands. Read-like bodies identify the exact plan
revision without putting disclosure consent in a URL. They do not change semantic
revision, consume a command replay ID or persist results. Ordinary user actions
may touch idle expiry; operation polling remains explicitly non-touching.
Check authority both before work and before publishing its response. A race with
revision, observation replacement, discard or revocation refuses the result.

Every request object is closed; no omitted default, duplicate/unknown property,
extra root, malformed Unicode or noncanonical integer is accepted. `revision` is
a canonical positive decimal string. Plan/object/handle IDs and Existing/Fresh
references use the initial HTTP vocabulary. Returned graph handles use the same
observation-bound mapping used by commands; raw logical identity tuples are never
wire handles. Fresh provenance remains explicit across materialization.

Unless otherwise specified, bodies use the existing 16 KiB/depth-4/128-token,
10-second small-reader limits and four immediate reader slots. Collection-bearing
capture/preview requests instead use a separate admitted semantic scratch read,
64 MiB/depth-8/1,000,000-token ceiling and 30-second absolute read deadline. They
share the existing single full scratch admission, with no executor queue or second
raw-body copy. A count within a semantic limit does not waive the wire limit.

Read responses use an admitted, bounded encoder with a 128 MiB wire ceiling and
the existing scratch budget; never retain many encoded pages in a session. Paging
does not retain snapshots or grant authority: each page rechecks the same live
revision. These eleven routes always provide `offset` and `limit` on page requests, canonical
integer tokens with offset 0–50,000 and limit 1–100. The binding extension defines
its own field and complete-location offset bounds. Responses include `revision`, `total`,
`offset`, `nextOffset` (integer or null) and `items`. Stable ordering and complete
totals apply to the whole selected scope, not only rendered rows. Reject unknown
enum values rather than returning an empty success.
Missing current/target content refuses the affected graph request; it is not an
empty successful page. If a lower-level diagnostic collection was capped, compute
the complete count/items or return an explicit resource refusal. Never report a
truncated collection's length as its complete total.

## Closed routes

All paths are below `/api/v1/plans/{planId}`.

| Route | Exact request | Exact successful response |
| --- | --- | --- |
| `/materializations` | `{revision}` | `{revision, complete, diagnostics}`; diagnostics are stable safe code strings |
| `/views/documents` | `{revision}` | `{revision, documents}` with ordered `{documentId, currentDigest, targetDigest, changed}` entries |
| `/views/entities` | `{revision, side, offset, limit}` | Page of entity items defined below |
| `/views/relations` | `{revision, side, offset, limit}` | Page of `{relationId, from, to}` using opaque entity references |
| `/views/draft` | `{revision, offset, limit}` | Page of explicit draft items defined below |
| `/views/containment` | `{revision, offset, limit}` | Page of explicit `{relationId, parent, child}` draft decisions using entity references |
| `/views/placements` | `{revision, documentId, projectionId, offset, limit}` | Page of eligible original-parent coordinates defined below |
| `/views/document` | `{revision, side, documentId, mode, completeDocumentDisclosure: true}` | `{revision, documentId, side, mode, text, exact, redacted, unmappedConcreteMayRemain, omissions}` |
| `/profile-captures` | `{revision, profileId, profileRevision, mappings}` | `{revision, definition, format: "json", source}` |
| `/profile-previews` | `{revision, profile, selection, section, offset, limit}` | Closed preview page defined below |
| `/validations` | `{revision}` | `{revision, inputFingerprint, checks, applicationRules, exportAvailable}` |

`side` is `current` or `target`; `mode` is `raw`, `placeholders` or `formatted`.
All listed properties are required; null occurs only where explicitly allowed.
No GET/reveal alias exists. A false or absent disclosure acknowledgement refuses
before document formatting. Current/target text may contain unmapped concrete or
secret values even in placeholders mode; the UI must disclose that before the
operator submits this acknowledgement. Raw/formatted views still refuse mapped
fields whose definition denies reading. Display formatting never changes a plan
or supplies writer input. `omissions` is an array of tool-owned code strings.

Document listing uses the complete observed inventory, sorted by document ID.
`currentDigest` is the complete source SHA-256; `targetDigest` is its complete
target SHA-256 or null while no target exists. `changed` is boolean or null with
no target. Never call an absent/incomplete target unchanged. A failed inspection
may leave current content visible with the plan's inspection-invalid status; this
display state cannot authorize capture, composition or export.

Entity items contain exactly `{entity, typeId, fields}`. Each field contains
`fieldId`, `present`, `masked` and `value`; value is string or null, and must be
null when masked or absent. Use declared readability and sensitivity, not a
browser reveal flag. Fields sort by ID; entities sort by their opaque stable
reference encoding. Relations sort by relation ID then source/target reference.
All relation endpoints must refer to the selected side's graph. No partial graph
pretends to be a complete total.

Draft items contain exactly `{entity, disposition, fields, references, placements}`.
Disposition is `retain`, `create` or `remove`. Each field is
`{fieldId, kind, masked, value}`, where kind is `unresolved`, `entered`,
`keep-observed` or `absent`. Only readable non-secret entered text may occupy
value; otherwise it is null. Reference items are `{referenceId, kind, target}`,
where kind is `unresolved`, `to`, `keep-observed` or `absent`; target is an entity
reference only for `to`, otherwise null. Placements use the existing command
shape. Remove has empty field/reference/placement arrays. Missing overrides stay
absent from this listing, rather than being inferred into KeepObserved decisions.
This masked view is not a replacement command body; the UI uses the field-level
commands to preserve other decisions it cannot read.
Explicit containment decisions sort by relation ID and child reference; they
remain inspectable even when an incomplete draft has no materialized target.

Eligible-parent items are exactly `{documentId, sourceDigest, elementIndex}`,
with canonical nonnegative decimal string elementIndex. Resolve them from the
original source and selected published projection's required parent path; never
infer a parent from a label or return unmatched coordinates. Sort by element
index. This list helps the operator make an explicit placement; the subsequent
command still revalidates every coordinate. Fresh-parent choices come from
explicit Fresh entities and do not invent original source coordinates.

Capture mappings are a complete nonempty bijection of observed entities:
`[{entity, slotId, label}]`, at most 20,000. Each entity must be Existing in the
current observation; Fresh or target-only mappings refuse. Profile ID, neutral
slot ID and label follow profile-v2; `profileRevision` is a positive decimal
string. The server builds value-free portable JSON, applies existing profile wire
limits and returns it with the exact published definition reference. Labels and
slots are explicitly entered, never copied/inferred from donor values. Capture
does not save or publish: those are separate explicit owned workspace operations.

For preview, `profile` is the exact owned published workspace reference.
`selection` is either `{kind: "all"}` or `{kind: "selected", roots: [...]}` with
1–20,000 unique neutral slot IDs. `section` is `included`, `dependencies`,
`relations` or `conflicts`. Return exactly `{revision, previewDigest, pins,
section, total, offset, nextOffset, items}`. `pins` is the exact eight-field object
in the initial HTTP preview-digest contract, with normalized complete roots.
Item shapes respectively are:

- included: `{slotId, typeId, label, requiredInputs}`; requiredInputs sorted IDs;
- dependencies: `{slotId, causedBy, relationId, reason}`, with reason
  `required-reference`, `containment-parent` or `declared-reuse-target`;
- relations: `{relationId, fromSlot, toSlot}`;
- conflicts: `{code, slotId, relationId, ruleId}`; all properties are required.
  The closed codes are `RELATION_CARDINALITY`, `INVALID_TARGET_RELATION`,
  `MULTIPLE_CONTAINMENT_PARENTS`, `CONTAINMENT_PARENT_MISSING`,
  `CONTAINMENT_CYCLE` and `ENTITY_COUNT`. Unavailable coordinates are null,
  never empty or invented identifiers. `ENTITY_COUNT` identifies its declared
  count rule through `ruleId`, with null `slotId` and `relationId`; every other
  code has null `ruleId`. Translate legacy internal diagnostic coordinates at
  the adapter boundary; never expose a rule ID as a relation ID. Unknown codes
  refuse until the contract is extended.

Each page independently resolves the immutable profile and recomputes the same
closure/digest against the live plan. Sort included by slot ID; other sections by
their displayed tuple order. Pages with different pins must never be combined in
the UI. Selection is advisory preview until the explicit compose-profile command;
that command rechecks every pin and preserves explicit prior values/unselected
siblings as already contracted.

Validation checks are ordered by the ten RequiredCheck categories and contain
exactly `{check, outcome, inputFingerprint}`. Application rules are ordered
`{ruleId, outcome}` entries for every compiled rule. Outcomes are `PASS`, `FAIL`,
`UNKNOWN` or `ERROR`; no omitted required check/rule is success. The fingerprint
is derived in Java from all frozen inputs. `exportAvailable` reflects actual
private server authority; it remains false while required D07/review/content
evidence is missing. A validation response cannot be submitted as export evidence.

## Acceptance boundary

Exercise whole/partial preview pagination and stale pins; explicit capture without
donor values or automatic persistence; Existing/Fresh consistency after repeated
materialization; explicit coordinate placement; masked draft updates that preserve
unreadable values; complete counts and missing-target display; all three document
modes/disclosure denial; malformed/foreign/stale/revoked requests; and every
required validation check/rule. Use the existing independent one-to-two expected
XML for the integrated HTTP path. Qualify response encoding, cancel/revoke races,
actual body deadlines and credential/value canaries before enabling these routes.
