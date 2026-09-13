# Immutable plan model version admission

The shared plan owner now records each installed plan's immutable V2/V3 model
version, derived from the sealed definition variant. Original owned ID/version
metadata survives retirement with the existing bounded replay history. Each
operation also retains its original version after cleanup releases the plan
reference. A replacement current plan cannot reclassify old metadata. See the
[contract](../contracts/plan-version-admission.md).

Explicit expected-version overloads reject mismatched IDs as NOT_FOUND before
reservation expiry, permits, credential consumption, scratch, replay changes or
cleanup callbacks. Null expected versions refuse INVALID_REQUEST. Every guarded
preflight retains the original continuation's live-lease/revision checks. Current
summary selection pins the matched plan ID across phases. Correct-version retired
replay and terminal status/cancel remain accessible to the same original live
lease; a new login cannot recover them. Existing v2 identity bytes and shared
capacity are unchanged. No route is enabled by this internal mechanism.

## Fixed review and verification

Author four-file manifest
`bd95a08a27c04fcadad361461d40fc74d7e857592b64921299d3de4b9ad222e3`
uses69752c8 plus the reviewed publication/runtime24; those dependencies are
committed in8b5843d. The lead contract preceded implementation. Independent report
SHA`2f3eae48022c82e4a587b31c656c6e5bb4e3ee48b457f87fe9c53f65d3952bd6`
records review of all four files and original lifecycle paths, with no confirmed
source blocker. Maven3.9.16/JDK21.0.12 local evidence:

- Actual RED2: eight assertions/zero errors among ten cases before guard
  implementation. Fifteen final author cases cover both model variants, wrong
  version before resource effects, nulls, retirement in both directions, exact old
  replay/status, inter-phase replacement, cleanup callbacks, original leases and
  global mixed-version capacity. Focused52 pass, including37 existing controls.
  Full1,358 pass (312 core,7 parser,729 server,310 supervisor), zero failures/
  errors/skips plus assembly/hostile launcher,10 September2026 at01:15:53BST.
- Six compiled author guard mutations remove owned-ID version, operation version,
  current version, matched ID pin, null validation or view preflight. Assertions
  fail6/5/3/1/1/2, with zero errors/skips. Exact four files restored;52 pass.
- Three independent cases replace a current V2 with another V2 between phases,
  revoke the original lease between version preflight and cleanup polling, and
  explicitly count permits before wrong-version refusal/correct retry/replay.
  Focused20 pass. Three compiled mutants allow current substitution, omit status
  preflight or allocate a permit before checking version; each fails one assertion,
  zero errors/skips. Exact four files restored and20 controls pass.

Setup failures remain in external records: an initial scaffold was accidentally
inserted inside ViewAdmission; a later author cleanup fixture omitted retry(); the
independent test omitted the PlanPorts qualifier on CredentialLengths. These were
compilation corrections to scaffolding/tests, not behavior RED or mutation kills.

The combined thirteen-file candidate over8b5843d has manifest
`f59bf576329c794cf15acf18a7bf8580a8362fef8c1452375adbec9dc2e0f9da`, including
fixed core/controller sources and all six independent added cases. Full integrated
Maven verification passes1,373 tests (315 core,7 parser,741 server,310 supervisor),
zero failures/errors/skips, assembly and hostile-environment launcher,
10 September2026 at01:26:09BST. These are independently
invented model/observation witnesses, not current v3 publication qualification.
The [legacy HTTP adoption](qf34-plan-version-http.md) is the separate enforcement
at public routes. Versioned v3 transport/routes, full operator workflow, combined
resources, clients/readback and deployment remain required.
