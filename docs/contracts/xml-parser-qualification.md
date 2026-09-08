# XML 1.0 Fifth Edition parser qualification

This corrects the parser mismatch found while integrating
[graph projection](graph-projection.md). The approved
[native v2](native-definition-v2.md) and [lossless XML](lossless-xml.md) contracts
permit XML 1.0 Fifth Edition names. JDK 21's default SAX parser rejects some valid
names; tested stock alternatives also differ. Do not silently narrow publication
or reinterpret source as XML 1.1. The original source remains the lossless writer's
sole character authority.

## Reproducible qualified dependency

Build a complete patched Woodstox 7.2.2 implementation in a dedicated
`backend/qualified-xml-parser` module. Fetch only the pinned public Maven source
artifact `com.fasterxml.woodstox:woodstox-core:7.2.2:jar:sources` into the build's
target directory. Verify SHA-256
`24669b0269917ca63270e81222c038b3ce737eba12bdb4a159e48e8b1d5a4822`
**before** extraction or patching. Refuse mismatches, unexpected archive paths,
duplicate entries, missing/changed patch anchors and excessive archive sizes.
Do not fetch tests, application models or a mutable branch. Generated upstream
sources and binaries are build outputs, never committed artifacts.

The patch replaces only `XmlChars.is10NameStartChar` and `is10NameChar` with the
normative Fifth Edition ranges. Preserve all XML 1.0 character, entity and line
normalization behavior. Char-level scanner handling admits paired high surrogates
only through D800–DB7F for names; the full-source paired-surrogate preflight is
mandatory. Both upstream and patched scanners alone accept some malformed UTF-16;
the qualified mechanism is the hardened parser **plus** that preflight.
No classpath shadowing of a class inside the stock binary is permitted.

Use pinned build plugins and a bounded Java 21 build helper. The proposed pins
are dependency plugin 3.6.1, antrun 3.1.0, compiler 3.14.1 and jar 3.4.2; actual
build evidence must verify their behavior. Runtime uses stax2-api 4.3.0; its
observed JAR SHA-256 is
`7c805f36129ea9fa42b696093b7ae1eb20bb6ccec65c8280d6f33db5609ca5e1`.
MSV/XSD, datatype and OSGi annotation/API dependencies needed to compile upstream
sources remain provided/build-only. Prove the runtime works without them, and
include no schema-provider service discovery entries. The server selects the
qualified implementation explicitly; unrelated JAXP/StAX discovery must not
change globally. Exclude stock Woodstox from the runtime dependency graph.

Retain applicable upstream Apache 2.0 license/notices and include a patch notice,
upstream source checksum and distinct patched implementation identity. Dependency
and release evidence must identify this as a modified build, not the official
unmodified binary. Changes to upstream version or patch require this qualification
again; no automatic source replacement is supported.

## Hardened adapter

Use the explicit qualified `WstxInputFactory` with set-and-readback verification
for namespace awareness, DTD disabled, external entities disabled, entity
replacement enabled, validation disabled, lazy parsing disabled and external DTD
access empty. Install an XMLResolver which always refuses and never opens a URI.
Bound element depth to 128, attributes per element to 256, elements to 20,000 and
characters to 1,048,576 in addition to lexical preflight budgets. Unavailable
required settings fail adapter initialization; no fallback parser is selected.

Reject DTD events and XML 1.1. An absent XML declaration has XML 1.0 semantics as
defined by the specification, not inferred application defaults. StAX performs
no XInclude processing; continue refusing XInclude, signature and encryption
namespaces through expanded-name checks. Uploaded schema-location attributes
remain data and do not activate a schema loader.

Join independently parsed names/decoded attributes with the existing exact
lexical spans and ancestry. StAX exposes namespace declarations separately from
ordinary attributes; map both explicitly to the established XmlDocument contract,
including default xmlns and the reserved xml namespace. Require exact agreement
on qualified names, attributes and element counts. No DOM serialization, source
normalization, namespace rewriting or global replacement enters the writer.

## Evidence and acceptance

Observe an integrated RED for a compiler-ready Fifth Edition declaration whose
matching source fails the old adapter, then GREEN with the qualified parser.
Use an independent specification-based oracle for all Unicode scalar values in
name-start and name-continuation positions, including invalid gaps and upper
bounds. Exercise elements, attributes, prefixes and processing-instruction targets;
test malformed surrogate pairs independently from the char-level classifier.

Retain exact no-op/output fixtures and challenge entity spelling, quotes,
comments/CDATA/PI, CR/LF, attribute normalization, U+0085/U+2028, namespaces,
empty/self-closing elements, every resource boundary, malformed XML, external
DTD/entities, schema hints and unsupported namespaces. Verify zero external
resolver calls. Repeat XML guard mutants and graph projection tests with the
final runtime classpath. Independent review examines the patch, archive/build
controls, parser properties and full integrated behavior. Scratch probe counts
are supporting evidence, not a substitute for these repository gates.

Primary sources: [XML 1.0 Fifth Edition](https://www.w3.org/TR/xml/),
[Woodstox 7.2.2 name classifier](https://github.com/FasterXML/woodstox/blob/woodstox-core-7.2.2/src/main/java/com/ctc/wstx/util/XmlChars.java).
