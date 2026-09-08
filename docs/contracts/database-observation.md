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
qualified adapter/account-policy version. Runtime configuration stays outside the
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

## Visibility and read-only account evidence

A successful SELECT, COUNT or JDBC `readOnly` flag cannot establish complete
visibility or least privilege. Require the qualified account provisioning policy
plus verifiable engine metadata. Refuse metadata denial, unsupported security
features and incomplete evidence instead of interpreting them as empty results.

PostgreSQL rejects enabled RLS/policies and unsupported relation kinds. Verify
effective SELECT access and reject ownership, superuser/admin/replication/BYPASSRLS
authority, reachable privileged roles and table/column write privileges, including
role and PUBLIC grants. `row_security=off` supplies an additional refusal if a
policy would filter rows; it does not bypass the policy. Qualification challenges
column grants and reachable roles, not only direct table grants.

Oracle rejects applicable enabled VPD, label-security/redaction or other
unqualified policy mechanisms. Obtain the metadata needed to prove policy
coverage using explicitly provisioned read-only grants. Absence in a restricted
view is not proof of absence. Reject table ownership and effective object,
column, schema or system write/admin privileges, including active roles/PUBLIC.
`SESSION_PRIVS` alone is insufficient. The external provisioning policy must
also exclude unqualified write-capable routines; arbitrary account purity cannot
be inferred from a few catalog rows. The disposable harness provisions and
adversely tests the exact supported policy, with no production qualification claim.
The initial Oracle policy rejects non-SYS EXECUTE grants, including PUBLIC and
role grants. Trusted SYS PUBLIC built-ins require the pinned vendor version and
an independently supplied approved grant-set digest; owner `SYS` alone does not
approve a new grant. Compare the observed sorted routine-identity/type/privilege
set to that expected digest and bind the policy content identity into the
observation. Additional SYS grants and all write/admin privileges still require
explicit evidence and otherwise refuse. Routine implementation purity remains
an external provisioning responsibility. The dedicated disposable PDB may revoke
unneeded non-SYS PUBLIC grants for this qualification policy; the application
never performs such provisioning and no production recommendation is implied.

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
`ES-OBSERVATION-1`, a zero byte, then a closed object containing logical digest,
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

`metadata` contains `adapterVersion`, `accountPolicyVersion`, `visibility`
(`complete`), `leastPrivilege` (`verified`) and `snapshot`
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
Read-only provisioning may grant the necessary identity/metadata reads explicitly,
including PostgreSQL `pg_control_system` and Oracle `SYS.V_$DATABASE` /
`SYS.V_$CONTAINERS`. The application never creates those grants itself.

## Acceptance and investigation

Use the native v2 invented family on both disposable engines with a genuinely
read-only account. Observe baseline/no-op fidelity, complete membership and
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
[pgJDBC settings](https://jdbc.postgresql.org/documentation/use/),
[Oracle read transactions](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/SET-TRANSACTION.html),
[Oracle policy metadata](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_POLICIES.html),
[Oracle cancellation limits](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjdbc/JDBC-troubleshooting.html).
