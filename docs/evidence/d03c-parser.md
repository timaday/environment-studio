# D03c qualified parser evidence — 8 September 2026

The source-span adapter now supports the approved XML 1.0 Fifth Edition names
using an explicitly hardened, source-built Woodstox implementation. A compiler-ready
native v2 declaration with matching astral/prefix/attribute names now projects
successfully. Original source characters, XML 1.0 normalization, exact namespace
attribute references and writer outcomes remain unchanged outside qualified edits.
This corrects parser qualification; it does not narrow the native definition or
reinterpret XML 1.0 as XML 1.1.

Authority: the lead-frozen `xml-parser-qualification.md` at root revision
`473deae6a09a332bf6e88e314728a6259a25e5bd`, plus the existing native v2, lossless XML
and graph-projection contracts. The writer read that root contract without changing
shared contracts. Worktree `/tmp/es-d03-graph-projection`, immutable base
`9f03afa9ba09c34e0dfa6929502f84cdcdc6c5a5` plus the frozen D03b candidate manifest
`233b0c1b015eae0be1c4b11d580d6360dfd295a1b2bff3a4580f7b4192ecb18a`.
All 13 D03b files remain byte-identical except the expressly authorized single
end-to-end regression added to `GraphProjectionAdapterTest`.

## Reproducible source dependency

The new `backend/qualified-xml-parser` module fetches only
`com.fasterxml.woodstox:woodstox-core:7.2.2:jar:sources` into `target/upstream`.
The bounded Java 21 build helper verifies SHA-256
`24669b0269917ca63270e81222c038b3ce737eba12bdb4a159e48e8b1d5a4822` before extraction,
then verifies the original two-function block digest before changing only
`XmlChars.is10NameStartChar`/`is10NameChar`. All other upstream Java and the original
license bytes are preserved. The classifier uses Fifth Edition productions with
D800–DB7F high surrogates; whole-source paired-surrogate preflight remains mandatory.

Bounds: 2 MiB compressed artifact, 512 entries, 512 KiB per entry and 4 MiB total
expansion. Paths, canonical duplicate entries, archive expansion, missing/changed
anchors, generated-tree symlinks and unexpected stale generated files refuse.
Validation completes before generated sources are written. Tests invent ZIP entries
independently; no upstream test fixtures or actual configuration are fetched.
Generated upstream code and binaries are ignored build outputs, not checked-in files.

Pinned lifecycle: dependency plugin 3.6.1 copies the source artifact during
`initialize`; antrun 3.1.0 runs the Java source helper during `generate-sources`;
compiler 3.14.1 compiles all 178 upstream Java files; jar 3.4.2 packages the complete
modified implementation. Surefire 3.5.4 explicitly fails on missing tests. The build
retains Apache 2.0 license text, original artifact license, source checksum and a
patch notice. `Implementation-Version` is `7.2.2-es-xml10-fifth-1`, not an unmodified
Woodstox release claim. No provider service-discovery files are packaged.

Runtime depends only on `stax2-api:4.3.0`, whose actual packaged SHA-256 is
`7c805f36129ea9fa42b696093b7ae1eb20bb6ccec65c8280d6f33db5609ca5e1`. Minimal-runtime
JUnit evidence loads only the complete parser and StAX2 API through an isolated
classloader, exercises Fifth Edition names and exact normalization, verifies missing
MSV/OSGi classes, and checks absence of parser/schema service entries. Provided-only
compile dependencies are `msv-core:2022.7`, `relaxngDatatype:20020414`,
`biz.aQute.bnd.annotation:6.4.0`, and `osgi.core:5.0.0`, with transitive dependencies
excluded. Neither xsdlib nor isorelax is needed. Inspection of the actual server
JAR confirms no stock Woodstox or provided-only dependency is bundled.

Independent candidate and restored scratch module builds produce byte-identical
JARs under the inherited fixed output timestamp. Qualified JAR SHA-256:
`089027c13298e40fb335bf7d7c37f39559ad4cbd7e366cbc02f008ed7a7f8784`.
No project artifacts were installed into shared Maven storage and no global parser
configuration or Git state changed.

## Adapter and preservation checks

