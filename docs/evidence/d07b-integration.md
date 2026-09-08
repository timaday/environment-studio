# Transaction template integration — 8 September 2026

Both deterministic transaction templates are integrated as **unqualified internal
candidates**. PostgreSQL emits one guarded DO block; Oracle emits bounded loaders
and a complete guard with checked LOB cleanup. The server still cannot export an
executable package or launch a client. See [author evidence](d07b-transaction-templates.md)
for actual RED/GREEN, independent witnesses and the outstanding combinations.

The reviewed final nine-file manifest SHA-256 is
`5b4350543fb2f53072259416ca9bc2888759ef38fc3ba1e179240d1637a5026d`.
The combined 27-file D07a/b manifest is
`246c7abb4f33636188176f3c9b8e6d29641bc5a31a7820121706db40c9b8b0e2`.
The lead copied all nine additions exactly and added the new independent fixture
family to the Docker Java build context. No existing D07a mechanism, dependency,
database port or user-visible capability flag changed.

Independent review found a real boundary defect that five passing unit tests
missed: PostgreSQL interpreted the unquoted minimum signed int64 literal's cast
before unary minus and raised `bigint out of range`. The renderer now quotes the
canonical signed decimal before casting. Actual generated transactions for the
minimum, maximum, -17 and combined keys pass full expected-target and rollback
witnesses. The original minimum/combined candidates failed. The reviewer checked
the corrected frozen files, focused regression and actual native-client evidence;
no remaining material finding was reported.

The disposable template matrices exercise wrong destination, incomplete membership,
stale unchanged dependency, NULL/empty content, row-count/post-state faults,
unsupported effects and bounded lock refusal. Fifteen deliberately weakened SQL
guards exposed failures against independent oracles. Final full-size multibyte
trials cover 16 MiB original plus 16 MiB target: PostgreSQL 1.970 seconds, Oracle
15.767 seconds, including complete guards and Oracle LOB cleanup. Every guarded
attempt rolled back with independent original-state verification; none claims
commit acknowledgement. The three late Oracle catalog exclusions have absence
and access-refusal evidence, not instantiated feature qualification. The read-side
FGA correction is a separate work unit.

Actual integrated commands, based on `dcf7e11`:

```text
docker build --target runtime --build-arg SOURCE_REVISION=dcf7e11-local-d07b -t environment-studio:d07b-review .
bash scripts/container_smoke.sh environment-studio:d07b-review
```

The isolated build passed **362 Java tests** (**124 core, 7 parser, 231 server**),
zero failures/errors/skips; **7 component tests**, **17 schema tests**, TypeScript/
format checks and production frontend build also passed. Protected non-root/
read-only startup, static UI, health, demo capability/denial, private workspace
initialization and explicit schema-2 upgrade/refusal smoke checks passed. Logs:
`/tmp/es-d07b-integration-container.log` and
`/tmp/es-d07b-integration-smoke.log`.

Local runtime manifest:
`sha256:3912bb89f2a3d97cc63d2360f60d8b55a24c25b2e280977dd9a0176102991a96`.
Image config:
`sha256:828da079814a6b83d06141f5d4d129f4d4879cc8bd8d8abe25d1eab742601417`.
The local image was not published and does not replace the published starter
reference. External supervisor/runtime/TLS/prompt/framing/commit acknowledgement,
hosted export authority, all enumerated scalar/index/collation variants and actual
application/deployment qualification remain outstanding. Runtime configuration
contracts do not constitute those missing qualifications.

All new fixture values and expected XML were independently invented and registered.
The independent review covered provenance and the actual source/witness scope.
Integration used one combined build/smoke after the signed-key correction; no
measured serial baseline supports a parallel speed-up claim.
