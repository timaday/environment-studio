# Workspace refusal, view cancellation and native arm cleanup corrections

The three confirmed continuation findings are corrected on the reviewed
`15385b473205c185d7a7dc58946c7c3943f34fdd` base. Thirteen integrated files match
manifest SHA256
`714d9cb1fd210f571164b9564f428c8af0f5cde600d13e57d2152d686a3c30d7`.
Each correction has an observed behavioral RED and independent review. These
are local development results, not complete workflow or release qualification.
All fixtures use independently invented mock history, XML and ownership schedules.

## Workspace refusals

The actual production controller, transport, runtime bridge and SQLite workspace
now return the same closed `404 NOT_FOUND` for absent and foreign definition
history. Schema2 storage used for a v3 request returns
`503 PLAN_SERVICES_UNAVAILABLE`. Owned unpublished history remains
`422 PUBLICATION_REQUIRED`; historical test publication still fails current
compiler qualification with `422 UNSUPPORTED_DEFINITION`. Unexpected exceptions
remain generic500, without exception text. See the
[hosted v3 contract](../contracts/hosted-plan-http-v3.md).

`V3WorkspaceRefusalTransportCompositionTest` reproduced both incorrect500
responses before production changes. Three new cases check repeated refusal,
owner isolation, exact code-only/no-store output, once-only async settlement,
released transfer resources, unchanged SQLite counts and absence of an installed
plan. This uses servlet test adapters around the production composition; it is
not an additional real-socket test. Author focused35 passes. The lead reviewed
all three changed files and independently ran17 tests including unexpected-error
controls. Removing each mapping separately gives one assertion failure/zero
errors; each restored run passes17. The author did not run a separate mutation;
these mutation results belong to the lead.

## V2 view materialization

The original view cancellation now guards target publication for both model
versions. If close wins the service lock, neither a complete result nor a refused
result may replace/remove the retained target or change its diagnostics. The v2
adapter is allowed to finish; scratch stays reserved until the executing worker
returns. Accepted v2 commands keep their existing completion and replay behavior.
The [view contract](../contracts/hosted-plan-views-v1.md) makes this distinction
explicit; an already published target is not rolled back by later transfer failure.

`V2ViewMaterializationCancellationTest` holds successful/refused adapter returns
with and without view closure, then checks distinct literal current/target XML,
blockers, scratch retention and recovery. Two further controls close an already
accepted v2 command during materialization and verify its result and exact replay
without another render. Final pre-change RED has six tests, two assertions and
zero errors: late replacement and late removal. An earlier test compilation error
is retained separately and does not count as RED. Author and independent focused46
pass, including existing v3 actual XML/proof cancellation controls. Two compiled
mutants—restore v3-only view forwarding, and cancel every accepted v2 command—each
fail two assertions/zero errors; exact restorations pass six tests.

## Native arm ownership

Root records affirmative evidence that a completed arm acquired no window;
launch snapshots it only after the primitive returns. Proven non-owning refusal
can settle its eventfd/listener without successful disarm. Disarm still refuses;
it does not fabricate ownership or admission. Active calls and acquired/uncertain
windows retain their existing disarm obligation. Explicit cleanup failure and
expired cleanup remain permanently inconclusive. See the narrowly clarified
[private ABI](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md).

The actual production launch/root/fork/listener/cleanup components reproduced the
complete-close failure while another thread held the global fork window. Five new
native schedules cover contention, close before finally-disarm, cancellation of
an acquired owner, arbitrary disarm refusal and explicit cleanup failure without
ownership. Checks include actual descriptor closure/reuse, endpoint removal,
repeated close/cancel, unaffected first owner and fresh window recovery. Kernel
`7.0.0-31-generic` provides actual `SO_PASSPIDFD`; no platform component was
substituted. Named fault cases inject only root-disarm return values.

