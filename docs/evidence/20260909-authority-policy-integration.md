# Integrated workspace authority and read-operation policy

Parent contract checkpoint: `9f17e4701ed79e78b2b9cd4ff8aec445c3c13ed9`.
The lead integrated only the reviewed workspace 12-file and D04 19-file author
deltas. The saved browser/native WIP and inherited contract overlays were not
merged. All integrated source/test bytes matched their fixed reviewer manifests.
This evidence is local development proof, not actual database or release qualification.

Workspace candidate: final manifest SHA-256
`9e9e2d957dfaf75e8f9287cd17f7c5f527dfd7455b29e8989de17b95d69ee948`.
The independent reviewer executed 15 authority cases and reproduced the close-Error
quarantine gap. After author RED/correction, an independent replay of the same
UnsatisfiedLinkError probe observed revoked lease, quarantine and preserved Error.
See [workspace evidence](workspace-live-authority.md) for actual RED/GREEN.

D04 source/test candidate: manifest SHA-256
`6da39dd37a7d869816bfd576051383e72a298c2e00dbd542e0053bc7072debf2`.
Independent review executed 14 focused cases and found no blocking implementation
defect. Author tests killed owner-refusal, mode-bypass and unqualified Oracle LOB
mutants. See [operation policy evidence](d04-operation-policy.md). Actual engine
privileges, owner-shadowing, read-only DML denial, TLS and committed-state witnesses
remain the next qualification; old account-policy results do not certify this change.

## Transport-test finding and correction

Both separate author reactors exposed the same existing transport-test race:
re-login expected 302 after expiry while session cleanup was INCONCLUSIVE. A safe
probe established no outstanding workspace commit, zero command readers, and all
five operations eventually cleanup COMPLETE after the first cleanup attempt.
This is consistent with required quarantine retaining authority until explicit
internal lifecycle retry; a new login cannot perform that retry implicitly.

The lead corrected the test to wait boundedly for the owned body reader to finish.
If quarantine remains, it requires INCONCLUSIVE/attempt 1 and denied login 403,
then one explicit internal retry must return COMPLETE. It still requires renewed
login 302, old operation 404, prior expired requests 401 and no DB connections.
No production automatic retry or weakened status assertion was added. Independent
review approved this exact test hash:
`c7515a86e380f001d6b73e6f939c4c52b2e15669b6156c056cc471fede6b9be1`.

## Integrated verification

The isolated integration checkout contains the exact reviewed deltas and test
correction. Apache Maven 3.9.16 and Ubuntu OpenJDK 21.0.12 executed:

- Focused workspace plus actual hosted transport: 53 PASS (14 core, 1 parser,
  38 server), log `/home/tim/.tmp/es-workspace-integrated-focused-20260909.log`.
- `mvn -B -ntp -f backend/pom.xml verify`: **475 PASS**, zero failures/errors/skips
  (136 core, 7 qualified parser, 294 server, 38 standalone supervisor).
  Log `/home/tim/.tmp/es-authority-policy-integrated-verify-20260909.log`.
  Assembly and hostile-environment standalone distribution checks also passed;
  the ordinary runtime is still unqualified with an empty registry.
- Repository integrity, reviewed staged content/provenance, diff whitespace and
  all 11 Python guard tests passed. No dependencies or frontend files changed.

Only independently invented fixture declarations and mock identities were used.
Real application models and credentials remain external. Inspection/export flags
remain false. No new browser, OCI publication, native TLS/commit, deployed IdP,
HiveForge or release-completion result is claimed.

Further work continues: XML final-context/readiness corrections, fresh D04 mock
matrix, remaining hosted browser/context/profile/target/export/readback, native
privacy/console/client qualification and maximum heap/backpressure/recovery.
Two subagent turns reached the account usage limit after completing their reviewed
candidates; later work requires its own independent review and cannot inherit it.
