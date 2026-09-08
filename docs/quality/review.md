# Cross-functional design review

Applied perspectives during repository design; not sign-offs by human experts.

| Perspective | Gap found | Refinement / remaining gate |
| --- | --- | --- |
| Business | Scalar-only MVP would miss one-to-two restructuring | D06 retained; Friday deadline explicitly conditional |
| Engineering | Old specs hard-coded domain concepts | Closed generic definitions, generic adapters and external application declarations |
| QA | Build success could masquerade as safe export | Separate starter CI, capability evidence and release gate |
| Data | Many CLOBs and unchanged dependencies could be omitted | Complete inventory/read set and cross-document acceptance tests |
| Security | Hosted deployment changes credential/session threat model | Demo-only starter; D02 precedes DB access; transient DB credentials |
| Operations | HiveForge format/identity not supplied | Standard OCI contract + Compose example; no fictitious platform manifest |
| UX | Placeholder views could hide different current/target values | Binding rail and changed-value badges persist across all XML modes |
| Performance | Virtualized previews could hide validation scope | Complete backend model; visible filtered/global counts and limits |
| RST | Same parser/writer can confirm its own mistakes | Independent expected XML and actual-client state observations |
| Agent workflow | Later agents might silently enable incomplete capabilities | Root/nested rules, acceptance matrix, progress and non-green release evidence |
| Information boundary | Redacting values can still reveal the real database/model | Real inputs external; independent mock provenance, staged artifact checks and whole-diff review; automated semantic detection is not claimed |
| Q feedback | A private observation or plausible example could become a claimed code defect | Structured evidence basis, independent mock case and actual acceptance results; Tim checks generic handoff before transfer |

Outstanding facts are in intake.md. No algorithm can infer missing application
semantics or independent destination identity from a cloned donor label. Public
research does not substitute for those inputs. The remaining implementation
review should challenge each assumption using independent mock cases and generic
feedback from the separate private application qualification workflow.
