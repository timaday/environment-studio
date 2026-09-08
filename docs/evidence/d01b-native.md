# D01b native v2 compiler evidence — 8 September 2026

The bounded v2 adapter now returns a rejected, incomplete or ready-to-publish
compiler result with an immutable logical/binding model and separate versioned
digests. Ready-to-publish is **not** a published workspace revision, observed
graph, qualified database/client or export capability. This implements the
compiler portion of [native definition v2](../contracts/native-definition-v2.md).
Traceability: ES-01, ES-03, ES-04, ES-06, ES-09 and ES-16; local G00/G01 evidence.

## Candidate, boundaries and provenance

Base `68508218825f0eea14048d718e90f08d198d9991`; independent writer worktree
`/tmp/es-d01-native-v2`. The lead clarified during implementation that mechanism
versions are integer frames and projection field-mapping lists may be empty,
producing semantic incompleteness. The corresponding reviewed contract content
had SHA-256 `7caa67aff586d7cb5c60d7fea76a730e6da0677e4e72b40ae74b7684087caab0`.
The writer did not change shared contracts or Git state.

New core code is confined to `core.definitionv2`; the existing v1 model/result
signatures remain unchanged. `server.definition.NativeDefinitionBytesCompiler`
is an explicit v2 entry point sharing the existing bounded parser and a closed,
local schema selector. There is no HTTP dispatch, save/publish action, session
state, database access or new dependency. The server POM change only adds the
canonical v2 schema resource include. Core remains parser/framework/I/O-free;
its ASCII URI grammar performs no network lookup or normalization.

The [native-v2 fixture family](../../fixtures/native-v2/README.md), including both
invented engine bindings and the Unicode/large-integer variant, was invented
from scratch. No actual configuration, XML, locator, schema, private feedback or
renamed private model was consulted. Its provenance manifest registers every
artifact. The family describes future mock one-to-two building blocks; no live
observation or generated SQL execution is claimed.

## Actual TDD and gates

Maven commands used `/tmp/es-lead-toolchain/maven/bin/mvn` 3.9.16 on OpenJDK
21.0.12, from the writer worktree. No shared project artifacts were installed.
Node commands used `/tmp/es-lead-toolchain/node/bin/node` 24.20.0. Frontend
packages were installed only into this worktree using the pinned lockfile.

| Actual command / observation | Result |
| --- | --- |
| `mvn -B -ntp -f backend/pom.xml test`, 16:22:02 +01:00 | Behavior RED: core passed; server 54 tests with 2 assertion failures, 0 errors. The callable v2 stub rejected the ready fixture and the incomplete missing-mapping example. |
| Same command, 16:27:36 +01:00 | Initial GREEN: core 17 and server 54 passed; 0 failures/errors/skips. Java matched the independent Python digest oracle. |
| Expanded `verify`, 16:35:05 +01:00 | Core 44 and server 66 passed; 0 failures/errors/skips. |
| Final test addition, 16:38:32 +01:00 | Test compilation failed because two wildcard imports made `EntityType`/`Document` ambiguous. Imports were made explicit; this was not claimed as behavior RED. |
| `mvn -B -ntp -f backend/pom.xml verify`, 16:39:03 +01:00 | Final GREEN: **core 46 and server 67 tests**, 0 failures/errors/skips; both JARs packaged. Total Maven time 5.122 seconds. |
| `node --test scripts/schema.test.mjs` | 8 passed, including all existing v1/profile cases and 4 new v2 shape cases. |
| `python3 scripts/check_repository.py` | Repository integrity: PASS. |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | 10 passed. |
| Working-candidate content assessment using `check_repository_content.assess` on all owned changed/new files | PASS; no staging or Git upload occurred. This limited check does not establish provenance. |
| `git diff --check` | Exit 0. |

The final Java run includes 29 new core lexical/model cases and 15 new v2 adapter
cases, plus existing architecture, v1 compiler/parser, reviewed XML and demo HTTP
boundary tests. Parameterized cases and additional mutation loops run within
those reported counts. Exact local logs remain outside the checkout at
`/tmp/es-d01b-*.log`. No browser, OCI integration, mutation coverage percentage,
DB qualification or governed HiveGate execution is claimed by this record.

## Digest oracle and covered behavior

[The Python oracle](../../fixtures/native-v2/digest-oracle.py) independently frames
strings, integers, booleans, arrays and objects according to the frozen contract;
it does not import or invoke Java implementation code. These commands reproduced
both committed expected-output files exactly:

```bash
python3 fixtures/native-v2/digest-oracle.py definition.json
python3 fixtures/native-v2/digest-oracle.py unicode-integer-definition.json
```

The base logical digest is
`86a12d3902ab52e747a0ca068bf93820913fbc6fd3829593bd70dc11cab8d09c`;
its independently computed PostgreSQL/Oracle binding digests differ. The second
logical digest is
`e652f3131cf9ea1a0a3121188d26969e1f2a3d532a7d706b462a7bc4a64dab26`;
it exercises a count larger than 64 bits and UTF-8 namespace bytes. Expected
binding values are recorded in the two `*expected-digests.json` artifacts.

