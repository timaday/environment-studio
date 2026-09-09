# Exact native hash prerequisite artifact checks

Local proof for source `45d6becc8de0f94a174f0d9310a5540714cd857c`,9 September2026.
A clean external Git archive includes draft/history HTTP and the reviewed
[file-hash prerequisite](d07c3-privacy-hash.md). It predates the owned profile draft
command and profile HTTP extension. Native primitives remain outside production
JNI/registry wiring; this build does not enable a client combination.

| Artifact | Exact identity |
| --- | --- |
| Local tag | `environment-studio:hash-45d6becc8de0` |
| OCI index | `sha256:4bcacf5aea2d534e9ee804b965ebea3dffdc586faa7d923314e12f8c5e850c25` |
| Linux amd64 manifest | `sha256:6be20f8edf1a3c029010961303bc7d5b886b30f632a914fccd485ac5819b843c` |
| Runtime config | `sha256:9d78f29dff1414fd42c3cb60366c0a186bdd09a7365b98b9268c0e7cce0e6a5f` |
| Separate supervisor ZIP SHA-256 | `d93e13bb7dc41fc729149ed1cff0ddc9c5cfdb19d3dec031301a360d4d1da256` |

The build installed the exact pinned Ubuntu libssl-dev/libssl3t64 packages
3.0.13-0ubuntu3.15 in its Java/native test stage, then passed1036 Java tests:
261 core,7 qualified parser,561 server and207 supervisor, zero failures/errors/
skips. The12 hashing families executed against that build environment; the
standalone assembly checksums and hostile-environment launcher also passed.
The unchanged frontend layers were cached; the40 frontend/36 schema checks
remain scoped to the preceding exact9454d9f build.

Protected startup/static UI/health/demo-denial smoke and explicit private workspace
initialization/upgrade/refusal checks passed. It verified non-root UID/GID10001,
read-only root, no-new-privileges and noexec tmpfs, and removed its exact owned
container/volume. Image inspection matched the source label and configured user.
The separate supervisor ZIP is unchanged from9454d9f because the new native
prerequisite is tested but not yet installed into the production distribution.

```text
docker buildx build --load --target runtime --build-arg SOURCE_REVISION=45d6becc8de0f94a174f0d9310a5540714cd857c --metadata-file <external-metadata.json> -t environment-studio:hash-45d6becc8de0 .
bash scripts/container_smoke.sh environment-studio:hash-45d6becc8de0
docker buildx build --target supervisor-artifacts --output type=local,dest=<external-artifact-directory> .
```

External state/logs: `/home/tim/.tmp/es-native-hash-oci-state.json`, archive
`es-native-hash-oci-3xlrondi`, build/smoke/metadata files and
`es-native-hash-supervisor-artifacts-20260909`. All content is generic source or
independently invented mock material. No private model or credentials were used.
The1 GiB smoke does not qualify workload capacity. No new browser, real provider,
full native memory/loader/thread teardown, client/readback, CI/GHCR or HiveForge
result follows from this artifact. No GitHub or registry upload was attempted.
