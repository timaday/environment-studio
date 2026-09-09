<img src="frontend/public/brand/Environment_Studio_Logo.svg" alt="Environment Studio" width="420">

# Model. Compare. Validate. Export.

Environment Studio helps retarget **many XML configuration records** after an
Oracle or PostgreSQL database is copied. Operators reuse value-free profiles,
map current configuration to a target, supply environment values and export
guarded SQL for their existing database deployment process.

**Status: implementation in progress.** Native JSON/YAML definitions compile in
Java; hosted workspace APIs support immutable definition/profile drafts and
publication, with explicit maintainer authority and private SQLite storage.
Separate [v3 definition](docs/contracts/workspace-http-v3.md) and
[profile draft/history APIs](docs/contracts/workspace-profile-http-v3.md) retain
exact source in an explicitly initialized schema3 workspace.
[Internal v3 publication commands](docs/evidence/qf34-publication-v3.md) now enforce
fresh qualification; the current compiler still refuses publication. V3
publication HTTP and plan integration remain unfinished. The
[internal v3 plan publication lookup](docs/evidence/qf34-plan-workspace-v3.md)
now rechecks exact current definition/profile history before future plan use.
The [internal v3 observation path](docs/evidence/qf34-observation-v3.md) now retains
separate fingerprints and passes actual mock database/TLS and derived-projection
checks; hosted v3 availability remains disabled.
The [internal plan content adapter](docs/evidence/qf34-plan-content.md) now retains
complete original and target proofs. The [shared internal v3 lifecycle](docs/evidence/qf34-shared-lifecycle.md)
now joins creation, observation, target work and physical commands; runtime
composition, profiles and validation remain in progress. The
[shared v3 comparison](docs/evidence/qf34-shared-comparison.md) now verifies complete
original/target proofs and rejects results invalidated by reinspection.
[Shared v3 profile capture](docs/evidence/qf34-shared-capture.md) now returns bounded
physical-only schema3 source from freshly verified original observation.
[Shared v3 profile reuse](docs/evidence/qf34-shared-composition.md) now verifies
whole/partial reuse and complete target proofs under the original plan owner.
The [native launch owner](docs/evidence/d07c3-privacy-native-launch.md) joins
root capture and its first connection; production JNI and client admission remain
unqualified. Both reviewed slices pass combined Java1253.
The [shared versioned workspace adapter](docs/evidence/qf34-shared-workspace.md)
now preserves selected publication pins and fresh qualification; combined Java1260
passes. Runtime registration remains unfinished.
The [native maps sampler](docs/evidence/d07c3-privacy-maps.md) now retains complete
bounded mapping evidence under the launch owner. Equal samples do not establish
mapped byte identity; production JNI and client admission remain unqualified.
Complete Oracle CLOB/PostgreSQL text observation, value-free profile composition
and cross-document structural target materialization are implemented as reviewed
internal mechanisms. The hosted plan service joins these mechanisms with session
leases and revision/cleanup enforcement; see [integration evidence](docs/evidence/d06b1-integration.md).
The nine initial hosted plan HTTP routes enforce owned revisions and one-shot
inspection credentials; see [HTTP integration](docs/evidence/d06b2-integration.md).
The hosted browser now supports definition save/publication, plan creation,
inspection status/recovery and Raw/Formatted document comparison. Its first
[browser slice](docs/evidence/d08a-hosted-workspace.md) is verified with a mock
observation port. The inspection UI remains disabled pending qualification;
explicitly configured hosted inspection APIs enforce ownership, destination and
read-operation checks independently. Profile/edit/export/readback UI remains unfinished. See
[workspace and structural integration](docs/evidence/d01c-d06a-integration.md),
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

Start the loopback demo backend before opening the Vite application:

```bash
mvn -B -ntp -f backend/pom.xml verify
mvn -B -ntp -f backend/pom.xml install
mvn -f backend/server/pom.xml spring-boot:run -Dspring-boot.run.arguments="--studio.mode=demo --server.address=127.0.0.1 --server.port=18080"
```

In a second terminal:

```bash
npm ci --prefix frontend
npm run dev --prefix frontend
```

The Vite development server binds to loopback and forwards `/api` requests only
to `127.0.0.1:18080`. Backend capabilities select the labelled synthetic demo;
an unavailable backend displays Workspace unavailable. Demo accepts no database
credentials and cannot connect to a database. This development proxy does not
configure hosted HTTPS/OIDC or change the container's normal port 8080. Vite
preview does not inherit the proxy.

Build and run the combined Java + React application:

```bash
docker build --target runtime -t environment-studio:dev .
docker run --rm --read-only --cap-drop ALL --security-opt no-new-privileges \
  --tmpfs /tmp:rw,noexec,nosuid,size=128m -p 127.0.0.1:8080:8080 environment-studio:dev
```

Open `http://localhost:8080`. Default mode is `demo`; it accepts no credentials.
Health probes report process health, **not configuration validity**.
Hosted authentication requires explicit OIDC/public-origin configuration; see
[the session contract](docs/contracts/hosted-session.md). Hosted views use actual
server capabilities and authority. Real identity-provider/HiveForge qualification
and complete browser-to-database workflow evidence remain separate work.

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
