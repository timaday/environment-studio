# Capture persistence browser prerequisite — 10 September 2026

This test-only slice starts at `de0ce255c42177921c21a74df9c51029d4c1ed1b`.
It connects the existing real browser session, HTTP capture and profile draft
routes to a matching historical definition in the actual schema3 SQLite store.
The existing independently invented V3WorkflowStorageFixtures compiler/storage
fixture and V3WorkflowHttpTestConfiguration observation witness are reused.
An in-memory publication witness alone cannot satisfy actual profile persistence.
No production endpoint, source behavior, schema, readiness or rendered view changes.

Acceptance: actual OIDC/PKCE login; original physical entity pages across two
requests; explicit complete neutral mappings; value-free capture without saving
or changing the plan; separate exact-source immutable draft save; historical read,
identical explicit replay and durability after logout/login; foreign owner404;
no browser storage or synthetic credential/source canaries in logs/workspace.
The test uses page fetch with the real session/CSRF and actual backend. It does
not intercept API routes or create a production fallback. No new profile
publication witness is installed, so saving a draft never grants reuse authority.

Resource assignment: explicit test-only `profiles-v3` harness mode uses loopback
HTTPS18445/control18446, a fresh0700 `/dev/shm` schema3 workspace, ephemeral TLS
key, bounded in-memory logs and owned shutdown. Existing harness modes retain
18443/18444 for the independent reviewer. Playwright reports remain in a separate
private RAM directory with trace/video/screenshots off. Populated content and
credentials are never copied into image proposals or persisted reports.

Actual author evidence:

- Java21 `javac --release21 -proc:none` compiles the five current test sources
  into14 isolated classes; all1,583 inherited runtime hashes are verified. The
  current production Java sources match the existing39a9009 runtime source.
  The first precheck incorrectly expected a controller overlay and stopped before
  compilation; this was a harness setup assumption, not production RED.
- Browser1 failed an incorrect oracle: creation returns an acknowledgement, not
  a definition-bearing summary. The corrected test checks an independent literal
  definition pin and the actual plan summary. Browser2 passes2.3s before the
  ASCII credential-prefix canary refinement. Neither failure changed production.
- Final browser4, after completed frontend build and with fixed source/runtime
  hashes, passes one actual HTTPS/OIDC test in2.3s. Browser3 also passed but
  overlapped a frontend build; browser4 is the stable final control. Every run
  confirms owned cleanup; no result silently substitutes for a failed run.
- Fresh frontend check3,221frontend/59schema tests1 and build2 pass. No new UI
  production source was added. This single API/persistence journey does not
  require a second viewport campaign; there is no responsive-layout claim.
- Two external separately compiled test-harness faults are detected after the
  final control. Omitting the matching SQLite definition fails the save200
  assertion with404; injecting an invented stored canary fails the final
  control/checks assertion. Both finish owned cleanup with unchanged candidate,
  frontend and inherited hashes. These calibrate fixture/monitor oracles, not
  production mutations or a production TDD correction.

External artifacts are `es-v3-profile-capture-harness2-20260910/result.json`,
`es-v3-profile-capture-browser4-20260910/result.json`,
`es-v3-profile-capture-browser-calibration-20260910.json`, and the separately
named prior runs; all are under `/home/tim/.tmp`. Commands are retained as
argument arrays in those records; no credential-bearing browser logs persisted.
Fixed non-author review verifies the frozen four-file manifest and actual author
results, with no confirmed defect and no reviewer execution. Report:
`es-v3-profile-capture-browser-fixed1-review-20260910.md`. Final evidence text
records the completed checks after that fixed-source review; the three source/
configuration hashes remain unchanged. G00/integrity/11Python/staged checks are
required before commit and publication. No production behavior changed, so the
fixture/oracle failures are not represented as production TDD results. This is an HTTP/
persistence prerequisite, not execution of the nonvisual capture hook or a
rendered capture user journey. No accessibility or visual fidelity result follows.
New/materially changed capture layouts require UX image approval; applicable
original Midnight references remain external and pending Definitions designs
remain unapproved. Production compiler publication, actual database observation,
native/client qualification, combined integration and release remain open.
