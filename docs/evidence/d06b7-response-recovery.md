# D06b7 — bounded response output and cleanup recovery

The hosted read-view candidate stops application output after revocation,
disconnect or its original 30-second output deadline, releases the encoded view,
and resumes confirmed original session cleanup. It preserves quarantine while
other work or commit cleanup remains outstanding. It does not claim delivery of
bytes already handed to the container or qualify whole-process maximum memory.

## Fixed scope and behavior

The original archive is `/home/tim/.tmp/es-plan-transfer-20260909`, base `212fb2f`.
Its frozen 14-file source manifest is
`/home/tim/.tmp/es-plan-transfer-source-20260909.sha256`, SHA-256
`f33ac2d68f2c3caa69a6331e79eb15953479d4eac84888e2a5862a801a855067`.
Independent review found a remaining cleanup race, so this candidate is superseded.

The corrected archive is `/home/tim/.tmp/es-plan-transfer-corrected-20260909`,
base `41a17cc`. Its frozen 15-file source manifest is
`/home/tim/.tmp/es-plan-transfer-corrected-source-20260909.sha256`, SHA-256
`2d3d50f6908b089440c57576ff005aa7d7d8e431749f404f5cc7e6646b6153e3`.
Only SessionLedger and the added independent race regression differ from the
original 14 files. Later native listener integration is present in this base.

Contracts were updated before implementation. One application writer uses
Servlet nonblocking readiness, at most 8 KiB writes, an absolute 30-second output
deadline and live-authority checks at most 100 ms apart while scheduled. Readiness
and successful progress cannot renew the deadline. Callbacks use volatile state
and unpark; they never acquire the writer lock or wait under a container lock.
No response queue or complete response copy is added. Native/authority exceptions
become sticky safe-code transport refusal, without retaining their cause.

The view's encoder and application admission remain owned until the writer
stops. Committed partial output is aborted without appending a second JSON object.
Servlet completion has one registered owner per async cycle. Callback and worker
completion use one nonblocking attempt; COMPLETE means accepted completion or
observed container completion, IN_PROGRESS makes no settled claim, and a refused
attempt stays INCONCLUSIVE. None of these outcomes establish client receipt or
release application resources early. Registration failure retains a bounded
container timeout and releases application admission.

Once the plan worker closes admission, it confirms the original plan retirement
before notifying the original session cleanup. Notifications coalesce atomically
with retry admission. No live session is retired by a notification, no old plan
is restored, and no new operation or credential retry is introduced. The existing
three-attempt limit and outstanding-commit exclusion remain enforced.

## Actual behavior evidence

All requests, accounts, definitions, fields and values are independently invented.
The socket cases use the actual embedded hosted server, OIDC session boundary,
command decoder, planning service, encoder and TCP output. Database observation
is an explicit completed mock port. Two existing mock glyphs receive four 1 MiB
quote strings; the incomplete draft response exceeds 8 MiB. The TCP peer advertises
a 1 KiB receive buffer and reads only headers. A separate live operator observes
view capacity refusal, then recovery without draining that peer's response.

| Case | Actual RED | Actual correction evidence |
| --- | --- | --- |
| Logout while output remains unread | One assertion: observer remained429 instead of200 | Real socket recovery and fresh same-owner login; old plan404 |
| Retired owner's next login | One assertion: callback403 instead of302 after view capacity recovered | Resume confirmed original cleanup; old session stays revoked |
| Notification during running cleanup | Two assertions: completion lost and expected bounded attempts absent | Coalescing tests plus full core164 at that stage |
| Output sink failure | One assertion: raw canary exception message escaped | Safe code, no cause, no subsequent bytes |
| Output callback holds container lock | One assertion: callback waited on writer | Callback notification no longer acquires writer lock |
| Async completion lock ordering | Independent controlled socket-lock inversion: one assertion | Nonblocking completion ownership, explicit racing refusal |
| Completion listener registration fails | One assertion: async timeout was unbounded | Bounded registration timeout; application admission closes once |
| Concurrent retry admission and completion notification | Reviewer reproduced one assertion on unmodified candidate bytecode at iteration422 | Atomic retry/notification admission; independent 10,000-schedule regression |

The first full output run failed five real login assertions and exposed an invalid
late AsyncContext call after a disconnect. Those failures are retained in
`es-plan-transfer-full-20260909.log`; they are not called flakes or passes.
One targeted invocation selected no qualified-parser test and was rejected by the
zero-test gate; `es-plan-transfer-callback-red-20260909.log` is a command setup
failure. The corrected selection in `...callback-red2...log` reached the intended
output callback assertion. No test gate was disabled.

