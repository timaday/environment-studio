# D06b3 hosted views integration — 8 September 2026

Eleven revision-bound POST routes now expose materialization, complete document/
entity/relation/draft/containment/placement views, explicit document disclosure,
portable profile capture, whole/partial preview and validation. Ownership,
inspection freshness, bounded admission and response transfer remain backend
decisions. Browser inspection and export capability flags remain false.

The author froze 30 files in `/tmp/es-d06b3-frozen.sha256`, manifest SHA-256
`df56b009279c36231a4b7bbcd3caa27e4e8b5098530ea6dbbb388caef824d436`.
The lead verified every hash, copied 28 files exactly, retained the current
schema-test superset and merged the single view-schema resource into the server
POM without losing the supervisor classifier or prior HTTP/package resources.
The Docker Java build adds exactly the invented view fixture directory.

Independent fixed-candidate review found no material issue. The reviewer ran
16 focused view/admission/capture tests and 21 schema tests successfully and
verified all hashes before/after. The author's separate 412-test reactor and
genuine mock OIDC/socket flows cover all eleven routes, real profile capture
limits, masking, one-to-two comparisons and owner quarantine/retry behavior.
Three authority mutants were killed and restored. See
[author evidence](d06b3-plan-views.md) for actual RED/GREEN runs, investigations,
test boundaries and the isolated-index provenance check.

## Integrated checks

- `docker build --target runtime -t environment-studio:d06b3-review --progress=plain .`:
  **450 Java tests passed** (134 core, 7 parser, 271 server, 38 supervisor), with
  zero failures/errors/skips. TypeScript/format checks, 7 component tests,
  22 schema tests and frontend production build passed. The supervisor assembly,
  inventory and unrelated-directory launch gates also passed in this combined tree.
- `bash scripts/container_smoke.sh environment-studio:d06b3-review`: protected
  non-root/read-only startup/static/health/demo denial, private workspace
  initialization, permissions/overwrite refusal and explicit schema-2
  upgrade/refusal all passed.
- Repository integrity, 11 Python script tests, staged-content/provenance review
  and whitespace checks run before commit. No database matrix is attributed to
  this view-only adapter integration; the observation implementation is unchanged.

The successful Java build took 111 seconds. Logs remain at
`/tmp/es-d06b3-integration-container.log` and
`/tmp/es-d06b3-integration-smoke.log`. The local runtime manifest is
`3f1252f59a9a74e092f07d808efd8165522b9c2a3eb79f2e7f408100c52dfb44`, with
image config `e9b72b857f3f5feb116af5f4771a928921a1fb95f44ee1566fc42ec5df323860`.
No serial baseline or parallel speed-up is claimed. This image is unpublished.

## Remaining local work

The browser workflow is being connected to these boundaries. Plan display
metadata, independently observed identity and the placeholder binding rail need
their follow-on contracts/data before the complete UX can be claimed. Maximum
retained/scratch heap, blocked-client response transfer and operational cleanup
retry/restart require additional local qualification.

Logout may quarantine an owner while a reader is still returning. The actual
test requires an internal cleanup retry after the worker stops before admitting
a new session. No HTTP retry or automatic downgrade was added. A response revoked
after commitment is truncated under its exact Content-Length; it cannot become a
complete JSON response with stale authority. This does not prove bounded recovery
from every slow or disconnected consumer.

Native supervisor/transaction qualification and hosted review/export/readback
remain incomplete. Actual application/IdP/PKI/HiveForge qualification remains
external. The authorized branch upload is still rejected by automatic approval
review with the session's Never setting; no new remote PR/CI/GHCR claim is made.
