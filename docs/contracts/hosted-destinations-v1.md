# Hosted destination configuration — operation policy revision 2

This is trusted operator configuration, supplied outside the checkout and image.
It connects [hosted plans](hosted-plans-v1.md) to the existing
[read-only observation adapters](database-observation.md). A browser may select
an authorized ID only. Configuration is neither a publication nor proof of
database identity, read-only operation enforcement, TLS or export readiness.

`studio.plans.destinations` is an ordered list of at most 32 closed entries. For the PostgreSQL 16.11 pilot, an authenticated operator may also add an owned runtime destination through `POST /api/v1/destinations` using only a JDBC URL target. The runtime destination stores host, port and database metadata only; username and password remain operation-scoped inspection credentials and are never stored.
Bind properties strictly, following the existing workspace publisher boundary:
unknown fields, duplicate destination IDs or owners within one destination,
missing fields, malformed scalar values or
noncontiguous list indices fail startup with a safe code, without input echoes.
Do not silently ignore an invalid destination or fall back to another one.
A configured private workspace and hosted mode are required for plan services.
An absent/empty configured destination list is valid for the PostgreSQL 16.11
pilot because operators can add a PostgreSQL target at runtime. Demo never
accepts credentials.
The normalized entry has exactly these fields (Spring's standard kebab-case
property spelling is equivalent to the listed Java names):

| Field | Required value |
| --- | --- |
| `id` | Tool ID, `[a-z][a-z0-9.-]{0,63}` |
| `engine` | `postgresql` or `oracle` |
| `host` | Exact ASCII DNS name or IPv4 address supported by the D04 adapter, 1–253 characters; no URL, credentials or descriptor syntax |
| `port` | Integer 1–65535 |
| `database` | Exact database/service token, `[A-Za-z0-9_]{1,128}` |
| `trustMaterial` | Absolute, normalized, readable regular certificate-only trust-file path; no symlink or credential/key material |
| `transportIdentity` | 64 lowercase hex characters identifying independently approved endpoint/trust policy; not derived from the current connection |
| `expectedPhysicalIdentity` | Exact closed engine-specific object below, obtained independently before inspection |
| `provisioningPolicyVersion` | Nonempty tool-safe ASCII policy identifier, at most 256 characters; letters/digits and `.-:_` only |
| `operationPolicyVersion` | Exact supported D04 policy identifier below |
| `owners` | 1–64 exact `{issuer, subject}` pairs; no roles, wildcards or groups |

Both owner strings are nonblank, valid scalar Unicode, without control characters.
Issuer is an absolute HTTPS issuer URI, at most 2,048 code points, with no
userinfo, query or fragment; subject is at most 1,024 code points. Compare both
exactly with the authenticated Owner. An explicitly injected disposable test
composition may use its existing loopback mock issuer, never a hosted runtime
configuration switch. Rechecking owner admission on create is required even when
the destination previously appeared in a list. Request/replay IDs confer no access.

PostgreSQL identity is exactly `systemIdentifier`, `databaseOid`, `databaseName`.
The first two are canonical positive decimal strings of at most 20 digits;
databaseName is the exact nonempty observed name, at most 128 code points.
Oracle identity is exactly `dbid`, `dbUniqueName`, `conId`, `conUid`, `conName`,
`pdbGuid`. The numeric fields are canonical positive decimal strings of at most
20 digits; both names are nonempty scalar strings of at most 128 code points;
pdbGuid is exactly 32 lowercase hex characters. All identity strings reject
control characters. Extra/missing engine fields refuse, not infer another engine.

`operationPolicyVersion` is exactly `postgresql-read-operation-v1` or
`oracle-read-operation-v1` for the selected engine. Revision 2 replaces the retired
`accountPolicyVersion` field and its identifiers. Reject old/unknown fields and
policy values at startup; do not translate them or reuse old observation evidence.
This is an explicit breaking external configuration revision, with no store migration.
Configuration pins the required closed read-operation policy; the adapter verifies
actual transaction, visibility, identity and cleanup evidence. It does not require
an account without write privileges or a pristine vendor PUBLIC-grant digest.

