# D07c3 — socket peer process pin

This internal native prerequisite acquires a process pin from the same AF_UNIX
socket as its kernel credentials. It does not establish executable identity,
ancestry, a Java-owned launch root, crash privacy or runtime admission. The
production listener, JNI, launch registry and database authentication are unchanged.

Candidate base: `203585411920e3aa7f7531db0a55bce42d0c4479`.
Initial reviewed archive: `/home/tim/.tmp/es-privacy-peer-c3cm_b2y`.
Deadline correction: `/home/tim/.tmp/es-privacy-peer-deadline-l6rdpkzl`.
Final lifetime correction: `/home/tim/.tmp/es-privacy-peer-null-spt8msp4`.
Only privacy-peer.c/.h, the C probe, PrivacyPeerTest and this evidence are authored.
No binaries or private inputs enter the repository. Every process and fixture in
these tests is independently invented and credential-free.

## Behavior and ownership

The caller retains the accepted socket and cancellation eventfd. The peer object
owns the returned SO_PEERPIDFD and temporary procfs read descriptors, closing each
once. Exact SO_TYPE/SO_DOMAIN/SO_PEERCRED/SO_PEERPIDFD lengths, positive PID,
CLOEXEC, pidfd fdinfo PID correlation, effective UID/GID and positive process-start
ticks are checked. UID/GID zero remain valid exact identities. Missing peer-pidfd
support refuses; there is no numeric pidfd_open fallback.

The pidfd is polled before and after bounded identity reads. A successful initial
read records start ticks; later reads require the same identity and original live
pin. Proc files must belong to procfs; they are opened nonblocking/CLOEXEC with
final-component symlinks refused. Read scratch is 16 KiB with a bounded EOF witness.
Malformed, oversized or inconsistent evidence refuses and wipes result state.
The stat parser accepts legal spaces, newlines and closing parentheses in comm.

The original absolute monotonic deadline is never renewed; remaining allowance
above ten seconds is refused before proc work. Cancellation is checked
first, without consuming the borrowed event. Deadline checks occur between local
kernel calls; this primitive cannot interrupt a stalled kernel syscall. Closure is
sticky, including uncertain close, and never retries a descriptor that may have
been reused. Reading an explicitly closed successful pin returns INVALID, not an
empty success. No process data is printed by the runtime primitive.

