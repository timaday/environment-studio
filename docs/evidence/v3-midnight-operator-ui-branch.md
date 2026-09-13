# V3 Midnight operator UI branch — 13 September 2026

Branch `implementation/midnight-operator-ui-after-pr10-20260913` is the no-force
publication branch for UI work after PR #10 merged. It rebases the local UI work
on `origin/main` merge commit `974b51332ffbf333ad47cdad1284412aa6beb459`, which
merged PostgreSQL 16.11 guarded package candidate
`56457455df46d18f7319cc1ff4f3d2a78fc89509`. The earlier remote branch
`implementation/midnight-operator-ui-20260913` remains preserved at its originally
published history; it was not force-pushed.

The applicable enterprise UX skill, `docs/ux/design-system.md`, approved Capture
states, approved Reuse states and approved Values/Validation states were read
before UI implementation and verification work. The Values/Validation approval is
recorded in `docs/ux/reference/values-validation-approval.json`; Definitions
approval remains separate.

## Combined viewport renderer settlement

The existing Capture and Reuse renderers already connect to real hosted v3 backend
operations. The first browser run in this worktree exposed a test-resource
assumption rather than a product fallback: desktop and narrow projects shared one
hosted harness, but the specs assumed an empty profile catalogue and a single
profile inspection for each project.

Observed RED for Capture:

```sh
python3 /tmp/es-run-profile-renderer.py \
  /home/tim/.tmp/es-midnight-operator-ui-20260913 profiles-v3-renderer \
  capture-renderer-20260913
```

Result: desktop PASS; narrow FAIL at the initial profile-list assertion because
the desktop project had already saved an invented draft profile in the same
harness. The harness cleaned up completely.

The Capture and Reuse renderer specs now use per-project invented profile native
IDs and assert absence/presence relative to that ID instead of assuming the shared
workspace is globally empty. The test-only hosted browser harness `/control/checks`
now accepts one or more profile observations for a combined renderer session while
retaining credential-observed, PKCE, workspace/log canary and cleanup checks.
Production code is unchanged.

A second Capture run exposed the remaining combined-session assumption:

```sh
python3 /tmp/es-run-profile-renderer.py \
  /home/tim/.tmp/es-midnight-operator-ui-20260913 profiles-v3-renderer \
  capture-renderer-20260913-green1
```

Result: desktop PASS; narrow FAIL only at `/control/checks` because two profile
inspections had occurred in the combined session. The harness cleaned up
completely.

Final Capture renderer run:

```sh
python3 /tmp/es-run-profile-renderer.py \
  /home/tim/.tmp/es-midnight-operator-ui-20260913 profiles-v3-renderer \
  capture-renderer-20260913-green2
```

Result: desktop PASS and narrow PASS, 2 tests, 7.7s. The journey maps returned
inventory, enforces duplicate/incomplete mappings, captures separately, refuses a
false save-success state after lost delivery, preserves the original pending save
across Back/reopen, retries the exact original command by keyboard, verifies
historical readback, checks axe/no horizontal overflow/44px controls and reports
owned cleanup complete. Browser log: `/tmp/es-capture-renderer-20260913-green2-browser.log`.
Harness log: `/tmp/es-capture-renderer-20260913-green2-harness.log`.

Final Reuse renderer run:

```sh
python3 /tmp/es-run-profile-renderer.py \
  /home/tim/.tmp/es-midnight-operator-ui-20260913 profiles-v3-reuse \
  reuse-renderer-20260913-redgreen1
```

Result: desktop PASS and narrow PASS, 2 tests, 8.6s. The journey performs actual
capture/save/readback setup, prepares target preview, loads catalogue/inventory,
selects a partial profile with a required dependency, disables apply until explicit
placement is complete, preserves original command identity through delivery loss,
uses explicit retry, verifies the returned plan update and checks axe/no horizontal
overflow/44px controls. Browser log: `/tmp/es-reuse-renderer-20260913-redgreen1-browser.log`.
Harness log: `/tmp/es-reuse-renderer-20260913-redgreen1-harness.log`.

