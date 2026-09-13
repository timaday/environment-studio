# D07c3 — private fork capture prerequisite

Status: independently reviewed and locally integrated. No installed
runtime admission, production JNI bridge, launcher wiring or registry entry is
added. The ordinary registry stays empty; no native database authentication ran.
This is local development proof, not HiveGate governance evidence.

## Fixed scope and provenance

Author base: `c4529e429a433a4a1d19ee7535c767472553e80d`.
The owned external archive is `/home/tim/.tmp/es-privacy-fork-ypizpz4x`.
The ten-file source manifest is
`/home/tim/.tmp/es-privacy-fork-source-20260909.sha256`, SHA-256
`2bc255cdcabb23e64e88e0813ce8562fe380bdbc08de4907e5b5df98a16b71de`.

The reviewed [private ABI](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md)
and [crash-privacy contract](../contracts/guarded-crash-privacy-v1.md) govern this
slice. Added `privacy-fork.c/.h`, two test-only native probes and two Java test
classes. A separately authorized narrow `privacy-peer.c/.h` extension adopts an
already kernel-delivered pidfd and reuses its bounded identity validation; its
existing tests were extended. No duplicate proc parser or numeric PID acquisition
was introduced. All markers, IDs, commands and processes are independently
invented, credential-free test material. Shared runtimes and database labs were
untouched. No compiled artifacts belong in the checkout.

## Implemented boundary

A fresh native object arms one serialized platform-thread fork window. Its
unnamed nonblocking CLOEXEC SEQPACKET pair carries exactly `ESFORK01` plus the
16-byte launch identifier. Fixed atfork handlers use initial-exec TLS. The child
sends once, closes both inherited endpoints and returns to exec preparation;
failed/short/interrupted send or uncertain close exits 125. The parent handler
closes its sender once and publishes lock-free atomic status. Arming checks
lock-free atomics as well as compiling only the required atomic representation.

The receive owner requires one exact kernel SCM_PIDFD and SCM_CREDENTIALS,
matching bytes, complete ancillary delivery and subsequent EOF. Extra packets,
SCM_RIGHTS, duplicates and truncation refuse. Rejected ancillary descriptors are closed once; duplicate numeric entries cannot
close a reused number. An already adopted pin remains owned until explicit close
after a later protocol refusal, preserving cleanup identity.
Borrowed cancellation remains unconsumed and open. Invalid live state ordering is
sticky. Armed handlers keep their descriptor numbers quarantined; close cannot
release them until the launcher disarms. Only capture may overlap disarm; storage
release and close wait for disarm completion under caller synchronization.

The original absolute monotonic deadline has at most ten seconds remaining.
No connection, retry or capture renews it. Default SCM_CREDENTIALS real IDs must
agree with the non-set-id launch's effective IDs before arm. UID/GID zero are
valid. The adopted pin validates exact kernel PID/UID/GID, positive start ticks,
CLOEXEC and liveness through the existing peer helper. Ownership transfer uses
an `int *`: invalid preconditions preserve the caller's pin; after consumption,
the slot becomes -1 and later refusal owns cleanup.

CAPTURED is only retained process-ownership evidence. It does not establish the
exact Java Process wrapper binding, executable image, ancestry, constructor
privacy, trusted installation or ROOT_REGISTERED state. Self suppression and
qualified Java settings remain caller prerequisites. Direct C controls isolate
primitive behavior; the actual Java FORK controls establish self suppression first.

## Actual tests and corrections

Pinned Maven 3.9.16, OpenJDK 21.0.12 and Ubuntu GCC 13.3.0 were used. Native flags
include C17, O2, Wall/Wextra/Werror, pthread, stack protection, FORTIFY=3 and
RELRO/NOW; probes use PIE, the test JNI library uses PIC/shared. Compilation is
inside owned 0700 scratch directories; successful test cleanup removes its files.

| Control | Actual result |
| --- | --- |
| Kernel-pin adoption against refusing stub | RED: 1 assertion, 0 errors; then all original 18 peer tests plus adoption controls passed |
| Initial fork/cancellation/deadline against refusing stub | RED: 2 assertions, 0 errors; then GREEN |
| Sender close uncertainty during setup | RED: 1/5 fork tests; cleanup short-circuit had skipped receiver close; two independent close attempts fixed it |
| Duplicate delivered descriptor with actual number reuse | RED: 1/11; deduplicated close set fixed it |
| Read before capture followed by capture | RED: 1/15; live invalid ordering now latches refusal |
| Final focused reactor selection | PASS: 45 tests — 17 fork, 21 peer, 4 core, 1 parser, 2 server |
| Final full reactor verify | PASS: 628 tests — 150 core, 7 parser, 361 server, 110 supervisor; assembly and hostile-launch checks passed |
| Repository integrity | `python3 scripts/check_repository.py`: PASS |

Setup failures are separate: the first Maven selection omitted a required server
selection and stopped before the new native tests; compiler warning failures
involved indentation and a test signedness comparison. No setup failure is
counted as semantic RED. An initial dead-sender fixture killed before the child
could send; the corrected fixture waits for actual packet readiness before killing.
The final dead-sender refusal then passed. Earlier full runs passed 624 and 625
before the last tests were added; 628 is the final candidate result.

