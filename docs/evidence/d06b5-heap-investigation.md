# D06b5 — hosted heap investigation

The current 1 GiB Compose allocation cannot support one legal maximum-entity
draft. Increasing the allocation to 6 GiB passed the specific four-plan,
validation and revocation/reinspection workloads below. This is a candidate
allocation, not completed maximum-scope or transfer qualification. No scope limit
or release flag changed.

The entered-value extension also passed with the same 6 GiB/one-CPU candidate:
four full unresolved drafts plus 64 MiB entered values, bounded draft-page
encoding, validation and recovery. It does not qualify actual HTTP backpressure;
the separate socket investigation has reproduced an output recovery defect.

## Exact runtime and workload

Production classes and libraries were extracted from the reviewed
`1bda69993ff1257b063d08a357fd2b260d8be385` image, ID
`sha256:4932ea4452240df0570803e35c98a393fcce48fbc6ef71f2651766a64f1e7c71`.
Extracted app.jar SHA-256:
`dd7b6716e051fde41ca9af5fecbbb93bd72cbd0f4b99a7c8e9c2ce59815c68c9`.
The harness runs those classes using that image's Temurin21.0.12+8 JVM,
`MaxRAMPercentage=65`, exit-on-OOM and disabled heap/fatal-error dumps. Owned
containers use no network, read-only filesystem, no capabilities, no-new-privileges,
no Docker logs, bounded PIDs and equal memory/swap limits. Only invented harness
classes and the extracted artifact are mounted read-only. No private configuration
or credentials were used. The production web entry point is not started by this
application-level probe.

External lab: `/home/tim/.tmp/es-heap-qualification-20260909`.
The fixed `validation-probe-manifest.sha256` has SHA-256
`2aa702475732c53f9dff0b51e20f9efe55c5fe31a2393cf14520cc6c1e58ffd5`.
It covers five Java sources and their harness classes. Independent review checked
all 18 hashes and compared the final workload with public limits.

- `UploadableDefinition` supplies an independently constructed closed JSON shape
  through the production byte parser/schema/compiler. Its final unresolved-case
  definition is 136,636 bytes and returns ReadyToPublish.
- `ScopeWitness` generates two XML documents with 10,000 entities each. The
  declaration has 256 fields and 256 optional reference relations; actual XML
  contains only each entity's unique identity attribute and zero reference edges.
  Production projection accepts all 20,000 entities and 440,074 source bytes.
- `HeapCommandBoundary` streams one entity chunk at a time through the actual
  PlanCommandReader. Each batch explicitly supplies unresolved decisions for all
  256 fields and 256 references of 2,500 entities. Independent token traversal
  counts 36,945,126 bytes, 6,457,514 tokens and depth 6. Eight such batches build
  a full draft within the command identity budget. No entered values are present.
- `UnresolvedPlanProbe` uses the real HostedPlanService, projection and target
  adapters with an explicit mock completed ObservationPort. Four distinct leases
  retain four plans. Incomplete targets must remain incomplete.
- `UnresolvedPlanValidationProbe` additionally reserves a view and validates each
  fully populated unresolved draft; every check/rule remains UNKNOWN and export unavailable. It
  revokes all leases, then creates and successfully reinspects four new plans,
  and revokes those leases too.

The workspace publication is explicitly synthetic with an empty export-policy
list. No save/publish HTTP, actual JDBC, Servlet body admission/scratch reservation
or 30-second network-body deadline is established by direct reader calls. The
service still enforces retained state and command/materialization admission.
These distinctions do not invalidate the memory counterexample.

## Observed results

