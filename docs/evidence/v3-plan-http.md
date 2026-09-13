# Initial v3 plan HTTP integration

Seven fixed-v3 routes connect hosted creation, current/ID summary, inspection reservation,
one-shot credentials, operation status and cancellation to the shared versioned service. The
destination-owner policy still runs before publication lookup and replay; GET polling cannot
extend idle or absolute session expiry. Actual v3 compilation remains incomplete and fresh
publication refuses. Positive integration cases use explicitly imported test-only publication
witnesses; these confer no runtime qualification or browser availability.

The worker owns original request body, credential submission and encoded/output buffers through
actual closure. Shared metadata capacity and bounded credential HTTP records remain separate
from physical observation permits. Original version/owner checks run before transfer admission,
body access or cleanup polling. Credential HTTP capacity is checked before consuming its
one-shot attempt. Early controller refusals contain no body and report only closed headers;
owned errors stay under their original output deadline. See [HTTP
contract](../contracts/hosted-plan-http-v3.md), [OpenAPI](../contracts/openapi-plans-v3.json)
and [transfer ownership](../contracts/plan-transfers-v3.md).

## Reproductions and review

Only independently invented principals, commands, XML and publication witnesses are used. Pinned
Maven3.9.16/JDK21.0.12 and Node24.20.0 execute in isolated source archives outside the checkout.

- Transport RED4 has two assertions/zero errors against the incomplete implementation. Final focused GREEN32 includes12 new cases and existing output/completion controls. Four compiled body-limit, cleanup-uncertainty, attempted-start and premature-settlement mutations each fail one assertion/zero errors; exact restoration passes14. One intermediate test stream did not support servlet listeners; its fixture was corrected without a production change or a claimed behavior RED.
- Independent transport review reproduces authority loss during ServletOutputStream.isReady(): the old shared output helper started a new write after that probe revoked authority. The shared helper now checks authority/deadline again after readiness and before write/flush. Author RED4 has two assertions/zero errors; corrected focused24 passes. Independent held-resource closure and original error-deadline cases join a final37-case pass. Reverting the readiness check or renewing the error deadline each fails one assertion/zero errors. These controlled callbacks establish ordering; they are not an observed hosted/export exploit. Fixed review SHA590f430de2cd458b92d5efce5c775da5b98f823d5e5b6dd95118311760af2a5d.
- Runtime/polling RED4 has two assertions/zero errors. Final focused18 and the earlier expanded52 including34 actual hosted boundary cases pass. Four compiled version/destination/GET/POST activity mutations fail assertions. Independent controls add policy change before exact replay, alternating GET idle expiry and absolute expiry despite valid activity;11 pass. Removing destination policy or classifying v3 GET as activity fails one assertion/zero errors each, then exact restoration passes11. Review SHA8b0900473e74fff3a50339fec61306ef79022c33061aba7ffa7cc633487d162d.
- Controller RED4 has two assertions/zero errors; final focused47 passes with six new cases. An intermediate test incorrectly expected an already-consumed observation permit to be explicitly closed again; only the oracle was corrected to its reported COMPLETE cleanup and zero duplicate closes. Three compiled operation-preflight, credential-admission-order and malformed-credential-refusal mutations each fail one assertion/zero errors; exact restoration passes8.
- Independent real MockOIDC and socket review reproduces authenticated creation returning403 after valid Host/Origin/CSRF because HostedSecurity omitted the seven routes. The exact supplement adds three GET and four POST authenticated matchers while retaining denyAll. The original actual HTTP witness then passes3 including core/parser controls. Expanded fixed HTTP review passes19 (1 core,1 parser,17 server), including eight actual socket cases. Literal physical counts1/3/0 and computed counts4/6/3 come from the invented XML through the actual content adapter. Replacing computed counts with null is a compiled mutation that fails one assertion/zero errors, then exact restoration passes19. Final test-only revision covers both legal logout schedules:204 if closure wins, or503/exact cleanup code while pending; both require original record settlement, an empty aborted source response, fresh same-owner login and no retained plan. Mandatory204 was an observed oracle failure; the subsequent mandatory503 expectation was corrected after source review found the opposite legal schedule. Controlled held-worker tests separately prove retention. Final review SHA1565447a0e42c38f5838d7e4ba40318908dbe517493bfb41bab53ad9d2658126.
- OpenAPI RED46 has two assertion failures: computed result fields were rejected and the closed500 response was absent. GREEN46 follows the schema/aggregate changes. The four new schema cases preserve common v1 shapes, exact string revisions and nullable/zero computed partitions while refusing caller authority. Header/body branch relationships and combined graph limits still require Java enforcement. Independent review then found that unconditional Content-Length const0 contradicted nonzero owned JSON errors. Correction RED46 fails one assertion for a nonzero encoded error length; candidate2 permits nonnegative integer lengths and retains the early zero-length requirement in its conditional prose. Fresh frontend40/schema46/check/build pass. The independent reviewer verified all three corrected hashes and found no further confirmed source/schema blocker.

Focused Java commands use `mvn -B -ntp -f backend/pom.xml -pl server -am
-Dtest=ArchitectureTest,MinimalRuntimeTest,<selected-tests> test`. New tests are
V3PlanTransportTest, OwnedOutputReadinessTest, V3PlanCreateCompositionTest,
V3PlanPollingBoundaryTest and V3PlanControllerTest; independent tests are
IndependentV3PlanTransportTest, IndependentV3CreationPolicyTest,
IndependentV3PollingDeadlineTest and V3PlanHttpBoundaryTest. Author/reviewer run journals retain
exact selectors, timestamps, original failures and restored manifests outside the checkout.

## Combined gates and limits

Fixed22 manifest overce1c306:
`fd8dff7ae6f082e0bb305a92ea8c9a3a51e3773204323d31cd9e09e53644a464`. All independent added tests
are included. Full `mvn -B -ntp -f backend/pom.xml verify` passes1,488 tests:324 core,7
parser,847 server,310 supervisor; zero failures/errors/skips, assembly and hostile-environment
launcher pass,10 September2026 at03:01:53BST. Full log
SHA`0d4822eedd21de4c937c173b9a607ece02df954c596d33be3437352bfec5913b`.

The earlier full run on candidate1 was stopped (exit143) after the logout test's scheduling
oracle changed. It is not a full pass or a product failure. The complete final run above covers
candidate2. Exact source hashes were checked before and after verification.

Fresh `npm ci --prefix frontend`, `npm run check --prefix frontend`, `npm test --prefix
frontend` and `npm run build --prefix frontend` pass on the fixed OpenAPI candidate:
frontend40/schema46, zero failures. G02 corrected-candidate log
SHA2303c7eb1197c07e113d2c6aea5288096e780bba164fb6875b57a7d11dd3216e. No UI source changed and
this is not a current browser test.

Product review preserves absent/empty/uncertain states and unavailable export. Engineering
review checks original authority, shared capacity, independent physical permits and sole
completion ownership. QA investigation covers malformed bodies, expired/replaced plans, idle
polling, concurrent completion and real socket recovery. The confirmed integration and schema
findings were corrected before the combined gate; intermediate fixture/oracle failures remain
distinguished from product defects. Lead integration/rework ran alongside the independent HTTP
and schema checks; no measured speed-up is claimed.

Semantic commands, complete views, capture/reuse and validation still need v3 HTTP routes.
Native client/export/readback, combined maximum-resource qualification, current
image/browser/remote CI and HiveForge evidence remain open. The retained047d1b0 OCI image
predates this change. New compiler publication remains blocked; no readiness flags or release
evidence are promoted. No Q publication or GitHub upload is claimed.
