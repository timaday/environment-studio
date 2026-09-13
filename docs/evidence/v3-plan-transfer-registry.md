# V3 transfer ownership and closed replies

The shared plan runtime now owns a bounded v3 HTTP transfer registry. Metadata
uses the existing four slots shared with v1; credential HTTP records have a separate
four-record limit and create no additional database permits. Cancellation and
uncertain completion retain original ownership and capacity. Conclusive closure
notifies session cleanup outside the registry lock. Both service and registry
cleanup are attempted even when one refuses.

Closed acknowledgement, status and summary adapters preserve exact revision
strings, original v3 ownership and separate physical/computed counts. Missing
computed evidence remains distinct from complete empty results. Summary verification
checks the full captured state; status verification does not poll cleanup or expire
reservations. Export remains false. These are internal prerequisites: no v3 plan
HTTP route, compiler qualification or browser availability is enabled here.
See [registry](../contracts/plan-transfers-v3.md) and
[replies](../contracts/plan-replies-v3.md).

## Reproduction and independent review

All principals, DTOs and publication witnesses are independently invented. Actual
commands use Maven3.9.16/JDK21.0.12 on10 September2026:

- Registry RED: four cases, two assertion failures, zero errors/skips against
  the admission scaffold. Fixed four-file manifest
  `51a3b3e0b642095ce78d8efb149ea3992be4cf405e55351eda12d47c8d63aa04`
  passes23 focused cases, including12 new controls. An expanded test incorrectly
  expected a retained reader's live flag to change under its still-live fixture
  authority; the test was corrected without changing production behavior.
  Four compiled guard mutations each fail one assertion/zero errors. Removing
  the settled guard survives the initially selected synthetic-session test.
- Independent registry review adds three actual session-ledger controls: original
  uncertainty quarantines only its owner; old callbacks cannot affect a replacement
  generation; a completed record cannot revoke its still-live original session.
  Focused/restored17 pass. Removing quarantine or the settled guard each fails one
  assertion/zero errors. The latter closes the original survivor's coverage gap;
  it does not retroactively make the author's initial test a kill. Review SHA
  `81f714fa5b45fab420157d7a248d2995162ae3ed4de0c5c0520506a772d2c31b`.
  The terminal metadata test restores only its exact synthetic slot through test
  reflection after assertions; the uncertain record remains retained. This is
  fixture cleanup, not a production recovery mechanism.
- Replies RED: six cases, four assertion failures/zero errors,02:09:27BST.
  Fixed three-file manifest
  `948f9634aa6a3dcbc182a74821e376b8b499e73d55c03003afc50b7ad91fed18`
  passes22 focused cases. Three compiled numeric-revision, status-polling and
  incomplete-summary-verification mutations each fail one assertion/zero errors;
  exact restoration passes six. Independent review adds literal UTF-8 observed
  context/export-false and retained v3 metadata across explicit v2 replacement
  controls. Focused/restored eight pass; export-forwarding mutation fails one
  assertion/zero errors. A missing-discard setup correctly refused CAPACITY and
  was corrected in the test only. Review SHA
  `f491b0972b8d9e781e924bac12c1d19544e69360a8dc58c9cad90e1651b3a8d6`.

Focused commands use `mvn -B -ntp -f backend/pom.xml -pl server -am
-Dtest=ArchitectureTest,MinimalRuntimeTest,<selected-tests> test`. Selected registry
tests are V3PlanTransfersTest,V3PlanTransferCompositionTest plus existing cleanup
controls; independent review selects IndependentV3TransferOwnershipTest and both
new author classes. Reply selection is V3PlanReplyTest,PlanV1VersionBoundaryTest,
PlanEncodingAuthorityTest,PlanViewEncodingTest; independent review selects
V3PlanReplyTest,IndependentV3PlanReplyTest. Exact restored hashes were verified.

## Combined verification and limits

Fixed ten-file manifest overb2a5ba1:
`e5516c7e40e6456c3f0681417017623967a53ea10cbb43d9155dd82c3f25e200`.
It includes all five independent added cases. Full `mvn -B -ntp -f backend/pom.xml
verify` passes1,448 tests:324 core,7 parser,807 server,310 supervisor; zero failures,
errors or skips; assembly and hostile-environment launcher pass,02:23:10BST.
Full log SHA
`d2bb73074a8f1ca6680558da706a0ed8087a882f6430910f28388278ddd79ca1`.

Lead review covers the complete combined source and independently invented fixture
provenance. Product review preserves missing/uncertain states and export refusal;
engineering review checks one runtime owner, shared bounds and monotonic settlement;
QA review exercises original/stale sessions, cancellation, held capacity and cleanup
recovery. Direct notification schedules are distinguished from actual HTTP events.
Integration and review overlapped new transport work; no measured speed-up is claimed.

Actual HTTP worker/resource closure still needs wiring and verification. Current
compiler publication remains incomplete. Frontend40/schema42 remain scoped to8b5843d;
retained image047d1b0 predates these changes. Native client/export/readback, combined
resource maxima, current browser/image/remote CI and HiveForge evidence remain open.
No Q publication, GitHub upload or release qualification is claimed.
