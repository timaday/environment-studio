# Discriminated direct-child fields — QF-0001/0002

Status: **implemented and registered for the documented native mapping paths**.
See [QF-0001/0002 evidence](../evidence/qf12-child-property.md) for actual checks
and remaining client, hosted-export and combined-capacity qualification.
This additive native-v2 extension is authorized by the amended 9 September task.
The original rejection was a capability gap, not a defect in the advertised direct
attribute contract. Apply [native v2](native-definition-v2.md),
[projection](graph-projection.md), [structural targets](structural-target.md) and
[lossless XML](lossless-xml.md). No derived graph semantics are introduced here.

## Closed declarations and source selection

A field mapping has exactly one of these forms, with no defaults or extra keys:

- Existing: `{field, attribute: {namespaceUri, localName}}`.
- New: `{field, childProperty: {element, discriminatorAttribute,
  discriminatorValue, valueAttribute}}`.

All three names inside `childProperty` are expanded names with the existing
namespace/NCName limits and reserved-name rules. `discriminatorValue` is an exact
decoded XML-1.0 string, including empty text if explicitly declared, with at most
1,048,576 UTF-16 code units and strict Unicode; native source/parser budgets also
apply. There is no XPath, descendant selection, expression, script or inferred
application vocabulary. Reference mappings retain their existing direct-attribute
form and single-target semantics.

