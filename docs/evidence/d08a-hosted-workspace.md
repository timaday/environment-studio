# D08a hosted browser candidate — 9 September 2026

The existing hosted definition, plan inspection and Raw/Formatted comparison task
passes on desktop and narrow browsers, including recovery after lost replies.
The fixed candidate passed independent review and was copied into the integration
tree with all source hashes verified. It does not enable inspection/export flags
or complete the profile/edit/export workflow.

## Candidate and preserved work

The continuation tree is `/home/tim/.tmp/es-d08a-continuation-20260909`, based on
`28b7a5d` plus the assigned D08a files. Only the original 21 author files were
copied from the paused tree; newer compiler, authority, schemas and fixtures were
preserved. The original tree and its 21-file manifest remain unchanged at
`/home/tim/.tmp/es-d08a-wip-20260908/files.sha256`, manifest SHA-256
`312f923e6bcf3b413add21ddc8903b6d617ce4adde1f5d4b78ebad043462ff9d`.
The review2 manifest separately freezes this continued candidate:
`/home/tim/.tmp/es-d08a-review2-20260909.sha256`, 23 files, SHA-256
`cebe745c2342de80423a3735741e08dcf0a5bbf230b430f70abfe143a805750e`.
The 22-source-file manifest excluding this evidence is
`/home/tim/.tmp/es-d08a-source2-20260909.sha256`, SHA-256
`24089f2740ba3d8ab081861dd9cb7c365fee574d7fec730795889c5f10740d90`.
Subsequent integration documentation does not change that source identity. Test inputs
come from the independently invented native-v2 family; no private model was used.

## Implemented behavior

- Server capability routing selects hosted or explicitly labelled demo views.
  Missing/malformed capability data refuses; no demo fallback impersonates a plan.
- Definitions use owned immutable revisions and explicit per-document publication
  policies. Save/publication uncertainty retains the exact method, path, body,
  request ID and expected revision. Conflicting controls remain locked until
  explicit original-command replay or a definitive refusal. Navigation retains
  the pending command; logout/expiry clears the whole hosted tree.
- Plan creation and inspection reservation retain their original commands too.
  A lost acknowledgement cannot silently generate a different plan or operation.
  Known creation followed by a failed summary read still retains recovery state.
- Inspection requires explicit consent to discard target changes on success.
  Credentials appear only after a reservation acknowledgement, leave the DOM
  immediately on submit/cancel/navigation, and are never automatically retried.
  Resumed operations use status polling, not another credential form. Polling
  recovers from transient reads without touching idle expiry. Old operation replies
  cannot overwrite a subsequent inspection; confirmed terminal cleanup permits a
  separately confirmed new reservation. Inconclusive cleanup stays visible.
- Known absolute/idle expiry clears local authority and aborts outstanding fetches.
  Late successful responses cannot reinstall a cleared session or hosted result.
  Credential-free recovery remains memory-only. Public capability/session reads
  remain available before authentication; other hosted requests require authority.
- Current/Target panes require explicit complete-document disclosure. Filtering
  clears consent and keeps the actual request aligned with the visible selection.
  Missing target is unavailable, never unchanged. A refused inventory read has an
  explicit reload. Revision changes/navigation clear displayed document content.
  Placeholders stay disabled pending the complete concrete binding/location rail.

## Actual RED and investigation evidence

All following logs are external under `/home/tim/.tmp/`; missing historical pause
logs are not used as new passing evidence. Counts below describe observed failing
examples, not invented coverage claims.

| Behavior | Actual evidence |
| --- | --- |
| Original uncertainty regression | `es-d08a-uncertainty-resume-red-20260909.log`: 1 fail, 2 pass |
| Exact save/publication replay | `es-d08a-exact-replay-red-20260909.log`: 3 fail, 2 pass; subsequent 5 pass |
| Pending command across navigation | `es-d08a-navigation-replay-red-20260909.log`: 1 fail; combined replay/navigation 6 pass |
| Required discard consent | `es-d08a-inspection-consent-red-20260909.log`: 1 fail, 1 pass; subsequent focused pass |
| Independent review reproduction | `es-d08-independent-red2-20260909.log`: wrong filtered document, missing absolute-expiry teardown and stopped polling, 3 assertion failures |
| Recovery integration | `es-d08a-recovery-red-20260909.log`: 6 failures; all 6 pass after corrections |
| Idle/late-response authority | `es-d08a-local-authority-red-20260909.log`: 3 fail, 2 pass; later all pass |
| Resume/late cancel correlation | `es-d08a-operation-race-red-20260909.log`: 2 fail, 6 pass; subsequent all 8 pass |
| Initial inventory refusal recovery | `es-d08a-inventory-retry-red-20260909.log`: 1 fail, 8 pass; subsequent all 9 pass |

