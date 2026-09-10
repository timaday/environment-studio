# V3 capture to a new profile draft

Next bounded application slice after `37351a9a7443bdf01814bfe6d4b4482a1da1ebbb`.
Implement nonvisual state using the existing capture, entity-page and profile
workspace clients. No backend API change or new rendering is included yet.
Future rendering requires an applicable approved image under the UX skill,
using the original Midnight references; pending Definitions images stay unapproved.

The operator explicitly opens capture, loads the complete original physical
inventory, assigns a neutral slot ID and label to every returned Existing handle,
and supplies portable profile ID/revision. Capture produces a value-free schema3
profile. A separate explicit Save creates a new workspace draft; capture itself
never saves, publishes or changes the target. Partial selection belongs to reuse.

Follow [capture authority](../contracts/plan-profile-capture-v3.md),
[HTTP workflow](../contracts/hosted-plan-workflow-v3.md) and
[draft ownership](../contracts/workspace-profile-http-v3.md). Read current entity
pages only on explicit entry; retain handles/types, not concrete field values.
Require consistent complete pages, unique handles and complete explicit mappings.
Foreign, Fresh, omitted, duplicate or excessive mappings refuse. No generated
label or ID may derive from donor values or inferred semantics.

Bind unsaved results to the exact session/API, plan, revision, definition and
observation. Changed inputs/context invalidate held reads and capture. Verify
the final summary before presenting complete inventory or captured source; this
is conservative client stale detection, not server authority or atomic proof.

Save uses exactly the returned source/definition, workspace expectedRevision0
and one destination/request identity. After dispatch, preserve that exact command
independently of later presentation/context changes until acknowledged, definitely
refused or session termination. No automatic retry. Unknown/malformed/network/5xx
and postcommit-capable403/413 outcomes retain explicit original-command replay.
Use only the reviewed closed precommit refusal classification to unlock changes.
Same-session hide/show must preserve pending save; no disk/browser persistence.

Acceptance: more than100 entities require all pages; no partial or mixed inventory
can capture; original values are absent from retained mapping state; capture and
save are separate; stale replies cannot restore cleared source; exact source and
reference survive response-loss replay; duplicate actions dispatch once; immutable
acknowledgement is not publication or readiness. Observe meaningful RED before
implementation, run focused adverse/mutation checks and obtain fixed review.

Actual browser save requires one matching invented historical definition in the
owned schema3 SQLite workspace and the plan's test-only publication witness.
An in-memory plan witness alone must not fabricate successful profile persistence.
Keep this fixture explicit and test-only, use real compiler/store/routes, separate
owned RAM resources and canaries. Positive production publication remains blocked
by actual qualification; no UI or release completion follows from the state hook.
