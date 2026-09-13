# Cleanup recovery and bounded plan transfer helpers

Completed HTTP work now checks every unfinished asynchronous cleanup family before
spending a session retry. Pending or failed checks retain quarantine without using
the three-attempt budget. Actual final cleanup still decides recovery; idle retained
plan state does not suppress it. Both plan/workspace runtime hooks forward passive
readiness. See [session cleanup](../contracts/hosted-session.md).

Two internal helpers prepare the upcoming v3 transport. The
[encoder](../contracts/plan-encoding-authority.md) checks original authority and
the caller's original deadline during serialization, wiping retained chunks on
refusal. The [completion owner](../contracts/plan-transfer-completion.md) reports
settlement only after worker closure, outside its attempt lock, and retains reported
uncertainty in every later result. Original constructors and legacy routes remain
supported. This change adds no v3 plan route, registry or availability flag.

## Reproductions and fixed review

All cases use invented inputs. Maven3.9.16/JDK21.0.12,10 September2026:

- Cleanup RED:10 cases,2 failing cases/zero errors. Repeated legacy-style completion
  notifications exhaust all3 attempts while an actual workspace record is held;
  its final closure leaves same-owner login denied. This is an actual session/
  registry schedule, not an actual mixed HTTP scheduling reproduction.
  Fixed13 manifest
  `de90a04ae1d10f73667b60e24e4cc324462044c2ff97ccc67ae7456d121a73d4`
  passes50 cases, including34 actual HostedBoundary HTTP/OIDC cases and14 new
  controls,01:51:29BST. Four compiled guard mutations fail2/4/1/1 assertions,
  zero errors; exact restoration passes16. A mistaken RESERVED-work oracle and
  unrefreshed Spring provider setup error were corrected and are excluded from RED.
- Independent cleanup review adds two cases: false passive readiness cannot replace
  actual cleanup or its attempt ceiling; a delayed old-lease query permits manual
  cleanup and replacement login without affecting the fresh lease. Focused/restored
  23 pass, including five existing ledger completion cases. Moving queries under
  the slot monitor fails one assertion/zero errors. All13 author hashes restored.
- Encoding RED:8 cases,3 assertion failures/zero errors,01:47:36BST. Fixed3 manifest
  `9a36fb200032c5cc3d80aa1d880f5010d4f2078f47235bb2107f3e6ba1bf4163`
  passes21 focused cases,01:49:14BST. Four new controls verify pre-serialization
  revocation, authority/deadline loss after actual prefix allocation, wiping and
  safe errors, exact32768-byte UTF-8 output and one-byte overflow. Three compiled
  mutations each fail one assertion/zero errors; restored9 pass. Independent
  literal escaping/multibyte and separate-encoder controls pass11 with one compiled
  wipe mutation. Review SHA
  `46f29bfd6d4bb49214ea01086eea46d3807af46128abfd081689183fda58afa6`.
- Completion initial RED:17 cases,4 assertion failures/zero errors,01:51:04BST;
  the11 legacy cases, including actual Tomcat timeout/error, pass. Review then finds
  two additional defects before integration. A reentrant callback notifies while
  the outer attempt holds its lock: lead RED21 cases/1 assertion and independent
  error-callback RED1 assertion. Deferring that notification fixes the lock issue.
  A further independent control reproduces observer INCONCLUSIVE followed by
  direct finish COMPLETE; retaining the same uncertainty at both result sites fixes
  that contradiction. These are controlled callback schedules, not observed Tomcat
  reentrancy or a demonstrated hosted exploit. Earlier fixed candidates and actual
  failing tests remain preserved outside the checkout.
- Final completion3 manifest
  `2d4675ef98faba37c8c6369a8c513907950e2d7dfff456d17ba42e6fa54d8321`
  passes independent/restored25 (8 author,4 independent,11 legacy,2 core/parser),
  including actual Tomcat controls. Four lead guard mutations fail2/1/3/2 assertions,
  zero errors; restored21 pass. The independent two-site uncertainty revert fails
  one assertion/zero errors and restores25. Final review finds both defects fixed,
  no confirmed remaining blocker; report SHA
  `d1d5dee78407e6b807bb85a4d3b99ab18c9fc76033c1a7f0be8de238e35498b0`.

Contracts preceded implementation; cleanup queries perform no status/handle polling,
resource release or authority transition. Encoding cannot preempt an arbitrary
blocking getter; production callers must provide closed typed replies. Worker closure
is the caller's actual resource-cleanup obligation, never inferred from onComplete.
An accepted Servlet completion does not establish delivered response bytes.

## Combined verification

Fixed22 manifest over473a344:
`dd9f942ea9dbbad4a53d25bee537524e36cdb7398b8be343719c10adc28afcf2`.
It includes all three reviewed changes and eight independent new cases. The only
shared-source merge adds the reviewed cleanup query to HostedPlanService while
preserving the previously committed v3 summary. Full combined Maven verification
passes1,427 tests (324 core,7 parser,786 server,310 supervisor), zero failures/errors/
skips, assembly and hostile-environment launcher,10 September2026 at02:05:48BST.
The lead reviewed the combined source and test provenance: generic engine/transport
contracts and independently invented principals/text only. Product review retains
visible unknown/uncertain states; engineering review preserves original ownership
and budgets; QA review includes stale notifications, reentrancy, cancellation and
recovery, with controlled schedules distinguished from actual container evidence.

Frontend/schema source is unchanged, last checked at8b5843d (frontend40/schema42).
Retained image047d1b0 predates this work. New v3 transfer records/routes, compiler
qualification, actual native clients/export/readback, combined resource maxima,
current browser/image/remote CI and HiveForge evidence remain open. No Q feedback
publication, GitHub upload or release qualification is claimed.
