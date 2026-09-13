# Owned profile browser client

The typed client now exposes the five existing profile workspace routes: list,
current, exact history, explicit save and explicit publication. It shares the
original HostedApi session/CSRF/cancellation owner. Capture's `json` is explicitly
translated to workspace `JSON`; exact source and its historical definition
reference are preserved. No profile UI, automatic publication or availability
switch is added. Contracts remain workspace-profile-http-v3,
workspace-publication-http-v3 and profile-inspection-v3.

The complete small profile model is decoded as closed readonly physical
entities/relations, with exact string revisions and string schemaVersion3.
Draft has no publication object; published history requires digest/sourceRevision.
Structural validity and old publication remain historical data, not current
readiness. Production publication still refuses incomplete qualification.

## Fixed review and correction

Base `bbaf8a30fd49948f3d1e6b809b19485aafeb288b`; candidate2's four files match
manifest SHA256 `817170895178a4b927a8140b4b7801aeae1b97fa6f46798ccdf4813ca9f9e934`;
patch `943c938d8f612234f2517e62c818cd36c4a0a0f1edeed3f018dad6c5c1af5d56`.
External prefix: `es-v3-profile-client-candidate2-20260910`.

Independent candidate1 review reproduced contradictory successful replies:
save accepted a different source/format/definition, and publication accepted
another sourceRevision. The correction requires exact saved command fields and
draft state, and exact publication input revision. Contradictions remain
RESPONSE_UNAVAILABLE so the original immutable command is retained for explicit
replay. Destination and nested command are detached/frozen together; no new
identity or automatic retry is generated. Old replay results remain accepted
after a later current revision is read.

Fixed reports: `es-v3-profile-review-a-report-20260910.md` and
`es-v3-profile-review-a-report2-20260910.md`. Non-author candidate2 review verified
all hashes, independently reproduced both corrected refusals and successful
original replay after current11/current12, and passed45 focused client tests.
No remaining concrete finding was reported.

## Actual checks and limits

Node24.20.0,10 September2026:

| Check | Observed result |
| --- | --- |
| Initial RED | Two assertions: fabricated readiness and wrong historical revision accepted |
| Response-correlation RED | Two assertions failed/12 passed for contradictory save/publication replies |
| Corrected focused GREEN | 14 profile tests PASS |
| Lead full frontend gate | 79 frontend/58 schema, both TypeScript checks, Biome36 and build PASS |
| Integrated frontend gate over232b958b | Same79/58/check/build PASS in es-profile-integrated-mslczno4 |
| Six isolated guard mutations | All compile and fail assertions; unchanged control passes, original bytes restored |

Integrated log: `es-profile-integrated-gates1-20260910.log`. Mutations separately
remove save source, format, definition object, definition revision, draft-state
and publication source-revision guards; results are in
`es-profile-client-mutations-xavenff8/results.json`. These are local checks, not
release evidence. Initial unavailable TypeScript string/array methods, a schema
URN-resolution error and an incorrect network-error oracle were setup errors;
the fixed implementation preserves existing compiler configuration and
NETWORK_UNCERTAIN versus RESPONSE_UNAVAILABLE behavior.

Tests cover closed shapes, complete100-entry inventory, full physical model
array bounds,1MiB scalar UTF8 source, numeric notation/CRLF, response-loss replay,
safe404/409/422/503 diagnostics and late source rejection after logout. The explicit
client chain connects capture/save/history to both selection request forms and
compose. Its selected form uses the sole root; it does not prove partial
multi-root semantics or sibling preservation. Source examples are opaque wire
data, not complete compiler-qualified profiles; the large relation fixture checks
array bounds with repeated rows. Earlier real multi-XML backend evidence retains
its own attribution.

Business review checked explicit save/reuse and honest publication state.
Engineering/security review checked exact source/reference/replay and shared
session ownership. QA investigated contradictory replies, stale history and
refusal recovery. Fetch/SSR probes are not actual browser/storage/HTTP/operator
journeys. No UI behavior or accessibility claim follows.

The preceding integrated backend gate remains Java1617/distribution PASS; this
four-file client slice changes no Java. Physical/computed view clients, declared
projection discovery, approved React journeys, actual publication qualification,
native/export/readback, combined resources and exact OCI/HiveForge remain open.
The retained c3b891a image predates this client. No release gate is closed.
