# Resume after the 8 September 2026 pause

Tim explicitly requested a pause until tomorrow. The overall implementation task
is unfinished; this is a checkpoint, not completion or an external-blocker claim.
Read [progress](progress.md) and the linked integration evidence before starting.
Do not restart completed D01–D07 analysis or repeat unaffected qualification.

## Integrated checkpoint

- Initial hosted HTTP: `33705b1`; closed view schemas: `c074cfa`.
- Reviewed JDBC TLS matrix/status corrections: `c3f75df`.
- Standalone supervisor candidate, separate artifact build and privacy corrections:
  `4d2ff2b`; 433 integrated Java tests and protected smoke passed.
- Bounded terminal/effective crash-privacy requirements: `7612861`.
- Reviewed hosted inspection/composition views: `6336d3d`; see
  [D06b3 integration](../evidence/d06b3-integration.md); 450 integrated Java tests,
  22 schema tests, 7 frontend component tests and protected smoke passed.

The latest local image is `environment-studio:d06b3-review`, runtime manifest
`3f1252f59a9a74e092f07d808efd8165522b9c2a3eb79f2e7f408100c52dfb44`.
Inspection/export capability flags remain false. The native registry is empty.
These are local development candidates, not published or release-qualified artifacts.

## Preserved work in progress

Two isolated worktrees contain unreviewed work beyond the integrated checkpoint:

| Slice | Worktree / branch | Resume with |
| --- | --- | --- |
| D08a hosted browser | `/home/tim/.tmp/es-d08a-hosted-workspace`, `implementation/d08a-hosted-workspace` | Memory-only API/session handling, definitions/publication, plan inspection and raw/formatted panes; genuine HTTPS/OIDC browser harness |
| D07c2 native runtime | `/home/tim/.tmp/es-d07c2-native-runtime`, `implementation/d07c2-native-runtime` | Bounded native terminal helper, actual native installation/TLS/transcript qualification and effective crash-privacy mechanism |

Both start from `c074cfa` plus explicit earlier candidate overlays. They are not
clean branches ready to merge wholesale. Preserve their changes; compare their
documented manifests and merge only assigned deltas into the current integration
tree. In particular retain newer classifier/schema/fixture/build changes. The
original D06b3 and D07c1 author worktrees remain frozen for review history.

C2's detailed handoff is `docs/evidence/d07c2-wip-handoff.md` inside its worktree.
Its complete 46-file external inventory is
`/home/tim/.tmp/es-d07c2-wip-20260908/files.sha256`, SHA-256
`a4ff727242799867debc733c72761821dd0984d41ecc1b0d5a1280def4eb9d3a`.
Nine files are C2 author work; the remainder includes its baseline overlay. Direct
PTY cases passed, but the final Maven/assembly suite was not rerun. All owned C2
processes completed; no native DB authentication or database mutation occurred.

D08a's detailed handoff is `docs/evidence/d08a-hosted-workspace.md` inside its
worktree. Its 21-file inventory and patch are preserved under
`/home/tim/.tmp/es-d08a-wip-20260908/`; inventory SHA-256
`312f923e6bcf3b413add21ddc8903b6d617ce4adde1f5d4b78ebad043462ff9d`.
The latest intentional failing test leaves Save enabled after `NETWORK_UNCERTAIN`;
fix reconciliation/original-command replay before allowing another save. The
actual HTTPS/OIDC browser progressed through save/publication but timed out at
the published-definition selector after switching to Plans. No final browser,
accessibility or full frontend-check pass is claimed. Owned browser/HTTPS/OIDC
processes stopped, ports 18443/18444 closed, and temporary RAM/failure artifacts
were cleared. Preserve the meaningful failing tests for tomorrow.

D08a must obey actual backend capability flags. Test-only capability overrides
and observation fixtures are not actual database/browser qualification. The HTTPS
harness uses independently generated memory-backed keys and explicit local
browser TLS-ignore settings; it must not claim external PKI verification. Trace,
screenshot, logging and browser-storage checks must preserve credential privacy.
Remaining UX data includes plan name/intended environment, independently observed
database identity and all mapped locations for the mandatory placeholder binding
rail. Complete their contracts before advertising those UX features.

D07c2 found Oracle startup diagnostics and a credential-free native crash before
any native authentication. The host has a piped crash collector, so zero
`RLIMIT_CORE` and absence of files in the working directory do not prove privacy.
A fixed trusted preload/JNI/seccomp mechanism is under scratch investigation only;
it needs a reviewed contract and per-exec evidence before implementation/authority.
Exec resets dumpability. No global kernel, collector or Codex configuration change
is authorized. The terminal helper proposal is in that worktree's tools docs.

## Next local qualification

After completing and reviewing those two bounded slices, continue profile reuse,
typed structural/value editing, hosted review/export and fresh readback. These
are remaining local work. Do not substitute the demo for the required workflow.

Maximum heap and blocked-response/cleanup recovery also remain local work. The
reviewer proposed five independent legal shapes: full 128-document/16 MiB source
and target retention across four leases; 20,000 entities/50,000 edges; dense
attributes within per-document lexical limits; 20,000 entities with 256 fields
and 256 optional references populated as unresolved draft decisions in eight
batches; and entered-value/JSON-escaping amplification up to the 16 MiB per-plan/
64 MiB global allowance. Validate each generated definition/XML/command against
all limits before measuring; do not combine incompatible maxima. Source-byte
counters do not account for millions of draft objects. Measure live/peak heap,
RSS, GC and cleanup across actual admission/materialization/transfer overlaps,
without heap dumps. Increase measured deployment memory if needed, not accepted scope.

## Environment and external limits

Use `/tmp/es-lead-toolchain/maven/bin/mvn` and Node 24 from
`/tmp/es-lead-toolchain/node/bin`; JDK 21. Root IDE-generated Java output has
interfered with host Maven before, so use isolated worktrees or Docker. Root
filesystem space is limited; large preparation artifacts are under `/home/tim`.
Its `.tmp` parent is group-writable and correctly fails trusted-file admission;
qualified native installations/configuration need a separately owned protected
directory, not a global permission change.

Shared disposable database instances are preserved. The TLS lab is
`es-tls-pg-2cce40c4` on loopback 32787 and `es-tls-oracle-2cce40c4` on 32788;
all lab reader accounts were disabled, reader sessions absent and original trust
settings restored after the JDBC matrix. Lab keys/bootstrap material remain
outside the checkout in memory-backed storage. If a reboot removes that material,
recreate the invented lab; do not fetch real configuration. Preserve older
qualification instances and inspect ownership before any cleanup.

Actual application/IdP/accounts/PKI/HiveForge qualification remains external.
The already-authorized push was rejected by automatic approval review because
approval is required while the session setting is Never. Do not retry through
another authority path or claim new remote PR/CI/GHCR evidence.

HiveMind project `environment-studio` is registered. Keep unfinished session
`sess-0f0200fc883f895866e6d470df5becb2` open for this work unit. HiveMap retains
the historical full scans plus bounded committed-source refreshes; neither is
release or governance authority.
