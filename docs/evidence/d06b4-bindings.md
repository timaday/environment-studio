# D06b4 complete value mapping — reviewed local implementation

This candidate adds `/views/bindings`, `/views/binding-locations` and stable
`[[value:<entity-handle>:<field-id>]]` document placeholders under existing live
session/revision/view-scratch/transfer authority. Field meaning stays in the
framework-free core; the XML adapter resolves exact qualified attribute spans
against a complete independent reprojection and admitted provenance. It never
searches raw values for matches or treats containment edges as reference attributes.

Bindings distinguish value, masked, absent, unresolved and unavailable states.
Masked present/present values never disclose equality. Resolved draft choices do
not claim target XML locations. Counts search every document; pages retain only
the requested bounded result. Identity references use their target's identity token;
retargeting changes that token. Source coordinates remain zero-based UTF-16,
revision/digest pinned and display-only. Raw and Formatted remain distinct views;
all three require explicit complete-document disclosure at the hosted boundary.

Labels, observed destination identity, browser binding navigation, full profile/
edit/export/readback flow and native runtime qualification remain unfinished.
Inspection/export capability flags and native registry were not enabled. This
candidate is not a new approved design, release-qualified image or published API.

## Inputs, RED and tests

Author tree `/home/tim/.tmp/es-plan-bindings-20260909` starts at `78c4860` with
[reviewed handles](d06b4-handles.md), subsequently integrated as `7c1cb4c`.
The binding candidate must be reviewed against exact `7c1cb4c`, not merged as its
whole older overlay. Only independently invented declarations and XML are used.
Contract prose preceded code; both closed route schemas and OpenAPI entries were
written before the new HTTP behavior. Pinned Maven3.9.16, Node24.20.0 and JDK21.

External logs are under `/home/tim/.tmp/`:

| Behavior | Actual RED | Actual GREEN |
| --- | --- | --- |
| Core field states/provenance | `es-plan-bindings-values-red3-20260909.log`: one assertion failure and four PROJECTION_REFUSED behavior errors | `es-plan-bindings-values-green1-20260909.log`: five new cases, 149 core total at that stage |
| Closed request readers | `es-plan-bindings-reader-red-20260909.log`: two MALFORMED_BODY behavior errors, two controls passed | `es-plan-bindings-reader-green-20260909.log`: four new reader cases plus existing controls |
| Complete XML locations | `es-plan-bindings-locations-red2-20260909.log`: two PROJECTION_REFUSED errors, one adverse control passed | `es-plan-bindings-locations-green-20260909.log`: three cases passed |
| Containment versus attributes | `es-plan-bindings-containment-red-20260909.log`: one PROJECTION_REFUSED error, three controls passed | Containment case passed in pages RED/GREEN and subsequent runs |
| Value/location pages | `es-plan-bindings-pages-red-20260909.log`: three PROJECTION_REFUSED errors | `es-plan-bindings-pages-green-20260909.log`: page and schema assertions passed |
| Stable exact placeholder output | `es-plan-bindings-placeholders-red-20260909.log`: two assertion failures, zero errors | `es-plan-bindings-placeholders-green-20260909.log`: exact full-document fixtures and missing-handle refusal passed |
| Actual hosted routes | `es-plan-bindings-http-red-20260909.log`: expected200/actual403, one assertion failure | `es-plan-bindings-http-green-20260909.log`: actual OIDC/HTTP/schema/ownership/revision/logout case passed |

Earlier field-fixture attempts had an ambiguous import and correctly refused
unpublishable UNKNOWN/unreadable declarations; those are setup failures, not RED.
The first rename fixture omitted explicit referencing decisions and correctly
failed retained-reference protection; it was corrected to select both referring
fields, not bypassed. The first large-page fixture reused projection IDs and was
correctly rejected; each document now declares a unique projection ID. These
failed setup attempts remain in separate earlier logs.

`es-plan-bindings-large-page2-20260909.log` validates an independently invented
six-document shape through the real definition compiler and XML/graph projector:
10,001 entities, exactly 50,000 reference edges, and all 50,001 locations for the
selected identity field. The final two locations and a beyond-end page preserve
complete totals and ordering. This is pagination evidence, not maximum heap or
transfer-backpressure qualification. Integer.MAX_VALUE offset also returns a
correct empty page without arithmetic overflow in bounded view tests.

