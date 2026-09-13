# V3 model and physical plan composition

Reviewed internal prerequisite,9 September2026. The
[versioned plan model](../contracts/plan-definition-model.md) retains actual v2
readiness or complete v3 checked metadata. Its physical declaration view allows
both versions to use the existing composition algorithm while keeping computed
declarations/rules separate. Two existing service composition call sites now use
the model. No v3 readiness conversion or hosted creation path is enabled.

The legacy v2 published-definition constructor and accessor retain the original
ReadyToPublish value. A v3 pin refuses that accessor and existing hosted creation
before plan installation/reservation. Model construction and physical merge are
internal data operations; callers still owe fresh publication/profile/target
checks and complete final XML/derived comparison.

## Candidate and actual TDD

Initial author base `8f44832`, archive `es-plan-model-w17k_sjs`; nonoverlapping
candidate rebased onto `1878436` in `es-plan-model-candidate-ln_xr2y7`. Frozen
six-file manifest SHA-256:
`b57dfdf492573fbf20cc2ca001346f9bb9bcab2c3da96c0a68bc23c09ed2be3f`.
The contract preceded implementation.

RED1 failed five ambiguous test imports; it established no production failure.
Corrected RED2 compiled the actual invented v3 definition and physical profile
and reached the untouched UNSUPPORTED_DEFINITION merge scaffold: one assertion
failure, zero errors; three existing composition controls passed. Implementation
followed RED2. Initial GREEN passed13 tests. Expanded testing first had a test-only
incorrect package for nested ObservationPort.Cancellation; corrected GREEN passed27.

Five new tests cover whole/partial physical shape, exact unresolved fields and
references, original Existing/Fresh provenance after identity edits, entered
value/placement preservation, repeated fresh slots, missing provenance and an
undeclared computed relation. Separate controls retain complete computed metadata,
exclude computed rules from physical rules and prove v3 cannot manufacture v2
readiness or consume hosted plan capacity. The actual compiler remains incomplete
with only MECHANISM_UNQUALIFIED for these valid mock definitions.

Five cleanly compiled mutations each failed one assertion with zero errors/skips:
fabricated origin from current key, overwritten entered values, accepted duplicate
fresh slot, fabricated v2 readiness and computed rules substituted as physical.
The restored27-test selection passed. Author full verification passed1,156 tests
with zero failures/errors/skips and distribution/hostile-launch checks.

## Independent review

The reviewer inspected all six fixed files in `es-model-review-ndi2fdxq` and added
an actual compiler/profile-composer control. An original Existing entity is
renamed, then an earlier Fresh entity takes its old literal identity. Partial
reuse selects that Fresh key and changes its dependency. Only the intended Fresh
reference changes; original Existing decisions, entered values and placement
remain exact.

The first independent test incorrectly expected Prepared: the composer correctly
reported NeedsResolution for the original max1 relation plus a proposed new edge.
The corrected test explicitly asserts RELATION_CARDINALITY and tests the merge
path already used by the service. That expectation failure is preserved and is
not a production defect. Corrected/restored selections each passed13 tests.
A compiled provenance-fabrication mutation failed one assertion with zero errors.
No material source or contract issue was found within this internal scope.

Independent record `es-versioned-plan-model-independent-review-20260909.md`,
SHA-256 `8eb0fe330a52f4c94a8c6e2a30792a74e64aa9c523cde11d86892849cbd154a6`;
independent test SHA-256
`3f738335ccb4000b92ee9c4bc2cf0f92377d13a0bf99aebd1d47e16fcf366c10`.
Author record `es-plan-model-author-evidence-20260909.md`, SHA-256
`cfaf6bf04eb9e8bdbee2be931b4fd4cf488360445f9dce3bdee047f740635b02`.
These records and the logs below remain under `/home/tim/.tmp`.

## Integrated result and actual database workflow

Base `1878436` plus the six files and independent test passed
`mvn -B -ntp -f backend/pom.xml verify` using Maven3.9.16/JDK21.0.12:
**1,157 tests**,279 core,7 parser,621 server,250 supervisor; zero failures,
errors or skips. Assembly and hostile-environment launcher checks passed.
Run ended22:02:45 BST on9 September2026; archive
`es-plan-model-integrated-wjl_8ub5`, log `es-plan-model-integrated-full1-20260909.log`.
Seven-file manifest SHA-256
`054489c6a522424604df48d89307ee0c0e84757fa9b61e00fd3aef5cfa3ab0aa`.
Every file hash was verified before copying to the root checkout.

The external actual Oracle/PostgreSQL workflow now uses production
PlanComposition.merge over the actual original XML projection. It asserts the
exact independently expected returned reference set and unresolved fields/
references before supplying explicit operator values and placements. Complete
final source/proof, value-free profile, committed-state, cancellation, TLS and
canary checks from the [prior workflow](qf34-database-workflow-v3.md) remain.
The reviewer inspected this delta; the lead executed both engines successfully.

Helper `V3ActualPlanCompositionProbe-20260909.java` SHA-256
`6c6b71d4f9524ca9efbae75fb87066f7e74d7e8b7bdd15cf797d5f53c018021e`;
log `es-v3-actual-plan-composition-observation-20260909.log` SHA-256
`db9823ba3991cf929d56d2a6bd9bfc3a326d16ff1334f224e9e373ad9eaea260`.
Its runner verifies candidate6 and helper hashes; result JSON records base1878436,
candidate manifest, exact class directories and compile/run exit0. The earlier
helper remains unchanged. The review record predates the successful rerun result
and does not claim independent database execution.

This is finite mock observation/composition proof, not hosted v3 lifecycle,
complete content retention, API/UI, native client, peak capacity or export
qualification. The retained f99aaad image and dbb457a remote CI predate this.
Actual current compiler/publication gates remain closed. No new GHCR, HiveForge
or release evidence is claimed. No measured parallel-development speed-up is claimed.
