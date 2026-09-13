# Derived target preparation and actual final XML comparison

This internal slice extends [actual observed projection](qf34-derived-projection.md)
and the [retained-origin correction](derived-retained-origin-fix.md). It implements
the [approved derived contract](../contracts/derived-graph-v1.md) without enabling
v3 publication, hosted plans or export. Base: `71e30404b64ff26435e34f38e41e06cee730fb82`.
All XML, graph and profile vocabulary used here is independently invented.

## Implemented behavior

The physical target compiler shares its existing algorithm between explicit v2
and v3 entry points. V2 retains its ordering and public contract. V3 recompiles
the exact checked definition, accepts only its expected qualification blocker,
uses scalar Unicode ordering and passes only physical declarations inward.
Neither entry point fabricates v2 publication authority for a v3 definition.

Typed preparation independently projects actual current XML and checks separately
supplied current/target pins. Existing references retain original identity even
after identity edits; Fresh references cannot inherit another entity's proof.
KeepObserved uses original source evidence, Entered has no source origin, optional
absence stays distinct from unresolved input. Incomplete results expose safe
declaration references. Complete preparation can retain failed rules for inspection
and cannot authorize materialization or validation.

The extracted physical writer preserves byte editing, namespace/placement admission
and complete physical comparison. Only after tracked assembly symbols match actual
final entity positions does it return an immutable reference-to-entity mapping.
The v3 facade reprojects every final XML document, computes actual final digests
and proofs, then compares complete derived results through that verified mapping.
Original retained proofs and final moved/escaped proofs remain separate.

The comparator independently reevaluates both supplied derived results before
using them. It requires exact decision/definition/binding pins, a complete physical
bijection, matching source states, computed keys, memberships, ordered contributor
roles and rule results. Any failed required rule refuses materialization. It does
not establish non-derived physical equality, XML identity or hosted authority;
those remain explicit caller prerequisites.

## Actual tests and independent review

Pinned Maven 3.9.16 / Java 21.0.12; isolated archives under `/home/tim/.tmp`.

| Work | Actual evidence |
| --- | --- |
| Physical compiler | Initial unsupported scaffold: two assertion failures, zero errors. Eight new cases plus controls pass; full core222 at its earlier base. Four guard mutants each produce one assertion failure, zero errors; restored18 pass. Existing v2 materialization selection61 passes. |
| Typed input | Corrected scaffold RED: six assertion failures, zero errors. Ten author tests pass. Independent escaped/non-BMP, same-decoded stale XML and Fresh identity-reuse controls pass; selected and restored49 pass. Two independent pin/proof mutants each produce one assertion failure, zero errors. |
| Comparator | Initial scaffold RED: two assertion failures, zero errors. Twelve author cases pass, including forged/truncated results, reordered contributors, wrong pins, unknown/absent, failed rules, cancellation and 20,000-entity mapping. Full core232 at its fixed base. Four author guard mutants each assertion-fail. |
| Physical/final materialization | Initial scaffold RED: three assertion failures, zero errors. Exact no-op, escaped scalar and rename-plus-Fresh creation tests pass with literal expected XML. The shared writer's existing44 tests pass. |
| Independent final XML investigation | Five additional tests cover actual two-document child-property move, exact namespace/CRLF/non-BMP preservation, original versus final spans, last-contributor removal, unresolved/stale parent, final cancellation and failed rules. Restored selection73 passes, zero failures/errors/skips. Substituting an original entity for verified final provenance gives one assertion failure, zero errors. |

The lead independently reviewed the agent-authored physical compiler and comparator;
the agent independently reviewed the lead-authored preparation and materializer.
No material blocker was found in these fixed slices. Shared extraction was compared
with the prior implementations. Reviewer-authored dependencies are explicitly
excluded from that reviewer's independence claim.

The lead added an independent comparator control where both physical entities have
all source fields absent and therefore empty computed graphs. The correct mapping
matches; collapsing both references to one final occurrence refuses. Removing the
bijection guard incorrectly returns Matched: one assertion failure, zero errors.
Restoring the fixed source passes19 tests (12 comparator, six origin, one independent).
A separate author's source-state-check mutant survived the swapped-mapping control
because complete canonical contributor comparison also rejected it. This is recorded
as a surviving redundant check, not counted as another killed mutant.

Setup failures are retained separately: typed-input imports/accessors failed to
compile before corrected RED; moving the writer's nested Work class initially broke
one reflection-based cache test until its class target was updated; an independent
move fixture incorrectly declared create/remove on a modeled document root; its
failed-rule fixture initially used an illegal zero maximum. No product gate was
relaxed for these setup corrections. An archive overlay initially left stale compiled
validator classes because of source timestamps; recompiling unchanged source restored
the retained-origin controls.

External records: `es-v3-target-compiler-evidence-20260909.md`,
`es-target-input-independent-review-20260909.md`,
`es-derived-comparison-evidence-20260909.md`,
`es-derived-materializer-independent-review-20260909.md`.
The fixed materializer six-file manifest is
`es-derived-materializer-candidate1-20260909.sha256`, SHA-256
`5871672de059be26a8edb5aa9aed78c4ef38b4d0ec12f81d2dc6fb346e542723`.
Its independent review record SHA-256 is
`5eff244085588cac52d1c136dc1c423ffd2b1cdc757a244c43733040da39bf21`.
The independent materialization test SHA-256 is
`486ba79bd8f64ad36c3acce5c2ddbcbd97bd596a714738f02c581efaeb1c642c`.

## Integrated verification and remaining work

Full `mvn -B -ntp -f backend/pom.xml verify` passes **894 tests**: 241 core,
seven parser, 478 server and 168 supervisor, zero failures/errors/skips. Distribution
checksum and unrelated-directory hostile launch checks pass. Duration 2m24s,
finished 18:09:53 BST; log `es-derived-integrated-full1-20260909.log`, archive
`es-derived-integrated-7dbx6l82`. This includes both later native work and the origin
fix, plus the separate inspection capability clarification. The target-only review
archives did not contain every later native change.

The integrated 40-file overlay on the base is recorded in
`es-derived-integrated-candidate1-20260909.sha256`, SHA-256
`22e5f09aa39a6d81247c037e10edd4878f137e308ce878b8f011918d709adc46`.
The target17 production/test/contract subset is
`es-derived-target-candidate1-20260909.sha256`, SHA-256
`8936f0e3e3b7e3c7639c6112eb133f10c5ef5207999a66a640064cebf560a45e`.
Subsequent capability test formatting affects no Java source or behavior. No
production rework was required by target review; earlier setup corrections and
the capability formatting correction are recorded separately. Parallel integration
and rework were not timed independently; no speed-up is claimed.

Business review preserves authoritative fresh target groups and complete contributor
evidence. Engineering review keeps versioned authority in facades and byte editing
in the shared physical adapter. QA/RST uses independent literal outputs, identity
reuse, mixed operations, moves, stale evidence and cancellation.

Repeated bounded parsing/reprojection and canonical reevaluation allocate temporary
data. Combined maximum-scope heap, latency and response transfer are not qualified
by these tests. Physical-only v3 profiles, history, hosted integration, native clients,
full operator workflow and exact-candidate CI/GHCR/HiveForge remain unfinished.
The prior correction image does not include this slice. No GitHub upload occurred.
