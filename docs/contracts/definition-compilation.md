# Definition compilation — D01a

This is the first implementation boundary within D01, not completed definition
publication. It preserves the existing version 1 draft schema. Later D01 work
must represent and qualify the missing publication semantics before any ready or
published capability exists. Uploaded `status` is data, never authority.

## Input and safe refusal

The definition adapter accepts bytes and an explicit JSON or YAML format. Decode
UTF-8 strictly. Limit input to 1,048,576 bytes, nesting to 32 containers, scalar
strings to 16,384 Unicode code points and the parsed tree to 20,000 nodes. Count
object keys in the node budget. Refuse limits before constructing an unbounded
object tree; parser resource hardening must be configured, not assumed.

Accept exactly one object/document. JSON rejects duplicate keys and trailing
content. YAML uses JSON-compatible scalar semantics; reject explicit tags,
anchors/aliases, merge keys, non-string keys and additional documents. No custom
constructors or implicit date/class conversion. Reject non-finite numbers. Schema
integer semantics permit mathematically integral decimals such as `1.0`; preserve
unbounded integer values without narrowing to Java int or long.

Validate the parsed value against the packaged, pinned
`schemas/definition.schema.json`. Remote schema resolution is unavailable; input
cannot select a schema or supply a reference. Unknown properties and unsupported
values are rejected, never ignored. Invalid UTF-8, syntax, resource or schema
input returns diagnostics without a partial model or raw parser exception.

## Semantic draft checks

After shape validation, use immutable framework-free Java records. Keep declared
array order and defensive copies of every nested list/map. Type, relation and
document IDs are unique in their respective definition-wide collections; field
IDs are unique within their entity type. Require known relation endpoint types,
minimum cardinality no greater than maximum, and known mapping entity types and
fields. Do not conflate recursive type declarations with cycles among instances.

The current schema allows empty field, relation, mapping and operation lists.
Those are not parse errors. Unknown sensitivity, missing identity/inventory
semantics, rule implementation binding and selector/writer/operation qualification
remain explicit publication blockers. Strings naming a rule or capability do not
install implementations. No application vocabulary is built into the compiler.

## Results and diagnostics

Results are disjoint: `rejected` has diagnostics and no model; `incomplete` has
the immutable checked draft plus publication diagnostics. D01a cannot return
ready/published, save a revision, authorize inspection or authorize export.
Semantic errors reject the draft. Publication blockers do not hide a structurally
and semantically checked model. No null-as-success or catch-and-continue behavior.

Each diagnostic has a phase (`parse`, `shape`, `semantic`, `publication`), stable
tool-owned code, RFC 6901 pointer and constant corrective message. A root error
uses the empty pointer. Sort by phase in the preceding order, then pointer, then
code; remove exact duplicates. Do not echo uploaded scalar values, unknown keys,
parser messages, XML, SQL or credentials in diagnostics. For an unknown property,
point to its containing object, not to the untrusted property name.

The UI presents one result revision through Model / Source / Diagnostics views.
Its projection carries a decimal-string revision, declared type/field/relation
summaries, the original supplied source and the same diagnostics. Source is
session display data, not browser storage or telemetry. Loading, rejected,
incomplete and unavailable states are distinct. A synthetic preview is labelled;
it does not claim upload, save, publication or validation against a live API.

No HTTP route is added in D01a. Demo mutation denial remains in force until the
hosted identity boundary is implemented and tested. This sequencing leaves the
full upload/save/publish workflow in D01, rather than advertising it early.

## Acceptance examples and evidence

- Arbitrary invented vocabulary parses into a model without built-in concepts.
- JSON and equivalent YAML produce equal immutable drafts and diagnostics.
- Duplicate keys/IDs, unknown shape, dangling types/fields and inverted
  cardinality reject with safe, deterministic diagnostics.
- Malformed UTF-8, trailing documents, tags/aliases, excessive depth/nodes/bytes
  and unsafe scalar conversion reject before semantic compilation.
- Repeated frozen input yields equal results; callers cannot mutate collections.
- A shape-valid `published` input remains incomplete and has no authority.
- Keyboard-accessible model/source/diagnostic selection preserves visible
  publication blockers and never implies a save or export occurred.

All cases are independently invented mock data. Parser/schema tests and core
semantic tests use independent expected outcomes; no fixture comes from private
application material. Follow G00/G01/G02 and applicable G03/G07 investigation;
publication, XML/DB/client and release qualification remain separate evidence.
