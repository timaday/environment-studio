# Native complete maps sampling prerequisite

The private sampler reads two complete mapping streams through the retained
kernel peer and original deadline/cancellation controls. It keeps every mapping,
exact opaque label bytes and bounded record metadata. A changed second sample,
truncation, malformed input, resource overflow or uncertain descriptor close
refuses the whole output. The existing launch owner permits sampling once by
its correlated receiver and retains cleanup uncertainty separately from the
first operational failure.

This is structural, non-atomic evidence. Equal streams do not establish one exec
generation, an atomic memory snapshot, mapped byte identity or loader closure.
No memory descriptor, production JNI, CHALLENGE or client admission is added.
See the [private contract](../../backend/tools/guarded-supervisor/docs/privacy-maps-v1.md).

## Fixed review and actual checks

Author eight-file manifest SHA
`ae311cdfd7177a6fbe726f04f936473efc88edb13709ffa79d9a0528acd0b02f`,
base `a0cd6bb`. Lead independent report SHA
`43b1fdc230d3bf71e4760120de277891bc854a875e814a575424561379f5bac3`.
Reviewed integration twelve-file manifest SHA
`2af9f4d50a099eb4757c677d62cd58cd80c74f4a1ddc405308e96c17111009d0`
adds four independent test families and clarifies two responsibilities: the
raw caller retains CLEANUP, and the launch owner persists its latch; the shared
EINTR allowance is32 retries, with the33rd interrupted read refusing IO. One
source comment was corrected; behavior stayed unchanged. Existing launch/JNI
test edits only add the new C compilation unit to their source lists.

Actual Maven3.9.16/JDK21.0.12/GCC13.3/Linux checks:

- Initial meaningful RED reached the sampler's PLATFORM scaffold with an actual
  owned child, kernel peer and known file mapping:1 assertion,0 errors. The
  launch-wrapper RED similarly reached its scaffold after actual correlation.
  Earlier FORTIFY test compilation failure was setup, not behavior evidence.
- Author focused36 pass:11 maps families,16 launch families,5 existing independent
  launch families and4 sibling checks. Actual split/nonzero-offset, deleted,
  newline/non-UTF8 and anonymous executable mappings stay visible. An acknowledged
  child mprotect between reads changes the sample and refuses. Synthetic streams
  cover exact4096 records,256KiB and8192-byte lines, one-over limits, grammar,
  fragmentation, EOF, process death, cancellation and close uncertainty.
- Author full1,271 pass (293 core,7 parser,675 server,296 supervisor),0 failures,
  errors or skips; assembly/hostile distribution pass,10 September00:01:08BST.
- Independent focused40 pass,0 failures/errors/skips,10 September00:02:01BST.
  Actual MAP_SHARED/PROT_NONE and read/write ranges retain known file metadata.
  All16 permission combinations and unsigned numeric maxima are exact. The
  retry limit spans both streams. Second-close uncertainty dominates cancellation;
  repeated cleanup cannot close a different descriptor reusing the exact number.
- Eight author and three independent mutants compile and fail actual native
  assertions; restored controls pass. Independent changes reset the retry counter,
  lose cleanup dominance or omit refused-output wiping. No setup failure counts
  as a mutation kill.

The reviewer initially selected nonexistent sibling tests twice; Maven's zero-test
gate correctly stopped before native execution. An eight-byte opaque label was
then incorrectly expected to have nine bytes in the reviewer oracle. Correcting
that test produced the independent pass above; no production defect was inferred.
Author test-only varargs forwarding, output cleanup and cancelled-peer close
expectations were also corrected without weakening production controls.

Combined twelve-file verification, including all four added independent cases:
`mvn -B -ntp -f backend/pom.xml verify` passes1,275 tests
(293 core,7 parser,675 server,300 supervisor),0 failures/errors/skips;
assembly/hostile distribution pass,10 September00:07:35BST. No newer OCI or
deployment evidence is claimed by this record.

All processes and mapping fixtures are independently invented in owned0700
temporary directories. No raw mappings, addresses, private process data or
application configuration are printed. Injected I/O schedules supplement actual
operations; they do not prove interruption of stalled kernel calls. The complete
output is bounded below576KiB, with separate small comparison scratch; this is
not qualification of the whole runtime's1MiB budget.

Business: runtime client availability remains disabled. Engineering/security:
opaque labels confer no path authority and borrowed peer identity is not an mm
pin. QA/RST covers complete EOF, numeric limits, mixed cancellation/cleanup,
once-only receiver ownership, concurrent close and exact descriptor reuse.
Trusted installation, exec quiescence, mapped bytes/loader closure, production
JNI, privacy admission, actual client/readback and combined resource qualification
remain required. A future memory descriptor must close conclusively before
CHALLENGE; suppression does not revoke it. No parallel speed-up is claimed.
