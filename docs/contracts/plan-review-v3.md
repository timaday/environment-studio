# V3 plan review acknowledgement

Implementation contract for the next application slice. Existing compiler/client
qualification and export availability are unchanged. An acknowledgement records
an operator's review of exact checked inputs; it grants no export capability.

## Owned command and result

The framework-free command contains the existing Mutation (`expectedRevision`,
UUID `requestId`), a lowercase64-hex `inputFingerprint`, the explicit configured
`destinationId`, and the single supported artifact intent
`protected-self-contained`. Destination uses the existing tool-ID grammar.
No document policy, validation result, checked model, XML or claimed PASS is an
input. The result is the existing small Ack at the unchanged plan revision, with
no operationId. The receipt's identity binds every command field through the
existing process-memory command HMAC.

Use the original live V3 lease/plan owner and one existing ViewAdmission. A direct
application entry reserves, runs and closes that owner. Check exact replay before
pinning the current plan: a prior successful request returns its original safe Ack
under the original live lease, including after edits or retirement. Replay never
installs/reactivates a review. Different content or command family under the same
requestId conflicts. Reuse the existing shared256-entry lease replay ledger and
collision namespace; no additional ledger, eviction or capacity reset.

For a new command, pin the original revision, inspection and generation, require
no active inspection/materialization, freshly resolve the complete immutable
publication and verify complete current/target XML and provenance through the
existing v3 validation path. Require an actual complete target. Compare the
request fingerprint and destination to server-owned data; mismatches conflict.
Missing target refuses INCOMPLETE_TARGET; original ownership, publication, proof,
capacity and cancellation refusals retain their existing codes.

Atomically install one small immutable active review record and its replay Ack
under the original lease/state guard. Bind plan/revision/generation, freshly
computed fingerprint, destination and artifact intent. Retain no XML, snapshot,
computed rule array, credential or additional work owner. The acknowledgement
neither increments the revision nor changes ES-PLAN-INPUT-3 framing or fields.

## Validation and document permission

Without a matching current review, REVIEW and CONTENT_POLICY stay UNKNOWN.
With a matching review, fresh validation still performs every existing publication
and XML proof check. REVIEW may then be PASS for that exact tuple. For its bound
protected-self-contained intent, evaluate the immutable publication's policies
for every declared document in the selected binding and complete original/target
inventories, including unchanged, unmapped and secret content. Both inventories
must match the declared complete scope; contradictory scope refuses projection.

All matching policies `protected-self-contained` gives CONTENT_POLICY PASS;
explicit `deny` gives FAIL; missing required policy gives UNKNOWN; duplicate or
extraneous selected-binding policy gives ERROR. ERROR takes precedence over FAIL,
then UNKNOWN, then PASS. Policies from another binding cannot supply permission.
This assessment does not inspect or infer permission from XML values or preview
masking. A review may acknowledge a denied/unknown policy result, but cannot
change it. CLIENT_CAPABILITY remains UNKNOWN and requestExport remains
EXPORT_UNAVAILABLE even when review and content policy pass.

Invalidate active review on semantic changes, new inspection reservation, failed
inspection, cancellation/expiry, target loss, retirement, session invalidation or
failed fresh publication/proof verification. Same-revision failures cannot revive
the old record after later recovery. Equivalent successful materialization may
preserve review when the fresh fingerprint, revision and generation match exactly.
Historical replay receipts remain metadata only. Review outcome itself is excluded
from input identity, so acknowledging a review does not change its fingerprint.
This applies across validation, preview/composition, physical/computed reads,
document comparison and capture, including their direct application entries.
Typed PROJECTION_REFUSED from retained-source/content reads invalidates the
original plan's review; an explicit full-proof invocation also invalidates on an
unexpected runtime failure. Ordinary malformed selectors, missing documents or
profile request refusals do not erase review when its required evidence succeeds.
Do not add another full XML verification merely to observe the existing refusal.

## HTTP wire and transfer ordering

POST `/api/v3/plans/{planId}/reviews` accepts exactly
`{expectedRevision,requestId,inputFingerprint,destinationId,artifactIntent}`.
The revision is canonical positive decimal, at most1024 digits; requestId is a
canonical lowercase UUID. Other fields use the application grammar above. Response
200 is exactly `{planId,revision}`, without operationId. The closed metadata
reader enforces16384 wire bytes, depth4/token128/name64/string16384 limits and
one original10-second body clock. The acknowledgement uses32768 encoded bytes
and one original30-second encoding/output/flush deadline. No collection-sized
body or view-sized reply allocation. Duplicate/unknown/nested fields, bad Unicode,
trailing JSON, claimed outcomes/policies and unsupported intents refuse.

Require original lease, Host/Origin/OIDC/CSRF and fixed V3 ownership before input,
async setup or admission. Retain the original ViewAdmission plus the existing
single semantic transfer through worker closure and sole async settlement. Exact
replay precedes pinning; output verifies the original pin when present, otherwise
the original live lease/version owner. A historical receipt is not current review.

## Transfer acceptance

The HTTP adapter must use the existing bounded body/output/transfer owners,
version admission and Host/Origin/OIDC/CSRF enforcement. Original transfer abort
must be ordered with the application commit: abort observed before atomic commit
prevents record/replay installation; abort after commit preserves the receipt for
exact replay. Response loss is not rollback, and successful delivery is not claimed
by a committed acknowledgement. Recheck original authority throughout output.
The original ViewAdmission owns a single OPEN/CANCELLED/COMMITTED atomic gate.
Its fixed nonthrowing abort signal performs no cleanup and acquires no application
lock. Cancellation wins only before commit; after COMMITTED the signal is inert.
Prepare the receipt and record before the final compare-and-set under the existing
lease/state guard. Insert the replay before assigning the record; no subsequent
cancellation check may split their installation. Exact replay retrieves history
without installing a record. Worker completion retains original cleanup ownership.
Only this abort signal is nonblocking; existing HTTP settlement may acquire locks.
This ordering and the closed wire schema must be tested before accepting the
route; the internal application entry alone is not an HTTP readiness claim.

Acceptance uses independently invented actual XML/compiler/content with explicit
historical publication/observation witnesses where currently necessary. Check exact
acknowledgement and unchanged fingerprint, whole-document permission including a
denied unchanged sibling, missing/duplicate/wrong-binding policy, mismatched input,
foreign/new lease, stale revision/generation, missing target and export refusal.
Challenge held lookup/proof cancellation, same-revision target loss/recovery,
reinspection failure, historical replay, cross-family collision and shared256
exhaustion without partial installation. Existing v2 fingerprints remain unchanged.
Independent fixed-candidate review and combined required gates remain mandatory.

## Typed browser request and receipt

The typed browser client provides `prepareReview` and `HostedV3Api.review` over
the existing session-owned transport. Preparation validates all five closed
fields and returns a detached frozen request without generating a request ID.
Each call posts that exact request once; uncertain delivery leaves explicit
same-request replay to the caller. Do not fetch current state or retry implicitly.

Accept only a frozen two-field receipt whose planId matches the requested plan
and whose revision exactly equals the retained request's expectedRevision,
including canonical1024-digit revisions. Later displayed edits/retirement do not
rewrite that historical revision. An operationId or claimed check/export state
is not part of this receipt. Local malformed input refuses INVALID_REQUEST before
network work; malformed/mismatched successful replies remain RESPONSE_UNAVAILABLE.
Session loss/expiry and uncertainty use the existing transport's original owner.
Receipt delivery does not install client-side review, validation or export authority.
