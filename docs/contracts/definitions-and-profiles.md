# Definition and profile contract v1

## Upload and publication

This contract describes product runtime behavior. Actual application definitions,
schemas and profiles are external inputs kept in a separately governed workspace,
never in this code repository or image. Only independently invented mock bundles
are committed here. Runtime publication means an immutable workspace revision;
it does not create or manage a separate configuration versioning repository.

Accept bounded UTF-8 JSON or YAML through a closed safe loader. Reject duplicate
keys, unknown fields/enums, executable tags, cyclic/excessive aliases, remote
references and oversized/deep data. Validate against the local pinned
`schemas/definition.schema.json`, then compile semantic constraints in Java.
No remote code, JavaScript, Java class loading or arbitrary SQL is an extension
mechanism. User-supplied identifiers are data and validated/quoted by adapters.

Draft → parsed → incomplete or ready-to-publish → immutable revision. A publish
requires types, fields, identities, relations, value classification, inventory,
selectors, supported operations and required rules to be complete for the
advertised capability. Display Model / Source / Diagnostics from the same
compiled revision. Editing a published revision forks a draft. Existing plans
and profiles remain pinned until an explicit compatible upgrade is reviewed.

XSD import is a later qualified adapter to the same draft. Preserve supported
structure/types/cardinality/key/keyref/assertion constraints and provenance.
Report unsupported features; do not discard constraints or turn schema defaults
into target values. Generic Spring bean schemas cannot reveal the missing
application identity, ownership, value classification or database record scope.
Do not execute bean definitions. No universal XSD support is advertised.

## Generic vocabulary

Entity types have stable IDs and display labels. Fields explicitly declare a
value type, requiredness, environment/structural classification and sensitivity.
Relations declare source/target types, cardinality, containment/reference and
reuse dependency behavior. Identities have declared scope/case/normalization
semantics. Model-only fields need no writer. XML-backed editable fields need
qualified exact selectors, record scope, cardinalities and write capability.

The draft meta-schema is deliberately smaller than a universal metamodel.
Boolean presence in a draft example is an explicit decision; absence is an
error or an unresolved draft decision, never an implicit optional/default rule.
The mock application declares `node` and `workload`; these are not built-ins.
Generic adapters implement qualified operations. Actual mappings, types and
application rules come from external declarations; do not embed them in adapter
code or committed fixtures. Qualify generic behavior using independently invented
mock databases, with actual application correctness evaluated separately and
reported through the generic feedback workflow.

## Value-free profiles and partial reuse

A profile carries schema version, profile identity/revision, a definition digest,
logical entities, relation instances and required input declarations. It carries
no raw XML, row keys, server IDs, actual application names, endpoints, paths,
credentials, literal field values or encrypted environment payloads. Logical
labels are operator-created neutral labels, never automatically copied from
observed values. Import is closed and allowlisted, including metadata.

Selection of all or some entities produces a proposed dependency closure.
Required relation targets join the proposal; unrelated siblings do not. The
operator can accept, select a compatible existing dependency or cancel. Name/type
collisions do not authorize merge. Every conflict has a declared resolution;
reuse never implies removal of target objects. Include a summary of additions,
retained objects, unresolved mappings and new value inputs before applying it.

Compatible profile reuse across engines requires the same logical definition
contract and all target operation capabilities. DB locators and surrogate keys
are not portable. This is configuration modelling, not database conversion.
