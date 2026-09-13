# V3 computed browser client candidate — 10 September 2026

Status: implemented and locally checked; independent review and integration pending.
Comparison base: physical-client candidate `59fef10bb7e87f7c3d99038d82b2beea315aaaa3`,
which also awaits review/integration. Immutable review SHAs and availability are
tracked in [issue9](https://github.com/timaday/environment-studio/issues/9).

The facade exposes all five existing computed POST routes through the original
HostedApi session/CSRF owner. Complete closed request/result types preserve exact
keys, physical references, occurrence counts, ordered contributor roles and both
child-selector/value location pins. Explicit requests retain their plan/route/side/
selector context. Every reply must match revision and exact page arithmetic.
Contributor replies must match selected physical membership, role count/order and
exact key values; selected-row helpers also compare the complete occurrence total.
These checks are display correlation, never server proof or export authority.

Contracts: [computed HTTP](../contracts/hosted-plan-computed-views-v3.md),
[controlled computed views](../contracts/plan-computed-views-v3.md) and the
[closed schema](../../schemas/plan-computed-view-v3.schema.json). No public wire
change, dependency, JSX, availability flag or server/native edit is included.
Existing physical decoder exports are reused without changes.

## Acceptance and actual checks

Initial behavior RED: two real assertion failures accepted an oversized UTF16
selector key and a truncated node page. Exact minimal source and manifest remain
in external `es-computed-client-red1-source-20260910`; log
`es-computed-client-red1-20260910.log`. The first implementation passes both.

Final checks from the external source archive's frontend directory:
`npm run check`, `npm test`, `npm run build` PASS: **111 frontend tests, including
15 computed tests;58 schema tests;TypeScript/Biome42 files;production build**.
Node24.20.0; `npm ci` used the unchanged lockfile. Final log
`es-computed-client-gates2-20260910.log`, SHA256 `b39d669a7d84d3cb2fe2d9cb370707f929c72bd7307bb5d94f71f00838f9d2be`.
All three candidate file hashes and every unchanged frontend/schema/script file
match the tested archive. No new Maven/OCI/browser/DB qualification is claimed.

Independently invented fixtures also validate against the actual canonical
schema. Checks include five exact routes; all three selectors; 1048576 UTF16-unit
supplementary keys and two-key requests; exact whitespace, combining forms and
server ordering; invalid XML/surrogates; mandatory disclosure; intmax paging and
complete equal-valued occurrences through the last page. Existing/Fresh identity,
child discriminator/value pins, duplicate equal co-occurrence roles and distinct
source/target roles remain intact. Missing/extra nested fields, reversed spans,
wrong role values/physical references and selected totals refuse. Huge decimal
cardinalities and current FAIL rules stay readable without validation authority.
Explicit RESOURCE_LIMIT/INCOMPLETE_TARGET/NOT_FOUND and a late JSON body after
session clear do not become empty success or trigger an automatic retry.

An invented escaped-attribute example separately preserves decoded `A&B` and
lexical `A&amp;B`, with an independent SHA256 and known raw spans. Its first
handwritten value offset was wrong; that fixture failure was corrected after
independent character-position inspection, without changing production behavior.
Another initial large-key fixture echoed offset0 for an intmax request and was
correctly refused; its response fixture was corrected. These are fixture defects,
not production RED. Formatting/lint setup failures remain in their original logs.

Seven manual guard mutations compile and fail actual assertions: UTF16 key bound,
complete page, disclosure, role value/order, membership physical reference,
selected occurrence total and reversed pin. Exact production bytes are restored
and hash-checked after each case. Two first mutation substitutions failed to
compile (unused index and lost discriminated-union narrowing); they are setup
failures, not killed mutants. Corrected substitutions compile and fail assertions.
Records/commands/substitutions are retained externally under
`es-computed-client-mutations-20260910`; no aggregate mutation score is claimed.

## Remaining evidence

Wire fixtures do not exercise a production publication, real compiler/XML/server
journey, maximum browser resources or operator workflow. Page envelopes have no
snapshot fingerprint or selector echo; captured context/revision/total cannot
prove an atomic cross-page snapshot. Locations remain transient disclosures and
are not client-supplied edit authority. Rules here are graph views, separate from
fingerprint-bound validation pages. All source models/text are independently
invented; no private, redacted or transformed application material is included.

Independent remote review, corrected native integration, required combined gates,
React journeys and release qualification remain open. JNI-QA-001 is still present
in this inherited base and assigned solely to IDE2. The first application branch
upload was rejected by automatic approval policy; this candidate is local until
its exact remote ref is confirmed. No alternate upload or premature review claim.
