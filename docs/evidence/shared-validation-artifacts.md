# Shared v3 plan and native owner artifact

The local image uses the clean source archive of
`047d1b025e74d929dd874dd9b48ad92c954455af`: reviewed shared v3 lifecycle,
comparison, capture/reuse, physical/computed views and validation, plus the native
launch owner, bounded maps and owned executable inspection. Versioned publication
HTTP and runtime composition are later candidates outside this image.

| Artifact | Observed digest |
| --- | --- |
| OCI index | `sha256:de8823c7a751645aca7b4b2dcb2b728a0d8746eaa336b8d70d3876072e6c9c9e` |
| Linux/amd64 manifest | `sha256:03a56f25fb56757874df56becff010d64689fdac78af320aa201f61b40ad4f8a` |
| Image configuration | `sha256:b20b681d31a63b2fa0bcf379a9fc7e5875a235ebedf0c7e067511de44f8e1f52` |
| Standalone supervisor ZIP | `sha256:61fe62cc2ee54ac9d3826ee64d423ca7069a5955f02550892553a658bd4219cc` |

`docker buildx build --load --target runtime --provenance=mode=max --sbom=true`
sets exact SOURCE_REVISION. The Java stage runs afresh and passes1,320 tests
(297 core,7 parser,706 server,310 supervisor), zero failures/errors/skips,
assembly and hostile-environment launcher checks,9 September2026 at23:50:41UTC.
Unchanged frontend checking/tests/build and dependency/package layers are cached;
this build does not claim a fresh frontend execution.

`bash scripts/container_smoke.sh environment-studio:shared-validation-047d1b025e74`
passes startup, static UI, health, denied demo mutations, private workspace
permissions and explicit schema2/schema3 initialization/upgrade/refusal. The
image revision and user10001:10001 match. The1 GiB smoke configuration establishes
startup only; supported workload capacity remains a separate open gate.

The `supervisor-artifacts` target exports the same cached exact assembly; all
distribution SHA256SUMS and the separate ZIP digest verify. Structural executable
and mapping primitives do not qualify production JNI, loader closure, mapped byte
identity or an actual native client combination.

External local records under `/home/tim/.tmp` use the prefix
`es-shared-validation-image`: OCI state/build metadata/build and smoke logs,
artifact-export log and exported distribution. Source archive is
`es-shared-validation-image-oci-k972t8tm`. The build context contains only reviewed
repository material and independently invented fixtures, with no private inputs.

Actual shared TLS mock database workflows are separately recorded in
[validation evidence](qf34-shared-validation.md). Browser evidence remains scoped
to7111230. No current browser, remote CI/GHCR, HiveForge, joint-resource or release
qualification follows from this local image; actual v3 compilation remains gated.
