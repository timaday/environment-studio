# D04 current-policy TLS addendum — 2026-09-09

This extends [the disposable operation-policy matrix](d04-disposable-read-operation.md)
with actual TLS transport evidence on the same newly owned, independently invented
lab. The original two-file utility/evidence candidate remains unchanged. This is
local development evidence, not private application or release approval.

The current compiler mechanism 2 and reviewed operation-policy adapters were used
against pinned PostgreSQL 18.6 and Oracle 23.26.3. Fresh independent CA/server
certificates include the intended localhost name. Private keys, CA keys and Oracle
wallets remain in private host/container RAM. Only the owned lab configuration was
changed. Shared runtime/JDK/orapki installations and global host/DNS configuration
were not changed. Oracle administration still uses local OS authentication; no
native SQLPlus password-authentication claim follows.

## Actual positive and negative evidence

| Case | Actual result |
| --- | --- |
| PostgreSQL `VERIFIED_TLS` owner, current compiler 2 | COMPLETE; exact original documents, independent committed state unchanged, confirmed cleanup and no remaining owner backend |
| Oracle `VERIFIED_TLS` owner, current compiler 2 | COMPLETE with the same source/identity/cleanup witnesses |
| Independent READ WRITE controls on both TLS connections | Changes actually commit; same DML under production `SqlRead.begin` read-only setup refuses |
| Exact old adapter and recognized old policy over TLS | Existing owner-purity refusal reached on both engines; not merely a new policy-name refusal |
| Independent root compile and owner rerun | COMPILE_0 and RUNNER_EXIT_0; both engine positive/control/cleanup/canary checks pass |
| Unrelated trusted CA, same account and endpoint | Both engines refuse; CLEANUP_INCONCLUSIVE / INCONCLUSIVE retained |
| Trusted server certificate with wrong requested hostname | Both engines refuse; CLEANUP_INCONCLUSIVE / INCONCLUSIVE retained |
| Owned Oracle diagnostics/audit directories and external result logs | Current-run credential/content canaries absent; Docker log drivers remain none |

The wrong hostname resolves to the owned loopback endpoint using a per-process
`jdk.net.hosts.file` containing only the invented test names. The certificate,
account and endpoint otherwise match the immediately preceding valid connection.
No global hosts file or DNS service was edited. Negative probes use an external
helper and the unchanged production `JdbcObservation` connection path.

A failed JDBC connect returns no acquired connection handle. The adapter therefore
retains inconclusive cleanup rather than inferring successful close. Independent
absence of an owner backend was recorded but never upgraded that result or released
its quarantined capacity. These expected refusals are not successful inspection
results and do not weaken the cleanup contract.

## Reproduction and qualification limits

Safe external logs: `compiler2-tls-owner-public-trust.log`,
`root-independent-tls-owners.log` and `compiler2-tls-negative-initial.log`; all three
actual runs exited 0. The first two use the frozen
`DisposableReadOperationQualification` runner in `owners` mode. The negative helper
uses the same fresh independently invented fixture family, with explicit valid,
wrong-trust and wrong-host cases for each engine. It has no production/runtime API.

Two setup failures precede this evidence: Oracle listener reload did not activate
its new TCPS endpoint, so the owned listener was restarted and its service
registered; an initially generated PKCS12 trust file encrypted its public certificate
bag under an empty password, yielding empty trust anchors for passwordless loading.
The final trust file contains public certificates only, with no private key and no
password-protected bag. An independent OpenSSL handshake observed a peer certificate
and successful CA validation. No TLS failure was relabelled as owner-policy RED.

This qualifies the current exact JDBC read statement path over verified TLS for
the two pinned mock engines. Account/side-effect/visibility/snapshot/cancellation
coverage remains detailed in the separate combined matrix. It does not qualify
native clients, private application inputs, arbitrary versions, every TLS deployment
configuration, certificate rotation/restart recovery or unbounded network stalls.
The two-day mock certificates are disposable lab artifacts, not deployable trust
material. Recreating this lab requires fresh independent credentials/certificates.
