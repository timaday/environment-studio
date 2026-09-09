# Private crash-privacy ABI v1 — candidate, unimplemented

This specifies the planned boundary in
[guarded crash privacy](../../../../docs/contracts/guarded-crash-privacy-v1.md).
It grants no runtime qualification and changes no public command/configuration.
Only the standalone supervisor may use this private ABI. The compiled registry
remains empty. All examples, fault probes and evidence must be independently
invented and credential-free until the separate privacy gates pass.

## Installation and launch prerequisite

The distribution fixes one Linux amd64 guard/JNI library, its absolute trusted
path, SHA-256, ABI, compiler/libc/JDK identity and closure in its compiled record.
Loading it follows trusted-file admission; no peer, package, configuration or
inherited environment chooses its path. JNI methods are package-private native
methods of `studio.environment.supervisor.PrivacyBridge`, registered by exact
name and signature. No general native loader, descriptor, process or command API
is exposed. The web application never loads the library.

The standalone launcher must set
`-Djdk.lang.Process.launchMechanism=FORK` before `ProcessImpl` initializes.
Admission verifies the exact pinned JDK and fixed setting. Property text alone
is insufficient: qualification must witness the actual fork/exec chain. There
is no POSIX_SPAWN, VFORK or helper fallback. A failed fork, including ENOMEM,
refuses with owned cleanup; standalone memory qualification measures fork cost.
The default JDK POSIX_SPAWN path executes `jspawnhelper` with the JVM environment
before applying the configured child environment. It is not silently exempted
from the per-exec boundary.

The only private child control environment name is `ES_PRIVACY_CONTROL`.
The standalone launcher clears any inherited value before starting the supervisor
JVM. Direct Java invocation must verify the variable is absent before library
loading; a supplied value refuses. Loading the fixed library in that parent
with the variable absent performs no constructor handshake and grants no privacy proof: the explicit JNI
`establishSelf` call, after fixed installation admission, is the sole parent
self-admission path. There is no separate mode flag and no constructor fallback
to self-admission.

Every directly owned child launch builds its environment from empty state,
sets `ES_PRIVACY_CONTROL` to exactly its native-owned socket pathname and sets `LD_PRELOAD` to exactly the
admitted fixed library. Descendants may inherit only that tool-owned environment
and transformations explicitly fixed in their compiled interpreter branch;
they cannot import caller values. A present but empty/malformed endpoint makes the constructor exit 125. A nonempty valid endpoint
selects the child handshake and must not invoke the parent self-admission path.
An absent endpoint gives no constructor proof; if that process is an owned child,
its required receipt is missing and the parent refuses before ARM, credentials
or package input, even if the child otherwise runs or exits successfully. Absence
is therefore not a way to admit a child as a parent. Unknown/duplicate environment
entries refuse the fixed child-environment construction. No public invocation
can select a role, listener path, library or exception to receipt admission.

The native self-admission call establishes dumpable zero, NO_NEW_PRIVS and the
qualified TSYNC filter before any credentials are requested. It verifies reset
1 and reset 2 both fail with EPERM and dumpability remains zero. Existing and
future JVM threads must be covered. Failed/partial synchronization is a refusal;
no fallback to a per-thread filter. Fixed fatal-error, heap-dump and core-limit
checks remain mandatory and independent of the socket protocol.

The filter first requires audit architecture `AUDIT_ARCH_X86_64` (`c000003e`);
other architectures, including i386 compat entry, terminate the process with
SECCOMP_RET_KILL_PROCESS. For that audit architecture, syscall numbers carrying
the x32 bit `40000000` are refused with EPERM before dispatching any syscall rule.
Only then match native x86-64 prctl and deny PR_SET_DUMPABLE whenever either half
of its 64-bit value argument is nonzero. Alternate x32/compat syscall encodings
cannot bypass the reset guard. Qualification exercises those actual entry paths;
an unrelated kernel ENOSYS/EINVAL refusal is not evidence of the filter policy.

## Channel ownership and identity

A launch owns one AF_UNIX/SOCK_STREAM listener under a fresh 0700 directory;
the socket is 0600. Its basename is fixed `control.sock`. The entire pathname
is at most 103 UTF-8 bytes plus NUL, is not abstract, and has no symlink component.
The native owner creates the directory and socket relative to an admitted owned
parent directory. Java receives a bounded owned path solely to install the fixed
child environment. It is never a connection target supplied by a peer.

