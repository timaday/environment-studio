# D06b2 — initial hosted plan HTTP boundary

Candidate is based on `4ccc4f5333f4e42eaa575e178387bf58f02b1438` in the
isolated `/tmp/es-d06b-plan-http` worktree, with lead-owned reviewed D06b1,
HTTP/destination contracts and FGA correction overlays. No Git state was changed.
The implementation exposes only the nine initial routes in
`hosted-plan-http-v1.md`; inspection/export capability flags remain false.
No comparison, profile-preview, validation, export or database-write route was added.

## Boundary and ownership

`PlanController` admits a live owned lease before reading any body. Credential
claims consume the one-shot operation before allocating a reader/thread; four
metadata-reader permits and the single semantic-command scratch are acquired
before body allocation. Dedicated immediately started threads own servlet async
readers; no executor or job queue carries requests or credentials. ReadListener
callbacks only signal readiness. Quiet and trickle bodies share a hard deadline.
Disconnect, cancellation and expiry retire the reader; late callbacks cannot
restore it. Command scratch is retained until an executing materializer returns,
even if its admission handle is concurrently closed.

GET plan/status/destination polling captures a live lease without touching idle
expiry. Host/Origin/CSRF and default-deny route rules remain enforced. Safe DTOs
contain only the contracted summary/status data. Typed semantic commands retain
opaque observation handles until replay/ownership checks have completed; original
closed command values determine the process-local HMAC. Exact replay after discard
returns the prior acknowledgement without resolving retired handles or restoring
content. Batch upsert preserves every unmentioned entity and placement decision.

Trusted destination configuration is exact and immutable, owner-allowlisted and
TLS-only. It validates bounded certificate-only files, actual JKS format versus
suffix, normalized nonsymlink paths and exact physical/policy identity fields.
An absent destination list leaves plan services unavailable. No startup database
authentication occurs. Read-only mounting is an external deployment qualification,
not inferred from POSIX permission bits. Only `plan-command-v1.schema.json` is
added to the server resource include list; integrate that POM include serially
with the lead's later guarded-package schema includes.

## Actual RED and GREEN evidence

Commands ran from this worktree with Java 21 and
`/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml verify` unless
explicitly marked core-only. Logs are external `/tmp/es-d06b2-<name>.log` local
proof, not governance authority. No skip or test-gate disabling flags were used.

| RED log stem | Observed failure before implementation | Subsequent GREEN |
| --- | --- | --- |
| credential-red | Valid owned credential decode refused; byte/deadline codes wrong | credential-green full reactor |
| destinations-red | Exact-owner visibility and unknown/gapped configuration behavior failed | destinations-green full reactor |
| capture-red | Polling renewed the idle deadline | later full reactor capture test |
| credential-expiry-red | Expiry inside reader still opened one database port (core-only `-f backend/core/pom.xml test`) | credential-expiry-green core; full submission-green |
| readiness-red | Closed/cancelled/unready servlet source behavior failed | readiness-green full reactor |
| command-reader-red | Valid closed typed command refused | command-reader-green full reactor |
| command-authority-red | Exact retired command replay refused (core-only) | command composition/full reactor |
| routes-red | Expected destination route 200, got 404 | routes-green full reactor |
| oidc-routes-behavior-red | Real OIDC session still received 403 on new route | oidc-routes-green full reactor |
| oidc-pkce-log-red | Captured DEBUG output contained authorization-code/PKCE form canaries | oidc-pkce-log-green full reactor |
| command-close-red | Closing admission during a latched render admitted replacement scratch | command-close-green core; admission-and-config full reactor |

`oidc-routes-red` was a test compilation typo, not behavioral RED. The first
actual socket workflow also found missing explicit PathVariable names (500);
that integration defect was corrected and rerun. Neither is misrepresented as
an intended failing behavior test.

The actual logging leak was the pinned Spring Web 7.0.9
`DefaultRestClient$DefaultRequestBodyUriSpec.logBody` path using
`org.springframework.web.client.DefaultRestClient`. Bytecode inspection confirmed
its DEBUG request-body formatting; the captured failing run had 18 credential-
bearing lines under that category. No values are reproduced here. Hosted
composition clamps that exact sensitive category to INFO and verifies the
effective level, even with its explicit test configuration at TRACE. Other Spring
web DEBUG remains enabled. Successful and failed real mock-provider flows check
authorization-code and received PKCE verifier canaries retained only in fixture
memory, plus client/access/ID/CSRF and database credential canaries. Fixture
teardown clears captured verifier values. No global logging configuration changed.

## Actual transport and semantic matrix

