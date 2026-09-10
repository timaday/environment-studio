# Redundant PostgreSQL uniqueness — 10 September 2026

Status: implemented and author-checked; independent verification and integration
pending. Comparison base `2d185bd0a88ae83a5788340d8fde2f9b8c5753bb` preserves the
separate OBS-QA-001 correction. [Issue9](https://github.com/timaday/environment-studio/issues/9)
tracks this candidate and accepted OBS-QA-002. The lead is the only application
correction writer; Oracle metadata source is unchanged.

The remote reviewer independently observed a supported PostgreSQL18.6 table
inspect successfully, refuse after adding a redundant UNIQUE on its existing
primary key, and inspect successfully after removing only that extra constraint.
The qualified constraint count changed1→2→1; committed invented documents remained
unchanged and inspection backend sessions disappeared. That experiment tested
published baseline30446e2, not this correction. Its exact pinned image, JDBC42.7.13,
JDK21.0.10/WSL environment, bounded disposable container and generic evidence are
recorded in issue comment5619969714.

PostgresMetadata now interprets the closed count as existence of one or more
fully qualified guarantees. It accepts canonical positive PostgreSQL bigint counts,
refusing zero, malformed or overflowing metadata. The original PG_UNIQUE_KEY SQL
and its exact table, P/U, validated, nondeferrable, single declared key and supporting
index valid/ready/live predicates are unchanged. The [observation contract](../contracts/database-observation.md)
makes redundant qualifying evidence explicit. Complete source inventory, read-only
transaction, driver/TLS/destination and cleanup controls are unchanged.

## Actual author checks

RED on unchanged metadata: counts2 and3 fail Complete assertions; count1 passes.
Three server tests ran with two failures/no errors, plus4core/1parser controls.
Exact four-file source/manifest is external in
`es-postgres-redundant-unique-red1-source-20260910`; log
`es-postgres-redundant-unique-red1-20260910.log`. After the correction all8 focused
tests pass. Final expanded verify passes **72 tests:4core,1parser,67server;zero
failures/errors/skips**, including17 new JUnit cases and the preceding
OBS-QA-001 correction controls. This is the combined local correction branch, not root integration.

New controls cover1/2/3 and maximum bigint count, zero/negative/noncanonical/overflow
counts, missing/duplicate/null/denied count metadata, and actual adapter duplicate
or unexpected source-key refusal despite two qualifying guarantees. Positive cases
retain exact invented XML, one open/rollback/close and complete cleanup. Existing
v2/v3 independent observation fingerprints and cancellation/quarantine tests pass.
JDBC doubles prove Java interpretation and lifecycle; they do not prove predicate
behavior on real deferred/composite/wrong-column/invalid database objects.

Command: `mvn -B -ntp -f backend/pom.xml -pl server -am
-Dtest=HostedPlanServiceTest,FifthEditionClassifierTest,PostgresUniqueKeyTest,ObservationCancellationPublicationTest,*Observation*Test,ReadOperationPolicyTest,SqlReadTest verify`
(the test selector is shell-quoted). JDK21.0.12, Maven3.9.16; isolated worktree/output.
Log `es-postgres-redundant-unique-green2-20260910.log`, SHA256
`8bc956c454c2d709cb7b481e02b58c4f48459cc1217087cb26242e958e41b413`.

Three distinct manual guard faults compile and fail four targeted assertion runs:
restore exactly-one; admit zero/leading-zero; remove overflow rejection. The same
zero/leading-zero substitution is checked with two inputs and is counted once as
a fault. Exact substitutions, source hashes, compiler/JVM commands and assertion
logs are external in `es-postgres-redundant-unique-mutations-20260910/results.json`.
Production source/classes are unchanged by these external mutant builds.

Whole-diff provenance review, staged-content guard, repository integrity, whitespace
and11 Python checks pass.

## Remaining qualification

Remote verification must fetch the exact correction before rerunning its original
real-engine reproduction and adverse no-qualified-key cases. The older experiment
cannot qualify this branch. Independent fixed review, required combined Java/
frontend/OCI and applicable real-engine gates remain pending; no new container,
DB or native build was run locally. No hosted/compiler/export readiness changes.

The analogous Oracle comparison is source-only; this PostgreSQL reproduction does
not establish Oracle reachability. Oracle documents different same-column key
restrictions ([constraint reference](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/constraint.html)).
No Oracle defect, fix or exhaustive impossibility is claimed. All fixture facts
reuse independently invented catalog/source plumbing with original count/lifecycle
interventions. No private or transformed model enters the candidate.