Use nonblocking native descriptors, CLOEXEC and a native poll/eventfd wakeup.
Java never receives a raw descriptor or reflects into JDK internals. The compiled
chain bounds concurrent pending peers to 1–16, including accepted-but-not-active
connections. Exactly one handshake is active. The listen backlog is bounded by
the same record; excess connection pressure refuses, rather than extending a
queue. Kernel backlog capacity is not evidence of an exact pending count:
qualification must establish the bound on the pinned kernel and test saturation.
No new peer is accepted after failure or cleanup starts.

Every exec creates a new connection. Forked children must not reuse a connection
or its challenge; the constructor closes its connection before returning, and
CLOEXEC supplies a second barrier. Credentials, package input and native output
never traverse this socket. The existing stdout/stderr protocol is unchanged.

On accept, obtain SO_PEERCRED PID/UID/GID from the kernel and obtain SO_PEERPIDFD
from that same accepted socket. This pins the socket's retained kernel peer
identity rather than looking up a potentially recycled numeric PID. Both options
must succeed with their exact result sizes; missing SO_PEERPIDFD support refuses
admission, with no `pidfd_open(SO_PEERCRED.pid)` fallback. Verify the returned
pidfd's CLOEXEC state, correlate its live kernel PID with SO_PEERCRED, and check
pidfd liveness before and after bounded process-identity reads. Keep the socket
and pidfd under single-owner native descriptor lifetime rules. A pidfd only pins
process identity; it does not establish executable identity or launch ownership.

Verify its start identity and live kernel parentage against the registered
owned launch root and compiled fork/exec graph. Keep the pidfd until cleanup; do
not reacquire a process by recycled numeric PID. Process start time is not an exec
generation: every connection receives a fresh ordinal and image inspection even
when PID/start time are unchanged. Reject ambiguous ancestry or a peer whose
parent exited before ownership could be established. No peer supplies trusted
PID, pathname, role, executable digest or chain identifier.

