# Owned native publication and profiles — D01c/D05b

This extension builds on [owned v1 drafts](definition-workspace.md),
[native v2 compilation](native-definition-v2.md) and [value-free profiles](profile-v2.md).
The v1 routes and stored historical compiler results retain their existing
meaning. Version 2 routes accept only their declared native artifact type; no
generic payload store or automatic interpretation of v1 as v2 is introduced.
The closed [OpenAPI extension](openapi-workspace-v2.json) and
`schemas/definition-inspection-v2.schema.json` /
`schemas/profile-inspection-v2.schema.json` specify the implemented DTOs. The routes require an initialized private hosted
workspace; actual identity-provider and deployment qualification remain separate.

## Native definitions

`PUT /api/v2/definitions/{objectId}` uses the existing closed draft command:
`expectedRevision`, `requestId`, `format`, `source`. The UUID, strict decoding,
1 MiB source/8 MiB wrapper limits, exact replay-before-compile, native-ID continuity,
owner isolation and safe errors remain unchanged. Only native schema version 2
is accepted. Rejected compilation persists nothing and returns 422. Both
incomplete and ready-to-publish results can be saved as immutable **drafts**.
Readiness from the compiler does not itself publish anything.

Successful save/read returns the closed object `objectId`, `workspaceRevision`,
`sourceDigest`, `format`, exact `source`, `compilerVersion` (`native-compiler-v2`),
`schemaVersion` (`2`), `state` (`draft` or `published`) and `projection`.
The projection has `kind` (`incomplete` or `ready-to-publish`), `model`,
`logicalDigest`, `bindingDigests`, `mechanisms` and `diagnostics`. `model` follows
native v2 property names and enum spelling, with all arbitrary-precision integers
as canonical decimal strings. Binding digests are keyed by exact declared binding
ID; mechanism names are the closed current registry and versions are canonical
positive decimal strings of at most 1024 digits. The response schema represents
readable historical versions; only the current supported version set may publish.
Diagnostics retain the existing safe phase/code/pointer/message shape. Historical
reads and exact retries decode the stored typed projection; they never recompile
it under a later compiler or derive a new readiness outcome.

`GET /api/v2/definitions`, current GET and `/revisions/{revision}` follow the
v1 ownership, ordering, errors and immutable history semantics. The list envelope
is `{definitions: [...], canPublish: boolean}`; canPublish reflects the current
session's maintainer authorization. Each item has the existing five metadata fields plus
`state`, `compilationKind` and `logicalDigest`, without source/model bodies.

`POST /api/v2/definitions/{objectId}/publish` accepts exactly `expectedRevision`,
`requestId` and `exportPolicies`. Require the current owned draft to contain a
ready result using the supported stored compiler/schema/mechanism versions.
An incomplete draft returns a safe 422 and remains unchanged. Publication creates
the next immutable workspace revision with the same exact source and original
projection, `state: published`, and a `publication` object. An unrelated repeated
publish request against a published current revision conflicts; exact request
replay still returns the original result. A later PUT creates a new draft and
does not modify any previously published revision or plan reference.

Definition publication requires the maintainer role from the product contract.
For the initial single-replica deployment, configure at most 64 exact authorized
`{issuer, subject}` pairs in the private `studio.workspace.definition-publishers`
allowlist. Compare against the authenticated OIDC owner; no request claim, header,
display name or uploaded document grants this role. An absent/empty allowlist
authorizes no definition publisher and does not prevent draft work. Refuse a
non-maintainer publication with 403 before replay lookup or mutation, including
an old successful request whose caller no longer has publication authority.
This live authorization check precedes the usual replay/revision ordering.
Profile publication remains an owner operation against a maintained definition;
it cannot change that definition or its document export policy.

`exportPolicies` is an array containing exactly one closed object for every
declared `(bindingId, documentId)`: those two IDs and `content`, either `deny`
or `protected-self-contained`. Reject duplicate, missing, extra or unknown
declarations, never default omitted choices. A protected policy explicitly
permits the complete original and target document in a protected artifact,
including unchanged/unmapped content and known secrets. The UI must explain this
before publication; masking does not change actual package bytes. `deny` remains
a valid publication choice but blocks export of any package requiring that
document. Unknown field sensitivity remains a compiler blocker. This document
policy does not authorize arbitrary SQL, unqualified clients or unsupported edits.

