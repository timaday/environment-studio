# Standalone crash privacy — planned Linux amd64 mechanism

This extends [standalone runtime admission](guarded-supervisor-runtime-v1.md).
It is a planned private mechanism, not a qualified runtime registry entry. Public
invocation and configuration remain closed and unchanged. The web application
never loads this library, launches these processes or handles execution credentials.

## Scope and installation

The distribution owns one fixed native guard/bridge library. Its ABI, source,
compiler/libc/JDK/platform, exact bytes and trusted installation path are pinned
by the compiled runtime qualification record. An operator cannot select a library,
preload, JNI path, suppression strategy, socket endpoint or executable chain.
Never load a library from a package, user configuration, working directory,
inherited environment or writable search path. Preserve trusted-file admission,
no-follow checks and the existing trusted-owner threat boundary. No global kernel,
collector, runtime-installation or security configuration change is required.

The candidate launcher fixes the exact qualified JDK's process mechanism to FORK
before ProcessImpl initialization; verify the setting during admission and qualify
the actual exec chain. The default POSIX_SPAWN mechanism executes jspawnhelper
using the JVM's native environment before the requested child's environment is
installed. Do not silently exempt that executable from per-exec privacy evidence.
No fallback to another launch mechanism is allowed. Missing/unsupported settings
or fork/ENOMEM failure refuse. Measure fork memory overhead with the actual
maximum standalone workload; a property string alone is not execution evidence.

The first Java-owned process must be pinned before exec, without looking up its
returned numeric PID. After JVM self-suppression, arm the fixed native library on
one dedicated platform launcher thread. Its qualified atfork child hook sends one
preprepared record on a private unnamed socketpair. Kernel SO_PASSPIDFD supplies
the message sender's process pin; a pre-fork socket's SO_PEERPIDFD instead identifies
the creating JVM and cannot establish child ownership. Registration correlates the
live captured pin with the exact Process returned by that same launch. Java's
isAlive/start-time metadata and a caller PID alone are insufficient.

The hook never waits for an acknowledgement: ProcessBuilder.start itself waits
for the exec-failure channel to close. A native owner may receive the capture
before Java returns solely to support bounded cancellation/cleanup. No unreturned,
failed or uncertain Java start grants admission. Fixed async-signal-safe hooks,
thread binding, descriptor lifetime and failure handling follow the private ABI;
this capture grants no executable, ancestry or crash-privacy admission.

The library provides a narrow private JNI boundary for self suppression and an
owned Unix-domain control listener. Use native descriptors owned by that boundary;
do not reflect into JDK file-descriptor fields or introduce a public native command
runner. Handles and bounded buffers are owned per invocation and closed once.
The listener lives in a fresh owner-only directory, has owner-only permissions,
is never abstract/global, and is removed with its owned directory during cleanup.
No request, payload, credential or diagnostic data is written there.

## Suppression before secret entry

For the supervisor JVM, verify fixed fatal-error/heap/core startup settings and
the zero soft/hard core-file limits. Load only the admitted distribution library
before requesting any credential. Set PR_SET_DUMPABLE to zero and verify zero.
Install an architecture-qualified seccomp filter with NO_NEW_PRIVS. A late JVM
installation must synchronize all existing threads with TSYNC; a per-thread
success does not establish process-wide coverage. Refuse failed/partial thread
synchronization and unsupported syscall/architecture forms.

The filter denies PR_SET_DUMPABLE requests for every nonzero value with EPERM,
including the qualified reset-to-1 and reset-to-2 controls. It permits zero.
Verify both controls and recheck dumpable zero. EINVAL from an unsupported reset
is not proof that the filter denied it. Qualified tests include an already-existing
JVM thread and a subsequently created thread. Do not claim this limited filter is
a general syscall sandbox, application authorization or protection from a trusted
owner/root changing the installation.

Linux resets dumpability across exec. Inherited limits/filter state therefore
cannot certify a later executable. Every exec in the owned terminal, launcher,
native client and orapki interpreter/tool/JVM chain requires a fresh private
receipt before its main routine proceeds. A fork without exec inherits the
established state; qualify that inheritance and every subsequent exec separately.
Never supply credential bytes, ARM a terminal or start package input until the
required final executable's receipt is admitted. An intermediate launcher's
receipt cannot admit the final client.

## Private per-exec admission

The fixed child environment may contain the tool-owned control socket location
and the exact admitted preload path. Neither is a credential or caller option.
The constructor uses the private channel; stdout/stderr remain the single native
protocol stream with no inserted marker. Missing preloading may still produce a
successful process exit, so exit status never substitutes for a receipt.

Use a two-phase handshake before credential-bearing work:

1. **Prepare.** The constructor connects and waits. The parent obtains peer PID,
   UID and GID from kernel SO_PEERCRED, pins that socket peer with SO_PEERPIDFD,
   and correlates the owned root/descendant and
   process-start identity, and verifies the expected executable/interpreter,
   loader and guard installation against the compiled chain. It examines the
   process before suppression: after dumpable zero, even an owning parent may
   be unable to read `/proc/<pid>/exe` or maps. Never temporarily restore
   dumpability in a process that has received secrets.
   Unsupported peer-pidfd acquisition refuses admission; numeric PID lookup is
   not a fallback because the peer can exit before that lookup. Retain the pidfd
   through cleanup and check liveness around subsequent identity reads.
