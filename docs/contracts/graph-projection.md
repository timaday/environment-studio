# Definition-driven XML graph projection — D03b

This internal mechanism joins native v2 declarations with a complete set of XML
documents. It supplies graph evidence for observation, profiles and planning;
it does not publish a definition, read a database or authorize export. Apply
[native v2](native-definition-v2.md) and [lossless XML](lossless-xml.md) exactly.

## Inputs and completeness

Use a server-compiled ready-to-publish result and an explicitly selected binding.
Accept document-ID/exact-source pairs from the declared complete inventory.
Reject duplicate, missing or additional document IDs before claiming completeness.
Database row-key, transaction and destination evidence remain D04 responsibilities;
an in-memory projection cannot manufacture that evidence. No arbitrary XPath or
uploaded executable function runs.

Bound the whole operation to 128 documents, 16 MiB total strict UTF-8 source,
20,000 projected entities and 50,000 relation edges. Check sizes before copying
or allocating collections; reject malformed Unicode and count overflow. Apply
the existing per-document XML limits. A limit, parse, unsupported-mechanism or
ambiguous-projection failure returns safe diagnostics and no complete graph.
No truncated document or partial inventory becomes a successful projection.

## Projection and graph checks

Match each expanded-name path from the root through direct children. Resolve
field locators with the shared [direct/child resolver](child-property-v1.md),
preserving decoded text exactly. References retain direct attributes. Located
child values retain their actual attribute span separately from the entity origin.
Validate document-wide physical ownership and discriminator protection before
accepting the graph; missing or multiple child matches follow the explicit
requiredness/cardinality rules. Final target projection repeats the same checks.
Unmapped elements and attributes remain uninterpreted and untouched. A physical
element may belong to only one projection, including projections of the same
logical type. All selected instances across all documents participate in checks.

Use `(type ID, exact identity value)` as observed entity identity. Keep a separate
source location containing document ID, projection ID, source digest and element
index. Source locations are transient revision-bound references, not profile
slots or permanent entity identity. Preserve absent versus present-empty fields;
required absence, malformed scalar codecs and empty or duplicate identity refuse
a complete valid graph. Do not coerce values or choose the first duplicate.

Resolve references to the declared target type across the complete inventory.
An absent optional reference creates no edge; a present empty or unresolved
reference is invalid. Containment uses the nearest selected compatible source
ancestor in the same document. Require one parent for each selected containment
target and no multiple containment parents or cycles. Check each relation's
minimum/maximum outgoing count for every source entity, including zero edges.
Apply every declared entity-count rule over the complete graph. Required checks
are explicit; empty rules never disable identity, field or relation validation.

Return immutable ordered entities, fields and edges, plus exact source documents
and digests through a separate transient projection object. Stable ordering uses
document ID, projection ID and source element order for origins, and explicit
type/identity/relation keys for graph content. Errors have stable codes and safe
document/projection/field declaration IDs; never interpolate raw XML, actual
identity values, field values or parser exception text into diagnostics/logs.
Value-bearing objects have safe `toString` implementations. No disk persistence,
global cache or background queue is introduced.

Keep graph records and validation rules in the framework-free core. The server
XML adapter maps lexical references into that core model. A successful result
proves the stated projection and graph checks for its exact sources; it does not
replace database completeness, target non-interference or SQL qualification.

## Acceptance and investigation

Invent independent mock documents for the existing native v2 fixture and register
their provenance. Exercise two declared types and cross-document references,
including two source entities referring to one target. Verify exact field values,
origins and edges with independent expectations. Add namespace-prefix variation,
repeated paths, optional absence/present-empty, whitespace and astral Unicode.
Challenge missing/extra documents, duplicate identities, overlapping projections,
dangling references, required absence, scalar violations, zero/out-of-range
cardinality, multiple parents, cycles and count-rule failures. Check source
non-interference and resource limits. Observe meaningful RED/GREEN and kill
targeted mutants removing a required-field, identity or reference guard.
