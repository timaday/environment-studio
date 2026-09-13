# Explicit target-value command state

Base `3efe73df8a69819046cdee6be4174cbd154b991a`. This nonvisual prerequisite
uses the existing bind-field API; it adds no Values renderer, publication,
materialization, validation, export or database execution. The lead is the sole
writer; Reuse renderer WIP is preserved separately while its recovery approval
is pending.

Acceptance: explicit entered text, including empty text, keep-observed, absent
and unresolved stay distinct. Existing and fresh entity references remain exact.
Only an explicit submission constructs a detached command with the initiating
plan/revision and fresh request ID. One command may be outstanding. Uncertain
results retain only that original command for explicit original-session retry;
plan/presentation changes cannot replace it. Known typed refusals release it.
Owner/session retirement prevents late restoration. No automatic requests,
browser storage, logging or inferred domain labels are introduced. See the
[browser command contract](../contracts/hosted-plan-commands-v3.md).

Initial callable-empty controller red1 failed all6 acceptance cases: no command
was dispatched and no original pending command retained. Green1 passed6. The
expanded adverse run passed21 and failed3: two assertions expected bare conflict
codes despite the existing safe explanatory formatter; the third exposed a real
stale-submit callback that could dispatch after context replacement. Its fixed
presentation owner retires the original callback while allowing an already sent
command to settle under its original API owner. Session generations separately
prevent late restoration across owner retirement. The conflict assertions now
require the code within the existing safe explanation.

Green2 passes26 cases. Green4 passes28, adding retired retry-callback and detached
original-plan metadata cases. Focused3 passes57 across the26 new cases and31 unchanged
reuse-controller cases. Check2 passes TypeScript/e2e typechecking and Biome84files;
check1 rejected intentional but syntactically unused memo dependencies, corrected
by retaining explicit ownership coordinates without disabling rules. Build1
passes. Repository integrity/content checks and11 Python tests pass. External
logs use `es-target-values-{red1,adverse-red1,green2,focused3,check2,build1}-20260911.log`.

RST coverage challenges misleading completion, empty versus absent values,
mutation after submission, original-plan replay across navigation, concurrent
submit/retry/clear, safe and uncertain refusal classes, malformed/foreign Ack,
SESSION_REQUIRED, API replacement, stale callbacks and unmount. These use
independently invented transport fixtures. They prove no production connectivity,
actual value persistence, accessible Values journey or full release capability.

Independent source review found no confirmed correction on fixed three-file
snapshot manifest
`c761480727e2ea6bfae48772cc986fd736372629a61f9ff5ef3d4497f0493c7f`.
Lead accepts that nonvisual source prerequisite. Reviewer execution NONE; report
`es-target-values-review1-20260911.md` is external. The subsequent two-test-only
addendum has snapshot manifest
`4a23400677454c22c9dccd684ccdd63e2fc92f5052e739a731f3f07b9e916040`;
its bounded verification confirms unchanged hook/contract and meaningful added
coverage, with no findings. The same external report records the addendum.
Exact integrated G01/G02/G03/G08 remain required at the combined boundary.
Remote b74 results do not cover this change; no duplicate full campaign is run.
The future renderer must bind host session retirement, keep original uncertain
context visible and distinguish acknowledgement from fresh target proof.
