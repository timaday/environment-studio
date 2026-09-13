# QF-0003/0004 — approved v3 contracts

Scope: closed contracts and executable shape checks for the approved
[authoritative derived direction](../product/derived-graph-decision.md), on base
`3ec78cca4070716184337dc7ef05db7ea098c63f`, 9 September 2026. No Java runtime,
availability registry, HTTP, workspace persistence or package authority changed.

## Contract result

[Native v3](../contracts/native-definition-v3.md) separates physical declarations
from computed types, distinct derivations, membership, same-occurrence relations
and computed count rules. The source and inspection schemas are closed and
versioned. Required dependencies are binding-specific; v2 hashes/codecs retain
their meaning. The new logical, binding and profile digest domains are fixed.

[Derived graph v1](../contracts/derived-graph-v1.md) specifies exact Unicode,
PUBLIC readable text eligibility, all contributor locations, observed and typed
target ordering, independent final XML reprojection, UNKNOWN inputs and shared
graph/provenance/UTF-8 budgets. [Profile v3](../contracts/profile-v3.md) contains
only physical slots/edges, with explicit target decisions and fresh recomputation.
Computed donor closure is excluded by the approved product choice.

The independent fixture family extends only the existing invented native/profile
families. Its optional mock field and computed declarations were created for
these generic tests. No private application input was used. The Python oracle
does not import Java; these goldens are future implementation oracles:

| Digest | SHA-256 |
| --- | --- |
| Logical v3 | `bbfead638fae8c814ec3a7c697e9645af9714163032a6dd6738af49cd5f9affd` |
| Mock Postgres binding with child field | `5485559ee69427dd24b31f1f2d8a9386b44ced3eef33f3363bd0ae86a7e8a13d` |
| Mock Oracle binding with direct fields | `e70728ee796ac21a82c365de9b56b783800d8137d6399ee5a968cd8456787373` |
| Physical-only profile v3 | `b661384049dfe987cfbaaffd305f34b7a7e1f287ef7686851aeae1cbcac321e0` |

## Actual checks and independent review

The initial schema test run against version-labelled but unextended physical
schemas had five intended assertion failures: v3 declarations were rejected as
extra properties. It also had one test setup error from recompiling an existing
Ajv schema ID; the test now reuses the existing v2 validator. This setup error is
not evidence of missing production behavior. Log:
`/home/tim/.tmp/es-v3-schema-red-20260909.log`.

After adding the closed arrays and bounds, pinned Node 24.20.0 ran
`node --test scripts/schema.test.mjs` in the isolated archive: **32 passed,
zero failures/skips**, including six new v3 cases. Strict Ajv remained enabled.
Log: `/home/tim/.tmp/es-v3-schema-green-20260909.log`.
`python3 fixtures/native-v3/digest-oracle.py` passed exact expected values and
its controls: label/revision/order invariance, semantic changes, selected child
binding isolation, strict Unicode and unchanged v2 goldens. These checks do not
establish Java compilation or runtime eligibility/partition enforcement.

The non-author reviewer verified all 14 fixed candidate files in
`/home/tim/.tmp/es-v3-contract-b9zhtmx1`. Manifest
`/home/tim/.tmp/es-v3-contract-candidate1-20260909.sha256` has SHA-256
`9806860035b83d0ef008e713ec9d722c8ffa3f232bb455fb935c691a4afae5a9`.
Independent schema execution passed all 32 cases in
`/home/tim/.tmp/es-v3-independent-schema-20260909.log`; the reviewer separately
ran the oracle and compared source/inspection schemas for unintended widening.

Review identified two contract ambiguities: preliminary contributors lacked
an explicit order for fresh target references, and “required target decision”
could be misread as excluding unresolved optional fields. Both were corrected
and reviewed in the derived contract supplement
`/home/tim/.tmp/es-v3-derived-contract-supplement-20260909.md`, SHA-256
`f7f12f76fb67d5094c91b43cda4ef1155d5d45d718d5bc70b51b5957ea4358ac`.
No remaining blocking contradiction was found in that bounded review.

Repository integrity, all 11 Python script tests and diff whitespace checks pass.
The whole staged change is reviewed for independent mock provenance and cumulative
disclosure; the staged content guard also passes. No Java,
browser, database, native-client, heap or OCI qualification was rerun or claimed
for this contract-only slice. Existing qualification records retain their exact
candidate scope. No parallel-development speed-up is claimed.

## Next implementation and open gates

Implement the v3 Java reader/compiler against these schemas and independent
digests, then a separate typed derived engine. Test semantic rejection before
value access, complete contributor sets, exact bounds and immutable results.
Keep availability disabled until the affected observation, typed target/final
reprojection, profile/history, hosted disclosure and export paths are qualified.
Version their closed APIs/pins before extending them. Full MVP/native-client/UX,
combined resource and deployment work remains open. No Q GitHub publication is
authorized, and this contract review does not confer execution authority.
