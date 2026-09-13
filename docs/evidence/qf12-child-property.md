# QF-0001/0002 — child fields and additive compatibility

9 September 2026. The amended findings are one capability extension under
[child-property-v1](../contracts/child-property-v1.md). Original rejection was
consistent with the old advertised capability. The integrated candidate
implements the declared mapping paths and passes the checks below. This is
local development evidence; no Q feedback was uploaded to GitHub.

## Behavior and review

The closed direct/child union runs through native declaration reading, compilation,
per-binding digests, immutable workspace storage, projection, source locations,
scalar changes, creation, moves, final reprojection and concrete/placeholder views.
The shared resolver separates entity origin from the actual child value attribute,
checks direct-child cardinality, protects every possible discriminator location
and refuses physical aliases across a document. Scalar changes cannot synthesize
absent properties. Creation groups distinct fields on the same selected child,
uses deterministic namespaces and retains separate nonentity child provenance.

The server adds `xml-child-property-v1=1` only to bindings declaring the locator.
The checked definition carries the union of its binding dependencies. Publication,
history and selected-package checks use those dependencies; uploaded metadata
cannot grant availability. Unknown vocabulary rejects, known unsupported
capabilities remain incomplete and actual missing/ambiguous XML refuses observation
or materialization. Native compiler revision remains 2; old base mechanism meanings
and direct normalization remain unchanged.

Independent review found two concrete readiness/resource defects and they were
corrected before integration:

- A generated property child could also be a separately declared entity without
  an explicit fresh slot or its required fields. A valid invented current graph
  reproduced `REQUIRED_FIELD_MISSING` on attempted parent creation. The compiler
  now returns `CHILD_ENTITY_CREATION_UNSUPPORTED` when creation is declared for
  that combination. Removing creation does not impose this extra refusal.
- A 16,296-byte schema-admitted colliding declaration caused 19,900 diagnostic
  additions for 200 value sites. The independent bounded counter failed before
  the fix. An ordered scan now emits at most one collision diagnostic per later
  site, preserving its location and all discriminator checks. This was an
  allocation-count witness, not a maximum-heap or OOM experiment.

The lead reviewed the adapter author's fixed ten files and independently exercised
whole/partial profile capture, dependency preview, plan composition, fresh values
and exact two-document targets for both invented binding declarations. The four
reuse cases retain unselected siblings and reject stale placements. Two further
cases put identity and a namespaced value on the same property child, with a
Unicode/TAB discriminator, scalar escaping and explicit creation. Expected XML
strings were written independently; strict UTF-8 comparisons assert character
fidelity, not physical Oracle CLOB storage-byte identity.

Two additional lead-owned external XML controls pass on the final production
sources: distinct statically valid selectors that alias one observed value refuse
with `ATTRIBUTE_ALIAS`; typed cross-projection alias and nonmatching-discriminator
cases refuse with their exact ownership codes. The focused reactor passed 5
(2 core, 1 parser, 2 server), with zero failures/errors. Source and log remain in
`es-child-final-xml-controls-8o2zko9d` and
`es-child-final-xml-controls-20260909.log`; these are additional independent
controls, not extra tests counted in the 754-case reactor.

SQLite controls reopen stored incomplete child history, replay the original result,
refuse its publication, then explicitly recompile and publish a new revision.
An actual hosted plan reservation retains its old published direct mapping after
a later child publication. A valid unequal plan revision refuses before reaching
the observation port; after explicit discard, a new plan selects the new mapping.
This control reserves no database connection and supplies no credentials.

The independent reviewer checked the lead-owned source, schemas, golden framing,
history and selected-package guards. No blocking finding remains in that reviewed
delta. Business review preserves value-free reuse and explicit topology changes;
Engineering review covers source ownership, bounded refusal and immutable pins;
QA/RST uses independent outputs, malformed declarations, namespace/absence/alias
cases, stale state and guard mutations. No private model was used or requested.

## Actual checks

