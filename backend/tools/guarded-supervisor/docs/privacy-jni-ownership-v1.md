# Production JNI launch ownership — private v1 contract

This supplements [privacy ABI v1](privacy-abi-v1.md). It composes the existing
controls, fork/root, connection and es_launch owners into the production JNI
lifecycle. It adds no separate inspector, public command, runtime qualification
entry or client support restriction. Production bootstrap, packaging, complete
mapped-image/loader strategy and client wiring remain subsequent integration.
The production compiled record registry stays empty.

## Exact Java boundary

All types below are nested in the package-private final class
`studio.environment.supervisor.PrivacyBridge`; nested types have package access.
Use the following closed types and names. Records expose only the listed
components. Constructors and enum constants are the exact JNI construction ABI.
Records containing a path/token override toString with a fixed redacted label.
Failure records may expose only their enum values in diagnostic formatting.

```java
enum Failure { PLATFORM, INSTALLATION, SELF_PRIVACY, THREAD_SYNC, RESOURCE,
    IDENTITY, CHAIN, PROTOCOL, DEADLINE, CANCELLED, CLEANUP }
enum ArmOwnership { NO_WINDOW_ACQUIRED, DISARM_REQUIRED_OR_UNKNOWN }
sealed interface SelfResult permits SelfEstablished, SelfFailed { }
enum SelfEstablished implements SelfResult { ESTABLISHED }
record SelfFailed(Failure failure) implements SelfResult { }
sealed interface OpenResult permits Opened, OpenFailed { }
record Opened(long launch, String socketPath) implements OpenResult { }
record OpenFailed(Failure failure) implements OpenResult { }
sealed interface ForkResult permits ForkArmed, ForkFailed { }
enum ForkArmed implements ForkResult { ARMED }
record ForkFailed(Failure failure, ArmOwnership ownership) implements ForkResult { }
sealed interface RootResult permits RootRegistered, RootFailed { }
enum RootRegistered implements RootResult { REGISTERED }
record RootFailed(Failure failure) implements RootResult { }
sealed interface DisarmResult permits ForkDisarmed, DisarmFailed { }
enum ForkDisarmed implements DisarmResult { DISARMED }
record DisarmFailed(Failure failure, ArmOwnership ownership) implements DisarmResult { }
sealed interface Event permits FinalAdmitted, EventFailed, EventClosed { }
record FinalAdmitted(long identity) implements Event { }
record EventFailed(Failure failure) implements Event { }
enum EventClosed implements Event { CLOSED }
enum Status { STARTING, ADMITTED, FAILED, CLOSED }
enum CloseResult { COMPLETE, INCONCLUSIVE }
```

Every enum/record component must be nonnull. Opened requires a positive opaque
token and the successful native listener's exact nonempty path, at most103 UTF-8
bytes, with no NUL, preserving the existing listener rules. FinalAdmitted requires
a positive opaque native identity token; it cannot be constructed by this slice.
Record invariants are checked in Java constructors and before JNI construction.
No identity/path supplied by a peer or Java caller becomes policy.

Preserve these exact static package-private native method signatures:

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

RegisterNatives uses those names and JVM descriptors. Result descriptors are
`Lstudio/environment/supervisor/PrivacyBridge$TYPE;`. Thus establishSelf is
`(I)Lstudio/environment/supervisor/PrivacyBridge$SelfResult;`, openLaunch is
`(IJ)Lstudio/environment/supervisor/PrivacyBridge$OpenResult;`, armFork/disarmFork/
nextEvent/status each use `(J)` and their corresponding result descriptor;
registerRoot and closeLaunch use `(JJ)` and RootResult/CloseResult; cancel is
`(J)V`. There is no dynamic method/provider discovery.

Cache and validate the exact class/method/enum identities during JNI_OnLoad
before any invocation allocation or suppression. The single defining classloader
and exact declaring class own this boundary. Fail linkage on missing/mismatched
classes, constructor descriptors or registration; never expose a partially
registered bridge. Failure constructors are `(L...$Failure;)V`, except ForkFailed
and DisarmFailed which are `(L...$Failure;L...$ArmOwnership;)V`. Opened is
`(JLjava/lang/String;)V` and FinalAdmitted is `(J)V`. Here `L...$` abbreviates
`Lstudio/environment/supervisor/PrivacyBridge$` only in this prose. Enum values
are cached global references, not constructed with NewObject.

