# Typed v3 definition save candidate — 10 September 2026

The client now prepares an immutable destination/command pair and sends one
versioned definition PUT through HostedApi. Comparison base:
`39a9009fc93f22bb65a2eb992718c96cb67b0d0b`. This is a separate candidate with fixed
non-author source review and no confirmed finding; required combined verification
remains pending. No React or backend
source, wire schema, publication availability or export authority changes.

## Behavior and acceptance

The [workspace contract](../contracts/workspace-http-v3.md) was updated first to
describe the typed facade. Preparation copies/freezes the closed objectId and
command fields; submission validates them again. Canonical expectedRevision0 and
up to1,024 digits remain strings. Format is JSON/YAML; source is preserved exactly
and bounded by1 MiB strict scalar UTF-8. The browser does not parse or rewrite the
native model. Unknown/missing fields, invalid IDs/revisions/Unicode and excess
source refuse before transport. An8 MiB wrapper cannot arise from these bounded
fields even with JSON escaping.

Successful replies use the existing complete immutable DefinitionRevision
decoder and must match destination, source and format with draft state. A stored
historical-ready projection conveys no current qualification. Response loss
does not trigger retry: the caller may explicitly replay the same prepared pair,
including after a newer current revision exists. Original session revocation
aborts pending work and rejects late decoded responses. The facade adds no cache,
browser persistence, new request ID generation or publication request.

Nine new tests cover detached caller mutation, contradictory source/object/format/
published replies, initial and large expected revisions, exact JSON/YAML PUT and
CSRF, UTF-8 boundary/one-over/malformed source, both closed wrappers, response-loss
replay after newer history, safe refusals without retries, and revocation while
JSON decoding is pending. Independent OpenAPI checks validate positive wrappers
and responses. Wire source strings are invented transport fixtures, not complete
native source/model compilation evidence; existing actual server save/history
tests provide their separately recorded integration evidence.

## Actual checks

Initial minimal PUT wiring produced three meaningful RED failures: caller edits
changed the submitted command, mismatched source was accepted, and a malformed
revision reached transport and returned success. Fourteen existing tests passed.
The corrected17-test selection passed; final23 definition tests pass.
External source/logs: `es-definition-save-red1-source-20260910`, red1 and green1 logs.
The broader first run referenced a nonexistent DraftRequest schema and failed
test setup; corrected SaveDefinition schema reference passes. An initial formatter
invocation used the repository cwd; running it in frontend resolves the existing
nested configuration. Neither setup error is production RED.

Node24.20.0 with fresh unchanged-lockfile npm ci passes `npm run check`, `npm test`
and `npm run build` in frontend:145 Vitest tests,59 schema tests, TypeScript/E2E
types, Biome46 files and production build. Exact commands, all tracked source
hashes before/after, log hashes and exits are recorded externally in
`es-definition-save-g02-20260910/result.json`.

Seven separately compiled guard substitutions fail the intended assertions:
detached preparation, submission revalidation, same-object correlation, exact
source, format, draft state and UTF-8 bytes. The first same-object substitution
failed TypeScript for an unused parameter and is not a killed mutant; its corrected
variant compiles and fails the behavioral checks. All production bytes are restored
and hash-checked. Exact sources/commands/logs/results are in
`es-definition-save-mutations-20260910` and `es-definition-save-mutations2-20260910`.
This is targeted client-boundary mutation evidence, not server authority coverage.

The remote reviewer independently passed39's full1,698 Java tests and G00/G02;
its separate G08 attempt is blocked by unavailable pinned build packages and
does not include this client delta. No local
duplicate Maven/OCI/database run or browser/operator qualification is claimed.
Native closure remains exclusively IDE2-owned. Current compiler/resource/client/
hosted-export and required UX work remain open.

Fixed review verified all five candidate hashes and read the supporting source,
contracts and author results without executing tests. External report:
`es-definition-save-fixed1-review-20260910.md`; source manifest SHA256:
`9d1e823e64c0417e23fc63ef43ab442f8fc21c85c905a28de5566533021acaac`.