Actual browser runs also exposed the missing accessible published-definition
label and absent discard consent in the reservation body. Both were corrected.
The new API guard initially blocked public capability discovery; the real browser
and a focused unit RED caught that regression before the explicit public-read
exception. A role assertion initially omitted the documented WORKSPACE_ prefix;
actual HTTP403 was correct, and only the expected safe code was corrected.
TypeScript/formatting/setup failures are recorded separately from behavior RED.

One inventory wait failed while Maven was recompiling the shared test classpath.
Its cause was not established. A stable-build rerun passed; a bounded investigation
then passed 20 repeated browser cases without reproducing it. Verification requires
completed, fixed build outputs. The subsequently added explicit inventory retry
was verified against an actual lost read response, not justified by a guessed cause.

## Current verification

- `npm run check`: TypeScript application/e2e and Biome pass; log
  `es-d08a-review2-check2-20260909.log`.
- `npm test`: 34 component tests across 9 files and 22 schema tests pass; log
  `es-d08a-review2-tests-20260909.log`. Production build passes in
  `es-d08a-review2-build-20260909.log`.
- Full `mvn -B -ntp -f backend/pom.xml verify`: 509 tests, zero failures/errors/
  skips; `es-d08a-candidate-java-20260909.log`. No production Java API was changed.
- Actual HTTPS/OIDC browser: 4 tests pass, maintainer and operator at 1440×1000
  and 390×844; `es-d08a-browser-inventory-recovery-review-20260909.log`.
  The maintainer test deliberately loses successful save, create, reservation and
  inventory replies, checks exact replay, then inspects and compares Raw/Formatted
  content. It verifies keyboard tabs, automated WCAG axe rules, no horizontal
  overflow, empty browser storage, expiry teardown and safe backend log canaries.
  It selects the exact object just published. The operator can save but receives
  actual HTTP403/WORKSPACE_FORBIDDEN on publication, then logs out and clears data.
- The mock observation port independently sees the exact non-BMP credential and
  one observation invocation. This is an encoding/one-shot witness, not JDBC proof.
- The independent reviewer verified all 23 candidate hashes, read the changes and
  reran 34 component/22 schema tests successfully in
  `es-d08-independent-review2-20260909.log`. Two additional adverse examples pass
  in `es-d08-independent-adverse-review2-20260909.log`: clearing authority during
  response-body parsing refuses the late result, and terminal INCONCLUSIVE cleanup
  keeps polling and denies restart until confirmed COMPLETE. No blocking finding
  remains against this fixed candidate.
- Existing labelled-demo keyboard/axe browser checks pass at both widths:
  `es-d08a-demo-browser-20260909.log`, 2 tests. Hosted raw content is never captured
  in screenshots; the existing demo screenshots contain invented fixture data only.
- Owned harness startup refusal at an occupied control port confirms reverse
  cleanup, removed owned RAM and closed HTTPS listener:
  `es-d08a-harness-startup-cleanup-20260909.log`. Successful browser runs also
  report MOCK_CLEANUP_COMPLETE. No shared process/container was stopped.

## Harness and reproduction boundary

`HostedBrowserHarness` uses actual HTTPS, MockIssuer OIDC/PKCE, session/workspace/
plan adapters and an explicit mock ObservationPort. Only browser inspection
capability enablement is overridden. The test certificate is independently
created in private RAM and browser contexts explicitly ignore its trust error.
This does not qualify external PKI, JDBC, native clients or a real deployment.

Register owned cleanup before startup allocations. Keytool receives its temporary
keystore password through owned stdin, never argv/environment. TLS keys and mock
SQLite live under private `/dev/shm/es-browser-mock-*`; captured backend logs are
bounded in memory. Checks cover actual source/credential, code/PKCE/token and
listener-password canaries. Definition persistence is intentional workspace
behavior; it is not an assertion that saved definition text never reaches SQLite.

Trace/screenshot/video are off. The pinned Playwright NO_COPY_PROMPT setting
suppresses failure DOM snapshots; textual failure context may still include
literal independently invented test-source constants. Hosted runs require a fresh
private `/dev/shm/es-browser-results-*` output directory, removed by the owning
runner afterward. No raw-input screenshot, screen-reader, zoom or diagnostic/core
privacy qualification is claimed by these tests.