No generic System.load pathname API, provider records, boolean admission callback
or public test factory is added. Bootstrap must eventually load only the fixed
admitted distribution library under the original trusted-installation contract.
This document does not itself implement or relax that bootstrap.

## Invocation and compiled records

There is exactly one native invocation lifetime per JVM, bound to its original
registered classloader/class and admitted self mechanism. Repeated self admission
cannot create another invocation, renew limits or restart a failed lifetime.
A failed self attempt is terminal for this invocation; return its fixed failure.
A completed successful repeat reports the existing established result without
reinstalling controls. No unload/reload or alternate classloader can reset the
invocation. Retain required native storage/global references until JVM termination.
No teardown/reset method is added to the frozen JNI API.

Production immutable native tables resolve only positive compiledMechanism and
compiledChain ordinals. A mechanism record identifies exact platform/JDK/library
installation and suppression prerequisites. A chain record identifies its owning
mechanism, fixed chain/installation digests, parent-namespace installation policy,
expected root and complete graph/closure policy reference, pending width and
required identity-stage version. Records are tool-owned compiled native data,
never JNI arguments, Java-configured providers, mutable policy or peer input.
Unknown ordinals, missing fields/strategy versions or unavailable admission
prerequisites return INSTALLATION. No default chain, wildcard or empty closure
hash is allowed. The lookup returns either the exact immutable record or a closed
missing/unsupported result; it must not return a generic success boolean.

Production tables have zero usable records in this slice. A separately linked,
credential-free test artifact may contain independently invented installation
records for lifecycle tests. It uses the same production registry/JNI/owner code,
but its synthetic tables are never compiled into, copied into or loaded by the
distributed library. No ordinary flag, environment value, config field or package
can select those tables. Test records do not authorize client admission.

The one invocation owns one descriptor for a pre-existing admitted parent
directory and its exact fixed pathname/identity policy. The external trusted
installation owns that base namespace; JNI neither creates nor deletes it. openLaunch never accepts either from Java.
Parent admission uses the existing no-follow/ownership/exclusive-namespace rules;
a pathname or mode0700 alone does not establish those rules. The parent descriptor
is borrowed by each es_launch and remains valid through every subordinate close,
including late/quarantined calls. Each launch owns its newly created private
listener directory/socket and every descriptor/crypto allocation acquired by its
native primitives. It never deletes or closes the borrowed parent on launch close.
CloseResult.COMPLETE requires all of those launch-owned resources to have settled
conclusively within the original cleanup budget; no child endpoint/descriptor may
be deferred to JVM exit while reporting launch COMPLETE.

Retain the invocation parent descriptor until JVM termination in this slice;
no per-launch close implies parent release. Its CLOEXEC state excludes children.
If invocation initialization fails before a launch can borrow it, attempt its
owned close once, tombstone before close and retain any uncertainty in the failed
invocation. Do not retry its numeric descriptor or reuse a failed invocation.
The admitted base descriptor is the sole namespace descriptor excluded from
per-launch cleanup by this explicitly separate invocation ownership. Do not
allocate another invocation-owned descriptor to conceal a launch cleanup failure.

The standalone JVM process owner is responsible for ending this invocation after
its original Java launchers/coordinators/processes/pipes and terminal cleanup have
settled, and for observing JVM termination. JVM termination releases the retained
base descriptor and native allocations; it does not delete the externally owned
base directory or prove that child-created endpoints were removed. A launch
COMPLETE result does not assert whole-invocation termination or base-FD release.
If the outer owner cannot establish termination, whole-invocation cleanup remains
INCONCLUSIVE. The production bootstrap integration must implement and test that
external ownership boundary before claiming whole-runtime cleanup. This contract
does not add an early parent-close/destructor/reset API, and never closes the base
while a late or quarantined native borrower can still use it.

## Tokens, references and tombstones

