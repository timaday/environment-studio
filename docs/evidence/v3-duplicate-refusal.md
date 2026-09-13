# Duplicate-member semantic refusal correction

DEF-QA-002 is corrected locally on application base
`820837cd9c0249bc5646f52733ea2a2b40b559e5`. A contradictory raw422 JSON body
previously lost duplicate members in `response.json()`, then falsely established
a definite precommit rejection and released the unresolved save command.

The two exact PUT v3 definition/profile object routes now retain raw reply text
for strict semantic refusal decoding. JSON grammar is validated, decoded member
names must be unique within each object, then the existing closed envelope is
checked. Escaped key aliases count as duplicates; repeated ordered diagnostic
array entries remain valid. Other routes/methods/statuses keep their existing
classification. Session retirement is rechecked after body consumption.

## Actual author evidence

Node24.20.0, local isolated worktree; independently invented raw responses and
existing registered native-v3 fixture only. No real model material.

- RED: selected workspace decoder and definition hook tests:7failed/53passed.
  Duplicate top-level/nested/escaped names normalized to REJECTED; actual
  HostedApi-to-hook pending command became false. Two later unreachable assertions
  initially named nonexistent hook properties; replaced with actual selected
  receipt/source-blocking checks before GREEN. The observed pending-state RED
  was independent of those oracle mistakes.
- GREEN1:60/60 selected cases; full311frontend/59schema, check and build PASS.
- Added adverse checks for malformed raw grammar and delayed completed/failed
  response text after session clear:77/77 selected cases PASS. Final full
  frontend318/schema59 and check PASS. Production unchanged since GREEN1/build.
- Four compiled external-copy faults detected: bypass uniqueness7fail; compare
  raw rather than decoded keys2fail; top-level-only checking2fail; share one
  member set across separate objects3fail. Unchanged control60PASS. Initial
  external copy omitted dependency dist directories, so all initial compilations
  failed before tests; those setup failures are excluded. Repaired copy/control
  and four distinct compiled mutations are retained separately.
- Fixed non-author source review and test-only supplement found no confirmed
  defect. Reviewer executed no tests.

Exact author logs are external `es-duplicate-refusal-{red1,green1,green2,check1,
check2,tests1,tests2,build1}-20260911.log`; fixed mutation records are under
`es-duplicate-refusal-mutants-20260911/fixed1`. Source/test review is external
`es-duplicate-refusal-fixed1-review-20260911.md`, including fixed2 supplement.
A Biome invocation from the repository root encountered its nested configuration;
rerunning in the existing frontend directory passed, with no config change.

## Review dispositions and remaining gates

Queue5631827584/5631906678 DEF-QA-002 accepted; correction belongs solely to the
lead application writer. Remote a9d recovery and actual1522 capture/store
prerequisite passed in their recorded environment. Preserve the remote original
syntax-phase mock oracle limitation: contract-valid parse-phase recovery and
actual server/browser evidence independently support DEF-QA-001 closure.

No claim of current-server duplicate-key emission or actual duplicate commit.
Raw parsing is transport recovery hardening, not new backend semantics. No new
renderer, browser campaign, Java/full Maven, database/native/client or OCI run
was performed for this correction. Remote verification of the fixed correction
and required combined G01/G03/G08 remain open; prior browser acceptance applies
to a9d only. The unexplained d67 initial legacy failure is not cleared here.
Release qualification and pending design approvals remain separate.
