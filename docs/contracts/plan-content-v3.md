# V3 plan content and complete derived evidence

Reviewed internal adapter prerequisite for the shared
[hosted plan lifecycle](hosted-plans-v3.md), using the
[versioned definition model](plan-definition-model.md). No route, publication
readiness, live plan admission or export capability is enabled by this contract.

## Shared content representation

`PlanPorts.Content` retains exact sources, the complete physical graph and the
mapping from final physical keys to original Existing/Fresh references. It also
retains a closed `PlanContentEvidence` variant. The existing three-argument
constructor explicitly selects V2 evidence and preserves v2 behavior.

- V2 carries no v3 proof. It cannot substitute for an empty computed result.
- V3Observed retains the original observation fingerprint, full observed
  `DerivedInput` and complete `DerivedResult`, including failing computed rules.
- V3Target retains that original fingerprint and current pin, the independently
  compiled physical `ExpectedTarget`, complete typed preliminary input/result,
  and complete independently reprojected final input/result.

All records are immutable and redact incidental rendering. They contain core
value types only, with no XML parser trees, database handles or session authority.
Record construction alone does not establish valid evidence. Original observed
locations, target decisions and actual final source locations remain distinct.
Fresh/Entered decisions never acquire donor source proofs.

## Internal projection and materialization port

`V3PlanContent.project` accepts the internal V3 model, selected binding, complete
observation and the original cancellation flag. Require a lowercase64-hex
observation fingerprint, matching current logical/binding digests and exact
unique document inventory. Validate document ID, configured key, byte/character
counts and independent source digests against the selected binding and actual
XML; do not trust declared counts or use source metadata as its own oracle.

Use the observation fingerprint as the original revision token. Feed independent
observation pins and the supplied exact documents to the actual
`DerivedGraphProjectionAdapter`. It must freshly recompile the checked metadata
and return the complete physical inventory, derived results and source proofs.
Return original Existing provenance for every physical identity. Observed failing
computed rules stay explicit evidence; they are not downgraded or dropped.

`materialize` accepts the same V3 model, an independently supplied expected
original pin, original Content, an independently supplied target-decision pin,
explicit Draft and original cancellation flag. The original content must carry
V3Observed evidence whose fingerprint/revision token equals the expected original
pin. Legacy evidence, target content substituted as original, wrong pins or
incomplete inventory refuse. The target-decision pin uses the original document
digests and a distinct valid decision revision token; it is not the final XML pin.

Independently reproject the original sources and compare exact physical graph,
complete input/result and bijective original provenance with the supplied content.
Use the actual `DerivedTargetMaterializer` and its qualified field/structural
writer and complete final comparison. Original sources supply no document-base
change; all placements come from typed Draft. After materialization, also compare
its independently rebuilt original evidence with the admitted original content.

Retain the materializer's complete typed physical expectation and derived result,
then its complete final observed input/result. Build final-key-to-original-ref
provenance only by inverting its explicit reference/actual-origin associations;
require unique complete key and reference coverage and exact final graph origins.
Never reconstruct original references from an edited target identity. Verify
matching definition/binding, original/decision/final pin roles and complete
physical/derived comparison before returning success.

Results distinguish Complete(Content), Incomplete(bounded declaration references)
and Refused(stable safe code). Unresolved fields/references remain Incomplete.
The existing DERIVED_RULE_FAILED materialization refusal remains a refusal;
failed target decisions remain available to the owning service as Draft.
Existing attribute presence transitions remain ATTRIBUTE_PRESENCE_UNSUPPORTED
under [the structural contract](structural-target.md). Retaining an optional
absence and choosing absence for a fresh field remain distinct from deleting an
existing attribute; this port introduces no new XML presence-edit mechanism.
Cancellation before or after work refuses and cannot publish a successful result.
No exception text, source value or arbitrary diagnostic enters refusal codes.

## Limits and subsequent integration

Actual adapters enforce128 documents, their existing exact XML/value limits,
and each physical+computed graph's shared20,000-node/50,000-edge limits,
100,000 contributor links and8MiB unique tuple UTF-8 limit. Full inputs/results
remain mandatory at a boundary; neither truncation nor a count-only summary is
successful evidence. Validate original content through fresh actual projection,
rather than maintaining a second derived evaluator in the plan adapter.

Retaining original content, target physical expectation, preliminary/final proofs
and observation/materialization scratch increases memory beyond previous physical
measurements. Existing6GiB tests do not qualify this combined retention. Measure
the integrated current candidate before admitting supported maxima.

The shared HostedPlanService must still capture and recheck the original live
lease, plan revision/generation, observation context, cancellation, scratch and
global capacity before/after these calls, then install atomically. Profile capture,
whole/partial composition, comparison views and versioned validation/fingerprints
must use this evidence before v3 runtime availability. No separate lifecycle or
capacity registry is introduced by the content port.

## Acceptance

Use actual independently invented XML and the real compiler, projection and
materializer. Test direct and child-property fields, complete multi-document
inventory, optional absence versus empty identity, group merge/split and last
contributor removal, exact escaped output, fresh creation, edited Existing
identities and moved final origins. Verify whole contributor/membership/co-
occurrence proofs, expected physical output and separate original/final pins.

Refuse changed source/count/key/digest metadata, duplicate/missing documents,
wrong version/evidence variant, stale or changed checked metadata, changed graph,
input, result or provenance, missing cancellation and cancellation after work.
Unresolved choices must not masquerade as refusal-free empty graphs. Existing
v2 content constructors and full compatibility tests retain their original scope.
