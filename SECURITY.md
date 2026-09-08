# Security model

This public starter contains synthetic data only. Demo mode has no database
operations. All non-read API operations are denied by the server security
filter; enabling real operations requires the D02 authentication/session gate.

Database credentials are transient inputs to one operation, never environment
variables or deployed secrets. Existing integrated/short-lived identity is
preferred when supported. Custom encryption inside the JVM does not eliminate
the clear credential needed by the driver; cleanup is best effort, not proof
of memory erasure. Application secrets in XML require a separate export policy.

Hosted deployment requires TLS, real authentication, per-principal ownership,
CSRF/Origin protection, destination restrictions, bounded sessions and tested
isolation. A reverse proxy user header is not trusted merely because it exists.
No wildcard CORS; no uploaded executable expressions, remote schema resolution,
arbitrary JDBC URL flags or outbound probes to endpoints found in XML.

Log allowlisted metadata/error codes only. Disable payload/access-body logging,
heap dumps and core dumps on operational deployments. Orchestrator OIDC/pull
secrets are distinct from operator database credentials and remain in the
platform's secret store. See `docs/contracts/security-and-state.md`.

If a credential is accidentally committed, revoke it through the existing
security process. Removing a working file or making a later commit does not
remove earlier copies. Never publish vulnerability details containing secrets
in a public issue.
