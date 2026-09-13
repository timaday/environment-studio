# QF-0003/0004 — shared v3 profile capture

Direct and admitted-view capture now use the plan's original observation and
independent original pin. The actual adapter freshly verifies complete XML,
physical inventory, derived input/results and Existing provenance, then runs
physical-only v3 capture and bounded portable encoding. Target edits cannot
change the captured original shape. Returned source is schema3; no profile is
saved or published by capture. V2 capture and portable bytes remain unchanged.

Incomplete/duplicate/computed mappings, stale proof/revision and cancellation
refuse. The direct path retains its original work reservation; the view path
rechecks its original context after encoding. A failed reinspection can cancel
already computed view output without changing the revision. Neither path may
return a late successful capture after original authority is lost.

## Fixed review and actual checks

Base `c4bd718`; independently reviewed six-file manifest SHA
`f39154dbbd0e51695d8fa6071f15ef1db77655ec64ab9be0ba30c8b4c83e2fdc`.
Review report SHA
`c620f40da4605170b517c6ad7eb49c4a2a2be67574193b9c24c56d9678decd8c`.
The reviewer added two tests, SHA
`8a744de3a76da7d3d9cb86945fa82589f66ad6592af8f407c4547445b7425219`.
Combined seven-file manifest SHA
`14a757d84f67754c74cc4707afedaeee52a3215bdcef889b7d7becdaa89cc4ff`.

Actual pinned Maven3.9.16/JDK21.0.12 results in isolated candidates:

- Initial shared-service capture:1 assertion failure,0 errors from legacy
  unsupported dispatch,15 controls pass. First green:21 pass; adverse suite:25.
- Five author tests cover schema3 round trip, physical-only shape, identical
  portable bytes despite changed donor values, original capture after target
  edits, direct/view equality, stale pins, mapping refusal and late cancellation.
- Independent31 tests pass (14 core,1 parser,16 server), including actual v2
  profile bytes/capture controls. Equal-count duplicate/omitted physical mappings
  refuse. Held view capture across failed reinspection signals cancellation
  before close and returns no result while the lease remains live.
- Two author mutants remove original-content verification and direct final
  cancellation; one independent mutant removes final view verification. All
  compile and fail assertions with0 errors; restored suites pass.
- Combined `mvn -B -ntp -f backend/pom.xml verify`:1,224 pass
  (289 core,7 parser,664 server,264 supervisor),0 failures/errors/skips;
  hostile unrelated-directory distribution launch passes,23:20:18BST.

No setup/oracle failures occurred in this slice. Its original base was rebased
onto the corrected comparison owner before review/integration; older passing
counts do not replace the combined result above.

All fixtures are independently invented. Business: capture stays value-free and
physical-only; reuse still needs fresh target choices. Engineering/security:
source proof and live original ownership are separate requirements. QA uses
mapping bijection and held-output invalidation controls. Complete operator
capture/save/reuse, runtime publication, combined retained-proof resources and
release qualification remain open. Current v3 compiler refusal remains intact;
test publication witnesses confer no operational availability.
