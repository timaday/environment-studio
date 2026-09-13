# D03b graph projection evidence — 8 September 2026

The internal projection adapter now joins a server-compiled native v2 definition,
an explicitly selected binding and its complete exact-source inventory into an
immutable validated graph. Two independently invented glyph instances refer to
one palette across two documents. Exact decoded values, optional absence versus
present-empty, source origins and untouched source characters remain available
through transient objects with safe `toString` representations.

This implements [graph projection](../contracts/graph-projection.md), using the
[native v2 contract](../contracts/native-definition-v2.md) and existing
[lossless XML adapter](../contracts/lossless-xml.md). Traceability: ES-03, ES-04,
ES-06, ES-09 and ES-16; bounded local G00/G01/G04 and targeted G06 evidence.
It does not provide database completeness, publication, a structural planner,
SQL/client qualification or export authority.

## Candidate and boundaries

Immutable base `9f03afa9ba09c34e0dfa6929502f84cdcdc6c5a5`; writer worktree
`/tmp/es-d03-graph-projection`. Only assigned `core.graph`, `server.projection`,
associated tests, independently invented XML/provenance and this evidence changed.
Existing XML/compiler APIs, dependencies, POMs, schema, routes and Git state were
not changed. No database, disk persistence, global cache or background queue was
introduced. Exact source documents and graph values remain operation-scoped.

`GraphProjectionAdapter.project(ReadyToPublish, bindingId, List<DocumentSource>)`
accepts the internal server compiler result, checks the trusted mechanism versions
and complete declared document inventory, then maps expanded-name direct-child
paths and attributes from the existing hardened lexical adapter. Rejections carry
safe codes and trusted declaration identifiers, with no graph or partial source
inventory. Unknown input document/binding identifiers are never echoed.

`GraphValidator` owns the framework-free identity, required-field, scalar,
reference, containment, outgoing-cardinality and entity-count checks. No first
identity or duplicate projection is accepted as a fallback. Containment chooses
the nearest selected compatible ancestor and refuses distinct multiple parents
or cycles. Graph content sorts explicit type/identity/relation keys; source origins
sort document ID, projection ID and element index. All owned nested collections
are immutable. Missing field-map keys denote absence; present empty strings stay
present. Only present attributes are materialized, avoiding large collections of
absent optional fields.

Bounds are checked before oversized input collection copies: 128 documents,
16 MiB strict UTF-8 source, 20,000 projected entities and 50,000 edges, plus the
existing XML limits. Strict UTF-8 sizing checks surrogate pairs without allocating
encoded buffers; bounded per-document lengths and a long accumulator prevent
count overflow. Diagnostics are capped at 256 safe entries; any anomaly still
rejects the whole graph. Core typed occurrences are internal lexical evidence;
source/Unicode validation remains at the server boundary.

## Provenance and independent oracles

`fixtures/native-v2/xml/glyphs.xml` and `palettes.xml` were invented from scratch
for the already invented tiles family. Their registration extends
`fixtures/native-v2/provenance.json`. No actual application model, private input,
redaction, renamed real material or external configuration was consulted.

Independent test expectations assert two glyph identities `alpha`/`beta`, target
identity `shared`, exact leading spaces, decoded ampersand/astral character/tab,
present-empty text, source element indices/ancestry and exactly two reference
edges. The source fixture independently includes CRLF, comments, a PI, CDATA,
namespace aliases, both quote styles and unmapped material. Retained source strings
are compared character-for-character, and source digests are independently checked
using JDK SHA-256 of strict UTF-8. Oracle/PostgreSQL selections produce equal graphs
and logical digests with different binding digests. Inline adverse declarations
and XML are likewise independently invented generic examples.

## Observed RED and GREEN

Toolchain: `/tmp/es-lead-toolchain/maven/bin/mvn` 3.9.16, Java 21.0.12;
`/tmp/es-lead-toolchain/node/bin/node` 24.20.0. Maven ran only in the independent
writer/mutation worktrees, never `install` or shared project-artifact publication.
Node read existing test dependencies through
`NODE_PATH=/tmp/es-d01-native-v2/frontend/node_modules`; no dependency changed.

- Behavioral RED: `mvn -B -ntp -f backend/pom.xml test`, 17:03:51 +01:00.
  Core 55 passed; server 98 tests with **2 assertion failures, 0 errors**.
  A callable production stub rejected the valid graph and returned the wrong
  inventory diagnostic. Log: `/tmp/es-d03b-red.log`.
