# Many-document comparison UI candidate

Date: 14 September 2026. Base: `759d395d72a94b1febc2b436dd08896778b3daf6`.
Scope: hosted Native v3 comparison presentation and local navigation. No backend,
workspace publication, export authority or native-supervisor behavior changes.

## Behavior and visual reference

The supplied approved Midnight XML comparison image governs the document navigator,
Current/Target columns, three display modes and selected concrete mapping below
those columns. The other four supplied workflow references were inspected for
consistent context, density and field treatment. This candidate preserves the
existing published-definition prerequisite and target/values/validation/export
operations; it does not introduce an alternate workflow.

The returned complete inventory now has document-identity search, explicit
Changed/Unchanged/Unknown filters, filtered and global totals, and previous/next
changed-document navigation within the filter. Filtering preserves the selected
comparison and says when the selection is outside the filter. Navigation does not
load XML or grant disclosure. Clear document selection uses the existing hook to
clear both panes and disclosure. No document inventory is invented when evidence
is unavailable.

The comparison uses a bounded document list, Current/Target panes and a selected
mapping rail below the XML. The selected mapping keeps concrete current/target
values, status and selected-document location counts visible. Raw remains exact;
placeholder/formatting remain projections. The hosted content area uses more of
the available desktop width and the panes stack at narrower widths.

All product data still comes from existing typed server responses. No new API,
fixture fallback, persisted browser state, fake publication or success state was
added. The acceptance tests use independently invented `mock-*` records only.

## Actual checks

- `npm ci --prefix frontend`: passed, 134 packages, audit reported no vulnerabilities.
- RED: from `frontend`, `npx vitest run src/hosted/V3PlanInspection.test.tsx`:
  two new navigation/search tests failed because the controls were absent; five
  existing tests passed. An earlier run from the repo root failed due to missing
  jsdom configuration and is not the behavior RED evidence.
- GREEN focused test: eight tests passed, including the two RED examples and
  concrete mapping selection. Search example uses 120 documents and preserves
  the full 60-change total while showing one search result.
- `npm run check --prefix frontend`: passed, TypeScript and Biome, no findings.
- `npm test --prefix frontend`: passed, 37 files / 468 frontend tests and 61 schema tests.
- `npm run build --prefix frontend`: passed; production assets built.
- Isolated real Chromium harness: 1543, 900, 390 and 320 CSS-pixel widths, DPR 1;
  120 documents, search, changed navigation, explicit disclosure/load/clear,
  exact CRLF and a long raw XML line, focused keyboard-scrollable panes and
  placeholder concrete values all passed. No horizontal page overflow and zero
  axe violations at each tested width. This is component rendering evidence,
  not browser-to-database qualification or a claim of full accessibility conformance.

The component harness, scripts, raw captures and test logs are outside the
checkout at `/home/tim/.tmp/es-ui-clob-browser-20260914`. They are explicitly
labelled independent mock browser fixtures and are not product routes or approval
images. The browser plugin reported no available browser, so local Playwright
Chromium ran the isolated checks.

The existing service at `http://127.0.0.1:18181` returned HTTP 200 and hosted
capabilities. A read-only attempt with the candidate frontend assets could not
reach the authenticated plan view: the fresh browser had no Basic local-operator
credentials. No credentials were read, copied or written. The existing full
PostgreSQL journey was not rerun for this candidate.

## Review and limits

Self-review: filtering and page layout do not change backend scope or disclosure;
unknown never becomes unchanged; mapping selection does not fetch or alter XML;
long raw lines remain inside bounded keyboard-scrollable panes. Independent review
and integrated verification remain the lead's responsibility.

Visual fidelity status: **DIFFERENCES_REMAIN**. The component captures were visually
inspected against the supplied reference, with the navigator, pane hierarchy,
Midnight controls and selected binding arrangement implemented. They are different
content/states and do not establish pixel identity or approval of every remaining
spacing/density difference. The full operator shell and all five populated design
states have not been qualified against the reference images by this slice.

Search covers document identities and status, not content across unopened CLOBs.
Change navigation moves between changed documents, not individual XML hunks.
The existing binding hook exposes selected-document location counts; this slice
does not add full cross-document mapped-location paths or synchronized XML scrolling.
Those capabilities need follow-up through the existing qualified document/location
contracts. PostgreSQL text is the current pilot target; this UI change does not
qualify Oracle CLOB access or production native-supervisor admission.
