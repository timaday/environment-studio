# Authority checks during bounded plan encoding

This internal transport prerequisite adds no route, compiler readiness or export.
PlanViewEncoding retains its existing maximum, fixed chunks, closed-on-failure
behavior and transfer-time verifier. Its original constructor preserves existing
callers. An additional constructor accepts a non-null Runnable verifier combining
the original lease/selection authority and one caller-owned absolute output deadline.

The verifier runs before serialization, before bounded writes and allocations,
during each chunk of bulk input, and after serialization before marking it complete.
The caller starts its output clock before encoding and passes the same deadline
through later output; encoding never creates or renews that clock. This check cannot
preempt an arbitrary blocking Java getter. Production callers supply closed small
reply records, never uploaded executable objects or plugins.

If verification refuses, discard and wipe every retained encoding chunk and deny
transfer. Preserve the existing safe PlanRefusal code even when the JSON library
wraps it; other failures expose only the static existing encoding refusal. No cause,
source value or arbitrary exception text is sent. A failed encode cannot be retried
or reused. No response bytes are emitted by encoding. The worker remains owner of
the admitted slot until its existing cleanup/completion contract settles.

Exactly32768 encoded UTF-8 bytes remain valid at the small-response limit; one more
refuses. Count actual encoded bytes, including quoting/escapes, independently from
source character length. Existing legacy constructors and output verification
remain unchanged. Test authority/deadline loss during serialization, no bytes after
failure, retained-buffer wiping, complete exact-boundary output and one-byte overflow.
Use literal expected bytes and controlled clocks/authority, not copied implementation
or real configuration data. Record actual RED, focused gates, compiled guard mutants
and independent review before use by new hosted routes.
