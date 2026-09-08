<img src="frontend/public/brand/Environment_Studio_Logo.svg" alt="Environment Studio" width="420">

# Model. Compare. Validate. Export.

Environment Studio helps retarget **many XML configuration records** after an
Oracle or PostgreSQL database is copied. Operators reuse value-free profiles,
map current configuration to a target, supply environment values and export
guarded SQL for their existing database deployment process.

**Status: implementation in progress.** Native JSON/YAML definitions compile in
Java; hosted workspace APIs support immutable definition/profile drafts and
publication, with explicit maintainer authority and private SQLite storage.
Complete Oracle CLOB/PostgreSQL text observation, value-free profile composition
and cross-document structural target materialization are implemented as reviewed
internal mechanisms. The UI still presents synthetic previews. Hosted plan and
database credential workflows, integrated transformation and guarded SQL export
remain **unavailable**. See [workspace and structural integration](docs/evidence/d01c-d06a-integration.md),
[observation evidence](docs/evidence/d04a-integration.md) and
[profile evidence](docs/evidence/d05a-integration.md).

Starter CI and GHCR publication have passed. [Verification evidence](docs/evidence/starter-verification.md) includes the tested source and image digest.

## Start here

| Need | Read |
| --- | --- |
| Give GPT/Codex the next task | [Agent kickoff](docs/agents/start-here.md), [AGENTS.md](AGENTS.md) |
| Speed up independent development work | [Lead, two writers and reviewer](docs/agents/parallel-development.md) |
| Keep the real model private; give Q feedback to GPT | [Repository boundary](docs/product/repository-content-policy.md), [Amazon Q rules and handoff](docs/agents/amazon-q-feedback.md) |
| Build by Friday 11 September 2026 | [Sequenced build plan](docs/delivery/build-plan.md), [progress](docs/delivery/progress.md) |
| Understand the agreed product | [Requirements](docs/product/requirements.md), [scope decisions](docs/product/decisions.md) |
| Implement safely | [Hexagonal architecture](docs/architecture/architecture.md), [contracts](docs/contracts/README.md) |
| Build the UX | [Flows and components](docs/ux/design-system.md) |
| Assess quality | [TDD and gates](docs/quality/quality-gates.md), [RST](docs/quality/rst.md) |
| Deploy the image | [GHCR and HiveForge](deploy/README.md) |

## Run the starter

Use Node 24, JDK 21, Maven 3.9.16 and Python 3.11+. Dependencies are pinned in
the manifests/lockfile. Docker is needed for the container and database gates.

```bash
npm ci --prefix frontend
npm run dev --prefix frontend
```

The Vite development server binds to loopback. It shows clearly labelled
synthetic data and cannot connect to a database. In a second terminal:

```bash
mvn -B -ntp -f backend/pom.xml verify
mvn -B -ntp -f backend/pom.xml install
mvn -f backend/server/pom.xml spring-boot:run
```

Build and run the combined Java + React application:

```bash
docker build --target runtime -t environment-studio:dev .
docker run --rm --read-only --cap-drop ALL --security-opt no-new-privileges \
  --tmpfs /tmp:rw,noexec,nosuid,size=128m -p 127.0.0.1:8080:8080 environment-studio:dev
```

Open `http://localhost:8080`. Default mode is `demo`; it accepts no credentials.
Health probes report process health, **not configuration validity**.
Hosted authentication requires explicit OIDC/public-origin configuration; see
[the session contract](docs/contracts/hosted-session.md). Its current UI still
shows synthetic previews. Real identity-provider/HiveForge qualification and
database functionality remain separate work.

## CI and container publication

Pull requests run repository, Java, frontend and container checks. Successful
`main` builds publish `ghcr.io/timaday/environment-studio:sha-<full-commit>` and
`main`, with SBOM/provenance. HiveForge should deploy the emitted immutable
`ghcr.io/timaday/environment-studio@sha256:…` reference. Publication does not
declare database functionality ready; version tags require release evidence.

The browser gate builds the frontend and checks definition review at desktop
and narrow widths using Playwright keyboard actions and axe. Run it locally with
`docker build --target browser-check -t environment-studio:browser-check .`.

The actual HiveForge manifest/API has not been supplied. [The deployment
contract](deploy/README.md) and Compose example expose standard OCI settings
without claiming an invented HiveForge integration. No automatic deployment
to an existing environment is configured.

## Delivery constraint

Friday is a **conditional pilot target**, not a production-readiness promise.
The critical path requires generic compiler contracts, independently invented
mock fixtures and disposable Oracle/PostgreSQL test databases. Actual application
semantics and destination identity must also be qualified in a separate authorized
private workspace; real XML, schemas, mappings and even value-free real profiles
never enter this repository. Share only generic feedback through the Q workflow.
Scalar-only replacement does not fulfill the requested topology-change workflow.
Unsupported engine/storage/client combinations stay visibly unavailable.
