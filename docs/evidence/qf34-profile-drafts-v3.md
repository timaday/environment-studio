# Owned value-free v3 profile draft persistence

Reviewed internal implementation,9 September2026. The
[profile command contract](../contracts/profile-v3.md#owned-profile-draft-command)
joins actual portable v3 validation with owned schema3 history. Saves retain exact
JSON/YAML source, format, content/source digests and the selected immutable
definition publication reference. They create an unpublished draft. No profile
HTTP route, new publication, capture/composition command or runtime readiness is
added. V1/v2 behavior and historical bytes remain unchanged.

The four-file author candidate uses base9454d9f; manifest SHA-256:
`73c2397fe7a29462e47dc60a2796cf695178db14ea8df4ccd9139ad8f89f582b`.
Contract2 manifest:
`c428ac3de3c56cb1bd49303fbac03894154396f35c2e91ce53e95628b71dff03`.
The final integration retains native hashing at
`45d6becc8de0f94a174f0d9310a5540714cd857c` and adds those four files, one independent
reviewer test and contract2. Its six-file manifest is
`c2a2108763391d333f70723eec75c43bceab5df09ecf57f64ed1991e436a69b1`.
All six hashes matched after verification and at source integration. Later prose
updates only report implementation status and this evidence.

## Actual checks and independent review

First RED: one real SQLite JSON/YAML save assertion failed against unavailable
scaffolding, zero errors, with passing core/parser controls. Before GREEN an
unreached fixture count expectation was corrected to the actual three invented
slot IDs; no production behavior changed to satisfy that mistaken expectation.
Final focused49 passed:6 core,1 parser and42 server, zero failures/errors/skips.
Nine new actual-store cases cover exact source/history/restart; replay after
definition/profile edits; owner/kind/version/reference refusal; computed slot,
edge, donor-field and digest rejection; commit rollback/no replay; concurrent
stale saves; malformed Unicode/byte limits; unsupported historical mechanisms;
and exact1 MiB source acceptance. Five core controls test sequencing, historical
blockers, bounded revision, compiler reference integrity and no append retry.

Six isolated mutants compiled and each failed exactly one assertion, zero errors:
replay bypass, historical publication bypass, historical blocker bypass, source
trimming before hashing, compiler reference replacement and byte-refusal
misclassification. The restored separate archive passed49 with all four original
hashes unchanged. No compilation failure or unrelated test failure counted as a
semantic kill. Author record SHA-256:
`26c59bb7c1c4a1e0dc08cfda2ac1179187f4c1d89afba89fe30ce27d30d30741`.

Independent review found no material source blocker. A contract ambiguity about
diagnostic limits was clarified before the fixed review: current parsing and
physical validation return one safe refusal; any future expanded producer must
signal typed TOO_LARGE before exceeding256, without truncation or an incidental
constructor failure. No impossible oversized-list test or shared validator
refactor was introduced.

The reviewer's42 focused tests passed with exact source/contract hashes. Three
new actual SQLite controls independently establish: revision33 refuses CAPACITY
while original revision1 replay survives; legal1 MiB tab-prefixed source whose
snapshot expands beyond2 MiB refuses TOO_LARGE with no object/replay; and an
otherwise valid changed native ID cannot replace the owned profile. Reviewer
record SHA-256:
`852f5ef5fef8917674dd5610150d2fe2d3d2e44acba9d671a881a3525b4d0f09`.
Added test SHA-256:
`c0951631e66803a88a2d082eebebd39b997d974d7cff4aa2f96168a934ed04ee`.
The lead read both before unchanged integration.

Full integrated `mvn -B -ntp -f backend/pom.xml verify` passed1053 tests:
266 core,7 qualified parser,573 server and207 supervisor, zero failures/errors/
skips, in3m08s at20:05:43 BST. JDK21.0.12/Maven3.9.16 used a fresh external archive.
Supervisor assembly checksums and hostile-environment launch passed too.
Whole selected-diff provenance/disclosure review, repository integrity, staged
content guard, Python11 and whitespace checks passed before commit. Known-pattern
content checks do not replace the provenance review.
Focused command:

```text
mvn -B -ntp -f backend/pom.xml -pl server -am -Dtest=V3ProfileWorkspaceTest,V3ProfileBytesAdapterTest,IndependentV3ProfileAdapterTest,V3NativeSqliteStoreTest,NativeWorkspaceTest,ArchitectureTest,MinimalRuntimeTest test
```

External records under `/home/tim/.tmp`: author archive
`es-v3-profile-drafts-mcjl9vun`, restored mutant archive
`es-v3-profile-mutants-78gvce1b`, integrated archive `es-profile-current-m579owc5`,
full log `es-profile-current-full1-20260909.log`, author/reviewer records and
`es-v3-profile-drafts-mutants-results-20260909.json`.

## Authority and limits

Business: profiles remain physical, value-free reusable structure. Exact draft
replay survives later edits and cannot import donor groups or values. Engineering:
the framework-free application delegates owned history and actual portable
compilation through narrow ports. Unseen commands require an owned immutable
historical definition publication with empty historical diagnostics, then current
checked-model validation. Historical publication is a draft-validation reference,
not current compiler qualification. A readable unsupported mechanism vector fails
new validation without altering history. QA: real parser/SQLite and independent
boundary oracles extend core sequencing checks.

Test historical publications are explicitly installed by an independent mock
harness; the current v3 compiler remains incomplete and does not create them.
No source/value/credential canary enters diagnostic text. Invalid donor fields
leave no profile bytes or replay. No real or transformed private model was used.
The retained9454d9f image predates this draft command and hashing; later image,
HTTP/operator capture, publication, plans, full resource, native client/readback,
current CI/GHCR and HiveForge verification remain separate work.
