# Handoff and capability status

Updated: 8 September 2026. See [starter evidence](../evidence/starter-verification.md)
and [D01a integration](../evidence/d01a-integration.md) for actual commands and
results; the table below reports capability, not test count.

| Slice | State | Next evidence |
| --- | --- | --- |
| D00 foundation | COMPLETE for starter: CI/build/smoke/GHCR passed | See ../evidence/starter-verification.md and image-reference.json |
| D01 definitions | D01a draft and D01b native v2 compilers integrated; independent static-limit finding corrected | Workspace v2 upload/publication and immutable runtime revision APIs |
| D02 security/state | D02a hosted sessions and D02b owned SQLite v1 draft revisions integrated and reviewed | V2 publication/profile persistence; actual deployment durability/identity external |
| D03 XML | Guarded spans, definition-driven graph projection and corrected Fifth Edition parser integrated; independent review findings closed | Structural target orchestration and complete application/DB writer qualification |
| D04 database reads | Both engine adapters integrated; complete disposable matrices and review corrections passed | Hosted plan/session wiring and external TLS/account qualification |
| D05 profiles | Value-free capture, bounded portable import/export and explicit whole/partial composition integrated and reviewed | Owned immutable persistence/publication and hosted plan binding |
| D06 planning | Evidence gate starter; full planner not implemented | Typed cross-document operations and binding validation |
| D07 SQL | Not implemented; export unavailable | Exact-client adverse transaction evidence |
| D08 UX | Synthetic comparison and definition inspector; component keyboard/axe checks pass | Backend integration, editing workflow and representative operator sessions |
| D09 deployment/release | OCI pipeline/contract; actual HiveForge unverified | Exact platform deployment and qualification matrix |

Repository information boundary and Amazon Q feedback rules are documented in
`docs/product/repository-content-policy.md` and `docs/agents/amazon-q-feedback.md`.
Actual application models remain external; the repository guard checks only
known patterns and mock registration, with human provenance review still required.
See [workflow verification](../evidence/feedback-workflow-verification.md) for
executed checks and the independent static review. Parallel development follows
the bounded lead/two-writer/reviewer workflow. D01a engine and UI candidates and
the lead's integration harness received independent fixed-candidate review with
no material findings. Integration and rework are recorded in D01a evidence;
there is no serial comparison supporting a speed-up claim.

Active work: D06 structural targets and native
workspace publication/profile persistence. D05a is integrated with 233 passing
Java tests; see [profile integration](../evidence/d05a-integration.md).
D04 adds reviewed observation with 250 integrated Java tests and the actual
two-engine matrix; see [observation integration](../evidence/d04a-integration.md).
D03b/c is integrated with 211 passing Java tests and protected container checks;
see [integration evidence](../evidence/d03bc-integration.md). D01b supplies
declared identity, inventory, projection, rule and operation semantics before
publication. Its integrated Maven suite passed 151 tests after the independent
review correction. See [D01b integration](../evidence/d01b-integration.md).
See [D02a integration](../evidence/d02a-integration.md) for the 103-test combined
result, actual container checks and closed review findings.
See [D02b integration](../evidence/d02b-integration.md) for 178 integrated Java
tests, closed storage findings and the protected OCI initializer checks.
Disposable PostgreSQL 18.6 and Oracle Free 23.26.3 are running for qualification.
Default vendor privileges require the explicit checks and trust assumptions in
[the observation contract](../contracts/database-observation.md); blanket Oracle
grant revocation broke the disposable instance and did not qualify an observation.
A fresh pinned instance is used for the replacement policy. An actual SQL*Plus client
error continued with exit zero; [the investigation](../evidence/d07-client-investigation.md)
records why SQLERROR/exit status alone cannot qualify a guarded package.
Private qualification and actual HiveForge/IdP configuration remain external.
Request only generic decisions through the Q handoff when needed. Do not mark
`release-evidence.json` PASS by editing a
status field alone; provide evidence files for the exact candidate context.

Delivery tooling blocker: the authorized branch push was rejected by automatic
approval review because the session requires approval while its approval setting
is Never. Local commits and implementation continue; no GitHub upload, PR/CI or
published-image evidence is claimed for these new slices.
