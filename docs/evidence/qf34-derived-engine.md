# QF-0003/0004 — internal derived computation

Scope: implement the approved [derived graph](../contracts/derived-graph-v1.md)
computation port on `bc157222861d4a04c9c4c37bda2d0b1e764ae011`,
9 September 2026. Current and typed target inputs use separate physical references.
This slice supplies core computation and consistency checks; its independently
invented pins are not actual XML, database or live owner evidence.

## Behavior and boundaries

Input contains the complete physical entity/edge inventory and explicit derivation
field states. The validator recompiles the selected v3 declaration, checks its
exact metadata, separately supplied expected/snapshot pins and document inventory,
then validates source-field states and proof consistency. Stale pins, duplicate
physical identities/origins, foreign edges and conflicting proof roles refuse.
An unresolved optional target input is incomplete, without a graph. Explicit
optional absence contributes nothing; a required absence refuses. Ineligible
source declarations are rejected before graph/value-dependent computation.

The engine returns separate immutable computed nodes, physical memberships and
directed same-occurrence edges. Exact values and derivation/type scope determine
identity. Results preserve every contributor, including both ordered field roles
for co-occurrences and the original child discriminator/location proof. Repeated
pairs deduplicate the edge without losing contributors. Last-contributor removal,
self-edges, exact Unicode ordering and distinct outgoing-target cardinalities
follow the approved contract. Complete rule evaluation can contain FAIL; a
complete graph is not export permission.

Shared limits count actual physical and computed inventory: 20,000 nodes,
50,000 edges, 100,000 contributor associations and 8 MiB of strict UTF-8 identity
values per distinct computed tuple. Input collections are bounded before copies;
result counters are charged before insertion. Cancellation discards partial output.
The engine checks cancellation before and after input validation; it does not
promise immediate cancellation inside every validator operation. Rule output can additionally contain up to 32 co-occurrence
checks per computed source node. This bounded allocation still needs integrated
heap/lifecycle qualification.

## Actual behavior evidence

Pinned Maven 3.9.16 / Java 21.0.12; logs below are in `/home/tim/.tmp/`.
Root integration archive: `es-derived-shared-0cebbshl`.
Engine author's archive: `es-derived-engine-iyk82v_e`.

| Run | Actual result |
| --- | --- |
| `es-derived-input-red-20260909.log` | 10 tests, nine intended assertion failures, zero errors against the unsupported-input scaffold; collection/redaction control passed |
| `es-derived-input-green1-20260909.log` | Ten input tests pass |
| `es-derived-input-red2-20260909.log` | 15 tests, one assertion failure: an impossible attribute span exceeded the existing 1 MiB XML limit |
| `es-derived-input-green2-20260909.log` | All 15 input tests pass after correcting the span ceiling |
| `es-derived-engine-red2.log` | Two intended engine assertion failures, zero errors; two old model controls pass. Initial red1 was an import-ambiguity setup failure, not behavior evidence |
| `es-derived-engine-green2.log` | 14 new engine tests and two old model controls pass |
| `es-derived-independent-green1-20260909.log` | 31 combined input/engine/independent-oracle tests pass |
| `es-derived-independent-green2-20260909.log` | Three independent oracle tests pass, including the added cross-derivation identity budget |

Independent expected output specifies every computed tuple, all eight memberships,
three co-occurrence edges, each complete contributor/field proof and the exact rule
results for the invented alpha/x, alpha/y, beta/x, repeated alpha/x example. No
beta/y edge is expected. The expected partition is literal, not generated using
the engine. Another example charges equal text separately in two derivations:
4,096 values × 1,024 UTF-8 bytes × two tuples reaches 8 MiB; one added byte refuses.

The engine's exact/one-over controls use complete inventories: 19,999 physical
nodes plus one computed node; 49,999 physical edges plus one membership; and
9,090 full occurrences × 11 associations plus two partial occurrences × five
associations reaches 100,000. The next full occurrence arrangement produces
100,001 and refuses. A separate 8,192-value Unicode case measures strict UTF-8,
not Java string length. These are core limits, not hosted capacity evidence.

The lead independently reviewed the author's frozen four files, manifest
`es-derived-engine-candidate1-20260909.sha256`, SHA-256
`b252e3e2a64f6536e7594a62123114f4fad18c59493a385b42b253837bb40ab5`.
Nine author guard mutants and three additional lead mutants each produce one
assertion failure and zero errors. Lead mutations swap co-occurrence value proof,
count contributing rows as outgoing edges, and charge equal values globally
instead of per computed tuple. Lead mutation archive:
`es-derived-root-mutants-ilnaniad`; results:
`es-derived-root-mutants-results-20260909.json`. Restoring the original engine
passes the three oracle tests again (`es-derived-root-mutants-restored-20260909.log`).

The other author independently reviewed the lead's input/validator15/oracle3 files
and found no blocker within the explicit consistency scope. Its selected 20 tests
include two historical controls; initial and restored runs pass. Three additional
validator mutants remove expected-pin equality, UNKNOWN accumulation or checked
metadata equality; each produces one assertion failure and zero errors. External
review archive: `es-derived-root-review-j56lyhsh`. Author/reviewer evidence:
`es-derived-engine-evidence-20260909.md`, SHA-256
`9b9e919f413d2e79ca9576764792a35069fdb356aa4f3de45fb5af64a7e52565`.
No author reviewed their own contribution as independent evidence.

Full integrated `mvn -B -ntp -f backend/pom.xml verify` in the isolated archive
passes **813 tests**: 214 core, seven qualified parser, 440 server and 152 standalone
supervisor, with zero failures/errors/skips. Assembly checksum and unrelated-directory
hostile-environment launch checks also pass. Log:
`es-derived-integrated-full-20260909.log`; elapsed 2m20s. The reviewed/tested eight
Java files are pinned by `es-derived-integrated-candidate1-20260909.sha256`, SHA-256
`ada53dbc378db55b73979832f4b50ed94ec3006761753c5199b148983af0d37d`.

Integration began at 17:13 BST; full verification finished at 17:17. One input
span-limit correction preceded integration. Review added three independent oracle
cases; no production rework was required by independent engine review. This
records coordination/verification, not a measured parallel speed-up.
Repository integrity, staged-content guard, 11 Python guard tests and diff checks
pass. The staged diff was reviewed for independently invented mock provenance.

## Review perspective and next gate

Business: authoritative recomputation preserves the approved exact grouping
semantics, optional absence and physical-only profile direction. No donor values,
computed retain decisions or invented XML origins are introduced.

Engineering: core depends on typed immutable ports and frozen physical value
models, with no framework/XML/database dependencies. Old v2 hashes, schemas,
mechanism availability and publication paths are unchanged. RST probes cover
Unicode distinctions, repeated pairs, last contributors, stale evidence, explicit
UNKNOWN and each capacity counter. QA expected outputs and mutation evidence are
independent of the production grouping algorithm.

Actual complete source inventory, lexical span ownership, locator selection and
physical graph validity must come from separately qualified adapters. The next
slice must construct observed proofs from actual independently invented XML;
then typed materialization must independently reproject and compare final target
results. V3 publication, profile/history codecs, hosted views, live owner/deadline
revocation, export, database/client and deployment qualification remain open.
The retained compiler OCI image excludes this later engine. No new image/browser,
heap or real-client qualification is claimed here. No GitHub publication occurred.
