# Schema3 persistence and v3 definition drafts

Reviewed internal implementation, 9 September 2026. This joins the reviewed
[v3 history](qf34-history-v3.md) to explicit private SQLite storage and the actual
v3 definition compiler. It adds no v3 publication, profile mutation, HTTP route or
plan/export admission. The [storage contract](../contracts/workspace-storage-v3.md)
keeps all v1/v2 source, snapshot and replay meanings unchanged.

## Candidates and actual checks

The lead's six storage files were frozen against `7b89e3c` plus the exact corrected
history overlay `d4d124bc17409d2ab544bc631a4fef09486744d5be87796a1ad836adcedec328`.
Their candidate manifest SHA-256 is
`5cc0ac37d0fec31ca137ddf660a48df8e0f1080155cdc85eda59eba5764f5a7e`.
The agent's four draft-service/compiler files use the same dependencies, with
manifest SHA-256
`8dda5fd963a37c725dd66d793542c95cfadd02fc653ee05448d1179ffe5607ad`.
The lead reviewed the complete draft implementation and tests, then assembled
both sets on `85a5ee616500b1b9a256e2aa9a716a1509a97650`. Later native/history fixes
are retained. Builds and all temporary mock stores stayed in owned external
archives; no private configuration or credential material was used.

Storage RED: two assertions against unavailable schema3 initialization/upgrade.
Expanded adverse RED: unsupported new CLI and a legal 1 MiB source whose JSON
snapshot escaped beyond 2 MiB exposed UNAVAILABLE instead of TOO_LARGE. Corrected
storage encoding distinguishes that size failure before historical decoding;
standalone historical encode/decode retain their existing UNAVAILABLE contract.
The first expanded run's test compilation error accessed PutResult as Saved; that
setup error was corrected, not counted as behavior RED.

Storage GREEN: 58 tests (4 core, 1 parser, 53 server), zero failures/errors/skips,
using actual private SQLite. Thirteen new families cover exact restart/replay,
v2 snapshot preservation, v1 upgrade replay, owner/kind isolation across all
versions, two concurrent stale saves, revoked commit rollback, malformed history
and kind overlap, source expansion, CLI refusal, 100 shared objects, 32 immutable
revisions, and compatible/incompatible pinned profile history. The profile case
constructs an artificial prior publication directly in the internal store; it
does not establish current publication authority. Commit refusal is injected
through the real store's commit port; this alone does not qualify HTTP lease use.

Draft service RED: two core assertions and one separate actual-compiler assertion
against unavailable scaffolds. Final author GREEN: 32 tests (6 core, 1 parser,
25 server), zero failures/errors/skips. A large-Incomplete test initially omitted
required fixture fields and correctly failed its setup; the corrected invented
fixture proves preservation of more than 256 Incomplete diagnostics. More than
256 Rejected diagnostics return typed TOO_LARGE without truncation or append.

Five isolated draft mutants each produced one assertion failure and zero errors:
compile before replay, trim before source hashing, omit revision increment,
discard Incomplete diagnostics, and omit the typed rejected-response budget.
The last proves refusal classification, not an acceptance bypass. Restored 32
passed with all four hashes unchanged. Author evidence SHA-256:
`42337f4fc83fa82dfeec58a0b792666ef8c66104589ab493e6c062b4d4cfcf01`.

The non-author lead's independent draft integration ran 47 tests (6 core, 1
parser, 40 server), zero failures/errors/skips. Two added tests join the actual
compiler, application service, historical codec and private SQLite: exact YAML
source survives a later JSON revision and restart; successful replay does not
invoke an unavailable compiler; changed request content and a foreign owner
refuse; real compiler rejection and revoked commit leave no draft or replay;
a subsequent valid retry starts at revision1. These extend the author's narrow
fake-store sequencing controls. No draft-service finding remained.

Commands used JDK21.0.12 and Maven3.9.16 in separate owned archives:

```text
mvn -B -ntp -f backend/pom.xml -pl server -am -Dtest=V3NativeHistoryTest,MinimalRuntimeTest,V3NativeSqliteStoreTest,V3NativeSnapshotCodecTest,NativeWorkspaceTest,SqliteDraftStoreTest,NativeUpgradeTest,WorkspaceControllerTest test
mvn -B -ntp -f backend/pom.xml -pl server -am -Dtest=V3NativeWorkspaceTest,MinimalRuntimeTest,V3NativeWorkspaceCompilerTest,IndependentV3DraftTest,V3NativeSqliteStoreTest,V3NativeSnapshotCodecTest,NativeWorkspaceTest test
```

The first full combined Maven verify passed 970 tests (261 core, 7 qualified
parser, 519 server, 183 supervisor), zero failures/errors/skips, in2m28s at
19:00:25 BST. The base85a5ee6 plus14-file overlay manifest is
`1dc28400f3ac0c36f25b707812b61b8d7de6999e8b2d9b5f1095f459b998853f`; all
14 hashes remained exact after verification. Supervisor assembly checksums and
hostile-environment launcher checks passed too. This run precedes the separate
reviewer storage tests.

Independent storage review found no material blocker. Its restored62 tests
(4 core, 1 parser, 57 server) passed with unchanged source6/ops3 hashes. Three
added actual SQLite controls reach a blocked COMMIT after rollback-journal
creation, preserve exact old schema2 bytes/replay, and then succeed after the
reader releases its lock; refuse replay corruption through an already-open
store on read/list/replay; and reject source/format inconsistent with the actual
command before append. This is a real post-write commit failure, not a power-loss
experiment or process kill at every DDL instruction. Removing per-operation v3
audit made a corrupt-history read succeed and failed one assertion, zero errors.
The source was restored and62 passed again.

Reviewer evidence SHA-256:
`5cec52ce1e71378e994cd30796bd0024ddd5d759c4323ada0c80fab9558f5a7f`.
Added storage test SHA-256:
`87f731fc6a9790e0f36e2880ba589ae68649b3e255a05d7378bcc308d045e159`.
Lead independent draft test SHA-256:
`030ede940d552c0726ce6273eb1fe6da7b206aee454a54a47bb8440509450b90`.
Ops contract/deployment/smoke manifest:
`d1fbe155a9e85802be5612e29d91f9bfa0a4076c5840c0e90c38b3cf3f9c23e0`.
The lead inspected the new reviewer test and evidence before integration.
Final combined focused verification passed75 (10 core, 1 parser, 64 server),
zero failures/errors/skips, including both independent test files. Repository
integrity, whole-diff invented provenance review, Python11 and whitespace checks
passed. Protected OCI administration remains pending at this checkpoint.

## Scope and risk

Business: v3 definition drafts retain the operator's exact source and complete
incompleteness. Ordinary write-capable managed database accounts and their closed
read-operation policy are untouched. Engineering: explicit versioned ports and
tables preserve historical compatibility; the draft application remains free of
framework dependencies. QA: concurrency, recovery, source expansion, immutable
replay, kind/owner boundaries and prior-history controls use independently
invented material. No test creates a current ready result by removing a compiler
gate.

Offline schema3 upgrade requires a stopped service and an external complete
workspace backup. Older schema2-only binaries refuse3; rollback needs the old
binary and matching pre-upgrade backup. Local SQLite and OCI checks do not qualify
HiveForge durability or an operator's backup process. Arbitrary DTO allocation,
all combined workload maxima, v3 hosted orchestration, native client/readback,
current-revision CI and publication/deployment remain separate work.
