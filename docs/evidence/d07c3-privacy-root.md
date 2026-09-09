# D07c3 root correlation — local prerequisite evidence

Base 5fa6695c3885e1b09dade70183513b3d225fd427. Final author archive `/home/tim/.tmp/es-privacy-root-scope-z6_6041s`; six-file manifest `/home/tim/.tmp/es-privacy-root-candidate2-20260909.sha256`, SHA256 36a45ac0aa3a3be9929cabba6abf6da74e39b0250de7e013d6955e67fcabe71f. Inherited, lead-owned ABI supplement SHA256 276f3f677e57b3e3b63150fbb776e19bc8c83c5cef7d72e81ab4aad8517b8bd3 is not an authored source delta. All earlier archives/candidate manifests remain unchanged.

Six new C/header/test files implement the lead-owned [private ABI supplement](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md). Production JNI, launcher, primitives, registry, build and client authentication remain unchanged. Result ROOT_CORRELATED establishes process correlation at checked boundaries; image/ancestry identity, self-suppression and runtime admission require later qualification.

## Implementation

The owner retains one existing fork capture, immutable launcher/generation/startup controls, receiver-serialized state and separate atomic disarm completion/failure. It registers only the supplied PID from the actual Java wrapper after live capture; C cannot independently certify Java provenance. Successful launcher disarm precedes the one borrowed connection match. Existing fork and connection reads retain/recheck both independently acquired kernel pins; exact PID/start/UID/GID must agree before and after comparison. Listener, peer and wire must share the exact original cancellation descriptor and startup deadline. The root neither duplicates nor closes the borrowed connection/socket/pin.

Disarm may overlap capture under the existing split ownership. A completed failed arm may report an ended window only from its recorded positive own generation and armed-zero return; fresh/busy/preinitialization/wrong-thread/generation/duplicate attempts refuse. Ending TLS cannot erase an earlier correlation failure or cleanup uncertainty. Original startup and independent cleanup budgets remain bounded; still-armed cleanup quarantines the owner until disarm. Complete cleanup tombstones survive repeated calls. Output aliases are refused before writes.

## Actual runs

Pinned Maven3.9.16, Java21.0.12, GCC13.3.0. Logs are external `/home/tim/.tmp/`.
Focused command: `mvn -B -ntp -f backend/pom.xml -pl tools/guarded-supervisor -am -Dtest=NativeModelTest,MinimalRuntimeTest,HostedPlanApplicationTest,PrivacyRootTest -Dsurefire.failIfNoSpecifiedTests=false test`. All selected modules contain nonzero controls. Full command: `mvn -B -ntp -f backend/pom.xml verify`.

- es-privacy-root-red1.log: one intended assertion failure, zero errors against PLATFORM scaffold; five unchanged core/parser/server controls pass.
- green1: six pass. adverse1: twelve pass. adverse2: test JNI compilation setup failure (signed ternary and misleading indentation), not behavioral RED. adverse3: fourteen pass. adverse4: seventeen pass.
- es-privacy-root-failed-arm-red.log: one assertion/zero errors against conservative failed-arm disarm. failed-arm-green: seventeen pass after the reviewed ended-window rule.
- es-privacy-root-final-focused.log: nineteen pass before final alias assertion strengthening.
- es-privacy-root-full1.log: earlier twelve-root-test candidate passes825 (214core/7parser/440server/164supervisor); full2: original frozen candidate1 passes827 (214/7/440/166). These do not close the subsequently discovered scope flaw.
- Lead independently reproduced same-process connection with foreign eventfd returning CORRELATED despite root cancellation. es-privacy-root-scope-red.log: one JUnit assertion/zero errors; independent actual foreign-cancel and foreign-deadline C controls both exit40 with assertions, recorded in es-root-scope-red-controls.json.
- es-privacy-root-constructor-red.log: two selected root cases, one assertion/zero errors. New before-main output witness rejects the previous main-only PREPARE sender with exit46; foreign-scope correction already passes.
- es-privacy-root-scope-green.log: twenty pass after actual test constructor synchronization.
- es-privacy-root-final2-focused.log: twenty-one pass (16 root families +2core+1parser+2server). Native families invoke44 distinct C modes, with disarm/capture overlap repeated8 times (51 C invocations), plus three separate actual Java FORK/POSIX_SPAWN/failed-exec JVM probes.
- es-privacy-root-full3.log: final exact candidate2 passes829 (214core/7parser/440server/168supervisor), zero failures/errors/skips; distribution assembly and hostile launch verification pass.

