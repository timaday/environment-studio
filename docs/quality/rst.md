# Rapid Software Testing investigation

Use short, focused sessions with an explicit mission, test data, fallible
oracle and stopping decision. Automated checks are instruments; an RST session
is active investigation. Preserve observations, surprises and follow-up ideas.
The initial charters below are **not yet executed**.

| Charter | Mission / perturbations | Oracle and failure sought |
| --- | --- | --- |
| RST-01 mistaken destination | Same copied IDs/labels, proxy endpoints, wrong clone witness | Independent destination evidence; seek a path to change donor |
| RST-02 corners of scope | Hidden row/privilege, namespace, unchanged dependency, extra record | Declared inventory + independent DB observation; seek false completeness |
| RST-03 plausible wrong topology | Similar names, one-to-two move vs copy, partial reuse collision, missing dependency | Owner-reviewed examples; seek schema-valid but application-invalid target |
| RST-04 time/interference | Two tabs, definition update, value change after review, concurrent insert/update/delete | Frozen revisions/read-set guards; seek stale authority |
| RST-05 last failure | Fail final DML/guard, truncate file, client command error, lost commit response | Full independent post-state; seek partial committed retarget or false success |
| RST-06 leakage/lifecycle | Synthetic credential/secret markers through cancel/crash/restart/download | Storage/log/browser and physical DB-session inspection; bound evidence honestly |
| RST-07 user comprehension | Many CLOBs, filters, long names, keyboard only, identical placeholders with different values | Operator explains scope/current/target/blockers; seek hidden differences |
| RST-08 engines and encodings | Oracle NULL/empty LOB vs Postgres text; Unicode and chunk boundaries | Per-engine complete content + domain meaning; seek silent normalization |
| RST-09 hostile definition | XXE, includes, aliases, deep graphs, executable tags, ambiguous selectors | Closed contracts/resource limits; seek execution, network access or partial acceptance |
| RST-10 deployment | Private GHCR pull, wrong digest, TLS forwarding, restart, two principals, two replicas | Platform observation/ownership rules; seek leakage or false readiness |
| RST-11 feedback boundary | Invented review canaries in quoted XML, renamed schema shape, private-style paths and combined findings | Content policy + independent mock provenance review; seek model disclosure or claims that Q reasoning is executed evidence |

## Session record

- Session ID/date/tester, charter, build/tree/image digest and exact DB/client matrix.
- Independent mock setup/data references; timebox (usually 30–60 min).
- Checks performed, experiments and observations, including mock-only evidence location.
- Oracles used and how they could be wrong; significant coverage not attempted.
- Bugs, blockers, surprises and new test ideas.
- Debrief: what confidence changed, what remains unknown and next decision.

Real application sessions and raw private evidence remain in the separate
authorized workflow. Only generic findings and independently invented mock
reproductions may be shared here; renamed or redacted real structures are forbidden.
Clearly separate private observation, mock execution and reasoning-only hypotheses.
A passed checklist does not mean every risk was explored. Stop optional testing
when the concrete risk is sufficiently investigated; expand only for new evidence.
