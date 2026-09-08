# Guarded package and external client supervisor — planned D07

This refines [XML and SQL guards](xml-and-sql.md). No implementation or client
qualification is implied by this contract. The application generates a protected
artifact and never connects with execution credentials or launches a client.
Operations runs a separately installed, pinned supervisor through its existing
change process. An exported package is data, not an uploaded executable extension.

## Closed package and selection

The initial package format is `es-guarded-package-v1`. It contains exactly four
regular files, with no directories, links, extra entries or duplicate names:
`manifest.json`, `payload.json`, `transaction.sql` and `instructions.txt`.
The archive is a deterministic uncompressed ZIP: fixed member order as listed,
fixed DOS timestamp 1980-01-01 00:00:00, no extra fields or archive/member comments,
fixed regular-file permissions and no data descriptors. Version-made-by is
Unix/2.0 (`0x0314`), version-needed is 2.0 (`20`), flags are UTF-8 only (`0x0800`),
compression is STORE (`0`), DOS time/date are `0`/`33`, internal attributes are
zero and external attributes are `0100600 << 16`. Local headers/data and central
records are contiguous in that member order; no padding or preamble is allowed.
CRC, sizes and local/central directory records must agree. ZIP64 is unnecessary under these bounds and
refuses. Both readers and writers enforce the complete archive/member limits;
trailing bytes, hidden entries or a truncated central directory refuse.

Maximum total uncompressed size is 160 MiB, including duplicate hex representation
in payload and SQL. Each JSON/SQL member is at most 80 MiB; manifest/instructions
are at most 64 KiB each. Source and target independently remain within the
observation's 128-document/16-MiB strict UTF-8 limits and existing per-document
XML limits. Bound parsing, template generation and archive assembly incrementally.
The server must stream or use bounded admission for generation, not retain many
full archives in session memory. Runtime packages remain outside the checkout,
image and metadata workspace; only independent mocks are eligible repo fixtures.

The operator supplies the intended complete archive SHA-256 independently from
the package, selected from the reviewed export. Self-contained hashes establish
integrity, not authenticity, destination permission or maintenance approval.
The supervisor must itself be installed/verified from its trusted versioned
distribution before it reads the package. It never executes a helper supplied
by the archive. A downloaded artifact cannot be revoked by a later plan edit.

The closed schemas are [manifest v1](../../schemas/guarded-manifest-v1.schema.json)
and [payload v1](../../schemas/guarded-payload-v1.schema.json). Their example family
is deliberately shape-only and is not an executable package. Semantic checks
below remain mandatory after schema acceptance.

The closed manifest pins format version, engine/storage, exact server and client
versions, platform, supervisor/template/writer versions, immutable plan revision,
plan input fingerprint, complete observation fingerprint, definition publication
digest and applicable profile publication digests. It includes the selected
binding digest, independent destination witness, explicit document content policy,
record/byte counts and each other member's exact byte length/SHA-256. No timestamp,
credential, arbitrary extension or caller-supplied PASS evidence occurs. The
manifest has no recursive self-hash or embedded complete archive hash.

The closed payload contains the selected native binding's exact physical table
and key/XML column declarations, typed key codec, and every declared document in
stable document-ID order. Each record has document ID, typed exact key, complete
original UTF-8 bytes as lowercase hex and complete target UTF-8 bytes as lowercase
hex. Include unchanged dependencies. The initial native binding has one physical
table and changes only XML inside existing rows; no row INSERT/DELETE, arbitrary
predicate/expression or auxiliary SQL is allowed. Derive intended UPDATEs solely
from unequal original and target bytes. Structural entity creation/removal remains
inside XML and does not authorize row defaults, sequences or trigger effects.

All JSON objects are closed, duplicate keys reject and large revisions/keys use
canonical strings. Count/byte/port tokens are canonical unsigned decimal integers,
not floating/exponent tokens. Reject malformed Unicode scalars after JSON escape
decoding. Bound payload JSON to depth 12 and 4096 nodes, manifest to depth 12 and
8192 nodes, with string/collection bounds from the schemas. Hex has even length
and decodes strictly to exact valid UTF-8; keys and identifiers
meet the native binding rules. Independently validate source/target inventory and
derived change set. Records and policies are sorted by document ID, profile
publication digests lexically, with no duplicate document IDs or typed keys;
every record key type equals table.keyType and key/XML columns are distinct.
Use strict signed-64-bit range checks, not numeric-schema shape alone. Counts
and each member digest/length must equal independently recomputed values. Writers
emit deterministic compact JSON with object keys sorted in unsigned UTF-8 order,
exact UTF-8 non-ASCII characters and JSON escaping; input map iteration order
never changes output. Required document policy `deny` refuses export; each included
full original/target document requires published `protected-self-contained`
permission, including unchanged/unmapped/secret content. Preview masks never
alter artifact bytes.

