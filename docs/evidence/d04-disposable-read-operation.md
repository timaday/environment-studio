# D04 disposable operation-policy qualification — 2026-09-09

Local development evidence on independently invented fixtures only. This is not
private application qualification or release approval. The earlier unit-policy
candidate and its 19-file manifest remain separately frozen in
[d04-operation-policy.md](d04-operation-policy.md).

## Tested boundary and provenance

The actual adapters are the reviewed D04 operation-policy implementation integrated
at `f4f2add0c6f3b46c00e03a8f0b11102e62163a50`: `jdbc-observation-v2`,
`postgresql-read-operation-v1`, `oracle-read-operation-v1`, fingerprint domain
`ES-OBSERVATION-2`. Ordinary write-capable accounts are eligible; inspection runs
closed reads inside the configured read-only transaction. Accounts are not changed
into read-only accounts to obtain eligibility.

Initial write-account and inventory cases used that implementation with compiler
mechanism 1. The subsequent owner, shadow, policy, snapshot and cancellation cases
used compiler mechanism 2 from the independently reviewed XML candidate integrated
at `40b67aebc116f1e0844c00c18c001dd2febbef84`. The operation-policy source did not
change. Each run compiles a fresh independent native-v2 mock declaration and checks
exact invented XML, independent physical identity and committed-state witnesses.

The RED child compiles eight exact observation sources from
`8125e57127b1583664d368b97b82d08ec103fe5d`, uses its recognized old operation-policy
names and the same newly provisioned owner/dataset as GREEN. PostgreSQL reaches
old `VISIBILITY_UNQUALIFIED` owner eligibility refusal; Oracle reaches old
`ACCOUNT_NOT_READ_ONLY`. Both return completed cleanup. The retired provisioning
harnesses were not executed; failing merely on the new policy name does not count
as RED.

## Owned lab

Two new disposable containers use independently invented fixture schemas, roles,
accounts and two XML documents derived from this repository's native-v2 mock
fixtures. No existing/shared database, data directory or global setting is changed.

| Engine | Pinned image | Limits |
| --- | --- | --- |
| PostgreSQL 18.6 | `postgres:18.6-bookworm@sha256:1c59e2c3c818eaa0f0628f695b36e7c9e362d6b219b36a54a32df645cbd7e1af` | 2 CPU, 1 GiB memory/swap, 128 MiB shared memory |
| Oracle 23.26.3 | `gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd` | 2 CPU, 3 GiB memory/swap, 1 GiB shared memory |

Host ports bind loopback only. Containers carry independent-mock/work-unit labels,
`no-new-privileges`, `--rm` and Docker log driver `none`. Bootstrap/password files
are private RAM files outside the checkout/build context. Operator passwords stay
in memory and are passed through JDBC properties or process stdin, never argv or
exported environment. Lab administration uses local OS authentication; no native
SQLPlus password-authentication qualification is claimed.

The public Oracle image entrypoint normally exports the password and sends it to
its reset helper as an argument. A narrowly patched external startup script keeps
the shell variable unexported and pipes setup SQL into OS-authenticated SQLPlus.
Original entrypoint SHA-256:
`e8191df41535034e34afa005768534160c09470fd50e01679bd756f8af0177d6`;
patched script:
`f3bee5e5104ea4acd2813411386ece215cc34ed7f7090f44fe4a035a16548b4e`.
Neither script nor private runtime state is included in the repository.

## Actual results

