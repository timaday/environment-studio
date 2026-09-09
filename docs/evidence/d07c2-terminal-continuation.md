# D07c2 terminal continuation — independent mock evidence

This reviewed slice continues the nine historical C2 files on `28b7a5d` in an isolated
worktree, then integrates on the D08 checkpoint `54d6127`. It exercises a bounded Linux amd64 terminal adapter using invented
credentials and owned pseudo-terminals. It does not enable native database
authentication: ordinary `Main` retains its unavailable console/runtime and empty
registry. Effective crash privacy remains a separate admission gate.

## Behavior and actual tests

The Java adapter retains the helper's exact 112-byte original snapshot before
arming entry, bounds each frame before allocation, owns mutable credential buffers,
and requires restoration acknowledgement, EOF and successful helper exit before
handoff. Entry has one absolute 120-second deadline. Cleanup owns a separate
absolute ten-second deadline, terminates and joins the original worker before at
most one fallback, and never renews the deadline. Missing restoration or unresolved
local resources stay `INCONCLUSIVE`; an acknowledgement alone is insufficient.

Actual TDD used a refusing adapter stub: four assertions failed, zero test errors
(`es-d07c2-console-red3-20260909.log`). The first implementation passed those four.
A further adverse test then demonstrated that a stalled fallback process survived
the parent deadline: one failure out of six, zero errors
(`es-d07c2-fallback-deadline-red-20260909.log`). The correction forcibly terminates
owned processes at expiry and checks late launch completion. All six cases passed
in `es-d07c2-console-green2-20260909.log`. They include oversized unsigned frame
length refusal before payload allocation, truncated snapshot refusal before ARM,
bounded stalled entry, fallback deadline/no retry, and direct buffer wiping.

Independent actual PTY controls passed:

- Existing helper cases: no echo, exact restoration, input limits and refusal paths.
- Conflicting input/local/output termios flags with control characters and Unicode;
  the complete original termios state is restored.
- A snapshot from a different controlling TTY is refused without changing that TTY.
- Actual 120-second absolute timeout while bytes arrive every 200 ms: no newline,
  no reset of the deadline, refusal and exact restoration. The external command
  was `python3 terminal-helper-extended.py OWNED_HELPER --deadline`; its safe log
  is `es-d07c2-actual-deadline-20260909.log`.
- Actual Java/helper Unicode handoff, original helper SIGKILL followed by parent
  fallback, JVM SIGTERM shutdown restoration, and SIGSTOP of the fallback. The
  last case returns `INCONCLUSIVE` within the ten-second parent bound and kills the
  stopped process; the PTY fixture then restores its own original state.

The Java PTY probe additionally checks absence of both the original child and any
remaining process executing its uniquely owned helper path. It reports only fixed
codes and checks that invented credential canaries do not appear in terminal output.
An initial integration refusal was test installation setup: the host temporary
root and compiler umask permitted group writes. Tests now create their own 0700
scratch under `/tmp` and make the compiled helper 0700. This is not recorded as a
production behavior RED.

## Boundary and remaining qualification

The native helper remains the historical candidate source; the additional controls
exercise its actual existing deadline and terminal behavior. The Java adapter is
package-private and exercised directly by mock tests; ordinary invocation remains
fail-closed. Distribution verification now distinguishes the exact privacy refusal
from the exact unavailable-runtime refusal using the actual protected launcher;
it does not accept arbitrary errors as success.

No database credentials, database connections, native client authentication,
installation qualification for general platforms, external crash collector policy,
or whole-machine shutdown guarantee are established by this evidence. A JVM or
host killed without running shutdown hooks cannot promise parent restoration.
Tests cover Linux amd64 and the available Java 21 toolchain only.

Commands and logs are local development evidence, not release qualification or
publication. External logs live under `/home/tim/.tmp`; only fixed outcomes and
independently invented fixtures belong in this repository.

## Fixed candidate verification

`mvn -B -ntp -f backend/pom.xml verify` passed in this isolated tree:
136 core, 7 qualified parser, 328 server and 48 supervisor tests, 519 total,
zero failures/errors/skips. The assembly and hostile-environment protected
launcher check also passed. Log: `es-d07c2-full-verify-20260909.log`.
The focused package run passed separately in
`es-d07c2-console-pty-green-20260909.log`. `git diff --check` and
`python3 scripts/check_repository_content.py` passed; the content check only
detects known patterns and does not establish provenance by itself.

The unchanged inherited C2 sources, the narrow distribution-check correction,
the new adapter and its independent tests form one fixed review candidate. No
Git staging, commit, publication or native runtime qualification was performed
by this author. Subsequent independent lead review accepted the fixed candidate;
the integration evidence below records the combined result.