At most four launches are live concurrently, and at most256 tokens are issued in
the entire JVM invocation. Use256 bounded native registry entries; a published
entry is never reused or evicted. Allocation reserves an unused entry and the
four-live capacity under one registry mutex. Reservation consumes its issuance
number even if later native/open/JNI publication fails. Neither failure, close,
new exec nor a later call resets the issuance counter. Exhaustion returns RESOURCE
before creating another listener or launcher. Failed records cannot be overwritten
to manufacture capacity; settled records may release live capacity only as below.

Each token is a positive opaque generation-checked handle, never an address or FD.
Its native entry retains immutable issuance generation and invocation identity.
For issuance number n in1..256, entry index is n-1, generation is n, and the token
is `(generation << 9) | (index + 1)`. Decode the low9 bits as the one-based entry
index and all remaining bits as generation; both must match the reserved entry.
No bit is ignored. Invocation ownership is the original bound native/classloader
context, not a caller-supplied token field; another invocation is never admitted
in this JVM. Validate exact registered caller class/invocation, representation,
index, generation and published state under the registry mutex before taking a
reference.
A representation must reject zero/negative, out-of-range and altered-generation
values. Never truncate a jlong, wrap generation arithmetic or use an unchecked
value as an index/pointer. Generations are monotonic and never reset. Token secrecy
is not the authorization boundary: this is a private trusted JVM API, not an
interprocess capability or an API accepting external tokens.

The registry owns stable native es_launch storage. Every admitted JNI operation
acquires a counted reference under the registry mutex before accessing it and
releases that reference on every return/exception. No Java raw pointer/FD exists.
Release the registry mutex before owner locks, native I/O, JNI object construction,
Java callbacks or waits. Do not hold an es_launch mutex while acquiring the
registry mutex. JNI references protect storage; the existing es_launch users and
primitive/signal claims determine when its descriptor cleanup may settle.

Keep each initialized native owner and its synchronization objects allocated
and stable through JVM termination. A registry refcount alone does not supply
the existing primitive contract's external launcher-thread completion/join proof;
do not introduce owner destruction/reclamation based on a quiescence snapshot.
After retirement, all future token lookups use only the immutable registry
tombstone and never call into that retired es_launch, even though its bounded
allocation remains retained. Late status returns CLOSED, repeat close returns
its recorded native outcome, nextEvent returns CLOSED, cancel performs no owner
access, and arm/register/disarm refuse. Retirement must therefore wait until
the original required finally-disarm has returned successfully or with the exact
proven no-window refusal; it cannot strand that permitted late call. Acquired or
unknown unfinished finally obligations retain the live entry and quarantine.
No retired allocation is reinitialized or reassigned to another token.

Mark a record settled only after original native cleanup is actually SETTLED and
all original launcher/coordinator primitive activity, signal claims and required
finally obligations have ended. Record this as retirement-pending under registry
ownership, prevent new work admission, and drain every existing registry reference
including result construction. Only after that drain atomically publish the
immutable tombstone and release its four-live slot once. Control calls during
the drain cannot acquire a raw pointer after tombstone publication. Native owner
SETTLED alone does not erase an unfinished original finally obligation; close
cannot report this bridge's COMPLETE until that obligation is conclusively ended. Preserve original failure, cleanup result and token tombstone.
An unreturned call, pending armed window or uncertain-unsettled owner retains live
capacity. A SETTLED but inconclusive owner may release live capacity only after the same
reference and obligation drain; its fixed inconclusive result and native storage
remain. Retired COMPLETE entries retain no launch-owned descriptor or namespace.
Retired INCONCLUSIVE entries record the unestablished close/namespace outcomes;
retirement does not prove those resources absent or authorize a numeric-FD retry. Do not confuse slot release with
whole Java/process/terminal cleanup or runtime admission.


### Finite retained storage

The implementation must enforce compile-time layout assertions and bounded
allocation accounting, not infer a memory guarantee from token count alone:

- Stable primitive-owner allocation: at most16KiB for each issued entry, including
  es_launch and any enclosing native ownership fields; at most4MiB across256.
- Separate immutable retirement metadata: at most256bytes per entry, at most64KiB;
  it contains generation, terminal status/failure/cleanup and no resource handle
  that later calls can use to re-enter a retired owner.
