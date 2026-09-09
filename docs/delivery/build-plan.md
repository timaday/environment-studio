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
| D01 | Generic definition compiler + mock contracts / 6–10h | Public behavior decisions; independent mock cases | Native upload compiles exact supported vocabulary; unknown semantics block; mock family reviewed; actual application qualification remains external |
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
competing implementations.
The user has chosen [a bounded lead/two-writer/reviewer strategy](../agents/parallel-development.md).
Apply it to independent ready slices, keep shared contracts with one owner, and
measure integration/rework before revising these estimates. It does not remove
the critical path or private application qualification.

## Amended Q findings — 9 September continuation

These extend the full completion plan; they do not replace the remaining hosted,
native-client, resource, operator or deployment work. Q's original wording is
superseded by the amended task. Current direct-only rejection is consistent with
the advertised contract. Q publication to GitHub is not authorized.

| Work | Dependency and acceptance |
| --- | --- |
| QF-0001/0002 child-property fields and mechanism compatibility | [Reviewed mapping slice integrated](../evidence/qf12-child-property.md): closed declarations, shared source locators, projection/creation/moves/views, target reprojection, whole/partial reuse, historical replay and per-binding compatibility. Java754, independent guard controls and exact candidate OCI smoke pass. Combined capacity, native clients and complete hosted export remain separate open gates. |
| QF-0003/0004 computed groups and co-occurrence | Tim approved [authoritative recomputation with physical-only v3 profiles](../product/derived-graph-decision.md), PUBLIC text inputs and shared graph limits. Contracts, internal compiler, derived engine and [actual observed XML](../evidence/qf34-derived-projection.md) are reviewed. [Typed/final-target integration](../evidence/qf34-derived-target.md) now compares complete proofs after actual XML materialization; combined Java894 passes. [Physical-only profile ports](../evidence/qf34-profile-v3.md) now pass full Java912 and independent XML/inventory/byte controls. [Separate typed history](../evidence/qf34-history-v3.md) is reviewed; combined Java943 passes. [Explicit schema3 storage and definition drafts](../evidence/qf34-workspace-v3.md) are reviewed; the exact candidate container passes Java973 and protected schema3 administration. The [draft/history HTTP routes](../evidence/qf34-workspace-http-v3.md) are reviewed; combined Java1024 and frontend40/schema36 pass. The [owned profile draft command](../evidence/qf34-profile-drafts-v3.md) now passes combined Java1053 and independent persistence controls. Profile HTTP is reviewed below; complete new publication, plan integration and qualification before availability. |

The [profile HTTP draft/history routes](../evidence/qf34-profile-http-v3.md) are
now reviewed and integrated: combined Java1072, frontend40/schema40 and independent
reference, exact-revision and owner controls pass.
[Internal publication commands](../evidence/qf34-publication-v3.md) now pass
independent review and combined Java1115; the actual compiler still refuses
publication. [Internal v3 observation](../evidence/qf34-observation-v3.md) now passes
independent review, combined Java1126 and actual mock Oracle/PostgreSQL TLS and
derived-projection controls. The
[current v3 plan publication lookup](../evidence/qf34-plan-workspace-v3.md) now
passes independent review and combined Java1151. The separate
[actual both-engine workflow extension](../evidence/qf34-database-workflow-v3.md)
passes complete target/proof, physical-only capture and whole/partial reuse
checks after correcting an independently found test-oracle gap. Publication HTTP
remains required before operational qualification.
The [versioned plan model](../evidence/qf34-plan-model.md) now shares the physical
composition algorithm while retaining distinct v2/v3 metadata: independent review,
combined Java1157 and actual both-engine production-merge workflows pass. The
[complete v3 content adapter](../evidence/qf34-plan-content.md) now passes independent
review, full Java1187 and eight compiled guard mutations. The
[shared internal lifecycle](../evidence/qf34-shared-lifecycle.md) passes full Java1203
and corrected independent review: current-only v3 inspection, separately proven
targets, physical commands/pages and readable unresolved summaries. Comparison
now passes [corrected independent review](../evidence/qf34-shared-comparison.md)
and full Java1217. [Shared profile capture](../evidence/qf34-shared-capture.md)
passes independent review and combined Java1224.
[Shared profile reuse](../evidence/qf34-shared-composition.md) and the
[native launch owner](../evidence/d07c3-privacy-native-launch.md) now pass
independent review and combined Java1253. The
[shared versioned workspace adapter](../evidence/qf34-shared-workspace.md) now
passes independent review and combined Java1260. Versioned views/APIs, runtime composition
and combined retained-proof resources remain required before admission.

The lead owns shared contracts and compatibility. Independent fixed-candidate
review, actual RED/GREEN, targeted guard mutations and integrated verification
remain required. Private models and original Q inputs stay outside the repository.

The later 5fa6695 independent review's retained-origin defect is
[corrected and reviewed](../evidence/derived-retained-origin-fix.md), with actual
RED and full Java850. Preserve later XML/root changes. Finish final derived
profile/hosted paths. [Inspection capability fields](../evidence/inspection-capability-clarification.md)
now separate UI availability from configured API admission; a false UI flag is
not backend enforcement. [Exact 7111230 container and browser checks](../evidence/derived-target-artifacts.md)
pass; native/client, combined resource and deployment qualification remain open.
The [native argument prerequisite](../evidence/d07c3-privacy-arguments.md) is now
independently reviewed with current-head Java985 and additional process controls.
The [bounded file-hash prerequisite](../evidence/d07c3-privacy-hash.md) is now
independently reviewed with combined Java1036; native resource/loader qualification
remains open. The [trusted-file opening prerequisite](../evidence/d07c3-privacy-file.md)
now passes independent review, full Java1086 and nine actual guard mutants.
The [executable association prerequisite](../evidence/d07c3-privacy-image.md)
now passes independent review, full Java1100 and thirteen guard mutants. Its
retrospective RED ordering gap remains recorded. The
[script/interpreter association prerequisite](../evidence/d07c3-privacy-script.md)
now passes independent review, full Java1141 and thirteen guard mutations.
The [structural ELF64 prerequisite](../evidence/d07c3-privacy-elf.md) now passes
independent review, full Java1171 and fourteen compiled guard mutations;
structural file metadata confers no mapped-object or runtime admission.
Complete loader closure, consumed-script evidence and coordinator/JNI before
client admission. The exact9454d9f [HTTP artifact](../evidence/workspace-http-v3-artifacts.md)
passes Java1024, frontend40/schema36 and protected smoke; it predates hashing.

## Daily checkpoints

**Tuesday:** confirm D01's generic compiler contracts and an independent mock
family, stand up CI/demo image, and create the first adversarial mock fixtures.
Start D02 and exact no-op XML proof. In the separate private qualification
workflow, the owner confirms actual application semantics and destination
evidence. Record only generic capability outcomes here. Without the necessary
evidence, Friday's outcome is an interactive prototype, not a certified export tool.

**Wednesday:** complete supported XML read/write contracts and complete
observations, then profile capture/reuse using mock databases. Real current data
may be evaluated only in the separately authorized deployed/private environment
after auth/privacy gates; never bring it into the checkout or CI. Make a single-field
vertical slice pass, then extend to the required structural case. Q returns only
generic findings and independent mock cases for fixes.

**Thursday:** finish one-to-two mapping across documents, target values and
review. Execute actual guarded packages in disposable databases with invented
mock schemas/data, deliberately
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

Real configuration, schema bundles and value-free actual profiles stay external.
Creating their separate versioning repositories or sync pipelines is out of scope.
This does not remove runtime upload, save or immutable revision features.

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
