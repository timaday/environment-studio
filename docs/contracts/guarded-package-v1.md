# Guarded package and external client supervisor — planned D07

This refines [XML and SQL guards](xml-and-sql.md). No implementation or client
qualification is implied by this contract. The application generates a protected
artifact and never connects with execution credentials or launches a client.
Operations runs a separately installed, pinned supervisor through its existing
change process. An exported package is data, not an uploaded executable extension.
Its packaging and closed external configuration are defined in
[supervisor runtime v1](guarded-supervisor-runtime-v1.md).

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

## Hosted package candidate route

The hosted application may expose `POST /api/v3/plans/{planId}/package-candidates/guarded`
as an operator download route for an unqualified guarded package candidate. The
request body is a closed JSON object with `revision` and `inputFingerprint` only.
The caller cannot provide destination, server, client, template, policy, binding
or publication fields. Those fields are read from the pinned plan admission and
trusted destination configuration after the server revalidates current ownership,
revision, inspection evidence, complete target materialization, definition
publication equality, protected document-content policy and the supplied input
fingerprint. Unsupported or unconfigured client tuples refuse before any package
bytes are written.

A successful response is `application/zip` with `Cache-Control: no-store`,
`Content-Disposition: attachment; filename="environment-studio-guarded-package.zip"`
and `X-Environment-Studio-Qualified: false`. The archive itself remains the
only self-contained payload: operators still select and record the reviewed
archive SHA-256 independently from the downloaded bytes. Refusals before the
stream begins return the normal plan refusal code. This route does not make
`exportAvailable` true, does not persist a package, does not execute SQL and
does not qualify a production client/runtime.

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
and ASCII hex chunks or the canonical Oracle base64 transport described below.
No artifact may add COMMIT, EXIT, CONNECT, include, host,
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

The supervisor starts the sole transaction through its fixed bootstrap: PostgreSQL
`BEGIN ISOLATION LEVEL READ COMMITTED READ WRITE`, or Oracle
`SET TRANSACTION READ WRITE` as the first SQL statement after connection/bootstrap.
The archive program contains no transaction-control statement. Its PostgreSQL
form is one DO block; its Oracle form is the deterministic sequence of bounded
anonymous PL/SQL blocks in [Oracle transport](oracle-transport-v1.md). Each block
ends in `END;` and LF, with one further LF between blocks and no slash lines.
The trusted generator returns both the exact concatenated artifact bytes and
the known block boundaries; the supervisor never splits arbitrary SQL text.
It supplies its own fixed SQL*Plus slash terminator after each verified block.
Procedural
BEGIN/END syntax does not begin or commit a separate transaction.
The program acquires the conservative exclusive table lock
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

Initial write-effect eligibility is conservative and template-versioned. PostgreSQL
requires an ordinary permanent local heap with no inheritance/partitioning, RLS,
policies, triggers, DML rewrite rules or generated/identity columns. Qualify only
immediate valid uniqueness and built-in plain btree indexes: no predicates,
expressions, custom access methods/operator classes or unqualified collations.
Oracle requires an ordinary permanent local heap: no IOT, partition/external/
nested/temporary storage, triggers, VPD/redaction, virtual/identity columns,
domain/function indexes or unqualified constraint expressions. Also reject
materialized-view logs, Flashback Data Archive enrollment and FGA policies on
the bound table using complete DBA catalog visibility. These conservative
write-effect exclusions do not assert that each feature affects ordinary reads.
Initial constraints
are qualified NOT NULL and immediate plain key uniqueness; foreign keys and
other check expressions refuse. Require AL32UTF8 and qualified binary key
comparisons. Checks apply to the complete table, including unselected columns
and index/constraint effects. These are candidate refusal rules; tests must
establish the exact catalog predicates, supported builtin variants and runtime
write effects before either template is advertised as qualified. Permission or
metadata uncertainty cannot be converted to absence of an effect.

Compare complete row membership and full original content for every document,
including unchanged dependencies, before the first UPDATE. NULL, empty and
missing are distinct. PostgreSQL compares exact UTF-8 bytea, not collation-sensitive
text equality. Oracle uses complete LOB comparison with explicit NULL/empty and
strict encoding/round-trip checks. Typed keys must identify exactly one row;
collation cannot collapse distinct text keys. Each intended UPDATE asserts
exactly one affected row. Re-read the complete expected target membership/content
after the last UPDATE and before commit. Wrong destination, schema drift,
unexpected row counts or any final mismatch aborts.

