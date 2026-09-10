# Complete database observation — D04

The application reads and exports; it never executes generated SQL against a
managed environment. This contract adds engine adapters to
[planning](planning.md), [security](security-and-state.md) and
[native v2 bindings](native-definition-v2.md). Qualification uses independently
invented disposable databases only. Actual account provisioning, destination
approval and application qualification stay in the authorized external workflow.

## Operation and authority

One operation selects an immutable compiled definition, one explicit binding and
one server-configured destination. Requests select an allowlisted destination ID;
they cannot supply JDBC URLs, arbitrary driver properties, SQL, trust-all TLS or
remote resolver options. The destination includes engine, exact host/port/database
or service, verified TLS settings, independent expected database identity and the
qualified adapter/operation-policy version. Runtime configuration stays outside the
checkout and image. Plaintext loopback endpoints are allowed only in an explicit
disposable-test composition, never through a hosted request or production default.

The independent destination witness is supplied by the authorized deployment
configuration before inspection. Compare it to observed physical database and
container identity; do not derive expected identity from the same observation,
matching XML, uploaded definition or caller-supplied PASS assertion. A matching
database name alone is insufficient. Cloning can duplicate database metadata;
the approved network/TLS identity and external provisioning evidence are also
required. Missing evidence blocks a complete qualified result.

Credentials belong to the individual operation, with one physical connection and
no pool, retry/reconnect loop or durable job. Do not put credentials into a URL,
environment variable, error, log or `toString`. Clear owned mutable buffers and
references at completion; acknowledge that drivers/JVMs may retain immutable
copies. The adapter receives credentials through a narrow transient boundary and
returns only a bounded result and cleanup outcome. No HTTP input is enabled until
session ownership, cancellation and expiry are integrated and tested.

## Consistent full-table read

Pin PostgreSQL 18.6/text and Oracle Free 23.26.3/CLOB as the initial disposable
test matrix. Proposed JDBC pins are pgJDBC 42.7.13 and Oracle Thin ojdbc17
23.26.3.0.0 on Java 21; record actual resolved versions and observed compatibility.
No fallback driver, storage conversion, connection pool or UCP is permitted.

Begin PostgreSQL `REPEATABLE READ READ ONLY`, set a safe `search_path` and
`row_security=off`, then verify effective isolation/read-only settings. Begin
Oracle `SET TRANSACTION READ ONLY` as the first transaction statement on a
non-administrative account. Obtain identity, metadata and all source rows within
that one transaction. Consume every CLOB before rollback/close. A statement or
snapshot error invalidates the whole observation; never combine independently
retried documents into one result.

The binding covers the whole exact quoted table, including unchanged dependencies.
Initially require an ordinary local base table with no inheritance/partitioning,
views, synonyms, foreign/external table, enabled DML triggers or unqualified
storage behavior. Verify exact built-in key type (`text` or int64 as declared)
and XML storage (`text` or `CLOB`), nullability and a qualified unique key.
PostgreSQL keys use `text` or `bigint`; Oracle keys use `VARCHAR2` with sufficient
declared character capacity or `NUMBER(19,0)` respectively. Enforce the native
text/code-point and signed-int64 value bounds while reading; numeric column
precision alone does not prove a value fits int64. Domains, bounded character
XML storage, native XML/OID/NCLOB and silent conversion are refused.
SQL uses only tool-owned statements, validated quoted identifiers and bound data.
Never accept uploaded predicates or row limits as completeness evidence.

Read every key and full XML value; compare the observed key set with the declared
document keys. Missing, additional, duplicate or null keys block completeness.
Keep SQL NULL, empty text and empty CLOB distinguishable even though none is a
valid XML document. Exact characters, encoding, byte/character length, typed keys,
source digests, declared document IDs, engine/storage versions and destination
identity form immutable transient evidence. Sort semantic output by declared
document ID; database collation does not define Java identity equality.

## Read-only operation policy — revision 2

Inspection and fresh readback accept ordinary write-capable accounts. Ownership,
direct or column-level write grants, active role grants, grant options and unrelated
write-capable routines are not account disqualifiers. Studio never changes external
users, roles, grants, database settings or schema objects to make an account eligible.
The separately authorized persistence of Studio workspace metadata is unaffected.

The guarantee is qualified, closed read operations under an enforced read-only
transaction, complete metadata and source visibility, followed by rollback and
confirmed closure. It is not account purity or a claim that arbitrary SQL, DDL,
autonomous routines, or operations outside Studio could never write. A successful
SELECT or JDBC readOnly hint alone proves none of these conditions. No write probe
or provisioning statement is part of the runtime inspection/readback adapter.

