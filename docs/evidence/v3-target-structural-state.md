# Explicit structural command state

Base `4d54f46251188be98acfdc52651214a3a8ca7f6e`. The shared target-command
controller now owns the existing immutable original-plan submission/retry lifecycle.
The value controller remains a narrow bind-field adapter. Structural inputs carry
explicit create/retain/remove, batch, replacement draft, forget, reference,
containment and discard intent; no placement, value or sibling change is inferred.
Profile composition is excluded and retains its reviewed preview/pin controller.
No structural renderer, XML writer, execution, publication or release authority
is added. See the [command contract](../contracts/hosted-plan-commands-v3.md).

## Actual process and checks

A prototype extraction was drafted before the first structural baseline run.
This is a chronology deviation from the required test-first workflow, not a
claimed strict RED-first implementation. The prototype was preserved externally;
a callable adapter over the actual committed field-only controller then failed
9structural acceptance cases (no structural command dispatch), while refusing
composition as expected. Restoring the shared implementation passed38tests:
10structural and28unchanged value/lifetime cases. The baseline is meaningful
missing-behavior evidence, but does not rewrite the actual implementation order.

Green2 passes64 affected cases:15structural,28value and21binding review. Structural
cases cover exact payloads for9explicit operations, forbidden composition,
caller metadata overridden by original ownership, detached nested structural
intent during uncertain replay, and malformed batch/reference/placement refusal
before dispatch. Existing values tests verify all four field states, original
owner retry, presentation/API/session/unmount retirement and overlap prevention
through the extracted lifecycle. Check1 caught an invalid negative-test cast;
check2 caught formatting, both corrected without changing the assertions or rules.
Check3 passes TypeScript/e2e checking and Biome88files. Build1 passes.
External logs: `es-target-structural-{baseline-red1,green1,green2,check3,build1}-20260911.log`.

Independent fixed source review verifies four-file manifest
`91c5fccea326ce1eefaf768eafcf43a7d7589ce183ef0766081944a39a2760ac`; no confirmed
finding. It confirms lifecycle preservation, explicit typed payloads and pinned
metadata. Lead accepts the bounded source review, preserving the process deviation.
Report external `es-target-structural-review2-20260911.md`; reviewer execution NONE.
The original draft and failed checks remain preserved; no full TDD/gate compliance
or integrated acceptance is inferred from this source disposition.

The tests use independently invented intent only and do not qualify actual
structural persistence, supported XML changes or a usable operator journey.
Future renderers require applicable image approval, explicit destructive intent,
one target-edit owner and actual integration verification. Combined G01/G02/G03/
G08 and release/resource gates remain outstanding; remote b74 results exclude
this source. Reuse WIP stays preserved separately while its four image approvals
remain pending. No native admission or scope reduction accompanies the change.
