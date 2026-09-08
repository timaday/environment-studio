# D06b3 hosted plan views — local development evidence

Candidate: prepared `01e6d14` plus the frozen D06b2 overlay. The lead owns integration, protected-image qualification and release authority. This slice adds eleven authenticated POST view routes; inspection/export capability flags remain false. No database credentials, source documents or plans are persisted by these routes.

## Contract and provenance

The seven-file contract candidate `/tmp/es-d06b3-contract-frozen.sha256` has manifest SHA256 `ef54751689f09bf696080c431118d64b91e734fe9cc0c14e71d7a3d7fcc9c2fe` and was independently reviewed before implementation. The lead integrated the schema/nullable-conflict contract and subsequent fixture registration at `c074cfa`. `openapi-plans-v1.json` remains the route source of truth; the aggregate references it.

All examples derive from independently invented repository mock fixtures or new synthetic test values. `fixtures/plan-views-v1/provenance.json` registers both shapes and provenance prose. Shape examples alone do not qualify executable profiles. Actual socket capture tests use the real portable profile adapter, then explicitly save/publish through the existing workspace routes. Capture itself leaves the profile catalog unchanged. The mock observation's 64-character fingerprint is a format-correct synthetic identity, not actual database evidence.

## Actual RED and GREEN observations

Commands use `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml`; focused core runs use `backend/core/pom.xml`. Logs are external local development evidence, not repository artifacts.

- View admission: `-Dtest=PlanViewAdmissionTest test` initially failed with two CAPACITY errors from the missing admission implementation (`/tmp/es-d06b3-admission-red.log`); implemented admission passed. A separate actual regression found failed reinspection could pass the final response check without changing revision/generation (`latch-red.log`, one failure); pinning inspection state and operation identity closed it.
- Reader: the first attempt had a Java record-constructor visibility compilation error, which is not behavior RED. The corrected behavior run rejected a valid page as MALFORMED_BODY (`reader-behavior-red.log`). The strict streaming implementation passed; an additional observed uppercase-mode acceptance failure was fixed with exact discriminants.
- Projection: three stub-result assertions failed (`projections-red.log`), then complete document/entity/placement projections passed. The full `projections-green.log` run passed 404 tests (134 core, 7 parser, 263 server).
- Encoder: two stub behavior failures (`encoding-red.log`) preceded bounded UTF-8 chunk encoding. Tests verify exact mixed-case/supplementary text, actual byte counting, refusal before transfer and interruption between chunks. Generated response overflow originally surfaced the request-body exception; `response-limit-red.log` observed the wrong exception type, then `response-limit-green.log` passed with RESOURCE_LIMIT (422).
- HTTP: the actual pre-inspection view initially returned 403 instead of the required domain refusal (`routes-red.log`); exact route authorization and controllers closed it. `eleven-routes.log` passed real mock-provider OIDC/PKCE/nonce login and all eleven routes. `wire-and-cleanup.log` subsequently passed every actual successful view response against the packaged closed schemas.

Targeted reactor commands used `'-Dtest=HostedBoundaryTest,PlanView*,MinimalRuntimeTest' test` or `'-Dtest=PlanView*,MinimalRuntimeTest' test`, including matching tests in every module. No test-gate disabling flags were used. Targeted passes are not represented as full reactor gates.

## Behavioral coverage and investigated failures

Actual socket flows cover complete current/target inventories, original parent indices, Existing opaque handles and Fresh provenance, one-to-two XML target output, raw/placeholders/formatted disclosure, stale revision, foreign-owner refusal before body, POST/CSRF enforcement, profile capture without automatic save, explicit owned profile publication, whole/partial previews with complete page totals, draft masking, containment, materialization and ten validation checks with export unavailable. Failed inspection preserves display evidence but invalidates response authority. Validation does not create missing D07 authority.

A test-only snapshot of the current admission synchronizes the quiet-reader test; it does not authorize production work. The actual socket then proves scratch contention (429), a quiet collection body's 30-second deadline and logout interruption (401). An initial probe raced admission and correctly caused 429; this was a test synchronization defect, not a production refusal bug.

Cleanup revokes authentication first. If the reader was still returning during logout, cleanup remains INCONCLUSIVE and the owner is quarantined. The test observes callback403 for that owner, waits for actual worker return, invokes the existing internal cleanup retry, requires COMPLETE, then proves a fresh login cannot read the old plan. There is no HTTP cleanup retry or automatic downgrade/restoration; operational retry/restart qualification remains remaining local work.

