# Hosted v3 profile capture, reuse and validation

Add POST `/api/v3/plans/{planId}/profile-captures`, `/profile-previews` and
`/validations` to the existing hosted v3 routes. Compose through the already
implemented `/commands` action `compose-profile`. Preserve all v1 interfaces and
core digest/qualification behavior. These routes add no implicit save, publication,
observation, export or readback. Current compiler qualification remains incomplete.

## Original ownership and budgets

Require live original lease, Host/Origin/CSRF and fixed V3 ownership before body,
async or resource acquisition. Reserve the original ViewAdmission and existing
single semantic HTTP record before input. If the second admission refuses, close
the first conclusively. No new pool, metadata substitute, queue or physical permit.
Original ViewScope.run spans parsing, revision pinning, complete controlled work,
encoding, readiness, output, flush and final verification. Before pinning, errors
retain the reserved authority; after pinning, every response retains that same pin
or aborts. Existing sole async settlement, cleanup uncertainty and lease quarantine
remain. Neither container completion nor a returned adapter proves worker closure.

Capture and preview use a closed COLLECTION body mode:67108864 wire bytes, the
original30-second read deadline, existing PlanViewReader depth8/token1000000 and
collection/field limits. Validation uses16384 bytes/depth4/token128 and the
original10-second read deadline. No renewed deadline, raw complete-body copy or
caller-selected budget. Replies use the existing134217728-byte VIEW encoder and
one original30-second encoding/output/flush deadline, with original cancellation
checked at bounded phases. No whole duplicate of value-bearing rule maps. Partial
output aborts; never append a refusal or claim an empty successful page after a
resource failure. Known workspace404/503 and unexpected generic500 remain unchanged.

## Capture

Request exactly matches the existing v1 capture grammar:
`{revision,profileId,profileRevision,mappings:[{entity:{kind:"existing",handle},slotId,label}]}`.
Mappings are nonempty, at most20000, with unique complete physical entity/slot
coverage. Tool IDs, decimal revisions and explicit neutral labels retain existing
limits. Resolve handles through the original admission, then call its controlled
capture port. No Fresh/computed/foreign or omitted physical entity is accepted.

Response200 is exactly `{revision,definition:{objectId,workspaceRevision},format:"json",source}`.
The portable source is schema3 with its v3 logical digest, at most1048576 UTF8
bytes. It contains physical structure only. No original/target values, computed
membership or donor-derived dependencies become portable. Capture always reads
verified original observation; target edits do not change capture. The returned
source/definition may be supplied to the existing separate v3 workspace save
command. Capture itself neither saves nor publishes. Historical test readiness
cannot replace current compiler qualification in the production workspace.

## Reuse preview and existing composition

Request exactly matches the existing v1 preview grammar:
`{revision,profile:{objectId,workspaceRevision},selection,section,offset,limit}`.
Selection is closed `{kind:"all"}` or `{kind:"selected",roots:[id,...]}` with
1..20000 unique roots. Section is `included|dependencies|relations|conflicts`;
canonical integer offset0..50000 and limit1..100. This reaches all current sections:
dependencies are appended only for newly included slots and cannot exceed19999;
included slots cannot exceed20000, and physical relations cannot exceed50000.

Response200 is exactly
`{revision,total,offset,nextOffset,items,previewDigest,pins,section,affectedDerivations}`.
Retain existing complete page semantics and closed section-specific item shapes:
included `{slotId,typeId,label,requiredInputs}`, dependency
`{slotId,causedBy,relationId,reason}`, relation `{relationId,fromSlot,toSlot}`, conflict
`{code,slotId,relationId,ruleId}` with existing nullable coordinates/safe code mapping.
Unknown conflict codes or a potentially capped256-conflict collection refuse.
Never report a capped count as complete. `nextOffset` is an integer or null.

Pins are exactly `{planId,revision,observationFingerprint,profile,publicationDigest,selectedRoots,rootsDigest,closureDigest}`;
profile retains its objectId/workspaceRevision shape. Require the complete v3
preview and equality between its physical component and exposed dependencies.
`affectedDerivations` is the complete sorted unique list of at most32 derivation
IDs, repeated even on empty/beyond-end pages; missing v3 evidence is refusal,
never an empty default. Compute previewDigest only through the actual core
ES-PLAN-COMPOSITION-PREVIEW-3 framing. Display pins do not replace full internal
XML/provenance proof. Every page freshly evaluates the entire selected preview.

Use the returned digest and normalized selectedRoots in the existing
`compose-profile` command with explicit create/cancel/use-existing decisions.
That command freshly resolves publication and complete target proof, recomputes
the versioned preview, verifies the digest, merges physical intent and materializes
under its original command cancellation. No new mutation endpoint or caller proof.
New slots require explicit target values and placement; donor values never import.
Partial reuse preserves unselected siblings. Replays never compose/render again.
Changing a target identity does not change its Existing/Fresh provenance reference.
The UI must discard mixed preview pages if any pin/digest/affected list differs.

## Validation summary and computed-rule pages