The server constructs `WstxInputFactory` directly and sets/reads back namespace
awareness, DTD off, external entities off, entity replacement on, validation off,
lazy parsing off, empty external DTD access and the existing parser resource bounds.
Unavailable/ignored settings fail initialization. The resolver always throws;
external-DTD probes observed zero resolver calls. XML 1.1 and DTD events refuse.
StAX performs no XInclude processing; expanded-name refusal of inclusion, signature
and encryption namespaces remains in place. Schema-location hints remain inert data.

Lexical preflight still enforces exact-source Unicode and size/token limits. The
adapter joins StAX expanded names/decoded values with the original lexical spans,
checks element/attribute agreement, and retains source characters. Namespace
declarations are explicitly merged with ordinary attributes. Woodstox validates
but omits explicit `xmlns:xml` from its namespace-declaration iterator, so that
lexical attribute uses the independently validated reserved namespace context.
A regression asserts its established expanded name, decoded URI and exact span.
No raw-source decoding, namespace rewriting or DOM serialization substitutes for
that parser agreement.

Independent tests cover 200 valid name placements across elements, attributes,
prefixes and PI targets, 136 invalid-gap/upper-bound placements, 32 malformed
surrogate placements and continuation-only characters. The module checks every
Unicode scalar against independently transcribed W3C name-production tables:
**2,224,128 start/continuation comparisons**. No compiler method generates this
oracle. Cases include U+037F, U+200C, U+F900, U+10000 and U+EFFFF; U+F0000 is invalid
in names but remains valid XML character data.

Existing G04 exact-output tests pass, alongside CRLF/CR/attribute normalization,
U+0085/U+2028 preservation, entity spelling, quote escaping, comments, CDATA, PI,
self-closing expansion, namespace aliases/default reset, explicit reserved namespace
bindings, schema hints and supplementary names across parser-buffer boundaries.
Tests exercise exact/max-plus-one depth 128, attributes 256, elements 20,000,
source units 1,048,576 and lexical tokens 100,000. Rejections return no partial target.

## Actual RED and GREEN

Tools: Maven 3.9.16 at `/tmp/es-lead-toolchain/maven/bin/mvn`, Java 21.0.12;
Node 24.20.0 via `/tmp/es-lead-toolchain/node/bin/node` with read-only test dependency
reuse through `NODE_PATH=/tmp/es-d01-native-v2/frontend/node_modules`.

- Integrated behavior RED: `mvn -B -ntp -f backend/pom.xml test`,
  17:39:30 +01:00. Core 61 passed; server 110 tests had **1 assertion failure,
  0 errors**. The compiler returned ready, but the old JDK SAX adapter rejected
  the matching Fifth Edition source. `/tmp/es-d03c-integrated-red.log`.
- First integrated GREEN: `mvn -B -ntp -f backend/pom.xml verify`,
  17:46:00 +01:00; core 61, parser 6 and server 110 passed.
  `/tmp/es-d03c-green2.log`.
- Final candidate GREEN: same `verify`, 17:52:25 +01:00;
  **183 tests passed, 0 failures/errors** (core 61, qualified parser 7, server 115).
  This includes all existing v1/v2, graph, XML, architecture and hosted session/HTTP
  regressions. `/tmp/es-d03c-final-verify.log`.
- Schema tests: 8 passed; Python script tests: 10 passed; repository integrity
  and `git diff --check` passed. `/tmp/es-d03c-schema.log`, `/tmp/es-d03c-python.log`.
- Working-tree content assessment has exactly one expected integration blocker:
  `UNREGISTERED_DATA_ARTIFACT: backend/qualified-xml-parser/pom.xml`. The lead owns
  adding this exact tool POM to the shared guard allowlist; the writer did not
  broaden the guard. G00 staged publication evidence remains lead-owned.

Source-archive preparation first refused a legitimate empty `annotations/`
directory; the exact upstream directory was added to the bounded path vocabulary.
A new namespace regression then exposed the explicit `xmlns:xml` iterator omission
and passed after context-based preservation. These observed development findings
are separate from the original behavioral RED. Line/branch percentages were not
measured; no coverage agent is configured for this slice.

## Targeted G06 and remaining integration

The separate `/tmp/es-d03c-mutants` copy contains the public backend, schemas,
invented fixtures and public deployment health-probe helper. Each guard mutation
runs the full Maven `test` reactor, then restores its original source in `finally`.
The five prior writer guards are repeated, along with Unicode preflight, hardening
readback and the two name-classifier boundary guards. Final clean-run results are
recorded below; all failures must be behavioral assertions, never compilation failures.