The first masking fixture set a required field unreadable and was correctly refused at definition publication (FIELD_UNREADABLE). The admitted fixture instead uses a readable SECRET field: entity/draft views mask it, unrelated edits preserve its exact internal value, placeholders hide it, and explicitly consented raw/formatted views may disclose it. DEBUG capture checks ensure it and transient DB/OIDC/code/PKCE/CSRF canaries do not appear in captured logs. No canary values are included in evidence output.

The actual capture-limit test projects 1,000 and 6,000 independently invented palette entities with explicit neutral labels. The smaller capture succeeds below 1 MiB; the larger valid projection receives PROFILE_REFUSED from the portable adapter, with no partial source or save. This server-generated result refusal is 422, not the request transport's 413.

A controlled ProfileComposer result containing 256 conflicts is refused rather than presenting its capped list as a complete total. A 255-conflict result has complete pagination. This tests cap handling, not a claim that the runtime fixture generated 256 distinct conflicts. Unknown conflict codes refuse; ENTITY_COUNT uses ruleId and absent coordinates are null.

## Qualification limits

The single response/materialization scratch is retained through actual encoder/write/cleanup, and byte output is capped at 128 MiB. These counters and small-shape tests do not establish maximum-shape Java heap usage; the lead's independent heap qualification remains required local work, not an external blocker. Database policy and both-engine matrix evidence belong to the previously qualified unchanged adapters. This slice did not rerun those matrices or the protected Docker image.

Full final checks, mutation outcomes, transfer manifest and elapsed/rework accounting are recorded below when complete. Repository content checks against an unchanged shared index are not candidate provenance proof; the final candidate check uses an isolated temporary index containing only this slice's intended files.

## Final local checks and mutation evidence

`mvn -B -ntp -f backend/pom.xml verify` passed 412 tests (134 core, 7 parser, 271 server), zero failures/errors/skips (`/tmp/es-d06b3-final-verify.log`). Three temporary authority mutants were each killed by an assertion and restored byte-for-byte:

- Removed inspection-valid latch comparison: one failure in `PlanViewAdmissionTest.failedReinspectionInvalidatesPreparedResponseEvenWithoutRevisionChange` (`/tmp/es-d06b3-mutant-latch.log`). Command: Maven `-f backend/core/pom.xml -Dtest=PlanViewAdmissionTest test`.
- Disabled field masking: one failure in `PlanViewProjectionTest` (`/tmp/es-d06b3-mutant-mask.log`).
- Disabled the 256-conflict completeness refusal: one failure in `PlanViewPreviewTest.aPossiblyCappedConflictListCannotMasqueradeAsACompleteTotal` (`/tmp/es-d06b3-mutant-conflict.log`). Both server mutant commands used Maven `-f backend/pom.xml '-Dtest=PlanView*,MinimalRuntimeTest' test`.

Pinned Node24 `node --test scripts/schema.test.mjs`: 21 passed; `python3 scripts/check_repository.py`: PASS; `python3 -m unittest discover -s scripts -p 'test_*.py'`: 10 passed; `git diff --check`: PASS. No image/DB gates are attributed to this slice.

The existing async transport ends the response on a committed runtime refusal; the new encoder verifies before every chunk and declares exact Content-Length, so a mid-stream refusal leaves an incomplete response. It does not provide a replacement JSON error after commitment. A constant committed-abort diagnostic is follow-on transport work; no exception/input is logged by this path.

The isolated-index candidate content check passed (`/tmp/es-d06b3-candidate-content.log`). It used a new temporary `GIT_INDEX_FILE`, `git read-tree HEAD`, and only the 30 explicitly enumerated intended files. No shared Git state changed. This is a known-pattern guard plus explicit independent-mock provenance review, not proof against every possible disclosure.

Scope audit compares this worktree with the original frozen D06b2 tree. Inherited D06b2/FGA/session corrections and the lead-owned normative Markdown overlay are excluded from the transfer manifest. The POM delta is solely the new view-schema resource include and must be merged serially by the lead. Eight already-reviewed schema/OpenAPI/fixture artifacts remain in the manifest for transfer integrity.

After all mutants were restored, the full `verify` passed again: 412 tests (134/7/271), no failures/errors/skips (`/tmp/es-d06b3-restored-verify.log`). Elapsed observable work interval from the first schema RED log to freeze: approximately 33.8 minutes. No external blocking time was recorded. Separate coding versus rework timers were not instrumented; the actual rework consisted of constructor visibility, strict enum decoding, inspection-latch enforcement, quiet-reader synchronization, publication-valid masking fixture and explicit quarantine retry expectation corrections described above. No parallel speed-up claim is made.