Validation always evaluates the complete core V3Validation under the original
admission, fresh publication and full original/retained-target proof. It never
calls legacy validation, materializes a target, changes a revision or grants
export. Missing target is distinct from complete empty target; invalid inspection
refuses. Preserve all ten RequiredCheck outcomes in declared order, all physical
count rules sorted by ID, client capability UNKNOWN, review/content-policy outcomes defined in
`plan-review-v3.md`, and
the ES-PLAN-INPUT-3 fingerprint. Pagination affects only presentation.

The closed request union is:

- Summary: exactly `{revision}`.
- Computed rules: exactly `{revision,section:"computed-rules",inputFingerprint,offset,limit}`,
  with the summary's lowercase64-hex fingerprint, canonical integer
  offset0..2147483647 and limit1..100.

Summary response200 is exactly
`{revision,inputFingerprint,checks,applicationRules,targetComplete,computedRuleCount,exportAvailable:false}`.
Checks are all ten `{check,outcome,inputFingerprint}` rows; every fingerprint equals
the summary's. Application rules retain `{ruleId,outcome}` rows and complete sorted
coverage. `computedRuleCount` is null when target evidence is missing, otherwise
the complete nonnegative count including zero. `targetComplete` is true exactly
when that count is nonnull. Never serialize a whole repeated-key computed-rule
array in the summary. A rule count is not a claim that the operator viewed every row.

Computed-page response200 is exactly
`{revision,inputFingerprint,targetComplete:true,total,offset,nextOffset,items}`.
First recompute complete validation, compare the requested fingerprint and refuse
CONFLICT on mismatch. Missing target refuses INCOMPLETE_TARGET. Total is the full
computed rule count; preserve requested offset, normal complete page ordering and
exact nextOffset. Beyond-end yields an empty list and null nextOffset. Each item
uses the reviewed computed-rule wire shape:
`{kind,declaration,source,actual,minimum,maximum,outcome}`; source is null or the
exact structured computed key, cardinalities/bounds are canonical decimal strings.
Retain every role/order and do not turn missing evidence into current-side rules.

Project only the selected rows through controlled lazy encoding, retaining complete
core validation and its authority checks. A page exceeding the response budget
refuses RESOURCE_LIMIT before publication, without advancing offset. A new read
may request a smaller limit at the same revision/fingerprint/offset, down to1.
The UI must expose the refusal and smaller-page recovery; it cannot present missing
rows as successful emptiness or silently change validation severity. A conservative
default of4 rows fits the existing single-key/tuple and uploaded numeric bounds;
larger requested pages remain available subject to the explicit wire budget.
Every successful page must match the summary fingerprint/count before joining its
display. A fingerprint is a read precondition and identity, never export authority.

## Bounds and acceptance

An independent actual compiler/DerivedGraphEngine probe with8192 physical inputs,
8192 distinct1024-byte keys and32 zero-min co-occurrences produced262144 passing
rules and zero pair edges. Its proposed full array requires313966593 ASCII bytes
or581353473 bytes with escaped backslashes. The engine's distinct-value budget is
8MiB; repeated rules can exceed128MiB despite staying within that budget. Thus
summary counts and explicit pages are required. Those probes use complete engine
inputs, not actual XML materialization or production admission. Oversized raw
engine keys also do not establish that the current XML adapter admits them.

For uploaded declarations and valid XML text, one row is conservatively below17MiB:
the global8MiB value budget, at most2x UTF8 JSON escaping,64-character IDs and
uploaded numeric expansion limits bound it. Existing XML source limits can be
stricter. Therefore a one-row read remains reachable. Tests must use actual
qualified source limits when claiming HTTP/XML coverage, and identify controlled
engine-result tests separately.

Acceptance requires observed RED before production implementation, independent
fixed review and focused guard mutations. Exercise all three actual HTTP routes
through mock OIDC/CSRF, unchanged v1 controls, admission before body, rollback,
original pin/cancellation through held lookup/encoding/output, stale/foreign
resources, cleanup uncertainty and recovery. Capture must round-trip physical-only
schema3 without saving; separate save/history uses the returned source/reference.
Verify whole/partial preview, complete dependencies/affected derivations, exact
digest-driven composition, explicit conflict decisions and unchanged siblings.

Drive returned physical refs/placement coordinates through one-to-two structure
across multiple independently invented XML records, explicit values/references,
Raw/Placeholders/Formatted comparisons and complete binding/contributor locations.
Compare literal independent expected XML and retained original content. Exercise
original-only, complete, edited and unresolved validation summaries; full checks,
null versus zero counts, fingerprint mismatch, page tails beyond50000 and recovery
from an oversized page without advancing its offset. Do not fabricate a checked
target merely to test a successful public workflow. Explicit test-only publication
ports must remain absent from production composition, whose compiler still refuses.

Qualify combined retained proofs, complete validation evaluation and bounded
selected-page encoding before operational availability. No new maximum-resource,
browser, native-client, export or release qualification is asserted by this API.

## Browser reuse state