The publication object contains exactly `digest`, `sourceRevision` (the preceding
draft revision), and `exportPolicies` sorted by binding ID then document ID.
Its digest uses domain `ES-DEFINITION-PUBLICATION-2` and a zero byte, followed by
native framing of `{objectId, workspaceRevision, sourceRevision, sourceDigest,
compilerVersion, schemaVersion, logicalDigest, bindingDigests, mechanisms,
exportPolicies}`. Revisions/versions are canonical strings, binding digests and
mechanisms are closed maps, policies are sorted as above. Owner identity remains
in the protected storage integrity envelope and authorization checks, not the
portable content digest. Plans pin object ID, published workspace revision and
publication digest; native source ID/revision are never mutation authority.

## Owned value-free profiles

`PUT /api/v2/profiles/{objectId}` accepts `expectedRevision`, `requestId`, `format`,
`source` and a closed `definition: {objectId, workspaceRevision}` reference to an
owned published v2 definition. Resolve this reference before compilation; a
profile's claimed logical digest is not evidence that a publication exists.
The strict profile parser/validator must accept the source against that pinned
definition, including exact digest/type/field/graph compatibility. Store a new
draft only after successful validation and the same immutable/replay checks.
Capture from a session observation uses the same typed profile validator/store;
there is no bypass accepting an arbitrary object or donor value map.

Save/read returns `objectId`, `workspaceRevision`, `sourceDigest`, `format`, exact
value-free `source`, `compilerVersion` (`profile-compiler-v2`), `schemaVersion`
(`2`), `state`, the pinned `definition` reference and `projection`.
The latter is `{kind: ready-to-publish, model, contentDigest, diagnostics: []}`;
`model` follows the closed profile v2 shape with native revision as a decimal
string. Successful validation is a draft until explicitly published. Invalid
profiles return 422 safe diagnostics and persist nothing.

`GET /api/v2/profiles`, current GET and `/revisions/{revision}` have the same
ownership and history rules. The list envelope is `{profiles: [...]}` with
`objectId`, `workspaceRevision`, `nativeId`, `nativeRevision`, `sourceDigest`,
`state`, `contentDigest` and the pinned `definition` reference. Source/graph
bodies are omitted. `POST /api/v2/profiles/{objectId}/publish` accepts only
`expectedRevision` and `requestId`, checks the current valid draft and unchanged
compatible definition publication, then creates the next immutable revision.
Require supported stored profile-compiler/schema versions and supported mechanisms
in the pinned definition publication. The reference selects that exact immutable
historical revision; a later draft on the definition object does not invalidate
it or require the current definition revision to remain published. Unsupported
versions block new publication, while historical GET and exact replay retain
their original result unchanged.
Keep registered readable historical codecs separate from the current publication
mechanism registry. Unknown/unreadable codec variants still refuse startup/read;
this rule does not permit decoding arbitrary future versions as a known type.
Published responses add `publication: {digest, sourceRevision}`. Hash domain
`ES-PROFILE-PUBLICATION-2`, a zero byte and native framed `{objectId,
workspaceRevision, sourceRevision, sourceDigest, compilerVersion, schemaVersion,
contentDigest, definition}`. No profile stores a document export policy, physical
binding, target observation, donor identity/value or credential.

## Mutation and storage integrity

All mutations require the current authenticated hosted session, approved
Host/Origin and CSRF. Demo mode denies them; responses are no-store and safe to
render under framework DEBUG without source or credential canaries. Unknown
fields and malformed wrappers return 400, cross-owner/missing references share
404, stale/reused command identity returns 409, wrapper/source/portable-output/
snapshot byte-limit refusal 413, semantic/publication refusal 422, bounded
owner/object/revision/replay capacity 429 and unavailable storage or store quota
503. Parser structural/numeric refusal remains a compilation diagnostic; byte
limits do not authorize accepting a truncated source or snapshot. No delete route exists; immutable
references cannot dangle through deletion. Changing source native ID or artifact
kind under an existing object ID conflicts. UUID object IDs share one catalog
namespace and the 100-object owner quota across v1 definitions, v2 definitions
and profiles.

