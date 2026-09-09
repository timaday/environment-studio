# Hosted v3 profile drafts and historical reads

Implemented with [reviewed local evidence](../evidence/qf34-profile-http-v3.md), extending the reviewed
[workspace HTTP ownership/transfer boundary](workspace-http-v3.md) and
[owned physical-only profile draft command](profile-v3.md#owned-profile-draft-command).
Provides PUT `/api/v3/profiles/{objectId}`, GET `/api/v3/profiles`, current GET and
GET `/api/v3/profiles/{objectId}/revisions/{revision}`. New publication, observation
capture, composition, plans and export remain separate work. Existing definition
routes and all v1/v2 source/history meanings are unchanged.

## Original authority and bounded work

Require configured audited schema3 storage, hosted mode, authenticated original
lease, approved Host/Origin and existing CSRF rules. Definition and profile routes
share the same four-operation process registry, body/response clocks, encoding
limits, completion owner and SessionCleanup obligation. There is no per-kind
capacity pool. The same original lease is checked through reads, parsing, service
execution and response transfer; authenticated durable commit remains mandatory.
Profile code must not weaken the reviewed terminal uncertainty/stale callback fix.

PUT accepts exactly expectedRevision, requestId, format, source and the closed
definition reference objectId/workspaceRevision. Use strict UTF-8, duplicate/unknown
key refusal, canonical UUID/revision syntax and explicit JSON/YAML format. Require
one complete wrapper with no trailing input; no permissive generic tree reader.
The nested reference adds no arbitrary source/model object. Keep the existing
8 MiB request/response and1 MiB source limits, bounded depth/tokens and safe errors.
Read through the existing nonblocking owned body so returned byte arrays are
wiped when parsing finishes or fails. Never widen the v2 reader to accept v3.

Invoke the actual V3ProfileWorkspace and portable compiler against the exact owned
immutable historical definition publication. Current checked-model validation
still runs for unseen commands. Replay precedes reference lookup/compilation,
returns exact prior history and never creates another revision. An already
committed result may need original-command replay after a lost response; no
new request identity is invented. Historical references do not enable current
definition or profile publication. Ordinary managed-DB read policy is untouched.

## Closed historical views

Successful PUT/current/history GET returns objectId, workspaceRevision,
sourceDigest, format, exact value-free source, compilerVersion=profile-compiler-v3,
schemaVersion=3, state, definition and projection. Projection is exactly
`{kind: structurally-valid, model, contentDigest, diagnostics: []}`. The model uses
`profile-inspection-v3.schema.json` with canonical decimal native revision.
Structural validity is historical data, never current ready-to-publish authority.
Reads do not revalidate historical source against a newer compiler or require
the definition object's current revision to remain published.

A stored historical publication adds exactly digest and sourceRevision; it has
no document policy. State remains the stored draft/published value. The absence
of publication is preserved, not synthesized into an empty object. No profile
view exposes values, source locators, computed groups or contributor evidence.

GET list returns `{profiles: [...]}` with at most100 entries in stable objectId
order. Each entry contains objectId, workspaceRevision, nativeId, nativeRevision,
sourceDigest, state, contentDigest and the exact definition reference. Source and
model bodies are absent. No canPublish or runtime availability flag is added.
The [OpenAPI extension](openapi-workspace-v3.json) defines the closed shapes.

Reuse the existing no-store, safe diagnostics, DEBUG redaction and HTTP refusal
classification. Missing, foreign and wrong-version references share404; immutable
conflicts409; byte budget413; semantic validation422; operation/revision capacity429;
schema2/unconfigured store or transport503. New publication routes stay denied.
All late-response and uncertain-cleanup rules apply equally to profiles.

## Acceptance

Use actual mock OIDC/security, compiler, SQLite and owned transfers. Exercise exact
JSON/YAML history/replay after later edits and restart, owner/version/type/ref
isolation, historical publication data without current authority, unsupported
mechanism metadata, duplicate/unknown/trailing/UTF-8/oversized wrappers, strict
source-byte/snapshot refusal and DEBUG canaries. Verify actual partial-body logout,
post-commit revocation with no subsequent source disclosure, shared operation
capacity across definition/profile requests, and cleanup/recovery. Reuse the
existing real backpressure tests where the same transfer code remains unchanged;
record which profile-specific network cases actually ran. No browser/deployed
IdP/combined capacity qualification follows from this API slice.
