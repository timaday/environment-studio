# D02a hosted OIDC/session boundary — local evidence

2026-09-08. Base `c8473ad68333fc4c3e684100073e132f5d97c2b2`, isolated writer
worktree `/tmp/es-d02-session`. Candidate is the assigned-file uncommitted diff;
lead owns integration, OpenAPI and independent review. This is local development
proof, not governed HiveGate execution or real-provider/platform qualification.

## Implemented boundary

Hosted mode uses Spring Boot's managed OAuth2 client starter (Spring Security
7.1.1) and its authorization-code/OIDC implementation. PKCE is explicit through
[`OAuth2AuthorizationRequestCustomizers.withPkce`](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/oauth2/client/web/OAuth2AuthorizationRequestCustomizers.html).
State, nonce, signed ID-token issuer/audience and key verification run through the
framework; there is no production mock-identity route or header authentication.
Provider discovery uses the configured issuer. Only configured HTTPS origins and
provider endpoints are accepted outside explicit loopback test configuration.

Configuration: `studio.mode=hosted`, `studio.security.public-origin`,
`studio.security.issuer`, `studio.security.client-id`, and platform-managed
`studio.security.client-secret`. Public origin is an origin without a path,
userinfo, query or fragment. Test-only HTTP requires both the `oidc-test` profile
and `studio.security.allow-test-http=true`; it permits localhost/127.0.0.1 only.
Unknown modes, missing configuration, forwarding processing, insecure cookie
settings, URL session tracking and servlet-session persistence refuse startup.
The callback uses the fixed public origin, independently of forwarding headers.
No external deployment configuration or secret was supplied or changed.

`GET /api/v1/session` requires session authority and returns the contracted
no-store CSRF/session projection. `POST /api/v1/session/logout` requires exact
Origin and valid CSRF, revokes session authority and returns 204 after conclusive
cleanup, or a safe 503 `SESSION_CLEANUP_INCONCLUSIVE` while quarantine remains. Unknown
API routes are denied. Capability output reports the actual mode while both
inspection/export remain false. Demo mutation denial remains covered.

The framework-free ledger binds ownership to issuer/subject and atomically
refuses a second active owner session. A shared reservation budget caps pending
and authenticated servlet sessions at 64. Full login admission creates no new
session/cookie; repeated starts reuse the pending reservation. Failed callbacks
release their own reservation. Active-session login starts and unsolicited
callbacks are refused without invalidating active work. Pending/authenticated
sessions have 30-minute idle and eight-hour absolute limits; the API reports the
absolute limit from the reservation's creation. A one-second sweep performs
background expiry, with synchronous expiry before request authority checks.

Session-ID rotation and Secure/HttpOnly/SameSite=Lax cookies are enabled. The
application does not retain an authorized-client access/refresh-token store or
an extra provider back-channel session index; only local logout is in scope.
The verified principal remains in ephemeral server-session memory. Spring's
repository interfaces use their documented null/empty absent-entry results;
these are not successful authentication results. Source code has no token/secret
logging or disk persistence. Lifecycle cleanup hooks receive an exact session
lease; later observation/operation services must attach their own cleanup.

## Independent mock provenance and oracles

All issuer URLs, client/principal names, canaries and test tokens were invented
for this task. RSA signing keys are generated in test memory. The mock provider
serves discovery, authorization redirect, JWKS and token endpoints over loopback
HTTP. Tests follow the authorization redirect through a real HTTP client and
perform real HTTP code/token/JWKS exchanges; callback and application APIs use
Spring's full security filter chain through MockMvc. These tests do not use the
`oidcLogin()` authentication helper. Controlled OIDC principals are used only in
separate servlet lifecycle units, explicitly labelled in their source.

The independent mock endpoint verifies the submitted PKCE verifier against the
authorization challenge and checks client authentication. Adverse signed tokens
have deliberately wrong nonce, issuer, audience or signing key; altered state is
also refused. Actual servlet HTTP separately verifies cookie flags. No real
provider, database, application declaration, credential or private fixture was
read or reproduced.

## Actual TDD and commands

Maven: `/tmp/es-lead-toolchain/maven/bin/mvn`, version 3.9.16, Java 21.0.12.
Commands ran from the isolated worktree, with output captured outside the checkout.

