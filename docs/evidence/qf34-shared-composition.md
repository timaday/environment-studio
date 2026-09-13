# QF-0003/0004 — shared v3 profile reuse

The shared plan service now previews and composes whole/partial physical-only
v3 profiles through separate fresh qualification and complete target-proof checks.
Preview identity includes affected derivations in its v3 domain; v2 bytes remain
unchanged. New slots keep unresolved choices and placements. Explicit target
values determine recomputed groups. Direct and command composition install fresh
display handles and preserve original Existing/Fresh provenance.

Original view/command cancellation stays active through verification, reuse and
target materialization. A late materialization cannot return success after close;
an already committed draft retains its revision and exact replay acknowledgement.
The current compiler still refuses v3 publication. Runtime wiring, HTTP routes and
operational availability are separate work.

## Fixed candidate and actual checks

Base `eedcd11`; corrected seven-file manifest SHA
`9dbfe43153c6d479ce7178bcb308788c3e63cb5016382a87069de863d9583865`.
Independent review report SHA
`449df21bac7de33135ac04ac302dc32654704ce1f28c40faac682445bca68420`.
Its added provenance test SHA
`4207cf08eaee0a2a0ae404fd08a1ebd1646702c5f1e8a004949faf580ad836aa`.

Actual pinned Maven3.9.16/JDK21.0.12 in isolated candidates:

- Initial RED:1 assertion,0 errors at unsupported v3 profile dispatch;11 controls
  pass. A second actual failure exposed missing direct Fresh display handles.
  Corrected green:18 pass. Adverse suite:20 pass.
- Original command cancellation RED:1 assertion,0 errors; corrected focused
  suite17, then19 with identity/child-property reuse and post-install cancellation.
- First full run exposed a v2 compatibility regression:289 core tests,1 error in
  the unchanged command-close/materialization test. This was a production
  regression, not an environment/setup failure. The correction scopes the new
  command cancellation to v3; historical v2 completion stays unchanged.
- Corrected compatibility suite47 passes. Author full verification:1,231 pass
  (289 core,7 parser,671 server,264 supervisor),0 failures/errors/skips,
  distribution/assembly pass,23:22:44BST.
- Independent41 pass (27 core,1 parser,13 server). Actual capture, original removal
  and same-literal Fresh replacement prove that a removed original handle cannot
  select the replacement. Explicit Fresh reuse preserves exact XML, original
  removal, Fresh provenance, three independently expected pairs and replay.
- Five compiled author guard mutations and one independent provenance-resolution
  mutation fail assertions with0 errors. Restored suites pass. The review's first
  new test had a Ref/Existing compilation error, corrected in the test only;
  it is not counted as behavior RED or a killed mutant.

Combined seventeen-file source manifest SHA
`722517e5586e7e574cb06af5b0c4767b9a68a0db45394a23209711312bfc0be6`
includes the native launch owner and both independent review additions. Actual
`mvn -B -ntp -f backend/pom.xml verify`:1,253 pass
(289 core,7 parser,672 server,285 supervisor),0 failures/errors/skips;
assembly/hostile distribution pass,23:33:33BST. Integration/review/rework occurred
across both slices; no measured parallel speed-up is claimed.

All XML/profile fixtures are independently invented. Business: unselected siblings
survive partial reuse and donor values never enter profiles. Engineering/security:
full retained-target proof, current publication and original ownership are separate
requirements. QA investigates same-text identity replacement, child properties,
stale previews, cancellation, replay and unchanged v2 behavior. Actual shared-service
database workflows, combined retained-proof resources and operator/release
qualification remain open.
