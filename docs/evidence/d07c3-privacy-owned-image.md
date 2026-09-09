# Native launch-owned executable inspection prerequisite

The reviewed [native contract](../../backend/tools/guarded-supervisor/docs/privacy-owned-image-v1.md)
joins trusted-file, executable association, shared hash and structural ELF checks
under the existing launch owner. It adds no CHALLENGE, production JNI, loader
qualification, native-client admission or SQL execution. The capability registry
remains disabled. Equal mapping evidence and a trusted executable file do not
establish mapped bytes or an unchanged address-space generation.

## Behavior and fixed candidate

The bound receiver enters once after root correlation and launcher disarm. Native
installation metadata is bounded and copied into wiped call-owned scratch.
One retained hash owner uses the original cancellation descriptor and startup
deadline, charges five full passes and preserves the existing aggregate/per-file
limits. The direct dynamic-root stage requires one nonempty PT_INTERP and
PT_DYNAMIC header after structural ELF checking; it does not interpret their
contents. File cleanup stays within the active call. Settlement closes initialized
hash resources once before its borrowed cancellation descriptor. Earlier failures
and independent cleanup uncertainty remain separate and sticky.

Author candidate9 on d3032f97bcb830a2ce5a98c661bf2647ec5c228a has manifest SHA
`ab3f8b3c46912a20cf9facc992cdd8be3eeaffe4a413bd0602c7b99a41c82e92`.
The integrated candidate on 9ded177ad6e4b9f9ad3c92d5194ca6ac8ec5a0e8 contains those
nine files, two contract files and two independent test files. Its13-file manifest
SHA is `e0d44fa8f592df33dd89aba0ed463e20b84b8a9bf800ba89dde61dcc55c87a15`.
All pre-existing native primitives are unchanged; three existing test files only
add required compile/link inputs. New mock sources were independently invented.

## Actual tests and review

Pinned local JDK21.0.12, Maven3.9.16, GCC13.3 and OpenSSL3.0.13 were used.
Commands use `mvn -B -ntp -f backend/pom.xml`; focused checks use explicit
ArchitectureTest/MinimalRuntimeTest/ObservationFingerprintTest controls alongside
the named supervisor cases. Full checks use `verify` without skipped tests.

- The first intended RED reached the scaffold after actual root correlation, but
  the compiler had created a0775 executable. The first implementation then
  correctly refused FILE_TRUST before hashing. Fixing only that owned fixture to
  0700 produced GREEN. Its later scaffold reruns, including an explicit successful
  trusted-file preflight, were retrospective. They do not establish a valid
  positive-fixture RED before implementation. Strict probe/JNI indentation failures
  were setup errors, not product RED or mutant kills.
- A separate actual static-root RED reproduced a contract dependency gap before
  correction: the existing ELF primitive accepts structural static ELF. The new
  owner now enforces its documented dynamic-header prerequisite. The contract's
  inaccurate reference to existing dynamic qualification was corrected first.
- Seven author families and existing launch/JNI/maps controls pass focused43.
  Actual owned FORK/test-only JNI, POSIX_SPAWN refusal, failed exec, wrong inode/
  digest/trust, original scope, once/receiver rules, cancellation/death, partial
  crypto initialization, independent cleanup uncertainty, exact descriptor reuse
  and held/shortened cleanup are covered. Prior-count507/508 tests are synthetic
  shared-budget boundaries, not512 large files within the startup deadline.
- Author full Java1282=core293/parser7/server675/supervisor307 passes with zero
  failures/errors/skips, assembly and hostile-environment distribution check,
  10 September 00:31:49 BST. Seven author mutants compile and fail at native
  assertions; every restored control passes.
- Independent review adds actual dynamic ET_EXEC and PIE positives, static PIE
  refusal and held hash settlement. The hash must use the original10-second
  startup scope rather than30-second operation lifetime. A concurrent shorter
  close retains the borrowed eventfd until hash cleanup exits and leaves both
  cleanup callers inconclusive. Focused14 passes at00:30:56 BST with zero failures,
  errors or skips. Three independent compiled mutations (longer startup scope,
  static PIE acceptance and premature eventfd close) fail with3/1/3 assertions,
  zero errors; restored focused7 passes. No independent source defect confirmed.

Integrated full Java1304=core293/parser7/server694/supervisor310 passes with zero
failures/errors/skips, assembly and hostile-environment distribution check,
10 September 00:36:42 BST. Log: `es-native-image-integrated-full1-20260910.log`.
Integration/review additions and re-verification took about6minutes; no measured
parallel-development speed-up is claimed.

External local records: `es-native-owned-image-author-evidence-20260910.md`,
`es-native-owned-image-{candidate1,candidate2}-20260910.{json,sha256}`,
`es-native-image-independent-review-20260910.md`, both mutation result JSON files,
and the corresponding focused/full logs under `/home/tim/.tmp`. Independent report
SHA is `ee3762a9670222a026c6833e3a9b0077c77320563c88e6cb446b8f46002b5162`.
They contain only generic outcomes and independently invented mock evidence.

## Remaining qualification

This is local development evidence. Production JNI/tokens, trusted bootstrap and
constructor/IFUNC order, mapped bytes, exec/mm stability, loader closure, dynamic
tags/relocations/RELRO/later loads and actual native database clients remain open.
Synchronous native work may outlive a caller budget; no arbitrary syscall
interruption or worst-case allocation/latency guarantee is claimed. Combined
resource measurements, exact candidate OCI/CI and HiveForge remain separate gates.
The retained76ab78b image predates this change.
