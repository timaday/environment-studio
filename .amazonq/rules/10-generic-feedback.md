# Amazon Q: actionable generic feedback for GPT/Codex

Apply `00-repository-boundary.md` first. The goal is a reproducible improvement
to generic product behavior, with the real database/model remaining private.

## Review method

1. Read the public brief, approved decisions and affected repository contracts.
   Distinguish generic product requirements from private application semantics.
2. Examine only the material authorized in the current workspace. Classify
   findings as observed defects, static-analysis findings, hypotheses, missing
   context or questions. Do not infer missing semantics or invent a flaw.
3. Translate a material issue into a generic invariant, boundary failure or
   usability problem. Describe behavior rather than the private schema.
4. Design the smallest independently invented mock case for that behavior. Keep
   unrelated fields, topology and domain terminology out. If no safe independent
   case is possible, return a blocked report rather than a model-preserving copy.
5. Propose a focused correction at the proper domain/application/adapter/UI
   boundary. Preserve deterministic behavior, SOLID, current contracts and
   fail-closed handling. State alternatives or unanswered questions where needed.
6. Define positive, negative and non-interference checks appropriate to the issue.
   Label every test NOT_RUN unless it was actually executed. Model reasoning or
   a plausible example is not a validator/test result.
7. Inspect the whole response for disclosure, including cumulative relationships
   across findings. Remove private evidence and model-preserving summaries.

## Required feedback format

Use one record per independent finding and this exact field structure. Omit
unnecessary prose; do not attach source XML, reports or screenshots.

```text
Feedback ID: QF-0001
Disposition: FINDING | HYPOTHESIS | QUESTION | BLOCKED
Area: generic capability (for example XML namespaces or partial profile reuse)
Priority: HIGH | MEDIUM | LOW | UNASSESSED, with a generic impact reason
Public contract: repository requirement ID/path, or NONE_IDENTIFIED
Evidence basis: MOCK_EXECUTED | PRIVATE_OBSERVATION | STATIC_ANALYSIS | REASONING_ONLY | UNKNOWN
Generic observation: what behavior was observed or suspected; no private model detail
Expected behavior: the public invariant, or an explicitly unresolved expectation
Independent mock case: existing mock fixture reference or a newly invented minimal case; NOT_AVAILABLE if unsafe
Reproduction steps: steps against that mock only; NOT_RUN if not executed
Observed mock result: actual result, or NOT_RUN
Suggested correction: bounded generic behavior/boundary change, or INVESTIGATE
Acceptance checks: positive, adverse and preservation checks relevant to this issue
Uncertainties: missing facts and limits of the evidence
Sharing state: GENERIC_DRAFT_REQUIRES_TIM_REVIEW | GENERIC_REVIEWED_FOR_ISSUE | BLOCKED
```

Use `GENERIC_REVIEWED_FOR_ISSUE` only after Tim has reviewed and authorized the
exact generic payload under rule 20; Q cannot grant this state to its own draft.

`PRIVATE_OBSERVATION` means private evidence exists, not that the repository
maintainer has reproduced it. Do not include that evidence or a link to it.
Use a fresh mock case to make the issue independently actionable. If a public
contract does not establish the expected behavior, use QUESTION/HYPOTHESIS until
the owner clarifies it; do not label private preferences as confirmed defects.

## What makes feedback useful

- Explain the trigger, boundary, observed/suspected effect and required behavior.
- Point to exact **public repository** code or contracts when known. Never
  invent a file, line, failing test or implementation that was not inspected.
- A suggestion such as "improve XML validation" is insufficient. Specify the
  generic condition and the observable acceptance check.
- Keep a real-world observation separate from a mock reproduction. If a mock
  does not reproduce it, report that discrepancy; do not change the oracle to
  force failure or assert the bug is fixed.
- Do not prescribe hidden application cardinalities, identifiers or hierarchy
  as product defaults. Prefer "respect the uploaded declared constraint" and
  an independent mock declaration.
- Do not expand scope, add a model service, weaken an export guard, or introduce
  application-specific Java/XML/SQL constants to make a private case pass.

## Stopping states

If no issue is found, state `NO_FINDINGS_WITHIN_REVIEWED_SCOPE`, the generic
scope, evidence basis and untested areas. Do not say "the model is correct" or
"all validation passed" on the strength of an AI review.

If safe feedback is impossible, use:

```text
Disposition: BLOCKED
Generic observation: An actionable reproduction would expose private model details.
Independent mock case: NOT_AVAILABLE
Suggested correction: Tim to provide an independently invented mock case in the private review workflow.
Sharing state: BLOCKED
```

GPT/Codex should reproduce an accepted finding with mock-only tests before
changing generic code, retain relevant evidence, and send generic questions
back through Tim. It must not request the real XML/model for this repository.
