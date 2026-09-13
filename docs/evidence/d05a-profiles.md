# D05a — value-free profile mechanism

This candidate implements portable draft capture/import/export, structural reuse
preview and explicit composition proposals. It does not implement workspace
persistence/publication, HTTP authorization, observation fingerprints, planning,
XML writes, SQL generation or export authority. `ReadyToPublish` is only the
internal native compiler consistency result. Composition accepts an internally
server-validated current graph; later hosting must bind the complete observation
and plan revision. A profile-only preview is recomputed before composition.

## Contract and scope

Base: `65fb6fec3860973592ada44d285a697f20ae16e2`. The lead owns the frozen
[profile v2 contract](../contracts/profile-v2.md) and canonical
[profile v2 schema](../../schemas/profile-v2.schema.json). The lead's clarified
contract read on 2026-09-08 has SHA-256
`32cf85a907d39439e12a9b06c5ea1f846bc615031c2d2afd64f6d9b29b086d80`.
`includeTargetOnReuse` applies to reference and containment edges, including a
parent added during closure. Neutral IDs/labels are explicit declarations in a
separate identity domain; no donor-value comparison classifies their text.

Core profile classes retain only allowlisted structural metadata. Core capture checks
a complete explicit mapping bijection, then builds new entity/edge records.
Required inputs exactly match declared required fields. Optional and required
fields both appear as unresolved decisions for every explicitly composed slot.
Current target keys/relations are transient target references in a composition;
no donor graph, origin, raw field map or XML is retained in a profile. Existing
entities/relations remain present; no inferred merge, move, removal, sibling
selection or KeepObserved occurs. Declared closure dependencies require explicit
create/use-existing/cancel decisions. Missing containment minima and completed
target count/cardinality/parent conflicts remain explicit.

`BoundedDocumentParser` is the sole added facade in the definition adapter
package. It delegates to the unchanged v1/v2 parser with typed safe refusal codes.
The adapter validates the packaged canonical profile schema, denies all remote
schema resolution and produces canonical UTF-8 JSON (also YAML 1.2 syntax).
Core remains framework-, filesystem-, JDBC-, HTTP- and XML-parser-free.
No dependency versions changed. The server POM change only packages the profile
schema. The schema script only appends independent v2 profile checks.

Server capture and import share the stricter 1 MiB/20,000-node budgets. A profile with
1,817 two-required-field slots and no edges has exactly 20,000 parser nodes and
round-trips; adding another slot refuses. Capture independently refuses node and
escaped-byte overflow. Numeric revision meaning remains exact, including `1.0`
and `1e1023`; export compresses a long integer only using exact trailing-zero
scientific notation within the parser token/expanded-digit limits. Revision is
excluded from the content digest; neutral IDs, labels and logical digest remain
included. Collections are immutable, bounded before copying, and diagnostics
and value-bearing transient references have safe string representations.

## Observed checks

All commands ran in `/tmp/es-d05-profile-composition`, except the explicitly
isolated mutation copy. Maven was `/tmp/es-lead-toolchain/maven/bin/mvn` 3.9.16
with Java 21. Node was `/tmp/es-lead-toolchain/node/bin/node` 24.20.0. These are
local development results, not governed execution or release qualification.

- Meaningful RED: `mvn -B -ntp -f backend/pom.xml -pl core test`,
  18:23:37 BST, 2026-09-08. 65 tests, 2 assertion failures, 0 errors/skips.
  Callable capture returned a rejection; acceptance examples expected a captured
  value-free profile and subsequent explicit composition. No compilation failure
  is counted as RED. Log: `/tmp/es-d05a-red.log`.
- Initial GREEN: the same core command, 18:29:48 BST, 65 tests, no
  failures/errors/skips. Log: `/tmp/es-d05a-core-green.log`.
- Pre-correction G01/G04 regression: `mvn -B -ntp -f backend/pom.xml verify`,
  18:41:40 BST. 231 tests: core 76, qualified parser 7, server 148; zero
  failures/errors/skips. The 20 new profile tests comprise 13 core and 7 adapter
  tests. Existing v1/v2 definition, graph, XML, parser, workspace, security and
  ArchUnit tests ran unchanged. Log: `/tmp/es-d05a-final-verify.log`.
- `python3 fixtures/profile-v2/digest-oracle.py`: passed; independent Python
  UTF-8 framing oracle matches
  `5c72829f3788e522c8d6571b979480ecf4f6be000c493ed55f086fbf9b431683`.
  The oracle imports no compiler implementation and excludes only revision.