Workspace mutation authority is checked again at the durable commit boundary, not
only before reading a request. Read/parse/compile, replay/revision checks and SQLite
lock acquisition occur before final commit admission. Admission atomically verifies
the exact original session lease and current idle/absolute expiry without renewing
lifetime. If revocation/expiry wins admission, roll back and persist no new revision
or replay record. This rule also applies to v1 definition saves.

At most one commit may be in flight per lease. An admitted commit is ordered before a
later revocation; logout cannot undo that transaction. Revocation immediately denies
later commands, while the admitted commit and actual connection cleanup retain the
lease's bounded capacity. Inconclusive completion quarantines it. Never hold the
global session authority monitor across request reads, compilation, SQLite I/O or
cleanup, and never interpret an interrupted thread as proof of rollback/close.
SQLite lock deadlines do not guarantee a bound on operating-system commit I/O.
A lost response retains original-command replay semantics; do not invent another
request ID or claim a known outcome before the store establishes it.

V2 command identity uses domain `ES-WORKSPACE-COMMAND-2`, a zero byte and native
framing of the closed decoded command with `kind` added (`save-definition`,
`publish-definition`, `save-profile`, `publish-profile`). Include all request
fields; preserve exact source, normalize only the unordered export-policy array
into the specified order. UUID object ID/owner scope the replay record. Different
routes/kinds cannot replay each other's commands. Resolve successful replay
before current revision, compilation or reference-readiness checks; it returns
the original immutable response even after later edits. Atomic checks repeat
inside the write transaction so simultaneous stale commands cannot both succeed.

The private SQLite store adds explicit typed artifact/revision/publication and
reference storage, retaining v1 snapshots/codecs unchanged. Every successful save
or publish consumes one of the 32 workspace revisions per object. Keep 256 replay
records, 2 MiB per immutable record and the combined 256 MiB workspace/journal
budget; never evict history or references. Validate all artifact variants, owner
bindings, source/projection/publication digests, revision chains, replay commands
and reference compatibility on startup and read. The integrity envelope binds
owner, object kind/ID, exact source, original typed projection, compiler/schema,
reference and publication policy. Reject corrupted/unknown variants rather than
accepting a generic JSON blob. Whole-workspace malicious rewrite or rollback
still requires external freshness/trust evidence; an unkeyed digest cannot prove it.

Storage schema 2 is explicit. Initialization creates schema 2 in an absent store.
An existing schema 1 store requires the sole offline argument
`--upgrade-workspace=/absolute/private/directory`; it starts no web/IdP service,
validates the complete supported schema 1 store first and upgrades all metadata
in one checked transaction. It preserves every source/projection, owner, revision
and replay result without recompilation. An already upgraded, unexpected or
corrupt store refuses unchanged; no automatic migration/fallback occurs during
hosted startup. Interruption produces the whole old or new schema, never a
partially admitted store. The same ownership/path/journal/permission protections
apply before, during and after upgrade. Older binaries refuse schema 2; deployment
rollback must account for the separately managed volume and cannot blindly reuse
it with an older binary.

## Acceptance evidence

Observe meaningful RED/GREEN for v2 draft/save/publish/fork/history/replay,
incomplete publication refusal, policy omissions/duplicates, cross-owner references,
kind collisions, profile compatibility and total quotas. Independently verify
publication/content digests and persisted canary absence. Race two stale save or
publish commands; only one new revision may commit. Corrupt each typed authority
field without changing its integrity envelope and require refusal. Exercise an
actual v1 store upgrade with unchanged historical responses, interrupted upgrade,
unsupported schemas and exact-repeat refusal. Repeat hosted auth/CSRF/DEBUG
canaries and protected OCI initialization/upgrade checks. Actual HiveForge storage,
IdP and application qualification remain external evidence requirements.
