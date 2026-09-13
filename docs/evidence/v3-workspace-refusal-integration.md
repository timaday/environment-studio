# Semantic refusal on the current application stack

Integrate correction61e22d27aaabefd85d91a0a8fb03124c32a7b6ba into
1522cc5c64ccfaa9f4dbdc9921a224e54f41e583 (Definitions upload, Plans inspection,
capture state and actual profile persistence prerequisite). Cherry-pick eb045ad
resolves one test-file end-of-file conflict by preserving every upload regression
and appending the four malformed-refusal replay cases. No production conflict or
additional production behavior change. Root integration remains pending.

The actual Definitions browser journey now begins with an invented invalid JSON
upload, checks the real unmodified422 kind=rejected response, editable exact
source, diagnostic display and absence of Retry original save. It accepts the
existing unsaved-source replacement confirmation, uploads the corrected invented
definition, and completes the existing save/postcommit403 exact-replay/history/
relogin/YAML journey. The invalid source includes the existing source-log canary;
final control/checks now verifies bounded log/PKCE/TLS/token canaries. No source
values or credential-bearing error reports are persisted; all logs remain RAM.
This is a finite canary check, not proof against every possible disclosure.

Dedicated test-only definitions-v3-refusal mode uses18445/18446 with actual
production-composition schema3 storage and no publication/observation witness.
Existing modes retain18443/18444 for the reviewer. Each viewport receives a fresh
workspace. No new UI design, token or renderer is introduced; pending Definitions
rendering exceptions remain unapproved and fidelity DIFFERENCES_REMAIN.

Actual browser1 reached valid recovery but later failed after the test dismissed
the unsaved-source confirmation by default. Browser2 waited for replacement text
and failed at that precondition. Browser3 explicitly accepted the existing dialog
and passed2.8s with fixed source/runtime hashes and complete cleanup. Later final
runs include an exact confirmation-message assertion and source canary.
These are test-journey corrections, not additional demonstrated production defects.
Final check2/tests2/build2 pass: 250 frontend and 59 schema tests, TypeScript,
Biome and production build. Browser4 desktop (1440×1000) passes in 2.8s;
browser5 narrow (390×844, with the existing 320px empty-state probe) passes
in 2.7s. Both use fresh owned workspaces, include the final confirmation/source
canary checks, and verify unchanged frontend/runtime hashes and complete cleanup.
Fixed non-author integration review found no confirmed defect; all ten fixed
source hashes match. It checked preservation of upload coverage, actual refusal
recovery, test-only ports and the bounded evidence. The reviewer executed no
tests. Local definitions/profile modes share18445/18446 and remain serialized.
Repository integrity, whitespace and 11 Python tests pass; the staged content
guard is run before this commit. Remote correction acceptance remains pending.

The test runtime compiles five current helpers into14 isolated classes against
1,583 verified inherited classpath files. Current production Java matches the
existing39a9009 runtime source; no broad Maven/container/native campaign is rerun.
External identity/proof: es-v3-refusal-integrated-harness1-20260910/result.json;
individual browser result directories preserve runner/source/class hashes and
safe summaries. G01/G08 combined application/supervisor gates and remote accepted
correction remain required; separate branch results are not release qualification.