The later focused run passed31 tests: core9, parser1 and server21, including three
actual socket scenarios (logout, absolute deadline and disconnect), seven output
controls and eleven async completion controls. The latter include actual embedded
Tomcat timeout/error callbacks and bounded controlled lock inversion.

Original frozen source14 passed full Java670: core166, parser7, server387 and
supervisor110, including assembly/hostile-launch checks. The reviewer subsequently
found the retry-admission race despite this pass. Corrected source15 passes full
core167, including the independent reproducer. Corrected source15 on the newer
`41a17cc` base passed full Java683: core167, parser7, server387 and supervisor122,
including assembly/hostile-launch checks. Log:
`/home/tim/.tmp/es-plan-transfer-corrected-full-20260909.log`.

Independent corrected-source review passed25 focused tests: core10, parser1 and
server14, including the original 10,000-iteration race test, cleanup/commit/attempt
controls, three actual socket cases, output7 and controller2. Two additional
external controls verified close while unready and signed nanoTime wrap. The
strengthened close control passed with core4/parser1 (seven selected tests total).
The reviewer found no remaining blocker within this slice; the original
notification race is closed. External review archive:
`/home/tim/.tmp/es-transfer-review-rguy9f0p`; logs
`es-transfer-corrected-independent-20260909.log` and
`es-transfer-independent-output-final-20260909.log` under `/home/tim/.tmp`.

Eleven targeted guard mutants were killed by assertions with zero test errors:
live authority, absolute deadline, sticky output failure, chunk bound, callback
lock, notification coalescing, cleanup attempt limit, commit retry exclusion,
original session resumption, single async completion and completion lock ordering.
After the correction, the three affected cleanup mutants were repeated and killed;
an additional mutant restoring the admission gap was killed by the independent
race test. This establishes twelve distinct guard checks, not fifteen independent
guards. Unchanged output/async hashes retain their original mutation evidence.

Logs and exact mutation command arrays are external under `/home/tim/.tmp`:

- `es-plan-transfer-http-red-20260909.log`, `...http-green...`, `...relogin-red...`,
  `...relogin-green...`, `...cleanup-notification-red...`, `...cleanup-notification-green...`.
- `es-plan-transfer-output-red-20260909.log`, `...output-green...`,
  `...callback-red2...`, `...registration-red...`, `...adverse-green...`, `...full2...`.
- `es-async-completion-inversion-red-20260909.log`,
  `es-async-completion-final-20260909.log`.
- `es-transfer-independent-race-20260909.log`,
  `es-plan-transfer-race-corrected-core-20260909.log`.
- `es-plan-transfer-mutation-results-20260909.json` and
  `es-plan-transfer-corrected-mutation-results-20260909.json`.

## Review and limits

The lead reviewed the agent-authored completion helper's fixed two files; the
agent independently reviewed the lead's output, controller/session integration and
cleanup change. The original helper used a blocking callback lock; review of
Tomcat's outer socket locking prompted the controlled inversion reproduction and
the revised nonblocking helper. The reviewer then reproduced the separate core
notification gap without source instrumentation. No reviewer self-certifies their
own implementation. Integration/rework is recorded by these rounds; no measured
parallel speed-up is claimed.

The [Servlet output contract](https://jakarta.ee/specifications/servlet/6.1/apidocs/jakarta.servlet/jakarta/servlet/servletoutputstream)
requires nonblocking readiness checks before write/flush. Exact
[Tomcat11.0.24 AsyncContext implementation](https://github.com/apache/tomcat/blob/11.0.24/java/org/apache/catalina/core/AsyncContextImpl.java)
and [socket processor locking](https://github.com/apache/tomcat/blob/11.0.24/java/org/apache/tomcat/util/net/SocketProcessorBase.java)
support the lifecycle investigation. Source inspection is not a reproduction of
every real shutdown ordering; controlled inversion and actual error/timeout
results retain that distinction.

Business: another operator can recover capacity and a retired owner can log in
again only after confirmed cleanup. Engineering: absolute clocks, bounded memory,
one writer and explicit outcomes preserve lifecycle authority. QA/RST: blocked
peer, disconnect, concurrent cleanup, late callbacks and commit quarantine are
separate oracles; green counts did not replace independent race investigation.

These probes do not qualify reverse proxies, TLS output backpressure, maximum
legal heap/response combinations, prolonged GC pauses, application deployment or
native client execution. Existing synchronous small metadata routes are outside
the new read-view output adapter. Whole hosted-process capacity and the complete
operator workflow remain open. No release status or scope limit changes.
