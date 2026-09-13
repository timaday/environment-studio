# Exact schema3 workspace candidate artifacts

Local proof for source `5925bc8b396237de901745370e3743c7c50e0962`, built from a
clean external Git archive on9 September2026. This includes the reviewed
[schema3 storage/drafts](qf34-workspace-v3.md), prior separate v3 history and
[native direct-parent check](d07c3-privacy-parent.md). It adds no v3 HTTP,
publication, plan admission or qualified native client combination.

| Artifact | Exact identity |
| --- | --- |
| Local tag | `environment-studio:storage-5925bc8b3962` |
| OCI index | `sha256:4f9663a4ac349926166bc1f42247ba83aeaf4fd2177f45ea27fffb697ca148e1` |
| Linux amd64 manifest | `sha256:93b38b893352d7aea361733b0097f1552c3d553a85ffb72a44fc88321adb117b` |
| Runtime config | `sha256:49f4d577262bb0f51ef60a076dba5286c4854f351ce61a2e151871c2a3a22549` |
| Separate supervisor ZIP SHA-256 | `6de6ddb5b1ff56c9e24d1b0fad6eb38a72141f11437500672e6f8a16017e7fc6` |

The multi-stage build ran973 Java tests:261 core,7 qualified parser,522 server,
183 supervisor; zero failures/errors/skips. It checked the assembled supervisor's
file hashes and hostile-environment launcher. The unchanged frontend layers were
cached: this is not a fresh frontend40/schema32 run. Later v3 HTTP contract/schema
work is outside this source. Browser evidence remains separately scoped to7111230.

The actual protected image passed startup/static UI/health, disabled demo
inspection/export, denied mutation, read-only root, UID/GID10001, no-new-privileges
and noexec tmpfs checks. Its owned-volume smoke passed old initialization2 and
explicit1-to2 upgrade, explicit2-to3 upgrade with all nine versioned tables,
fresh3 initialization, private directory/file modes and unchanged bytes after
repeated/invalid administration. Both new modes start no web or identity client.
The smoke cleaned its exact owned container and volume. Image inspection verified
the source label and configured user.

Commands ran against that same external archive:

```text
docker buildx build --load --target runtime --build-arg SOURCE_REVISION=5925bc8b396237de901745370e3743c7c50e0962 --metadata-file <external-metadata.json> -t environment-studio:storage-5925bc8b3962 .
bash scripts/container_smoke.sh environment-studio:storage-5925bc8b3962
docker buildx build --target supervisor-artifacts --output type=local,dest=<external-artifact-directory> .
```

Build metadata and logs remain in the owned external task area; only generic
results and artifact identities are recorded here. No private application model,
real configuration or credential entered the archive, build, log or image.

The1 GiB smoke proves startup/administration only; it does not repair the known
workload-capacity gap. Combined maximum workloads and later mechanisms still need
measurement. Native image/chain/coordinator/client/privacy/readback qualification,
remaining operator UI, current-revision CI, GHCR and HiveForge remain outstanding.
There was no push, registry publication or deployment in this artifact refresh.
