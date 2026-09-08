# Lossless XML adapter — D03a

The adapter supplies qualified lexical mechanisms, not application meaning or
export authority. D01a's declared vocabulary is sufficient to begin generic XML
conformance work; immutable definition publication still needs later binding and
operation qualification. No public HTTP endpoint accepts arbitrary XML patches.

## Source and projection

Accept exact XML characters with a source digest; retain the original sequence.
Reject malformed Unicode/XML, DTDs/entities requiring declarations, XInclude,
XML signatures/encryption and unsupported XML versions. Support XML 1.0, named
predefined/numeric references, namespaces, comments, processing instructions,
CDATA, either attribute quote style, CR/LF variants and self-closing elements.
Qualified parser hardening disables all external entity/schema/DTD access and
XInclude processing; unavailable required flags fail initialization. Parser
exceptions become safe tool-owned diagnostics without input content.

Initial per-document limits: 1,048,576 UTF-16 code units, 128 element depth,
20,000 elements, 256 attributes per element and 100,000 total lexical tokens.
Enforce limits during scanning/parsing, before unbounded tree construction.
The scope-level orchestrator will separately bound document count and total
bytes/time. Limits refuse the document; no truncated source is returned as valid.

Return immutable source-bound element/attribute references with expanded names
(namespace URI/local name), decoded values, ancestry and exact character spans.
Element references are tool-owned positions within this source revision, never
portable identity or database keys. Namespace resolution and XML validity must
agree with an independently configured conforming parser. Display formatting is
separate and never replaces the original character sequence.

## Qualified operations

The first adapter supports these internal typed operations against existing
source-bound element references:

- Replace an existing non-namespace attribute, requiring its expected decoded
  value. Escape replacement characters for the original quote style; encode
  CR/LF/tab as character references so parsed target values remain exact.
- Remove a complete non-root element, preserving characters outside its span.
- Insert a single self-contained valid XML element before an explicit direct
  child or at the end of an explicit parent. Its namespace declarations are
  explicit; inherited namespaces must not change the supplied element's meaning.
  Preserve existing siblings and surrounding characters. Self-closing parents
  require a qualified expansion of their closing delimiter, not DOM serialization.

Text-field replacement, namespace declaration editing and arbitrary XPath
expressions are not advertised by this initial mechanism. They require separate
conformance before a definition can bind them. This does not remove structural
operations or either database engine from the pilot; unsupported bindings remain
explicitly unavailable and later D03 work must satisfy the selected mock family.

Apply a batch only to its exact source revision. Reject stale references, missing
attributes/anchors, wrong expected values, overlapping edits and conflicting
ancestor/descendant edits. Multiple insertions at one location follow the explicit
command order; they never depend on map iteration order. Validate the entire
resulting document and intended operation outcomes. A failure returns no partial
target. No-op returns the original characters exactly.

Use source spans and a single bounded assembly of untouched slices plus edits.
No global string replacement, regular-expression XML writer or whole-DOM
serialization is an implementation of this contract. Domain planning owns which
operations are intended; this adapter cannot prove complete environment validity.

## Acceptance and investigation

Invent independent fixtures and exact expected outputs. Include namespace-prefix
variations, repeated equal attribute values, entity spellings, both quotes,
comments/CDATA/PI containing angle brackets, CRLF, astral Unicode, empty elements,
self-closing parent expansion and cross-document one-to-two building blocks.
Assert non-interference outside the explicitly edited spans and no-op equality.

Adverse cases include malformed input, XXE/DTD, XInclude, signed/encrypted XML,
limits, stale source, wrong expected value, invalid insertion namespace, wrong
parent/anchor, overlapping removals/replacements and a final operation failure.
Use independent expected text plus conforming-parser semantic checks, not just
writer round trips. Run G04 and targeted G06 guard mutations. Record untested
combinations without granting writer/publication/export authority.
