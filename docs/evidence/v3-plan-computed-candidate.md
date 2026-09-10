# Computed HTTP candidate — independent review pending

The five computed collection/contributor routes are implemented and locally tested
in an isolated candidate. They are **not integrated into the branch**. The next
required step is independent fixed-candidate review; all available reviewer agents
hit account usage limits. The lead has not retried those agents or substituted a
model to bypass the limit. Passing author tests does not satisfy the repository's
requirement for a reviewer who did not author a material change.

## Fixed source

Base `8ad1e6a16eefc3c1c846ea9c9a0a0f286a1ab2ae`, archive
`es-v3-computed-http-lead-ji9xj1xo`, fourteen-file manifest
`cdd9993aeda87f3a4393267131b8129a39738760481b0471fc652fdc442069de`.
External manifest/JSON: `es-v3-computed-http-lead-candidate1-20260910`.
Review patch `es-v3-computed-http-lead-review-candidate-20260910.patch` SHA
`1a788eefa4fa7c1ed6df54f7b4151add57c6468f4f5f5001104c283af14d5ab3`.
All fourteen hashes remain exact after full verification and restored mutation
controls. All other base files were compared against their Git blob identities.
The lead owns this entire candidate; no independent review is claimed.

The candidate adds `hosted-plan-computed-views-v3.md` before implementation,
`plan-computed-view-v3.schema.json`, five OpenAPI routes, a dedicated streaming
reader, explicit closed reply mapping, a controller and exact security matchers.
The existing eighteen OpenAPI routes/components and v1 reader/schemas are unchanged.
It delegates existing [controlled computed views](../contracts/plan-computed-views-v3.md)
inside the original executing/pinned view and shared semantic transfer lifetime.

## Behavior and contract choices awaiting review

Collections return nodes, memberships, co-occurrences and complete rule outcomes,
with exact structured keys, full counts, stable ordering and bounded pages.
Contributors require complete-document disclosure and retain all physical origins,
ordered field roles and actual attribute/child-selector pins. Fresh references
remain distinct after same-literal replacement. Missing target, complete empty
results, optional absence and failed current rules retain distinct meanings.

An exact contributor selector can exceed the existing16KiB/2048character reader
limits. Its separate input permits16MiB/original30s, at most128tokens/depth4,
strict XML Unicode and at most1048576 UTF16 code units per key value. That value
bound follows existing selected XML source limits; two fully escaped maximum keys
fit the body bound. Four collection bodies remain16KiB/original10s. Every response
retains128MiB and the original30s encoding/output/flush budget. No body tree,
normalization, key hash substitution, extra page cache or automatic truncation.
Exact rule cardinalities are decimal strings, preserving Java BigInteger values.

The lead's engineering/security review checked explicit field mapping, original
pin checks, input/output budgets, V2 preflight and scratch rollback. Product/UX
implications remain full-document consent for locations and no editable computed
entities. QA/RST evidence includes actual mixed HTTP edits, original/target proof,
complete pagination, duplicate/last-contributor behavior, stale/foreign inputs and
recovery. These are author perspectives, not independent sign-off or user design
approval. Combined peak-memory and operator-browser qualification remain open.

## Actual local checks

Pinned JDK21.0.12/Maven3.9.16/Node24.20.0,10 September2026:

| Check | Actual result |
| --- | --- |
| Initial actual MockOIDC/socket RED | 3 assertion failures/0 errors for missing routes,10:27:08BST |
| Final focused checks | 39 total:2 controls/37server, zero failures/errors/skips,10:40:16BST |
| Full Maven verify | 1565:324core/7parser/924server/310supervisor, zero failures/errors/skips, assembly/hostile launcher PASS,10:45:30BST |
| Fresh npmci/check/test/build | 40frontend/57schema PASS |
| Bounded mutations | Consent, target side, duplicate roles, rollback and decimal encoding each clean-compile and fail1 assertion/0 errors; each exact restored control passes3 total |
| Local repository checks | Python11/repository PASS; complete candidate file-table content assessment PASS, without staging the candidate |

