# Start a GPT/Codex build session

Open the repository in Codex. Root AGENTS.md is the canonical working agreement;
do not duplicate it into a long system prompt. Use this kickoff:

> Build the next dependency-ready Environment Studio slice from
> docs/delivery/build-plan.md. Read AGENTS.md, requirements, progress and the
> affected contracts. First run relevant baseline gates and inspect existing
> code/CI. State independent mock acceptance examples and generic unknowns. Use TDD
> for behavior changes; record actual RED/GREEN evidence. Keep the core
> framework-free, deterministic and fail-closed. Update progress/contracts and
> leave a reviewable commit with evidence. Do not enable a capability without
> its qualification or invent selectors/semantics to make tests pass.

For parallel work, use [the lead/two-writer/reviewer workflow](parallel-development.md).
Delegate only dependency-ready tasks with explicit file ownership and frozen public
contracts; the lead verifies the integrated result. Pull the repository and start
a fresh Codex session to load its project defaults. Account/client availability
and loading of the config still need to be observed locally.

## Useful context per task

Provide a slice ID, public contract versions, independently invented mock cases
and Tim-reviewed generic findings from the [Amazon Q handoff](amazon-q-feedback.md).
GPT can refine examples and review; Codex implements/runs them. Neither is runtime
authority in the shipped product. Real XML, database models, application-specific
definitions/profiles and private evidence remain external, even when redacted or
value-free. Do not request or paste them into this repository's agent context.

Application intake is a private qualification activity. Share generic behavior
questions and capability outcomes here; do not fill the intake document with
actual locators, schemas, application semantics or private evidence links.

Read the authoritative docs selectively. The task template keeps handoff concise;
new agents should not restart completed research. Use the review prompt before
claiming a slice done. If the toolchain is blocked, leave exact evidence and
continue independent work rather than pretending the tests ran.
