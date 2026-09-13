# V3 plan review acknowledgement — local candidate

Local application/HTTP implementation on comparison base
`9b1b7e369e7c55781d1cc5bb3d3e2bba2dc3892d`. Non-author source review resolved
one P2 after correction; no further confirmed findings. Integration, full
Maven/OCI and release qualification are pending. This evidence does not
supersede the published baseline or qualify production compiler/client admission.

## Behavior and risks

POST `/api/v3/plans/{planId}/reviews` acknowledges exact freshly checked inputs,
destination and `protected-self-contained` intent at the unchanged revision.
The existing lease replay ledger returns historical receipts before pinning;
replay never reinstalls current review. Validation evaluates immutable policies
for every selected document, including unchanged/unmapped/secret content. Denied,
missing and contradictory policy remain FAIL/UNKNOWN/ERROR respectively.
CLIENT_CAPABILITY remains UNKNOWN; export stays unavailable.

The original view owns one atomic abort/commit gate. Abort before commit prevents
record/receipt installation; abort after commit preserves the receipt despite
response loss. Only its fixed signal takes no application lock. Existing HTTP
settlement can acquire locks and retains worker closure/quarantine rules. The body
and acknowledgement use existing16KiB/10s and32KiB/30s bounds; no new work pool,
credential owner, observation, materialization or export operation is introduced.

Acceptance covers actual independently invented XML/content with explicit test-only
publication/observation witnesses, missing target, stale revision/fingerprint and
destination, complete document policy, shared256 replay exhaustion, cross-command
collision, edits/retirement, fresh-proof failure, same-revision target loss/recovery,
lease loss and all four async abort events. The actual HTTP path uses mock OIDC,
CSRF, multi-document current/target and canonical response-schema validation.

## Observed TDD and focused results — 2026-09-10

All local commands ran in the isolated candidate worktree with Java21.0.12,
Maven3.9.16 and Node24.20.0. Counts below are command-specific, not additive coverage.
Logs and immutable RED sources remain external under `/home/tim/.tmp/`.

- `es-v3-plan-review-red1-20260910.log`: the initial review entry refused the
  positive case with UNSUPPORTED_DEFINITION;4 core/1 parser passed. This was an
  intended runtime refusal, not a compile failure. Initial green1 compile setup
  failed on a lambda capture; corrected green2 passed6 tests. Expanded green4
  passed32 (8 core/1 parser/23 server).
- `es-v3-plan-review-abort-red1-20260910.log`: held proof plus an original abort
  returned an Ack when no receipt was expected; one assertion failed. Exact RED
  source is frozen. The atomic gate passed34 tests in abort-green1, including
  the post-commit abort control.
- `es-v3-plan-review-adapter-red1-20260910.log`: the decoder skeleton refused
  valid input, misclassified overflow, and the callback bridge allowed commit
  after abort (2 assertion failures/1 runtime error). Adapter-green1 passed20
  (4 core/1 parser/15 server), including11 existing async completion controls.
- `es-v3-plan-review-http-red1-20260910.log`: actual authorized review POST
  returned403 before the route existed. The same actual HTTP test passed in
  http-green1 for both allowed and denied policy, including retired-plan replay.
- Transport-green1 exposed a test assumption: immediate service invalidation
  during held output correctly refused CLEANUP_INCONCLUSIVE. The corrected
  fixture revokes the lease, waits for worker settlement, then invalidates.
  Transport-green2 passed15; this fixture correction is not a product defect.
- `es-v3-plan-review-focused1-20260910.log`:78 tests passed (8 core/1 parser/
  69 server), zero failures/errors/skips,12.939s. This combines review/validation,
  actual HTTP ownership/CSRF, existing transport regressions, callback registration
  and application-lock controls. After making successful stale/retired replay
  assertions explicit, the affected12-test run also passed.
- Frontend TypeScript/Biome36 files,79 Vitest tests,59 schema tests and production
  build passed. Output is retained in `es-v3-plan-review-g02-20260910.log`.
  Initial schema invocation lacked this worktree's Ajv install and ran no schema
  tests; `npm ci` installed134 packages with zero audit findings, then all59 passed.
  Dependency/lockfiles and React behavior are unchanged.
- Repository integrity/content-pattern/whitespace checks and11 Python tests pass.
  The pattern checker is limited; whole-diff independent-mock provenance still
  requires review before publication.