Require `operationPolicyVersion` exactly `postgresql-read-operation-v1` or
`oracle-read-operation-v1` for the matching engine, adapter `jdbc-observation-v2`,
and fingerprint domain `ES-OBSERVATION-2`. Old `accountPolicyVersion` configuration,
old policy identifiers and old observation evidence are not accepted or automatically
translated. Reconfigure the external destination explicitly and obtain a fresh
observation. Earlier account-policy and TLS qualification remains historical; it
does not certify the changed operation policy or its complete statement sequence.

### Closed execution boundary

Before opening a physical connection, validate the entire selected binding and all
identifiers against the qualified identifier grammar. The JDBC boundary accepts a
closed typed vocabulary of tool-owned control/metadata operations and narrowly
constructed source/length/lock operations, not caller-provided SQL strings or an
expression tree. Dynamic identifiers are validated and quoted; metadata values are
bound parameters. A starts-with-SELECT check is insufficient. No uploaded predicates,
expressions, routines, `SELECT FOR UPDATE`, DDL/DML, `SET ROLE`, mode reset, transaction
restart, connection-property override or reconnect path is admitted. Cleanup may
rollback and close only. Repeated setup after entering observation refuses.

PostgreSQL establishes server-side `REPEATABLE READ READ ONLY` before observation,
sets `search_path=pg_catalog` and `row_security=off`, and verifies all four effective
settings with snapshot-free SHOW commands before the qualified table lock or
metadata/source access; SELECT current_setting is not an equivalent ordering.
Recheck before source transfer. Acquire the existing `ACCESS SHARE` lock before
the first snapshot SELECT. Read identity, metadata and full inventory in that
snapshot while holding the lock; release it only at rollback. Require effective
table SELECT and schema USAGE; ownership and
active inherited read grants satisfy these checks. Column-level write grants remain
acceptable; column-only SELECT is not advertised as sufficient for the explicit
lock. Refuse system-catalog/information-schema source bindings, nonordinary storage,
RLS flags/policies, unsupported triggers and incomplete metadata regardless of any
owner/BYPASSRLS privileges. No global role/write/delegation scan or pg_settings ACL
baseline is needed: those operations are absent from the closed execution surface.

Oracle executes `SET TRANSACTION READ ONLY` as the first transaction statement.
Refuse SYS explicitly, administrative authentication (`ISDBA`), proxy sessions and
unsupported common/vendor identities before source access; the qualified identity
is an ordinary local PDB user, with no account READ_ONLY requirement. SYS does not
provide the required read consistency even after SET TRANSACTION READ ONLY. Qualify
transaction setup and unchanged snapshot behavior on the actual pinned server;
never invent an unavailable effective-mode metadata field or infer mode from account
READ_ONLY. Any setup failure invalidates the operation.

Oracle effective read access is ownership, or at least one legitimate SELECT/READ
path through the authenticated user, PUBLIC or enabled SESSION_ROLES, or applicable
active system/schema SELECT/READ ANY TABLE privilege. Duplicate direct/role READ and
SELECT paths are valid. Inactive reachable roles do not supply effective access.
Require complete relevant metadata; missing access never becomes an empty success.
Missing effective read access returns READ_ACCESS_DENIED; unsupported authenticated
identity returns IDENTITY_UNSUPPORTED. The retired ACCOUNT_NOT_READ_ONLY code is
not emitted by this operation policy. Unknown operation policy is
DESTINATION_UNQUALIFIED.
Refuse vendor-maintained source owners/system objects instead of applying ordinary
ANY TABLE semantics to dictionary objects. Qualify catalog/package references with
SYS, including SYS.DBMS_LOB and SYS.DUAL, so an ordinary owner cannot shadow them.
The closed catalog vocabulary uses trusted vendor routines only; installed vendor
software integrity is an external trust assumption, not a grant-digest claim.

### Visibility and read effects

Retain the supported local base table/storage/key/encoding checks and complete source
inventory. PostgreSQL RLS/policy refusal applies even when the current account owns
the table or can bypass RLS. row_security=off adds refusal, not authorization.

Oracle rejects applicable enabled VPD, label-security/redaction, fine-grained auditing
(FGA), or other unqualified policy mechanisms before any bound source row or length
is read. Require complete SYS.DBA_AUDIT_POLICIES metadata and reject any enabled
policy on the bound owner/table, including policies without a handler or with
non-SELECT statement declarations. Never evaluate conditions or infer harmlessness.
Unavailable catalog access is METADATA_UNAVAILABLE; a present enabled policy is
VISIBILITY_UNQUALIFIED. Restricted catalog absence is not proof of absence. Required
metadata permissions may already exist or be supplied in the separate authorized
external workflow; Studio performs no grant changes. The old complete vendor PUBLIC
ACL digest and account-level READ_ONLY provisioning checks are retired: they do not
prove the safety of the actual qualified read path. FGA/other read-effect barriers
remain because an enabled handler may have effects outside an ordinary transaction.

