# Internal controlled v3 physical views

The internal v3 presentation adapter supplies document inventory, physical entity
and relation pages, explicit draft/containment, original placement choices and
concrete physical binding/every-location pages. It accepts the existing pinned
ViewAdmission; a snapshot alone is not live authority. No HTTP route, publication
qualification or browser availability is added. Existing v1 wire behavior and
v2 masking/value semantics remain unchanged.

The view owner invokes a trusted read callback with its immutable snapshot and
original cancellation flag. It checks the original lease, revision, generation,
inspection and active-operation identities before and after the callback. Closing
or invalidating that admission signals the same flag; nested reads cannot renew
scratch or return late success. The callback cannot grant export authority.

Before v3 presentation, freshly verify complete original content and, whenever a
target is retained, the complete actual target materialization/proof against the
original and decision pins. Use the existing V3PlanReadContent verifier. Missing
target refuses selected target graph/location requests, while original inventory,
draft choices and original placement remain readable. A failed inspection may
leave original content readable without capture/export authority. Retained proofs
cannot substitute for current ownership or independent re-verification.

Use the selected v3 model's declared physical types/bindings without constructing
v2 readiness. Computed nodes have no physical handles, editable fields, placements
or invented XML locations. This slice does not render computed contributor pages.
Stable opaque Existing/Fresh handles retain their original provenance through
identity changes and same-literal replacement.

Keep the existing physical page shapes, complete totals, stable ordering and
100-item page limit. Document absent target digest/change are null, never
unchanged. Concrete binding states remain value, masked, absent, unresolved or
unavailable; field change remains unchanged, changed, added, removed or unresolved.
No retained target is not an empty complete target. Fresh current locations are
explicitly unavailable; incomplete target locations are unavailable. An actual
complete absent field has zero locations. All mapped references and qualified
attribute/child-property occurrences count; pagination cannot omit siblings or
report a page length as the complete total. Location disclosure remains mandatory.
Never show denied/secret/unknown-sensitivity text as concrete values.

Every bounded projection/scan and result-building loop observes the same original
control. Legacy uncontrolled adapter entry points continue refusing v3; only the
explicit controlled v3 adapter composes these helpers. Response encoding and
transfer limits remain the future versioned HTTP owner's obligation. Additional
full proof recomputation and retained graph/page memory require combined resource
qualification before runtime admission.

Acceptance uses independently invented actual XML/compiler/content adapters:
original-only inspection, unresolved target values, changed fields/identity,
Fresh replacement/removal, attribute and child-property locations, eligible
original parent coordinates, complete counts, explicit masking/absence and no
computed leakage. Forged original/target proof and cancellation during/after
projection refuse, unchanged v2 output stays exact. No test publication witness
qualifies the actual v3 compiler.
