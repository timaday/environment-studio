# V3 review browser client — 10 September 2026

Status: implemented, locally checked and independently source-reviewed; integration
and release qualification pending. Comparison base
`76ad813bce7e400555e056b5e9e21420efcc55f9` combines the pending definition/physical/
computed client stack954ded65 with the unchanged server review candidate32499697.
The [review contract](../contracts/plan-review-v3.md) defines the exact request,
historical receipt and server authority. Fetch status remains in
[issue9](https://github.com/timaday/environment-studio/issues/9).

`prepareReview` validates and freezes a detached five-field request.
`HostedV3Api.review` sends it once through the existing session owner and accepts
only a frozen two-field receipt matching the requested plan and retained request
revision. An uncertain request can be replayed explicitly with identical bytes
after later displayed edits. No ID generation, implicit retry, current-state
lookup, receipt authority or UI activation is added.

## Acceptance and actual checks

The initial positive schema-valid behavior test failed with INVALID_REQUEST from
the deliberately refusing implementation, then passed after the route was wired.
Exact five-file RED source and base are retained in external
`es-v3-review-client-red1-source-20260910`; logs `es-v3-review-client-red1-20260910.log`
and `es-v3-review-client-green1-20260910.log`. Expanded focused GREEN passes11 tests
in `es-v3-review-client-green2-20260910.log`.

Invented fixtures exercise maximum1024-digit revision and64-character destination,
canonical schema parity, malformed/missing/unknown fields before network work,
foreign plan/revision receipts, forbidden operation/check/export fields, exact
explicit replay after network uncertainty or unreadable success, caller mutation
during fetch, closed server refusals, session clearing during fetch/JSON and
absolute expiry. Requests stay below16KiB. Tests use the actual client/session
classes and canonical AJV schemas over mock fetch responses.

Combined `npm run check --prefix frontend`, `npm test --prefix frontend` and
`npm run build --prefix frontend` PASS: **136 frontend tests across16 files,
59 schema tests, TypeScript/Biome46 files and production build**, Node24.20.0,
unchanged lockfile. Log `es-v3-review-client-g02-20260910.log`.

Seven separately compiled faults fail behavior assertions: permissive operationId
receipt, omitted plan correlation, omitted revision correlation, correlation to
mutable caller input, bypassed request preparation, automatic retry and a second
artifact intent. The unchanged control passes11 tests; all seven TypeScript
compilations succeed and Vitest exits1 with assertion failures. Exact source,
commands and JSON reports are external in `es-v3-review-client-mutations-20260910`.
Initial harness setup omitted copied contract imports; the next harness classifier
did not recognize Vitest's promise assertion form. Both setup attempts are retained
separately and are not product RED or mutation results. Candidate source was never
modified by the mutation harness.

## Independent review and limits

Non-author fixed-source review verified all1093 manifest hashes and1088 unchanged
base files; no confirmed source finding. Manifest SHA256
`292e32e4d99858e03e0cbce918d49b7fc5ede28f695216c83b2736f3b8595e78` in external
`es-v3-review-client-fixed1-20260910`. Report
`es-v3-review-client-fixed1-review-20260910.md` assessed author G02 evidence but
ran no tests/builds and did not assess later mutation results. The tracked patch
omits the new test; all five manifest files, including that test, belong to the
candidate. The committed evidence file is additional documentation only.

G00 provenance/content/integrity/whitespace and11 Python checks pass before commit.
No new Java full gate, OCI, actual browser/OIDC/server journey or database evidence
is claimed. The remote baseline30446 full Java gate has six429 HTTP assertion
failures under investigation; earlier local success does not supersede it.
Combined integration must resolve that failure and verify the exact selected
native/application candidates. Current publication, React journeys, resource,
client/export/operator and release qualification remain required. Only independently
invented identifiers/data appear in this change.
