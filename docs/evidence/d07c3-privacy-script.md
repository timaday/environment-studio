# D07c3 — native script and interpreter association

Reviewed private prerequisite, 9 September 2026. `es_script_check` checks an
already pinned peer against an exact compiled script path, shebang, argument
vector and independently trusted interpreter. The original deadline, cancellation
descriptor and shared hash budget apply throughout. See the
[private ABI](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md).

The primitive checks the current association of measured objects. It does not
prove which bytes an interpreter previously consumed, a stable exec generation,
future immutability, script safety or loader closure. No JNI, registry,
coordinator, privacy admission or credential-bearing client path is enabled.

## Contract and fixed candidate

The lead supplied the ABI before implementation. The author worked from
`62a5db7` in `es-native-script-xbwchzxu`; the four-file manifest SHA-256 is
`471106c66eef25c0f5615ae403b74fd866c8ee05569637fc6a068378e2b2b7ff`.
Independent review used `es-script-independent-lycu9edq`, adding a separate C
probe and four Java tests. The seven-file integration manifest SHA-256 is
`b05b8f7a8f5e51236338398f17bcc327d1d576c1c95e756a4e57f6c116f8b7ae`.
Every hash was checked before integration; production source remained fixed.

The check reads the argument vector twice before and twice after association,
checks a bounded script prefix without changing borrowed offsets, hashes the
retained script, verifies the retained interpreter against the peer executable,
then reopens and hashes the exact compiled script path. The five successful
hash occurrences consume the original launch counters. Equal bytes on a different
device/inode refuse. A literal shebang alias may differ from the independently
compiled canonical interpreter path; no alias resolution weakens trusted opening.

Invalid owner/output aliases refuse without overwriting borrowed memory. Read-only
expected input slices may overlap each other. Other refusal output is zeroed.
Owned temporary descriptors close once; uncertain cleanup overrides earlier
identity, resource or cancellation failures. A reused descriptor number is not
closed again. Final peer/control checks run after cleanup. Callers must latch
refusal; this private stateless function cannot grant subsequent admission.

## Actual tests and independent review

The first RED attempt selected a nonexistent server test and stopped at NoTests;
it is a setup failure. Corrected RED2 reached `result == OK` with the original
PLATFORM scaffold, one assertion failure and zero errors, before implementation.
Four sibling controls passed. Initial GREEN passed five tests. Expanded testing
initially encountered three probe-only C indentation errors; these were corrected
before the passing 81-test native/sibling run. None is counted as a guard kill.

Author controls cover actual live script execution, exact argument/shebang/path
boundaries, canonical interpreter versus literal alias, equal-byte substituted
inodes, script/header/trust changes, actual argument memory changes at both pairs
of readings, fragmented/error reads, scope/alias validation, cancellation, death,
deadline and exact descriptor reuse after uncertain close. The 507/508 prior
hash cases distinguish success at512 total occurrences from resource refusal.
The whole2 GiB streaming and complete native memory bounds were not repeated.
The author full reactor passed1,137 tests with zero failures/errors/skips.

Independent controls use an invented ELF interpreter that connects and blocks in
its constructor. The test observes no main marker before/during this check;
explicit release allows main and successful reap. This is a mock ordering control,
not actual client constructor qualification. Additional controls cover read-only
path/argv overlap, preserved offsets7/13, device-only mismatch, resource refusal
followed by uncertain close/cancellation, and early identity refusal followed by
uncertain final peer cleanup. Focused verification passed23 tests. No material
source or contract blocker was found within this private prerequisite.

Nine author mutations and four independent mutations compiled cleanly and failed
explicit native assertions; all restored controls passed. Independent mutations
removed device equality, skipped owned cleanup after prior refusal, masked final
cleanup behind earlier identity refusal, or rejected legal read-only input overlap.
The author's index-role mutant changes two related sites, rather than one guard.

## Integrated verification and scope

Actual pinned Maven3.9.16/JDK21.0.12 command in the isolated integration archive:

```text
mvn -B -ntp -f backend/pom.xml verify
```

Base `62a5db7` plus the exact seven reviewed files passed **1,141 tests**:
273 core,7 qualified parser,611 server and250 supervisor; zero failures, errors
or skips. Distribution and hostile-environment launcher checks passed. The run
ended at21:42:24 BST on9 September2026 on the recorded Linux/GCC/OpenSSL platform.
It excludes the separately pending v3 publication lookup candidate.

External evidence under `/home/tim/.tmp`:

- Author record `es-native-script-author-evidence-20260909.md`, SHA-256
  `3eeb371407d0907651ac47bd05f8fa30a61e7777a8c9763252b6e17337112f71`.
- Independent record `es-script-independent-review-20260909.md`, SHA-256
  `d745c5b926ae65fbb9d6f9f0b88dd6fbd9d32078c70a922d110c0d6817ea7468`.
- Full log `es-script-independent-full1-20260909.log`; independent mutation
  results `es-script-independent-mutants-results-20260909.json`.

All fixtures are independently invented process/filesystem cases in private
owned directories. Review covered identity, ownership, bounded resources and
refusal/cleanup recovery. Integration and review are recorded, but no measured
parallel speed-up is claimed. The retained f99aaad image predates this source;
current OCI, remote CI, GHCR, HiveForge and release qualification remain open.
