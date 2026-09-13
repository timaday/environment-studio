# Versioned definition workspace journey

Design/implementation candidate based on e338ec9950693ec831836a46b6f983af705fc6bf.
Use the exact enterprise UX skill supplied in user commit
0e2df3ee609580513802e547aa251d45307c8be0; the candidate copies its seven skill files
and AGENTS instruction unchanged. Existing Midnight tokens, semantic shell and
v2 functionality remain applicable. New version selection and first-use layout
use the approved corrected upload images recorded below.

## Bounded journey and real behavior

| Journey | Authority and completion | Recovery |
| --- | --- | --- |
| Select model version | Explicit Native v2/Native v3 selection; exact matching routes; preserve existing v2 use | No fallback or schema upgrade on v3 unavailable; cannot switch while a mutation is pending/uncertain |
| List/load v3 definitions | Existing typed HostedV3Definitions list/current methods under original HostedApi session | Loading/empty/error separate; late superseded replies cannot replace current choice |
| Upload a definition file | Visible Upload definition action accepts one JSON/YAML file up to 1 MiB; strict UTF-8 decoding loads exact source into the editor without saving or publishing | Cancel/read/encoding/size failure preserves the existing editor; no file path is sent or stored; pending save/replay locks file selection and stale reads cannot replace newer edits or another session |
| Create/save v3 draft | Empty source initially; exact JSON/YAML text, detached destination/request ID/body; successful save returns authoritative immutable revision | Pending disables conflicting changes; uncertain response retains exact command for explicit replay; only a closed pre-commit refusal preserves editable source without replay;403 FORBIDDEN/413 TOO_LARGE may follow commit and retain the exact command |
| Inspect saved definition | Model/Source/Diagnostics derive from acknowledged revision; original source remains separate from unsaved editor | Saved revision remains inspectable; diagnostics do not become current publication evidence |
| Explicit publication | Existing typed publish method, exact per-document policies and disclosure review; authorization and current compiler kind required | Current production compiler is Incomplete, so new publication remains blocked with explanation; historical-ready is not current authority |
| Session ends | Existing HostedApi clears original session; unmount releases editor/pending references | Late replies cannot restore source or success; fresh sign-in starts new UI state |

The first implementation slice connects file upload, listing, draft save/replay and saved source/
model/diagnostics. Plan creation, profile reuse, mapping, values, comparison,
validation/export and readback remain the complete MVP scope and subsequent journeys.
A v3 draft is not a newly available plan definition. No fake timers, persistence,
mock production port or demo fallback is permitted. Actual schema3 workspace is a
prerequisite for v3 routes; existing capabilities do not authorize migration.

File selection and source editor text are unsaved UI memory until acknowledged
by the real save API. Upload opens an accessible native file picker for .json,
.yaml or .yml (case-insensitive extension selection); format stays explicit and
editable. A file loads exact text, without normalization or reserialization, into
the current draft editor. Loading over unsaved edits requires an explicit replace
decision; cancelling preserves those edits. The filename is display-only and does
not become a definition ID, label, request ID, server path or repository artifact.
Validate byte size before reading, refuse invalid UTF-8 rather than substitute
replacement characters, and handle read failures safely. A new selection/edit,
model-version change, draft change or session end invalidates prior reads. The
Save draft command retains the established exact-source/revision/replay contract.
Server compilation remains authoritative; client file selection is not a save.
Saved definitions are metadata-workspace revisions. No database credential, raw
application XML or model is entered for design approval. Tests use independently
invented fixtures only. Approval images show a successfully loaded empty workspace,
not an unknown connection represented as empty; no names, counts or source samples.

## Visual approval packet

Requested desktop CSS viewport1440x1000/DPR1 and narrow390x844/DPR1. Actual generated
raster dimensions and exact image references are recorded in
`reference/definitions-approval.json`; the user approved both corrected layouts
by replying "continue" to their approval request on 10 September 2026. The upload
revision supersedes the earlier editor-only proposals after the user identified
the missing required action. Earlier reference files are preserved. These are approved design references,
not implemented screenshots or approvals of subsequent workflows.

First-use copy: Definitions; Model version / Native v3; Saved definition / No saved
definitions; New definition; No saved definitions for this model version;
Definition source; Upload definition; JSON or YAML · up to 1 MiB;
Upload a file or paste your source below.; Format / JSON; Source;
Save draft (disabled with empty source);
Enter a definition to save a draft; Model / Source / Diagnostics;
Save a draft to inspect its model and diagnostics. Publication explanation:
Saving a draft does not make it available for plan use. Publication requires
qualified validation. Preserve actual Environment Studio branding; generated text
wordmark is a proposal detail, not permission to replace the approved SVG asset.

Measure and record final approved controls, spacing, typography and responsive
composition before rendering. Desktop/narrow captures must use the exact approved
state, viewport and rendering conditions. Do not use generated images as runtime
screens or relabel a rendered regression baseline as the design reference.

## Acceptance and material risks

Meaningful component RED/GREEN must cover JSON/YAML file selection, exact Unicode/
line endings, oversize and malformed UTF-8 refusal, failed/cancelled selection,
explicit replacement of unsaved edits, stale file reads and file selection during
uncertain save; then exact route/version, saved revision and
source versus editor state; complete diagnostics and model declarations; explicit
same-command replay after response loss; refused publication; load/list failure,
late replies, duplicate submission and original session revocation. Keyboard tabs,
labels/focus, narrow reflow and error announcements require browser verification.
Actual browser save must survive real server readback in the schema3 workspace;
transport mocks alone cannot qualify persistence. Match design fidelity separately
from functionality and accessibility; preserve earlier evidence and limitations.

## Browser test environment

Extend the existing test-only HostedBrowserHarness with an explicit
`definitions-v3` mode. It initializes a private RAM-backed schema3 workspace,
uses actual HTTPS/OIDC/workspace composition and unchanged fail-closed compiler
qualification, and exposes no mock observation/capability override. The default
legacy harness keeps its schema2 observation tests and their credential checks.
Unknown mode is refused before resources start. Definitions-mode checks verify
actual PKCE and absence of source/token canaries; they do not claim a database
inspection. Keep output/keys in the existing RAM-backed private harness paths.
No production startup switch, migration or runtime dependency is introduced.
