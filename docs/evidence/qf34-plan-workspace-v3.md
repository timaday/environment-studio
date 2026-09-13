# V3 plan publication lookup

Reviewed and integrated on 9 September 2026. The [contract](../contracts/hosted-plans-v3.md) precedes the new
versioned core port and read-only workspace adapter. Hosted v3 plan creation,
publication HTTP and runtime availability remain unfinished.

The lookup resolves exact owned immutable publications, recompiles original
source/format and compares complete current checked metadata. Profiles also
recheck the selected plan definition and their original immutable definition;
different bindings are allowed only with matching logical compatibility and
fresh physical-only profile validation against both definitions. No lookup writes
history, uses command replay, enumerates objects or constructs a v2 Ready result.
The actual current compiler still refuses the synthetic historical publications.

## Author verification

Base `62a5db7`, isolated archive `es-v3-plan-workspace-5125mpew`; four-file
candidate manifest SHA-256
`11c0a584eb85070a10935b92739a564e812745be3433409d2fb29754b90b67f9`.
The first test invocation stopped at a nonexistent parser-test selector. The
corrected actual RED compiled successfully and reached the untouched UNAVAILABLE
lookup scaffold after SQLite history/restart setup: one assertion failure and
no errors, with six sibling controls passing. Implementation followed that RED.

Nine new tests cover exact JSON/YAML source, immutable references after later
drafts/restart, actual compiler refusal without SQLite changes, compatible binding
differences, logical incompatibility, owner/kind/version isolation, unpublished
history, null results, changed binding/mechanism metadata, repeat qualification,
edited selected pins, profile content/reference mismatches and safe diagnostics.
An expanded test setup initially called the nonexistent v2 saveDefinition method;
using its existing mutate method corrected that compilation failure. No production
change was needed for that test setup error.

Successful sequencing tests first use the actual compiler, verify its sole
MECHANISM_UNQUALIFIED diagnostic, then explicitly remove it through a test-only
compiler witness. No runtime setting enables that witness. The focused compatibility
selection passes50 tests: nine core, one parser and40 server. Seven compiled
guard mutations each cause one selected assertion failure with no errors or skips:
complete checked equality, current diagnostics, selected pin equality, profile
content equality, exact source, original definition resolution and the second
profile check. The restored15-test selection passes.

Full Maven verification passes1135 tests:273 core, seven parser,620 server and235
supervisor, with zero failures/errors/skips and the standalone assembly/launcher
checks passing. The run finished at21:26:12 BST on9 September using Maven3.9.16
and JDK21.0.12. This is local evidence for the frozen candidate, not an explanation
of every failure in the incoming JDK21.0.8 review environment.

External records under `/home/tim/.tmp`:

- `es-v3-plan-workspace-candidate1-20260909.sha256` and
  `es-v3-plan-workspace-author-evidence-20260909.md`; author record SHA-256
  `30afb11a1889f0908925768ea46cab1a7d80bba4f6a56853fea5912913cce58b`.
- `es-v3-plan-workspace-red2-20260909.log`,
  `es-v3-plan-workspace-green4-20260909.log` and
  `es-v3-plan-workspace-full1-20260909.log`.
- `es-v3-plan-workspace-mutants-20260909.py`, its results JSON, individual logs
  and restored log. The mutant archive is `es-v3-plan-lookup-mutants-9zd9760z`.

Business review preserves value-free reuse across compatible bindings. Engineering
keeps versioned history separate from the shared hosted lifecycle. QA covers
historical/current disagreement, isolation and repeat qualification. The owning
plan service must still enforce its original live lease, revision, cancellation
and shared capacity before and after lookup. No plan, target or export is
authorized by these internal records alone.

## Independent review and integration

The reviewer inspected all four fixed files in `es-lookup-review-ob9_bepn` and
added an independent actual-SQLite test. Donor and recipient publications have
matching logical digests and different physical bindings. After successful reuse,
the donor loses current qualification while the recipient stays qualified. Lookup
must refuse before profile compilation, preserve every table count, and recover
only after fresh donor qualification. No material source/contract blocker was
found within this lookup boundary.

Initial and restored focused runs each passed24 tests. A cleanly compiled mutation
substituting the selected definition for the original donor was killed by one
assertion, with zero errors/skips. Review record
`es-v3-plan-lookup-independent-review-20260909.md` SHA-256
`e6149b958c84478ae6b46f49e55c66cf2e2b60758edc81e43cb32c1757eef0fe`;
independent test SHA-256
`659e3cfeecaba4959cb69191ae298da6db36b54409ec099292aa8510907756e8`.

Lead integration uses base `8f44832` (including the reviewed native script
prerequisite) plus those four files and the independent test. Manifest SHA-256
`ffa995ab39b938825a9cb5cdfc5c0507b855cdd34d586d187e0481cdc8e94754`.
Pinned Maven `mvn -B -ntp -f backend/pom.xml verify` passed **1,151 tests**:
273 core,7 parser,621 server,250 supervisor; zero failures/errors/skips, with
assembly and hostile-launch checks passing. The run ended at21:47:45 BST on
9 September2026; log `es-v3-plan-integrated-full1-20260909.log`, archive
`es-v3-plan-integrated-cen_8mfa`. Reviewed hashes were verified before integration;
the contract status label was then updated from planned to reviewed.

The [actual both-database workflow extension](qf34-database-workflow-v3.md)
separately exercises target materialization and profile reuse from real mock TLS
observations. Its reviewed helper correction and final both-engine run retain
exact source/log hashes. It does not implement the hosted lifecycle or exercise
this lookup. No current image, CI, publication, deployment or release gate follows
from these local results. No measured parallel speed-up is claimed.
