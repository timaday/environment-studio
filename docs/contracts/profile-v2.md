# Value-free profile capture and composition — D05

Version 2 extends the [profile contract](definitions-and-profiles.md) for
[native v2](native-definition-v2.md). Profiles describe reusable logical
structure, never donor values or physical database/XML locations. Runtime inputs
and eligible immutable revisions remain in the private owned workspace.

## Closed portable structure

The JSON/YAML shape retains v1 profile properties, replacing `schemaVersion` with
`"2"` and `definitionDigest` with `logicalDefinitionDigest`. Required top-level
properties are `schemaVersion`, `id`, positive integer `revision`, the logical
digest, nonempty `entities` and `relations` (which may be an empty array).
No arbitrary metadata or extensions
are accepted. Parsing has the same strict UTF-8, duplicate-key, numeric, nesting
and size limits as definitions; schema and semantic validation remain separate.
The closed shape is `schemas/profile-v2.schema.json`. At most 20,000 slots and
50,000 edges are eligible; the stricter 1 MiB source and 20,000-node parser budgets
still apply. Capture must refuse an output that cannot pass the same import
budgets, rather than creating a profile that cannot be read back.

Each entity contains only `id`, `type`, `label` and `requiredInputs`. Its ID is a
neutral logical slot using the existing ID syntax, distinct from observed entity
identity, a source element index or database row key. A label is explicitly
provided neutral operator text, never automatically copied from a field value.
Required input IDs are exactly the required fields declared by its entity type,
sorted without duplicates. They declare the need for fresh values, not defaults.
Optional fields also require an explicit value-state decision during planning.
Each relation contains only `type` (declared relation ID), `from` and `to` (slot
IDs). Entities and relation triples are unique. Imported types, fields, relation
endpoints and the logical digest must match the pinned published definition.

Reject unknown keys at every level, including value bags, source XML, raw
identity, physical locator, endpoint, path, credential or encrypted-value
properties. No capture path copies these values into a profile, including fields
classified structural. Both field classifications require new target decisions.
Do not classify portability from a field name or infer that a public value is
safe to reuse. Generic labels/IDs are operator declarations; the UI explains
that actual application names and environment values do not belong there.

Validate profile graph endpoint types, duplicate edges, per-source relation
cardinality and containment cycles/multiple parents. Global environment entity
count rules apply to the completed target, not a reusable subset by itself.
A profile can be structurally valid with no literal values; that does not make
a composed target valid or authorize export. Profile revision, content digest
and logical definition digest remain immutable after publication.

## Capture

Capture requires a complete server-created observed graph and an explicit
one-to-one mapping of all captured observed entities to fresh neutral slot IDs
and labels. The full capture operation maps the complete graph; partial reuse
selects a subset afterward. Reject missing/extra/duplicate slot mappings or names
that violate syntax. Never generate slots from actual IDs, row keys or values.

Build a new allowlisted structure containing declared type IDs, slot IDs/labels,
required field IDs and relation edges remapped through that explicit mapping.
No source graph object or raw-value map is retained by the profile result or
its serialized form. Use safe diagnostics and logging. Canary tests must prove
that donor identity, environment/structural/secret values and raw document
content are absent from serialized profiles, persistence and error messages.

## Whole and partial reuse

Selection is an explicit nonempty set of profile slots; selecting all uses the
same algorithm. Resolve against a pinned compatible profile/definition revision.
The closure proposal includes every required reference target and required
containment parent, plus targets declared `includeTargetOnReuse`. Expand until
stable, preserving relation IDs and reasons. Return selected slots, added
dependencies and the relevant edges in stable order. Unrelated siblings never
enter closure merely because they share a parent or type.
Required outgoing containment still needs explicit satisfaction. If selecting a
child adds a parent whose minimum child count is no longer met, return an
unresolved cardinality conflict. The operator must select additional specific
children or compatible existing target children. Never choose siblings
automatically or present the closure alone as a sufficient target structure.

Present the proposal before composing. Each selected/dependency slot needs an
explicit decision: create a new target slot, use a specifically identified
compatible existing target entity, or cancel. A compatible existing dependency
can satisfy closure without cloning the profile's dependency. Names/types never
authorize a merge. Reject duplicate target assignments where distinct slots
would be collapsed, missing decisions, stale proposals and incompatible types.
No unresolved conflict is silently accepted. Preserve unselected existing target
entities and relations unless a separate explicit supported command changes them.

Composition returns additions, retained existing entities, proposed relation
changes, unresolved field decisions and conflicts. It does not copy donor
values, delete siblings or mutate the observation. Newly created slots require
fresh identity and environment values; existing slots initially have unresolved
decisions until the operator explicitly chooses KeepObserved or enters a value.
Relation moves/creation still pass the structural planner and writer capability
checks. A closure preview is not authorization to apply a later edited proposal.

## Revision and compatibility

Only the same `logicalDefinitionDigest` is compatible; physical Oracle/PostgreSQL
binding differences do not prevent reuse when target operations are qualified.
No automatic migration interprets v1's digest as v2's logical digest. Import or
capture produces a draft. Save/publish uses owned immutable workspace revisions,
expectedRevision and requestId replay semantics; changing an already published
revision forks a draft. HTTP/storage extensions require their own closed DTOs and
allowlists before implementation. Do not store a profile through an arbitrary
definition-source or generic payload escape hatch.

The profile content digest frames the closed profile object with the native v2
framing algorithm and domain `ES-PROFILE-2` plus a zero byte. Normalize by sorting
entities by slot ID, requiredInputs by field ID, and relation triples by
`(type, from, to)`; exclude only native profile `revision`. Preserve neutral ID,
labels and logical digest. UTF-8 framing and integer meaning are unchanged.

## Acceptance and investigation

Use only independently invented graph/profile fixtures with provenance. Capture
two source entities referring to one target, then select one source. Its required
target appears in closure while the unselected source does not. Demonstrate both
creating the dependency and explicitly selecting a compatible existing target.
Verify whole reuse, cycles, missing targets, duplicate/colliding slots, incompatible
digests/types, stale proposal/revision replay and immutable outputs. Inject donor
canaries across every field classification and identity, then independently inspect
serialized/persisted profile bytes for absence. Unknown nested metadata and
value-bearing properties must fail closed. Observe meaningful RED/GREEN and
challenge removal of the dependency/compatibility/value-free guards.