### Qualification of write-capable accounts

Use independently invented disposable databases only. Observe meaningful RED/GREEN
for table owners and accounts with direct, column-level and active role-inherited
write grants; Oracle redundant direct/role SELECT/READ paths; and denied/inactive-only
read paths. A separate READ WRITE control must actually change an invented managed
row. Under the inspection transaction configuration ordinary managed-table DML must
be rejected, and independent committed-state checks must show inspection preserved
the complete original rows. Write controls are qualification harness operations only.
Do not expect Oracle read-only transactions to block autonomous routines or every DDL
commit effect; show those statements cannot reach the runtime JDBC boundary instead.

Challenge owner-shadowed routines/catalog names, supplied SQL and malformed identifiers,
mode setup failure/mismatch/reset, extra physical connection attempts, metadata denial,
RLS/VPD/redaction/FGA, stale/missing/extra inventory, cancellation, timeout and cleanup
failure. Record exact engines/drivers, expected statements and connection count.
Credential/content canaries must be absent from logs, storage, URLs and diagnostic
artifacts. Preserve destination/TLS controls; do not promote inconclusive cleanup.

## Bounds, cleanup and fingerprint

Use 128 documents, 16 MiB strict UTF-8 total source and existing per-document XML
limits. Enforce bounds while reading streams, before large allocation; a size or
row limit refuses the whole operation. Retain no truncated authoritative value.
Bound LOB prefetch, row fetch size, connect/read/query/cancel timeouts and the
whole operation with a monotonic deadline. Initial operation deadline is 30
seconds, connect 5 seconds and cleanup allowance 5 seconds; a timeout never
silently extends or reconnects. Actual supported capacity needs measured evidence.
Driver-internal allocation also matters: stream-loop limits alone are insufficient
if a driver buffers full values before returning a stream. Verify server-side
length bounds within the same snapshot before transferring values, qualify the
actual text/LOB transfer and prefetch settings, and retain incremental limits.

Close result streams, LOB handles, statements and physical connections on every
path. Roll back the read transaction. Cancellation, abort and close are separate
steps: a requested cancel is not confirmed cleanup. If work/cleanup cannot be
confirmed within the bound, return INCONCLUSIVE and retain the session's resource
reservation/quarantine; no observation or later export authority survives it.
At the final completed-work result check, recheck the original cancellation
flag, including cancellation during rollback or connection close. With no earlier
latched terminal reason, an observed cancellation selects `CANCELLED`; it cannot
publish a complete observation. Keep the actual cleanup outcome independent: cancellation
does not make cleanup complete, renew a deadline or release a quarantined permit.
Successful driver close/rollback behavior must be checked against actual backend
session disappearance in disposable tests. Do not claim guaranteed remote cleanup
during network partitions. No background task may continue with unowned secrets.
An INCONCLUSIVE refusal may carry an internal operation-owned cleanup handle with
status/cancel/retry methods. It retains the original task/resources and accepts no
new authentication. It is never serialized into HTTP, fingerprints or persistent
state. Future session integration retains the quarantine until cleanup completes.
Bound the adapter to four concurrent physical operations or quarantines; refuse
capacity before allocating another worker/connection. Inconclusive work retains
its permit. Cancelling a Future is not evidence that driver or remote work ended.

The observation fingerprint uses the native v2 framing algorithm with domain
`ES-OBSERVATION-2`, a zero byte, then a closed object containing logical digest,
binding digest, engine/driver/storage/encoding versions, independent/observed
destination identity, and all document IDs, typed keys, exact XML values and
source digests in stable document-ID order. Exclude credentials and timestamps.
Successful cleanup and verified metadata are explicit evidence bound to that
fingerprint. Target planning must additionally project and validate the graph;
a database read cannot substitute for those checks.

The closed fingerprint object has exactly `logicalDigest`, `bindingDigest`,
`engine`, `engineVersion`, `driverVersion`, `storage`, `storageVersion`, `encoding`,
`destination`, `metadata`, `cleanup` and `documents`. All are strings except the
three nested structures and document array. `storageVersion` is the mechanism
name `postgresql-text-v1` or `oracle-clob-v1`; cleanup is `complete` only after
the stated completion checks. Hashing a literal does not perform that check.

