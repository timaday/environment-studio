# D07c3 Java launch ownership prerequisite

The independently reviewed Java owner binds native pre-exec capture to its exact
internally created Process. Review reproduced and corrected a cleanup caller that
kept waiting against an older deadline after another caller shortened it. The
corrected candidate passes 700 Java tests on the integrated branch. This remains
an unconnected prerequisite: the production native bridge and runtime registry
are not enabled.

## Contract and acceptance

The existing [private ABI](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md)
requires fixed JDK FORK, private kernel sender capture and exact Java root ownership.
`PrivacyLaunchOwner` adds no public API. Fixed, admitted, credential-free launch
inputs and one installed supervisor class loader are prerequisites; an arbitrary
implementation of its package-private CapturePort is not trusted evidence.

The owner copies inputs, clears inherited environment and calls ProcessBuilder.start
once on a dedicated platform thread. Arm, registration of the exact returned live
Process PID, and finally-disarm run on that thread. A static gate admits one armed
window without a waiting launcher queue. Failed or unreturned disarm retains the
gate as quarantine. No Process, pipe or credential is returned to callers.

Startup and cleanup use original absolute monotonic deadlines, at most ten seconds
away. Repeated cleanup may shorten its shared budget. Cancellation and cleanup have
owned workers so a faulty native port cannot indefinitely block the calling thread.
Uncertain or timed-out cleanup stays INCONCLUSIVE. A late launcher makes a best-effort
termination of its exact Process after disarm; uncertain native close is not retried.
Quarantined native resources may require eventual invocation teardown.

Acceptance examples cover a live captured child, refused or failed exec, serialized
arm windows, same-thread disarm, cancellation, original startup expiry, late disarm,
copied inputs, inherited environment removal, failed-disarm quarantine and shortened
cleanup. Test-port stalls investigate scheduling; they do not qualify an actual
kernel-stalled ProcessBuilder.start.

## Actual evidence

Pinned Maven is `/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn`,
with installed OpenJDK 21.0.12. All Maven invocations below used that full path.

| Observation | Result |
| --- | --- |
| Refusing stub after correcting masking finally assertions | RED: 2 assertions, 0 errors; missing registration and arm/disarm sequence |
| Registration after original deadline elapsed during disarm | RED: 1 assertion, 0 errors; final publication now rechecks the original deadline |
| Original focused selection | PASS: 23 tests, including 16 owner cases |
| Original full reactor | PASS: 671 tests and distribution checks |
| Independent cleanup shortening control against original source | RED: 1 assertion, 0 errors; earlier close caller still waited after shared shortening |
| Corrected focused selection | PASS: 24 tests, including unchanged independent regression |
| Corrected author full reactor | PASS: 672 tests and distribution checks |
| Exact corrected candidate on latest `d7894a3` archive | PASS: 700 tests — core167/parser7/server387/supervisor139; assembly/checksums and hostile-environment launch passed |

The independent regression blocks CapturePort.close behind a latch, starts a
three-second close, then shortens its budget to thirty milliseconds. The original
caller was still waiting after 250 milliseconds. The single production correction
replaces the caller's captured-deadline wait with polling of the shared deadline;
native close remains one attempt and outstanding work remains INCONCLUSIVE.

Actual full integration command was `mvn -B -ntp -f backend/pom.xml verify`, from
`/home/tim/.tmp/es-privacy-launch-integrated-20260909`. It completed in 2m15s.
Log: `/home/tim/.tmp/es-privacy-launch-integrated-full-20260909.log`.
Independent RED log:
`/home/tim/.tmp/es-privacy-launch-independent-cleanup-red-20260909.log`.
Focused selection included HostedPlanServiceTest, FifthEditionClassifierTest,
HostedPlanApplicationTest, PrivacyLaunchOwnerTest and IndependentLaunchCleanupTest;
the selected modules all ran tests.

Six original isolated guard mutants were killed by assertions with zero errors:
armed-window gate, registration, disarm, post-disarm lifetime, inherited environment
and native cleanup refusal. A seventh mutant restoring the captured caller wait
was killed by the unchanged independent regression, also one assertion and no errors.
These are seven distinct guards; unrelated native matrices were not rerun.

The corrected wrapper's immutable class copy was also tested with an external JNI
probe using the unchanged controls/peer/fork primitives. Actual Java FORK captured
the live kernel sender matching the internally returned sleep Process, reported
REGISTERED and completed cleanup. Actual POSIX_SPAWN supplied no capture and was
refused at the native deadline. Both owned JVMs exited zero under a 15-second
external timeout. No fallback or credential input was used. The minimal probe's
empty cancel method does not qualify native cancellation.

## Fixed candidates and review

Corrected archive: `/home/tim/.tmp/es-privacy-launch-corrected-ne1_74_4`, based on
`41a17cc57af6047eb53eecccead57f771dbc1e7b`. Its exact three-file manifest is
`/home/tim/.tmp/es-privacy-launch-candidate2-20260909.sha256`, SHA-256
`661b080cb95119a20cab017a7c7626661b43a7b182e3acbc054fcdb2e5caad17`.
The original author archive remains unchanged. Root read the complete production,
test and probe sources, reproduced the finding independently, reviewed the
correction, and verified that the integration archive used all three exact hashes.

Original evidence manifest:
`/home/tim/.tmp/es-privacy-launch-evidence-20260909.sha256`, SHA-256
`cbc1b17cb19f4e36a09338eb7f2f552efdaa94c8ba5dcd3c2460f7d37ced6cd4`.
Correction evidence manifest:
`/home/tim/.tmp/es-privacy-launch-correction-evidence-20260909.sha256`, SHA-256
`3788b79cc94d253134883ea90853f5856c9d23e3cbbf0ea817544f1b31c7437e`.
All 17 original and 21 correction evidence hashes were verified. The corrected
native probe, source, classes, library, commands and safe-code results remain in
`/home/tim/.tmp/es-launch-corrected-native-9hb_o0wu`.

Business: no supported runtime is advertised from this prerequisite. Engineering:
ownership cannot be supplied as an arbitrary Process/PID; only the future admitted
port can establish native authority. QA/security/operations: adverse schedules and
mutation controls support the bounded behavior, while complete process-tree cleanup
and privacy remain separate gates. There is no serial baseline for a speed-up claim.

## Remaining work

Production JNI/coordinator wiring, exact installation/image/ancestry checks,
constructor handshake, hostile same-UID interference, actual stalled fork/ENOMEM,
JVM shutdown races, authenticated native-client TLS/transcripts/COMMIT and complete
invocation teardown remain unqualified. Existing OwnedNativeProcess adoption is
also separate. The runtime registry stays empty. The exact-source OCI/browser
artifact remains `c4a3246`; it predates this unused supervisor prerequisite.
All results are local development evidence, not release or HiveGate authority.

Before commit, root reviewed the complete staged diff for provenance and cumulative
disclosure: only generic process controls, independently invented test inputs and
aggregate evidence are included. Repository integrity, staged-content guard,
`git diff --cached --check` and all 11 Python tooling tests passed. No frontend
behavior changed; the latest frontend and OCI results retain their exact c4a3246
scope. No GitHub upload or new CI result is claimed.
