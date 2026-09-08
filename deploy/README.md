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
| Capability visibility | GET /api/v1/capabilities reports mode and disabled inspection/export |
| Runtime mode | STUDIO_MODE=demo by default; hosted requires the configuration below; unknown modes refuse startup |
| Filesystem | Read-only root; bounded noexec /tmp tmpfs; optional separately initialized private workspace volume |
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

## Hosted authentication rehearsal and remaining data gate

D02a implements hosted OIDC, transient server sessions, strict Host/Origin/CSRF,
cookie controls and cleanup hooks. It has independent mock protocol evidence;
actual IdP/HiveForge qualification remains outstanding. The UI still shows
synthetic previews and inspection/export remain disabled.

Configure `STUDIO_MODE=hosted`, `STUDIO_SECURITY_PUBLIC_ORIGIN` (approved HTTPS
origin), `STUDIO_SECURITY_ISSUER` (approved HTTPS issuer),
`STUDIO_SECURITY_CLIENT_ID` and platform-managed client authentication through
`studio.security.client-secret`. Do not enable the test-only HTTP issuer profile.
Register the exact public-origin `/login/oauth2/code/studio` callback with the
IdP. Start login at `/oauth2/authorization/studio`. Keep ingress restricted to the
approved proxy, which supplies the original public Host; forwarding headers are
not authority and are ignored.

The packaged process probe connects to loopback at `SERVER_PORT` (default 8080)
and sends the Host from `STUDIO_SECURITY_PUBLIC_ORIGIN`. Supply those same values
to service and probe even if other settings use a mounted configuration source.
No public DNS call or Host-policy exemption is used. Probe success proves process
readiness only. Session cookies are Secure/HttpOnly/SameSite=Lax; authenticated
session/logout responses are no-store. Logout cleanup failure revokes access and
returns an explicit inconclusive result while capacity stays quarantined.

Before adding DB inspection, qualify actual TLS/proxy/IdP behavior and extend
authorization/ownership to every implemented object and download. Confirm allowed DB destinations.
DB credentials remain operation inputs held only in session memory; they do
not become STUDIO_DB_PASSWORD or an orchestrator secret. An IdP client secret,
if needed, is a separate platform credential supplied by the platform's secret
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
invocation. These local mock checks do not qualify an actual platform volume.

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
