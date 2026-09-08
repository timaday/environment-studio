# Qualified XML parser build

This module builds the complete Woodstox 7.2.2 Java implementation from the pinned
public Maven source artifact, changing only two XML 1.0 name classifiers. It is a
modified implementation identified as `7.2.2-es-xml10-fifth-1`, not the official
unmodified Woodstox JAR. See the frozen `docs/contracts/xml-parser-qualification.md`
in the integrating repository revision.

`mvn -B -ntp -f backend/pom.xml verify` fetches the exact source classifier into
`target/upstream`, verifies SHA-256 before extraction, then launches the bounded
Java 21 helper. Only generated upstream Java and its license are extracted to
`target/generated-sources/woodstox`; the source tree and binaries are not committed.
The helper refuses unexpected paths, duplicate entries, stale unexpected output
files, symbolic links, changed patch anchors and archive resource excess. Limits:
2 MiB archive bytes, 512 entries, 512 KiB per entry, 4 MiB total expansion.
The pinned source has 226 entries, 178 Java files and 2,141,047 expanded bytes.

The helper verifies the exact original two-function block separately before
replacement. All other upstream Java bytes and the original `META-INF/LICENSE`
are preserved. Tests assert that boundary and compare classifier behavior against
independent W3C production tables over every Unicode scalar. Malformed UTF-16 is
separately refused by the server's mandatory whole-source preflight; this module
alone is not the qualified mechanism.

Runtime needs only `stax2-api:4.3.0`. MSV core, datatype API, bnd annotations and
OSGi API are provided only to compile the complete upstream implementation. There
is no XSD/MSV runtime strategy or provider discovery registration. The server
constructs `WstxInputFactory` explicitly and verifies its required hardening settings.
A test isolates the parser and StAX2 API from all optional build dependencies.

Plugins are pinned: dependency 3.6.1, antrun 3.1.0, compiler 3.14.1, jar 3.4.2.
Surefire inherits the repository pin and explicitly fails on missing tests.
The parent reproducible output timestamp applies to the generated JAR. License
and patch notice resources identify the modification and source digest.
An upstream/patch update requires repeat qualification, not an automatic upgrade.
