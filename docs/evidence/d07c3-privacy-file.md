# Native trusted file opening prerequisite

9 September 2026. A native owner opens a bounded trusted installation path into
one retained read-only file descriptor, which feeds the existing shared file-hash
owner directly. The [private ABI](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md)
defines the original cancellation/deadline, no-follow traversal, ownership/write
checks, local filesystem restriction, privilege refusal and close-once lifetime.
This establishes a file-opening prerequisite only. Executable/script association,
loader closure, native memory, coordinator/JNI and client admission remain open.

## Fixed candidate and actual tests

Author archive `es-native-trusted-file-v1y2z_fz`, base d1d0608, has five frozen
files: private ABI supplement, C/header, C probe and Java test. Manifest SHA256:
`80231ffcee5c7f75940391235f2d5ffe5e07c1855f5bffa287112e5b471e28c0`.
The independent reviewer read that exact candidate and added two separate probe
files. The combined seven-file manifest on7c615ec is
`7afb2a7a6929127c2429aaf5ab9db9473d755195d8445d9fe2224ba93dd70a52`.

Before implementation, the actual invented abc opening/hash probe failed against
the PLATFORM scaffold. The reused Java runner rejected the new native diagnostic
format, so the recorded Java failure is an unexpected-format assertion, not a
clean expected-exit assertion. That limitation is preserved in
`es-native-trusted-file-red1-20260909.log`. Initial implemented control plus hash
and sibling controls passed18. No compile/setup failure is described as RED.

The first expanded adverse run had four harness failures: fortified `openat`
resolved through unwrapped `__openat_2`, preventing intended open/final-close
faults, and descriptor reuse selected a lower free number than assumed. The
diagnostic matcher also obscured native assertion details. Correcting the hooks,
explicitly duplicating the replacement to the exact released descriptor number,
and fixing the bounded runner produced28 passing tests:11 file families,12 hash
families and five existing controls. Production was unchanged by these harness
corrections. Refusal probes keep borrowed cancellation readable and close retained
owners twice to verify sticky cleanup without closing reused descriptor numbers.

Real mock filesystem cases include retained abc bytes after pathname replacement,
Unicode paths, leaf/intermediate symbolic links and a symlink substitution between
lookup/open, writable modes, sticky directories, FIFO/directory and set-ID refusal.
Injected metadata/syscall cases cover foreign ownership, capability presence or
unavailable evidence, unsupported filesystem, open/stat/lookup/clock failures,
and original cancellation/deadline through the final parent close. These injected
cases do not establish actual other-UID/capability or unsupported-platform
qualification. No real application model, client or credential is involved.

Eight isolated native mutants compiled and each failed an assertion with exit40:
no-follow, ownership, writable mode, set-ID, capability absence, filesystem,
final cancellation and close-result guards. All eight restored controls exited0
with unchanged production bytes. The first close-result mutant did not compile
because its replacement triggered misleading-indentation; it is excluded from
semantic evidence. Correcting that mutant-only syntax produced the actual kill.
Drivers/results: `es-trusted-file-mutants{,2}-20260909.py` and
`es-trusted-file-mutants-results{,2}-20260909.json` under `/home/tim/.tmp`.

## Independent review

No material blocker was found within the documented trusted-installation model.
Independent tests open/hash an actual4095-byte nested path with a non-NUL byte
after the supplied length, refuse4096 bytes without owner mutation, hash a real
owned `/dev/shm` tmpfs file, and reject a real existing Linux filename containing
a complete but invalid UTF-8 continuation sequence. The last case strengthens
the original malformed sequence, which encountered truncation first. Removing
only the continuation guard admits the invalid filename and fails the independent
assertion; restored source passes. This is the ninth meaningful guard mutant.

The reviewer ran31 selected tests, then8 after strengthening its independent
probe; both passed with zero failures/errors/skips. All five author hashes stayed
unchanged. Independent C probe SHA256:
`c494975514a5d8fb5f5c00719b425eee4f438ae36bd2068ee2fe9911a0ba39e7`;
Java test:
`4e195ab5432b79f0de3e52fa2d9546051eb3935ed29673878aca8f1b8548bab5`.
Reviewer evidence:
`0f421463e06b21a74ec2c44515881aa704aed544c2404fa5816b0b763e70d2e2`,
file `es-trusted-file-independent-review-20260909.md`. The lead read the report
and both independent probes before integrating them unchanged.

## Integration and limits

The full current-candidate run uses the owned `es-file-current-4f4if10f` archive
and pinned Maven3.9.16/JDK21.0.12. C probes use GCC13.3.0, C17/O2, warnings as
errors, stack protection, FORTIFY3, PIE/RELRO/NOW and pinned system OpenSSL3.0.13.

```text
mvn -B -ntp -f backend/pom.xml verify
```

Full integration passes1086 tests:266 core,7 parser,592 server and221 supervisor,
zero failures/errors/skips, in3m10s at20:32:22 BST. Assembly checksum and hostile
launcher checks pass. Log: `es-trusted-file-full1-20260909.log`. All seven frozen
hashes match the integrated root. Review and integration ran from approximately
19:20 to19:32UTC alongside profile HTTP work; no isolated speed-up is claimed.
This result does not stand in for exact OCI, current remote CI/GHCR or HiveForge
evidence.

Whole staged-diff provenance/disclosure review, repository integrity, content
guard, Python11 and whitespace checks pass before commit. The limited content
guard does not establish provenance by itself.

Only the admitted file descriptor is retained. At most two traversal descriptors
and4096 bytes of pathname scratch are used; path/metadata scratch is wiped and
uncertain close overrides other outcomes. Every opened component is independently
root/operator-owned on admitted ext2/3/4, tmpfs or overlay semantics; writable
ancestors require the documented sticky protection and owned next component.
Final set-ID/capability evidence refuses. Root or same-operator installation
mutation remains outside this trust model. Neither successful lookup nor hashing
promises future immutable content, and clock checks cannot interrupt a stalled
filesystem syscall. Whole native invocation capacity remains unqualified.

Business: no credential-bearing execution becomes available from this change.
Engineering: one retained descriptor composes with bounded hashing; public
configuration and production JNI remain unchanged. QA/security: actual path,
byte, mode, cancellation and cleanup controls plus independent cases cover this
prerequisite within its stated assumptions. The next native work is exact
executable association, followed by script/loader closure and complete admission.