## Local checks

```sh
npm ci --prefix frontend
npm run check --prefix frontend
npm test --prefix frontend
PATH=/home/tim/.tmp/es-toolchain-20260909/node-v24.20.0-linux-x64/bin:$PATH npm run build --prefix frontend
/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn -B -ntp -f backend/pom.xml test-compile dependency:build-classpath -Dmdep.outputFile=target/browser-classpath.txt -DincludeScope=test
```

Results: dependencies installed from the lockfile; Node 26.7.0 emitted an engine
warning outside the pinned Node build path. Frontend check PASS, 99 files. Vitest
PASS, 32 files and 446 tests. Schema contract tests PASS, 61 tests. Frontend
production build PASS. Backend test-compile/classpath PASS. Full backend Maven
verification belongs to the parent branch evidence and passed at 2026-09-13
06:52:02 Europe/London.

## Limits

This checkpoint verifies existing approved Capture/Reuse renderer behavior in a
local invented hosted browser environment. It is not independent non-author review,
not pixel identity, not production database qualification, not SQL execution
qualification and not GHCR/HiveForge release readiness. Values/Validation visuals
remain pending approval; Definitions approval remains separate.

## Export approval packet and package client plumbing — 13 September 2026

Desktop and narrow blocked package-candidate proposals were generated from the
approved Midnight references and recorded in
`docs/ux/reference/export-flow-approval.json`. Tim approved that view on
2026-09-13 with the correction "less is more where possible." The implemented
view therefore keeps the status cards, one package action panel, compact required
checks and an optional external-execution disclosure, trimming repeated review
copy from the generated proposal.

`HostedApi.postBinary` supports a session-owned POST that reads a binary response
while preserving CSRF, same-origin credentials, no-store request mode, 401 session
expiry, closed v3 early-refusal codes and JSON error responses.
`HostedV3Api.guardedPackageCandidate` validates the closed request
`{ revision, inputFingerprint }`, posts to the existing guarded package candidate
route and accepts only the contracted unqualified ZIP response headers:
`Cache-Control: no-store`, `Content-Type: application/zip`, the exact attachment
filename and `X-Environment-Studio-Qualified: false`. The Export view runs backend
validation for the current revision, enables the package request only when every
returned required check is PASS, starts a browser download only from that actual
binary response and never exposes Deploy, Run SQL, Execute or Commit.

Meaningful RED:

```sh
npm test --prefix frontend -- hostedV3Package.test.ts
```

Result before implementation: the new tests failed because
`HostedV3Api.guardedPackageCandidate` did not exist and the existing client had
no binary response path.

Checks after implementation:

```sh
npm test --prefix frontend -- hostedV3Package.test.ts
npm run check --prefix frontend
PATH=/home/tim/.tmp/es-toolchain-20260909/node-v24.20.0-linux-x64/bin:$PATH npm run build --prefix frontend
```

Results before screen implementation: focused frontend suite PASS, 34 Vitest
files and 453 tests plus 61 schema contract tests; frontend check PASS, 105
files; production build PASS.

Screen implementation RED/GREEN:

```sh
npm test --prefix frontend -- V3ExportJourney.test.tsx
```

Initial result: the new component tests failed after implementation because the
generated proposal's repeated readiness sentence appeared in both the status card
and package panel. The shipped view was simplified per approval feedback, leaving
the actionable reason in the package panel only. Final focused result: PASS
through the full frontend suite harness, 35 Vitest files and 455 tests plus 61
schema contract tests.

Hosted browser verification after wiring Export into the v3 operator journey:

```sh
python3 <local owned harness wrapper> \
  /home/tim/.tmp/es-midnight-operator-ui-20260913 profiles-v3-values \
  values-validation-export-20260913-green2
```