- `NODE_PATH=/home/tim/IdeaProjects/environment-studio/frontend/node_modules
  /tmp/es-lead-toolchain/node/bin/node --test scripts/schema.test.mjs`:
  all 10 tests passed. Existing installed locked AJV was read without modifying
  shared dependencies. Log: `/tmp/es-d05a-schema.log`.
- `python3 scripts/check_repository.py`: PASS.
  `python3 scripts/check_repository_content.py`: PASS for known patterns only.
  `python3 -m unittest discover -s scripts -p 'test_*.py'`: 10 tests, OK.
  `git diff --check`: clean. Provenance requires independent human review too.
- Packaged server JAR contains the exact canonical
  `BOOT-INF/classes/schemas/profile-v2.schema.json` resource. Docker G08 is owned
  by the lead; the Java build stage must copy `fixtures/profile-v2` for tests.

The actual XML projection-to-profile test uses independently invented glyph and
palette documents with identity, structural/environment/secret canaries. It
inspects exported bytes independently for absence of every donor canary, raw XML,
document IDs and raw-value/origin keys, then reimports the result. No persistence
canary claim is made because persistence is outside this slice. Adverse cases
cover nested value bags at every level, duplicate keys, YAML tags/anchors/merges,
invalid UTF-8/surrogates, numeric exponents, scalar typing, size/depth/node limits,
duplicate slots/edges, wrong types/endpoints/digests/required fields, incomplete
mapping bijections, required-reference cycles, containment multiple/missing
parents, partial minimum conflicts, explicit flagged dependencies, empty/unknown
selection, stale/edited previews, missing/extra/duplicate/collapsing decisions,
cancel, unresolved optional fields and preserved existing target edges.
Containment ancestor qualification prevents cyclic native declarations from
becoming ready; its defensive iterative cycle guard is challenged directly.

## G06 focused guard mutations

Only `/tmp/es-d05a-mutant-work` was mutated, copied from the independent candidate
with backend, schemas, invented fixtures and the public health probe. Each
mutation was restored byte-for-byte in `finally`; no candidate file was mutated.
Core mutations ran `mvn -B -ntp -f backend/pom.xml -pl core
-Dtest=ProfileCaptureCompositionTest test` (13 tests each).

| Removed/weakened guard | Actual behavior failure | Failures / errors |
| --- | --- | --- |
| Required reference dependency | Selected source lacks required shared slot | 6 / 0 |
| Containment `includeTargetOnReuse` | Newly added parent omits declared child dependency | 1 / 0 |
| Logical definition compatibility | Incompatible profile receives a preview | 1 / 0 |
| Recomputed preview equality | Stale proposal produces a prepared composition | 1 / 0 |
| Existing target type check | Wrong-type assignment becomes a conflict draft instead of refusal | 1 / 0 |
| Closed schema validation | Unknown nested value bag is accepted | 1 / 0 |
| Explicit neutral capture label | Donor field copied into label appears in exported bytes | 1 / 0 |

The two adapter mutations used the reactor selector
`-Dtest=ArchitectureTest,FifthEditionClassifierTest,ProfileBytesAdapterTest#METHOD`
with the relevant named adapter test, so every module executed a real test and
no zero-test guard was disabled. Mutation logs are
`/tmp/es-d05a-mutant-<name>.log`; exact run records are
`/tmp/es-d05a-mutants.json`. Fully restored reactor `test` at 18:44:01 BST ran all
231 tests with zero failures/errors/skips
(`/tmp/es-d05a-mutant-restored.log`). No required survivor remains.

## Provenance, rework and limits

All new standalone material is independently invented and registered in
[provenance](../../fixtures/profile-v2/provenance.json). The profile derives from
this repository's invented native family only; the digest oracle and expected
digest are independent of compiler output. Inline test models/documents and
canaries are also invented. No private input or renamed/redacted application
model was consulted or retained.

The lead supplied a 232-file SHA-256 manifest of the immutable base
(`/tmp/es-d05a-base65.sha256`, manifest hash
`73c30fc1d476e9eb18e5ba2be770a7a87cee261a4ee56d38cc47f9af50609e8d`). Byte comparison
found only the authorized server POM resource addition and schema-test append
changed among existing files; all additions are in the delegated profile paths,
parser facade, fixture family and this evidence. No shared Git mutation occurred.

