# Amazon Q → Tim → GPT/Codex feedback

Amazon Q may assist Tim's private XML/model review. Real material remains in
that separately authorized workspace. GPT/Codex receives generic findings and
independently invented mock cases, then improves the generic application.
Configuration versioning outside this code repository is not part of this work.

## Rules to load

- [Repository boundary](../../.amazonq/rules/00-repository-boundary.md)
- [Generic feedback format](../../.amazonq/rules/10-generic-feedback.md)
- [Reviewed GitHub issue publication](../../.amazonq/rules/20-github-issues.md)

AWS documents Markdown project rules in `.amazonq/rules` as project context for
Amazon Q Developer chat. Keep all three rule files in the project where Q performs
the review; copy these generic files into the authorized private review workspace
if it is separate. Check that the active Q session includes the rules before
reviewing. See [AWS project-rules documentation](https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/context-project-rules.html).

These instructions guide the model; they do not guarantee non-disclosure or
grant permission to upload private data to any provider. Tim's existing approved
review environment determines what Q may inspect. There is no Q API/SDK/runtime
integration, automatic publication or real-data download in this repository.

## Handoff

1. Tim performs the authorized private review with Q using the three rules.
2. Q produces one generic draft record per independent finding. Private raw
   evidence/intermediate drafts stay outside the code checkout and GitHub.
3. Tim checks every field and the report as a whole. Reject renamed real XML,
   model-preserving diagrams, hidden private identifiers and raw validator logs.
   If abstraction cannot preserve usefulness safely, retain the item privately
   until an independent mock case is prepared.
4. Q or Tim submits the reviewed record through the generic GitHub issue form
   after Tim authorizes publication. Tim triages it and hands the issue URL to
   Codex. It is a claim to investigate, not evidence of correctness.
5. GPT/Codex checks the public contract, creates/runs a mock-only regression,
   makes a focused generic fix if justified, and reports actual results/limits.
6. Tim can rerun the private evaluation with Q. Any follow-up crosses the same
   boundary; real XML, modelling artifacts and direct private evidence links do
   not enter the repo, even when a fix cannot otherwise be reproduced.

## GitHub issues as the work queue

Use [the generic-feedback form](../../.github/ISSUE_TEMPLATE/generic-feedback.yml).
Start with `NEEDS_TRIAGE`. Tim determines whether the public contract, mock case
and acceptance checks are sufficient for a bounded investigation or fix, then
marks `READY_FOR_CODEX` and gives Codex the issue URL. A `ready-for-codex` label is
an optional convenience; these files do not provision labels or automatic workers.

In a connected Codex cloud/GitHub setup, Tim can explicitly invoke Codex from the
issue with an `@codex` task comment. OpenAI documents issue mentions in its
[changelog](https://learn.chatgpt.com/docs/changelog#tag-codex-on-github-issues-and-prs)
and describes the [cloud integration](https://learn.chatgpt.com/docs/cloud).
Connection, repository access and task execution have not been configured or
verified by these files. The immediate alternative is to paste the issue URL
into an authorized Codex session with this instruction:

> Investigate this reviewed issue against the public contracts. Follow AGENTS.md,
> reproduce with independently invented mocks, and implement a focused fix only
> if justified. Use the parallel workflow where tasks are independent. Open a PR
> linked to the issue with actual test results and remaining uncertainties.

Q can publish using its existing approved GitHub tools after authorization; no
native Q integration or credentials are assumed. Tim can submit the same form
when Q has no publishing capability. An API submission must retain all required
feedback fields and review acknowledgement; it does not pass through form checks.

[GitHub issue forms](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms)
structure reports but are not a disclosure filter or execution permission. Repo
CI/content checks inspect files, not issue bodies/comments. Review must happen
before publication, including follow-ups and proposed attachments. A safe generic
PR/test result and any private application recheck remain separate evidence.

## Example: useful and generic

This is a newly invented review example, not an observed defect or Q execution:

```text
Feedback ID: QF-0001
Disposition: HYPOTHESIS
Area: XML namespace selection
Priority: MEDIUM — a namespace mismatch could hide required configuration
Public contract: docs/contracts/xml-and-sql.md; ES-09
Evidence basis: REASONING_ONLY
Generic observation: Check whether a selector that returns no required match is reported as incomplete rather than valid.
Expected behavior: Namespace-aware required selection must match its declared cardinality or block.
Independent mock case: Invent a mock record with root/entry elements in urn:environment-studio:mock and a mock mapping requiring one entry. Do not copy a real schema.
Reproduction steps: Run the mock mapping with its correct namespace, then with a deliberately wrong namespace. NOT_RUN.
Observed mock result: NOT_RUN
Suggested correction: INVESTIGATE; only correct generic selector/cardinality handling if a mock test exposes the issue.
Acceptance checks: Correct mapping succeeds; wrong namespace blocks; unrelated mock XML characters remain unchanged.
Uncertainties: No implementation defect has been established by this example.
Sharing state: GENERIC_DRAFT_REQUIRES_TIM_REVIEW
```

A report saying "use the attached redacted XSD" or quoting an actual table,
class, XPath, tree or validator message is not acceptable. Stripping values
does not strip the real model. Questions should request generic observable
behavior or an independent mock, never the original artifact.
