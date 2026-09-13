# D07c3 exact argument inspection — reviewed prerequisite

The private native argument verifier is independently reviewed and integrated
unchanged. Five-file candidate manifest `es-privacy-arguments-candidate1-20260909.sha256`
has SHA-256 `19d9b40de965bb60975bcd78aec05535afeaf47b0a4a3f35ff55df0056bb4ebe`.
Author archive `es-privacy-arguments-c85mukrn` starts at
`85a5ee616500b1b9a256e2aa9a716a1509a97650`; current integration archive
`es-arguments-current-rci2nyvg` starts at `3a10aaa93689c44e95e03098d9bcd229f31c12da`.
Both retain the exact five source/test/[ABI](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md)
hashes. No native runtime entry, production JNI or client admission is enabled.

The verifier borrows an existing acquired peer and an immutable expected NUL
argument vector. It checks exact argument count, order, bytes and terminators
against two complete procfs reads. Limits are128 arguments,1024 bytes per argument
and16 KiB total including NULs; empty arguments remain distinct. Invalid or
overlapping inputs refuse before inspecting or mutating the peer. The retained
pin/start identity, original cancellation and deadline apply around reads, EOF
and close. Temporary descriptors are procfs-verified, no-follow, nonblocking and
CLOEXEC; close uncertainty overrides other failures. Scratch is wiped, and no
borrowed descriptor is closed or drained.

This establishes observed argument equality only. The
[Linux command-line reference](https://man7.org/linux/man-pages/man5/proc_pid_cmdline.5.html)
describes NUL-separated process-modifiable argument memory. A process-start tick
is not an exec generation. Constructor blocking, executable/script/loader identity,
complete branches, future liveness and privacy remain independent prerequisites.
A stalled kernel syscall is not made interruptible by the deadline. The future
coordinator must retain ownership and permanently latch cleanup uncertainty.

## Actual tests

Local JDK21.0.12, Maven3.9.16 and GCC13.3.0. All processes, argument values and
fault cases are independently invented and credential-free. C probes compile
with C17/O2/Wall/Wextra/Werror, stack protection, FORTIFY3, PIE, RELRO and NOW.
Both ordinary and fortified read entry points are wrapped for injected faults.

The scaffold produced one intended assertion failure and zero errors in
`es-privacy-arguments-red2-20260909.log`; five sibling-module controls passed.
The earlier selection omitted a required core test and stopped at the no-tests
gate; that setup failure is not behavior RED. Expanded tests initially had one
null-input harness bookkeeping error, corrected without production changes.

Final focused53 passes: core1, parser1, server3 and supervisor48, including twelve
new argument families, peer21 and parent15. Cases cover actual fork/exec with
Unicode/spaces/empty arguments, exact bounds, reordered/extra arguments, invalid
vectors preserving owners, partial/EINTR reads, malformed/truncated/oversized
records, wrong procfs, read failures, death/start mismatch, final cancellation/
deadline and temporary close uncertainty with exact closed-number reuse.
A real child signal handler changes its argument memory and acknowledges between
the two reads; the second reading refuses the changed argument.

Six isolated compiled mutants each failed with an assertion: omitted byte
equality, second read, pinned checks, close uncertainty, procfs check and vector
preflight. The preflight mutant proves typed boundary refusal rather than a valid
acceptance bypass. All six restored controls passed. An initial mutant failed
compilation because its parameter became unused; only its subsequently compiled
semantic replacement counts as killed. Driver/results remain in external
`es-privacy-arguments-mutants-20260909.py` and `.json`.

The original full author build passed955 tests. The current-head integration
passes **985 tests**: core261, parser7, server522 and supervisor195, zero failures,
errors or skips. Distribution checksums and the unrelated-directory hostile
launcher pass. Command: `mvn -B -ntp -f backend/pom.xml verify`, using the pinned
toolchain in the integration archive. Log `es-arguments-current-full1-20260909.log`,
2m28, finished19:30:27 BST on9 September. All five candidate hashes still match.
This build excludes the separate unfinished v3 HTTP and native file-hash work.

## Independent review

The non-author reviewer read the fixed five files and ran the focused53 selection
in `es-arguments-review-ejeqpoha`, with zero failures/errors/skips. Four additional
actual owned-child controls pass: death and cancellation immediately after the
second cmdline close, non-UTF8 argument bytes, and an empty argv[0]. The cancellation
control also proves the borrowed event remains readable. Fault timing is injected;
this is not an uninstrumented natural-race claim.

Independent probe SHA-256:
`0b6954fecc152e392f901e4d0b319c6041dec2e09b26400ef9a3747f258a48a0`.
Review record `es-arguments-independent-review-20260909.md`, SHA-256
`ce67597e569da2468d269c48a152fe3c7a2ceefae03cebb6a8129a42d7ddf776`.
The lead reviewed that record and verified both hashes before integration.
No material finding remains within the argument-verification scope.

Business review preserves external-only execution and unavailable native clients.
Engineering review checks exact-byte semantics, retained ownership and clocks.
QA/RST separates actual process controls from injected observations and retains
the earlier review environment's individually unresolved native-test failures.
Image/loader/script hashing and association, complete ancestry/coordinator/JNI,
native TLS/transcripts/transactions/readback, combined resource measurement and
deployment remain open. No new OCI, browser, CI, GHCR or HiveForge result is claimed.
Integration/review work is recorded without a measured speed-up claim.
