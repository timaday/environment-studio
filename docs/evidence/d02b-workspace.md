# D02b — private definition workspace

Candidate based on `2d62646cce6dbedb3fe389542083b16ef505f50b`, implemented in the
isolated `implementation/d02-definition-workspace` worktree. Parent-owned wire
clarifications were supplied during implementation: decoded command fields use
UTF-8 decimal byte-length framing (`length:bytes`), and native integer values in
saved/HTTP projections are decimal strings. No application/private model was
consulted. Every declaration, identity, token and database row in these tests was
independently invented. Runtime test databases are private temporary directories
outside the checkout; none is an artifact or fixture for publication.

## Behavior and authority

Framework-free `DraftWorkspace` uses a narrow `DraftStore` port. Server adapters
own strict UTF-8/JSON decoding, compilation and SQLite. Replay is resolved before
stale-revision checks and compilation; an IMMEDIATE transaction repeats replay
and expected-revision checks before catalog/revision/replay mutation. Saved
source bytes, compiler/schema versions, incomplete projection and diagnostics
are immutable. Reads/replays decode saved snapshots without running a compiler.
Owner issuer/subject are server-session authority, stored as separate bound SQL
columns. Foreign objects have the same 404 as missing objects. Native revision
values never authorize workspace revision changes.

GET/list/history and PUT are explicitly authorized in hosted security. PUT needs
Origin and CSRF, and the controller reads a bounded stream instead of allowing
Spring to bind/log the uploaded body. Responses carrying source/model content
have safe `toString` implementations. GET/list/history and refusals are no-store.
The inspector model retains native field names, lower-case enum values and
arbitrary-precision integer decimal strings; the original source stays exact.
Rejected compilation is 422 without persistence; size refusal is 413, conflicts
409, unavailable/capacity 503. Inspection/export remain disabled.

Absent workspace configuration leaves authentication usable and workspace calls
explicitly unavailable. A configured invalid hosted store refuses startup. Demo
reports `HOSTED_MODE_REQUIRED` and cannot open or mutate this hosted workspace.
The sole offline `--initialize-workspace=/absolute/directory` path executes
before Spring and never starts web/IdP services or overwrites existing entries.

## Storage bounds and failure handling

The initialized private directory is 0700; database/journal files are owned 0600,
regular, single-link files. Symlink components, unexpected files/sidecars,
checkout locations and unknown journal headers refuse use. Existing WAL mode is
refused rather than changed. Startup verifies exact schema/application version,
SQLite integrity/foreign keys, snapshot digests/versions, catalog identities and
contiguous revisions, replay command digests and owner counts. Snapshot lengths
are checked in SQL before blob allocation. There is no automatic migration,
store recreation, fallback or exception-text response.

Connections verify DELETE journal mode, synchronous EXTRA, foreign keys, MEMORY
temp storage, 4096-byte pages and bounded page count; cache spilling is disabled.
The database is conservatively capped at 30,720 pages (120 MiB). One complete
original-page rollback journal plus per-page/header allowance remains below the
256 MiB database+journal service budget. Before mutation, available space must
cover maximum remaining database growth plus the original-page journal and one
MiB framing allowance. This deliberately conservative SQLite cap can refuse
before the absolute 256 MiB aggregate limit. Per owner/object/record limits are
checked within the same transaction; no revisions or replay records are evicted.

## Actual test observations

All Maven commands used `/tmp/es-lead-toolchain/maven/bin/mvn` (Maven 3.9.16,
Java 21), `-B -ntp -f backend/pom.xml`. No shared install was performed. The sole
dependency addition is managed-purpose `org.xerial:sqlite-jdbc:3.53.4.0`.

| Run | Exact additional arguments | Observation |
| --- | --- | --- |
| Core RED | `-Dtest=DraftWorkspaceTest -Dsurefire.failIfNoSpecifiedTests=false test` | Two behavior errors from the initial unavailable service stub; replay could not bypass compilation and rejected input could not return without saving. `/tmp/es-d02b-core-red.log`. |
| Wrapper RED | `-Dtest=DraftWorkspaceTest,DraftRequestReaderTest -Dsurefire.failIfNoSpecifiedTests=false test` | Reader stub rejected the valid exact-Unicode request and did not distinguish bounded size refusal. `/tmp/es-d02b-wrapper-red.log`. |
| Store RED | `-Dtest=DraftWorkspaceTest,SqliteDraftStoreTest -Dsurefire.failIfNoSpecifiedTests=false test` | Missing-store refusal was absent and valid create/restart paths hit the unavailable stub: one failure, two errors. `/tmp/es-d02b-store-red.log`. |
| First GREEN | `-Dtest=DraftWorkspaceTest,DraftRequestReaderTest,SqliteDraftStoreTest -Dsurefire.failIfNoSpecifiedTests=false test` | 2 core + 6 adapter tests passed. `/tmp/es-d02b-store-green.log`. |
| Corruption RED | `-Dtest=DraftWorkspaceTest,SqliteDraftStoreTest -Dsurefire.failIfNoSpecifiedTests=false test` | Corrupted native catalog identity/replay digest was accepted at startup: one failure. Fixed with explicit consistency validation. `/tmp/es-d02b-adverse-red.log`. |
| Ordering RED | `-Dtest=DraftWorkspaceTest,SqliteDraftStoreTest -Dsurefire.failIfNoSpecifiedTests=false test` | Stale request with invalid source returned compilation rejection instead of conflict: one failure. Fixed pre-compilation revision validation. `/tmp/es-d02b-order-red.log`. |
| Journal RED | `-Dtest=DraftWorkspaceTest,SqliteDraftStoreTest -Dsurefire.failIfNoSpecifiedTests=false test` | SQLite silently ignored an independently invented unknown journal header: one failure. Fixed bounded header validation. `/tmp/es-d02b-journal-red.log`. |
| Final GREEN | `verify` | 28 core + 99 server tests, zero failures/errors/skips; packaged jar built. `/tmp/es-d02b-final-verify.log`. |

