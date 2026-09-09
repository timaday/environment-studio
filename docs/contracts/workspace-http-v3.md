# Hosted v3 definition drafts and historical reads

Implemented and [independently reviewed](../evidence/qf34-workspace-http-v3.md).
These routes expose exact v3 source
compilation and immutable history through the [schema3 workspace](workspace-storage-v3.md).
They do not enable definition publication, profile mutation, plan observation or
export. Those operations remain required subsequent MVP work. V1/v2 URLs retain
their meanings and never accept a v3 source.

## Closed routes and authority

Add only PUT `/api/v3/definitions/{objectId}`, GET `/api/v3/definitions`, GET
`/api/v3/definitions/{objectId}` and GET
`/api/v3/definitions/{objectId}/revisions/{revision}`. Require hosted mode, a
configured fully audited schema3 private workspace and the existing authenticated
session, approved Host/Origin and CSRF rules. Schema2 is not upgraded by a request
and returns safe503. No v3 profile or publication route is admitted by this slice.
Demo mutations and unlisted hosted methods/routes remain denied.

PUT accepts exactly the existing neutral draft wrapper fields: expectedRevision,
requestId, format and source. UUIDs, canonical decimal revisions, strict UTF-8,
duplicate/unknown keys, trailing input, JSON/YAML format and source1 MiB/wrapper
8 MiB limits follow the existing draft request contract. Native schema3 compilation
is separate; v1/v2 source rejects without persistence. Exact successful replay
precedes compilation and returns its original immutable historical result.

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
including body reads, storage/compilation, encoding and transfer. Refuse excess
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
the original worker and completion owner establish cleanup, notify the existing
resumeCleanupAfterWork path without renewing authority. Retain bounded records
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
strings. Digest/mechanism maps and diagnostic order/duplicates remain exact. Historical
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

## Acceptance

Join actual HTTP security/session behavior, v3 compiler, historical codec and
private SQLite. Exercise JSON/YAML exact source and historical replay after edits,
owner/kind isolation across versions, malformed/trailing/oversized input,
unsupported publication/profile methods, schema2 refusal without migration,
revocation during reads/commit, safe DEBUG rendering and startup/restart. Freeze
and independently review the integrated candidate; keep actual IdP/deployment and
v3 publication/plan qualification gaps explicit.
