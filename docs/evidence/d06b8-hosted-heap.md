# D06b8 retained drafts through hosted HTTPS

The exact c4a3246 hosted application passed the four-plan unresolved/entered-value
workload under a 6 GiB memory limit and one CPU, including real HTTPS body handling,
workspace publication, large responses, validation and cleanup recovery. Unread
TLS responses held shared view capacity until deadline, disconnect or logout;
each case then recovered it. This qualifies the measured shape, not the complete
deployment memory/support matrix. No allocation or release flag changed.

## Runtime and boundary

Source: `c4a324636d4aeaa20012349e68cce7eb2b025f8b`. Local image ID:
`sha256:64176f7b650f23e25d2bf1759e450f8305984edc788350e75c626c7ec20f0b75`.
The [artifact record](d06b4-artifacts.md) contains its build and browser evidence.
Extracted app.jar SHA-256:
`5fca94d00704caf57814c9ee8bf2d3a01491381121b1484d65a8bfd0b546dddf`.
The reviewer compared all 345 extracted production class/library entries with
that jar, and independently recompiled seven harness sources: all 13 generated
class files matched the frozen candidate byte for byte.

The owned container runs the image's Java21 with MaxRAMPercentage65, exit-on-OOM,
disabled heap/fatal-error dumps, read-only filesystem, no capabilities,
no-new-privileges, 128 PID ceiling, equal 6 GiB memory/swap limits and one CPU.
The runtime and external mock classes are mounted read-only. The image's non-root
user starts the real Spring application; only the observation port is replaced
with an independently invented completed observation. Real workspace storage,
publication, sessions, HTTP controllers, command parsing, projection, planning,
validation and response ownership remain active. This is not JDBC qualification.

HTTPS uses an ephemeral localhost certificate and verified client trust. The
separate mock OIDC issuer and client run outside the measured container. An owned
loopback control port supplies passive aggregate heap/cgroup/GC measurements; it
does not expose plan content or bypass product authorization. The first attempted
MVC metrics route was correctly refused403 by the product's closed route policy;
moving test measurements to the separate control port preserved that policy.

Workspace, TLS private material and captured application logs stay in owned
container RAM. Only public certificate material, aggregate counts, safe codes and
independently invented fixture definitions leave it. Database credential canaries
and transient TLS/platform secrets are checked against captured application logs.
This harness does not establish a complete trace of every OIDC token or all
credential lifetimes; the separate browser/privacy gates retain their scope.

## Legal workload and actual witnesses

Each owner uploads and publishes a closed definition through the actual workspace
routes, with an explicit deny export policy for every declared document. Inspection
uses a fresh operation reservation and the actual credential HTTP boundary.
The real byte compiler and projection accept two documents, 20,000 entities,
256 declared fields and 256 optional reference relations. Current XML supplies
only each identity field and no reference edges. This is a valid unresolved-draft
dimension, separate from maximum source bytes, dense attributes and 50,000 edges.

Each plan receives eight 2,500-entity commands, each **36,945,126 bytes**, explicitly
setting every field and reference to unresolved. A further **33,569,419-byte**
command replaces 16 fields on one entity with independent 1 MiB quote strings.
Four plans retain **64 MiB entered values** in total before any large response is
read. All 36 commands pass actual revision advancement and admission, totaling
1,316,521,708 command request bytes. Targets remain incomplete and export unavailable.

Each plan then serves its first 100 draft decisions over verified HTTPS. The client
receives exactly **36,721,221 bytes**, checks Content-Length and parses the JSON.
The corrected witness checks all 256 field IDs/states and 256 reference IDs/states
on every returned decision, all 16 exact entered strings, masking flags, revision,
pagination and the 20,000-decision total. These are sampled pages, not an exhaustive
readback of every retained decision. Actual validation must include exactly all
ten required checks and the one declared count rule, all UNKNOWN, with matching
input fingerprints and no export capability. Empty result arrays cannot pass.

After all four normal responses/validations, the client requests a further large
page but reads only HTTP headers over a socket with a small receive buffer.
TLS may retain its current decrypted record. A different plan must return429 for
its document view, proving the real response still owns shared capacity. Three
separate attempts then exercise:

| Trigger | Actual result under full retained scope |
| --- | --- |
| Leave TLS response unread | Observer recovered200 after 30.413 seconds from request start; client stayed connected |
| Close the TLS connection | Observer recovered200 after 0.127 seconds from trigger |
| Logout while response remains unread | Observer recovered200 after 0.123 seconds; old authenticated request refused401; fresh same-owner login saw retired plan404 |

The deadline and disconnect cases leave all four plans retained. The logout case
starts with all four retained, then retires its owner. Remaining owners are logged
out; four fresh same-owner sessions each create and inspect a new plan, then logout.
This proves fresh inspection capacity recovery, not recreation of all large drafts
and 64 MiB entered values during the recovery phase.

