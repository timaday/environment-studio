# QF-0003/0004 — shared internal v3 comparison

The independently reviewed change adds complete-proof Raw, Placeholders and Formatted XML to the
shared v3 plan lifecycle. Before returning either side, the adapter independently
reprojects original XML; target comparison also reruns materialization and checks
the entire retained target proof. Missing/changed pins, draft, rule results or
provenance refuse. V2 paths and exact output remain unchanged.

Independent review found a direct-comparison race: a failed reinspection could
invalidate evidence without changing revision/generation, then a held comparison
returned old XML. The corrected owner rechecks inspection/active-operation context
and signals original work/view controls on successful, failed, cancelled and
expired observations. Stable retained current remains readable after failure;
inspection and target authority are not restored. Scratch is released only when
work leaves. No new HTTP route, runtime admission or export is enabled.

## Fixed candidate and actual evidence

Base `1bafdf1`; fixed12 manifest SHA
`1e1aecbf60f98696ca8a5219cb80d64b9cb375f251d63bbdf472a7833be5b381`.
Independent finding SHA
`08bd02a4f8a6f4e38b9ba06289d3cb1747c69e36e62fbfacabad1d608add4ff9`.
Corrected independent re-review SHA
`fe07067c318d48286bbcbc34aa8fbf8c3b423aa0c7ddf81ffa736e88f6b54af8`
verified all12 hashes unchanged and passed21 actual tests (1 core,1 parser,19
server),0 failures/errors/skips. A nonexistent optional test selector is excluded
from its counts/coverage; no new reviewer mutation is claimed. No further
concrete blocker was found in the reviewed scope.

Pinned Maven3.9.16/JDK21.0.12 commands in isolated candidate:

- Initial actual Raw test:1 assertion failure,0 errors from legacy unsupported
  dispatch. Comparison controls cover all modes, consent, original/final proof,
  exact encoded text, child-property locations, absent/unresolved targets and
  original cancellation.
- Independent race:1 failed test/0 errors; unchanged and admitted-view controls
  pass. Lead reproduced it unchanged, then added pending/successful/repeated
  failure transitions for5 failed tests/0 errors before correction.
- Corrected focused suite:31 pass. Six transition tests include actual held
  output, view signalling before close, cancellation, expiry and recovery.
- Full `mvn -B -ntp -f backend/pom.xml verify`:1,217 pass
  (289 core,7 parser,657 server,264 supervisor),0 failures/errors/skips;
  hostile unrelated-directory distribution launch passes,23:09:04BST.
- Four earlier compiled comparison guard mutants and three corrected ownership
  mutants are killed by assertions with0 errors. Corrected restored suite passes.

The first Formatted oracle expected decoded characters; it was corrected to the
contract's retained source encoding. A fixture initially had two JUnit constructors;
its setup error was corrected without counting it as RED. The ownership mutation
selector initially matched two sites; after correction all three mutants compiled
and failed assertions. Its first restored command lacked a supervisor test; adding
BoundaryTest preserves the mandatory nonzero-test gate. These harness failures
are not product failures or successful mutation evidence.

All model/XML are independently invented. Business: original/current and proven
target remain distinct, including failed/unresolved states. Engineering/security:
complete proof, exact pins and live original ownership are independent checks.
QA: adverse schedules expose invalidation without a revision change. Performance:
full proof recomputation and retained response overlap still need combined resource
qualification. Computed pages, remaining binding/view APIs, v3 validation, full
operator workflow, client/readback and release qualification remain open.
