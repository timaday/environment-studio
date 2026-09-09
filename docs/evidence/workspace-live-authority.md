# Workspace authority through metadata commit

Base: `8125e57127b1583664d368b97b82d08ec103fe5d`. This slice corrects the
reported in-flight workspace save after logout/expiry. It implements the commit
admission rules in [native workspace v2](../contracts/native-workspace-v2.md),
including v1 saves. All test declarations and identities are independently
invented; real SQLite test stores are private temporary directories outside the
checkout.

## Behavior and review

Controllers retain the exact session lease across body reading and recheck live
authority before service work and returned results. SQLite adapters acquire an
explicit lease commit permit only after compilation, transaction lock acquisition,
revision/replay checks and prepared metadata changes. Admission checks idle and
absolute expiry without touching activity. Denied admission closes the uncommitted
connection; independently reopened catalog and replay queries observe no save.

At most one permit is outstanding per lease. An admitted commit is ordered before
later revocation. No authority monitor covers SQLite I/O. Logout immediately
revokes further commands and attempts independent cleanup hooks; its slot remains
quarantined until the admitted connection has observably closed. An explicit
internal retry completes cleanup after closure without repeating successful hooks.
Failed close retains the permit and quarantine; it never becomes success because
of an interruption or an independent thread disappearing. Commit failure with
observed close remains unavailable/indeterminate and preserves exact-command replay.
An Error from close retires authority and is rethrown unchanged.

The independent reviewer identified an initial close-Error gap. A dedicated
`UnsatisfiedLinkError` test reproduced the still-live lease before the correction;
the fixed test observes revoked authority and inconclusive cleanup while preserving
the original Error. No account, managed configuration database, SQL export or
frontend behavior changes in this slice.

## Observed checks

Toolchain: Apache Maven 3.9.16, OpenJDK 21.0.12. Earlier unavailable `/tmp` toolchain
and host Maven 3.8.7 were correctly refused before tests; those are not RED runs.

The initial behavior RED used:

```sh
mvn -B -ntp -f backend/pom.xml -pl server -am \
  -Dtest=WorkspaceLiveAuthorityTest,SessionLedgerTest,MinimalRuntimeTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Four failures: v1/v2 saves each returned 200 after logout or idle expiry completed
while the request body was held. The close-Error RED had one failure: authority
remained live after the original Error escaped.

Final corrected-candidate focused GREEN: **52 tests passed** (14 core, 1 parser,
37 server), using:

```sh
mvn -B -ntp -f backend/pom.xml -pl server -am \
  -Dtest=SessionCommitAuthorityTest,SessionLedgerTest,MinimalRuntimeTest,WorkspaceCommitTest,WorkspaceLiveAuthorityTest,WorkspaceControllerTest,NativeWorkspaceTest,HostedSessionsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The focused tests cover body-read revocation, final-precommit rollback of prepared
catalog/replay changes, multiple commits on one lease, immutable exact replay after
fresh login, idle/absolute admission boundaries, unrelated-owner liveness,
commit/close failure combinations, pending-close quarantine and independent cleanup.
`git diff --check` and `python3 scripts/check_repository_content.py` passed; the
content checker establishes only its known-pattern checks, not provenance.

## Remaining full-gate investigation

A full `mvn -B -ntp -f backend/pom.xml verify` on the first frozen candidate reported
136 core and 7 parser tests passing, with one failure among 284 server tests:
`HostedBoundaryTest.actualQuietTrickleDisconnectCancelAndMetadataCapacityStayBounded`
expected renewed login 302 at line 309 but received 403. An unchanged focused rerun
reproduced it. This is not recorded as a passing full gate.

A temporary independent probe retained the original assertion and observed the
safe internal state after failure: session cleanup INCONCLUSIVE, one attempt, one
unfinished plan cleanup hook, no outstanding workspace commit, zero command
readers and all five operations now cleanup COMPLETE. The test requests fresh login
after an earlier expired body-reader cleanup was inconclusive without a subsequent
internal cleanup retry. The probe is diagnostic only and is excluded from the
candidate. The integrated owner must resolve this full-gate failure under existing
quarantine semantics; no automatic cleanup retry or relaxed status assertion is
introduced here. A later unchanged run of the hosted case passed alongside those
52 focused tests, establishing intermittent timing; it does not erase the earlier
full-gate failure or prove cleanup was conclusive in the failing run.

Operating-system commit I/O is not claimed bounded by SQLite lock timeout. Actual
HTTP network delay during workspace uploads, filesystem crash/close failures and
external deployment behavior remain separate qualification. The focused regression
uses servlet controller calls, deterministic input barriers and real local SQLite;
connection fault injection uses a closed test proxy.