- Initial GREEN: same command, 17:06:14 +01:00; core 55 and server 98 passed.
  Log: `/tmp/es-d03b-green1.log`.
- Final expanded GREEN: `mvn -B -ntp -f backend/pom.xml verify`,
  17:11:50 +01:00; **170 passed, 0 failures/errors** (core 61; server 109).
  Includes 6 new core graph and 13 new server projection tests, existing v1/v2,
  XML, inward architecture, hosted session and HTTP regressions.
  Log: `/tmp/es-d03b-full-green.log`.
- `node --test scripts/schema.test.mjs`: 8 passed; Python script tests: 10 passed.
  Logs: `/tmp/es-d03b-schema.log`, `/tmp/es-d03b-python-tests.log`.
- `python3 scripts/check_repository.py`, `git diff --check`: passed.
  Full working-tree `check_repository_content.assess` including unchanged fixture
  members: passed. This is local candidate evidence, not the staged upload guard;
  the integrating lead must run that guard after staging reviewed eligible files.

Tests challenge duplicate/missing/extra inventory; unknown binding/mechanism;
physical projection overlap; cross-document duplicate identity; empty identity;
required absence even with empty rule lists; integer/boolean/URI scalar refusal;
exact large integer text; namespace-qualified versus unqualified attributes;
optional absence/present-empty; dangling/empty references; zero/excess outgoing
cardinality; missing/nearest/multiple containment parents; cycles; and zero/excess
entity counts with arbitrary-precision bounds. Safety tests exercise malformed
XML, DTD/external entities, invalid surrogate sequences and safe diagnostics.

Resource boundary examples exercise 128 accepted versus 129 declared documents,
20,000 accepted versus 20,001 projected entities across documents, 50,000 accepted
versus 50,250 edges, aggregate UTF-8 excess using astral source text, per-document
source/depth excess and virtual enormous lists that fail if iterated. No bounded
refusal returns a truncated success. Nested immutability and deterministic source
ordering are asserted. Line/branch percentages were not measured; no coverage
agent is configured for this slice. Passing counts are not a release verdict.

## Targeted G06 investigation

An independent directory copy `/tmp/es-d03b-mutants` contained only the backend,
schemas and public invented fixtures. Each mutation replaced exactly one guard in
`GraphValidator`, then ran
`mvn -B -ntp -f backend/pom.xml -pl core -Dtest=GraphValidatorTest test`.

| Removed guard | Observed outcome |
| --- | --- |
| Required field absence | Killed: 6 tests, 1 assertion failure, 0 errors; 17:12:46 +01:00. |
| Duplicate actual identity | Killed: 6 tests, 1 assertion failure, 0 errors; 17:12:48 +01:00. |
| Empty/unresolved reference | Killed: 6 tests, 1 assertion failure, 0 errors; 17:12:50 +01:00. |

Logs: `/tmp/es-d03b-mutant-required-field.log`,
`/tmp/es-d03b-mutant-duplicate-identity.log` and
`/tmp/es-d03b-mutant-reference-resolution.log`. Each mutant incorrectly accepted
the adverse graph, so the assertion failures exercised real production behavior.
The scratch file was restored in `finally` after every mutation and compared
byte-for-byte against the untouched candidate. Restored SHA-256:
`daffcabdef68735a43362ab6985a9086d202bf018e906058d24c3674f552d49a`.
The restored core suite passed all 6 tests; log
`/tmp/es-d03b-mutant-restored.log`. No mutation remains in the candidate or scratch.

## Investigation scope and remaining work

Business: the complete two-to-one mock graph is inspectable, but this slice cannot
claim a database returned every row. Engineering: adapters depend inward, source
strings are retained exactly, and no uploaded behavior or inferred semantics runs.
QA/security: malformed inputs and mandatory evidence gaps refuse without partial
graphs or value-bearing diagnostics. Stable safe representations reduce accidental
logging; no real credentials/configuration were used to test privacy.

A complete structural planner, target read sets/non-interference, database row-key
and transaction evidence, SQL/client execution tests and workspace authority remain
separate slices. There is no wall-clock timeout API in this bounded synchronous
mechanism; operation cancellation/time budgeting belongs to its future orchestrator.
G08 image verification and independent candidate review belong to lead integration.

Elapsed writer effort: approximately 15 minutes; under 1 minute rework for an
ArrayNode test-helper API typo and content-assessment invocation context, no blocked
time. Those helper errors are not behavioral RED evidence. Integration/review time
has not yet been measured; no parallel speed-up or whole-D03 qualification is claimed.
