# Qualification matrix

No database/client combination has yet been qualified. Replace these unknowns
with exact versions and evidence; never fill the matrix from driver availability.

| Capability | Candidate | Scope | State |
| --- | --- | --- | --- |
| Oracle | Version unknown; CLOB; exact SQL*Plus/SQLcl unknown | Qualified application family + structural/scalar operations | NOT RUN |
| PostgreSQL | Version unknown; text; exact psql unknown | Same logical family where capabilities overlap | NOT RUN |
| PostgreSQL xml/OID | Not selected | Separate storage contract required | UNSUPPORTED |
| Native YAML/JSON definitions | Version 1 draft meta-schema | Shape checks exist; semantic compiler missing | INCOMPLETE |
| XSD import | Dialect/features not selected | Supported subset into draft, no semantic guessing | NOT IMPLEMENTED |
| HiveForge | Platform/config unknown; OCI linux/amd64 candidate | Actual registry pull, TLS/identity/probes/restart | NOT RUN |

Record engine, server/client/JDBC/parser/writer/definition versions, encoding,
identity strategy, row/byte/depth limits, operations, lock semantics, test data
references, actual commands/results, image digest and source fingerprint.
``release-evidence.json`` requires all named capability gates; reference evidence
JSON files under docs/evidence for the exact source fingerprint. Redacted public
records may link to internal evidence locations but must not expose real data.

Automated bookkeeping can reject missing/stale evidence; it cannot verify that a
human's observation is true. Review the evidence and oracles before a versioned
release. A fictional PASS file does not qualify the application.
