# Retained derived provenance — consistency correction

The 9 September independent review examined `5fa6695c3885e1b09dade70183513b3d225fd427`.
This correction preserves later XML projection and native root work on
`864156a2bd488cf3f7cfc87a7cc717b3ada997a9`. It fixes an internal consistency defect
against the existing [derived provenance contract](../contracts/derived-graph-v1.md).
V3 remains unavailable; no hosted or export bypass was demonstrated.

## Reproduction and correction

Two invented eligible text fields have separate derivations and a same-occurrence
co-occurrence rule. One Existing reference keeps `tone=alpha` from entity element
1 and `finish=beta` from element 2. Each proof individually names the same identity,
document and projection and has internally consistent field pins. Before this fix,
the engine accepts the contradictory origins and produces one computed pair.

The validator now retains one complete original Origin per kept Existing identity,
and one identity per original physical `(document, entityElementIndex)` location.
All validated retained field proofs must agree with both maps. The checks execute
before grouping, including when repeated derivations read the same source field.
Both maps are bounded by the admitted 20,000 physical entities.

The original entity origin is separate from a field's attribute location. Different
direct attributes or child-property elements within that one origin remain legal.
Entered values and Fresh references gain no invented source origin. These checks
do not compare an original source span with a moved or escaped final target span;
final mapping/reprojection remains separate work.

## Actual tests

Pinned Maven 3.9.16, Java 21.0.12. Isolated archive:
`/home/tim/.tmp/es-derived-origin-fix-cwxuzkcj`.

`es-derived-origin-red1-20260909.log` runs six new core tests against unchanged
production: **three intended assertion failures, zero errors**. Direct-field
splicing, child-field splicing and distinct identities claiming one original
occurrence all incorrectly return Complete. Consistent origins/wrong identity,
mixed Entered/KeepObserved/Fresh, and distinct occurrences with equal values
provide passing controls. The consistent child case also passes before its
contradictory-parent assertion fails.

After the correction, `mvn -B -ntp -f backend/pom.xml -pl core -am verify`
passes all **220 core tests**, zero failures/errors/skips
(`es-derived-origin-green1-20260909.log`). The new assertions inspect actual
co-occurrence values, ordered field roles and retained contributor counts in
positive controls, rather than calculating expected output with the engine.

Two separate author guard mutations remove the full-origin or physical-identity
consistency rejection. Each produces one assertion failure and zero errors.
Restoring production passes all six new tests again. Archive:
`es-derived-origin-mutants-83ucot70`, with `results.json`, per-mutant logs and
`restored.log`. The fixed production/test pair is pinned by
`es-derived-origin-candidate1-20260909.sha256`, SHA-256
`1011734b0fbc23f74c59ceb847f4454a7701cc5e84962dc5b02dc76b9a1c56bc`.

An independent reviewer inspected the fixed pair and found no blocker in this
consistency scope. Its initial and restored selections each pass 21 tests, zero
failures/errors/skips. A separate mutation incorrectly keys origin consistency
by identity plus field; direct and child splices produce two assertion failures,
zero errors. The two fixed hashes were verified after restoration. Review record:
`es-derived-origin-independent-review-20260909.md`, SHA-256
`c3310463115e36ec6f0763ccab514f6df85d3b7044377ab078a25683a1180e86`.

The full isolated `mvn -B -ntp -f backend/pom.xml verify` passes **850 tests**:
220 core, seven parser, 455 server and 168 supervisor, zero failures/errors/skips.
Distribution checksum and unrelated-directory hostile launch checks pass.
Log: `es-derived-origin-full1-20260909.log`; 2m24s, finished 17:57 BST.
The tested code is 864156a plus the exact fixed pair above, excluding unfinished
target/materialization work. Review required no production rework. Reproduction,
correction, review and integrated verification ran approximately 17:53–17:57 BST;
there is no measured parallel speed-up claim.
Repository integrity, staged-content guard, 11 Python guard tests and diff checks
pass after complete provenance review. No GitHub upload occurred.

## Review environment and remaining evidence

The incoming review reports 214 core, seven parser and 440 server tests passing
at 5fa6695, plus frontend34/schema32/Python11 and frontend/repository checks.
Its full Maven run failed: 152 supervisor tests included 52 failures and 15 errors.
Independent review controls observed denied named Unix sockets and unavailable
live-child start times under Java 21.0.8. These explain some prerequisites, not
every individual failure. Those failures are retained as inconclusive for that
environment; they are neither all classified as product defects nor all dismissed.

Local independent prerequisite controls now pass named Unix socket bind/connect
and an actual Java live child's start-time query with confirmed child cleanup.
Recorded runtime is Java21.0.12, GCC13.3.0 and Linux7.0.0-31-generic; external record:
`es-review-platform-prerequisites-20260909.json`. This does not certify Java21.0.8
or establish that every previous failure has the same cause.

The incoming review's release evidence remains incomplete. Its successful earlier
CI run 34360507393 covered dbb457a, not 5fa6695 or this correction. Later local test
counts do not establish remote CI, GHCR, HiveForge or release qualification.
The inspection UI/API capability wording is a separate confirmed contract
inconsistency queued for alignment; existing configured read-only API admission
and ordinary write-capable account support must be preserved.

Business review preserves exact same-occurrence semantics. Engineering review
keeps original entity ownership separate from attribute and final target locations.
QA/RST exercises contradictory positive-looking evidence, both identity/origin
directions and legitimate mixed decisions. No mechanism version, digest domain,
v2 behavior, database account policy or runtime availability changes here.

## Exact correction container

The clean Git archive of committed `71e30404b64ff26435e34f38e41e06cee730fb82`
built successfully as `environment-studio:origin-71e30404b64f`. Local OCI index:
`sha256:443244ff5607562059ee6e766ab7f2a1e7cb50a8352625b0e37a3b692188ac89`;
Linux platform manifest:
`sha256:5c8a3fd74a286af266afc2c76837efeab642b2df0248c573bb8c5a733ea47ab9`;
configuration:
`sha256:26ff15c3b27f9b3ddb9645058fdcfbf3e4200aee3f3d0cc152a158e51fdbefa6`.
The revision label matches the commit and runtime user is `10001:10001`.

`docker build --target runtime --build-arg SOURCE_REVISION=<commit>` and
`bash scripts/container_smoke.sh environment-studio:origin-71e30404b64f` pass.
The smoke uses a read-only filesystem, dropped capabilities, no-new-privileges
and the committed demo denial checks; private workspace initialization, permission,
overwrite and schema-upgrade/refusal checks also pass. Logs:
`es-origin-oci-build-20260909.log`, `es-origin-oci-smoke-20260909.log`.
The same archive's `supervisor-artifacts` export succeeds; distribution ZIP SHA-256
is `87f9cdca3886523d4936e371095152f94e8240fb4528632f4f04d3db542e5b9f`.
This local image excludes subsequent target and capability changes. Startup smoke
at 1 GiB is not workload capacity qualification, native client qualification,
remote CI, publication or HiveForge evidence.
