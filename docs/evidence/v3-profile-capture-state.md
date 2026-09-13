# Nonvisual v3 capture-to-draft candidate

Base `d67df448b4bb13f806f3b9d44b388dc165a318c6`, branch
`implementation/v3-profile-capture-state-20260910`; lead application writer.
IDE 2 retains native ownership. The [journey](../ux/v3-profile-capture-journey.md)
uses existing clients and contracts. No API, backend, renderer or admission change.

Explicit entry loads every original physical page and retains only Existing
handles/type IDs. Complete consistent totals, unique handles, whole explicit
neutral mappings and unique slots are required. Capture uses the verified original
observation; a separate Save creates a new immutable workspace draft from the exact
returned source and definition. No values are inferred, copied into mappings or
stored in browser persistence. No implicit save, publication, reuse or export.

Read generations discard late results after context/input changes. Dispatched
saves have separate session ownership: same-session hide/show or plan replacement
cannot change the pending destination, request ID, source or historical reference.
Unknown response, response loss, malformed200, 403/413/503 retain explicit exact
replay. Only the existing closed precommit refusal tuples unlock a new command.

## RED, correction and review

Actual RED examples: missing complete inventory after load; missing configure/
capture action; invalid mapping reported RESPONSE_UNAVAILABLE instead of
INVALID_REQUEST. These were implemented and corrected. Independent fixed1 review
then found terminal session failure during capture retained earlier handles and
mappings. Extended coverage failed once with19 passing tests. The shared terminal
handler now clears all state/pending work, retires read/action ownership and blocks
stale configuration. The corrected20-test run passes.

Fixed2 non-author review accepts that P2 correction with no new confirmed defect.
All three frozen source/test/journey hashes match manifest SHA-256
`1913e2e0c8d1fe64d42bb7b5cf9320fd16da70f5d37d2c80752dd567e3f122bf`.
Review inspected source and author evidence; no reviewer execution is claimed.
New API-owner replacement coverage confirms an old successful acknowledgement
cannot populate the replacement owner's state; the replacement can load normally.

Final author commands from `frontend` with Node24.20.0:

- `npm run check`: TypeScript/e2e types and Biome pass,62files checked.
- `npm test`:221frontend/59schema pass, including23 focused hook tests.
- `npm run build`: production build passes.

Seven targeted compiled mutation kinds are killed at recorded boundaries. Earlier
campaign1 covers first-page truncation, foreign mappings and late generations.
Campaign2 runs a23-test control and four kills: broadened precommit refusal,
changed replay identity, omitted final capture context and reversed terminal
session classification. The last mutation causes eight assertion failures through
both missed retirement and incorrect retirement; that is not eight separate bugs.
Campaign1's first attempt at a refusal mutant failed TypeScript narrowing and is
not a kill; corrected campaign2 compiles it before execution.

Preserve setup/oracle failures: initial mock handles were not valid UUIDs;
positive fixtures were corrected. A wrong-cwd test lacked DOM setup. A bare
CONFLICT expectation was corrected to the existing safe error-message contract.
Optional API-owner test props needed a typing correction. These are not additional
production RED or passing gates. The inherited Plans formatting gap was separately
corrected and verified in the base before this candidate's final full checks.

## Limits and next executable task

Tests use independently invented transport responses, not real persistence or
publication. No rendered profile journey, browser capture-to-save, visual fidelity,
production qualification or release acceptance follows. Acknowledged structural
draft data does not grant current publication or export authority.

The hook is not a session-event subscription: an idle API clear without a request
error does not itself notify it. Future rendering must retire/unmount it on actual
session end, preserve it through same-session presentation changes and use a new
owner/remount for a new session. Pending commands do not survive actual unmount.
JavaScript stack references are not claimed to be synchronously scrubbed.

The next browser prerequisite can reuse existing `V3WorkflowStorageFixtures` and
the matching `V3WorkflowHttpTestConfiguration`, which already construct and
round-trip an invented historical definition through the real compiler/store.
The current document-only Plans harness witness alone lacks that owned SQLite
publication. Add a closed test-only capture mode with consistent issuer/subject,
definition and observation; run actual capture/save/readback before claiming its
browser support. Do not duplicate the implemented API or enable production
publication. New rendering still needs image approval against the original
Midnight references; pending Definitions renders remain unapproved.
