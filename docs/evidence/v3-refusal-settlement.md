# V3 semantic refusal-loop settlement correction

Base `256e1875a6880c83c00402d259ed3b2357212c5d`, combined operator integration.
Lead is the sole writer. Production, contracts, fixtures and build code are unchanged.

[TEST-QA-008](https://github.com/timaday/environment-studio/issues/9#issuecomment-5634725357)
is accepted. The original malformed capture400/MALFORMED_BODY reply on e4a41e5
was held after core cleanup/before workerClosed. The next original preview request
returned429/CAPACITY, failing its unchanged400 assertion. Original-worker release
and registry zero restored identical400/MALFORMED_BODY. Remaining validation,
export and wrong-origin assertions passed; revision1 and targetComplete=false
were unchanged. This independent controlled legal schedule supplies RED evidence
for a sequential test precondition defect, not a production refusal/cleanup defect
or the precise unscheduled OCI timing.

Only the ordinary capture/preview/validation refusal loop now uses a class-local
helper awaiting the existing bounded original registry-zero witness. All request
bodies, expected statuses and failure codes stay unchanged. The raw four-partial-
metadata capacity test, wrong-origin socket and held-body/logout oracles remain
intact. A pre-zero witness is added before beginning the separate one-record
logout probe, so unrelated setup cannot satisfy its count. That source-audit
precondition correction is not presented as a separately reproduced failure.
Metadata/credential requests are not relabelled as semantic requests or broadly
wrapped. Teardown uses the already reviewed Materialization first-failure,
all-logouts, final-settlement pattern; privacy assertions remain after settlement.
No retry, tolerated429, capacity, timer or ownership change.

Author fixed check2: fresh Java21 compilation and all8 actual HTTP/OIDC/private SQLite
class tests passed,0test/container failures, -Xmx1024m, ephemeral ports/private RAM
workspace and logs. Current test compiled ahead of hash-verified inherited
production/fixtures; source and inherited hashes remained unchanged, private RAM
cleaned. Commands/results: external `es-v3-refusal-settlement-check2-20260911`.
This includes ordinary creation/inspection/ownership/version/refusal/history and
raw metadata-capacity/logout probes. It does not independently verify corrected
held-worker scheduling or cleanup fault branches. Independent source review1 found REFUSAL-REVIEW-001: the initial patch put the
pre-zero wait inside the intentional four-body admission loop. The original
check1 reported8PASS on its actual schedule; that did not exclude this regression.
Its source/results and blocking review remain preserved. The corrected patch
restores that whole capacity method byte-for-byte and puts the wait only before
the separate logout probe's begin.

A bounded local actual HTTP control forced each partial body to reach its
original registry count before admitting the next. On the first patch it failed
at the misplaced zero wait (1test failed, line199); on this correction all four
held admissions, fifth429 and original release/recovery assertions passed
(1test passed). External packets `es-v3-refusal-settlement-held-red1-20260911`
and `es-v3-refusal-settlement-held-green1-20260911` retain source/commands/hashes.
Only existing registry witnesses were added to the external test copies; no
production clock, guard, socket behavior or original assertions changed.
This is author control evidence, not independent corrected execution. Fixed
review2 accepts REFUSAL-REVIEW-001 corrected with no further confirmed finding:
manifest `bd0764a5eeef5163f8a94c1bcdac29bb9b792bfed7c449e0b31672e9badd4686`,
external `es-v3-refusal-settlement-review2-20260911.md`, execution NONE.
The reviewer verified original method identity, source/result hashes and the
single witness difference in each controlled copy. This status update changes
no test source. Independent execution and combined gates remain pending.
Earlier failures/results are preserved.

The eight-class read-only audit found the remaining five classes already await
ordinary semantic settlement; they are unchanged. Together the bounded Physical,
Command and refusal corrections address only the three confirmed source gaps.
Only existing independently invented mock fixtures are exercised; no private
application material, new model, credentials or response payloads are introduced.
