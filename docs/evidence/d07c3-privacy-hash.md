# Bounded native file hashing prerequisite

Reviewed local prerequisite, 9 September 2026. A native owner measures an existing
borrowed read-only regular-file descriptor with fixed OpenSSL SHA2-256. Its
[private ABI](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md)
keeps original cancellation/deadline and shared file/byte budgets. This is content
measurement only; trusted path, script/executable association, loader closure,
process admission and production JNI/coordinator wiring remain required work.

## Candidate and actual evidence

Author archive `es-native-file-hash-nz0q88f3` contains base3a10aaa plus the exact
reviewed argument verifier. Frozen six-file manifest SHA-256:
`39fcf57fd7a3f86ab526414e4fd05b824a343a61816fdc54fc0ff94f3e7db0a8`.
It includes the header/C implementation, native probe/Java test, ABI supplement
and exact build-stage libssl-dev/libssl3t64 pins3.0.13-0ubuntu3.15. This does not
install or enable a production native library. Author evidence SHA-256:
`99f72baf846f4e31af764e34cd9e09dc427a6802956c50780209ee068c72f7c6`.

Actual first RED: hashing an independently invented abc file failed one assertion
against the PLATFORM scaffold; zero errors and five passing sibling controls.
An adverse run later found a real null-owner output-wipe defect and a fixture
error: the sparse512 MiB zero file retained its initial abc prefix. The former
was fixed in preflight; the latter by truncating the fixture before enlargement.
The earlier probe compiler warning is retained as setup failure, not behavior RED.
Final focused17 passed:12 hash families and five existing controls, zero failures,
errors or skips. Author full997 passed on that exact candidate:261 core,7 parser,
522 server and207 supervisor, at19:49:59 BST in2m32s. No HTTP overlay was in that run.

The matrix uses independent abc, empty, million-a and Python hashlib512 MiB-zero
oracles. It streams actual2 GiB aggregate content, accepts exact512 MiB files and
512 empty occurrences, refuses one-over limits, retains borrowed descriptor
offsets, and exercises partial/EINTR reads. Actual growth/truncation/content
mutation, each compared metadata field, wrong type/mode/CLOEXEC, final EOF/stat/
digest cancellation and deadlines, crypto acquisition/update/finalization failure,
invalid/overlapping output and close uncertainty have explicit controls.

Eight isolated mutants compiled and each failed an assertion with exit40:
file cap, aggregate cap, occurrence cap, cancellation, metadata equality, output
wipe, close-result propagation and digest update. All eight restored controls
passed on unchanged final source/tests. No compile failure or crash counted as
a semantic kill. The hardened probes use C17/O2, warnings-as-errors, stack
protection, FORTIFY3, PIE, RELRO and NOW under GCC13.3.0.

Independent review found no material blocker in this bounded prerequisite.
Its own focused65 passed with all six hashes unchanged. An additional external
probe ran72 serialized thread-lifetime iterations across three open/hash/close
thread arrangements, retaining exact abc digests and borrowed descriptors.
These controls did not reproduce a thread-lifetime defect; they do not establish
arbitrary concurrent use, every OpenSSL context-specific thread allocation path
or whole-library teardown. Coordinator threading still needs explicit qualification.
Reviewer record SHA-256:
`bdba64db7aa3af97e6ae3957d646d1a561852350e80f7cd9f321ea3f331b7577`;
independent probe SHA-256:
`36c5cbf2aafc53e241eeb8d8b6321cea7646fd95810159c18a70bddc846026fb`.
The lead read both and retained the unchanged implementation.

The final integration uses exact9454d9f plus these six files, preserving the new
HTTP OpenAPI Docker copy while adding only the two pinned OpenSSL build packages.
Its manifest SHA-256 is
`a48ca2ea1b4d88c01a7d84695111bdcd83da485123a154bcd92573d03b582a29`.
Full `mvn -B -ntp -f backend/pom.xml verify` passed1036 tests:261 core,7 parser,
561 server and207 supervisor, zero failures/errors/skips, in3m04s at20:00:35 BST.
Assembly checksums and hostile-environment launch passed. All six integrated
hashes match the verified archive and root. Log/archive:
`es-hash-current-full1-20260909.log` and `es-hash-current-eprrb9b8`.
Whole selected-diff provenance/disclosure review, repository integrity, staged
content guard, Python11 and whitespace checks passed before commit. The guard
checks known patterns only and does not establish provenance by itself.
The subsequent [exact45d6bec image](native-hash-artifacts.md) installed the pinned
OpenSSL packages, passed1036 Java tests and protected smoke. Production native
library installation and loader/resource qualification remain separate work.

External records are under `/home/tim/.tmp`: `es-native-hash-author-evidence-20260909.md`,
`es-native-hash-independent-review-20260909.md`, the candidate manifest,
`es-native-hash-full1-20260909.log`, `es-native-hash-mutants-20260909.json`,
and `es-hash-thread-review-8hulquy2`. No private models, native clients or credentials
entered these probes. Their assertions use independently invented bytes.

## Ownership, resources and limits

The caller serializes a stable noncopyable owner and retains its original borrowed
file/control descriptors. One launch has512 total file occurrences,2 GiB hashed
bytes and a ten-second original startup allowance; repeated files consume the
same budgets again. A file is at most512 MiB. The fixed private OpenSSL context
uses configuration loading disabled, built-in default provider and an explicit
SHA2-256 fetch. No supplied algorithm/provider/path or cryptographic fallback
exists, and allocating crypto never enters the fork hook.

Streaming uses64 KiB file scratch, pread from zero, initial-size exact reads plus
an EOF witness, metadata comparison and final original-control checks. Accepted
device/inode/size/digest is evidence at those boundaries, not future immutable
content. Typed operational refusal is sticky. Distinct refused outputs and local
scratch are wiped. Partial crypto resources close once; provider-unload uncertainty
overrides earlier failure and remains sticky on repeated close. Borrowed file and
cancel descriptors are never closed or drained.

An external allocation probe measured nine successful/fault cases using test-only
OpenSSL allocator callbacks and actual malloc_usable_size, including tracking
headers. Peak tracked heap was207976 bytes;120176 process-global cache bytes remained
after owner close and zero after explicit OPENSSL_cleanup. This excludes file
scratch, other stacks, loader mappings, allocator arenas and other owners. It is
not proof of the complete1 MiB native bound or process capacity. The earlier
configuration-feasibility probe observed no config/module access under its three
controlled environments; installed loader/constructor trust is still unqualified.

Business: no credential-bearing execution becomes available through file hashes.
Engineering: narrow borrowed-descriptor content measurement composes with future
trusted-file/image checks; no public runtime command changes. QA: actual bytes,
adverse boundary controls, mutations and independent thread controls establish
this prerequisite's limits. Full chain/image admission, suppression, JNI, client
TLS/transcripts/transactions, cleanup, readback and resource/deployment evidence
remain outstanding. A stalled filesystem syscall is not made interruptible by
the clock. No current CI, registry publication or HiveForge result is claimed.
