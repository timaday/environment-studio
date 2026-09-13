# Preserve definition capabilities in workspace views

Both v2 and v3 definition projections previously replaced a capability array
before reading its values. Jackson's live property entry then returned the new
empty array, so save/current/history models displayed no capabilities even when
the compiled and stored definition contained five. The correction retains the
original array before replacing its display representation. It changes neither
source nor stored typed definitions, snapshot bytes, digests or publication
eligibility.

The finding arose while implementing profile workflow tests and was independently
reproduced against c3b891a. The external probe compiled the existing independent
v3 mock, verified exact snapshot round-trip and inspected the real controller
view builder. Its v2 historical control reproduced the same omission while
preserving original stored bytes. This demonstrated projection loss, not engine
authority corruption or an authenticated HTTP test.

## Actual regression and independent review

Three new regressions exercise real compilation, SQLite history and both
controllers: ordered nonempty declarations on save/history/exact replay, a later
explicitly empty current revision, original source/snapshot preservation, and the
independent literal v2 historical snapshot. The v3 controller completes its actual
async encoding path with mock servlet readiness; these are not OIDC/socket tests.
New v3 drafts remain incomplete and no publication is invented.

The first author run used incorrect short enum names in two request fixtures and
was rejected before reaching their intended assertions. That setup failure is
retained in `es-capability-projection-lead-red1-20260910.log`; it is not the claimed
route regression RED. Correct schema spellings then produced three assertion
failures and zero errors in `es-capability-projection-lead-red2-20260910.log`, SHA256
`98ed1b4b538d4814df015a36108fcb3e31896f9c072a3e2ab157aedc2eed02d2`.
After the two codec corrections, focused verification passes34 tests:32server,
one core architecture and one parser control. Log
`es-capability-projection-lead-green1-20260910.log`, SHA256
`caecb62c3c7a31ea3be2d087d5fde9beff2c54eab229634b33e81590a43c6b97`.

The five-file candidate over c3b891a has manifest SHA256
`1be1b137ff7742f5bdfaae70ca6125c32d7ee9d992f1975fe7e39f7232ce64a0`
and patch SHA256 `9691f8445b25f391839b5210c5732bb1ef4d4df1a9ba55f34455fc4f89ffc071`.
Independent review verified those files and1020 unchanged base files, then reran
the original probe against the fixed compiled classes: both versions returned
all five capabilities and retained exact snapshots/source. No material finding
remained. Report `es-capability-correction-review-4qz0d2g0/review.md`, SHA256
`f3e0305f15263b4b804bc421b0f8b8a0f86823d31afe9551442858adf51e4bcf`.

The focused Maven command uses `-pl server -am` and selects
`NativeDefinitionCapabilitiesProjectionTest,V3NativeSnapshotCodecTest,V3WorkspaceControllerTest,NativeWorkspaceTest,IndependentV3HistoryTest,ArchitectureTest,MinimalRuntimeTest,NativeDefinitionCompilerTest`.
Actual working directory is the isolated exact-source archive
`es-capability-projection-lead-4azbvcl0`, avoiding the previously recorded IDE
compilation collision. Test fixtures use only existing independently invented
mock declarations; no private application material was introduced.

## Integrated verification

The same five reviewed files over bd30c53 pass full Maven verification in
`es-capability-integrated-clean-n7c771vi`:1582 tests (330core,7parser,930server,
315supervisor), zero failures/errors/skips, assembly and hostile-launch checks,
10 September2026 at12:09:54BST. Fresh frontend installation, checking,40 component/
57 schema tests and production build pass in that same isolated source archive.
Full log `es-capability-integrated-full1-20260910.log`, SHA256
`70dc04bbd1a6f512b573646f1d66d92ffeae0ba590bc17ad6f6f4c2fb8a5ff76`;
frontend log `es-capability-integrated-frontend-20260910.log`, SHA256
`23a075d13d4ea8ace54eac8210294e75d8f33c4525d5654fd85059f93da0de72`.

Root authoring to fixed candidate took4m34s, including the failed fixture setup,
actual RED/GREEN and contract clarification. The integrated gate itself took3m55s;
this is elapsed verification time, not a parallel speed-up. The retained c3b891a
image predates this correction. Browser, combined resource, native/client/export
and release qualification remain separate requirements; this display correction
grants no new authority.