Commands used pinned Maven 3.9.16 and Java 21.0.12. Focused reactor commands include
real core/parser tests and `-Dsurefire.failIfNoSpecifiedTests=false`; zero-test
modules were not passed off as success. Logs are external under `/home/tim/.tmp`.

| Check | Actual outcome |
| --- | --- |
| Initial child declaration RED (`es-child-property-red-20260909.log`) | 3 assertion failures, 0 errors: the old schema rejected the new closed form. |
| Adapter RED with recognized declarations (`es-child-adapter-red2-20260909.log`) | 4 assertion failures, 0 errors. Old blanket refusal of duplicates was not counted as cardinality support. |
| Historical codec RED/GREEN | 2 assertions failed before the closed field codec; corrected focused history/workspace suite passed 19. |
| Package schema and pin RED | New dependency schema refusal: 1 assertion; wrong logical pin accepted by initial scaffold: 1 assertion. Both corrected. |
| Child-as-entity creation readiness | Independent actual materialization witness; compiler RED 1 assertion/0 errors, corrected focused GREEN 46. |
| Diagnostic allocation regression | Independent RED 1 assertion/0 errors; lead corrected GREEN 14 and final independent rerun passed. |
| Availability/publication/package qualification | RED 2 assertions/0 errors with availability disabled; enabling the qualified candidate passed focused 61. |
| Final old/new plan pin probe | Corrected focused GREEN 7; final independent history/reuse/static/package suite GREEN 29. |
| Full integrated Java (`es-child-integrated-full2-20260909.log`) | **754 PASS**: core 167, qualified parser 7, server 428, supervisor 152; no failures/errors/skips. Assembly and hostile-launch checks passed. |
| Frontend | `npm ci`, check, 34 component tests, 26 schema tests and production build passed with Node 24.20.0. No dependency changes. |
| Pinned OCI build | Repeated Java 754, frontend 34/schema 26, production UI build, supervisor assembly/checksums and hostile-launch checks successfully. |
| Protected container/workspace smoke | Non-root/read-only startup, static UI, health, disabled demo operations, private initialization, overwrite refusal and explicit schema upgrade/refusal passed. Owned smoke resources were cleaned up. |
| Repository scripts | Root staged-content gate, integrity check and 11 Python tests passed; candidate content assessment returned no known-pattern errors. The lead reviewed the full staged 47-file change for invented provenance and cumulative disclosure. |

Preserved unsuccessful integration attempts are not behavioral RED evidence.
The first lead focused invocation omitted real core tests and correctly failed
the zero-test gate. An initial whole-profile expectation missed the existing
explicit reference-conflict proposal; the test now checks that conflict before
resolving the target. Strict Ajv rejected the initial nested `required` schema
forms; corrected closed `oneOf` forms compile without weakening strict mode.
The subsequent schema run had one real assertion failure for the missing optional
workspace dependency; its contract correction passes all 26 cases.

The initial plan probe used invalid revision 0 instead of a valid unequal revision;
its focused run and first broad Java run failed one assertion. The next fixture
attempt opened a second same-owner session and produced one setup error. The
final probe uses explicit plan discard under the existing lease. Both root and
reviewer preserve those failed logs; neither is claimed as a production defect.
The adapter author's earlier broad run also lacked the then-pending historical
codec overlay; its 22 failures/10 errors remain documented externally. The
corrected adapter-only integrated run passed 730 before the lead's extra cases.

Eight adapter guard mutants were killed: duplicate cardinality, selector protection,
physical aliasing, source membership, actual child value location, generated child
markup, generated child provenance and dependency-vector admission. Initial three
test-error outcomes were strengthened to assertions and rerun; all final kills
have zero errors. Six additional independent mutants each fail exactly one
assertion with zero errors: static alias, discriminator, child-entity creation
readiness, checked dependency authority, binding hashing against the whole
definition union and selected package-vector equality. Mutations occurred only in
external archives; restored candidate hashes were checked before clean controls.

## Compatibility and candidate identity

