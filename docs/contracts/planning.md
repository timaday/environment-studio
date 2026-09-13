# Observation, planning and revision authority

An observation covers the declared complete inventory in one qualified
consistent read transaction. Capture typed row keys (decimal strings across
HTTP), engine/storage identity, exact full values, NULL/empty distinctions,
scope membership, dependencies, encoding and destination evidence. Preview
truncation is labelled and never narrows authoritative validation. Permissions
that hide rows cannot be equated with an empty complete scope.

A plan pins definition/profile revisions, observation fingerprint, target
mapping, field bindings, supported operations, rule/writer versions and
independent destination identity. Hash canonical semantic content with
unambiguous length framing; do not include timestamps/layout. Any relevant edit
creates a new revision and invalidates dependent validation and review.

Typed commands include ComposeProfileSelection, ChooseMapping, CreateEntity,
MoveRelation, RemoveEntity, BindField and KeepObserved. These are vocabulary
independent. The definition and writer must support each operation. Dragging
canvas positions changes layout only. Moves, replicas and copies are distinct;
no silent deletion cascade. Inserts/row keys/triggers need a qualified strategy.
The internal [structural target mechanism](structural-target.md) specifies exact
field/reference decisions, creation/move placement and independent final graph
comparison before hosted plan authority is added.

Every mutation includes expectedRevision and requestId. A stale command returns
409; the same request ID/body returns the original bounded result, while reuse
with different content conflicts. Credential-bearing operations are not retried
as ordinary idempotent mutations: submit once, poll by operation ID, and request
new authentication for a new inspection. Never queue credentials durably.

Bindings are Unresolved, Entered, KeepObserved or ExplicitlyAbsent where legal.
Requiredness, editability, portability and readability are separate. Values
retain case, leading zeroes and whitespace unless semantics explicitly define
normalization. There is no environment-wide blind find/replace.

Diagnostics include stable code, rule revision, phase, subject, safe document
locator, expected/observed cardinality where safe, outcome and corrective action.
Outcomes: PASS, FAIL, UNKNOWN, ERROR, NOT_APPLICABLE. Required anomalies cannot
be waived in the operator UI. NA requires explicit applicability evidence.

The validator checks completeness, definition compatibility, identity, mapping,
values, graph constraints, cross-document references, XML target validity,
patch footprint, destination, adapter/client capability and artifact policy.
The writer is called only with a server-created validated capability bound to
all current inputs. Export rechecks authority; the UI is advisory.
