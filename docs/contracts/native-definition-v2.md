# Native definition version 2 — publication semantics

Version 1 remains an uploadable draft format with incomplete results. Version 2
adds explicit logical identities, complete inventory and XML projection/edit
semantics. There is no inferred conversion from version 1. A maintainer supplies
the missing declarations. Schemas describe generic tools; actual application
definitions remain external runtime inputs.

The [child-property extension](child-property-v1.md) adds a closed field locator
and per-binding dependency rules. Its selection, collision and creation rules
apply alongside the existing direct-attribute behavior below.

## Logical and runtime contracts

A version 2 document has closed properties `schemaVersion: "2"`, `id`, positive
integer `revision`, `logical` and `bindings`. Parser budgets and safe diagnostics
are those of [definition compilation](definition-compilation.md). Native revision
is declarative input; an authenticated workspace revision owns mutation authority.
Uploading a document never asserts it is already published.

`logical` contains `entityTypes`, `relations`, `rules` and
`operationCapabilities`. Entity types retain version 1 IDs, labels and fields;
each field additionally declares `readable` and `editable` booleans. Each entity
type declares `identity: {field, scope: "type", normalization: "exact"}`. The
identity must select one required, readable, non-secret text field. Values retain
case, whitespace and leading zeros; neither equality nor identity uses display
labels. Actual identity values are distinct from tool-owned logical slot IDs.
Identity values must be nonempty. Attribute codecs are explicit: text preserves
every decoded character, integer accepts only `0` or `-?[1-9][0-9]*` within the
existing 1024-digit numeric budget, boolean accepts only `true` or `false`, and
URI accepts an absolute ASCII URI without whitespace using RFC 3986 syntax.
Writers preserve supplied lexical text after validation; they do not normalize
case, whitespace, URIs or integers. Required means present; empty text is valid
unless it is identity. Absent attributes are distinct from present empty text.

Relations retain explicit version 1 source/target types, kind, bounds and reuse
dependency behavior. Recursive type declarations are legal; instance containment
cycles and multiple containment parents are validation failures. Cardinality and
reference resolution apply to the complete instance graph, including unchanged
dependencies. No built-in type, entity hierarchy or application rule is inferred.

Rules are closed declarations with unique `id`, `kind: "entity-count"`, `type`,
nonnegative integer `minimum` and positive integer `maximum`, minimum <= maximum.
This first application-rule mechanism counts instances of the named declared
type across the complete inventory. Identity uniqueness, required fields,
relation cardinality, dangling references and containment validity are mandatory
engine checks regardless of these extra rules. Unsupported rule mechanisms
cannot be silently discarded or treated as PASS. Rules may be empty only when
the maintainer explicitly declares no additional count constraints.

Operation capabilities use the closed vocabulary `retain-entity`, `create-entity`,
`remove-entity`, `bind-field`, `move-relation`. All selected capabilities must be
supported by the declared projections and qualified engine adapter. Retaining
unselected siblings is mandatory. Removing an entity with remaining incoming
references or children requires explicit supported edits resolving them; no
automatic cascade. An unsupported operation keeps publication incomplete.

## Binding declarations

`bindings` is a nonempty list of independently identified runtime bindings.
Each contains `id`, `engine` (`postgresql` or `oracle`), `storage` (`text` or
`clob` respectively), `schema`, `table`, `keyColumn`, `xmlColumn`, `keyType`
(`text` or `int64`) and nonempty `documents`. Identifiers are exact quoted SQL
identifiers restricted to `[A-Za-z_][A-Za-z0-9_]{0,127}`; they are never SQL
expressions. Schema/table and key/XML columns are explicit, with distinct column
names. Physical destination, driver options and credentials are separate
operation-scoped inputs, never native definition fields.

The initial scope is the **whole named table**, with no uploaded query, predicate
or row-limit shortcut. Every document declares unique `id`, typed exact `key`,
and `entities`. The document key set is the expected complete table membership;
missing or extra rows block observation/export. An int64 key is a canonical
decimal string within signed 64-bit range. A text key is a nonempty exact string
of at most 256 code points. NULL/empty XML values remain distinct and cannot
masquerade as valid documents. This mechanism changes XML inside existing rows;
it does not insert/delete database rows or invent a row-key/trigger strategy.

Each entity projection declares `id`, `type`, `path`, `fields` and `references`.
The path is a nonempty ordered list of expanded names `{namespaceUri, localName}`
from the document root through direct children. It selects every matching entity
occurrence; it is not arbitrary XPath. Names use XML 1.0 NCName syntax and bounded
namespace URI strings. No descendant wildcard, function, predicate or remote
resolution is supported. Distinct projections must not claim the same element
as different entities. Each type has one or more canonical projections per
binding; projection IDs are unique within the binding. The same type may occur
in several documents, but each observed identity must be unique across those
documents. This version does not merge several occurrences into one entity.
Instances of a type may repeat at any selected path. Creation selects an explicit
projection ID and parent context; multiple choices never imply first-match reuse.

