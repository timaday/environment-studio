# Start a GPT/Codex build session

Open the repository in Codex. Root AGENTS.md is the canonical working agreement;
do not duplicate it into a long system prompt. Use this kickoff:

> Build the next dependency-ready Environment Studio slice from
> docs/delivery/build-plan.md. Read AGENTS.md, requirements, progress and the
> affected contracts. First run relevant baseline gates and inspect existing
> code/CI. State acceptance examples and missing application facts. Use TDD
> for behavior changes; record actual RED/GREEN evidence. Keep the core
> framework-free, deterministic and fail-closed. Update progress/contracts and
> leave a reviewable commit with evidence. Do not enable a capability without
> its qualification or invent selectors/semantics to make tests pass.

## Useful context per task

Provide a slice ID, sanitized application examples, selected definition/adapter
versions and any answered intake facts. GPT can refine examples and review;
Codex implements/runs them. Neither is runtime authority in the shipped product.
Do not paste actual DB credentials or unclassified CLOBs into prompts.

Read the authoritative docs selectively. The task template keeps handoff concise;
new agents should not restart completed research. Use the review prompt before
claiming a slice done. If the toolchain is blocked, leave exact evidence and
continue independent work rather than pretending the tests ran.
