# D06b4 — image and hosted browser refresh

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