Tests verify metadata exclusions, native revision precision, input object order,
declaration/mapping order, per-binding changes, logical field/identity/rule
changes and projection path preservation. Framework-free records defensively
copy nested lists/maps; checked digest/mechanism maps have explicit stable order.
Repeated frozen input and diagnostics compare equally. Uploaded mechanism
metadata and unknown operation/rule vocabulary reject safely.

Semantic adverse cases cover duplicate IDs/typed keys, invalid identities,
unknown endpoints/fields/relations, inverted cardinalities, engine/storage or
column mismatches, overlapping projections, attribute collisions and reserved
namespace names. Missing readable/reference/type mappings, unreadable fields,
unknown sensitivity, multi-target references, root creation/removal and missing
containment mechanisms remain explicitly incomplete. Multiple same-type
projections across documents and a supported containment ancestor compile;
recursive logical containment remains legal but incompletely bound.

Scalar/key tests distinguish absence and empty text, forbid empty identities,
preserve canonical integer text, enforce signed int64 key boundaries and validate
absolute ASCII URI generic syntax without resolving it. Unicode NCName and
namespace bounds, strict parsing refusals, YAML equivalence, source-size limits,
short exponent allocation limits and the 1024-digit expansion boundary are
exercised. Primary syntax references were
[XML 1.0 Fifth Edition](https://www.w3.org/TR/xml/) and
[RFC 3986](https://www.rfc-editor.org/rfc/rfc3986).
The schema is validated independently with AJV and byte-compared with its packaged
resource. Both parsers load only tool-selected local schemas; uploaded content
cannot select remote schema resolution.

## Integration and remaining work

The Docker Java stage already copies the schemas directory, which covers the new
canonical schema. It additionally needs the independently invented
`fixtures/native-v2` directory before Maven tests, because those tests read the
canonical fixture/oracle artifacts. The lead owns that Dockerfile edit and OCI
verification. Fixtures must remain test-stage inputs, not runtime resources.
The UI build stage already copies fixtures and schemas.

Trusted mechanism versions are compiler capability metadata. Actual generic
instance graph/count evaluation, complete inventory observation, field mutation
preconditions, selected-parent resolution, one-to-two orchestration, publication
storage, profile v2 integration, driver/client qualification and guarded export
are subsequent work. Existing-attribute replacement is the current mechanism;
missing optional attributes and removal of attributes are not silently supported.
This fixture's ready result does not claim those downstream stages ran.

Elapsed writer work was approximately 30 minutes, including contract review,
schema/model implementation and adverse/oracle investigation. Contract questions
did not block independent work. Final import correction/reverification took less
than one minute; lead integration and independent-review rework are separate.
No measured parallel speed-up is claimed. The lead owns the final fixed-candidate
review, cumulative provenance review, staged-content gate and any publication.

## Independent-review correction: static XML mechanism limits

The reviewer reproduced ready results for a 129-step path and 257 required
attribute mappings even though `xml-span-v1` refuses depth above 128 and more
than 256 attributes per element. The compiler now returns publication-phase
`XML_DEPTH_UNSUPPORTED` or `XML_ATTRIBUTES_UNSUPPORTED`, retaining its checked
model without granting publication authority. This follows the explicit limits
in [lossless XML](../contracts/lossless-xml.md), lines 19–20, and its self-contained
insertion namespace requirement, lines 41–43. No schema or digest framing changed.

The attribute lower bound includes required field mappings and reference mappings
whose relation minimum is positive. Optional mappings do not inflate the bound.
For creation, explicit namespace declarations needed by the element and required
attributes also count, including an empty-namespace reset; the implicit `xml`
namespace needs no declaration. For observed document roots, required nonempty
namespaces must be declared there. Observed non-root elements may inherit them.
Distinct namespaces are counted once, so aliases do not manufacture extra static
requirements. Actual source length, element count, lexical-token count, optional
attribute presence and concrete declaration placement still require runtime
observation/writer qualification; compiler readiness does not claim those checks.

Behavior RED: `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml test`
finished 16:54:08 +01:00 with core 46 passing and server 70 tests, **3 assertion
failures, 0 errors**: all three excessive-budget cases incorrectly returned ready.
Log: `/tmp/es-d01b-limits-behavior-red.log`. An earlier helper static-context
compile error was corrected before this behavioral RED and is not RED evidence.

Final GREEN: the same Maven executable with `verify` finished 16:55:13 +01:00,
**117 tests passed** (core 46; server 71, including native-v2 19, v1 28, XML 21,
HTTP 3). Log: `/tmp/es-d01b-limits-final-green.log`. Boundary tests cover depth
128/129; 256/257 required attributes; positive/zero reference minima; optional
fields; creation namespace overhead; implicit XML namespace; empty-namespace
creation reset; and document-root versus inherited namespace declarations.
Both independent digest oracles and the inward architecture test still pass.
Node24 `--test scripts/schema.test.mjs`: 8 passed; repository integrity and
`git diff --check`: passed. No dependencies, fixture artifacts or Git state changed.
Correction effort: approximately 5 minutes elapsed, under 1 minute helper rework,
no blocked time. This remains a compiler-only slice, not D03 or export qualification.