For Oracle, payload hex remains unchanged. Derive canonical base64 from its
validated bytes and load two aggregate BLOBs through the bounded transport blocks,
then construct whole document CLOBs using qualified temporary LOB conversion.
No managed table read or write occurs in the loader; the final guard block acquires
the table lock before all database baseline checks and DML. Loading, lock acquisition,
all guards, DML and successful LOB cleanup share the 120-second transaction budget.
Never split a UTF-8 sequence and
decode fragments independently without an explicit boundary proof. Check conversion
warnings, byte/character units, full round trip and complete comparisons. Free
every temporary LOB on success and failure; cleanup failure prevents readiness.
The exact chunk/conversion strategy needs actual full-size/multibyte evidence.
Program exception paths clear readiness, attempt bounded LOB cleanup and rethrow
a safe failure; no exception is swallowed or converted to readiness. PostgreSQL
errors leave the transaction aborted. The supervisor owns bounded rollback through
its fixed error protocol (including Oracle SQLERROR rollback exits); the package
never supplies ROLLBACK or another transaction boundary.

The program contains no COMMIT. Only after every guard and cleanup succeeds does
it set a session-local successful-program marker to programDigest. This
marker cannot be supplied by payload fields or a prior session. The supervisor
then sends its own fixed readiness query with a runtime nonce and independently
selected archive digest, checking the program marker first. The query is the
last precommit input: there is no queued SQL/client tail. Random runtime nonce
does not affect deterministic package bytes.
Use a fresh 128-bit nonce rendered as 32 lowercase hex characters. The qualified
Oracle bootstrap owns `VARIABLE es_program_digest VARCHAR2(64)` for the program
marker; the anonymous blocks use `:es_program_digest`. The initialization and
final guarded blocks clear it before work; only the final guarded block assigns
programDigest at
successful completion. PostgreSQL uses the transaction-local custom setting
`environment_studio.program_digest`, assigned with transaction-local set_config
by the fixed program and cleared before work. Readiness queries check that exact
marker and emit only the fixed protocol frame containing nonce and archive digest.
No payload declaration can name these variables/settings or provide that frame.

Only a complete, exact ordered transcript through that barrier permits the
supervisor's separately compiled COMMIT and acknowledgement query, sent once.
Oracle requires qualified `COMMIT WRITE IMMEDIATE WAIT`; PostgreSQL requires
qualified synchronous commit settings. A malicious package cannot provide its own
readiness, commit or exit command. Any SP2/SQL error, unexpected output/prompt,
overflow, timeout, EOF or truncated frame before commit permanently prevents it.

## External supervisor command contract

The planned separately installed command is `environment-studio-guarded apply`.
It requires exactly `--package` (absolute local ZIP path), `--sha256` (independently
selected lowercase archive digest), `--configuration` (absolute approved external
client configuration path) and `--destination` (independently selected destination
ID). Reject unknown/duplicate options or extra arguments. No username, password,
connection string, SQL expression or test-mode option is accepted on this command
line. External configuration names trusted client binaries, supported versions,
transport/trust and approved destination identities, not DB credentials. Its
closed schema and the separately installed runtime require qualification before
implementation is advertised. Test-only plaintext composition is injected by the
qualification harness, not an ordinary command-line bypass.

`instructions.txt` is fixed UTF-8 with LF and a final LF, exactly:

```text
Environment Studio guarded package v1
Use the separately installed, verified Environment Studio guarded supervisor.
environment-studio-guarded apply --package /absolute/package.zip --sha256 REVIEWED_ARCHIVE_SHA256 --configuration /absolute/approved-client.json --destination APPROVED_DESTINATION_ID
Replace placeholders from the reviewed export and independently approved client configuration.
Provide credentials only when the supervisor requests them. Never add them to this command or the package.
Do not execute transaction.sql directly. The supervisor owns transaction control and commit acknowledgement.
```

No package is qualified merely because a future supervisor accepts its shape.
The exact installed supervisor/runtime, native clients, TLS and fault matrix
remain required evidence for the advertised combination.

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

