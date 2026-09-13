# Command HTTP sequential settlement correction

Base `76ab726485f13f656fcd2b3f48d709cd9a9113e8`, combined operator integration.
Lead alone owns this test-only correction; production, fixture and build code
are unchanged.

[TEST-QA-007](https://github.com/timaday/environment-studio/issues/9#issuecomment-5634651174)
is accepted. On unchanged e4a41e5, the independent reviewer held the first command's
original worker after core cleanup/before workerClosed. Its200/revision3 and
intervening metadata/computed-count/core XML assertions passed; the identical
replay returned429/CAPACITY/empty body, failing the original Ack equality assertion.
After original-worker release and registry zero, identical replay and subsequent
collision/discard/replacement/retained replay assertions passed. This controlled
legal schedule is RED evidence for a sequential test precondition defect, not a
production mutation/replay fault or the unscheduled Physical OCI cause.

Acceptance: ordinary setup/commands/refusals await the existing bounded original
registry-zero witness before one original request. No retry, accepted429, guard,
timeout or ownership changes. The entire intentional held-body test stays raw
and unchanged, including metadata availability and logout/recovery. Teardown
preserves the first settlement/logout failure, attempts all client logouts and
a final settlement check, and suppresses later failures; existing canary assertions
remain required after settlement. This matches reviewed Materialization/Physical.
The [original lifetime contract](../contracts/plan-transfers-v3.md) is unchanged.

Author check1 freshly compiled this exact test ahead of hash-verified inherited
production/fixtures and passed all3 actual HTTP/OIDC/private SQLite tests, with
0test/container failures. Java21/-Xmx1024m, private RAM workspace/logs and ephemeral
ports; source/runtime inputs unchanged and private directory cleaned. Exact
commands/hashes/results: external `es-v3-command-settlement-check1-20260911`.
This is affected local execution, not independent corrected-candidate verification,
held-worker/fault-injection control or full Maven/OCI qualification. Fixed
non-author source review found no confirmed finding against two-file manifest
`dc1d5f3112e310a0f80e4026233aca357f0476a7a975ef871b234b42227e48ed`;
report `es-v3-command-settlement-review1-20260911.md`, execution NONE.
The entire held probe is byte-identical and replay/core oracles remain intact.
This status update changes no test source. Independent corrected execution and
combined gates remain pending. Earlier independent results
and failed OCI evidence remain preserved.

Only existing independently invented mock data is exercised. No private model,
new application fixture, credentials or response payloads are added.
