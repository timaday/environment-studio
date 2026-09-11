# Capture pagination test execution correction

TEST-QA-009 is accepted from independent issue9 reports5637135014 and5637228235.
On exact b74ffac25decd95f1207ea30ca2292faaf678bba, Windows full frontend
350PASS/1FAIL and OCI350PASS/1FAIL exceeded the original5000ms budget while
typing first-page setup. The cross-page assertions had not executed. This is a
confirmed test execution defect; it does not establish a production performance
defect. Separate three-item browser evidence cannot resolve this21-item case.

Against parent1dc30283d4673bef057728a4bf11112303dae7f8, the lead changed only
repetitive first-page fixture entry to actual user-event click/paste interactions.
All21 items, two pages, typed duplicate/correction, accessible diagnostics,
clearing, disabled/enabled capture and zero-request assertions remain. Returned
first-page values are now checked for all20 identifier/label pairs. No production
component, state, test timeout, timer, assertion or fixture count was weakened.

The original focused test passed locally; no local RED is claimed. Independent
Windows/OCI failures above are the defect evidence. Corrected author execution:
all6 Capture component tests PASS, affected cross-page test1048ms within the
unchanged5000ms budget. TypeScript/Biome check PASS. Logs remain external as
es-capture-pagination-baseline-20260911.log, es-capture-pagination-green1-20260911.log
and es-capture-pagination-check1-20260911.log. Linux/Node24.20.0/Vitest5.0.0;
these results do not establish Windows or constrained OCI performance.

Independent fixed source review found no findings: two-file manifest
8cfc4a915bafbd3ccecefcc4adbce4f2e63233bbb5a3d44abfe278e5a1b6accd,
external es-capture-pagination-source-review1-20260911.md; reviewer execution NONE.
Remote corrected-candidate G02/G08 remain required. Production Capture browser evidence is unchanged in scope. This test
correction confers no export, native client or release qualification.

## Corrected Windows execution follow-up

Independent ffb83da Windows425PASS/1FAIL still exceeded5000ms during row16
setup, before cross-page assertions (issue9/5637547990). Paste was insufficient
in that environment; the finding remains open. The next test correction uses
DOM change events for19 repetitive background rows, with real typing retained
on row1, row21, the duplicate and correction. Navigation,21items/two pages,
all20 returned pairs, accessible diagnostics/clear, disabled/enabled capture and
zero requests remain asserted. No component/state or timer/timeout changes.
This checks controlled-input change handling; it does not claim bulk keyboard
throughput or replace the other fully typed Capture journey.

Author6Capture PASS, affected669ms; TypeScript/Biome PASS. External logs
es-capture-pagination-input-green1-20260911.log and
es-capture-pagination-input-check1-20260911.log. Independent Windows/G02/G08
verification of the corrected exact SHA remains required; no localRED claimed.

Fixed non-author follow-up source review found no findings; manifest
857952801d7ce2c37ce409711865d4340143e2b166429b03714b06a35e0d7309, external
es-capture-pagination-input-source-review1-20260911.md; execution NONE.
