# Confirmed semantic save refusal — 10 September 2026

DEF-QA-001 is accepted from the [remote reproduction](https://github.com/timaday/environment-studio/issues/9#issuecomment-5625980193)
on bc6dfe8b105ded5cab123e0f38a02c74edb0d21e. Actual v3 save422 returns
kind=rejected and diagnostics, without a top-level code. HostedApi previously
reported HTTP_422, which the Definitions state treated as uncertain and retained
for replay. The source could not be corrected. Existing tests used a different
code-only envelope and missed this integration mismatch.

The browser now recognizes the complete rejection envelope only on422 PUT to an
exact v3 definition/profile UUID object path. It validates closed objects,
1–256 diagnostics, declared phases, safe code1–128 and scalar pointer/message;
it preserves diagnostic order and duplicates. A valid refusal becomes REJECTED.
Missing/malformed/contradictory envelopes are RESPONSE_UNAVAILABLE and keep exact
replay. No publication/plan/legacy route classification or403/413 handling changes.
This changes no backend API, production authority, rendering or visual approval.

Acceptance: real HostedApi plus Definitions hook preserves exact source and
returned diagnostics, retires a definitively rejected command, permits editing,
and does not replay it. Unrecognized422 replies retain the original destination/
body and block conflicting edits. Boundary checks cover both workspace families,
all four phases, exact diagnostic limits, malformed fields/scalars, extra fields,
methods/paths and postcommit-capable statuses. Fixtures are independently invented.

Actual author RED before production change:15hook tests,3failures/12passes.
The valid envelope remained pending; code-only and contradictory-code responses
incorrectly released the command. GREEN after correction:15passes. The broader
suite found the same incorrect code-only fixture in the save-client test; it now
uses the actual server envelope. Final check3,195frontend/59schema tests3 and
build1 pass. Initial root-directory Biome invocation refused nested configuration;
check1 then reported three unformatted files. Running the formatter from frontend
resolved these setup/format failures; no configuration or gate was weakened.

Commands: Node24 `npm run check`, `npm test`, `npm run build` from frontend;
focused `npm exec vitest run -- src/hosted/useV3Definitions.test.ts`. External logs
`es-v3-workspace-refusal-{red1,green1,check1,check2,check3,tests2,tests3,build1}-20260910.log`
retain the actual boundaries. Fixed non-author review verifies the six immutable source/contract/test hashes
and reports no confirmed defect; no reviewer execution. Report:
`es-v3-workspace-refusal-fixed1-review-20260910.md`. External mutation control
passes40tests. Removing the closed-object check, accepting empty diagnostics and
omitting normalization compile and fail their required recovery/decoder assertions.
The initial external copy omitted contract files and failed compilation for all
variants; no kill was counted. The second campaign corrected the external setup.
A route-expansion variant initially had an unescaped regular-expression slash;
its corrected compile/run fails the route/status boundary assertion (39pass/1fail).
Four distinct compiled faults are therefore detected; setup failures are not kills. Combined frontend integration
and remote correction verification remain required. Prior remote controller,
legacy browser and schema3 HTTP results apply to their tested candidate; they
are not new execution by the lead. Native/root/full/OCI/release qualification is
unchanged and remains separate. Pending Definitions designs stay unapproved.
