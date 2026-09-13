# Immutable plan model version admission

The shared HostedPlanService remains the only plan/operation/replay/capacity owner.
This prerequisite adds explicit expected-model-version admissions for future
versioned HTTP callers. It enables no route, browser availability, publication or
export, and changes no existing wire shape or digest. Model V2 corresponds to the
existing /api/v1 plan surface; model V3 is separate from that wire version number.

PlanDefinition.Version is the closed V2/V3 enum. PlanDefinition.version derives it
from the sealed model variant; callers cannot relabel a definition. Atomically
record that version with each successfully installed owned plan ID. The immutable
ID/version association survives retirement/content cleanup for the same lifetime
and bounds as existing owned-ID/replay metadata. Never replace it with the lease's
newest live plan version, infer it from a nullable Operation.plan, or keep retired
content merely to recover its version. Retained operations resolve their own
original immutable version after physical cleanup has released Plan references.

Add non-null expected Version overloads to requireOwned, view (current or explicit
ID), verifySummary, reserveView, reserveCommand, reserve, claimCredentials, status
and cancel. Existing internal unversioned entry points retain their behavior.
Null expected version is INVALID_REQUEST; HTTP supplies a fixed route-owned enum,
never a field from a request. Every expected-version check uses original live lease,
owned ID and immutable version inside the existing authority/state guard.

Wrong model version shares NOT_FOUND with missing/foreign IDs. Reject it before
expiring a reservation, invoking a cleanup/status/cancel callback, consuming
credentials, reserving metadata/content/command capacity, allocating a permit or
changing replay/draft/plan state. An immutable version preflight may precede the
existing operation in a separate guarded phase: its ID association cannot change,
and all original final lease/revision/generation/cleanup checks still run. No
unversioned fallback occurs on mismatch. External work remains outside authority
monitors; do not hold those locks across body reading, JDBC, proof or output work.

Current summary resolves and filters the actual live plan under the original lock,
before reservation expiry or summary construction. Return NOT_FOUND if its model
differs, without finding a retired same-version plan. Pin the matched ID across
any following phase so a replacement current plan cannot be substituted. Explicit
ID summaries and final summary verification retain original revision/equality
checks. No version field or v3 computed count is silently added to v2 responses.

requireOwned/reserve/reserveCommand on the correct version must continue to admit
the existing bounded metadata path for an owned retired ID far enough to perform
exact successful replay before retired/stale refusal. A new command on that ID
still refuses NOT_FOUND. An old operation's correct-version status/cancel remains
available after retirement; wrong-version polling must not run its cleanup handle.
A new live plan of the other version does not reclassify old acknowledgements or
operations. New login by the same owner cannot recover old metadata.

Preserve one live plan per lease/four globally, original observation/scratch bounds,
256 replay/operation limits, no eviction and original cleanup quarantine. Keep v2
ES-PLAN-COMMAND-1 input bytes and existing explicit v3 creation identity unchanged;
cross-version reuse of a creation request ID still conflicts. Version metadata is
small bounded tool data, never another owner or an authority token for a caller.

Acceptance uses both variants in one actual shared owner with clearly labelled
test-only publication/observation witnesses. Test wrong version before scratch,
credential consumption, reservation expiry and cleanup callbacks; a subsequent
correct-version operation must still work. Exercise both retirement directions,
correct old create/reserve/command replay and terminal status/cancel after a new
other-version plan, version-filtered current/ID summaries, stale/revoked/foreign
leases and global mixed-version capacity. Preserve independent v2 identity tests.
Meaningful RED, adverse controls, compiled guard mutations and fixed independent
review are required. No test witness qualifies an actual v3 compiler.

All existing v1 controllers must adopt fixed V2 admissions before any v3 plan HTTP
routes are installed. Versioned HTTP, non-touching polling, original transport
completion/cleanup and response encoding are separate next integration work; this
internal prerequisite alone does not establish that HTTP isolation is enforced.
