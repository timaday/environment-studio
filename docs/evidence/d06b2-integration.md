# D06b2 hosted HTTP integration — 8 September 2026

The nine initial hosted plan routes are integrated: owned plan creation/status,
allowlisted destinations, one-shot inspection reservation/credentials/cancellation
and typed revision commands. The server enforces lease, replay and body admission
before credentials or expensive work. This is backend capability evidence;
inspection/composition views, the browser workflow and export remain incomplete.

The author froze 33 files in `/tmp/es-d06b2-frozen.sha256`, manifest SHA-256
`1fbbdf67bbd4fb9537e44f8c6f1baa94fabcf51c571778668318d0923439af88`.
The lead verified every hash, copied 32 files exactly and merged only the
plan-command schema resource into the current server POM. The integrated tree
retains the later package/transaction tests and Oracle FGA correction, absent
from the author's original base. Author evidence records 375 passing Java tests,
three detected/restored authority mutants and genuine mock OIDC/socket workflows;
see [the HTTP boundary evidence](d06b2-plan-http.md).

Independent review found no material issue in this fixed candidate. The reviewer
verified all hashes before/after and independently invoked 40 focused test methods
plus two actual-adapter XML scenarios: **42 passed**. These ran against the frozen
compiled classes; they are not represented as a fresh full Maven or socket-suite
run. The lead's subsequent container build runs the complete integrated suite.

## Actual integrated checks

- `docker build -t environment-studio:d06b2-review .`: **395 Java tests passed**
  (131 core, 7 qualified parser, 257 server), with no failures/errors/skips.
  Frontend checks, 7 component tests, 17 schema tests and frontend build passed.
- The first container attempt reached 395 tests but had two errors because the
  existing blanket PEM exclusion removed the invented public test certificate.
  The correction adds an exception for exactly
  `fixtures/plan-http-tls/mock-ca.pem` to Git/Docker ignores and copies the two
  HTTP fixture directories into the Java build. Its private key was generated
  only in author process memory and never saved; no other PEM/key exclusion was
  changed. This was an integration failure, not an intended TDD RED result.
- `bash scripts/container_smoke.sh environment-studio:d06b2-review`: protected
  startup/static/health/capability denial, private workspace permissions/overwrite
  refusal, schema-2 initialization and explicit offline upgrade/refusal passed.
- Repository integrity, staged-content inspection, 10 Python script tests and
  whitespace checks ran before commit. The pattern guard is limited and does
  not replace review of the independently invented provenance.
  The first staged-content check identified the missing fixture registration;
  the lead added its exact `provenance.json` manifest and reran the gate. The
  author's earlier content check inspected its unchanged Git index, so it did
  not establish eligibility of those then-untracked fixture files.

Build logs are `/tmp/es-d06b2-integration-container.log` (initial failure) and
`/tmp/es-d06b2-integration-final-container.log` (passing rerun); smoke output is
`/tmp/es-d06b2-integration-smoke.log`. The local runtime manifest is
`3ef6e813d05f13f954fcc1d59cf6d8cc9997df3eecac3ed96565921a1c67a74e`, with
image config `7945a03f560475468047d20c8ca4cebeb39e03e3909777478aa19df3db31cebf`.
The complete Java build took 87 seconds in this run. Integration included the
failed fixture-packaging attempt and correction; no serial comparison or parallel
speed-up is claimed.

## Remaining qualification

Maximum retained-plan, scratch and response heap remain unqualified; the author's
20,000-entity partitioned decoder test does not measure those allocations. Actual
configured TLS is being investigated on separate invented database instances;
it is not established by the public certificate parsing fixture. Real deployment,
accounts/IdP and private application qualification remain external. No UI flow,
guarded supervisor/commit acknowledgement or export authority is enabled here.
The local image is unpublished. Automatic approval review still blocks the
authorized branch push and therefore new remote PR/CI/image evidence.
