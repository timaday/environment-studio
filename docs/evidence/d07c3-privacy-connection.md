# D07c3 private connection prerequisite

This is local development evidence for a private native prerequisite, not runtime
or installation qualification. Base: `dbb457ab408d808aced8bb3176e4677277629975`.
Only independently invented local processes, frames and paths were used. No
credentials, native database authentication, public flags or registry entries
were added.

The connection obtains the existing same-socket kernel process pin, transfers
one accepted socket from the listener to the wire owner, receives PREPARE, and
rechecks process liveness. It sends no CHALLENGE or ACK. The listener retains
its capacity and cleanup obligation until actual receiver close settlement.
Wire and peer cleanup are attempted independently. Earlier listener uncertainty
and completed close tombstones remain sticky; reused descriptor numbers are
never retried. Output overlapping owner storage is refused before writing.

The two private headers were reviewed by the lead before implementation. The
source candidate contains four production C/header files and four test files;
the ninth file is this evidence. No build manifest, existing peer/wire source,
JNI binding, launcher or public contract changed.

## Actual checks

Toolchain: pinned Maven 3.9.16 at
`/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn`,
Java 21.0.12 and GCC 13.3.0. Author archive:
`/home/tim/.tmp/es-privacy-connection-ikfrx7yw`.

Focused command, run from that archive:

```sh
/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn -B -ntp -f backend/pom.xml -pl tools/guarded-supervisor -am -Dtest=HostedPlanServiceTest,FifthEditionClassifierTest,HostedPlanApplicationTest,PrivacyConnectionTest,PrivacyListenerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- Initial `es-privacy-connection-red-20260909.log` contained a C test formatting
  compilation error and a listener assertion. The compilation error is setup
  failure, not behavior RED.
- Corrected `es-privacy-connection-red2-20260909.log`: three assertions failed,
  zero errors, exercising listener transfer and missing live/malformed connection
  behavior against the initial implementation stub.
- `es-privacy-connection-green1-20260909.log`: 22 passed.
- Expanded adverse tests initially had another test C formatting compilation
  failure (`adverse1`). After correction, `adverse2`: 28 passed.
- `adverse3` had one assertion caused by fault injection at the wrong phase: it
  changed the peer's first SO_DOMAIN check instead of wire initialization. The
  corrected test injects at the second check; production was unchanged.
  `adverse4`: 31 passed.
- `es-privacy-connection-alias-red-20260909.log`: one assertion, zero errors.
  An overlapping identity output erased owner state before refusal and skipped
  owned cleanup. The fixed overlap guard precedes output writes.
- `es-privacy-connection-final-focused-20260909.log`: 32 passed, zero failures,
  errors or skips: four core, one parser, two server, 25 supervisor cases.
- Full `mvn -B -ntp -f backend/pom.xml verify`, recorded in
  `es-privacy-connection-full-20260909.log`: **713 passed** (167 core, seven
  parser, 387 server, 152 supervisor), zero failures/errors/skips. Assembly and
  unrelated-directory hostile-environment launch passed; runtime remains
  unqualified.

All named logs above are external under `/home/tim/.tmp/`.

The ten connection test families invoke 26 C modes: live fragmented PREPARE;
malformed, duplicate, truncated, ancillary and absent input; sender death before,
after and during reception; cancellation and expired startup; wrong token,
reopening, repeated PREPARE and null output; listener-first cleanup; independent
wire/peer uncertain closes; expired cleanup; unsupported kernel pin and identity
mismatch; post-transfer wire setup failure; and three owner-output aliases.
The 15 listener families include its earlier controls and seven transfer modes.

Actual owned child processes create their own AF_UNIX connections. SCM_RIGHTS
rejection is checked with descriptor counts. Fragmented successful reception is
followed by EOF without any parent protocol output. A wrapped real recvmsg
control kills and waits for the owned child during PREPARE to check the final
pin guard. Close uncertainty controls actually close the descriptor and then
report EINTR once; subsequent descriptor reuse and repeat cleanup are checked.
These syscall injections supplement actual kernel operations.

Eight targeted mutations in the separate
`/home/tim/.tmp/es-connection-mutants-0q47yqri` archive each produced one assertion
failure and zero errors: omit transferred-slot clearing, release capacity during
transfer, remove the single active transfer guard, settle before receiver close,
skip peer close, omit the post-read pin check, ignore prior listener uncertainty,
and disable output-alias protection. `results.json` and individual logs record
those outcomes. All eight original file hashes were verified after restoration.

An additional external-only probe in
`/home/tim/.tmp/es-connection-extra-ph8025pi` compiled and passed against the
unchanged candidate. A thread signals the borrowed eventfd after 30ms while
PREPARE reception is blocked. It verifies CANCELLED, zero distinct identity,
complete owned close, and an unread/unclosed borrowed eventfd. This is additional
local evidence, not an extra repository JUnit case.

## Frozen identity and remaining boundaries

The eight source/test files are pinned by external manifest
`/home/tim/.tmp/es-privacy-connection-source-20260909.sha256`, SHA-256
`b7c9921f92f184d8bb9255cfe1bc52281fd1e1492141aa19986ea6466902acb7`.
The final nine-file manifest includes this evidence separately. Independent
fixed-candidate review and integrated verification belong to the lead.

Lead review accepted the exact nine-file candidate manifest
`/home/tim/.tmp/es-privacy-connection-candidate-20260909.sha256`, SHA-256
`9ddb6d9355cce76ca150f25ef7eb0474f9b721399aa7b3651ebc146cfd0f8162`.
Production ownership transitions, listener deltas and all new test source were
read independently. Three extra compiled controls against the unchanged source
passed in `/home/tim/.tmp/es-connection-independent-ovr28ysr`: read before PREPARE
refuses and closes both owned descriptors; an output beginning at the owner's
last byte refuses before writing; a cleanup deadline more than ten seconds away
closes resources but remains inconclusive on retry. Borrowed cancellation storage
survives these controls. No production correction was required by this review.

The exact candidate was overlaid onto `924c25e` in
`/home/tim/.tmp/es-connection-integrated-20260909`. Pinned Maven full `verify`
passed **713 Java cases**, zero failures/errors/skips, with assembly and hostile
launch checks; log `/home/tim/.tmp/es-connection-integrated-20260909.log`.
The eight source/test files remain identical to the author manifest; this lead
evidence addendum is subsequent documentation. Integration/review took about
eleven minutes while independent heap checks ran; no serial speed-up comparison.

PREPARE_RECEIVED is not ROOT_REGISTERED or privacy admission. Root matching,
ancestry, executable and loader identity, suppression receipt, subsequent wire
states, JNI ownership and production coordination remain unimplemented here.
The coordinator must preserve global refusal when any required connection fails;
this serialized per-connection owner cannot grant that authority. Listener
namespace admission remains a caller prerequisite: owner/mode checks do not
establish exclusive mutation against hostile same-UID processes. Only eventfd
signalling may be concurrent with owner operations. Checked clocks do not make a
stalled kernel syscall interruptible. No complete process-tree cleanup,
installation, same-UID threat or authenticated native-client qualification is
claimed by these checks.
