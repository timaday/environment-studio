# D07c3 — native executable association

Reviewed internal prerequisite, 9 September 2026. `es_image_check` compares a
retained trusted file and compiled SHA-256 with two independently acquired
executable descriptors of an already pinned socket peer. All three hashes share
the original launch budget. Device, inode, size and digest must match; original
identity, cancellation and deadline checks continue through temporary cleanup.
See the [private ABI](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md).

This adds no production JNI, executable registry or admission capability.
Script/interpreter/loader/maps closure, complete ancestry/coordinator, client
qualification and the whole native memory bound remain unfinished. Two matching
observations do not prove an exec generation or future immutability.

## Fixed candidate and review

The author archive `es-native-image-r82rucsz` contains four new native/test files;
manifest SHA-256
`33bc2aa0df802912a2f171cde6eebc1ffce9b4883542a3207244d98467bfa668`.
The lead supplied the private ABI first. Independent review used base `1ea5381`
plus those files in `es-image-review-apqf3t16`, adding a separate invented native
probe and four Java tests. No material defect was confirmed.
The final seven-file integration manifest is
`3d0f771f2d8cd4c311c4c47dd4595db43d8d7a1a715474057a286fc2512f123c`.
Production source remained unchanged during independent testing.

The result mappings distinguish invalid arguments, unsupported platform,
cancellation, deadline, identity, resource, I/O and cleanup refusal. Ordinary
refused output is wiped; overlapping output/owners/digest is rejected without
overwriting borrowed memory. Temporary descriptors close once, and uncertain
close overrides other failures without retrying a reused descriptor number.
Borrowed file/control descriptors remain owned by their original callers.

## Test chronology and limits

The original author RED attempts did not establish the intended failure: the
first failed C compilation; the second failed trusted-file fixture setup before
calling the image function. The inherited umask made the mock executable group
writable. Tests now explicitly set their owned fixture to mode0700.

Initial implementation had already begun when this was discovered. The author
saved that implementation, restored the original PLATFORM scaffold and reran
the corrected fixture. RED3 then reached the image result assertion, with one
failure and no errors. This is **retrospective boundary confirmation**, not a
preimplementation RED. The earlier mistaken RED report and setup failures remain
recorded. Subsequent green/adverse tests and independent review do not erase that
TDD ordering gap.

Author controls include actual live child association, independently calculated
executable SHA-256, equal bytes on the wrong inode, wrong expected digest,
pathname replacement, a real second exec, child death, scope/alias refusals,
cancellation/deadline through final close, metadata/crypto errors and descriptor
reuse after uncertain close. Exact512 occurrence capacity uses actual prior
empty-file hashing; the2 GiB boundary is an injected counter, not a new streaming
qualification. Final author reactor:1,096 tests, no failures/errors/skips.

Independent controls use a separate ELF child with unusual Linux `comm` text,
preserve borrowed offset17, refuse bad descriptor flags on each acquired object,
detect device-only mismatch and reject a digest overlapping an owner. A close
fault combined with actual cancellation returns CLEANUP and leaves the borrowed
eventfd readable. The corrected focused run passes19 tests. Its first selection
omitted a server test and correctly stopped at the no-tests gate.

Nine author guard mutations and four independent guard mutations compiled and
failed explicit native assertions; their restored controls passed. Independent
mutation setup initially placed the executable under a mode0775 scratch parent,
which trusted traversal refused for both mutants and restored controls. Moving
the owned fixture into a private directory under sticky `/tmp` corrected the
setup. Those initial refusals are not counted as guard kills.

## Integrated verification

Actual command, pinned Maven3.9.16/JDK21.0.12 on the recorded Linux/GCC/OpenSSL
platform:

```text
mvn -B -ntp -f backend/pom.xml verify
```

Base `1ea5381` plus the exact seven reviewed files passes **1,100 tests**:
266 core,7 qualified parser,592 server and235 supervisor; zero failures, errors
or skips. Distribution verification and hostile-environment launcher controls
pass. The run ended at20:56:41 BST on9 September2026. All seven file hashes were
verified before copying to the root checkout.

External local evidence under `/home/tim/.tmp`: author record
`es-native-image-author-evidence-20260909.md` SHA-256
`4f84e94926877b7b08784fbc5cfde70a6789c4fbbfd6c7fda1184d32947d57a9`;
independent record `es-image-independent-review-20260909.md` SHA-256
`bd3e6138ce657c241735141566dd5cee57b6547eff55178e339bffdac4785006`;
full log `es-image-independent-full1-20260909.log`.
Fixtures are independently invented local process/filesystem cases; no database
or application model is involved. Review covered bounded ownership, cleanup,
identity and refusal semantics. No measured parallel-development speed-up is
claimed. The retained b35d328 image predates this source; current OCI, remote CI,
GHCR, HiveForge and release qualification are not established here.