## Measured results and review correction

| Run | Outcome | Passive measurements |
| --- | --- | --- |
| Initial full hosted shape, normal transfers | PASS, 356.899 seconds before cleanup | Peak cgroup4,477,931,520 bytes; final reported GC116,568ms; no cgroup OOM |
| Corrected witnesses plus three adverse TLS transfers | PASS, 388.342 seconds before cleanup | Peak cgroup4,460,826,624 bytes (4.15 GiB); final reported GC118,618ms; no cgroup OOM |

Both owned servers stopped with container exit143 after successful Docker stop;
both server and issuer reported complete cleanup. The corrected result has no
cleanup failures. Heap maximum was 4,048,420,864 bytes; the unread-response sample
reached 3,982,045,080 bytes used. GC accounts for about30.5% of the corrected run's
reported elapsed time. This is a responsiveness concern, not a latency guarantee.
The final passive heap sample remained 3,223,170,856 bytes: successful ownership
release does not imply immediate collection, erasure or return of memory to the OS.
No System.gc is invoked in the measured hosted path.

Independent review found a harness fault: a Docker-stop TimeoutExpired escaped
the original finally block before issuer cleanup and result persistence. A
controlled failure against the frozen original main reproduced that omission
with an assertion failure. The corrected runner independently attempts cleanup
for every owner, records fixed refusal codes and writes passed=false with exit1
when cleanup is unconfirmed. Both root and the reviewer ran the adapted control;
issuer termination and durable safe refusal then passed. The original candidate
and results remain preserved. This was a harness correction, not a product RED.

The corrected runner's 900-second budget is checked before admitting each client
call. Socket, startup, metrics and cleanup have separate bounds; it is not a proven
900-second wall-clock watchdog. Logged response durations exclude subsequent
validation work. Reported run durations include startup and workload but stop
before final resource cleanup. No reverse proxy, actual database, largest possible
response, all observation overlaps or full graph/source combination is established.

## Reproduction and fixed evidence

External lab: `/home/tim/.tmp/es-hosted-heap-20260909`. Actual commands:

```
python3 /home/tim/.tmp/es-hosted-heap-20260909/run_hosted.py unresolved-cap full
python3 /home/tim/.tmp/es-hosted-heap-20260909/run_hosted_corrected.py unresolved-cap full
```

Each result.json records the complete Docker argument array and exact image.
The successful run directories are `es-hosted-heap-c980da9e1a` and
`es-hosted-heap-bb8a6fe7b6`; their containers and issuer processes were closed.
Original 21-entry source/class manifest `hosted-scope-candidate.sha256`, SHA-256:
`36fcb0fd9192fe9fb83d384b47b656072b056b5ceae49d718e0ff741431d255b`.
Corrected 21-entry manifest `hosted-scope-corrected-candidate.sha256`, SHA-256:
`ee9ff01acfb5dd646aea8bfc558cf1c264a740347d6c17b2964b97ecde28e5a9`.
Nine-entry results/evidence manifest `hosted-unresolved-evidence.sha256`, SHA-256:
`7d3aab0078062b2387b2b8bf9fd5603e3edd8d0ab7c574ce8571e599b46cc9a9`.
Independent review and failure controls remain in
`/home/tim/.tmp/es-hosted-heap-review-k5aoqbwh`.
The reviewer verified all nine final evidence hashes and the corrected completed
run, accepted its stated bounds and reported no remaining concrete finding that
invalidates this exact workload.

Business: the 1 GiB deployment example remains unsuitable; no newly qualified
allocation is advertised. Engineering: these checks include the actual HTTP
admission and encoding overhead missing from the earlier application-only probes.
QA/security/operations: sampled semantic witnesses, real backpressure, explicit
cleanup refusal and fixed artifact identity support this slice; additional source,
graph, observation-overlap and complete export/runtime qualification remain active.

For this documentation commit, root reviewed the complete staged diff for provenance
and cumulative disclosure. Only public contracts, independent mock workload bounds,
aggregate results and exact public code/artifact identities are included. Repository
integrity, staged-content guard, diff whitespace check and all 11 Python tooling
tests passed. No production code or scope limit changed in this evidence update.

## Complete source, field and graph workloads

The same exact c4a3246 production artifact, actual HTTPS/OIDC routes and 6 GiB /
one-CPU container completed three separately shaped workloads. Each retained four
current and changed targets, reinspected/re-edited them serially, then created
four fresh-owner plans with full changed-target recovery. All current and target
XML characters were compared through actual Raw responses with independently
constructed expectations; these were complete inventories, not sampled pages.
Each run recorded twelve observations, zero OOM events, both cleanup markers
true and no cleanup failures.