Each field mapping is either `{field, attribute}` or the mutually exclusive
`{field, childProperty}` form in [child-property-v1](child-property-v1.md).
The direct form selects exactly the expanded-name attribute on the entity element;
the child form selects a value attribute on one discriminated direct child.
Every declared readable field must have one mapping, with no duplicate/unknown fields or attribute
collisions. Required absence, unclassified/unknown sensitivity, duplicate actual
identity or ambiguous selection prevents a complete observation/target.
Non-readable required inputs still require an explicit target value; they cannot
be retained by reading an unavailable observation. Initial publication requires
all fields readable because this projection version has no write-only strategy.

A reference mapping is `{relation, attribute}` on the relation's source entity.
Its attribute contains exactly one target identity value, resolved against the
declared target type across all documents. The relation must have maximum 1;
absent optional references and required missing references are distinct. Source
and target must each have a canonical projection. Attribute collisions with
field mappings or other references are rejected. Multi-target token/list codecs
remain unsupported until a separate explicit qualified codec exists.
Every logical reference relation must have exactly one mapping on every canonical
projection of its source type. Omitting a required mechanism is incomplete,
including when no current instance would exercise it.

Containment is represented by canonical projection ancestry: the target path
must extend the source path in the same document, and each target occurrence's
nearest selected source ancestor is its parent. Both endpoint types must have
canonical projections; containment projections never use reference attributes.
Changes preserve complete subtrees and require explicit mapping/removal choices.
Each target projection of a containment relation must have exactly one compatible
source projection ancestor in that document. Logical recursive containment is
legal, but this finite direct-path binding cannot represent recursion; report an
incomplete binding rather than rejecting the logical type graph as cyclic.

Creation inserts an entity at the end of its explicitly selected parent element.
That parent must resolve uniquely in the affected target context. Its existing
children remain untouched. The element name is the final expanded path name;
new attributes come only from explicit field/reference bindings. New markup uses
deterministically assigned namespace prefixes sorted by namespace URI, declares
all required namespaces, and escapes XML characters. No donor literal, template
default or executable extension supplies a value. Existing XML is still written
only through qualified source-span edits. Nested creation requires explicit
parent mapping and dependency order, never a first-match fallback.
Capabilities are selected for the logical contract and checked across every
projection. Root-element projections cannot support create/remove and therefore
leave such declared capabilities incomplete. `editable=false` prevents changing
an existing value; it does not authorize importing a donor value and does not
prevent supplying a fresh value during creation. `bind-field` and reference
`move-relation` initially replace existing attributes only. Adding an absent
optional attribute or removing an existing attribute is a typed unsupported edit
until separately qualified. Retaining optional absence is supported. Containment
moves require an explicitly selected compatible parent and qualified remove plus
insertion; source/target overlap or unresolved order refuses the plan.

All object properties are required unless this document explicitly says optional;
there are no defaults. IDs use the version 1 ID syntax. Entity types, logical
field declarations, bindings, documents and entity projections are nonempty.
Relations, rules, projection field mappings, reference mappings and operation
capabilities may be explicitly empty; missing required mappings are semantic
incompleteness. IDs are
unique within their collection; document and projection IDs are unique throughout
one binding, fields within one type. Limits are inherited from the bounded native
parser. Expanded names have required `namespaceUri` (empty or <=2048 code points)
and `localName` (nonempty XML 1.0 NCName, <=128 code points). Reject the reserved
xmlns namespace everywhere and unnamespaced attribute name `xmlns`. The XML
namespace uses its reserved `xml` prefix. New unnamespaced elements explicitly
reset a surrounding default namespace with `xmlns=""`; never inherit unintended
meaning. Namespace declarations are not editable field/reference mappings.

## Publication and digest authority

The compiler returns rejected, incomplete or ready-to-publish. Ready means all
declared semantics are internally consistent and supported by the registered
mechanism versions; it does not mean a database/client or actual application has
been qualified. Workspace publication is an explicit immutable revision action;
plans cannot supply their own compiler or capability result.
The trusted base dependencies pin `native-compiler-v2=2`, `xml-path-v1=1`,
`xml-span-v1=1` and `generic-graph-v1=1`. A binding declaring child fields also
requires `xml-child-property-v1=1`; an unchanged direct binding keeps only the
base vector. The checked definition records their union. Registry values are integers (`I2;` for the compiler and `I1;` for the other
mechanisms in digest framing), not strings. The first two define this format and its direct
child/attribute projections; xml-span-v1 is the qualified D03a mechanism;
generic-graph-v1 provides the declared graph/count checks. Uploaded content cannot
replace registry versions or claim mechanism availability. Unknown vocabulary
rejects; recognized declarations that exceed a mechanism's capabilities remain
incomplete. Oracle/PostgreSQL binding syntax can be compiler-ready before a
specific database/client is qualified; inspection/export still require that
separate server-owned qualification evidence. XSD import remains unavailable.
Static mechanism limits also constrain readiness: paths deeper than 128 elements
or projections requiring more than 256 attributes (required fields plus required
reference attributes) are incomplete under `xml-span-v1`. Optional observed
attributes and namespace declarations still count toward the adapter's actual
per-document limit. Runtime budget checks remain mandatory for each observation.

