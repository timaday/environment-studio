# Physical HTTP sequential settlement correction

Base `824d5681bee9c236b3a620832f5cef15dd8874b3`, combined application integration.
Lead is the sole writer; native work and production behavior are unchanged.

The [independent TEST-QA-006 report](https://github.com/timaday/environment-studio/issues/9#issuecomment-5634498466)
reproduced the original Physical assertion failure on e4a41e5: revision3 current
entities returned200, then documents expected200 received429/CAPACITY while the
first original worker was held after core cleanup but before workerClosed.
Its same-lease semantic record remained active. Releasing that worker and awaiting
registry zero restored the identical documents revision/digest oracle. This is a
legal controlled schedule, not proof of the exact unscheduled OCI timing or a
production leak. The original failed OCI and controlled assertion are RED evidence;
no new local production RED is claimed.

Acceptance: ordinary sequential setup, physical pages, commands and refusal
assertions use the existing bounded original-registry-zero witness. Preserve all
status/body/digest/revision/privacy assertions. The partial-body contention test,
its second-owner429, metadata availability, raw logout and original worker recovery
remain unchanged. No retry, accepted429, sleep, increased capacity or production
lifetime change. See [transfer ownership](../contracts/plan-transfers-v3.md).

The class-local helper follows the already reviewed Materialization correction.
Teardown attempts all raw logouts even after a failed initial settlement, checks
settlement again, and preserves the first exception/assertion with later failures
suppressed. Privacy canaries remain required after conclusive settlement.

Author verification: fresh Java21 compilation and all3 actual HTTP/OIDC/private
SQLite tests passed,0test/container failures, with -Xmx1024m, isolated RAM
workspace/logs and ephemeral ports. Current test was compiled ahead of hash-verified
inherited production/fixture classes. Source and inherited hashes remained
unchanged. Commands and result are external in
`es-v3-physical-settlement-check1-20260911`; raw logs were hashed then removed with
the private RAM workspace. These checks preserve the ordinary page/mutation,
owner/version/CSRF/grammar and raw held-body recovery oracles. They do not establish
a local held-worker schedule, cleanup fault injection, independent acceptance,
full Maven/OCI or release qualification.

The non-author eight-class source audit is external at
`es-v3-http-settlement-audit-20260911.md`; it executed no tests. Command has a
related source gap and a separately assigned original-worker control; base Plan's
semantic-refusal loop is a source-only risk. Five already synchronized classes
remain unchanged. Fixed non-author source review found no confirmed finding against two-file
manifest `7b3d96caafacc3b9e1252369f41b2a804d1556054f33fe445bc72d9a736bad6b`;
report `es-v3-physical-settlement-review1-20260911.md`, execution NONE.
The entire held-body test is byte-identical to base, and cleanup matches the
previously reviewed pattern. This status update changes no test source.
Corrected independent execution and combined gates remain pending.

Provenance: existing independently invented mock fixtures only; no new model,
real application material, credentials or response payloads were added.