| Actual case | Result |
| --- | --- |
| Both engine owners: old eligibility RED, new operation policy GREEN | PASS; complete exact two-document inspection and unchanged committed source |
| PostgreSQL direct, column and active inherited-role UPDATE accounts | PASS; independent READ WRITE updates commit, identical DML under actual `SqlRead.begin` refuses with SQLSTATE 25006, inspection completes |
| Oracle owner, direct, column and active-role UPDATE accounts | PASS; independent READ WRITE updates commit, managed transaction refuses ORA-01456, inspection completes |
| Oracle active schema SELECT ANY TABLE and active system READ ANY TABLE | PASS; inspection completes. READ ANY TABLE control uses an unfiltered committed UPDATE because predicate UPDATE separately needs SELECT |
| Oracle four redundant direct/active-role READ/SELECT paths | PASS; actual catalogue count 4 and inspection completes |
| Oracle inactive-only read role | READ_ACCESS_DENIED before source barrier, complete cleanup |
| PostgreSQL RLS owner bypass and disabled RLS with a remaining policy | VISIBILITY_UNQUALIFIED before source barrier |
| Oracle account with actual source SELECT/UPDATE but missing required catalogue access | METADATA_UNAVAILABLE before source barrier |
| Both engines committed extra and missing rows | INVENTORY_MISMATCH, no adapter state change, complete cleanup |
| Both engines independent commit after metadata and before transfer | Original XML and inventory remain snapshot-consistent; fresh subsequent inspection refuses changed inventory |
| Oracle source-owner shadow DBMS_LOB, DUAL and NLS_DATABASE_PARAMETERS | Actual autonomous shadow control commits despite READ ONLY; qualified adapter uses SYS objects, completes and leaves shadow counter 0 |
| Actual Oracle SYSDBA JDBC session | IDENTITY_UNSUPPORTED before sources; complete cleanup and independent SYS session-count witness |
| Oracle VPD predicate and FGA handler | Refused before sources, effect counter 0; independent source controls actually invoke autonomous effects |
| Oracle full redaction of mock numeric keys | Refused before sources; independent query returns redacted zero values |
| Both engines cancellation with an established connection before source transfer | CANCELLED with COMPLETE cleanup; committed state unchanged and backend absent |
| PostgreSQL actual ACCESS SHARE wait behind independent ACCESS EXCLUSIVE lock | Lock wait independently observed, cancellation refused with COMPLETE cleanup; only blocker backend remains until independent rollback/close |

Cleanup claims require the adapter's own successful cleanup result as well as the
independent physical-session witness. Backend absence alone never upgrades an
INCONCLUSIVE result. No such upgrade was used. Snapshot controls deliberately
change the owned mock source in a separate committed transaction, then independently
restore the exact fixture state. They are outside the runtime adapter.

## Reproduction and limits

Explicit runner: `DisposableReadOperationQualification` under server test sources;
it is not an automatically executed JUnit test and cannot provision an arbitrary
endpoint. It validates the dedicated container names and labels first. Run via
`javac`/`java` using the pinned reactor classpath, nonsecret owned-lab state path and
checkout path. Modes `owners`, `shadow`, `oracle-policies`, `redaction`, `snapshot`
and `cancel` isolate the corresponding actual cases; no mode selects SQL supplied
by an application user. Default mode executes the combined matrix.

External safe result logs for this work unit: `write-account-matrix-corrected.log`,
`adverse-matrix-initial.log`, `compiler2-shadow-initial.log`,
`compiler2-oracle-policies-quoted.log`, `compiler2-redaction-initial.log`,
`compiler2-snapshot-initial.log`, `compiler2-cancellation-initial.log`.
Each listed run actually exited 0. Printed check totals count internal assertions
including repeated canary checks; they are not independent test-case counts.

The final fixed runner also executed the entire combined matrix against compiler
mechanism 2 in one process: `compiler2-combined-final.log`, exit 0. That run scans
all passwords generated by the combined run and the invented content canary across
owned Oracle diagnostic/audit directories and owned external result logs; none
occurred. Both Docker log drivers remained `none`. Bootstrap credentials remain
deliberately present only in private RAM bootstrap files; database datafiles contain
the intentional mock source and are not claimed content-free. This is a bounded
owned-artifact scan, not a claim about every host/process storage location.

`git diff --check` and `python3 scripts/check_repository_content.py` passed in the
author worktree. The repository checker is a known-pattern check; independent
fixture provenance is reviewed separately.

Setup failures are retained separately, not counted as policy RED: initial PG
host-authentication route refusal; an Oracle mixed-case fixture identifier mismatch;
an Oracle READ ANY TABLE predicate-write control refusal; and Oracle policy-package
normalization of an unquoted lowercase mock table name. Fixes changed only the
owned disposable setup/control statements. Oracle policy APIs now receive the
quoted mock table identifier.

This evidence is loopback TCP only. Current-policy verified TLS remains a separate
qualification gate. Native password authentication, private application storage,
other database versions, sustained maximum-size performance, Oracle stalled network
I/O and arbitrary administrative identities remain unqualified. The SYS test uses
a test-only JDBC factory to request SYSDBA explicitly; this is not a new production
connection option. Closed typed statement-sequence and setup-replay poisoning proof
remain in the separately reviewed unit-policy tests.

Oracle API references used for disposable control setup:
[DBMS_RLS](https://docs.oracle.com/en/database/oracle/oracle-database/26/arpls/DBMS_RLS.html)
and [DBMS_REDACT](https://docs.oracle.com/en/database/oracle/oracle-database/26/dbred/DBMS_REDACT.html).
These references inform the controls; the results above are actual database proof.
