# D06b1 internal hosted plans — worker evidence

Candidate: isolated `/tmp/es-d06b-hosted-plans`, explicit implementation base
`9fbc8f14ce08132f439efc092b14515fecdf04e3` (lead advanced the initial
`86624da289ab585c3482a430a582aab745716fc9` by shared contract changes only).
Lead-owned hosted-plan contract overlays clarify separate scratch accounting
(`7c17d44e81ebc8dab3ec9f8465c2602533a61c7a`), nonempty password authentication,
and exact lease replay before retired-plan/stale checks. No shared contract,
HTTP route, capability flag, composition root, dependency or Git state was changed
by this worker. This is local development evidence, not governed execution or
production qualification.

## Implemented boundary

The framework-free plan application service owns lease-bound in-memory state,
small HMAC command replay records and bounded operation records. Only the
read-only workspace bridge loads owned immutable, currently supported published
definitions/profiles. A compiler-ready draft is insufficient. The bridge never
recompiles stored source, publishes, writes or performs current-revision lookup.
The server content adapter uses the existing graph, structural XML and portable
profile adapters; no caller supplies an observation or export authority.

An atomic SessionLedger guard checks exact lease, idle and absolute lifetime
without a request or touch. Authentication retains the pending login's original
absolute deadline centrally; an expired pending login is refused before admission.
All plan authority transitions take that guard before plan state. XML, profile,
credential reading, JDBC and cleanup work run outside those authority locks.

Credential-free observation reservations own JdbcObservation's existing four
physical permits, shared with direct observe. Submit consumes once before filling
bounded owned character buffers. Every credential path clears owned buffers;
terminal operation records detach plan/permit/resource references. Cancellation
and stale/expired completion cannot install results. Inconclusive original cleanup
retains capacity, and status can recognize original cleanup completion without
re-observing or restoring inspection validity. Session cleanup remains inconclusive
while owned render/read work has not returned; the session ledger retains its
existing maximum-three-attempt policy.

Complete observation plus independent projection installs current and exact no-op
target together. Any failed/cancelled/expired/malformed inspection sets the validity
latch false; previous content is display-only. Accepted draft changes advance the
revision and remove the old target before rendering. A complete target is independently
reprojected and its identities mapped back to original Existing or Fresh provenance.
Profile whole/partial preview and composition preserve earlier explicit decisions,
Fresh identities and unselected siblings. Portable capture uses the server adapter's
private-constructor Accepted result and returns a draft with definition reference;
it does not save or publish.

Safe summaries/status, bounded entity pages and explicit-disclosure single-document
Raw/Placeholders/Formatted comparison remain internal. Placeholders replace qualified
attribute spans only and explicitly admit that unmapped concrete content remains.
Raw is exact; formatting is display-only. Secret/unknown or unreadable structured
fields are masked; raw/formatted documents refuse mapped unreadable fields.
Validation computes its own input fingerprint, reports every RequiredCheck and
compiled count rule, and cannot export. CLIENT_CAPABILITY, REVIEW and unqualified
artifact content-policy intent remain UNKNOWN. There is no D07 writer/client
capability or HTTP authority in this slice.

## Observed RED, then GREEN

All commands ran in this worktree with Maven 3.9.16/Java 21 at
`/tmp/es-lead-toolchain/maven/bin/mvn`; no skip or test-gate-disabling flags.

| Log under `/tmp/` | Actual failing behavior | Command scope |
| --- | --- | --- |
| `es-d06b-session-red.log` | Initial permissive guard allowed expired and mismatched leases to enter transitions; two failures | `mvn -B -ntp -f backend/core/pom.xml test` |
| `es-d06b-deadline-red.log` | Central authentication deadline was 18:20 rather than pending-login 18:00 | `mvn -B -ntp -f backend/pom.xml test` |
| `es-d06b-reservation-red.log` | Newly declared reservation boundary refused instead of reserving physical capacity | full reactor `test` |
| `es-d06b-plan-red.log` | Initial application stub accepted retired authority and a second live plan | core `test` |
| `es-d06b-submit-red.log` | Initial submit stub left operation RESERVED instead of installing complete inspection | core `test` |
| `es-d06b-draft-red.log` | Initial draft boundary accepted edits without inspection authority | core `test` |
| `es-d06b-composition-red.log` | Initial merge lost prior Fresh decisions and admitted a colliding Fresh slot | core `test` |
| `es-d06b-render-cleanup-red.log` | Cleanup returned conclusive while owned materialization remained blocked | core `test` |
| `es-d06b-retired-replay-red.log` | Exact successful draft replay after discard incorrectly threw NOT_FOUND | core `test` |

