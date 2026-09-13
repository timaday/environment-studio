# V3 computed-route image and supervisor artifacts

The committed application at `c3b891a8b8056c56870514dcc4cb5dbb99fe029c`
passes a full pinned OCI build and protected startup/workspace smoke. It includes
owned v3 creation/inspection/commands/materialization, physical/structural/binding
views, document/location routes and all five computed views. It includes the
three reviewed workspace, v2 cancellation and native arm-cleanup corrections.

The [container input correction](v3-container-contract-inputs.md) was required:
Docker now receives the v3 OpenAPI file used by the frontend contract tests.
The UI stage executes40 component/57 schema tests, checking and production build.
The Java step executes afresh and passes1579 tests (330core/7parser/
927server/315supervisor), zero failures/errors/skips, assembly and hostile-launch
checks,10 September2026 at10:42:13UTC. All base images and build packages retain
existing pins. No runtime dependency or supported workload limit changed.

## Recorded identity

| Artifact | Identity |
| --- | --- |
| Local development tag | `environment-studio:v3-computed-c3b891a8b805` |
| OCI index | `sha256:c8d47282cbc492c79ed96d9b3dc243843ea67781940b246f89ec3a1d30700987` |
| Linux amd64 manifest | `sha256:2fdc9c06d03e435dd4e2d964b9a3291d8b377b58b7be372afc6882e7a9ec7663` |
| Runtime config | `sha256:67643721100efbf74e266b6ed4e4e3ce84d71010564f94b5d42b4c554fcac966` |
| Supervisor ZIP SHA256 | `cc6bab5106e6ece4f6c079d5fecd1cba36feb7c1cb4643036c09ab946b0a328d` |

Docker's image ID here identifies the OCI index; the runtime config identity above
comes from the explicit build export record. The image source label exactly
matchesc3b891a and its configured user is10001:10001. External inspection record:
`es-computed-c3b891a-oci-inspection-20260910.json`; exact git source archive
`es-computed-c3b891a-oci-t9x1iru1`. The separately exported supervisor directory
and ZIP are under `es-computed-c3b891a-supervisor-artifacts`; every SHA256SUMS member passes.
Export reused the same qualified Java build layers.

## Actual smoke and remaining limits

`bash scripts/container_smoke.sh environment-studio:v3-computed-c3b891a8b805`
passes readonly rootfs, dropped capabilities, no-new-privileges, non-root startup,
static UI, readiness, demo capability/operation denial and private workspace checks.
Schema2 initialization, legacy offline upgrade, explicit schema3 upgrade/fresh
initialization and unchanged refusal cases pass. Workspace permissions remain
0700/0600 under10001. The harness uses only its newly created empty mock stores;
no managed configuration or credentials are supplied. Its own containers/volume
are cleaned up after verification.

The smoke's1GiB allocation proves startup only. It does not overturn the recorded
failure of1GiB on supported workloads or qualify combined maxima at6GiB. No new
operator browser, database/client, native privacy/admission, export/readback,
remote CI, GHCR publication or actual HiveForge qualification is claimed.
The actual compiler still refuses new v3 publication. This local development
image does not enable unqualified operational UI or export.

Build log `es-computed-c3b891a-oci-build-20260910.log` SHA
`2fe87c910958706fc1bd806ebdc73be129454005536321381129436a2e0de93f`.
Smoke log `es-computed-c3b891a-oci-smoke-20260910.log` SHA
`45222001bbf6ae154f65907b4a7ae509fbbea3cb9f92222837a341726644261b`.
Actual `python3 scripts/release_readiness.py` still exits1: no matching source
fingerprint and all eleven capability gates NOT_RUN. No release evidence status
was changed. Exact-revision CI/upload remains subject to the previously recorded
automatic approval rejection; no alternate publication path was attempted.

The preceding8ad1e6a document-route image remains historical evidence in this
file's Git history. Later uncommitted API and native contract work is excluded
from the c3b891a source archive and image.
