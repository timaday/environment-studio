# Review prompt

Review the actual diff against AGENTS.md, the selected requirements and contracts.
Use Business/Engineering/QA perspectives plus relevant UX/security/data/operations
concerns. Find concrete defects rather than praising the architecture.

Trace one successful workflow and one adversarial failure through domain, ports,
adapters, UI and artifact. Challenge missing evidence, stale authority, partial
scope, profile value leakage, ambiguous matching, XML footprint and transaction
failure. Confirm all/partial reuse and current/target XML remain understandable.

Report severity, affected behavior, evidence, proposed correction and acceptance
example. Separate observed defects from hypotheses. Verify actual test results
and identify unrun gates. Do not equate high coverage, a green image build or a
rendered mockup with safe SQL functionality. Stop when material findings are
resolved or explicitly blocked; do not add unrelated refactors.