- Invocation registry mutex/counters/base-path identity bookkeeping: at most4KiB.
- At most four active launches, each retaining the existing1MiB total mutable
  privacy-state/scratch bound, including its stable owner and live metadata.
  Maps/frame/challenge/JNI-operation scratch is released/wiped when its owning
  call ends; it is never retained once an entry retires.

Thus this slice has a conservative native storage ceiling of8MiB+68KiB for its
registry/owners and four active privacy workspaces (the bound deliberately double
counts the active owners). This is a hard implementation bound for this slice,
not a claimed measurement or whole-JVM/OpenSSL/allocator/thread-stack budget.
Immutable compiled installation tables, fixed cached JNI global references and
crypto/runtime allocations retain their separate qualification/accounting duties;
none may hide per-token unbounded mutable state. The implementation must report
actual sizeof values, peak owned allocations and residual256-tombstone storage.
If an owner cannot fit16KiB, stop for contract review rather than increasing the
limit, discarding proof fields or using an unbounded side allocation. Full-runtime
memory and maximum-workload FORK qualification remain required.

Invalid/stale/wrong-invocation tokens return fixed PROTOCOL failure for typed
operation/event results (DisarmFailed also carries DISARM_REQUIRED_OR_UNKNOWN),
Status.FAILED for status, and CloseResult.INCONCLUSIVE
for close. armFork returns ForkFailed(PROTOCOL,DISARM_REQUIRED_OR_UNKNOWN): absence
of a valid owner is not evidence that some prior attempted arm acquired nothing.
Void cancel makes no success claim and must not touch any owner/FD on invalid
input. For a valid owner it latches cancellation and uses the original eventfd
wake; any wake failure remains native failure/uncertainty as specified by its
owner. No caller may infer cleanup success merely because cancel returned.

## Original clocks and single coordinator

openLaunch accepts1..180,000,000,000 remaining operation nanoseconds, additionally
bounded by its compiled chain. Check signed input and overflow before conversion.
Capture CLOCK_MONOTONIC at the actual native openLaunch entry before registry,
record or parent work. Create operation deadline=entry+validated remaining budget
and startup deadline=min(operation deadline,entry+10s), with checked arithmetic.
The private es_launch_open_started helper receives this trusted native entry time;
it is never a Java/peer absolute timestamp or configurable policy. It rejects a
zero/future timestamp and expired/overflowed original deadlines. The existing
es_launch_open C API remains a wrapper that captures its own native entry time.
Listener/root/control owners receive these exact clocks from inception; do not
pass a shortened startup duration as a substitute for the retained operation
clock or overwrite clocks after a subordinate opens. Allocation, parent/record checks, root capture, all expected
execs, identity and handshakes consume it. The Java lifecycle receives and uses
only remaining time from its existing original budget; no cross-language epoch
assumption or newly started10s allowance is permitted.

A single dedicated Java platform coordinator calls nextEvent for an owner. Bind
it on first admitted call; reject another/concurrent coordinator without consuming
a frame or returning a successful event. It may begin before arm and calls the
existing es_launch_capture waiting path, then es_launch_correlate after exact
Java registration and successful required disarm. The original dedicated launcher
alone calls armFork/registerRoot/disarmFork, using the exact Process returned by
PrivacyLaunchOwner's sole ProcessBuilder.start. A caller PID alone never grants
root ownership. The coordinator uses the existing owner, not another listener,
root capture, pin parser, cancel descriptor or deadline.

After correlation, invoke the mandatory compiled identity stage. This slice has
no complete mapped-image/loader/exec strategy: return EventFailed(INSTALLATION),
latch FAILED, wake control/cleanup and send no CHALLENGE. An unknown chain already
refuses at open. A separately linked fixture may exercise root correlation before
this missing-stage refusal; it cannot provide a verifier that returns admission.
No ROOT_CORRELATED, maps equality, file hash, ELF layout or receipt count maps to
Status.ADMITTED or FinalAdmitted. Those existing final vocabulary values remain
reserved for the later complete strategy. nextEvent after settled cleanup returns
EventClosed.CLOSED; failure stays terminal and cannot restart correlation.

