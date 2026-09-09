# D06b4 — image and hosted browser refresh

## Response recovery refresh at c4a3246

Exact source: `c4a324636d4aeaa20012349e68cce7eb2b025f8b`, including the reviewed
response/session recovery correction and native fork/listener prerequisites.
Separate fresh `git archive` trees supplied the OCI and hosted browser checks.

`docker build --progress plain --build-arg
SOURCE_REVISION=c4a324636d4aeaa20012349e68cce7eb2b025f8b
-t environment-studio:c4a3246-review-20260909 .` passed Java683
(core167/parser7/server387/supervisor122), supervisor assembly/checksums and hostile
launch checks. The unchanged frontend layer was cached; fresh host checks using
Node24.20.0 passed frontend34/schema24, TypeScript and production build.
An earlier invocation had a mistyped source-label argument and was stopped before
acceptance. It is not an artifact result. The final inspected source label equals
the exact commit above.

Docker-inspected image ID:
`sha256:64176f7b650f23e25d2bf1759e450f8305984edc788350e75c626c7ec20f0b75`.
Extracted app.jar SHA-256:
`5fca94d00704caf57814c9ee8bf2d3a01491381121b1484d65a8bfd0b546dddf`.
`bash scripts/container_smoke.sh environment-studio:c4a3246-review-20260909`
passed protected startup/static UI/health/demo refusal and private workspace
initialization, permissions, schema2 and explicit offline legacy-upgrade checks.
Logs: `/home/tim/.tmp/es-c4a3246-oci-build-20260909.log` and
`/home/tim/.tmp/es-c4a3246-container-smoke-20260909.log`.

The separate supervisor artifact is
`/home/tim/.tmp/es-c4a3246-supervisor-artifacts-20260909/environment-studio-guarded-0.1.0-SNAPSHOT.zip`.
ZIP SHA-256: `2c0205111b0800bdda0e2e9cfa54cc2f6724b24603f6fad16ec97a173e8f5404`.
The runtime registry remains empty; the included prerequisites do not qualify
native authentication, effective crash privacy or execution.

Fresh reactor `test-compile dependency:build-classpath`, `npm ci`, frontend checks,
tests/build and the actual local HTTPS/OIDC browser harness passed. Runner:
`python3 /home/tim/.tmp/es-run-browser-c4a3246-20260909.py c4a3246 all`.
All four desktop1440x1000/narrow390x844 maintainer/operator cases passed in7.0s,
including keyboard/accessibility and credential/log/storage canary controls.
Observation remains an independently invented mock port. TLS/workspace and browser
outputs stayed in RAM, without trace/video capture. Owned harness cleanup was
COMPLETE, without force kill or an inconclusive marker.
Log: `/home/tim/.tmp/es-d08a-browser-c4a3246-20260909.log`.

Actual blocked-output HTTP evidence is in
[response recovery](d06b7-response-recovery.md). These functional browser and
startup checks do not establish maximum whole-process heap, TLS backpressure,
the full operator workflow, native-client qualification or external deployment.
The 1 GiB deployment allocation remains inadequate for the demonstrated legal
draft workload. Whole-hosted HTTP scope qualification is continuing separately.
No new GitHub CI, GHCR publication or HiveForge result is claimed.

## Observed-context refresh at 1bda699

Exact source: `1bda69993ff1257b063d08a357fd2b260d8be385`, including the reviewed
observed-destination and live summary-publication correction, retained independent
transfer regressions, native peer primitive and root-capture contract.
All builds used a fresh `git archive` of that commit.

`docker buildx build --load --progress=plain --build-arg
SOURCE_REVISION=1bda69993ff1257b063d08a357fd2b260d8be385
-t environment-studio:1bda699-review-20260909 .` passed Java623
(core161/parser7/server365/supervisor90), frontend34/schema24, production UI build,
supervisor assembly/checksums and hostile launch checks.
Log: `/home/tim/.tmp/es-1bda699-oci-build-20260909.log`.
Inspected image ID:
`sha256:4932ea4452240df0570803e35c98a393fcce48fbc6ef71f2651766a64f1e7c71`.
Its source label exactly matches the commit above.

`bash scripts/container_smoke.sh environment-studio:1bda699-review-20260909`
passed protected startup/static UI/health/demo refusal and the included private
workspace initialization, permissions, schema2 and explicit offline legacy-upgrade
checks. Log: `/home/tim/.tmp/es-1bda699-container-smoke-20260909.log`.

