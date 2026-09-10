# V3 Plans resume and document inspection candidate

Lead application slice, base `8d87311c5f4405e2616c25db107de284e9dc1b6f` on
`implementation/v3-plan-inspection-journey-20260910`. Native implementation and
tests remain owned by IDE 2. No production Java, schema or admission change.

The existing Plans screen consumed v1/v2 routes only. The new explicit version
selector preserves that mounted journey and enables the existing v3 summary and
document clients. The [journey contract](../ux/v3-plan-inspection-journey.md)
defines acceptance and real sources. Current/Target reads require explicit
disclosure; Raw preserves returned characters and Formatted is display only.
Unknown state, confirmed absence, missing target and failed lookup are distinct.

## Behavior and independent review

Selection, mode, consent, refresh and owner changes invalidate pending reads and
clear both panes. A successful current response stays hidden until any target
read and final same-context summary succeed. Final summary comparison is
conservative; it does not establish an atomic snapshot across HTTP requests.
Backend authority remains necessary on every request. No automatic retry,
version fallback, browser persistence, save, publication or export is added.

Actual author RED: missing version selector (one failure/two passes); unobserved
physical counts incorrectly displayed as zero (one failure). Independent fixed1
review then found the selectable empty document option did not clear existing
selection. Its regression failed once, then passed after the empty selection
used the existing generation/clear path. A held-response case also verifies
that clearing cannot restore XML or start the subsequent target read.

Fixed2 non-author review verified all 17 frozen hashes and accepted the
correction with no new confirmed defect. Reviewer inspected source and evidence;
the reviewer did not execute tests. Manifest SHA-256:
`b347809eb58dc3fdb3c0316f75c9affac3f6aa29cbd9fec0d68d3f1c855e38ba`.

Author commands from `frontend`, with Node 24.20.0:

- `npm run check`: TypeScript and Biome pass, 60 files checked.
- `npm test`: 198 frontend and 59 schema tests pass.
- `npm run build`: production build passes.
- Focused hook mutations: compiled removal of disclosure, generation checking,
  final context checking and deferred pane publication each fails its intended
  behavior assertion. A separate final campaign passes 12 control tests and
  kills broadened absence handling with one intended failure. Earlier four
  mutations are not represented as a fresh campaign on fixed2.

Wrong-cwd Biome configuration failure was setup, not a behavior RED. An absence
mutation initially failed its source-marker setup and did not run; the corrected
runner compiled and killed it. Neither failure is concealed or counted as a kill.

## Actual browser scope

The test-only harness adds a closed `plans-v3` mode using existing invented
publication/observation witnesses. Actual HTTPS/OIDC, sessions, CSRF, server
routes and XML materialization run. Test setup creates and observes a plan,
then applies explicit edits through the real routes. Independent two-document
expected strings check changed scalar characters, unchanged siblings, CRLF,
entity spelling and supplementary Unicode. No production entry enables witnesses.

Separately compiled Java 21 helper classes reuse hash-verified inherited backend
classes and an absolute nonempty classpath. This is focused local evidence,
not Maven, OCI, real database/native-client or production-publication qualification.
Owned ports, private RAM workspace/browser logs and complete cleanup are checked.
The settlement endpoint only waits on the existing bounded observer; it cannot
release records, bypass capacity or authorize work. Canary coverage is finite.

Fixed2 desktop browser4 passes in 3.0s; narrow browser5 passes in 3.5s. Both verify
unchanged frontend/inherited/compiled hashes and complete cleanup. Cases include
no-plan lookup, current without target, changed/unchanged documents, disclosure
reset, exact reads, formatted display, clearing, logout and new-session absence.
Browser1 passed functionality but source changed during formatting: it is not
fixed-candidate evidence. Browser2/3 remain earlier fixed1 evidence only.

Accessibility scope: empty and populated axe scans, keyboard XML focus,
document-level reflow, no browser storage, seven empty-state controls with minimum
44px height/width, and 320px narrow reflow with required regions/actions present.
This is not universal target-size coverage, an operator study or complete WCAG
conformance. Only no-plan states are captured; populated test XML stays external.

The four existing legacy journeys pass against this combined frontend and new
harness (7.0s, all source/runtime hashes unchanged, complete cleanup).
Definitions desktop succeeds in the combined run. Its second, narrow case fails
the initial empty-workspace assertion because both projects share the first
case's durable definitions. A diagnostic rerun reproduces that exact precondition
failure at line 11; no application behavior is changed. With its own fresh owned
harness/workspace, narrow passes in 2.6s, including JSON/YAML save, replay and
readback. Hashes and cleanup pass. Future Definitions viewport assignments must
use separate fresh workspaces. The failed shared-workspace runs remain evidence;
they are not an application RED or a passing two-project gate.

## Design and remaining gates

Exact Plans desktop/narrow no-plan proposals are explicitly approved in
[the manifest](../ux/reference/plans-approval.json). Actual captures use
1440 × 1000 and 390 × 844 CSS viewports with natural full-page scrolling.
Raster dimensions, spacing, system-font wrapping, surface colors and SVG glyphs
differ; visual fidelity is **DIFFERENCES_REMAIN**, not pixel identical. The
[original Midnight references](../ux/midnight-reference-reconciliation.md)
remain the source for subsequent populated journeys. Pending Definitions renders
remain unapproved.

Actual desktop full-page capture is 1440 × 1052 pixels against the original
1505 × 1045 raster; narrow is 390 × 1616 against 853 × 1844. Dimension equality
fails for both. Direct inspection was performed; no resized or masked comparison
is reported as exact identity. The supplied populated references are a separate
state and cannot be verified by these empty captures.

Full document navigation, Placeholders with concrete binding rail, profile
capture/reuse, structural/explicit-value editing, validation and export remain
MVP work. Positive production creation still needs actual compiler/publication
qualification. Required combined G01/G08, operator rehearsal and exact release
qualification remain open; remote full/OCI ownership is preserved. This candidate
is implemented and source-reviewed, not accepted root integration or release.
