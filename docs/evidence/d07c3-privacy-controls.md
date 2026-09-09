# D07c3 native privacy controls — reviewed local primitive

This credential-free slice implements only the reusable native suppression
primitive specified by the private privacy ABI. It does not install a library,
wire runtime admission, add registry entries, implement exec identity receipts,
or qualify native database authentication.

## Fixed inputs and behavior

Base: `78c48602b35bf91583c19231f4e9935059608a05`.
Context-only reviewed contract SHA-256:
`422ad6de0918c8402cdd9203b37343477922be8d1efd47d72305b04145baff5e`.
Context-only reviewed private ABI SHA-256:
`26f7eac30522cd2fa55a2c2ddcfe9ea8d0a2402d4cea6a08e66bd0a2bd8968f4`.
Neither context file is part of this authored delta.

On Linux amd64 LP64, the primitive establishes dumpable zero and NO_NEW_PRIVS,
installs the reset filter with TSYNC, then checks actual reset refusal and current
dumpability. Only a zero TSYNC return succeeds; a positive offending thread ID
refuses. Failures return fixed ABI-aligned codes and emit no diagnostics. Partial
suppression is never undone or converted into admission.

The filter kills incompatible audit architectures, refuses the x32 syscall bit,
and refuses every nonzero 64-bit PR_SET_DUMPABLE value with EPERM. It matches the
prctl option's low 32 bits, as consumed by the kernel. Setting dumpability to zero
remains allowed. The unsupported compilation branch returns PLATFORM.

## Actual checks, 2026-09-09

Tooling: pinned Maven 3.9.16, installed JDK 21, host GCC
`13.3.0-6ubuntu2~24.04.1`, Linux `7.0.0-31-generic`. Host compiler execution is
local test evidence; this is not qualification of a final distribution closure.
JUnit compiles the exact production C with strict warnings into owned mode-0700
`/tmp` scratch, removes its generated artifacts, and runs credential-free native
and JNI probes in separate bounded processes. No production build manifest was
changed and no binary was committed.

Actual RED used a callable stub returning success without installing protection.
All six initial cases failed assertions, with zero setup/runtime errors:
reset controls, fork inheritance, x32 refusal, incompatible architecture kill,
divergent existing thread refusal, and existing/future JVM thread coverage.
After implementation, all six passed. A seventh high-option-word reset control
also passed in the full run. Values exercised include 1, 2, high-word-only,
high-word plus 1, and all bits set. The JVM probe creates a worker before JNI
installation, checks that worker after TSYNC, and checks a newly created worker.
The JNI test library has no preload constructor.

Focused command (RED and initial GREEN):

```sh
mvn -B -ntp -f backend/pom.xml -pl tools/guarded-supervisor -am \
  -Dtest=PrivacyControlsTest,FifthEditionClassifierTest,NativePublicationTest,PackageAdmissionTest \
  -Dsurefire.failIfNoSpecifiedTests=false package
```

Initial focused GREEN: 7 core + 1 parser + 8 server + 6 supervisor tests passed;
package assembly passed. Actual full `mvn -B -ntp -f backend/pom.xml verify`
ran 526 tests: 136 core, 7 parser and 328 server passed; supervisor ran 55 with
one failure in unchanged `TerminalHelperTest.boundedNativeTerminalEntryPreservesExactState`.
All seven new privacy tests passed. The terminal assertion reports Python probe
exit 1 and discards its captured diagnostic. Three subsequent independent direct
runs of that unchanged terminal probe, with only external safe line-number
failure reporting, passed. Cause remains unresolved; this is not a full verify
PASS, and the follow-up does not establish that the failure was harmless.
No terminal source/test changes were made in this slice.

Four external source mutants were independently compiled and run, all killed:

| Removed control | Actual refusal/failure | Required healthy result |
| --- | --- | --- |
| TSYNC | JVM probe exit 41 | exit 0 |
| High value word | native probe exit 22 | exit 0 |
| x32 guard | native probe exit 25 | exit 0 |
| Architecture guard | compat probe exit 26 | SIGSYS |

All mutant child output lengths were zero. Credential-free strace controls
confirmed successful healthy/fork/high-option/x32 probes; an actual i386 `int 0x80`
probe was killed by SIGSYS. The divergent-filter trace showed TSYNC returning a
positive existing thread ID, followed by the expected typed refusal. Trace
inspection after dumpability suppression cannot decode every pointer; it is not
an executable-identity or library-closure proof.

`python3 scripts/check_repository.py`: PASS. This archive snapshot has no Git
index; staged content checks and independent review remain integration gates.

## Evidence and limitations

External logs under `/home/tim/.tmp/`:
`es-privacy-controls-red-20260909.log`,
`es-privacy-controls-green-20260909.log`,
`es-privacy-controls-full-20260909.log`,
`es-privacy-controls-mutants-20260909.log`,
`es-privacy-controls-terminal-followup-20260909.log`, and
`es-privacy-controls-repository-20260909.log`.
External owned mutation/trace lab: `/tmp/es-privacy-mutants-msj2y_1q`.
No private model, database credential, authentication, collector modification,
or shared runtime modification was involved.

No non-amd64 host, old kernel, final OCI compiler/runtime closure, heap/error/ADR
crash path, final executable identity, secure/static executable, socket protocol,
or fork memory ceiling is qualified here. Dumpability can reset on exec; this
primitive cannot replace the required fresh per-exec handshake. It grants no
release or native authentication readiness.

Exact five-file source candidate:

```text
36387fd21d7273b0db04cc56e7c83a2e166391c867738d85b4dbc9f12dfcab17  backend/tools/guarded-supervisor/src/main/c/privacy-controls.c
f10d38e9d89d441716658d8d170ee1a0147a5db6d76d2be316711fa60550fc4a  backend/tools/guarded-supervisor/src/main/c/privacy-controls.h
08d0c10149a03def5cc4842d22ef2358db152f24555896a51bfad6096f7ec021  backend/tools/guarded-supervisor/src/test/java/studio/environment/supervisor/PrivacyControlsTest.java
c4bd59dedc24bf6138b0f6345dfcf74aaec3892e06a02188a8bebad02a4f3de9  backend/tools/guarded-supervisor/src/test/c/privacy-controls-probe.c
06d7a31b366cec4f40338f2ddabf0a615cb552dd3cab41485ed25301068e59ab  backend/tools/guarded-supervisor/src/test/c/privacy-controls-jni-probe.c
```

## Independent integration

Root reviewed the exact five source/test files and fixed six-file candidate
manifest SHA-256 `27be03113868cd37d838e227a54049e543e7eb26722716c0d7aef431a784802c`.
The native/compat/x32 branch targets, full-width reset checks, TSYNC failure
handling and separation from runtime admission were accepted. No source correction
was required. The reviewed candidate was copied with hashes checked onto exact
`744e981` in `/home/tim/.tmp/es-privacy-controls-integration-20260909`.

Root ran the complete pinned Maven reactor there: **526 tests passed** (136 core,
7 parser, 328 server, 55 supervisor), including all seven privacy controls and
the unchanged terminal tests, plus package assembly and hostile-launch checks.
Log: `/home/tim/.tmp/es-privacy-controls-independent-full-20260909.log`.
This successful independent run does not explain or erase the author's earlier
terminal failure. No runtime registry, launcher, distribution privacy wiring or
release status changed; no new published artifact is claimed.
