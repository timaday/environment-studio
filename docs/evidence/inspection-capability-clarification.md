# Inspection UI availability and backend admission

The independent review identified conflicting descriptions of `inspectionEnabled`:
the UI used false to disable inspection while configured hosted APIs still enforced
their own operation admission. The [revised contract](../contracts/hosted-destinations-v1.md#inspection-capability-response)
now describes that distinction explicitly.

`inspectionUiEnabled` remains false. `inspectionApiConfigured` reports whether
hosted mode, a private workspace and valid destinations compose the service.
The deprecated `inspectionEnabled` alias equals the UI flag. Configuration is
neither a qualification nor an owner/connectivity decision. The React boundary
requires both new booleans and rejects a conflicting alias; the browser uses only
the UI field and explains its unavailability. Existing hosted owner/destination,
one-shot credential and read-operation checks remain unchanged. Ordinary
write-capable accounts stay supported without write probes or grant revocation.

Base `71e30404b64ff26435e34f38e41e06cee730fb82`; fixed22 manifest
`es-inspection-capability-candidate2-20260909.sha256`, SHA-256
`e261d16cb04e9c19efe844d75d7cc1393936810818f344b4761cfa6d57ea7513`.
Only independently invented fixtures and the existing mock OIDC/observation port
are used. No destination model or credential enters the capability response.

Actual RED: two Java assertion failures, zero errors for the absent explicit
fields; five React failures and one positive control. Earlier two Java selections
failed because upstream modules selected no tests; those are command setup errors,
not behavior RED. Corrected commands include core/parser controls and retain the
repository's no-tests failure policy. Logs `es-inspection-capability-red3-20260909.log`
and `es-inspection-ui-red-20260909.log` retain the behavior failures.

After implementation the focused Java selection passes20 tests: six core, one
parser and 13 server, zero failures/errors/skips (`es-inspection-capability-green2-20260909.log`).
It verifies each composition prerequisite without connecting, demo denial,
foreign-owner refusal before an observation call, and an authorized actual
HTTP/OIDC mock inspection succeeding while the UI flag is false. React40 and
schema32 pass; frontend checking and production build pass.

Independent fixed-candidate review found no authority/behavior blocker. Its Java13
selection and initial/restored React40/schema32 pass. Mutating the browser to use
API configuration enables the confirmation checkbox; the new behavior test catches
it with one assertion failure and five controls passing. The reviewer found a
Biome formatting issue in two strengthened assertions. The final candidate changes
only their line wrapping; independent TypeScript/Biome checks pass. An earlier
review archive excluded an unchanged fixture directory named `target`; its three
import errors were corrected in the archive, not by modifying product behavior.
Record `es-capability-independent-review-20260909.md`, SHA-256
`8eda59250591f859881097c8ca0cab3f4cbc00a55314b335febb8e93a31bd27a`.

The [combined Java894 verification](qf34-derived-target.md) includes this change.
Its Java source matches the final candidate. Browser E2E and exact revised-container
verification are pending; no new runtime inspection/export qualification is claimed.
The existing browser harness can enable both UI booleans only through mock response
interception for component-flow evidence. This is not production enablement.

Business review preserves the operator's unavailable state without withdrawing
authorized API functionality. Engineering review makes the configuration diagnostic
explicit and keeps operation authority in Java. QA/security review exercises the
false-UI/true-API combination, missing/malformed responses, foreign access and
configuration prerequisites. No database policy, digest or mechanism version changes.
