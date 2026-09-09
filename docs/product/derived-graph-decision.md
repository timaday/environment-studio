# Derived entities and co-occurrence — approved direction

Status: **Tim approved this direction on 9 September 2026; runtime unimplemented**.
Scope: amended QF-0003/0004, ES-04/05/06/09. This is a generic semantic decision,
not an application model or evidence of mechanism qualification. The amended
9 September instruction requires this decision before authoritative derivations.

## Recommendation and alternatives

Adopt explicitly declared, authoritative computed entities and relationships,
with **physical-only profiles and recomputation from fresh target values**.
This lets declared validation use the derived graph without inventing XML origins
or carrying donor groups into reuse. It needs a new definition/profile contract
version; the current v2 physical-entity contract remains unchanged.

| Option | Consequence |
| --- | --- |
| Display-only grouping | Useful navigation; excluded from validation, dependencies, profiles and export authority. Does not satisfy authoritative modelling needs. |
| Authoritative computation, physical-only profiles — recommended | Validate computed target groups/edges after every materialized change. Profiles carry only physical slots/relations; fresh values determine groups. |
| Authoritative computation with portable derived constraints | Requires a separate value-free constraint and reuse-resolution language. Greater scope; no ordinary donor-derived slots or frozen membership. |

The recommendation deliberately does not promise donor-derived dependency closure.
Reuse previews physical dependencies and identifies declared derivations whose
constraints remain unresolved until fresh values are supplied. No derived minimum
may silently add a donor source, infer a value or choose an unrelated sibling.
If portable derived constraints are required, choose the third option before
implementation rather than treating the second as satisfying that requirement.

## Proposed semantics

Each derivation declares a unique ID, one physical source type, one readable
text field and one distinct computed type. Each computed type belongs to exactly
one derivation and cannot also be a physical projected type. No derived-to-derived
input, expression, normalization, implicit type or inferred relationship exists.

Eligibility initially requires explicit `PUBLIC` sensitivity and readable text.
`INTERNAL`, `SECRET`, `UNKNOWN`, unreadable and non-text fields are unsupported
derivation inputs. Classification as structural/environment does not grant
disclosure. Reject an ineligible declaration before computing any groups, counts,
edges or value-dependent hashes. Diagnostics name only declarations and safe
codes. Runtime authorization and redacted value-object rendering still apply.

Compare decoded Unicode strings exactly, with no trimming, case folding, Unicode
normalization or sentinel values. Optional absence contributes nothing; a required
absence already invalidates the physical graph. Present empty text is an invalid
derived identity and blocks completion, rather than being skipped. Nonempty
whitespace remains exact text under the ordinary text codec. Malformed Unicode
or an invalid source value refuses the whole operation.

