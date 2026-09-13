# D06a — structural target compiler and XML materializer

Candidate based on `589044f5015465458ccd23156a8c82784bae86ee`, implemented in
`/tmp/es-d06-structural-target`. This is an internal mechanism, not publication,
export, SQL execution, complete database observation or hosted plan authority.
The lead owns integration, independent review and OCI checks.

## Contract and implementation

The frozen `structural-target.md`, native-v2, graph-projection and lossless-XML
contracts govern this slice. Lead clarifications require explicit reference
choices for selected retained entities, move-relation for new relation edges,
and an affected entity union of at most 40,000 while each graph remains bounded
at 20,000 entities/50,000 edges. Each intent collection remains at most 20,000.

`TargetIntentCompiler` independently resolves exact fields, identities and edges
using separate existing and fresh reference domains. It checks declared
operations, codecs, requiredness, explicit choices, identity uniqueness,
containment and cardinality/count rules. KeepObserved retains both the old
reference literal and stable target; an identity rename plus fresh reuse of the
old identity cannot capture retained references.

`StructuralTargetAdapter.materialize` reprojects the full supplied inventory,
compiles the independent semantic expectation, assembles qualified lexical edits,
then reprojects all complete targets and compares exact entity/field/edge sets.
It separately checks document, projection and direct parent positions using
tracked element provenance. It returns every complete target source, final graph
and affected sets, or safe refusal codes without a partial target.

Creation supports explicit existing or created parents, ordered grouped inserts
and nested creation. Attribute edits preserve lexical context. Moves extract
whole subtrees, apply inner edits, qualify namespace and complete inherited
xml:lang/xml:space/xml:base/document contexts, and insert self-contained markup.
Parent removal with relocated children extracts first and collapses only the
remaining source removals; other modeled descendants require explicit removal.
Nested move sources and overlapping source/destination placements refuse.

Full parsed trees are processed per document, not retained for the entire scope.
Only modeled/selected-parent metadata, exact source/target strings and compact
final positions survive across documents. Edit collection checks 100,000 edits
and cumulative inserted characters against the per-XML 1 MiB limit before
retaining another payload. Source and final scope UTF-8 budgets remain 16 MiB;
per-document/parser limits are unchanged. Domain code has no XML/framework or
wire serialization dependencies. Value-bearing public records render redacted
strings and copy bounded collections immutably.

## Observed RED and GREEN

Toolchain: `/tmp/es-lead-toolchain/maven/bin/mvn` (Maven 3.9.16, Java 21).
Full command: `mvn -B -ntp -f backend/pom.xml verify` in this independent worktree.
No install, zero-test override, shared Git mutation or dependency change occurred.

| Behavior RED | Actual observation | Local log |
| --- | --- | --- |
| Cross-document one-to-two | Server 142 tests, one assertion failure: expected Complete, callable stub returned rejection | `/tmp/es-d06a-red.log` |
| Independent semantic expectation | Core 64 tests, one assertion failure: expected target, callable stub rejected | `/tmp/es-d06a-core-red.log` |
| Move with inner edit | Server 143 tests, one assertion failure: move not implemented | `/tmp/es-d06a-move-red.log` |
| Parsed-tree retention architecture | One assertion failure detects scope-wide parsed source cache | `/tmp/es-d06a-retention-red.log` |
| Containment intent pre-copy limit | One assertion failure: oversized virtual list traversed before refusal | `/tmp/es-d06a-intent-budget-red.log` |
| Incremental insertion/edit budgets | Two assertion failures: oversized accumulation did not refuse | `/tmp/es-d06a-assembly-budget-red.log` |
| Missing typed containment endpoint | One assertion failure: assertDoesNotThrow observed NullPointerException | `/tmp/es-d06a-null-red.log` |

Each RED had zero test errors and exercised callable production behavior (the
cache case is explicitly an architecture assertion). A temporary parenthesis
compilation typo during move implementation was corrected and is not counted as
RED. An interrupted verification ending during the structural adapter suite is
not claimed successful (`/tmp/es-d06a-final-verify.log`).

Final full verify: **240 tests passed** (71 core, 7 qualified parser, 162 server),
zero failures/errors/skips, at 2026-09-08T20:59:15+01:00,
`/tmp/es-d06a-frozen-verify.log`. Earlier full GREEN after the sequential refactor
and expanded tests: 239 tests (71 core,
7 qualified parser, 161 server), zero failures/errors/skips, at
2026-09-08T20:58:21+01:00, `/tmp/es-d06a-final-verify-resumed.log`.