| Command / experiment | Observed result |
| --- | --- |
| `mvn -B -ntp -f backend/pom.xml -pl core -Dtest=SessionLedgerTest test` | RED: 6 failures against renderable ledger stubs. GREEN: 6/6 passed after implementation. |
| `mvn -B -ntp -f backend/pom.xml -Dtest=SessionLedgerTest,HostedBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test` | Hosted RED: 5 tests failed/errored against missing hosted boundary; core 6 passed. Subsequent GREEN passed the actual mock protocol. |
| Same focused command after adding active-session callback/relogin case | RED: new case expected 403 but received a redirect. GREEN after preserving active-session authority. |
| `mvn -B -ntp -f backend/pom.xml -Dtest=SessionLedgerTest,HostedSessionsTest -Dsurefire.failIfNoSpecifiedTests=false test` | RED against reservation stub; GREEN after shared pending/authenticated reservation implementation. |
| `mvn -B -ntp -f backend/pom.xml verify` | Initial restored candidate PASS: 18 core tests and 20 server tests, packaging and Spring Boot repackage. No skips. |
| Manual CSRF-disable mutant, same focused core/hosted test command | Killed: missing-CSRF logout expected 403, mutant returned 204. Mutation restored; full verify passed again. An initial mutation run also failed at missing CSRF projection; the oracle was reordered to test direct logout denial first. |
| `git diff --check` | PASS. |

An initial focused reactor invocation selected only the server test and stopped
at the core's no-tests gate. The command was corrected to select actual core and
server tests; the gate was not disabled. The initial HTTP exploration ran against
the unchanged demo boundary and failed as expected; hosted RED evidence above
uses the completed independent mock-provider test configuration.

Packaged startup checks executed `/usr/lib/jvm/java-21-openjdk-amd64/bin/java
-jar backend/server/target/environment-studio.jar --server.port=0` with each:

- `--studio.mode=unsupported`: nonzero exit, `UNSUPPORTED_RUNTIME_MODE`.
- `--studio.mode=hosted`: nonzero exit, `HOSTED_CONFIGURATION_REQUIRED`.
- `--studio.mode=hosted --server.servlet.session.cookie.secure=false`: nonzero
  exit, `UNSUPPORTED_SESSION_CONFIGURATION`.

No generated security password appeared. Both synthetic provider credential
canaries were absent from captured final Maven/test output and tested session/
cookie response bodies. Test source contains only intentionally synthetic values.
This bounded trace does not claim comprehensive browser/disk/heap leak analysis.

## Remaining qualification and measurement

Actual HiveForge TLS/proxy ingress, real issuer, operator browser flow, deployment
lifecycle and provider logout integration are NOT RUN. Workspace metadata,
object ownership endpoints, DB credentials, inspection and export remain absent;
no cross-owner object/DB/storage qualification is claimed. Cleanup hooks are
observed for normal and failing logout/idle/absolute expiry as described below.
Real external-work cancellation and container-specific invalidation races still
need service-specific evidence. Java line/branch coverage
is not configured in this starter; no coverage percentage is claimed. The single
manual CSRF mutant is targeted G06 evidence, not exhaustive mutation qualification.

Writer elapsed time approximately 22 minutes; blocked on contracts/dependencies:
0 (independent core work continued during coordination); local rework approximately
3 minutes. Integration/reviewer time is excluded. No speed-up/token-usage claim.


## Independent-review correction: cleanup failures and logging

The reviewer reproduced a cleanup defect: revocation removed all expired leases,
then the first thrown callback prevented later callbacks from running. A second
independent hook could also be skipped after a servlet/hook exception. The lead
approved the cleanup clarification in the hosted-session contract before this
correction: revoke first, attempt all independent work, retain typed inconclusive
state and owner/global capacity, permit at most three explicit attempts, and never
restore authentication. Completed hook obligations are not retried. Exhausted
quarantine remains until process restart; restart is not external cleanup proof.

`SessionLedger` now retains bounded cleanup reports, atomically counts revoked
inconclusive leases against capacity, and refuses that owner's new authentication.
The servlet adapter separately tracks servlet invalidation and each idempotent
hook; one failure does not skip another. Pending-login invalidation failures also
retain their shared slot. Internal lifecycle methods expose immutable reports and
bounded explicit retry; no retry HTTP route or automatic retry loop was added.
Logout owns revocation/cleanup before Spring clears authentication and cookies;
an inconclusive result returns no-store 503 with the constant safe code.

Actual additional commands (same Maven/JDK/worktree):