Actual controls include child capture, parent and both child endpoint closure
before exec, wrong-thread disarm, active-generation rearm refusal, missing hook,
post-arm cancellation, maximum deadline, stalled capture, dead sender, uncertain
parent close, malformed correlation/ancillary data, real SCM_RIGHTS and real
ancillary truncation. Descriptor counts return to baseline after hostile receipt
cleanup. Sixteen capture/disarm overlaps and sixteen concurrent cancellation
controls run per focused/full test invocation. Adoption controls cover transfer,
non-transfer, aliases, zero initial start requirement, wrong UID/GID/PID,
non-pidfd, missing CLOEXEC, dead/cancelled pin, reinitialization and uncertain close
with descriptor reuse. The original peer suite remains intact.

Actual isolated Java tests run the installed JDK with fixed FORK and establish
self suppression before arming. The exact returned child PID matches the captured
pin; invented merged stdout/stderr stays byte-identical. POSIX_SPAWN produces no
capture and is refused; a failed Java exec produces dead-capture refusal and
owned cleanup. These test JNI methods are not the production bridge or a proof
of its future Java Process provenance checks.

## Mutants, syscall and generated-code evidence

Nine final-source mutants were killed by assertion exits, with no mutant compiler
failures: omitted parent close, omitted child receiver close, omitted nonce check,
omitted EOF, skipped cancellation, omitted duplicate-FD protection, extended
maximum deadline, non-sticky invalid ordering and skipped ancillary FD cleanup.
They ran in independent external copies, not the author archive.

Final owned proof lab: `/tmp/es-fork-evidence-alsu9g7k` (0700).
`trace-artifacts.sha256` SHA-256:
`ea6ad280cb90d1d73a048a3b77a626d30a12df23ea1a2b7b7aab8e0bdc310b03`.
The unwrapped JNI library's child hook calls only prebound send, close and _exit;
the parent hook calls only close. Generated hooks contain no dynamic TLS lookup,
stack-failure helper, allocation, locks or JNI calls. ELF BIND_NOW was verified.
Actual FORK tracing shows one 24-byte send, immediate sender/receiver closes and
one child exec. POSIX_SPAWN shows two child execs and no capture send. The first
POSIX exec pathname is unreadable after dumpable zero; it is not claimed as a
decoded helper-path witness. Initial Java exec is excluded from those child counts.

Nonsecret author logs are under `/home/tim/.tmp/`:
`es-privacy-fork-adopt-red2-20260909.log`, `es-privacy-fork-red-20260909.log`,
`es-privacy-fork-adverse-red2-20260909.log`,
`es-privacy-fork-duplicate-red-20260909.log`,
`es-privacy-fork-order-red-20260909.log`,
`es-privacy-fork-final-focused3-20260909.log`,
`es-privacy-fork-full3-20260909.log`,
`es-privacy-fork-final-mutations-20260909.log` and
`es-privacy-fork-repository-20260909.log`.

## Remaining gates

No production Java wrapper, listener/coordinator, launch graph, image/loader
inspection, per-exec constructor handshake or final admission is implemented here.
The caller must correlate the captured pin with its exact returned Process while
both remain live. Failed/stalled fork, ENOMEM/exhaustion, real same-UID interference,
JVM shutdown, arbitrary signal delivery, handler ordering under the complete fixed
runtime and maximum standalone fork-memory overhead are not fully qualified.
Cooperative checks do not interrupt a kernel-stalled syscall. The tested overlaps
are bounded examples, not a proof of every schedule. Native cleanup is not whole
process/pipe/terminal cleanup and cannot erase uncertainty. No installed native
client, orapki chain, crash collector or database authentication was qualified.

## Independent integration

The fixed eleven-file candidate manifest is
`/home/tim/.tmp/es-privacy-fork-candidate-20260909.sha256`, SHA-256
`823225bf202883031cb41dfe8fe0089f1bb0a55d3c390316f689bc9a88239776`.
Root reviewed the fixed code/test/evidence delta, accepted its ownership boundaries
and verified every candidate hash before integration. The author archive remains
unchanged.

Four independently written, unwrapped native controls passed: 24 sequential fresh
capture generations, cancellation after capture with the borrowed event still
readable, child death after capture, and null read after capture. They checked
sticky refusal, zeroed output, owned-pin closure and repeated close after descriptor
reuse. Source: `/home/tim/.tmp/es-privacy-fork-independent-20260909.c`.

The fresh `3b2a87e7014ca5cfb8587e60e94aa80e8878af61` archive plus this exact
candidate passed the full Java643 reactor: core161/parser7/server365/supervisor110,
including assembly/checksums and hostile launch checks. Integration tree:
`/home/tim/.tmp/es-privacy-fork-integration-20260909`; log:
`/home/tim/.tmp/es-privacy-fork-integrated-full-20260909.log`.
No production registry or launcher was enabled by these results.