Compiler mechanism revision 2 aligns static readiness with the registered parser's
element-namespace capabilities. Any element at any position in a projection path
using one of the following namespaces is recognized but incomplete, with publication
diagnostic XML_NAMESPACE_UNSUPPORTED pointing at that expanded name's namespaceUri:

- `http://www.w3.org/2001/XInclude`
- `http://www.w3.org/2000/09/xmldsig#`
- `http://www.w3.org/2009/xmldsig11#`
- `http://www.w3.org/2001/04/xmlenc#`
- `http://www.w3.org/2009/xmlenc11#`

These are element-vocabulary refusals. Attribute names and namespace declarations
retain their existing independently qualified behavior; a declaration alone does
not invoke an unsupported mechanism. Compiler and parser use the same framework-free
capability definition; lexical validity remains a separate requirement.

Previously stored compiler mechanism revision 1 results remain readable and exact
retries retain the stored result. They cannot newly publish, create plans or authorize
inspection/export under the current registry. Re-save/recompile and explicitly
publish a new definition revision; there is no automatic history rewrite. The schema
and readable compiler codec names remain version 2. Current compilation changes the
binding digest because its mechanism vector changes; logical/profile compatibility
still follows the documented logical digest independently.

Use separate versioned SHA-256 digests with unambiguous length framing and stable
ordering. The logical digest covers types, field semantics, identity, relations,
rules and operation requirements, excluding labels, source layout, native
revision and physical bindings. A binding digest covers its complete physical
inventory, XML projections, declared operations and mechanism versions, plus the
logical digest. Do not hash timestamps or credentials. Definitions with the same
logical contract but different Oracle/PostgreSQL locators can therefore share a
logical digest while their binding digests differ.

Version 2 profiles explicitly carry `logicalDefinitionDigest`; version 1's
`definitionDigest` is not silently reinterpreted. New plans pin the immutable
workspace definition revision, logical digest, selected binding digest and every
qualified mechanism version. Incompatible upgrades require explicit review.

Digest bytes are precisely defined. Frame strings as ASCII `S`, decimal UTF-8
byte length, `:`, then exact UTF-8 bytes; integers as `I`, canonical decimal,
`;`; booleans as `T` or `F`. Frame arrays as `A`, decimal item count, `:`, then
item frames. Frame objects as `O`, decimal property count, `:`, then key-string
and value frames in unsigned UTF-8 key-byte order. No null values occur.
Logical normalization removes only entity display labels and sorts entity types,
their fields, relations and rules by ID; sort capability strings lexically.
Hash UTF-8 `ES-LOGICAL-2` followed by a zero byte and the normalized logical frame.
Native definition ID/revision and binding ID are metadata, excluded from digests.
For a binding, sort documents/projections by ID, field mappings by field and
reference mappings by relation; preserve path order. Remove only binding `id`.
Hash UTF-8 `ES-BINDING-2`, zero byte, then the framed object with properties
`logicalDigest`, `mechanisms` (that binding's exact required vector above) and `binding` (normalized
binding). Digest output is lowercase hexadecimal. Semantic IDs inside the model,
document IDs and projection IDs remain included because they name plan subjects.

## Acceptance evidence

Implement shape and semantic tests from independently invented declarations.
Unknown/duplicate properties, bad identities, undeclared fields/endpoints,
overlapping projections, attribute collisions, bad containment/reference
mechanisms, duplicate typed keys, unsafe SQL identifiers and unsupported
capabilities must refuse safely. Equivalent source map ordering must produce
identical digests; logical field/identity/rule changes must alter the logical
digest. Changing only physical engine/locators must preserve the logical digest
and alter the binding digest. Preserve mathematical integer meaning and exact
Unicode source under the existing parser limits.

The required invented structural acceptance case has two declared types and a
cross-document reference: create a second target of that reference with fresh
identity/environment values, then move one source entity's reference to it while
retaining the other source and every unrelated document character. Both database
bindings must represent the same logical contract. Later planner/writer/database
gates must execute that case; compiler readiness alone does not satisfy it.
