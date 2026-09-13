# Values and Validation browser API sequence

The existing profile-capture browser workflow now exercises explicit target
retention, bind-field submission/replay, bindings readback and complete validation
paging through actual authenticated HTTPS. This is a test of existing backend/API
behavior, not a new Values renderer or an independent review.

Acceptance verified on 11 September 2026:

- Binding an unselected item returns422 and leaves revision2 unchanged, including
  after materialization. Explicit retention establishes revision3 and unresolved
  tone; explicit beta submission establishes revision4. Exact replay returns the
  same acknowledgement.
- Readback keeps current alpha and returns target beta. Current inventory and
  both unselected siblings remain unchanged. Protected values remain masked,
  with comparison unresolved rather than an invented equality assertion.
- Complete validation remains export-unavailable with seven PASS and three
  UNKNOWN checks. Two one-row pages return both expected co-occurrence outcomes;
  an earlier validation fingerprint is refused409 without changing the plan.
- Captured durable profile source remains unchanged after target editing.
  Original session/isolation/storage canary assertions and owned cleanup pass.

Desktop:1 passed in3.5s. Narrow:1 passed in3.4s. Actual runner/results:
`/home/tim/.tmp/es-values-validation-browser-20260911/{run.py,result.json}`.
The Java runtime is the freshly compiled, verified505f815 source tree; test source
hash and its84e5656 base are recorded separately. Reports remain in private RAM;
credentials, session responses and raw documents are not captured as artifacts.
TypeScript, e2e type checking and Biome pass. Initial type-check correction used
complete typed entity-reference comparison, preserving existing/fresh identity.

No production behavior changed in this coverage addition, so no RED production
failure is claimed. Lead self-review checked independent expected values, positive
readback and stale/unselected refusals. The pre-render test does not establish UI
interaction, visual fidelity, accessibility, actual database observation,
production compiler publication or client qualification. Those gates remain open.