| Run | Outcome | Observed memory and timing |
| --- | --- | --- |
| Host JVM, 650 MiB heap, final uploadable definition and actual command parser | OOM after 17,500 entities; exit 3 | 24.570 seconds; log `uploadable-unresolved-plan-650m.log` |
| Exact image, 1 GiB memory, one plan | OOM after 17,500 entities; exit 3 | JVM heap maximum 675,086,336 bytes; 66.238 seconds; last completed-stage cgroup peak 847,540,224 bytes, no kernel OOM event at that stage |
| Exact image, 6 GiB memory, four plans, CPU quota unspecified | Passed all 32 batches and revocation; exit 0 | 86.583 seconds; retained heap 2,366,796,576 bytes; requested-GC snapshot after revoke 8,336,000 bytes; cgroup peak 4,458,496,000 bytes |
| Exact image, 6 GiB memory, one CPU, four plans plus validation/recovery | Passed all batches, four validations, revocation and four fresh reinspection plans; exit 0 | 206.452 seconds; heap maximum 4,048,420,864 bytes; cgroup peak 4,340,891,648 bytes; zero recorded kernel OOM events |

Exact argument arrays, timeout, observed exit and elapsed time are recorded in
`run-metadata.json`; these are retained orchestration observations, not independent
inspection of the already removed containers.
Runtime logs are `runtime-unresolved-1g.log`, `runtime-unresolved-6g.log` and
`runtime-validation-6g.log` in the external lab. The one-CPU run spent 112,941 ms
in reported GC by its final stage. Its post-recovery/revocation requested-GC heap
snapshot was 166,141,808 bytes. JVM committed memory/RSS did not immediately
return to baseline. Requested GC is a measurement point, not proof of complete
collection, memory erasure or return of pages to the OS. No responsiveness claim
follows from these timings.

Other final byte-compiler/projection checks passed independently under host650MiB:
128 documents/16,777,216 source bytes; four documents/1,536 entities with 256 actual
mapped attributes per entity/3,393,172 bytes; and eight documents/20,000 entities/
50,000 edges/1,140,296 bytes. These are separate valid shapes, not one combined
maximum. Their logs end in `-uploadable.log`. They do not test retained plans or
all admitted scratch combinations.

## Corrections and remaining qualification

Initial typed-only generators bypassed the byte parser. A four-document unresolved
definition later returned RESOURCE_LIMIT; this was invalid harness scope, not a
product defect. The final two-document form passes the actual byte boundary.
The earlier eight-document typed-only OOM is historical and is not public
uploadability evidence. Initial ambiguous Java imports and repeated projection
IDs were also harness setup failures, not RED behavior tests.

Still required: the largest admitted command/response shapes beyond the entered-value
page extension below; remaining graph/observation/projection scratch combinations;
actual slow/blocked HTTP output, revocation, cancellation,
deadline and disconnect recovery; and whole hosted-process overhead. The 6 GiB
candidate must not be advertised as qualified before those checks and independent
review. Investigate the substantial one-CPU GC cost as part of responsiveness.

## Entered values and response-page extension

The same exact image was run with 6 GiB memory/swap and one CPU, network none,
read-only root/mount, all capabilities dropped, no-new-privileges, 128 PID ceiling,
and no container log driver. The original invocation and observed completion are
retained in `entered-run-result.json` under the external heap lab. Exit 0 after
**265.982 seconds**; the orchestration timeout was 600 seconds and did not fire.

Each of four 20,000-entity drafts retained 256 field and 256 reference decisions per
entity. One existing entity per plan received 16 distinct 1 MiB entered strings of
XML-legal quote characters: **16 MiB per plan / 64 MiB global**. Each actual JSON
command was **33,569,419 bytes** and was decoded inside `reserveCommand` before
execution, qualifying that command scratch overlap. The earlier unresolved
2,500-entity batches still decode before `service.command`; they do not qualify
reserve-before-decode HTTP admission.

For each plan the real draft-page projection and encoder produced and transferred
**36,721,221 bytes** to a counting sink. These are the first 100-record pages,
including the selected entity, not complete 20,000-record draft responses.
The sink verifies emitted byte count; it does not prove socket behavior, semantic
JSON round-trip, client receipt or HTTP deadlines. No transport claim is inferred
from this measurement.

All four validations remained UNKNOWN/export unavailable. After revocation,
four fresh plans were created and reinspected, then revoked. The final
requested-GC heap sample was 27,352,016 bytes; cumulative GC time 126,212 ms;
cgroup peak 4,313,772,032 bytes, with no recorded OOM event. The large GC time
remains a responsiveness concern. These are samples, not proof of erasure or
return of committed memory to the OS.

