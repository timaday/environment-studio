# Native single-root launch owner prerequisite

The private nine-function C owner now composes the existing listener, fork/root
capture and connection owners. One launcher and one receiver retain the original
startup clock, cancellation descriptor and cleanup lifetime. Successful correlation
means only the exact Java-owned root matches its first PREPARE connection. No
CHALLENGE, client execution admission, production JNI registry or runtime
availability is implemented by this slice.

Primitive calls run outside the bookkeeping mutex. Completed stages publish under
that mutex. Cancellation holds a descriptor reference until its bounded wake
attempt returns; cleanup cannot close and reuse the number meanwhile. Close
requested, settling and settled remain distinct. Later close calls only shorten
the original deadline. Cleanup uncertainty remains permanent after late completion;
externally joined calls and ended TLS windows are required before reclaiming owner
storage. Width1 is this private prerequisite, not a reduced product support matrix.

## Fixed review and actual checks

Author six-file manifest SHA
`481c2a30a73f1bead8f619a7739851868835315f6e31eba605968325de51e0bd`,
base `76ab78b`. Exact complete ABI SHA
`0502ed024f886cd84157dad9354c6c8bb83148fb0b8a763b0af262da26a89fd1`.
Independent review on `eedcd11` report SHA
`d6c1c006c9d20e239d1641a82658047ff9723d5c2d54fae5b8127bfdf2d9ee45`.
Lead authored the ABI and reviewed the six implementation/test files written by
the separate author. All six source hashes remain unchanged through review.

Actual Maven3.9.16/JDK21.0.12/GCC13.3/Linux controls:

- Actual initial RED:1 assertion,0 errors at scaffold open,4 sibling controls pass.
  An earlier diagnostic-format rejection was harness setup, not accepted RED.
  Strict compiler warnings were fixed without relaxing flags. A valid-open
  failure corrected an overly broad input overlap check.
- Later actual adverse failures corrected safe output wiping for overlong/null
  paths and owner cancellation between accepted connection stages. Separate
  test declaration/compilation and expired-fixture cleanup mistakes are recorded
  as test issues; they are not product RED or mutation kills.
- Final author focused20 pass:16 native families and4 siblings. Actual test-only
  JNI runs fixed FORK through PrivacyLaunchOwner, receives constructor PREPARE and
  exact invented stdout/stderr, then joins/reaps ownership. POSIX_SPAWN and failed
  exec never correlate. No main marker or CHALLENGE occurs.
- Author full verification:1,203 pass (279 core,7 parser,637 server,280 supervisor),
  0 failures/errors/skips, assembly/hostile distribution pass,23:24:49BST. This
  deliberately older author base excludes the later shared lifecycle work.
- Independent25 pass:5 new native families,16 author families and4 siblings,
  0 failures/errors/skips,23:28:32BST. Real eventfd saturation cannot reset
  cancellation or affect another owner. Partial entropy exhaustion closes once
  and wipes the identifier. Invalid aliases cannot allocate/overwrite owners.
  Actual subordinate cleanup returning after its deadline stays inconclusive;
  concurrent shortening during SETTLING and exact descriptor reuse preserve it.
- Eight author and two independent compiled mutants each fail an assertion;
  restored controls pass. Independent mutants remove the final cleanup-clock
  check and saturated-eventfd wake acceptance. No setup failure counts as a kill.

Combined seventeen-file source manifest SHA
`722517e5586e7e574cb06af5b0c4767b9a68a0db45394a23209711312bfc0be6`
includes shared profile reuse and both independent review additions. Actual
`mvn -B -ntp -f backend/pom.xml verify`:1,253 pass
(289 core,7 parser,672 server,285 supervisor),0 failures/errors/skips;
assembly/hostile distribution pass,23:33:33BST. No parallel speed-up is claimed.

All processes, paths and wire fixtures are independently invented in owned0700
temporary directories. Injected holds/faults exercise real subordinate operations
but do not prove interruption of an actually stalled kernel call or ProcessBuilder.
No exhaustive schedule/sanitizer/native-memory qualification is claimed. A
synchronous cleanup syscall can outlive its caller budget; storage remains owned
and later results stay inconclusive.

Business: client availability stays disabled. Engineering/security: first
operational failure and cleanup uncertainty remain separate; early uninitialized
arm cannot invent successful disarm. QA/RST covers stage cancellation, descriptor
reuse, partial allocation and independent remaining cleanup. Production JNI/token
ownership, trusted installation, complete exec/image/loader/memory closure, privacy
admission and actual database client/readback qualification remain required.
