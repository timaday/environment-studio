# Ordered native-client protocol — candidate D07c

This makes the state machine in [guarded package v1](guarded-package-v1.md)
concrete. It is a candidate for exact-client qualification, not completed
supervisor authority. [Runtime admission](guarded-supervisor-runtime-v1.md)
precedes every step. No ordinary runtime is qualified yet.

## Grammar and states

Use one fresh random 128-bit lowercase hex nonce per process attempt. `archive`
and `program` below are independently verified 64-character lowercase SHA-256
strings. Only these tool-owned scalars may enter the fixed control queries.
Successful SQL frames are exactly these ASCII lines terminated by LF:

```text
ES_BOOTSTRAP|nonce
ES_READY|nonce|archive|program
ES_COMMITTED|nonce|archive|program
ES_ROLLED_BACK|nonce
```

Replace each placeholder with its exact admitted scalar. Never substring-match
a frame or accept it from an earlier phase. Reject duplicate, reordered, wrong
nonce/digest or unexpected nonempty lines. Blank lines are permitted within the
existing 1 MiB transcript budget. The only non-line frames are the exact password
and initial SQL*Plus prompts below. Each frame is at most 64 KiB. Native output
is transient and never printed or persisted, even on parser failure. No success
is inferred from an exit code or a later grep.

States are admitted, prompt, authenticated settings, transaction, ready,
commit-sent, committed, closing and terminal. A refusal before commit-sent can
never send COMMIT. Set commit-sent immediately before attempting the first write
of commit bytes; partial write, timeout, interruption or loss thereafter is
UNKNOWN unless the complete ordered acknowledgement and exit are established.
The native process must not already have exited when readiness is accepted.
The writer sends no command after readiness until the reader has consumed all
expected prior output and accepted the exact complete frame.

Candidate execution usernames are exact ASCII account identifiers:
PostgreSQL `[A-Za-z_][A-Za-z0-9_$]{0,62}` and Oracle
`[A-Z][A-Z0-9_$#]{0,127}`. Other forms refuse with AUTHENTICATION_UNSUPPORTED;
never trim, uppercase or normalize. This is an explicit initial candidate
credential matrix, not a production-support claim for all possible usernames.
Password UTF-8/Unicode bounds and CR/LF/NUL rejection remain unchanged. PostgreSQL
account-name visibility through fixed `-U` requires the runtime contract's
external privacy qualification.

## PostgreSQL

The fixed client switches include `-X -W -A -t -q`, `ON_ERROR_STOP=on`,
`ON_ERROR_ROLLBACK=off`, `ECHO=none`, `VERBOSITY=sqlstate` and pager disabled.
Endpoint/database/account and verified TLS options come only from admitted typed
inputs, with no shell or raw connection-string interpolation. The single forced
prompt is exactly `Password: `; send one bounded password line after it. No
second prompt, discovery connection or retry is allowed.

After the prompt, send the client-only command
`\echo ES_SETTINGS nonce :ON_ERROR_STOP :ON_ERROR_ROLLBACK` and require exactly
`ES_SETTINGS nonce on off` plus LF, substituting the fresh nonce. This proves
entry into the authenticated client command loop, not database transaction
success. Any authentication failure or unexpected output refuses.

Start the sole transaction with `BEGIN ISOLATION LEVEL READ COMMITTED READ WRITE;`.
Then set only these local settings: `search_path=pg_catalog`,
`client_encoding='UTF8'`, `standard_conforming_strings=on`, `row_security=off`,
`synchronous_commit=on`. The fixed bootstrap SELECT returns the bootstrap frame
only when all five settings match, server_encoding is UTF8,
transaction_isolation is `read committed`, and transaction_read_only is `off`.
Use pg_catalog functions and exact string equality. A failed predicate returns
NULL, which is never a success frame. No source-dependent expression is used.

Send the exact regenerated DO block once. Its sole success marker is the
transaction-local `environment_studio.program_digest`. The final precommit SELECT
returns the ready frame only when that setting equals program and the bootstrap
settings still match. No statement follows in the queued input. On acceptance,
send only `COMMIT;`, then one fixed SELECT of the committed frame. The native
client must process and acknowledge COMMIT before it can execute that SELECT;
any earlier error/unexpected output invalidates the sequence. After the exact
committed frame, send `\quit` and require EOF/exit zero within cleanup time.

Before commit-sent, a still-live usable client may receive only `ROLLBACK;`
followed by a fixed SELECT of the rollback frame and `\quit` after that frame.
Acknowledged rollback plus clean exit permits database cleanup Complete. A client
that has already exited on ON_ERROR_STOP supplies no rollback acknowledgement;
absence of a backend is not substituted for it.

## Oracle SQL*Plus

Use non-silent `-L -R 3 /nolog`, with `ORA_PLUS_AUTOEXEC=DISABLE` and the qualified
clean runtime environment. The observed startup is one leading LF, these lines,
one further blank line and the prompt:

```text
SQL*Plus: Release 23.26.3.0.0 - Production on TIMESTAMP
Version 23.26.3.0.0

Copyright (c) 1982, 2026, Oracle.  All rights reserved.
```

