# D07c3 — first Java process ownership

The reviewed private ABI now requires pre-exec kernel sender capture for the
Java-owned root. A later numeric PID lookup, socketpair creator credentials and
Java start-time/isAlive metadata cannot establish that ownership. This is a
contract decision and external feasibility result; no coordinator, JNI runtime
or qualified registry entry is enabled.

The investigation inspected exact upstream JDK21.0.12+8 sources plus installed
Ubuntu21.0.12+8 bytecode and native behavior. ProcessHandle metadata precedes
reaper setup; Java publishes hasExited asynchronously after reaping. No forced
PID reuse or specific reaper race was reproduced. The source demonstrates why
timestamp/PID correlation alone is insufficient; it is not such a reproduction.

The actual owned prototype first establishes the reviewed JVM privacy controls.
A fixed JNI atfork child hook sends a private byte over a precreated unnamed
SOCK_SEQPACKET. SO_PASSPIDFD yields the sender's kernel process pin; the control
explicitly confirms socketpair SO_PEERPIDFD names the creating JVM instead.
After ProcessBuilder.start returns, captured PID/live-pin checks match its owned
Process. The child never waits for a parent ACK, since the JDK waits for the
exec-failure pipe before returning start.

Final credential-free controls passed32 sequential and64 concurrent captures,
preserving exact independent native output. Wrong expected PID, exited child,
failed exec, POSIX_SPAWN/no hook and arming on the wrong launch thread refuse.
A separate traced32 captures confirms each child send precedes that child's exec.
Suppression prevents ancillary decoding in the trace; native assertions establish
the received SCM_PIDFD, not an invented decoded trace witness. Generated hook
instructions use direct initial-exec TLS and bound calls; the inspected final
hook has no dynamic TLS resolver or stack-failure helper. This inspection does
not qualify all production callback ordering or signal schedules.

External owned lab: `/tmp/es-root-anchor-7g0pqf6x`. Final prototype/source/control
manifest SHA-256: `f45e357bffc9729bb7bbe69e128b0360a34ceddbaf32a7835826235ca15be5d0`.
No prototype binaries or external runtime files enter the checkout or build context.
The [JDK fork implementation](https://github.com/openjdk/jdk21u/blob/jdk-21.0.12%2B8/src/java.base/unix/native/libjava/ProcessImpl_md.c)
and [kernel SCM implementation](https://github.com/torvalds/linux/blob/v6.8/include/net/scm.h)
support the ownership/ordering rationale. Exact runtime qualification is still required.

Independent review accepted the lead's two-document contract candidate based on
`693ff37`, manifest SHA-256
`f1ca5e5ead0637876f0ab7ba5f88a48d8eaf6131256b3b65c3aa3d397f01a387`.
Its parent-hook sender close, child both-end close,24-byte launch-bound record,
serialized dedicated platform-thread window, generational descriptor ownership,
early capture for cleanup only and sticky quarantine are production requirements
beyond the prototype. The Java wrapper must enforce exact Process provenance;
the native registerRoot PID argument alone cannot express that invariant.

Missing support/hook, failed or stalled start, callback failures, descriptor reuse,
extra/truncated ancillary, same-UID interference, cancellation/shutdown races,
process cleanup and generated async-signal-safe closure remain unqualified. A
captured root is not image/ancestry admission or a constructor privacy receipt.
