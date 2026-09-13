# AJV development dependency — 8 September 2026

The pinned AJV dependency changed from 8.17.1 to 8.20.0. Only that package's
direct pin, resolved tarball and integrity changed; no transitive package changed.
AJV is used by repository contract checks, not by the application server.
The checks do not enable `$data`.

An actual `npm audit --prefix frontend --json` reported one moderate advisory,
[GHSA-2g4f-4pwh-qvx6](https://github.com/advisories/GHSA-2g4f-4pwh-qvx6), before
the update. The upstream [8.18.0 release](https://github.com/ajv-validator/ajv/releases/tag/v8.18.0)
records its fix; [8.20.0](https://github.com/ajv-validator/ajv/releases/tag/v8.20.0)
also explicitly supports the repository's Node 24. No vulnerable-input timing
experiment or production exploit claim was made.

The lead ran a fresh `npm ci` in `/tmp/es-ajv-qualification`, using Node 24.
`npm run check`, `npm test`, `npm run build` and `npm audit --json` passed there:
7 component tests, the then-current 16 schema tests, and zero audit findings.
The independent reviewer verified the exact package-map diff and installed
8.20.0, and reran all 16 schema tests successfully. Later, the integrated Docker
build passed the expanded 17 schema tests, 7 component tests and production build
with the same updated lockfile. The audit is point-in-time dependency evidence,
not complete application security qualification.

Local logs: `/tmp/es-ajv-{ci,check,tests,build}.log`,
`/tmp/es-ajv-audit-{before,after}.json` and
`/tmp/es-d07a-integration-container.log`. No credentials, actual configuration or
new fixture data were introduced. The existing remote dependency PR was not
merged or modified; publication of the integration branch remains separately
blocked by automatic approval review.
