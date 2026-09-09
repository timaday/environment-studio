# Plan context and complete value mapping — D06b4

This contract defines plan context and the binding rail in
[the Midnight UX contract](../ux/design-system.md). Operator labels and observed
identity below remain planned. The binding/location APIs and stable document
placeholders have a local implementation candidate; see
[its evidence](../evidence/d06b4-bindings.md). Closed schemas accompany that code.
Backend route availability does not approve or advertise a new browser design. Existing
[hosted authority](hosted-plans-v1.md) and [bounded view transport](hosted-plan-views-v1.md)
remain mandatory. No new design is approved by this API contract.

## Operator labels and observed identity

Creation adds required `labels: {plan, intendedEnvironment}` to the closed create
command. Each label is exact scalar Unicode, 1–128 code points and at most 512 UTF-8
bytes, with no control characters or all-whitespace value. Do not trim, normalize,
derive labels from credentials/configuration or invent an environment identity.
Labels live in owned session memory only. Include them in creation's command HMAC;
exact uncertain-command replay cannot silently create a differently labelled plan.

A `set-labels` semantic command contains expectedRevision, requestId, kind and the
same complete labels object. It increments the plan revision once, preserves current
and target content/draft, and invalidates validation/review/artifact authority.
Atomically bind any retained complete target to the new revision without rewriting
its bytes or changing its provenance. Old revision-bound pages remain stale; this
content-preserving update does not reuse old validation or artifact authority.
It obeys the usual busy, owner, expiry and exact-replay rules. Labels describe
operator intent; they never authorize a destination or establish actual DB identity.

The current/by-ID plan summary adds the required labels object and required
`observedDestination`, null before successful inspection. Once installed, this is
exactly `{engine, identity, observationFingerprint, evidenceValid}`. Engine and closed
physical identity fields match the observation contract; fingerprint is 64 lowercase
hex, and evidenceValid equals the observation-validity latch for that retained
observation. Obtain this object only from the configured adapter's complete result,
after identity comparison, metadata validation, full projection and confirmed cleanup.
A malformed/missing identity or policy pin refuses installation; never pass through
an arbitrary evidence map or substitute configured expected identity.

Reinspection success replaces it atomically with content; failure retains the old
observed identity marked evidenceValid=false. Discard/revocation removes view
authority immediately, including while physical cleanup retains quarantined memory;
confirmed cleanup then releases the retained content. The UI distinguishes configured destination, operator
intent and observed physical identity; a stale observation is never a live connection
indicator. Expected/provisioning records, trust paths, owners and credentials remain
server-side. Labels and observed identity have redacted toString/log behavior.

## Stable placeholder tokens

A placeholder denotes one logical field on one provenance-stable entity. Its token
is `[[value:<handle>:<fieldId>]]`, using an opaque entity UUID handle and
allowlisted definition field ID. Existing entities use their observation-bound
handles. Allocate a distinct server-generated handle when a Fresh `{slotId,typeId}`
creation is first admitted, before it can appear in a binding page. That handle is
display identity only: commands still address Fresh entities through their explicit
slot/type provenance. Allocation must reject a collision with any live handle.
The same Existing entity keeps its token when its concrete identity changes. A
Fresh handle survives edits and failed/repeated materialization while its creation
remains in the draft. Dropping only the materialized target must not drop that
handle. Removing a Fresh creation retires its handle; a later creation with the
same slot/type receives a new one. Keep only live Existing/Fresh provenance, at
most 20,000 handles of each kind; no unbounded history or page-created handles.
No row number, raw logical identity, document ordering or current-side position
creates a token. Newly inspected plans receive new observation-bound handles.

Use this token at every mapped occurrence of that field. A mapped reference to an
entity uses the token for that target entity's identity field, so identity changes
are visible through the rail at every referring location. Reference retargeting
changes the token to the selected target entity. Do not replace unmapped lookalike
strings. Backend projection resolves all occurrences by exact expanded names,
qualified attribute spans and complete graph/provenance; it cannot guess a match.

The existing document view's placeholder projection uses these tokens, with the
same explicit complete-document disclosure and unmapped-concrete-value warning.
Raw and Formatted remain distinct read-only views; none changes export bytes.

## Binding and location pages

All routes below are POST under `/api/v1/plans/{planId}`. They require the current
live owner, Host/Origin/CSRF, exact revision, no-store, one admitted view scratch,
bounded complete encoding and authority rechecks through transfer. Each small
request obeys the existing 16 KiB/depth-4/128-token/10-second reader limits. Responses
use the existing 128 MiB ceiling. No endpoint persists page cursors or old snapshots.

`/views/bindings` accepts exactly `{revision, entity, offset, limit}`. Entity is an
existing opaque reference or explicit Fresh reference already represented in current
or draft/target provenance. Offset is 0–256 and limit 1–100. Return the usual
`{revision, total, offset, nextOffset, items}` page, ordered by field ID. Total covers
all fields declared on that entity type, including optional absence and unresolved
target decisions; no filter changes the count.

Each item is exactly `{fieldId, token, current, target, change, currentLocations,
targetLocations}`. A value is a closed tagged object:

- `{state: "value", text}` for a readable non-secret present exact value;
- `{state: "masked"}` for a present secret/unknown/unreadable value;
- `{state: "absent"}` for confirmed absence;
- `{state: "unresolved"}` when the target field decision is incomplete;
- `{state: "unavailable"}` when the side's entity/content is unavailable.

