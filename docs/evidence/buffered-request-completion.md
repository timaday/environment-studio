# Buffered request completion — reviewed transport correction

The one owned servlet reader now derives EOF from its ServletInputStream, while
completion callbacks only wake it. A latched callback flag previously returned EOF
with unread container-buffered bytes remaining. This intermittently rejected a
complete valid credential request as MALFORMED_BODY, and could truncate other
request bodies using the same reader. No credential retry or new queue is added.
Cancellation, error, close, deadline and byte-limit checks retain their precedence.

## Investigation and meaningful RED

The corrected binding integration at `7c1cb4c` plus binding33/wire5 failed the old
maskedDraft HTTP credential submission, expected200/actual400, and recorded the
allowlisted MALFORMED_BODY code. The binding routes had not yet been exercised.
Log: `/home/tim/.tmp/es-bindings-corrected-integration-20260909.log`.

An isolated worktree at `4d767fb` reproduced the failure using repeated fresh
reservations and exact independently invented non-BMP credentials through real
OIDC and HTTP sockets. Temporary diagnostics retained only fixed Java call sites
and fixed EOF categories; no body/value/length/hash or exception text was captured.
One run failed in the JSON string reader. A later eight-case run failed three
cases at string/expected-token reads after CALLBACK_EOF. Logs:
`es-credential-read-review2-20260909.log` and `...review4-20260909.log`.
An earlier test-subclass setup lacked its inherited test configuration and failed
before execution; this is a setup error, not RED. All diagnostic production edits
were removed before constructing this fix candidate.

The resolved runtime uses Tomcat 11.0.24. Its
[CoyoteAdapter](https://github.com/apache/tomcat/blob/11.0.24/java/org/apache/catalina/connector/CoyoteAdapter.java)
checks [Request.isFinished](https://github.com/apache/tomcat/blob/11.0.24/java/org/apache/catalina/connector/Request.java)
at the Coyote request layer to deliver completion. The input stream's
[InputBuffer.isFinished](https://github.com/apache/tomcat/blob/11.0.24/java/org/apache/catalina/connector/InputBuffer.java)
also accounts for bytes still in its local buffer. Treating the callback as the
owned worker's unconditional EOF loses that distinction.

Two deterministic regression examples model buffered bytes following a completion
notification. Actual RED (`es-buffered-body-red-20260909.log`): credential decoding
raised MALFORMED_BODY; bulk input returned3 of36 expected invented bytes (one
behavior error and one assertion failure). The fix removes the callback EOF latch.
The first focused GREEN passed21 tests:4 core,1 parser,16 server, including both
regressions, existing deadline/closed/cancelled reader controls, strict credential
decoder cases and eight real HTTP cases with32 fresh operations each (256 total).
Log: `es-buffered-body-green-20260909.log`.

The permanent repeated HTTP case is added to the existing hosted test class so
that it shares the established OIDC, canary, workspace and cleanup lifecycle.
It resets the mock exact-credential witness for each attempt and requires an
actual successful observation with exactly one port invocation. No failed
submission is automatically repeated; every iteration explicitly creates a new
reservation. The fixture model and credentials are independently invented.

## Candidate and remaining verification

Only OwnedServletBody, BufferedBodyCompletionTest, the added HostedBoundaryTest
case, the HTTP contract clarification and this evidence belong to the delta.
The author tree predates binding integration; its HostedBoundaryTest uses the
exact `21649ba` version as context. Compare against `21649ba`, never merge that
whole older tree. There are no credential-parser or PlanBodyFailure diagnostic
changes in the candidate. Pinned Maven3.9.16/JDK21 are used.

The strengthened HTTP witness also passed all21 focused tests, including256
confirmed successful mock observations with exact credentials and one port call
each (`es-buffered-body-green2-20260909.log`). Independent fixed-source review,
final combined reactor, guard regression mutation and artifact/browser gates
remain pending at this record. Earlier untraced intermittent400 reports may share this mechanism,
but their exact executions cannot be retroactively diagnosed. The separate
native terminal-test failure is not explained by this servlet correction.
This is local mock evidence and does not enable production inspection/export.

## Independent acceptance and combined result

Independent review accepted the fixed five-file manifest SHA-256
`71bb7bc58ee04f25d97f140d977fda2cb5f5c1c535fbb19af15ec777d15a12b9`
against `21649ba`. A fresh archive passed23 focused tests, including256 actual
fresh HTTP observations and four independent adverse cases: early completion
while unready still expires; callback completion cannot suppress error/cancel or
byte limit; real stream EOF does not require a callback. Log:
`es-buffered-body-independent-20260909.log`. No source correction was requested.

The detached integrated `21649ba` + exact fixed5 candidate passed all590 Java
tests (150 core,7 parser,361 server,72 supervisor), assembly and hostile-launch
checks. This includes the full existing hosted boundary suite and both previously
failing credential submission locations. Log:
`es-buffered-body-integration-full-20260909.log`. No failures/errors/skips.
Reinstating the old callback latch as a targeted mutant again caused the exact
bulk truncation assertion and credential MALFORMED_BODY behavior error. The fixed
source was restored and all five candidate hashes verified after that experiment;
log `es-buffered-body-mutant-20260909.log`.

The full local Java integration is now green; OCI/browser qualification of the
new combined candidate remains pending. Earlier failed runs remain evidence of
the defect and have not been relabelled successful. No native runtime registry,
inspection/export capability or release-evidence status was enabled.
