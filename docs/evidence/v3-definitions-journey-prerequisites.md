# Definitions journey prerequisites

Based on e338ec9950693ec831836a46b6f983af705fc6bf. This candidate adds nonvisual
React state and test-environment support. New desktop/narrow designs remain
pending approval in `docs/ux/reference/definitions-approval.json`; no new screen
is connected or accepted. User0e2df3e's repository UX skill/AGENTS are copied
unchanged. Existing v2 layout and production backend remain unchanged; the related v2
recovery correction is recorded below.

## Behavior and adverse evidence

`useV3Definitions` uses the real typed v3 facade and original HostedApi owner.
It distinguishes unknown inventory from confirmed empty, retains authoritative
saved source separately from editor text, locks concurrent operations before a
rerender, and retains the exact destination/request/body for explicit uncertain
save replay. A failed list refresh cannot undo an acknowledged save. Parent
session teardown must unmount the hook; saved diagnostics must remain distinct
from diagnostics for a refused attempted save.

Initial meaningful RED exposed duplicate saves and regenerated retry identity;
corrected9 hook/162 frontend and59 schema tests/check/build passed. Fixed
non-author review then identified P1: decoded403 may occur after durable commit,
so general4xx classification discarded required replay identity. Two403/413
transport regressions failed their pending assertions, then passed after closing
definite refusal to exact precommit status/code pairs. A strengthened refusal
control also checks actual editing/retry behavior, not only visual pending state.
Fixed2 source review verifies the correction with no new confirmed finding;
reviewer executed no tests. Hook SHA256
`787fe1cc5c81ce770c1de6e1bf25c867214f514f19a195161c77ffef9b08ca71`.

Final author frontend:164 Vitest/59 schema tests, TypeScript/Biome and build pass
with Node24.20.0 and unchanged lockfile. Seven individually compiled guard mutants
are detected: duplicate save, discarded replay, changed replay ID, uncertain edit,
unknown-as-empty, postcommit403 classification and hidden pending after rejection.
Control11 tests pass. Initial mutation setup omitted schema/contract imports and
one mutant used a nonexistent property; its compile failures are preserved and
not counted. Wrong-cwd Vitest invocations lacked jsdom; these are setup failures,
not behavioral RED. Correct frontend-cwd RED/GREEN logs are retained externally.

## Actual server and harness checks

A new controller test revokes the original session at response-output acquisition
after actual SQLite save. It observes safe403 without source disclosure, retained
revision1, drained original operations, reauthentication and exact original-command
replay without another revision. One focused JUnit passes in581ms. This is an
actual controller/session/SQLite test with servlet doubles; it is not a browser
race or proof that every possible encoding413 is reachable for a valid model.

The test-only HostedBrowserHarness accepts explicit `definitions-v3`, initializes
a private RAM-backed schema3 workspace and uses normal production composition.
Default legacy mode retains its schema2 mock observation and credential checks.
Unknown modes refuse before resources start. No production migration/switch,
capability override or runtime mock is added.

The focused harness probe passes actual HTTPS/OIDC/PKCE, empty schema3 inventory,
exact save/current readback, a second immutable revision, original historical
replay, re-login persistence and source/token canary absence. Owned shutdown
reports complete. The initial probe omitted required Origin and correctly
received403; its setup failure is retained, without weakening the guard.
The corrected probe supplies Origin. Both runs leave inherited/compiled hashes
unchanged. This Python HTTP probe establishes the harness prerequisite, not a
completed React browser journey or trusted-public-certificate qualification.

Both focused Java checks compile only changed test helpers against explicit
absolute, nonempty classpath entries from39a9009. Backend source is unchanged
between39 and e338. Every inherited file hash matches the earlier fixed manifest
before/after; helper sources/classes and Java binaries are inventoried separately.
This reuses focused development outputs, not an exact OCI artifact. No full Maven,
OCI or database campaign ran in the lead lane; remote41c8 owns that reservation.

## Evidence locations and remaining gates

External local records: `es-v3-definitions-state-review-{red,green}-20260910.log`,
`es-v3-definitions-state-final-{check,test,build}-20260910.log`,
`es-v3-definitions-state-mutations2-20260910/results.json`,
`es-v3-definitions-state-fixed2-review-20260910.md`,
`es-v3-browser-harness-check2-20260910/result.json`, and
`es-v3-definitions-postcommit-20260910/result.json`, under `/home/tim/.tmp`.
All fixture inputs are independently invented existing repository mock models;
no invented records appear in approval images or normal product routes.

Fixed non-author harness/controller supplement review found no confirmed blocking
finding; reviewer executed no tests. A later actual invalid-mode control also
refuses before runtime startup. Final repository integrity, content/whitespace and11 Python checks pass; staged
content is checked again immediately before committing. G01/G08
combined candidate gates, actual approved UI journeys, keyboard/accessibility and
visual fidelity remain required. Browser connector discovery returned no available
browser; existing repository Playwright remains available for later journey checks.
No integration acceptance, current publication authority or release qualification.

## Related v2 recovery correction

The existing NativeWorkspaceController also checks the original lease after its
service returns. Its403 WORKSPACE_FORBIDDEN therefore cannot distinguish an early
refusal from a committed operation without acknowledgement. Definitions.tsx now
retains that outcome for the existing exact-command retry flow; no markup,
layout, new control or publication authority changes. Two component RED cases
(save and publication) could not find the retry control; both pass after the
single predicate correction. Seven definition component controls pass. Restoring
the old guard as an individually compiled mutant fails both403 cases.
The initial test-edit script failed before writing the tests; its subsequent
baseline5-test pass is not RED.

Final combined frontend166 tests/59 schemas/check/build pass. Existing hosted
Playwright workflows pass all four desktop/narrow cases in6.8s: actual v2 save and
publication complete on the server, then the test injects403 acknowledgement loss,
and unchanged UI controls replay identical commands without another revision.
Plan inspection/review and operator-role checks remain in the existing journey;
observation is its declared mock port, not actual database qualification. Existing
axe/keyboard/reflow assertions pass within those tests. No v3 layout or generated
reference fidelity is claimed. This transport fault is not actual browser session
revocation; the separate controller test supplies postcommit-revocation evidence.

The first browser process exited0 with complete cleanup, but Playwright removed
the runner log placed inside its output directory. Its incomplete report is
preserved. A corrected external runner separates two private RAM scopes for
output and logs; all four passes, source/class hashes and complete shutdown are
recorded in `es-definitions-browser2-20260910/result.json`. No full/OCI/DB campaign
was added. Fixed non-author v2 correction review verifies the four-file manifest and
finds no confirmed defect; reviewer executed no tests. External RED/GREEN and
mutation records use prefix `es-v2-definitions-postcommit-20260910` or the same
prefix with the check/stage inserted before the date.
