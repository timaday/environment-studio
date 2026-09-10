# V3 profile logout settlement — TEST-QA-002

The five ordinary logout assertions in V3ProfileBoundaryTest now wait for the
actual workspace operation registry to reach zero before making one logout
request and requiring204. The existing bounded wait still fails on timeout.
JUnit assertAll then attempts logout and retains both failures. The immediate
partial-body logout and both fifth-request429 assertions remain unchanged.
No production lifetime, capacity, response or authority changes.

## Defect and acceptance

Remote combined candidate `875a257d659d3139a2c180cc587531ddcd095c96` failed its
full Maven gate: 1,355 tests, two failures, no errors/skips; server failed and
supervisor was skipped. The failed assertions expected logout204 after receiving
profile responses. [Independent queue reproduction](https://github.com/timaday/environment-studio/issues/9#issuecomment-5621732360)
holds the original operation after async completion but before actual record
removal. The full profile response has arrived, yet its same-lease record remains
unsettled and nonuncertain. Logout correctly returns503
SESSION_CLEANUP_INCONCLUSIVE and revokes authority. Release permits original
cleanup and fresh same-owner login. The lead accepted TEST-QA-002; this establishes
a legal failure path, not instrumentation of both original unscheduled failures.

Acceptance: ordinary listing, history and recovered-workspace logout retain204
after actual settlement. Held work remains owned; immediate logout and four-slot
contention still exercise the original concurrent behavior. A timeout must remain
a failure and cannot prevent the cleanup request. The contract already specifies
these204/503 outcomes in hosted-session.md; no public interface changed.

## Local author checks and review

Controlled actual HTTP/OIDC/SQLite listing and history tests each fail expected204
versus actual503 on the base. With the correction, both pass after the barrier
releases the same original record and the existing wait observes actual removal.
The hold changes only external scheduling, verifies original registry membership,
and drains independently during teardown. A compiled mutation removing the test
barrier makes both tests fail at the same204/503 assertion. A separate held-work
timeout retains both wait0/1 and logout204/503 failures; its safety timeout never
fires. These are test-oracle controls, not production guard qualification.

Focused Maven passes **56 tests:12 core,1 parser,43 server**, no failures/errors/
skips,44.740 seconds, including all five affected HTTP cases. Linux amd64,
Ubuntu JDK21.0.12 and Maven3.9.16 ran in a separate worktree:

```text
mvn -B -ntp -f backend/pom.xml -pl server -am -Dtest=SessionLedgerTest,MinimalRuntimeTest,V3ProfileBoundaryTest,V3WorkspaceBoundaryTest,V3WorkspaceTransferTest,V3WorkspaceCompletionTest,V3WorkspaceLiveAuthorityTest,HostedSessionsTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The first focused selection found no core tests and stopped before parser/server;
the corrected selection above preserves the gate. The first history launcher
selected no test because it omitted a parameter type. The first timeout driver
misread a diagnostic label and overwrote its hash-map variable; corrected timeout2
was rerun with intact attribution. These attempts remain preserved and are not
counted as passing checks or meaningful RED.

Fixed non-author source review verifies exactly five substitutions, unchanged
concurrent controls and the seven relevant recorded result/log pairs. No blocking
finding; no reviewer execution. Source SHA256:
`f72646247b14cc1d9d114371281f926177f744aef6204792e746adeafeec6366`;
patch SHA256:
`6229e798a6369bbd4b37a4c7e58a946eabc073354a9783acc0c36f4b3dd10f37`.
External evidence remains in `es-profile-settlement-controls-20260910`,
`es-profile-settlement-fixed1-20260910`, the focused2 log and
`es-profile-settlement-fixed1-review-20260910.md`.

The exact corrected candidate still requires remote verification and the combined
full gate. No local full/native/OCI/DB qualification campaign ran. Earlier passing
branches, focused results and source review do not clear the failed full run.
