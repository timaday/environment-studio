# Current inventory for explicit profile placement

Base b74ffac25decd95f1207ea30ca2292faaf678bba. This nonvisual controller supports
existing whole/partial reuse; it does not implement a renderer, choose mappings,
copy values to profiles, submit commands or authorize publication/export.

Acceptance: an explicit read loads all current entity pages under the initiating
plan, checks total/side/revision and unique existing handles, then requires a
fresh full plan summary to match before exposing any row. Unknown/partial/stale
inventory stays unavailable. Preserve concrete, empty, absent and masked values
only in this transient inspection state; retire reads/clear values on context,
presentation, owner/API or session invalidation. No auto-load/retry/persistence.
See [browser inventory contract](../contracts/hosted-plan-workflow-v3.md).

Meaningful RED: with a callable empty controller, the focused suite failed8 of9
behavior cases, including expected complete101-row inventory remaining null and
missing refusal/recovery behavior. Initial command red1 used the wrong executable
working directory and exited127; red2 is the actual behavior RED. Green1 passed9;
additional empty/oversized/concurrent-load/unmount cases and a valid fresh-ref
adverse input passed11 in green2. Final exact-source green3 passed11. Check2 passes
TypeScript/e2e typechecking and Biome82files without warnings. Format1's wrong-root
nested-config failure and earlier3test non-null assertion warnings are preserved;
format3/check2 correct them without rule/config changes. Logs are external
`es-reuse-inventory-{red2,green3,check2}-20260911.log`.

The101-row fixture crosses the100-row page boundary. Cases cover count/revision/
duplicate/fresh-reference tail refusals, changed final context, held reads retired
on inactive presentation/unmount, failed reload and SESSION_REQUIRED retirement,
absent/masked/empty/concrete distinctions, zero versus unknown, the20000 bound,
and prevention of a second load while the first is pending. These are mocked
transport state tests, not real database/HTTP/browser execution or whole resource
qualification. Passive session expiry depends on the existing hosted owner
retiring/unmounting the presentation/API; no new global session mechanism exists.

Fixed non-author source review found no confirmed finding; lead accepts this
bounded source prerequisite. Reviewer execution NONE. External report
`es-v3-reuse-inventory-review1-20260911.md` verifies four-file manifest
`039b6affdb7a01781fc222660dbf8a0481bd4cee85ebaf3bc60271537ed9635a`;
hook SHA256 `5b08715fe2d4e8f7ad5a6d511af0b6201593514ebb6e466af9fb7fa3f6a6c95b`.
Only this disposition paragraph changes after that review. Integrated gates remain
pending. Exact b74 independent results exclude this change.
Only independently invented wire fixtures are added; no application model or
production fallback. Reuse images retain their separate approval requirement.
