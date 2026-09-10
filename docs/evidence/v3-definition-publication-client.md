# Typed v3 definition publication candidate — 10 September 2026

The definition facade now prepares an immutable destination/publication command
and sends one POST through the existing HostedApi. Base:
`41c8c3e1d575225d43c1780874a65b82f4121ffd`. Fixed non-author source review found no confirmed issue; combined verification
remains pending. No backend, native, JSX,
schema, build or availability changes; current new publication remains refused.

The [publication contract](../contracts/workspace-publication-http-v3.md) was
updated before implementation. Both preparation and submission decode the closed
pair, exact UUID/canonical revision strings and 1–20,000 closed policies using the
existing OpenAPI bounds. Keep submitted order and every duplicate for backend
coverage checks; never infer, replace or deduplicate policies. Nested caller edits
cannot change the detached prepared request. No automatic retry or persistence.

Successful replies use the complete existing history decoder and require the
same object, published state and exact sourceRevision. Policy content, identity
and multiplicity must equal the command after stable binding/document tuple
ordering. The existing decoder independently requires complete published policy
coverage and sorted order. Explicit replay after lost response/newer history
retains the original request ID, destination and body. Original session clear
aborts pending work and refuses late JSON. No historical-ready result grants
current compiler, publication, export or UI authority.

Eight new tests cover mismatched source revision/policy content/draft/foreign
responses; exact POST/CSRF and detached nested lists; prefix binding/document
ordering; preserved duplicates and refused collapsed success; explicit replay;
closed wrappers, malformed IDs/revisions/scalars, empty/20,001-policy refusal;
complete 20,000-policy forwarding and canonical 0/1,024-digit revisions; backend
DEFINITION_INCOMPLETE diagnostics and late JSON revocation. Positive wrappers/
history validate against independent committed OpenAPI schemas. All models and
responses are invented transport fixtures; these are not actual new publication
or native source/model compilation controls.

Initial minimal POST wiring produced two meaningful RED assertion failures:
wrong source revision and changed policy content were accepted; 23 existing tests
passed. Correlation guards make 25 tests pass, then the broader 31-test definition
selection passes. The request list's minimum was aligned with the existing OpenAPI
minItems1 before final checks; no schema or backend behavior was changed.
RED source/manifest and logs remain external in
`es-definition-publication-red1-source-20260910` and red1/green1/green2 logs.

Node 24.20.0 with fresh unchanged-lockfile npm ci passes frontend `npm run check`,
`npm test` and `npm run build`: 153 Vitest, 59 schema, TypeScript/E2E types,
Biome 46 files and production build. `es-definition-publication-g02-20260910`
records exact commands, exits, source/log hashes and unchanged tracked inputs.
Seven distinct guard substitutions compile and fail the intended assertions:
detached preparation, submission validation, sourceRevision correlation, complete
policy equality, tuple ordering, and upper/lower policy bounds. No compiler/setup
failure is counted. Exact sources/commands/results are in
`es-definition-publication-mutations-20260910`; original production bytes are
restored and hash-checked. This is targeted client evidence, not server authority
mutation coverage.

The preceding 41c8 candidate is published with its own remote review/G08 assignment;
its results cannot qualify this later client delta. No duplicate local Maven,
OCI or database campaign ran. Native closure stays with IDE2; complete resource,
compiler-publication, native-client, operator and deployment evidence remains open.

Fixed review `es-definition-publication-fixed1-review-20260910.md` verified all
five file hashes and complete source/test/contract delta against base41c8. Manifest
SHA256: `ce747961ff86a9e51f598fa39c5b35485e23eaf4afd080e43d74c5b30d4f604e`.
The reviewer inspected author results but ran no tests or builds. This final
evidence-status update changes prose only; reviewed production/test/contract bytes
remain unchanged. G00 integrity/content/whitespace and 11 Python checks passed
before the fixed review; final staged checks are required before committing.
