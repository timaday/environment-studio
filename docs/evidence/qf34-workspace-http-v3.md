# V3 definition draft and historical HTTP integration

Reviewed local implementation, 9 September 2026. Four closed hosted routes join
the actual v3 compiler, immutable history and explicit schema3 private workspace.
See the [HTTP contract](../contracts/workspace-http-v3.md) and
[OpenAPI](../contracts/openapi-workspace-v3.json). V1/v2 meanings are unchanged.
This adds no v3 publication, profile mutation, plan admission or release readiness.

## Candidate and verification

Original author13 used base `5925bc8b396237de901745370e3743c7c50e0962`; its manifest
SHA-256 is `b2de118012891c1074477af3e7abcaaef4111c6f8b4eb1b7b1ebcd99c81e062c`.
After independent review, corrected14 includes the unchanged lead regression
file; its manifest is
`1d9edf0fdd985e7dab1802eae2357c569b5483c2c891beb0111b8de832266248`.
The lead integrated those14 with the five contract/build files onto
`f90051dade82429221dd161d6715d5061e781374`, retaining native argument verification.
The resulting19-file manifest is
`175eab9bbd401e929df24a8d21e331cee9d00711f2ca6de735db05754442c437`.
All19 hashes matched after verification. Subsequent documentation changes only
record implementation status and this evidence; they do not change behavior.

Full `mvn -B -ntp -f backend/pom.xml verify` passed **1024 tests**:261 core,
7 qualified parser,561 server and195 standalone supervisor, with zero failures,
errors or skips. Maven3.9.16/JDK21.0.12 completed in3m04s at19:50:33 BST.
Supervisor assembly checksums and unrelated-directory hostile-environment launch
also passed. This was an owned external archive, not reused root build output.
Frontend `npm ci`, checking,40 tests and production build passed separately;
the revised contract shape suite passed36. No UI behavior changed.
The lead reviewed the complete selected diff for invented provenance and cumulative
disclosure; repository integrity, staged content guard, Python11 and whitespace
checks passed before commit. The pattern guard is not proof of generic provenance.

External reproduction records are under `/home/tim/.tmp`: integrated archive
`es-v3-http-integrated-hrfkiau1`, manifest
`es-v3-http-integrated-candidate1-20260909.sha256`, and full log
`es-v3-http-integrated-full1-20260909.log`. Author and correction evidence hashes:
`93ee5579e24e59267c75ca43996115ccbc95d09c3dbae7a8e814948ba40bdcc9`
and `073d666d510ac6f7f9ef6b00fec5b9f7001a579945682567ee0bb324a182a5ed`.
These records use independently invented native-v3/native-v2 fixtures and mock
identity infrastructure. No private application structure or credentials were used.

## Meaningful RED, review and adverse controls

The actual initial save test failed one assertion: expected200, unavailable
scaffold503. Separate adverse REDs found acceptance of a60-second transfer clock
and persistence after a startup timeout; both were corrected under the original
30-second bounds. Final original focused60 passed. Setup compilation errors and
one logout scheduling error are retained in the external author record; they are
not behavior RED or product failures. The corrected logout test waits for actual
request admission before asserting mid-operation revocation.

Independent lead review added three tests and reproduced two assertion failures,
zero errors, against the frozen original implementation:

* Checked IOException from request input acquisition escaped the safe response
  boundary. It now returns safe503 where possible, drops the cause and retains
  the original cleanup path.
* A previously computed COMPLETE notification could arrive after terminal
  INCONCLUSIVE and erase the retained operation obligation. Settlement now latches
  terminal uncertainty under the same registry lock as completion. The regression
  uses a registered mock lease and a controlled callback schedule. It is an
  internal lifecycle defect, not a demonstrated hosted exploit.
* The positive control revokes the original lease while obtaining output. No
  source is disclosed; the already committed command remains revision1 and replays
  exactly after reauthentication through the actual runtime cleanup composition.

The author reproduced both review failures unchanged and added an adjacent
unsupported-cycle race. That test first failed one assertion because onStartAsync
alone did not notify uncertainty while an older COMPLETE notification was paused.
The completion owner now reports terminal uncertainty immediately after worker
closure, without another completion attempt or waiting in a callback. The lead
reviewed these corrections and the added test; no material finding remained.

Corrected focused64 passed, including all three independent tests and seven real
HTTP cases. Six original isolated mutants each compiled and failed one assertion:
encoding cap, encoding wipe, startup timeout, operation capacity, output authority
and completion refusal. Their restored30 passed. Three correction mutants also
compiled and each failed one assertion: checked-I/O handling, terminal latch and
unsupported-cycle notification. Restored34 passed with all14 hashes unchanged.
The six original mutants were not relabelled as rerun against the correction.

The seven actual loopback Tomcat/mock-OIDC cases cover exact save/edit/replay and
history; owner/CSRF/Host/Origin/route isolation; partial-body logout; unread output
disconnect, logout and original30-second deadline; four blocked outputs, fifth429
and subsequent recovery. Test-only8192-byte send and1024-byte receive buffers
create backpressure with a legal source prefixed by600000 invented tabs. DEBUG
capture asserts absent source and synthetic provider canaries. These are actual
local socket tests, not deployed TLS, real IdP or browser qualification.

## Scope and remaining work

Business: operators can retain exact v3 definition source and inspect historical
results. Historical-ready describes stored data only; the current compiler still
returns incomplete. Engineering: neutral draft commands enter the versioned
application/store; session authority remains in Java. Four process-owned HTTP
operations include body reads, compilation/storage, bounded encoding, transfer
and unsettled cleanup. Callback notifications cannot renew authority or release
another operation. QA: stale completion, revocation, backpressure, replay,
malformed input, schema2 refusal and cross-version isolation have actual controls.

Owned returned body arrays and response chunks are wiped; immutable JVM strings,
codec trees and transport buffers are not claimed zeroized. Already admitted
bytes may remain in flight after revocation. Uncertain completion retains its
capacity and lease obligation. There is no atomic network delivery or assertion
that filesystem operations are interruptible.

New OCI evidence, deployed TLS/identity, combined maximum-resource measurement,
v3 profiles/publication/plans, native execution/readback and current CI/GHCR/
HiveForge evidence remain separate work. The retained5925 image and7111230 browser
results predate this change. No GitHub upload was attempted in this slice.
