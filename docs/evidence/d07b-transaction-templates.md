# D07b — deterministic candidate transaction templates

This slice returns deterministic **unqualified candidate SQL**, not an enabled export or executable authority. `TransactionTemplates.generate` accepts only the existing strict `PackageAdmission.Accepted` boundary and returns immutable bytes, digest and known block boundaries, or a safe refusal. `Candidate.qualified()` remains false. The D07a inspector and generation-unavailable behavior are byte-for-byte unchanged. No database port, client launcher, HTTP route, persistence or dependency was added to production.

Worktree base: `4ccc4f5333f4e42eaa575e178387bf58f02b1438`, with the lead's exact D07a 18-file overlay (manifest `e71e059df6a0b888192f217ea766dfd0626ebcc73d4fb3b11a15e54bfc7ccb2d`) and reviewed guarded-package/oracle-transport contracts. The lead subsequently froze the explicit three-catalog exclusions in `0df7e694466b873afcc5e55d059d3d9574d7fa3a`; this implementation matches that additive contract. Only the assigned new export classes/tests, invented fixtures and this evidence were changed.

## Mechanism

PostgreSQL emits one DO block. It stores each original and target bytea literal once, keeping the full 16 MiB + 16 MiB scope within the 80 MiB SQL-member limit. It locks the complete table, checks physical destination/version/settings and positive catalog eligibility, verifies complete membership and every original, updates only byte-unequal records with exactly-one-row assertions, then checks complete target membership/content. The transaction-local program marker is assigned last. Errors abort with a fixed safe error; there is no transaction-control statement in the artifact.

Oracle emits initialization, bounded canonical-base64 loaders and one final guard. At most two 16 KiB chunks occur per loader, with at most 2048 base64 characters per literal and 2499 ASCII characters per source line. Original and target aggregate hashes/lengths and document byte offsets are derived independently from admitted payload bytes. Each document is copied whole into an owned bounded temporary BLOB, converted to an AL32UTF8 CLOB with explicit offset/warning checks, then completely reverse-converted and compared before use. It releases document LOBs between operations. Complete table/destination/original/row-count/post-state guards run under the exclusive table lock. Every successful owned temporary-LOB cleanup precedes readiness; exception paths clear readiness, attempt all owned cleanup and rethrow a fixed safe error. SYS package qualification prevents execution-user package shadowing.

The trusted generator supplies exact Oracle block spans alongside their identical concatenated artifact bytes. Each block ends `END;` plus LF, with one further LF between blocks. There are no slash/client-command lines. This class does not parse untrusted SQL to recover boundaries.

Catalog predicates are explicit candidate allowlists. They cover all stored columns and all indexes/constraints, not just the selected XML/key columns. Ordinary extra stored columns are permitted only through those predicates. PostgreSQL checks heap/permanence/partition/inheritance/RLS/policies/triggers/rules, builtin column types, immediate validated enforced constraints, plain builtin btree operator classes and qualified collations; default C-locale labels also require libc provider. Oracle checks ordinary heap/storage, all triggers/VPD/redaction, stored builtin types and binary column/session comparisons, constraints and indexes. It additionally refuses fine-grained audit policies, materialized-view logs and Flashback Data Archive enrollment. Direct catalog access failures refuse; no permission failure becomes a zero count.

Those final Oracle routes were found during a pre-freeze semantic audit. Oracle documents FGA handlers in [audit policies](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_AUDIT_POLICIES.html), write logs in [materialized-view logs](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_MVIEW_LOGS.html), and enrolled tables in [Flashback Archive tables](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/DBA_FLASHBACK_ARCHIVE_TABLES.html). Only absence and metadata-access refusal were exercised for these three additions; their feature configurations are not advertised as qualified.

## Observed RED and local checks

- `/tmp/es-d07b-red.log`: callable rejecting stub caused the PostgreSQL candidate assertion to fail; one assertion failure, no errors.
- `/tmp/es-d07b-pg-green.log`: initial PostgreSQL artifact behavior passed.
- `/tmp/es-d07b-oracle-red.log`: callable Oracle rejection caused its candidate assertion to fail; one assertion failure, no errors.
- `/tmp/es-d07b-initial-green.log`: both engine artifact examples passed.
- `/tmp/es-d07b-effect-catalog-red.log`: two artifact assertions failed because required effect-catalog/provider guard fragments were absent, with no test errors. The final implementation adds those guards.

Final command:

```text
/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml -DargLine=-Xmx768m verify
```

