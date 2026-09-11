# Inventory callback retirement correction

INV-QA-001 accepted from issue9/5637463950 on1dc3028 and unchanged ffb83da.
Independent positive/adverse control showed a saved load callback making three
old-plan requests and restoring101 rows after invalid-inspection context change.
The affected nonvisual hook is not yet wired to a renderer; no DB write, command
or export bypass is claimed.

The read owner now belongs to API, complete plan context and presentation
lifetime. Cleanup retires the original owner permanently; its retained callbacks
cannot acquire the new owner's generation. Paging and fresh-summary checks are
unchanged. Current valid callbacks still read both pages and the final summary.

Author RED: two failures/14passes before implementation, after plan replacement
and presentation leave/reentry; each observed three forbidden requests. Log:
es-inventory-callback-red2-20260911.log. The earlier red1-named log ran unchanged
tests after a test-edit path error and is not RED behavior evidence.
GREEN:16 inventory tests pass, including retained callbacks after plan/API/session/
unmount/presentation retirement, unchanged held-read and adverse-page checks.
TypeScript initially caught an inferred test-props type missing owner; corrected
with explicit Props. Biome then required formatting; check3 passes. No timer,
timeout, production gate or assertion was weakened. Independently invented
101-item fixture is unchanged; no real application material.

Fixed non-author review and remote exact-candidate verification remain required.
No new visual design or renderer is introduced. Existing design approvals and
production-admission restrictions remain; functionality, visual fidelity, full
resource behavior and release acceptance are separate.

Non-author source review1 verified INV-QA-001 and raised INV-REVIEW-002:
untagged state could expose old rows during the first replacement render before
effect cleanup. A render-observation control confirmed it:1FAIL/16PASS in
es-inventory-render-red1-20260911.log. State now carries its owning context;
replacement renders expose empty state immediately. Final17PASS in
es-inventory-callback-green3-20260911.log and check4 PASS. Source review1 execution
was NONE; these controlled executions were performed by the author.

Fixed non-author review2 confirms both corrections with no remaining finding;
manifest0cf40c71976a82e64b5452f0f3c805d9bb21f0131cb7b5f02c4e6a1b54c3c6df,
external es-inventory-callback-source-review2-20260911.md. Reviewer execution NONE.
Remote and combined integration gates remain required.
