# V3 profile workflow and paged validation

Three owned HTTP routes now expose physical-only profile capture, whole/partial
reuse preview and validation. They use the existing composition command and the
original plan/view/transfer ownership. Capture returns portable source; saving
and publication remain separate operations. Export availability remains false.
The [workflow contract](../contracts/hosted-plan-workflow-v3.md) was reviewed and
frozen before implementation.

## Fixed source and independent review

Base `bf052b631a8ffb1ae4ce0f97dba25622d8cf786b`; integrated24-file manifest
`07021d88df8755b4f5c5044e87c57ac18ef9f964b58cffb63eb0c7d1028b600b`, patch SHA256
`bbc399cf23188c7a3e7a7543c3f6c2463349443a0de11cc21dd204cf0db10fbf`.
External records use prefix `es-workflow-api-candidate3-20260910`.
All24 files match the fixed review. The six production files and canonical
workflow schema are unchanged from the author's first candidate.

Independent candidate1 review found two P2s: copied OpenAPI descriptions stated
the wrong collection limits, and the socket helper silently skipped v3 schema
validation. Both were corrected. Candidate2 review verified23 hashes and1021
unchanged base files; independent12 Java and58 schema checks passed. Candidate3
review verified the additional existing route-test correction and independently
passed10 Java tests. Reports: `es-review-workflow-fixed-pp1bue_u-review.md`,
`es-review-workflow-candidate2-gvwqt_6u-review.md` and
`es-review-workflow-candidate3-w9l7q19x-review.md`. These are external local records,
not release approvals.

## Exercised behavior

Six actual loopback HTTP/OIDC tests use real session/CSRF, runtime composition,
XML projection/materialization and SQLite profile save/history. Observation and
current publication are explicitly test-only ports using independently invented
XML; they do not qualify production publication or a deployed identity provider.

- Capture returns source from the complete original physical graph. Saved source,
  immutable history and exact save replay agree. Whole and selected-root previews
  preserve dependency closure, eight pins, affected derivations and siblings.
- Explicit compose decisions and keep-observed inputs produce complete targets.
  The one-to-two change uses returned Fresh references and placement coordinates,
  explicit identities/values/references and independent literal expected XML for
  both affected records. Comments, CRLF, existing single quotes and unselected
  siblings are preserved. Raw, Placeholders, Formatted, bindings, locations,
  contributors and final validation are checked against actual backend results.
- Validation reports every check and a complete nullable computed-rule count.
  Missing target differs from complete zero. Actual accepted XML with2000 keys
  and32 declarations produces64000 rules; the last row at63999 is reachable.
  Three900000-backslash keys produce96 rows: a100-row response refuses at the
  existing128MiB ceiling, while an explicit one-row retry with the same fingerprint
  succeeds and can reach row95. These are actual XML/HTTP cases, distinct from the
  separate lazy60001-row encoding-unit control.
- Missing/foreign resources, CSRF, malformed input, stalled body, shared scratch,
  logout, worker settlement and recovery retain their refusals. Preview pages
  beyond the end retain the original pins and affected derivations. Fingerprint
  mismatch refuses; an oversized page never becomes a truncated successful page.

Product review concerns were explicit reuse, complete dependencies and inspectable
multi-record changes. Engineering/security review checked bounded original
ownership, value-free capture and no new admission. QA/RST investigated stale pins,
partial selection, complete tails, refusal recovery and exact XML. These checks do
not replace the missing operator-browser, combined-resource or native-client work.

## Actual checks and corrections

Pinned JDK21.0.12, Maven3.9.16 and Node24.20.0,10 September2026:

| Check | Observed result |
| --- | --- |
| Author missing-route RED | Three assertion failures, zero errors; actual403 instead of expected200/404 |
| Author final focused gate | 48 Java PASS |
| Schema-helper RED | Three tests, two assertion failures, zero errors; malformed v3 success was not rejected |
| Corrected focused gate | 55 Java PASS, including six actual HTTP cases and four schema controls |
| Existing route-test correction | 10 Java PASS; malformed new routes400/MALFORMED_BODY, export/origin403 |
| Full integrated Maven verify | 1599 Java:330 core/7 parser/947 server/315 supervisor; zero failures/errors/skips; distribution and hostile launcher PASS |
| Fresh frontend installation/check/test/build | 40 frontend/58 schema PASS; npm audit reported zero vulnerabilities |
| Repository checks | Integrity and11 Python tests PASS |

The schema assertion now loads canonical repository schemas with remote fetching
disabled. It rejects empty/malformed successful replies, incomplete rule fields,
lossy decimal encodings and XML-invalid Unicode; supplementary XML characters pass.
An intentionally cancelled transfer has a dedicated assertion requiring no body
and no JSON result. Ordinary empty200 responses remain invalid. The test profile
publication witness hashes its synthetic captured source and grants no production
publication authority.

Networknt's default JDK regex engine and an attempted Joni test engine could not
parse the contract's ECMA Unicode escapes. The schema is unchanged; the assertion
uses the [documented GraalJS factory](https://github.com/networknt/json-schema-validator/blob/3.0.7/README.md).
Three pinned25.0.1 dependencies are test-scoped; both lead and independent dependency
trees show all13 transitive Graal artifacts confined to test scope. This executes
only fixed schema regexes over invented test values, with no host access or remote
schema loading. It is not a runtime scripting feature or a vulnerability-free claim.
Both the actual packaged application JAR and supervisor ZIP were inspected; neither
contains Graal, Truffle, Polyglot or the attempted Joni test engine.

The full integrated command was `mvn -B -ntp -f backend/pom.xml verify`, run in the
isolated exact-source archive `es-workflow-review-corrections-pof8ff1v` to avoid
concurrent IDE build outputs. It passed at12:48:16BST in4m06s. Full log
`es-workflow-candidate3-full1-20260910.log` SHA256
`7e3df6646da4899a9c283f5820b948d10506f84d2cb5111f7d24ebbd4f564223`.

Retained intermediate failures are distinguished from behavior RED: a missing
packaged test schema, incompatible regex engines, a non-digest publication witness
and incorrect validation of a cancelled response were test setup errors. The first
full integrated run reached947 server tests and failed one stale assertion expecting
unimplemented capture/validation routes403; supervisor verification was not reached.
The first correction used the wrong error-code oracle; it was corrected to the
existing MALFORMED_BODY contract without changing production code.

Five author mutations were compiled and restored in a separate copy. Missing
admission rollback produced a real CAPACITY error on recovery; missing-v3 fallback,
fingerprint guard removal, null-to-zero count and truncated total each produced an
assertion failure. All original candidate hashes were restored. No failed compile
was counted as a killed behavior mutant.

Author work was approximately24 minutes plus setup, with roughly10 minutes of
fixture rework. Lead review corrections to candidate2 took25.8 minutes wall time,
including dependency/setup investigation and parallel waits. Candidate2 review's
measured verification/report portion was96.6 seconds. These observations are not
a velocity or parallel speed-up estimate.

## Remaining qualification

The current compiler still refuses new publication; this change adds no production
qualification witness. React integration, guarded native execution, hosted export
and readback, combined deployment workloads, actual supported client/database
qualification and HiveForge remain required. The retained `c3b891a` image predates
this workflow and the definition-capability correction; its successful startup
smoke is not evidence for either change or workload capacity. No release gate is
closed by these local API tests.
