# Combined Reuse artifact verification

Exact source: `505f815552d4439220cc17e5c1ce120cff0df535`, combining the
approved Reuse renderer, original-command recovery and verified payload ancestry.
Lead checks and self-review only; no new independent acceptance is claimed.

On 11 September 2026 the full host Maven run passed 1,724 tests: core335,
parser7, server1039, supervisor343, with no failures/errors/skips. The log is
`/home/tim/.tmp/es-reuse-completion-full-maven-20260911.log`, SHA256
`cda1518a638cb65fb46531ac29a24a6c3eb8685b04b2c52fef2b907934878761`.

The exact-source runtime Docker build passed the same 1,724 Java tests and
443 frontend/61 schema tests. Its protected container smoke passed static UI,
health, demo capability, denied hosted operations, private workspace permissions,
overwrite refusal and explicit schema1/2/3 initialization/upgrade cases.
The matching supervisor-artifacts target reused the tested build and all29
distribution checksums passed. No native production-admission claim follows.

Local image identity:
`sha256:3e7d022da656ae6b6a02b479064857c5369d67ecd61ed0c3969662fab5cfd8a6`.
Exact commands, module totals and log hashes are recorded externally in
`/home/tim/.tmp/es-reuse-completion-oci-20260911/results.json`.
The artifact is local; no GHCR push or HiveForge qualification occurred.

The final actual HTTPS/OIDC Reuse browser run8 passed desktop and narrow with
fresh Java classes from this source: preview, explicit placement, intentionally
lost successful response, original-command retry, revision readback, unchanged
sibling exclusion, accessibility checks, logout and complete owned cleanup.
Its records are `/home/tim/.tmp/es-v3-reuse-renderer-browser8-20260911/result.json`.
Create-item command shape has component coverage; this browser run did not
execute creation. Visual differences remain as described in v3-reuse-renderer.md.

These results apply to the stated source and environment. The subsequent
Validation callback correction has separate frontend evidence and is not part
of this image. Production publication, native/client/resource closure, remaining
operator flows, combined release gates and operator rehearsal remain open.
