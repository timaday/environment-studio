# Derived graph v1 — complete recomputation and proof

Status: planned mechanism, not registered as available. Implements the
[approved decision](../product/derived-graph-decision.md) for
[native v3](native-definition-v3.md). Physical graph validation, physical identity
uniqueness and XML fidelity retain their existing contracts.

## Typed results and exact computation

Keep physical and computed partitions separate. A computed node's key is the
tuple `(computedType, derivationId, exactDecodedValue)`. A membership edge's key
is `(membershipRelation, physicalOccurrence, computedKey)`; a co-occurrence edge's
key is `(relationId, sourceComputedKey, targetComputedKey)`. The physical occurrence
is the already unique physical entity and its pinned origin, not a donor slot or
an invented XML element for a computed node. Hashes may index keys but never
replace exact equality. Value-bearing objects render as redacted in diagnostics,
logs and incidental string conversion.

Validate all declared source-field eligibility before any value-dependent work.
Then use strictly decoded, codec-validated Unicode text without trim, case-fold,
normalization or sentinel. Optional absence contributes nothing. Required absence
invalidates the physical result. Present empty text refuses derived completion;
nonempty whitespace remains exact. Invalid/malformed source text refuses the
whole result. Computed values are PUBLIC inputs but remain operation-scoped data,
subject to owner authorization and transient-data controls.

For each derivation and each physical source occurrence with a present valid
field, add its computed key and one membership edge. Equal keys merge groups,
never physical occurrences. For each co-occurrence, inspect the two declared
fields on that same occurrence. If both are present and valid, contribute the
directed computed edge. Either optional absence contributes no pair. Never join
across different occurrences or take a Cartesian product. The same derivation at
both endpoints produces a self-edge. Deduplicate edge keys, retaining all their
distinct occurrence contributors. Removing one contributor preserves the result
while others remain; removing the last removes it.

Sort computed keys by unsigned strict UTF-8 bytes of each tuple component in
order. Sort membership keys by relation ID, then physical origin order below,
then computed key. Sort co-occurrence keys by relation ID, source key, target key.
Do not use Java UTF-16 `String.compareTo` for value ordering: U+E000 sorts before
U+10000 in this contract. Field values with canonically equivalent Unicode remain
different keys. Labels never participate in equality or ordering.

## Contributors and revision pins

Every computed node and derived edge has a separate nonempty contributor list.
An observed contributor identifies the physical entity, document ID, projection
ID, source digest and numeric entity element index, plus the actual source field
location. The location includes the logical field ID and pinned attribute
reference on its owning element; a child-property value can have a different
element index from its physical entity. Selection pins and source ownership must
be checked through the qualified physical locator. Co-occurrence contributors
carry both field locations in declared source-side then target-side order.
When both sides use the same field, keep both ordered roles.

Contributor lists sort by document ID then projection ID in unsigned UTF-8 order,
then numeric entity element index. Each occurrence appears once per result, with
its required field location(s). Equal occurrence identity with conflicting pins
or locations refuses; it must not be arbitrarily deduplicated. Every supplied
pin must match the complete physical observation and definition/binding revision.
Missing, extra, foreign, stale or ambiguous contributor evidence refuses the
whole result. No first-contributor shortcut or truncation is allowed.

For a typed target before XML materialization, contributors instead refer to the
explicit existing/fresh physical target reference and resolved typed field
decisions. Fresh entities have no source XML origin. After materialization, map
those references to actual final physical origins through the assembly's verified
provenance. Independently reproject all final documents and recompute derived
state from their actual locators; do not reuse preliminary computed results.
Compare exact node keys, edge keys and all contributor field roles after this
mapping, including source/target pins and physical value decisions. Missing,
extra or unequal results refuse target completion. A graph hash alone is
insufficient evidence of this comparison.

Before materialization, order physical target references by `(type, kind, key)`:
unsigned UTF-8 type, Existing before Fresh, then unsigned UTF-8 original observed
identity for Existing or explicit neutral slot ID for Fresh. This reference order
replaces observed-origin order in preliminary membership and contributor lists;
an edited identity does not rename its Existing reference. Never depend on command
or insertion order. After assembly maps references to final origins, compare exact
sets with complete contributor roles/pins, then order final output by final origin.
Different preliminary/final list order alone is not a mismatch, and matching
counts alone is not equality.

## Validation and incomplete inputs

Recompute current and target separately. Every edit, create, removal, relation
change or physical profile composition invalidates prior target derivations.
There are no computed retain/create/remove/field commands and no inferred
cascades or default values. Physical field decisions are resolved before typed
target derivation and independently verified after final XML projection.

A complete computed graph is distinct from a valid computed graph: complete
results can fail a declared count or cardinality rule. Count rules inspect all
computed instances of the named type. Co-occurrence cardinality counts distinct
outgoing target keys for each existing source computed node, including zero.
An absent source node has no per-source obligation; a computed type-count minimum
is required to demand one. Empty complete input produces an empty computed graph;
minimum zero passes and a positive count minimum fails.

Any unresolved derivation input, including an optional field whose target decision
is unresolved, or missing evidence is UNKNOWN. Only explicit optional absence
contributes nothing. Return a typed incomplete result naming safe declaration
or rule references, with no complete graph, definitive affected count or success
proof. Invalid values, stale pins, ambiguity and resource overflow are typed
refusals. Do not silently omit unknown contributors and validate a smaller graph.
Independent physical checks may retain their own results, but required derived
UNKNOWN/refusal/failure always prevents complete target validation and export.
Late cancellation, owner revocation or revision change discards computed results
under the same cleanup contract as the physical observation.

## Admission budgets

Bounds apply to each current or target graph, including physical and computed
parts, and are shared rather than added to the existing v2 capacities:

| Counter | Maximum | Charge before inserting |
| --- | --- | --- |
| Nodes | 20,000 | Every physical entity plus each distinct computed key |
| Edges | 50,000 | Every physical edge plus each distinct membership/co-occurrence edge |
| Contributor links | 100,000 | One occurrence-to-result association for each computed node or derived edge |
| Computed identity bytes | 8,388,608 | Strict UTF-8 value bytes once per distinct computed key |

For one occurrence participating in one derivation, its group and membership
consume two contributor links. One co-occurrence adds one link, even when the
edge already exists and both field roles use the same field. Multiple occurrences
sharing a result each consume a link. Re-encountering the same occurrence/result
association is an input/provenance error, not a free way to lose contributors.
Equal text in different derivations creates different keys and is charged once
for each such key. Repeated values of the same key are not charged again.
Association count bounds provenance even when edge/node deduplication is large.

Check physical counts first; enforce incremental counters, checked arithmetic,
strict Unicode and source limits before allocating result collections or encoded
buffers. A duplicate key does not consume another result count. Do not build an
unbounded candidate cross-product before checking limits. On overflow discard the
whole result; no partial graph or truncated proof is accepted. Parser, inventory,
transport, operation, plan ownership and deadlines still apply. A 20,000-node
physical graph has no remaining computed capacity. A 50,000-edge physical graph
has no remaining membership capacity. Show these as v3 support constraints;
they do not narrow v2. These contractual maxima require separate integrated heap
and lifecycle qualification before being advertised as an available workload.

## Required independent evidence

The first implementation boundary is an internal computation port over a complete
physical input supplied by an adapter. It is not an HTTP DTO, publication result
or permission to trust caller-created graph records. The port receives expected
snapshot pins separately from supplied input pins: opaque transient revision token,
logical digest, binding ID/digest and the complete document-to-source-digest map.
It checks equality and the selected checked definition. Tokens and hashes confer
no owner, database or export authority. Observed source inventory, qualified field
selection and physical validation are adapter prerequisites; typed input additionally
requires complete explicit physical topology/decisions. Those adapters and their
live revision/owner checks require separate qualification before runtime availability.

The internal physical input contains actual entity and edge lists, never claimed
counts. Every entity has a separate Observed reference (physical key and origin)
or Target reference (Existing/Fresh), and explicit source-field states Present,
Absent or Unresolved. One input uses one reference kind. A Present field carries
its exact text and a separate observed-location or target-decision proof. Observed
proof identifies the value attribute and, for a child locator, the selected child
name, parent index and discriminator attribute. Attribute pins include document,
source digest, owner element index, expanded/qualified name, decoded value,
UTF-16 value span and quote. Core checks pin consistency; only the XML adapter
can establish that those spans and selectors actually belong to the source.
Target proof retains the explicit typed decision and observed evidence for KeepObserved;
fresh/Entered values have no fabricated XML pin. Required source-field states
may not be omitted; absence is explicit. Unresolved observed input is invalid;
unresolved target input produces incomplete computation without a graph.

Core groups and checks only eligible derivation inputs after the complete input
boundary checks. Other physical rules retain their independently validated result.
Every physical entity/edge still contributes to shared capacity. A Complete result
contains the computed partition, all contributors and explicit count/cardinality
outcomes; failed rules cannot authorize a target. No first internal implementation
may advertise the still-unimplemented adapters, typed materialization comparison,
hosted views, profile/history or export as qualified.

### Observed XML input adapter

An internal observed adapter accepts a checked v3 declaration, independently held
expected pins, an immutable source snapshot and the owning operation's cancellation
signal. The snapshot supplies its own revision token, logical digest, binding ID
and binding digest plus the actual complete document sources. It must not copy
an expected revision token into supplied evidence or derive source pins from an
expected digest map. Compare snapshot metadata and the exact selected v3 checked
metadata before parsing; check the full supplied/declared document inventory and
existing per-document 1 MiB UTF-16 / per-observation 16 MiB strict UTF-8 bounds.

Reuse the physical projection/locator mechanism without manufacturing a v2 ready
result, changing a v2 digest domain or enabling v3 availability. Validate actual
physical identities, fields and topology. Obtain each document digest and every
attribute/selector pin from its parsed source, and require exact equality with
the independently expected source digests. A bounded second parse of the same
immutable source may construct field proofs; it must verify the same source pin.
Only complete current input can reach derived computation. Missing/ambiguous
selection, invalid physical graphs, stale pins or cancellation returns refusal,
with no partial physical/computed result.

The complete internal result retains exact transient sources, the complete physical
graph, the observed computation input and the derived partition/rule outcomes.
All value-bearing wrappers render redacted. Cancellation is checked during bounded
inventory/projection/proof work and immediately before returning a complete result;
one existing parser/validator operation is not made immediately interruptible by
these checks. Caller-owned live revision/owner, deadlines and DB snapshot authority
remain separate prerequisites. This adapter establishes XML/source correspondence,
not JDBC, typed target decisions, final materialization, hosted disclosure, profile,
package or runtime publication authority.

Exercise the decision record's invented tuples, plus directional/self edges,
optional absence on each side, last-contributor deletion, whitespace and Unicode
ordering/equality. Use independent expected node, edge and complete contributor
sets, not values calculated by the engine under test. Test exact and one-over
budgets, repeated-pair provenance growth, equal text in different derivations,
zero-source minima, unresolved inputs and hostile declaration sensitivity before
any value access. Mutate eligibility, deduplication, provenance, revision pins,
resource checks and final reprojection to demonstrate meaningful failures.
Compiler/string tests do not qualify JDBC, hosted disclosure, writers, profiles,
history, export packages, native clients or deployment.
