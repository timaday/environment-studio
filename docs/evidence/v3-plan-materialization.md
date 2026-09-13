# Hosted v3 materialization and original cancellation

The v3 materialization route now uses the original full view admission through
parsing, revision pinning, actual target proof, encoding, output and final
verification. COMPLETE, INCOMPLETE and REFUSED remain distinct; materialization
does not increment semantic revision or grant export authority. Original view
checks also govern owned errors after pinning. Full declared diagnostic sets use
the existing128MiB view response limit. See [HTTP contract](../contracts/hosted-plan-materialization-v3.md),
[lifecycle](../contracts/shared-plan-lifecycle-v3.md) and
[OpenAPI](../contracts/openapi-plans-v3.json). Actual compiler publication remains
incomplete; positive cases use explicit test-only witnesses.

## Confirmed cancellation defect and correction

Before this route, a controlled original ViewAdmission.close during v3
materialization signalled a different cancellation flag from the one passed to
the adapter. Scratch correctly remained occupied, but an already computed late
target could install before the outer view check threw CONFLICT. Author RED10
has two assertions/zero errors, with normal completion passing. The correction
forwards the exact original v3 view flag, retaining the existing command branch
and unchanged v2 behavior. The existing final cancellation guard now runs before
either installing a complete result or dropping a previous target on refusal.
Scratch remains held until the executing worker returns.

Author focused36 passes, including v2 command/lifecycle controls. Omitting view
forwarding is a compiled mutation with two assertions/zero errors; exact
restoration passes36. Independent review adds the other branch: first retain an
actual checked XML target, then hold a controlled RESOURCE_LIMIT result. Closing
the original view must prevent that late refusal from erasing the prior target.
Focused11 passes; omitting forwarding fails one assertion/zero errors, then exact
restoration passes11. Review
SHA`83a66b9c8d147a39c5f22cde53c63bd16b4204c63afd8b177b1ab88ca316701b`.
This is an internal consistency/cancellation defect, not a demonstrated hosted
or export bypass. The new route includes the correction from its first integration.

## Route and wire verification

All principals, declarations, XML, values and credentials are independently
invented. Isolated archives use Maven3.9.16/JDK21.0.12 and Node24.20.0.

- Route RED3 has one assertion/zero errors against an unsupported scaffold. Final focused33 passes with nine new controller/transport cases plus existing controls. Both admissions precede input; a failed second admission rolls back only the first. Malformed/stale pre-pin errors remain bounded. Held output retains scratch; inspection-context changes abort without a replacement error. Held materialization with lost pin never acquires output for a lease-only error. Actual unresolved input returns INCOMPLETE; controlled REFUSED and256 distinct129-character references test state and whole-response limits. Four compiled run-scope, pinned-verification, response-limit and rollback mutations each fail one assertion/zero errors; exact final restoration passes33.
- Independent actual MockOIDC/CSRF/socket baseline RED3 returns403 at the materialization route. After fixed controller installation, the same RED remains until adding exactly one authenticated POST matcher. Only materialization is removed from the unsupported-route test. Focused29 passes: three new HTTP cases, existing command/initial HTTP and controller/cancellation controls. Actual XML materializes unchanged at revision2; repeated materialization keeps revision2. A returned command Ack for unresolved tone leads to INCOMPLETE/by-tone at revision3. Entered empty tone leads to actual REFUSED/INVALID_DERIVED_IDENTITY at revision4, then repaired beta yields COMPLETE at revision5 with recomputed4/6/2 counts. Entire unchanged XML and four-field replies are literal oracles. The original opaque handle is still supplied by a core page; this does not qualify an HTTP entity page.
- Actual missing-inspection, stale revision, wrong-version/owner, CSRF and malformed/trailing/numeric/extra-result inputs refuse without installing a target. A partial view body blocks another owner's materialization while metadata remains available. Logout permits either legal204-empty or503/exact pending-cleanup code, followed by original worker settlement, empty aborted output and successful recovery by another owner. Fresh same-owner login cannot recover the retired plan. Synthetic credential/OIDC/CSRF canaries stay out of DEBUG logs and workspace files.
- OpenAPI RED49 has three assertion failures for the missing ninth path/result. Strict schema compilation then identified a missing conditional array type and missing existing schema registration in the test loader; both were corrected without counting them as behavior mutations. Fresh frontend40/schema49, checking and build pass. The unchanged revision-only request rejects caller replay/model/result authority. Closed result tests enforce state/complete agreement, empty COMPLETE diagnostics and complete256-reference output beyond32KiB. Removing the state/complete condition is a compiled mutation with one assertion, then exact restoration passes49.

Independent source review covers the fixed route/cancellation code; the route
author independently reviews root security/HTTP/OpenAPI additions. No author
claims independent review of their own changes. Combined supplement review
SHA`2d7fe8f792204da0aa05172d9f7f55bb637d17caab358660699ff4fc5cfaca81`.
Focused commands use `mvn -B -ntp -f backend/pom.xml -pl server -am
-Dtest=ArchitectureTest,MinimalRuntimeTest,<selected-tests> test`. New tests are
V3ViewMaterializationCancellationTest, IndependentV3MaterializationCancellationTest,
V3PlanMaterializationControllerTest, V3PlanMaterializationTransportTest and
V3PlanMaterializationHttpBoundaryTest. Exact selectors, original failures and
restored manifests remain in external author/reviewer journals.

## Combined result and limits

Fixed16 over963a084:
`758754666dfa49f694f8863524ad07c1c3db6ca74b4f044c69a8689b2332602f`.
Full `mvn -B -ntp -f backend/pom.xml verify` passes1,522 tests:324 core,7 parser,
881 server,310 supervisor; zero failures/errors/skips, assembly and hostile
launcher pass,10 September2026 at03:41:31BST. All fixed hashes match before and
after verification. Full log
SHA`485bbb3d3fd36612006b6d3ade51635423851ddc93a909bad0171018dad4600f`.
Fresh `npm ci --prefix frontend`, `npm run check --prefix frontend`, `npm test
--prefix frontend` and `npm run build --prefix frontend` pass on the exact schema
candidate: frontend40/schema49; log
SHA`1d933e5a65e812e565ae86607571d5eb4f17ef276dbdd350546a6d42ecf85f99`.

Next are actual HTTP document inventory and physical entity pages, then remaining
comparison/profile/validation routes and operator workflow. Native client/content,
fresh readback and combined retained-proof/resource qualification remain required.
No current browser, new image, remote CI, GHCR or HiveForge result is claimed;
retained image047d1b0 is older. Export and actual v3 publication remain unavailable.
