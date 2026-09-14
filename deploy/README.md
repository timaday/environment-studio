# GHCR publication and HiveForge deployment

CI publishes the application image to **ghcr.io/timaday/environment-studio**
after Java, UI, repository and container gates pass. PRs build/test without
publication. Main publishes `main` and `sha-<full commit>`. Version tags (`v*`)
run the complete release-evidence gate first.

The Monday pilot image is PostgreSQL 16.11 only. Configure PostgreSQL text XML
storage and a psql 16.11/linux-amd64 export client with template
`postgresql16-text-v1`. Oracle implementation remains in the codebase for later
qualification, but Oracle destinations with export clients are rejected for this
pilot and must be treated as unavailable.


## Quick commands

The `scripts/studio.sh` wrapper keeps local and hosted commands consistent. It
does not bake credentials or database facts into the image.

```bash
# Build and smoke-test a local runtime image tagged environment-studio:dev.
scripts/studio.sh package

# Run the synthetic no-database demo on http://localhost:18181.
scripts/studio.sh demo

# Prepare a private local-operator/PostgreSQL env file, then validate and start hosted mode.
scripts/studio.sh init-hosted-env
# edit deploy/hosted.env; keep DB login credentials out of this file
scripts/studio.sh hosted-config
scripts/studio.sh hosted-prepare-workspace
scripts/studio.sh hosted-init
scripts/studio.sh hosted-up
```

Use `STUDIO_IMAGE_LOCAL`, `STUDIO_IMAGE`, `STUDIO_HOST_PORT`,
`STUDIO_ENV_FILE` and `STUDIO_COMPOSE_PROJECT` to override the defaults.
`hosted-config` validates Compose interpolation before starting the service;
`hosted-down` stops the container without deleting the workspace. The first
`package` build downloads npm and Maven dependencies inside Docker and can be
slow on WSL; subsequent builds reuse BuildKit caches while still running the
release-grade checks. For local image iteration after a tested candidate,
`scripts/studio.sh fast-package` skips Docker-stage UI and Maven tests, builds
the same runtime target and still runs the container smoke. Do not use a fast
build alone as release or publication evidence.

## Image contract

| Property | Implemented starter behavior |
| --- | --- |
| Image | OCI/Docker image, initially linux/amd64 |
| Process | One Java 21 process serving React and REST on container port 8080 |
| Entrypoint | Already defined by image; no command override needed |
| User | UID/GID 10001; never root |
| Liveness | GET /actuator/health/liveness |
| Readiness | GET /actuator/health/readiness; process only, no plan validation |
| Capability visibility | GET /api/v1/capabilities reports mode and available hosted capabilities from actual configuration |
| Runtime mode | `deploy/compose.yaml` sets `STUDIO_MODE=hosted`; the image default remains demo when no mode is supplied; unknown modes refuse startup |
| Filesystem | Read-only root; bounded noexec /tmp tmpfs; optional separately initialized private workspace volume |
| Stop | SIGTERM, graceful shutdown 20s; platform grace period at least 30s |
| Resources | Starting budget 1 CPU / 1 GiB; not measured product capacity |
| Replicas | One; no shared session/raw observation support |
| Secrets | Local-operator password or OIDC client secret is supplied by deployment secret handling; database credentials are operation inputs only and are never configured as container env/files |
| External access | Hosted mode requires authenticated local-operator or OIDC access and an explicitly configured PostgreSQL 16.11 destination; DB credentials are supplied per operation |

The workflow emits an `image-reference-<commit>` artifact containing the full
image digest and source revision, and displays the digest in its summary.
SBOM and BuildKit provenance are attached to registry output. Those are build
metadata, not proof of safe SQL behavior. Deploy by digest, not the floating
`main` tag. Pull-only registry credentials belong to HiveForge's secret facility;
GitHub Actions uses its temporary GITHUB_TOKEN to publish and never hands that
token to the application. For a private package, grant the deployment identity
read:packages with access to the package, following the account's SSO policy.

## HiveForge handoff

The repository now includes a HiveForge project manifest (`hiveforge.yaml`) and
one managed component manifest (`environment-studio-service.hiveforge.yaml`) for
the PostgreSQL 16.11 no-OIDC pilot. The component uses HiveForge's Ansible
adapter to render `deploy/hiveforge/templates/docker-compose.yml.j2`, initialize
the private schema3 workspace when needed, start the container and wait for the
container health check. The deploy action uses privilege only to create/stat the
private workspace directory as UID/GID 10001; Docker commands still run through
the target's configured Docker access.

Use the `docker-single-postgres16-local-operator` profile. It deploys one
linux/amd64 service from an exact GHCR image reference, keeps the container root
filesystem read-only, runs as UID/GID 10001, requires a private bind-mounted
workspace, and mounts the PostgreSQL CA bundle read-only. The deployment accepts
local-operator Basic sign-in instead of OIDC, but the public origin remains
strict: HTTPS is required outside localhost/127.0.0.1 rehearsals, and secure
session cookies remain enabled outside loopback.

HiveForge must supply these values through its target environment or secret
handling. Do not commit them to the repository or bake them into the image:

- `HIVEFORGE_PROFILE=docker-single-postgres16-local-operator`
- `STUDIO_IMAGE=ghcr.io/timaday/environment-studio@sha256:...`
- `STUDIO_SECURITY_PUBLIC_ORIGIN`
- `STUDIO_SECURITY_LOCAL_OPERATOR_PASSWORD`
- `STUDIO_WORKSPACE_HOST_PATH`
- `STUDIO_PG_TRUST_MATERIAL_HOST_PATH`
- `STUDIO_PG_HOST`, `STUDIO_PG_PORT`, `STUDIO_PG_DATABASE`
- `STUDIO_PG_TRANSPORT_IDENTITY_SHA256`
- `STUDIO_PG_SYSTEM_IDENTIFIER`, `STUDIO_PG_DATABASE_OID` as observed numeric PostgreSQL identifiers

The deployment template pins the only supported pilot export client tuple:
PostgreSQL server `16.11`, `psql` client `16.11`, `linux-amd64`, template
`postgresql16-text-v1`. Do not override it for this pilot.

Optional values include `STUDIO_HOST_PORT`, `STUDIO_BIND_ADDRESS`,
`STUDIO_SECURITY_LOCAL_OPERATOR_USERNAME`, `STUDIO_COMPOSE_PROJECT`,
`HIVEFORGE_RUNTIME_DIR`, `STUDIO_PULL_BEFORE_DEPLOY`, and matching owner or
definition-publisher issuer/subject overrides. The default local operator user is
`operator`; the password must come from secret handling.

The HiveForge actions are:

- `deploy`: validate environment, render Compose, initialize the workspace if
  absent, optionally pull the image, start the service and wait for health.
- `update`: validate environment, render Compose, optionally pull, reconcile the
  service and wait for health.
- `remove`: stop the service without deleting the bind-mounted workspace or trust
  material.

A local HiveForge-manifest rehearsal validates the manifests and the rendered
Compose shape. Actual HiveForge platform deployment, registry pull identity, TLS
routing and target-host evidence must still be recorded from the deployment
environment before claiming HiveForge release qualification.

A quick local hosted run uses the same Compose contract. Choose a unique host
port, create the workspace outside the repo, set the local-operator password from
your shell or secret manager, and replace the PostgreSQL destination/trust
identity values with observed PostgreSQL 16.11 facts before using a real
destination. Do not add database usernames or passwords to `deploy/hosted.env`.

```bash
scripts/studio.sh init-hosted-env
# edit deploy/hosted.env; use chmod 0600 and keep it out of commits
scripts/studio.sh hosted-config
scripts/studio.sh hosted-prepare-workspace
scripts/studio.sh hosted-init
scripts/studio.sh hosted-up
```

The ignored `deploy/hosted.env` file is copied from
`deploy/hosted.env.example`. Replace the illustrative digest and PostgreSQL
destination/trust identity placeholders before running. The example publishes
loopback port
`${STUDIO_HOST_PORT:-18181}` and uses `localhost` as the operator-facing origin;
for platform ingress, route directly to container port 8080 on its private
service network. Do not expose an unauthenticated real-data service. Database
usernames and passwords are not deployment variables; operators enter them for
scoped read-only inspection/export operations.


## No-OIDC Dockerized demo

For local process/UI rehearsal without OIDC or database access, use the separate
demo Compose file. It starts the same published image in `STUDIO_MODE=demo` and
binds loopback port `${STUDIO_HOST_PORT:-18080}` to container port 8080.

```bash
STUDIO_HOST_PORT=18080 scripts/studio.sh demo
```

Demo mode is intentionally synthetic and denies hosted workspace, inspection,
profile persistence and export routes. Do not use it to claim PostgreSQL 16.11
pilot readiness. Use `deploy/compose.yaml` when PostgreSQL access or guarded
package download is required.

## Hosted PostgreSQL pilot configuration

The PostgreSQL 16.11 pilot image supports hosted local-operator or OIDC authentication, transient server sessions,
strict Host/Origin/CSRF, private workspace storage, owned definition/profile
publication, read-only PostgreSQL inspection, target values, validation and
guarded PostgreSQL package download. Actual IdP/HiveForge qualification remains
an environment-specific gate.

Configure `STUDIO_MODE=hosted`, `STUDIO_SECURITY_PUBLIC_ORIGIN` and
`studio.security.local-operator.enabled=true` with one local operator username,
password, issuer and subject. The Compose template supplies the enablement flag
and maps `STUDIO_SECURITY_LOCAL_OPERATOR_*` environment variables to those
properties. Start sign-in at `/oauth2/authorization/studio`; in local-operator
mode that route issues a Basic-auth challenge and then returns to `/`. Keep
ingress restricted to the approved proxy, which supplies the original public
Host; forwarding headers are not authority and are ignored. Use HTTPS for any
non-loopback HiveForge origin and set `STUDIO_SESSION_COOKIE_SECURE=true` there.

