# Hosted v3 workspace transfer and definition history

Implemented and [independently reviewed](../evidence/qf34-workspace-http-v3.md).
These routes expose exact v3 source
compilation and immutable history through the [schema3 workspace](workspace-storage-v3.md).
The [implemented profile extension](workspace-profile-http-v3.md) adds four
draft/history routes under the same transfer and ownership rules. The
[publication extension](workspace-publication-http-v3.md) adds two guarded commands;
current compiler qualification, plan observation and export remain required MVP work. V1/v2 URLs retain
their meanings and never accept a v3 source.

## Closed routes and authority

The definition routes are PUT `/api/v3/definitions/{objectId}`, GET `/api/v3/definitions`, GET
`/api/v3/definitions/{objectId}` and GET
`/api/v3/definitions/{objectId}/revisions/{revision}`. Require hosted mode, a
configured fully audited schema3 private workspace and the existing authenticated
session, approved Host/Origin and CSRF rules. Schema2 is not upgraded by a request
and returns safe503. Only the separately contracted profile draft/history routes
and two publication POST routes extend this set.
Demo mutations and unlisted hosted methods/routes remain denied.

PUT accepts exactly the existing neutral draft wrapper fields: expectedRevision,
requestId, format and source. UUIDs, canonical decimal revisions, strict UTF-8,
duplicate/unknown keys, trailing input, JSON/YAML format and source1 MiB/wrapper
8 MiB limits follow the existing draft request contract. Native schema3 compilation
is separate; v1/v2 source rejects without persistence. Exact successful replay
precedes compilation and returns its original immutable historical result.

The typed browser save facade prepares a detached immutable pair of destination
objectId and this exact command. It accepts canonical expectedRevision0 for an
initial save and preserves JSON/YAML source exactly, without parsing or rewriting
the native model. Validate the closed pair again before its one PUT; reject
malformed scalar Unicode and source over1 MiB before transport. Response loss
permits an explicit replay of that same pair, never an automatic retry or a new
request ID. A successful result must be a draft for the same object with identical
source and format. The existing complete DefinitionRevision decoder applies;
historical replay need not match the latest current revision. The caller owns
the prepared pair in memory; the facade adds no cache or persistence. Requests
retain the existing HostedApi session revocation and late-response checks.
This adds no route, publication permission or browser storage.

Capture the original session lease before reading the request. Recheck it after
read/parse and after service execution; the final SQLite commit must use the
existing authenticated commit admission. Expiry/revocation during body reads,
compilation, lock wait or before commit cannot create new durable history/replay.
An already admitted commit may precede revocation. GET/list also retain the
original lease. Every content response uses the guarded transfer below; returning
a copied ResponseEntity alone does not establish response authority. Do not hold
the authority monitor across SQLite, encoding, servlet access or output I/O. Exact ownership remains required on every object
and historical reference. Foreign and missing objects share404; wrong-version
owned mutations conflict and wrong-version reads return404.

## Bounded owned HTTP work and transfer

Admit at most four simultaneous v3 workspace operations across this process,
shared by definition and profile routes, including body reads, storage/compilation,
encoding and transfer. Refuse excess
capacity429 without a payload job queue. Each immediately started async worker
retains its slot until its owned input, encoding and output resources close.
Use one async cycle with one registered completion owner; callback races cannot
retry completion or claim application cleanup. An in-progress completion retains
its exact operation and slot while the sole completion owner is running; it is not
a terminal refusal. A final inconclusive completion quarantines the original
session and retains its cleanup obligation. Conclusive completion releases the
slot only after the original worker's resources close. Stale notifications cannot
undo settlement or trigger a second completion attempt or successful response.
A workspace-private operation registry participates in the existing SessionCleanup
composition. Outstanding or uncertain workspace work retains its original lease
cleanup obligation and capacity; retiring the lease alone is insufficient. Once
the original worker and completion owner establish cleanup, remove only that
settled operation under the registry lock. Notify the existing
resumeCleanupAfterWork path only after no active or inconclusive workspace
operation remains for the same original lease, outside the registry lock.
Intermediate same-lease completions must not consume the session's bounded cleanup
retry budget. Other leases remain independent; retain the existing retry limit,
terminal uncertainty and stale-settlement guards. No notification renews authority. Retain bounded records
only for active or inconclusive owned work; stale callbacks cannot release another
operation or erase a prior uncertainty.

PUT reads at most8 MiB through nonblocking servlet input under one30-second body
clock, checking the original lease while waiting and reading. Readiness callbacks
signal the owning worker without storing payload bytes. No indefinitely blocked
request-body read may hold a slot after observed revocation or deadline. The
closed source parser still applies its separate1 MiB limit. GET has no body phase.
SQLite keeps its existing lock deadline; this HTTP contract does not make a
stalled filesystem syscall interruptible or renew commit authority.

