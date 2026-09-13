# QF-0003/0004 — observed XML inputs

On 9 September 2026, the internal v3 adapter was implemented and independently
reviewed on `5fa6695c3885e1b09dade70183513b3d225fd427`. It constructs derived
inputs from actual independently invented XML. It does not establish a database
snapshot, live plan authority or v3 publication readiness.

## Behavior

The adapter first recompiles the checked v3 declaration and compares its metadata.
Only the known internal mechanism-qualification incompleteness is allowed.
Separately supplied snapshot revision, logical/binding identity and complete
document inventory must agree with the expected pins. Source digests are computed
from the actual immutable XML; expected values are never copied into source proof.

Physical projection retains identity, reference, containment, inventory, Unicode
and source-budget validation. A second bounded parse of those same immutable
sources constructs actual attribute spans, decoded values and child discriminator
proofs. All physical entities/edges and required derivation states reach the core
engine. Ambiguous, stale or incomplete evidence refuses without partial output.
Complete derived evaluation may report a failed rule; it is not export permission.

The extracted physical mechanism preserves the existing public v2 adapter's
readiness/dependency checks. It accepts explicit physical declarations and digest
domains internally; no artificial v2 ReadyToPublish value is manufactured for v3.
Cancellation is polled across admission, documents, source scanning, proof building
and final return. Individual parser and physical-validator calls remain bounded
operations, without a claim of immediate interruption inside each call.

## Actual verification

Pinned Maven 3.9.16 / Java 21.0.12. External integration archive:
`/home/tim/.tmp/es-derived-xml-ggjqebtp`; logs below are under `/home/tim/.tmp/`.
Focused command selects `NativeModelTest,MinimalRuntimeTest,DerivedGraphProjectionAdapterTest,GraphProjectionAdapterTest,ChildPropertyProjectionTest`
with `-pl server -am -Dsurefire.failIfNoSpecifiedTests=false test`.

| Log | Result |
| --- | --- |
| `es-derived-xml-red-20260909.log` | Ten assertions, zero errors: nine unsupported-adapter failures and one invalid test declaration with duplicate projection IDs. The latter is a fixture error, not behavior RED |
| `es-derived-xml-green1-20260909.log` | Nine new cases and 25 existing projection controls pass; the same fixture assertion remains |
| `es-derived-xml-red2-20260909.log` | Corrected independent projection IDs; restored unsupported scaffold yields ten intended assertion failures, zero errors, with three historical core/parser controls passing |
| `es-derived-xml-green2-20260909.log` | 38 pass after restoring implementation |
| `es-derived-xml-green3-20260909.log` | 41 pass after adding physical-edge, failed-rule and final-cancellation controls |
| `es-derived-xml-full1-20260909.log` | Full reactor passes 826 before the independent reviewer's two regression tests |
| `es-derived-xml-full2-20260909.log` | Exact integrated candidate passes 828: 214 core, seven parser, 455 server, 152 supervisor; zero failures/errors/skips |

Full command: `mvn -B -ntp -f backend/pom.xml verify`. Final run finished at
17:36 BST in 2m20s, including assembly checksum and unrelated-directory hostile
launch verification. The six reviewed contract/Java files are pinned by
`es-derived-xml-candidate2-20260909.sha256`, SHA-256
`31cd788e7aab7df12c23c613c2f140c3a5acdf297f1208eaa37420d1883d197b`.

The 13 adapter tests cover direct and namespaced child mappings, exact decoded
values/spans, CRLF/comments/escaping, multiple documents, required and optional
absence, empty values, ambiguous children, duplicate identity, foreign/missing
inventory, stale revisions/digests, physical references, eligibility before XML,
cancellation and source limits. A failed computed count remains visible.

Independent review inspected the fixed five-file candidate and the extraction
against v2. Its two added tests use literal source slices to check an escaped
discriminator after a non-BMP comment and a decoded carriage return, then compare
actual v2/v3 physical graphs/sources while retaining distinct digest domains.
The lead reviewed these two tests before integration. Independent focused and
restored runs each pass 43 tests. Review record:
`es-derived-xml-review-20260909.md`, SHA-256
`841437f5bc02525b0653b9e34177fc54e6341154941daa4c6394fe2bb5e5bb40`.
No blocking source/contract finding remained within this internal observed scope.

Three author mutations and three independent mutations each produce one assertion
failure and zero errors. They exercise actual span offsets, parent ownership,
snapshot revision authority, eligibility ordering and child discriminator proof.
The revision mutation both removes the header comparison and substitutes the
expected revision when constructing the actual pin. These are six mutation runs,
with overlapping categories, not six distinct discovered defects. Author results:
`es-derived-xml-mutants-results-20260909.json`; independent results:
`es-derived-xml-review-l_f6d1q7/xml-mutants.json`. Frozen source was not mutated.
The restored author archive passes all 13 adapter tests and three historical
controls (`es-derived-xml-mutants-restored-20260909.log`).

## Review and remaining work

Business: source changes recompute groups from exact PUBLIC values without donor
membership, inferred semantics or fake origins. Engineering: the shared physical
adapter remains separate from the framework-free computation engine; digest and
publication authority remain explicit. QA/RST used independent slices, prefix
equivalence, ambiguous/missing children, full references and stale-source controls.

Review added two tests and required no production rework. The early multi-document
fixture correction is recorded above. Integration/verification ran from about
17:20 to 17:36 BST; there is no serial comparison supporting a speed-up claim.
Repository integrity, staged-content guard, 11 Python guard tests and diff checks
pass. The complete staged diff was reviewed for independent mock provenance.

Typed target decisions, physical materialization and independent final XML
recomputation/comparison remain next. V3 profiles/history/publication, hosted
integration, live authority, export, capacity and actual database/client gates
remain open. Existing runtime availability stays disabled. This run does not
refresh the retained OCI/browser/heap evidence, and no GitHub upload occurred.
