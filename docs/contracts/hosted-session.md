# Hosted identity and workspace boundary — D02

Implementation contract; hosted mode is unavailable until its application gates
pass. Actual identity-provider and HiveForge integration require separate
deployment evidence. Independent mock OIDC/session tests cannot qualify a real
deployment. Demo mode continues to accept no mutations or DB credentials.

## Runtime configuration

`studio.mode=hosted` requires one explicit approved HTTPS public origin and OIDC
issuer, client ID and platform-managed client authentication. Use the provider's
authorization-code flow with state/nonce and PKCE. Platform OIDC credentials
are separate from operation-owned DB credentials. Never log either. Discovery
and JWKS use the configured issuer only; uploaded definitions cannot affect them.

Fail startup for missing/invalid configuration, unknown modes or unsupported
trust settings. Do not silently fall back to demo, anonymous access or a mock
identity. Loopback HTTP is restricted to explicitly selected test configuration,
never a production default. The public origin has no userinfo, query or fragment.

Ignore client-supplied identity and forwarding headers. Initial deployment uses
an explicit external origin for redirect construction, with forwarding processing
disabled. The platform must restrict direct ingress to its trusted proxy and
provide TLS; documenting this requirement is not proof that it is configured.
Reject unexpected Host and unsafe-request Origin values. No permissive CORS.
The packaged process probe connects only to loopback at `SERVER_PORT` (8080 by
default) and sends the approved public-origin Host in hosted mode. Supply
`STUDIO_SECURITY_PUBLIC_ORIGIN` consistently to both service and probe; a missing
or malformed origin fails the probe. This does not exempt health URLs from Host
validation or route probe traffic through public DNS/TLS.

## Session and request authority

Use server-side sessions and Secure, HttpOnly, SameSite=Lax session cookies.
Rotate the session ID after authentication; do not expose provider tokens to the
browser. Bind workspace ownership to the authenticated issuer/subject pair, not
an email address, display label, cookie-supplied owner or request header.

Idle expiry is 30 minutes; absolute lifetime is eight hours. Logout and expiry
invalidate the session and its active operations/observations/validation state.
Restart discards all sessions and ephemeral authority. A fresh login can access
owned persisted metadata, but requires fresh observation and validation.
Revoke session authority before cleanup and attempt every independent session
and cleanup hook even if another fails. Hooks are idempotent. Failed obligations
remain typed INCONCLUSIVE without raw exception details; quarantine their owner
and global capacity until cleanup succeeds. Internal lifecycle retries attempt
only unfinished obligations, at most three total attempts per lease. They never
restore authentication. Exhausted obligations remain inconclusive until process
restart, and new owner login is refused with CLEANUP_INCONCLUSIVE. Quarantined
records count toward the same 64-session budget. Actual database operation and
restart cleanup still require separate G07 observations.

An internal work-completion notification may request that original retirement
cleanup resume. It never creates a new session or restores authority. If the
original cleanup attempt is already running, coalesce such notifications into
one pending retry after that attempt reports inconclusive. Consume the notification
when an attempt starts; completed cleanup needs no retry. Preserve the existing
three-attempt ceiling, commit-permit exclusion and explicit inconclusive state.
Ordinary cleanup-status reads and retry calls do not create repeated notification
loops. The plan adapter signals only after its original plan cleanup is confirmed,
so intermediate reader completions cannot exhaust the session retry allowance.
Initial application limits are 64 live sessions total and one live session per
owner. A new login for an already active owner is refused rather than silently
invalidating active work. Capacity is checked atomically before pending login
allocation and at authentication; pending logins count toward the global limit.

Require CSRF protection on every unsafe request, including logout. Provide the
authenticated session's CSRF token through a no-store same-origin API; it is not
stored in localStorage/sessionStorage. A wrong/missing token or wrong Origin
returns a safe 403. API requests without a valid session return safe 401 rather
than a login HTML redirect. Authorization checks apply to every object read,
mutation, operation poll and artifact download; an owner is never taken from a
request body. Cross-owner access returns a uniform safe refusal.