`destination` contains `id`, `host`, integer `port`, `database`,
`transportIdentity`, `expectedPhysicalIdentity`, `observedPhysicalIdentity` and
`provisioningPolicyVersion`. The physical identities are closed engine-specific
objects: PostgreSQL uses `systemIdentifier`, `databaseOid`, `databaseName`;
Oracle uses `dbid`, `dbUniqueName`, `conId`, `conUid`, `conName`, `pdbGuid`.
All physical identity fields are strings; numeric identities use canonical
decimal strings and the PDB GUID uses 32 lowercase hexadecimal digits.
`transportIdentity` names the approved hostname/trust-material policy, not an
invented observed peer certificate. Actual driver TLS/host verification is still
required outside the explicit disposable loopback-test composition.

`metadata` contains `adapterVersion` (`jdbc-observation-v2`),
`operationPolicyVersion` (the exact engine identifier above), `visibility`
(`complete`), `readOnlyOperation` (`verified`) and `snapshot`
(`repeatable-read-read-only` for PostgreSQL or `read-only` for Oracle).
Only the adapter may assemble these successful facts after executing the checks.
Each document contains `documentId`, `key: {type, value}`, exact `xml`, integer
`utf8Bytes`, integer `characters` (UTF-16 code units), and `sourceDigest`.
Key type is `text` or `int64`; value is always an exact string. Source digests
are SHA-256 of strict UTF-8 XML. Native framing orders object keys by unsigned
UTF-8 bytes; sort document arrays by declared document ID.

The narrow core port accepts a server-compiled ready-to-publish result, explicit
binding ID and transient credentials/cancellation. Destination configuration is
adapter-owned. Return a complete observation only with cleanup COMPLETE;
otherwise return a typed refusal with cleanup COMPLETE or INCONCLUSIVE. No
public caller can provide a compiler result, metadata PASS or destination witness.
The external account must have the necessary identity/metadata read access,
including PostgreSQL `pg_control_system` and Oracle `SYS.V_$DATABASE` /
`SYS.V_$CONTAINERS`. The application never creates those grants itself.

## Acceptance and investigation

Use the native v2 invented family on both disposable engines with ordinary
write-capable accounts under the operation policy above. Observe baseline/no-op
fidelity, complete membership and
concurrent changes across one snapshot. Challenge missing/extra rows, denied
metadata/SELECT, RLS/VPD-hidden rows, role/column write grants, wrong destination,
null/empty values, multibyte CLOB streaming, exact size boundaries, stalled reads,
cancel races, disconnects and cleanup failure. Assert backend session cleanup
through a separate harness observer, not the connection under test. Synthetic
credential canaries must remain absent from logs, errors, storage and artifacts.
Record exact engines/drivers/clients, commands, outcomes and untested combinations.

Primary references: [PostgreSQL isolation](https://www.postgresql.org/docs/18/transaction-iso.html),
[RLS](https://www.postgresql.org/docs/18/ddl-rowsecurity.html),
[privilege functions](https://www.postgresql.org/docs/18/functions-info.html),
[session settings view](https://www.postgresql.org/docs/18/view-pg-settings.html),
[pgJDBC settings](https://jdbc.postgresql.org/documentation/use/),
[Oracle read transactions](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/SET-TRANSACTION.html),
[Oracle policy metadata](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_POLICIES.html),
[Oracle FGA metadata](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/DBA_AUDIT_POLICIES.html),
[Oracle FGA handlers](https://docs.oracle.com/en/database/oracle/oracle-database/26/arpls/DBMS_FGA.html),
[Oracle PUBLIC privileges](https://docs.oracle.com/en/database/oracle/oracle-database/26/dbseg/configuring-privilege-and-role-authorization.html),
[Oracle PUBLIC grant restriction](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/GRANT.html),
[Oracle grant metadata](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/DBA_TAB_PRIVS.html),
[Oracle object provenance](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_OBJECTS.html),
[Oracle account read-only mode](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/ALTER-USER.html),
[Oracle session mode](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/READ_ONLY.html),
[Oracle autonomous transactions](https://docs.oracle.com/en/database/oracle/oracle-database/26/lnpls/autonomous-transactions.html),
[Oracle cancellation limits](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjdbc/JDBC-troubleshooting.html).

## Planned hosted admission extension

[Hosted plans](hosted-plans-v1.md) require a credential-free reservation over the
same four physical/quarantine slots. A reserved permit is single-use; an unused
permit releases without a connection, while a started permit remains occupied
until actual complete cleanup. Direct observation reserves internally and shares
that budget. This extension is planned; the integrated D04 adapter currently
acquires its slot inside observe. Hosted routes cannot accept credentials until
the shared reservation boundary and lease/cancellation tests are implemented.