`es-plan-bindings-full1-20260909.log`: **505 core/parser/server tests passed**
(150/7/348) with Maven verify. Then an extra qualified-name lookalike/extra-provenance
case, explicit post-revocation cleanup check and formatting cleanup were added.
`es-plan-bindings-adverse-20260909.log`: 14 focused tests passed on that behavior.
`es-plan-bindings-schema-20260909.log`: all 23 contract tests passed. Fresh current
unavailability, incomplete target refusal and actual materialized removal/zero
locations are covered. HTTP runs use a mock observation port, genuine OIDC/PKCE
and actual servlet sockets; they are not database or external PKI qualification.
Existing authentication/value logging and persistence canary checks remain active.

## Independent review and disclosure correction

The original fixed 32-file candidate was independently reviewed against `7c1cb4c`.
Its manifest SHA-256 was
`61facb3b867f7f67b72b5e9a7caa794a4d908637d58b66e9c1a2572fe4b24195`.
The reviewer ran 23 focused Java tests, the complete 50,001-location case and
23 schema tests. One blocker was confirmed: exact raw span endpoints disclosed
a masked field's lexical length without complete-document acknowledgement.
Suppressing only secret-field coordinates would still expose indirect lengths
through later public offsets. The independent mock witness failed one semantic
assertion with zero errors (`es-bindings-masked-length-review-20260909.log`).

Contracts and closed schema now require `completeDocumentDisclosure: true` on
all location requests, matching complete-document views. Missing/false/non-boolean
input refuses at the HTTP reader. Direct Java calls without acknowledgement refuse
DISCLOSURE_REQUIRED before scanning, for both sides and empty/beyond-end pages.
Binding values remain masked after disclosure; their complete occurrence counts
remain available without acknowledgement.

The correction is isolated at `/home/tim/.tmp/es-binding-disclosure-20260909`,
leaving the original fixed candidate unchanged. New RED ran two semantic assertion
failures with zero errors: both the reader and Java view accepted unacknowledged
coordinates (`es-binding-disclosure-red-20260909.log`). The subsequent focused
GREEN passed 26 tests (5 core, 1 parser, 20 server), including actual OIDC/HTTP
missing/false acknowledgement refusal, accepted coordinates, stale revisions and
logout during body reading (`es-binding-disclosure-green2-20260909.log`). Schema23
passed with missing/false/string acknowledgement adverse cases.

The first GREEN attempt had two failures. One new expected XML substring wrongly
assumed a plain fixture value; it was corrected to the actual independently
invented escaped/non-BMP lexical source. The other failed an unchanged credential
reinspection submission (expected200/actual400) before later authority assertions.
Its refusal code was not captured. A safe allowlisted-code diagnostic was added;
the focused rerun passed both credential submissions and the complete route case.
The intermittent credential refusal remains an investigation, not an established
fixture defect or a dismissed flake.

The original candidates' combined detached baseline `7c1cb4c` + binding32 +
reviewed native wire5 passed Java578, assembly and hostile-launch checks in
`es-bindings-wire-review-baseline-20260909.log`. Those tests do not override the
independent privacy finding. The corrected 33-file manifest (SHA-256
`087dff1fb33566c0d428b690a7ccc38893cc4b07acc4d15b890f08b7396cb040`)
was independently accepted against `4d767fb`. The reviewer ran 26 Java cases,
including the adapted original disclosure witness, and schema23; no additional
source blocker was found. Logs: `es-bindings-corrected-independent-20260909.log`
and `es-bindings-corrected-independent-schema-20260909.log`.

Five targeted guard mutants were killed by actual assertions: masked equality,
extra provenance cardinality, wrong reference identity token, missing disclosure
and missing ownership before body reading. The last case returned400 after
reading the foreign request instead of immediate404. No compile failure was
counted as a kill. Exact candidate hashes were restored before final integration.
External runner: `es-bindings-mutation-review-20260909.py`; result logs use
`es-bindings-mutant-<guard>-20260909.log`.

The corrected full integration rerun stopped in the server module: 351 tests,
one failure, zero errors. It reproduced the older credential submission defect
in `maskedDraftChoicesSurviveEditsAndViewReadersRetainAdmissionUntilDeadlineOrLogout`
and captured the allowlisted code MALFORMED_BODY (expected200/actual400).
The failure precedes binding/disclosure checks; it remains a required independent
transport investigation. The supervisor module was not reached on this run.
Log: `es-bindings-corrected-integration-20260909.log`. No full corrected pass is
claimed. The reviewed binding slice is integrated locally with this unresolved
integration defect visible; it grants no release or inspection capability.

## Remaining qualification

Final integration/artifact checks remain pending. Maximum retained heap, large response backpressure,
cleanup/recovery and user-facing navigation still require their own evidence.
The requested enterprise-ux-design skill is unavailable; its location question
remains pending. New browser designs require approved references before coding.