The socket-bound acquisition mechanism is documented in the
[Linux 6.8 socket implementation](https://github.com/torvalds/linux/blob/v6.8/net/core/sock.c)
and [UAPI](https://github.com/torvalds/linux/blob/v6.8/include/uapi/asm-generic/socket.h).
These source references are design evidence only. Qualify actual kernel support,
exited peers, missing support, cancellation, deadline and descriptor cleanup on
the intended runtime; never substitute a version string for that evidence.

While the PREPARE constructor is blocked and before suppression, inspect the
peer's executable, interpreter, loader, mapped guard and complete compiled
installation closure using native owned descriptors and bounded reads. Match
those objects to the exact next allowed node/branch of the compiled graph. A
script is checked as a script plus its actual interpreter; `/proc/pid/exe` alone
does not identify script bytes. While the constructor is blocked, read the peer's
actual NUL-separated `/proc/pid/cmdline` with a 16 KiB total bound, at most 128
arguments and 1024 bytes per argument. Require exact argument count/order/bytes
against the parent-owned typed launch node. For a script, the compiled record
fixes the exact shebang/interpreter shape and argument position containing its
absolute script pathname; open that script under trusted-file rules and match its
inode/device/content hash to the compiled script record. Match all interpreter
options and the remaining fixed or typed tool-owned arguments too. A different
script under the same interpreter, `sh -c`, stdin script, extra argument, unknown
shebang option or ambiguous argv representation refuses. A script's pathname
alone is insufficient; no claimed child path establishes script identity.

Typed dynamic arguments may only be the already-admitted endpoint/account/public
trust/control inputs allowed by the runtime contract; they are never new command
text or configuration inferred from the peer. Account-name visibility retains
its separate existing qualification/disclosure requirement. Compare actual argv
in bounded mutable memory and wipe it; never log or persist it. Recheck process
identity and exact script association before CHALLENGE.
Store a 32-byte digest of the accepted canonical identity record in memory.
The identity record is the ASCII domain `ES_PRIVACY_IDENTITY_1\n`, followed by
raw compiled runtime SHA-256 (32 bytes), compiled chain SHA-256 (32), node ordinal
u32, peer UID u32, GID u32, PID u32, kernel process start ticks u64, executable
st_dev u64, st_ino u64, executable SHA-256 (32), verified closure SHA-256 (32),
and guard SHA-256 (32). Integers use the wire's big-endian encoding. Hash exactly
those bytes; never JSON, display strings or a peer-provided record. All metadata
comes from the pinned peer/file descriptors. Peer PID, exec ordinal,
node/object ordinal and kernel process start ticks must be strictly positive.
UID, GID, device and inode metadata must be available and match their exact
qualified/kernel values; numeric zero is not a generic missing-value sentinel.
Unavailable mandatory evidence is always refused.

The closure digest is SHA-256 over ASCII `ES_PRIVACY_CLOSURE_1\n`, entry count
u32, then each verified compiled object in increasing object-ordinal order:
ordinal u32, st_dev u64, st_ino u64, content SHA-256 (32). Include the executable,
loader, guard, required interpreter/script and every required mapped installation
object. The compiled chain fixes required/allowed objects and branches; an
unexpected executable mapping or absent mandatory object refuses. Bound this list
to 512 verified object occurrences per entire launch, each admitted file to
512 MiB and cumulative bytes hashed to 2 GiB per launch, using at most 64 KiB
scratch. Every handshake/exec/branch consumes the same counters; re-verifying a
previous object consumes another occurrence and its hashed bytes again. Neither
a fresh connection nor a new ordinal resets these budgets. The unchanged startup
deadline may refuse earlier.
Unknown compiled closure encodings or incomplete records refuse, not an empty
record hash. Anonymous executable/JIT mappings require an explicit qualified
runtime branch; their absence from a file list never self-approves them.

Do not reset a suppressed peer to dumpable one to inspect it. Static binaries,
AT_SECURE, set-ID/file capabilities, unknown loaders, absent/ignored preload and
uninspectable identity refuse. No secret can enter any process until the required
final node has completed admission. The verified constructor/loader closure must
establish that PREPARE occurs before any secret-consuming work or untracked fork.

## Private direct-parent inspection prerequisite

Before the complete coordinator, `es_parent_check(es_peer *child, es_peer *parent)`
may check one live direct kernel parent edge between two existing acquired pins.
It returns `es_peer_result`: OK is only that bounded process fact, never compiled
chain, Java launch, image, suppression or runtime admission. It accepts no PID,
pathname, claimed ancestry or new pin acquisition. Both caller-serialized owners
must remain live and share the same borrowed cancellation descriptor and original
startup deadline. Null, overlapping/same owners, same PID/pin or foreign controls
refuse before mutating either owner. Existing pinned identity rechecks may close
their own failed pin under the peer's sticky cleanup contract.

Use verified local procfs, no-follow/nonblocking/CLOEXEC temporary descriptors and
at most 16 KiB per parent record including the EOF witness. Parse PID, direct PPID
and start ticks from the child's actual stat record, allowing legal comm delimiters;
require exact child identity and the pinned parent's PID. Recheck both original
pins before/after each bounded record read and read the parent edge twice. An
exited/reparented child, dead parent, malformed/unavailable evidence, cancellation
or deadline refuses. No liveness/parent check establishes an exec generation or
atomic lifetime beyond the checked boundaries. The future coordinator must retain
both pins and reject any subsequent missing or contradictory graph evidence.

Close each temporary descriptor once and wipe scratch on every return. A close
error takes precedence as CLEANUP; the invoking coordinator must latch it and
cannot retry the inspection to erase uncertainty. No borrowed control descriptor
is closed or drained. A stalled kernel syscall is not made interruptible by the
deadline. Tests use actual independent mock processes plus narrow syscall fault
injection, including death/reparenting, legal comm, exact closed-number reuse and
refused foreign launch controls. Production registry/JNI remains unchanged.

## Fixed wire encoding

All integers are unsigned big-endian; no native struct layout, padding, strings,
JSON, NUL terminators or extensible fields occur on the wire. Every frame starts
with this exact 12-byte header:

| Offset | Bytes | Meaning |
| --- | ---: | --- |
| 0 | 8 | ASCII `ESPRV001` |
| 8 | 1 | Type below |
| 9 | 3 | Zero, reserved |

The type fixes the total length. A header never supplies an allocation size.
Use fixed mutable buffers of at most 108 bytes; reject unknown type, nonzero
reserved bytes, truncation, duplicate frame or any byte beyond the expected frame.
Stream fragmentation does not change grammar or deadlines. A frame is complete
only after every byte has been read. A parser never hunts for a later magic word.

| Type | Direction | Payload / total frame size |
| --- | --- | --- |
| `01` PREPARE | child → parent | Empty / 12 bytes |
| `02` CHALLENGE | parent → child | Correlation tuple / 96 bytes |
| `03` ESTABLISHED | child → parent | Tuple + proof fields / 108 bytes |
| `04` ACK | parent → child | Correlation tuple / 96 bytes |
| `7e` ABORT | parent → child | Empty / 12 bytes |
| `7f` REFUSED | child → parent | Refusal code u16, zero u16 / 16 bytes |

The 84-byte correlation tuple is, in order: fresh per-launch random identifier
(16 bytes), assigned per-launch exec ordinal (u32, 1–64), fresh unpredictable
per-connection challenge (32 bytes), and accepted identity-record SHA-256
(32 bytes). The parent generates identifiers/challenges from the qualified OS
random source; failure refuses. The child copies the tuple exactly, never changes
or interprets its identity digest. Tuples are control data, not credentials.

ESTABLISHED adds exactly: dumpability u8=`0`; NO_NEW_PRIVS u8=`1`; thread coverage
u8=`1` (successful TSYNC, including the currently single-threaded case); reserved
u8=`0`; reset-to-1 errno u16=`1` (EPERM); reset-to-2 errno u16=`1`; Linux audit
architecture u32=`c000003e`. Every field must equal the expected constant. These
claims are meaningful only in the verified constructor/installation, not a peer's
self-asserted policy. Tests exercise actual kernel behavior on existing threads.

Child refusal codes are closed: `1` PLATFORM, `2` SUPPRESSION, `3` THREAD_SYNC,
`4` RESET_CONTROL, `5` PROTOCOL, `6` DEADLINE. They carry no OS message, exception,
path, native output or arbitrary errno. Any refusal is terminal for that launch.
Unknown refusal codes also fail closed. Child protocol failure exits with fixed
status 125 after closing its control descriptor; it emits no stdout/stderr text.
ABORT may terminate any child wait, but never converts a failure into success.

## State machine and deadlines

### Initial Java fork ownership

Java PID/start-time/isAlive metadata is consistency evidence, never the root pin.
The qualified fixed JNI library registers its atfork handlers once, cannot unload
until invocation teardown, and uses a private pre-exec capture separate from the
constructor wire. `armFork(launch)` runs after self-suppression on one dedicated
Java platform launcher thread, immediately before its one ProcessBuilder.start.
Reject virtual threads, another thread's registration, rearming, nesting, stale
generations and another ProcessBuilder start inside that window. Serialize all
supervisor arm/start/register/finally-disarm windows; no unrelated process launch
may share an armed thread. Qualify the actual installed JDK FORK path and handler
ordering; a property or source tag alone does not prove callbacks ran.

Native code creates one fresh unnamed AF_UNIX SOCK_SEQPACKET pair with CLOEXEC and
NONBLOCK, enables and verifies SO_PASSPIDFD and SO_PASSCRED on the receiving end,
and prepares exactly 24 immutable bytes: ASCII `ESFORK01` followed by this launch's
16-byte random identifier. No pathname, PID, credential, Java object or caller
buffer enters the record. Capture scratch and descriptors count toward the existing
per-launch bounds. No filesystem/abstract endpoint or public inherited descriptor
is introduced. Missing kernel support refuses without numeric pidfd_open fallback.

The child hook accesses preallocated initial-exec TLS and already bound immutable
data. It sends the whole record once with MSG_DONTWAIT|MSG_NOSIGNAL, then closes
both inherited capture descriptors and returns to JDK exec preparation. A short,
interrupted, blocked or failed send, or uncertain close, exits125 immediately,
with no retry, output or parent acknowledgement. No allocation, locks, JNI/Java,
formatting, hashing, stdio, lazy binding or dynamic TLS resolution occurs in the
hook. Qualify the generated instructions and exact libc call closure, including
compiler-inserted helpers and signal behavior, for the installed runtime.

An atfork parent handler closes its sending copy once after the fork attempt;
its receiving copy remains under the native coordinator. The launcher thread's
finally path disarms any remaining generation-bound TLS on every return/throw.
Cancellation cannot close/reuse a descriptor while an armed handler may still use
its number. If the fork window has not demonstrably ended, retain its bounded
quarantine and report cleanup uncertainty. Hook or finally close uncertainty is
sticky; never retry a number that could have been reused. The child closes its
own inherited copies; parent cleanup cannot close a child's descriptor for it.

The native owner receives one exact record with MSG_CMSG_CLOEXEC, requiring exactly
one kernel SCM_PIDFD and one SCM_CREDENTIALS with exact sizes, and no other ancillary
data. Reject truncation, unknown/duplicate records, trailing packets, non-EOF after
all sending owners should be closed, wrong launch identifier and malformed sender
identity. Close every received descriptor on refusal, including truncated ancillary
delivery, without accepting SCM_RIGHTS. The existing constructor wire still rejects
all ancillary data. Check the pin's CLOEXEC, live fdinfo PID, UID/GID and process-start
identity under the same bounded procfs rules as socket peer checks.

The coordinator may capture the sender pin before Java start returns for cancellation
and cleanup only. `registerRoot` requires the exact Process returned by the same
armed thread/window, its positive PID and this still-live kernel pin. It performs
no numeric acquisition. Match an initial constructor's independent live socket pin
to that root's PID and start identity; retain both pins while comparing. Neither a
dead/recycled root nor an inherited socket's creator credentials can satisfy this.
Failure, no hook, missing/extra receipt, unreturned start or mismatch refuses; process
and descriptor cleanup still must be established. A captured pin may support owned
termination after cancellation, but never makes uncertain process cleanup COMPLETE.

The original startup and cleanup clocks bound arming, fork capture, registration
and disarming; no hook/capture creates another allowance. This protocol's production
qualification includes failed/stalled fork or exec, missing hook/kernel feature,
same-UID interference, wrong/migrating thread, duplicate ancillary/records, descriptor
exhaustion/reuse, cancellation, expiry, concurrent cleanup and JVM shutdown. The
external feasibility probe does not establish those gates.

The ownership mechanism uses the kernel message sender captured by
[Linux SCM_PIDFD](https://github.com/torvalds/linux/blob/v6.8/include/net/scm.h).
The [pinned JDK fork path](https://github.com/openjdk/jdk21u/blob/jdk-21.0.12%2B8/src/java.base/unix/native/libjava/ProcessImpl_md.c)
explains why the hook cannot await post-start acknowledgement. Qualify actual
binaries and [pre-exec async-signal safety](https://pubs.opengroup.org/onlinepubs/9799919799/functions/fork.html)
before enabling an installed runtime.

### Native root-correlation prerequisite

Before the complete coordinator/JNI boundary, one private C owner may establish
only the first root correlation. It owns one existing `es_fork` object in stable,
fresh zeroed storage and borrows an already prepared `es_connection` only during
one serialized match. It does not acquire a pin from a numeric PID, duplicate a
socket, parse procfs independently, send CHALLENGE/ACK, verify image/ancestry or
admit a runtime. No descriptor or C object crosses production JNI in this slice.

The closed C entry points in `privacy-root.h` are:

```c
es_root_result es_root_arm(es_root *, int cancel_fd, uint64_t deadline_ns,
                          const uint8_t launch_id[16]);
es_root_result es_root_capture(es_root *, es_peer_identity *);
es_root_result es_root_register(es_root *, uint64_t exact_returned_pid);
es_root_result es_root_disarm(es_root *);
es_root_result es_root_match(es_root *, es_connection *, es_peer_identity *);
es_root_cleanup es_root_close(es_root *, uint64_t cleanup_deadline_ns);
```

`es_root_result` is closed: `ES_ROOT_OK=0`, `ES_ROOT_CAPTURED=1`,
`ES_ROOT_REGISTERED=2`, `ES_ROOT_CORRELATED=3`, `ES_ROOT_INVALID=4`,
`ES_ROOT_PLATFORM=5`, `ES_ROOT_IDENTITY=6`, `ES_ROOT_DEAD=7`,
`ES_ROOT_PROTOCOL=8`, `ES_ROOT_DEADLINE=9`, `ES_ROOT_CANCELLED=10`,
`ES_ROOT_IO=11`, `ES_ROOT_CLEANUP=12`. Cleanup has
`ES_ROOT_CLOSED_COMPLETE=0`, `ES_ROOT_CLOSED_INCONCLUSIVE=1`,
`ES_ROOT_CLOSE_INVALID=2`. There is no FINAL_ADMITTED result.

Arm/register/disarm belong to the original dedicated platform launcher thread.
One caller serializes capture, register, match and close, including safe publication
of completed capture to the registering launcher. Disarm may overlap capture only
under the existing fork primitive's split ownership: it never reads/mutates the
receiver's non-atomic state, and completion must be published before match/close.
Only signalling the borrowed cancellation eventfd may otherwise be concurrent.
The owner retains the original startup deadline and launch generation; operations
cannot replace them. The cancellation descriptor remains borrowed and live until
all owner calls and its final close have completed.

The successful order is arm, CAPTURED, same-launcher register, same-launcher disarm,
then one first-root match. Capture may precede ProcessBuilder return for cleanup
evidence only. Register-before-capture, wrong thread, duplicate/stale operations,
zero/out-of-range or mismatched returned PID refuse. Registration compares the
still-live retained kernel capture to the positive PID from the exact Process
created by the reviewed Java launch wrapper; the C parameter by itself cannot
prove that Java provenance. A test-only bridge must use that actual wrapper for
FORK controls. It cannot turn a generally callable PID API into root authority.
No unreturned/failed start can be registered or matched.

Match requires successful registration and completed disarm, and a connection
that already received exactly one PREPARE. The root owner retains its original
cancellation descriptor independently of its fork member. Require the borrowed
connection's listener, peer pin and wire owner to carry that exact cancellation
descriptor and original startup deadline. A foreign or internally inconsistent
launch scope refuses with ES_ROOT_INVALID before correlation; root identity alone
does not establish the connection's operation/deadline authority. Recheck the
retained root pin and the connection's separately retained live pin before and
after comparison through
`es_fork_read` and `es_connection_read`. Require exact PID, start ticks, UID and
GID equality; unavailable identity, an exited root/peer, unrelated same-UID peer
or inherited socket creator refuses. Keep both pins owned during comparison.
This establishes correlation at the checked boundary, not atomic future liveness,
image, suppression, descendant or repeated-exec identity. The caller keeps the
connection under its original owner; root match never transfers/duplicates its
socket or pin and never directly closes or releases its listener capacity. A
connection read refusal retains that connection's own cleanup semantics.

All refusals on a live root are sticky and cannot be retried into correlation.
Distinct identity outputs are zeroed on refusal. Null or overlapping output is
refused before writing into either owner; caller output may not overlap the root
or borrowed connection storage. Missing hook, cancellation or expiry cannot be
converted into a later successful registration or match. Disarm may report OK
solely to establish that its launcher window ended; it preserves any earlier
refusal and grants no registration/correlation. A same-launcher failed arm may
also report ended-window OK only when that exact completed `es_fork_arm` call
created a positive owned generation, returned failure with its armed flag zero,
and the root owner recorded this fact before publishing to a receiver. The fork
primitive then never installed that generation in TLS and released its window.
Fresh/unattempted objects, pre-initialization rejection, unrelated generations,
wrong-thread and duplicate disarm still refuse. This proof is immutable arm-result
data, not a later receiver-state inspection; cleanup uncertainty remains sticky.
Cleanup still attempts each owned release once, even after cancellation/expiry. A still-armed
window is quarantined until the launcher demonstrably disarms; uncertainty remains
sticky after later release. Never retry a closed descriptor number or reinitialize
a closed owner. First close fixes its independent cleanup deadline (at most ten
seconds remaining); repeats may shorten but never renew it. Root cleanup reports
only this owner's native resources. Owned-process, borrowed-connection/listener
and enclosing invocation cleanup remain separate required outcomes.

Required credential-free controls include actual captured/returned/constructor
root agreement, wrong/unrelated/inherited peer, dead root or peer, missing hook,
POSIX_SPAWN, order/thread/generation faults, cancellation during capture, late
return/disarm quarantine, close-once/reused descriptors and unchanged native
output. Test-only JNI exercises actual Java FORK launch ownership; it does not
qualify production token registries, library installation, complete coordinator
or client crash privacy. Those gates remain required before runtime availability.

### Coordinator transitions

One native coordinator owns these transitions and all descriptor operations:

1. `CREATED`: listener exists, launch deadline fixed, no process admitted.
2. `FORK_CAPTURE_ARMED`: bind the private pre-exec channel to the dedicated launcher
   thread. Capture the kernel sender pin without admission; await the matching Java
   start result within the original launch deadline.
3. `ROOT_REGISTERED`: correlate that still-live pin with the exact returned
   Java-owned Process. A constructor connection may already be pending, but no
   connection is processed before registration. A failed Java start closes the
   launch; an unreturned/uncertain Process cannot report clean admission.
4. `PREPARE`: accept a permitted peer, read one PREPARE, verify identity/graph,
   assign its ordinal, send one CHALLENGE.
5. `ESTABLISHING`: child receives CHALLENGE, establishes/checks suppression, then
   sends one ESTABLISHED. Parent validates the complete tuple and proof fields.
6. `ACK_SENT`: send one ACK; require child EOF with no extra bytes and successful
   local connection closure. Only then commit that graph-node admission.
7. `AWAIT_NEXT`: allow only a compiled next branch/exec, including admitted
   concurrent interpreter pipeline nodes. Repeat fresh PREPARE for every exec.
8. `FINAL_ADMITTED`: all mandatory nodes are admitted and the required final
   process is alive. Publish one immutable admission event to the owning Java
   lifecycle. A receipt count alone cannot reach this state.
9. `FAILED` or `CLOSING`: terminal, no new admission, trigger owned cleanup.
10. `CLOSED_COMPLETE` or `CLOSED_INCONCLUSIVE`: sticky descriptor/endpoint result.

Compute the startup deadline once when creating the launch: the earlier of its
surrounding operation deadline and CLOCK_MONOTONIC now + 10 seconds. JNI/native
code owns this clock; Java passes remaining operation nanoseconds, not a raw
System.nanoTime value assumed to share an epoch with native time. Reject a
nonpositive duration, arithmetic overflow, or duration beyond the compiled
chain lifetime, whose hard ceiling is 180 seconds. This is an outer bound, not a
replacement for current 10-second authentication, 10-second bootstrap/settings,
120-second transaction, 10-second cleanup or terminal-entry/restoration clocks.
Each shorter clock still starts and expires at its existing phase boundary;
unused time in another phase cannot extend it. Any setup,
root registration, pending peer, hashing, read, write, child transition or retry
consumes that same budget. Reads/writes poll with remaining time and cancellation;
EINTR never restarts a clock. No operation waits indefinitely in a JNI call.

The listener remains monitored through owned process cleanup. After admission,
monitoring uses the retained surrounding operation deadline; the spent startup
budget cannot be reopened to admit another exec. After final
credential-bearing admission, any attempted additional exec/handshake fails the
operation; no new startup allowance exists. Credential-free later branches are
allowed only when explicitly represented before final admission. The controller
checks live admission before ARM, credential delivery and package/commit writes.
A fault after commit may have been sent preserves the transaction protocol's
UNKNOWN outcome; it cannot be rewritten as a confirmed rollback.

## Private JNI surface

These are exact Java method signatures; their named immutable result types and
closed enums must be implemented together, without sentinel null/zero success.
Native linkage errors are fixed local refusal, never fallback. The compiled chain
ordinal selects only an installed entry; no JNI argument describes executable
commands, library locations or new trust policy.

```java
static native SelfResult establishSelf(int compiledMechanism);
static native OpenResult openLaunch(int compiledChain, long operationRemainingNanos);
static native ForkResult armFork(long launch);
static native RootResult registerRoot(long launch, long ownedPid);
static native DisarmResult disarmFork(long launch);
static native Event nextEvent(long launch);
static native Status status(long launch);
static native void cancel(long launch);
static native CloseResult closeLaunch(long launch, long cleanupRemainingNanos);
```

`SelfResult` is ESTABLISHED or a fixed Failure. `OpenResult` is a valid nonzero
opaque launch token and bounded socket path, or Failure. `ForkResult` is ARMED or
Failure. `DisarmResult` is DISARMED or Failure; disarming grants no root admission
and cannot erase an earlier failure. `RootResult` is REGISTERED
or Failure. `Event` is FINAL_ADMITTED with a native-owned final identity token,
FAILED with Failure, or CLOSED. `Status` is STARTING, ADMITTED, FAILED or CLOSED.
`CloseResult` is COMPLETE or INCONCLUSIVE. Failure is one of PLATFORM,
INSTALLATION, SELF_PRIVACY, THREAD_SYNC, RESOURCE, IDENTITY, CHAIN, PROTOCOL,
DEADLINE, CANCELLED or CLEANUP. No result includes syscall text or raw output.

A token is a generation-checked native registry handle, not a descriptor/address.
Bound the registry to one live supervisor invocation and at most four concurrent
launches. An invalid/stale/wrong-invocation token refuses; it cannot touch a newly
reused descriptor. Successful close retains a tombstone until invocation teardown
so repeat close returns its original result, with no renewed deadline. Token
exhaustion refuses. `cleanupRemainingNanos` conveys the already-running Java
cleanup budget, not a configurable timeout. The first close fixes a native
absolute deadline no later than that remaining budget or ten seconds from first
close; repeated calls can only shorten it. A nonpositive remaining budget starts
immediate best-effort descriptor shutdown and cannot report unconfirmed cleanup
as COMPLETE. No caller can renew the owning lifecycle's clock.

One dedicated Java platform coordinator calls `nextEvent`; concurrent callers refuse.
The dedicated launcher alone calls arm/register/disarm for its bound window; it
cannot consume coordinator events. `cancel` and `closeLaunch` are the cross-thread
control operations and wake native
polling via the owned eventfd. `status` reads a latched immutable state and never
consumes a frame. FINAL_ADMITTED is emitted once; the same coordinator continues
monitoring for failure/cleanup so post-admission faults reach the owning lifecycle.
No unbounded event queue or retained credential buffer exists in the bridge.

The native owner retains each listener, accepted descriptor, pidfd, directory
handle and identity-read descriptor until it has closed it or latched uncertainty.
No raw descriptors cross JNI. All control/identity scratch buffers are bounded
and wiped on success, refusal and interrupted cleanup. JNI must not pin a Java
array across blocking I/O. Control/identity native scratch is at most 1 MiB per
launch, excluding immutable compiled installation records; allocation failure
refuses. The compiled closure contents and platform representation of device,
inode and process start ticks require exact qualification before admission.

## Cleanup and qualification gates

A failed handshake triggers Java's existing owned-process/pipe cleanup and terminal
restoration. Native close cancels its coordinator, closes every owned descriptor,
unlinks only its exact socket and removes its owned directory. Join the coordinator
and establish these outcomes within the existing absolute cleanup deadline.
A close syscall, worker join, process exit or endpoint removal not established
within that deadline is sticky INCONCLUSIVE. Native COMPLETE alone is not whole
operation cleanup: Java must also establish process and all three pipe closure,
credential buffer wiping and terminal restoration. Never retry a Linux close on a
numeric descriptor after it may have been released/reused.

Before implementation review: independent byte fixtures for every frame; all
partial boundaries and hostile lengths/types; wrong PID/start/exec/chain/image;
concurrent pipeline width; missing/static/secure preload; stale challenge; failed
TSYNC and reset1/2 controls; final-process exit; backpressure and cancellation;
late fork/exec; exact output preservation; JNI handle reuse and cleanup exhaustion.
Then qualify the exact FORK/JDK/native client/loader closure and credential-free
crashes/diagnostics. No authenticated native database probe is authorized by this
ABI document. Existing external transport tests establish feasibility only; their
JDK-internal descriptor bridge and ancestry-only receipt do not implement this ABI.
