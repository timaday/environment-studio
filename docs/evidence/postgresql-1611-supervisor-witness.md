# PostgreSQL 16.11 guarded supervisor client witness

Status: local invented-environment witness for the narrowed PostgreSQL 16.11
operator path. This is not production database qualification, not TLS evidence,
not GHCR/HiveForge release evidence and not a claim that arbitrary customer
schemas are supported.

## Scope

The witness adds an explicit opt-in JUnit test under the separately installed
supervisor module. It is skipped in ordinary Maven runs unless
`ES_POSTGRES16_CLIENT_WITNESS=true` is set. The test starts a disposable
`postgres:16.11-bookworm` container using independently invented schema/table/XML
values, generates the package transaction through the existing package admission
and `TransactionTemplates` path, then drives the existing Java `ClientProtocol`
against the real container `psql` 16.11 binary through `OwnedNativeProcess`.

The browser/application still does not execute SQL, persist credentials, or make
`exportAvailable=true`. The witness does not introduce a runtime registry entry.
It proves only the bounded local combination named above.

## Acceptance examples

- the real `psql` 16.11 prompt matches the supervisor's fixed `Password: `
  transcript expectation and responds to the `ES_SETTINGS` probe;
- a generated PostgreSQL 16.11 package program updates the invented row only
  after the supervisor sends COMMIT and receives the committed frame;
- a supervisor-side refusal before the program write sends ROLLBACK to the still
  usable client, receives the rollback frame, exits cleanly and leaves the row
  unchanged;
- a lost committed-frame read after real `psql` returns the committed marker remains
  `UNKNOWN`, makes no rollback claim and leaves the row committed;
- the witness remains skipped by default so routine Java gates do not require
  Docker or a database daemon.

## RED and GREEN

Initial live execution refused before commit with `NOT_APPLIED` and inconclusive
cleanup when the disposable database used the image default locale. That was an
expected guard finding: the PostgreSQL template refuses default-collation indexes
unless the database collation/ctype are within the supported C/UTF-8 shape. The
witness now initializes the disposable database with
`POSTGRES_INITDB_ARGS=--locale=C --encoding=UTF8`, matching the supported
collation contract.

Focused explicit witness:

```sh
ES_POSTGRES16_CLIENT_WITNESS=true ES_POSTGRES16_IMAGE=postgres:16.11-bookworm \
  /home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn -B -ntp \
  -f backend/tools/guarded-supervisor/pom.xml -Dtest=Postgres16ClientWitnessTest test
```

Result: BUILD SUCCESS. Tests run: 3, failures: 0, errors: 0, skipped: 0. Finished
2026-09-12 17:18:44 Europe/London.

Default invocation:

```sh
/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn -B -ntp \
  -f backend/tools/guarded-supervisor/pom.xml -Dtest=Postgres16ClientWitnessTest test
```

Result: BUILD SUCCESS. Tests run: 2, failures: 0, errors: 0, skipped: 2. Finished
2026-09-12 17:13:52 Europe/London.

Integrated backend gate after the post-COMMIT witness update:

```sh
/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn -B -ntp \
  -f backend/pom.xml verify
```

Result: BUILD SUCCESS. Module totals: 335 core tests, 7 qualified XML parser
tests, 1,047 server tests and 346 guarded-supervisor tests, with the three
Docker-gated witness cases skipped in the normal run. Finished 2026-09-12
17:26:12 Europe/London. The known recycled-response diagnostic noise appeared
during existing hosted boundary tests, but no test failed.

## Remaining limits

Production PostgreSQL execution still requires its own approved definition,
operator-selected archive digest, destination maintenance approval, exact client
installation identity, TLS configuration, and DBA/application-owner witness. This
local evidence also does not cover Oracle, non-C collations, partitioned tables,
row security, triggers, generated columns, unsupported types, multi-table
packages or interrupted terminal entry.
