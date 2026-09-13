# V3 definition upload and inspection candidate

10 September 2026. Isolated branch `implementation/definitions-upload-design-20260910`,
comparison base `bc6dfe8b105ded5cab123e0f38a02c74edb0d21e`. Application source is
implemented and fixed non-author source review accepts both corrections.
Rendered-exception approval is pending. Root integration, G01/G08 and release qualification are separate.
Native IDE2 work is excluded.

## Behavior and acceptance

The hosted workspace preserves v2 and explicitly selects v3. Upload opens the
real file picker and reads JSON/YAML into the editor; it does not save. Extension,
1 MiB and strict UTF-8 checks precede acceptance. BOM, Unicode and line endings
remain exact in the submitted source. Unsaved replacement requires confirmation;
cancelled, failed and superseded reads preserve newer state. Pending commands
lock conflicting actions and version changes. Save uses the existing detached
destination/request/body, including explicit replay after uncertain outcomes.

Model, Source and Diagnostics inspect the acknowledged revision, separately from
the editor and command errors. Complete declarations remain available. Dispatch
invalidates stale inventory; a failed refresh cannot turn uncertainty into an
empty-workspace claim. Duplicate diagnostics retain their order and multiplicity
with distinct occurrence keys. Compilation/publication authority is unchanged;
the current compiler remains incomplete and saving does not permit plan use.

## Actual author evidence

External records use the prefix `/home/tim/.tmp/es-definitions-` and date suffix
`-20260910`. Source is independently invented `fixtures/native-v3` material.
No private application model, credential-bearing browser report or runtime
workspace is copied into the checkout/build context.

| Check | Actual result and scope |
| --- | --- |
| UI RED/GREEN | Missing version selector reproduced in `ui-red2`; aborted reader remained locked in `upload-adverse-red`; both corrected. Initial wrong-directory append was setup failure, not RED. |
| Independent finding 1 | Fixed1 review found stale empty inventory. Acknowledged-save/failed-list and uncertain-save assertions failed before dispatch invalidation, then passed. |
| Independent finding 2 | Fixed2 source review identified duplicate keys. Actual `[A,A]` to `[B]` revision transition left one A; `ui-duplicate-red` fails that assertion, `ui-duplicate-green` passes all five view tests after occurrence keys. |
| G02 | `ui-tests3` passes184 frontend/59 schema; `ui-check9` checks53 files with no fixes; `ui-build9` passes. Repository integrity and11 Python tests pass. Earlier183-test evidence predates the duplicate regression. |
| Guard calibration | `ui-mutations2`: three independently compiled size-before-read/strict-UTF8/BOM mutants killed. `ui-mutations4`: passing27-test control and five compiled stale-read/abort/inventory/pending-upload/version-lock mutants killed. |
| Oracle repair | Stale-read mutant initially survived because a cross-realm synthetic ArrayBuffer was rejected before the write. Positive current-reader calibration failed; a same-realm fixture repairs the oracle and the mutant now fails edit/new/version cases. Compilation failures and this survivor remain recorded. |
| Actual v3 browser | `ui-browser12` narrow and `ui-browser13` desktop use HTTPS/OIDC and a private schema3 SQLite workspace. Visible picker, JSON and YAML save, exact source/current readback, post-success403 response loss and exact replay, logout/relogin and retained revision pass. YAML uses a document marker plus invented JSON flow syntax; it is not valid JSON. |
| Accessibility | Empty and saved-state axe, keyboard tab selection/focus,44px initial controls, desktop/narrow reflow and320px empty reflow pass. This is bounded Chromium evidence, not full assistive-technology or WCAG certification. |
| V2 compatibility | `ui-legacy1` passes all four existing desktop/narrow hosted journeys in7.0s, including save/publication replay, inspection, role and plan checks. Later duplicate-key correction only changes the new v3 helper. |

The browser runs reuse1583 hash-verified inherited backend classes plus the
separately compiled, unchanged test harness from the prerequisite candidate.
Absolute nonempty classpath, before/after inherited/compiled/frontend hashes and
owned harness cleanup pass. This is local development proof, not a fresh Maven,
OCI, actual database-client or server-revocation-race qualification. Browser13
also hashes Playwright configuration/manifests; earlier runs did not. Raw browser
assertion logs remain in temporary RAM and are removed by the runner.

Browser attempts1–3 exposed duplicate accessible Upload controls; the attempted
patch in3 had not applied.4 passed functionality but exposed stylesheet ordering
and layout differences;5/6 failed selected-navigation contrast at4.48:1. Corrected
CSS loading and contrast pass subsequent checks. Earlier failures are not erased.

## Design and RST assessment

The exact corrected desktop/narrow v2 images and hashes remain in
[the approval manifest](../ux/reference/definitions-approval.json). The user's
continue response approved those upload layouts. Original v1 omissions remain
explicitly superseded. No reference is replaced by an application screenshot.

Rendered comparison currently has **DIFFERENCES_REMAIN**: system-font wrapping,
natural scrolling, retained SVG branding, contrast-adjusted selected navigation
and the existing session-expiry footer. The readable14–16px text/44px controls
and scrolling requirements are already in the image packet; the concrete residual
captures from browser9/10 have been presented for bounded approval. Desktop card
is1130px wide at1440 CSS pixels; narrow stacks every action at390 and reflows320.
The generated raster and browser have different dimensions/rendering; no pixel
identity or numerical similarity threshold is claimed.

Lead heuristic RST challenged misleading success, revision/list disagreement,
lost-response recovery, delayed file completion, encoding preservation and stale
diagnostic display. Independent review and the mutation survivor changed the
test oracles and implementation. Browser textarea display normalizes CRLF, while
untouched state, save payload and saved-source inspection retain exact CRLF;
the first view test incorrectly conflated those surfaces and was corrected.
Business outcome is actual definition ingestion and inspection; engineering
retains typed server authority; QA exposes recovery rather than treating a build
as a completed journey. No operator study or private-model test is claimed.

Fixed3 source review verifies all16 source/design hashes and the three-file
correction delta, with no remaining confirmed finding and no reviewer execution.
Manifest SHA-256 is `1dc61dd1aa6f387962088d17c8f3eaed5cdde91e4068354c35a05d840b986809`;
the full external report is `es-definitions-ui-fixed3-review-20260910.md`.

## Remaining gates

The remote machine retains issue9 assignments for41c8/e338/5d/bc6d. No newer
acknowledgement/result was visible after5623788071 at this boundary; no duplicate
full/OCI campaign is started. Independent source review does not replace required
combined integration gates. Publication, hosted export, installed native clients,
whole-resource qualification and operator/HiveForge rehearsal remain open.