Configured hosted destinations use VERIFIED_TLS: full certificate-chain and
hostname validation, with no plaintext flag, arbitrary JDBC property bag,
trust-all mode, wallet credential or URL. `trustMaterial` contains public trusted
certificates only and is mounted read-only. Bound it to 1 MiB and require a
nonempty trust set. The read-only mount is deployment/composition evidence;
POSIX write bits alone cannot establish it, since a read-only bind mount may
retain host mode 0644. PostgreSQL uses a PEM certificate bundle. Oracle uses a
certificate-only JKS (`.jks`) or PKCS12 (`.p12`/`.pfx`) trust store readable
without a supplied password by the pinned Java/driver runtime. Require the
actual format to agree with this lowercase suffix and the driver's effective
type resolution; an arbitrary suffix must not select a different implicit type.
Reject key entries and unsupported/unreadable formats. See Oracle's
[automatic keystore type resolution](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjdbc/client-side-security.html).
No trust-store password enters configuration or an operation. Exact TLS
interoperability and refusal of an untrusted/wrong-host certificate must be
qualified separately.

Runtime PostgreSQL 16.11 pilot destinations are operator supplied from a JDBC URL
and use the closed `OPERATOR_SUPPLIED_PLAINTEXT` transport. The URL stores only
host, port and database. Username and password are supplied later for the single
read-only inspection operation. The adapter observes PostgreSQL physical identity
during inspection and uses that evidence for validation/export checks; no caller
may provide identity, driver properties, TLS flags or credentials through the
destination-create request.

The existing explicit disposable-loopback constructor remains test-only and is
not expressible through these properties or any HTTP request.

The composition root constructs configured ObservationDestination/JdbcObservation
ports and owner filters. A PostgreSQL 16.11 pilot request may create an owned
runtime destination from exactly `requestId` and `jdbcUrl`; it must be
`jdbc:postgresql://<host>:<port>/<database>` with no userinfo, query string,
fragment, driver properties or credentials. Runtime destination creation is
idempotent for the same owner and normalized URL, adds no export authority by
itself and never alters a configured driver. Configuration changes still require
restart, which revokes old leases/plans. The destination list response exposes
only id, engine, host, port and database;
owner lists, trust paths and expected identity/provisioning records stay server-side.
No startup validation connects to a database or asks for credentials. Missing
workspace yields the safe services-unavailable response; an empty configured
destination list is allowed so the operator can add a PostgreSQL connection in
the UI. Configured but malformed entries fail startup.

## Inspection capability response

`GET /api/v1/capabilities` distinguishes browser availability from configured
backend composition. These booleans are required and have separate meanings:

| Field | Meaning |
| --- | --- |
| `inspectionUiEnabled` | The operator inspection UI is qualified and available for the PostgreSQL 16.11 pilot when hosted workspace services are composed. |
| `inspectionApiConfigured` | Hosted mode and an enabled private workspace compose the inspection service. False in demo or when that composition is absent. The destination list may initially be empty in the PostgreSQL 16.11 pilot. |
| `inspectionEnabled` | Deprecated compatibility alias of `inspectionUiEnabled`; always equal to it. It never represented per-request server authority. |

`inspectionApiConfigured` is a configuration diagnostic, not a qualification,
connectivity or owner-admission result. It exposes no destination or owner data,
does not connect to a database, and can be true while the UI remains disabled.
An explicitly configured hosted API can accept an inspection only through its
existing live-lease, owner/destination, revision, one-shot credential, read-operation,
transaction, TLS, identity and cleanup gates. Neither true nor false UI state
replaces those checks. Ordinary write-capable accounts remain supported through
the closed read-operation policy; no write probe or grant revocation is required.

The browser uses `inspectionUiEnabled` only to expose inspection controls; it
must not infer per-request authority from API configuration. Missing/nonboolean
fields or a conflicting compatibility alias make its capability response
unavailable. New clients require these fields; old clients can continue reading
the alias. For the PostgreSQL 16.11 pilot, `qualifiedDatabaseAdapters` contains
only the `postgresql`/`16.11`/`psql`/`16.11`/`postgresql16-text-v1` tuple when
the hosted workspace is available; Oracle remains unavailable.
