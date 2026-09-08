# Application and deployment intake

Use sanitized samples; do not add real credentials/configuration to this public
repo. An application owner/DBA must confirm the following before qualification.

| Fact | Required evidence | Current state |
| --- | --- | --- |
| Scope | Actual schemas/tables/columns/typed unique row keys; expected membership query strategy | Unknown |
| XML | Representative many-document sets, namespaces, largest CLOB, comments/CDATA/Unicode and unknown extensions | Only synthetic example exists |
| Meaning | Declared types, identity scope, relations, multiplicity, application-specific constraints | Unknown |
| Structural change | Approved one-to-two before/after configuration and legal move/copy/create semantics | Unknown |
| Values | Required environment fields, ID uniqueness, absent/empty/case/whitespace semantics | Unknown |
| Portability | Explicit profile capture allowlist, labels, structural constants/input declarations | Unknown |
| Secrets | Embedded-secret classification and whole-document export policy | Unknown |
| DB matrix | Oracle/Postgres version, CLOB/text storage, encoding, JDBC version, exact CLI/version | Unknown |
| Destination | Independently managed post-clone witness distinguishable from donor, enforceable by script | Unknown |
| Transactions | Privileges/locking window, triggers, sequences, side effects, application isolation/reload | Unknown |
| A/B | Separate physical DBs? Must update atomically or orchestrated independently? | Unknown |
| HiveForge | Actual platform/docs, OCI/Compose/API format, CPU, TLS/ingress, volumes, identity, pull-secret method | Unknown |
| Operators | OIDC/provider or approved auth boundary; allowed users; shared vs per-user instance | Unknown |
| Capacity | Complete record count/bytes/depth/concurrent plans and workstation/container resources | Unknown |

The definition example is a design fixture, not permission to substitute invented
SQL tables or server semantics for these facts. Record decisions with the owner,
date, evidence reference and affected capability matrix entry.
