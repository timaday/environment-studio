# D07c3 privacy investigation — credential-free local evidence

The reviewed shared contract and private ABI specify the next implementation.
They do not establish effective runtime privacy or authorize a native DB client.
Main's registry remains empty; the existing terminal/runtime prerequisites still
refuse the current piped-collector environment. No production suppression code
was added by this investigation.

The external scratch is `/tmp/es-private-receipt-7o7je7e_`. It contains invented
control programs and public runtime tools only. No credentials, database
authentication, shared installation, global collector/kernel or configuration
change occurred. Owned runners completed and stopped.

## Observations that changed the design

- A late JNI per-thread seccomp installation denies reset1/reset2 in its calling
  thread, but an already-existing Java thread resets dumpable to1 successfully.
  That thread immediately restores0. A fresh-JVM TSYNC control returns EPERM for
  both resets in both threads. EINVAL from an unsupported reset2 is not filter
  proof. This is actual host evidence, not a qualification of every kernel/JVM.
- The private Unix-domain prototype gets kernel SO_PEERCRED PID through a narrow
  JNI experiment and correlates live owned ancestry. Direct/setsid/orapki control
  chains produce1/2/7 receipts with unchanged merged native output. The orapki
  `version` argument is unsupported: both ordinary and protected command exit255
  with identical output SHA-256
  `3a41e703de5f0bfa4afdb6797e1dc19e4d2242723f6afd958c1b9ed20ff4db3f`.
  This proves transport compatibility, not wallet creation or client readiness.
- Missing-library and static controls execute without a receipt. Exit success
  therefore cannot admit privacy. Actual AT_SECURE and the full wrong-peer,
  challenge, deadline, cleanup and diagnostic matrices were not run.
- Root independently confirmed that reading an owned child's `/proc/<pid>/exe`
  fails with PermissionError after dumpable0. The contract consequently requires
  blocked PREPARE image verification before suppression, then challenge-bound
  establishment; a suppressed secret-bearing process is never reset to1.
- Source inspection and actual credential-free strace show the default JDK
  POSIX_SPAWN route executes java→jspawnhelper→requested program. The prototype's
  child environment did not protect the intermediate helper. Fixed FORK removes
  that extra exec: direct2 total execs/1receipt; setsid3/2; orapki8/7. The final
  merged output remains unchanged. Some descendant trace paths become pointer-only
  after suppression (one setsid and six orapki rows); those rows do not prove exact
  executable identity. See `fork-trace-results.json`, `fork-exec-counts.json` and
  `trace-*.log` in the external scratch.

## Reviewed contract and limits

The shared contract was independently reviewed at SHA-256
`422ad6de0918c8402cdd9203b37343477922be8d1efd47d72305b04145baff5e`.
The lead independently reviewed the final private ABI, SHA-256
`26f7eac30522cd2fa55a2c2ddcfe9ea8d0a2402d4cea6a08e66bd0a2bd8968f4`,
from `/home/tim/.tmp/es-privacy-abi-3wbdcqya`. Its one-file manifest is
`/home/tim/.tmp/es-privacy-abi-candidate3-20260909.sha256`, SHA-256
`129f02781d654e1881e030af7291eeba047dfac436d0c61adc7f65fa70e245d9`.

Review corrected simultaneous orapki pipeline handling: one active handshake
does not mean one pending peer. It also required exact script/argv association,
fresh inspection on repeated exec within one PID, listener lifetime through
cleanup, explicit parent/child library roles, cumulative bounds and protection
against alternate syscall forms. Control tuples never contain native output or
credentials. The fixed launcher candidate uses FORK, whose actual memory cost
must be measured; no fallback or new public option is allowed.

The experimental bridge uses a JDK-internal descriptor field and ancestry-only
admission. It does not implement the reviewed native descriptor/pidfd ownership,
compiled executable/loader/script closure, two-phase proof or complete adverse
matrix. Implement and independently test these boundaries before native TLS,
transcript, COMMIT and crash/diagnostic qualification. No release-evidence status
was changed, and no external publication or deployment is claimed.
