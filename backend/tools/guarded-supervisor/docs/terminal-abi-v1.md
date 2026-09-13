# Candidate private terminal helper ABI

This is an internal Linux amd64 qualification proposal, not an ordinary runtime
registry entry. The shared supervisor contracts remain authoritative. Java 21
cannot use unbounded `Console.readPassword()` for this boundary. The separately
installed, hash-pinned C executable accepts no command-line operands, scripts,
paths, settings or arbitrary commands. Its build/compiler/libc identity is part
of the qualified installation. No native binary is committed to this repository.

## Ownership and transport

The helper inherits the Java parent's foreground process group. It opens
`/dev/tty` itself and verifies terminal type, session, foreground process group,
device and inode identity, including Linux TIOCGDEV for the underlying TTY
(rather than treating the shared /dev/tty device node as a unique terminal). The database client continues to run in its separate
session without a controlling terminal. A background caller, missing controlling
terminal or identity mismatch refuses before changing terminal state.

Helper stdin/stdout are exclusively owned mutable binary pipes. Stderr may contain
only fixed tool-owned refusal codes; it never contains terminal input, native
exceptions or credentials. The Java adapter reads these pipes directly into
bounded mutable buffers; the native database transcript/output queue is not used.
No asynchronous request or credential queue exists. One entry operation is active
at a time. No credential bytes enter command arguments, environment, files or logs.

Every multibyte integer uses unsigned big-endian framing. Fixed ASCII magic is
`ESTTY001`. Commands are single octets, accepted only in their specified state:

| Command | Meaning |
| --- | --- |
| `0x01` | Capture current terminal state; do not change it. |
| `0x02` | ARM PostgreSQL entry after parent stores the snapshot. |
| `0x03` | ARM Oracle entry after parent stores the snapshot. |
| `0x04` | Restore the original snapshot and terminate. |
| `0x05` | Restore-only fallback, followed by the original snapshot frame. |

A response is magic, one status octet, a four-byte payload length and bounded
payload. Status `0x01` is a snapshot; `0x02` account bytes; `0x03` password bytes;
`0x04` successful restoration; `0x7f` refusal with one fixed numeric reason.
No arbitrary status strings are accepted. Unexpected commands, lengths, trailing
frames, duplicate ARM or EOF before a complete frame refuse. The snapshot payload
is at most 512 bytes: fixed platform ABI identifier, device/inode/session/pgrp,
32-bit initialized `struct termios` length, then exactly that many opaque bytes.
The specific struct layout/size is pinned by the compiled helper; unknown platform
or sizes refuse. The whole struct is zero-initialized before `tcgetattr` so padding
does not disclose process memory. Credentials are never part of this snapshot.

The helper emits the snapshot while echo remains unchanged and waits for ARM.
Only after the Java parent has successfully retained that complete snapshot in
memory may it send ARM. Refused/truncated capture or parent EOF before ARM leaves
the terminal unchanged. Restore-only fallback accepts only the parent's original
in-memory snapshot and verifies the same terminal identity before `tcsetattr`;
configuration/package input cannot supply a snapshot. Restoration includes exact
readback comparison of all captured termios fields/bytes supported by the pinned
ABI; failed verification is INCONCLUSIVE.

## Bounded entry and restoration

After ARM, the helper disables echo and canonical line buffering, flushes pending
input, and emits fixed account/password prompts directly to the TTY. PostgreSQL's
prompt includes the fixed account-name argv visibility disclosure. Username
alphabets are exactly those of the ordered protocol, without normalization.
Password bytes must be strict UTF-8, at most 4096 bytes/1024 Unicode scalars, with
no embedded NUL, CR or LF. Physical CR or LF terminates an entry; neither becomes credential data. Ordinary
Enter and LF/Ctrl-J both work. CRLF is one entry with residual bytes discarded.
EOF/hangup before a delimiter refuses. ICRNL/INLCR/IGNCR are disabled so this framing
is explicit; other bytes are never trimmed or normalized. Pending input is flushed
between prompts and before return/restoration, so pasted suffixes cannot become
the next credential or shell input. Clipboard text containing a delimiter is not
claimed to be a single password. Input buffers are fixed-size, with one extra byte available
only to detect overflow. Every helper and Java credential buffer is explicitly
wiped after transfer/use, including rejection and cleanup paths.

The helper polls both its parent's command/EOF channel and the TTY, using one
absolute 120-second deadline for the whole human-entry operation. Input does not
reset this deadline. Restoration has a separate absolute 10-second deadline,
without automatic entry retry. Overflow, malformed input, interruption, stop
request or parent death discards pending terminal input and restores the original
snapshot. Remaining characters are never interpreted as another account/password
attempt. A restore acknowledgement follows successful exact restoration; helper
exit alone is not evidence of restoration.

Signal handlers only set `sig_atomic_t` flags/write the nonblocking self-pipe;
normal control flow polls that pipe and restores synchronously. INT, TERM, HUP,
TSTP and SIGPIPE are handled. No allocation, termios operation or Java callback is
claimed async-signal-safe. The parent always attempts restoration in finally and
its shutdown hook. If the helper was killed before restoration, the parent may
invoke the same verified executable once for the fixed restore-only operation.
Failure or ambiguous restoration remains INCONCLUSIVE. Simultaneous SIGKILL of
both processes, kernel failure and power loss cannot guarantee restoration.

The candidate Java adapter owns one entry worker and direct bounded frame buffers,
not a native transcript queue. It validates the entire pinned 112-byte snapshot
(52-byte header and 60-byte Linux amd64 termios) before ARM. It accepts exactly
snapshot/account/password/restored followed by EOF and successful helper exit;
oversized, duplicate, out-of-order or trailing frames refuse. Credential bytes
are validated in their bounded mutable representation before conversion.

Java enforces its entry clock independently of the helper. On failure or shutdown
it stops and joins the original owned helper before any single restore-only
fallback. The original in-memory snapshot is the only fallback payload. Cleanup,
pipe closure, worker joining and fallback share one absolute 10-second deadline;
repeated restore calls do not renew that deadline. A restoration acknowledgement
alone cannot hide an unclosed helper/pipe/worker. An unresolved cleanup deadline
remains INCONCLUSIVE even if a late task later exits. The adapter is not installed
as the ordinary console while runtime privacy and the registry remain unqualified.

ISIG is disabled during input so permitted control bytes, including U+0003, remain
data. Prompts explicitly say "Enter to finish; entry expires after 120s". External
INT/TERM/HUP/TSTP/SIGPIPE still cancel through the self-pipe. Raw terminal EOF
control bytes remain data; actual input hangup refuses. CRLF, pasted suffix and
trailing-input flushing require independent PTY cases.

## Independent qualification examples

Use pseudoterminals with independently chosen nondefault termios settings. Verify
no credential echo or stderr/pipe-status disclosure; exact state restoration on
success, EOF, overflow, malformed Unicode, interruption, parent death, helper kill
with parent fallback and failed/truncated snapshot acknowledgement. A foreign TTY
or changed session/pgrp must refuse fallback without changing that terminal.
Exercise continuous/trickling input against the absolute deadline, bounds before
allocation, all allowed account/password boundaries and explicit buffer clearing.
Record normal, refused and INCONCLUSIVE outcomes separately. Passing synthetic
PTY cases does not qualify a native database client or enable the empty registry.

Reference semantics: [termios](https://man7.org/linux/man-pages/man3/termios.3.html),
[poll](https://man7.org/linux/man-pages/man2/poll.2.html), and
[signal safety](https://man7.org/linux/man-pages/man7/signal-safety.7.html).
