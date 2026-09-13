# Derived target and inspection clarification — exact artifact refresh

Candidate `7111230ac3c59aea94c2f8cb1bf1c5f5dfa9b2f0` includes the reviewed
[derived target integration](qf34-derived-target.md) and
[capability clarification](inspection-capability-clarification.md). These local
checks use separate clean Git archives; subsequent profile WIP is excluded.

## OCI and standalone distribution

`docker build --target runtime --build-arg SOURCE_REVISION=7111230ac3c59aea94c2f8cb1bf1c5f5dfa9b2f0`
passes as `environment-studio:target-7111230ac3c5`. The build runs Java894 and
frontend40/schema32 with checking and production build. Log:
`/home/tim/.tmp/es-target-oci-build-20260909.log`; archive
`/home/tim/.tmp/es-target-oci-8u_o3s87`.

| Identity | SHA-256 |
| --- | --- |
| OCI index | `eabf02b76e4986fd84007253e4434a418f551260d8eecab49118306bee581125` |
| Linux platform manifest | `06530742ccb9b33a1ef47b69f06c28338bcb17adedba21792517565c6feafcd0` |
| Configuration | `b4cb394656d75ad8dcf41cb7651e54acecd6e8a338c754b3f5c5354fc3625502` |
| Standalone supervisor ZIP | `b9749d411889a107a5d116497dbf66d01caa4a692843cd85eee33e209caeea49` |

The source revision label matches and the runtime user is `10001:10001`.
`bash scripts/container_smoke.sh environment-studio:target-7111230ac3c5` passes
startup/static UI/readiness, demo denial and all three explicit inspection-field
assertions under read-only rootfs, dropped capabilities and no-new-privileges.
Private workspace initialization, permissions, overwrite refusal and schema
upgrade/refusal checks pass. Log: `es-target-oci-smoke-20260909.log`.
The same archive's `supervisor-artifacts` target exported the ZIP above.

## Browser behavior

The separate clean archive `/home/tim/.tmp/es-browser-7111230-yzlelo3r` passes
four hosted HTTPS/OIDC cases at 1440×1000 and 390×844, including maintainer
save/publication, lost-reply recovery, plan creation, inspection, comparison,
keyboard/axe and operator publication denial. The observation port remains the
explicit independently invented mock. The harness intercepts UI capability
booleans for this flow; production capabilities remain disabled.

Runner: `python3 /home/tim/.tmp/es-run-browser-7111230-20260909.py 7111230 all`.
Log: `es-d08a-browser-7111230-20260909.log`; four pass in 6.8s. TLS/workspace
and browser result output stay in RAM; trace/video/screenshots are disabled.
The owned harness exits after requested termination with `MOCK_CLEANUP_COMPLETE`
and no inconclusive cleanup marker. Its bounded log-canary checks pass.
`npm run test:e2e` on the same archive also passes both demo keyboard/accessibility
cases; log `es-browser-7111230-demo-20260909.log`.

Initial browser preparation tried a server-only dependency-classpath goal without
installed sibling snapshots and failed dependency resolution. The corrected
reactor `package dependency:build-classpath` runs the selected core/parser/server
tests and resolves its actual artifacts. Logs `es-browser-7111230-setup-20260909.log`
and `es-browser-7111230-setup2-20260909.log` retain both outcomes. No dependency
version or runtime gate was changed.

These results establish local artifact and browser behavior for this revision.
They do not qualify database/native-client execution, combined maximum workloads,
the complete operator workflow, remote CI, GHCR publication or HiveForge. Startup
smoke at 1 GiB remains separate from workload capacity measurements. No GitHub
upload or deployment occurred.
