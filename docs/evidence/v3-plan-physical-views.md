# Hosted v3 document inventory and entity pages

The two POST routes in [the contract](../contracts/hosted-plan-physical-views-v3.md)
now use the existing controlled physical adapter under fixed V3 ownership. Original
full view scratch and one semantic HTTP record precede body access; the original
pinned view remains executing through complete proof checks, encoding, output,
errors and final verification. Read actions preserve semantic revision. No new
publication readiness or export authority is introduced.

## Fixed candidate and review

Base `f9329cdb1869071eb4793c992e3803096677fed5`. Combined thirteen-file manifest
`449dc87e6ef639b654cc8581b4eb7c44821dbd008c8c994027a99ce9be412c7b` contains author5,
OpenAPI3 and independent HTTP/security supplement5. All hashes were checked before
and after full verification. Author candidate2 manifest
`6296b9c2728c129b0f2e433dd7d265c524efb1775cc192844d3f3d58b1a6c6fd` changes only the
recovery assertion from candidate1; production stays exact. Review by the lead
found no production blocker. The non-author review of root5 verifies all13 and
finds no blocker; it records that the current pagination oracle checks page sizes,
not concatenated reference equality. Existing stable ordering remains unchanged;
that stronger HTTP check is planned with structural views.

External review record `es-v3-physical-http-supplement-review-20260910.md` SHA
`e52ff1b02fea4fff935504c5ecdccec66ebbe9cd16b3a3a7b13627f19374c928`.
Author evidence `es-v3-physical-http-author-evidence-20260910.md` SHA
`f5968e2a6fd3359f74658f0091423877ce673d58783a179b4b225ec446dd38bb`.
Review is local development evidence, not release authority.

## Behavior and adverse checks

- Complete document inventory preserves current digest and null target/change
  before materialization, equal digests/false for an unchanged target, and a new
  exact digest after an edit.
- Actual MockOIDC/CSRF/socket pages return physical-only totals and Existing opaque
  references. A returned reference drives an identity rename and PUBLIC value
  edit; target retains the same reference while original content stays exact.
  Independent literal expected XML supplies the target digest oracle.
- SECRET values are masked in current/target responses. Optional empty PUBLIC
  text and confirmed absence stay distinct. End/beyond-end pages retain total3;
  computed rows do not enter that count. Existing Fresh grammar is preserved.
- Foreign/V2/stale/CSRF/malformed/noncanonical integer/reveal requests refuse.
  Failed requests do not fabricate a target or mutate revision. Controlled held
  output loses its original pin on reinspection and writes zero bytes.
- Actual partial entity body holds scratch and the semantic record while another
  owner receives429 for a view and200 for metadata. Logout retains the documented
  immediate204 or cleanup-inconclusive503 schedules. After worker closure the
  second owner proceeds and fresh login cannot recover the retired plan.
- Credentials, tokens and CSRF canaries stay out of DEBUG logs and workspace files.
  All definitions/XML/owners are independently invented test material. Explicit
  imported publication/observation witnesses are never runtime property switches.

The added UNKNOWN/unreadable fixture initially failed inspection because compiler
`SENSITIVITY_UNKNOWN`/`FIELD_UNREADABLE` diagnostics correctly prevent qualified
projection. The final eligible fixture uses SECRET plus optional PUBLIC. Those
qualification guards remain unchanged; actual HTTP masking for unsupported
UNKNOWN/unreadable definitions is not claimed. Wrong test imports and a diagnostic
Cancellation-lambda setup error were corrected separately; no setup failure is
counted as production RED.

## Commands and results

Pinned JDK21.0.12/Maven3.9.16 and Node24.20.0 on10 September2026:

| Gate | Actual result |
| --- | --- |
| Author route RED | 3 selected tests,1 assertion failure,0 errors before implementation |
| Author final focus | 44 tests,zero failures/errors/skips |
| Route mutations | Wrong target-side selection and omitted rollback each compile and fail1 assertion; restored controls pass. Earlier rollback uncaught-error result retained, not counted as an assertion kill |
| Independent actual HTTP RED | After fixture corrections,3 server assertions fail: absent routes return403 and no semantic record is admitted |
| Independent HTTP focus | 22 tests:2 controls +20server,zero failures/errors/skips. Mistyped plural command class selected none; full verification below covers the actual command suite |
| G01 `mvn -B -ntp -f backend/pom.xml verify` | 1531:324core/7parser/890server/310supervisor,zero failures/errors/skips; assembly and unrelated-directory hostile launcher PASS,03:59:23BST |
| G02 fresh `npm ci`, checking, tests, production build | 40frontend/51schema PASS on fixed OpenAPI3; schema RED51 originally failed2 missing-route assertions |

Full log `es-v3-plan-physical-full1-20260910.log` SHA
`0bcdf996e679723276b092a63a01df31317e0aeaccb7ad21b203c613af978f67`.
HTTP green log SHA`084129b60d83301d8a626c1a932ae22f2ebed578aa3795658fcf39cb980946c0`;
OpenAPI green log SHA`7034890705d0c54c7efe3b6c417626e0c2269fea2b15d316b9f9d4f6d9800d4b`.
G00 whole staged provenance/content review, Python11, repository integrity and
staged diff checks PASS. The reviewed eighteen-file change contains only generic
tool contracts/code and independently invented mock fixtures; the content checker
remains a limited pattern check. No remote upload was attempted.

No current browser/image, maximum heap, native client, remote CI/GHCR/HiveForge or
release qualification is established. Structural/binding and complete document/
computed views, capture/reuse/validation, operator UX, export/readback and the
existing deployment/resource gaps remain. The retained047d1b0 image is older.
