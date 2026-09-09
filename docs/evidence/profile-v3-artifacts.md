# Exact profile v3 container verification

Source `aaddcfbaabd814c22201e0a54916d89f3f9120e2` contains the reviewed
[physical-only profile ports](qf34-profile-v3.md) and independent adapter cases.
It does not enable v3 persistence, publication, hosted composition or export.
The build context is a clean external Git archive, `es-profile-oci-g4seauy7`.

Actual `docker build --target runtime --build-arg SOURCE_REVISION=aaddcfbaabd814c22201e0a54916d89f3f9120e2`
passes **914 Java tests**: core251, parser7, server488 and supervisor168, with zero
failures/errors/skips. Supervisor checksum and hostile-directory launch checks
pass. The unchanged frontend build/check/test layers reuse the successful exact
frontend inputs from the preceding artifact; no fresh frontend run is claimed.
The last actual frontend40/schema32 and four hosted/two demo browser runs remain
scoped to [7111230](derived-target-artifacts.md).

| Artifact | SHA-256 |
| --- | --- |
| Local OCI index | `6f02aa37d7a125dbe1e8bc9d26090bab85a400cc0466f22304a2266da80545e4` |
| Linux image manifest | `fee209fa331ded23b607e2e5453f59e4f71274ae7d503fbd1fa28ad75471e95d` |
| Image config | `c227fec6942139795b53e93ed345f1aa4876dcaf28a2cf9223451de095ec1798` |
| Standalone supervisor ZIP | `2ac84be011152186804db5360891c405b86c09e6697da8c5b2ce721330a69738` |

Image inspection confirms the exact source label and user10001:10001. Actual
`bash scripts/container_smoke.sh environment-studio:profile-aaddcfbaabd8` passes
read-only/non-root startup, health, static UI, closed demo capability/mutation
checks, private workspace initialization/permissions and explicit schema2 offline
upgrade/refusal controls. This 1 GiB smoke exercises startup, not supported workload
capacity. The same clean archive exports the standalone supervisor ZIP through
the `supervisor-artifacts` target.

External logs: `es-profile-oci-build-20260909.log`,
`es-profile-oci-smoke-20260909.log`, `es-profile-supervisor-export-20260909.log`.
This is local artifact evidence, with no current CI, GHCR or HiveForge claim.
History/native WIP, complete operator workflow, client execution/readback and
combined resource qualification remain separate open work.
