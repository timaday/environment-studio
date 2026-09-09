# Owned v3 revisions and historical snapshots

This is the explicit versioned extension for [native definitions](native-definition-v3.md)
and [physical-only profiles](profile-v3.md). Internal typed history and versioned
digests are [implemented and reviewed](../evidence/qf34-history-v3.md).
[Schema3 storage and definition drafts](workspace-storage-v3.md) are also
implemented, with [definition draft/history HTTP](workspace-http-v3.md) and
[internal profile draft commands](profile-v3.md#owned-profile-draft-command).
[Profile draft/history HTTP](workspace-profile-http-v3.md) is also implemented.
New publication and plan integration remain pending. Existing
v1/v2 sources, routes, snapshots, digests and replay records retain their meaning.
No v3 operation may be implemented by changing a v2 version field or accepting
v3 through the v2 reader. Internal history support does not enable publication.

## Separate typed history

A v3 revision has `objectId`, positive decimal `workspaceRevision`, source `format`,
exact `source`, its SHA-256 `sourceDigest`, `compilerVersion`, `schemaVersion`,
typed `content` and optional `publication`. Definitions use `native-compiler-v3`;
profiles use `profile-compiler-v3`; schemaVersion is exactly `3`.
Definition content contains the v3 checked model and its stored diagnostics.
Profile content contains the value-free checked profile and a pinned v3 definition
reference `{objectId, workspaceRevision}`. UUID/reference syntax and bounded
revisions follow the existing workspace contract. A reference's kind/version is
established by the owning v3 store, never by its UUID or a client assertion.

The internal snapshot is a closed object with `kind` equal to `definition-v3` or
`profile-v3`, all revision fields above and optional publication. It has a distinct
codec. Canonical encode/decode must round-trip exactly; duplicate/unknown keys,
malformed UTF-8, trailing input, malformed integer strings, unsupported kind/schema/
compiler combinations, mismatched source digest or noncanonical bytes refuse as
safe `UNAVAILABLE`. The existing 2 MiB snapshot, depth64, token200,000, string1 MiB
and numeric1024 limits apply. Source remains independently limited to 1 MiB by
admission. A decoded snapshot is historical data, not a new compilation result.

The codec uses the closed `definition-inspection-v3.schema.json` and
`profile-inspection-v3.schema.json` model shapes, preserving canonical decimal
strings, physical child locators and all computed declarations. Binding IDs must
exactly match their digest keys. The mechanism name set must match the v3 model's
required dependency names; known historical positive versions up to1024 decimal
digits remain readable. Decoding must not recompile the model, substitute current
mechanism versions, recompute logical/binding/profile digests under current rules,
remove diagnostics or promote an incomplete draft. Required digest fields are
exactly64 lowercase hex characters. Owner/store integrity and the pinned historical
definition still govern reads and profile compatibility.

Stored incomplete diagnostics remain PUBLICATION diagnostics with their original
order and safe code shape `[A-Z][A-Z0-9_]{0,127}`. Preserve historical duplicates;
do not normalize the stored list. Its total size is bounded by snapshot byte/token
limits, with no additional arbitrary diagnostic-count cap. An empty diagnostic list can describe a historical ready
revision; it is not current publication eligibility. An incomplete definition
cannot carry a publication record. Any current publication path must separately
verify the currently supported compiler/mechanism vector and complete integrated
qualification. The current v3 compiler remains unqualified.

## Versioned identities

Source SHA-256 remains a digest of the exact UTF-8 source. Command hashing uses
the existing closed command fields and strict native framing with domain
`ES-WORKSPACE-COMMAND-3` plus a zero byte. Preserve exact expected revision,
request ID, format/source, pinned definition reference and sorted document policies.
Do not reuse a v2 command digest for replay in v3.

Publication framing uses `ES-DEFINITION-PUBLICATION-3` or
`ES-PROFILE-PUBLICATION-3`, each followed by a zero byte. Its fields match the
existing versioned publication intent: object/workspace/source revision,
source digest, compiler/schema versions, and either logical/binding/mechanism
digests with complete document policies, or profile content digest with its pinned
definition reference. Mechanism versions are canonical decimal strings; policies
sort by binding/document. Profile publications have no document policy entries.
The publication sourceRevision must be a positive canonical decimal of at most
1024 digits and exactly workspaceRevision minus one, preserving the preceding
revision semantics of v2 publication. The codec verifies a present publication's
digest and complete policy coverage;
this historical consistency check does not authorize a new publication.

## Workspace and HTTP integration boundary

The [explicit schema3 store](workspace-storage-v3.md) implements persistence and
definition draft saves. Its [definition HTTP routes](workspace-http-v3.md) preserve
the original lease through bounded input and response transfer. The
[profile draft/history routes](workspace-profile-http-v3.md) share that boundary.
Subsequent publication integration must preserve
exact owner/lease checks, UUID/type continuity, quotas, immutable revision history,
exact replay before compilation, live maintainer admission for definition
publication, and explicit profile-owner publication against an eligible maintained
definition. Plans cannot obtain publication authority from an internal history DTO.
All existing source/privacy/content-policy and session commit checks apply.

Use a closed `/api/v3/definitions` and `/api/v3/profiles` extension with typed
commands/responses before adding routes. Existing v2 URLs never accept v3.
Storage must register explicit v3 artifact kinds and versioned snapshot integrity
and replay domains. Any required local workspace schema change needs its own
documented offline upgrade, backup/restart/rollback behavior and invented-data
verification before use. No automatic source conversion, reinterpretation of old
snapshots or startup migration is authorized by this contract. This work concerns
the application's private workspace, never managed configuration database writes.

## Internal publication commands

The next application slice provides separate typed definition/profile publication
commands through `V3PublicationWorkspace`. It reuses the owned schema3 store and
current versioned definition/profile compiler ports. It adds no route, public
configuration, runtime availability assertion or way to remove the compiler's
current MECHANISM_UNQUALIFIED blocker. Public publication and complete integrated
qualification remain subsequent work.

Definition publication requires the deployment-owned maintainer predicate before
replay and again before append. Profile publication requires the authenticated
profile owner, preserving the existing role distinction. Exact successful command
replay precedes current lookup or compilation; a replay returns historical data
without creating a new publication. Store-level owner, partition, immutable
revision, stale-command and final authenticated commit checks remain mandatory.

For an unseen command, read the exact owned current draft of the requested kind.
Refuse an already published revision, unsupported compiler/schema, or incomplete
historical definition. Definition publication requires exactly one explicit
policy for every binding/document and no extra or duplicate entries. Recompile
the draft's exact source/format through the current v3 compiler; require no
publication diagnostics and exact equality of the freshly checked definition,
logical/binding digests and mechanism vector to the stored checked result. A
stored historical-ready flag is insufficient. A null compiler result is unavailable.
Current actual compilation remains incomplete, so no production publication can
succeed through this slice.

For a profile draft, resolve its exact owned immutable historical definition
reference, require an existing publication and supported compiler/schema with
empty historical diagnostics, then apply the same current definition recompilation
check. Recompile the exact profile source against that fresh checked definition;
require equality of checked physical profile/content digest and unchanged exact
definition reference. No current-definition substitution, donor values, computed
membership or stale compiled profile may enter publication. Missing/foreign/
wrong-kind/version references keep the store's indistinguishable refusal.

A successful qualified command appends one bounded next revision with unchanged
source/format/content and the existing v3 publication framing. `sourceRevision`
is the preceding current revision. Definition policies are complete and sorted;
profile policies are empty. Reject revision overflow before constructing a larger
revision. All failures leave revision/replay state unchanged; atomic append
refusals propagate once without retry. No v2 artifact/digest is reinterpreted.

Acceptance uses actual schema3 SQLite and current compilers to prove refusal of
both incomplete drafts and test-only historical-ready data. Positive publication,
immutable history/replay, policy coverage, maintainer recheck, reference continuity,
stale concurrency, capacity and commit-refusal cases may use an explicitly labelled
test compiler witness. Such witnesses exercise the application/store transition;
they do not qualify the real compiler or enable public routes. Preserve a meaningful
failing behavior test, actual guard mutants and independent fixed-candidate review.

## Acceptance

Use independent v3 source/profile fixtures, frozen digest oracles and old v2
snapshot bytes. Demonstrate byte-exact history round-trips, retained incomplete
diagnostics and historical mechanism versions, refusal of altered kinds/digests/
policies/schema/fields, and no source recompilation during history reads. New
publication and persistence require their own actual operation evidence. Until
those paths qualify, v3 runtime availability remains disabled.
