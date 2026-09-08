# GHCR publication and HiveForge deployment

CI publishes the application image to **ghcr.io/timaday/environment-studio**
after Java, UI, repository and container gates pass. PRs build/test without
publication. Main publishes `main` and `sha-<full commit>`. Version tags (`v*`)
run the complete release-evidence gate first. The starter cannot pass that
release gate because the product/DB capabilities are not implemented.

## Image contract

| Property | Implemented starter behavior |
| --- | --- |
| Image | OCI/Docker image, initially linux/amd64 |
| Process | One Java 21 process serving React and REST on container port 8080 |
| Entrypoint | Already defined by image; no command override needed |
| User | UID/GID 10001; never root |
| Liveness | GET /actuator/health/liveness |
| Readiness | GET /actuator/health/readiness; process only, no plan validation |
| Capability visibility | GET /api/v1/capabilities reports demo and disabled inspection/export |
| Runtime mode | STUDIO_MODE=demo; any other mode refuses startup |
| Filesystem | Read-only root; bounded writable /tmp tmpfs; no persistent volume yet |
| Stop | SIGTERM, graceful shutdown 20s; platform grace period at least 30s |
| Resources | Starting budget 1 CPU / 1 GiB; not measured product capacity |
| Replicas | One; no shared session/raw observation support |
| Secrets | No real DB credentials accepted; none configured as container env/files |
| External access | Demo does not need a DB or external API |

The workflow emits an `image-reference-<commit>` artifact containing the full
image digest and source revision, and displays the digest in its summary.
SBOM and BuildKit provenance are attached to registry output. Those are build
metadata, not proof of safe SQL behavior. Deploy by digest, not the floating
`main` tag. Pull-only registry credentials belong to HiveForge's secret facility;
GitHub Actions uses its temporary GITHUB_TOKEN to publish and never hands that
token to the application. For a private package, grant the deployment identity
read:packages with access to the package, following the account's SSO policy.

## HiveForge handoff

The actual HiveForge deployment format/API is not available in this repository.
Public projects sharing its name are not evidence of the user's platform.
Use these standard OCI settings in its existing deployment UI/template:

1. Container image = the exact emitted `ghcr.io/...@sha256:...` reference.
2. Registry = ghcr.io; use platform-managed pull identity if the package is private.
3. Architecture = linux/amd64; container HTTP port = 8080, public URL routed by
   the platform. Keep origin access private behind the chosen ingress.
4. Runtime mode = demo; configure both probes and the resource/user/tmpfs limits
   above. Start with one replica and no durable volume.
5. Apply TLS and the platform's normal access boundary. The public demo contains
   synthetic data only; real-data mode must not be enabled before D02.
6. Deploy, observe probes and the capability endpoint, then record digest,
   platform version, routing settings and observed results for G10.

`compose.yaml` is a real **standard Docker Compose** example. If HiveForge
supports Compose imports, import it and provide STUDIO_IMAGE; that capability
must be confirmed rather than assumed. Otherwise map the same settings to its
container deployment form/API. Do not invent a `hiveforge.yaml` dialect or an
unauthenticated deployment webhook.

A manual local rehearsal with Docker:

```bash
export STUDIO_IMAGE='ghcr.io/timaday/environment-studio@sha256:REPLACE_WITH_EMITTED_DIGEST'
docker compose -f deploy/compose.yaml pull
docker compose -f deploy/compose.yaml up -d
```

Replace the illustrative digest before running. The example binds only to
loopback; for platform ingress, route directly to container port 8080 on its
private service network. Do not expose an unauthenticated real-data service.

## Real-data deployment gate (D02, not implemented)

Before adding DB inspection, implement a qualified authenticated mode with
TLS/Origin/CSRF, OIDC/session handling and authorization/ownership for every
object and download. Confirm trusted proxy behavior and allowed DB destinations.
DB credentials remain operation inputs held only in session memory; they do
not become STUDIO_DB_PASSWORD or an orchestrator secret. An IdP client secret,
if needed, is a separate platform credential supplied by the platform's secret
mount/integration. No raw observations go to the persistent metadata volume.

Choose/qualify a versioned metadata store and mount contract during D02; do not
create an unused volume now and imply persistence works. Restart expires raw
observations and secrets and requires fresh inspection. Horizontal scaling,
shared sessions and tenancy remain outside the initial single-replica contract.

## Rollback and troubleshooting

Rollback deployment by selecting the prior qualified image digest. This changes
application code only: it does not reverse SQL already executed externally.
After restart, reauthenticate/reinspect before exporting. A code downgrade may
be refused by an incompatible metadata schema; qualify forward/backward behavior
before shipping persistence migrations.

If publication fails with package permission errors, check repository Actions
package-write policy and package linkage/access. Do not add a broad PAT to the
repository as a default workaround. If the image fails startup, inspect safe
startup error codes/probes and confirm demo mode/resources. Never turn on body
logging or heap dumps to debug actual credential-bearing requests.

Action commit SHAs, Dockerfile frontend and builder/runtime image digests are
pinned. The image digests were captured from the first actual GitHub build;
Renovate can propose reviewed digest updates. CI must test every dependency/base
change. The source/lockfile, resolved toolchain and emitted image digest belong
in release evidence; pinning dependencies is not evidence of application safety.
