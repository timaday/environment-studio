# IDE 2 native handoff — 10 September 2026

**Ready for IDE 2 to take the existing JNI candidate.** Lead remains the sole
application writer in IDE 1. Both previous coding subagents have checkpointed,
stopped editing and relinquished their assignments. The existing non-author JNI
reviewer is read-only. There may be at most two writers across both IDEs, one per
IDE, counting the lead; do not start additional writing subagents.

| Item | Exact assignment |
| --- | --- |
| IDE 2 worktree | `/home/tim/IdeaProjects/environment-studio-native-ide2` |
| IDE 2 branch | `implementation/native-supervisor-ide2` |
| Starting commit | `d7c484425a3dcc88e26525a7e5041f41c83d056d` |
| Lead checkout/branch | `/home/tim/IdeaProjects/environment-studio`, `implementation/d01-definition-compilation` |
| Native candidate base | `bd30c53456a1e6410bd4c8e2308127f9fc97c03d` |
| Carried work | Exact21-file candidate1 applied as **uncommitted WIP**, all hashes verified; no duplicate implementation |
| First task | Reproduce and fix the confirmed late-open deadline P2; complete the fixed JNI ownership review and return one bounded candidate |

Read root/nested instructions, README, requirements, build plan, progress,
repository-content policy, quality gates and the contracts below. The starting
commit already integrates reviewed profile capture/reuse and paged validation:
Java1599/frontend40/schema58 pass. Do not repeat those application slices.

## Preserved candidate and current review

Original source: `/home/tim/.tmp/es-jni-ownership-author-20260910`.
Patch: `/home/tim/.tmp/es-jni-ownership-candidate1-20260910.patch`, SHA256
`d180c4b45a730e133a4390c748aa28fa700cc420a8fcefc96b333436c57b8997`.
Exact file list and hashes: same prefix `.files` and `.sha256`; manifest SHA256
`9e4ae7b53b4a81833f543a631b0448ffdedaa36a9f35ad126991be9f8c2c1955`.
Author evidence: `/home/tim/.tmp/es-jni-ownership-checkpoint-20260910.md` and its
linked logs/mutations. Preserve these originals; use new outputs for corrections.

Author full1596 and distribution checks pass on the original base;17 bridge tests
and six compiled mutation controls ran. This is **implemented, not independently
accepted, integrated or release-qualified**. The initial RED proves opening only;
its original scaffold source/full command was not preserved. Do not claim a
complete immutable FORK RED record. No author processes or containers remain.

Independent review confirmed that `openLaunch` can return `Opened` after its
original deadline: hold JNI `NewObject` beyond a100ms allowance for250ms, then
release it; the candidate reports `EXPIRED_OPEN_PUBLISHED=true` and STARTING.
The existing registry status/publication path does not recheck that deadline.
Reproduction: `/home/tim/.tmp/es-review-jni-late-open-3gi567oe/commands2.json`,
`PrivacyBridgeProbe.java` and `result2.log` (exit41). The earlier `result.log`
used an ineligible parent directory and is a setup failure. The completed bounded
review is `/home/tim/.tmp/es-review-jni-candidate1-handoff-20260910.md`: independent79
tests/distribution and256-token cleanup controls pass, with this P2 still open.
No reviewer processes remain. Obtain a fixed delta review after correcting it.

## File ownership

Only these **19 implementation/test files** beneath
`backend/tools/guarded-supervisor/` are assigned to IDE 2 for this task:

- `src/main/c/`: `privacy-compiled.c`, `privacy-compiled.h`, `privacy-jni.c`,
  `privacy-launch.c`, `privacy-launch.h`, `privacy-registry.c`, `privacy-registry.h`.
- `src/main/java/studio/environment/supervisor/`: `PrivacyBridge.java`,
  `PrivacyLaunchCoordinator.java`, `PrivacyLaunchOwner.java`.
