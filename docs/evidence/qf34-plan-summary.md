# Separate physical and computed plan metadata

The internal v3 summary now returns separate current/target computed counts under
the original plan guard. Missing complete evidence remains absent; a complete empty
graph has present zero counts. Physical document/entity/relation counts retain their
existing meaning. No contributor values or whole graph copies enter this metadata.
The exact original snapshot must still match before publication, including changes
at the same revision. Pure operation ownership checks neither poll cleanup nor
expire or consume credentials. See [the contract](../contracts/plan-summary-v3.md).

This adds no HTTP route, compiler qualification or export authority. Actual XML
tests use explicit invented publication/observation witnesses. The actual v3 compiler
still refuses publication and hosted creation remains unqualified.

## Fixed author and independent evidence

Four-file author manifest over688e799:
`3d762ab9e57055428964109666de40e4d2463126e2c7f3358630a615e9859df0`.
The contract preceded implementation. Maven3.9.16/JDK21.0.12 results on10 September2026:

- RED1 ran3 core cases and failed2 assertions/zero errors: wrong-version summary
  and operation checks did not refuse. RED2 separately reached the XML adapter and
  failed1 assertion/zero errors among3 cases: expected computed(4,6,3), got absent.
  Original scaffold and tests are preserved outside the checkout. Compiler/setup
  failures are not counted as RED.
- Expanded author controls pass46 (23 core,1 parser,22 server), zero failures/errors/
  skips,01:34:24BST. Nine new cases cover missing/current/target/complete-empty
  results, actual XML field edits and unresolved values, held scratch, original
  identity replacement, failed same-revision reinspection, pure expired/terminal
  ownership and pending cleanup callback counts. Existing version controls pass.
  An earlier selector named a test absent from its archive; it ran zero and is
  excluded. The final archive contains the current independent legacy controls.
- Five compiled guard mutations fail1/3/3/1/2 assertions, zero errors: omitted V3
  admission, current counts substituted for target, absent replaced with zero,
  physical-only snapshot comparison and status polling as ownership preflight.
  The first mutation result parser missed ERROR-labelled totals after an actual
  assertion failure; its log is preserved and the parser corrected. The restored
  exact four files pass11 targeted controls.
- The author's full fixed candidate passes1,382 tests (319 core,7 parser,746 server,
  310 supervisor), zero failures/errors/skips, assembly and hostile launcher,
  01:41:49BST. Log SHA
  `f6eee6c77eacb471048a7714e56615810fbe424848d6bd663e18603dcbcab3f7`.

Independent review found no confirmed blocker. Report SHA
`c2e9b4bb2a284139be79f175bd04b946d74317e07df4fd104321ff01045d33b7`.
Two own literal XML controls check equal text across different derivations, repeated
contributors and exact(2,4,1) counts, forged target optionals/counts, and an active
reservation changing the snapshot without changing revision or computed counts.
Initial/restored13 pass (5 core,1 parser,7 server). A compiled physical-only verifier
fails one assertion/zero errors. All four author hashes are restored unchanged.

## Integrated candidate

Combined nine-file manifest over688e799:
`7ea0c46b4556675f0aaceaa3a538d10c1aa6870d94f710bad08c5bc8668f07e2`.
It adds both independent summary controls and the separately reviewed
[body callback correction](owned-body-callbacks.md), including its independent
tests. Full combined Maven verification passes1,393 tests (319 core,7 parser,
757 server,310 supervisor), zero failures/errors/skips, assembly and hostile
launcher,10 September2026 at01:46:09BST. The full gate includes the actual hosted
HTTP/OIDC and asynchronous Servlet controls.

Frontend/schema source is unchanged; latest clean install/check/frontend40/schema42/
build remains scoped to8b5843d. The retained047d1b0 image predates this work. New v3
plan transport/routes, actual compiler/client/content/review qualification, combined
resource limits, browser/export/readback and matching deployment evidence remain open.
