# No-OIDC hosted pilot and HiveForge local rehearsal

Date: 13 September 2026, Europe/London.
Branch: `implementation/no-oidc-hosted-pilot-20260913`.
Base: `7c7cab4` (`Merge pull request #12`).
Fixed hosted-auth source before HiveForge work: `f5e78498d5781c04548dd491677f938564e955ac`.

## Scope

This evidence covers the PostgreSQL 16.11 no-OIDC pilot deployment path only.
It does not qualify Oracle, production database contents, production TLS routing,
actual HiveForge target execution, registry pull identity, or package execution
against a real destination.

The HiveForge files added in this slice are:

- `hiveforge.yaml`
- `environment-studio-service.hiveforge.yaml`
- `deploy/hiveforge/deploy.yml`
- `deploy/hiveforge/update.yml`
- `deploy/hiveforge/remove.yml`
- `deploy/hiveforge/tasks/validate.yml`
- `deploy/hiveforge/tasks/render-compose.yml`
- `deploy/hiveforge/templates/docker-compose.yml.j2`

The templates keep OIDC disabled for this pilot by enabling the local-operator
hosted mode. The operator password is supplied by deployment secret handling.
Database credentials remain operation inputs only and are not configured as
container environment or files.

## Local no-OIDC image

The runtime image was built locally with the same command used for the GHCR
release path:

```bash
docker build --target runtime --build-arg SOURCE_REVISION=$(git rev-parse HEAD) -t environment-studio:local-operator .
```

Result: PASS. The image runs as `10001:10001`. The build reused cached frontend
checks/build and backend Maven reactor output for core, qualified XML parser,
server and guarded supervisor tests, plus supervisor checksum verification.

Because this evidence file is committed in the same candidate, the immutable
source revision for a published image must be taken from the final Git commit and
the GHCR digest emitted by that build. Do not use a local floating tag as release
evidence.

## Hosted-auth regression

Focused checks run before the image build:

```bash
mvn -B -ntp -f backend/pom.xml -pl server -Denforcer.skip=true -Dtest=HostedSettingsTest,LocalOperatorSecurityTest,HostedSessionsTest test
python3 scripts/check_repository_content.py
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/check_repository.py
git diff --check
```

Result: PASS. The focused Java command ran 28 tests. Script unittest ran 14
tests.

Runtime checks on `deploy/compose.yaml` with local origin `http://localhost:18181`
showed:

- `/actuator/health/readiness` returned 200 `{"status":"UP"}`.
- `/api/v1/capabilities` with a stale cookie and cached Basic header returned 200
  in hosted mode.
- `/api/v1/session` with local Basic credentials returned 200.
- A second `/api/v1/session` with local Basic credentials replaced the prior
  same-owner local lease and returned 200.
- Foreign-origin capability access returned 403 `REQUEST_ORIGIN_DENIED`.
- A headless browser using HTTP credentials loaded the hosted shell instead of
  the `Workspace unavailable` screen. Expected empty-workspace API results such
  as current plan 404 remained visible.

## HiveForge manifest and Compose rehearsal

The local HiveForge schema loader accepted the manifests after removing the
unsupported root `version` field:

```bash
node --input-type=module -e "import { loadProjectRegistry } from './dist/src/manifest/project-registry.js'; const registry = await loadProjectRegistry('/home/tim/IdeaProjects/environment-studio'); console.log('hiveforge-loadProjectRegistry PASS', registry.project.name, registry.components.map(c=>c.name).join(','));"
```

Run from `/home/tim/IdeaProjects/HiveForge`.
Result: `hiveforge-loadProjectRegistry PASS environment-studio service`.

YAML parsing of the root manifest, component manifest and Ansible playbooks
passed for seven files.

A no-interpolate Docker Compose config check of the HiveForge template passed and
confirmed the local test password was not written into the checked Compose output.

A local HiveForge-style smoke copied
`deploy/hiveforge/templates/docker-compose.yml.j2` to
`/home/tim/.tmp/environment-studio-hiveforge-runtime/docker-compose.yml`, used a
fresh private workspace directory owned by UID/GID 10001, and started the service
with project `environment-studio-hiveforge-test` on `http://localhost:18182`
using the local `environment-studio:local-operator` image built from the branch.

Results:

- workspace owner/mode prepared as `10001:10001` and `0700` through a temporary
  root helper container using the same runtime image with `--entrypoint /bin/sh`.
- workspace initializer completed on a fresh workspace.
- service container reached Docker health status `healthy`.
- `GET /api/v1/capabilities` returned 200 with hosted mode.
- `GET /api/v1/session` with local Basic credentials returned 200 with
  `authenticated: true` and CSRF metadata.
- the test service was stopped with `docker compose down --remove-orphans`.
- temporary command output files were checked for Authorization header leakage
  and removed.

The final smoke workspace was preserved outside the repository at
`/home/tim/.tmp/environment-studio-hiveforge-workspace-smoke-current`. An earlier
failed rehearsal workspace was also preserved outside the repository for
diagnostics; it was not copied into the checkout or build context.

## Remaining qualification

Actual HiveForge release qualification still requires a real target run using the
published GHCR digest, platform registry credentials, platform routing/TLS,
configured PostgreSQL 16.11 identity facts and the real PostgreSQL CA bundle.
Record the HiveForge run result before claiming G10/release readiness.
