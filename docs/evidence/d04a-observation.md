# D04a — bounded read-only observation evidence

Implementation candidate for independent review. The lead owns integration and
release qualification. This is local development evidence, not external account/
transport qualification or publication authority.

## Scope and provenance

Framework-free observation port accepts a server-compiled native-v2 ready definition
and an exact binding ID. The server adapter owns destination/policy configuration,
one physical connection per operation, strict membership/size checks and cleanup.
There are no new HTTP routes, pools, managed-database writes or inspection enablement.

The XML fixtures are copies of the independently invented native-v2 glyph/palette
family, with a local provenance manifest. All additional table names, users, rows,
policy functions and credential/source canaries were invented independently for
these tests. Vendor catalog object names are public tool metadata, not application
configuration. No private input was consulted. Generated passwords exist only in
Java memory and process stdin; setup output is captured and checked before release.

Pinned qualification engines are PostgreSQL 18.6 with JDBC 42.7.13 and Oracle Free
23.26.3.0.0 with JDBC 23.26.3.0.0. Only the lead-owned disposable containers were
used. Driver dependencies are exact versions; no dependency range or datasource
pool was introduced.

## Observed RED and corrections

Commands below run from the isolated D04 worktree. Maven prefix is
`/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml`.

- `-Dtest=ObservationInventoryTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  two core tests, one failure with the initial always-incomplete stub.
- `-Dtest=ObservationInventoryTest,ObservationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  core green; server boundary test expected INVALID_SELECTION but the stub returned
  DATABASE_FAILURE. Implemented typed early refusal and credential disposal.
- `-Dtest=ObservationInventoryTest,ObservationLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  observed mutable credentials retained while original connection close stalled.
  Credentials now clear immediately after connect returns, before metadata/cleanup.
- `-Dtest=ObservationInventoryTest,SqlReadTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  failed statement configuration left the allocated statement unclosed (expected
  one close, observed zero). Configuration failure now closes it before propagation;
  the owning connection still receives independent rollback/close.
- The lifecycle selection safe-print test observed compiled definition/binding
  details in the default record string. Selection now prints only REDACTED.
- An initial targeted server-only test command stopped at the core module's
  no-tests gate; it was rerun including the core inventory tests. This was an
  invocation correction, not a behavioral RED.

The early targeted RED commands above used `surefire.failIfNoSpecifiedTests=false`
so modules without a selected test did not fail merely for that selection. Each
reported behavioral failure actually ran, but those commands were not complete
reactor gates. No whole-gate PASS is inferred from them. The review correction
below uses unfiltered full-reactor `test`/`verify`, with no gate-disabling flags.

Initial compilation corrections and failed disposable setup experiments are not
claimed as successful tests. Final GREEN totals and commands appear below.

## Actual PostgreSQL evidence

`python3 scripts/db_observation_qualification.py --engine postgresql` passed
80 assertions on the designated disposable PostgreSQL 18.6 instance. It checked
baseline and no-op fingerprint, exact CRLF/astral XML preservation, missing/extra
membership, enabled RLS, direct column writes, a reachable non-inherited writer
role, actual metadata and table SELECT denial, independent wrong destination,
NULL/empty input, exact/over document characters, concurrent snapshot changes,
text keys `01` versus `1`, 128 documents exactly 16 MiB and one byte over.
The measured exact-total observation took 455 ms in that run; this is not a
production latency claim.

Ordinary transactions independently denied INSERT/UPDATE/DELETE with SQLSTATE
42501 and rolled back. A held administrator table lock exposed the reader's real
backend wait: cancellation returned CANCELLED with COMPLETE cleanup and the backend
vanished. Administrator termination caused INCONCLUSIVE cleanup with the original
handle retained; backend disappearance alone did not restore authority.

