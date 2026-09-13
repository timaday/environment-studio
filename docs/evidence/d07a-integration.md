# D07a and plan-contract integration — 8 September 2026

The bounded package admission, canonical byte/digest and deterministic ZIP
mechanisms are integrated. They cannot generate or authorize an executable
artifact: inspection still returns Unqualified and generation is unavailable.
Both transaction templates and the external supervisor remain subsequent work.
See [author evidence](d07a-guarded-package.md) for observed RED/GREEN, six restored
guard mutants and the precise mechanism boundary.

The fixed 18-file author manifest was independently reviewed and byte-verified:
`e71e059df6a0b888192f217ea766dfd0626ebcc73d4fb3b11a15e54bfc7ccb2d`.
The reviewer ran all 15 package test methods under a 768 MiB heap and both Python
byte/digest oracles; all passed. The lead copied 17 exact additions and merged
only the two guarded-schema resources into the server POM. Docker's Java build
now includes both independently invented guarded fixture families. No new runtime
dependency or database connection path was added.

The planned [initial hosted-plan HTTP contract](../contracts/hosted-plan-http-v1.md)
and closed command schema also received independent review. Findings resolved:
retired-plan replay/status precedence, small-body streaming limits/deadlines,
non-touch status polling, and bounded batch upsert so large drafts do not require
one retained request ID per entity. The batch schema test first failed with an
actual false-versus-true assertion, then passed after the closed variant was
added; malformed/implicit-default examples refuse. Routes remain unimplemented
and denied. Maximum-shape partitioning and hosted heap evidence are still required.

The root integrated candidate used the standard parser source-root correction
and updated [AJV pin](ajv-update.md). Actual commands:

```text
docker build --target runtime --build-arg SOURCE_REVISION=4ccc4f5-local-d07a -t environment-studio:d07a-review .
bash scripts/container_smoke.sh environment-studio:d07a-review
```

The isolated Docker build passed **317 Java tests** (94 core, 7 parser, 216
server), **7 component tests**, **17 schema tests**, TypeScript/format checks
and the production frontend build. Protected non-root/read-only startup, static
UI, health, demo capability/denial, private workspace initialization and explicit
schema-2 upgrade/refusal smoke checks passed. Logs:
`/tmp/es-d07a-integration-container.log` and
`/tmp/es-d07a-integration-smoke.log`.

Local runtime manifest:
`sha256:e3c4e0320960ef0e8dcf742bd24b2b56408fd5905e887a1cf7ec15fe01eec7c3`.
Image config:
`sha256:973d8cba9a440b6bbc6d43844fa71fe7a099f6b3987c030e875341af781554f4`.
This local development image was not published and is not a release candidate.
The published starter image reference is unchanged.

The separately reviewed [Oracle transport refinement](../contracts/oracle-transport-v1.md)
follows the measured base64 feasibility result without changing payload bytes,
scope or the transaction deadline. That probe is not SQL guard qualification.
No frontend workflow, hosted credential route, template generation, TLS/client
fault matrix, deployment or actual application qualification is claimed here.
All newly copied fixtures have reviewed independent provenance. Integration
included shared-contract correction and one combined build/smoke; no measured
serial baseline supports a parallel speed-up claim.
