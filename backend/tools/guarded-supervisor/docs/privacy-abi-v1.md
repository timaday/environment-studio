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

## Private argument inspection prerequisite

`es_arguments_check(es_peer *peer, const unsigned char *expected, size_t length)`
checks the actual NUL-separated command line against an immutable parent-owned
expected byte vector. This private C prerequisite accepts no PID, path, claimed
child command, descriptor transfer or runtime policy. Expected bytes come from
the future compiled launch node and its already-admitted typed arguments, never
from peer input. Caller serialization keeps the peer and expected buffer stable.
Reject null/overlapping buffers and an invalid vector before touching the peer:
1–128 arguments, at most1024 non-NUL bytes per argument, at most16 KiB total
including all terminators, and an exact final NUL. Empty arguments are explicit;
an empty vector is invalid. Byte equality does not infer encoding or normalize
text; admission of typed argument values is the owning launch boundary's job.

Use the retained peer's original kernel pin and original cancellation/deadline.
Read verified local procfs cmdline with no-follow/nonblocking/CLOEXEC temporary
descriptors, at most16 KiB payload plus one EOF/overflow witness byte. Require
exact length, terminator positions and every byte. Repeat the complete independent
read, with pinned liveness/start checks before, between and after all reads,
including final EOF. A mismatch, changed vector, truncation, extra bytes,
unavailable/non-procfs file, dead peer, deadline or cancellation refuses. Close
owned temporary descriptors once, wipe scratch on every exit, and give close
uncertainty precedence as CLEANUP. Borrowed controls are never closed/drained.
The future coordinator must latch uncertainty; a retry cannot erase it.

