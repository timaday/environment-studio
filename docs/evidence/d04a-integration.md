# D04a observation integration — 8 September 2026

Both engine observation adapters are integrated behind an internal framework-free
port. Complete exact inventory, independently configured destination, checked
read-only account policy and confirmed cleanup are required for a Complete
result. No HTTP route, credential input UI or inspection/export flag is enabled.
Hosted plan/session wiring and external TLS/account qualification remain separate.

The final independently reviewed candidate has 28 files; manifest SHA-256 is
`0cf240719a85db0f5197a6312b65af90b3b7c28626386541b48e5503a4519cd1`.
The lead verified every source hash and copied 27 files exactly, then removed one
trailing space from a qualification-test line caught by the staged whitespace gate.
That formatting correction changes no behavior. The only POM
merge adds pgJDBC 42.7.13 and ojdbc17 23.26.3.0.0 to the current integrated POM.
Docker includes the invented observation fixtures for the build; an exact-family
Git attribute preserves the glyph fixture's eight CRLF sequences. All fixtures,
additional users/tables/routines and canaries were independently invented. Public
vendor catalog checks do not encode an actual application model.

Independent review found two material account/privacy gaps. PostgreSQL's original
table-only scan omitted sequences, MAINTAIN, large-object authority and delegation.
Thirty actual adverse grants were initially accepted; the correction qualified
40 direct/reachable-role/PUBLIC/ownership/delegation challenges and restoration.
The first logging check inspected only parent loggers; a verbose child reached
authentication. A second review reproduced a configured child that did not yet
exist. Both paths now refuse before credentials reach a driver. Pinned driver
loggers are created and strongly retained before checking effective configuration;
custom Oracle diagnostic routes refuse. Production does not alter logging levels
or global configuration. Privileged code changing logging during an operation is
outside this process isolation claim.

Both findings are closed. The reviewer verified final manifests and independently
passed all six isolated-JVM lazy-logger probes, including actual PostgreSQL driver
class initialization after refusal. Earlier independent review passed the 15
then-existing observation tests. The worker's full TDD, bytecode investigation,
26 Oracle account controls, database matrix and limitations are in
[D04a evidence](d04a-observation.md). Initial targeted RED commands disabled the
per-module specified-test selection check; their assertion failures remain actual
observations, not complete quality-gate passes. Correction and lead runs used the
complete reactor without test-skipping or gate-disabling flags.

Actual integrated checks:

- `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml -Dmaven.jar.forceCreation=true verify`:
  **250 tests passed** (79 core, 7 qualified parser, 164 server), no failures,
  errors or skips; finished at 18:30:04 UTC.
- `python3 scripts/db_observation_qualification.py --engine both`: **4,292
  assertions passed** on both designated disposable engines. Of these, 3,982
  check individual vendor-baseline entries; they are not separate behavior cases.
  The complete behavior matrix and actual controls are linked above.
- The integrated run accepted 128 documents exactly 16 MiB in both engines:
  Oracle 1001 ms and PostgreSQL 350 ms in this local run. These are capacity
  observations, not production latency claims. Over-limit cases refused.
- Actual cancellation, backend disappearance and targeted disconnect checks ran
  on both engines. Oracle active-statement cancellation encountered driver rollback
  error 17008 and retained INCONCLUSIVE with its original handle. Independent
  backend disappearance did not promote it. The separate production-query barrier
  cancellation confirmed COMPLETE cleanup. PostgreSQL lock-stall cancellation
  confirmed cleanup; targeted termination remained inconclusive on both engines.
- `docker build -t environment-studio:d04-review .` passed the integrated frontend,
  schema and Java gates. `bash scripts/container_smoke.sh environment-studio:d04-review`
  passed protected startup, static/health/capability denial and private workspace
  initialization, permissions and overwrite refusal.
- Repository integrity, the complete staged-content guard and 10 Python script
  tests passed before commit. Staged XML bytes were compared with the tested
  fixture bytes; no line-ending normalization entered the candidate.

The pinned engines are PostgreSQL 18.6/bookworm and Oracle Free 23.26.3.0.0 with
the image identities recorded in the observation contract/evidence. Oracle's
complete pristine PUBLIC object-grant baseline has 3,982 entries and digest
`ef5326b6f4a158d1962f5397e10a7b5b38bbf20a7ba005fea394c02710db9a33`.
Its required account-level READ ONLY mode is separate from the togglable session
setting. The earlier disposable blanket-revocation experiment is not qualified.
The replacement instance retained vendor grants throughout the qualified runs.

G01/G05/G07 and protected OCI evidence support these local observation mechanisms.
No SQL writer/client, arbitrary Oracle routine purity, actual application,
real-account TLS or HiveForge qualification is claimed. Observations remain
transient; incomplete cleanup never supplies plan/export authority. G09 remains
blocked by missing release capabilities/evidence. The image is local/unpublished;
automatic approval review still blocks the authorized branch push and new remote CI.

The first review correction took 8 minutes 3 seconds from recorded RED to its
complete database matrix. The later lazy-logger correction and lead integration
are separate intervals recorded in worker/lead logs; concurrent contract work
prevents an isolated active-time claim. No serial comparison or speed-up is claimed.
