# Shared internal v3 plan lifecycle

Internal creation/observation/target candidate extending [v3 lookup](hosted-plans-v3.md) and
[complete content](plan-content-v3.md). One HostedPlanService owns both versions.
No HTTP route, browser availability, publication qualification or export is
enabled. Runtime composition follows complete paths and version isolation.

## Shared creation and ownership

Existing create remains v2-only with unchanged command identity. Explicit internal
createV3 resolves Workspace.definitionV3; its default refuses UNSUPPORTED_DEFINITION.
Production lookup must use current V3PlanWorkspace qualification. Wrong model
version or immutable reference refuses before installation. Checked metadata alone
is insufficient; the trusted port must establish current publication qualification.

Both entries use the same lease, replay and atomic install: one plan per live
lease, four total across versions. V3 creation identity includes explicit schema3
so identical request IDs cannot alias v2 commands. Recheck original lease, replay,
destination binding/engine and shared capacity after lookup. No credential or
observation reservation during creation. Missing/foreign publications remain
non-disclosing. Models expose original metadata by version without v2 readiness.

## Observation and target work

Reservation dispatches to reserveV3 for V3 and existing reserve for V2, sharing
the same four observation slots, one-shot permit, credential buffers, original
cancellation, deadline, cleanup and replay. Ordinary write-capable accounts remain
supported through the closed read-only operation policy.

After complete observation check logical/binding pins and destination context.
V3 projection receives original cancellation and must return complete V3Observed
proof for that observation. Install under original lease/generation/revision and
complete cleanup. Observed failing computed rules remain inspectable failures.
V3 observation installs current content only. It clears any previous target and
draft and advertises TARGET_INCOMPLETE until controlled materialization establishes
V3Target evidence, including the unchanged-target case. Preserve v2's initial
current/target behavior and account for only the content actually retained.

Materialization retains original current content and a work-owned cancellation
flag; retirement/invalidation signals it. Current pin comes from retained plan
observation metadata. Target decisions use a distinct plan/revision/generation
token with original document digests. Call the actual complete v3 adapter.
Recheck lease/generation/revision, observation context, cancellation and shared
scratch before target installation. Incomplete/refused work keeps draft choices
and discards prior successful target evidence. No success from empty graphs or
caught exceptions. Controlled ContentAdapter overloads preserve legacy V2 behavior
and default to refusal for V3; legacy adapters cannot admit missing v3 proof.

The internal materialization result distinguishes COMPLETE, INCOMPLETE and
REFUSED. Its existing two-argument V2 constructor retains its prior meaning.
Existing v1 HTTP response encoding remains unchanged; future v3 responses must
expose the explicit state rather than infer it from diagnostic text.
Incomplete result references remain available in the materialization result;
the plan summary uses stable TARGET_INCOMPLETE rather than treating declaration
IDs as diagnostic codes. An unresolved draft remains readable through view().

Opaque physical entity commands and physical entity pages use the selected
version's physical declarations. Computed types are never editable decisions.
Complete declared field/reference decisions remain required; Existing handles
stay stable through identity edits and Fresh replacement retains its own origin.

## Subsequent profiles, views and validation (not implemented by this slice)

Profile lookup uses selected version with current selected/original v3 publication
checks. Shared physical profile records do not erase schema validation. V3 capture
freshly verifies original full content and uses physical-only capture/codec for
schema3 bytes, followed by original lease/revision/cancellation checks.

V3 preview retains physical preview and affected derivation IDs in exact fresh
equality and identity. Compose against freshly qualified target, use shared physical
merge and actual materializer. Preserve original Existing/Fresh refs after identity
edits. New slots require explicit values/placement; donor values or frozen computed
groups/dependencies never enter the draft.

Physical binding/every-location views use the shared qualified scanner after full
versioned content verification. Raw/Placeholders/Formatted retain disclosure and
response bounds. Explicit v3 computed views paginate complete contributor evidence
under the same lease/revision. Unknown, absent, unresolved, unchanged and unavailable
remain distinct. V2 validation preserves ES-PLAN-INPUT-1 bytes; V3 uses a separate
domain with actual compiler/derived versions, complete source/decision pins and
physical/computed rule outcomes. Client/review/content-policy remain UNKNOWN without
actual evidence. No export authority is introduced by this internal work.

## Limits and acceptance

Existing shared source/value/scratch/plan/operation limits remain mandatory. V3
physical and computed results share graph/contributor bounds. Older6GiB physical
measurements do not qualify combined proof retention; measure integrated candidate
before availability. No deployment limit or synthetic witness constitutes evidence.

Use actual invented XML/compiler/SQLite integration with clearly marked test-only
publication witnesses and actual compiler refusal. Test mixed V2/V3 quotas/replay,
ownership, default-adapter refusal, cancellation/retirement during work, stale final
installation, cleanup uncertainty, multiple documents, moved/fresh/renamed origins,
optional absence versus empty/unresolved, whole/partial physical reuse and unchanged
v2 behavior/digests. Every future HTTP route must enforce expected version before
credentials or transfer reservation.