The manifest has a closed `execution` context containing engine/storage,
server/client/platform and supervisor/template/writer versions, plan revision and
input fingerprint, observation fingerprint, publication digests, binding digest,
destination witness and document policies. These fields and the payload digest
participate in SQL generation and readiness binding. Cross-check their engine,
storage, document and binding declarations against the payload and selected
template. The context excludes member hashes, byte counts and archive hashes,
avoiding circular SQL generation. Define `programDigest` as SHA-256 over
`ES-EXECUTION-1`, a zero byte, then native framing of the closed object containing
`execution` and `payloadDigest`. Recompute it independently in the supervisor.

The supervisor validates all members and exactly regenerates the qualified SQL
template from this validated execution context and payload before starting a
client. Comparing hashes
of arbitrary SQL is insufficient. Trusted versioned templates have fixed syntax;
dynamic substitutions are closed quoted identifiers, validated decimal values
and ASCII hex chunks. No artifact may add COMMIT, EXIT, CONNECT, include, host,
substitution, client metacommand or arbitrary expression. Instructions name the
exact client and supervisor invocation and contain no command carrying a secret.
Schema acceptance never replaces template, transaction or authority checks.

## Client admission and credential channel

First qualify Linux amd64, PostgreSQL 18.6/text with psql 18.6, and Oracle Free
23.26.3.0.0/CLOB with SQL*Plus 23.26.3.0.0. Driver qualification alone does not
qualify a CLI. Unknown client/version/platform/template combinations refuse;
SQLcl is not implicitly equivalent to SQL*Plus. The supervisor launches an exact
trusted executable only after matching destination, endpoint and transport identity
against independently approved external supervisor configuration. Archive labels
do not grant endpoint or transport approval. Ordinary supervisor execution and
hosted export require verified TLS with the qualified client's full certificate
chain and hostname verification; trust material comes from external configuration,
never the package. Qualify that the native client verifies TLS before sending DB
authentication over the network. A forced local password prompt may precede that
handshake; its secret is sent only to the already admitted client's owned stdin.
Unknown trust configuration, verification failure or a transport-policy mismatch
refuses without fallback. Plaintext is available only through an explicit
disposable-test composition using literal `127.0.0.1` or `localhost`, with resolved
addresses checked as loopback. This mode is unavailable to ordinary supervisor
invocation and hosted export. Schema validation of the loopback label/host does
not substitute for that independent admission decision.

The supervisor launches the admitted
trusted executable with a clean allowlisted environment and a new process
session, without a controlling terminal. Disable system/user startup scripts,
history, reconnects, substitution and external includes. Verify effective client
settings before any DML. Do not use client error-logging tables or other DDL.

The supervisor owns the sole child stdin and one merged stdout/stderr pipe,
merged at process spawn. Separate output streams plus a timed drain cannot prove
that an earlier error was consumed before readiness. Bound bytes, frames and
operation time; retain only transient captured output and emit safe tool-owned
status externally. Never print SQL/client diagnostics that may contain values.
Initial qualification limits are 10 seconds for each authentication/bootstrap frame,
30 seconds for lock acquisition, 120 seconds for the guarded transaction and
10 seconds for cleanup. Bound each output frame to 64 KiB and the complete
transcript to 1 MiB. Password input is at most 1024 Unicode code points/4096 UTF-8
bytes before its one newline terminator. Timeouts are monotonic deadlines, not
idle timers reset by arbitrary output. Capacity tests must qualify these limits;
timeout refuses and never weakens a guard. The exact supported supervisor runtime
and native-client installation are part of the recorded qualification matrix.

The operator enters execution credentials in ephemeral process memory. Secrets
never enter argv, environment variables, files, URLs, history, logs or the
package. Qualify one explicit password prompt and send one bounded password line
only after that prompt has been recognized. Reject CR/LF/NUL before transmission;
qualify supported credential encoding without silently modifying the password.
For psql, `-W` forces the initial prompt without a failed discovery connection;
`-X` disables startup files. The qualified no-terminal prompt uses stdin/stderr.
For SQL*Plus omit the password from CONNECT, quote the connect identifier
separately from the username, and qualify the actual non-silent prompt protocol.
No second CONNECT is allowed: Oracle CONNECT commits an existing transaction.
Authentication failure, expiry/change-password requests or extra prompts refuse
without retry. All allocated resources receive explicit bounded cleanup.

SQL*Plus silent mode suppresses the prompt needed by this candidate protocol;
its suitability cannot be assumed from the earlier SP2 experiment. One initial
connection is established before transaction work. Execution credentials are
separate from the application's read-only observation account; no artifact
changes account grants or Oracle account mode.

## Guarded transaction and sole commit

The qualified bootstrap sets psql ON_ERROR_STOP on and ON_ERROR_ROLLBACK off,
safe search_path and explicit transaction/client encodings; Oracle disables
AUTOCOMMIT, EXITCOMMIT, DEFINE and all startup execution, and installs explicit
SQL/OS-error rollback exits. Verify settings through expected bounded output.
SQLERROR/exit status alone cannot catch Oracle client errors such as SP2.

