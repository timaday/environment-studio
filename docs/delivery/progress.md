# Handoff and capability status

Updated: 8 September 2026. See [starter evidence](../evidence/starter-verification.md)
and [D01a integration](../evidence/d01a-integration.md) for actual commands and
results; the table below reports capability, not test count.

| Slice | State | Next evidence |
| --- | --- | --- |
| D00 foundation | COMPLETE for starter: CI/build/smoke/GHCR passed | See ../evidence/starter-verification.md and image-reference.json |
| D01 definitions | D01a bounded JSON/YAML draft compiler and semantic diagnostics implemented; no publication or runtime upload | Complete logical/binding contract, publication checks and immutable revision APIs |
| D02 security/state | Demo rejects mutations; hosted session implementation in isolated worktree | True mock OIDC, ownership/CSRF/expiry tests; metadata persistence remains separate |
| D03 XML | Lossless mechanism contract committed; adapter in isolated worktree | Qualified parser/span writer and independent expected outputs |
| D04 database reads | Not implemented | Oracle/PostgreSQL disposable DB observations |
| D05 profiles | Closure algorithm starter; capture/persistence not implemented | Value-free import/export and composition behavior |
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

Active work: D02a hosted session and D03a lossless XML mechanism, using separate
worktrees and frozen public contracts. Next D01 work must supply complete declared
identity, inventory, projection, rule and operation semantics before publication.
Private qualification and actual HiveForge/IdP configuration remain external.
Request only generic decisions through the Q handoff when needed. Do not mark
`release-evidence.json` PASS by editing a
status field alone; provide evidence files for the exact candidate context.
