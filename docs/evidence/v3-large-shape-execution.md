# Large v3 semantic workload execution

The previously reviewed scalable fixture now executes against the freshly built
Java classes of `505f815552d4439220cc17e5c1ce120cff0df535`. Lead-owned local
verification; no independent execution or production publication is claimed.

The original fixed probe is unchanged, SHA256
`b37d77f50a04ea32b3baa8da881880d9f9257e0fffab729296b0eb435a6a2b99`.
Its small control and first large execution both passed. The large case checks
complete original/target XML and pins, physical fields/origins, derived inputs,
ordered contributors, memberships, all rules and retained provenance:

- 128 documents;16,777,216 source bytes on each side.
- 10,000 physical and10,000 computed nodes;50,000 memberships.
- 100,000 contributor links;8,388,605 computed-value bytes;64,000 rules.
- Actual compiler remains Incomplete with MECHANISM_UNQUALIFIED.

One JVM used `-Xmx1024m -XX:ActiveProcessorCount=2`, with an external180-second
deadline and owned-process-group cleanup. Small elapsed0.265s; large elapsed6.032s
including launch. GNU time reported large peak RSS742,600KiB (about725MiB),
10.06s user CPU and0.29s system CPU. Both exited0; no timeout or OOM occurred.
This is process peak RSS, not retained heap, a container allocation or a native
memory attribution. The deployment MaxRAMPercentage setting was not exercised.

External `/home/tim/.tmp/es-v3-capacity-current-20260911/result.json` records
exact compile/run commands, every input class/resource/jar hash, probe classes,
source identity and result hashes. All input hashes remained unchanged.
Large output SHA256:
`b4c936d8959a76e5281c19fc2fda8544c6695544e1671bf21e4935daad189219`.

This closes the single-large-shape execution gap in the earlier fixture evidence.
Four retained hosted owners, HTTP output/observation overlap, dense child-property
proofs, original operation deadlines, whole native/process memory and deployed
artifact capacity remain unqualified. No admission limit or release gate changed.
