# D06b5 — hosted heap investigation

The current 1 GiB Compose allocation cannot support one legal maximum-entity
draft. Increasing the allocation to 6 GiB passed the specific four-plan,
validation and revocation/reinspection workloads below. This is a candidate
allocation, not completed maximum-scope or transfer qualification. No scope limit
or release flag changed.

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

Still required: maximum retained current and independently changed target source
bytes; 64 MiB of entered values and their encoded expansion; the largest admitted
command/response shapes; simultaneous observation/projection scratch; old/new
replacement overlap; actual slow/blocked HTTP output, revocation, cancellation,
deadline and disconnect recovery; and whole hosted-process overhead. The 6 GiB
candidate must not be advertised as qualified before those checks and independent
review. Investigate the substantial one-CPU GC cost as part of responsiveness.