Encode one complete response into bounded mutable chunks, at most8 MiB and8192
bytes per chunk; wipe them on every outcome. A transfer's single30-second clock
covers encoding through final flush, without renewal. Recheck the original live
lease during encoding, after encoding, after obtaining servlet output, before
and after each nonblocking output chunk, while waiting for readiness, and after
flush. A failed check stops subsequent content writes. A chunk already admitted
before concurrent revocation may be in flight; bytes already handed to the
transport cannot be recalled. Do not claim atomic network delivery or prevent
revocation by holding the session monitor across I/O.

Before response commitment, send a bounded safe error where transport permits.
After commitment, abort the incomplete transfer; never append another JSON
result or claim that the client received a complete response. Observed revoked
lease is403, body/encoding/transfer deadline or transport failure is safe503 where
an HTTP error remains possible, byte capacity is413, and slot capacity is429.
Late failure does not erase an already committed revision: the original request
identity remains the only replay authority after reauthentication.

Workspace-private body/encoding/output/completion adapters may follow the existing
reviewed plan transfer mechanisms. Keep their error types and lease checks
explicit; no plan DTO or plan mutation authority enters workspace operations.
These bounded primitives require actual readiness/backpressure, revocation,
late-write, callback-race, wipe and cleanup tests before claiming transfer support.

## History projection, not current readiness

Successful PUT/current/history GET return exactly objectId, workspaceRevision,
sourceDigest, format, exact source, compilerVersion, schemaVersion, state and
projection, plus publication only when present in stored historical data.
compilerVersion is native-compiler-v3 and schemaVersion is3. State preserves draft
or published history. Projection contains kind, model, logicalDigest,
bindingDigests, mechanisms and diagnostics. Its model is the closed
`definition-inspection-v3.schema.json` shape, with canonical decimal integer
strings. `logical.operationCapabilities` preserves every stored declaration in
its original order, using the schema's lowercase names; an explicitly empty list
stays empty. Display conversion cannot alter source, stored snapshots, digests or
publication eligibility. Digest/mechanism maps and diagnostic order/duplicates remain exact. Historical
diagnostics have phase publication and the safe code shape
`[A-Z][A-Z0-9_]{0,127}`; request rejection diagnostics retain their broader phases.

Projection kind is incomplete when stored diagnostics are nonempty; otherwise it
is historical-ready. The latter is explicitly historical data, never a claim of
current compiler/mechanism qualification or permission to publish. New actual
compiler results remain incomplete. A stored publication includes its exact
digest, sourceRevision and complete sorted exportPolicies. Decoding or displaying
it neither recompiles its source nor issues a new publication.

GET list returns exactly `{definitions: [...]}`. Each entry contains objectId,
workspaceRevision, nativeId, nativeRevision, sourceDigest, state, compilationKind
(incomplete or historical-ready) and logicalDigest; it omits source/model bodies.
Order is stable by objectId and the existing100-object shared quota bounds it.
There is no publication-authority flag until its separate route contract exists.

Responses are no-store; view objects must redact incidental framework DEBUG
rendering. Rejections use the existing safe422 diagnostics; invalid wrapper400,
unauthenticated401, forbidden403, missing404, conflict409, byte budget413,
object/revision capacity429 and unavailable store503 stay distinct. More than256
compiler Rejected diagnostics returns413 without truncation; larger Incomplete
history remains subject to snapshot budgets. No source, projection, owner or
credential canary may appear in logs/errors.

The semantic save422 envelope is exactly `{kind:"rejected",diagnostics:[...]}`;
it has no top-level code. Diagnostics contain exactly phase, code, pointer and
message: phases parse/shape/semantic/publication, safe uppercase code1–128
characters, scalar string pointer/message, and1–256 entries in returned order.
The browser recognizes this complete envelope only for PUT to an exact v3
definition/profile object route and surfaces REJECTED with its diagnostics.
That confirmed pre-commit outcome releases a pending draft command while retaining
editable exact source. A bare422, code-only REJECTED, malformed/incomplete envelope
or contradictory fields do not establish this outcome and retain uncertain replay.
Duplicate JSON member names at any object depth also retain uncertainty, including
escaped spellings that decode to the same name. Detect them from the raw reply
before semantic refusal classification; ordinary JSON parsing alone loses this
evidence. Repeated diagnostic array entries remain valid and ordered.
Do not extend recognition to publication/plan routes or possibly committed403/413.

## Acceptance

Join actual HTTP security/session behavior, v3 compiler, historical codec and
private SQLite. Exercise JSON/YAML exact source and historical replay after edits,
owner/kind isolation across versions, malformed/trailing/oversized input,
unsupported publication/profile methods, schema2 refusal without migration,
revocation during reads/commit, safe DEBUG rendering and startup/restart. Freeze
and independently review the integrated candidate; keep actual IdP/deployment and
v3 publication/plan qualification gaps explicit.
