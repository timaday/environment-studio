# D03b/c integrated graph and parser — 8 September 2026

The definition-driven graph adapter and XML 1.0 Fifth Edition correction are
integrated with the previously reviewed native compiler, hosted sessions and
private SQLite drafts. This is component qualification; structural target planning,
actual database observations and guarded SQL/client export remain separate work.

The lead verified the final 28-file writer manifest
`77d23a3cd7f4b7f6e019b08086ef0ff3f0c5fcdd374e632de11673703eb78530`, copied the
26 non-POM files byte-for-byte and merged only the module/dependency additions
into the current POMs. Root integration also allowlists the exact new tool POM
in the content guard. The independent mock XML family remains registered; no
actual application inputs or private model evidence entered this work.

Staging exposed a concrete fidelity issue: Git's text normalization changed the
glyph fixture from eight CRLF sequences to zero. The exact fixture family now
uses `-text` and recognizes intentional CR-at-EOL in `.gitattributes`, retaining
the other standard whitespace checks. After restaging, both XML blobs equal the tested
working files byte-for-byte. This preserves the independent lexical oracle in a
fresh checkout; it does not change the runtime parser or normalize user input.

## Observed checks

- `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp
  -Dmaven.jar.forceCreation=true -f backend/pom.xml verify`: **211 passed**,
  zero failures/errors/skips: core 63, qualified parser 7, server 141.
  Log `/tmp/es-d03bc-integrated.log`; Maven 3.9.16, Java 21.0.12.
- Node 24.20.0: `npm run check --prefix frontend`, `npm test --prefix frontend`
  and `npm run build --prefix frontend` passed: 7 component and 8 schema tests.
  No UI behavior changed in this slice; the earlier browser evidence retains
  its limited definition-inspector scope.
- `python3 scripts/check_repository.py`, Python script tests (10), staged
  `python3 scripts/check_repository_content.py` and diff whitespace checks passed.
  The staged provenance review covers generic engine/tool code, the pinned
  third-party source-build mechanism and independently invented fixtures. The
  pattern guard is not a proof that arbitrary content is generic.
- `docker build --target runtime -t environment-studio:d03bc-review .` passed,
  including the container's Maven reactor. Log `/tmp/es-d03bc-container.log`.
- `bash scripts/container_smoke.sh environment-studio:d03bc-review` passed:
  non-root/read-only filesystem, no-exec temporary directory, startup/readiness,
  static UI, denied demo mutations and private SQLite initialization/permissions/
  overwrite refusal. Log `/tmp/es-d03bc-smoke.log`.

An independent reviewer verified the fixed candidate and reproduced closure of
both parser findings: the original Fifth Edition name rejection and Woodstox's
hidden 524,288-character attribute default. The corrected adapter accepts an
exact 1,048,576-unit source and refuses one unit more. No material graph, parser,
hardening, dependency packaging or source-build findings remain in that review.
The reviewer did not rerun the full Maven suite. See the separate
[graph evidence](d03b-projection.md) and [parser evidence](d03c-parser.md) for
actual RED/GREEN, independent oracles, three graph and nine clean writer/parser
guard mutants, archive adversaries and the recorded rework.

Lead integration/review coordination took approximately 5 minutes, including
the staging correction; parser feasibility experiments and worker effort are
recorded separately. No serial baseline exists for a parallel speed-up claim.
Line/branch coverage percentages were not measured. This local image has not been
uploaded; the previously recorded automatic approval-review rejection still
blocks GitHub delivery. No exact published release or HiveForge claim is made.