2. **Establish.** Only after identity acceptance does the parent send a fresh
   unpredictable challenge. The constructor establishes zero dumpability,
   the qualified filter/reset controls and whole-thread coverage, then returns
   the bounded challenge-bound result on the same connection. The parent admits
   it once and acknowledges before the constructor returns to the program.

Specify the fixed binary frames, JNI signatures and state machine in the tools
ABI document before implementing them. Bound every read/write before allocation;
no strings from the peer become commands, paths, log messages or policy. Socket
work shares one absolute 10-second startup allowance per owned child launch,
including all its expected execs, and the surrounding operation deadline. A
receipt, retry, new peer or intermediate exec never resets that clock. Support
at most 64 expected execs and one actively processed handshake per launch. The
compiled chain specifies its concurrent pending-peer width, at most 16; bound
accepted descriptors and the listener backlog accordingly. Qualify interpreter
pipelines whose children reach constructors concurrently. A serial accept loop
does not prove that only one peer is pending. Unexpected
peers/frames, duplicate or stale challenges, EOF, timeout and ambiguous identity
refuse the launch and trigger owned cleanup; do not continue to a later success.

The compiled record describes the exact expected chain and accepted branches,
including fork/exec parentage, interpreter and final executable roles. PID alone,
claimed parent PID, a client-supplied path or a count of receipts is insufficient.
Account for PID reuse and repeated exec within one PID. Validate process identity
while the constructor remains blocked. The known loader/constructor order and
complete fixed library/search-path closure must be qualified; no unreviewed
constructor may read credentials or create untracked work before admission.
Later runtime library loads must stay within that qualified installation closure.
Keep listener and chain authority alive through owned process cleanup. Each exec
uses a fresh connection/challenge and repeats image verification even when PID and
start time are unchanged. Once the final credential-bearing process is admitted,
no further exec is permitted; an attempted later handshake refuses the operation.
Credential-free intermediate branches must be explicitly present in the compiled
chain and remain within the same startup clock.

Static binaries, AT_SECURE/set-id/file-capability execution, ignored preload,
unknown interpreters, unexpected execs, changed hashes and unavailable identity
evidence remain refused. A child terminating without its required receipt is
unqualified even if its version output looks correct. No process may be admitted
solely because it reported that suppression succeeded. Trust in the receipt is
limited to the verified installation, private kernel-correlated channel and
independently qualified constructor behavior.

## Diagnostics and cleanup

Kernel dump suppression does not prove JVM fatal/heap or Oracle ADR/log privacy.
Keep fixed JVM settings, sanitized environments, empty owned working directories,
private bounded native-output handling and explicit client diagnostic suppression.
Qualify credential-free startup errors and native/JVM crashes before any native
authentication. Check actual collector behavior and all documented client
diagnostic locations; absence in the working directory alone is insufficient.
Only independently invented markers are used in fault tests. Record inaccessible
collector/diagnostic evidence as unresolved instead of inferring absence.

On channel failure, stop only owned processes, close the listener/connections and
all three native pipes, wipe mutable challenges/frames and restore the terminal
under its existing independent deadline. Admission requires confirmed cleanup;
unclosed processes, workers, descriptors or an unremoved owned endpoint remain
INCONCLUSIVE. No automatic credential or execution retry is introduced. Suppression
does not change the ordered native transaction protocol or COMMIT acknowledgement.

## Required qualification

Tests must distinguish local controls from registry authorization. Require actual
RED/GREEN for late per-thread versus TSYNC installation, exec resetting dumpability,
missing/static preload receipt absence, and private-channel faults. Exercise
wrong peer/start identity/chain/image, stale/duplicate challenge, reordered/truncated
frames, stalled peers/backpressure, process exit during admission, failed TSYNC,
reset1/reset2 attempts, descendants and cleanup exhaustion. Verify byte-identical
native protocol output with the channel enabled. Test the actual pinned psql,
SQL*Plus and complete orapki chain, followed by credential-free crash/diagnostic
controls and only then the separately authorized disposable DB qualification.

The current external prototype establishes transport feasibility and the TSYNC
gap/correction on one host. Its internal-JDK descriptor bridge, ancestry-only
identity and incomplete fault matrix do not satisfy this contract. The empty
ordinary registry must remain empty until independently reviewed evidence exists
for the exact complete runtime.

Primary references: [exec process attributes](https://man7.org/linux/man-pages/man2/execve.2.html),
[dumpability](https://man7.org/linux/man-pages/man2/PR_SET_DUMPABLE.2const.html),
[seccomp filter semantics](https://www.kernel.org/doc/html/latest/userspace-api/seccomp_filter.html)
and [Unix-domain peer credentials](https://man7.org/linux/man-pages/man7/unix.7.html).
The JDK-specific launch behavior is described in OpenJDK21's
[native process implementation](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/unix/native/libjava/ProcessImpl_md.c)
and [Java process implementation](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/unix/classes/java/lang/ProcessImpl.java).