The independent reviewer checked the frozen 25 source/class hashes, actual
compiler/projection/service/decoder paths, exact scope and result logs without
repeating the costly workload. No invalid combined maximum was found within
this workload. Empty mock publication policies, the completed mock observation
port, absent complete target/full-source maxima and absent HTTP transport remain
explicit limitations.

External artifacts in `/home/tim/.tmp/es-heap-qualification-20260909`:

- `HeapEnteredBoundary.java`, `EnteredValuesValidationProbe.java` and dependencies.
- `entered-values-probe-manifest.sha256`, SHA-256
  `d4a1c3537a761c274bb0e1d1aa95fb3ead209741ab9b86392d28e8136d338855`.
- `entered-boundary-control.log` for standalone actual decoder admission.
- `runtime-entered-values-6g.log` and `entered-run-result.json` for the exact runtime.

The deployment example still uses 1 GiB. Increasing it remains required, but 6 GiB
is not yet certified for the unfinished graph/scratch, hosted process, slow-output
and cleanup matrix.

## Retained current and independently changed target bytes

The same exact image, 6 GiB/one-CPU settings and protected invocation passed a
separate source-byte workload: four plans each retain 128 current documents and
128 independently changed targets, each document 131,072 ASCII bytes. The total
retained current/target source scope is **134,217,728 bytes (128 MiB)**.
The actual byte compiler accepts the 36,638-byte invented definition. Each
observation projects 128 glyphs with one declared identity field and no edges;
comment padding supplies the remaining bytes. This is a source-byte dimension,
not the maximum graph/draft complexity combined with it.

Each command reserves scratch before building and decoding its closed JSON body.
The real command reader, service and target adapter edit one identity character
per document. An independent witness checks every current/target character,
exactly 512 differences across the four plans, unchanged byte sizes and distinct
changed target strings. No typed complete target substitutes for materialization.

Four further semantic edits replace targets at full capacity. That path drops the
old target before materialization; it does not establish old/new target overlap.
Four separate reinspections construct a complete 16 MiB incoming observation while
the old 128 MiB current/changed-target scope remains retained. Scope witnesses
before replacement and counts inside the observation callback verify this overlap.
Each reinspection is edited back to a distinct same-size target before proceeding.
After revocation, four new plans are inspected and edited simultaneously, then
revoked. All exact witnesses pass again.

The final run exited 0 after **11.792 seconds**, with 16 successful edit commands
and 12 full-source observations. The maximum sampled cgroup peak was
**490,557,440 bytes**, with no recorded OOM event; aggregate GC was 481 ms over
64 collections. Requested-GC heap samples were 147,303,504 bytes at full retained
scope and 14,183,664 bytes after recovery/revocation. These samples do not prove
erasure, complete collection or return of memory to the OS.

The lead independently reviewed both new harness sources, the unchanged mock
generators, exact scope witnesses, actual command/target paths, invocation and
results. All 12 candidate hashes, 10 evidence hashes and 342 copied runtime hashes
matched. The costly runtime was not repeated without a changed workload or an
unresolved result. An initial wrong reserve-overload compilation was a harness
setup error, not a production RED.

External lab: `/home/tim/.tmp/es-target-heap-wvzeiess`.

- `HeapTargetBoundary.java`, `RetainedTargetsProbe.java` and dependencies;
  `candidate.sha256`, SHA-256
  `b97e29665c7fe00413cd30deb0de4ec1229da058d9586537beaf458abbb71729`.
- `full-result.json`, `full.log`, `review.md` and fixed runtime inventory;
  `evidence.sha256`, SHA-256
  `239deed324f677ee4f57ed1dccbc3a39f4047b4ea0a9d1274ddbb9a9d95c6005`.

The mock completed observation port and empty publication policies retain the
earlier limitations: no JDBC/TLS, workspace publication HTTP, Servlet/socket,
export or deployment qualification. This does not combine 20,000 entities,
50,000 edges, dense XML, 64 MiB entered values or maximum encoded responses.
The remaining whole-process and transfer evidence still blocks capacity claims.
