# D07 SQL*Plus client investigation — 8 September 2026

This is an adverse client observation, not SQL writer qualification. No generated
artifact or application DML ran. The local database is disposable Oracle AI 26ai
Free 23.26.3.0.0 with its bundled SQL*Plus 23.26.3.0.0. No actual application model
or credentials were used. The image is pinned to
`gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.

The lead launched the actual client using `sqlplus -s -L -R 3 /nolog`, with
`ORA_PLUS_AUTOEXEC=DISABLE`, and connected through local OS authentication solely
inside the disposable database. This administrative connection was used only for
the following read-only client-control experiment, never as an observation
account or least-privilege qualification.

The input set AUTOCOMMIT OFF, EXITCOMMIT OFF, DEFINE OFF and both
`WHENEVER OSERROR EXIT FAILURE ROLLBACK` and
`WHENEVER SQLERROR EXIT FAILURE ROLLBACK`. SHOW output confirmed both commit
settings OFF. A SELECT emitted `ES_CLIENT_SETTINGS_BARRIER`; the next command was
an intentionally unknown SET option. SQL*Plus printed **SP2-0735**, continued to a
second SELECT emitting `ES_AFTER_SP2_BARRIER`, and returned **exit code 0** after
`EXIT SUCCESS ROLLBACK`.

This directly confirms that SQLERROR plus the process exit code cannot prove
client-command success. It agrees with
[Oracle WHENEVER SQLERROR](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqpug/WHENEVER-SQLERROR.html).
No DML was submitted, so the experiment does not prove rollback behavior.

A candidate external execution package needs a qualified supervisor which
withholds the sole COMMIT until the actual client has completed the guarded
transaction block and emitted a bounded, validated output barrier. Unexpected
output, SP2 errors, timeouts, EOF or a failed final guard must prevent COMMIT.
The application itself must remain read/export-only. Exact-client experiments
are still needed for transaction rollback, process/pipe failures, startup-file
suppression, truncation, lost commit acknowledgement and the full baseline,
membership, destination, row-count and post-state guards. An after-exit grep is
not sufficient evidence. The package design remains unimplemented/unqualified.

## Actual prompt controls

The lead additionally exercised the exact bundled clients with independently
invented disposable credentials held only in the probe's memory and child stdin.
PostgreSQL psql 18.6 with `-X -W -q -A -t`, explicit host/database/user and
ON_ERROR_STOP enabled ran without a controlling terminal. Its password prompt
arrived on stderr; after one password line, BEGIN READ ONLY, an independent
SELECT marker and ROLLBACK completed with exit zero. The password canary was
absent from the captured output. This was a prompt/read-only control, not DML
or guarded package qualification.

SQL*Plus required further investigation. A bare Easy Connect descriptor in the
tested omitted-password CONNECT command produced SP2-0306 and exit zero without
authentication. Quoting the entire user/descriptor in the tested form produced
ORA-01017; no SQL was sent after that refusal. Quoting the connect identifier
separately (`CONNECT <invented-user>@"//<loopback>:1521/FREEPDB1"`) with non-silent
`sqlplus -L -R 3 /nolog` produced an observable password prompt, authenticated and
completed the independent SELECT marker. The canary was absent from captured
setup/client output. Silent mode did not provide the prompt required by this
candidate protocol. Oracle's [CONNECT documentation](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqpug/CONNECT.html)
describes omitted-password prompting and CONNECT's implicit commit behavior.

The independent Oracle prompt user had CREATE SESSION only and account-level
READ ONLY. Its explicit SQL `ROLLBACK` returned ORA-28194 and the configured error
exit returned one. This does not establish SQL*Plus rollback qualification or
contradict the separately observed JDBC protocol cleanup; the interfaces differ.
The future external execution account must be independently qualified with the
required write authority and transaction controls. No account-mode change is
part of an artifact, and the application continues to use its read-only account.

The planned [package/supervisor contract](../contracts/guarded-package-v1.md)
requires one merged output stream, fully validated prompts/settings and a sole
commit withheld until an ordered readiness barrier. These exploratory controls
did not implement that supervisor or run DML and cannot replace its fault matrix.
