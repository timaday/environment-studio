# Profile v3 — physical structure with fresh derived results

Status: planned portable contract for [native v3](native-definition-v3.md).
Capture, import, persistence, publication and composition are unimplemented for
this version. [Profile v2](profile-v2.md) and its historical bytes remain unchanged.

## Closed portable shape and digest

Use the same required closed shape as profile v2, with `schemaVersion: "3"`:
`id`, positive integer `revision`, `logicalDefinitionDigest`, nonempty `entities`
and possibly empty `relations`. The digest must match the selected v3 definition.
Each entity has only neutral `id`, physical `type`, neutral `label` and
`requiredInputs`. Each relation has only `type`, `from` and `to`, referring to a
physical relation and explicit neutral slot IDs. All v2 ID/label/source/output,
integer, slot/edge and parser budgets apply. The source and decimal-string
inspection schemas are `schemas/profile-v3.schema.json` and
`schemas/profile-inspection-v3.schema.json`.

Computed types are never slot types. Membership/co-occurrence relations are
never profile edges. Reject derived declarations, groups, values, computed keys,
contributors, constraints, source identities and physical locators in profiles;
there is no derived extension bag. JSON shape checks alone cannot establish that
a named type or relation belongs to the physical partition: semantic validation
must check it against the pinned definition. Required inputs remain exactly the
required fields of the named physical type. Runtime field decisions still cover
optional fields explicitly.

Hash UTF-8 `ES-PROFILE-3`, a zero byte and the normalized profile using the native
strict UTF-8 framing algorithm. Sort entities by slot ID, required inputs by field
ID and relation triples by `(type, from, to)`; exclude only native `revision`.
Retain `schemaVersion: "3"`, neutral ID/labels and `logicalDefinitionDigest`.
Values and computed results never enter this digest or the portable profile.
No v2 digest is reinterpreted as a v3 digest.

## Capture, preview and composition

Capture requires a complete, server-owned and revision-pinned physical/derived
observation. Require an explicit one-to-one mapping of all physical entities to
fresh neutral slots/labels. A computed node needs no mapping and supplying one
is rejected. Copy only physical type IDs, required field IDs and physical edges
through that mapping. Do not retain the donor graph, any computed result or
contributor object. Enforce portable encoding and re-import budgets before
accepting output; core structural validity alone is insufficient.

Whole and partial reuse follow v2 physical closure, explicit mapping and conflict
rules. Computed nodes and edges do not enter that closure. Required physical
dependencies still appear; unselected siblings are preserved. Preview separately
identifies declared derivations affected by the selected physical source types
and explains that fresh/retained target decisions and complete target validation
determine their results. It contains declaration references only, never donor
group identities, frozen membership or portable derived constraints. A missing
derived minimum cannot silently select another donor source or infer a value.

The planner recomputes every target derivation after composition, including
unchanged physical entities. Any co-occurrence/count constraint validates the
whole destination, not the selected donor subset. Unresolved input leaves the
affected required checks UNKNOWN. Fresh values or explicit KeepObserved choices
can merge, split, add or remove groups; no profile decision freezes them. Final
XML reprojection and independent derivation comparison remain mandatory.

Profile validation checks only physical structural constraints. Global physical
and computed count rules apply to the complete target. Profile structural
validity, capture, closure or a matching logical digest does not authorize a
composed target or export. Donor computed dependencies are deliberately absent;
this contract does not promise portable derived constraint closure.

## History and evidence

Import/capture creates a draft under a separate closed v3 workspace extension.
Publish a new owned immutable revision explicitly. Existing v2 profiles remain
readable/replayable under v2 and cannot be used as v3 by replacing their digest or
version field. An eventual conversion must be an explicit reviewed operation
that validates physical structure against the new logical definition and creates
a new draft; no automatic conversion mechanism is qualified by this contract.

Use independently invented donor canaries and neutral declarations. Inspect
serialized and persisted bytes, diagnostic paths and object retention for donor
values, computed keys and contributors. Demonstrate all/partial reuse with fresh
target groups, empty physical input refusal for the nonempty portable shape,
computed-slot/edge rejection, unchanged siblings, stale revision/proposal refusal,
and old profile hash/history goldens. Internal graph tests do not qualify these
capture, hosted UI, persistence or export paths.
