# Legacy plan HTTP model admission

Every existing `/api/v1/plans` and `/api/v1/operations` route addresses model V2.
The shared service can retain both model versions, but route version is a fixed
server choice, never a body, header, query parameter or inferred current-plan
property. This boundary uses the immutable version admissions in
[plan-version-admission](plan-version-admission.md). It adds no v3 route, response
field, migration, publication qualification or browser availability.

Before metadata slots, scratch, async registration, body/output access or one-shot
credential consumption, reject an owned V3 plan/operation with the same safe404
NOT_FOUND used for a missing or foreign ID. A wrong-version operation status or
cancel must not invoke cleanup callbacks or expire its reservation. Use the
original live lease and immutable original model version; a later current plan
cannot reclassify retained operation/replay metadata.

Summary reads resolve a V2 current/explicit-ID snapshot before taking the small
metadata slot or encoder. Current filtering and snapshot capture happen inside
the shared authority guard. Keep the captured plan ID and the existing final
summary equality/revision checks through encoding and output. Never replace it
with a subsequently created current plan. A wrong-version read must still be404
when the four metadata slots are occupied. No computed counts are serialized in
the legacy summary shape.

Inspection reservation checks fixed V2 ownership before body admission and fixed
V2 again at the application command. Credential routes use fixed V2 one-shot
claim. Status and both cancel preflight/final transition use fixed V2 admission.
Commands use fixed V2 command scratch admission before parsing. All thirteen view,
materialization, capture, preview and validation routes check fixed V2 before Work
construction, and acquire their view admissions with fixed V2 after small-body
decode or before collection-body decode as appropriate. An immutable ID/version
preflight does not replace existing final authority checks or hold locks across
external work.

POST creation remains explicit V2 with existing destination-owner selection and
exact legacy command identity. Destinations remain the version-neutral configured
list. Correct V2 create/reservation/command replay after retirement and terminal
status/cancel remain accessible to the same original live lease, including while
its current plan is V3. New commands against retired IDs still refuse; new login
cannot recover old metadata. Wrong-version rejection consumes no replay identity,
reservation, credential, metadata slot or scratch. Preserve no-store/error mapping,
original non-touching polling, byte/time budgets and owned transport cleanup.

Acceptance uses the actual shared service with explicitly labelled invented V2/V3
publication/observation witnesses and real controllers. Reproduce the current V3
summary exposure before changing implementation. Verify every existing route
refuses wrong version before application resource or transport access; correct
version remains usable after each refusal. Cover occupied metadata/scratch,
current-plan replacement, retained operations and replay, reservation expiry,
foreign/revoked leases, and existing actual HTTP/OIDC/transport regressions.
Test witnesses confer no current V3 compiler qualification. New V3 HTTP transfer
and combined resource qualification remain separate work.