The pristine `pg_settings` PUBLIC UPDATE privilege initially caused the strict
policy to refuse. The approved exception verifies vendor ownership, exact ACL,
view/rule definitions and built-in function identities against PostgreSQL REL_18_6.
It excludes no other writes. The scoped disposable setup revokes PUBLIC EXECUTE
on `pg_control_system` and grants it directly to the reader, so revoking that reader
grant is a real metadata-denial test. Runtime code never grants anything.
See [upstream vendor view/rules](https://raw.githubusercontent.com/postgres/postgres/REL_18_6/src/backend/catalog/system_views.sql)
and [pg_settings documentation](https://www.postgresql.org/docs/18/view-pg-settings.html).

## Oracle policy investigation

The first isolated Oracle instance had 216 non-SYS PUBLIC EXECUTE grants. The
initial SYS-only policy refused it. A lead-authorized disposable revocation
experiment removed 62 before recursive vendor DDL failed (ORA-06545/00600/06550/
06512); 154 remained. The lead stopped that experiment. No further changes were
made to that instance, and it is not qualified.

A fresh instance from the same pinned image preserved all vendor grants. Independent
administrator capture found 1,829 pristine PUBLIC EXECUTE entries. Exact target
resolution identified 112 catalog TYPE=UNKNOWN entries uniquely as vendor DOMAIN
objects. The execute-only policy still correctly refused additional PUBLIC writes,
including persistent XDB targets; they were not mislabeled as harmless temporary
objects and no vendor grants were revoked.

Oracle's supported account-level READ ONLY mechanism was then qualified with a
separate controlled harness, `OracleReadOnlyAccountQualification`. Known-working
READ WRITE controls included all three managed DML forms, vendor persistent-target
DELETE WHERE 1=0 authorization controls, an invented normal definer writer and an
invented autonomous definer writer. The normal procedure changed a canary to 1
before rollback; the autonomous procedure committed 2, witnessed independently,
then the administrator reset it to 0.

After administrator ALTER USER READ ONLY and a fresh connection, DBA_USERS reported
YES/NO for READ_ONLY/COMMON. All managed/vendor/normal-definer/autonomous writes
refused ORA-28194. Self ALTER USER READ WRITE refused 28194. Disabling the session
READ_ONLY flag succeeded, but subsequent writes still refused 28194. The independent
canary remained 0, managed row count remained 2 and the backend disappeared. The
first controlled run passed 23 assertions; the final controlled run passed 26,
including successful session-flag disable and an independent full managed-source
length/SHA-256 witness before and after the challenge. Vendor DELETE WHERE 1=0 is authorization
refusal evidence, not a claim of vendor row mutation/restoration.

The exact server lacks ALL_USERS.READ_ONLY (actual ORA-00904). The adapter requires
explicit access to DBA_USERS instead. VIRTUAL_COLUMN requires DBA_TAB_COLS; the
initial DBA_TAB_COLUMNS query also refused with actual ORA-00904 and was corrected.
These are explicit supported metadata selections, not fallback success paths.
See [Oracle account restriction documentation](https://docs.oracle.com/en/database/oracle/oracle-database/26/dbseg/configuring-privilege-and-role-authorization.html)
and [DBA_TAB_COLS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/DBA_TAB_COLS.html).

The final approved policy combines local account READ ONLY with an independently
captured complete pristine PUBLIC object-grant baseline. After revoking only the
automatic PUBLIC INHERIT PRIVILEGES grants on this harness's own new mock users,
the baseline has 3,982 entries and framed SHA-256
`ef5326b6f4a158d1962f5397e10a7b5b38bbf20a7ba005fea394c02710db9a33`.
The exact intrinsic PUBLIC pseudo-role entry uses the explicit PUBLIC_SPECIAL_ROLE
provenance marker from the frozen contract; no reader grant on SYS.USER$ exists.

## Limits and remaining qualification

Actual production accounts, TLS certificates/trust stores, network fault matrices,
external provisioning assurance and operational restart procedures remain external.
The disposable plaintext loopback composition is not a hosted configuration.
Account mode and grant digests do not prove arbitrary vendor routine purity or
constrain arbitrary network/file effects; the adapter exposes no arbitrary routine
invocation. Observation does not authorize graph validity, plans, export or publication.


## Final checks and exact matrix

- `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml verify`:
  **194 tests passed** (59 core, 135 server), zero failures/errors/skips.
  This includes 16 observation tests: core 2, server 14.
- `java -Dloader.main=studio.environment.server.observation.OracleReadOnlyAccountQualification -Dloader.path=backend/server/target/test-classes -cp backend/server/target/environment-studio.jar org.springframework.boot.loader.launch.PropertiesLauncher`:
  **26 controlled account assertions passed**.
- `python3 scripts/db_observation_qualification.py --engine both`:
  both engines were explicitly executed; the initial frozen run passed **4,167 assertions**.
  The review-correction run and additional cases are recorded below.
  Of these, 3,982 are individual baseline provenance checks, not separate behavioral
  scenarios. The matrix below identifies the actual behavior groups.
- `python3 scripts/check_repository.py`, `python3 scripts/check_repository_content.py`:
  PASS; the content scanner only checks known patterns and does not prove provenance.
- `python3 -m unittest discover -s scripts -p 'test_*.py'`: 10 passed.

| Actual disposable behavior | PostgreSQL | Oracle |
| --- | --- | --- |
| Whole inventory, missing/extra keys, exact text keys | PASS | PASS |
| Baseline/no-op digest, exact CRLF and astral source | PASS | PASS |
| Column writes, reachable inactive roles, metadata/SELECT denial | PASS | PASS |
| RLS / VPD visibility refusal | PASS | PASS |
| Independent destination mismatch | PASS | PASS |
| NULL/empty source; exact/over document character limit | PASS | PASS |
| 128 documents exactly 16 MiB strict UTF-8; over limit | PASS | PASS |
| Additional multibyte total requiring incremental stream refusal | Unit stream proof | Actual CLOB PASS |
| Concurrent data change retains original snapshot | PASS | PASS |
| Concurrent required-column rename refuses | Access-share lock prevents conflicting DDL | PASS |
| Ordinary managed DML denial | SQLSTATE 42501 | Account-mode controls: ORA-28194 |
| Account-mode downgrade and PUBLIC/direct routine drift | Not applicable | PASS |
| Actual cancellation and backend disappearance | Blocked closed SQL: COMPLETE | Production-query barrier: COMPLETE |
| Active statement sleep cancellation | Not applicable | INCONCLUSIVE retained; rollback 17008 |
| Targeted original backend termination | INCONCLUSIVE retained | INCONCLUSIVE retained |
| Memory-only password/source canary scan of captured output/logs | PASS | PASS |

The Oracle active-statement control substitutes one fixed metadata query with a
vendor DBMS_SESSION.SLEEP using a test-only connection proxy. The administrator
observes the original reader's actual PL/SQL lock-timer wait. Cancellation follows
the unchanged adapter active-statement path. The original driver's rollback then
fails with code 17008 (closed connection); cleanup remains INCONCLUSIVE even after
independent backend disappearance, and retry/status do not promote it. The initial
COMPLETE test expectation was unsupported and was corrected after investigating
that exact driver state; this is not claimed as a production cleanup bug fixed by
changing an assertion. The production-query barrier cancellation separately proves
CANCELLED/COMPLETE and backend disappearance. No arbitrary SQL hook is exposed by
the production adapter's public constructor.

Four physical operations/quarantines share a global admission bound. Unit tests
hold all four original closes, obtain four INCONCLUSIVE handles and demonstrate
that a fifth attempt refuses before allocating a connection. Original successful
closures release capacity; status/retry never reconnect or restore observation
results. A failed final rollback/close remains quarantined until process restart.
Driver/JVM-internal immutable credential copies cannot be forcibly erased; owned
mutable arrays clear before metadata and before potentially stalled cleanup.

Oracle disposable fixture DDL required a setup-only quiescence barrier: actual
same-second nullability changes caused ORA-01466, which correctly produced a safe
DATABASE_FAILURE. Setup now waits before beginning a new operation; runtime does
not retry. A separate concurrent-column-rename case still proves DDL refusal.
[Oracle documents ORA-01466 as an old snapshot versus changed definition](https://docs.oracle.com/en/error-help/db/ora-01466/).
The invented locked schema's initial 64 MiB storage quota was insufficient after
CLOB revision/extent overhead; its bounded setup quota is 256 MiB. This does not
change the 16 MiB observation limit or give the reader storage ownership. One
initial over-limit fixture appended non-whitespace after the XML document and
correctly failed INVALID_SOURCE; the corrected fixture appends legal whitespace
so it actually reaches the intended byte-limit challenge.

All transient diagnostics used for catalog/DDL investigation were removed from
production code. Source/credential-bearing records print REDACTED; driver DEBUG
logging refuses admission before the credentials reach a driver. The disposable
harness captures setup output and container logs and checks actual in-memory
password canaries plus an independent source marker. SQL exceptions escaping the
harness are reduced to numeric error codes, with no cause/payload dump.

G00 provenance and repository checks, G01 inward core dependency boundary, relevant
Java behavior gates, G05 disposable engine qualification and G07 local canary
checks are supported by this evidence. TLS/real-account/real-input and deployment
qualification remain outside this candidate, as do HTTP ownership/session wiring
and all plan/export authority.

## Time accounting

The first recorded dependency build completed at 16:22:59 UTC on 2026-09-08; the
first behavioral RED completed at 16:24:30 UTC. The complete both-engine run first
passed at 17:54:30 UTC, a 91 minute 31 second span from that dependency-build record.
These timestamps give a recorded lower bound, not fabricated precise active-time
accounting. Oracle policy/provisioning investigation ran from the failed revocation
experiment at 16:42:05 through the account-policy baseline investigation around
17:31:52 (about 50 minutes); useful PostgreSQL and generic lifecycle work continued
in parallel. There was no deliberate idle wait and no measured speed-up claim.
Integration/review rework belongs to the lead's subsequent fixed-candidate review;
these overlapping intervals must not be added as separate elapsed time.


## Independent review corrections

The independent reviewer found two concrete gaps in the first frozen candidate.
Both were reproduced before changing production code.

1. PostgreSQL's table-only scan omitted sequence USAGE/UPDATE and ownership,
   MAINTAIN, large-object UPDATE/ownership, and grant-option delegation. The actual
   disposable harness challenged 30 direct/reachable-role/PUBLIC cases against the
   frozen adapter; **all 30 incorrectly returned Complete**. Setup restored every
   challenged grant/owner after each case. These were metadata-policy failures,
   not evidence that closed production SQL performed writes or that the read-only
   transaction allowed managed DML.
2. A parent JUL logger at INFO did not prevent a registered child logger from
   using FINEST. Full-reactor `mvn ... test` failed with one connection-factory
   allocation and an authentication canary captured by the child's in-memory
   handler. The test restores original logger levels, handlers and propagation.
   No real credential was used or emitted to console.

The corrected PostgreSQL adapter explicitly rejects sequence writes/ownership,
MAINTAIN and large-object writes/ownership; it scans ACL grant options across
relation, column, database, namespace, routine, type, language, tablespace,
foreign-wrapper/server, large-object, parameter and default-privilege catalogs.
Shared ownership dependencies plus explicit catalog ownership checks prevent
other owned object categories from bypassing the table scan. Tablespace CREATE
and foreign-wrapper/server USAGE also refuse because they confer creation or
management paths. The sole approved pg_settings exception remains unchanged.
See [PostgreSQL privilege definitions](https://www.postgresql.org/docs/18/ddl-priv.html)
and [shared ownership dependencies](https://www.postgresql.org/docs/18/catalog-pg-shdepend.html).

The expanded actual matrix includes 40 challenges: the original 30 plus
foreign-wrapper/server USAGE through direct, reachable-role and PUBLIC grants,
and domain ownership/delegation through direct and reachable roles. PUBLIC
WITH GRANT OPTION is not a legal PostgreSQL grant; delegation challenges use
direct and reachable roles instead. All challenges now refuse and the ordinary
read-only baseline succeeds again. Objects and schemas are independently invented;
foreign wrappers have no handler or endpoint and initiate no external access.

The logger guard checks both driver namespace roots and every registered
namespace descendant, resolving effective levels through its parent chain.
Any level below INFO refuses before connection-factory allocation and credential
handoff. PostgreSQL and Oracle child-logger canary cases pass. Global user
configuration is unchanged; changing trusted logging configuration concurrently
with an operation is not qualified by these local tests.

Actual review-round commands:

- `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml test`:
  observed RED, 59 core tests passed and 135 server tests ran with one logger
  behavior failure. No test filter, skip or missing-test suppression flag.
- `python3 scripts/db_observation_qualification.py --engine postgresql-privileges`:
  observed actual RED with all 30 listed policy gaps; corrected 40-case run PASS.
- `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml verify`:
  corrected complete reactor PASS, 194 tests, zero failures/errors/skips.

Logs are external local proof: `/tmp/es-d04-review-red-full.log`,
`/tmp/es-d04-review-pg-red.log`, `/tmp/es-d04-review-pg-expanded.log`,
`/tmp/es-d04-review-final-verify.log` and `/tmp/es-d04-review-both-final.log`.
No private input or credentials are copied into this evidence document.

Final review-correction `--engine both` passed **4,292 assertions**, including the
3,982 Oracle baseline provenance checks and all 40 new PostgreSQL privilege
challenges. Oracle exact-16-MiB observation measured 1262 ms; PostgreSQL measured
299 ms in this run. Repository/content checks and all 10 script tests passed
again. Review RED-to-final-matrix log timestamps span 8 minutes 3 seconds;
this is correction elapsed time, not a claimed parallel speed-up.

## Lazy driver logger configuration correction

Independent re-review reproduced a second configuration path: JUL can retain a
child `.level=FINEST` property while `LogManager.getLogger(child)` is still null.
Enumerating registered loggers alone therefore did not close the logging finding.
The isolated-JVM test first establishes that absence, then observes the unchanged
adapter. Actual RED command:

`/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml test`

`/tmp/es-d04-lazy-red.log` records 59 core passes and 136 server tests with one
failure: authentication factory allocated once and the independently invented
password canary was captured in memory. The failure reports booleans/counts only;
the canary value is never emitted. No filtering or gate-disabling flags were used.

`DriverLoggingPolicy` now creates and strongly retains the qualified driver
logger names before checking effective verbosity. JUL applies lazy properties
when those names are created; retained references prevent garbage collection
between qualification and driver initialization. Existing registered descendants
remain checked. Levels, handlers and user/global configuration are never changed.
An Oracle custom `oracle.jdbc.diagnostic.loggerName` route is unqualified and
refused before authentication. This guards the pinned composition; privileged
in-process code changing logging after qualification is not an isolation boundary.

The qualified name set was derived by scanning pinned JAR class constant pools
for JUL references, then inspecting all matching classes with `javap -c -p`.
PostgreSQL has 39 matching class entries (including two multi-release entries),
with logger construction through fixed strings or class names. For example,
`TempFileHolder` uses the `StreamWrapper` logger, and `QueryExecutorCloseAction`
uses `QueryExecutorBase`; the policy follows the actual constructor arguments.
Oracle has nine direct JUL-reference classes. Its fixed routes include `oracle`,
`oracle.jdbc`, `oracle.jdbc.driver`, `InstalledProviders`, `ReplayLoggerFactory`
and `oracle.jdbc.internal.replay`. The shared `Diagnostic` obtains its name from
`CommonDiagnosable` (default `oracle.jdbc` or the explicit system-property override).
No classes in this pinned JAR's `oracle.net`, `oracle.sql` or `oracle.security`
families reference JUL directly. This is evidence for the qualified non-debug
`ojdbc17` artifact, not other Oracle debug/security/provider artifacts.

Pinned artifact SHA-256:

- pgjdbc 42.7.13: `6e0e4cc2d8cae902084f8a2b18728b073a6fd9d1f87c9d8bff8f298c18185b93`
- ojdbc17 23.26.3.0.0: `17ad1c2d5242432cc4db9e2a7f67762bc8a4086cbcb3cd81f6ffaa2a510c41d8`

Final GREEN command:

`/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml verify`

`/tmp/es-d04-lazy-final.log`: **195 tests passed** (59 core, 136 server), no
failures/errors/skips. Six independent JVM cases cover the lazy PostgreSQL
authentication logger, Oracle driver/replay/provider routes and custom diagnostic
route. The PostgreSQL case initializes the actual pinned `ConnectionFactoryImpl`
after refusal and verifies its FINEST configuration remains effective. Synthetic
authentication canaries stay in memory, with zero allocation/capture on GREEN.
Process-local LogManager changes cannot affect the test runner or user environment.

No database policy, SQL, transfer or lifecycle code changed in this correction.
The immediately preceding both-engine 4,292-assertion qualification belongs to
identical database behavior and was not needlessly rerun for this logger-only fix.

Lazy-review RED-completion to final-GREEN log timestamps span 113 seconds.
Bytecode investigation and evidence preparation were additional active work; no
external blocked time or parallel speed-up is claimed.