## Workspace state

Only explicit allowlisted metadata types can be persisted: compiled definitions,
value-free profiles and permitted non-secret metadata. Raw XML observations,
credential payloads, provider tokens, unclassified/secret target bindings and
generated SQL are never written to a metadata store or durable work queue.

Each stored object has an immutable owner, opaque tool-generated ID, monotonically
increasing decimal-string revision and canonical content digest. Commands include
expectedRevision and requestId. An exact retry returns its original bounded
result; a reused requestId with different content or stale revision conflicts.
After authentication and ownership checks, look up the owner/object-scoped
requestId before comparing the current revision. An exact replay therefore
returns the original result even after the object's revision has advanced.
Only a new request proceeds to the current-revision check. Credential-bearing
operations remain excluded: submit once, poll by operation ID, and supply new
authentication for a new inspection; never replay or durably queue credentials.
Bound replay history, object counts, payload sizes and session operations. If
capacity is exhausted, refuse explicitly rather than evicting active authority.
Initial metadata limits are 100 objects per owner, 32 immutable revisions per
object, 256 replay records per object, 2 MiB per stored record and 256 MiB per
workspace. Active inspection/export work is limited to one operation per owner
and eight globally. These are explicit conservative service budgets, not measured
application capacity. Exhaustion is a visible typed refusal; limits do not
justify partial inspection, deletion of retained revisions or silent truncation.
Persist the object catalog, metadata mutation and bounded replay result as one
atomic transaction.
Restart must not silently re-execute an applied command whose reply was lost.
An unavailable/expired replay record produces explicit conflict, never guessed
success or an automatic new mutation.

A file-backed metadata adapter, if selected, uses a configured private directory
outside the source/image and restrictive permissions. Validate its schema/version,
do not follow symlinks, use atomic writes, and refuse unsupported durability or
invalid/corrupt records. No catch-and-continue data-loss recovery. One application
replica owns the store; multi-replica access is outside the initial contract.

## First HTTP boundary

D02a implements authentication/session handling before any database input or
workspace mutation API. Its authenticated `GET /api/v1/session` response is a
no-store object containing `authenticated: true`, `csrfHeaderName`, `csrfToken`,
`idleTimeoutSeconds: 1800` and `absoluteExpiresAt` (UTC RFC 3339). The CSRF token
is intentionally available only to the authenticated same-origin UI; it is not
a provider token. No email, display name or provider credential is returned.

`POST /api/v1/session/logout` requires valid Origin and CSRF and returns 204
after complete cleanup. If cleanup remains inconclusive it returns safe 503
`SESSION_CLEANUP_INCONCLUSIVE`; authority remains revoked and capacity quarantined.
Login uses `/oauth2/authorization/studio`, with the fixed
public-origin callback `/login/oauth2/code/studio`. Callback state/nonce and token
issuer/audience/signature must be verified, not replaced by request claims.
Unknown API routes remain denied until implemented. D02a does not enable
inspection/export or mark metadata persistence and application qualification
complete. OpenAPI records these routes only when implemented with tests.

## Acceptance and qualification

Use an independently invented mock issuer and principals for application tests.
Exercise anonymous access, successful login/session rotation, invalid state/nonce,
forged identity/forwarding headers, unexpected Host/Origin, missing/wrong CSRF,
cross-owner reads/mutations/downloads, exact request retries, conflicting retries,
stale revisions, capacity exhaustion, idle/absolute expiry, logout and restart.

Trace synthetic credential/token canaries through errors and cancellation without
printing them. Assert absence from disk, logs, browser storage and response bodies,
and observe operation-owned cleanup. Test metadata atomicity/corruption/permissions
and immutable revisions independently. No real DB input is enabled until relevant
G07 application checks pass. G10 still needs actual TLS/issuer/proxy/storage and
platform lifecycle observations for the exact deployment candidate.