`HostedBoundaryTest` retains genuine mock issuer authorization-code/PKCE/nonce
roundtrips and adds random-port Tomcat/socket requests, not only mock principals.
The controlled observation port supplies independently invented native-v2 data.
The tests publish a definition, create an owned plan, reserve/submit inspection,
then atomically create a second palette and retarget a glyph across two documents.
Both resulting XML documents are compared to independent structural-target expected
files. Original Current remains available internally and target values preserve
case, whitespace, XML-sensitive characters and supplementary Unicode.

The socket tests additionally exercise missing CSRF, foreign ownership before
body consumption, duplicate and malformed credential consumption, a quiet body,
a continuously trickling body, disconnect, explicit cancel, four occupied metadata
readers plus a fifth 429, cleanup before readmission, and lease expiry during a
quiet credential read. A fresh login cannot recover the old operation. Polling
has a separate deterministic non-touch deadline test. Failed reinspection revokes
inspection authority while retaining display-only state. Discarded command replay
and operation status/cancel remain lease-owned.

Actual `PlanContentAdapter` composition tests exercise a stale preview refusal
without revision change, selected Existing plus previously materialized Fresh
provenance, preservation of prior entered values and unselected siblings, and
replay after discard without target resurrection. The separate previously reviewed
profile reuse test still requires explicit field choices before no-op completion.
All nine closed command variants are parsed; direct command tests check absent
selection, unknown field, duplicate batch and preservation of unmentioned choices.

Maximum-shape decoder evidence partitions 20,000 entities into eight batches of
2,500, each with 256 explicit fields and 256 explicit references. Streaming input
has independent exact byte accounting; each partition fits 128 MiB, and eight
commands fit the 256 replay-entry allowance. This is decoder/partition evidence,
not a claim that counters measure heap or that maximum retained service graph,
old/new scratch and response heap have been qualified together.

Independent Python native framing/HMAC produced the checked wire-command vector
`4b9c9dd77f26175af81ff9458452d4f950f55ceae7caa76bc2f443292325b852`
for synthetic lease/plan, revision `9007199254740993`, an explicit bind-field
command and mixed-case supplementary-Unicode value. The preview digest test uses
the lead's separate checked-in Python oracle/fixture (expected `f129f8c6...`).

Three temporary production mutants were each run with the full core module test
command and restored: early scratch release (one assertion failure), checking a
retired plan before replay (NOT_FOUND error in the exact replay test), and dropping
unmentioned draft selections (one assertion failure plus one refusal error).
Logs are `mutant-scratch-release`, `mutant-retired-replay` and
`mutant-preserve-unmentioned`. These are additional sensitivity evidence; the
original D06b1 mutants remain separately attributed.

## Provenance, scope and limits

All data is independently invented. Existing native-v2, profile-v2 and
structural-target mock fixtures retain their provenance. The added TLS fixture is
only a self-signed public certificate; its generated key existed in process memory
and was never saved. No actual application model, source, credential, TLS trust or
production destination was used. Private inputs were not requested.

The lead's FGA overlay consists of OracleMetadata, OracleFgaGuardTest,
OracleFgaQualification, d04-fga-guard evidence and the disposable qualification
harness metadata grant. Those byte-exact overlay files are outside this author's
change manifest. Existing D06b1 source/cleanup guards and both additional reviewed
reader-expiry/cancellation tests are preserved. Shared contracts/schemas/preview
fixtures are lead-owned overlays, not changes authored in this slice.

Remaining qualification: integrated protected Docker smoke (G08) is lead-owned;
actual configured TLS trust-store connections and actual account/IdP environments
remain external qualification. The unchanged read-only SQL policy matrix and the
lead's actual shared-permit engine evidence are not rerun or claimed here. Full
maximum retained-plan/scratch/response heap remains unqualified. No UI flow or
export authority is enabled by these tests.

## Final candidate checks and time accounting

At 2026-09-08 21:27 UTC, final
`/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml verify`
passed **375 tests: 131 core, 7 parser, 237 server; zero failures/errors/skips**
(`/tmp/es-d06b2-final-verify.log`). `git diff --check`,
`python3 scripts/check_repository.py`,
`python3 scripts/check_repository_content.py`,
`python3 -m unittest discover -s scripts -p 'test_*.py'` (10 tests), and
`python3 fixtures/plan-http-v1/preview-oracle.py` passed in this worktree.
The pattern checker is only a limited provenance guard.

Measured wall time from completion of the first credential RED log to final
checks is 39.3 minutes; preparation before that timestamp is not precisely
instrumented. Recorded RED-to-GREEN completion intervals were 70.9 seconds for
the OAuth logging correction and 15.9 seconds for the command-close correction;
these are wall intervals, not exclusive labor measurements. Contract/overlay
coordination overlapped independent implementation; exclusive blocked time was
not instrumented. No parallel speed-up claim is made.
