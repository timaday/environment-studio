# Private bounded maps sampling prerequisite

This extends the single-root owner in privacy-abi-v1. It samples structural
mapping evidence through the existing retained kernel peer. It establishes no
atomic process snapshot, exec generation, mapped byte identity, loader closure,
privacy or client admission. No memory descriptor, CHALLENGE, production JNI,
new token registry or alternate coordinator is introduced.

## Exact private C surface

```c
#define ES_MAPS_RECORDS 4096U
#define ES_MAPS_BYTES 262144U
#define ES_MAPS_LINE_BYTES 8192U
typedef enum {
 ES_MAPS_OK=0, ES_MAPS_INVALID=1, ES_MAPS_PLATFORM=2, ES_MAPS_RESOURCE=3,
 ES_MAPS_IDENTITY=4, ES_MAPS_FORMAT=5, ES_MAPS_DEADLINE=6,
 ES_MAPS_CANCELLED=7, ES_MAPS_IO=8, ES_MAPS_CLEANUP=9
} es_maps_result;
typedef struct {
 uint64_t start,end,offset,inode;
 uint32_t device_major,device_minor;
 uint32_t read,write,execute,shared;
 uint32_t label_offset,label_length;
} es_maps_record;
typedef struct {
 es_peer_identity identity;
 uint32_t count,byte_length;
 es_maps_record records[ES_MAPS_RECORDS];
 unsigned char bytes[ES_MAPS_BYTES];
} es_maps_snapshot;
es_maps_result es_maps_sample(es_peer *borrowed_peer,es_maps_snapshot *out);
/* Added to privacy-launch.h; only the bound receiver after ROOT_CORRELATED. */
es_launch_result es_launch_maps(es_launch *owner,es_maps_snapshot *out);
```

The complete bounded output is caller-owned stable native memory, not Java data
or a public response. It includes the exact first complete maps bytes. Record
labels are offsets into that arena, never pointers. Zero unused records/bytes on
success. The caller wipes the entire output after use. Assert the complete output
fits576KiB; this is an implementation ceiling, not a claim that combined privacy
state fits its existing1MiB budget. Whole-runtime measurement remains mandatory.

Null/fresh/unadmitted peer or overlapping peer/output refuses without allocating
or corrupting owner memory. Validate overlap before output wiping; zero distinct
safe output on all refusals. Caller supplies valid memory of documented bounds;
no arbitrary pointer validation is promised. Sampling borrows the original live
peer, cancellation fd and startup deadline; it never acquires a new numeric pidfd,
replaces a pin, resets a cancellation flag or renews a clock. Existing es_peer_read
owns process validation and any terminal pin cleanup. Do not duplicate its parser.

## Complete sampling and grammar

Recheck the original peer before and after every bounded operation and before
publication. Open only its fixed numeric /proc/PID/maps leaf with readonly,
CLOEXEC, NONBLOCK and no-follow controls; verify actual PROC_SUPER_MAGIC. Never
accept a caller path or filesystem fallback. Each temporary descriptor has one
close attempt on every outcome. Keep its number tombstoned before attempting close.

Read a first complete EOF-terminated nonempty stream with at most256KiB,4096
records and8192bytes per line including newline. Exact maxima require a separate
bounded EOF check, not prefix success. No raw text copy may exceed its declared
bound. Parse complete newline-terminated records; reject embedded NUL or a final
unterminated line. A record is: lowercase hex start, '-', lowercase hex end,
one ASCII space, exact four permission bytes, one space, hex file offset, one
space, hex device major, ':', hex minor, one space, decimal inode, then either
newline or one-or-more ASCII spaces and an opaque remainder before newline.
Hex values accept leading zeros and1..16 digits; device fields must fit uint32.
Decimal inode accepts digits with checked uint64 arithmetic. Permissions are
`[r-][w-][x-][ps]`; record booleans are0/1 and shared means final's'.

Require start<end and increasing nonoverlapping intervals; adjacency is allowed.
Check all additions/conversions. No page-size, readable-user-address, path,
device/inode trust or ELF assumptions are inferred by this parser. Preserve high
architecture ranges and anonymous/shared/executable/special mappings for later
policy. Unknown permission syntax and malformed columns refuse. Do not silently
discard rows to fit bounds or hide unexpected executable coverage.

