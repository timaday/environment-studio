# Observable settlement for an owned plan transfer

This internal helper extension prepares bounded v3 plan transport. It introduces
no route, resource registry, admission flag or release qualification. The original
OwnedAsyncCompletion constructor and finish semantics remain supported. One
original Servlet async cycle has at most one complete attempt; IN_PROGRESS never
authorizes another attempt and INCONCLUSIVE remains sticky after refusal.

An optional settlement observer and workerClosed() let the transport separately
observe HTTP completion after its worker has stopped using, closed and wiped every
application-owned resource. The observer is not notified while that worker is active.
workerClosed signals that caller-owned precondition; it is not evidence produced
by a Servlet callback. Repeated workerClosed calls do not start another attempt.
If a callback owns an in-flight attempt, the worker reports IN_PROGRESS and that
attempt's eventual callback result supplies the terminal notification. Conversely,
when the worker owns the attempt, competing callbacks return without waiting and
its eventual result supplies terminal notification. Observer calls occur outside
the attempt lock, including callbacks invoked reentrantly by context.complete on
the same thread. Latch their uncertainty immediately but defer observer invocation
until the original outer attempt returns and unlocks. Observers must be bounded, trusted transport code and must not
wait for another completion attempt or hold a lock across Servlet calls.

Error/timeout/new-cycle callbacks immediately mark transfer work aborted. checkActive
refuses CANCELLED after abort, unsupported cycle or observed container completion.
It does not call the Servlet context, extend authority or renew any deadline.
onComplete only records container completion and never calls arbitrary observers
or releases application resources. Error/timeout still attempt completion in their
legal callback scope even when the worker has not yet closed. A completion refusal
uses the existing static IOException diagnostic and preserves uncertainty; later
onComplete cannot turn it into success. Unsupported cycles never replace ownership.
Reported uncertainty is also retained by every subsequent finish result; the observer
and direct completion query must not contradict each other.
An unsupported-cycle flag observed before notification overrides a computed COMPLETE;
later notifications must not erase a previously reported INCONCLUSIVE outcome.

COMPLETE means the original completion was accepted or container completion was
observed. It is not evidence of delivered/flushed response bytes. Future transport
records must retain capacity until worker closure plus conclusive completion, keep
uncertain records and quarantine the original lease. Observer results alone cannot
restore authority or authorize export. The session's passive pending-work gate and
all existing cleanup limits remain required. Registration failure remains a static
refusal; the caller retains any unowned async context as uncertainty, after bounded
container timeout was installed before listener registration.

Acceptance covers callback before worker closure, completion before closure,
simultaneous held worker/callback attempts, refusal followed by onComplete, no callback
lock inversion, unsupported-cycle ordering, observer calls outside the attempt lock
and checkActive without context access. Rerun existing actual Tomcat timeout/error
controls and all legacy completion tests. Use controlled schedules plus actual
container evidence; neither alone qualifies future registry or HTTP route wiring.