Focused command: `mvn -B -ntp -f backend/pom.xml -pl server -am` with
`-Dtest=HostedPlanServiceTest,PlanValidationV3Test,FifthEditionClassifierTest,SharedV3PlanReview*,SharedV3PlanValidation*,IndependentV3PlanValidationTest,V3PlanReview*,V3ReviewAsyncCompletionTest,V3PlanWorkflowControllerTest,V3WorkflowWireAssertionsTest,OwnedAsyncCompletionTest,V3PlanTransportTest,IndependentV3PlanTransportTest,V3PlanWorkflowHttpBoundaryTest#reviewAcknowledgesExactWholeDocumentPolicyAndReplaysAfterRetirement+workflowOwnershipCsrfAndStalledBodyKeepTheOriginalScratch`
and `test`. Frontend commands are the documented check/test/build sequence.

## Authority challenges and limits

Eight distinct compiled faults have specific assertion detection: allow denied
document, allow missing policy, omit fingerprint equality, pin historical replay,
omit failed-proof invalidation, disconnect the HTTP abort signal, omit final
commit CAS, and cancel after commit. The first six run unchanged candidate JUnit
assertions with successful original controls. Sources/classes are external;
candidate source hashes remain unchanged. Results/commands/hashes are in
`es-v3-review-mutations-20260910/results.json`.

Two additional controlled schedules pause an externally instrumented copy exactly
after abort CAS/before cancellation-token store, and after commit CAS/before
record installation. Both originals pass; the corresponding last two faults fail
semantic assertions. This isolates the gate from secondary token timing without
adding a production test hook. These are instrumented native Java scheduling
probes, not a claim of exhaustive concurrency exploration. See
`es-v3-review-gate-controls-20260910/results.json` and its frozen source/logs.

The27-file fixed source/contract/test candidate is in
`es-v3-plan-review-fixed1-20260910/source`, manifest SHA256
`03e8e161ce4c7b9789a74ec68b8a0f72ae2093bf445076227e84a3da04fcc045`.
Non-author review verified the snapshot and identified the P2 below. This evidence
file is subsequent reporting only; the first snapshot remains preserved.

## Review correction and fixed2

Accepted P2: full-proof failure during preview left review active, allowing PASS
after same-revision recovery. Root alone corrected the application lane. Actual
verifier RED used an independently invented copied source with a stale digest;
additional actual physical/computed reads used the existing forged retained-rule
fixture. `es-v3-plan-review-proof-red2-20260910.log` records8 assertion failures
and one passing ordinary-error control, zero test errors. Immutable RED sources
are retained separately from the first reviewed snapshot.

The correction observes existing typed PROJECTION_REFUSED at owned content/read
boundaries and failures from the explicit profile full-proof invocation. It adds
no duplicate XML verification. Direct and view-based capture/comparison, preview
and physical/computed reads now invalidate review after failed required proof.
Missing documents and invalid profile requests preserve a valid review.

`es-v3-plan-review-proof-green1-20260910.log` passes99 focused tests (8 core/1 parser/
90 server), zero failures/errors/skips,13.859s, including the earlier selection
plus capture/composition regressions and all9 new proof-refusal controls. All prior
mutants were rerun on the corrected production source; two added faults omit the
preview-proof and content-proof invalidation. Ten distinct compiled mutants are
assertion-detected; both controlled CAS schedules still pass. Exact commands and
hashes are in `es-v3-review-mutations2-20260910/results.json` and
`es-v3-review-gate-controls2-20260910/results.json`. Earlier results remain intact.

Corrected28-file immutable source manifest SHA256
`c22b87f9cd5d03d3bd5f1d9e8ecf463ca5136c99d3d82de0e9d08a7d8ad15a82` is in
`es-v3-plan-review-fixed2-20260910`. Non-author review verified all hashes and the
three-file correction, resolved the P2 and reported no additional confirmed
finding. Report: `es-v3-plan-review-fixed2-review-20260910.md`. The reviewer read
author evidence and did not execute tests/builds; this is independent source
review, not independent test execution or integration acceptance.

No combined retained v3 maximum-resource qualification, real DB/client campaign,
private application material, browser journey, full Maven gate, OCI build,
GHCR/HiveForge deployment or release evidence was produced for this candidate.
The actual HTTP successes use explicit historical test publications; production
compiler readiness remains incomplete. No real-data or release authority follows.