For each entity occurrence, examine only direct children whose expanded element
name matches `element`. A child matches only when its discriminator attribute has
the declared expanded name and its decoded value exactly equals the declaration.
Prefixes are spelling, not identity. Unprefixed attributes use the empty namespace,
including on elements in a default namespace; namespaced discriminator attributes
are supported. See [Namespaces in XML](https://www.w3.org/TR/xml-names/).

Zero matching children means absent field. Exactly one matching child with no
value attribute also means absent field. Required absence refuses completion;
optional absence remains distinct from present empty text. More than one matching
child always refuses, even if values agree or value attributes are absent. Read
the exact decoded value attribute of the single match; apply the existing field
codec, identity, sensitivity and readability rules without normalization.

Compilation validates declaration shape, names, static conflicts and mechanism
capability. It does not establish actual occurrence counts. Observation and final
target reprojection enforce cardinality and required content across every entity
and document, including unchanged dependencies.

## Aliases, selector integrity and provenance

Each mapped field/reference value owns one physical attribute. Reject mappings
that necessarily alias within a projection, or across projections in the same
document whose declared paths and selectors establish the same target. Two child
mappings with identical child name, discriminator name/value and value name are
an alias, even when their logical field IDs differ.

At runtime, enforce uniqueness across the complete document by source revision,
element index and expanded attribute name, covering parent/child entity
projections and different selectors that happen to match the same child. Reading
the same physical value into two logical fields is not supported. Multiple fields
may share a selected child only when their value attributes are distinct.

Discriminator attributes are structural selectors, not editable value bindings.
Reject a field/reference value locator that can address a discriminator attribute
used on the same possible child path. This includes an ordinary field on a
separately projected child entity and a child mapping whose value name equals
another selector's discriminator name. The restriction applies even to a currently
nonmatching child or non-editable field: it prevents later selector rebinding via
creation, another field or reference edit. Different discriminator values do not
excuse an attribute collision with selector authority. Unknown unmodeled attributes
remain untouched. Structural changes must still match the complete independently
expected target after reprojection; no selector-driven side effect is inferred.

Use one shared locator resolver for projection, binding/placeholder views and
patch planning. A located field keeps the entity occurrence origin separately
from its actual attribute element index/span and, for a child mapping, the
matched child/discriminator references. Every reference is pinned to the exact
source digest. Verify membership, expected decoded values and selector identity
against that source before writing; never reuse coordinates on another revision.
Explicit absent results carry no invented attribute span. Value-bearing locator
results and diagnostics use safe rendering.

Scalar binding replaces an existing located value attribute only. It cannot
create a missing child/value attribute, remove an attribute or change a selector.
Stale source, changed expected value, aliasing, overlapping edits and selector
conflicts refuse the entire target. A disabled UI action is not enforcement.

## Creation and structural materialization

Creation remains an explicit entity operation with an explicit canonical
projection and unique parent placement. Fresh field/reference values or optional
absence are required; no donor literal or application default supplies a value.
The declared discriminator literal is fixed selector syntax, never a donor value.

For a new entity, emit direct attributes using the existing deterministic syntax.
Group present child fields by `(element, discriminatorAttribute,
discriminatorValue)` and emit one child per group. Emit its discriminator and
explicit present value attributes in expanded-name order. An all-absent optional
group emits no child. A required absent value is already a semantic refusal.
Order child groups by unsigned UTF-8 expanded element name, discriminator name,
then discriminator value; namespace prefixes are assigned once for all emitted
names using the existing unsigned UTF-8 namespace ordering and reserved `xml`
handling. Unnamespaced generated elements explicitly reset the default namespace.
Escape namespace, discriminator and value text with the qualified XML writer.

When a property child's exact path is also a canonical entity projection in the
same document, declared `create-entity` is incomplete with
`CHILD_ENTITY_CREATION_UNSUPPORTED`. The property generator cannot invent that
entity's fresh slot or fill its independent required fields. An explicit separate
nested create would add a second sibling, not complete the generated child.
Read/scalar bindings without creation may still qualify when ownership is disjoint;
a coordinated generation strategy would require a separate qualified operation.

Conflicting generated attributes, selector interference or additional projected
entities caused by generated children refuse through static checks where
decidable and complete target reprojection otherwise. Compilation applies the
compiler-2 unsupported element-namespace vocabulary check to the child element
and checks depth (entity path length plus one) and required attributes/namespaces
against parser limits. Actual generated
markup must pass all element/token/source budgets before successful assembly.

Moves preserve complete lexical subtrees, including property children, comments
and unmodeled content. Scalar edits inside a moved subtree use the same located
attribute references and qualified fragment process. Existing namespace and
inherited xml:lang/xml:space/xml:base final-context checks remain mandatory.
Creation/removal/containment changes must produce exactly the independent expected
graph and intended placements after complete target reprojection. No binding is
ready when its declared creation or structural operations are unsupported.

Preserve exact XML characters outside qualified edit spans. Tests comparing bytes
encode both expected and actual character sequences as strict UTF-8 without BOM;
they do not assert physical Oracle CLOB storage-byte identity. Database/client
qualification remains separate from compiler, parser and string-test evidence.

## Mechanism and compatibility authority

Register `xml-child-property-v1=1` only after the complete affected path is
qualified. The unchanged base dependency vector is `native-compiler-v2=2`,
`xml-path-v1=1`, `xml-span-v1=1`, `generic-graph-v1=1`. Existing mechanisms keep
their current meaning. The new mechanism owns child selection, collision and
selector guards, creation and structural integration as a coherent extension.

Separate server availability from declaration dependencies. For each binding,
derive the exact required vector: the unchanged base four entries, plus the child
mechanism iff that binding declares any child mapping. The checked definition's
`mechanisms` is the union of these binding dependencies, not the full available
server registry. No uploaded version/vector can authorize itself. Unknown
vocabulary is rejected; a recognized unqualified dependency or unsupported
declared capability is incomplete; missing/ambiguous actual XML is an observation
or target refusal.

Keep existing logical normalization/domain unchanged: selecting a different
physical field location does not change logical/profile compatibility. Preserve
existing direct mapping normalization exactly. Normalize a child mapping as its
closed declared object. Use the unchanged `ES-BINDING-2` framing with that
binding's required vector in `mechanisms`. Selector/name/value changes affect the
child binding digest. Adding a child binding does not change an otherwise
unchanged direct binding digest. A publication includes the whole definition and
dependency union, so adding a binding legitimately changes publication identity.

Keep old direct snapshot encoding byte-identical, including no new nullable keys
or type tags. Registered historical codecs read/replay the stored result without
recompilation or rewriting published history. Compiler revision 1 remains readable
but ineligible under the existing revision-2 rule. Re-save/recompile and explicit
publication are needed for changed definitions. Existing direct revision-2 plans
gain no new field locations or capabilities from adding server availability.

Publication/projection require the exact server-derived union and selected binding
vector, not arbitrary subsets or available-registry equality. Profiles still pin
only logical compatibility; the destination binding must qualify all selected
operations. Planning, observation and export retain immutable publication,
selected binding and source pins. Export package versions include the selected
binding dependencies plus its existing qualified structural/validation mechanisms;
extend the closed manifest with only the optional child dependency and preserve
direct-only package encoding. Missing, extra or wrong required versions refuse.
The internal definition-pinned package admission entry compares the exact selected
binding ID/digest, logical digest and dependency vector with the server-compiled
definition. Schema-only package inspection has no definition and cannot infer a
missing child dependency from XML or an opaque digest; it remains mechanical
inspection, never export authority. The hosting export flow must supply its pinned
definition to this check in addition to all existing validation/publication gates.

## Implementation and acceptance record

The lead owns this contract, schemas, shared locator/domain shape and integration.
Base: `924c25ed256ffd316203ad62f31d1d0e60df0a31`; reviewed Q snapshot `dbb457a`
differs only by later evidence/handoff documents. QF-0001 is a confirmed capability
gap; QF-0002 is its dependent compatibility work. Both are implemented as one
additive mapping slice; database/client and complete hosted export qualification
remain separate.

Require meaningful failing behavior tests before implementation, independent
expected XML and digest goldens, then focused and integrated gates. Cover both
mapping forms, mixed/multi-document entities, required/optional absence, empty,
duplicate child matches, namespaces and non-direct descendants, Unicode,
escaping/CRLF/comments, stale spans, cross-projection aliases, discriminator
guards, explicit creation/moves, placeholders, profile reuse and export pins.
Historical stored reads/replay/publication, unaffected direct binding/publication/
package goldens and stale-plan refusal need independent controls. Targeted mutants
must exercise cardinality, stale source, collision, readiness and compatibility
guards. Review the fixed integrated candidate from Business/Engineering/QA and
XML/data/security perspectives. Record actual commands and remaining combinations;
do not infer database qualification or MVP completion from these tests.
