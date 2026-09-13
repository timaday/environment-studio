# D07c3 private frame transport — reviewed local primitive

This implements the fixed-frame codec and one native-owned connection's bounded
handshake transport. `ES_WIRE_COMPLETE` means that connection's required protocol
and local closure completed. It is not process identity, launch admission,
privacy qualification, or permission to authenticate to a database.

## Fixed scope

Base: `744e9812f56a38ad661e9fcb0ef8bf20a8fab50e`, plus the five exact reviewed
privacy-controls source/test files as inherited context. Those inherited files,
the shared crash-privacy contract and private ABI are not authored in this delta.
Only privacy-wire C/header, its native/Java tests and this evidence are authored.
No launcher, registry, Main, RuntimePrivacy, JNI coordinator, listener, compiled
installation record or production build manifest is changed.

The codec uses the ABI's exact 12-byte header and closed 12/16/96/108-byte frame
sizes. It validates the complete tuple ordinal, reserved bytes, closed refusal
codes and all twelve proof bytes. No length is supplied by a frame. Buffers are
fixed, and failed output is wiped. The transport checks direction/sequence and
complete tuple correlation, refuses queued extra bytes, and requires parent-side
EOF after ACK before connection completion. Child-side completion closes its
connection after matching ACK. No parser searches ahead for another magic word.

One native owner calls the API. Only the borrowed cancellation eventfd may be
signalled from another thread; the transport never consumes or closes it. A fresh
connection takes ownership of its nonblocking CLOEXEC AF_UNIX stream descriptor.
Descriptor aliases and reinitialization refuse before adoption. Terminal closure
is sticky, wipes retained correlation, and never retries a numeric close. An
uncertain close returns CLEANUP rather than a completed connection.

The caller supplies a fixed native CLOCK_MONOTONIC bound of at most ten seconds;
no peer/frame supplies timing authority. The future parent coordinator must pass
its original shared launch deadline through all connections and phases. A child
can only bound its own local wait; that bound cannot extend the parent's deadline
or grant admission. Parent completion still requires EOF within the original
budget. The ABI has no new timing field or environment variable. The final header
comment clarifies this distinction; it changes no tested executable behavior.

Reads/writes handle partial progress and EINTR without renewing that bound. Polling
prioritizes cancellation and also accepts terminal frames during blocked writes.
MSG_NOSIGNAL prevents a broken control connection from causing SIGPIPE. recvmsg
rejects ancillary data and closes every delivered SCM_RIGHTS descriptor, including
when control truncation occurred. No control frame or OS error is logged.

## Actual tests, 2026-09-09

Toolchain: pinned Maven 3.9.16, installed JDK 21, host GCC
`13.3.0-6ubuntu2~24.04.1`. Tests compile exact production C with strict warnings in
owned mode-0700 `/tmp` scratch and remove their outputs. All data is independently
invented control data; no credential, private model or native DB input was used.

Actual TDD:

- Initial callable refusal stub: 11 assertion failures, zero errors.
- Initial implementation: all 11 passed.
- Expanded blocked-write ABORT test: one actual assertion failure among 15 cases;
  the old poll watched only writability and delayed ABORT until the deadline.
  Monitoring incoming terminal frames during writes made all 15 pass.
- Positive partial-send control added: test-only linker wrapper caps each actual
  AF_UNIX send to three bytes. It never fabricates a successful syscall result.
- Invalid owned/borrowed descriptor alias: one actual assertion failure among 17
  cases; refusal closed the borrowed eventfd. Rejecting the alias before adoption
  corrected this behavior.

Final full command: `mvn -B -ntp -f backend/pom.xml verify` with the pinned Maven.
PASS: 543 tests (136 core, 7 parser, 328 server, 72 supervisor), including all
17 wire cases and all 7 inherited suppression cases. Assembly and hostile runtime
verification passed. An earlier full candidate run also passed 541 tests; the
final total includes the later partial-write and descriptor-alias controls.

The 17 wire cases cover independent byte fixtures for every frame type, every
truncation, reserved/type/ordinal/proof/length errors, each split boundary in both
handshake directions, positive partial writes, malformed/reordered/trailing data,
wrong correlation, received/truncated ancillary descriptor cleanup, ABORT and
REFUSED, read/write deadlines, cancellation without consuming the borrowed event,
repeated signal interruption, post-ACK stall/extra data, and slow partial progress.
Each unwrapped fragmentation direction performs 107 fresh socket handshakes;
the partial-send control performs another 107. These are transport tests, not
321 process admissions.

Eight external source mutants were killed by actual probe assertions:

| Removed/corrupted behavior | Detecting control |
| --- | --- |
| Received tuple correlation | stale tuple |
| Immediate trailing-byte check | extra data after PREPARE |
| Closing received rights | actual process descriptor count |
| Shared deadline, renewed on progress instead | slow input |
| Required final EOF | peer holds connection after ACK |
| Read monitoring during blocked write | ABORT under backpressure |
| Proof validation | independent malformed proof bytes |
| Positive partial-write loop | real three-byte sends |

Every mutant exited with the probe's fixed assertion status 40. Diagnostics contain
only fixed source line markers, and stdout was empty. The first proof-mutant build
failed because its altered source left a constant unused under -Werror; that was
a setup error, not a RED or killed mutant. A corrected expression retained the
constant and the actual adverse run failed as expected.

Credential-free strace corroborated rights/control truncation closure, blocked
write ABORT, final EOF deadline and slow input. These traces preceded only the
invalid-alias initialization correction; final tests reran all those paths. No
trace is an executable-identity, trusted-closure or native-output admission proof.
`python3 scripts/check_repository.py`: PASS. This external archive has no Git
index; staged-content checks and independent review remain integration gates.

## External evidence and remaining gates

Logs under `/home/tim/.tmp/`:
`es-privacy-wire-red-20260909.log`, `es-privacy-wire-green1-20260909.log`,
`es-privacy-wire-abort-red-20260909.log`, `es-privacy-wire-green2-20260909.log`,
`es-privacy-wire-full-20260909.log`, `es-privacy-wire-final-focused-20260909.log`,
`es-privacy-wire-alias-red-20260909.log`, `es-privacy-wire-final-full-20260909.log`,
`es-privacy-wire-mutants2-20260909.log`, `es-privacy-wire-partial-mutant-20260909.log`,
`es-privacy-wire-traces-20260909.log`, `es-privacy-wire-repository-20260909.log`.
The owned credential-free trace/mutant lab is `/tmp/es-wire-mutants-k4tty2z5`.

Not implemented/qualified: listener ownership/backlog saturation, OS randomness,
SO_PEERCRED/pidfd/ancestry/start/image/loader/script verification, trusted compiled
closure, JNI handles and cross-thread coordinator lifecycle, child constructor
installation, concurrent exec graph, secure/static/missing preload refusal, exact
FORK/client runtime, crash/heap/error/ADR privacy, release artifacts, or native DB
authentication. A peer's proof bytes have meaning only after the later trusted
identity/constructor checks. No real application configuration or secrets entered
the checkout, build context, output or these tests.

Exact four-file source candidate:

```text
2d91520266062d72fea9b65f562f308089d138b670c64e567d3548b2501a5ab4  backend/tools/guarded-supervisor/src/main/c/privacy-wire.c
904f5e3bb731a02ebc0f8fec3a81ca3faad7776558c1bcaed413bc142e4a0357  backend/tools/guarded-supervisor/src/main/c/privacy-wire.h
6321cacd40e5ed0db15543fa6b049526a47965a8e0cefeeb594326ed1ad8fb81  backend/tools/guarded-supervisor/src/test/c/privacy-wire-probe.c
98be991aa3b0480232e73fa8eb0ed80075504710b6c29fddfae23decf52af19a  backend/tools/guarded-supervisor/src/test/java/studio/environment/supervisor/PrivacyWireTest.java
```

## Independent review and integration

Root reviewed the fixed five-file manifest
`es-privacy-wire-candidate-20260909.sha256` (SHA-256
`2eba81554315a42480872ab5679bf48471cff3a6597a7e2bf7bf462c5955b8c1`)
without source correction. The combined detached integration at `7c1cb4c` plus
this exact wire candidate and the then-frozen binding candidate passed all 578
Java tests (150 core, 7 parser, 349 server, 72 supervisor), assembly and hostile
launch checks. Log: `es-bindings-wire-review-baseline-20260909.log`. The independent
binding review subsequently found a coordinate-disclosure defect in that other
candidate; the passing baseline does not override that finding.

Five additional independent native controls passed against these exact C bytes:
actual close followed by simulated EINTR returns sticky CLEANUP with one close
attempt; signalled cancellation wins over readable PREPARE without consuming the
borrowed event; child ESTABLISHED and parent ACK each reject altered outbound
correlation; an incompatible AF_INET descriptor is closed while the cancellation
event remains owned by its caller. The test-only close wrapper performs the real
close before reporting uncertainty; it does not fabricate successful cleanup.
The external harness is `/home/tim/.tmp/es-wire-independent-review-20260909.c`,
built with `/usr/bin/cc`, C17, strict warnings, hardening, pthread and
`-Wl,--wrap=close`; its actual execution returned 0 with empty output.

Review found no further blocker in this transport-only scope. Trusted executable
identity, launch-wide coordination and actual crash-path qualification remain
required before any native authentication. No runtime admission flag changed.