| Command / experiment | Observed result |
| --- | --- |
| `mvn -B -ntp -f backend/pom.xml -Dtest=SessionLedgerTest,HostedSessionsTest -Dsurefire.failIfNoSpecifiedTests=false test` | RED: first expired cleanup threw and prevented the independent expired session's attempt. |
| `mvn -B -ntp -f backend/pom.xml '-Dtest=SessionLedgerTest#idleBoundaryExpiresAndCleansBeforeReAdmission,HostedSessionsTest#firstFailedHookMustNotSkipIndependentHookOrRestoreExpiredAuthority' -Dsurefire.failIfNoSpecifiedTests=false test` | RED: first failing hook prevented the second independent hook. Existing core case selected so reactor no-tests gate remained enforced. |
| `mvn -B -ntp -f backend/pom.xml -Dtest=SessionLedgerTest,HostedBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test` | Logout RED: expected 503, observed 204. GREEN after explicit inconclusive logout reporting. |
| Same focused core/hosted command with captured-log assertions | CSRF logging RED: Spring DEBUG response converter exposed a 29-character CSRF-token prefix through the record's generated `toString()`. GREEN after safe `SessionView.toString()` returns REDACTED. |
| `mvn -B -ntp -f backend/pom.xml -Dtest=SessionLedgerTest,HostedSettingsTest -Dsurefire.failIfNoSpecifiedTests=false test` | RED: detailed request logging accepted. GREEN after hosted startup refusal for both `spring.http.log-request-details` and `spring.mvc.log-request-details`. Managed Boot 4.1.1 metadata confirms these property names. |
| Manual CSRF-disable mutant against corrected candidate | Killed again: missing-CSRF logout expected 403, mutant returned 204; source restored. |
| `mvn -B -ntp -f backend/pom.xml verify` after restore | PASS: 21 core and 25 server tests, no skips, package/repackage complete. |

New adverse cases demonstrate two expired sessions with the first callback
throwing; multiple hooks with the first failing; a failing servlet invalidation
that still attempts all hooks; owner/global quarantine; exhausted three-attempt
budgets; successful retry that skips completed hooks and never revives the old
session; and logout refusal while session authority remains revoked.

The inherited `DEBUG` environment variable was present and Spring emitted DEBUG
web/client converter output. It was not changed, and logging was not suppressed
to conceal the test result. Captured stdout/stderr assertions now cover the
literal client-secret canary, its encoded Basic client-authentication value, the
access-token canary, every issued signed ID token and its first 80 characters,
and session CSRF-token prefixes on successful/failed protocol/session paths.
Provider tokens did not reproduce a leak; the CSRF response-record prefix did.
No token values are reproduced in this evidence. A safe response `toString()`
fixes that observed path while preserving the intentional authenticated JSON
response. Detailed request logging defaults false and true overrides refuse
hosted startup. Future workspace/DB request/response DTOs still need their own
privacy qualification before those APIs are implemented.

Correction/review rework approximately 15 minutes; no contract blocking time
(independent RED tests continued during the lead's clarification). No further
Git staging/commit or global environment/configuration changes were performed.

## Independent re-review correction: admission versus retirement

A controlled two-thread test reproduced the reviewer's race: authentication had
passed the retired check while holding the slot monitor; expiry selected pending
cleanup outside that monitor before authentication assigned its ledger lease.
The servlet slot disappeared without cleaning the newly admitted lease. RED
reported zero hook calls where one was required.

Retirement and the pending/authenticated cleanup decision now share the same slot
monitor as lease assignment. Cleanup uses the lease captured after acquiring
that monitor. Logout, core invalidation, explicit retry selection and servlet
binding destruction were checked and use the same synchronized retirement/lease
selection discipline. External cleanup remains outside the retirement decision.

The latch-based tests pause admission inside the monitor, start expiry or servlet
destruction, observe the retirement thread blocked on that monitor, then release
admission. Both paths must clean the admitted lease exactly once, leave no pending
cleanup report after success, and allow a fresh session for the same owner.
They use no sleep-based timing oracle; waits have five-second failure bounds.

Actual commands (same isolated worktree and toolchain):

- `mvn -B -ntp -f backend/pom.xml -Dtest=SessionLedgerTest,HostedSessionsTest
  -Dsurefire.failIfNoSpecifiedTests=false test`: RED, one admission/expiry failure
  (`expected 1 cleanup, observed 0`); servlet destruction counterpart passed.
- `mvn -B -ntp -f backend/pom.xml verify`: GREEN after the lock correction,
  21 core + 27 server tests, no skips; package/repackage passed. Existing
  protocol, logout503, bounded cleanup and captured-log assertions remain green.
- `git diff --check`: PASS.

Only `HostedSessions.java`, its test, and this evidence changed in this correction.
Additional rework approximately four minutes; contract blocking zero. This is a
specific application race reproduction, not general container/race qualification.
