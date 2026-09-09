# Definition workspace boundary — D02b

This adds authenticated runtime draft upload and immutable local revisions to
[hosted sessions](hosted-session.md). It does not publish definitions or accept
database credentials. The version 1 compiler still returns incomplete drafts.
Stored application declarations are runtime inputs outside the image and checkout;
only independently invented examples belong in repository tests.

## Requests and authority

Hosted mode requires a configured private workspace directory before enabling
these routes. Demo mode continues to deny mutations. The owner comes exclusively
from the authenticated session's issuer and subject. Origin, CSRF, session expiry
and server-side ownership checks apply to every route. Responses are no-store;
errors contain stable codes and safe messages, never submitted source or paths.
Configure the directory with `studio.workspace.directory`. With that setting
absent, hosted authentication may run but workspace routes remain unavailable
and capabilities report `definitionWorkspaceEnabled: false`. A configured but
missing, invalid or incompatible store fails startup; never fall back to empty
state. A valid configured store enables only these owned draft routes and reports
`definitionWorkspaceEnabled: true`; inspection/export remain false.

`PUT /api/v1/definitions/{objectId}` accepts a closed JSON object with
`expectedRevision` (canonical nonnegative decimal string), `requestId` (UUID),
`format` (`JSON` or `YAML`) and `source` (string). `objectId` is a canonical UUID
chosen before submission, so the caller can retry a lost create response using
the same object, request ID and body. Revision `0` creates an absent object;
subsequent successful writes increment the server-owned workspace revision.
The uploaded native document's own `id` and `revision` are source declarations,
not mutation authority. Its `id` cannot change within a workspace object.

The compiler applies its existing UTF-8, syntax, shape, semantic and numeric
budgets. Reject unknown wrapper fields and request bodies exceeding 8 MiB before
unbounded binding. Decode wrapper UTF-8 strictly and reject duplicate keys.
Reject malformed Unicode, including lone surrogates created by JSON escapes,
before strict UTF-8 encoding of decoded source; replacement encoding is forbidden.
Decoded source remains limited to 1 MiB UTF-8. A rejected
compilation returns 422 with bounded diagnostics and persists nothing. An
incomplete result may be saved; it never changes publication/export authority.

Successful create/update returns 200 with `objectId`, `workspaceRevision`,
`sourceDigest` (SHA-256 of exact UTF-8 source), `format`, exact `source` and the
compiler's immutable incomplete projection. A repeated request with identical
body returns that same revision/result even after later edits. A reused request
ID with different content, stale expected revision, or changed native `id`
returns 409. Atomic replay semantics follow the hosted-session contract.
Command identity frames decoded `expectedRevision`, `requestId`, `format` and
`source` in that fixed order, each as decimal UTF-8 byte length, `:`, then exact
bytes, and hashes the concatenation with SHA-256. Wrapper whitespace/property
order does not matter; source whitespace and characters do. The request ID is
scoped by owner and object, never shared across objects.
Persist the original projection and diagnostics with compiler/schema versions,
not only source. Historical GET/replay never recompiles using a later engine and
silently changes the original result.

The D02b response also includes `compilerVersion: "definition-compiler-d01a"`,
`schemaVersion: "1"` and `projection: {kind: "incomplete", model, diagnostics}`.
The model uses native v1 property names, lowercase enum values, and canonical
decimal **strings** for every arbitrary-precision integer. Source retains its
original numeric notation. Diagnostics use `phase`, `code`, `pointer`, `message`;
saved projections contain publication diagnostics. A 422 response is
`{kind: "rejected", diagnostics}` with lowercase parse/shape/semantic/publication
phases. Other workspace errors use `{code, message}` with a stable `WORKSPACE_`
code and constant safe corrective text. These DTOs must have safe log rendering;
framework DEBUG logging must not expose source or secret-bearing fields.

