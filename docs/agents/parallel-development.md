# Parallel development with one integration owner

Use the user's lead/two-coders/reviewer strategy for independent, dependency-ready
slices. The goal is shorter time to an integrated, tested result. Adding agents
does not reduce the work needed to qualify XML fidelity, transactions or actual
application correctness, and no measured speed-up is claimed yet.

## Roles and limits

| Role | Owns | Boundaries |
| --- | --- | --- |
| Lead | Task split, public contracts, file ownership, integration, CI and final evidence | One integration owner; does not ask multiple writers to change the same contract |
| Writer A | One bounded engine/adapter slice and its meaningful tests | Only assigned files; no invented application semantics |
| Writer B | One independent UI/platform slice and its meaningful tests | Only assigned files; mock UI state remains visibly mock until integrated |
| Reviewer | Actual diff, adverse behavior, evidence gaps, relevant RST and information boundary | Did not author the change; reports findings without editing it |

At most two agents write concurrently. Up to three subagents may be open under
one lead, so a reviewer can inspect a completed change while writers work on
unrelated slices. The lead must supply an immutable candidate commit/diff for
review; review is not evidence for later edits. Subagents must not spawn more
agents independently. Keep small or tightly coupled work with one agent.

## Before delegating

Use the task template to record the slice/requirement, base commit, public
contract versions, allowed files, forbidden/shared files, acceptance examples,
mock fixture origin, gates and return format. A task is ready only when its
required contracts and dependencies are sufficiently settled to implement it.
Missing private facts become generic questions through the Q workflow.

The lead owns shared schemas, API contracts, build manifests/lockfiles, root
rules and integration metadata unless a single writer is explicitly assigned
one of them. A writer needing a contract change reports the need to the lead;
it does not silently modify both sides. Disjoint filenames alone do not make
tasks independent when behavior or test oracles are still changing together.

Use distinct branches and Git worktrees for separate coding sessions. Shared
workspace subagents may edit only disjoint assigned files; they must not stage,
commit, switch branches or run concurrent dependency installs/builds into shared
output directories. Serialize those actions through the lead. Worktrees isolate
edits, not access to private data, and do not grant extra permissions.

## First useful work split for this starter

| Wave | Writer A | Writer B | Lead and review |
| --- | --- | --- | --- |
| First | D01 smallest Java compiler slice against a frozen public contract | D08 definitions/review UI components using that contract and independent mock cases | Set contracts/ownership; coordinate D02 platform questions; review and integrate each small slice |
| After prerequisites | D03 XML projection/patching after D01 | D04 JDBC observation only after D01 and D02 gates | Own observation/edit contracts; qualify each engine and integrate before planner work |
| Composition and export | One owner advances D05/D06/D07 in dependency order | Independent UI integration or test harness work against stable contracts | Prevent competing planner/writer semantics; execute integrated adverse cases |

Do not begin every delivery slice at once. Keep the current/target multi-document
workflow coherent and demonstrate a small integrated path early. One-to-two
restructuring and all/partial profile reuse remain required pilot behaviors.
Actual application qualification stays external; all repo tests use independently
invented mock database models/data. Q, GPT and every subagent follow the same
information boundary; there is no private-model exception for a reviewer.

## Completion and measurement

Each writer returns changed files, actual RED/GREEN commands/results, remaining
uncertainties and its base/candidate revision. The reviewer reports concrete
findings with evidence and distinguishes observations from hypotheses. The lead
resolves material findings, runs affected gates against the combined tree, and
runs the staged-content guard plus provenance review before any GitHub upload.
Two passing branches do not prove the integrated result passes.

For the first two integrated slices, record elapsed time to acceptance, time
blocked on contracts, integration/rework time and usage when the client exposes
it. Compare similar work cautiously; do not report a percentage saving without
a defensible baseline. Reduce concurrency when coordination/rework exceeds the
benefit. The Friday plan remains conditional on actual qualification evidence.

## Codex configuration and limits

The repo config sets Astra (`gpt-6-astra`) for the lead and as the subagent default,
with a cap of three spawned threads excluding the lead. These keys and delegation
through AGENTS.md are documented in [OpenAI's subagent guidance](https://learn.chatgpt.com/docs/agent-configuration/subagents).
This is a default and a coordination instruction, not a file-lock mechanism.

[Codex configuration guidance](https://learn.chatgpt.com/docs/config-file/config-basic)
states that project config loads for trusted projects and may be overridden by
explicit client settings. Start a fresh session after pulling, inspect the loaded
settings and use the existing trust flow if needed. Do not weaken approval or
sandbox settings. The repo config does not reconfigure an already-running hosted
ChatGPT Work session.

[The model documentation](https://learn.chatgpt.com/docs/models) lists Astra;
availability still depends on the user's client/account. A missing model or
unsupported setting must be reported, never silently presented as an Astra run.
Codex CLI is unavailable in this build workspace: TOML syntax and the documented
keys can be checked here, but local client loading and its thread cap must be
observed in the user's Codex session. No runtime provider integration is added.
