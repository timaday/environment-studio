# XML fidelity and SQL artifact contract

## XML

DTD, external entities, XInclude and external schema access are disabled. The
exact parser's safety flags must be asserted; unavailable hardening fails
initialization. Use a closed pinned local dependency bundle. Bound document
count, chars/bytes, depth, attributes, references, selector work and total
operation time; measure supported capacities on representative data.

Selectors use explicit namespaces, enclosing record/context and expected
cardinality. Never broaden a selector to every similarly named property in a
database. Missing declared unique identity requires refusal, not an invented ID. Source spans and
patches must preserve unchanged lexical characters including comments, entity
spelling, quote style, line endings and significant whitespace. Reject overlapping
patches and ambiguous contexts. Signed/encrypted XML needs dedicated support or
refusal. DOM pretty-printing is a display projection, not a lossless writer.

## Artifact guard sequence

1. Pin the exact engine/version/storage/client and independent destination witness.
2. Establish client fail/exit/rollback behavior before execution; no interactive continuation.
3. Open the single explicit transaction and obtain bounded locks in stable table order.
4. Verify independent destination identity, **complete** row membership and original full content of the read set, including unchanged dependencies.
5. Perform only intended DML; assert exact affected-row counts after each operation.
6. Re-read/assert the complete expected target state and scope before the single commit.
7. Abort on every exception. Record uncertain commit responses as unknown; use fresh readback.

There is no generic SQL generator in the starter. Do not supply a plausible
unguarded script to make the demo look finished. Export must be an engine-specific
qualified implementation with adversarial exact-client evidence.

Oracle initially targets CLOB and qualified SQL*Plus/SQLcl. Avoid DDL/implicit
commits and DML-triggered external effects. Qualify temporary LOB cleanup,
complete comparisons, NULL vs empty LOB, multibyte chunking/literal limits,
substitution, client command errors, session/login scripts and final-error
rollback. SQL*Plus SQLERROR alone does not catch all client command errors.

PostgreSQL initially targets text and pinned psql with startup files disabled,
ON_ERROR_STOP on and ON_ERROR_ROLLBACK off. Qualify repeatable-read observation,
schema-qualified SQL, safe search_path, bounded locks covering inserts/deletes,
complete byte/encoding comparisons, Unicode, NULL/empty, delimiters and client
metacommands. Native xml/OID/bounded-character storage remains unsupported
until separately qualified; no automatic conversion to text.

Lock only qualifying application tables with DBA-approved maintenance semantics.
The initial conservative candidate is a stable-order exclusive table lock over
the full read set; actual lock mode and timeout require engine/client testing.
A timeout does not fall back to weaker protection. One package covers one
physical DB transaction boundary. A/B distributed atomicity is not assumed.

Guarded post-commit recovery is a **new** artifact against freshly observed
current state. Do not promise a blind rollback script; sequences, external
triggers and already-running applications may have irreversible effects.
Keep the cloned application isolated through the existing operational process.

Whole-document SQL may contain unchanged application secrets. A preview mask
never changes payload bytes. An explicit field/document export policy must
permit protected self-contained export or refuse it. Packages include SQL,
manifest/digests and exact-client instructions, never connection credentials.
Old downloaded artifacts cannot be revoked by editing a plan; the release
process must select the intended digest and maintenance window.
