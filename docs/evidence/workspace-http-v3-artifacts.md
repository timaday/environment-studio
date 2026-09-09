# Exact v3 draft/history HTTP artifact

Local proof for source `9454d9fcf8d6af5458598edf8211005af5d11f6f`,9 September2026.
A clean external Git archive includes the reviewed [HTTP routes](qf34-workspace-http-v3.md)
and native argument verifier. It predates the native file-hash prerequisite and
later profile draft work. No v3 publication, profile route, plan or client is enabled.

| Artifact | Exact identity |
| --- | --- |
| Local tag | `environment-studio:http-9454d9fcf8d6` |
| OCI index | `sha256:8fcac3b1d26eba0c8cec050358defa6282f8fe282923f95aced135133e92f9af` |
| Linux amd64 manifest | `sha256:774cd5bc88d550595e429bba56163fbe4ddeec384b5703824f9cdafce47cd70e` |
| Runtime config | `sha256:c0e1d1e056a233c668a2d097b1d7865a4994d7b0b5182ca8e09786deff0cbc80` |
| Separate supervisor ZIP SHA-256 | `d93e13bb7dc41fc729149ed1cff0ddc9c5cfdb19d3dec031301a360d4d1da256` |

The multistage build passed1024 Java tests:261 core,7 parser,561 server and195
supervisor, with no failures/errors/skips. It also ran frontend checking,40 tests,
36 schema tests and production build; the changed OpenAPI copy was in the actual
UI build context. Supervisor checksums and hostile-environment launch passed.
Protected startup/static UI/health/demo-denial smoke and old/new explicit workspace
administration passed, including private modes and unchanged repeat-refusal bytes.
The owned smoke container/volume were removed. The separate supervisor artifact
was exported from the same archive using cached verified build output.

```text
docker buildx build --load --target runtime --build-arg SOURCE_REVISION=9454d9fcf8d6af5458598edf8211005af5d11f6f --metadata-file <external-metadata.json> -t environment-studio:http-9454d9fcf8d6 .
bash scripts/container_smoke.sh environment-studio:http-9454d9fcf8d6
docker buildx build --target supervisor-artifacts --output type=local,dest=<external-artifact-directory> .
```

External state/logs are `/home/tim/.tmp/es-v3-http-oci-state.json`,
`es-v3-http-oci-{build,smoke}-20260909.log`, build metadata and
`es-v3-http-supervisor-artifacts-20260909`. The1 GiB smoke tests startup and
administration, not supported workload capacity. This refresh adds no browser,
deployed TLS/IdP, combined maximum-heap, native-client, CI/GHCR or HiveForge proof.
Earlier7111230 browser evidence retains its scope. No private application material
was used and no GitHub/registry upload was attempted.
