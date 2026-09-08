# Handoff and capability status

Updated: 8 September 2026. See docs/evidence/starter-verification.md for actual
commands and results; the table below reports capability, not test count.

| Slice | State | Next evidence |
| --- | --- | --- |
| D00 foundation | Starter authored; execution evidence recorded separately | First CI image and registry digest |
| D01 definitions | Contract/schema examples only | Java compiler and real application intake |
| D02 security/state | Demo rejects mutations; hosted auth not implemented | OIDC/ownership/CSRF/session tests |
| D03 XML | Synthetic before/after fixtures only | Qualified parser/span writer and independent oracle |
| D04 database reads | Not implemented | Oracle/PostgreSQL disposable DB observations |
| D05 profiles | Closure algorithm starter; capture/persistence not implemented | Value-free import/export and composition behavior |
| D06 planning | Evidence gate starter; full planner not implemented | Typed cross-document operations and binding validation |
| D07 SQL | Not implemented; export unavailable | Exact-client adverse transaction evidence |
| D08 UX | Synthetic comparison component starter | Integrated flows, accessibility and operator sessions |
| D09 deployment/release | OCI pipeline/contract; actual HiveForge unverified | Exact platform deployment and qualification matrix |

Next dependency-ready work: D01 with intake facts. If facts are unavailable,
complete D02 platform contract and synthetic compiler tests without inventing
production selectors. Do not mark `release-evidence.json` PASS by editing a
status field alone; provide evidence files for the exact candidate context.
