# Canonical product requirements

Status: agreed user direction plus explicitly labelled engineering decisions.
Later user corrections override the older Word specifications. These IDs are
traceability anchors; they are not claims of implemented functionality.

| ID | Required outcome | Acceptance evidence |
| --- | --- | --- |
| ES-01 | Deterministic Java engine; React/TypeScript UI; no runtime AI | Repeated frozen inputs produce identical plan, diagnostics and per-adapter SQL payload bytes |
| ES-02 | Inspect a complete declared scope across many XML records on Oracle/PostgreSQL | Missing/extra/denied/truncated rows and unchanged dependencies are tested on each qualified combination |
| ES-03 | Upload YAML/JSON application definitions; optional qualified XSD-to-draft import | Unknown properties/features rejected, missing semantics diagnosed, published revision immutable |
| ES-04 | No built-in application hierarchy or inferred semantics | A definition with arbitrary entity names works; an empty definition knows no server/service/webapp concepts |
| ES-05 | Save value-free profiles and reuse all or selected parts | Dependency closure preview, conflicts explicit, donor values absent, unselected objects unchanged |
| ES-06 | Model structural changes, including one instance becoming two sharing responsibilities | Explicit create/move/retain/remove decisions affect all required CLOBs; application-owned rules validate the result |
| ES-07 | Enter target environment values explicitly | Different server IDs per target slot; logical identity distinct from actual ID/DB row key; missing values block |
| ES-08 | Compare current and target across the environment and raw XML | Whole-scope overview, searchable document list, synchronized selection, Raw / Placeholders / Formatted modes |
| ES-09 | Fail closed on anomalies and missing evidence | Required FAIL/UNKNOWN/ERROR, missing checks, ambiguous identity and stale revisions all block export |
| ES-10 | Export guarded engine/client-specific SQL for external execution | Independently executed artifact proves destination, complete baseline, row counts, final state and transaction outcome |
| ES-11 | Database credentials supplied on request and never persisted | Synthetic credential tracing through success/cancel/error/expiry, logs, storage, browser and connection cleanup |
| ES-12 | Existing Flyway DDL/DML remains separate | No tool-generated migration modifies managed application configuration through Flyway |
| ES-13 | Publish a Docker/OCI application image in GHCR from CI; deploy through HiveForge | Image digest, successful boot/probes, pull credentials and actual HiveForge deployment evidence |
| ES-14 | Learnable Midnight enterprise UX, keyboard-equivalent editing | Representative operator completes reuse/map/values/review; understands destination and blockers without assistance |
| ES-15 | Fresh readback can compare an exported target manifest | Matches / Differs / Unknown separate from operator-reported execution and application health |
| ES-16 | Keep the real database/application configuration model outside this repo | No real or renamed/redacted XML, schema, mapping, topology or profile in code/docs/artifacts; independent mock provenance reviewed; separate configuration versioning out of scope |
| ES-17 | Amazon Q private review produces useful generic GitHub issues for GPT/Codex | Tim reviews/authorizes publication and triages the issue; record identifies evidence basis, public behavior, independent mock case, bounded correction and acceptance checks; linked PR has actual results, no private model detail |

## Roles and boundaries

Operators create plans and provide values. Maintainers publish definitions.
DBAs/Operations execute reviewed artifacts through the existing change process.
One person may hold several responsibilities. No new approval bureaucracy is
invented. A hosted deployment must enforce authorization and object ownership.

The Friday pilot targets one explicitly qualified application XML family and
its supported structural operations. Both Oracle CLOB and PostgreSQL text are
requirements; each advertises only individually evidenced capabilities. Native
PostgreSQL xml/OID, arbitrary schemas, cross-database atomicity, full visual
schema authoring, solver-driven optimization, multi-replica hosting and automated
SQL execution are outside the initial qualification boundary.

## Completion is not one flag

A profile can be ready with no target values. A target can be structurally valid
with missing values. A validation applies only to its frozen inputs. An exported
artifact is immutable and is not evidence of execution. Fresh matching readback
is not proof of application readiness or of which script caused the state.
