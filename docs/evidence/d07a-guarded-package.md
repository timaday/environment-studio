# D07a — bounded package codec and admission mechanism

This candidate implements mechanical package admission and deterministic container encoding. It does **not** generate a qualified transaction, enable export, establish private validation or publication authority, or launch a SQL client. `GuardedPackageInspector` returns `Unqualified` after successful metadata checks and always reports generation unavailable. Both guarded transaction templates and their actual engine/client qualifications remain required follow-up work.

Base: `9fbc8f14ce08132f439efc092b14515fecdf04e3`. Reviewed contract overlay: root `guarded-package-v1.md` at `7c17d44e81ebc8dab3ec9f8465c2602533a61c7a`. The lead explicitly approved this smaller mechanism slice after the full-size Oracle experiment below disproved a presumed bounded monolithic transport path. No accepted scope or export gate was weakened.

## Implemented boundary

- Closed JSON admission uses the canonical manifest/payload schemas, strict duplicate detection, Unicode/UTF-8 and integer token checks, node/depth/member limits, engine/storage/binding consistency, deterministic inventory and publication ordering, typed immutable records and safe string rendering. Existing qualified XML parsing validates every original and target. Int64 keys retain exact signed decimal semantics.
- Original and target scopes are independently limited to 16 MiB, 128 records and the existing per-XML limit. Complete record membership, distinct keys, policy coverage, changed-record counts and byte totals are derived from supplied content. This establishes internal consistency only; no database observation or physical destination is independently verified here.
- Canonical JSON and versioned native execution framing have independent Python byte/digest oracles. Input payload digest covers the exact admitted bytes; canonical re-encoding and re-admission deliberately obtain the digest of the new canonical bytes.
- The ZIP mechanism writes exactly four ordered STORE members with the contracted metadata and independently checked CRC/central-directory structure. It rejects truncation, trailing data, altered flags/headers, wrong names/order, duplicate entries, inconsistent offsets and resource limits. It never extracts files. Output failure returns a safe refusal; callers must discard their partial output stream.
- The inspector verifies closed manifest shape, member digests/lengths, fixed instructions, execution digest and derived counts. It does not interpret arbitrary SQL as qualified. Generic ZIP output and caller-constructible data records confer no execution authority.

No new dependency was added. The only existing-file edit adds the two canonical guarded schema resources to the server POM. All remaining source changes are in `server.export`; there are no routes, database ports, persistence, planner or authentication changes.

## Observed tests

Commands ran in the independent worktree using Maven 3.9.16 and Java 21. Targeted reactor runs included real `ArchitectureTest` and `FifthEditionClassifierTest` prerequisites; no no-test gate was disabled.

Behavior RED runs used minimal callable rejecting implementations:

| Boundary | Actual RED | Subsequent GREEN |
| --- | --- | --- |
| Admission | `/tmp/es-d07a-admission-red.log`: expected Accepted, received Rejected; one assertion failure, no errors | `/tmp/es-d07a-admission-green.log` |
| ZIP encoding | `/tmp/es-d07a-zip-red.log`: expected Written, received Rejected; one assertion failure, no errors | `/tmp/es-d07a-codec-green.log` |
| Inspection | `/tmp/es-d07a-inspector-red.log`: expected Unqualified, received Rejected; one assertion failure, no errors | `/tmp/es-d07a-inspector-green.log` |

A later new test initially used `Path(...)` instead of `Path.of(...)`; that compilation error was corrected and is **not** claimed as behavior RED (`/tmp/es-d07a-full-boundaries.log`).

Final command:

```text
/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml -DargLine=-Xmx768m verify
```

Final log: `/tmp/es-d07a-final-verify.log`. 317 tests passed: core 94, qualified parser 7, server 216; zero failures, errors or skips. This includes 15 new package tests. Adverse cases cover duplicate/unknown properties, noncanonical numbers, malformed Unicode/UTF-8/XML/hex, signed-int64 overflow and alternate spellings, duplicate/unsorted inventories, cross-engine mismatch, immutable arrays/collections, safe failures, metadata tampering, every truncation and each single-byte mutation of the independent 471-byte ZIP. Capacity cases accept complete 16 MiB original plus 16 MiB target XML scopes and stream exactly 160 MiB of ZIP member content; one byte above total/member limits refuses before output or allocation of absent member content. The tests do not establish a hosted concurrent memory budget or maximum-archive inspection peak memory.

