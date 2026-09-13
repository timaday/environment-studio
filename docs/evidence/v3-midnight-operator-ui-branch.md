# V3 Midnight operator UI branch — 13 September 2026

Branch `implementation/midnight-operator-ui-20260913` starts from local candidate
`56457455df46d18f7319cc1ff4f3d2a78fc89509` after the PostgreSQL 16.11 guarded
package route was locally completed. PR #10 publication of that parent remains
blocked by the current Codex session's Git push policy; this UI branch therefore
carries local parent work until it can be pushed, merged or rebased.

The applicable enterprise UX skill, `docs/ux/design-system.md`, approved Capture
states and approved Reuse states were read before UI verification work. Values and
Validation approval remains pending in `docs/ux/reference/values-validation-approval.json`,
so those visuals are not claimed implemented by this checkpoint.

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