Corresponding restored GREEN logs include `es-d06b-session-green.log`,
`es-d06b-plan-green.log`, `es-d06b-submit-green.log`,
`es-d06b-composition-green.log`, `es-d06b-render-cleanup-green.log` and
`es-d06b-retired-replay-green.log`. The definitive final command was:

```
/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml verify
```

`/tmp/es-d06b-final-verify.log`: BUILD SUCCESS, **337 tests**
(**120 core / 7 qualified parser / 210 server**), zero failures/errors/skips;
finished **2026-09-08 20:35:21 UTC**, 19.361 seconds. This includes the existing
real mock-provider OIDC/PKCE/nonce and DEBUG boundary tests, existing JDBC logger
and cleanup tests, and the new internal application tests. No new HTTP OIDC flow
is claimed because no plan route was added.

Intermediate failures not mislabelled as gate passes: the first combined green
attempt exposed servlet idle bookkeeping after authentication; the subsequent
admission-race test was updated to expect refusal when pending login actually
expires, while retaining the live destruction/admission race. A new bridge test
initially used an invalid invented policy spelling and was corrected to the existing
closed `protected-self-contained` vocabulary; that fixture setup failure is not
claimed as production TDD.

## Adverse coverage and independent outputs

- Exact live lease, foreign owner, idle boundary, central pending-login absolute
  deadline, transition-versus-revocation latch and cleanup-blocked revocation.
- Shared four physical reservations plus fifth refusal before factory allocation;
  unused close, one-shot consumption and direct-observe sharing.
- Concurrent credential submissions have exactly one body reader and one operation;
  malformed Unicode consumes the attempt, clears buffers and does not connect.
- Monotonic reservation expiry, late successful cancellation, expiry during blocked
  observation, failed-inspection display-only latch and successful reinspection.
- Original cleanup quarantine, status completion without retries/reconnection,
  and cleanup refusal until local materialization returns.
- Four logically full 32 MiB retained plans can still replace a target using separate
  scratch admission; fifth plan and oversized entered values refuse before adapter
  work. Replay/operation history exhausts without eviction or credential reading.
- A target is absent during blocked materialization of its new revision, not only
  after an incomplete result. Exact replay precedes stale/retired-plan checks and
  never reinstalls a retired plan; retained operation status remains lease-owned.
- Actual existing XML adapters produce the independent two-document expected
  structural XML, then partial profile reuse selects a prior Fresh palette while
  preserving entered case, original provenance and the unselected glyph. Whole
  preview, portable capture, stale source digest, incomplete target and comparison
  modes are exercised.
- An independently assembled Python HMAC/native-frame vector pins the command
  domain, lease/plan boundaries and case. Expected HMAC is
  `47161ad3ce18698f53fe31c381dc2a7834acf560c42b84c3b1a8bfd266773cec`.
  The key and vector are independently invented test values, not runtime material.

Five targeted mutants were killed and restored: live guard, one-shot submission,
cancellation precedence, old-target visibility and Fresh provenance. Logs are
`/tmp/es-d06b-mutant-{live-guard,one-shot,cancel-wins,fresh-provenance}.log` and
`/tmp/es-d06b-mutant-old-target-strengthened.log`. The original old-target mutant
**survived** final-state assertions (`es-d06b-mutant-old-target.log`); the added
latch-based during-render assertion killed it. Core probes ran the complete core
module; the Fresh-provenance probe ran full reactor tests. Final full verify ran
on restored production code.

Final captured verification output contained zero selected credential/source
canaries. New source/value DTOs have safe `toString` representations; password
case assertions use boolean comparisons with generic failure text. This does not
qualify future HTTP response/body logging or browser credential handling.

## Other gates, provenance and remaining qualification

Actual PASS: `python3 scripts/check_repository.py`,
`python3 scripts/check_repository_content.py` (known-pattern check only),
`python3 -m unittest discover -s scripts -p 'test_*.py'` (**10 tests**), and
`git diff --check`. No staging or commit was performed.

New mock owners, clocks, credentials, concurrency barriers, sources and commands
were independently invented inline. Existing independently invented fixture
families `fixtures/native-v2`, `fixtures/profile-v2` and
`fixtures/structural-target` supply reviewed declarations and independent expected
XML. No new fixture family, private-derived input or real database content was
added. The bridge test deliberately uses a non-source sentinel to prove that
published history is not recompiled; storage integrity remains the separately
reviewed D01c boundary.

The SQL, metadata policy, snapshot and driver-logging bodies of D04 are unchanged.
The prior both-engine matrix remains evidence for that unchanged code. The lead
assigned targeted actual both-engine shared-permit lifecycle qualification to
integration; it was **not rerun by this worker**. No DB container was changed.
Docker/G08, deployment heap qualification, new plan HTTP/body-decoder/OIDC wiring,
UI workflows and D07 export remain **not run/not enabled** here. Logical byte-budget
tests are admission/accounting evidence, not a claim of exact JVM heap use or
maximum deployment memory qualification. The lead's future poll/status filter must
capture authority without calling the existing touching `current(request)` path.

