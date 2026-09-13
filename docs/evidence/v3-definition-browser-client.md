# V3 definition discovery client candidate — 10 September 2026

Status: implemented and locally checked; independent review and integration pending.
Comparison base: `06b0888726b3bbd43e98498af18a8a2fee57c55e`, the pending computed
client candidate above the pending physical client. Exact review SHAs/fetch status
are tracked in [issue9](https://github.com/timaday/environment-studio/issues/9).

The three fixed v3 GET methods return complete closed definition list/current/
history models through the existing HostedApi owner. Exact historical reads must
match both object ID and revision. The pure discovery mapping selects the explicitly
requested binding from the plan's exact definition reference and returns declared
document/projection/type IDs. These exist without observed entities. No default
binding or inferred XML parent is selected: placement coordinates still come from
the existing controlled server endpoint. No JSX or availability change is included.

The complete decoder follows [workspace HTTP](../contracts/workspace-http-v3.md),
[workspace OpenAPI](../contracts/openapi-workspace-v3.json) and
[definition inspection](../../schemas/definition-inspection-v3.schema.json).
It retains physical and computed declarations, exact integer strings, capabilities
in stored order (including explicit empty), both field mapping forms, optional
mechanisms, diagnostics and published policy entries. Historical-ready data never
establishes current compiler qualification. No save/publication endpoint or public
wire change is added. The only shared decoder change exports its existing closed
dictionary primitive; existing decoding behavior is unchanged.

## Actual acceptance and checks

Initial RED: two assertion failures accepted another historical revision and an
ambiguous field with both attribute and child-property declarations. Exact minimal
source/manifest are retained in external `es-definition-client-red1-source-20260910`,
log `es-definition-client-red1-20260910.log`. Both pass after implementation.
A later legitimate published-policy example with prefix IDs `a`/`a-b` exposed
incorrect ordering from concatenated strings. Exact four-file RED is frozen in
`es-definition-client-policy-order-red-source-20260910`; the ordering log shows
one failure/two passes. The reader now compares binding/document tuples separately;
that three-test control passes without changing stored order or the wire contract.

Final local `npm run check`, `npm test`, `npm run build`: PASS **125 frontend
tests, including14 definition tests;58 schema tests;TypeScript/Biome45 files;
production build**. Node24.20.0 and unchanged lockfile via `npm ci`. Log
`es-definition-client-gates1-20260910.log`, SHA256 `f10c8039589c7d253e8dc196d2e3f2c9a3636d736ef51bae40b0b1033a80e540`.
Candidate source hashes and all unchanged frontend/schema/script files match the
tested external archive. No new Maven, OCI, browser or database result is claimed.

Independent invented fixtures exercise both engines' declaration shapes, two
bindings and two documents, child-property/reference mappings, all five operation
capabilities, physical/computed rules and1024-digit cardinalities. Positive models
validate against the actual inspection/OpenAPI schemas. Tests cover complete
sorted100-object inventory, empty inventory, pinned history after newer current,
source CRLF/scalar UTF8 limits, supplementary XML names, draft/published unions,
complete policy order/coverage, duplicate diagnostics and omitted optional mechanisms.
Required logical sections, ambiguous mappings, invalid names/versions/sources,
unknown joins, duplicate IDs and safe/late session responses remain explicit
refusals where applicable. No new disclosure endpoint
or permission is inferred by discovery.

One client chain uses the selected declared empty-document projection to request
placements, then uses only returned original digest/element coordinates in an
explicit Fresh create command. Empty placement results remain empty; no entity
observation request or invented parent is substituted. These are transport/shape
correlation examples, not actual XML materialization or current publication proof.
The deliberately minimal source string in wire fixtures is not a complete native
source/model compilation example. Stored digests are not recomputed by this client.

Six manual guard substitutions compile and fail actual assertions: exact history
revision, source UTF8 bytes, exclusive field mapping, complete policies, discovery
revision and known type join. Every original production byte is restored and
hash-checked. Exact substitutions/commands/results remain external under
`es-definition-client-mutations-20260910`. Initial lint rejected the normative XML
combining-mark class; equivalent explicit alternatives pass without a rule waiver
or config change. This is targeted investigation, not aggregate mutation coverage.

## Remaining evidence

Independent fixed review, corrected native/application combination and required
combined gates remain pending. The preceding review-branch push was rejected by
automatic approval policy; no alternate upload/retry or fetchability claim is made.
Native JNI-QA-001 and its remaining clock-handoff question stay with IDE2. Current
compiler publication, React journeys, resource/operator/client and release
qualification are unchanged. All models, names and source/XML examples are
independently invented, with no private or transformed application material.
