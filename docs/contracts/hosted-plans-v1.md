# Hosted plans and transient operation authority — planned D06b

This extends [planning](planning.md), [hosted sessions](hosted-session.md),
[native workspace](native-workspace-v2.md), [observation](database-observation.md),
[profiles](profile-v2.md) and [structural targets](structural-target.md).
It specifies application behavior before HTTP/UI implementation. Routes remain
unavailable until their closed wire contracts and qualification land. No caller
can submit an observation, projection, validation result or export capability.

## Plan ownership, revisions and budgets

A plan belongs to one authenticated session lease and its exact OIDC owner. It
pins an owned immutable published definition, one declared binding and one
independently configured destination. Destination engine must match the binding.
Publication must use the currently supported compiler/mechanisms. Neither a
request nor a definition supplies a JDBC URL, trust policy or account policy.
Create returns a server-generated UUID and revision `1`; subsequent accepted
semantic input changes increment a positive decimal revision. Reservation,
polling, materialization of unchanged inputs, validation, review and artifact
lifecycle do not increment it. No raw plan state persists.
Restart, logout and idle/absolute session expiry revoke it. A fresh login by the
same owner cannot recover old plans, operations, observations or artifacts.

Initial admission allows one live plan per lease, four live plans globally,
one active operation per owner and eight globally, additionally constrained by
the observation adapter's four physical connection/quarantine slots. These are
service capacity limits, not reduced document or engine support. An explicit
discard command retires a plan and releases its memory after unfinished cleanup;
there is no silent eviction. Session revocation retires it without a request.
At capacity return a typed refusal before accepting a new credential or raw body.

Every plan permits the full 128 documents/16 MiB current and independently 16 MiB
target UTF-8 scope, 20,000 entities and 50,000 edges per graph. Retained current
plus target source bytes across plans are capped at 128 MiB. Entered values have
a separate 16 MiB per-plan/64 MiB global UTF-8 allowance. Reserve worst-case old and
new retained bytes and temporary work before allocation. Initially serialize
materialization scratch globally; observation still observes its independent
physical-operation budget. Retained-state accounting and scratch reservation are
atomic and released in every success/refusal/cancel path. No partial observations,
truncation or replacement of the old target on failure satisfies admission.
These counters are not a claim of exact JVM heap use. Qualify maximum retained
graphs, strings, response encoding and scratch against the advertised deployment
memory; increase that deployment allocation if necessary, not the accepted scope.

Credential-free mutations carry `expectedRevision` and UUID `requestId`. After
live-lease/ownership checks, exact successful replay precedes stale-revision
comparison. Keep at most 256 command identities and their original small safe
acknowledgements per lease; exhaustion refuses, never evicts an ID into possible
re-execution. Do not retain old request bodies, entered values, XML, graph results
or old authority in replay records. Identify closed decoded commands using a
process-memory keyed HMAC-SHA256 over native framing with domain
`ES-PLAN-COMMAND-1`, NUL, lease ID, plan ID and the complete command. Restart loses
the key and all plans together. Exact replay returns the original acknowledgement
even after later revisions, but cannot reinstall an old state or capability.

## Observation reservation and one-shot credentials

Inspection starts with a credential-free reservation command. It checks current
revision and capacity, then allocates an opaque operation UUID, captures the
lease/plan revision/cancellation generation and becomes `reserved`. A reservation
owns its physical admission permit through success or actual cleanup; checking
a counter without reserving it is insufficient. Expose a credential-free
reservation boundary from the observation adapter using the same four global
physical/quarantine slots as direct observe. Consuming that permit starts exactly
one physical operation, without a second semaphore acquisition. The existing
direct observe entry reserves internally for standalone qualification. Closing
an unused permit releases it; started permits release only on actual complete
cleanup. No adapter request may bypass the common budget.
A reservation
expires after 60 seconds measured monotonically. It retains no authentication.
While reserved or running, reject new plan mutations with `PLAN_BUSY`; allow
authorized read, status, cancel and discard. The reservation itself does not
alter current/target content or its revision; its successful replay returns the
original operation ID, including after completion, without another connection.

The separate credential submission supplies exactly username/password to that
reserved ID once. Atomically consume the reservation before invoking the adapter;
also consume it before parsing an authorized credential body. Malformed, oversized
or unsupported credentials permanently refuse that attempt and release its unused
physical permit; require a new credential-free reservation to try again. Unauthorized
access cannot consume someone else's reservation. Reserve bounded decode memory
before reading the body; transport-level buffering must obey the same small cap.
there is no credential queue, body digest, replay, automatic retry or reconnect.
Repeated submission returns `CREDENTIALS_ALREADY_CONSUMED`, even if identical.
The UI retains the reservation ID before soliciting credentials. A lost submission
reply is resolved by polling that ID; it never resends the password. Validate
strict UTF-8, no NUL, username at most 128 code points/512 bytes, password at most
1024 code points/4096 bytes, and reject unsupported authentication explicitly.
Owned mutable credential arrays are cleared on every path, including rejection
before JDBC. Existing JVM/driver-copy limitations still apply.

