# Small v3 plan summaries and operation ownership

This extends the shared model-version admission boundary with typed internal
metadata needed by the future v3 plan routes. It enables no HTTP path, compiler
qualification, browser availability or export. Existing V2 View/Counts and their
wire meanings, fingerprints and final checks remain unchanged.

HostedPlanService.V3View contains the existing physical View summary and separate
Optional<ComputedCounts> currentComputedCounts and targetComputedCounts.
ComputedCounts contains exactly nodes, memberships and cooccurrences. They count
the complete retained computed graph partition independently from the physical
documents/entities/relations in View. No value, identity, source, contributor,
proof, model or driver record belongs in this small metadata response.

viewV3(originalLease,current-or-explicit-ID) captures all fields under the existing
session/state guard. It requires immutable model V3 before reservation expiry or
metadata construction. Current lookup selects only the actual live plan; an old
V3 plan cannot replace a current V2 plan. Missing/foreign/retired/wrong-version IDs
share NOT_FOUND. A revoked lease retains SESSION_REQUIRED. There is no fallback
to V2 or unresolved caller-supplied version.

No complete current observation means currentComputedCounts is absent. A complete
observed graph with zero computed results contains present zero counts. No complete
materialized target means targetComputedCounts is absent, including a current-only
inspection or unresolved draft. A complete empty target has present zero counts.
An invalidated observation may remain as inspectable retained context; its existing
inspectionValid/observedDestination validity still reports that it is invalid.
Counts neither establish fresh proof qualification nor authorize export.

For current content require the retained V3Observed evidence; for target require
V3Target. Unexpected evidence variants refuse PROJECTION_REFUSED instead of
returning zero, absent or physical-only fallback counts. Use existing immutable
list sizes without iterating or copying whole graphs, contributors or XML. No
large scratch, credential, database, parser, compiler or workspace port is needed
to read this metadata. All original plan/operation resource limits still apply.

verifySummaryV3(originalLease,capturedV3View) rechecks the same explicit captured
plan ID and full metadata equality under original authority. A changed plan,
revision, active operation, observation-validity latch, target presence or computed
counts refuses under the existing NOT_FOUND/CONFLICT distinctions. Same-revision
materialization and failed reinspection must invalidate an earlier snapshot.
This verifier never substitutes a replacement current plan. External encoding or
output must remain outside authority locks and must call this final verifier.

Add requireOperationOwned(originalLease,operationId,expectedVersion) as a pure
expected-version preflight over retained operation metadata. It shares exactly
the existing immutable operation-version check: wrong/missing/foreign NOT_FOUND,
null expected INVALID_REQUEST, revoked original lease SESSION_REQUIRED. It must
not call status(), operation(), a cleanup handle, expiry or cancellation, consume
credentials or change any capacity. Matching expired/terminal original operations
remain owned metadata. This permits HTTP transport admission before an atomic
one-shot claim, without hiding resource allocation in a status poll. A later
claim/status/cancel still performs all existing final checks; this pure preflight
is not a reusable authorization token.

Acceptance uses actual shared owner and invented XML/projection cases. Distinguish
uninspected, observed/current-only, complete target, unresolved target and complete
zero computed graphs. Assert exact separate physical/computed totals, changes after
target edits and invalidation by same-revision materialization/reinspection. Hold
large scratch while reading small metadata; no second large admission is required.
Cover wrong version, retired/current replacement, original lease loss and foreign
IDs. Count permit/cleanup callbacks around pure operation checks before and after
expiry; correct one-shot submission remains possible when still reserved. Meaningful
RED, guard mutations and independent fixed review precede public route integration.
