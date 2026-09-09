# Structural target generation

D06a resolves explicit graph edits into complete target XML documents. It is an
internal, stateless mechanism under [planning](planning.md),
[native definitions](native-definition-v2.md) and [lossless XML](lossless-xml.md).
It does not publish definitions, persist observations, authorize export or accept
HTTP-supplied validation evidence. Later hosting must bind the operation to an
owned plan revision, published definition/profile revisions and a complete
database observation fingerprint.

## Inputs and decisions

The core intent compiler accepts a compiler-ready native definition, the complete
validated observed graph and a bounded typed intent. It produces either an
expected complete semantic target or a safe rejection. The server materializer
accepts that definition, one binding ID, the complete exact source inventory and
explicit target intent/placements. It reprojects the source inventory itself;
caller-supplied graph values, source offsets or digests cannot replace that check.
Successful materialization returns every complete target document, the final
graph and the explicit affected entity/document set. Failure returns no partial
target. A draft with unresolved choices may be displayed structurally but cannot
claim to be materialized XML.

An entity reference is either an existing observed type/identity key or a fresh
operator-owned neutral slot ID with an explicit declared type. These identity
domains remain distinct. Fresh slots never import donor values. Unselected
observed entities, fields and relations are retained. Omission is not deletion.
Every selected entity has an explicit disposition: retain/edit or remove; every
fresh slot has an explicit create disposition. Duplicate, unknown or conflicting
decisions reject. No many-to-one collapse or first-match mapping is permitted.

Field decisions are `Unresolved`, `Entered(exact text)`, `KeepObserved` or
`ExplicitlyAbsent`. A selected existing entity requires a decision for every
declared field; a new entity requires fresh entered values for required fields
and explicit values/absence for optional fields. KeepObserved is only available
for a readable existing field and preserves absence separately from empty text.
Existing non-editable fields permit only KeepObserved. New values must satisfy
the declared lexical codec, requiredness and identity rules. No normalization,
generated identity, donor value, template value or cascading default is allowed.

Reference decisions distinguish KeepObserved, explicit `To(entity reference)`,
explicit absence and Unresolved. KeepObserved preserves both the original
reference literal and its original stable target entity reference. This applies
to unselected retained references too. Identity edits do not silently rewrite or
retarget incoming references: renaming an existing target and assigning its old
identity to a fresh slot cannot capture a retained reference. Every affected
reference needs explicit rebinding or must still resolve to its original entity
under complete target validation.
A selected retained entity must decide every declared reference as well as every
field. Fresh entities cannot choose KeepObserved. Creating a reference or
containment edge requires move-relation in addition to create-entity for any new
endpoint; new edges are actual relation changes, not implicit creation defaults.
Absent-to-present or present-to-absent edits of existing mapped attributes remain
unsupported under xml-span-v1; retaining optional absence is legal. Creation may
supply fresh field and reference attributes directly.

Containment is an explicit relation between target entities, with a compatible
physical placement. Moving an existing entity requires an explicit new parent;
the final containment graph and XML ancestry must agree. A removed entity's
modeled descendants each require explicit removal or qualified relocation.
Incoming references must be explicitly resolved. There is no deletion cascade.
All operations must be declared by the definition, including retain-entity for
preserved entities, bind-field for actual scalar changes and move-relation for
actual reference or containment changes.

## Independent semantic expectation

Resolve the complete expected graph before patch assembly. Validate exact
type-scoped identities, all field presence/values/codecs, reference resolution,
relation cardinalities, containment uniqueness/acyclicity and declared global
count rules. Retained entities and dependencies participate in these checks.
Temporary edit order cannot excuse an invalid final graph. Independent expected
entities/edges use semantic identity and exact values, not source offsets,
element indices or source digests.

After XML materialization, reproject every complete target document through the
qualified graph adapter. Compare its exact semantic entity/field/edge sets with
the independently resolved expectation. Separately verify explicit document,
projection and parent placements. Every retained or moved modeled descendant
must occur exactly once in its intended context. A valid XML document alone is
insufficient. Unmodeled content is preserved through qualified lexical edits;
no inferred application semantics participate in comparison.

## Physical placement and lexical assembly

Each create/move selects one explicit document ID, canonical projection ID and
unique parent element. Existing parents are source-bound element references
under the full declared direct-child path. A new parent may be another explicit
creation only when its element is exactly the required parent path. Topologically
order such dependencies. A newly created entity does not supply arbitrary
intermediate wrappers; missing unmodeled path scaffolding refuses placement.
Insertions occur at the parent's end in explicit command order. Multiple
insertions at one location use the qualified grouped insertion mechanism.

Create only the declared element name and attributes from explicit field and
reference decisions. Assign `ns0`, `ns1`, ... to required nonempty, non-XML
namespace URIs in unsigned UTF-8 lexical order. The XML namespace uses `xml`;
unnamespaced elements explicitly reset the default namespace. Declare every
required binding on the created element. Emit mapped attributes in expanded-name
order (namespace URI, then local name, unsigned UTF-8), with double quotes and
escaping for ampersand, less-than and quote; TAB, LF and CR use character
references. This deterministic syntax applies only to newly generated markup.
Existing characters outside qualified edit spans remain exact.

