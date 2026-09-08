# Starter verification — 8 September 2026

This file describes the starter, not production qualification. Exact final CI
results will be recorded after the initial repository publication.

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