## Independent examples and adverse coverage

The four registered standalone XML files are invented mock glyph/palette data.
Both Postgres and Oracle bindings use independent exact expected XML files for
two glyphs sharing one palette, creation of a second palette with fresh identity
and environment value, and rebinding only one glyph. The unselected glyph and
old palette remain. Inline tests independently construct other mock examples;
expected target strings are literal expectations, not outputs generated by the
materializer.

Additional checks cover exact no-op, repeated equal values, single-quote and
whitespace preservation, CRLF/comment/PI/CDATA/unicode preservation, escaped
attribute controls, optional absence versus empty, unsupported presence changes,
noneditable KeepObserved, integer lexical rules through 1,024 digits, malformed
Unicode/XML characters, removal with child relocation, nested creation and
insertion order, moved modeled descendants exactly once, stale parent digest or
index, wrong physical parent, nested moves, namespace aliases/unused bindings,
inherited language/space, the full base chain rather than nearest declaration,
relative-base uncertainty, explicit document context, virtual oversized lists,
complete scope byte bounds, immutable collections and safe canary rendering.

Actual capacity example: 128 documents, each with 20,000 lexical elements,
2.56 million total mostly unmodeled elements, about 10 MiB XML, exact no-op,
passed under a 256 MiB test JVM heap. Command:

```
mvn -B -ntp -f backend/pom.xml -DargLine=-Xmx256m -Dtest=ArchitectureTest,FifthEditionClassifierTest,StructuralTargetAdapterTest#fullDocumentCountWithTwentyThousandMostlyUnmodeledElementsProcessesSequentially test
```

Log: `/tmp/es-d06a-capacity.log`. All three selected prerequisite/adapter tests
passed. This is one measured scratch case, not a hosted concurrency or complete
worst-case capacity qualification.

## G06 mutation evidence

Six focused manual guard mutations ran in an isolated backend/schema/fixture
copy; no production worktree mutation was left behind. Every mutation produced
one assertion failure and zero errors:

- Remove retained-reference identity check: identity capture test failed.
- Remove operation-capability check: undeclared operation test failed.
- Remove complete namespace-context equality: changed unused namespace test failed.
- Compare only nearest xml:base declaration: full ancestor base-chain test failed.
- Remove semantic outcome comparison: wrong physical parent case was accepted.
- Remove cumulative insertion budget: oversized accumulation was accepted.

Core-only selectors run real core tests. Reactor selectors include
`ArchitectureTest,FifthEditionClassifierTest` plus the relevant adapter test;
no no-test gate is disabled. The reproducible local runner is
`/tmp/es-d06-frozen-mutants.py`, exact commands/results are in
`/tmp/es-d06a-frozen-mutants.json`, logs use
`/tmp/es-d06a-frozen-mutant-<name>.log`. Each changed source is restored byte
exactly in `finally`; the final worktree full verify runs unmutated code.

## Scope, provenance, gates and remaining work

Read-only comparison against the base Git archive checked all 237 tracked files:
none changed. Only assigned planning packages/tests, invented structural-target
fixtures/provenance and this evidence file are additions. No POM, existing XML
adapter, schema, shared contract, HTTP, authentication, workspace or Git state
was changed. No new dependency is introduced.

G00 repository integrity and known-pattern content checks passed; Python script
unit tests passed (10). Node 24 mock schema tests passed (8), using the existing
frontend dependency directory read-only through NODE_PATH. These checks do not
establish private-data provenance: the entire new content was also reviewed as
independently invented generic mock material.

G01/G04 are covered by full reactor and exact independent examples; G06 is the
six targeted mutants above. Synthetic canary/toString checks are bounded G07
mechanism evidence only. Frontend, browser, real DB/client, OCI, release and
hosted lifecycle/capacity gates were not run for this isolated internal slice.
The lead must add `COPY fixtures/structural-target fixtures/structural-target`
to the Docker build stage before its reactor tests. Existing source metadata is
not a complete database observation fingerprint; later hosting/planner work must
bind the complete observation and plan revision. No publication or export
qualification is implied.

Timing: the first behavior RED was at 19:23:10 BST; final work continued after a
roughly 55-minute interruption from about 20:03 to 20:58. Active implementation
and qualification time is approximately 45 minutes, with about eight minutes
of that spent on capacity/budget/null-ref correction and reruns; these are
estimates, not instrumented performance measurements. No blocking product
ambiguity remains. Lead integration/review time is separate; no speed-up claim.
