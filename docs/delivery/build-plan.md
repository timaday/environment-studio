# Build plan — Friday 11 September 2026

Start: Tuesday 8 September. Goal: the highest honestly qualified pilot by
Friday, retaining the complete workflow. The broad enterprise product will
extend beyond this window. Indicative remaining work is **55–90 engineering
hours**, plus DBA/application-owner access and independent qualification. This
is an initial planning estimate, not measured velocity. One developer's normal
four-day capacity does not cover the upper range; Codex helps implementation
but cannot supply missing application facts or replace database evidence.

## Critical path and slices

| ID | Slice / estimate | Depends on | Concrete done condition |
| --- | --- | --- | --- |
| D00 | Repo, contracts, demo, CI, GHCR / starter | — | Gates run, image boots, published digest recorded; product still explicitly incomplete |
| D01 | Application intake + definition compiler / 6–10h | Owner input | Native upload compiles exact supported vocabulary; unknown semantics block; fixture family approved |
| D02 | Hosted identity/session/storage boundary / 5–8h | HiveForge/IdP facts | Auth, owner scoping, TLS/CSRF, expiry and leakage/isolation checks pass before real credentials |
| D03 | Lossless XML projection and patches / 8–12h | D01 | No-op exact fidelity; intended scalar/structural edits preserve unrelated spans across fixtures |
| D04 | Complete Oracle/Postgres observation / 6–10h | D01,D02 | Independent read-only/scope/identity/cleanup tests on each intended engine/storage/version |
| D05 | Profiles + all/partial composition / 5–8h | D01 | Value-free capture, immutable revisions, closure preview and explicit conflict resolution |
| D06 | Mapping, values and structural planner / 7–10h | D03,D04,D05 | One-to-two example works across CLOBs with explicit IDs, references and typed moves/creates |
| D07 | Guarded SQL writers and client qualification / 10–16h | D03,D04,D06 | Complete baseline/destination/row-count/post-state/rollback guards pass actual client fault tests |
| D08 | Integrated current/target UX / 5–9h | D01,D05,D06; can build components earlier | Operator completes workflow; multi-document XML modes and keyboard paths agree with backend |
| D09 | Readback, RST, release and HiveForge / 3–7h | D02,D07,D08 | Evidence matrix and image digest match candidate; clean-target deployment and operator rehearsal |

These ranges overlap with integration work; sum is roughly 55–90 hours, not a
fixed-price commitment. D03/D04/D07 are the critical unknowns. A second engineer
can take UX/deployment alongside the engine; do not split core semantics into
competing implementations. Agent parallelism is optional and owner-controlled.

## Daily checkpoints

**Tuesday:** confirm D01 intake, agree one representative application family,
stand up CI/demo image, fix native definition semantics and first adversarial
fixtures. Start D02 and exact no-op XML proof. By end of day, record supported
engines/clients and known identity evidence. Without these inputs, Friday's
outcome is an honest interactive prototype, not a certified export tool.

**Wednesday:** complete supported XML read/write contracts and complete
observations, then profile capture/reuse. Demonstrate real current data through
the API only after auth/privacy gates. Use synthetic data until then. Make a
single-field vertical slice pass, then extend to the required structural case.

**Thursday:** finish one-to-two mapping across documents, target values and
review. Execute actual guarded packages in disposable databases, deliberately
fail final DML and change unchanged dependencies/row membership. Do not spend
this day polishing a canvas while the transaction contract is unproven.

**Friday morning:** feature freeze for the pilot; qualification, RST and operator
rehearsal. Confirm HiveForge pulls the exact image digest, restart/expiry behavior
and rollback to the previous image. Re-run only affected gates after fixes.

**Friday afternoon:** release the supported matrix with evidence or label the
candidate incomplete and list concrete blockers. Never enable export to meet a
date. A single-engine or scalar-only candidate requires an explicit scope
agreement; it does not silently satisfy the full original requirements.

## Safe scope controls

Defer freeform diagram editing, broad XSD import, arbitrary application schemas,
advanced solver allocation, cross-DB atomic updates, multi-replica state,
collaborative approvals and automatic deployment/SQL execution. Keep whole/partial
reuse, explicit environment values, current/target XML visibility and the
one-to-two use case in the pilot acceptance list. More supported families/DB
versions are incremental qualification work after the first family.

## First task for Codex

Read `docs/agents/start-here.md`. Run starter checks and inspect current CI.
Implement D01's smallest contract compiler behavior with a failing test; do not
start by enabling export or writing a generic regex replacement engine.
