# Shared internal v3 validation

Explicit v3 validation now uses the shared plan owner, re-resolves the complete
selected publication and freshly verifies original and retained target XML/proofs.
It reports all ten required checks, every physical count rule and the complete
computed rule checklist. Missing target remains UNKNOWN and differs from a
complete target with no applicable computed rules. Export remains unavailable.
See the [contract](../contracts/plan-validation-v3.md).

The separate ES-PLAN-INPUT-3 fingerprint includes publication, policies, original
and decision pins, complete source digests, draft, mechanisms and rule results.
Computed checks are framed lazily under the original cancellation flag. Existing
v2 fingerprint bytes remain unchanged. Earlier physical graph and computed target
rule refusals remain intact; validation does not admit a rejected target to show
failed rules. Current computed failures remain inspectable through current views.

## Candidate and observed checks

Author seven-file manifest SHA
`12e1a3296b0f9c261002b154ccbd3c4a91bba0f7a759d458cf6641cbbb174033`,
base `9ded177`. Independent review report SHA
`e115b598f2b2df291cf9063db5e742c91384efce2103bf77dccce3abe2f0de9a`.
The separate reviewer read the fixed seven files and added two adverse tests;
no material blocker was confirmed. Integrated eight-file manifest SHA
`f7b34b24e93b776ea59eae1ee5c4692cee09f771d8224dbd8a45c7231f8ce2cc`,
base `0ab24eea7292823dab138faa9fba4c9e3eab1bec`.

Actual Maven3.9.16/JDK21.0.12 results:

- Initial behavior RED: one assertion, zero errors at the default unsupported
  validation scaffold. Initial GREEN passes eight selected Java tests.
- Final author focused21 pass (5 core,1 parser,15 server). Four new core cases
  cover literal v3 rule/input framing, cancellation during lazy rule traversal
  and the unchanged full v2 preimage. Ten server cases use actual invented XML
  for current/target/edited validation, all rule outcomes, optional absence,
  failed target refusal, fresh authority, forged proofs, failed reinspection,
  stale revisions and held work across owner close/reinspection.
- Author full1,308 pass (297 core,7 parser,704 server,300 supervisor), zero
  failures/errors/skips; assembly and hostile-environment distribution pass,
  10 September2026 at00:31:32BST.
- Ten compiled author mutations fail assertions: remove fresh lookup, reduce
  publication equality, omit full proof verification, ignore inspection state,
  promote UNKNOWN, collapse missing target, omit decision pin, omit rule maximum,
  omit physical outcomes, or omit the live hash check. Exact source is restored
  and21 controls pass. The result collector initially omitted the parser module
  from totals; corrected totals were read from actual logs, without reclassifying
  outcomes or inventing another run.
- Independent18 pass (5 core,1 parser,12 server). Same publication identity/model
  with changed policy refuses; restoring the lookup restores identical validation.
  Swapped original physical origins refuse even when values and derived evidence
  are unchanged. This is an injected fault seam, not a real-projector defect claim.
  Two compiled mutations, digest-only publication equality and omitted full proof,
  each fail one assertion/zero errors. Restored18 and all seven hashes pass,
  10 September00:36:54BST.
- Integrated `mvn -B -ntp -f backend/pom.xml verify` passes1,320 tests
  (297 core,7 parser,706 server,310 supervisor), zero failures/errors/skips;
  assembly and hostile-environment distribution pass,10 September00:42:22BST.

Independent literal whole-input SHA
`baae12671d2727786c086da7d52dc401a34c615a86e492a6871239fe1ac12963`;
mixed computed-rule SHA
`afddb09160c169f760425ee3ca93f6c0a43fafd5e4efb12e2b04f2060141bfdb`;
complete-empty rule SHA
`fa0f16811fd22bdbd481549f61f5d03abf139fb6fc7ce94ecb3e137833c15f57`.
These framing controls supplement actual full XML verification; hashes are never
a substitute for provenance equality.

Setup/oracle failures remain recorded separately: an invalid focused selector,
a physical-count fixture rejected before projection, an attempted failed target
already refused by the materializer, incorrect expected engine rule order and an
invalid policy token. The contract was corrected to preserve the existing target
refusal; production guards were not relaxed. None of these counts as behavior RED
or mutation evidence. The independent initial selector also named a nonexistent
test; it ran nothing and is not included in the18-test count.

Business/UX still requires the complete approved operator flow. Engineering and
security preserve fresh authority, original ownership and separate v2/v3 identity.
QA/RST exercises mixed edits, incomplete evidence, forged origins, cancellation
and recovery. Fixtures and publication witnesses are independently invented and
explicitly test-only. They do not qualify the actual v3 compiler or client/export
admission. Versioned HTTP, current OCI, combined graph/provenance/recomputation/
retained-target resources, client/content/review evidence and deployment remain
open. No new browser or public HTTP qualification is claimed.

## Actual owned mock database workflow

The external shared-plan probe compiled and passed against the integrated eight
files on10 September00:44BST. It uses the actual shared HostedPlanService,
JdbcObservation, PlanContentAdapter, profile merge, comparison, controlled views
and validation with both owned disposable PostgreSQL and Oracle engines over
verified TLS. Current archive class directories precede dependency jars; no old
product class directories are admitted. A labelled in-memory publication witness
is the only admission seam. The actual v3 compiler is still Incomplete with
MECHANISM_UNQUALIFIED, and no runtime registration or publication is enabled.

For each engine, whole and partial workflows inspect both complete documents
using one-shot credentials, compare exact Raw XML and display-only Formatted/
Placeholders, capture physical-only value-free profile source, preview dependency
closure and affected derivations, apply explicit Fresh/Existing reuse with exact
command replay, preserve unresolved target state, then supply fresh values and
placements. Complete target physical/computed views retain fresh contributor
identity and exact final values; unselected siblings and unchanged dependency XML
remain present. Repeated validation is identical, checks without client/content/
review evidence remain UNKNOWN and export refuses.

The preserved lower-level actual XML cases also pass: duplicate-group merge,
last-contributor removal, exact Unicode/whitespace, stale and cancelled work and
empty derived identity refusal. Independent before/after database witnesses match
and no physical sessions remain after successful work or connected cancellation.
Ordinary write-capable mock accounts remain accepted under the closed read-only
operation policy. Test writes occur only in the explicitly owned disposable lab.
Wrong trust and wrong hostname each refuse for both engines; their cleanup remains
INCONCLUSIVE/quarantined until process exit, not falsely complete. Diagnostic and
final-log canary scans pass. The Oracle fixture is below4000 characters; this run
does not qualify maximum CLOBs or combined resource bounds.

External helper SHA
`78c61d7d4daca6c00938536d57d87835e35f440162ba85ae86813b003d25508c`;
safe outcome log SHA
`0072e2dedfd5e4da20f804007d4b702dd13fb187e43c5c778902c15b1fd5d457`.
The external runner records compile exit0/run exit0 and the same integrated
manifest/base above. Mock credentials and raw database material stay outside the
checkout, process arguments and evidence. No production correction was needed;
before compilation the helper's nonexistent cleanup call was corrected to the
existing invalidate hook. This is fixture setup, not a product RED.
