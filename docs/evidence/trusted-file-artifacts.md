# Exact profile HTTP and trusted-file artifact evidence

9 September 2026. Source
`b35d3286ef7ac844883086339f322e80a21829c2` includes the reviewed
[profile HTTP](qf34-profile-http-v3.md) and [native trusted-file](d07c3-privacy-file.md)
changes. The image was built from a fresh `git archive` of that exact commit;
isolated later publication/executable-association work is absent.

| Artifact | Observed SHA-256 |
| --- | --- |
| Local OCI index | `0bfa051aad2ab37bbef243b7d645725e21ff6ab204c38419eb79c051c338ece6` |
| Linux amd64 manifest | `cf38d866303a01c4ace26f07456b6fc427bd69daac4075088ceb8a598e30f87f` |
| Image config | `5548d07bb210f37e8353e82d97e7e5a104e679e2db3eda40feeae70853cbcffa` |
| Standalone supervisor ZIP | `5f9176b6326558fbf63b01d5e838fbed09b44efc7bce527cce72b3aae979a7da` |

Local tag: `environment-studio:file-b35d3286ef7a`. The OCI index includes build
attestation/SBOM metadata. Its revision label and user10001:10001 were inspected.
The ZIP is a separate distribution artifact; its presence does not establish
production JNI, native privacy/client qualification or web execution of SQL.

The actual container build passes1086 Java tests:266 core,7 parser,592 server
and221 supervisor, zero failures/errors/skips. Maven finished at19:37:48 UTC
in4m21s. Fresh frontend checking,40 component tests,40 schema tests and production
build pass. The pinned native build packages are unchanged from the preceding
hash image; their installation layer was cached. No fresh package-installation
qualification or browser run is inferred from caching.

Protected smoke passes read-only root filesystem, dropped capabilities,
no-new-privileges, non-root startup, readiness/static UI, disabled demo capability
and mutation denial. Actual workspace administration checks pass permissions,
overwrite refusal, legacy/schema2 handling, explicit schema3 upgrade and fresh
initialization, with unchanged bytes on refused operations. Owned smoke containers
and volume are absent after cleanup. The1GiB smoke limit is a startup check, not
qualification of supported workloads or combined resource maxima.

```text
docker buildx build --load --target runtime --build-arg SOURCE_REVISION=b35d3286ef7ac844883086339f322e80a21829c2 --provenance=mode=max --sbom=true --metadata-file /home/tim/.tmp/es-trusted-file-oci-build-metadata-20260909.json -t environment-studio:file-b35d3286ef7a .
bash scripts/container_smoke.sh environment-studio:file-b35d3286ef7a
docker buildx build --target supervisor-artifacts --output type=local,dest=/home/tim/.tmp/es-trusted-file-supervisor-artifacts-20260909 .
```

External archive: `/home/tim/.tmp/es-trusted-file-oci-bbs_drcr`; state and actual
logs: `es-trusted-file-oci-state.json`,
`es-trusted-file-oci-build-20260909.log`,
`es-trusted-file-oci-smoke-20260909.log`,
`es-trusted-file-artifact-export-20260909.log`. All material is invented fixtures
and generic application/tool code; no private model or configuration was supplied.

This is local exact-source evidence. The last actual hosted/demo browser checks
remain scoped to7111230. Current remote CI, GHCR publication and HiveForge pull,
TLS/routing/identity/storage/restart/rollback remain unverified. New publication,
complete operator flow, native client execution/readback and resource/release
qualification remain required; this image is a development artifact.
