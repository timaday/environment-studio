# Reuse and validation review coverage — 11 September 2026

Test-only candidate based on `dd4d007683dcc2ebefd6694f3bc321ad10fea6bc`.
Issue9 independent reports5632037064 and5632188242 demonstrated four existing
suite gaps; the production guards were already correct. Lead accepted all four
and owns these additions. No runtime, renderer, contract or native change.

| Finding | Acceptance / new regression |
| --- | --- |
| REUSE-COV-001 | Individually valid second dependency/relation/conflict pages with changed totals invalidate preview and cannot dispatch apply. |
| REUSE-COV-002 | Internally consistent preview pages pinned to a different original observation refuse and cannot dispatch apply. |
| VAL-COV-001 |422 UNAVAILABLE/REJECTED clear summary/page/request and offer no smaller-page recovery. |
| VAL-COV-002 | Offset2147483648 refuses without IO and retains the exact displayed summary/page/request objects. |

Author control:31reuse +29validation =60PASS. Four separately compiled external
faults reproduce the reported gaps: the original53 tests pass every fault; the
expanded60 detect respectively3/1/2/1 failures. Every fault compiled successfully
before both runs and was restored in the external copy. No production RED is
claimed: these are regression-strength improvements to already-correct behavior.
The initial external copy omitted referenced contract/schema/fixture files, so
TypeScript failed before tests. That setup failure is retained and excluded;
fixed1 copied the eligible dependencies and produced the four measured results.

The examples challenge section-relative consistency, original-observation
correlation, status/code recovery classification and preservation of displayed
state after invalid navigation. They prove client refusal/dispatch behavior,
not backend sibling preservation, actual validation rules or resource limits.

External commands/results: `es-review-coverage-control-20260911.log`,
`es-review-coverage-mutations-fixed1-20260911.py` and its `result.json`/per-run
compile and test logs under `/home/tim/.tmp`. Each variant runs
`node node_modules/typescript/bin/tsc --noEmit` then Vitest on the two hook tests.
Node24.20.0, Linux, existing locked dependencies, isolated build outputs; no
browser/server/database/container/native or reserved full Maven/OCI resources.
Fresh G02 passes325frontend/59schema tests, types/e2e types and Biome69.
Fixed non-author review verified all three snapshot hashes and the recorded
mutation failures; no confirmed finding, no reviewer execution. The two test
hashes remain unchanged after review; this evidence paragraph records completed
results. Review: `es-review-coverage-independent-20260911.md`. No production
bundle changed, so the independently verified dd4d007 build evidence is reused;
no new build claim is made.
Independent broad review evidence remains scoped to its original candidates;
combined integration, G01/G03/G08 and release qualification remain open.
