# Sequential HTTP settlement correction — 10 September 2026

Status: implemented, author-checked and independently source-reviewed;
remote correction verification and full integration pending. Base
`30446e206b5489c23f98e9c966056a7f678de8bd`. The lead accepted
[TEST-QA-001](https://github.com/timaday/environment-studio/issues/9#issuecomment-5620907130)
and is its sole correction writer.

The remote full baseline gate executed330 core/7 parser/947 server tests, with
six server assertions receiving429 instead of their intended status. Supervisor
was skipped. Two isolated green workflow runs did not supersede that failure.
The remote reviewer's four-context latch probes demonstrated a received response
with one original unsettled semantic record, closed429/CAPACITY on the next
request, then the expected response after actual settlement. They did not identify
every original unscheduled interleaving. No application authority defect was
established.

Four security HTTP test classes now use a class-local sequentialRequest helper:
await the existing actual fixture registry's zero-record barrier, then send the
unchanged request exactly once. This covers ordinary success, refusal, setup,
replay and large-page recovery. The existing three-second bound fails on missing
settlement. Teardown waits before routine logout but executes its original raw
logout loop in finally if that wait fails. Normal cleanup must then settle.

The four held-body blocks remain byte-identical: partial body, one record,
immediate competing429, immediate metadata read, original-owner logout, zero
records and cancelled response. Production, socket helpers, fixtures, timeouts,
request arguments and all existing assertions are unchanged. No retry or
premature release is introduced. This follows the existing
[transfer completion contract](../contracts/plan-transfer-completion.md).

## Actual local evidence

Author RED used an external compiled copy of V3PlanTransport with a bounded latch
after closeResources and before workerClosed. The original computed test received
429 while expecting200. At the hold, reflection under the actual registry monitor
confirmed exactly one original SEMANTIC record, unsettled and not uncertain.
The baseline test did not enter its zero-record barrier; the bounded hold expired.
Exact original/instrumented sources, commands and logs remain external in
`es-http-settlement-controls-20260910/red1-computed`, with `red1-results.json`.

Four controlled GREEN runs (computed/document/structural/workflow) compile and
exit0. The external fixture hook releases the held original worker only when
the existing actual zero-record poll is reached; that poll still observes real
settlement. Each original test then passes. These fresh JVMs use-Xmx1024m and an
external DirtiesContext annotation for explicit Spring shutdown. Hooks never
enter repository sources or outputs. GREEN preceded one indentation-only edit
to the teardown body; subsequent focused checks use the exact frozen candidate.

Four separately compiled test mutants remove only their sequential helper's
barrier. Every run exits1 with the original200-versus429 assertion under the same
controlled schedule. Other barriers, cleanup and production guards remain intact.
Results: `green1-results.json`, `mutant-results.json`; no compile failure or
uncontrolled timeout is counted as a detected fault.

Actual uninstrumented focused Maven: **20 tests,4core/1parser/15server**, zero
failures/errors/skips,19.371s. All four complete HTTP classes run, including held
requests, whole/partial reuse, multi-document edits and oversized validation pages.
Command: `mvn -B -ntp -f backend/pom.xml -pl server -am
-Dtest=HostedPlanServiceTest,FifthEditionClassifierTest,V3PlanComputedHttpBoundaryTest,V3PlanDocumentHttpBoundaryTest,V3PlanStructuralHttpBoundaryTest,V3PlanWorkflowHttpBoundaryTest test`
(selector shell-quoted). Log `es-http-settlement-focused1-20260910.log`.
The earlier skip-tests build only prepared isolated classes; it is not test PASS.

G02 passes79 frontend/58 schemas, TypeScript/Biome36 and production build, unchanged
lockfile, Node24.20.0 (`es-http-settlement-g02-20260910.log`). G00 whole-diff
provenance/content/integrity/whitespace and11 Python checks pass before commit.

## Review and limits

Independent fixed-source review verified1069 hashes and1065 unchanged base files,
all four byte-identical held blocks, and preservation of every existing request
argument/assertion after normalizing the helper rename. No confirmed finding.
Manifest SHA256 `62dfce4ee3e8e07a4eeeb71067ee1341785a123f2723b78a2b0689bb5b3b004a`;
report `es-http-settlement-fixed1-review-20260910.md`. Reviewer assessed author
RED/GREEN/focused evidence without executing tests; later mutants have separate
author attribution.

The existing fixture uses a static runtime reference under sequential class
execution; parallel-context safety is not qualified. The remote full gate remains
failed until an exact fetchable correction is independently verified with the
selected combined code. No new full reactor, OCI, database/native, browser or
release qualification is claimed. Existing publication rejection remains; no
alternate upload or unchanged remote retest is assigned. All fixture material is
independently invented.
