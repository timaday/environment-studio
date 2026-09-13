# Versioned plan definition model

Internal prerequisite for [v3 hosted plans](hosted-plans-v3.md). One hosted plan
service will retain lifecycle, capacity, cancellation and cleanup authority for
both versions. This model does not enable v3 admission or change any HTTP schema.

`PlanDefinition` is a closed immutable union: V2 retains the actual v2
ReadyToPublish object; V3 retains checked v3 metadata. Its physical declaration
view contains only the original physical entity types, relations, count rules
and operation declarations. For v3, computed types, derivations, co-occurrences
and computed rules remain accessible only through the complete v3 variant.
Constructing a physical view never constructs v2 readiness, recompiles metadata,
changes a digest or turns a computed type into a portable physical entity.
Incidental model rendering is redacted. Metadata alone is not publication,
observation, validation or export authority.

The internal published-definition pin stores this model beside its immutable
workspace reference, publication digest and policies. Existing v2 construction
and `compiled()` access retain the original ReadyToPublish value. Calling that
v2 accessor on a v3 pin refuses UNSUPPORTED_DEFINITION; there is no conversion
or fallback. Existing hosted creation therefore still refuses a supplied v3 pin
before any plan installation, destination reservation or credential read.
Separate current v3 publication lookup and subsequent lifecycle integration are
required before any production v3 plan can exist.

## Physical composition into original target intent

The versioned `PlanComposition.merge` accepts the model, prior target intent,
current materialized physical content/provenance and a physical-only composition
proposal. The existing v2 overload delegates to the same physical algorithm.
It does not accept HTTP input or independently qualify the proposal: the caller
must freshly validate the matching version's profile and preview against the
exact target before merging, then independently materialize/reproject the result.

New physical slots seed all declared fields and outgoing references as unresolved;
profile shape supplies no donor values. Proposed physical reference/containment
assignments replace only the selected relation, preserving unrelated assignments.
Existing target identities resolve through the complete original provenance map:
an edited identity never creates a new Existing reference, and a previously
created entity remains Fresh with its existing Entered values. Missing original
provenance, unknown physical declarations and duplicate fresh slots refuse
PROFILE_REFUSED. Existing placement decisions are retained; newly created
entities still require explicit physical placement and values.

The physical view is insufficient for v3 application validation. After later
hosted integration, fresh target XML must satisfy complete physical and computed
comparison, contributor proofs, shared limits and separate v3 input fingerprints.
Computed dependencies/groups never enter this proposal or freeze donor evidence.

## Acceptance and compatibility

Use independently invented declarations and actual v3 compiler/profile-composer
results. Verify whole/partial physical reuse, exact unresolved fields/references,
original Existing/Fresh provenance after identity edits, unrelated sibling and
placement preservation, duplicate slot and missing provenance refusal. V3
computed declarations/rules remain in the full model and outside physical merge.
Existing v2 behavior, digests, history and hosted admission tests remain unchanged.
Explicit tests must show that v3 metadata cannot pass the v2 Ready accessor or
reserve a destination through the still-v2 hosted creation path.