The separate `supervisor-artifacts` target exported
`/home/tim/.tmp/es-1bda699-supervisor-artifacts-20260909/environment-studio-guarded-0.1.0-SNAPSHOT.zip`.
ZIP SHA-256: `9186713530eb5014fef278092d7b4d29daf384aa61fe6049a2333c5822b66176`.
Its runtime registry remains empty; this artifact does not qualify native
authentication, crash privacy or execution.

Fresh exact-source reactor `test-compile dependency:build-classpath`, frontend
`npm ci`/production build and the actual local HTTPS/OIDC browser harness passed.
Runner: `python3 /home/tim/.tmp/es-run-browser-1bda699-20260909.py 1bda699 all`.
All four desktop1440x1000/narrow390x844 maintainer/operator cases passed in 6.5s,
including their keyboard/accessibility, exact credential/log/storage canaries and
disclosure controls. Observation is an independently invented mock port. Owned
TLS/workspace/browser outputs stayed in RAM; trace/screenshots/video were disabled.
Harness cleanup was COMPLETE, with no force kill or inconclusive marker.
Log: `/home/tim/.tmp/es-d08a-browser-1bda699-20260909.log`.

The 1 GiB Compose allocation remains unqualified for maximum hosted scope. These
startup and functional checks do not establish heap/blocked-transfer recovery,
full operator workflow, native-client qualification or external deployment.
No GitHub CI, GHCR publication or HiveForge result is claimed for this refresh.

## Earlier bindings refresh at 19be08f

Exact tested source: `19be08f57ff0a3b1366d4b660f7f0c6b220ba179`.
This includes reviewed stable handles, bindings/locations/placeholders, explicit
raw-coordinate disclosure and the buffered servlet completion correction. The
later native peer primitive and observed-identity candidate are excluded.

`docker build --target runtime --build-arg SOURCE_REVISION=19be08f57ff0a3b1366d4b660f7f0c6b220ba179
-t environment-studio:d06b4-review-20260909 .` passed the pinned build's Java590,
frontend34/schema23, production UI build, supervisor assembly/checksums and hostile
launch checks. Log: `/home/tim/.tmp/es-d06b4-oci-build-20260909.log`.
Docker reports image ID
`sha256:d342960f82c5b854377e8bc3e09c7cd66609e6c05fdc22316a8026fb029de3e9`;
the inspected source label exactly matches the commit above.

`bash scripts/container_smoke.sh environment-studio:d06b4-review-20260909` passed
startup, static UI, health, demo capability/refusal, non-root/read-only runtime and
the included private workspace initialization/permissions/schema2/legacy-upgrade
checks. Log: `/home/tim/.tmp/es-d06b4-container-smoke-20260909.log`.

The separate `supervisor-artifacts` target exported
`/home/tim/.tmp/es-d06b4-supervisor-artifacts-20260909/environment-studio-guarded-0.1.0-SNAPSHOT.zip`.
ZIP SHA-256: `3f0e40443ba99c48581098de1c39aa357501f144ae14a8a54ad4e661a27ba043`.
It is a development artifact with an empty runtime registry; this
build does not qualify native authentication, crash privacy or execution.

Four actual hosted browser cases passed from a fresh exact-commit archive:
maintainer save/publish/inspect/review and operator draft/publication refusal, each
at desktop1440x1000 and narrow390x844. The existing keyboard/accessibility, exact
credential/log/storage canaries and disclosure controls passed. The harness runs
actual local HTTPS/OIDC and workspace persistence with an explicitly invented
observation port, not JDBC or deployment PKI. Trace/screenshots/video are disabled;
owned temporary outputs and TLS/workspace state stayed in RAM. Cleanup reported
COMPLETE, with no force kill or inconclusive marker.

The first standalone Maven classpath attempt could not resolve uninstalled reactor
artifacts; that was setup failure. The exact archive's reactor `test-compile
dependency:build-classpath` succeeded. Its frontend was built and served by
HostedBrowserHarness. Runner:
`python3 /home/tim/.tmp/es-run-browser-19be08f-20260909.py d06b4-19be08f all`.
Browser log: `/home/tim/.tmp/es-d08a-browser-d06b4-19be08f-20260909.log`.

No GitHub CI, GHCR publication, HiveForge deployment, full remaining operator
workflow or new UI design is established by this local refresh. Missing qualification
continues to block the affected release capabilities.