A preliminary adapter-only selector stopped at the core zero-test gate; it was
rerun including core tests, without weakening that gate. Intermediate HTTP work
exposed missing explicit path-variable names, then an invalid synthetic fixture
with an empty required-rule list. Both were corrected; neither is represented as
a passing run or a deliberately written production RED. HTTP coverage was added
after initial wiring; the principal TDD observations are the service, wrapper,
store, corruption, ordering and journal behavior tests above.

The final suite exercises duplicate/unknown wrapper keys, malformed UTF-8, lone
surrogates, exact Unicode, decoded byte limits, revision length limits and an
independent command-frame oracle; restart/history/replay after later revisions;
owner isolation; two concurrent writers with exactly one expected-revision
winner; 32-revision and 100-object quota refusals; unsafe permissions, symlinks,
orphan journals, unsupported WAL and malformed metadata.

A separate child JVM starts a real SQLite transaction, changes catalog/revision/
replay, forces a hot journal with test-only cache spilling, then halts with code
17 before commit. The adapter recovers all three original records and accepts a
subsequent revision. This is process-crash/SQLite rollback evidence, not a power
loss or production-filesystem durability claim; production disables spilling.

The existing real mock-provider authorization-code/PKCE flow now exercises
workspace PUT/GET through the full hosted filter chain, rejecting missing CSRF
and wrong Origin and preserving no-store. Captured DEBUG logs assert absence of
synthetic source, CSRF token prefixes, client secret, encoded Basic client
authentication, access tokens and whole/truncated ID tokens. Direct controller
units use controlled principals only and do not claim another protocol test.
No source/token log leak was observed in the new workspace flow.

`python3 scripts/check_repository.py`, `python3 scripts/check_repository_content.py`
and `git diff --check` passed locally. Content checking is limited known-pattern
proof, not a provenance verdict. No files were staged or committed by the writer.
A separate packaged `java -jar ... --initialize-workspace=...` subprocess created
the private database with no stdout/stderr or web/IdP output; its second invocation
failed with the safe `WORKSPACE_UNAVAILABLE` code.

## Limits and handoff

The lead owns independent fixed-candidate review, integration, v2 dispatch,
OpenAPI/schema changes, frontend/e2e, native-library OCI extraction, image gates
and final release evidence. Exact runtime-volume fsync/power-loss behavior,
one-replica deployment enforcement and freshness against restored stale volume
backups remain external qualification work. No production readiness, database
observation, managed-configuration access, publication or export is claimed.

Measured local implementation/test window: first dependency preparation at
16:39 BST through final verification/evidence at approximately 17:06 BST on
2026-09-08, about 27 minutes. Earlier contract reading was not separately timed.
No external blocked time was observed; approximately 5 minutes of this window
was local correction/recheck (binding, synthetic fixture, corruption/order/journal
adverse findings). Integration/reviewer rework has not yet occurred and is not
included. No parallel speed-up claim is made.

## Independent review corrections

The reviewer reproduced an owner-integrity gap by changing only a catalog owner
field: the first candidate reopened and let the reassigned owner read the saved
revision. The reviewer also identified eager directory enumeration via
`entries.toList()`, which could materialize an unbounded private-directory listing.
These are corrected in the four-file review increment.

Observed RED command:
`/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml -Dtest=DraftWorkspaceTest,SqliteDraftStoreTest -Dsurefire.failIfNoSpecifiedTests=false test`
(`/tmp/es-d02b-review-red.log`). Two adapter failures: owner corruption did not
refuse startup; directory validation reached a deliberately forbidden later
stream element instead of refusing the first unexpected path. Directory checking
was first extracted without changing its eager behavior to expose the traversal
boundary for the latter test; no huge filesystem or OOM event is claimed.

Snapshot integrity now frames issuer and subject alongside object ID, revision,
source/projection digests and versions. Startup, reads and replay validate this
binding against the catalog owner. Independent cases mutate issuer, subject and
all three tables' object IDs. A single changed ownership field cannot grant
historical authority. This is accidental-corruption detection, not cryptographic
protection against an actor who can rewrite the complete database and recompute
its hashes. Provisional snapshots with the prior digest encoding are refused;
there is no silent conversion or migration.

Directory validation now iterates lazily, refuses the first unexpected entry or
a third entry, and catches traversal `UncheckedIOException` as typed unavailable.
A controlled failing iterator verifies early termination; a separate generated
I/O failure verifies safe refusal without exposing its synthetic exception text.

GREEN command:
`/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml verify`
(`/tmp/es-d02b-review-green.log`): 28 core + 102 server tests, zero failures,
errors or skips. Repository integrity/content and diff whitespace checks were
rerun. Approximately 4 additional minutes of reviewer-driven correction/testing;
no external blocked time. The initial candidate timing above is unchanged.
