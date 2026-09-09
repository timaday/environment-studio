# Separate v3 historical revisions

The reviewed internal [v3 historical boundary](../contracts/native-workspace-v3.md)
preserves exact source, typed checked models, digest/mechanism metadata and stored
publication blockers. Its codec never recompiles source or supplies current
eligibility. This is history support only; v3 storage, routes and publication
remain pending. Old v2 DTOs/codecs and digest domains are unchanged.

Corrected six-file manifest `es-v3-history-candidate2-20260909.sha256`, SHA-256
`d4d124bc17409d2ab544bc631a4fef09486744d5be87796a1ad836adcedec328`, contains five
author files plus the lead's independent regression. Two explicit v3 inspection
schema resources are added by the lead. Author base457b28d includes the reviewed
profile dependencies; integration is on7b89e3c. All invented examples reuse the
registered native-v3 mock family. Artificial historical versions and source text
demonstrate no recompilation; they are not claims of prior admitted artifacts.

## Behavior and independent review

The new immutable revision type has separate schema/compiler/kind identities.
Command and publication digests use domain3 and strict UTF-8 native framing.
The bounded codec requires canonical exact bytes, closed schema shapes, correct
source SHA, exact known mechanism names, positive bounded stored versions and
matching binding digest keys. Publications require the immediately preceding
revision, complete unique document policies (none for profiles), a matching digest
and empty historical diagnostics. Empty historical diagnostics grant no new
publication authority. Stored PUBLICATION diagnostics keep their order and exact
duplicates; snapshot budgets apply without an invented count256 limit.

Lead review found a concrete defect in candidate1's integer prevalidation:
recursive property-name checks treated legal binding IDs `minimum`, `maximum` or
`revision` as numeric fields inside the binding-digest map. Independent compilation
accepts those IDs, but the original codec refused their history. The actual
regression fails one assertion with zero errors; corrected guards address only
declared numeric paths before BigInteger conversion. Recursive strict Unicode
validation remains separate. The unchanged independent test SHA-256 is
`9b19a86d32beb671848861b1b70e65569760dcb57760c0520cc69152bfe7a4d6`.
The lead reviewed all five author files and the correction; no further material
blocker was found within the internal history boundary.

## Actual verification

Author digest scaffold RED2 and separate codec RED1 are assertion failures with
zero errors. An initial arbitrary diagnostic cap was removed after a257-entry
historical preservation witness: the first run produced an unexpected refusal
error; the strengthened assertion produced RED1. Independent Python framing
provides literal v3/old-v2 command and v3 profile-publication digest oracles.
Original candidate1's six isolated authority mutants each fail one assertion:
domain, canonical bytes, mechanism names, policies, incomplete publication and
publication digest. Corrected candidate2's separate numeric-name regression mutant
also fails one assertion; the original six were not rerun on the corrected freeze.

Author restored and lead-independent corrected selections each pass **29 tests**:
four core, one parser, nine new codec, one independent regression and14 old v2
workspace/child-history cases; zero failures/errors/skips. Original v2 snapshot
bytes remain exact and both cross-version decoder attempts refuse. Tampering,
malformed UTF-8, integer strings, duplicate/trailing input and output limits are
covered. No arbitrary caller-created DTO heap qualification is claimed.

The clean combined history/native-parent archive
`es-history-parent-integrated-mz_kgtcn` passes **943 tests**: core255, parser7,
server498 and supervisor183, zero failures/errors/skips. Distribution checksum
and hostile-directory launch pass. Command `mvn -B -ntp -f backend/pom.xml verify`,
2m25s, finished18:43:30 BST; log `es-history-parent-integrated-full1-20260909.log`.
The frozen13 overlay on7b89e3c has manifest
`es-history-parent-integrated-candidate1-20260909.sha256`, SHA-256
`f04891a75486f3edc9c2690c657b026cd06c48af28c822f4b0b878ed32651f93`.
Native-parent independent review is separate; this result does not close it.

Author record `es-v3-history-evidence-20260909.md`, SHA-256
`4137044a269577f3e0facc2cb7efd46430c6e51588860dc339bff238780f541a`.
Lead logs: `es-v3-history-binding-id-red-20260909.log` and
`es-v3-history-independent-corrected-20260909.log`.
Business review preserves historical meaning; engineering review separates
compatibility from current eligibility; QA/RST covers stale and malformed history,
legal vocabulary collisions, preservation and adverse publication consistency.
Schema3 persistence, hosted integration, resource/native-client qualification and
delivery remain active. The latest aaddcfb image predates this history slice.
