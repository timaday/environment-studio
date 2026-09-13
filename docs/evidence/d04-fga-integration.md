# Oracle FGA guard integration — 8 September 2026

The Oracle read adapter now refuses every enabled FGA policy on the selected
table before reading source rows. Missing complete catalog access refuses as
METADATA_UNAVAILABLE; present enabled policy refuses as VISIBILITY_UNQUALIFIED.
This closes the identified read-policy gap. It does not claim protection against
concurrent privileged policy changes or qualify hosted credentials/TLS.

The four-file reviewed author manifest SHA-256 is
`9169c91207fa0f322e298a1e01378f88cf9210f84e98f60b697ce72e02d092bc`.
All four files were copied exactly. Independent review ran the three focused
tests and inspected the actual Oracle witness evidence with no material finding.
The lead also added the one required DBA_AUDIT_POLICIES grant to the existing
disposable reader setup; that test-only integration change received separate
review. The broader old matrix was not rerun merely to check this grant-list edit.

[Author evidence](d04-fga-guard.md) records the observed three-assertion RED,
restored guard mutant and **42 actual Oracle assertions plus final cleanup**.
An enabled SELECT policy's harmless handler actually changed session CLIENT_INFO
under account READ ONLY in an independent direct-read control. The adapter
refused before the source barrier for that policy, a handler-free policy and an
UPDATE-only policy. Denied catalog access refused; disabled/removed policies and
clean tables returned complete exact mock XML. Newly invented owner/reader
accounts were locked and their sessions absent after the qualification.

The final integrated command was:

```text
docker build --target runtime --build-arg SOURCE_REVISION=3573cdb-local-fga -t environment-studio:fga-review .
bash scripts/container_smoke.sh environment-studio:fga-review
```

The isolated final build passed **365 Java tests** (**124 core, 7 parser,
234 server**), zero failures/errors/skips. The unchanged frontend layer reuses
the previously passed 7 component/17 schema tests, checks and production build.
Protected non-root/read-only startup, static UI, health, demo denial/capabilities,
private workspace initialization and explicit schema-2 upgrade/refusal smoke
passed. An initial build/smoke preceded the test-only grant-list edit; the final
rebuild reran the full reactor and produced the identical runtime manifest/config,
so the protected smoke still applies to exactly those runtime bytes.

Logs: `/tmp/es-d04-fga-integration-final-container.log` and
`/tmp/es-d04-fga-integration-smoke.log` (initial build log is
`/tmp/es-d04-fga-integration-container.log`). Runtime manifest:
`sha256:8c60dc91642e171e6d5c1c2155b10c3cd2aedd4211bd723e879cf901807b6b13`.
Image config:
`sha256:0a423c137a0c7c8d24fd6a1628a4976c4e883039c8aacd93f8f50528e9e11401`.
No image was published and no inspection/export flag was enabled.

New mock SQL/XML, accounts and handler behavior were independently invented.
The tests contain no private model or persisted runtime credentials. The exact
FGA guard and existing required policy checks remain additive; no PUBLIC/vendor
baseline, other database object or managed application write path was changed.