A computed node's identity is the structured tuple `(computed type ID, derivation
ID, exact value)`, distinct from every physical identity and profile slot. Use
unsigned UTF-8 ordering of tuple components for deterministic output. No hash
alone establishes equality. A declaration supplies a unique membership relation
ID, disjoint from physical and other derived relation IDs. A source occurrence contributes one membership edge
to its computed node when its field is present and valid. Repeated values merge
computed groups only; physical occurrences remain distinct.

A co-occurrence declaration names two derivations on the **same physical source
type**. For each physical occurrence, contribute an edge only if both selected
fields are present and valid on that occurrence. Either absent optional field
contributes no pair. Edges are unique by `(relation ID, source computed identity,
target computed identity)`. There is no Cartesian product, attribute-reference
encoding or inference across occurrences. Direction is declared. Selecting the
same derivation at both endpoints is allowed and contributes a self-edge for each
distinct present value; duplicates still collapse by the declared relation ID.
Removing one
contributor preserves an edge while others remain; removing the last removes it.
Cardinality counts distinct outgoing targets, including zero for each existing
computed source node. Membership is 0..1 for an optional source field and exactly
1 for a required source field in a complete graph.

Keep all contributor provenance in a separate bounded transient structure: each
node and edge points to every contributing physical occurrence and its field
locator(s), pinned to the source revision. Never manufacture an XML element origin
for a computed node. Ordering uses document ID then projection ID in unsigned
UTF-8 order, followed by numeric element index. Co-occurrence field locators use
declared source-side then target-side order. No truncation, first-contributor-only
shortcut or partial graph success.

Recompute current and target independently from their physical graphs. Resolve
target typed field decisions before materialization, then independently reproject
and recompute from final XML. After edits, creation, removal or profile composition,
discard old derived state and recompute before validation. An unresolved target
input leaves affected checks UNKNOWN and blocks completion: do not omit the unknown
contributor and report a definitive smaller group/edge count. Computed nodes have no retain/create/remove/value
planner commands. Groups may appear, disappear, merge or split solely as results
of explicit physical changes. No inferred cascade alters another source entity.

Empty complete physical input produces an empty computed graph. Declared count
minimums still apply to computed types; a positive minimum fails on zero. Relation
minimums apply to each existing source node and are vacuous when none exist;
use a type-count minimum to require an instance. Never create a placeholder group.

Initial proposed bounds: at most 32 derivations and 32 co-occurrence declarations;
20,000 total physical plus computed nodes and 50,000 total physical, membership
and co-occurrence edges per graph; 100,000 contributor links across derived nodes
and edges, counting each occurrence-to-result association; 8 MiB strict UTF-8
for distinct computed identity values. Existing source, parser, plan and transport
limits also apply. Enforce bounds during construction before excessive allocation;
overflow refuses the entire result. These are proposed limits requiring resource
qualification, not a claim that the current deployment can support them. The total
graph caps are an explicit v3 capacity tradeoff: 20,000 physical nodes leave no
room for a computed node, and 50,000 physical edges leave no room for membership.
Show these limits as v3 support constraints. Existing v2 admitted capacities do
not change; larger derived workloads need a separately qualified budget decision.

## Profiles and compatibility

Version 3 capture maps all physical entities to explicit neutral slots and copies
only physical relations. It excludes computed identities, values, nodes, edges
and contributor lists. Required inputs describe physical fields. Whole/partial
reuse operates on this physical structure; recompute the destination's groups
only after explicit fresh/retained target decisions. Global and derived constraints
validate the complete target, not the donor subset. Computed nodes are never
reusable donor slots or implicit physical dependencies.

Authoritative derivation declarations, source-field eligibility, absence/identity
rules, membership/co-occurrence semantics, bounds that affect accepted graphs and
validation constraints belong in the new logical compatibility contract. Physical
extraction and qualified mechanism dependencies belong in binding compatibility.
Exact field values and contributor identities remain outside definition/profile
digests. Runtime graph evidence is value-bearing, access-controlled and never
written into profiles or safe diagnostics. A visual-only option has no effect on
authoritative digests or validation.

No v2 snapshot, published revision, profile or plan acquires derivations implicitly.
Keep historical reading/replay under its original codec. A maintainer must supply,
compile and publish an explicit new-version definition; profile migration requires
reviewed explicit conversion, and plans must select the new revision. Define the
new closed schemas/digest domains before coding; no old hash changes meaning.

## Acceptance and approval

All examples below are independently invented text tuples, not private fixtures.

| Case | Required outcome |
| --- | --- |
| Three items with category values alpha, alpha, beta | Two groups and three membership edges; three physical items remain. |
| Same-occurrence pairs (alpha,x), (alpha,y), (beta,x) | Exactly three co-occurrence edges; beta→y is absent. |
| Add a second (alpha,x); remove either contributor, then the last | One alpha→x edge, then one, then zero; provenance tracks each change. |
| Edit alpha to beta; create/remove an item; compose physical profile slots | Recompute group membership and all constraints; no frozen donor group. |
| Optional absent, present empty, alpha versus Alpha, composed/decomposed Unicode | No contribution; refusal; distinct groups; distinct groups respectively. |
| Secret/unknown/internal input, overflow, missing contributor or stale revision | No complete graph, exported evidence or value-bearing diagnostic. |
| Empty input with minimum zero versus one | Complete empty graph versus count failure. |
| Capture/reuse with donor canaries | No computed group value/membership in profile; destination values determine results. |

Business: choose whether authoritative validation and physical-only reuse match
the intended need. Engineering: preserve separate physical/computed models,
versioned compatibility and complete recomputation. QA: use independent expected
node/edge/provenance sets and adverse privacy/resource/stale controls; string
examples alone do not qualify Java behavior, database clients or deployment.

Tim explicitly selected “Approve the proposed authoritative semantics” after
review of this record. Approval covers authoritative recomputation, physical-only
v3 profiles, PUBLIC text inputs, the eligibility/absence rules and shared graph
limits above. It does not qualify runtime behavior or authorize Q publication.
Write the closed v3 contracts before implementation; QF-0001/0002 remains the
first implementation slice alongside independent MVP work.
