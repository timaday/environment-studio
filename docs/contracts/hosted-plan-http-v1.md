# Hosted plan HTTP boundary — planned

This is the wire boundary for [hosted plans](hosted-plans-v1.md), with a
[closed initial OpenAPI contract](openapi-plans-v1.json) and
[semantic command schema](../../schemas/plan-command-v1.schema.json). It is not an
enabled API or a claim of database/export qualification. Existing native
workspace APIs remain the publication and immutable-profile persistence boundary.

Every route requires the current hosted lease, approved Host, owner authorization
and no-store responses. Every unsafe request additionally requires Origin and
CSRF. Demo denies the routes. Cross-owner, foreign-lease and missing objects
share a safe 404. Retired plans return 404 for content and new commands; the
current lease's retained replay and operation metadata follow the exceptions
below. Request IDs never substitute for current authentication.
No request contains an owner, driver properties, JDBC URL, observed XML, claimed
validation outcome, execution command or serialized capability.

## Initial operations

| Method and path | Closed request / result |
| --- | --- |
| GET `/api/v1/destinations` | `{destinations: [...]}` with only destinations authorized by external configuration for the current owner; each item has id, engine, host, port and database |
| POST `/api/v1/plans` | expectedRevision `0`, requestId, definition `{objectId, workspaceRevision}`, bindingId and destinationId → small Ack |
| GET `/api/v1/plans/current` | Current lease's live plan summary or 404 |
| GET `/api/v1/plans/{planId}` | Safe summary with revision, pins, complete counts and evidence/target availability |
| POST `/api/v1/plans/{planId}/inspections` | expectedRevision, requestId, `discardDraftOnSuccess: true` → Ack containing reserved operationId |
| POST `/api/v1/operations/{operationId}/credentials` | Exactly username/password, once after reservation; bounded synchronous submission returns safe operation status |
| GET `/api/v1/operations/{operationId}` | Safe operation status; no credential resubmission |
| POST `/api/v1/operations/{operationId}/cancel` | Exactly `{}`; idempotent cancellation of that operation, returns safe status |
| POST `/api/v1/plans/{planId}/commands` | Closed tagged semantic command, expectedRevision and requestId → Ack |

Ack is exactly `planId`, `revision` and optional `operationId`. It contains no
source, values or authority. Creation replay is lease-scoped: its HMAC input uses
the literal `new-plan` in place of the not-yet-generated plan ID. A repeated
successful create returns its original Ack even after discard; it never restores
the old plan. All command and terminal-operation metadata remain bounded per
lease across discarded/recreated plans, as specified in hosted-plans-v1.
After checking the live lease and ownership, look up the lease-scoped request ID
before checking whether the plan is retired or its revision stale. This applies
to create, inspection reservation and every semantic command, including discard.
An exact command replay returns its original Ack; the same request ID with a
different plan/body returns 409. A new command for a retired plan returns 404.
Polling or cancelling a retained operation remains available to that same live
lease after plan discard; terminal cancellation is idempotent. None of these
metadata responses restores raw content, reservations or authority. Revocation
removes access to all such metadata, including for a new login by the same owner.

An operation status contains `operationId`, `planId`, phase, safe outcome code,
cleanup state and optional installedRevision. Credential submission can finish
synchronously because the reservation ID is already known; another request can
poll/cancel concurrently. The browser never retries that POST. A lost response
does not create a new operation. No raw credential-bearing request enters an
executor/job queue. Platform HTTP timeouts must allow the bounded operation plus
cleanup; a disconnected client does not establish successful backend cleanup.

Authorized credential bodies are consumed only after atomic one-shot admission.
Bound the entire JSON wrapper to 16 KiB and depth 2, with duplicate/unknown fields
and malformed scalar Unicode refused. Enforce the username/password code-point
and UTF-8 byte bounds before the reader hands owned buffers to the adapter.
Malformed authorized submission consumes the reservation; foreign access does not.
Disable request/response-body logging, framework payload DEBUG and credential
tracing before enabling the route. Existing driver logging admission still applies.
Create, reserve and cancel each use a 16 KiB streaming wrapper limit, depth 4 and
128 JSON tokens. Credential decoding allows at most 16 tokens. All four routes
reject duplicate fields, trailing roots and non-JSON whitespace. Their body-read
deadline is 10 seconds from admitted read, independent of subsequent bounded
database work; arbitrary incoming bytes do not renew it. Slow or disconnected
authorized credential submissions consume the attempt and initiate cleanup.
Read/status polling captures the lease without touching its idle deadline; it
must not renew the session indirectly through an earlier HTTP security filter.

