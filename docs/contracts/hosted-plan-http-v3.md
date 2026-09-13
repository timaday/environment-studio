# Initial hosted v3 plan HTTP contract

Implementation target, not an availability claim. Seven routes share the actual
versioned HostedPlanService and explicitly require immutable modelV3. The actual
compiler still returns Incomplete; fresh actual publication and creation must refuse
until qualification exists. Test-only publication/observation witnesses cannot
change runtime readiness. Existing v1 routes remain fixedV2. Destination listing is
version-neutral at its existing v1 route. No request field can choose readiness,
owner, driver, mechanism, model version, SQL/XML or validation authority.

POST /api/v3/plans accepts the existing closed Create metadata command; calls an
explicit PlanRuntime.createV3 wrapper preserving configured destination-owner
policy and actual createV3 publication lookup. Success201 acknowledgement.
GET /api/v3/plans/current and GET /api/v3/plans/{planId} return200 small v3 summary.
POST /api/v3/plans/{planId}/inspections accepts existing closed reservation command;
success202 acknowledgement. POST /api/v3/operations/{operationId}/credentials uses
existing closed one-shot credential decoder, original operation and physical permit;
success200 status. GET /api/v3/operations/{operationId} returns200 status.
POST /api/v3/operations/{operationId}/cancel accepts exactly the existing empty JSON
object command and returns200 status. All unsafe requests require original authenticated
lease, expectedHost/Origin and CSRF. Read polling captures without extending idle time.
Missing/foreign/wrong-version resources and unavailable current-plan identities
share safe404; revoked originallease401. Retained original operation and replay
metadata keep the ownership behavior specified below.
Version check precedes transfer capacity, body/output/async access, expiry or cleanup.
Creation translates missing/foreign workspace definition history to the same404
NOT_FOUND. Workspace unavailability, including an incompatible workspace schema,
returns503 PLAN_SERVICES_UNAVAILABLE. Owned unpublished history still returns422
PUBLICATION_REQUIRED; unqualified current compilation returns422 UNSUPPORTED_DEFINITION.
Unexpected failures remain500 PLAN_INTERNAL_REFUSAL. Error responses contain only
closed codes, never exception messages or workspace existence details.

Acknowledgement fields remain planId, decimal-string revision and optional operationId.
Operation fields remain operationId, planId, lowercasephase, closedcode, cleanup
(complete/in-progress/inconclusive) and optional installedRevision. Poll once to capture
status; transfer checks use pure immutable operation ownership, never repeated cleanup
polls. These snapshots are not reusable approval/export tokens. A retained original
operation or replay acknowledgement keeps its immutable version across replacement.

V3 summary preserves every current physical summary field and adds required
currentComputedCounts and targetComputedCounts, each null or an object with integer
nodes,memberships,cooccurrences. Null means no complete current/target evidence;
presentzero means complete empty results. Counts never flatten into physical totals.
Preserve observedDestination null/validity semantics, inspectionValid,targetComplete,
exportAvailable=false, closedblockers and optional activeOperationId. Before publishing
and throughout transfer recheck exact captured V3View including same-revision changes;
never substitute a new current plan. No raw graph, contributor value or source proof
belongs in this small response. Encoding emits deterministic closed maps in declared
field order, with existing safe enum spellings and exact decimal revision strings.

All seven paths use the existing shared4metadata slots or separately bounded4
credential HTTP records; no additional physical observations, executor queue or
waiting capacity admission. See plan-transfers-v3. Credential record admission must
precede claim; refusal cannot consume a still valid one-shot attempt. Summary capture
and pure plan/operation ownership precede allocation and all final service checks
remain after allocation. Request bodies max16384bytes and one original10s deadline.
Success/error replies max32768 encodedUTF8bytes. A single original30s output deadline
starts beforeencoding and covers encoding, output acquisition, readiness waits,
each chunk and finalflush. Actual observation keeps its own original clocks. Never
renew outputdeadline to encodefailure. Stream chunks8192bytes, readiness/cancellation
recheck within100ms while scheduled; an already in-flight container call is not
claimed forcibly stopped. Response delivery is not established by completion acceptance.

The original worker owns body, Submission and encoded/output buffers through actual
closure/wiping. Sole OwnedAsyncCompletion settlement then permits registry cleanup.
Register bounded container timeout before listener, disable that timer only after
owned listener registration; start failure with unowned asynccontext retains honest
uncertainty. onComplete does not release worker resources; refused/unsupported
completion retains its minimalrecord and quarantines originallease. No secret/body/
request/Servletcontext in terminalregistry records. If setup fails before any
startAsync attempt, close the admitted application resources, then settle its
record directly COMPLETE only when that closure is conclusive; otherwise retain
INCONCLUSIVE. Once startAsync has been attempted, failure without an installed
completion owner retains INCONCLUSIVE even when no context was returned. With an
installed owner, close resources and notify workerClosed through that owner. These
setup branches cannot claim a worker or async cycle was completed when none existed. All family readiness/commit/
threeattempt guards remain.

Before an owned async transfer exists, a controller refusal sets its HTTP status, no-store,
zero Content-Length and the closed X-Environment-Studio-Code header only. Do not
obtain an output stream, write a body, start unadmitted async work or wait for
transport capacity to report such a refusal. The header value is a tool-owned closed
code, never raw exception/input text. After admission, an uncommitted safe error may
use the same owned encoder/output deadline and closed JSON {code}; if original
authority or deadline has ended, terminate without appending a new body. A committed
partial response is aborted, not followed by another JSON result. The client handles
both no-body refusals and owned JSON errors and treats incomplete success JSON as
unknown/failed transfer, never successful work or permission to retry credentials.
Existing authentication/Host/Origin/CSRF filters keep their own safe refusal
contract; the new no-body header applies to this controller, not a claim that all
security middleware uses its bounded worker. No new physical commands, views,
capture/reuse, validation, export or readback route
is enabled here. Independent contract review accepted the early no-body refusal and original-budget
owned error rules. Implementation still requires actual response/header/body-access
controls; contract review alone does not establish working HTTP behavior.
Actual mockOIDC/HTTP tests must cover crossversion, owner/revocation/replacement,
capacity/credentialretry/cancel/status, idle polling, closedrequest shapes, stalled
body/output, completionuncertainty and actual compiler/publication refusal. Current
candidate browser/nativeclient/combinedheap/OCI/remoteCI/deployment gaps remain separate.