Do not use null, empty text or masking as an absence/unresolved surrogate. A present
empty string remains value with empty text. Change is `unchanged`, `changed`, `added`,
`removed` or `unresolved`, derived by Java from actual sides and typed decisions.
Masked binding values never include raw text, lengths, hashes or a reveal action.
The separate complete-document disclosure boundary below governs raw coordinates.

Bindings require current observed content; otherwise refuse `INSPECTION_REQUIRED`.
Resolve each side in this order:

| Side/context | Field state |
| --- | --- |
| Current Existing entity | Exact observed value or confirmed optional absence, then apply masking |
| Current Fresh entity | `unavailable`: this provenance has no observed entity |
| Complete materialized target | Exact target value or confirmed absence, including removal of an Existing entity, then apply masking |
| Incomplete target, explicit removal | `absent` for every field of that Existing entity |
| Incomplete target, unselected Existing entity | Retained observed field, with masking |
| Incomplete target, selected retain/create | Entered value, explicit absence or Existing KeepObserved resolves that field; an unresolved/missing decision remains `unresolved` |

A partially resolved draft may therefore show resolved intended field values while
another decision blocks materialization. These values do not assert that target
XML or export is available. Never carry forward an earlier target's values after
its revision becomes stale. Fresh KeepObserved and invalid entity/field provenance
refuse; they are not unresolved success. A removed Fresh creation is no longer an
addressable entity, even if a caller retained its old token.

Derive `change` in the following order. Any unresolved field or unavailable side
produces `unresolved`, except that the known missing current entity of a live Fresh
creation counts as absence for this comparison. Absence on both sides is
`unchanged`; absent to present is `added`; present to absent is `removed`. Two
readable present values compare exact scalar text, giving `unchanged` or `changed`.
When both sides are present and either is masked, return `unresolved`; two masks
must not imply equality or disclose a secret-value equality oracle. This field
comparison does not replace entity disposition or document-level change evidence.

Each location-count property is exactly `{state: "complete", total}` or
`{state: "unavailable", code}`, where code is `CURRENT_ENTITY_ABSENT` for a Fresh
current side or `INCOMPLETE_TARGET` for missing target materialization. A complete
count is a nonnegative JSON integer and covers every mapped field/reference
occurrence across all documents on that side. Count zero asserts a complete
search, including an absent optional field or a removed Existing target entity;
it never stands for missing content. A resolved draft value alone cannot supply
target locations. Do not truncate or report a partial total as complete. The
location route deliberately extends the usual 50,000 offset ceiling so every
occurrence within the accepted document/graph scope remains addressable.

`/views/binding-locations` accepts exactly `{revision, entity, fieldId, side, offset,
limit, completeDocumentDisclosure: true}`; side is current/target, offset
0–2,147,483,647 and limit 1–100. Every location request requires the same explicit
complete-document disclosure acknowledgement as Raw/Formatted document views.
Exact span endpoints reveal lexical lengths, and later public offsets can reveal
earlier masked lengths; checking only the requested field is insufficient. This
boundary applies to both sides, all classifications and empty/beyond-end pages.
Missing, false or non-boolean acknowledgement is malformed at the HTTP boundary;
a direct Java call without disclosure refuses `DISCLOSURE_REQUIRED` before scanning.
The masked binding rail and its complete occurrence counts remain available
without this acknowledgement. Disclosure does not reveal values in that rail. The same page
envelope gives the complete location count. Unknown field/entity refuses; an
existing field with no occurrences returns total=0. Missing target content is an
`INCOMPLETE_TARGET` refusal, not an empty successful target page. The current
side of a Fresh entity refuses `NOT_FOUND`. A removed Existing entity has a
successful zero-location target page only when the target is materialized.

Each location is exactly `{documentId, sourceDigest, projectionId, elementIndex,
attribute, span, role, declarationId}`. Attribute is the expanded name
`{namespaceUri, localName}`. ElementIndex is a canonical nonnegative decimal
string, as in eligible-parent coordinates. Span is `{start, end}` with canonical
nonnegative JSON integers in zero-based UTF-16 indices in
the exact raw source, covering only the attribute value, with end exclusive.
Role is field/reference; declarationId is the mapped field/relation ID. Sort by
document ID, element index, span start, role and declaration ID. Coordinates are
display/navigation evidence pinned to revision and sourceDigest, never edit authority.
Require `start <= end <= raw UTF-16 length`; an empty attribute value has equal
endpoints. Page total, offset and non-null nextOffset are canonical nonnegative
JSON integers; limit is a canonical positive integer. Digests are 64 lowercase hex.

For an identity field, include its own field occurrence and every declared inbound
reference occurrence targeting it, including unchanged documents. For other fields,
include their exact field occurrences only. Refuse missing/ambiguous provenance,
inconsistent values or incomplete mapping; do not silently omit an affected record.
The renderer maps these raw spans to its own display projection without treating
formatted offsets as source offsets. Keyboard navigation can visit every location.

## Required evidence

Use independent mock expected tokens, values and locations: identity rename with
references in multiple changed/unchanged documents; retargeted reference; Fresh
creation/removal/move; optional empty/absent/unresolved values; masked secrets;
reinspection and stale/revoked pages; complete pagination and invalid coordinates.
Demonstrate that both panes using the same token still expose changed concrete
current/target values, and that unselected siblings/lookalike text remain unchanged.
All current/target labels and source-derived identities stay memory-only, with log,
URL, disk and diagnostic canary checks. No user-facing capability claim follows
from a schema/example alone.