Actual Java tests use PrivacyLaunchOwner.CapturePort and the exact internally returned Process. Reflection inspects that Process only to assert output; it never supplies or substitutes one. A test-only ELF constructor connects and sends PREPARE before main, blocks until the owned parent releases the connection, then main emits exactly INVENTED-OUT and INVENTED-ERR. Before release the pipe has no bytes. This invented fixture uses ES_ROOT_TEST_ENDPOINT and EOF solely as test synchronization; it is not the production privacy constructor/ACK protocol. POSIX_SPAWN and failed exec cannot register. No secret-consuming child runs.

Tests include wrong/zero/wide PID, unrelated same-UID process, inherited socket creator, each identity component, death before/during matching, missing hook, invalid ordering/thread/generation, optional overlapping capture/disarm with late-registration refusal, deadline/cancellation, borrowed eventfd preservation, null/overlapping outputs, foreign and internally inconsistent controls, and sticky cleanup. After actual pidfd close, test-owned /dev/null is forced onto that exact former descriptor number with dup2, equality is asserted, and repeat cleanup must leave it live. No live socket/pidfd is duplicated. Earlier candidate1 tests checked another replacement descriptor without forcing exact pidfd reuse; final evidence closes that limitation.

## Mutation and independent review evidence

Final separate archive `/home/tim/.tmp/es-root-final-mutants-fra0nf_z`: nine compiled native mutants all exit40 on actual assertions, with no compile failures or crashes: returned PID, full identity, disarm gate, sticky refusal, both post-comparison pin reads, owned root close, output aliases, failed-arm ended-window and shared launch scope. Original candidate1 mutation run had one unused-parameter compilation setup failure; corrected rerun killed that mutation. All final9 compile cleanly. No frozen source is mutated. All six hashes were verified after restoration; clean native live/foreign-scope/uncertain-close controls pass. Final unwrapped shared test library disassembly records child hook calls send/close/close/_exit and parent hook close, with direct TLS and no __tls_get_addr; hook-call-review.json and unwrapped-objdump.txt retain this bounded artifact observation.

The lead's unchanged `/home/tim/.tmp/es-root-independent-iagv0c1_/foreign-control-probe.c` compiles against final source and passes with ROOT_RESULT_4 (INVALID), exit0; result `/home/tim/.tmp/es-root-foreign-control-corrected.log`. Its original candidate1 run returned3 and failed. Shared-control rejection now precedes the injected late cancellation; a separate same-control late-cancellation test returns CANCELLED.

## Independent integration and remaining qualification

The lead independently reviewed all six frozen files, the correction against the
first candidate and the ABI, then verified hashes before integration on
`6fd4c05a8d3d792fa09592ea7b8a55329f4cbd0c`. The original foreign-control witness
also passes a separate lead rerun against corrected sources, along with live,
death-during-match, wrong-UID and cancellation controls. Log:
`es-root-independent-corrected-root-20260909.log`; all five exit zero. These use
actual local processes/kernel pins with narrowly injected timing/adverse conditions.

Review required the shared startup-control check, a real before-main constructor
witness and exact closed-pidfd number reuse in cleanup tests. The earlier green
reactor did not establish these properties. Business review preserves disabled
native execution until complete privacy qualification. Engineering review checks
thread ownership, immutable launch controls, borrowed lifetimes and sticky failure.
QA/RST distinguishes real child creation/constructor ordering from protocol and
executable admission that are still unfinished. There is no measured speed-up
claim; review correction and integration time are part of this work.

The combined XML/root candidate on `6fd4c05a8d3d792fa09592ea7b8a55329f4cbd0c`
passes the complete pinned Maven reactor: **844 tests**, 214 core, seven parser,
455 server and 168 supervisor, with zero failures/errors/skips. Distribution
checksum and hostile launch checks also pass. Archive:
`es-xml-root-integrated-ndw183av`; log:
`es-xml-root-integrated-full-20260909.log`; 2m22s, finished 17:42 BST.
The source delta is exactly the six-file manifest and ABI hash above.
The lead's independent controls, source review, corrected native integration and
combined verification finished between approximately 17:34 and 17:42 BST.
This does not refresh the earlier OCI/browser/heap or actual-client evidence.
Repository integrity, staged-content guard, 11 Python guard tests and diff checks
pass after complete staged-diff provenance review. No GitHub upload occurred.

No production token registry, trusted library installation, parent self-privacy admission, image/loader/script identity, descendant chain, full challenge/receipt/ACK coordinator, complete process-tree cleanup, native client or DB qualification follows. CapturePort remains unconnected outside tests. The first-root correlation borrows caller-serialized connection lifetime and admitted namespace; same-UID namespace safety remains an upstream unqualified prerequisite. Checked deadlines cannot interrupt a stalled kernel syscall. Existing fork hook code is unchanged; test builds inspect its fixed TLS/call shape, not universal installed-runtime closure. Java blocked-port scheduling and native capture cancellation are not proof of arbitrary stalled ProcessBuilder behavior.
