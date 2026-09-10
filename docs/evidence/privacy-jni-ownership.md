# Native JNI launch ownership

The private production bridge now retains bounded opaque launch tokens, original
launcher/receiver ownership, JNI references and native cleanup across real FORK
registration. The standalone runtime remains unqualified: production compiled
installation tables are empty, identity returns INSTALLATION, and no
CHALLENGE/FinalAdmitted or client admission is enabled.

## Fixed candidates and integration

Original author base `bd30c53456a1e6410bd4c8e2308127f9fc97c03d`; candidate1
21-file manifest SHA256
`9e4ae7b53b4a81833f543a631b0448ffdedaa36a9f35ad126991be9f8c2c1955`.
Its writer checkpointed and relinquished ownership before the exact candidate was
transferred to IDE2 from `d7c484425a3dcc88e26525a7e5041f41c83d056d`.

IDE2 returned fixed commit `f5025cc5afbdca2df70fa142be77f02aac4221ef`, same parent,
with exactly five implementation/test corrections and sixteen unchanged candidate
files. Manifest SHA256
`ae5b3468c4ddcc03ca6bed933f2a9ee4b2d5cb5ed4d194c21293b6d234012dab`;
patch `7e3a00a54e73c3fc4867e6f0eb2ca5a8f3ce1fe6fc678e1b8135883332f50774`.
All21 integrated hashes match. Root integrated over `bbaf8a3`, which already
contains the reviewed profile workflow, paged validation and typed browser client.

The two carried contract changes retain the native openLaunch entry time across
registry/record lookup. Both original deadlines include that work. Java supplies
only a bounded remaining duration, never an absolute native clock or admission
record. Existing es_launch_open delegates through the same trusted native helper.

## Confirmed review correction

Independent candidate1 review reproduced an Opened result after its original
startup allowance: actual JNI result construction held250ms with100ms remaining
returned success/STARTING. A later arm still refused; this was not FinalAdmitted
or credential exposure. Candidate2 checks the original startup deadline under the
registry mutex after JNI allocation and before publication, retains DEADLINE and
closes the still-owned unpublished launch. Neither clock is reset.

The correction's preserved RED has one actual assertion failure after successful
compilation/prerequisite tests. Its direct expired probe fails and unexpired
control passes. GREEN covers100ms/250ms and12s/10.25s expiry, distinguishing the
10s startup from the longer operation allowance;3s/250ms succeeds. All four launch
descriptors close, references drain, the unpublished token retires without reuse
and four live slots recover. Expiry exhausts the original namespace-cleanup budget:
one socket directory remains until the external fixture observes JVM exit and
removes only its own paths. Native cleanup stays INCONCLUSIVE permanently.

Fixed non-author reviews are `es-review-jni-candidate1-handoff-20260910.md` and
`es-review-jni-candidate2-20260910.md`. Candidate2 review verified all21 hashes and
the exact five-file delta, independently rebuilt production JNI and passed eight
isolated modes. The original reviewer reproduction now reports
`EXPIRED_OPEN_PUBLISHED=false`, exit0. No additional confirmed finding remains.
Combined expiry plus refusal-allocation failure was source-reviewed, not newly
fault-injected by that reviewer; broader allocation/finally controls remain in the
author gate. Review evidence is not installed-runtime qualification.

## Actual gates and resources

JDK21.0.12, Maven3.9.16,10 September2026:

| Check | Actual result |
| --- | --- |
| Original candidate author full gate | Java1596 and distribution PASS on its earlier base |
| Original non-author focused gate | Java79/distribution and256-token lifecycle control PASS; late-open P2 separately reproduced |
| IDE2 correction RED | Assertion failure, preserved exact source/commands;13.353s |
| IDE2 focused/full GREEN | Java80 focused; full1617, zero failures/errors/skips; distribution PASS |
| Corrected independent native controls | Eight modes plus original reproduction PASS |
| Lead integrated Maven verify | Java1617:330 core/7 parser/947 server/333 supervisor; zero failures/errors/skips; distribution and hostile launcher PASS |
| Existing reviewed client gate | Frontend65/schema58/check/build PASS; no UI added by native integration |

The integrated exact-source archive is `es-jni-integrated-5cc58u5u`; full command
`mvn -B -ntp -f backend/pom.xml verify` passed at13:26:20BST in4m24s. Log
`es-jni-integrated-full1-20260910.log` SHA256
`9026b792a436fc20adbc8b71932800e0230dea8e23a20a0cff422a6bf722af80`.
The packaged web JAR and supervisor ZIP contain no probe/fixture or Graal/Truffle/
Polyglot entries. ZIP SHA256
`31eaeb602855c9bd396e3d8c9dd6f38a942ee4e783be6854ac4c4c376861105a`.
An initial inventory command used a nonexistent conventional JAR filename; the
actual environment-studio.jar was then inspected successfully.

Original author controls include actual native arm contention, positive no-window
finally handling, wrong-thread/duplicate calls, held JNI refs, linkage/allocation
failure, once-only cleanup and descriptor reuse. Six cleanly compiled isolated
guard mutations failed. IDE2 added three compiled assertion-killed mutations:
omitted publication deadline, substituted operation deadline and omitted
unpublished cleanup. Originals were restored. The first author's preserved RED
only proves opening through a minimal scaffold; its temporary source/full command
was not retained. It is not a complete immutable FORK RED record. IDE2's later
correction RED is separately preserved in full.

Native retention measures2240 bytes per owner and573512 bytes registry bookkeeping,
with four live/256 issued entries and retired refs drained. These are component
measurements, not whole-JVM/allocator/thread-stack or installed workload capacity.
No databases/containers were assigned. Every writer used separate build outputs;
expensive builds were serialized. Original fault/setup namespaces were preserved.
Author freeze took39 minutes; IDE2 full4m23s and lead full4m24s are measured command
durations. Rework was not separately timed; no parallel speedup is claimed.

Business review checked that internal success cannot admit a client. Engineering/
security review examined clocks, token/reference lifetime, lock order and cleanup.
QA investigated late allocation, exact descriptor reuse, retained uncertainty and
capacity recovery. These local RST investigations do not replace operator rehearsal.

## Remaining qualification

Mapped bytes, loader/constructor/graph closure, exact installed bootstrap and
clients/orapki, crash collectors, native credential operations, combined resources,
hosted export/readback, actual OCI candidate and HiveForge remain required.
The retained c3b891a image predates these changes. All eleven release capability
gates remain NOT_RUN; no runtime record or release approval is created here.