Independent native execution passes all five new schedules and eight existing
success/cancel/cleanup controls. Author full Java1554 and distribution verification
pass. Five final compiled guard mutants fail assertions. A preliminary mutation
clearing uncertainty before settlement survived because the unchanged expired
deadline immediately restored it; the final settlement mutant fails. That
observation is retained, not relabelled as a killed mutation.

The acquired disarm-fault case deliberately retains quarantined launch storage;
test-only root disarm releases the TLS window and process exit reclaims remaining
descriptors. It does not prove production recovery from unpublished disarm
completion. Lock-free prerequisite failure, generation exhaustion, thread
sanitizers and exhaustive schedules were not executed. The new probe itself does
not start Java children; existing Java FORK integration remains in the full suite.
No production JNI, mapped-byte/loader admission or native-client readiness follows.

## Evidence and integration

External journals preserve exact commands, immutable source manifests, original
logs and reviews under `es-workspace-refusals-20260910`,
`es-v2-view-close-lead-*-20260910`, `es-arm-cleanup-*`,
`es-review-arm-newturn-*` and `es-p2-lead-*-20260910`. No private model was used.
Java commands use Maven3.9.16/JDK21.0.12. Focused reactor selections include actual
upstream tests; preliminary native command-selection/dependency failures are not
behavioral RED and did not weaken the no-tests gate.

Business review checks useful safe refusals, preservation of the operator's
retained target and no false readiness. Engineering review checks the real
composition and lock/ownership boundaries. QA/RST investigation checks both race
orderings, repeated refusal, accepted-command controls, descriptor reuse and
recovery. These bounded investigations support the corrections; they do not
complete the broader stale-state, operator, database/client or release charters.

Writers used disjoint external archives; the lead remained read-only until the
workspace writer froze its source. Workspace author elapsed approximately seven
minutes; native author9m06s; independent v2 review approximately four minutes;
native review160.3s. No author rework was requested by the fixed reviews. Lead
integration, evidence and gate interval was8m11s, including diagnosis of shared
build artifacts and an isolated rerun. V2 authoring to frozen source was5m18s.
These measurements are wall-clock observations, not engineering velocity or a
claimed speed-up.

Stable review/evidence references:

| Record | SHA256 |
| --- | --- |
| V2 independent review | `1b42bb32c6852c1179d69217d5ca68da50cbc4bc604e0a16a0eb7d3d1f046c40` |
| Native independent review | `8bedae12fed219046ae961c62f7bdc7528d7cef7f83bd09f08e971bf3ea48e3d` |
| Lead workspace/v2 mutation results | `eb80e5ca69b773663ce4527bc5c3e9f337d5a23364b507916646102106b66bf0` |

Fresh frontend dependency installation, checking, frontend40/schema55 tests and
production build pass; log `es-p2-integrated-frontend-20260910.log` SHA256
`2ea9b319ac1a757c90c9cc722e436d40f8f826a3720eba6799a796ba53e22099`.
The first combined checkout run encountered duplicate parser tests under erroneous
`src.test.java` package names. A clean run passed the seven parser tests but later
encountered server bytecode throwing unresolved-compilation errors, consistent
with concurrent IDE build output. Both failures remain recorded. An isolated
archive of the exact thirteen reviewed files over the same base is used for final
Java verification; no source correction or test suppression is used to avoid the
shared build artifacts.

Final isolated `mvn -B -ntp -f backend/pom.xml verify` passes1,563 tests:
330 core,7 parser,911 server,315 supervisor; zero failures/errors/skips,
assembly and hostile-environment distribution verification,10 September2026
at11:23:05BST. All thirteen source hashes match both the checkout and isolated
archive after verification. Full log `es-p2-integrated-full3-20260910.log` SHA256
`c220eba70944af33e608f594e5a92d73a3de6b4f5f424dbe2ea31cbad6fc5e70`.
Whole staged provenance/content review, repository integrity, Python11 and diff
checks pass. This closes the three findings for this candidate; current compiler,
complete operator journey, native clients, combined resources and release gates
remain open. The existing8ad1e6a image predates these corrections and is not proof
of their OCI result; refresh the next coherent application candidate.