closeLaunch receives the already-running Java cleanup remaining budget. Native
first close fixes a deadline no later than that remaining time or10s; repeats can
only shorten it. Nonpositive, >10s or overflowed durations request immediate
best-effort cleanup and latch inconclusive. Status/cancel/failed calls never renew
a deadline. Waits release locks needed by returning calls. A synchronous syscall
may outlive its budget; retain its reference/storage and pending cleanup, never
claim it was interrupted. Java must join its original launcher/coordinator and
close its exact Process/pipes/terminal under its existing deadline. Native COMPLETE
is only native cleanup, never those Java outcomes.

## No-window evidence through Java finally

armFork returns ForkArmed.ARMED only from successful original es_launch_arm.
On refusal it returns NO_WINDOW_ACQUIRED only from that completed original arm's
affirmative immutable root no-window evidence. Entry rejection, linkage failure,
exception, missing/unreturned result, zeroed owner fields, a generic refusal or
unchanged phase cannot create that evidence. Otherwise return
DISARM_REQUIRED_OR_UNKNOWN. The record is published only after arm finishes.

Replace the private CapturePort.arm Step result with the exact ForkResult (or a
lossless internal adapter retaining both failure and ArmOwnership); adapt disarm
to preserve DisarmResult without inventing successful DISARMED. No new public API
or alternative process-creation seam is introduced. Java stores the completed arm
result before finally and cannot overwrite it with a later refusal.

The launcher still calls finally-disarm after entering arm. A valid no-window
attempt's native disarm returns the expected refusal, not DISARMED. Java may end
its own launch-window obligation and release its semaphore only on original
successful disarm, or affirmative NO_WINDOW_ACQUIRED plus the expected
DisarmFailed(PROTOCOL,NO_WINDOW_ACQUIRED) corresponding to the native INVALID
no-window refusal. The bridge publishes that disarm evidence only for the first
finally-disarm on the original bound launcher and the exact completed affirmative
no-window arm generation/attempt. All other disarm failures carry
DISARM_REQUIRED_OR_UNKNOWN, including wrong-thread, duplicate or token rejection.
The arm operation remains refused; no register, process start, identity or admission
retry follows. Native cleanup may still fail independently. Repeated arm/disarm,
wrong-thread calls or a different generation cannot borrow this exception.

A disarm exception, missing result, explicit CLEANUP or other unexpected failure
retains Java uncertainty/quarantine even after affirmative no-window arm evidence.
Acquired/unknown arm obligations always require the original successful disarm.
Do not use successful close alone to release an uncertain Java window. The
completed failed-arm positive-generation ended-window exception remains governed
by es_root_disarm and is separately represented as actual DISARMED.

## JNI failure paths and result mapping

Before a native operation, validate arguments and acquire the registry reference;
afterward publish its result/failure while retaining that reference until JNI
construction finishes. Cache enum references; use bounded local-reference frames
and no pinned Java arrays across I/O. Native scratch follows the existing1MiB
per-launch ceiling, including active owner/registry-related mutable state; total
retained256-entry owner/tombstone storage is separately bounded and must be measured.
Do not allocate1MiB for every tombstone or retain maps/frame scratch after a call.

Native launch INVALID/PROTOCOL map to PROTOCOL, PLATFORM/IO to PLATFORM, RESOURCE
to RESOURCE, IDENTITY to IDENTITY, DEADLINE/CANCELLED/CLEANUP to their matching
Failure. Preserve existing first operational failure and independent cleanup
uncertainty; mapping cannot erase either. es_privacy PLATFORM maps to PLATFORM,
SUPPRESSION/RESET_CONTROL to SELF_PRIVACY and THREAD_SYNC to THREAD_SYNC.
Only an actual completed successful establish result maps to ESTABLISHED.

If JNI allocation/record construction throws after native state changed, retain
that pending Java exception, latch an operational RESOURCE refusal in the original
native owner if no earlier refusal exists, request its cancellation and retain
all acquired ownership. Never clear the exception to return null/zero as success.
The Java wrapper treats linkage/allocation/constructor exceptions as fixed refusal
with DISARM_REQUIRED_OR_UNKNOWN; finally cleanup still runs. An arm which succeeded
before result allocation failed still requires its same launcher finally-disarm.
A returning coordinator exception cannot strand an unreferenced native launch.

