# D01b integration — 8 September 2026

The native v2 compiler is integrated with D01a, D02a and D03a. It adds closed
logical/binding declarations and deterministic digests while preserving v1 draft
behavior. This is compiler readiness only: runtime v2 upload/publication, complete
observations, graph orchestration, profiles, planning and guarded export remain
separate. See [writer evidence](d01b-native.md) for meaningful RED/GREEN and the
independent digest oracle.

The writer's original 23-file manifest had SHA-256
`b238773e487c4a9d5c298cb4132b35865c3c288fca3c518b77e66555bb4ab264`.
Independent review reproduced readiness despite paths deeper than 128 or more
than 256 required attributes. Three correction files were integrated from manifest
`ed1218db55a6f730f85d26eb497e9de23486de8d019e1ad4ce9f4626a66ccbeb`.
The reviewer verified the fixed hashes and reran both original reproductions;
each now returns a publication blocker. Reference/namespace accounting and
boundary regressions were also reviewed, with no remaining material finding.

The lead verified all source hashes before copying, merged the v2 schema resource
include into the current OAuth-enabled server POM, and added the invented fixture
directory only to the Java Docker build/test stage. Fixtures are not runtime
resources. No new dependency or public endpoint was introduced by this slice.
The whole candidate's provenance review found only independently invented model
fixtures, generic contracts and tool implementation; no actual application inputs
or transformed private material were used.

Actual integrated checks:

| Command | Observed result |
| --- | --- |
| `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml -Dmaven.jar.forceCreation=true verify` | PASS: 55 core and 96 server tests, zero failures/errors/skips; 151 total |
| `PATH=/tmp/es-lead-toolchain/node/bin:$PATH node --test scripts/schema.test.mjs` | PASS: eight schema tests |
| `docker build --target runtime -t environment-studio:d01b-review .` | PASS: pinned Java/Node stages, frontend checks/tests/build and integrated Java tests |
| `bash scripts/container_smoke.sh environment-studio:d01b-review` | PASS: non-root/read-only startup, UI, health, demo capabilities and denial behavior |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | PASS: ten tests |
| OpenAPI parsing with Python PyYAML | PASS: eight paths parsed; this does not prove HTTP implementation |

Exact integration logs are `/tmp/es-d01b-integrated.log`,
`/tmp/es-d01b-container.log` and `/tmp/es-d01b-smoke.log`, outside the checkout.
The local image is a development proof, not a published release digest. Unchanged
browser behavior was not rerun; prior D01a browser evidence still applies only to
its synthetic inspector. No DB/client transaction or actual HiveForge evidence
is claimed here. G00 integrity/content/whitespace checks are required again on
the complete staged candidate before commit.

Integration took approximately 12 minutes including parallel contract preparation;
the engine's static-limit review correction took approximately five minutes.
These are overlapping wall times, not a measured speed-up. The authorized push
remains blocked by automatic approval review requiring approval while this
session's approval policy is Never. No new PR, remote CI or image publication ran.
