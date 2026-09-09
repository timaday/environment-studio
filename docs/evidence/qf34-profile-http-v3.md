# V3 profile draft and history HTTP evidence

9 September 2026. The reviewed profile routes save exact physical-only JSON/YAML
drafts against an owned historical definition publication and expose bounded
current/history/catalog responses. They share the original definition route's
four-operation registry, lease, body/response ownership and final authenticated
commit. Profile projection is historical structural data; new publication,
capture/composition, plan integration and operational availability remain open.

The [contract](../contracts/workspace-profile-http-v3.md) preceded implementation.
The author worked from45d6bec plus the exact reviewed profile draft dependency.
Source9 manifest SHA256 is
`12f0fefc5b4272453c766f71350d58aa479bbddb88f5f7d006c0e7c57e4e2e89`;
contract4 manifest is
`cc322a0f3ce0e81c59dd0cba7603afe9899cc997a020570e6a9a595279e6b875`.
The independent lead reviewed this fixed candidate and combined it with85c9472
and three additional controls. Integrated14 manifest is
`5f6015d8b4c578e8023e54ab4655e92d740464c2375f609548fba63485f8fc16`.
All fixtures and historical publications are independently invented test data;
no test publication establishes current compiler eligibility.

## Actual behavior and investigation

The author RED used actual mock OIDC/Tomcat: authenticated profile listing
expected200 and received403 on the closed baseline, one assertion and zero
errors. The implementation opens only PUT/list/current/exact-history profile
routes. Strict wrapper decoding rejects unknown/duplicate/nested/trailing input,
invalid Unicode, noncanonical command fields and byte limits. Owned mutable
input encodings are wiped on success/refusal. No legacy reader was widened.

Author final focused verification passes18 tests; the broader selection passes71
including existing30-second unread-output controls. New real network cases cover
JSON/YAML history and replay after definition edits, ownership, CSRF/Host/Origin,
DEBUG canaries, partial-body logout, and two definition plus two profile blocked
transfers sharing four slots. Disconnects recover capacity. Controller/SQLite
controls verify postcommit revocation prevents subsequent source disclosure and
the same original command replays after reauthentication; logout while actual
SQLite replay waits prevents a later durable commit.

The first SQLite scheduling test incorrectly waited for append while its own
IMMEDIATE lock blocked replay first. It failed one assertion; this was a test
prerequisite error. The corrected control witnesses the actual worker in replay
before logout and lock release. Removing authenticated commit then persisted a
profile after logout, producing a meaningful assertion failure. Five other
compiled guard mutants were killed: trailing input, source-byte limit, body
wiping, historical projection kind and profile read partition. A widened
publication security matcher survived because no handler admits that operation;
it is recorded as a survivor, not counted as a kill or permission to widen routes.

Independent review found no production defect. Three additional controls verify
request identity includes the exact definition reference, foreign history/catalog
isolation, and exact1024-digit expanded native revision strings in model, catalog
and history. The first numeric positive oracle used an invalid1024-character
token; the existing parser permits256 lexical characters. Correcting only that
test to valid `1e1023` verifies the full1024-digit expansion. The final independent
selection passes21 tests with zero failures, errors or skips. The invalid first
oracle is not described as a product defect or implementation RED.

## Verification and limits

Commands use Maven3.9.16/JDK21.0.12 and Node24.20.0 in owned external archives:

```text
mvn -B -ntp -f backend/pom.xml -pl server -am -Dtest=IndependentV3ProfileHttpTest,V3ProfileControllerTest,V3ProfileRequestReaderTest,V3ProfileBoundaryTest,ArchitectureTest,MinimalRuntimeTest test
node --test scripts/schema.test.mjs
npm run check --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
```

Independent selection21, schema40, frontend40, checking and production build
pass. Full `mvn -B -ntp -f backend/pom.xml verify` on the integrated14 candidate
passes1072 tests:266 core,7 parser,592 server and207 supervisor, with zero failures,
errors or skips, in3m10s at20:26:37 BST. Assembly checksums and hostile-environment
launcher checks pass. Log: `es-profile-http-full1-20260909.log`; archive:
`es-profile-http-review-77oa5ky5`. All14 frozen file hashes match root after copying.
Only implementation-status prose changed subsequently. Whole staged-diff
provenance/disclosure review, repository integrity, content guard, Python11 and
whitespace checks pass. The content guard alone does not establish provenance.
No browser behavior changed. Current exact-image, remote CI, GHCR and HiveForge
evidence do not follow from these focused checks.

Author evidence SHA256:
`1dcf111229fefc637e92225b63b9db4ca66d86bd03e1419289704536cf84de08`.
Independent review SHA256:
`d8da089e512266d3c94d8f6ed4c9012a6bcb01aecc0dd74d877cb34571378c00`.
External logs `es-v3-profile-http-{red1,green7,green8}.log`,
`es-profile-http-independent{1,2,3}-20260909.log` and mutant reports preserve the
actual distinctions above. Review/integration began around19:20UTC and reached
the passing combined build at19:26:37UTC, including correction of the independent
numeric oracle. This approximate seven-minute interval excludes the author's
implementation work; no speed-up is claimed.

Business: an owned draft is inspectable/replayable without donor values or frozen
computed membership. Engineering: existing shared ownership and original commit
authority apply. QA/security: independent history, reference, byte, revocation,
capacity and redaction witnesses pass within the recorded local scope. No new
publication, complete operator flow, native-client admission, whole-process
resource qualification or release readiness is established.
