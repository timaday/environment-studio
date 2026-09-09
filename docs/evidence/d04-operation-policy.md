# D04 operation policy — 9 September 2026

The new implementation accepts ordinary write-capable accounts and qualifies the
read operation instead of requiring account purity. This is implementation/unit
proof, **not new-policy disposable database qualification or release evidence**.
The earlier account-policy/TLS reports remain historical.

## Candidate and boundary

Writer base: `8125e57127b1583664d368b97b82d08ec103fe5d`. The lead's reviewed
operation-policy contracts were inherited separately from checkpoint `9f17e47`;
they are not writer-authored deltas. The frozen 18-file source/test manifest is
`/home/tim/.tmp/es-d04-operation-policy-candidate.sha256`, SHA-256
`6da39dd37a7d869816bfd576051383e72a298c2e00dbd542e0053bc7072debf2`.
Independent reviewer accepted that fixed candidate with 14 focused cases passing.
Lead integration and its combined verification remain separate gates.

The implementation uses `operationPolicyVersion`, engine-specific
`postgresql-read-operation-v1` / `oracle-read-operation-v1`, adapter
`jdbc-observation-v2`, fingerprint domain `ES-OBSERVATION-2` and
`readOnlyOperation=verified`. It emits no `leastPrivilege` claim. Old configuration
keys/policies are refused. `ACCOUNT_NOT_READ_ONLY` remains deprecated only so the
historical qualification assertions compile; the runtime adapter does not emit it.
Historical provisioning main entrypoints now refuse immediately, before executing
any old qualification or provisioning body.

`ReadQuery` is a closed engine-specific metadata vocabulary. `SqlRead` accepts
that vocabulary and typed source operations, with a private closed control enum;
it has no externally callable SQL-string method. Setup is single-use and an
attempt to repeat it permanently refuses further reads. PostgreSQL server-mode
SHOW checks precede the table lock and run again before source transfer. Oracle
begins with `SET TRANSACTION READ ONLY`; catalogs and the LOB package are explicitly
SYS-qualified. Bound source identifiers remain validated and quoted. The existing
operation/cleanup deadlines, one connection, rollback, close and quarantine remain.

## Actual checks

Toolchain: Maven 3.9.16 at
`/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn`, Java 21.
All fixture facts and values in the tests were independently invented.

- RED: targeted configuration/fingerprint run executed seven server tests with
  one expected old-domain digest failure and two new-policy configuration errors.
  The new expected digest was independently calculated with Python framing.
  Log: `/home/tim/.tmp/es-d04-policy-red.log`. Two earlier invocations stopped at
  the reactor's hardcoded no-tests gate; those were not behavior RED evidence.
- GREEN: configuration, fingerprint, lifecycle, boundary and statement-cleanup
  selection passed 19 server tests; new operation tests initially passed five.
- Additional RED: setup replay was refused but later source reads remained possible.
  The new regression failed (seven tests, one failure), then setup replay was made
  terminal. Log: `/home/tim/.tmp/es-d04-policy-replay-red.log`.
- Three independent targeted changes were rejected by behavior tests: reintroducing
  PostgreSQL owner refusal, bypassing initial mode verification, and removing SYS
  qualification from Oracle LOB length access. Each mutated file was restored.
  Logs: `/home/tim/.tmp/es-d04-mutant-{owner,mode,oracle-package}.log`.
- The first full reactor run found three stale Oracle FGA test configurations.
  They now use the new policy and provide ordinary session-user identity while
  retaining the expected denial of later missing metadata. The affected ten-test
  server selection passed. No FGA assertion was downgraded.
- `python3 scripts/check_repository_content.py`: PASS, known-pattern scope only;
  this does not replace provenance review.

Final source-candidate reactor verification (`-B -ntp -f backend/pom.xml -pl server
-am verify`) passed 134 core tests and seven parser tests. Server executed 280
checks: 279 passed, with the sole failure in the unchanged
`HostedBoundaryTest.actualQuietTrickleDisconnectCancelAndMetadataCapacityStayBounded`
re-login assertion (expected 302, received 403 after expiry). The lead independently
diagnosed the test's premature assumption that required quarantine retry had already
completed; it owns that shared-test correction and final combined verification.
Log: `/home/tim/.tmp/es-d04-policy-final-verify.log`. A targeted retry had started
before that diagnosis arrived and was stopped; it supplies no result. The reactor
is therefore not claimed fully green or packaged successfully here.

The 18 source/test hashes were rechecked after this run and still match the frozen
manifest. The final candidate includes eight operation-policy tests; all eight
passed in the full run. No source mutation occurred after freezing.

## What is and is not proved

The independent JDBC double supplies ordinary owner metadata and refuses unexpected
queries. It verifies complete source fidelity, new evidence fields, one open and
rollback/close, early setup/mode/autocommit denial, late mode drift, missing access,
missing metadata, RLS/FGA barriers, SYS/common/vendor identity denial, malformed
identifiers and old policy rejection before connection allocation, typed statement
admission, and retirement of the old provisioning entrypoints. Existing lifecycle
checks exercise cancellation, deadlines and retained cleanup quarantines.

The double does not prove PostgreSQL/Oracle grant evaluation, real transaction
semantics, backend-session disappearance, native TLS, concurrent catalog behavior,
or owner-shadowed routine execution on either actual server. New-policy disposable
qualification must still show owner/direct/column/active-role write accounts,
Oracle redundant read paths and inactive-only denial, real READ WRITE controls,
ordinary DML refusal under inspection settings, independently unchanged committed
rows, side-effect barriers and credential/content canaries. It must not invoke
retired provisioning helpers or claim that Oracle read-only transactions prevent
arbitrary autonomous transactions or DDL effects.
