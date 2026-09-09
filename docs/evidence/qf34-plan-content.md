# QF-0003/0004 — complete v3 plan content

The internal v3 plan content adapter is implemented and independently reviewed.
It retains complete original, typed target and independently reprojected final
derived evidence alongside exact sources and original physical references.
Shared hosted installation, versioned APIs and runtime qualification remain open;
the actual compiler still reports MECHANISM_UNQUALIFIED.

## Fixed candidate

Author base `1878436` plus the reviewed versioned plan model, functionally
`22e2f0d`. Independent review used `22e2f0d` plus the frozen seven files.
Integration used `c4b0503`, adding the reviewer's separate test. External records
under `/home/tim/.tmp`:

- Author manifest `es-v3-plan-content-candidate1-20260909.sha256`:
  `6e2c8e13f0758ff05ef5169d67a445ade11e6795b05291c16624c99b5f29e351`.
- Author record `es-v3-plan-content-author-evidence-20260909.md`:
  `5d9c0500263094bd2deb3c7217b7224a294fd4e6fc0bf3b38b48e787792d4f7a`.
- Independent record `es-v3-plan-content-independent-review-20260909.md`:
  `bf6d3f0281726466ec0b93ecd68e5fcfdd1dee4a3ff3c3a3027cefa84ad90285`.
- Integrated eight-file manifest `es-v3-content-integrated-candidate1-20260909.sha256`:
  `0b59963181ce34cef9a0ce5d94a8d9d654312e159cc0a07b0c3f8ab1a3ac7cf8`.

All source hashes were checked before copying to the checkout. After verification,
the contract's status word changed from planned to reviewed; no normative behavior
or production code changed. The [content contract](../contracts/plan-content-v3.md)
defines the remaining hosted ownership and resource obligations.

## Behavior and test oracles

Projection checks complete selected inventory, configured keys, strict Unicode
byte/character counts and independent digests, then calls the actual XML/derived
projector. It retains failing observed rules. Materialization reprojects and
compares the whole original graph, input, computed result and bijective original
references before using the actual target materializer. Typed decisions, original
KeepObserved proofs and final XML locations remain separate. Final provenance
comes from verified original-to-final associations, including edited identities
and Fresh references; no donor-derived evidence is created.

Fifteen author tests use independently invented XML and real compiler/adapters:
direct and child mappings, exact escaped output, source metadata/inventory changes,
changed full proof/graph/reference data, wrong versions/pins, failed-rule repair,
Unicode, empty/unresolved/absent inputs, group split/merge and last contributor
removal, fresh identity reuse, duplicate contributors across documents and a
cross-document containment move with distinct original/final source proofs.

The exact bound control accepts19,998 physical nodes plus2 computed nodes with
complete membership/contributor lists and rejects19,999 plus2. A source above
the existing1MiB character limit refuses. A real worker is observed inside the
actual materializer on8,000 invented entities before signalling the original
cancellation flag; it returns CANCELLED and no target. These are behavior controls,
not combined peak-memory qualification.

The independent reviewer changed only original failing RuleCheck outcomes to PASS,
retaining graph/input/provenance. An independently verified target repair remains
valid, but the tampered original refuses STALE_CONTENT. Removing exact derived
result equality makes this test fail with Complete; restored source passes.

## Actual commands and corrections

Pinned Maven3.9.16/JDK21.0.12. Separate real preimplementation RED runs reached
projection and materialization scaffolds and each failed one assertion with zero
errors. Implementations then passed their controls. Expanded tests initially
expected deletion of an existing optional attribute and omitted the explicit
empty default namespace on a moved fragment. Existing structural contracts
require refusal and namespace insertion respectively; corrected literal test
expectations passed without changing production behavior. Original logs remain.

One focused command selected no parser tests; a corrected selection ran them.
The reviewer's initial test reversed Content constructor arguments and failed
compilation. Neither setup failure is counted as behavioral RED or mutation proof.
Final author focused38 and full1,172 passed. Independent focused/restored22 passed.
Seven author mutations and the distinct independent rule-forgery mutation compiled
cleanly and failed assertions; every restored control passed.

Integration command: `mvn -B -ntp -f backend/pom.xml verify` in
`es-v3-content-integrated-0lcee55u`, log
`es-v3-content-integrated-full1-20260909.log`. **1,187 tests passed**:279 core,
7 parser,637 server,264 supervisor; zero failures/errors/skips. Assembly and
hostile-launch checks passed. Finished9 September2026 at22:29:35BST.

No new database, browser, OCI, remote CI or HiveForge run is claimed here. Earlier
actual database workflows have separate scope. Retaining complete original,
expected physical, typed and final proof sets adds memory beyond older physical
measurements; maximum combined retention remains unqualified. Business review
preserves disabled availability; engineering review preserves one future owning
service and explicit versions; QA review retains stale/cancel/refusal and full
evidence oracles. Integration ran alongside isolated lifecycle work; no measured
parallel speed-up is claimed.