Measured interval from first observed guard RED (20:11:37 UTC) to final verify
(20:35:21 UTC): **23 minutes 44 seconds**. Initial reading/setup and active versus
rework time were not separately measured; no speed-up is claimed. Rework included
the idle/admission correction, fixture-policy spelling, surviving-mutant test gap,
local-render cleanup and lead-confirmed retired replay ordering. No external
blocked interval was observed; integration qualifications are explicitly assigned
follow-up work, not completed worker evidence.

## Independent review correction — explicit profile value authority

The reviewer reproduced a profile reuse failure from an empty draft: selected
original entities did not receive complete field/reference decision maps. The
initial repair inferred KeepObserved; lead contract review correctly rejected
that inference. Profile selection must leave new value decisions Unresolved.
The final merge initializes complete Unresolved maps for every selected target,
including selections without relation proposals, preserves prior explicit choices
and Fresh provenance, and applies only the explicitly proposed reference targets.
Already-present containment edges do not create unnecessary physical moves.

Actual full-reactor commands (no skipped gates or test-selection flags):

- `mvn -B -ntp -f backend/pom.xml test`, log
  `/tmp/es-d06b1-review-reuse-red.log`: original adapter reuse failed completeness.
- Core containment RED `/tmp/es-d06b1-review-containment-red.log` observed missing
  child disposition; `/tmp/es-d06b1-review-containment-noop-red.log` observed an
  unnecessary physical move for an unchanged containment edge.
- `mvn -B -ntp -f backend/pom.xml test`, log
  `/tmp/es-d06b1-explicit-values-red.log`: the inferred-keep repair incorrectly
  produced a complete target from newly selected original entities.
- `/tmp/es-d06b1-review-final-verify.log` failed a diagnostic expectation: the
  existing compiler reports FIELD_NOT_EDITABLE for unresolved immutable identity
  fields before checking UNRESOLVED_FIELD. No compiler change was made; the test
  now asserts that actual refusal and separately verifies complete unresolved
  decision shape, then explicit KeepObserved choices and exact original XML.
- Final `mvn -B -ntp -f backend/pom.xml verify`, log
  `/tmp/es-d06b1-review-authority-final.log`: **339 tests passed**
  (**121 core, 7 parser, 211 server**), zero failures/errors/skips,
  finished **2026-09-08 20:47:57 UTC**.

Only PlanComposition, its core test, the real-adapter HostedPlanApplicationTest
and this evidence changed. The tests use existing independently invented mock
fixtures and new generic inline containment cases. No private input, database
policy, JDBC lifecycle or other adapter implementation changed. Earlier
qualification limitations remain unchanged. Review/rework was not separately
timed; original final verification to this corrected final verification elapsed
12 minutes 36 seconds, including review and investigation, not continuous coding.

## Credential-reader expiry/cancellation review correction

HTTP preparation exposed a pre-authentication gap in this internal service: a
credential reader could return after lease expiry or cancellation and still
invoke the reserved observation port, although target installation refused later.
The original worktree reproduced both cases with an injected clock and a reader
that cancels its own operation. No database or real credential was used.

`mvn -B -ntp -f backend/core/pom.xml test` produced actual RED in
`/tmp/es-d06b1-preauth-expiry-red.log`: both expired/cancelled reader tests expected
zero port invocations but observed one. This is a core-module RED, not a full
reactor gate. The service now checks the non-touching live lease and operation
state before invoking the reader and immediately before invoking the port. Both
checks release their short authority lock before I/O. Refusal closes the unused
permit, clears owned credential buffers and follows existing terminal cleanup;
no expired/cancelled plan gains authority. Existing asynchronous cancellation and
cleanup races remain covered, plus a pre-submission expiry case never reads the
body. The original service has no HTTP submission-handle extension in this fix.

Final `mvn -B -ntp -f backend/pom.xml verify` passed **342 tests**
(**124 core, 7 parser, 211 server**), zero failures/errors/skips, log
`/tmp/es-d06b1-preauth-final-verify.log`, finished **2026-09-08 20:56:22 UTC**.
Only HostedPlanService, PlanLifecycleTest and this evidence changed from the
previous corrected candidate. Repository/content/diff checks passed again.
The preceding corrected verification to this final verification elapsed
8 minutes 25 seconds, including separate HTTP preparation; active correction and
blocked time were not separately measured. All earlier qualification limitations
remain explicit; no new real-engine or deployed HTTP claim is made here.
