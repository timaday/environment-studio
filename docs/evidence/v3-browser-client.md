# V3 plan browser client boundary

The typed client now exposes the existing v3 plan lifecycle, commands,
materialization, physical-only profile capture, whole/partial preview and paged
validation. It shares the existing session, CSRF, cancellation and credential
ownership. This slice adds API plumbing; no React journey or operational
availability is enabled. Contracts remain
[plan HTTP v3](../contracts/hosted-plan-http-v3.md) and
[workflow v3](../contracts/hosted-plan-workflow-v3.md).

## Fixed candidate and review

Base `cc60e0e3605f310bc9e8142525b91d5347cddbb7`; six source/test files match
manifest SHA256 `8d4d6ff744c8c91c06da776932b12c8969012fefde367eded708e76fba80cd77`.
Patch SHA256 `4c2df55bf58f9f25b0f8a6c18ae404ebdcf87654617542697ac58dfc55c33ee5`.
External artifacts use prefix `es-v3-browser-client-candidate1-20260910`.
Non-author review `es-review-browser-fixed-candidate1-20260910.md` verified all
six hashes and1044 unchanged base blobs, with no remaining findings.

Closed decoders reject unknown properties, malformed versions and incomplete
pages. Detached frozen commands preserve the exact caller-supplied replay
identity and acknowledged revision. Preview retains all eight pins, normalized
roots, dependencies, affected derivations and section totals. Validation retains
all ten checks, the matching fingerprint and missing-target versus complete-zero
distinction, including offset63999 and explicit smaller-page recovery.

Credentials remain one-shot across v1/v3, including unreadable successful replies.
Logout, idle/absolute expiry and late body resolution preserve the original
session generation. Early bodyless controller errors require a v3 plan/operation
route, non-success status, exact zero Content-Length, a known closed error code
and an actually empty body. Contradictory framing remains RESPONSE_UNAVAILABLE;
the existing JSON error path is preserved.

## Actual checks

Pinned Node24.20.0,10 September2026. Lead and reviewer used separate exact-source
archives, without native/Java/container resources.

| Check | Observed result |
| --- | --- |
| Preserved initial behavior RED | Two assertions failed: early no-body error translation and acceptance of a missing-v3 legacy response |
| Sparse-array correction RED | One assertion failed/14 passed: a hole was skipped and submitted as null |
| Lead full frontend check/test/build | Both TypeScript checks, Biome33 files,65 frontend and58 canonical schema tests, Vite build PASS |
| Independent full frontend check/test/build | Same65/58 tests and build PASS in a fresh source archive |
| Six isolated guard mutations | All compile cleanly and fail assertions; unchanged control passes; original hashes restored |

Review confirmed the sparse-array P2. Array.from now visits holes, which reject
locally as INVALID_REQUEST before fetch. The six mutations cover credential
one-shot ownership, session generation, validation fingerprint, null versus zero,
complete page length and sparse arrays. They ran outside the frozen candidate.
Exact results are in `es-v3-browser-mutations-2c8tahne/results.json`.

Independent fixtures exercise the canonical schemas, Unicode/XML scalar edges,
exact revision/cardinality strings, capture's1MiB UTF8 limit and response-loss
replay. Capture source is opaque invented client data, not evidence of successful
profile compilation. No provider, storage or dependency was added. Four local
regex lint annotations document normative Unicode/control-character rules; no
global lint or schema gate changed.

Lead full log: `es-v3-browser-client-lead-gates1-20260910.log`.
Independent command/log prefix: `es-review-browser-fixed-alu2tu6m`.
Sparse RED: `es-v3-browser-client-sparse-red1-20260910.log`.
Initial import/type-narrowing and nested Biome configuration failures were setup
errors, not production RED. They were corrected without loosening contracts.
Lead archive-to-freeze time was7.7 minutes; the measured independent
archive-through-report portion was123.2 seconds. Neither is a parallel speedup.

Business review checked explicit reuse and inspectable complete results.
Engineering/security review checked closed boundaries, shared session ownership
and immutable replay. QA investigated malformed replies, stale completion,
Unicode boundaries, response loss and complete-tail paging. These use fetch/timer
mocks; actual browser/OIDC/server interaction, accessibility and operator RST
remain open. There is no newly completed operator journey or release gate.

## Remaining work

Profile workspace save/history/publication client support, physical/computed view
clients and approved React integration remain. The current compiler still refuses
new publication. Native admission, hosted export/readback and combined deployment
qualification remain required. The retained c3b891a image predates this client;
these frontend checks are not new OCI or full integrated Java evidence.
