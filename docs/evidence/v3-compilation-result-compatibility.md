# V3 compilation result compatibility — 10 September 2026

Status: implemented, author-checked and independently source-reviewed; not
integrated or publication-qualified. Base `a6291d2605b87d3730b2166e36f5aa7e03d253d7`
preserves both pending observation corrections. The
[native definition contract](../contracts/native-definition-v3.md) defines the
closed result and limits. Exact candidate/fetch status remains in
[issue9](https://github.com/timaday/environment-studio/issues/9).

ReadyToPublish now represents compiler eligibility data, with an exact Checked
model, immutable empty diagnostics and redacted rendering. A shared compatibility
predicate compares the complete declaration, logical/binding digests and mechanism
vector. It accepts only exact ReadyToPublish or exact Incomplete containing solely
MECHANISM_UNQUALIFIED; Rejected, mismatches and all physical/mixed blockers refuse.
All five existing internal consumers retain actual fresh compilation before this
comparison. The workspace adapter projects the typed result without removing
incomplete diagnostics or creating a publication reference.

The actual compiler is unchanged and still emits Incomplete for every valid input.
This is preparation for a later evidence-backed transition. No configuration flag,
mechanism/digest/wire change, uploaded authority or production readiness is added.
Existing new-publication and fresh-lookup checks remain authoritative.

## Actual checks

Core RED: the matching constructed ready model failed the compatibility assertion;
four adverse/current-compiler cases passed. Exact source/base are frozen externally
in `es-v3-compilation-result-red2-source-20260910`; log
`es-v3-compilation-result-red2-20260910.log`. The earlier red1 log is a test-setup
compile error from the wrong Document accessor, not behavioral RED.

Adapter RED: constructed ready projection raised WORKSPACE_INVALID_REQUEST;
one assertion failed, six server controls and five core/one parser controls passed.
Exact ten-file source is frozen in
`es-v3-compilation-result-adapter-red1-source-20260910`. The adapter then gained its
explicit ready projection. Tests distinguish legitimate source-revision changes
(excluded from compatibility hashes) from forged digest/vector substitution.
Actual missing physical mappings and mixed diagnostics remain refused.

Focused GREEN: **149 tests, 60core/1parser/88server**, zero failures/errors/skips,
11.597s. It includes compiler/model, direct/child derived XML, target/profile,
observation, new-publication refusal, history and fresh plan lookup controls.
Log `es-v3-compilation-result-focused1-20260910.log`. The first selector spelled
the two pending observation regression classes incorrectly, so those were run
explicitly afterward: **38 tests, 5core/1parser/32server**, including15 cancellation
and17 PostgreSQL key cases, zero failures/errors/skips,8.789s. Log
`es-v3-compilation-result-observation-20260910.log`. Counts overlap by six controls;
there are181 distinct selected tests across these runs.

Commands use JDK21/Maven3.9.16 and `mvn -B -ntp -f backend/pom.xml -pl server -am`
with explicit test selectors followed by `test`. Isolated worktree outputs;
no full reactor/native build, container or database allocation. G02 on this branch
passes79 frontend/58 schema tests, TypeScript/Biome36 and production build with
Node24.20.0 and unchanged lockfile (`es-v3-compilation-result-g02-20260910.log`).

Eleven separate compiled mutations fail existing/new behavior assertions: refuse
ready, accept a physical blocker, compare only logical digest, accept Rejected,
strip workspace blockers, refuse ready workspace projection, and bypass each of
the five fresh-compiler consumer guards. Every unchanged control exits0, mutation
compiles and assertion run exits1; production hashes remain unchanged. Exact Java
sources, commands and logs: `es-v3-compilation-result-mutations-20260910/results.json`.

## Review and remaining evidence

Non-author fixed-source review verified all1075 hashes and1065 unchanged base
files, including preservation of both observation corrections. No confirmed
finding. Manifest SHA256
`0a6165b0f9356e25b26ce1db15856b97a168eab08ad614eaeb028bd824492bb3`;
report `es-v3-compilation-result-fixed1-review-20260910.md`. Reviewer assessed149
author tests without execution; later observation/G02/mutation results have author
attribution. Preserve all ten manifest files, including the new test omitted by
the tracked-only patch. This evidence document is additional documentation.

G00 whole-diff provenance/content/integrity/whitespace and11 Python checks pass
before commit. Constructed-ready unit/adapter tests do not establish actual-ready
execution through every consumer. Actual compiler publication-to-profile/history/
plan round trip and combined v3 resources remain required. The remote baseline
full gate also remains failed pending TEST-QA-001 correction/verification. Native,
browser/operator, client/export, exact combined OCI/GHCR/HiveForge and release
qualification remain incomplete. All declarations/data are independently invented.