`/tmp/es-d07b-final-verify.log`: **322 tests passed** (core 94, qualified parser 7, server 221), zero failures/errors/skips. Five new unit tests cover independent SQL fragments, deterministic/no-op output, exact immutable block spans, long identifiers and supplementary-character keys under Oracle line bounds, int64 minimum spelling, existing admission refusals, and complete PostgreSQL source/target scope within the SQL budget. Targeted reactors included real architecture/classifier tests; no no-test gate was disabled.

Repository integrity/content checks passed (content check is known-pattern evidence only); Python script tests: 10 passed. Existing Node schema suite: 14 passed, `/tmp/es-d07b-schema.log`. No frontend or OCI gate is claimed by this slice.

Some scratch/test construction errors were corrected and are not behavior RED evidence: an initial independent expected table label, expecting admission to accept a denied policy that its schema already rejects, a missing Python `re` import in lock-result recording, and early SQL*Plus bootstrap/witness spelling. These did not justify loosening production guards.

## Actual disposable engine checks

Only assigned disposable containers and newly invented owned namespaces/accounts were used. PostgreSQL runs used psql 18.6 against PostgreSQL 18.6; candidate SQL executed under the new restricted NOLOGIN execution role through SET ROLE. Oracle runs used SQL*Plus 23.26.3.0.0 against Oracle Free 23.26.3.0.0, with a new non-SYS owner/execution account, 256 MiB quota and explicit required catalog/package grants. Its automatic PUBLIC INHERIT privilege was immediately revoked; the account was locked after checks. No old D04 table, account, data, PUBLIC vendor grant or global setting was changed.

Test-only Python runners were saved outside the checkout before execution. They never form a production connection path. The Oracle runner phases non-silent `/nolog`, quoted connect descriptor, the observed password prompt and authenticated SQL prompt; password canary absence is checked before persisting any diagnostic capture. Credentials are generated and retained only in process memory. These local plaintext mock runs do **not** qualify TLS, the final supervisor prompt/state machine or commit acknowledgement.

The independent one-to-two family changes a palettes XML document and one glyph reference while retaining the other glyph and every row's extra `note` value. `fixtures/guarded-transaction-v1/expected-state.json` records the independently invented expected original/target family, and `expected-sql-fragments.json` records independent source fragments. Complete small-document UTF-8 bytes were compared through native-client witnesses; the large cases use independently computed complete per-document hashes and lengths.

Actual intact-template refusal cases:

| Both engines | Additional Oracle cases |
| --- | --- |
| Wrong physical destination; extra/missing rows; stale unchanged dependency | Missing direct metadata privilege |
| NULL and empty original; injected row-count and post-state fault | Corrupted target aggregate transport |
| CHECK constraint; trigger; expression index; generated/virtual column | Injected reverse-conversion warning |
| Exclusive lock contention and bounded lock refusal | Injected cleanup failure |

Every guarded attempt used explicit rollback and independent post-attempt state checks; no guarded candidate was committed. Oracle sessions were uniquely tagged with SID/serial/SPID identified before workload submission. Each recorded writer session/process disappeared after its attempt. Faults never produced the expected readiness marker. Setup DML/commits were confined to owned mock objects and are distinct from generated candidate execution.

Lock refusal: PostgreSQL 30.067 seconds, Oracle 30.421 seconds. The initial Oracle holder-cleanup assertion did not establish completion; a subsequent explicit session/process/proc witness confirmed absence, recorded separately. No OS signal was sent. PostgreSQL's first lock-result recording had a Python import error; the corrected saved runner reran the actual lock case successfully.

Full-size qualification inputs contain 16 documents, each exactly 1,048,576 UTF-8 bytes, with supplementary Unicode characters crossing transport chunk boundaries. Both original and target independently total 16,777,216 bytes; all documents change. Final candidate guarded elapsed times: PostgreSQL **1.970 seconds**, Oracle **15.767 seconds**, with complete expected-target and restored-original witnesses. This measures the tested content/engines, not every possible input or a hosted concurrent-memory budget. Oracle's full guard includes loading, all document conversions/comparisons, DML, checked cleanup and readiness within the unchanged 120-second transaction deadline.

Scratch evidence and executed runners:

