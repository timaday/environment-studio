# Handoff and capability status

Updated: 8 September 2026. See [starter evidence](../evidence/starter-verification.md)
and [D01a integration](../evidence/d01a-integration.md) for actual commands and
results; the table below reports capability, not test count.

| Slice | State | Next evidence |
| --- | --- | --- |
| D00 foundation | COMPLETE for starter: CI/build/smoke/GHCR passed | See ../evidence/starter-verification.md and image-reference.json |
| D01 definitions | Native compilation, owned v2 upload/history and maintainer publication implemented and reviewed | Browser publication workflow and external application qualification |
| D02 security/state | Hosted sessions, immutable workspace revisions and internal plan lease/operation integration reviewed | HTTP credential lifecycle; actual deployment durability/identity external |
| D03 XML | Guarded spans, graph projection, corrected Fifth Edition parser and structural target assembly integrated | Hosted orchestration and guarded SQL/client qualification |
| D04 database reads | Both engine adapters and internal plan reservations integrated; disposable matrices and shared-permit checks passed | HTTP credential transport and external TLS/account qualification |
| D05 profiles | Value-free capture, portable whole/partial composition and owned immutable persistence/publication integrated | Hosted capture/composition and browser workflow |
| D06 planning | Internal hosted plans, revisions, operation cleanup, composition and complete structural targets reviewed | HTTP/backend-to-browser workflow and full hosted privacy/heap qualification |
| D07 SQL | Package mechanisms and both unqualified transaction templates integrated/reviewed; disposable rollback/guard matrices passed | External supervisor, TLS/transcript/commit acknowledgement and hosted export authority |
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

Active work: hosted plan HTTP integration and the separate external supervisor;
subsequent inspection/composition view contracts are defined before implementation.
The Oracle FGA read-policy correction passed independent review, actual handler/
refusal controls and the combined 365-test Java build; see
[FGA integration](../evidence/d04-fga-integration.md).
The internal plan service passed independent review, the combined 357-test Java
Docker build and protected smoke, plus 58 actual both-engine shared-permit checks;
see [hosted plan integration](../evidence/d06b1-integration.md).
Both guarded templates passed independent review after a signed-int64 correction,
the combined 362-test Java Docker build and protected smoke; actual full-size
multibyte transactions and rollback witnesses passed on both disposable engines.
See [transaction integration](../evidence/d07b-integration.md). These internal
candidates do not enable export or establish supervisor/commit/TLS qualification.
The bounded package mechanism passed independent review
and the combined 317-test Java Docker build, 7 UI/17 schema tests and protected
smoke; see [package integration](../evidence/d07a-integration.md).
Full 32 MiB Oracle binary loading passed in 9.903 seconds with base64 after hex
approaches exceeded the unchanged 120-second bound; this is transport feasibility,
not writer/client qualification. See [the investigation](../evidence/d07-client-investigation.md).
Native workspace and structural target mechanisms have passed independent review
and the combined 302-test Java reactor in the isolated Docker build; see
[workspace/target integration](../evidence/d01c-d06a-integration.md).
D05a's earlier 233-test integration is recorded in
[profile integration](../evidence/d05a-integration.md).
D04's 250-test integration and actual two-engine observation matrix are recorded
in [observation integration](../evidence/d04a-integration.md).
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
