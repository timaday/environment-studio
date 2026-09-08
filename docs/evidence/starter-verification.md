# Starter verification — 8 September 2026

**Starter CI passed and the image was published.**
[Verified run 34220469080](https://github.com/timaday/environment-studio/actions/runs/34220469080)
validated source commit `55ce47bb926c18fc47322219a031ba0460f3d12d`.
The pinned Java/React build and container startup/static-UI/health/mutation-denial
checks passed. GHCR publication with SBOM/provenance succeeded. See
[image-reference.json](image-reference.json) for the exact deployed-artifact input.

This is starter evidence, not SQL, database or HiveForge qualification.
The history below records the failures found and corrected along the way.

- UI tests were authored first. Initial run failed because App.tsx did not exist
  (module-resolution failure, not a behavior assertion RED). Implementation then
  passed three component behavior cases: demo/export denial, multi-document
  navigation, and concrete value visibility in placeholder mode.
- TypeScript/Biome checks and a Vite production build passed locally after
  correcting XML pane keyboard/ARIA markup. Literal `${...}` strings are
  intentional placeholder fixtures; only that lint rule is scoped off in those
  fixture/test files, not globally.
- Four JSON Schema positive/negative fixture tests passed locally. These verify
  shape/refusal, not Java semantic compilation or publication readiness.
- Java behavior/architecture/HTTP tests are authored. Local Maven invocation
  returned command-not-found: no local RED/GREEN execution is claimed. The
  workspace has a Java 17 runtime but no javac/Maven/Docker; Java 21 and container
  evidence must come from CI.
- Repository and release bookkeeping tests are run before publication. The
  release gate is expected to report BLOCKED for the unimplemented capabilities.
- No actual DB connection, SQL writer, exact-client transaction test, security
  session test, operator session or HiveForge deployment has been executed.

See CI for actual build evidence. Main image publication is a development
artifact, not a production capability certificate.

## First actual CI result

[Run 34219658452](https://github.com/timaday/environment-studio/actions/runs/34219658452) compiled the core/server and passed 12 core checks. Server tests
then failed with a JUnit ExtensionContext binary incompatibility caused by an
explicit older JUnit BOM override. Removed that override so the pinned Spring
Boot BOM manages compatible JUnit versions. No image was published by that run.

The first CI log also supplied the exact Node/Maven/JRE/Dockerfile frontend
digests; these are now pinned in Dockerfile. Demo security declares an empty
user store so no unused bootstrap password is generated/logged.

## Second actual CI result

[Run 34220005166](https://github.com/timaday/environment-studio/actions/runs/34220005166)
passed 12 core checks, all three HTTP boundary tests, frontend/schema checks and
built the combined image. The startup probe then encountered a TCP reset before
Java had begun serving. Added that specific transient startup condition and
remote disconnect to the existing bounded readiness retry; the deadline and
required health/capability/security assertions are unchanged. Publication stayed
blocked until the smoke test passed.

## Successful container and registry evidence

Quality job `102042133418` passed its container contract smoke test. Publication
job `102042291238` published both the source-commit tag and `main` at digest
`sha256:20e6a8f5480660dad54141f18957e394fbafc0ec4a3e847ee3cd7fb89859341e`.
The image-reference artifact is available from the verified workflow run.
Subsequent documentation-only commits do not alter that evidence's source scope;
new images still receive their own revision tag/digest and CI run.

The source fingerprint for this evidence is
`cf0b13f6010e112248df088a641891cd6f5a0eb74a2714593ef9cbc3ec3cf5fd`.
The separate product release matrix remains NOT_RUN where functionality does
not exist. No production-readiness claim is implied by this successful build.
