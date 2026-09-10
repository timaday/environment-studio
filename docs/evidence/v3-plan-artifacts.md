# V3 document-route image and supervisor artifacts

The committed application at `8ad1e6a16eefc3c1c846ea9c9a0a0f286a1ab2ae`
passes a full pinned OCI build and protected startup/workspace smoke. It includes
owned v3 creation/inspection/commands/materialization, physical/structural/binding
views and document/location routes. The separately prepared computed HTTP
candidate is not part of this committed-source image.

The [container input correction](v3-container-contract-inputs.md) was required:
Docker now receives the v3 OpenAPI file used by the frontend contract tests.
The actual preceding UI-stage execution passed40 component/55 schema tests,
checking and production build; that identical UI layer is cached in the full
build. The Java step executes afresh and passes1549 tests (324core/7parser/
908server/310supervisor), zero failures/errors/skips, assembly and hostile-launch
checks,10 September2026 at09:47:28UTC. All base images and build packages retain
existing pins. No runtime dependency or supported workload limit changed.

## Recorded identity

| Artifact | Identity |
| --- | --- |
| Local development tag | `environment-studio:v3-document-8ad1e6a16eef` |
| OCI index | `sha256:f038a250aff3ea83d59a743e68f716901a9b9a145df82a6316f6daa49a9eb6ce` |
| Linux amd64 manifest | `sha256:40aca4a1ab5c7ebeb4dbacd2311219c3b0bcef492eaeeb06c0868eb49380e13e` |
| Runtime config | `sha256:122b32a7d8bd5a5a4c6233781403269486d70e491a3e4c24bbeec92d9f53d252` |
| Supervisor ZIP SHA256 | `de9c958e2031d68f31f2de280be8692dbc719a23b1bcb86b71746394b451dd31` |

Docker's image ID here identifies the OCI index; the runtime config identity above
comes from the explicit build export record. The image source label exactly
matches8ad1e6a and its configured user is10001:10001. External inspection record:
`es-v3-document-runtime-oci-inspection-20260910.json`; source archive
`es-v3-document-runtime-oci-gqoa8s5k`. The separately exported supervisor directory
and ZIP are under `es-v3-document-supervisor-8ad1e6a`; every SHA256SUMS member passes.
Export reused the same qualified Java build layers.

## Actual smoke and remaining limits

`bash scripts/container_smoke.sh environment-studio:v3-document-8ad1e6a16eef`
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

Build log `es-v3-document-runtime-oci-build-20260910.log` SHA
`89d0d4c6af95f7aa00a8dab3f1b066739b049ed9236fd37f34687bcf1ecf32e8`.
Smoke log `es-v3-document-runtime-oci-smoke-20260910.log` SHA
`45222001bbf6ae154f65907b4a7ae509fbbea3cb9f92222837a341726644261b`.
Actual `python3 scripts/release_readiness.py` still exits1: no matching source
fingerprint and all eleven capability gates NOT_RUN. No release evidence status
was changed. Exact-revision CI/upload remains subject to the previously recorded
automatic approval rejection; no alternate publication path was attempted.