If Opened allocation/publication fails after resource allocation, Java receives no
usable token. The registry remains its cleanup owner: request bounded native
cleanup using the remaining original startup allowance capped at10s, retain any
pending/inconclusive owner in the reserved entry and retain live capacity until
settled. This cleanup never creates a fresh startup allowance or retries open.
Partial eventfd/listener/entropy allocation follows the existing initialized-owner
cleanup contract. Failures before native initialization unwind each acquired
resource once; unknown close outcomes stay recorded. All native references and
JNI local frames are released on exception paths. No cleanup calls arbitrary Java
callbacks or allocates unbounded replacement result objects during an OOM path.

If failure occurs constructing a result after native complete close, the native
tombstone remains complete; the Java caller cannot report whole cleanup complete
from an exception. A later valid close may read that same native tombstone, while
Java's own original uncertainty remains sticky. Linkage failure before any native
entry supplies no ownership proof and never selects another library/launch mode.

## Acceptance before implementation integration

Start with exact API scaffolds that refuse. Observe a real failing lifecycle test
through the production registry/JNI/PrivacyLaunchOwner path: an independently
invented compiled fixture reaches actual kernel FORK/root correlation, then the
mandatory missing-identity refusal and original complete cleanup. Initial RED
must be the missing lifecycle behavior, not a compilation/linkage setup error.
No test may expect FinalAdmitted, CHALLENGE or client readiness in this slice.

Use production controls/root/fork/launch/connection code and actual socket-pidfd
support, with explicitly identified narrow fault substitutions only. Exercise:

- Actual self suppression through JNI on pre-existing and later Java threads;
  fixed FORK child capture, exact returned Process, failure to exec and unchanged
  stdout/stderr. Child PREPARE remains blocked with no main marker until cleanup.
- Real native contention before acquisition through Java CapturePort: refused arm,
  refused finally-disarm, complete native/Java cleanup, and a later Java WINDOW
  acquisition. Contrast acquired/unknown obligations and explicit disarm failure,
  which stay quarantined. No fake DISARMED or direct semaphore reset in tests.
- Four live tokens/fifth refusal,256 issued/257th refusal after closed launches,
  no counter reset, altered generation, wrong invocation/classloader, zero/negative
  token, failed open publication, tombstones and exact reused descriptor numbers.
  At256 retirements, assert every COMPLETE launch endpoint/FD is gone, only the
  separately owned base descriptor remains, late tokens never enter retired
  primitives, and measured retained allocations satisfy the fixed bounds. The
  subprocess fixture's external owner observes JVM exit/base-FD release and removes
  its own independently invented base namespace; it never deletes a leaked launch
  endpoint and calls that launch cleanup complete.
- Wrong launcher/receiver, second coordinator, cancellation across each operation,
  late arm/capture/JNI result, close while calls are active, shortened deadlines,
  elapsed registry work before open with unchanged startup and outer-operation
  deadlines (including an operation allowance greater than10s),
  late cleanup settlement, repeated close and signal-reference retention.
- JNI allocation and registration failures before/after ownership acquisition;
  close errors still attempt every other owner; no leaks on unpublishable opens,
  no result recovery that clears Java uncertainty, no library/provider fallback.
- Missing compiled identity stage and all production ordinals refuse; separate
  test records are absent from distribution. Mutations bypassing the identity
  gate, fabricating no-window proof, accepting stale tokens, resetting budgets or
  omitting required cleanup must fail behavioral assertions after clean compilation.

Independent fixed-candidate review and full integrated Java/native gates remain
required. Production packaging/loading, same-exec/quiescence and complete mapped
bytes/relocation/loader/graph policy, exact JDK/native installation/FORK memory,
collector/client diagnostics and both-client/orapki qualification remain open.
Prior owned-memory feasibility is not registry admission. Do not read credentials,
private process/model data or authenticate native clients to qualify this slice.
