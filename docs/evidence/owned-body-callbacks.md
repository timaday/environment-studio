# Body callbacks no longer wait on the reader monitor

The owned Servlet body reader now serializes application reads while readiness,
all-data, error and close notifications publish safe state and wake the reader
without taking its monitor. This prevents notifications waiting behind application
calls to Servlet readiness/read methods. The original byte count, absolute deadline,
cancellation checks, buffered EOF and interrupt handling remain enforced. See the
[body contract](../contracts/hosted-plan-http-v1.md).

A held Servlet call still owns its reader and admission until it actually returns.
Closing the wrapper does not forcibly stop that call; an already in-flight read may
return a byte before the next read sees closure/error, as before. No Throwable or
payload queue is retained. These controlled schedules reproduce a helper lock
interaction, not a demonstrated Tomcat socket deadlock or hosted exploit.

## Evidence

Fixed production/test/contract manifest over688e799:
`3da5bc2f966a62d6946ed5d213f1880839545a82a0eba2652b95441fd9f54de5`.
The author archive started at8b5843d plus the exact subsequently integrated version
boundary. The lead amended the body contract before implementation. All inputs are
independently invented. Maven3.9.16/JDK21.0.12 results on10 September2026:

- Original RED:10 cases,2 assertion failures/zero errors. Held readiness/read calls
  prevent their callbacks returning; cancellation and existing controls pass.
  The original test is retained with SHA
  `8376af4126c8e30172e0c1a8183f7a631119efa2a6099e39b6ce96ad5c225246`.
- Actual hosted/body/async suite passes55, including34 HostedBoundaryTest HTTP/OIDC
  cases,01:34:02BST. That invocation compiled the initial3 new cases; later author
  additions are counted only in their separate final25-pass focused run.
- Final7 author controls include close while a read remains held, pre-registration
  notifications, buffered bytes, cancelled/expired waiting readers and preserved
  interruption. Three compiled mutations restore callback/close locking or treat
  all-data notification as immediate EOF; each fails one assertion/zero errors.
  Earlier EOF error and ambiguous test-overload compilation attempts are preserved
  and excluded from assertion-kill claims. Exact final sources restored:25 pass.
- Independent review finds no confirmed blocker. Two separately written stream
  controls test mixed single/bulk reads against one exact byte limit and an
  all-data callback during a held finished probe, followed by literal bytes/EOF.
  Focused/restored27 pass (1 core,1 parser,25 server). Restoring the all-data monitor
  fails one assertion/zero errors among4 controls. Exact candidate3 restored.
  Restored log SHA
  `97b0c0bf48efdd7df2589439cc2dbbad7c4b9edcc343a85bc6448a97f8adc468`.

The exact combined candidate and its full gate are recorded with
[v3 plan summaries](qf34-plan-summary.md). No new route, completion-registry
qualification, maximum-capacity result or browser/deployment readiness follows.
Mixed-family cleanup notifications are a separate reproduced issue under active
investigation; this reader correction does not resolve it.
