# Derived graph memory correction

Status: local source candidate under investigation; not release-qualified.
Lead owns implementation, tests and self-review. No new independent acceptance
is claimed. Base is `0abf932caf5bd6686af734e18d04a47663087622`.

## Required behavior

Four retained hosted plans must survive complete validation and owner cleanup
under the existing Compose limits: 1 GiB total memory, one CPU, 256 PIDs and the
image's 65% heap allocation. The external test has a 300-second deadline.
Each plan has 128 documents, 16 MiB XML per side, 10,000 physical and 10,000
computed nodes, 50,000 memberships, 100,000 contributor links, 8,388,605 identity
bytes and 64,000 computed rule results. All complete XML, graph, field-role,
revision, pin, ownership, capacity refusal and cleanup assertions remain required.

The source correction shares immutable computed keys within one evaluation and
contributors within one entity, indexed by the exact declared field identifier.
A private node bucket retains the canonical key. Identical String objects compare
equal immediately; other strings retain strict Unicode scalar ordering. Logical
budget charges, separate ordered co-occurrence roles, cancellation and complete
proof checks remain intact. There is no global interning or value normalization.

## Actual evidence

- Exact image `505f815` fails the original large test with Java heap exhaustion
  after 156.119 seconds. Small control passes in 3.074 seconds. A stage-only repeat
  fails after 157.118 seconds at backend validation of the fourth plan, after the
  independent current/target reprojection checks. Docker reports no kernel OOM
  kill. These results are in `es-v3-hosted-shape-oci{,2}-20260911` externally.
- An experiment shortening temporary original-projection lifetime still fails
  after 159.107 seconds. Its 39 focused tests passed, but the source was reverted
  and preserved externally in `es-v3-hosted-shape-lifetime1-20260911`. An earlier
  incorrect Maven selection ran no core tests and was correctly refused.
- Sharing experiments 1, 2 and 3 avoid the former observed OOM but reach the
  unchanged 300-second deadline during final verification. All exit 143 and are
  failures. Their external directories are `es-v3-hosted-shape-sharingN-20260911`.
  Experiment 3 includes GC counters and three method-only thread samples; no heap
  dump, input content or session material was recorded.
- Current source passes 106 affected Java tests: 50 core, one parser and 55 server.
  Log: `es-v3-graph-sharing-focused3-20260911.log`. The strengthened literal oracle
  verifies distinct field roles when field values are equal. A compiled wrong
  value-based cache key is detected: fixed exit 0, fault exit 1. Manifest:
  `es-v3-graph-sharing-role-control3-20260911/result.json`.

The current resource comparison changes only the external oracle's decimal
formatter from String.format to explicit nonnegative decimal zero padding.
Exhaustive equivalence over 0..10,000 and widths 1..6 passes 60,006 cases.
Original oracle SHA256:
`b37d77f50a04ea32b3baa8da881880d9f9257e0fffab729296b0eb435a6a2b99`.
Optimized oracle SHA256:
`2c50809b2844cc9d87e963f99bea3c27a4d79f5fcd7d9d58b8bc556935628f38`.
All expected content, workload, assertions, limits and deadlines are unchanged.
This does not turn the earlier timeouts into passing evidence.

With that same optimized oracle, the original image still exhausts its heap
(exit 3 after 137.112 seconds). The corrected classes pass in 248.133 seconds, including all 19 complete
verification passes, capacity refusal, foreign-owner refusal, held logout,
same-owner recovery and final cleanup. Both containers are removed and all
bundle hashes remain unchanged. The production export refusal remains intact.
Results and exact commands: `/home/tim/.tmp/es-v3-hosted-shape-oracle4-20260911/`.
Current source SHA256:
`3c7116696663779b6e8639401027e14699a83b7dec66b1955a6ba23b50e68ca1`.

All diagnostic overlays use the original image's JRE, application classes and
libraries with only the explicitly recorded replacement classes. Original image
and extracted bundles remain unchanged. One external compile omitted dependency
jars and failed before execution; the corrected compile includes the exact jars.
Owned containers are stopped and removed by the runner. Application cleanup is
not claimed after VM termination or an interrupted run.

## Limits and next gate

The earlier host 1-GiB-heap/two-CPU result does not qualify a 1-GiB-total deployment.
These hosted lifecycle checks use explicit mock publication/observation ports.
They do not qualify simultaneous database buffers, HTTP encoders, native tracing,
compiler publication or production export. The next gate is the immutable candidate OCI build, which runs full Maven,
frontend tests/check/build and package checks, followed by protected startup and
the same resource probe against the new image. A duplicate full host Maven run
is not required by G01; focused host checks above remain separately recorded.

## Immutable candidate integration

Source `8b85011d6a2a4860f1f3cef6204ac2944594d6fb` passes the combined OCI build:
1,724 Java tests (335 core, 7 parser, 1,039 server, 343 supervisor), 446 frontend
tests, 61 schema tests, TypeScript/format checks and production build. Protected
container startup/static UI/health/denials, private workspace schema initialization
and upgrade refusal checks pass. All 29 supervisor package checksums pass.
Build context is an exact Git archive; no untracked inputs were included.

Local image:
`sha256:d381f17ecd29c27d717f526e5f3952246c3363432067abeec4f0927e7a3f3f2e`.
Its revision label matches the source and runtime user is `10001:10001`.
Actual commands, log hashes and results are in `es-graph-memory-oci-20260911`.

The same full hosted lifecycle probe passes against that image's actual classes,
libraries and JRE, with no replacement classes: 252.123 seconds, exit 0,
complete owner cleanup and unchanged input bundle. Container removed; no kernel
OOM. Result: `es-graph-memory-image-resource-20260911/result.json`; log SHA256
`289246ea9162a8f78d23a38f66b65482c9ba8dac5730f9c853f83755a1c08ba8`.
The entrypoint runs the explicit mock hosted probe, so full HTTP/database/native
resource combinations are still outside this result.

Lead source review found no confirmed defect; all new results are author-run.
This accepts the correction as a locally integrated prerequisite, not production
admission or release qualification. Release readiness still refuses: its source
fingerprint does not match and all required release capabilities remain NOT_RUN.
No registry publication, native ownership change or release setting was made.

Next independent resource check: prepare the existing unqualified package payload
while four large plans remain retained. Verify full original/target content and
canonical payload bytes, without granting download or execution authority.