Use pinned Node24/Maven3.9.16/JDK21. First run frontend build, then:

```sh
mvn -B -ntp -f backend/pom.xml test-compile dependency:build-classpath -Dmdep.outputFile=target/browser-classpath.txt -DincludeScope=test
```

Start `studio.environment.server.security.HostedBrowserHarness` from
`backend/server` with test/classes, server/classes, core/classes, parser/classes
and that module's generated classpath. Do not share one absolute dependency output
file across reactor modules. Wait for MOCK_HTTPS_BROWSER_READY. Run
`ES_HOSTED_BROWSER=1 ES_HOSTED_BROWSER_OUTPUT_DIR=<fresh private RAM directory>
npm run test:e2e` from frontend, then stop only the owned harness and observe cleanup.
The actual continuation wrapper is `/home/tim/.tmp/es-d08a-run-browser-20260909.py`;
its final invocation was `inventory-recovery-review all`. It refuses occupied
ports, bounds startup/run/shutdown and owns its temporary browser-output directory.

## Review scope and remaining work

Business review focuses on exact plan/document identity, explicit replacement and
credible recovery. Engineering review focuses on live server authority, command
identity, transient credentials and stale-response exclusion. QA used independent
reproductions, lost-response transport faults, real OIDC roles and both viewport
sizes. No serial baseline or speed-up measurement exists for the parallel work.

The first independent review found the corrected recovery/display defects; the
second fixed-candidate review accepted their integrated corrections. Full
profile capture/save/reuse, structural/value editing, concrete placeholder rail,
validation/review/export/readback UI and representative operator qualification
remain local work. The planned context contract is reviewed but unimplemented.
The requested enterprise-ux-design skill is unavailable; its location remains a
pending external clarification. No new design or release readiness is approved
by this evidence. Native privacy/client and maximum-heap qualification are separate
unfinished work. Push remains rejected by automatic approval policy; no new
remote PR/CI/GHCR/HiveForge result is claimed.

## Local artifact

The OCI build passed the combined Java509, frontend34/schema22 and production
build gates: `es-d08a-oci-build-20260909.log`. Image
`environment-studio:d08a-review-20260909` has local ID
`sha256:78e793e29e85516cc12ba7fdf1a7618e2f97966f341ce8d3dc14a841f1dcd0a9`
and source label `28b7a5d+d08a-24089f27`. This identifies the frozen source candidate,
not a later integration commit or a registry digest.

Protected container/workspace smoke passed in `es-d08a-oci-smoke-20260909.log`:
non-root, read-only root filesystem, no-new-privileges, owned tmpfs, health/static
UI, demo capability/mutation refusal, initializer permissions/overwrite refusal
and schema-2 offline legacy upgrade/refusal. Owned browser/harness processes and
RAM artifacts were cleaned; no production inspection/export flag was enabled.
This source slice does not change the separately built supervisor artifact.

## Development startup correction

Integration review found that the new capability request reached Vite's HTML
fallback when running the documented development command. A separate one-file
candidate adds an exact `/api` prefix proxy to fixed loopback port 18080 and
explicitly disables preview inheritance. README now starts the loopback demo
backend first. No configurable target, origin override, synthetic capability
fallback or production proxy was added.

The isolated author snapshot is `/home/tim/.tmp/es-vite-dev-l5n8z97d`; its only
authored file, `frontend/vite.config.ts`, has SHA-256
`b5a9b4bd6e10afbb280beb1f023458da9e164a4390683efeb52a76d2797343b1`.
Actual RED (`es-vite-dev-red-20260909.log`) received HTML instead of backend JSON.
Author GREEN and independent root rerun both prove exact real backend capability
bytes, unaffected `/` and `/apiary`, actual Chromium demo rendering, and HTTP502
with Workspace unavailable after backend removal. Root used Node24.20.0; logs are
`es-vite-dev-independent-20260909.log` and
`es-vite-dev-independent-unavailable-20260909.log`. Owned processes were closed.
The lead independently accepted this fixed development-only diff. It is separate
from the earlier 22-source-file OCI candidate; the recorded image does not contain
this later build-time configuration correction.

After copying the reviewed sources and proxy into the root integration tree,
TypeScript/Biome, 34 component/22 schema tests and production build pass again:
`es-d08a-integration-{check,tests,build}-20260909.log`. All 22 frozen source hashes
match; the whole staged diff contains only generic code/tool contracts and invented
test data. Repository integrity, staged-content checks, Python11 and diff whitespace
checks pass. These checks do not establish private-model provenance automatically.