Success establishes only observed argument equality at these checked boundaries.
It is not executable/script/loader identity, parentage, an exec-generation token,
constructor blocking, future liveness or runtime admission. The complete image
and graph checks remain mandatory before CHALLENGE. The [Linux command-line interface](https://man7.org/linux/man-pages/man5/proc_pid_cmdline.5.html)
reflects mutable process argument memory; this prerequisite cannot certify that it did not change
between checks. Tests use actual independently invented child execs plus narrow
read/liveness/cancellation/cleanup faults; no credentials enter these processes.
A stalled kernel syscall is not made interruptible by the startup deadline.

## Private trusted file opening prerequisite

Before image/script/loader association, `es_file_open` may open a tool-owned
normalized absolute installation pathname into one native owner. This does not
admit executable content or associate a file with a process. The compiled runtime
will supply the pathname; it is not a new public configuration field or JNI path
parameter. Existing Java external-file/package admission is unchanged.

Use fresh zeroed stable noncopyable storage and caller-serialized operations.
Borrow the original cancellation eventfd and absolute CLOCK_MONOTONIC startup
deadline, with at most ten seconds remaining. Path bytes are strict UTF-8,
1..4095 bytes excluding NUL; reject embedded NUL/CR/LF, relative paths, empty
components, dot/dot-dot components and trailing slash before acquiring resources.
Invalid caller inputs leave the owner unchanged. Keep the borrowed control
descriptor open and stable until this owner closes.

Traverse from a no-follow directory descriptor using single-component openat.
Use CLOEXEC throughout and nonblocking final O_RDONLY/O_NOFOLLOW open, then verify
the actual descriptor is a regular file. Before the final open, inspect the
anchored no-follow entry to refuse a known FIFO/device/other-owner entry without
opening it; recheck the opened descriptor and retain that single read handle.
Do not check a pathname and reopen it
for hashing. Each opened component must be on an admitted local Linux ext2/3/4,
tmpfs or overlay filesystem and owned by root or the invoking UID, with matching
real/effective UID. Group/other write authority refuses, except sticky directories
whose next component is independently root/operator-owned. On these qualified
Linux filesystem semantics, the POSIX ACL group mask also bounds named-user/group
write authority. Unsupported filesystem/ownership evidence refuses. Set-ID final
files or any security.capability attribute refuse; unavailable capability-xattr
evidence is not absence. Intermediate and final symbolic links are never followed.

Retain only the admitted file descriptor. At most two traversal descriptors are
owned simultaneously and temporary path/metadata scratch is wiped. Check the
original controls before/after traversal and metadata operations and before
publishing the descriptor. Close every owned temporary descriptor once; cleanup
uncertainty overrides an earlier refusal and cannot be retried. Failed initialized
owners must still close; no reopen or second ownership transfer is allowed.
`es_file_close` releases its retained descriptor once and preserves the established
cleanup result. No cancellation descriptor is closed or drained. The private
result set is OK, INVALID, PLATFORM, CANCELLED, DEADLINE, TRUST, IO and CLEANUP.

On successful open the retained descriptor can be borrowed by `es_hash_file` under
the same original launch controls and shared hash budgets. Neither primitive
proves expected digest, current path association, immutable future contents,
executable/script/loader membership or privacy. Concurrent root/same-operator
installation mutation remains outside the existing trust boundary; filesystem
syscalls are not made interruptible by the clock. Actual traversal, symbolic-link,
sticky/permission/owner/privilege, FIFO/device, cancellation and close-uncertainty
controls are required before integrating this prerequisite. Full native resource,
runtime closure and production coordinator qualification remain separate work.

## Private executable association prerequisite

`es_image_check` may compare one retained trusted installation file with the
current executable of an already acquired socket-bound `es_peer`. Borrow the
existing `es_file`, `es_hash` and `es_peer` owners in stable caller-serialized
storage; all must share the same original cancellation descriptor and absolute
startup deadline. The expected32-byte SHA-256 comes only from the compiled
installation record, never peer text, configuration or uploaded data. This adds
no public path, process handle, JNI method or qualified runtime registry entry.

Validate owner states, distinct owned descriptors and matching original controls
before acquisition. Refuse missing or foreign scope. The ordinary refused output
is zeroed; an output overlapping any borrowed owner or expected digest is invalid
and must not overwrite it. No borrowed file/control descriptor is closed or
drained, and neither file nor hash budgets are reset. Existing peer/hash failures
retain those owners' documented terminal cleanup behavior.

Hash the retained trusted file and require the compiled expected digest. Open
verified local procfs with no-follow directory descriptors, then the numeric
directory of the already pinned peer, with liveness/identity checks before and
after every potentially changing observation. Only that verified directory's
fixed `exe` magic link may be followed to acquire its actual read-only CLOEXEC
regular-file descriptor; no peer-supplied pathname or numeric pidfd lookup is
accepted. Hash that descriptor with the shared hash owner and require exact
device/inode/size/content equality to the trusted file. Repeat executable
acquisition independently, retaining the original process pin and comparing the
same identity again. Each of the three file hash occurrences consumes the same
launch-wide512 occurrence/2GiB byte limits; per-file512MiB and the original
deadline still apply. No separate content-sized buffer is introduced.

Close each temporary executable/proc directory descriptor once, wipe local
metadata/digests, and recheck the original peer/control evidence after final
temporary cleanup before returning the descriptor-derived identity. Close
uncertainty overrides earlier outcomes and never permits a retry of a reused
descriptor number. A failure is terminal for the caller's pending admission;
this prerequisite cannot retry its way into a successful handshake.

The result establishes the observed executable association at these bounded
checks only. It does not establish a script's bytes/argv, interpreter options,
loader/maps closure, constructor blocking, an exec generation, future immutability,
ancestry, privacy suppression or admission. A future coordinator must still
associate every fresh connection/ordinal and complete the entire compiled chain
before CHALLENGE/credentials. Concurrent mutation by root/the invoking operator
remains outside the trusted-installation model; a stalled filesystem syscall is
not made interruptible by checking the clock.

Acceptance uses actual socket-bound live child pins and independently invented
executables, plus changed/wrong file/hash, pathname replacement, dead peers,
foreign control scopes, cancellation/deadline through final close, metadata/hash
failures and exact descriptor-reuse cleanup controls. Mutation tests must witness
real association/evidence guards, not only compilation failures. No actual native
client, script/loader qualification or production library installation is inferred.

## Private script and interpreter association prerequisite

`es_script_check(peer, retained_script, retained_interpreter, whole_launch_hash,
expected, out)` composes the existing peer, arguments, file, hash and image
primitives. Its borrowed owners are stable, distinct and caller-serialized, with
the same original cancellation descriptor and absolute startup deadline. A
private immutable compiled `es_script_expected` holds the absolute script path
and length, exact LF-terminated shebang bytes and length, exact NUL-separated
argument bytes and length, script argument index, and two32-byte expected SHA-256
digests. `es_script_identity` contains only measured script and interpreter
`es_hash_identity` records. This adds no public configuration or runtime entry.

Validate all owner states, descriptor distinctions, original controls and bounded
pointer spans before acquisition. Input spans must not overlap mutable owners or
output. Output overlapping any input or owner is invalid and untouched; otherwise
all refused output is zero. Preserve borrowed owners' existing terminal semantics.
The caller latches every refusal; no retry or budget reset can admit a launch.
Closed results are OK, INVALID, PLATFORM, CANCELLED, DEADLINE, IDENTITY, RESOURCE,
IO and CLEANUP. Peer death, untrusted files and content/association mismatches
map to IDENTITY; cryptographic failures map to IO. Temporary cleanup uncertainty
overrides earlier results, including cancellation and identity refusal.

Use the existing128-argument/1024-byte-per-argument/16KiB total argv limits.
The script path is a nonempty absolute argument of at most1024 bytes, with the
trusted-file path rules. The exact shebang is at most255 bytes including LF,
starts `#!/`, and contains no NUL, CR, extra LF or ambiguous whitespace. Its
interpreter token is absolute and contains no spaces or tabs. This prerequisite
supports either no option or exactly one space followed by the compiled `-e`
option. Require interpreter token equality with argv[0], optional `-e` equality
with argv[1], script index exactly1 or2 respectively, and exact script pathname
equality at that index. Remaining arguments come from already-admitted typed
launch inputs and participate in complete byte equality. No env dispatch, stdin,
`-c`, unknown option, generic command parser or inferred script policy is added.

The literal shebang/argv interpreter token and the independently compiled trusted
canonical interpreter file may differ, for example `/bin/sh` and `/usr/bin/dash`.
Both are explicit installation facts. Never discover an alias from peer input,
follow it as a trust fallback or relax the trusted-file no-symlink policy. Actual
current interpreter association must still match the retained canonical file.

Check the original live peer and the complete argv through `es_arguments_check`.
Read the bounded exact shebang from the retained script with offset-preserving
reads, hash that file and require its compiled digest. Apply `es_image_check` to
the retained interpreter and compiled interpreter digest. Reopen only the exact
compiled script pathname through `es_file_open`, hash it and compare complete
device/inode/size/SHA-256 identity with the retained script. Repeat the complete
argv check, close the newly owned file once, then check the live original peer
and controls after cleanup. Each argv call retains its two independent reads.
Perform every acquired resource's cleanup after any refusal; never retry a
descriptor number after close uncertainty. Wipe temporary prefix and identities.

A successful call charges five hash occurrences: retained/reopened script and
the three interpreter image occurrences. All use the original shared512-object,
2GiB total and512MiB-per-file limits; failed attempts retain consumed charges.
Prefix scratch is at most256 bytes, never a full script buffer. Hash and argument
scratch retain their existing bounds. Borrowed file offsets and descriptors stay
unchanged. Deadlines do not make a stalled filesystem syscall interruptible.

Success proves measured script/current-interpreter association at these checks.
It does not prove which bytes an interpreter previously consumed or will reopen,
script safety, loader closure, blocked constructors, exec generation, future
immutability, privacy or runtime admission. Root/operator mutation exclusions
remain. Nested script interpretation cannot substitute a different actual image.
The future compiled chain and coordinator must qualify the complete behavior.

Acceptance uses owned invented scripts and interpreters, exact kernel shebang
argv and independently measured hashes; no-option/`-e` and Unicode/spaced paths;
equal-byte different inodes, replacement paths, shifted/extra args, malformed
headers, wrong digests, scope/alias faults, preserved offsets, actual death and
cancellation, final cleanup and descriptor reuse.507 prior charges plus five
succeeds;508 plus five refuses without resetting capacity. Independent mutation
tests must expose lost script/image association, argv positioning, charging or
cleanup precedence. Ordinary running-script controls are not constructor proof.

## Private bounded file hashing prerequisite

The private native file-hash owner may measure one already-owned read-only regular
file descriptor. It receives no pathname, PID, executable policy or configuration.
Successful hashing returns descriptor-derived device/inode/size plus SHA-256 only;
it does not establish trusted ancestry/ownership, executable or script association,
loader closure, current process identity, privacy or runtime admission. The caller
keeps the borrowed file and cancellation eventfd open and stable for every call.
No descriptor crosses production JNI or transfers to this owner.

Use a fresh zeroed `es_hash` owner for one launch. Keep its storage stable and never
copy an initialized owner. `es_hash_open` fixes its borrowed
cancellation descriptor and original absolute CLOCK_MONOTONIC startup deadline,
with at most ten seconds remaining. It initializes one private OpenSSL3 library
context with configuration loading disabled, the fixed built-in default provider
and an explicitly fetched SHA2-256 implementation. No supplied algorithm, provider,
module, engine, pathname or fallback exists. The pinned build dependency is Ubuntu
libssl-dev/libssl3t64 3.0.13-0ubuntu3.15; runtime installation and the library's whole
loader closure still require separate qualification before production wiring.
The native child fork hook must never invoke this allocating library.

`es_hash_file` borrows one distinct descriptor with O_RDONLY and CLOEXEC, verifies
it is a regular file, and uses bounded pread from offset zero without changing its
file offset. Size is at most512 MiB. Read exact initial size plus one EOF witness,
reject truncation/growth or changed device/inode/size/mode/uid/gid/mtime/ctime, and
recheck cancellation/deadline before/after every read, finalization and metadata
check. A stable metadata reading is not an immutable-file or atomic future-content
proof. Trusted installation admission and subsequent association checks remain
independent. A stalled filesystem syscall is not made interruptible by the clock.

The same owner allows at most512 object occurrences and2 GiB successfully hashed
bytes over the whole launch; repeated files consume another occurrence and bytes.
Use at most64 KiB file scratch. EOF witnesses consume no hash bytes. Capacity is
checked before reading/hashing beyond the remaining budget. Invalid arguments
refuse before ownership/counters change; null or overlapping outputs cannot
corrupt the owner. After a valid operation begins, every refusal is sticky and
cannot be retried or reinitialized to regain a budget. Distinct output and mutable
file/digest/metadata scratch are wiped on refusal and before local release.

`es_hash_close` releases each acquired EVP/provider/library object once, including
partial initialization. Provider unload uncertainty is terminal CLEANUP; repeated
close returns its established result without another release. Never close or
drain either borrowed descriptor. OpenSSL internal allocation and initialization
must still fit the complete native1 MiB-per-launch bound under the pinned runtime;
64 KiB file scratch alone does not qualify that bound or the library closure.
The fixed result set is OK, INVALID, PLATFORM, CANCELLED, DEADLINE, RESOURCE,
FILE, CRYPTO, IO and CLEANUP. Only OK from file hashing carries a measured record.
Open/close OK grants no file or process admission.

Required actual controls include independent SHA256 vectors, partial/EINTR reads,
unchanged offset/descriptors, exact size/aggregate/occurrence limits, wrong file
kind/mode, truncation/growth or metadata/content change, deadline/cancellation
through the final read, typed library failures, close-once ownership and wiped
outputs. Review the fixed implementation/dependency before integrating it.
The [OpenSSL EVP API](https://docs.openssl.org/3.0/man3/EVP_DigestInit/)
defines explicit digest selection/update/finalization. The
[Linux userspace crypto documentation](https://docs.kernel.org/crypto/userspace-if.html)
marks AF_ALG deprecated; this implementation does not use it.

## Private structural ELF64 layout prerequisite

`es_elf_check` is a bounded structural check of one already admitted retained
file. OK means only the supported ELF header/program-header layout was read
between two equal compiled-content measurements. It is never mapped-object,
loader, relocation, constructor, process, closure or runtime admission. No
peer/process/memory descriptor is accepted or opened. No dynamic tag, dependency
name, interpreter string, note property, relocation or section table is parsed.
In particular, this prerequisite does **not** reject or qualify text relocations,
CET/ISA properties, lazy binding or later loading.

The private C ABI is:

```c
typedef enum {
    ES_ELF_OK=0, ES_ELF_INVALID=1, ES_ELF_PLATFORM=2,
    ES_ELF_CANCELLED=3, ES_ELF_DEADLINE=4, ES_ELF_IDENTITY=5,
    ES_ELF_RESOURCE=6, ES_ELF_IO=7, ES_ELF_CLEANUP=8,
    ES_ELF_FORMAT=9
} es_elf_result;
typedef struct {
    uint32_t type, flags;
    uint64_t offset, vaddr, paddr, filesz, memsz, align;
} es_elf_program;
typedef struct {
    es_hash_identity file;
    unsigned char ident[16];
    uint16_t type, machine;
    uint32_t version, flags;
    uint64_t entry, phoff, shoff;
    uint16_t ehsize, phentsize, phnum, shentsize, shnum, shstrndx;
    uint32_t load_count;
    es_elf_program programs[128];
} es_elf_layout;
es_elf_result es_elf_check(const es_file *file, es_hash *hash,
        const unsigned char expected_sha256[32], es_elf_layout *output);
```

Records retain all ELF-header fields and all eight fields of every program
header in original table order. `programs[phnum..128)` and all struct padding
are zero; no pointer, descriptor, process address acquisition or authority token
is returned. `vaddr`, `paddr` and `entry` are declared integers from the file,
not verified mapped addresses. `load_count` counts PT_LOAD entries, including
zero-sized entries. No layout serialization/digest domain is introduced.

### Borrowing, measurements and limits

The caller serializes access and owns stable live `es_file`/`es_hash` objects
admitted through their existing APIs. Both must have exactly equal original
absolute startup deadline and borrowed cancellation descriptor, not a newly
renewed clock. Hash state must be live without sticky failure/cleanup uncertainty;
file state must be live without prior failure/uncertainty. The file descriptor
must remain distinct from the borrowed cancellation descriptor, read-only,
CLOEXEC, non-O_PATH and a regular file under the retained trusted-file policy.
No object copying, reopening after failure, raw FD substitution or resetting
shared counters is permitted.

Require valid nonoverlapping spans for file owner, hash owner, expected digest
and output; expected digest cannot reside inside either mutable owner. Invalid
owner/input/output overlap refuses INVALID and preserves every overlapping
owner/input byte. A distinct safe output is zeroed before any other refusal and
again on any later failure. Null output refuses without dereferencing it.
No attempted repair, owner close or crypto mutation occurs for preflight-invalid
arguments. Address validity of non-null C pointers remains a caller precondition;
integer span-overflow checks do not make arbitrary addresses safe.

Use the existing shared `es_hash_file(hash,file->fd,...)` before bounded header
reads and again after all structural validation. Each successful pass must equal
the compiled expected SHA256. Both measurements must agree exactly on device,
inode, size and SHA256. No digest-only identity comparison. Successful checks
consume two object occurrences and twice the actual file size under the existing
whole-launch512-occurrence/2GiB-hashed-byte/512MiB-file limits. Failure retains
all charges already consumed; early failure need not start the second pass.
No connection, repeated check or output layout resets those budgets.

Header and table reads use `pread`, preserving the borrowed descriptor's offset.
Read exactly64 header bytes and at most128*56=7168 program-table bytes, handling
partial progress and EINTR within the original controls. Decode little endian
explicitly rather than casting unaligned input to native ELF structs. Check
bounds/overflow before each read or arithmetic-derived access. A short file or
EOF within a required structural region is FORMAT; an actual read syscall error
is IO unless a higher-priority original control/cleanup refusal applies. Parsing
uses fixed storage; ELF parsing/layout scratch is at most16KiB. Retained layout
scratch may coexist with the existing es_hash_file64KiB file buffer, so the sum
of those buffers is at most80KiB, not16KiB. No allocation scales with declared
section count, file size or virtual size. Measure the complete native stack,
control-helper and crypto footprint separately; these buffer bounds are not the
complete1MiB privacy qualification claim.

Check original cancellation/deadline and retained descriptor metadata/control
validity around bounded I/O and after the final second hash pass and its owned
temporary cleanup. Cleanup uncertainty from either shared operation dominates
ordinary identity/format/IO/cancellation/deadline refusal; never retry a close or
infer it succeeded from another descriptor's state. Always attempt cleanup of
any locally owned temporary resource despite cancellation. This checker normally
owns no file descriptor; borrowed file/hash/cancellation owners remain caller
owned on every return, including a hash owner made terminal by its own failure.
Output success is copied only after final controls succeed; wipe local header,
layout and measured-identity scratch on all exits.

Two hashes sandwiching reads do not establish atomicity or ABA immunity. Stable
trusted-file namespace/content assumptions remain those of the admitted owner.
A later mapped-byte/coordinator stage must separately establish its actual
process/loader/quiescence assumptions; this checker does not strengthen them.

### Closed header support

Accept exactly ELF magic, ELFCLASS64=2, ELFDATA2LSB=1, ident version1,
OSABI SYSV=0 or GNU/Linux=3, ABI version0 and zero ident padding bytes9..15.
Accept ET_EXEC=2 or ET_DYN=3, EM_X86_64=62, e_version1 and e_flags0. Require
`e_ehsize=64`, `e_phentsize=56`, `e_phnum`1..128, `e_phoff>=64`, and a whole
program-header table within the first measured file size. Extended program
header numbering (PN_XNUM) is unsupported, not resolved through a section table.
There is no ELF-header/program-table byte overlap. Ident/header format outside
this closed support is FORMAT; an otherwise regular non-ELF input is also FORMAT,
not expected-content IDENTITY, after its first compiled-hash check succeeds.

Retain but do not interpret e_entry or section-table fields. Zero e_entry is
permitted: shared objects need not define an entry point. No section-table
extent, numbering or string-table authority is inferred from retained fields;
a future section consumer must validate them separately. No canonical mapped
virtual-address range or nonzero-address rule is imposed on ET_DYN-relative
vaddrs or any declared address here. Integer overflow and layout consistency
checks below still apply.

Header types are closed:

| Type | Numeric value | Multiplicity |
| --- | --- | --- |
| PT_NULL | 0 | zero or more within128 total |
| PT_LOAD | 1 | 1..32 |
| PT_DYNAMIC | 2 | 0..1 |
| PT_INTERP | 3 | 0..1 |
| PT_NOTE | 4 | zero or more within128 total |
| PT_PHDR | 6 | 0..1 |
| PT_TLS | 7 | 0..1 |
| PT_GNU_EH_FRAME | 0x6474e550 | 0..1 |
| PT_GNU_STACK | 0x6474e551 | 0..1 |
| PT_GNU_RELRO | 0x6474e552 | 0..1 |
| PT_GNU_PROPERTY | 0x6474e553 | 0..1 |

PT_SHLIB, unknown OS/processor/vendor types and GNU extensions not listed above
refuse FORMAT. Duplicate singleton headers refuse FORMAT. PT_NULL is retained
verbatim and ignored semantically: unused type0 entries need not zero otherwise
ignored fields, and no arithmetic or reads are derived from those fields.
For every other type reject flag bits outside PF_R|PF_W|PF_X. A PT_LOAD combining
PF_W and PF_X refuses FORMAT. A GNU_STACK with PF_X refuses FORMAT regardless of
PF_W. Absent GNU_STACK is represented as absence, not proof of actual
non-executable stack permissions; later admission must resolve that requirement.

For each non-NULL header, require p_filesz<=p_memsz, p_offset<=file_size and
p_filesz<=file_size-p_offset. Require checked unsigned addition for
p_vaddr+p_memsz and p_paddr+p_memsz, even for non-load location metadata; paddr
is retained only and never becomes a physical-memory access. p_align is0,1 or
a power of two. For PT_LOAD and PT_TLS with p_align>1 require
p_vaddr%p_align==p_offset%p_align. Every PT_LOAD additionally has equal low12
bits in p_vaddr and p_offset (the initial x86-64/4096-byte load-page branch).
This is a supported structural subset; ignored/non-load fields in other valid
ELF producer conventions are not silently treated as supported.

PT_LOAD headers appear in nondecreasing p_vaddr order. Nonempty half-open
virtual-memory byte intervals of distinct loads must not overlap; adjacency
and empty intervals are allowed. Page-rounded extents may overlap and are not
rejected by this byte-interval rule. File-byte ranges of distinct loads may
alias: this is retained explicitly and does not establish a unique future
load bias. A mapped-object adapter must resolve/reject ambiguity independently.
Non-load headers may overlap each other or loads (for example NOTE and
GNU_PROPERTY); a blanket overlap prohibition would reject ordinary layouts.

PT_INTERP and PT_PHDR, when present, precede the first PT_LOAD. PT_PHDR must
describe the exact program-header table: p_offset==e_phoff,
p_filesz==p_memsz==e_phnum*56. Its nonempty file/virtual extent must be covered
by one PT_LOAD with the same file-to-virtual displacement, established without
signed subtraction/overflow. For PT_DYNAMIC, PT_INTERP, PT_TLS,
PT_GNU_EH_FRAME, PT_GNU_RELRO and PT_GNU_PROPERTY, each nonempty file extent and
its virtual-memory extent must likewise fit one PT_LOAD with matching
displacement; TLS zero-fill may extend beyond its file-backed bytes but must
fit that load's memory extent. NOTE may be a nonloaded file note and therefore
requires only the generic extent rules. GNU_STACK carries permission metadata,
not an image location: require offset/vaddr/paddr/filesz/memsz all zero; its
allowed alignment and flags remain retained. This structural containment does
not interpret any associated payload.

### Typed refusals and acceptance

INVALID means violated argument/owner/scope/alias preconditions. FORMAT means
unsupported/malformed header or layout as above. IDENTITY means a retained-file
trust/descriptor identity change, first/second identity mismatch or mismatch to
the compiled expected SHA. PLATFORM means unavailable underlying supported host
or crypto prerequisite, not malformed file bytes. RESOURCE means an existing
shared object/byte/file budget or fixed parser count bound is exceeded; use
RESOURCE for phnum>128 or PT_LOAD>32 (PN_XNUM remains FORMAT as an unsupported
encoding). Other header/layout failures use FORMAT. CANCELLED and DEADLINE
retain their distinct original-control meanings. Map ES_HASH_FILE to IDENTITY,
ES_HASH_CRYPTO/ES_HASH_IO to IO, and other hash enums to their same-named ELF
meaning (CLEANUP always dominates). Unexpected enum values refuse PLATFORM.
No failure can fall back to layout success, a v2 result or runtime admission.

Acceptance uses invented byte-built ELF headers with independent expected
layouts, plus actual owned compiled x86-64 ET_DYN/ET_EXEC files. Include complete
record/order retention (DYNAMIC/INTERP/RELRO/TLS included), distinct identification
and format cases, exact128/129 and32/33 count boundaries, unsigned wraparound,
empty/adjacent/overlapping loads, allowed file alias and non-load overlap,
PHDR containment/order, wrong alignment, W+X/stack-X, section fields retained
without interpretation, high ET_DYN-relative addresses without overflow, two
measured hashes/counts, expected digest/identity mismatch, partial/EINTR/error
reads, original cancellation/deadline, borrowed offset/owner/alias preservation,
final cleanup uncertainty and exact reused-FD close-once witnesses wherever
existing hash helpers own a temporary descriptor. A source changing and then
restoring between observations remains outside atomicity claims, even when an
adverse control demonstrates an observable change is refused.

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
Failure carrying the completed arm ownership evidence defined in
[JNI ownership v1](privacy-jni-ownership-v1.md). `DisarmResult` is DISARMED or Failure; disarming grants no root admission
and cannot erase an earlier failure. `RootResult` is REGISTERED
or Failure. `Event` is FINAL_ADMITTED with a native-owned final identity token,
FAILED with Failure, or CLOSED. `Status` is STARTING, ADMITTED, FAILED or CLOSED.
`CloseResult` is COMPLETE or INCONCLUSIVE. Failure is one of PLATFORM,
INSTALLATION, SELF_PRIVACY, THREAD_SYNC, RESOURCE, IDENTITY, CHAIN, PROTOCOL,
DEADLINE, CANCELLED or CLEANUP. No result includes syscall text or raw output.

A token is a generation-checked native registry handle, not a descriptor/address.
Bound the registry to one live supervisor invocation and at most four concurrent
launches, with at most256 tokens issued per JVM invocation. Issuance never resets;
settled tombstones are neither reused nor evicted. An invalid/stale/wrong-invocation token refuses; it cannot touch a newly
reused descriptor. Successful close retains a tombstone until invocation teardown
so repeat close returns its original result, with no renewed deadline. Token
exhaustion refuses RESOURCE. `cleanupRemainingNanos` conveys the already-running Java
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

The exact result classes, JNI constructors, compiled-record boundary, registry
references/tombstones, parent lifetime and Java no-window propagation are frozen
in [JNI ownership v1](privacy-jni-ownership-v1.md). That ownership slice stops at
the mandatory missing-identity refusal; it cannot emit FINAL_ADMITTED or send
CHALLENGE before the complete mapped-image/loader strategy exists.

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

## Native single-root launch owner prerequisite

This section specifies the next private production C ownership layer, tested with test-only JNI through the existing PrivacyLaunchOwner.CapturePort. It does not implement the production PrivacyBridge, its token registry, trusted installation/self admission, a child guard constructor, CHALLENGE, FINAL_ADMITTED or runtime availability. Width1 is this prerequisite's implementation boundary, not a reduction of the advertised product support matrix. No wider compiled graph is accepted as width1.

### Exact C surface

All names below are private to the standalone tool. `es_launch` has a complete definition in privacy-launch.h so a native caller can allocate stable zero-initialized storage. It is never a Java address, descriptor or public token. Its members are implementation-private even when visible to the C compiler. The type is not copied, moved, reinitialized or destroyed while any admitted call, launcher window, pending cleanup or external thread may refer to it.

```c
typedef enum {
 ES_LAUNCH_OK=0, ES_LAUNCH_ROOT_CORRELATED=1,
 ES_LAUNCH_INVALID=2, ES_LAUNCH_PLATFORM=3, ES_LAUNCH_RESOURCE=4,
 ES_LAUNCH_IDENTITY=5, ES_LAUNCH_PROTOCOL=6, ES_LAUNCH_DEADLINE=7,
 ES_LAUNCH_CANCELLED=8, ES_LAUNCH_IO=9, ES_LAUNCH_CLEANUP=10
} es_launch_result;

typedef enum {
 ES_LAUNCH_PHASE_OPEN=0, ES_LAUNCH_PHASE_ARMED=1,
 ES_LAUNCH_PHASE_CAPTURED=2, ES_LAUNCH_PHASE_REGISTERED=3,
 ES_LAUNCH_PHASE_CORRELATED=4, ES_LAUNCH_PHASE_FAILED=5
} es_launch_phase;

typedef enum {
 ES_LAUNCH_CLOSE_NONE=0, ES_LAUNCH_CLOSE_REQUESTED=1,
 ES_LAUNCH_CLOSE_SETTLING=2, ES_LAUNCH_CLOSE_SETTLED=3
} es_launch_close_state;

typedef enum {
 ES_LAUNCH_CLOSED_COMPLETE=0, ES_LAUNCH_CLOSED_INCONCLUSIVE=1,
 ES_LAUNCH_CLOSE_INVALID=2
} es_launch_cleanup;

typedef struct {
 es_launch_phase phase;
 es_launch_result failure; /* OK or first operational refusal */
 es_launch_close_state close_state;
 unsigned disarm_completed; /* 0 or 1; never root or privacy admission */
 unsigned calls_quiescent; /* 0 or 1; snapshot only, not a free authorization */
 unsigned cleanup_inconclusive; /* sticky 0 or 1, may precede SETTLED */
} es_launch_status;

typedef struct es_launch es_launch;

es_launch_result es_launch_open(es_launch *owner,
 int borrowed_parent_fd, const char *admitted_parent_path,
 uint64_t operation_remaining_ns,
 char out_path[ES_LISTENER_PATH_BYTES]);
es_launch_result es_launch_arm(es_launch *owner);
es_launch_result es_launch_capture(es_launch *owner);
es_launch_result es_launch_register(es_launch *owner, uint64_t exact_returned_pid);
es_launch_result es_launch_disarm(es_launch *owner);
es_launch_result es_launch_correlate(es_launch *owner);
es_launch_result es_launch_status_read(es_launch *owner, es_launch_status *out);
es_launch_result es_launch_cancel(es_launch *owner);
es_launch_cleanup es_launch_close(es_launch *owner,
 uint64_t original_cleanup_remaining_ns);
```

OK means only that the named open/arm/capture/register/disarm/cancel operation completed its own prerequisite. Only correlate may return ROOT_CORRELATED. No function returns an identity, C pointer, raw descriptor or admission token. `out_path` is the native-created bounded private endpoint solely for constructing the fixed child environment; it is not a peer-selected path.

### Initialization, arguments and failure mapping

Fresh means never used, all-zero stable storage with no concurrent caller. open is exclusive and must finish before publication to launcher/receiver/control threads. No other call may race initial mutex/condition initialization. Validate non-null owner/path/output, nonnegative parent fd, owner/output/path memory non-overlap and strictly positive bounded duration before allocation or output mutation. Paths and output are caller-owned valid C memory of the documented bounds; no API purports to validate arbitrary pointers. An invalid/fresh precheck leaves owner and borrowed parent untouched; zero a distinct safe output buffer on refusal. If output aliases owner or input, refuse without writing through it. Bound path reads to ES_LISTENER_PATH_BYTES; the existing listener performs strict Unicode/path correlation and parent checks.

After native synchronization is initialized, mark the owner initialized before any eventfd, entropy or listener allocation. Every later open failure produces an initialized FAILED owner requiring close; all subsequent path outputs are zero. Do not report a leaked or uncertain partial open as a never-created object. An initialization failure must unwind initialized synchronization without live users and leave no owned descriptor; otherwise retain an initialized failed owner. Parent directory remains borrowed throughout, including close; it must retain the listener's admitted pathname/identity/mode and exclusive namespace preconditions. UID/mode0700 alone does not prove exclusion of hostile same-UID mutation.

open creates one EFD_NONBLOCK|EFD_CLOEXEC cancellation eventfd, generates a private16-byte launch ID using bounded native entropy, and opens the existing listener width1. No caller-supplied eventfd/launch ID or fallback entropy. Copy the admitted parent pathname into bounded owner storage; do not retain a caller string pointer. Pass the same immutable eventfd and startup deadline to every child owner. The native owner adds no alternative socket/process parser or numeric pidfd lookup.

operation_remaining_ns must be1..180,000,000,000 inclusive. Native monotonic arithmetic must be checked for overflow. At open entry compute the original operation deadline once, and startup deadline=min(operation deadline, now+10,000,000,000). Open allocation, arm, capture/register/disarm, pending peer, all waits and matching consume this same original startup deadline. A remaining duration is not a raw Java System.nanoTime absolute timestamp. No re-entry or signal renews it. This root-only slice never uses leftover operation time to extend root startup or accept another exec.

Safe mapping: unsupported platform/kernel/lock-free prerequisites→PLATFORM; listener capacity or bounded allocation/entropy exhaustion→RESOURCE; dead/mismatched root or peer→IDENTITY; frame/order/second-receiver faults→PROTOCOL except invalid/null API arguments→INVALID; original expiry→DEADLINE; latched cancellation→CANCELLED; remaining syscall failures→IO; owned close uncertainty→CLEANUP. Preserve the first operational refusal in status.failure, independent of cleanup uncertainty. A CLEANUP result/tombstone takes precedence when reporting uncertain teardown, without rewriting that original failure. Disarm's ended-window exception below remains separate. No errno text, PID, path other than successful out_path, native output or source value appears in results.

### Thread ownership and publication

There is one launcher, bound by the first admitted arm call, and one receiver, bound by the first admitted capture call. Both are dedicated platform threads in the Java test bridge. A receiver may call capture before arm: it binds its identity then waits for completed arm publication against the original startup/cancel/close conditions. A second capture call or a different receiver does not consume a second capture. The launcher never consumes frames; receiver never calls register or disarm. correlate must run on the bound receiver after its successful capture; it waits for completed register and disarm publication if they have not finished.

All shared bookkeeping, initialized/phase/failure/close flags, bound thread identities, active-call claims and descriptor-reference counts are protected by one private state mutex (or an equivalently documented atomic scheme). The existing root's disarm atomic fields remain its own internal contract; do not access its receiver-owned non-atomic state concurrently. All native waits release the state mutex. Never hold it across blocking root/listener/connection operations, entropy calls, JNI/Java calls or waiting for another active call. Publication after a primitive returns occurs under the mutex and wakes waiters. No condition wakeup is itself success: recheck stage, failure, cancellation and original clock.

arm claims exclusive root mutation, calls es_root_arm on the launcher, and publishes its completed immutable result before receiver can call capture. Only successful completed arm admits es_root_capture. A failed arm wakes the receiver with refusal, not a capture retry. Record whether a root arm actually initialized and whether its same-launcher disarm obligation remains; do not infer success from zero-initialized state.

capture claims the single root receiver operation after successful arm, calls es_root_capture without the state mutex, and publishes completion. register runs on the launcher; it boundedly waits for capture to complete, then claims the root mutation slot and calls es_root_register(exact_returned_pid) on that same launcher. The PID must come from PrivacyLaunchOwner's exact returned Process; C correlation alone cannot establish that Java provenance. A failed start does not call register. Duplicate register refuses and cannot mutate an admitted registration.

disarm always runs on the launcher finally path. It is allowed after cancellation/close/failure and may overlap capture only as es_root permits. It must not overlap an unfinished arm or register from another thread; wrong thread always refuses. Only es_root_disarm determines ended-window success. Its documented completed failed-arm positive-generation exception may return OK, but never clears owner.failure or permits capture/register/correlate retry. An early uninitialized/global-busy failure, wrong generation or duplicate disarm has no invented successful fallback. A completed root arm records affirmative, immutable no-window evidence only when it refused before calling fork arm, or fork arm refused before initializing its state and generation. In that case no disarm obligation was acquired: disarm still refuses, but does not introduce cleanup uncertainty, and quiescent close may settle all allocated resources without successful disarm. An attempted or unfinished arm, a generic failure, or absent evidence cannot establish this exception. Failed/unreturned disarm for an acquired or uncertain window keeps launch-window/cleanup uncertainty sticky. Record disarm completion with safe publication before correlation or final root close.

capture/register/correlate/close never concurrently operate on root. Apart from the expressly allowed disarm overlap, each primitive owner has one active call. correlate is admitted only after successful capture, exact registration and successful completed disarm, with no existing failure/cancel/close. It accepts one listener token, opens and reads one connection PREPARE, then calls es_root_match with that same shared-scope connection. Each boundary rechecks owner flags and startup deadline. A success that finishes after cancellation/close/expiry cannot be published as ROOT_CORRELATED. On success retain root, connection and listener ownership and publish CORRELATED once. No CHALLENGE/ACK, new accepts or second correlate occurs; the mock child remains blocked until cancellation/close.

### Cancellation and descriptor references

cancel may run concurrently after open publishes initialized storage. It first latches cancellation under the mutex, then acquires an active signalling reference to the current owned eventfd before dropping the mutex. It performs a nonblocking eventfd write and drops the reference under the mutex. Teardown may not close that eventfd while any such reference exists. No method reads a numeric fd then signals it after releasing the last ownership reference. A write returning EAGAIN means the eventfd is already readable/saturated; it is an adequate wake, not a drained/reset event. Do not consume cancellation to permit later success. Other write failure is latched IO and cannot authorize retry/admission. No unbounded retry on EINTR. The implementation must specify a finite syscall attempt policy and preserve refusal on failure.

Once eventfd teardown has been claimed, later cancel calls only retain the cancellation flag; they never write the closed number. After completed close, cancel/status/repeat-close cannot affect another owner or reused fd. A cancel after CORRELATED changes it to FAILED and retains cleanup ownership. It does not mean root process termination was established.

### Cleanup state and storage lifetime

close may be concurrent with launcher/receiver work and repeated by multiple control callers. The first initialized-owner call latches close requested and cancellation, fixes a cleanup deadline to native now+min(original_cleanup_remaining_ns,10s), and wakes waiters. Zero or >10s remaining is an invalid budget: fix deadline to now, latch permanent inconclusive, still request best-effort teardown. Arithmetic failure likewise cannot extend a deadline. Later calls only shorten the shared deadline; all existing close waiters must notice shortening within bounded polling (<=1ms), not keep a captured older deadline. This budget is already running in the caller, not a fresh allowance for each close.

CLOSE_REQUESTED prevents new work admission except the original required finally-disarm and cleanup/control/status calls. Wait for all active primitive operations and signal references to end and for the launcher window to be conclusively ended. Waiting does not hold a mutex those calls need. Expiry before quiescence returns CLOSED_INCONCLUSIVE and permanently sets cleanup_inconclusive, but does not free storage, close an active fd or claim SETTLED.

One caller or final leaving operation claims CLOSE_SETTLING under the mutex only when all required primitive users have ceased. This cleanup claim itself holds owner lifetime. Other close callers wait only to the currently shortened deadline. Close connection (if initialized), root (if initialized), listener (if initialized) and the owned eventfd independently, even after an earlier failure. Connection must settle its transferred listener token before listener close. Read the current shared cleanup deadline before each subordinate close and recheck it after; a shortening during a synchronous syscall cannot interrupt that syscall, but must prevent a late COMPLETE result. Never re-close a numeric fd whose one attempt may already have released it. Keep all subowner uncertainty/tombstones; borrowed parent remains untouched.

If another active operation outlives close's bounded wait, that original operation's leaving path must request the same one cleanup settlement when it becomes safe. It cannot erase the permanent inconclusive flag or create a second close owner. If a required launcher never returns/disarms, retain requested/inconclusive ownership; no other thread disarms its TLS or frees its state. Best-effort termination of the exact Java-owned Process belongs to PrivacyLaunchOwner and does not prove native TLS cleanup.

CLOSE_SETTLED means every owned cleanup attempt has actually finished, no native primitive/signal reference remains, and each descriptor is confirmed closed or recorded uncertain. If any closure/clock/window outcome is uncertain, return CLOSED_INCONCLUSIVE forever even if late work eventually ends. CLOSED_COMPLETE requires conclusive closure within the shortest admitted cleanup budget and no prior cleanup uncertainty. Original operational refusal does not by itself force cleanup uncertainty: a fully ended failed arm/peer refusal can still clean completely. Preserve first operational failure separately.

No destructor/free/reinitialization API exists in this slice. The enclosing native allocation owner may reclaim storage only after externally joining all threads that could still call it, all calls and cleanup claims have ended, and no armed root/TLS references remain. status.calls_quiescent is merely a snapshot and cannot authorize reclamation. Completed tombstones remain readable for the enclosing owner lifetime; repeat close returns their exact outcome without touching descriptor numbers. Mutex/condition destruction is not performed by close while status/repeat callers can still enter.

status_read is a bounded synchronized copy only. It does not consume a frame, advance state or turn CORRELATED into runtime admission. Invalid/null/overlapping output refuses without altering the owner; zero a distinct safe output. Valid status read can report FAILED and requested/inconclusive after operational refusal. Other invalid operations on an initialized live owner latch INVALID/PROTOCOL as applicable; they cannot reopen its lifetime. Fresh/uninitialized/null-owner calls return INVALID and do not touch synchronization state. Output zero values are never a success substitute; callers must inspect the result.

### Acceptance and explicit exclusions

Actual test-only JNI uses existing PrivacyLaunchOwner with fixed FORK and exactly one invented executable whose constructor sends PREPARE and waits. Exercise receiver-before-arm, capture-before/after Java start result, register waiting for capture, finally-disarm, same-root correlation and preserved stdout/stderr; require no CHALLENGE and no main marker before cancellation/cleanup. It is an ownership prerequisite, not installed JNI or constructor/loader admission.

Adverse schedules and faults: partial eventfd/listener open; entropy failure; completed failed-arm disarm exception and early failure refusal; no hook/POSIX_SPAWN; wrong-thread/duplicate calls; early foreign peer; mismatched root; deadline/cancel at all waits and after a returning primitive; close while capture/register/disarm/correlate active; late completed work cannot restore success; shortened concurrent close wait; exact-number eventfd reuse while stale cancel/status/close run; signal reference held across teardown request; uncertain connection/root/listener close still attempts other owners; partial-open borrowed parent survives; second owner cannot be affected. Close-out tests join/reap every owned mock thread/process and preserve stuck-scheduling tests as injected schedules rather than actual ProcessBuilder stall proof. Compile targeted mutations and count actual assertion failures, not setup errors.

All existing namespace, kernel pin, frame, close-once, output wiping and original-clock preconditions remain. No memory/maps reader, hash context, image/ELF call, loader/quiescence proof, exec-generation attestation, script/client execution, full fork/exec graph, crypto budget reset, token registry, library installation, parent self suppression, credentials, registry/readiness or production JNI method is introduced. Future memory inspection must close every held mem fd conclusively before CHALLENGE; dumpable0 does not revoke it. Two sampled identities/byte sequences remain non-atomic. The next identity stage must extend this same owner, not introduce a parallel coordinator.

## Bounded maps sampling extension

The next private receiver-owned prerequisite is specified in
[privacy-maps-v1](privacy-maps-v1.md). It extends the existing launch owner
without granting image, loader, privacy or client admission.

The following [owned executable inspection](privacy-owned-image-v1.md) composes
existing file/image/ELF primitives under the same launch and hash ownership. It
adds structural executable evidence only; loader and runtime admission remain
unqualified.