TIMESTAMP is bounded English `Www Mmm D HH:MM:SS YYYY`: named weekday/month,
one- or two-digit day, valid 24-hour time and four-digit year. It cannot contain
line breaks or other text. Qualify the exact fixed locale at runtime. Require one
`SQL> ` prompt. Send exactly one `CONNECT USER@"descriptor"` line, with the
admitted uppercase account and closed TCPS descriptor. Require `Enter password: `
with exactly one trailing space, then send one password line. Require LF and the
exact `Connected.` line and one `SQL> ` prompt; no expiry/new-password/reconnect
flow is accepted. These observed tokens still need full protocol timing trials.

First set `SQLPROMPT ""` so later prompt text cannot prefix a control frame.
Client-only bootstrap sets ECHO, FEEDBACK, HEADING, VERIFY, DEFINE, AUTOCOMMIT,
EXITCOMMIT, SQLNUMBER and SERVEROUTPUT OFF; PAGESIZE 0 and LINESIZE 32767.
Set TRIMOUT ON, TAB OFF and WRAP OFF for exact bounded line framing. Install
`WHENEVER SQLERROR EXIT FAILURE ROLLBACK` and
`WHENEVER OSERROR EXIT FAILURE ROLLBACK`. Declare only the three bindings
specified in Oracle transport v1. No database SQL has been sent at this point.

SHOW the fifteen settings below in this order. The observed pinned `/nolog`
client emits exactly these nonempty lines; require every line, in order:

```text
echo OFF
feedback OFF SQL_ID OFF
heading OFF
verify OFF
define OFF
autocommit OFF
exitcommit OFF
sqlprompt ""
sqlnumber OFF
pagesize 0
linesize 32767
serveroutput OFF
trimout ON
tab OFF
wrap : lines will be truncated
```

WHENEVER acceptance is not its error-behavior
proof; actual SQL/OS/client-error probes are mandatory. No SP2/ORA/PLS text is
allowed in a successful transcript.

The first SQL is exactly `SET TRANSACTION READ WRITE;`. The fixed bootstrap
SELECT returns the bootstrap frame only for AL32UTF8 database encoding and
binary NLS_COMP/NLS_SORT. Send known regenerated initialization/loader/final
blocks, each followed by the supervisor's slash/LF, once and in order. No program
tail is queued after the last block except the fixed readiness SELECT. It returns
the ready frame only when `:es_program_digest` equals program. A false predicate
returns NULL; no frame means no success. Complete owned-LOB cleanup already
precedes the marker in the trusted template.

After consuming the ready frame, send only `COMMIT WRITE IMMEDIATE WAIT;`, then
the fixed committed-frame SELECT from SYS.DUAL. Require its exact frame and no
prior error/unexpected output. Then send `EXIT SUCCESS ROLLBACK` and require
clean EOF/exit zero. This exit does not undo an acknowledged commit.

Before commit-sent, a still-live usable client receives the fixed cleanup block:
clear the marker; independently attempt to free both owned aggregate BLOBs;
check both are non-temporary/null; raise a fixed safe error if any cleanup failed.
Then send `ROLLBACK;` and the fixed rollback-frame SELECT. Only after accepting
that frame send `EXIT SUCCESS ROLLBACK`. Missing bindings, lost connection or
failed cleanup cannot be converted into success; an already exited client gives
no acknowledgement. Never include a new CONNECT.

## Process ownership, deadlines and outcome

The candidate Linux session launcher arguments are exactly
`--fork --wait -- CLIENT FIXED_CLIENT_ARGUMENTS`, corresponding to the qualified
setsid utility. It must create a new session without a controlling terminal;
do not use --ctty. Track the wrapper and actual native descendants by owned
process handles/start identities. Qualification must prove shared descriptor
ordering, prompt stdin, native exit propagation and cleanup; wrapper exit alone
does not prove the client's outcome.

Console input/restoration is a narrow bounded port in the first implementation.
An ordinary console adapter remains unavailable until its exact helper operations,
allocation bounds, pending-input discard and terminal restoration are qualified.
In particular, stty echo changes alone do not prove safe overflow/interruption
cleanup. Tests inject this port; there is no public bypass or unbounded fallback.

Authentication and each bootstrap phase use the existing 10-second deadlines.
The single 120-second transaction budget starts before BEGIN/SET TRANSACTION and
includes bootstrap SQL, loading, all guards and readiness; block output never
renews it. Cleanup/commit acknowledgement has a 10-second deadline. Writes must
also obey deadlines; a blocked pipe cannot leave an unowned worker. After a
possible commit, timeout is UNKNOWN even if later process cleanup succeeds.

Final machine output is exactly `{outcome, code, cleanup}` with tool-owned code,
outcome `REFUSED`, `NOT_APPLIED`, `APPLIED` or `UNKNOWN`, and cleanup `COMPLETE`
or `INCONCLUSIVE`. Exit 0 means APPLIED/COMPLETE; 2 means pre-client REFUSED;
3 means NOT_APPLIED before any commit attempt; 4 means UNKNOWN after a possible
commit; 5 means acknowledged APPLIED with other tool-resource cleanup inconclusive.
Code is a stable safe identifier, never native text. Missing final output is not
success. Terminal restoration/process cleanup uncertainty remains visible even
when the database outcome is known. No failure is retried automatically.