An initial scratch copy omitted `deploy/HealthProbe.java`, producing an unrelated
hosted-probe test failure in addition to the intended mutant failures. Those mixed
runs are retained as `*.pre-deploy.log` and are not the final mutation evidence.
After adding the unchanged public helper, all nine mutations were rerun.

| Removed/weakened guard | Final behavioral failures | Finished (+01:00) |
| --- | --- | --- |
| Exact source digest | 1 / 115 server tests; 0 errors | 17:56:48 |
| Expected attribute value | 2 / 115; 0 errors | 17:56:58 |
| Source-bound element reference | 1 / 115; 0 errors | 17:57:08 |
| Post-state semantic validation | 2 / 115; 0 errors | 17:57:18 |
| Ampersand escaping | 2 / 115; 0 errors | 17:57:28 |
| Mandatory Unicode preflight | 4 / 115; 0 errors | 17:57:38 |
| Required property readback | 1 / 115; 0 errors | 17:57:48 |
| Fifth Edition U+037F range | 1 / 6 module tests; 0 errors | 17:57:52 |
| Supplementary name upper bound | 1 / 7 module tests; 0 errors | 17:57:56 |

All nine mutants were killed. Logs: `/tmp/es-d03c-mutant-<guard>.log`, with exact
names/results in `/tmp/es-d03c-mutations.json`. Every source guard was restored and
compared byte-for-byte against the untouched candidate. The final scratch `verify`
regenerated the correct upstream sources/classes, passed all 183 tests and rebuilt
the byte-identical qualified parser JAR; `/tmp/es-d03c-clean-restored-verify.log`.
No mutant remains in candidate or scratch source/build outputs. This is focused
manual mutation evidence, not a complete PIT/line-coverage claim.

The source-built correction remains an internal XML mechanism. Database completeness,
SQL/client qualification, target non-interference orchestration, plans and export
approval remain separate. Root owns OCI/Docker verification, the exact new tool-POM
allowlist update, staged-content checks and independent candidate review. No product
support reduction, XML 1.1 fallback or runtime application-model inference was added.

Writer implementation effort: approximately 21 minutes, excluding the preceding
scratch feasibility experiment. Rework was approximately 3 minutes for the archive
empty-directory allowlist, explicit reserved namespace declaration and correcting
the mutation scratch inventory. No contract-blocked time; lead integration/review
and G08 work are separate. No parallel speed-up claim is made.

## Independent-review correction: hidden attribute-size default

The reviewer reproduced an undeclared Woodstox default: an attribute value of
524,289 characters was refused even though its document remained below the
approved 1,048,576 UTF-16 source bound. `P_MAX_ATTRIBUTE_SIZE` now explicitly uses
`XmlLexicalScanner.MAX_CHARS`, with the same mandatory property readback as every
other configured parser limit. Source-level limits remain unchanged.

A new behavior test accepts values of 524,288, 524,289 and 1,048,567 characters
(the last creates an exact 1,048,576-character source), checks the exact decoded
value and no-op source, and rejects a source one character over the bound.
Behavior RED: `mvn -B -ntp -f backend/pom.xml test`, 18:07:45 +01:00;
server 116 tests with 1 assertion failure and 0 errors, core/parser passing.
Log: `/tmp/es-d03c-attribute-red.log`. GREEN: the same pinned executable with
`verify`, 184 tests passed (core 61, parser 7, server 116), 0 failures/errors.
Log: `/tmp/es-d03c-attribute-green.log`.

Review of all ReaderConfig maximum fields and parser limit call sites found no
other smaller active default: text length and children-per-element are
Integer.MAX_VALUE, document character/element defaults are Long.MAX_VALUE and
are already explicitly bounded, while depth/attribute-count are explicitly set.
Entity depth/count and DTD depth apply to declared-entity/DTD processing, which
this adapter refuses before parsing; predefined/numeric references do not use
that declared-entity expansion path. Symbol-table growth is an internal capacity
threshold, not a hidden maximum accepted-name size. No additional contradiction
was found. This scoped correction changed only the hardened adapter, XML test
and this evidence; existing parser-source/module/classifier and writer guards
remain untouched. Correction effort approximately 3 minutes, no blocked time.
