# PostgreSQL 16.11 guarded package operator handoff

Status: local reviewed-candidate handoff for the narrowed 12 September delivery.
This is not production export qualification, GHCR publication, HiveForge deployment
or proof of live database execution.

Candidate branch: `implementation/v3-postgres16-guarded-package-route-20260912`.
Runtime route code was introduced by `83b7afd522660359e844f2cecf7a522234d5959b`;
later amendments add the OpenAPI route contract, local exact-image evidence, this
handoff and the opt-in local PostgreSQL 16.11 supervisor/client witness. Exact
published heads are tracked in issue #9.

## Supported today

Environment Studio can prepare an **unqualified guarded package candidate** for a
hosted V3 plan whose trusted server-side destination configuration is exactly
PostgreSQL `16.11`, psql `16.11`, linux-amd64 and template
`postgresql16-text-v1`. The plan must already have a current inspection, complete
target values and a validation result whose input fingerprint matches the request.
The application does not take destination, client, server, template, binding,
policy or publication fields from the browser request.

The downloaded ZIP contains the deterministic `es-guarded-package-v1` members:
`manifest.json`, `payload.json`, `transaction.sql` and `instructions.txt`. The SQL
is generated from the admitted package execution context, includes the PostgreSQL
16.11 guard and does not contain `COMMIT` or `ROLLBACK`; transaction control remains
owned by the separately qualified supervisor workflow.

## Operator request shape

Use the application session's normal authenticated HTTP client and CSRF handling.
The request body is closed JSON with only the pinned plan revision and current
validation fingerprint:

```http
POST /api/v3/plans/{planId}/package-candidates/guarded
Content-Type: application/json
Accept: application/zip

{"revision":"{pinnedRevision}","inputFingerprint":"{validationInputFingerprint}"}
```

A successful response has `Content-Type: application/zip`, `Cache-Control: no-store`,
`X-Environment-Studio-Qualified: false` and downloads the package as an attachment.
Record the independently reviewed archive SHA-256 outside the package before any
separate supervisor trial.

## Required refusal examples

The route refuses before streaming when any required authority is missing or stale:
wrong owner, V2/nonexistent plan, stale revision, missing inspection, incomplete
target, mismatched validation fingerprint, destination physical-identity mismatch,
unsupported client tuple, protected-document policy denial or package-admission
failure. The application keeps `exportAvailable=false` after package download.

## Evidence available

- Focused route/package checks: 45 Java tests pass.
- Full backend Maven verification passes on corrected candidate `0f81c0c9d5ee1657ab4671d3c4f1c2721a9aadd6`.
- Exact-source runtime image from fresh `git archive` passes G08 build and protected
  container smoke. Local image:
  `sha256:a333641de0567e70f90ff74fef406451155bec60ab1e6ef5887d241332c9db0f`.
- Supervisor artifact export from the same final archived source verifies the ZIP
  and all 29 `SHA256SUMS` entries. ZIP SHA256:
  `7e47aea26110db349981f22b56ee31dc951423b11552211ad4fbd8f233fea576`.
- Detailed evidence: [v3 PostgreSQL 16 guarded package route](v3-postgres16-guarded-package-route.md).

## Local supervisor/client witness

A separate opt-in local witness now exercises the generated PostgreSQL 16.11
transaction through the guarded-supervisor Java `ClientProtocol` and real `psql`
16.11 inside `postgres:16.11-bookworm`. It proves the invented C/UTF-8 disposable
database path for commit acknowledgement, pre-program rollback acknowledgement and post-COMMIT unknown classification;
see [PostgreSQL 16.11 guarded supervisor client witness](postgresql-1611-supervisor-witness.md).
This does not qualify production TLS, a production client installation or a live
customer database.

## Still required before production use

Independent review found PKG-QA-001 against effective code candidate
`4a824555ed8988fee9508c04af8f764b3cc78ac5`; the package transfer failed to keep
`ViewScope` in pinned-authority mode after parsing the package request. The lead
accepted the finding in issue #9 and corrected it locally with focused
regressions for invalidation before output bytes and after the first output
write. The TEST-QA-012 witness cleanup correction records owned Docker volumes,
removes each witness container with volumes and verifies owned resources are
absent. The corrected immutable candidate `0f81c0c9d5ee1657ab4671d3c4f1c2721a9aadd6`
still requires independent verification before merge or release use.

Live PostgreSQL client execution, TLS/client identity, supervisor commit and
rollback fault behavior, GHCR publication, HiveForge deployment and production
definition qualification remain separate evidence gates. Do not describe this
candidate as release-ready or production-qualified until those gates pass for the
same source and image.
