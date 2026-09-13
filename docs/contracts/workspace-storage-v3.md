# Explicit schema3 workspace extension

Status: contract for implementation. This extends
[v3 historical revisions](native-workspace-v3.md) in the application's private
workspace only. Managed configuration databases remain read-only. Existing v1/v2
commands, snapshots, digest domains and stored source bytes retain their meanings.

## Shared catalog and separate typed records

Schema3 retains the exact schema2 catalog, v1 tables and v2 typed tables. Add
separate `v3_artifact_types`, `v3_native_revisions` and `v3_native_replays` tables.
The v3 kind set is exactly `definition-v3` and `profile-v3`. Its typed revision and
replay shapes follow schema2, with versioned codecs, command digests and integrity
envelopes; no v2 row is widened, moved or reinterpreted. V3 profile references
resolve only to an owned immutable published v3 definition in the same store.

All kinds share the existing UUID/owner catalog, 100 objects per owner, 32 immutable
revisions per object, bounded replay records and combined workspace/journal budget.
An object can have exactly one versioned kind for its lifetime. Reusing an owned
UUID across kind/version refuses CONFLICT; foreign ownership remains NOT_FOUND.
V1/v2/v3 list and read routes only return their own kinds. UUID syntax or a supplied
logical digest cannot establish a reference's kind, owner or publication.

On startup and before v3 reads and mutations, verify the complete registered schema, integrity,
foreign keys, kind partition, contiguous history, native ID continuity, exact
replay-to-revision correspondence, publication chains and profile references.
Reject overlapping kind registrations or v3 objects with legacy/v2 revision rows.
Store the v3 integrity envelope using the existing strict workspace field framing
and domain `native-workspace-v3`: issuer, subject, v3 kind, object ID, workspace
revision and SHA-256 of the exact canonical v3 snapshot. The envelope is corruption
detection, not proof against a whole-workspace malicious rewrite or rollback.

Every mutation rechecks owner, expected revision, exact replay, shared quota,
reference compatibility and revision/command correspondence inside the existing
IMMEDIATE transaction. The session lease still authorizes its final commit. A
refused, cancelled or revoked operation cannot become a successful replay. Exact
successful replay returns the original historical result before compilation;
definition publication still requires the current maintainer role before replay.
History consistency never substitutes for current publication/plan eligibility.

The storage encoder distinguishes a source or canonical snapshot that exceeds its
byte budget as TOO_LARGE before calling the historical consistency decoder. The
existing historical `encode`/`decode` refusal remains UNAVAILABLE for malformed,
unsupported or oversized standalone history. Legal source bytes can expand when
escaped in a snapshot; test that storage size failure separately. This distinction
does not establish whole-process heap bounds for arbitrary caller-created DTOs.

## Internal store and draft service

`V3NativeStore` is a separate framework-free port with `replay(owner, command)`,
`append(owner, command, V3NativeRevision)`, `read(owner, objectId, optionalRevision,
profile)` and `list(owner, profile)`. It uses the existing closed `NativeCommand`
field records as neutral commands; the chosen v3 service/store supplies the version
and exclusively uses v3 framing. It never accepts a v2 revision or returns one.

The first `V3NativeWorkspace` operation is `saveDefinition(owner, SaveDefinition)`.
It checks exact store replay before compilation, compiles a fresh source only when
needed, creates the next immutable schema3/native-compiler-v3 draft and asks the
store to append atomically. Compiler rejection leaves storage unchanged. The existing WorkspaceRejection
response allows at most256 diagnostics: a larger Rejected list returns typed
TOO_LARGE without truncation or append. Incomplete diagnostic history has no such
count cap and remains subject to the snapshot budgets. The adapter uses the actual v3 byte compiler and retains its incomplete diagnostics;
this operation cannot create publication data or reinterpret a stored draft as
current readiness. V3 profile mutation/publication and HTTP admission require
their subsequent explicit integration; the first draft port adds no placeholder
success for those operations.

## Explicit offline administration

Keep `--initialize-workspace=/absolute/private/directory` creating schema2 and
`--upgrade-workspace=/absolute/private/directory` upgrading schema1 to2. Their
existing argument/refusal behavior remains intact. Add two separate sole arguments:

- `--initialize-workspace-v3=/absolute/private/directory` creates schema3 only in
  an absent store under the existing private path/file ownership protections.
- `--upgrade-workspace-v3=/absolute/private/directory` upgrades a fully valid
  schema2 store to3 in one checked transaction, after complete historical audit.

No command starts HTTP, identity or database-observation services. Extra/duplicate,
mixed-version, malformed or unsupported arguments refuse before touching storage.
Repeated initialization or upgrade, schema1 input to the v3 upgrade, corrupt or
unknown schemas refuse unchanged. Schema1 owners may explicitly run the existing
upgrade to2 before the new upgrade to3; there is no chained automatic upgrade.

Hosted startup recognizes only fully audited registered schema2 or3; recognizing
schema3 does not migrate schema2, compile old sources or enable v3 publication.
The v3 store refuses schema2 as UNAVAILABLE. All v1/v2 operations continue on
both registered versions. V3 HTTP commands require a separately specified route
contract before implementation; this storage extension adds no route by itself.

Stop the hosted instance and take an external backup of the complete private
workspace before upgrading. Interruption yields the complete old or new schema;
restart must audit it before serving. Older schema2-only binaries refuse schema3.
Rollback requires stopping the new instance and restoring its separately managed
pre-upgrade workspace backup together with the old binary; there is no downgrade
or lossless merge of revisions created after upgrade. Never copy private backups
into this repository, image or CI artifacts.

## Acceptance

Use independent invented records to demonstrate v1/v2 exact bytes and replay after
upgrade, typed v3 history, whole shared quotas, owner/kind collisions, concurrent
stale saves, pinned compatible/incompatible profiles, forged replay/envelope and
cross-version references, revoked commit, restart and failed upgrade rollback.
Run actual private SQLite and OCI administration controls. Missing publication,
hosted planner, native-client and release evidence remains a separate blocker.
