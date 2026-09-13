# D04 JDBC TLS qualification — 8 September 2026

The existing observation adapters passed an isolated TLS matrix on independently
invented databases. This adds transport evidence; it does not enable the browser,
qualify native psql/SQL*Plus execution or approve external accounts/deployment.
No production adapter change was needed.

| Combination | Actual checks |
| --- | --- |
| PostgreSQL 18.6, pgJDBC 42.7.13, certificate-only PEM | Verified hostname/root; wrong hostname, unrelated CA, missing/corrupt trust, missing intermediate and no default-root fallback |
| Oracle Free 23.26.3, ojdbc17 23.26.3.0.0, JKS | Same six adverse cases with explicit truststore and hostname verification |
| Oracle Free 23.26.3, ojdbc17 23.26.3.0.0, passwordless PKCS12 | Same six adverse cases; public certificate entries only |

All **18 adverse cases**, **42 successful controls** and **three independent
default-trust controls** passed. Successful observations compared both complete
mock XML records, including Greek/supplementary characters, against independent
expected strings and checked credential closure and backend-session absence.
Each adverse case ran in a fresh JVM because refused connection attempts retain
their physical-operation quarantine permits. Repeated checks of the 3,982-entry
Oracle vendor-grant baseline are not counted as independent TLS cases.

The adverse result was `CLEANUP_INCONCLUSIVE` with a retained `INCONCLUSIVE`
cleanup handle: the driver attempted connection but returned no owned connection
whose closure could be established. Independent administrator inspection found
no reader backend session. That witness does not promote the adapter's cleanup
result to Complete or permit automatic credential retry.

## Independent controls and review

Before and after each refusal, the normal root-trusting destination successfully
read exact XML. For each missing-intermediate variant, the server presented a
leaf without its intermediate. A client supplied with the intermediate then
successfully read exact XML on that same altered endpoint both before and after
the roots-only client's refusal. This excludes listener unavailability as the
explanation for the refusal. Original chain/wallet settings were then restored.

For each default-trust case, a separate direct driver connection proved that the
configured default trust could connect. The adapter then refused an explicitly
unrelated destination trust file despite that usable default. The PostgreSQL
control used an owned temporary client home; Oracle controls used JVM truststore
properties. These settings existed only in the disposable qualification JVMs.

Independent review identified two gaps in the first runner: a restarted listener
needed the altered-endpoint positive control, and reading helper output before
waiting for process completion was unbounded. Both were corrected before the
final matrix. Helper input/output now run in immediately owned workers under a
20-second deadline with bounded output, termination and joins. An actual silent
`/bin/sleep 30` helper was stopped within the bounded deadline. A killed local
Docker helper does not establish remote SQL cleanup; the runner says so explicitly.
The reviewer verified the corrected source and all three altered-endpoint logs
and found no additional material issue. The final matrix and final account/
configuration restoration were subsequently run by the lead.

All lab-created reader accounts, including one left by an earlier failed truststore
setup, were disabled. Independent PostgreSQL/Oracle reader-session counts were
zero. The PostgreSQL certificate bytes matched the original leaf plus intermediate;
both Oracle listener configuration files referred to the original wallet. Separate
root-only TLS handshakes to both restored endpoints passed. Existing qualification
databases and their accounts were not changed.

## Candidate and reproducibility

The runner used the frozen D06b2 Boot artifact's production classes and drivers.
Its observation, Oracle metadata and driver-logging source hashes matched the
integrated repository. The Java runtime was Ubuntu OpenJDK
`21.0.12+8-1-24.04-Ubuntu`, Linux amd64.

Database images were pinned to:

- `postgres:18.6-bookworm@sha256:1c59e2c3c818eaa0f0628f695b36e7c9e362d6b219b36a54a32df645cbd7e1af`
- `gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`

The generated root/intermediate/leaf and unrelated CA were independent test
certificates. Private keys and database bootstrap secret files stayed in external
memory-backed storage; observation credentials existed only in process memory
and administrator input. No private material or configuration entered the checkout
or image. The production credential/configuration contract was not relaxed.

Oracle's slim image lacked the Java/PKI runtime needed for wallet preparation.
The isolated lab used Temurin `21.0.12+8-LTS` from the pinned Maven build image and
the official `oraclepki:23.26.3.0.0` JAR, SHA-256
`3e180d09700e56f167052c160b3a16ce5a953455187cc60986a853109fff2356`.
This is lab setup, not a qualified supervisor installation. Passwordless PKCS12
truststores used unencrypted public certificate entries and no MAC; the test JVM
set those creation properties explicitly. An initial encrypted-store attempt
failed, and was not counted as a passing product case.

Actual command: `python3 /tmp/es-tls-controlled-final-matrix.py`.
Runner source SHA-256:
`a2c089798fcb3e53d47ba705464eb9a5b6655397bc4b015c7deee69fff62a46a`.
Matrix script SHA-256:
`f221a5d8bca44bf61c0baa33f179eafa59b2a37b6b9bf2ff33e5d8c77ba7bcef`.
Final summary log SHA-256:
`aad76ee500a89860903cc6546761c8c8b3ab6a67429e0122ed7a23bd049e6dcb`.
Individual `jdbc-controlled-final-*.log` files and the source remain in the
external local qualification directory. These local records are not CI or
HiveForge evidence. Native clients, other TLS formats/versions, real PKI/accounts
and external deployment remain unqualified.