- PostgreSQL baseline/faults: `/tmp/es-d07b-pg-qualification-v4.py`; `/tmp/es-d07b-pg-5d9d255c/`.
- PostgreSQL additional faults/mutants: `/tmp/es-d07b-pg-adverse-v2.py`; `adverse-and-mutants.json` in that directory.
- PostgreSQL full scope: `/tmp/es-d07b-pg-full.py`; `/tmp/es-d07b-pg-full/full-result.json`.
- PostgreSQL lock: `/tmp/es-d07b-pg-lock-v2.py`; `lock-result.json` in the small-case directory.
- Oracle baseline/faults: `/tmp/es-d07b-oracle-qualification-v6.py`; `/tmp/es-d07b-oracle-537d3fd6/`.
- Oracle additional faults/mutants: `/tmp/es-d07b-oracle-adverse.py`, `/tmp/es-d07b-oracle-lob-faults.py`; respective `adverse-and-mutants.json` and `lob-faults-and-mutants.json`.
- Oracle final additional catalogs: `/tmp/es-d07b-effect-catalog-smoke.py`; `effect-catalog-results.json`.
- Oracle full scope: `/tmp/es-d07b-oracle-full-v3.py`; `/tmp/es-d07b-oracle-full/results.json`.
- Oracle lock/cleanup: `/tmp/es-d07b-oracle-lock.py`, `/tmp/es-d07b-oracle-lock-cleanup.py`; `lock-result.json` in the small-case directory.

The first large Oracle setup run lost useful late diagnostics behind SQL*Plus prompt output; the harness disabled prompt/line-number noise. A subsequent independent SQL witness incorrectly referenced a PL/SQL package constant in SQL; its numeric SHA-256 selector was corrected. These are setup/witness errors, not successful candidate qualifications. The later complete runs include explicit successful target/rollback witnesses.

## Targeted mutation and remaining authority

Fifteen deliberately weakened generated-SQL copies exposed missing guards: six each for PostgreSQL/Oracle (original, complete membership, physical destination, row count, post-state and trigger eligibility), plus Oracle aggregate hash, reverse-conversion warning and cleanup-refusal guards. Intact versions refused the associated adverse state; removed guards permitted readiness. The corrupted-target aggregate mutant also failed the independent expected-target witness. Each mutation ran only against owned disposable objects and was rolled back; production Java and frozen candidates contain no mutations.

These are actual SQL guard mutations, not a claimed Java mutation-tool run. The final generated candidates were rerun through the matrices after the last catalog changes; safe logs are `/tmp/es-d07b-final-pg-adverse.log`, `/tmp/es-d07b-final-oracle-adverse.log` and `/tmp/es-d07b-final-oracle-lob-faults.log`. `/tmp/es-d07b-mutation-oracle-check.py` applies independent refusal assertions to recorded weakened-SQL observations; the observed failing assertion log explicitly records the semantic oracle violations. No missing-guard survivor is reported as a success.

Still unqualified: the complete external supervisor/TLS/credential/framing/startup/EOF/death matrix, commit/acknowledgement authority, private D06b validation/export binding, all permitted scalar/index/collation variants, untested storage/effect configurations and hosted concurrent resource admission. Ordinary extra text columns and the tested plain primary-key variants have actual evidence; every enumerated allowed variant does not thereby acquire qualification. Missing required qualification continues to block export. No user-visible capability or publication status was enabled.

Provenance: all fixture data is independently invented; its manifest registers both expected-output files. Public engine metadata names are generic adapter mechanisms. No actual application model, credentials or private source enters this candidate. The lead owns integration, independent review, any shared contract refinements and image gates. Elapsed/rework and exact frozen hashes are recorded in the handoff; no parallel speed-up is claimed.

## Review correction: PostgreSQL signed int64 literal

Independent review reproduced PostgreSQL casting the unsigned magnitude before unary minus in `-9223372036854775808::bigint`. All int64 keys now use a quoted canonical decimal cast, including negative and positive values. The prior spelling-only expectation was incorrect and has been replaced.

Observed RED: `/tmp/es-d07b-int64-red.log` records the corrected minimum-key assertion failing against the original generator (one failure, no errors). `/tmp/es-d07b-int64-db-red.log` records actual generated minimum and combined boundary transactions refusing with `ES_GUARDED_TRANSACTION_FAILED`, while maximum and -17 pass. The first combined rollback witness also ordered a text output alias instead of the numeric table column; its false result was a witness construction error, not evidence of changed state. The witness now explicitly orders the numeric table column.

Observed GREEN: the saved executed scratch runner `/tmp/es-d07b-pg-int64-boundaries.py` checks minimum, maximum, -17 and all three together against a new independently invented table in the writer-owned disposable PostgreSQL schema. `/tmp/es-d07b-int64-db-green.log` records all four passing readiness, exact expected UTF-8 target bytes with unchanged extra-column values, and independent original-state readback after rollback (0.151, 0.149, 0.141 and 0.159 seconds). No guarded transaction was committed.

Full pinned Maven reactor `verify` passed again: **322 tests**, zero failures/errors/skips, 20.217 seconds (`/tmp/es-d07b-int64-green-verify.log`). This focused review rework changes only the PostgreSQL literal renderer, its assertion, and this evidence. It does not change qualification or export authority.