The measured implementation/test window began with the callable stub files at
18:23:34 BST and ended with restored mutation verification at 18:44:01 BST:
20 minutes 27 seconds. Initial reading and final evidence preparation are not
included in that interval. Rework was approximately two minutes: an invented XML
test initially used names inconsistent with its fixture declaration; two first
adapter-mutant selectors hit the existing prerequisite-module zero-test guard.
Both were corrected and rerun; neither is counted as meaningful RED or a killed
mutant. An intermediate chat test-count arithmetic mistake was corrected from
actual module totals. Blocked time: zero; useful independent work continued while
contract clarifications arrived. Lead integration/review time is not included.
No line/branch coverage percentage is claimed; focused behavioral and mutation
results are recorded above. Workspace authority, persisted immutable revisions,
complete observation/plan binding, DB/client qualification, UI decisions and
release/export capability remain separate unfinished slices.


## Integration review correction — serialization boundary

Independent integration review identified that the original public core
`ProfileEncoding.json` owned external DTO serialization, contrary to
[architecture](../architecture/architecture.md). This was a material architecture finding;
the original 17-file candidate is superseded by this corrected candidate.

Core `ProfileEncoding` is now package-private and contains only the native
canonical content-digest framing. Core `ProfileResult.StructurallyValid` means
structural consistency, not wire portability. `ProfileWireEncoding` in the server
profile package owns external JSON spelling and all node, numeric-token and byte
budgets. It is package-private. The server performs cheap node preflight before
structural capture/export allocation, and after structural validation performs
bounded encoding, strict reparse, canonical schema validation and exact
round-trip model/digest equality. No portable acceptance depends on callers
remembering an additional check.

`ProfileBytesAdapter.Result.Accepted` has a private constructor and is returned
only after the complete portable check. `read` and `capture` return this adapter
result; `compose` consumes it. `write` still accepts structurally checked input
but revalidates structure and every wire guarantee before returning its privately
constructed `Encoded` result. These are portable drafts, not publication or
export authority. The original parser and shared contracts were not changed.

Observed meaningful architecture RED: at 19:03:42 BST,
`mvn -B -ntp -f backend/pom.xml -pl core test` ran 77 tests with 1 assertion
failure and no errors/skips. The ArchUnit rule against public profile-domain
wire-byte serialization identified the actual `ProfileEncoding.json(Profile)`
method. This was not a compile failure or an invented runtime failure.
Log: `/tmp/es-d05a-architecture-red.log`. Initial correction reactor GREEN at
19:06:15 BST is in `/tmp/es-d05a-architecture-green.log`.

Final correction `mvn -B -ntp -f backend/pom.xml verify` passed at 19:10:31 BST:
233 tests, comprising core 77, qualified parser 7 and server 149, with zero
failures/errors/skips. This includes 22 profile tests: 13 core behavior tests,
1 profile architecture test and 8 adapter tests. Log:
`/tmp/es-d05a-correction-final-verify.log`. Added real complete XML observations
exercise server capture with 1,817 slots accepted and reimportable, 1,818 slots
refused, escaped-byte overflow refused, `1e1023` captured/exported/reimported
exactly and a nonrepresentable 1,024-significant-digit revision refused. Core
capture deliberately reports structural validity independently of these wire
budgets; direct adapter export of those nonportable structures also refuses.

All seven original guard mutations were rerun against the correction. Their
behavior failure counts remain 6, 1, 1, 1, 1, 1 and 1 respectively, all with zero
errors. An eighth mutation bypassed the adapter's portable-acceptance refusal
and caused the server capture budget test to fail (1 failure, zero errors).
The unchanged prerequisite-module real-test selectors were used; no zero-test
guard was disabled. Logs: `/tmp/es-d05a-correction-mutant-<name>.log`; exact records:
`/tmp/es-d05a-correction-mutants.json`. Every mutated source was restored exactly.
The complete restored scratch reactor passed all 233 tests at 19:11:07 BST
(`/tmp/es-d05a-correction-mutant-restored.log`).

The independent digest oracle still returns the exact original expected digest.
All 10 schema tests pass (`/tmp/es-d05a-correction-schema.log`). Repository,
content, Python and whitespace checks were repeated after this evidence update.
The canonical profile schema still matches the packaged JAR resource byte-for-byte.
The 232-file immutable-base audit still finds only the two authorized shared-file
changes; additions remain entirely in the assigned paths. No new dependency or
Git mutation was introduced.

Measured integration correction from the architecture RED to restored regression
GREEN: 7 minutes 25 seconds, plus final evidence/manifest preparation. This is
additional rework, not part of the earlier implementation interval. Blocked time
remained zero. Lead integration and independent re-review remain separate.
