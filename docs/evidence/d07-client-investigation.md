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
