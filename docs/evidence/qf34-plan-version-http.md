# Fixed model version at legacy plan routes

All existing v1 plan and operation routes now require model V2 through the shared
owner's immutable version admissions. Wrong-version plans or operations refuse
NOT_FOUND before body/output access, async registration, metadata/scratch,
one-shot credentials, reservation expiry or cleanup polling. Summary capture
precedes its metadata slot and retains the exact captured ID plus final equality
checks through output. See the [contract](../contracts/plan-http-version-admission-v1.md).

The initial regression reproduced a V3 plan summary returning200 through the
v1 current-plan route. It used the actual shared service/controller with an
explicit invented publication witness; actual runtime v3 creation is still
unqualified. This is a version-isolation defect in the shared controller path,
not evidence of an available public v3 creation or export bypass.

Correct V2 behavior remains: creation and destination authorization, all thirteen
view routes, command replay, one-shot inspection and retained terminal operation
status. A new current V3 plan cannot hide or relabel old V2 terminal metadata.
Existing transport limits, non-touching polling, closed response shapes and v2
digests remain. This change enables no new route or availability flag.

## Author evidence

Six-file manifest
`07a7eea1e01960d928116e86b41316dc417d979b625b1a78dcbb38b51ae84704`
over8b5843d depends on core4
`bd95a08a27c04fcadad361461d40fc74d7e857592b64921299d3de4b9ad222e3`
and the lead's immutable admission contract. Three HTTP contract files preceded
implementation; production changes touch only the two existing controllers.
Maven3.9.16/JDK21.0.12 results:

- Initial actual RED1: expected404 but got200, one assertion/zero errors among
  three cases,10 September2026 at01:08:55BST. Expanded RED2 precedes production
  changes: seven server assertions/zero errors, with two passing controls.
  Failures include capacity before version refusal, wrong-version async/output
  access, credential consumption, reservation expiry and cleanup effects.
- Nine final cases cover occupied four metadata slots, all thirteen view routes
  and commands under held scratch, preserved credential attempts, expired
  reservations, pending cleanup callback counts, retired V2 operation access
  across V3 replacement, summary replacement during output acquisition and
  foreign/revoked/new-login isolation. Focused63 pass (17 core,1 parser,45 server),
  zero failures/errors/skips,10 September at01:13:42BST. This includes all34
  existing actual HostedBoundaryTest HTTP/OIDC cases and two controller controls.
- Eight compiled mutations omit summary versioning, move metadata admission
  before version checking, omit reservation preflight, credential/status/cancel
  version checks, or command/view preflight. Assertions fail1/1/1/2/3/3/1/1,
  zero errors/skips. Exact six files restored;11 controls pass.
- Full author combination passes1,367 tests (312 core,7 parser,738 server,310
  supervisor), zero failures/errors/skips, assembly and hostile-environment
  launcher,10 September at01:18:17BST. This includes the exact core partition and
  HTTP candidate; independent added controls require final integration below.

## Independent review and integration

The reviewer read all six files and their dependencies, with no confirmed blocker.
Report SHA`53aa43b55f412a752591c15d9ad0eb9701d90526f384e605350fb0f32416dbc5`.
Three independent cases verify reverse retirement (old V3 operation remains hidden
after a current V2 plan), V2-to-V2 replacement during output acquisition, and
request version hints that cannot select V3. Focused/restored16 pass (1 core,
1 parser,14 server). A compiled no-op final summary verifier fails one assertion,
zero errors. All six candidate hashes are restored. These added tests use actual
shared service with servlet mocks; the author's34 HTTP/OIDC cases are separate.
An initial nonexistent PlanViewControllerTest selector ran no tests and is excluded;
the restored command removes it. No compilation or setup failure is counted as RED.

The exact combined thirteen-file candidate over8b5843d has manifest
`f59bf576329c794cf15acf18a7bf8580a8362fef8c1452375adbec9dc2e0f9da`.
It includes both authors' fixed sources and all six independent added cases.
Full integrated Maven verification passes1,373 tests (315 core,7 parser,741 server,
310 supervisor), zero failures/errors/skips, assembly and hostile-environment
launcher,10 September2026 at01:26:09BST. Frontend/schema source is unchanged;
its latest clean install/check/frontend40/schema42/build remains scoped to8b5843d.
All models and credentials in these tests are invented. Browser availability,
new v3 plan HTTP, combined resource qualification, actual native clients/readback,
remote CI and deployment are not established by these checks.
