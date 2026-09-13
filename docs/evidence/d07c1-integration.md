# D07c1 standalone supervisor integration — 8 September 2026

The separate Java supervisor candidate is integrated with strict admission,
ordered native-client protocols, phase-aware outcomes and owned process cleanup.
Its compiled qualification registry is empty; concrete console/native-runtime
ports remain unavailable. It cannot execute an ordinary invocation yet, and the
web application has no supervisor dependency or execution route.

The author froze 37 files in `es-d07c1-output-fixed-frozen.sha256`, manifest
SHA-256 `0af8049b6b3abe2a6e25d2816d1261f596b322ea9502fdae8230d389ab3dfcc7`.
The lead verified all hashes, copied the nonshared files and merged the module,
thin server classifier and schema test into the current HTTP/view-contract tree.
See [author evidence](d07c1-supervisor.md) for actual RED/GREEN runs and mutants.

## Review corrections

Independent review reproduced three authority/ownership defects before integration:

- An `Error` after a commit attempt could escape phase classification and become
  a pre-client refusal. Cleanup could also erase an acknowledged result. The
  corrected boundaries preserve UNKNOWN or acknowledged APPLIED with INCONCLUSIVE
  cleanup and attempt all owned cleanup paths.
- The original input executor could queue a credential-bearing buffer. It was
  replaced with an immediately owned exclusive writer, retaining its buffer and
  handle until actual completion. Tracking failures no longer skip other cleanup.
- An output-reader exception could masquerade as normal EOF, exit zero and complete
  cleanup. The corrected failure latch refuses read/exit authority and waits for
  actual output-reader completion before accepting a clean exit.

The final independent focused review ran 24 test methods successfully, including
actual invented child processes and injected output-read failures. All full and
incremental hashes matched before/after review. The author reported 400 passing
reactor tests and 200 repeated adverse-process cases after the corrections. One
earlier conservative cleanup refusal did not reproduce; neither reviewer nor
author claims that rare timing was isolated or fixed. Refused adverse cases may
retain INCONCLUSIVE cleanup; that is never accepted as successful execution.

## Actual integration gates

- Final clean Docker build: **433 Java tests passed** (131 core, 7 parser,
  257 server, 38 supervisor), zero failures/errors/skips. TypeScript/format checks,
  7 component tests, 22 schema tests and the frontend production build passed.
- The build assembled the separate distribution, enforced its exact 12-JAR set,
  checked its SHA inventory and launched it from an unrelated directory with a
  hostile PATH/CLASSPATH/JVM-option environment. It returned only the expected
  `REFUSED / RUNTIME_UNQUALIFIED / COMPLETE` status.
- The `supervisor-artifacts` Docker target exported the versioned directory and
  ZIP. The lead compared every ZIP member byte with the directory and verified
  every inventory digest. A fresh extracted ZIP, after the documented explicit
  launcher mode restoration, passed the unrelated-directory hostile-environment
  launch check. Its SHA-256 was
  `60e323f7ea02ee93ba8855d07eccdb152113c0c72ff71e849585b660977c97a7`.
- Protected non-root/read-only container smoke passed startup, static/health,
  demo denial, private workspace initialization, permissions, overwrite refusal
  and explicit schema-2 upgrade/refusal. The web Boot archive contained no
  supervisor classes/JAR; psql, SQL*Plus, orapki and Python entry points were absent
  from the runtime PATH. Docker's final stage copies only the web artifact/probe
  and required SQLite library from the build stage.
- Repository integrity and **11 Python tests** passed. The new exact supervisor
  POM allowlist test first failed with `UNREGISTERED_DATA_ARTIFACT`, then passed
  after adding only that descriptor; adjacent XML and other tool POMs still refuse.
  Whole-diff provenance review, staged-content and whitespace checks precede commit.

The initial container attempt exposed a missing aggregate OpenAPI file in the UI
build context; an exact ignore/COPY exception fixed it. The next attempt ran 433
Java tests but had five errors because the author's Git-filtered freeze omitted
an ignored public certificate required by tests. Its independent origin was
confirmed, and the lead copied its exact bytes, SHA-256
`47beee1691eb006f6e9fb73660ea4860ef137382638b4587b8edbff83d52d2d0`, with narrow
Git/Docker exceptions. Its private key existed only in the author's generator
memory. The content assessor also rejected the fixture manifest's extra `note`;
that text moved to the fixture README, leaving the closed registration intact.
These were handoff/integration failures, not claimed TDD RED runs. Future candidate
inventories must include required ignored files and every provenance member.

An extracted-install probe under the user's group-writable temporary parent
correctly refused `UNTRUSTED_FILE_WRITE`. The successful installation used an
explicit protected directory under `/tmp`; no global directory permissions or
file policy were relaxed. ZIP mode restoration is documented in
[deployment instructions](../../deploy/README.md).

The pinned Maven image lacked Python and `/usr/bin/java`. Only the build stage
adds the three exact minimal Python packages needed by invented child tests and
links the launcher's fixed Java path to its already pinned Temurin installation.
CI builds and retains the supervisor ZIP separately from web-image publication.
No native client or Python runtime is added to the application image.

The final build took 85 seconds. Logs are
`/tmp/es-d07c1-integration-complete-container.log`,
`/tmp/es-d07c1-integration-smoke.log` and `/tmp/es-d07c1-artifacts.log`; failed
attempts remain separately recorded. Local runtime manifest:
`3d884ccbfc7cc7c606f9a5458ca393f5fdd9d93879cfb5c397e70cf81a542133`;
image config: `be4d7772d4317c5a8b1ca1dc47652d0f2b58f91df21f7ff68671259e9875e93a`.
No serial comparison or parallel speed-up is claimed.

## Still required

Native runtime/TLS/console qualification, real commit acknowledgement, fault
matrices and full-state transaction witnesses remain local work. The separately
[tested JDBC TLS combinations](d04-tls-qualification.md) do not qualify these
native clients. Hosted review/export authority, browser workflow, maximum heap
and operational cleanup recovery also remain incomplete. Actual deployment,
identity-provider/accounts and private application qualification remain external.
This image and distribution are local unpublished candidates; the authorized
branch push remains rejected by automatic approval review under the session's
Never setting, so no new remote PR/CI/GHCR result is claimed.
