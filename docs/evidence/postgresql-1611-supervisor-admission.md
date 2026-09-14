# PostgreSQL 16.11 guarded supervisor admission

Status: local implementation candidate for the ordinary standalone supervisor
entry point. This admits only the narrowed PostgreSQL 16.11 pilot tuple and keeps
Oracle plus all unqualified client/runtime combinations refused.

This is not production environment qualification, not HiveForge deployment
evidence and not evidence for a real customer database. External PostgreSQL
host/client/TLS qualification remains required before a production environment is
claimed admitted.

## Scope

The ordinary `Main` composition now uses a compiled runtime registry and concrete
console/native runtime ports instead of the previous unavailable ports. The
compiled registry permits only:

- Linux `amd64`/`x86_64`;
- destination engine `POSTGRESQL`;
- destination transport `verified-tls`;
- client family `psql`;
- client version `16.11`;
- the compiled PostgreSQL 16.11 runtime identity in `RuntimeComposition`.

The runtime command uses the configured pinned `psql` installation, writes the
admitted trust material to a private temporary root certificate, sets only fixed
PostgreSQL TLS/process environment variables, launches through the admitted
session launcher and drives the existing framed `ClientProtocol`. Credentials
remain operation-scoped console input. They are not accepted through arguments,
environment variables, URLs, browser storage or package files.

The supervisor still does not run inside the web application, does not add an HTTP
execute route and does not execute generated SQL from the application. Oracle is
preserved in source contracts but remains unavailable in this PostgreSQL-only
pilot admission path.

## Acceptance examples

- PostgreSQL/psql 16.11 on Linux amd64/x86_64 with verified TLS and the compiled
  runtime identity is admitted by the ordinary registry.
- PostgreSQL/psql 18.6, a wrong runtime identity, disposable-loopback transport
  and Oracle/sqlplus are refused by the ordinary registry.
- The `psql` launch command is fixed to `-X -W -A -t -q`, enforces
  `ON_ERROR_STOP=on`, disables pager/history/service-file behavior and carries no
  password in the environment.
- The guarded-supervisor distribution verification still refuses an unrelated
  hostile launch as unqualified.

## Verification

Focused admission and schema checks:

```sh
docker run --rm \
  -v /home/tim/.tmp/es-postgres16-supervisor-admission-20260914:/workspace \
  -v /home/tim/.m2:/root/.m2 \
  -w /workspace maven:3.9.16-eclipse-temurin-21 \
  mvn -B -ntp -f backend/tools/guarded-supervisor/pom.xml \
  -Dtest=ConfigurationTest,RuntimeCompositionTest test
```

Result: BUILD SUCCESS. Tests run: 3, failures: 0, errors: 0, skipped: 0. Finished
2026-09-14 20:28 Europe/London.

Focused supervisor boundary checks:

```sh
docker run --rm \
  -v /home/tim/.tmp/es-postgres16-supervisor-admission-20260914:/workspace \
  -v /home/tim/.m2:/root/.m2 \
  -w /workspace maven:3.9.16-eclipse-temurin-21 \
  mvn -B -ntp -f backend/tools/guarded-supervisor/pom.xml \
  -Dtest=ConfigurationTest,RuntimeCompositionTest,BoundaryTest,SupervisorTest,PackageAdmissionTest test
```

Result: BUILD SUCCESS. Tests run: 15, failures: 0, errors: 0, skipped: 0. Finished
2026-09-14 20:28 Europe/London. `PackageAdmissionTest` was requested but no class
with that exact name was selected by Surefire; package admission remains covered
by the existing supervisor suite below.

Full guarded-supervisor host gate with Maven 3.9.16 and host `/usr/bin/cc`:

```sh
/home/tim/.tmp/apache-maven-3.9.16/bin/mvn -B -ntp \
  -f backend/tools/guarded-supervisor/pom.xml verify
```

Result: BUILD SUCCESS. Tests run: 356, failures: 0, errors: 0, skipped: 3. The
skipped tests are the opt-in PostgreSQL 16.11 Docker witness cases, which require
`ES_POSTGRES16_CLIENT_WITNESS=true` and an explicit PostgreSQL image. Distribution
verification printed `Standalone unrelated-directory hostile-environment launch
PASS; runtime remains unqualified`. Finished 2026-09-14 20:32 Europe/London.

A previous attempt to run the full module inside `maven:3.9.16-eclipse-temurin-21`
was rejected as evidence because that image lacks `/usr/bin/cc`; native privacy
helper tests failed for environment reasons. The host run above used the required
Maven version and the host compiler.

## Remaining limits

External production admission still requires an installed, pinned PostgreSQL 16.11
client identity, verified TLS/trust bundle, destination identity evidence,
operator-selected package digest and DBA/application-owner witness. The local
checks do not prove a real production database, arbitrary schema, HiveForge pull,
platform routing/TLS, or native-client installation outside this workstation.
