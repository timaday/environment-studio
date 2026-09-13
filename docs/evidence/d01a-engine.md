# D01a engine evidence — 8 September 2026

The local bytes adapter compiles bounded JSON/YAML into a checked immutable
draft or a safe rejection. Every accepted draft remains **incomplete**. This
implements the engine portion of [D01a](../contracts/definition-compilation.md),
not definition publication, HTTP upload, persistence, inspection or export.
Traceability: ES-01, ES-03, ES-04, ES-09 and ES-16; G00/G01 and bounded hostile
input investigation relevant to G07. Frontend/browser and OCI integration gates
belong to the lead's combined candidate.

## Candidate and provenance

Writer base: `b00e5776b074d271675d517bc106290b7f14be8f`, isolated worktree
`/tmp/es-d01-engine-b00e577`. The numeric budget clarification was read from the
lead's `939b90d2740c847dbf21d90711f33c9592c92cc2` contract without changing the
writer's Git state. The writer changed only the two assigned `definition`
packages and their tests, `backend/server/pom.xml`, and this evidence record.
No staging, commits, branches, credentials, private inputs or external state
were changed by the writer.

All test declarations were invented directly for generic compiler behavior.
`mock-orbit`, its arbitrary types and its XPath/namespace strings have no real
application provenance. Tests contain no database fixtures or production
configuration. No standalone configuration fixture was added; provenance is
also stated on both test classes. No private model was read or transformed.

## Actual TDD and verification

All Maven commands below used this exact prefix, with only the independent
worktree mounted:

```bash
docker run --rm -v /tmp/es-d01-engine-b00e577:/workspace -w /workspace \
  maven:3.9.16-eclipse-temurin-21@sha256:8f6ac126f7810bb5549c4cd122d2bf0e9cda5bdeb0838aa928f09e779fd8bef8 \
  mvn -B -ntp -f backend/pom.xml
```

| Run / command suffix | Actual result |
| --- | --- |
| Core RED: `-pl core test`, 14:31:01 UTC | 15 tests; 2 assertion failures, 0 errors. A callable stub returned rejection for a valid recursive declaration and omitted required semantic diagnostics. This was behavior RED, not compilation failure. |
| Adapter RED: `test`, 14:33:07 UTC | Core passed; server 25 tests, 6 assertion failures, 0 errors. The callable adapter stub could not produce equal JSON/YAML drafts, exact integers, schema diagnostics, documented resource refusals or YAML scalar semantics. |
| Initial GREEN: `test`, 14:36:10 UTC | Core 15 and server 25 tests passed; 0 failures/errors/skips. |
| Expanded GREEN: `verify dependency:tree`, 14:38:30 UTC | Core 17 and server 31 tests passed; 0 failures/errors/skips. Both JARs packaged. |
| Final GREEN after excluding unused validator YAML parser: `verify dependency:tree`, 14:40:04 UTC | Core 17 and server 31 tests passed; 0 failures/errors/skips. Dependency tree confirms the exclusion. |
| `python3 scripts/check_repository.py` | Repository integrity: PASS. |
| `git diff --check` | Exit 0. |

The complete test classes are
[DefinitionCompilerTest](../../backend/core/src/test/java/studio/environment/core/definition/DefinitionCompilerTest.java)
and [DefinitionBytesCompilerTest](../../backend/server/src/test/java/studio/environment/server/definition/DefinitionBytesCompilerTest.java).
Existing architecture and demo HTTP denial tests also passed. Raw local Maven
logs remain outside the checkout at `/tmp/es-d01-*.log`; only generic results
are recorded here. Host Maven was not used as evidence. No new mutation-testing
qualification is claimed; the starter mutation target remains unchanged.

The final Maven run took 38.672 seconds. Writer work took approximately
14 minutes, including reading the contract and library APIs; no contract wait
blocked useful work. Numeric clarification was incorporated before adapter
implementation. Dependency exclusion/reverification took about one minute.
Lead integration and independent-review rework are not included; no speed-up
claim is supported by this single work unit.

## Findings and limitations

Business: arbitrary vocabulary, empty allowed lists and recursive type
declarations remain inspectable. Uploaded `published` status conveys no
authority. Identity, inventory, selectors, writers, operations, rule binding
and unknown sensitivity produce explicit publication blockers. A name cannot
install a rule or writer implementation.

Engineering: the core has no parser/framework dependency. Nested lists and
maps are copied; declaration order is retained. Revision and cardinality
integers use `BigInteger`, including integral decimal input. Semantic duplicate
and reference failures produce no model. Results sort and deduplicate constant
safe diagnostics. Dynamic namespace keys and unknown properties never appear
in diagnostic pointers.

QA/security investigation: tests perturb duplicate keys/IDs, dangling mapping
references, inverted cardinalities, Unicode supplementary characters, strict
UTF-8, multiple documents, explicit tags/anchors/aliases/merge keys/non-string
keys, non-finite numbers, and every resource budget. Exact byte/depth/node/string
and expanded-number boundaries are exercised. Short billion-scale exponents
are refused before arbitrary-precision conversion; `1e1023` remains bounded
and accepted by the parser. The schema resource is byte-compared with the
canonical repository file. Input `$ref` is rejected data; the schema loader has
remote fetching disabled and blocks resource resolution.

These are focused mock experiments supporting RST-09, with diagnostic-marker
checks relevant to RST-06. They do not constitute a complete RST campaign,
network packet capture, browser lifecycle audit, private application validation,
DB qualification, sustained throughput test or process-level memory profiling.
No selector, XPath, XML writer, SQL or uploaded code executes in this slice.

## Dependencies and packaging

New direct pins: `com.networknt:json-schema-validator:3.0.7` and
`org.snakeyaml:snakeyaml-engine:3.1.1`. The existing Spring Boot 4.1.1 BOM resolves
Jackson core/databind 3.1.5. Networknt's unused `jackson-dataformat-yaml` dependency
is excluded; only the bounded low-level SnakeYAML Engine event adapter handles
supplied YAML. Its resolver uses JSON scalar semantics; no YAML constructors
or implicit dates/classes are instantiated.

The server packages `../../schemas/definition.schema.json` as
`/schemas/definition.schema.json`; there is no second maintained schema copy.
The lead must copy the canonical schemas directory into the Docker build stage
at the corresponding repository root. Runtime uses only the packaged resource.
Dependencies were inspected using Maven Central release metadata and the
libraries' source APIs; the dependency tree was executed. This is not a
vulnerability audit or a governed HiveGate execution record.

Remaining D01 work includes representation and qualification of publication
semantics, hosted identity before real uploads, persistence and immutable
published revisions. D02-D09, private application qualification and release
readiness remain separate work. The lead owns staged-content review, integrated
checks, the fixed-candidate independent review and any commit or publication.