`GET /api/v1/definitions` returns owned objects in object-ID order with current
workspace revision, native definition ID/revision and source digest. It omits
source and model bodies. `GET /api/v1/definitions/{objectId}` returns the current
revision; `GET /api/v1/definitions/{objectId}/revisions/{revision}` returns the
specified immutable revision. Cross-owner and missing objects return the same
404. Revision syntax errors return a safe 400. No delete or publish route exists
in this slice. Unknown API routes stay denied.
The list envelope is `{definitions: [...]}`; each item contains only `objectId`,
`workspaceRevision`, `nativeId`, `nativeRevision` and `sourceDigest`, all strings.

## Private SQLite metadata adapter

Use SQLite behind a narrow core port for tool-owned metadata only. This grants
no managed-configuration write capability or SQL execution API. Do not add a
database pool. The configured absolute directory is a private runtime volume,
not a classpath resource, image layer or repository folder. Verify its ownership
and restrictive permissions before accepting traffic. Reject symbolic links in
the configured path and database/journal paths, unsafe permissions, malformed
records, unsupported versions and ambiguous recovery state. Explicit offline
initialization creates a new database and catalog; service startup requires the
established database and never creates an absent one. Refuse unknown storage
schemas rather than performing an implicit migration.
The offline entry point is `java -jar app.jar --initialize-workspace=/absolute/directory`.
It accepts exactly that argument, starts no web server or IdP client, requires an
existing private owned directory and creates `studio-workspace.db` only when
absent. Existing storage is never overwritten. Normal hosted startup opens that
same file without a create option. Success/failure emits only a safe status code.

Encode owner identity unambiguously and use bound SQL parameters. Neither owner
claims nor object IDs influence file paths or SQL syntax.
Persist only allowlisted source declarations, immutable revision metadata and
bounded replay information. Never persist OIDC tokens, session/CSRF secrets,
database credentials, observations, target values or SQL artifacts. Uploaded
definitions follow the closed definition schema; arbitrary workspace payloads
are not accepted by this port.

Object catalog, revisions and replay records commit in one SQLite transaction.
Use rollback-journal DELETE mode, synchronous EXTRA, foreign keys and bounded
busy timeout; verify actual pragma values. Success requires commit success.
Crash yields the complete old or new transaction; ambiguous commit errors return
unavailable and exact retry resolves through persisted replay state. Qualify
rollback-journal recovery. Database and auxiliary files require restrictive
permissions inside the private directory; metadata never uses an external
temporary directory. Bound page/cache and temporary memory, database size and
transaction journal space. No SQL extensions or uploaded queries run. See
[SQLite durability](https://www.sqlite.org/pragma.html#pragma_synchronous) and
[atomic commit](https://www.sqlite.org/atomiccommit.html). The pinned JDBC adapter
is [Xerial 3.53.4.0](https://github.com/xerial/sqlite-jdbc/releases/tag/3.53.4.0).

Apply the hosted-session limits atomically across concurrent writes: 100 objects
per owner, 32 revisions per object, 256 replay records per object, 2 MiB per
record and 256 MiB per workspace. Reject exhausted capacity before mutation;
do not evict old revisions or replay records to admit a write. Revision history
and replay responses can reference the same immutable stored source; no need to
duplicate it. The database plus journal must fit the workspace budget; reserve
worst-case journal space before writes and bound database page growth. Temporary
writes cannot expose partial records as successful reads. Logout/expiry invalidates session
authority while saved eligible revisions remain owned and available after a
fresh login as the same issuer/subject.

## Evidence

Observe RED/GREEN for create/read/revise/replay, native-ID conflict, semantic
rejection without persistence, owner isolation and restart recovery. Use an
independently invented draft and temporary private directories outside the
checkout. Challenge simultaneous stale writes, request-ID conflict, quota
boundaries, symlinks, unsafe permissions, corruption and interrupted transactions.
Trace synthetic provider/session canaries through persisted bytes and safe error
responses. The local filesystem evidence does not qualify actual HiveForge
volume, backup or multi-replica behavior. Valid whole-workspace rollback cannot
be detected without external freshness evidence; backup/restore and total-volume
loss remain deployment responsibilities, never claimed locally proved.

The [live commit-admission rule](native-workspace-v2.md#mutation-and-storage-integrity)
also governs v1 saves: revoked/expired authority during request reading or
compilation must persist no new revision or replay record.
