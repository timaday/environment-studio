# Resume and inspect a v3 plan

Bounded next application journey after `8d87311c5f4405e2616c25db107de284e9dc1b6f`.
Existing Plans rendering uses v1/v2; the reviewed v3 clients and document routes
are not yet consumed by React. Preserve that legacy journey. No new backend
route, schema, publication witness or runtime availability flag is proposed.

## Function and authority

| Action/state | Existing authority | Intended UI behavior |
| --- | --- | --- |
| Select Native v3 | Explicit version; `HostedV3Api.current()` | Query only v3. Loading, safe404 absence and failure differ; never switch to v2 after failure. |
| Resume/refresh | Actual current/explicit summary | Retain exact plan ID/revision, definition/binding/destination and observation/target status. Counts do not authorize export. |
| List documents | `HostedV3Physical.documents` with exact revision | Show complete inventory in returned order, with current/target availability. No invented document/model labels or inferred application vocabulary. |
| Inspect a document | Explicit document, side and mode; full-document disclosure | POST only after acknowledgement. Show authoritative exact/redacted/omission flags; unknown or absent target is not empty XML. |
| Raw/Formatted | Existing modes and controlled response | Raw exact characters; Formatted display only. Neither mutates source or writer input. |
| Plan/revision/session changes | Existing session owner plus local request identity | Invalidate stale selection/results; late replies cannot replace another plan/revision/document/side/mode or restore cleared source. No browser storage. |

Current and target remain explicitly labelled in desktop columns and stacked
narrow sections. The first approval images use the genuine confirmed-no-current-
v3-plan state: no IDs, counts, XML, model records or fictitious observation.
Resume is a real retry of the current lookup; Definitions opens the existing
workspace. Disabled document controls explain the missing plan/observation.
No actual XML may appear in design images. Populated behavior tests may use
independently invented fixtures under the repository policy.

Placeholders require the complete concrete binding rail and are a following
slice; do not expose a mode that hides actual changed values. Profile capture/
reuse, target changes/values, validation/review/export and readback remain the
agreed MVP scope. This slice establishes their missing v3 plan/context entry.

## Acceptance and material risks

Observe RED for version-isolated current lookup and no404-to-v2 fallback;
unknown versus absence; exact summary/document revision; late reply after plan,
document, mode or session change; no disclosure request before explicit consent;
Current/Target and Raw/Formatted values/flags from real typed responses; missing
target and failed reinspection remain truthful. Preserve legacy navigation and
pending commands. Browser checks require keyboard/focus, reflow, explicit consent
and real route completion, plus exact approved-state captures.

Positive runtime creation still depends on qualified actual publication. Existing
isolated publication/observation witnesses may qualify transport and UI behavior
only, clearly labelled in evidence; they never qualify production admission.
Use owned RAM workspace, isolated outputs and assigned browser ports, and do not
duplicate the remote machine's full/OCI runs or IDE2 native tests.

## Design status

New desktop1440x1000 and narrow390x844 compositions have explicit user approval under the enterprise UX workflow. Reuse Midnight tokens and existing
SVG branding. Readable14–16px text,44px controls, natural vertical scrolling and
explicit Current/Target labels apply. Exact reference images and approval are recorded in
[the Plans manifest](reference/plans-approval.json);
implementation now proceeds against this approved bounded journey.
