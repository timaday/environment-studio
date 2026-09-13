# D07c3 — private structural ELF64 check

The retained-file ELF header/program-header prerequisite is implemented and
independently reviewed. It returns bounded structural metadata between two
measurements under the original hash budget, cancellation and deadline. Runtime
privacy/client admission remains unavailable. Loader closure, mapped bytes,
relocations, consumed-script evidence and coordinator/JNI work remain required.

## Candidate and scope

Author base `1878436`; integrated review base `22e2f0d`. The four author files
were frozen before lead review. The lead added a separate C probe and three
independent test families. The seven-file manifest includes those six files and
the frozen [private ABI](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md):

- Author manifest `es-native-elf-candidate1-20260909.sha256`:
  `8c7135eda5ece4480dc3792fa7d4d77027868bea61ad9eb7d365517eda971445`.
- Integrated manifest `es-elf-integrated-candidate1-20260909.sha256`:
  `7ca213fd385b489d7719cded1429fed125af1f333df62592cd7f8fc99ab344cc`.
- Author record `es-native-elf-author-evidence-20260909.md`:
  `994957d400034c928ef193365f6249e58798f4f59bd1a8b95391b126d499c630`.
- Independent review `es-elf-independent-review-20260909.md`:
  `9d883e9f0152f03c7b6303609b13261d1725e58b4fb62e38395d13cda2abd8af`.

External records and logs are retained under `/home/tim/.tmp`; native fixtures
are independently invented byte-built ELF files or owned compiled programs in
private0700 `/tmp` directories. Tests clean their own fixtures. No credentials,
private application material or arbitrary process memory entered this slice.

The checker accepts its closed ELF64 little-endian x86-64 header vocabulary,
retains all ordered program records and checks extents, alignment, permissions,
singletons, load ordering and same-load metadata containment. Both full hashes
must equal the compiled SHA and agree on device/inode/size/SHA. Reads preserve the
borrowed descriptor offset. Safe refused output and scratch are wiped; aliases
preserve owners. Cleanup uncertainty dominates cancellation and ordinary refusal.

ELF parsing does not interpret dynamic tags, sections, interpreter payloads or
relocations. Two sampled measurements do not establish atomicity or ABA immunity.
Structural success does not establish that a process mapped these bytes or that
its loader/constructors satisfy privacy admission.

## Actual verification

Pinned Maven3.9.16, JDK21.0.12 and GCC13.3 on the recorded local Linux platform.
The author ran a real preimplementation RED: one assertion, zero errors after
trusted file/hash setup, because the ELF scaffold returned PLATFORM. The first
implementation passed. An expanded fixture accidentally wrote17 ident bytes;
seven correct FORMAT refusals exposed that test-generator error. The failing
source/log remain recorded; correcting the fixture restored the controls.

The author full build passed1,162 tests. Lead review added actual controls for
TLS file/BSS bounds, matching file/virtual displacement, metadata spanning loads,
unloaded NOTE versus DYNAMIC, empty-load overlap and arbitrary ignored PT_NULL
fields. A separate probe cancels after the first actual header read and injects
first-hash cleanup uncertainty with simultaneous real cancellation. It checks
original counters, unconsumed cancellation, zero output and unchanged owner/offset.
Cleanup injection is explicitly a shared-result control, not a claimed temporary
descriptor owned by the hash function.

An initial module-only independent Maven command failed dependency resolution;
no native test ran. The corrected reactor selection passed24 tests. Eleven author
mutants and three independent mutants compiled cleanly and failed assertions;
all restored controls passed. No compile failure counted as a mutation kill.

Independent combined `mvn -B -ntp -f backend/pom.xml verify` in
`es-elf-independent-glwc6a82` passed **1,171 tests**:279 core,7 parser,621 server
and264 supervisor; zero failures/errors/skips. Assembly and hostile-launch checks
also passed. Log `es-elf-independent-full1-20260909.log`, finished9 September2026
at22:19:58BST. Source hashes were verified before copying into the checkout.

GCC stack-usage output reports15,088 bytes for the checker and66,032 for its
hash callee, with additional helper/library frames. Fixed ELF/layout scratch is
at most16KiB alongside the hash64KiB buffer. This is compiler footprint evidence,
not whole-crypto/process memory qualification. No new current OCI, database,
browser, remote CI or HiveForge qualification is claimed by this local build.

Business review: no new availability/support claim. Engineering review: private
ABI, shared original controls and inward module boundaries preserved. QA review:
independent malformed/boundary/control oracles pass; loader, resource and client
combinations remain open. Integration/review ran alongside content work; no
measured parallel speed-up is claimed.