Result: desktop PASS and narrow PASS, 2 tests, 7.1s. The journey enters one
target value, validates, loads computed rules, opens Export, runs backend
readiness, observes three UNKNOWN checks, keeps Download package candidate
disabled, verifies no Run SQL control exists, checks axe, 44px controls, 320px
reflow and no horizontal overflow, then logs out and confirms cleanup complete.
Browser log: `/tmp/es-values-validation-export-20260913-green2-browser.log`.
Harness log: `/tmp/es-values-validation-export-20260913-green2-harness.log`.

Final checks after Export screen implementation:

```sh
npm test --prefix frontend
npm run check --prefix frontend
PATH=/home/tim/.tmp/es-toolchain-20260909/node-v24.20.0-linux-x64/bin:$PATH npm run build --prefix frontend
git diff --check
python3 scripts/check_repository_content.py
python3 scripts/check_repository.py
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Results: full frontend tests PASS, 35 files and 455 tests; schema contract tests
PASS, 61 tests; frontend check PASS, 108 files; production build PASS with
`dist/assets/index-DQP8UVgs.js` and `dist/assets/index-BC651ns4.css`; whitespace,
repository content, repository integrity and script unit guards PASS.

Limits: this proves the blocked Export path in the hosted browser and the binary
package client in component tests. It does not prove a successful hosted browser
package download, production PostgreSQL/client/supervisor execution, GHCR publish
or HiveForge release qualification. HiveMind/HiveMap tools were not exposed in
this session; durable status is kept in repo evidence and issue #9 instead.

### Export copy refinement — 13 September 2026

After Tim approved the Export view with ‘less is more where possible’, the view copy was tightened without changing package authority or request behavior. The operator still sees plan context, can run readiness, cannot download until every required check is PASS, and receives only an unqualified package candidate for external review. No Run SQL, Deploy, Execute or Commit action exists in the application.

Checks for this refinement:

```sh
npm test --prefix frontend -- V3ExportJourney.test.tsx
PATH=/home/tim/.tmp/es-toolchain-20260909/node-v24.20.0-linux-x64/bin:$PATH npm run build --prefix frontend
/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn -B -ntp -f backend/pom.xml -pl server process-resources
python3 /tmp/es-run-hosted-browser-values.py /home/tim/.tmp/es-midnight-operator-ui-20260913 profiles-v3-values values-validation-export-less-copy-20260913-green2 desktop
python3 /tmp/es-run-hosted-browser-values.py /home/tim/.tmp/es-midnight-operator-ui-20260913 profiles-v3-values values-validation-export-less-copy-20260913-green2 narrow
npm run check --prefix frontend
git diff --check
python3 scripts/check_repository_content.py
python3 scripts/check_repository.py
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Results: focused frontend Export tests passed through 455 Vitest tests plus 61 schema tests; production frontend build passed with `dist/assets/index-DTngD3af.js` and `dist/assets/index-BC651ns4.css`; pinned Maven resource processing passed; hosted desktop and narrow browser checks passed with `VALUES_VALIDATION_RENDERER export-blocked`, axe, 44px controls, 320px reflow, no horizontal overflow and cleanup complete. The first browser attempt used stale packaged assets and failed on the old copy string before resources were rebuilt; it is retained as RED evidence for the packaging dependency, not as product behavior. Repository content, integrity, diff and script-unit guards passed. Logs: `/tmp/es-values-validation-export-less-copy-20260913-green2-desktop-browser.log`, `/tmp/es-values-validation-export-less-copy-20260913-green2-desktop-harness.log`, `/tmp/es-values-validation-export-less-copy-20260913-green2-narrow-browser.log`, `/tmp/es-values-validation-export-less-copy-20260913-green2-narrow-harness.log`.

Limits remain unchanged: no successful hosted browser package-download e2e, no production PostgreSQL/client/supervisor execution, no GHCR/HiveForge release qualification.

## Values and Validation operator screens — 13 September 2026

