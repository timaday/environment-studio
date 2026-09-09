# D06b4 — retained observed destination context

This backend candidate adds closed, owned observed identity to the current/by-ID
summary and rejects installation without current adapter identity/policy evidence.
It does not implement operator labels, approve a browser design, establish a live
connection indicator or perform fresh readback. Base: `19be08f57ff0a3b1366d4b660f7f0c6b220ba179`.
Candidate: `/home/tim/.tmp/es-observed-destination-20260909`.

The plan-context contract was updated first. A complete registered observation must
carry the matching engine/destination, canonical fingerprint, closed matching
observed/expected physical identities and current metadata/cleanup pins. Application
admission checks these before projection and retains an immutable identity copy
only alongside successful complete content installation. The adapter remains the
source of executed database checks; the application does not authenticate by
reading evidence literals. Expected identity and the other adapter evidence never
enter the summary. Stale retained context follows the inspection-validity latch;
revocation blocks its reads and normal retired-content cleanup releases it.

All fixtures are independently invented. Existing application lifecycle mock ports
now include explicit current-policy identity evidence and canonical mock digests;
lower-level projection fixtures remain projection-only. No database declaration,
runtime profile or private input was imported. No database or JDBC behavior changed.

## Actual checks

The first test harness omitted the required reserved observation permit and stopped
with four OBSERVATION_REFUSED setup errors. The first implementation attempt used
that same bad harness; neither result is RED or GREEN. After correcting the mock
port and restoring the exact base service, **four assertion failures, zero errors**
showed SUCCEEDED instead of REFUSED for missing identity, retired account-purity
policy, expected-only identity and a malformed fingerprint. Log:
`/home/tim/.tmp/es-observed-destination-red2-20260909.log`.

The first corrected implementation passed all154 core tests. Added adverse tests
initially used a second simultaneous lease for the same owner, which the session
contract correctly denied; changing the independent caller to another owner fixed
that setup error. One subsequent server test compile lacked a qualified List name;
that was a compile error, not a behavior result.

The final selected run passes **41 tests**:39 core (including ten new identity
cases), one parser and one actual HTTP/OIDC case. Command: pinned Maven3.9.16/JDK21
`-B -ntp -f backend/pom.xml -pl server -am
-Dtest=PlanObservedDestinationTest,PlanLifecycleTest,PlanHandleIdentityTest,HostedPlanServiceTest,FifthEditionClassifierTest,HostedBoundaryTest#observedIdentitySummaryIsOwnedClosedAndRetainsStaleEvidenceAfterFailedReinspection
-Dsurefire.failIfNoSpecifiedTests=false test`.
Log: `/home/tim/.tmp/es-observed-destination-http3-20260909.log`.

Controls cover pre-inspection null, projection refusal, independent exact expected
identity, stale/replaced context, both engine shapes, every required metadata pin,
noncanonical numbers, wrong/missing/extra identity keys, non-scalar/control text,
Oracle GUID forms, immutable/redacted output and foreign/revoked owner refusal.
HTTP verifies both summary routes, exact closed output, no-store and retained invalid
identity after a consumed malformed reinspection body. Credentials are synthetic;
the existing logging-canary and storage assertions remain active.

`node --test scripts/schema.test.mjs` passes24 tests using pinned Node24.20.0.
The new closed schema checks both engines, missing/extra fields, no expected/trust
record passthrough, fingerprints, validity types and Unicode bounds. Log:
`/home/tim/.tmp/es-observed-destination-schema-20260909.log`.

Independent fixed-candidate review, guard mutations and combined verification remain
required. This focused result is not a fresh OCI/browser/JDBC or release qualification.


## Independent review correction

The original14 manifest is
`93f0d726db653e04d971f255c5cfd70472189c0a0ae612269db6472e26371ec1`;
that archive remains unchanged. Independent review reproduced logout completing
between summary capture and first output-stream access, followed by physical
identity publication. Its separate exact19be08f control reproduced the same
inherited transport race with planId, establishing baseline correspondence.
Both are actual one-assertion failures with zero errors. The new identity makes
this inherited gap material to this slice. Other independent44 focused checks
and schema24 passed; three targeted mutants (expected-identity equality, cleanup
pin and stale validity) failed semantic assertions.

Root independently reproduced the publication failure in
`/home/tim/.tmp/es-observed-summary-red-20260909.log`, then adapted the witness to
expect explicit SESSION_REQUIRED and zero bytes; unchanged production again failed
one assertion/zero errors (`...summary-red2...`). No session/domain lock may be held
across output: the witness completes actual HTTP logout inside getOutputStream.

The correction archive is `/home/tim/.tmp/es-observed-destination-correction-20260909`.
It completely encodes the small summary within32KiB, owns a metadata slot until
transfer ends, and checks live owner plus whole-summary equality after encoding,
after obtaining the stream, before each chunk and after transfer. This includes
validity changes that do not increment revision. Uncommitted failure clears the
buffer/length before refusal; committed failure aborts instead of appending JSON.
Encoding scratch is wiped and slots released. Large-view capacity is unchanged.
These boundary checks do not claim to interrupt a blocked kernel/socket write;
backpressure qualification remains open.

The first full candidate14 integration reached160 core/7 parser then failed two
HostedPlanApplicationTest cases: their real service was fed a projection-only
mock observation without identity. Those mock ports now explicitly provide current
identity/policy evidence and canonical mock fingerprint; production admission is
unchanged. No other full-reactor failure is attributed to that fixture diagnosis.

The first summary-correction compile named a nonexistent STALE_REVISION enum; it
was corrected to the existing CONFLICT code. Final focused GREEN passes45 tests:
40 core, one parser and four server (two HTTP/OIDC and both application/profile
workflows). The logout witness now receives SESSION_REQUIRED with zero response
bytes; the validity-only change rejects the stale snapshot despite equal revision.
Log: `/home/tim/.tmp/es-observed-summary-green2-20260909.log`.
Independent corrected-source review and final combined gates remain due.


## Accepted correction and integration

Independent review accepted corrected15 manifest
`a008941aaa58f98253ee3ac486a798fd29a44c3b20916c40572288fe745048e1`.
Its external review archive passed47 tests, including controlled during-write
revocation: uncommitted output is cleared and length reset is requested; committed
output aborts without appending another JSON result. An initial mock response
assertion expected null Content-Length to remove a stored mock header. The pinned
Tomcat Coyote Response control confirms the production call resets length to -1;
the corrected test distinguishes that mock behavior. These two reviewer-authored
regressions were read by the lead and retained in the integrated test source.

The critical verifySummary-disabled mutant was killed by the actual logout witness
(one assertion failure, zero errors). Original identity3 mutants remain applicable.
Independent logs: `es-observed-destination-independent2-green-20260909.log` and
`es-observed-destination-summary-mutant-20260909.log`, under `/home/tim/.tmp`.

Before adding those two regression cases, a fresh `de328a6` archive plus corrected15
passed the full **621-test** Java reactor:161 core,7 parser,363 server,90 supervisor.
Assembly/checksums and hostile-environment launch also passed. Full log:
`/home/tim/.tmp/es-observed-corrected-integration-full-20260909.log`.
The root ran the added transfer regressions against that combined production source;
all seven selected tests passed (two core, one parser, four server).
Log: `/home/tim/.tmp/es-observed-root-transfer-20260909.log`.
Fresh exact-source OCI/browser verification follows the local integration commit;
full heap/backpressure and the remaining operator/native workflow remain active.
