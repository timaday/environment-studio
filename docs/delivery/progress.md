# Handoff and capability status

Updated: 8 September 2026. See docs/evidence/starter-verification.md for actual
commands and results; the table below reports capability, not test count.

| Slice | State | Next evidence |
| --- | --- | --- |
| D00 foundation | COMPLETE for starter: CI/build/smoke/GHCR passed | See ../evidence/starter-verification.md and image-reference.json |
| D01 definitions | Contract/schema examples only | Generic Java compiler, independent mock contracts and generic private-review feedback |
| D02 security/state | Demo rejects mutations; hosted auth not implemented | OIDC/ownership/CSRF/session tests |
| D03 XML | Synthetic before/after fixtures only | Qualified parser/span writer and independent oracle |
| D04 database reads | Not implemented | Oracle/PostgreSQL disposable DB observations |
| D05 profiles | Closure algorithm starter; capture/persistence not implemented | Value-free import/export and composition behavior |
| D06 planning | Evidence gate starter; full planner not implemented | Typed cross-document operations and binding validation |
| D07 SQL | Not implemented; export unavailable | Exact-client adverse transaction evidence |
| D08 UX | Synthetic comparison component starter | Integrated flows, accessibility and operator sessions |
| D09 deployment/release | OCI pipeline/contract; actual HiveForge unverified | Exact platform deployment and qualification matrix |

Repository information boundary and Amazon Q feedback rules are documented in
`docs/product/repository-content-policy.md` and `docs/agents/amazon-q-feedback.md`.
Actual application models remain external; the repository guard checks only
known patterns and mock registration, with human provenance review still required.
See [workflow verification](../evidence/feedback-workflow-verification.md) for
executed checks and the independent static review. Parallel development follows
the bounded lead/two-writer/reviewer workflow; no measured speed-up is claimed yet.

Next dependency-ready work: D01 using public contracts and independently invented
mock cases. Request generic decisions through the Q handoff when needed; private
intake facts stay outside this repo. Do not mark `release-evidence.json` PASS by editing a
status field alone; provide evidence files for the exact candidate context.
