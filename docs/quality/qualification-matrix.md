# Qualification matrix

No database/client combination has yet been qualified. Replace these unknowns
with exact versions and evidence; never fill the matrix from driver availability.

| Capability | Candidate | Scope | State |
| --- | --- | --- | --- |
| Oracle | Disposable Free 23.26.3.0.0; CLOB; bundled SQL*Plus 23.26.3.0.0 | Client SP2 continuation observed; reads and guarded transaction package not implemented/qualified | INCOMPLETE |
| PostgreSQL | Disposable 18.6; text; psql qualification pending | Same logical family where capabilities overlap | NOT RUN |
| PostgreSQL xml/OID | Not selected | Separate storage contract required | UNSUPPORTED |
| Native YAML/JSON definitions | Version 1 draft meta-schema; D01a compiler and D02b owned upload | Bounded parsing, checks, immutable incomplete revisions; publication absent | INCOMPLETE |
| Native version 2 compiler | D01b closed logical/binding semantics and versioned digests | Independent digest oracle and reviewed static mechanism limits; runtime publication/observation separate | COMPILER CHECKS PASS |
| Definition inspector | Synthetic version 1 projection | Component behavior and desktop/390px keyboard/axe checks; no integrated editing or operator rehearsal | COMPONENT CHECKS PASS |
| XML spans and graph projection | D03b/c, qualified Woodstox 7.2.2-es-xml10-fifth-1 on Java 21 | Exact source and graph, Fifth Edition names/bounds, three graph and nine writer/parser guard mutants; [211-test integration and protected image](../evidence/d03bc-integration.md); structural planner/client qualification separate | MECHANISM CHECKS PASS |
| Hosted session | D02a, independently invented mock OIDC provider | Protocol, CSRF/cookies, expiry, cleanup failure/races and log canaries; actual IdP/TLS/proxy and DB cleanup separate | MOCK CHECKS PASS |
| Private draft workspace | SQLite JDBC 3.53.4.0, one replica | Owner isolation/integrity, immutable replay, quotas, process-crash journal recovery and protected-image initializer; deployment fsync/power loss/freshness separate | LOCAL CHECKS PASS |
| XSD import | Dialect/features not selected | Supported subset into draft, no semantic guessing | NOT IMPLEMENTED |
| HiveForge | Platform/config unknown; OCI linux/amd64 candidate | Actual registry pull, TLS/identity/probes/restart | NOT RUN |

Record engine, server/client/JDBC/parser/writer/definition versions, encoding,
identity strategy, row/byte/depth limits, operations, lock semantics, test data
references, actual commands/results, image digest and source fingerprint.
``release-evidence.json`` requires all named capability gates; reference evidence
JSON files under docs/evidence for the exact source fingerprint. Repository
evidence uses independent mock databases and public code/artifact references only.
Actual application evidence, private source hashes, model/definition versions,
paths and links stay in the separate private qualification workflow. Generic
private-review outcomes do not replace mock execution evidence or prove a fix.

Automated bookkeeping can reject missing/stale evidence; it cannot verify that a
human's observation is true. Review the evidence and oracles before a versioned
release. A fictional PASS file does not qualify the application.
