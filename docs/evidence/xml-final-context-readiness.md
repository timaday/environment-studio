# Final move context and XML readiness correction

Candidate base: `f4f2add0c6f3b46c00e03a8f0b11102e62163a50`.
The isolated integration tree adds 29 independently reviewed candidate files.
Fixed manifest SHA-256:
`86d70bfa675c1144bc9b757552a3f2cf6dbdc46efae0e004d9919ffab6bc14a6`.
This is local development evidence, not a release or database qualification claim.

## Behavior and scope

Containment moves compare the original source-parent context with the final
assembled destination-parent context. Source-bound element provenance locates the
final moved root after all scalar/ancestor/creation edits. The check includes unused
and default namespace bindings, nearest inherited language/space including absence,
and the entire ordered base chain plus trusted document base. Fragment-root namespace
insertion remains bounded; no whole-DOM rewrite or normalization was introduced.
Destination parent admission retains its original digest check. Old destination
context caches no longer stand in for final context.

Compiler and XML parser share the same framework-free list of five unsupported
element namespaces. Such paths produce Incomplete/XML_NAMESPACE_UNSUPPORTED at the
namespaceUri pointer, at every path position. Attributes and unused namespace
declarations remain independently supported. The current native compiler mechanism
is integer 2, including binding digests, publication/projection eligibility and the
guarded package schema. Other mechanism revisions and the readable v2 snapshot codec
are unchanged. Historical records/replays remain readable; they cannot newly publish,
create a plan, project fresh XML or authorize an execution package.

Only independently invented existing mock declarations and literal synthetic XML
were used. No actual application model, imported private fixture or DB credentials
entered this change. All current/target value-bearing types retain safe rendering.

## Actual RED, GREEN and mutation evidence

Pinned Maven 3.9.16 and Ubuntu OpenJDK 21.0.12 ran in the isolated author/integration
worktrees. Commands use `-B -ntp -f backend/pom.xml`; focused runs add
`-pl server -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=... test` with actual
core and parser tests selected, never zero-test success.

- Initial production RED: 11 failures, zero errors. Five namespace cases incorrectly
  compiled ready; three changed-destination contexts incorrectly completed; three
  final-corrected destination contexts incorrectly refused. Log:
  `/home/tim/.tmp/es-xml-correction-red-20260909.log`.
- Mechanism RED: four failures, zero errors, for current revision pin and old revision
  publication/projection/package admission. Log:
  `/home/tim/.tmp/es-xml-mechanism-red-20260909.log`. Corrected focused run: 52 PASS
  (7 core, 1 parser, 44 server).
- Expanded move suite: 19 PASS. It covers ancestor edits, created parents, simultaneous
  edits to both parents, exact matching created contexts, unused created namespace
  refusal, and positive language/space overrides with independent literal final XML.
  Four early created-parent cases initially omitted the required retained-child
  disposition; their MISSING_ENTITY_DISPOSITION failures were test setup mistakes,
  not production RED. Explicit dispositions corrected them.
- Three deliberate guard mutants were killed: omitted final-context check (13 failures
  in 19 cases), final context compared with itself (13/19), omitted compiler namespace
  capability check (5/11). All had zero errors and the exact source bytes were restored.
  Logs: `/home/tim/.tmp/es-xml-mutant-*-20260909.log`.
- Independent Python framing recomputed binding, publication and guarded package
  identities. Logical/profile compatibility and payload bytes stayed unchanged.
  `python3 fixtures/guarded-package-v1/execution-digest-oracle.py` and
  `python3 fixtures/guarded-writer-v1/canonical-oracle.py` passed. Both native binding
  fixture families were recalculated using `fixtures/native-v2/digest-oracle.py`.
- First full integration run found one missed historical expected-digest literal in
  GuardedPackageInspectorTest; the independent oracle supplied its correction. The
  second full `mvn ... verify` passed **509 tests**, zero failures/errors/skips:
  136 core, 7 qualified parser, 328 server, 38 standalone supervisor. Assembly and
  hostile-environment distribution checks passed. Log:
  `/home/tim/.tmp/es-xml-integrated-verify2-20260909.log`.
- Node 24.20.0: `npm ci --prefix frontend`, check, test and build passed: 7 component
  tests plus 22 contract/schema tests. Log:
  `/home/tim/.tmp/es-xml-integrated-frontend-20260909.log`.
- Repository integrity, diff whitespace and all 11 Python guard tests passed.

## Independent review and OCI artifact

The database author independently reviewed the fixed 29-file candidate and ran
74 focused tests (7 core, 1 parser, 66 server), all PASS. Source provenance, complete
final context, element-only namespace capability, compiler registry consistency,
historical replay/readability and new eligibility gates were checked. No blocking
finding remained. Two initial filtered attempts hit the parser's required-tests
rule; adding its actual FifthEditionClassifierTest fixed the test selection without
changing code. Review log: `/home/tim/.tmp/es-xml-independent-review3-20260909.log`.

The pinned multi-stage Docker runtime build passed all 509 Java tests, frontend
checks, schema tests and production assembly. Local image tag:
`environment-studio:xml-review-20260909`; image ID:
`sha256:56cb74e3baa6282c90bedb8dc3446a801786d1c6d2da4c5e12112cfc5aa63a72`.
Its source label records base f4f2add plus reviewed XML manifest 86d70bfa; it is not
presented as an image of a subsequently created commit. The separate supervisor
artifact target was exported through the same cached build. Both artifacts remain
unqualified for native database execution; the registry is empty.

## Remaining qualification

Protected container smoke passed: non-root read-only-rootfs startup, static UI,
health, demo capability/mutation denial, private workspace permissions and refusal
checks, plus explicit offline schema upgrade. Log:
`/home/tim/.tmp/es-xml-oci-smoke-20260909.log`.
Separate supervisor ZIP SHA-256:
`fb4ae6b88b522350d2889247872c6ee185500901bb9d4e901e1d1e1a07d89c75`.

The whole staged diff was reviewed for independent mock provenance and cumulative
disclosure. Repository content/integrity, whitespace and all 11 Python guard tests
passed before commit. No new hosted workflow, native-client TLS/commit,
measured maximum heap, GHCR, deployed identity provider or HiveForge result is
claimed. The compiler revision change does not certify a database combination;
fresh read-operation matrix evidence is maintained separately. Release gates remain
blocked until their required evidence exists.
