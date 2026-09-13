# D07c3 direct-parent inspection — local prerequisite

Base `aaddcfbaabd814c22201e0a54916d89f3f9120e2`. Author archive
`es-privacy-parent-ru2jrofr`; fixed four-file candidate2 manifest
`es-privacy-parent-candidate2-20260909.sha256`, SHA-256
`5c47dfe3967d07ba1d08df94ece03dd3640b6ca666a7a9102461fbe01bbc7d5c`.
The lead-owned [private ABI](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md)
supplement SHA-256 is `10a1057eb7b0e428ac3e6615a28703d6d0a60647d3bf7a138fc69974bc073c36`.
Independent review is complete and all four files are integrated unchanged.

The private C function checks one live direct parent edge between two existing
kernel peer pins in the same cancellation/deadline scope. It reads the child's
actual procfs PID/PPID/start record twice, rechecks both retained pins around reads
and never obtains a new numeric-PID pin. Temporary reads are bounded, no-follow,
nonblocking, CLOEXEC and verified as procfs; scratch is wiped and close uncertainty
takes precedence. Caller-owned eventfd/socket descriptors remain live. This is a
process fact at checked boundaries, not a complete ancestry, executable, Java
launch, suppression or admission proof. The
[Linux procfs reference](https://docs.kernel.org/filesystems/proc.html) documents
the process fields and warns that a procfs descriptor alone does not prevent PID
reuse; actual existing pidfds remain the separate identity prerequisite.

## Actual author investigation

Pinned local Java21.0.12/Maven3.9.16/GCC13.3.0. The harness creates an independent
mock parent and child through real fork/connect operations, acquires both pins
from accepted sockets and keeps them live through the check. It also acquires
the actual grandparent pin and refuses that as a direct parent. No credentials
or application configuration appear in any fixture.

`es-privacy-parent-red1-20260909.log` has one intended assertion failure and zero
errors against the unsupported scaffold; five sibling-module controls pass.
The first implementation passes the real parent-edge case. Initial expanded fault
tests fail five assertions because fortified compilation calls `__read_chk`, while
the harness initially wraps only `read`. Separate symbol inspection identifies
that missed injection entry point. The harness then wraps both forms with buffer
checks; production fortification is unchanged. Test scratch parsing gains an
explicit NUL terminator, and actual child death waits on the acquired pidfd before
checking refusal. These are harness corrections, not claimed product defect fixes.

The corrected adverse selection passes17 tests: 12 native families plus five
sibling controls. The expanded focused selection passes19 (14 native families
plus five controls). A final parent-death witness brings the full author reactor
to **929 passing tests**: 251 core, seven parser, 488 server and 183 supervisor,
zero failures/errors/skips; distribution checksum and hostile launch checks pass.
The final test-only addition was present before supervisor test compilation;
all four source hashes match candidate2 at completion. Log
`es-privacy-parent-full1-20260909.log`, 2m26s, finished18:37:41 BST.

Cases include legal comm delimiters, partial reads, direct versus grandparent,
reversed/same/overlapping owners, foreign launch controls, cancellation/deadline,
process death/reparenting during and after the final record, malformed/oversized
records, wrong start/PPID, wrong filesystem, unavailable reads and temporary close
uncertainty. Closed-descriptor reuse uses dup2 to the exact former number and
checks that peer cleanup leaves it live.

Eight compiled guard mutants each exit40 with an assertion, no compile failure or
crash: parent PID, child start, second record, both launch controls, final recheck,
parent liveness and close-result propagation. Restored live/grandparent/final
deaths/close-uncertainty controls exit0. Results are in external
`es-privacy-parent-mutants-20260909.json`. Independent fixed-candidate review finds no material blocker in the single-edge
scope. Its actual verify selection passes46 (one core, one parser, eight server,
36 supervisor including parent15/peer21), zero failures/errors/skips; assembly and
hostile launch pass. Additional independently compiled actual-process controls
pass live parentage, read EIO plus temporary close EINTR (CLEANUP wins with exact
closed-number reuse) and cancellation at the final EOF with the borrowed event
still readable. Independent probe SHA-256
`03e0699bdaf65e57e1bf74610804cbb2b4052fe63314ba2a64f2549085947ea8`.
Review record `es-privacy-parent-independent-review-20260909.md`, SHA-256
`47a260a37306d18d8e0fd6557395af4b061303f46fd6c8a7d4b0e6abaaa6d662`.
The lead reviewed that independent probe and all frozen hashes before integration.

The clean combined history/parent candidate on7b89e3c passes **943 tests**:
255 core, seven parser, 498 server and 183 supervisor, zero failures/errors/skips;
checksum and hostile launch pass. Its frozen13-file overlay manifest SHA-256 is
`f04891a75486f3edc9c2690c657b026cd06c48af28c822f4b0b878ed32651f93`.
Archive `es-history-parent-integrated-mz_kgtcn`, log
`es-history-parent-integrated-full1-20260909.log`, 2m25s, finished18:43:30 BST.
All overlay hashes match after completion. Neither review nor tests admit a
runtime; the future coordinator must retain pins and permanently latch cleanup
uncertainty. Review/integration work is recorded without a measured speed-up claim.

Business review retains disabled native execution. Engineering review checks
borrowed ownership, shared scope and cleanup propagation. QA/RST distinguishes
real process relationships from injected stale/malformed/failure observations.
Image/loader/script identity, complete compiled branches, production JNI and
coordinator, native-client TLS/transcript/transaction and readback remain open.
No OCI, heap, CI, GHCR or HiveForge qualification follows from this prerequisite.
