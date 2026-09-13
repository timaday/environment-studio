# Hosted v3 semantic command integration

The fixed-v3 command route joins the existing typed command reader, shared plan
service and authoritative target recomputation. It checks original owner/version
before admitting the existing full scratch and one separately bounded semantic
HTTP record. The worker retains both through parsing, execution and Ack transfer.
The registry now permits four metadata, four credential and one semantic record;
these do not add physical observation permits or another full scratch budget.
See [command contract](../contracts/hosted-plan-commands-v3.md), [transfer
ownership](../contracts/plan-transfers-v3.md) and [OpenAPI](../contracts/openapi-plans-v3.json).
Current compilation/publication remain incomplete. Explicit test-only publication
witnesses confer no runtime availability; export remains unavailable.

## Reproductions and independent review

All examples use independently invented XML, principals and operation credentials.
Pinned Maven3.9.16/JDK21.0.12 and Node24.20.0 run in isolated archives outside the
checkout. Original failures, selectors and fixed/restored hashes remain in local
author/reviewer journals.

- Registry RED5 has three assertion failures/zero errors against credential-class admission. GREEN35 passes. Independent simultaneous contender and mixed-family retirement controls pass18; removing the single semantic bound admits nine contenders instead of one and fails an assertion. Exact restoration passes18.
- Body RED4 has two assertions/zero errors against the old small body mode. Closed semantic mode permits134217728 bytes under one original30s body deadline; small bodies remain16384 bytes/10s. GREEN28 passes. Exact-limit and one-over streams use bounded generated whitespace, without claiming valid command grammar or heap qualification. Three compiled limit/deadline/premature-resource-close mutations each fail one assertion/zero errors; exact restoration passes4. Independent caller tests exercise retained scratch through held output/error.
- Command wire compatibility passes14 including three new cases: PUBLIC upsert, incremental Unicode binding, batch edits and exact replay/collision across discard/replacement. Entire expected XML and computed tuples are literal independent oracles. This extends coverage of existing behavior; no invented RED is claimed.
- Caller RED3 has one assertion/zero errors against an unsupported scaffold. GREEN28 passes with five caller tests. Busy semantic admission rolls back the exact core scratch; busy core admission allocates no HTTP record. Malformed/trailing bodies leave state unchanged. A held Ack retains both admissions; output failure does not roll back the already applied command. Omitting rollback close is a compiled mutation with one assertion/zero errors; exact restoration passes28. An earlier invalid test enum was a setup compile error, not behavior RED.
- Independent real MockOIDC/CSRF/socket RED3 reaches the new command but receives403 because the exact security matcher is absent. Adding only that POST matcher preserves denyAll and other future-route refusals. Final expanded28 passes, including three new actual HTTP cases and eight original HTTP cases. Edit/recompute returns distinct current4/6/3 and target4/6/2 node/membership/pair counts, preserves exact XML and returns original replay Acks after discard/replacement. Opaque handles come from a real core page, explicitly not a v3 HTTP view. Foreign/V2/CSRF/closed-grammar refusals preserve state. A partial body blocks another owner's command while metadata remains available, then logout settles the original worker and restores capacity. Legal204 or pending503 logout schedules both require eventual closure and empty aborted output. DEBUG logs and workspace files pass synthetic credential/OIDC/CSRF canary checks. Initial accessor compilation and two mistaken expectations that discard increments revision were test setup/oracle errors; production discard semantics were preserved.
- OpenAPI RED47 has two assertion failures for the missing eighth route/schema. Fresh frontend40/schema47, checking and build pass. The request reuses the exact closed v1 command schema; caller owner/model/computed/target/validation/export authority remains invalid. Previously corrected error-length semantics remain. Independent source review finds no blocker in the fixed body, schema or security supplement. Root independently reviews the caller and wire tests; the caller author does not claim independent review of their own files.

Focused Java commands use `mvn -B -ntp -f backend/pom.xml -pl server -am
-Dtest=ArchitectureTest,MinimalRuntimeTest,<selected-tests> test`. New cases are
V3SemanticTransfersTest, IndependentV3SemanticTransfersTest, V3SemanticBodyTest,
V3CommandWireCompatibilityTest, V3PlanCommandControllerTest and
V3PlanCommandHttpBoundaryTest. Fixed supplement review
SHA`d11bec2e052359959052fea1b53951f72026bf4ff0bdeaa9d35f3dd532324053`.

## Combined gates and remaining work

Fixed17 manifest over6acb1e2:
`500be21c2132c1bea656dad5d2a620047bb6afebeb968ace740684690fafc2c7`.
Full `mvn -B -ntp -f backend/pom.xml verify` passes1,506 tests:324 core,7 parser,
865 server and310 supervisor; zero failures/errors/skips, assembly and hostile
launcher pass,10 September2026 at03:26:14BST. All fixed source hashes match before
and after verification. Full log
SHA`3059f37ad04b9919e4a3450f4cddd999e53f1c6739aa75f103cf6b08a29545d0`.
Fresh `npm ci --prefix frontend`, `npm run check --prefix frontend`, `npm test
--prefix frontend` and `npm run build --prefix frontend` pass on the fixed OpenAPI
candidate: frontend40/schema47; log
SHA`43a5befd105709d2c3c469fd23d56451162e3dd7634f15247ef6cf7a88fd988c`.

Preparation for the next route separately reproduced a direct v3 view-cancellation
defect: a held materializer could install a late target before its outer view check
refused. Its isolated correction and review are not part of this17-file candidate;
they precede hosted materialization. No hosted/export exploit is claimed. Complete
versioned views, profile operations and validation routes remain required, followed
by the remaining operator workflow, native clients, readback and combined retained
proof/resource qualification. No UI change, current browser result, new image,
remote CI, GHCR or HiveForge result is claimed. Retained image047d1b0 remains older.
