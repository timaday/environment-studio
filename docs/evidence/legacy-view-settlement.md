# TEST-QA-004 — sequential legacy replay settlement

Base028d6b08d58fd66003cc5d42d451d4544db34f19 includes TEST-QA-003.
Lead owns this test/evidence correction; native IDE2 retains its assignment.
No production, API, guard, deadline, model, build or dependency change.

Independent f012956 OCI verification failed after materialization HTTP200:
the following exact command replay returned429 CAPACITY. Issue9/5633282051
preserves the controlled actual-context reproduction: hold original Work.close,
observe the same lease/revision admission and scratch still retained, observe
the exact capacity refusal, release original close, await actual scratch zero,
then observe the original receipt and remainder of the workflow passing.
This supports a test synchronization defect, not a production capacity leak
or replay corruption. Lead accepted the finding in issue9/5633386733.

Acceptance: the intentionally sequential replay follows actual view scratch
release. Socket receipt alone is insufficient. Keep exact receipt/status/body
assertions, adversarial held-capacity tests, original clocks and application
ownership. Do not retry a command or accept capacity refusal as success.

The correction invokes existing `PlanHttpTestConfiguration.awaitViewScratch(false)`
after materialization and before the original replay. That bounded helper reads
actual admission state under the service lock; it does not mutate or release it.
If settlement fails, attempt explicit session logout and rethrow the original
failure, retaining any cleanup failure as suppressed. No ordinary assertion is
weakened or hidden by cleanup.

Author verification: freshly compiled corrected HostedBoundaryTest and a narrow
JUnit launcher on Java21, then executed only
`actualHttpOneToTwoCrossDocumentTargetReplayForeignBodyAndFailedInspection`.
Actual result:1 discovered,1 successful,0 test/container failures. This includes
the original HTTP replay, foreign-body, failed-inspection and privacy assertions.
Hash-verified inherited runtime and unchanged production/fixture sources were
used with isolated1024MiB JVM, ephemeral ports and a fresh private RAM workspace.
Raw output stayed in RAM; process exited and temporary resources were removed.
Source/runtime hashes remained unchanged. External commands/hashes/safe result:
`es-legacy-view-settlement-check1-20260911/result.json` under `/home/tim/.tmp`.

RED provenance remains the independent failed OCI run and controlled held-close
probe. No unchanged failing campaign was rerun for acknowledgement. Local normal
execution does not prove the held schedule or cleanup-failure branch; independent
corrected verification must exercise the reported boundary. G00 and fixed-source
review results are recorded in the candidate handoff. Full corrected G01/G02/G08
and applicable integration gates remain required; f012956 acceptance stays blocked.
Its1699 host tests and9hosted/2demo browser cases passed independently, while
G02 and G08 failed. OCI runtime image/smokes/artifacts were not reached. Preserve
the separate unexplained d67 visibility failure.

Provenance: existing independently invented structural-target fixtures and generic
test synchronization only. No private model, renamed source or new fixture data.
