# D07c2 original pause — historical WIP

This records the unchanged 8 September pause. For the reviewed 9 September
terminal continuation and actual integration results, read
[the continuation evidence](d07c2-terminal-continuation.md).

Paused at the user's request on 8 September 2026. Do not treat this tree as a
qualified runtime, final candidate or passing build. The ordinary qualification
registry remains empty. Original C1 worktree and frozen candidates are unchanged.

## Resume point

Worktree: `/home/tim/.tmp/es-d07c2-native-runtime`, base
`c074cfa70918e66be990851332c861ac58fc7dde` plus the lead's C1 overlay and merged
shared packaging/schema files. The lead also supplied the missing C1 public PEM,
strict provenance correction and fixture README. No shared contracts, POMs,
Git state, global configuration or database objects were changed by this C2 author.

C2 author files:

- `backend/tools/guarded-supervisor/docs/terminal-abi-v1.md`
- `backend/tools/guarded-supervisor/src/main/c/terminal-control.c`
- `backend/tools/guarded-supervisor/src/main/java/studio/environment/supervisor/RuntimePrivacy.java`
- `backend/tools/guarded-supervisor/src/main/java/studio/environment/supervisor/Main.java`
- `backend/tools/guarded-supervisor/src/build/java/Assemble.java`
- `backend/tools/guarded-supervisor/src/test/java/studio/environment/supervisor/RuntimePrivacyTest.java`
- `backend/tools/guarded-supervisor/src/test/java/studio/environment/supervisor/TerminalHelperTest.java`
- `backend/tools/guarded-supervisor/src/test/resources/terminal-helper-probe.py`
- This handoff.

## Actual progress and limits

The callable rejecting C helper produced meaningful PTY RED: one intended
assertion failure, zero errors (`terminal-red.log`). The privacy predicate stub
also produced one intended assertion failure, zero errors (`privacy-red.log`).
The initial Maven attempt after C implementation failed at strict compiler
warnings; these were implementation errors, not additional behavior RED. Those
warnings were corrected, and the exact hardened compiler command followed by the
independent Python PTY harness passed. The expanded direct harness passed bounds,
malformed UTF-8, INT/TERM/HUP/TSTP, parent EOF/death, invalid ARM, explicit restore,
helper-kill fallback and changed-identity refusal. Success tests verify snapshot
before ARM, no echo, CRLF/paste flushing, Unicode and exact nondefault termios
restoration. No Maven rerun occurred after the final direct C/harness pass.

The helper has fixed buffers, no dynamic commands, a 120-second absolute entry
clock, parent EOF/signal polling and original-state restoration. Remaining work:
actual long-duration timeout, wider nondefault IXON/IXOFF/ISTRIP/OPOST/ICRNL matrix,
foreign-terminal rather than only changed-identity test, Java bounded console
adapter and its shutdown/fallback orchestration. Restoration's synchronous ioctl
path still needs parent-enforced deadline qualification. The separate executable
is reviewable in the external lab; no helper binary is committed or advertised
qualified. No compiler/POM build hook was added.

Main/launcher WIP checks zero soft/hard core limits and fixed JVM crash options.
The launcher sets `ulimit -c 0`, `-XX:ErrorFile=/dev/null`,
`-XX:-CreateCoredumpOnCrash` and `-XX:-HeapDumpOnOutOfMemoryError`; local Java 21.0.12
accepts those options. A newly added core-policy predicate refuses unknown/piped
collectors. The host uses enabled piped apport, so ordinary invocation remains
privacy-unqualified. The distribution verification helper still expects the old
RUNTIME_UNQUALIFIED response and needs a deliberate contract-consistent update;
no final assembled verification or full reactor PASS is claimed for C2.

## Native runtime and privacy investigation

Public files extracted from the already pinned disposable images now run host
`psql --version` (18.6) and `sqlplus -V` (23.26.3.0.0). Oracle needed its public NLS,
timezone and English messages in addition to ldd libraries. An early credential-
free incomplete-home Oracle version probe segfaulted and generated default ADR
material under `/var/tmp/oradiag_tim`; no credentials or application inputs were
in that probe. ADR/client log suppression remains unqualified. No database
credentials have been entered and no DB authentication/transaction probe started.

The public-cert-only orapki create/add/display sequence passed without a password:
`wallet create -auto_login_only`, `wallet add -trusted_cert -auto_login_only`,
`wallet display -complete`. It created only `cwallet.sso` and its lock, containing
one trusted subject. Exact DER trust-set verification and native TLS remain work
for resumption. The pinned Java/oraclepki additions are external, not application
SDK dependencies.

Core limits alone do not suppress piped collectors. A scratch C exec probe
actually observed dumpability reset from 0 to 1 after exec. A scratch preload
constructor sets/verifies PR_SET_DUMPABLE(0), NO_NEW_PRIVS and a seccomp filter that
rejects PR_SET_DUMPABLE(nonzero). Its JNI probe observed actual Java dumpability 0
and denied reset to 1; re-exec with the preload again observed 0. Both native client
version commands still passed with this scratch component. A uniquely named,
credential-free SIGSEGV probe exited by signal 11 with kernel WCOREDUMP false and
no matching own apport report. This is stronger than working-directory absence,
but is not complete runtime qualification. No unrelated core/report was read.

Pending lead/reviewer decision: exact trusted library installation/hash, parent
JNI origin verification, per-exec nonce/PID/dumpability/NO_NEW_PRIVS/seccomp proof
before native prompts, coverage of shell/orapki descendants, static/secure-exec/
missing preload refusal, reset-to-2 and loader failure cases. Proposed child proof
would be a shared protocol refinement; it has NOT been implemented in production.
The scratch preload/JNI library remains outside the checkout. No host collector
or global setting was changed. Do not start credential probes until the effective
privacy and Oracle diagnostic controls are qualified.

## External artifacts and pause state

- `/home/tim/.tmp/es-d07c2-runtime-install/`: saved extraction, wallet/privacy
  runners, C/JNI scratch sources, compiled helper/preload/probes and safe logs.
- `/home/tim/.es-d07c2-runtime-20260908/`: fresh owner-only directory with copied
  public native runtime and public-cert wallet. This is the secure-ancestor
  candidate; `/home/tim/.tmp` itself is group-writable and not admitted.
- Existing public trust: `/dev/shm/es-tls-qualification-2cce40c4/trust/`.
- Existing shared JDK/jar were read only:
  `/home/tim/.tmp/es-tls-jdk-runtime/jdk` and
  `/home/tim/.tmp/es-tls-orapki/oraclepki.jar`.

No execution accounts, schemas, rows, grants, listeners or certificates in the
shared TLS labs were changed. No background test/native processes remained in the
assigned worktree at pause inspection. All launched helper/harness/version/crash
processes completed; no database cleanup is pending. Public-cert wallet and lab
binaries remain for reproducibility. No Git staging or commits occurred.

The external WIP manifest explicitly inventories required filesystem artifacts,
including ignored `fixtures/guarded-supervisor-v1/mock-ca.pem`; it does not rely
solely on Git's ignored-file filter. The C1 PEM hash is
`47beee1691eb006f6e9fb73660ea4860ef137382638b4587b8edbff83d52d2d0`.
This fixes the handoff inventory method, not the already frozen C1 record.
