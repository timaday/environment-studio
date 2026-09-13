# V3 plan integration

Status: reviewed internal publication lookup prerequisite. Hosted v3 plans remain
unavailable. This extends [v3 workspace history](native-workspace-v3.md),
[v3 observation](database-observation-v3.md), [derived graphs](derived-graph-v1.md)
and [physical-only profiles](profile-v3.md). Existing
[hosted v2 plans](hosted-plans-v1.md) keep their contracts and fingerprints.

## Current immutable publication lookup

The internal `V3PlanWorkspace` port resolves a definition or profile by exact owned
immutable workspace reference. Its versioned records contain checked v3 metadata,
publication/reference pins and definition document policies. They are internal
values, never request DTOs or authority that a browser may submit. Their incidental
rendering is redacted. No v2 Ready result is constructed from v3 metadata.

`definition(owner, reference)` reads exactly that v3 revision through the owned
store, requires a published definition with schema3 and native-compiler-v3, and
requires complete unique document policy coverage. Historical diagnostics must
be empty. Recompile its exact stored source and format through the current v3
compiler; current diagnostics must be empty and the complete checked metadata
must equal history. Historical readiness or matching logical digest alone is
insufficient. The actual current compiler still reports MECHANISM_UNQUALIFIED,
so a synthetic historical publication cannot enable plans through this lookup.

`profile(owner, reference, selectedDefinition)` first resolves that selected
definition again and requires exact equality with the supplied internal pin.
Then read the exact owned published schema3/profile-compiler-v3 profile and its
immutable original definition reference. Resolve that original publication with
the same current-definition checks. It may differ from the plan's definition
object or physical binding, but its logical digest must match the selected
definition. Recompile the exact stored profile source/format against both current
checked definitions and require full equality with the historical physical-only
profile content and original reference. No donor value, computed group or frozen
derived dependency is introduced. A later draft on either object never substitutes
for a selected immutable publication.

Null input is INVALID_REQUEST. Missing, foreign-owner, wrong-kind and wrong-version
objects retain the owned store's non-disclosing NOT_FOUND behavior. Unpublished
history refuses PUBLICATION_REQUIRED. Unsupported/incomplete/changed definition
qualification refuses UNSUPPORTED_DEFINITION; incompatible or changed profile
content refuses PROFILE_REFUSED. Null compiler results refuse as unavailable.
Source/parser limit refusals retain the bounded store/compiler behavior; no raw
source or exception detail becomes a plan diagnostic. Failed lookup changes no
history, replay state, plan or observation. There is no lookup success cache.

The store remains responsible for validating persisted source/publication digests,
history shape, ownership and version isolation. The future owning plan service
must check its original live lease, revision, cancellation and capacity before
and after lookup and atomically install the result. This prerequisite neither
creates a plan nor reserves credentials, publishes a revision, validates a target
or authorizes export. Destination admission, complete physical/derived evidence,
profile composition, views and shared lifecycle integration remain required.

## Acceptance

Use actual private SQLite with independent mock JSON/YAML declarations and
profiles. Explicit test-only compiler witnesses may remove the known mechanism
diagnostic to exercise the successful lookup sequence; actual compiler controls
must refuse those historical publications. Test exact history after later drafts
and restart, foreign owner/kind/version, unpublished content, changed checked
metadata, profile/source mismatch, stale selected pins, compatible different
binding definitions, incompatible logical definitions and no writes on refusal.
Independent tests/mutations must distinguish complete checked equality from
digest-only comparisons and prove current requalification on repeated lookup.
