# Bounded v3 plan transfer ownership

Internal registry plus future transport; no route/compiler/browser/export enable.
The original PlanRuntime owns one V3PlanTransfers instance, composing its cleanup
and passive readiness into the existing plan hook. Every hook obligation is checked
on invalidation even if another reports uncertainty; no third independently
uncoordinated session cleanup path. Actual integration must preserve destination
owner checks and current v2/v3 service composition.

Metadata admission uses PlanController.metadata() and transfers ownership of that
exact shared slot to the registry record. V1 and V3 together retain at most4 small
metadata slots. No second semaphore or response queue. Credential HTTP records have
an independent bound4, without reserving additional physical observation permits.
The physical Submission owner remains unchanged. If its permit becomes reusable
while HTTP completion is uncertain, that HTTP record still counts against4.
A separately bounded semantic HTTP record covers the existing sole full command/view/materialization scratch. At most1 semantic record and9 V3 transport records total; no eviction. Semantic admission creates no second scratch budget, metadata slot or physical permit. Its caller separately acquires the exact existing core admission before any request input. If either immediate admission fails, the caller conclusively releases only acquired resources; unstarted HTTP settlement follows the owned transport setup rules. Terminal semantic HTTP uncertainty remains counted after core scratch closes and cannot be discarded to admit another transfer. Capacity refusal leaves credential
attempt unclaimed. Caller must perform pure fixedV3 plan/operation ownership before
record/metadata allocation, then original final admissions after allocation.

Records contain exact lease, kind, owned metadata slot if any, cancellation and
monotonic settlement flags only. They never retain body/request/Servlet context,
credential arrays, reply values, encoded buffers or callback Throwable. A record
is not a reusable authority token. Invalidation signals cancellation of all matching
records and reports pending while any remains, including terminal uncertainty.
It never force-closes an actively used stream/descriptor from another thread.
awaitingWork reports retained exact-lease records without side effects.

Only the original worker closes/wipes body, Submission, encoding and output before
calling OwnedAsyncCompletion.workerClosed. The helper is the sole completion
state machine and observer source. Registry settlement COMPLETE removes only the
original identity record and releases its metadata slot once; IN_PROGRESS retains
it; INCONCLUSIVE is sticky, retains capacity and quarantines original lease.
A delayed COMPLETE cannot clear a record already marked uncertain. A completion
notification after the record was conclusively settled is inert. Last same-lease
conclusive removal requests original session cleanup outside the registry lock;
intermediate completions cannot exhaust retries. Session passive readiness gates
all unfinished actual families before original bounded retry.

Controlled acceptance: all4 sharedmetadata slots block bothV1/V3 fifth admission;
credential4 plus metadata4 do not create physical permits; exhausted credential
records leave pureowned reservation retryable; metadata stays held while completion
uncertain; observerordering/invalidation/cancel; latecompleteafteruncertainty cannot
freecapacity; originalrecord/samelease/foreignowner/stalecallback; lastcompletion
notification outside registrylock and mixedfamily retirement. Record-only tests
cannot establish actual route ownership, workerclosure, credential/readoperation
safety, actual HTTP scheduling or maximum-capacity heap qualification.

Internal surface: package-private V3PlanTransfers implements SessionCleanup, with
admitMetadata(originalLease), admitCredentials(originalLease), admitSemantic(originalLease), awaitingWork and
invalidate. Metadata admission acquires the shared slot inside the registry method;
if installing its record fails, close only that acquired slot. Caller cannot transfer
or duplicate another record's slot. Returned Operation exposes cancellation and
settlement(OwnedAsyncCompletion.Outcome, HostedSessions); only the helper observer
calls settlement after the original worker closure precondition. This registry
records settlement; it is not another Servlet completion state machine. An IN_PROGRESS
result retains the record. Do not store the observer, Servlet context or HTTP request.

PlanRuntime constructs exactly one registry and exposes it package-locally to the
future transport. Its existing cleanup attempts service and registry invalidation
independently even if either refuses, then reports CLEANUP_INCONCLUSIVE if any
obligation is unfinished. Passive readiness reports either service work or registry
records. The existing Spring plan hook forwards this composite owner. Disabled
runtime retains no application-service obligation, without skipping registry cleanup.
No v3 route or physical credential claim is added in this slice. Registry tests do
not claim the future caller's version checks or resource closure have been wired.

Semantic acceptance: one semantic record plus all four metadata and all four credential records coexist regardless of admission order. No class can consume another class's slots. Semantic IN_PROGRESS and terminal uncertainty retain the one semantic record; conclusive settlement alone makes it reusable. Records keep the same minimal fields and original-lease cleanup rules. See [v3 semantic command contract](hosted-plan-commands-v3.md).