Actual socket cases exercise every route through original ownership/CSRF; all
physical references come from actual entity views. They cover full node page
concatenation, two-document contributor origins and lexical slices, pair role
order, field/identity edits and removal of one versus the last contributor.
Consent applies before unknown/beyond-end selectors. A20K-character unknown key
reaches exact lookup rather than the old small-body refusal. Held input preserves
shared capacity, metadata access and legal204/503 logout recovery.

Controlled actual XML cases round-trip a returned20K-character child-derived key,
preserve selector/value coordinates after XML escaping and supplementary Unicode,
retain UTF8 tuple order and canonical-equivalent distinctions, optional absence,
current FAIL rules, duplicate equal self-co-occurrence roles and Fresh replacement.
All five routes refuse V2 before body/capacity and lose original authority during
held output without emitting bytes. Reader controls cover two maximum fully escaped
keys, maximum supplementary UTF16 length, exact/one-over body ceilings, invalid
UTF8/surrogates/XML text, canonical integers, closed unions and all consent forms.

Two intermediate test compilation errors were corrected: a broad reference needed
an explicit Existing assertion, and a test needed a checked-exception declaration.
One test oracle expected eight rule fields where the documented closed response
has seven. These were test-only corrections, not production defects or behavior
REDs. The genuine initial missing-route RED preceded production implementation.

Full log `es-v3-computed-http-lead-full1-20260910.log` SHA
`aa48ed102a1d0694424b1a236455aefcc52a088fabd89fa6c7f2ca41ae5b463f`.
RED log SHA`b264a4517e8c0754c154d8247c46709e8708cf6f4a6f6c647438282b42e97246`.
Focused final log SHA`5ac534c25ad2d85d6f3bf0ed1c196e36ced387c634e75fbf1b260e35033ea843`.
Frontend log SHA`f517ca702920204302cf1981fcf91a228e71b4977dae5b0edebd44fd42155cc4`.
Mutation result SHA`2e484d9c19b9ffde159dbf31831ae29c9ca8180002387f24d29b91609e4fcf20`.

## Separate review image

The exact frozen candidate also passes its own OCI build:1565 Java tests with
zero failures/errors/skips, assembly/hostile launcher, and an executed frontend40/
schema57/check/build step. The npm dependency layer is cached. The Java build
finishes at2026-09-10T09:54:24Z. Protected non-root/readonly startup, static UI,
readiness/demo denial and private schema2/schema3 initialization/upgrade/refusal
smoke pass. This is a local unreviewed development image; the1GiB smoke is startup
proof only, not combined workload qualification or readiness to admit v3.

| Identity | Value |
| --- | --- |
| Tag | `environment-studio:v3-computed-cdd9993aeda8-review` |
| Source label | `8ad1e6a16eefc3c1c846ea9c9a0a0f286a1ab2ae+computed-cdd9993aeda87f3a` |
| OCI index | `sha256:1455e746b5a5fa8e3540626e422229d3927009b11dd644ce8082076b528def1d` |
| Linux amd64 manifest | `sha256:8d7e53fa92c087c8c185cec2e928e0e6c8c954f6ced2ce9ef55803cdce622fd9` |
| Runtime config | `sha256:5e2cd3813adf060a0ff7fc4a34d82bf43877f9d343093ddd8b429a2f2e26735b` |

Build log `es-v3-computed-runtime-oci-build-20260910.log` SHA
`cf1fd35a0a3b5d2f20e1e097b0c5662a0f71d2084e8466701d5b7fd1c120d54d`.
Smoke log SHA`45222001bbf6ae154f65907b4a7ae509fbbea3cb9f92222837a341726644261b`. Image inspection record is
`es-v3-computed-runtime-oci-inspection-20260910.json`. All fourteen source hashes
remain exact after the build. `git apply --check` confirms the frozen review patch
applies to the branch; it has not been applied there.

## Resume

Review the fixed fourteen files and contract choices, investigate any findings,
and verify any changes before integration. Preserve the explicit test-only
publication witnesses and current compiler refusal. Do not stage/commit this
candidate as reviewed or enable v3 availability on author evidence alone.
Capture/reuse and validation HTTP follow this dependency-ready review step;
operator UX, native execution/readback, combined resources, exact remote CI,
GHCR and actual HiveForge remain incomplete. The current branch image excludes
these isolated computed routes. No private model, live credential, actual user
configuration or runtime AI was introduced.
