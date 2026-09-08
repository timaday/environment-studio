# Security, state and lifecycle

| Data | Storage policy | Expiry / authority |
| --- | --- | --- |
| DB password/token | Operation-owned memory only; never environment variables, disk, logs or URL | Clear owned references and close physical connection on success/cancel/error/timeout; cleanup must be observed |
| Raw observation XML | Bounded principal/plan session memory | Proposed 30-minute idle expiry with warning; restart/expiry requires reinspection |
| Application secret / unknown value | Session only | Re-enter after expiry; unknown classification blocks export |
| Explicit non-secret binding | Save only where definition permits and UI discloses | Pinned plan revision; no silent classification downgrade |
| Value-free profiles/definitions | Versioned private owned workspace | Immutable published revisions; references prevent deletion |
| SQL export | User-requested protected artifact under explicit content policy | Existing release process owns downloaded copies |
| Audit metadata | Allowlisted safe IDs, counts, digests, outcomes | Not raw data; internal metadata is still access controlled |
| OIDC client secret / GHCR pull secret | Orchestrator secret facility if required | Separate platform credentials; not operator DB credentials |

Connection lifecycle: new operation → validate approved endpoint/options/TLS →
request supported authentication → consistent read → close physical connection.
JDBC readOnly is a hint, not authorization: qualify an actual least-privilege
read-only account. No shared credential-bearing pool or reconnect loop. A timeout
with unconfirmed session cleanup is UNKNOWN. Minimize immutable String copies
but do not claim guaranteed JVM/driver/OS memory erasure.

The final hosted mode must use real OIDC or an explicitly qualified trusted
identity integration, server-side sessions, Secure/HttpOnly/SameSite cookies,
CSRF and Host/Origin validation. Restrict proxy ingress and trusted forwarding;
never trust arbitrary X-User headers. Authenticate and authorize every plan,
observation, operation, profile and download by owner. Downloads use session
authorization with no reusable secret in query strings; return no-store.

One replica initially. Restart expires observations and active operations;
health recovery cannot restore prior validation authority. Persistent storage
must enforce principal ownership and metadata schema versions; no raw-data
backups. Do not mount a host Docker socket or allow arbitrary runtime plugins.
Use bounded DB destination allowlists and egress; uploaded XML/schema must not
cause network requests. Disable remote resolver access and debug payload logs.

The starter server only accepts `studio.mode=demo`; all mutation requests are
denied. Real authentication, storage and data access are D02/D04 work. Do not
change this boundary merely by adding `STUDIO_MODE=production` to deployment.