| Shape per plan and per current/target side | Seconds | Peak cgroup bytes | GC ms |
| --- | ---: | ---: | ---: |
| 128 documents, 16 MiB strict UTF-8 each side; 128 explicit one-character identity edits | 48.405 | 630,370,304 | 1,014 |
| Four documents, 1,536 entities with 256 present fields; 3,393,172 bytes each side | 71.770 | 1,625,870,336 | 12,995 |
| Eight documents, 20,000 entities and 50,000 edges; 1,140,296 bytes each side | 408.123 | 828,235,776 | 9,109 |

Actual complete edit commands were respectively 24,830, 12,653,694 and 8,870,126
bytes. Graph identity edits explicitly rebound every affected reference to its
original stable target; retained-reference identity was not silently rewritten.
Final passive Java heap values were 267,655,120, 1,184,644,632 and 316,760,496
bytes. No requested GC or immediate memory-erasure claim applies.

Source/result identities in `/home/tim/.tmp/es-hosted-heap-20260909`:

- Source run `es-hosted-heap-68c82fa6f2`; source22 manifest
  `hosted-source-target-candidate.sha256`, SHA
  `886d7b2161d14adf20e83f329afb8676d7dd77beac500a6cacec4a0e1abdc8fe`;
  result3 manifest `hosted-source-target-evidence.sha256`, SHA
  `2a31a5fdfa0806ea540210ffb4ca6c4e9ca082c08ec46b4d966c59753e3c18ce`.
- Dense run `es-hosted-heap-6606f6d85b`, graph run `es-hosted-heap-c8c1f236bb`;
  source23 manifest `hosted-graph-dense-candidate.sha256`, SHA
  `3629ea0899fe6528aae383b729b5296625a4f1f208ea8a05ead4ec7f6b95f348`;
  result5 manifest `hosted-graph-dense-evidence.sha256`, SHA
  `117c622d11948f264d94f08398322b036fae7db3582e84480441dd9718dfd8bc`.

Independent review accepted the frozen harnesses and all three completed results
within these separate workload bounds. These runs do not combine every maximum.

## Four incoming observations overlapping retained targets

A separate external lab, `/home/tim/.tmp/es-hosted-overlap-20260909`, retains the
same production code. Its mock observation holds actual source collections and
accepted graph projections at a four-arrival latch before conversion/installation.
Reachability fences retain both through the wait; an absolute ten-second hold,
twenty-millisecond cancellation polling and independent loopback release bound
this test point. Only harness classes/control routes changed. Independent review
read both complete Java harness classes and verified all 345 production files
byte-identical to the previously jar-verified runtime.

The original run falsely required `inspectionValid=false` during reinspection.
An actual independent core held-read test confirmed the contracted old validity,
revision and content remain until success or failure. No product change was
needed. Original failure `315ef09b2a` and the first corrected runner's optional-key
setup failure `5fbfd9e3f5` remain preserved with completed cleanup. The final separate
`run_overlap_corrected2.py` compares prior validity/counts/revision and each plan's
own active operation. It handles an omitted inactive operation ID explicitly.

| Separate shape | Run suffix | Seconds | Peak cgroup bytes | GC ms |
| --- | --- | ---: | ---: | ---: |
| Full source bytes | `770ef4942c` | 37.411 | 705,650,688 | 1,155 |
| Dense present fields | `e28b0b05e6` | 49.761 | 1,832,452,096 | 9,339 |
| Full entity/edge graph | `3d9484d9b9` | 264.362 | 846,327,808 | 7,175 |

Each passed with four incoming projections held while four old current/changed
targets remained inspectable. The byte shape holds 64 MiB incoming source beside
128 MiB old current/target character content, counted as strict UTF-8 content,
not actual JVM object allocation. After release, every operation succeeded,
revision advanced once, and the complete no-op inventory had equal current/target
digests. Four subsequent changed targets passed full independent XML witnesses.
Every run had eight observations, zero OOM events, both cleanup markers true,
no cleanup failures, Docker stop exit 0 and terminated container exit 143. Runs
were sequential and cleanup completed before the next run.

Frozen source26 manifest `overlap-corrected2-candidate.sha256`, SHA
`61a473c3085baf3938316d4247eb41d4ed4bd6dcc0192a0acf7faa96496a14f4`;
results8 manifest `overlap-corrected2-results.sha256`, SHA
`9bc07ac3e8720325be53c035a1bfa63d6dc1489877fbe38dd9d0c59dc8a1dfec`.
Root independently verified both manifests, corrected Python source and result
witnesses. The other reviewer verified the Java hold, production identity and
actual prior-validity control. Final passive heaps were 378,798,480, 1,242,120,488
and 342,905,416 bytes. The latch is not evidence for cancellation races at release,
all simultaneous server phases, combined maxima or immediate heap return.

These are exact older-artifact workload results. They do not qualify subsequent
child-property/derived mechanisms, native-client export or a new deployment
allocation. The full resource and operator workflow qualification remains active.
