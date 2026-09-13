# V3 materialization sequential settlement correction

Base `4ca0aee1fa4c9c18d80a67903284d673b590598a`; lead is the sole implementation
owner. Branch `review/v3-materialization-settlement-20260911` changes only the
affected HTTP test, this evidence and delivery progress. Native WIP and the
Capture renderer are excluded. Production, fixtures, contracts, dependencies
and build configuration remain unchanged.

## Confirmed failure and acceptance

The [independent TEST-QA-005 report](https://github.com/timaday/environment-studio/issues/9#issuecomment-5633868884)
reproduces an additional sequential test defect after the first corrected OCI
build failed: first materialization200, next expected200 received429. A latch
after original core cleanup but before original workerClosed left exactly one
same-lease semantic HTTP record; the original second-response assertion failed.
Releasing the original worker and awaiting actual registry zero restored the
unchanged200/body/revision oracle. Core scratch already being clear was
insufficient. The report proves a legal held schedule, not the exact original
unscheduled timing. No production leak or materialization corruption established.

Acceptance: intentionally sequential materialization, command and refusal calls
wait for the existing bounded actual registry-zero witness. Preserve exact
COMPLETE/INCOMPLETE/REFUSED outcomes, revisions, target content and early response
assertions. Held partial-body capacity and logout requests remain raw. No retry,
accepted429, early record release, production deadline or ownership change.
The [original lifetime contract](../contracts/plan-transfers-v3.md) remains unchanged.

## Correction and actual author checks

The class-local sequentialRequest helper uses existing awaitRecords(0) before
ordinary requests, following the established neighboring workflow tests. This
helper inspects the original registry under its lock with the existing3second
bound. Teardown attempts every raw logout even if initial settlement failed,
then checks settlement again; the first exception/assertion is preserved and
later cleanup failures are suppressed. This does not turn inconclusive cleanup
into success. Existing privacy canary checks remain required on settled teardown.

Fresh compilation and actual HTTP/OIDC/SQLite class execution passed **3tests**,
0test/container failures: ordinary all-three-outcome workflow, required
inspection/version/owner/CSRF/body refusals, and raw held-capacity/logout recovery.
Java21, -Xmx1024m, fresh private RAM workspace/logs and ephemeral test ports;
current test compiled ahead of hash-verified inherited production/fixtures.
Exact commands/results/source/runtime identities remain external under
`es-v3-materialization-settlement-check2-20260911`. All inherited inputs and the
current source retained their hashes; raw logs were hashed then removed with
the private RAM directory. This is affected author integration evidence, not
full Maven/OCI, independent replay or release qualification.

Preserve check1: javac and all3JUnit tests passed, but the copied launcher still
required exactly1test and exited1. Its result/launcher and limitation note remain
unchanged. A fresh run with the corrected3test count passed; the first command
is not relabelled successful.

The independent failed OCI and held-worker reproduction supply the existing
RED evidence for this test correction. No local production RED or new production
defect is claimed. Local normal execution does not prove the held-worker schedule
or newly aggregated teardown failure paths; these need bounded independent
verification on the immutable correction. No unchanged native/browser/database
or full build campaign was repeated.

## Review and remaining gates

Fixed non-author review found no confirmed finding against the immutable
three-file snapshot (manifest SHA256
`d866e812e4992722bb8b2f262bf9eb60e400c8f84cdbf8fdb727499750b05e21`).
External report: `es-v3-materialization-settlement-independent-20260911.md`.
Reviewer execution: NONE. The review verifies preserved sequential/adversarial
oracles and first-failure cleanup semantics; independent corrected-candidate
execution remains pending. This subsequent status update changes no test source. Full applicable combined gates remain required after publication;
4ca's failed OCI is preserved. TEST-QA-003/004 remain independently verified in
their recorded scope, and applicable unchanged f012 browser evidence may be
reused with its d67 limitation. Runtime images/smokes/artifacts were not reached
in either failed OCI build. No application/native release or admission claim.

Provenance: existing independently invented generic mock fixture only. No real
or transformed application model, XML, credentials or client material was added.
