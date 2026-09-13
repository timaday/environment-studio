# V3 physical browser client candidate — 10 September 2026

Status: implemented and locally checked; independent review and integration pending.
Candidate comparison base: `30446e206b5489c23f98e9c966056a7f678de8bd`.
Review assignment and immutable SHA are recorded in [issue9](https://github.com/timaday/environment-studio/issues/9).

The fixed facade exposes all nine existing physical, structural and document
POST views through the original HostedApi session/CSRF owner. Complete closed
request/result types retain the original plan, route and request scope. Responses
must match revision, exact page size/offset/continuation and echoed document
identity. No automatic paging or disclosure is performed. This is browser API
plumbing; no React journey or availability change is included.

Contracts: [physical views](../contracts/hosted-plan-physical-views-v3.md),
[structural views](../contracts/hosted-plan-structural-views-v3.md),
[document views](../contracts/hosted-plan-document-views-v3.md) and the reused
[complete view schema](../../schemas/plan-view-v1.schema.json). No public wire
contract or server authority changes.

## Acceptance and actual evidence

Independently invented wire fixtures exercise all nine URLs, closed bodies,
Existing/Fresh references, original placement coordinates, explicit binding
states, missing target versus unchanged, complete zero counts, long decimal
coordinates and large location offsets. Complete positive fixtures are also
checked against the canonical JSON schemas. Malformed/truncated pages, masked
text/length/digest leakage, missing flags, wrong identities, absent disclosure,
invalid Unicode and late responses after session clear refuse explicitly.

A transport chain uses two original physical entities, preserves the unselected
sibling reference and submits explicit field/reference/Fresh commands from
returned handles/coordinates. This does not prove actual XML materialization.
Invented two-document XML covers CRLF and supplementary characters with independent
SHA256/UTF16 expected spans. Raw lexical selection checks document/side/revision,
inventory digest, exact/redaction/omission flags and scalar boundaries. Formatted
and Placeholders strings never receive Raw offsets, even with a contradictory
`exact:true` flag. No DOM serialization or text rewriting is introduced.

Initial RED: two actual assertion failures accepting masked concrete text and
another document side; exact source plus manifest retained in
`es-physical-client-red1-source-20260910`, log `es-physical-client-red1-20260910.log`.
After the first implementation, focused14 passed. Expanded checks exposed one
additional actual failure accepting an unpaired surrogate (95 passed/1 failed,
`es-physical-client-gates3-20260910.log`); its exact four source files are frozen
in `es-physical-client-unicode-red-source-20260910`. The physical text decoder now
rejects that value without changing existing decoder consumers.

Final local commands from the external source archive's frontend directory:
`npm run check`, `npm test`, `npm run build`: PASS TypeScript, Biome39 files,
**96 frontend tests (17 physical),58 schema tests**, and production build.
Node24.20.0; dependencies from the existing lockfile; no dependency/build changes.
Final log `es-physical-client-gates5-20260910.log`, SHA256 `e07405ba9da878f1c9951d03aafd262600cb7df2767f49605c454bb51bdf11a1`.
The archive started at ddad9cf; its existing frontend/scripts/schemas are identical
to the candidate base30446e2. Full candidate combined gates remain separately
assigned; no new Maven, OCI, browser or database result is claimed here.

Six manually substituted guards compiled successfully and produced assertion
failures: masked text, complete page, response revision, requested side in page
compatibility, Raw-only selection and scalar text. Production bytes were restored
and checked after every substitution. The first Raw-mode mutant survived because
its fixture was also inexact; the independent-mode adverse case above closes that
test gap and the repeated mutant fails. Records/logs are retained externally in
`es-physical-client-mutations-20260910`; this is targeted investigation, not an
aggregate mutation-coverage claim. Initial formatting and unused-import/type setup
failures remain recorded separately from behavioral RED.

## Review and qualification limits

Request context/revision/total are not an atomic cross-request snapshot proof.
Physical page envelopes have no observation/target fingerprint. Raw selection
compares the location digest to the matching inventory side; it does not recompute
the returned text digest or authorize edits/export. Every backend call retains
its own complete proof checks. The view client accepts an explicit declared
projectionId; a future typed definition-discovery slice must supply it for the UI.

No actual server/browser/database journey, stable-token identity-change workflow,
maximum browser resource qualification, new design or release readiness is claimed
by these wire fixtures. Those require their own integrated evidence. All mock
names, records, text and values here were independently invented; no application
model, private XML, credentials or transformed real material was used.

The base still contains native **JNI-QA-001**, accepted from remote review in
issue9 and assigned only to IDE2. It is unrelated to these frontend files and
remains open until a corrected candidate passes independent and combined checks.
The previously passing Java1619 suite did not detect it. Production native
admission and every outstanding release capability remain unqualified.
