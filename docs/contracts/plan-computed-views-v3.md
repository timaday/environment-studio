# Internal controlled v3 computed views

This read-only server adapter presents the computed partition under the shared
plan's existing pinned ViewAdmission. It adds no HTTP route, command, publication
qualification or runtime availability. Physical views retain their existing
shapes. Computed identities never become editable physical handles or acquire
invented XML origins.

Every request freshly verifies the complete original content and any retained
target through V3PlanReadContent, under the original admission cancellation flag.
The admission verifies ownership before and after rendering. Selecting a missing
target refuses INCOMPLETE_TARGET; it never returns a complete empty graph. An
original retained after failed inspection remains readable without acquiring
inspection, capture, validation or export authority. No snapshot-only entry exists.

The adapter returns typed pages for nodes, memberships, co-occurrences, rule
outcomes and contributors. Every page carries revision, complete total, requested
offset, optional next offset and at most 100 items. Negative offsets and limits
outside 1..100 refuse INVALID_REQUEST. An offset beyond the complete total returns
an empty page with no next offset, including Integer.MAX_VALUE without overflow.
Preserve the qualified graph's unsigned UTF-8 tuple/physical-origin ordering;
never sort by opaque physical handles or use a page length as the complete count.

Node rows contain the exact structured computed key and contributor total.
Membership rows contain relation, physical command reference, computed key and
contributor total. Co-occurrence rows contain relation, exact source/target keys
and contributor total. Rule rows retain complete explicit count/cardinality and
PASS/FAIL outcomes. Completeness does not imply validity. Values come only from
declared eligible PUBLIC source fields; no unqualified field is added to a row.

Contributors are separately paged using a typed exact selector: node key,
membership relation/physical command reference/computed key, or co-occurrence
relation/source/target keys. A missing result refuses NOT_FOUND. The page total
counts all distinct occurrence contributors; duplicate values never collapse
physical contributors. Location disclosure is mandatory and refuses
DISCLOSURE_REQUIRED before rendering when absent.

Each contributor contains its original Existing or explicit Fresh command
reference, the selected actual XML entity origin, and all ordered field roles
with their qualified observed locations. Resolve that reference through the
selected content's complete provenance and the pinned snapshot; never derive it
from the final identity literal. A same-literal Fresh replacement must stay Fresh.
Target locations come from the independently reprojected final XML, never typed
preliminary decisions or retained original coordinates. Co-occurrence roles stay
source then target, even if field names sort differently. Attribute/child selector
pins remain intact, and no contributor or role is silently omitted.

Result selection, pagination and contributor mapping check the original live
control. All DTO diagnostic strings are redacted; returned data is transient
view content. No cache, publication digest, profile or export authority is added.
Response encoding/transfer limits and combined maximum-memory qualification
remain prerequisites of future operational HTTP wiring.

Acceptance uses independently invented actual XML and compiler/content adapters:
exact Unicode equality/order, duplicates and complete paged contributors, field
changes, last-contributor removal, optional absence, unresolved target, physical
identity rename and Fresh replacement, child selectors and co-occurrence role
order. Forged full proof, stale/closed admission and missing disclosure refuse.
Unchanged physical/v2 behavior remains covered separately.
