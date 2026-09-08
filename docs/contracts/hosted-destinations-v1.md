# Hosted destination configuration — planned D06b2

This is trusted operator configuration, supplied outside the checkout and image.
It connects [hosted plans](hosted-plans-v1.md) to the existing
[read-only observation adapters](database-observation.md). A browser may select
an authorized ID only. Configuration is neither a publication nor proof of
database identity, least privilege, TLS or export readiness.

`studio.plans.destinations` is an ordered list of at most 32 closed entries.
Bind properties strictly, following the existing workspace publisher boundary:
unknown fields, duplicate destination IDs or owners within one destination,
missing fields, malformed scalar values or
noncontiguous list indices fail startup with a safe code, without input echoes.
Do not silently ignore an invalid destination or fall back to another one.
An absent/empty list leaves plan services unavailable. A configured private
workspace and hosted mode are also required; demo never accepts credentials.
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
| `accountPolicyVersion` | Exact supported D04 policy identifier below |
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

PostgreSQL accountPolicyVersion is exactly `postgresql-read-only-v1`. Oracle is
`oracle-account-read-only-v2:` followed by the independently approved pristine
PUBLIC-grant baseline SHA-256 (64 lowercase hex characters). Configuration pins
the expected policy; the adapter must still verify all actual evidence. Policy
labels never replace the verifiable read-only account and visibility checks.

Hosted transport is fixed to VERIFIED_TLS: full certificate-chain and hostname
validation, with no plaintext flag, arbitrary JDBC property bag, trust-all mode,
wallet credential or URL. `trustMaterial` contains public trusted certificates
only and is mounted read-only. Bound it to 1 MiB and require a nonempty trust set.
The read-only mount is deployment/composition evidence; POSIX write bits alone
cannot establish it, since a read-only bind mount may retain host mode 0644.
PostgreSQL uses a PEM certificate bundle. Oracle uses a certificate-only JKS
(`.jks`) or PKCS12 (`.p12`/`.pfx`) trust store readable without a supplied password
by the pinned Java/driver runtime. Require the actual format to agree with this
lowercase suffix and the driver's effective type resolution; an arbitrary suffix
must not select a different implicit type. Reject key entries and unsupported/
unreadable formats. See Oracle's
[automatic keystore type resolution](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjdbc/client-side-security.html).
No trust-store
password enters configuration or an operation. Exact TLS interoperability and
refusal of an untrusted/wrong-host certificate must be qualified separately.
The existing explicit disposable-loopback constructor remains test-only and is
not expressible through these properties or any HTTP request.

The composition root constructs immutable ObservationDestination/JdbcObservation
ports and owner filters. No request creates a destination or alters its driver.
Configuration changes require restart, which revokes old leases/plans. The
destination list response exposes only id, engine, host, port and database;
owner lists, trust paths and expected identity/provisioning records stay server-side.
No startup validation connects to a database or asks for credentials. Missing
workspace/destinations yields the safe services-unavailable response; configured
but malformed entries fail startup. A capability flag remains false until its
actual operation, privacy and qualification gates are complete.