Only the configured ObservationPort may supply a complete observation. Require
cleanup COMPLETE, exact definition/binding/destination pins and independent full
graph projection before installing it. On successful installation, increment the
plan revision, replace the current observation, set target to its exact no-op
state, clear previous edits/profile selections and invalidate all validation,
review and artifact handles. Reinspection explicitly reports that successful
replacement discards the prior draft. Failure preserves prior content but
invalidates inspection-dependent validation/review/export authority. No previous
observation is promoted as evidence for the failed attempt.
An inspection failure, cancellation or inconclusive cleanup sets an explicit
observation-evidence validity latch to false. Retained current/target content is
display-only for that plan until a new complete successful inspection; validation,
composition, materialization, capture and export cannot reauthorize it. Cleanup
completion alone does not reset this latch. An unused reservation expiring or a
malformed credential attempt also invalidates it, making the rule unambiguous.

Completion compares the captured live lease, plan revision and cancellation
generation under the same plan monitor before installation. Cancellation is
permanent even if the adapter later returns success. Logout/expiry must revoke
authority before any cleanup hook runs; completion cannot publish during another
slow or failing hook. A read or download also rechecks current lease validity.
Extend the central session authority with a request-free atomic live-lease guard:
check idle/absolute deadlines without renewing them and perform a short guarded
state transition against the same lock used to revoke the lease. Always acquire
that authority lock before a plan lock; execute network, rendering and cleanup
outside both. A copied request lease or delayed cleanup hook is insufficient.
The servlet absolute deadline includes pending-login time and must be the same
deadline used by this central guard. Background polling/completion never extends
session lifetime. Test revocation while an earlier cleanup hook is deliberately
blocked, and expiry without a new HTTP request.

Operation status contains only operation/plan IDs, phase, safe outcome code,
cleanup state and successful installed revision when present. Terminal phases are
`succeeded`, `refused`, `cancelled` or `expired`; cleanup independently says
`complete`, `in-progress` or `inconclusive`. Pending physical cleanup retains both
owner/global work capacity and the adapter quarantine slot. Retry only the original
unfinished cleanup handle, within the session's bounded lifecycle policy. Never
open another connection to claim the original one was closed. A retry that finishes
cleanup releases capacity, not cancelled authority. At most 256 operation records
per lease; safe terminal status records count and are not evicted into re-execution.

## Capture, composition and explicit target editing

Capture uses the current complete observed graph, a native profile ID/revision
and a complete explicit bijection to neutral slot IDs/labels. The server's
portable profile adapter must accept the result. Return only the allowlisted
profile source/inspection and its pinned definition reference for explicit save
through the native workspace. Capture does not publish, copy donor values or
infer neutral identities. Recheck the expected plan revision before returning.

Composition preview selects one owned immutable published profile, whole or
explicit roots. Its published definition logical digest must equal the plan's;
it may have a different physical binding. Use the same closure algorithm for
whole and partial selection. Pin plan revision, observation fingerprint, exact
profile publication digest and a digest of selected roots/closure. Preview is
advisory; composition recomputes it before applying complete create/existing/cancel
decisions. Missing/conflicting/collapsing decisions refuse or remain explicitly
unresolved. Cancel changes nothing. Never delete unselected siblings or import
values; each selected field has an explicit unresolved/entered/keep/absent choice.
Compose against the complete current materialized target; an incomplete draft
must first be resolved or explicitly replaced by the operator. Preserve existing
draft decisions and all unselected entities/edges. Maintain a bijection from the
current materialized graph's keys to the original observation Existing refs or
the prior draft Fresh slots. Reusing a previously created entity resolves through
that bijection to its original Fresh slot, never a fabricated observation ref.
New slot IDs cannot collide with retained Fresh slots. Translate D05 proposals
through this table before merging explicit decisions; no stable identity may be
guessed from a mutable concrete identity string. Pin at most 128 distinct profile
publications per plan, consistent with the guarded manifest limit.

The editable draft holds complete selected entity decisions, reference decisions,
containment moves and explicit qualified placement as in structural-target.md.
Existing entities use observation-bound handles resolving to stable graph keys;
fresh entities use neutral slot/type references. Parent handles pin document ID,
source digest and exact element index. No browser-supplied identity or offset is
trusted without resolving it against the pinned observation. Replacement commands
replace the entire explicit draft, not the observed graph; an omitted observed
entity remains retained. Typed per-field commands update that same draft without
implicit defaults. Field values and retained XML stay in session memory only.

