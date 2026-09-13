# V3 package dependency pins

Lead-owned prerequisite from b8ca932d6d871c923dc21ce1556d8d05985bffb1.
The existing container now distinguishes v2 and v3 compiler/validation families.
The separate internal v3 admission checks selected binding, engine/storage,
logical/binding digests and exact dependency versions. No publication, hosted
export, client admission or rollback workflow is enabled.

Acceptance uses only the reviewed native-v3 and guarded-package mock fixtures,
with independently computed expected digests. The actual compiler still returns
Incomplete; the test constructs an explicitly labelled internal compiler witness.
Complete plan-to-payload membership/provenance remains a separate prerequisite.

Actual local checks on 11 September 2026 (logs outside checkout):

- `es-v3-package-red1-20260911.log`: new callable admission refused the intended
  v3 candidate; one assertion failure, zero errors. Schema RED separately refused
  the v3 mechanism family (`es-v3-package-schema-red1-20260911.log`).
- Initial Java GREEN passed. Initial schema GREEN attempt exposed AJV strictRequired
  on branch-local required keys; definitions were made explicit without relaxing
  validation. `es-v3-package-schema-green2-20260911.log`: all60 schema cases PASS.
- `es-v3-package-engine-red1-20260911.log`: matching execution/payload could still
  mislabel the selected binding's engine; one intended failure among8 cases.
  The v3 pin check now refuses this mismatch.
- `es-v3-package-green4-20260911.log`: focused Maven reactor PASS,40 tests
  (1 architecture,7 qualified parser,32 package/template including8 new cases),
  zero failures/errors/skips. Includes historical v2, malformed payload/content
  policy, missing/extra dependency, wrong digest, cross-family and deterministic
  unqualified-template checks. No database/native/browser/full OCI run is implied.

Run with pinned Maven3.9.16/JDK21 and Node24.20.0. This worktree owns its build
outputs; remote reviewer retains b74 full/browser/OCI reservation. Full integration
and independent candidate acceptance remain separate. User's newly specified
PostgreSQL16.11 requires its own observation/template/client qualification;
the existing18.6 checks cannot qualify that target.
