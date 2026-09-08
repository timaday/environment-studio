# Private application and deployment qualification checklist

This is a generic checklist, not a place to record actual application facts.
An application owner/DBA confirms them in a separately authorized private workspace,
outside this repository's checkout and build context. Do not fill this file with
real XML, schemas, locators, definitions, profiles, values or private evidence
links, even after redaction. Share only the generic outcomes and independent mock
cases described in [the Q handoff](../agents/amazon-q-feedback.md).

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

The repo definition example is an independently invented mock fixture. It cannot
establish actual application correctness. Record private decisions and evidence
in the authorized external workflow. This repo receives only generic capability
outcomes, public contract questions and mock evidence, with report-local finding
IDs where useful. Creating or managing external configuration versioning is out
of scope; runtime upload and profile save/revision features remain in scope.