An accepted incomplete edit may advance the draft revision with diagnostics,
but has no current materialized target or export authority. Never show a previous
target as if it represents the new draft. Materialization evaluates the complete
intent and qualified placements, then independently reprojects all target XML and
compares its graph to expected semantics. Install the complete target atomically
only after all checks pass; a refused materialization keeps the explicit draft
and its diagnostics, not a partial set of rewritten documents. Edits invalidate
all dependent validation/review/artifacts immediately. No layout command changes
semantic revision or pretends to make a structural edit.

## Inspection and comparison

Every read projection states its plan revision and complete scope counts.
Document/entity pages and comparison require an expected revision; stale pages
return conflict instead of mixing revisions. Sort documents by declared ID and
entities by stable tool handle; search/filter/pagination affect display only.
Retain complete validation scope. Return explicit absent-target status for an
incomplete draft, never the current XML disguised as a new target.

Comparison modes are Raw, Placeholders and Formatted. Raw is exact current/target
source only when explicitly requested with the complete-document disclosure
acknowledgement; full XML may contain unmapped or secret values. By default show
structured field summaries with secret/unknown values masked. Published export
content policy does not itself authorize a raw browser reveal. Reveals require
the same live lease and expected revision, remain no-store and expire with it.
An unavailable or masked raw view must not claim exactness. Readability restrictions
always apply; the current native compiler qualifies only readable mapped fields.

Placeholders replace only declared qualified field/reference value spans with
deterministic tool slot labels and explicitly identify unmapped content as still
concrete. Formatted is a separately marked display projection; it never feeds
materialization, validation or SQL. Full-document projections require the same
disclosure acknowledgement as Raw because unmapped content may remain. Report
mode, exactness, redaction and omitted/truncated ranges explicitly. A bounded
view never narrows source authority. Support a complete single document within
the existing 1 MiB XML limit; no requirement forces simultaneous full-scope DOMs.

## Validation, review, export and fresh readback

The server computes a stable plan input fingerprint using native framing and
domain `ES-PLAN-INPUT-1` plus NUL over the closed semantic inputs: plan ID/revision,
definition publication digest, sorted applicable profile publication digests,
observation fingerprint, binding/destination pins, explicit draft decisions and
placements, current/target source digests, compiler/parser/writer/rule versions
and document export policies. Layout, timestamps and response pagination are
excluded. No incoming fingerprint or claimed PASS is authority.

Evaluate all RequiredCheck categories and every compiled application rule.
Missing/stale/FAIL/UNKNOWN/ERROR required evidence blocks. A missing qualified
D07 writer/client yields CLIENT_CAPABILITY UNKNOWN and no export capability.
Validation first reports REVIEW UNKNOWN until an explicit acknowledgement binds
the exact checked input fingerprint, destination and artifact intent. Review
cannot override any other failure. Recheck every pin and live authority, then
evaluate REVIEW PASS from that acknowledgement before creating a capability.
The capability has a private application-owned constructor; no public JSON or
untrusted adapter can deserialize one. Export policy's reference Decision record
alone is insufficient to authorize generation.

Artifact generation rechecks the live lease, revision, observation, immutable
publications, all required checks, qualified writer/client and explicit document
content policy before and after bounded work. A cancelled/expired/edited plan
cannot install or download the result. Guarded package generation has no database
write/client-launch dependency. Generated bytes never enter SQLite or a durable
queue. Stream or use separately reserved bounded artifact memory, one generation
globally initially. An artifact handle binds its immutable archive digest and
target manifest; download rechecks live authority and returns no-store with no
secret URL parameter. Downloaded files are owned by the external process.

Fresh verification reserves a new observation operation using a live exported
artifact's immutable target manifest and destination witness. It never executes
SQL or trusts operator-reported execution. Compare complete membership and exact
target bytes only after complete fresh observation and cleanup. Return Matches,
Differs or Unknown, separately from execution outcome and application health.
Matching readback establishes state, not which artifact caused it. An UNKNOWN
external commit never triggers an automatic retry. Verification leaves the
editing baseline unchanged; explicit reinspection is a separate action.

## Qualification before route enablement

Use real mock OIDC sessions plus independent mock observation/materialization
ports for races, and both existing disposable DB engines for the integrated
credential/cleanup path. Exercise same-owner new lease and cross-owner isolation,
exact replay after advancement, changed request ID/body, reservation expiry,
duplicate credential submission, lost reply polling, cancellation/late success,
logout/idle/absolute expiry during completion/review/download, restart, four
quarantines and fifth admission refusal, byte/graph/scratch limits before allocation,
stale pages/preview, incomplete edits and no partial target installation.

Trace credential/raw-value canaries through logs, exception rendering, replay,
SQLite, response DTOs and temporary files. Test full-scope one-to-two materialization
after whole and partial composition, with distinct fresh values and unchanged
dependencies on both engines. Mutate live-lease/revision guards, missing required
check, UNKNOWN handling and content policy; surviving authority mutants block.
No hosted capability flag changes until its actual path and privacy gates pass.