- `src/test/c/`: `privacy-compiled-fixture.c`, `privacy-registry-fixture.c`.
- `src/test/java/studio/environment/supervisor/`: `PrivacyBridgeLinkageProbe.java`,
  `PrivacyBridgeProbe.java`, `PrivacyBridgeTest.java`, `PrivacyLaunchImageProbe.java`,
  `PrivacyLaunchOwnerTest.java`, `PrivacyNativeLaunchProbe.java`, `PrivacyRootProbe.java`.

The two carried document modifications, `docs/privacy-abi-v1.md` and
`docs/privacy-jni-ownership-v1.md`, contain the lead-authorized original-clock
clarification. Preserve their candidate bytes; they are **reserved for the lead**.
All other files are reserved, including Main, RuntimePrivacy, Admission, execution
engines, distribution assembly, every POM/build script, application/core/server,
frontend, schemas/contracts, CI, Docker and delivery/evidence metadata. Report a
needed shared change with its reason; the lead resolves it before dependent work.
No new file or next identity/admission implementation is implicitly assigned.

## Contracts and acceptance

Use the carried `privacy-abi-v1.md` and `privacy-jni-ownership-v1.md`, plus
`docs/contracts/guarded-crash-privacy-v1.md` and the existing launch/root/fork
contracts they reference. Preserve the nine JNI signatures and the lead-authorized
`es_launch_open_started` helper with both original native clocks.

First reproduce the late-allocation failure using the actual production JNI and
registry with the declared test allocation hold. Add its meaningful regression,
then fix the smallest ownership boundary. Expired work must not publish an Opened
token or renew either deadline; cleanup must be conclusive or explicitly
inconclusive. Cover held/result-allocation failures, cancellation/late references,
repeat close, invalid tokens and descriptor reuse. Preserve actual FORK/root
correlation, native contention recovery, affirmative no-window handling, four-live/
256-issued limits and settled tombstones. Keep production compiled tables empty,
the required INSTALLATION refusal and no CHALLENGE/FinalAdmitted.

The next mapped-image/loader stage remains separate and needs a settled shared
contract. Preparation is `/home/tim/.tmp/es-native-mapped-closure-next-slice-20260910.md`.
Equal maps/file hashes are not mapped-byte identity or loader qualification.

## Checks and resource allocation

Use JDK21 and Maven `/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn`.
Run focused bridge/launch/root controls in the separate worktree or an exact-source
archive if IDE compilation touches targets. Include actual core/parser/server
controls when using `-am`; do not disable the no-tests gate. Full candidate gate:
`mvn -B -ntp -f backend/pom.xml verify`, including distribution checks. Run G00
provenance/staged-content, repository/Python checks before a candidate commit.

IDE 2 owns the **next expensive full Maven gate**; IDE 1 runs frontend checks and
defers OCI/full-backend builds until this native candidate is returned. The existing
reviewer uses separate outputs and focused tests only. Keep Maven targets separate.
New native scratch/logs belong under `/home/tim/.tmp/es-ide2-native-20260910/`;
trust-sensitive fixtures use their own fresh0700 directories beneath `/tmp`.
Use only self-created unique namespaces and ephemeral loopback ports. No database
or container is assigned for this first task; do not reuse older DB containers,
ports, volumes or the author's retained fault/setup directories. Actual DB/client
qualification gets a separate resource assignment. Do not prune or delete others'
resources. No credentials/private application inputs enter checkout/build context.

## Return and checkpoint

Return a fixed commit on this branch after G00, with base, exact changed files,
RED/GREEN commands/results, all substitutions, remaining findings and test-resource
cleanup. Keep the inherited candidate and your correction distinguishable in the
report. If still WIP, return a frozen patch plus file hashes and the exact remaining
acceptance check; do not call it accepted. Do not push, merge into the lead branch,
rewrite history or change shared contracts. Lead owns non-author review, integration
and final combined gates.

Write the short return at `/home/tim/.tmp/es-ide2-native-return-20260910.md` and
report its path in IDE 2. This is a handoff artifact, not a coordination service.
First two-hour checkpoint: **14:49 BST,10 September2026**. Report each lane's
candidate or exact remaining acceptance check, blockers and next action. The lead
continues the preserved typed-v3 browser client in IDE 1; native authoring stays
exclusively with IDE 2 until returned or explicitly reassigned.