## Draft commands and references

The semantic command envelope contains `kind`, `expectedRevision`, `requestId`
and only the fields declared for that kind. Supported initial command kinds are
`replace-draft`, `batch-upsert`, `upsert-entity`, `forget-entity-decision`, `bind-field`,
`bind-reference`, `move-containment`, `compose-profile` and `discard`.
The full draft contains explicit entity decisions, containment decisions and
placements. These are tool commands, not an uploaded graph or target XML.

An Existing entity reference is `{kind: "existing", handle}`. The server resolves
the opaque observation-bound handle to a stable graph key. A Fresh reference is
`{kind: "fresh", slotId, typeId}`. It remains Fresh after materialization until a
new inspection creates a new observation. Field/reference maps must cover every
declared selected field/reference explicitly; unknown names, duplicate IDs and
implicit missing defaults refuse. `forget-entity-decision` removes an override,
restoring observation retention where applicable; it never means entity deletion.
Explicit deletion uses a remove entity decision and its dependency checks.

`batch-upsert` contains nonempty `changes` and an explicit `containment` array.
Each change is exactly `{decision, placements}` and supplies one complete entity
decision and that entity's complete replacement placement set; an empty placement
set explicitly clears its overrides. Merge entity decisions by stable Existing or
Fresh reference and containment decisions by `(relationId, child)`. A placement
must name its containing change's entity; duplicate entity, containment or placement
keys refuse the entire batch. Preserve all unmentioned entity decisions, placement
sets and containment decisions. No missing field/reference decision is inferred.
The whole batch consumes one request ID and advances the semantic revision once.
It may leave an explicitly incomplete draft, with no target/export authority.
`upsert-entity` has the same single-change semantics. Large drafts can therefore
be assembled through bounded batches without requiring one of the 256 retained
command IDs for each entity. Maximum-shape batch partitioning and heap behavior
still require implementation evidence; replay entries are never evicted to fit.

`bind-field` and `bind-reference` operate on an already selected draft entity and
replace only the named existing decision; they do not silently create a selection.
Field states are unresolved, entered with exact text, keep-observed and absent.
Reference states are unresolved, to an explicit entity reference, keep-observed
and absent. `move-containment` replaces the declared relation's parent decision
for the specified child. Structural removal/creation and capability checks follow
the complete intent compiler; malformed input never partially mutates the draft.

Placement names entity, documentId, projectionId and an explicit parent. An
existing parent pins documentId, sourceDigest and canonical elementIndex; a fresh
parent pins its Fresh entity reference. The server resolves all source-bound
coordinates against the current observation. No supplied offset/digest is trusted
as evidence of a match. Edits invalidate authority before target recomputation.

Bound an individual semantic command wrapper to 128 MiB, strict depth 16 and
8,000,000 JSON tokens; decoded retained input still obeys the much smaller
document/value/graph budgets. Stream and reserve decode scratch before allocation;
do not keep both an entire parsed JSON tree and a second raw-body String. The
large wrapper ceiling supports bulk replacement within that cap. It is not a
guarantee that every permitted full draft fits: repeated field/reference metadata
can exceed the wrapper and token ceilings even without entered values. Use the
bounded batch-upsert and incremental entity/field/reference/move commands to build
larger supported drafts;
each command still obeys all retained scope limits. Ordinary per-field commands
use the same bounded decoder. The body-read deadline is 30 seconds from admitted
read, without extension for incoming bytes. Exhausted scratch
refuses before body allocation and never queues the request. Exact limits require
maximum-scope heap qualification before advertisement, not an assumption that a
request below the wire ceiling necessarily fits retained semantic capacity.

## Subsequent route groups

Profile capture, closure preview, paged graph/document inspection, all comparison
modes, validation, review, artifact download and fresh verification follow the
same revision/lease rules. Their exact closed DTOs are added before those routes
are implemented. In particular a query-string reveal flag cannot replace the
explicit complete-document disclosure acknowledgement required by hosted-plans-v1.
Absent D07 qualification returns a backend blocker; no public route accepts PASS
or exposes an execute operation. Missing downstream routes remain denied.

HTTP mapping: malformed/unknown input 400, no live authentication 401,
Host/Origin/CSRF/policy denial 403, missing/foreign lease 404, stale/colliding/busy
or consumed reservation 409, wire byte overflow 413, semantic refusal 422,
bounded plan/operation/memory capacity 429 and unavailable trusted services 503.
Use a closed `{code}` error with a stable tool-owned code. Diagnostic detail uses
an explicit safe DTO, never raw input, JDBC text, SQL, exceptions or credentials.
