# Owned V3 payload prerequisite

The lead implemented and self-reviewed this bounded addition after the user
retired the reviewer and assigned all work to the lead. No independent acceptance
is claimed. Base: `d3f4ae3f3d47c912e1bf40b74dc58034c9077c1d`.

An executing, pinned plan admission now supports payload preparation against an
explicit validation fingerprint. Fresh validation rechecks publication and the
original lease. The existing cancellable read verifies authority after payload
generation. It returns the same unqualified payload type; no HTTP export,
client qualification or publication authority is introduced.

Actual author checks on 11 September 2026:

- RED: three new behavioral tests failed against the unavailable stub, with zero
  test errors. Expected candidate and specific conflict/publication refusals were
  absent.
- GREEN: the three new tests passed. Actual shared owner, projection and
  materialization fixtures establish byte equality; mismatched fingerprint,
  abort, publication replacement and lease closure during lookup refuse. The
  caller closes its reservation and can subsequently validate again.
- Affected combined Maven checks: 57 passed (8 core, 7 parser, 42 server), zero
  failures/errors/skips. Includes admission, validation, review authority/proof
  failures, actual XML payload and package assembly tests.

Self-review covered original-session ownership, stale publication and fingerprint
handling, cancellation, resource cleanup ownership, disclosure and dependency
direction. No source content enters diagnostics. The method cannot establish
that required validation checks passed: its candidate remains unqualified even
when client capability is UNKNOWN. Final export admission and aggregate generation
memory accounting remain unfinished. There is no production enablement claim.

HiveMap `environment-studio` graph and scan history were read successfully during
review. Its historical D07 notes distinguish native prerequisites from release
qualification. Historical parser documentation findings were checked against the
current qualified Woodstox matrix rather than reopened. This was a bounded
read-only aid to self-review, not a new full scan or independent review.

External logs: `/home/tim/.tmp/es-owned-payload-red-20260911.log`,
`/home/tim/.tmp/es-owned-payload-green-20260911.log` and
`/home/tim/.tmp/es-owned-payload-affected-20260911.log`.
Fixed candidate `fa50f3ee3b8600381d7e7252285a5becc61ada39` subsequently passed
the full host Maven `verify`: 1,724 tests (335 core, 7 parser, 1,039 server,
343 supervisor), zero failures/errors/skips. Log SHA-256:
`2c74b5e9021fd9469d8020ea7719b0b7a5e1630156cc5d06b9cd4a963afda221`.
The external `es-owned-payload-full-results-20260911.json` records module counts.
Two externally compiled guard-removal controls were detected: skipped fingerprint
comparison and skipped fresh publication validation. Both fixed-source controls
pass; production source was unchanged. Results are in
`/home/tim/.tmp/es-owned-payload-mutants-20260911/results.json`.
The fixed four-file self-review manifest and report are in
`/home/tim/.tmp/es-owned-payload-review-20260911/`.

Prior OCI qualification still applies only to exact source `6574e9e`, not this
addition. No new browser, OCI, GHCR, HiveForge or release acceptance is claimed.