The independent Python oracle preserves logical digest
`86a12d3902ab52e747a0ca068bf93820913fbc6fd3829593bd70dc11cab8d09c`
and unchanged direct Oracle binding
`5ca6baddc92fb594bea44017e62e2ce9d37e4c342b71642bd43ace25d68fdada`.
The invented child Postgres binding becomes
`085064d8e44ae9e972f41595fc79c3a99626f4952beeeca5f61d175ef2e6bdce`.
Changing its physical selector changes only that binding's compatibility.
The frozen pre-extension direct snapshot roundtrips byte for byte and retains its
publication digest; its provenance and fixed hashes are in the
[fixture record](../../fixtures/native-v2/README.md). Direct package payload/encoding
goldens remain covered by the existing full suite. Portable profiles retain
logical compatibility and contain no child selector or donor value.

All candidates use base `e338da82b35493ede32f0acfa5a622015aba881c`; later root
`d24dbaf` adds only heap/handoff evidence. Author adapter10 manifest SHA:
`a4f0e985095804ad337c9fe9f99c8c0ca088d2a4efd9fd5b33514344b9682577`.
Reviewed integrated37 manifest SHA:
`26279324b561ea5ea83d6a44a4abb8f3742950b2e9556d2e72dc3f875027afff`.
Final corrected history-test SHA:
`9e5979c722fe55dc1d8a30042b600979705645f02f4ae7f017ad813c2efba479`.
Final source/test/schema/fixture38 manifest, including fixture README, SHA:
`f7bb6f3bde405909450170e037528cd7144b3b7e4c3aa14dfa0978e7b06bca22`.
Independent review report SHA:
`7d790c91d1774fbf3ec671add212a1484ca33af78b0007e414838d093c0fb36a`.
Manifests/logs and author archives remain outside the checkout. Documentation
clarifications do not alter those production files. Integration/rework was
interleaved with history, schema and independent controls; no elapsed speed-up or
parallel-efficiency claim is made.

The exact candidate38 OCI build uses the unchanged pinned Dockerfile and explicit
revision label `candidate-f7bb6f3bde405909450170e037528cd7144b3b7e4c3aa14dfa0978e7b06bca22`.
Retained local image/index ID:
`sha256:cad649ef4cc03f1b54d3b7ffa6304a64dcf2acf126fed74d06b6e99dcc0de20f`;
platform manifest `sha256:8f5252e5302efa487ed2d2409f79162246ef486e2f711f947a00218af27899a9`;
config `sha256:15bd612d0bac165026a326295a78ffb7b1fdcc3198d72a7c844b54e0ae5508f8`.
The separate retained supervisor ZIP has SHA-256
`3387fb7ab97acf26110e532c93c0889efccec7e8a639cc87e4755de72220257e`.
External build context: `es-child-oci-19k7k_o3`; logs:
`es-child-oci-build-20260909.log`, `es-child-container-smoke-20260909.log` and
`es-child-supervisor-artifacts-20260909.log`. The image was not pushed to a registry
or deployed; this is not new hosted-browser or database-client evidence.

## Limits and next work

`PackageAdmission.readPinned` checks claimed definition/binding digests and the
exact dependency vector after mechanical parsing. It does not certify payload
table/destination equality, publication authority, complete graph validation or
client execution. The hosted export path and native-client registry remain
unqualified; schema admission is never an export grant.

New resolver indexes, combined graph/source/dense maxima and complete hosted
export phases still need capacity qualification on this changed implementation.
The earlier exact-c4a3246 heap measurements do not qualify this candidate or the
current 1 GiB Compose allocation. Both binding declarations were tested with
invented XML; no new real-engine/client qualification is claimed here.

QF-0003/0004 retain the [approved authoritative derived direction](../product/derived-graph-decision.md):
physical-only v3 profiles, PUBLIC text, complete current/target recomputation and
shared total limits. Closed v3 contracts and runtime remain next; no computed
nodes, relationships or donor dependency semantics were added in this slice.
Native coordinator/JNI/client admission, operator workflow and deployment remain
part of the unchanged MVP completion plan.