label_offset/length cover the exact remainder after the inode digits, including
any separator/padding spaces, excluding newline. Zero length is allowed. Preserve
spaces, deleted suffix, escapes and non-UTF8 bytes as opaque; do not trim/decode or
turn ambiguous proc labels into trusted filenames. A literal newline is a record
delimiter; kernel-escaped pathname bytes remain literal backslash digits.

After closing the first descriptor, independently reopen and read a second full
stream, comparing byte-for-byte and complete EOF with the first under the same
original controls. Use bounded small comparison scratch, not another full arena
or second record array. A changed byte, length or extra record refuses IDENTITY.
Close the second descriptor conclusively and recheck original peer/control before
publishing output. Equal samples remain non-atomic evidence: process start time
and pidfd do not pin a single mm across exec, and a maps iterator may release locks.

Unsupported platform→PLATFORM; bounds→RESOURCE; dead/mismatched/changed process
or changed second sample→IDENTITY; grammar→FORMAT; original expiry→DEADLINE;
cancellation→CANCELLED; other read/open/filesystem syscall failure→IO. Wrong
filesystem is PLATFORM. Owned close uncertainty dominates→CLEANUP, wipes output
and remains separately sticky. Preserve failures without raw errno/messages,
paths, addresses, mappings or model data in diagnostics. Bound EINTR handling by
the original deadline and at most32 retries shared across both streams; the33rd
interrupted read refuses IO. No success on a short,
failed, cancelled or late operation. A synchronous syscall may outlive its budget;
do not claim interruption or free storage while it remains active.

The raw sampler has no persistent maps owner. Its caller must retain a CLEANUP
result and must not erase that uncertainty by sampling again. It does not mark
the borrowed peer failed solely because closing a temporary maps descriptor was
inconclusive. The launch wrapper below owns and enforces the persistent latch.

## Original launch-owner composition

es_launch_maps is callable once, by the bound receiver, after successful root
correlation, before close/cancel/expiry. Validate safe output/owner overlap first.
Claim the existing active primitive slot and users reference under the owner
mutex, then sample its same connection.peer outside that mutex. No alternative
peer, caller address range or new deadline is accepted. Duplicate/wrong-thread/
wrong-phase sampling latches protocol refusal; fresh/null owner refuses INVALID.

Map results to the existing launch result vocabulary: FORMAT→PROTOCOL,
RESOURCE→RESOURCE and the remaining corresponding result names directly.
Operational refusal latches the first failure. CLEANUP additionally marks owner
cleanup_inconclusive forever, independently of the first operational code. A late
success after cancel/close/expiry is wiped and refused before return. On success
return OK for sampled evidence only; keep phase CORRELATED without any new privacy
admission. Root/connection/listener ownership remains for subsequent qualified
work. All existing stages still cannot rerun.

Concurrent close waits against the same shortened cleanup deadline and cannot
close root/connection/eventfd while sampling is active. When sampling leaves, it
requests the existing one settlement. A late caller must not return mappings or
clear prior uncertainty. Borrowed parent and externally joined lifetime rules
remain unchanged. No output is retained in es_launch after the call returns.

## Acceptance and exclusions

Initial meaningful RED uses an actually owned socket-correlated invented child
with a known file mapping. Independently assert its exact range/offset/device/
inode/permissions from the test mapping operation, plus complete ordinary rows.
Actual split/nonzero-offset, deleted/space/newline/non-UTF8 pathname and anonymous
executable mappings are represented, never self-approved. Supplement with bounded
synthetic complete streams for exact/one-over maxima, fragmentation, grammar,
overflow, ordering, malformed/truncated EOF and second-pass differences. Test
aliases, late return, cancellation/expiry at read/EOF/close, child death, wrong
filesystem, independent close uncertainty and exact-number reuse. Test original
launch receiver/once-only ownership and close during held sampling; retain
remaining independent cleanup. Test wrappers are explicit injected schedules,
not proof of actual stalled kernel syscalls.

No private process/configuration material is read in tests. No pointer/address/
mapping dumps in logs, snapshots or stdout. Qualify actual fixtures and compiled
mutants, preserve setup failures, and retain existing native/JNI controls.
Trusted installation, exec quiescence, mm identity, executable-byte/file association,
ELF dynamic tags, relocations/RELRO, JIT/vDSO/dlopen policy and memory-read budget
remain subsequent gates. No /proc/PID/mem access occurs; a future retained memFD
must close conclusively before CHALLENGE because suppression does not revoke it.