The profile picker explicitly loads the owned v3 catalogue and reads the exact
immutable workspace revision selected from that catalogue. Unknown/unloaded or
failed catalogue is distinct from a successful empty list. No automatic profile
selection, publication, preview or plan command follows from loading or selecting.
Before exposing a selected model, compare its object/revision, native identity,
source/content digests, stored state and definition reference with the selected
catalogue entry. Any mismatch fails closed. Drafts remain inspectable; a stored
publication is historical state, never current runtime qualification.

Reload, another selection, inactive presentation, API/session replacement or
session termination retires pending reads and clears selected content. Late
responses cannot restore retired state. Selection inputs are copied from the
owned catalogue, not retained from mutable caller objects. Explicit retry is a
new read; there is no automatic request retry or browser persistence. Rendering
and whole/partial selection reuse their separately approved designs/contracts.

The nonvisual reuse controller accepts an explicit immutable profile revision and
whole/selected roots. Entry/configuration never mutates the plan. Preview reads
all four sections to completion, including empty ones; it exposes no partial
collection. All pages must share pins, digest and affected derivations, each
section must retain its total, and the observation fingerprint and final fresh
plan summary must still match the original context. A failed or retired read
clears the preview. Unknown inventory is not an empty successful preview.

Apply is a separate explicit action with one create/use-existing/cancel decision
per included slot. The command uses only returned normalized roots/digest/profile
and the original revision; no inferred mapping, donor value or replace-draft
command is generated. Java remains responsible for composition, conflict
resolution, publication, sibling preservation and materialization. A complete
preview does not authorize export or establish publication readiness.

A dispatched command is detached and retained with its original plan ID until a
valid acknowledgement or confirmed pre-commit refusal. Network, malformed reply
and possibly committed403/413 preserve exact retry; configuration/context changes
must not replace it or replay against another plan. An acknowledgement is an
original command receipt, never a fresh summary or target-completeness assertion.
Session termination/unmount retires owned state. Future rendering must connect
session retirement and display receipt ownership explicitly. No rendered capture
or reuse design is approved by this nonvisual prerequisite.

## Browser reuse inventory

Placement uses an explicitly loaded current physical inventory under the selected
plan. The browser reads every bounded page, verifies original plan/side/revision,
consistent total within the20000 physical bound, unique existing handles, and the
count declared by the initiating plan. Before exposing any rows it re-reads the
full plan summary and requires the initiating context to match. A partial, stale,
duplicate or refused collection is unavailable, never an empty successful list.

Keep the backend field distinction between absent, empty, masked and concrete.
These current values aid explicit placement only; they never enter the reusable
profile or trigger matching/plan commands. Unknown inventory remains null. Reads
are explicit, with no automatic retry or local persistence. Context, presentation,
API/session replacement and session refusal retire pending reads and clear values;
late results cannot restore retired content. Retained load callbacks belong to
their original context and cannot acquire a replacement owner or start old-scope
requests after retirement, including leave/reentry to the same plan. Rendering and command choice state
remain separately owned and subject to approved UX.

## Browser validation state

Explicit validation loads the complete summary without automatically fetching all
computed rules. Retain all backend outcomes and null versus zero rule counts;
never derive export permission. Publish a summary or page only after a fresh full
plan summary matches the initiating context. Reads retire on context, presentation,
owner or session changes; no partial or previous page remains visible as current.

Rule navigation requests one bounded page, default limit4, at an explicit offset.
It verifies revision, fingerprint, complete count and page arithmetic against the
retained validation summary. A RESOURCE_LIMIT refusal retains that summary and
the failed offset/limit for explicit retry at the same offset with a smaller
limit down to1. It clears the old page, never advances offset or silently changes
the page size. Other failures clear validation evidence and require revalidation.
A new validation retires any older page read. Only one page is retained; paging
never changes the scope of backend validation. Future rendering must propagate
idle session retirement and disclose which page is visible. No new layout,
accessibility result or design approval is introduced by this state prerequisite.

## Reuse renderer preparation and lifetime

An inspected plan needs a complete target proof before profile preview. The
operator explicitly prepares it through the existing materialization route;
selection does not silently materialize. Refresh the plan after the result and
use that returned context before revealing selection. Incomplete/refused results
remain prerequisites, never success or export authority.

Bind chained catalogue then current-inventory reads to the initiating API,
plan context and presentation lifetime. A retired catalogue completion must not
start another inventory request after reentry. A new explicit load belongs to
the new context. Keep existing hook retirement and final-summary checks.

Completed preparation diagnostics belong to the initiating context as well.
Clear them on owner, plan or presentation retirement; preserve them across
unrelated rerenders of the same context. Never display a prior plan diagnosis
as evidence about the replacement plan.

Changing to selected-parts mode with no roots is an ordinary unsubmitted state,
not an invalid backend request. Explicitly clear previous selection/preview/error
read state when selection becomes empty; do not issue a preview or fabricate a
replacement selection. This local reset cannot clear or replace a pending apply.

Validation read callbacks belong to the exact API, plan context and presentation
lifetime that created them. Leaving/reentering does not reactivate a retained
callback. A replacement context immediately renders empty validation state,
including before effects settle; no old summary or rule values appear under the
replacement plan. New explicit validation and paging remain available.