Preserved WIP nine-file boundary: `terminal-abi-v1.md` and
`TerminalHelperTest.java` were extended; `terminal-control.c`, `RuntimePrivacy.java`,
`Main.java`, `Assemble.java`, `RuntimePrivacyTest.java`,
`terminal-helper-probe.py`, and the historical handoff remain inherited unchanged.
Seven additional files are the narrow `VerifyDistribution.java` correction,
`TerminalConsole.java`, `TerminalConsoleTest.java`, `TerminalConsolePtyProbe.java`,
`terminal-console-probe.py`, `terminal-helper-extended.py`, and this evidence.
The frozen candidate is based on `28b7a5d7b243d1877bbdd7e4303e374c2f942254`.

## Independent review and integration

The original 16-file manifest is
`/home/tim/.tmp/es-d07c2-terminal-candidate-20260909.sha256`, SHA-256
`189bc5b5dd4cac4251b16adb16fe86615bbde7b99151009d2054f6a99f1ecb71`.
The lead read the fixed source, ABI and tests and independently ran all 48
supervisor tests, plus explicit nonzero core/parser/server selections. All passed,
including PTY and assembled hostile-launch checks:
`es-d07c2-independent-focused2-20260909.log`. An earlier package-pattern selection
matched no supervisor tests and correctly failed; it was corrected without a
zero-test bypass. Six additional external frame/Unicode boundary controls passed
in `es-d07c2-independent-adverse-20260909.log`.

The expanded independent control passes eight examples, including a bad frame
magic and restoration acknowledgement followed by nonzero exit. Three isolated
mutants, removing clean-exit enforcement, frame-magic checking or retention of
the original snapshot, each fail an assertion. Log:
`es-d07c2-independent-mutation-20260909.log`. Sources are outside the checkout;
the fixed candidate was never mutated. This targets three critical guards, not
a claim of complete mutation coverage.

The first full independent host rerun failed an unchanged server test's initial
credential POST (expected200, actual400), before reaching supervisor tests:
`es-d07c2-independent-full-20260909.log`. The exact HTTP case passed eight later
repetitions, each including its real 30-second stalled-view deadline; 24 further
fresh reservation/Unicode credential submissions passed. Logs:
`es-body-investigation-repeat1-20260909.log` and
`es-body-investigation-fast24b-20260909.log`. The cause remains unknown. No production
fix, proven-flake claim or erased failure is justified by nonreproduction. The
test now reports only an allowlisted safe refusal code if it recurs; no raw body,
credential or exception message is added to diagnostics.

The separate integration tree `/home/tim/.tmp/es-d07c2-integration-20260909` starts
from `54d6127` and adds the reviewed 16 files, that narrow diagnostic assertion and
build-only pinned gcc/libc headers/Python stdlib packages. The pinned Maven image
had no C compiler; native/PTY tests stay mandatory. No compiler or Python package
is copied into the web runtime. The other reviewer verified these two additions
and the complete 18-file manifest, SHA-256
`3b8a6fd84bc11a5ebf173eff8976d831852cdc2c161d8ac231aabf0ab2a8310e`, at
`/home/tim/.tmp/es-d07c2-integration-candidate-20260909.sha256`.
The 16-source-file manifest, excluding the two evidence documents, has SHA-256
`7aa4c3d8bda9b18db3552288f63ebd17e0d131b7b1a51d8b1e62baee8233e66c`.
All integration source hashes match on copy into the root tree.

The actual combined OCI build passes Java519 (136 core, 7 parser, 328 server,
48 supervisor), frontend34/schema22, production build, distribution manifest
verification and hostile-launch checks. Log:
`es-d07c2-integration-oci-build-20260909.log`. Image
`environment-studio:d07c2-review-20260909` has local ID
`sha256:14ef3102f46f69de995c8d4e78ee0cbc65d2f6af99ebecb40908c042165f0989`
and source label `54d6127+c2-7aa4c3d8`. Protected container and workspace smoke pass;
`es-d07c2-integration-smoke-20260909.log` records the actual commands and the
accidental repeated workspace-only check. Earlier hosted browser evidence remains
the unchanged D08 mock-port result; this build adds no production server behavior.

The separate `supervisor-artifacts` target exports
`/home/tim/.tmp/es-d07c2-supervisor-artifacts-20260909/environment-studio-guarded-0.1.0-SNAPSHOT.zip`,
SHA-256 `91e672a3cd988e589bd0d414dbd808b9c66f7f1c842a054ecd24439efd0b1d2c`.
It remains an unqualified standalone candidate with no enabled native client.
No new GitHub/GHCR/HiveForge publication or deployment result is claimed.
