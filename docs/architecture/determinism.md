# Deterministic model and algorithms

Let D be an immutable compiled definition, O a complete observation with exact
original character sequences L, P a value-free profile, mu explicit object
correspondence and B explicitly typed target bindings. V pins engine, rules,
writer, encoding and capability versions. The target is T = Plan(D,O,P,mu,B,V).

The same frozen inputs produce the same completed semantic plan, diagnostic
ordering and per-adapter SQL payload bytes. Audit timestamps and random request
IDs are outside the deterministic payload. Operational timeout outcomes may
vary; they are inconclusive evidence and never PASS.

## Graph model

G = (E,R,type,fields) is a finite typed graph. E are logical entities; R are
explicit relations. Type declarations define allowed fields, containment,
reference cardinalities, identity scope and supported operations. Logical IDs
are tool-owned slots; database row locators and actual server IDs are separate.

For selected profile roots S and required-dependency relation dep, closure is
the least fixed point C0=S; C(n+1)=Cn union dep(Cn). A visited-set traversal is
O(|E|+|R|), followed by stable ordinal ordering O(|E| log |E|). Cycles terminate;
forbidden containment cycles are diagnosed separately. Missing dependencies
block. Removal or changed relation selections recompute closure. The user sees
why each extra dependency is required and resolves any collision explicitly.

Matching uses declared identity invariants and explicit choices. Zero, one and
multiple candidates are distinct states. No name similarity, first-result
selection, donor-ID equality, confidence score or unconstrained optimization
selects a match. Two target identities cannot silently alias one source object.

Typed operations are normalized and stable-topologically ordered across affected
documents. Cyclic edit dependencies need a qualified atomic strategy or refusal.
The planner's read set includes unchanged dependencies and scope membership;
the write set is only the intended edits. A single logical change may touch
many records; a record may encode multiple logical entities.

## XML laws to qualify

- Extract(Patch(L, delta), D) = intended target projection, for the supported family.
- Patch(L, empty) = L character for character.
- Characters outside declared, non-overlapping edit spans remain identical.
- Reference and scope invariants hold across the complete target document set.
- Exact expected XML is independently reviewed; using the same parser/writer
  as the sole oracle can reproduce the same bug.

Use namespace-aware parsing for meaning and a qualified token/span mechanism
for writes. Display formatting and placeholder rendering are separate read-only
projections. Hash full length-framed inputs with a pinned encoding; sort semantic
maps, preserve significant ordered lists, and distinguish absent/null/empty.
Do not claim cross-engine byte identity where storage semantics differ.

## Evidence gate

ExportAllowed = CurrentRevision AND CompleteRequiredEvidence AND
AllRequiredChecksPass AND ReviewMatches AND QualifiedDestinationAndClient.
NOT_APPLICABLE is allowed only when a rule has explicit applicability evidence;
it is not a generic success. The reference gate requires PASS for every mandatory
category and rejects missing, duplicate or stale results. Additional compiled
application rules must also pass in the later validator.

No solver is needed for the first application family. If coupled constraints
later require one, pin variables/domains/order/tie-breaking and qualify refusal
on timeout. A solver cannot invent an application's semantics.