Existing containment moves preserve the complete lexical subtree, including
unmapped content and descendants. Namespace safety includes prefixes appearing
only in QName-like unmapped values/text. The qualified initial move strategy
requires equal complete effective namespace contexts at the source and target
parents, including default and otherwise unused prefixes. Destination-only
bindings also count. Different contexts refuse with a corrective diagnostic;
expanded-name equality alone does not establish semantic preservation. Effective
inherited xml:lang and xml:space values (including absence) must likewise agree.
For xml:base compare the complete ordered ancestor declaration chain and trusted
document-base context, not merely the nearest attribute text. Different chains
refuse even if a URI resolver might normalize them to the same result. With no
external document base supplied, a rootmost absolute xml:base can establish a
known base for an identical subsequent chain; a relative chain depending on an
unknown external base refuses. If neither context contains xml:base and both
sources have no supplied document base, they share the adapter's explicit absent
base context. XML projection never invents a base from a row key, file path or
document ID. Inspect relative xml:base declarations inside the moved fragment
against this same preserved context. No normalization or inferred application
meaning excuses a difference.

For every moved root, compare the original source-parent context with the final
assembled target-parent context after all scalar edits, ancestor edits, creations
and moves. Original cached destination context is insufficient. Do not compare
against a newly edited source-parent context: the preserved subtree came from the
original source. Resolve each moved root's final destination through source-bound
placement provenance and require one exact final occurrence. Apply this check to
existing destinations and created parents beneath edited existing ancestors.

The complete namespace map (including unused/default bindings), nearest inherited
xml:lang/xml:space values and full ordered xml:base chain/document-base context
must all satisfy the preceding rules. Equal final source/destination edits cannot
excuse a difference from the original context. An ancestor edit hidden by an
unchanged nearer declaration may preserve the effective context; test that positive
case with independent exact output. A mismatch refuses the entire target, never
returns a partial candidate or silently restores/normalizes an ancestor value.

Make an extracted move fragment independently parseable by adding its inherited
namespace bindings to its root where not already locally declared, including an
explicit empty default namespace when appropriate. This is a bounded, separately
qualified fragment-root insertion, not arbitrary namespace editing. Apply any
explicit edits inside a moved subtree to that qualified temporary fragment first.
Then assemble disjoint outer removal and insertion patches. Nested move sources,
source/destination overlap and unresolved assembly order refuse; a destination
inside its own moved/removed subtree cannot be selected. Moving an ancestor with
retained descendants is supported only when every descendant has a compatible
target projection and remains exactly once in the final result.

For removal of a parent, first extract and qualify every explicitly relocated
descendant (including its explicit inner edits), with destinations outside that
removed subtree. Then collapse source removals into the maximal parent span;
the parent's removal subsumes those relocated descendants' original source spans.
Every other modeled descendant must be explicitly removed. Nested relocation
sources still require a non-overlapping qualified order, never duplicate output.
When all descendants are explicitly removed the same maximal-span rule applies
without extraction. A scalar edit within an unconditionally removed subtree is
a conflicting decision. Whole-DOM serialization, global replacement and fuzzy
selection remain prohibited. Original documents, fragments and final documents
use the same qualified XML parser and source-bound patch rules.

## Bounds and safe failure

Apply the observation limits of 128 documents, 16 MiB aggregate strict UTF-8,
20,000 entities and 50,000 edges, plus each XML document's existing limits, before
unbounded copying or assembly. Bound intent collections by those corresponding
graph limits, strings by the source/field budgets and placement depth by 128.
An affected-reference report may contain the union of before/after identities:
at most 40,000 entity references and 100,000 edge references. These report bounds
do not expand either graph's limits or the 20,000-entity intent collection bound.
Enforce limits while constructing fragments and final XML; do not build an
unbounded string and only then reject it. Unknown slots, stale sources,
unsupported changes, budget exhaustion, ambiguous placement or semantic mismatch
return stable safe diagnostics without values, XML, row keys or physical
locators. All transient value-bearing records have redacted string rendering.

## Acceptance and investigation

The required independent mock case starts with two glyphs referencing one
palette across documents. Create a second palette with fresh identity and shade,
explicitly move one glyph reference to it, and retain the other glyph, original
palette and every unrelated character. Run the same logical case against both
native database bindings with independent exact expected XML and graph values.
Database/client execution is a later gate, not implied by materialization.

Also cover no-op, exact scalar edits, optional absence/empty distinctions,
identity changes with explicit and missing rebinding, old-identity capture by a
fresh slot with unselected incoming references, nested creation, multiple
same-parent inserts, explicit removal and parent removal with child relocation,
compatible containment moves with edited
and untouched descendants, namespace aliases/default resets/unused prefixes,
inherited XML attributes (including equal relative base text under different
absolute ancestors), CRLF/comments/CDATA/PI/astral characters, stale sources,
wrong or ambiguous parent/projection, overlapping moves, resource boundaries and
all final graph constraints. Test independent non-interference and expected
outputs; round trips alone are insufficient. Observe meaningful RED then GREEN,
run G01/G04/G06/G07 and record actual commands, mutants and untested combinations.
