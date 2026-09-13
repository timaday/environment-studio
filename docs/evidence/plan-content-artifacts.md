# Exact v3 content and native ELF artifact

Local verification on 9 September 2026 uses a clean archive of
`76ab78b3baa827481e1265da30b528ec9beaeef4`. It includes reviewed internal v3
observation, plan metadata, complete content and native structural ELF checks.
Shared v3 lifecycle and the native launch coordinator remain later work.

| Artifact | Observed digest |
| --- | --- |
| OCI index | `sha256:90e771cd6afb0bdeb75b42d8552c546b56f4c0a273e0d4caea1e4c5a07ac9c61` |
| Linux/amd64 manifest | `sha256:6c85fe503a85cca6d0303770a57ccb308ce948889b3529bc3e85b5f4f7011ecb` |
| Image configuration | `sha256:cc28b464ae050d8c9a27695692240c8beb1cb68b204783f1d633ccfc9034c496` |
| Standalone supervisor ZIP | `sha256:07556f10ce4d3c79cabd3af84be9c0ac185ab2a437308302454ae3bc88dc706a` |

`docker buildx build --load --target runtime --provenance=mode=max --sbom=true`
sets the exact SOURCE_REVISION. Java actually passes **1,187 tests**:279 core,
7 qualified parser,637 server and264 supervisor, with no failures/errors/skips.
Assembly and hostile-environment launcher controls pass; Java finishes at
21:35:22 UTC. Frontend checking,40 component/API tests,40 schema tests and
production build also run afresh and pass. Dependency/build-package layers
are cached.

`bash scripts/container_smoke.sh environment-studio:plan-content-76ab78b3baa8`
passes protected startup, static UI, health, denied demo mutations, private
workspace permissions and explicit schema2/schema3 initialization/upgrade/refusal.
The image revision label and user10001:10001 match. The1 GiB smoke setting
establishes startup only; it does not qualify supported workload capacity.

The `supervisor-artifacts` target exports the cached exact assembly. Its ZIP
digest and all distribution SHA256SUMS verify. This artifact does not install
production JNI or qualify a native executable/client combination.

External evidence under `/home/tim/.tmp`: `es-plan-content-image-oci-state.json`,
`es-plan-content-image-oci-build-20260909.log`, its build metadata JSON,
`es-plan-content-image-oci-smoke-20260909.log`,
`es-plan-content-image-artifact-export-20260909.log`, and exported distribution
`es-plan-content-image-artifacts-20260909`. Clean source archive:
`es-plan-content-image-oci-6y8v2h39`. All fixtures are independently invented
repository material. No private input entered the build context.

Browser evidence remains scoped to7111230. No fresh browser, remote CI/GHCR,
HiveForge, joint-resource or release qualification follows from this local image.
