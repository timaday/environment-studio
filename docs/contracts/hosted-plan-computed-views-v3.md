# Hosted v3 computed views

Five POST routes under `/api/v3/plans/{planId}/views/computed/` expose `nodes`,
`memberships`, `cooccurrences`, `rules` and `contributors`. They use the existing
[controlled computed adapter](plan-computed-views-v3.md), original fixed V3 plan
owner, Host/Origin/CSRF checks, full view scratch and one semantic transfer record.
Reserve before body access; close the first admission if the second refuses.
Original ViewAdmission.run spans parsing, pinning, rendering, encoding, output,
flush and final verification, including owned errors. No extra cache, registry,
physical permit, replay identifier or revision change is introduced.

The four collection requests are exactly `{revision,side,offset,limit}`.
Revision is the existing canonical positive decimal string (at most1024digits),
side is `current|target`, offset is a canonical JSON integer0..2147483647 and
limit1..100. They retain16KiB/depth4/128tokens/original10s input bounds. Do not reuse
the legacy graph reader's50000 offset ceiling or change any v1 request semantics.

Contributor request is exactly
`{revision,side,selector,offset,limit,completeDocumentDisclosure:true}`. Selector is
one closed union:

- `{kind:"node",key}`
- `{kind:"membership",relation,physical,computed}`
- `{kind:"cooccurrence",relation,source,target}`

Each computed key is exactly `{computedType,derivation,value}`. IDs use the existing
lowercase declaration-ID syntax, physical is the existing closed Existing/Fresh
command reference, and value is nonempty exact XML1.0 Unicode text. No trimming,
normalization, case folding, value hashing or implicit lookup by label is allowed.
A value is at most1048576 UTF16 code units, as any selected qualified original or
final XML document is already limited to that length. Two fully JSON-escaped keys
plus closed metadata fit the dedicated16MiB contributor body ceiling. Use original
30s input deadline, depth4,128tokens,64-character property names; enforce each
field's own size and strict Unicode, rather than applying the old2048character
string ceiling. Exact large returned keys must round-trip, including literal
supplementary characters and escaped XML whitespace. This new reader is separate
from the unchanged v1 reader. Reject duplicates, unknown properties, extra roots,
noncanonical integers, invalid UTF8/surrogates and excess depth/size. Missing,
false or nonboolean disclosure refuses malformed input before result lookup,
including unknown selectors and beyond-end pages. No body tree or unbounded input.

Every result freshly verifies complete original content and any retained target
with the original cancellation flag. Missing target refuses INCOMPLETE_TARGET;
complete empty graph stays distinct. Retained original after failed inspection
remains readable without inspection/export authority. Complete current FAIL rules
remain inspectable. Preserve unsigned UTF8 tuple/physical-origin ordering from
the qualified adapter, never sort by opaque handles. A computed key is never an
editable physical entity or an invented XML origin. Missing exact selector refuses
NOT_FOUND. Unknown physical selector handles do not authorize access to another
plan. Contributor references resolve through selected provenance, retaining Fresh
identity after same-literal replacement; target coordinates come from actual
independently reprojected final XML.

All replies are closed `{revision,total,offset,nextOffset,items}` pages, with
complete total, requested offset, null nextOffset at end and at most100items.
Beyond-end offset returns empty items and complete total without overflow. Map
fields explicitly; do not serialize arbitrary internal proof records.

| Route | Item fields |
| --- | --- |
| nodes | key, contributorTotal |
| memberships | relation, physical, computed, contributorTotal |
| cooccurrences | relation, source, target, contributorTotal |
| rules | kind, declaration, source, actual, minimum, maximum, outcome |
| contributors | physical, origin, roles |

Rule kind is `ENTITY_COUNT|COOCCURRENCE`; source is a computed key or null.
Actual/minimum/maximum are canonical nonnegative decimal strings (not JSON numbers),
retaining arbitrary-precision declared cardinality; outcome is `PASS|FAIL`.
Contributor totals count every distinct occurrence, never page length or distinct
text. Collections contain only declared eligible PUBLIC values and need no full
XML disclosure acknowledgement. Contributor locations always require it.

Origin is closed `{documentId,projectionId,sourceDigest,elementIndex,ancestry}`.
Element indexes and ancestry entries are canonical nonnegative decimal strings,
ancestry at most128entries. Roles retain their exact one/two ordered entries,
including equal source/target roles in self-cooccurrence. Each is closed
`{field,location}`; location is closed `{value,selector}` with null selector for a
direct attribute. Attribute pin is closed
`{documentId,sourceDigest,elementIndex,name,qualifiedName,decodedValue,valueStart,valueEnd,quote}`.
Name is `{namespaceUri,localName}`; quote is one literal single/double quote.
Start/end are JSON integers0..1048576 selecting exact UTF16 lexical value spans.
Child selector is closed `{parentElementIndex,element,discriminator}`: element is
an expanded name and discriminator is the complete attribute pin, retaining both
selector and value coordinates. These are transient disclosed observations, not
caller-supplied authority or locations for the computed group itself.

Replies retain128MiB encoded UTF8 and one original30s encoding/output/flush budget.
Refuse RESOURCE_LIMIT before response publication if a requested page exceeds the
bound; never truncate, silently reduce its size, retain a page cache or append an
error after partial output. No renewed deadline or lease-only authority fallback.
Original worker closure/uncertain cleanup rules remain unchanged. No new combined
maximum-memory, browser, compiler/publication, export or native-client admission
is established by these routes.

Acceptance uses independently invented XML/compiler/content adapters and actual
HTTP ownership controls. Check exact Unicode order, duplicate and last contributor,
complete paginated equality, physical rename/Fresh replacement, child selector and
final lexical coordinates, co-occurrence role order, complete FAIL rules, optional
absence and unresolved target, intmax paging, exact selectors beyond16KiB and both
keys at their allowed size, all consent forms, malformed/foreign/stale/V2/CSRF,
original pin loss during held output, rollback and logout capacity recovery.
Existing lower-level full-proof adverse tests and unchanged v1 controls remain.
