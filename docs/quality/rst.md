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

## Session record

- Session ID/date/tester, charter, build/tree/image digest and exact DB/client matrix.
- Setup/data references and privacy classification; timebox (usually 30–60 min).
- Checks performed, experiments and observations, including raw evidence location.
- Oracles used and how they could be wrong; significant coverage not attempted.
- Bugs, blockers, surprises and new test ideas.
- Debrief: what confidence changed, what remains unknown and next decision.

Do not copy production data into public evidence. Use sanitized reproductions.
A passed checklist does not mean every risk was explored. Stop optional testing
when the concrete risk is sufficiently investigated; expand only for new evidence.
