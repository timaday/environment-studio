# Hosted plan service integration — 8 September 2026

The reviewed internal hosted-plan service now joins immutable publications,
session leases, read-only observation, profile composition and complete target
materialization. It enforces revision/replay ownership, bounded reservations,
cancellation and cleanup, explicit value choices, and private validation inputs.
There are no plan HTTP routes or credential forms in this integration; inspection
and export capabilities remain unavailable. See [author evidence](d06b1-hosted-plans.md)
for the observed behavior RED/GREEN runs, five restored guard mutants and limits.

The final 24-file candidate manifest SHA-256 is
`21eba182b2b24243dc5c41f586396f3571f1312efb62efe2653c8fbd64dace26`.
The lead verified every integrated file against that manifest. Independent review
closed two material corrections. Profile selection now initializes newly selected
original entities with complete Unresolved decisions, retaining prior explicit
choices; it cannot infer KeepObserved. Credential reading now checks the live
lease and cancellation both before reading and immediately before invoking the
reserved observation port. A reader returning after expiry/cancellation invokes
no port, closes the reservation and clears owned buffers. The final reviewer ran
10 focused lifecycle cases and verified all frozen file hashes with no remaining
material finding. Short authority locks contain no I/O.

## Integrated gates

Actual commands on the integrated candidate, based on `d2c919d`:

```text
docker build --target runtime --build-arg SOURCE_REVISION=d2c919d-local-d06b1 -t environment-studio:d06b1-review .
bash scripts/container_smoke.sh environment-studio:d06b1-review
```

The isolated Docker reactor passed **357 Java tests**: **124 core, 7 qualified
parser and 226 server**, with zero failures/errors/skips. Its frontend layer
retains the previously passed **7 component and 17 schema tests**, TypeScript/
format checks and production build; Docker reused that unchanged layer. Protected
non-root/read-only startup, static UI, health, demo denial/capabilities, private
workspace initialization and explicit schema-2 upgrade/refusal checks passed.
Logs are `/tmp/es-d06b1-integration-container.log` and
`/tmp/es-d06b1-integration-smoke.log`.

Local runtime manifest:
`sha256:0679e5c7263ddca90e999bc20b4aa01eba6fd170701b3027bd275cf3844b3d66`.
Image config:
`sha256:d99363d42192d7969b20fdd6d9b82fd34bbe1898f0bc1731d750a882416448b4`.
This image was not published. An earlier integrated 354-test build passed before
HTTP preparation exposed the credential-reader expiry gap; its result does not
qualify this correction. The final 357-test build includes the reproduced cases.

## Actual shared JDBC reservation checks

The lead ran the saved independent qualification runner
`/tmp/es-d06b1-permit-proof/SharedPermitQualification.java`, SHA-256
`d3a80e6a96aa14faa0499fba93f1a503c0af28ee4cde8e76106485f72dba2387`,
against the existing pinned disposable PostgreSQL 18.6/text and Oracle Free
23.26.3/CLOB mock databases. **58 assertions passed** across both engines; output
is `/tmp/es-d06b1-permit-proof/run.log`. The tested ObservationPort/JdbcObservation
files are byte-identical to the final integration. Later corrections affect the
plan service/composition, covered by the final reactor, rather than JDBC code.

Checks covered four shared physical reservations, fifth/direct-observe refusal,
zero connections for unused reservations, complete independent source comparison,
one-shot use, repeated close without inflated capacity, pre-cancellation and
cancellation at an actual JDBC source barrier after credentials were cleared.
Rollback/close was confirmed, tagged database sessions were absent and all four
slots were reusable. New independently invented reader accounts received only
the existing qualified read policy; Oracle's automatic PUBLIC INHERIT grant on
that new account was immediately revoked. Existing tables, data and vendor grants
were unchanged. Accounts were disabled/locked in cleanup. The initial harness
compile needed explicit copied module class roots; that setup failure is not a
production TDD RED. No plaintext lab result establishes TLS qualification.

## Scope and provenance

New tests contain independently invented owners, clocks, credentials, commands
and barriers. Existing registered mock fixture families supply the public model
and independent expected XML. No private model, new dependency or runtime secret
was added. Independent integration/provenance review was clear. Actual
`check_repository.py`, staged `check_repository_content.py`, all 10 Python
regressions and `git diff --cached --check` passed before the integration commit.

HTTP streaming/deadlines, end-to-end OIDC ownership for plan routes, credential
canary tracing through deployed transport, browser workflows, full hosted heap
qualification, verified TLS, SQL supervisor and actual application/deployment
qualification remain outstanding. Logical byte-budget tests do not establish
maximum JVM memory use. Integration included two review corrections and two
Docker builds; active/rework time was not separately measured and no parallel
speed-up is claimed.

A subsequent focused policy review identified an outstanding Oracle read-adapter
gap: enabled FGA policies are not yet explicitly rejected before source SELECT.
Oracle permits FGA handlers on SELECT; account READ ONLY alone does not prove
absence of handler effects. This needs a separate conservative metadata guard
and independent mock qualification before real hosted plan mode. It does not
change the focused reservation/lifecycle results above.