The packaged process probe connects to loopback at `SERVER_PORT` (default 8080)
and sends the Host from `STUDIO_SECURITY_PUBLIC_ORIGIN`. Supply those same values
to service and probe even if other settings use a mounted configuration source.
No public DNS call or Host-policy exemption is used. Probe success proves process
readiness only. Session cookies are Secure/HttpOnly/SameSite=Lax; authenticated
session/logout responses are no-store. Logout cleanup failure revokes access and
returns an explicit inconclusive result while capacity stays quarantined.

The hosted PostgreSQL destination must be configured explicitly from observed,
approved environment facts: destination id, host, port, database name, trust
material, TLS/transport identity, PostgreSQL system identifier, database OID,
owner issuer and owner subject. Oracle is not enabled for this pilot. DB
credentials remain operation inputs held only in session memory; they do not
become `STUDIO_DB_PASSWORD` or an orchestrator secret. An IdP client secret, if
needed, is a separate platform credential supplied by the platform's secret
mount/integration. No raw observations go to the persistent metadata volume.

For hosted definition/profile storage, provision a private volume directory owned by UID/GID
10001 with mode 0700, then run the same image once with the argument
`--initialize-workspace=/workspace` and that directory mounted at `/workspace`.
This offline mode starts no HTTP or IdP client and refuses to overwrite any
existing store. Initialization creates `studio-workspace.db` with mode 0600.
Normal service startup never creates missing storage or migrates unknown schemas.
New stores use metadata schema 2. To upgrade a valid schema-1 store, stop its
service and invoke the same image with the sole argument
`--upgrade-workspace=/workspace` against that private volume. This explicit offline
upgrade preserves v1 history/replays and starts no web or IdP service. Already
upgraded, corrupt and unknown stores refuse; there is no automatic migration.

Schema3 is an explicit storage extension for the internal v3 draft service. The
old commands keep their defaults. To create a fresh schema3 store, use the sole
argument `--initialize-workspace-v3=/workspace`. To upgrade schema2, stop the
service, back up the complete private workspace externally, and use the sole
argument `--upgrade-workspace-v3=/workspace`. Schema1 must first use its existing
explicit upgrade to2. The v3 upgrade preserves v1/v2 snapshots and replay records;
it neither recompiles history nor enables v3 publication or HTTP routes. See
[schema3 storage](../docs/contracts/workspace-storage-v3.md).

Current startup accepts fully audited schema2 or3. Older schema2-only images
refuse3. Rollback after upgrade requires the previous image **and** its matching
pre-upgrade workspace backup, restored with the service stopped. There is no
downgrade or lossless merge of revisions saved after the upgrade. Keep backups
outside this checkout, build context and CI artifacts.

Configure `STUDIO_WORKSPACE_DIRECTORY=/workspace` in hosted mode to enable owned
v1 and native v2 definition/profile routes. Without it, authentication works but workspace routes are unavailable.
Definition publication additionally requires the private
`studio.workspace.definition-publishers` list of exact issuer/subject pairs.
Absent or empty configuration authorizes no definition publisher; headers or
source content cannot grant that role. Profile publication is an owner operation
against an owned immutable published definition. See the [native workspace
contract](../docs/contracts/native-workspace-v2.md) for the closed policies and APIs.
Keep the directory outside the source checkout and build context. Single-replica
ownership, volume durability and backup freshness require deployment evidence.

The linux/amd64 image embeds the pinned SQLite native library in
`/opt/studio/native` and selects it with `org.sqlite.lib.path`. Initialization and
service storage must work with the read-only root and noexec `/tmp`; no writable
executable directory is required for native library extraction. The OCI workspace
smoke initializes a fresh tool-owned volume, checks mode/ownership, and verifies
that a refused second initialization preserves its exact database bytes. It also
checks the explicit schema-1 upgrade and unchanged bytes on repeated/invalid CLI
invocation, then explicit schema2-to3 upgrade and fresh schema3 initialization.
These local mock checks do not qualify an actual platform volume.

Restart expires raw
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

## Separate supervisor candidate

The external guarded supervisor is built and tested separately from web-image
publication. It is not included in the application image and its qualification
registry initially enables no execution combination. See the
[runtime contract](../docs/contracts/guarded-supervisor-runtime-v1.md).

`docker build --target supervisor-artifacts --output type=local,dest=/tmp/studio-supervisor .`
exports its versioned directory and ZIP after the complete Java checks, fixed
dependency inspection and hostile-environment launcher test. The directory's
`SHA256SUMS` covers its launcher, JARs and retained license notices. CI checks those
hashes and retains the separate candidate as an artifact; it does not publish or
install native clients. The supervisor requires a separately qualified Java 21
installation at `/usr/bin/java` and externally installed client runtimes.
The Java-produced ZIP preserves file bytes but does not carry Unix executable
permissions. After extracting it into a new operator-owned installation directory,
run `sha256sum --check SHA256SUMS` there, then
`chmod 0755 environment-studio-guarded` before invoking the launcher. The directory
export already has that permission. Neither form enables an unqualified runtime.

Only the Java build stage installs the exact Python minimal packages used by
invented child-process tests and provides the launcher's fixed Java path. Python,
the supervisor and native database clients are absent from the web runtime.
This build prerequisite does not qualify an operator's terminal or client runtime.
