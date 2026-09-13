# Exact internal-publication and native-association artifact

Local verification on 9 September 2026 uses a clean archive of
`f99aaad3522f183405ae71f8442155ea7c79cc6f`. It includes reviewed internal v3
publication and native executable-association tests. It predates the subsequent
v3 observation candidate and does not enable publication HTTP or native admission.

| Artifact | Observed digest |
| --- | --- |
| OCI index | `sha256:446852f157ef64694a5e01d742fe0215dd27c9d3da67df37b98b4f57a4743ff4` |
| Linux/amd64 manifest | `sha256:7f14162d4373a1ead028fbd607d16196c8346a993097675a45b61afdd3561994` |
| Image configuration | `sha256:20067d148d8cd18cc4ef0b8a0ac5f5fdaf0b7b63a81f44c32daa41531e1b9889` |
| Standalone supervisor ZIP | `sha256:cfad320347e84ee54116ba8f81c4c68d730e22525f2f911611ec11d876a3ccb5` |

The `docker buildx build --load --target runtime` invocation sets the exact
SOURCE_REVISION and requests `--provenance=mode=max --sbom=true`. The build records
the SBOM scanner digest
`ae4f3b554449e7e25548e7d8ccc029d17357348e30c6e3df01b92bc93654d6a9`.
The Java image build actually passes **1,115 tests**:273 core,7 qualified parser,
600 server and235 supervisor, with no failures/errors/skips. It finishes at
20:07:37 UTC. Distribution checks and hostile-environment launcher controls pass.

The unchanged UI check/test/build and pinned build-package layers were cached.
Git comparison confirms frontend/schema/OpenAPI inputs match the preceding
[b35d328 artifact](trusted-file-artifacts.md), whose fresh frontend40/schema40
results remain the relevant evidence. No fresh frontend run or package
installation is claimed for this build.

`scripts/container_smoke.sh` passes protected startup, static UI, health, disabled
demo mutations, private workspace permissions and explicit schema2/schema3
initialization/upgrade/refusal. The runtime revision label and user10001:10001
were inspected. The smoke's owned containers and volumes are absent afterward.
Its1 GiB startup setting does not qualify supported workload capacity.

The `supervisor-artifacts` target exports the exact cached Java assembly;
SHA256SUMS verifies all distribution files. This does not install the new native
prerequisite into production or qualify an executable/client combination.

External local evidence under `/home/tim/.tmp`:

- `es-publication-image-oci-state.json`, with source/archive/tag.
- `es-publication-image-oci-build-20260909.log` and its build metadata JSON.
- `es-publication-image-oci-smoke-20260909.log`.
- `es-publication-image-artifact-export-20260909.log` and distribution checks.

The archive is `es-publication-image-oci-dhcmwx34`; local tag
`environment-studio:publication-image-f99aaad3522f`. All fixtures are independently
invented repository material. No private input entered the context or image.
Browser evidence remains scoped to7111230; no new browser, remote CI/GHCR,
HiveForge, joint-resource or release qualification follows from this local image.
