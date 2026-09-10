# Retained readback snapshot correction — 10 September 2026

READBACK-QA-001 is accepted from the [independent report](https://github.com/timaday/environment-studio/issues/9#issuecomment-5625690169)
on `5d724159d7cb839cdcf762f0704c2bf5b5afb137`. Validation previously walked the
caller list, then copied it. An ordinary concurrent replacement could introduce
changed XML with an old digest into the retained Expected and produce MATCHES.
This is an internal correctness defect, not a demonstrated HTTP/export bypass.
The lead alone owns this isolated correction; native IDE2 ownership is unchanged.

The constructor now copies at most 128 document references into its own bounded
collection, freezes that collection, and validates exactly those retained entries.
Documents and keys are immutable records. Detected concurrent structural changes
and null entries use only INVALID_READBACK_EXPECTATION, without a retained cause.
The copy does not claim an atomic view of an unsynchronized caller collection;
every retained entry still has to pass the complete existing inventory checks.

Acceptance uses the existing independently invented two-document literals and a
new invented replacement. A bounded scheduling hook at the second list entry
allows a separate thread to replace the first using ordinary ArrayList.set.
The retained original snapshot must match the original observation and differ
from the changed valid observation. Additional cases retain the safe diagnostic
for null/detected structural mutation and stop copying at the first excessive
entry even when a caller's advertised size no longer describes its iterator.
No real model, XML, image content or database input entered this slice.

Actual author checks (Java21.0.12, Maven3.9.16, isolated worktree/core output):

- Initial command used system Maven3.8.7 and was refused by the toolchain enforcer;
  `es-readback-snapshot-red1-20260910.log` is setup failure, not production RED.
- Correct-toolchain focused RED: 17 tests, two failures. The coordinated mutation
  retained the wrong inventory; the structural-change case exposed a raw
  ConcurrentModificationException. Log `es-readback-snapshot-red2-20260910.log`.
- Focused GREEN after correction: 17 tests pass; `green1` log. After adding the
  independent growing-inventory bound case, affected core gate passes 353 tests,
  zero failures/errors/skips, including architecture (`core1` log).
- Command: `/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn
  -B -ntp -f backend/pom.xml -pl core test`; focused runs additionally select
  `-Dtest=PlanReadbackTest`. Logs remain outside the checkout under `/home/tim/.tmp`.

Fixed non-author source review finds no confirmed defect; it verifies all three
frozen hashes and reviews the author checks without executing tests. Report:
`es-readback-snapshot-fixed1-review-20260910.md`. The fixed manifest remains
`es-readback-snapshot-fixed1-20260910/manifest.json`. An isolated external copied
core control passes all 18 focused tests. Three distinct source faults compile
and fail the intended assertions: restoring validation-before-copy, removing the
128-entry copy bound, and exposing raw concurrent-modification errors. No invalid
mutant or survivor; `es-readback-snapshot-mutations-20260910/results.json` preserves
individual outcomes. The first mutation also fails the growing-inventory bound
case; two failed assertions do not mean two additional production defects.

RST-04/08/09 and Business/Engineering/QA assessment: a definite result requires
checked retained bytes; bounded retention and safe diagnostics preserve privacy
and resource behavior. The ordinary set race is exercised with bounded latches
and a joined executor, independently invented literals and exact expected results.
The constructor does not repair or synchronize a caller's mutable collection.
Combined integration and remote correction verification remain pending.
No full reactor, frontend, browser, OCI, database or native campaign was run for
this correction. Independent 350-core/2,210-assertion evidence on the parent is
preserved; it missed the snapshot race. The remote reviewer's prior three mutants
were all detected by existing tests and two by its new probe. Those are separate
parent results, not new verification of this correction. Combined integration,
publication, fresh readback ownership and release qualification remain required.