Begin one explicit transaction and acquire the conservative exclusive table lock
over the complete read set, with bounded timeout. If several tables are supported
by a future binding, use stable order. Never fall back to weaker locks. After
locking, independently check destination identity and full storage/visibility/
write-effect eligibility. Recheck exact schema/types/keys/encoding, ordinary
local table status, required metadata access and complete visibility. Reject
triggers, DML rewrite rules, generated/custom expression effects, unsupported
constraints/access methods or any unqualified route that could execute external
effects or change unselected data. Read-adapter metadata checks alone do not
establish write-effect safety. No DDL, custom routine, autonomous transaction or
transaction boundary may appear in the guarded program.

Compare complete row membership and full original content for every document,
including unchanged dependencies, before the first UPDATE. NULL, empty and
missing are distinct. PostgreSQL compares exact UTF-8 bytea, not collation-sensitive
text equality. Oracle uses complete LOB comparison with explicit NULL/empty and
strict encoding/round-trip checks. Typed keys must identify exactly one row;
collation cannot collapse distinct text keys. Each intended UPDATE asserts
exactly one affected row. Re-read the complete expected target membership/content
after the last UPDATE and before commit. Wrong destination, schema drift,
unexpected row counts or any final mismatch aborts.

For Oracle, construct full original/target CLOBs through bounded ASCII hex
chunks and qualified temporary LOB conversion. Never split a UTF-8 sequence and
decode fragments independently without an explicit boundary proof. Check conversion
warnings, byte/character units, full round trip and complete comparisons. Free
every temporary LOB on success and failure; cleanup failure prevents readiness.
The exact chunk/conversion strategy needs actual full-size/multibyte evidence.
All exception paths clear readiness, attempt bounded rollback/LOB cleanup and
rethrow a safe failure; no exception is swallowed or converted to readiness.

The program contains no COMMIT. Only after every guard and cleanup succeeds does
it set a session-local successful-program marker to programDigest. This
marker cannot be supplied by payload fields or a prior session. The supervisor
then sends its own fixed readiness query with a runtime nonce and independently
selected archive digest, checking the program marker first. The query is the
last precommit input: there is no queued SQL/client tail. Random runtime nonce
does not affect deterministic package bytes.
Use a fresh 128-bit nonce rendered as 32 lowercase hex characters. The qualified
Oracle bootstrap owns a VARCHAR2 bind variable for the program marker; its
anonymous guarded block clears it before work and assigns programDigest only at
successful completion. PostgreSQL uses a transaction-local custom setting owned
by the fixed program, cleared before work. Readiness queries check that exact
marker and emit only the fixed protocol frame containing nonce and archive digest.
No payload declaration can name these variables/settings or provide that frame.

Only a complete, exact ordered transcript through that barrier permits the
supervisor's separately compiled COMMIT and acknowledgement query, sent once.
Oracle requires qualified `COMMIT WRITE IMMEDIATE WAIT`; PostgreSQL requires
qualified synchronous commit settings. A malicious package cannot provide its own
readiness, commit or exit command. Any SP2/SQL error, unexpected output/prompt,
overflow, timeout, EOF or truncated frame before commit permanently prevents it.

## Outcomes and investigation

Before any commit bytes are delivered, a failure is an aborted attempt with
cleanup COMPLETE or INCONCLUSIVE according to actual rollback/connection evidence.
Request fixed rollback only while client framing/state is known. Otherwise
terminate and retain uncertainty until independent backend/readback evidence;
EOF or process death alone is not proof of rollback. Never regain commit authority
after a failed frame. After any commit bytes may have been delivered, missing or
bad acknowledgement, disconnect, timeout or death means UNKNOWN. Never auto-retry
or describe that outcome as rolled back. APPLIED requires qualified acknowledged
commit plus clean client exit; matching fresh readback is a separate observation,
not proof that this particular execution caused the state.

Qualify both actual clients with independent complete mock-state witnesses:
one-to-two cross-document changes, unchanged dependencies, wrong destination,
missing/extra rows, concurrency/locks, schema/rule/trigger drift, row-count mismatch,
failed final DML and post-state guard, Unicode/NULL/empty/full-size boundaries and
Oracle LOB cleanup. Challenge startup files, password no-echo/history, SP2 before
readiness, forbidden appended tail, fragmented/oversized output, EOF, parent/child
death before and after commit and lost acknowledgement. Kill baseline, membership,
destination, row-count, post-state, rollback and readiness guard mutants; unexplained
survivors block the combination. Record all actual RED/GREEN and RST observations.
No guarded SQL is offered as qualified until the appropriate G04–G07 evidence exists.

References: [psql options and error behavior](https://www.postgresql.org/docs/18/app-psql.html),
[pinned prompt implementation](https://raw.githubusercontent.com/postgres/postgres/REL_18_6/src/common/sprompt.c),
[SQL*Plus CONNECT](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqpug/CONNECT.html),
[SQL*Plus startup configuration](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqpug/configuring-SQL-Plus.html)
and the [actual SP2 investigation](../evidence/d07-client-investigation.md).