The reviewed ABI cites the [Linux socket implementation](https://github.com/torvalds/linux/blob/v6.8/net/core/sock.c)
for socket-bound acquisition. Actual local support is demonstrated below; a kernel
version string alone was not used as admission evidence.

## Actual RED and GREEN

Pinned Maven 3.9.16/JDK21; tests compile C17 with the local `/usr/bin/cc`, warnings
as errors, stack protection, FORTIFY, PIE and RELRO. Scratch directories are 0700.
The Java runner accepts only fixed probe assertion-line markers on failure, never
raw proc data. Compiler setup diagnostics are separate from behavior results.

- Two initial Maven selections stopped at core's no-tests gate. These setup errors
  are retained in `es-privacy-peer-red-20260909.log` and `...red2...`; neither is RED.
- Corrected selection ran seven existing core/parser/server cases, then six peer
  assertion failures with zero errors against a refusal stub (`...red3...`).
- The first implementation compile stopped on three misleading-indentation warnings;
  that setup failure is `...green1...`. Corrected formatting produced GREEN6
  (`...green2...`) and actual successful local SO_PEERPIDFD acquisition.
- Added adverse controls produced RED2/11 with zero errors (`...adverse-red...`):
  closed-pin read returned empty success; death during a proc read returned generic
  I/O instead of the checked dead-peer result. Both were corrected; GREEN11 followed
  (`...green3...`), then GREEN15 with partial reads, legal comm and inheritance
  (`...green4...`).
- Initial review candidate full `mvn -B -ntp -f backend/pom.xml verify` passed **571 tests**:
  core145, parser7, server331, supervisor88 (including peer16). Assembly and hostile
  launcher checks passed. Log `/home/tim/.tmp/es-privacy-peer-full-20260909.log`.
  The final alias/reinitialization case and corrected test fault lifetime are included.
- Repository integrity passed (`es-privacy-peer-repository-20260909.log`). This
  archive has no Git index; staged provenance/content gates remain root integration work.

The focused command used `-pl tools/guarded-supervisor -am` and
`-Dtest=PrivacyPeerTest,NativeModelTest,FifthEditionClassifierTest,PlanContentAdapterTest`
with `-Dsurefire.failIfNoSpecifiedTests=false`; each selected module ran actual tests.

## Adverse controls and independent witnesses

Actual tests create a private listener and fork an owned child that connects after
fork. Its observed PID matches the independently owned child, with current UID/GID
and stable positive start ticks. Peer death before acquisition, after pinning and
during identity reads refuses. Partial reads cap each actual proc read to three
bytes. The child comm containing spaces, ')' and newline remains readable.

A separate pre-fork socketpair control returns the creator's identity, not the
later child. Thus an inherited socket is explicitly not treated as proof of a
connecting child or Java launch ownership. Actual socket and cancellation descriptors
remain open after peer closure. Numeric FD reuse and an injected uncertain close
prove no retry closes a replacement descriptor.

Test-only syscall wrappers supplement actual calls for missing support, short
credential/pidfd results, absent CLOEXEC, UID mismatch, malformed fdinfo, oversized
reads, changed expected start ticks and impossible returned-FD alias. They do not
simulate a successful kernel pin. Reinitialization preserves the original owner.

Six targeted external mutants were killed by semantic assertions: omit pidfd PID
correlation, CLOEXEC, effective UID, start identity, deadline and liveness checks.
The first PID-correlation mutant survived because the test fault accidentally
followed numeric FD reuse into the subsequent status read. The fault now ends on
close; the isolated correlation mutant then fails as intended. Original and final
logs are `es-privacy-peer-mutants-20260909.log` and `...mutants2...`.

Four actual strace controls (live, exit, inherited and partial) passed with empty
stdout/stderr. Raw credential-free traces remain external, mode0600, in the owned
0700 lab `/tmp/es-peer-mutants-cg8gatcu`. No processes remain running from this lab.
Trace acquisition is local development evidence, not runtime identity authority.

## Independent review correction

The lead found that expiry was checked but an excessive caller deadline could
exceed the ABI's ten-second startup ceiling. The initial five-file candidate
(manifest SHA-256 `ae80522648f26214343e417ff51cb62a9d7aee5be427b44317e6906c8ed62b43`)
remains unchanged. A new isolated archive at the same base contains the correction.

Actual added RED: one assertion failure, zero errors, accepting UINT64_MAX instead
of refusing it (`es-privacy-peer-deadline-red-20260909.log`). The helper now rejects
remaining allowance above ten seconds after cancellation/expiry checks and before
socket acquisition or proc reads. Subtraction occurs only after current<deadline,
so excessive bounds cannot overflow into an accepted interval.

Corrected focused GREEN passed24 tests: seven existing core/parser/server cases
and peer17 (`es-privacy-peer-deadline-green-20260909.log`). It checks UINT64_MAX,
eleven seconds (both INVALID before pidfd acquisition) and the accepted ten-second
boundary, preserving borrowed descriptor owners. Seven targeted mutants failed
semantic assertions, including removal of the new maximum-allowance check. Four
actual strace controls passed again with no stdout/stderr. Final mutation/trace log:
`es-privacy-peer-deadline-mutants-20260909.log`; owned external trace lab:
`/tmp/es-peer-mutants-u1zyrr5z`. The expired-deadline mutant changes the refusal code;
the independent maximum-allowance guard also prevents accepting an expired bound.
The full571 result above is the initial candidate's result, not a claimed full run
of this corrected candidate. Root will perform independent integrated verification.

The lead's next review found that a null output pointer on a live peer returned
INVALID but retained its pin, contrary to the documented terminal failure behavior.
Candidate2 remains unchanged (manifest SHA-256
`5dc334fd2a0116e839fc34bf5553b637f8a7f3b3a27e700a668bdbc299085c27`).
A new actual null-output control produced one assertion failure with zero errors
among peer18 (`es-privacy-peer-null-red-20260909.log`). The live peer now closes
once and retains sticky INVALID; subsequent reads cannot recover success.
Fresh/null-peer and reinitialization behavior remain unchanged.

Final focused GREEN passed25 tests (seven existing plus peer18), including pin
closure, zeroed later output and preserved borrowed socket/eventfd. Log:
`es-privacy-peer-null-green-20260909.log`. Eight external mutants failed semantic
assertions, including omission of null-output terminal cleanup. Four actual trace
controls passed again with empty output (`es-privacy-peer-null-mutants-20260909.log`,
owned 0700 lab `/tmp/es-peer-mutants-ywnrnpo9`). This final candidate has focused
verification; the earlier full571 remains explicitly the initial candidate result.

## Remaining qualification

Independent fixed-candidate review accepted the final correction. No forced
kernel PID reuse, other PID/user namespaces, actual UID0, other kernel/libc/toolchain
combination, resource exhaustion or stalled kernel syscall is qualified here.
Held-pin death and deliberately mismatched start identity are bounded controls,
not a claim that every PID-reuse schedule was exercised.

The Java registered-root numeric PID correlation gap remains for the coordinator.
Image/loader/guard/script/argv hashes, ancestry graph, pending listener bounds,
per-exec handshake integration, late exec enforcement and effective crash-artifact
privacy remain separate gates. A process pin never satisfies those gates.
The earlier intermittent HTTP400 and terminal-test investigations are not diagnosed
by this passing full reactor; no unrelated production fix is included.

## Lead integration

The final five-file manifest SHA-256 is
`a9a1264b954b9b626e426ae2f89c38dcee9d47d0f2fad091f232f30bcc6029ee`.
The lead reviewed the final sources, including both deadline and null-output
corrections, and reran four independent actual native controls: changed effective
GID, cancellation after opening, expiry after opening and cancellation concurrent
with peer death. All pass with empty output, zeroed failed-read results, closed
owned pins and surviving borrowed socket/eventfd. External harness:
`/home/tim/.tmp/es-peer-independent-review3-20260909.c`.

A fresh archive of `19be08f57ff0a3b1366d4b660f7f0c6b220ba179` plus the final
five files passes the full **608-test** Java reactor: core150, parser7, server361,
supervisor90. Distribution checksums, assembly and hostile-environment launch
checks pass. Command: pinned Maven `-B -ntp -f backend/pom.xml verify`.
Log: `/home/tim/.tmp/es-peer-integration-full-20260909.log`.
This result includes the reviewed binding/disclosure and buffered-request fixes.
The separate `19be08f` image/browser evidence excludes this later peer primitive.