Independent oracles:

```text
python3 fixtures/guarded-writer-v1/zip-oracle.py
python3 fixtures/guarded-writer-v1/canonical-oracle.py
```

The ZIP oracle uses Python `struct`/`zlib`, not the production encoder. The canonical oracle uses independent Python JSON/native framing over the existing independently invented public mock family. Exact expected ZIP SHA-256: `e30d13d293f35d0eece0e44199e92c50b60a683617118f553f3f9596b61bdf48`. Canonical payload digest: `2cf1e83d865f960b07829c30ed9a7e076372181a0de6391db0ce15f4c2f0d98f`; canonical program digest: `c6793114d3daf67953343793885a260c86e8d1eb946550d7dda52b683d25453c`.

Six targeted guard mutants were killed by assertion failures, with no test errors: int64 range validation, sort order, strict UTF-8 decoding, ZIP CRC verification, program digest verification and generation-unavailable status. They ran in `/tmp/es-d07a-mutant-copy`; exact selectors and outcomes are in `/tmp/es-d07a-mutants.json`, individual logs `/tmp/es-d07a-mutant-*.log`. Production source bytes were restored after each mutant; no mutation is in this candidate.

Repository structure/content checks passed; the content check is limited pattern evidence, not proof of eligibility. Python script tests: 10 passed. Existing Node schema tests: 14 passed (`/tmp/es-d07a-schema.log`). No frontend behavior changed and no image/runtime gate is claimed here.

## Oracle transport experiment and unresolved template work

Only the designated disposable replacement Oracle mock was used. No users, objects, rows, grants or global settings were created or modified. The no-DML SQL*Plus experiment submitted anonymous blocks assigning independent hexadecimal literals to a local RAW variable, then explicitly rolled back. It tested prospective source-buffer/compiler behavior, not LOB conversion, DML or guarded execution. Oracle's published SQL*Plus limits motivated measurement: [SQL*Plus limits](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqpug/SQL-Plus-limits.html).

| Assignment lines | Submitted source bytes | Actual outcome |
| --- | --- | --- |
| 600 | 1,241,489 | Success marker, 3.073 seconds |
| 4,096 | 8,474,713 | Success marker, 19.859 seconds |
| 32,768 | 67,797,081 | Timed out at 120.116 seconds; **not qualified** |

Results: `/tmp/es-d07a-oracle-buffer-results.json`. Full anonymous block SHA-256: `79e8e7bd2f372245b0a324b55a1e2582d32afd99dc95e77160a15abdba420693`. Only safe counts/outcomes are recorded here; no SQL body or credentials belong in evidence.

The client timeout initially left the unique matching backend session active. Exact owned-session kill/disconnect attempts reported marked-for-kill; cleanup was initially **inconclusive**. The lead subsequently independently observed zero matching sessions, zero matching V$PROCESS rows for the recorded process, and the process absent, without an OS signal. This later cleanup confirmation does not turn the original timed-out probe into a pass. No further Oracle probe or signal was sent after the lead assumed cleanup ownership.

Remaining work includes a contract-backed bounded loader/transport strategy, both deterministic transaction templates, physical identity/settings checks, complete locked membership and all-original verification, unchanged-record guards, row counts, complete final-state comparisons, full table write-effect eligibility, Oracle full LOB conversion/round-trip/cleanup, marker discipline, native-client transport/TLS/rollback/commit-acknowledgement qualification and binding to private validation/export authority. No PostgreSQL template execution or actual SQL guard qualification is claimed by this candidate. The lead is separately investigating aggregate-LOB transport feasibility.

## Provenance and review boundary

All new fixtures are independently invented or independently calculated from the already public invented fixture family; `fixtures/guarded-writer-v1/provenance.json` registers each artifact. No actual configuration, database model, credentials or private source is present. The SQL text in the inspector test is explicitly unqualified mock shape text, never a transaction template.

The frozen manifest/patch and exact scope audit are produced outside the checkout for lead integration and independent review. Elapsed time is recorded from the first observed RED through freeze, with experiment/replanning and correction time separated in the handoff; no parallel speed-up is claimed. Release/export readiness remains blocked.