Transaction-control references: [PostgreSQL DO](https://www.postgresql.org/docs/18/sql-do.html)
for the prohibition on transaction control inside an enclosing transaction, and
[Oracle SET TRANSACTION](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/SET-TRANSACTION.html)
for explicit transaction start and first-statement ordering. These rules inform
the candidate protocol; actual exact-client qualification remains mandatory.

V2 packages require compiler mechanism `native-compiler-v2=2`, matching the
[current publication registry](native-definition-v2.md#publication-and-digest-authority).
The strict manifest schema and package admission refuse mechanism revision 1;
reinspection and a new reviewed/exported artifact are required. Historical workspace
readability does not authorize execution of an old package.

For [child-property bindings](child-property-v1.md), the closed manifest adds
`xml-child-property-v1=1` to that selected binding's required dependencies. Direct
bindings omit it even when another binding in the same definition requires it.
Definition-pinned package dependency admission refuses missing, extra or wrong
versions. Mechanical package parsing alone cannot establish these definition
dependencies, publication eligibility, complete target validation or client
qualification; those remain separate server-owned export requirements.

## Version-explicit v3 package pins

The [PostgreSQL16.11 candidate tuple](postgresql-16-template.md) is version-pinned
separately from18.6; neither metadata acceptance nor generated SQL is authority.

The closed v1 container may describe exactly one mechanism family. Existing v2
execution metadata and bytes remain valid without change. V3 metadata requires
`native-compiler-v3=1`, `derived-graph-v1=1` and `plan-validation-v3=1` instead of
`native-compiler-v2` and `plan-validation-v1`. Both families require `xml-path-v1`,
`xml-span-v1`, `generic-graph-v1` and `structural-target-v1`, each at revision1.
`xml-child-property-v1=1` remains selected-binding-specific. Mixed families,
missing dependencies, unknown keys and unsupported revisions refuse. The maximum
mechanism count is eight. Older strict readers reject the new family; do not
relabel a v3 package as v2 for compatibility.

The separate internal `PackageAdmission.readPinnedV3` takes a v3 compiler
ReadyToPublish value, selected binding and the existing execution/payload bytes.
It checks the complete checked definition's declared dependency vector, then the
selected binding ID, engine/storage, logical/binding digests and exact
binding-specific vector. Engine/storage disagreement refuses
`DEFINITION_BINDING_MISMATCH` even if both execution and payload agree with each
other. Complete table/document membership and target proof remain the future
plan-to-package assembler's responsibility.
It never converts an Incomplete compiler result to ReadyToPublish. Mechanical
admission does not establish that its caller's ReadyToPublish value represents a
fresh immutable publication, nor that XML is a validated target. Publication,
complete plan proofs, review, content policy and client qualification remain
server-owned prerequisites before any export. Actual v3 compilation continues
to refuse publication; explicit test-only compiler witnesses cannot enable it.

Acceptance: actual compiled independent v3 fixture plus an explicit test witness
admits its exact selected-binding pins; mismatched digests, wrong binding, mixed
compiler families and missing/extra child dependencies refuse. Preserve historical
v2 cases and strict XML/payload checks. Generated transaction candidates remain
unqualified, the inspector remains unavailable for export, and no hosted export,
SQL execution or post-commit rollback workflow is added by this prerequisite.

## Internal candidate assembly

The internal assembler accepts only mechanically admitted immutable inputs and
writes the exact four-member archive to a caller-owned output stream. It computes
canonical payload metrics from the exact payload bytes to be emitted, rebinds the
private admitted input to that payload digest, and generates SQL from the rebound
execution context so programDigest binds the exact emitted payload. The admission
boundary remains `PackageAdmission`; callers cannot construct an accepted package
or supply SQL, manifests or instructions. The assembler derives all member
lengths/hashes/counts from the actual bytes it emits. Identical admitted content
produces identical bytes.

Success is explicitly an unqualified candidate with total bytes and archive
SHA256; it is never publication, review, download or execution authority.
Cancellation before writing emits no bytes. Cancellation/output failure during
streaming returns refusal; any partial bytes must be discarded by the owner.
The assembler neither flushes nor closes the caller stream. Existing per-member,
source/target and archive limits apply. The future hosted owner must reserve
combined memory/streaming resources and check all original live plan and export
authority before and after assembly. No endpoint or capability is enabled here.
