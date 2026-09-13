# Bounded Oracle transport — planned D07b

This refines [the guarded package contract](guarded-package-v1.md). It is a
candidate template format, not qualified export or execution authority. The
full-size binary feasibility experiment is recorded in
[the client investigation](../evidence/d07-client-investigation.md).

The payload still carries exact original/target UTF-8 bytes as lowercase hex.
After strict package admission, the trusted generator decodes those bytes and
concatenates originals in canonical document order into one logical byte stream,
and targets in the same order into another. It independently computes each
aggregate's length and SHA-256, and each document's one-based byte offset/length.
It never decodes a partial UTF-8 sequence into text. Each aggregate is at most
16 MiB; no SQL, identifier, expression or client command comes from a decoded byte.

The supervisor owns three SQL*Plus bindings: `es_program_digest VARCHAR2(64)`,
`es_original_blob BLOB` and `es_target_blob BLOB`. VARIABLE declarations are fixed
client bootstrap commands. The first SQL after connection/bootstrap is the sole
`SET TRANSACTION READ WRITE`. BLOB initialization and every loader/guard block
follow that statement; none may commit, roll back or reconnect.

The artifact is an ordered sequence: initialization, all original loaders, all
target loaders, then the final guard block. Every block ends `END;` plus LF; join
blocks with one further LF. No slash or other client-command line is in the
artifact. The trusted generator exposes these exact block boundaries alongside
the identical concatenated bytes; the supervisor verifies the whole artifact
before authentication and sends each known block followed by its own slash/LF.
Never recover boundaries by parsing untrusted SQL. Only the separately compiled
readiness query may follow the final block, with no queued precommit tail.

Initialization clears the successful-program marker and creates both cached
temporary BLOBs with session duration. Each loader holds a local RAW(16384).
Canonical RFC 4648 base64 uses the standard alphabet and required padding, with
no whitespace. Divide each aggregate into at most 16,384-byte chunks; the last
chunk may be shorter. Encode each chunk independently, split its encoded string
into consecutive at-most-2,048-character literals and join them with fixed `||`
syntax inside `UTL_ENCODE.BASE64_DECODE(UTL_RAW.CAST_TO_RAW(...))`. No source line
exceeds 2,499 ASCII characters. Decode once and append once per chunk, using its
exact byte length. At most two chunks occur in one block; no more than 1,024
loader blocks cover the complete two-aggregate scope. Literal splitting concerns
ASCII source only, never partial XML decoding. Empty aggregate/chunk is refused.

The final guard first clears readiness and verifies both complete aggregate
lengths and SHA-256 using the qualified `DBMS_CRYPTO.HASH` BLOB operation. Length
or hash mismatch prevents any managed DML. The execution account must have the
necessary explicitly qualified package/metadata authority; a privileged SYS
experiment is not account qualification. It then acquires the complete table
lock and executes all destination, write-effect, membership, original, row-count
and final-state checks from the parent contract. Aggregate bytes are only a
transport source, never independent evidence of database contents.

For each whole document, copy exactly its aggregate byte range into a bounded
temporary BLOB, then perform full AL32UTF8 BLOB-to-CLOB conversion. Check exact
offset completion, warning status, character/byte units and the complete reverse
conversion bytes before comparing or updating. Reuse no stale converted locator
for a different record. Release per-document temporary LOBs as soon as their
guard/update operation finishes. Initial implementation may reconvert a record
for successive guard stages to avoid retaining every full CLOB simultaneously.

Every initialization, loader and final-block exception path clears readiness,
attempts cleanup of every allocated owned temporary LOB and rethrows a fixed
safe error. A cleanup failure is never converted to success. All temporary LOBs,
including both aggregate bindings, must be freed with checked postconditions
before the final block assigns programDigest. The supervisor also owns a fixed
bounded cleanup/rollback sequence for a still-live client after failure; this
sequence is not supplied by the artifact. Client loss without an acknowledged
rollback/cleanup remains inconclusive under the parent contract.

The 120-second monotonic transaction budget includes loading and every final
guard. Per-block progress does not extend it. Whole-size multibyte XML/CLOB,
write-account permissions, transaction ordering, guard faults, cleanup failures,
TLS and exact-client transcript/acknowledgement tests remain mandatory before
either the writer or supervisor can be advertised as qualified.

Oracle documents the RAW byte conversion in
[UTL_ENCODE](https://docs.oracle.com/en/database/oracle/oracle-database/26/arpls/UTL_ENCODE.html)
and the client limits in
[SQL*Plus limits](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqpug/SQL-Plus-limits.html).