This slice implements the approved Values and Validation operator states in the
Midnight shell with real backend operations only. Values loads the paged v3 target
draft after an observed inspection, shows the returned physical inventory and
required mappings, loads current/target binding comparison explicitly, submits a
real bind-field command and shows saved acknowledgement only after the backend
returns the command receipt. Validation runs the backend validation summary and
loads computed-rule pages with the returned fingerprint; export remains
unavailable when required checks are UNKNOWN.

Acceptance examples:
- Values preserves the current plan context and explains that target values are
  entered for the plan while current values remain read-only.
- The item selector and page controls come from the actual target draft page; no
  field labels, requiredness or edit eligibility are inferred from identifiers.
- Masked current values show comparison unavailable rather than changed or
  missing.
- Submitting a value uses the existing plan-command lifecycle, records the exact
  pending command during uncertainty and reports success only after the revision
  receipt is returned.
- Validation displays every returned check, keeps UNKNOWN checks blocking export
  and pages computed rules through the backend with summary/fingerprint
  correlation.
- Desktop and narrow layouts retain readable 14–16px text, at least 44px visible
  controls, natural scrolling and no horizontal page overflow at 320 CSS px.

Meaningful RED:

```sh
npm test --prefix frontend -- V3ValuesValidation.test.tsx
```

Result before implementation: the new test file failed because the
Values/Validation component and draft-loading hook did not exist, while existing
frontend tests still passed. Later hosted browser runs found real integration
issues: comparison had to be an explicit operator action, hidden mounted journeys
made unscoped assertions ambiguous and the saved acknowledgement had to survive
the plan-refresh timing after a successful command receipt.

Focused and frontend gates:

```sh
npm run check --prefix frontend
npm test --prefix frontend -- V3ValuesValidation.test.tsx
npm test --prefix frontend -- useV3TargetCommands.test.ts useV3TargetValues.test.ts V3ValuesValidation.test.tsx
npm test --prefix frontend
PATH=/home/tim/.tmp/es-toolchain-20260909/node-v24.20.0-linux-x64/bin:$PATH npm run build --prefix frontend
```

Results after implementation: check PASS, 104 files; focused tests PASS through
the full suite harness; full frontend tests PASS, 33 files and 450 Vitest tests;
schema contract tests PASS, 61 tests; production build PASS with
`dist/assets/index-Dy_yneL1.js` and `dist/assets/index-fZrgn5jm.css`.

Hosted browser verification:

```sh
python3 <local owned harness wrapper> \
  /home/tim/.tmp/es-midnight-operator-ui-20260913 profiles-v3-values \
  values-validation-renderer-20260913-green10
```

Result: desktop PASS and narrow PASS, 2 tests, 5.7s. The journey used actual
HTTPS/OIDC, target materialization, draft inventory, binding comparison, value
command submission, backend command receipt, revision readback, validation
summary and computed-rule paging in the invented hosted harness. It checked axe,
44px visible controls, no horizontal overflow and 320px reflow; owned harness
cleanup completed. Browser log:
`/tmp/es-values-validation-renderer-20260913-green10-browser.log`. Harness log:
`/tmp/es-values-validation-renderer-20260913-green10-harness.log`.

Repository guards after the slice:

```sh
git diff --check
python3 scripts/check_repository_content.py
python3 scripts/check_repository.py
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Results: whitespace check PASS; repository content PASS with provenance review
required; repository integrity PASS; script unit tests PASS, 14 tests.

Limits: this is local author implementation and verification for the approved
Values/Validation screens, not independent review, not pixel identity, not
production PostgreSQL/client qualification, not SQL export execution evidence and
not GHCR/HiveForge release readiness. The UI uses only actual backend responses
from the authorized invented test workflow and does not include production demo
fallbacks.

## Plan-inspection Placeholder mode and binding rail — 13 September 2026

The original v3 plan-inspection journey withheld Placeholder mode until a concrete
binding rail existed. This slice adds the missing client/renderer support rather
than exposing placeholders as a value-hiding display mode.

Acceptance examples:
- Raw, Placeholders and Formatted are all visible document modes after an observed
  v3 plan is resumed.
- Loading Placeholders still requires explicit complete-document disclosure.
- The document request uses `mode: "placeholders"` for current and target panes.
- The binding rail is derived from actual entity, binding and binding-location
  endpoints for the selected document. It shows each returned token, field, current
  value state, target value state, change status and selected-document location
  counts.
- Late replies are still cleared by plan/document/mode/consent/version changes.
- Narrow layout at 320 CSS px has no horizontal page overflow; long placeholder
  tokens wrap inside the rail.

Meaningful RED:

```sh
npm run check --prefix frontend
```

Result before implementation: TypeScript rejected `setMode("placeholders")` and
`bindingRail` in the new tests because the hook exposed only Raw/Formatted and no
rail state.

After the first implementation pass, the focused test also failed because the
placeholder rail needs both current and target entity pages when a target document
is available; the test fixture was corrected to supply both pages. The first
hosted narrow browser run failed the 320px reflow assertion, confirming the long
mode controls/token display needed responsive CSS. The first combined hosted run
also showed the plan harness/session checks assumed one observation and one final
session, so the e2e now logs out after its final re-login and the test-only harness
accepts one or more credentialed observations for combined desktop+narrow runs.
Production application code remains focused on read-only rendering and backend
calls; harness changes are test-only.

Focused checks:

```sh
npm exec vitest run src/hosted/useV3PlanInspection.test.ts src/hosted/V3PlanInspection.test.tsx
```

Result: PASS, 2 files, 15 tests. Finished 2026-09-13 17:33 Europe/London.

Frontend gates after the slice:

```sh
npm run check --prefix frontend
npm test --prefix frontend
PATH=/home/tim/.tmp/es-toolchain-20260909/node-v24.20.0-linux-x64/bin:$PATH npm run build --prefix frontend
```

Results: check PASS, 99 files; Vitest PASS, 32 files and 448 tests; schema
contract tests PASS, 61 tests; production frontend build PASS. Finished
2026-09-13 17:38 Europe/London.

Hosted browser verification:

```sh
python3 /tmp/es-run-hosted-browser-project.py \
  /home/tim/.tmp/es-midnight-operator-ui-20260913 plans-v3 \
  plan-placeholders-narrow-20260913-green1 narrow
python3 /tmp/es-run-hosted-browser.py \
  /home/tim/.tmp/es-midnight-operator-ui-20260913 plans-v3 \
  plan-placeholders-20260913-green4
```

Results before rebase: narrow-only PASS, 1 test, 320px reflow passed, cleanup
complete. Combined desktop+narrow PASS, 2 tests, both viewports exercised Raw,
Placeholders and Formatted over the actual hosted v3 plan-inspection workflow,
with explicit disclosure, target materialization, binding rail, axe scan, 44px
controls, no horizontal overflow and cleanup complete. Browser log:
`/tmp/es-plan-placeholders-20260913-green4-browser.log`. Harness log:
`/tmp/es-plan-placeholders-20260913-green4-harness.log`.

After rebasing onto merged `origin/main`, the same hosted browser gate was rerun:

```sh
python3 /tmp/es-run-hosted-browser.py \
  /home/tim/.tmp/es-midnight-operator-ui-20260913 plans-v3 \
  plan-placeholders-rebased-20260913
```

Result: desktop PASS and narrow PASS, 2 tests, 7.3s; 320px reflow passed and
owned harness cleanup completed. Browser log:
`/tmp/es-plan-placeholders-rebased-20260913-browser.log`. Harness log:
`/tmp/es-plan-placeholders-rebased-20260913-harness.log`.

Limit: this is functional/accessibility/reflow evidence for the implemented local
renderer, not pixel identity against the supplied XML placeholder design image and
not production database qualification. Placeholder mode remains read-only and does
not authorize export.
