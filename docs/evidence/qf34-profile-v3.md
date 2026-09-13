# Physical-only v3 profile mechanisms

This implements internal profile validation, capture, portable bytes and composition
under [profile-v3](../contracts/profile-v3.md), on source `7111230ac3c59aea94c2f8cb1bf1c5f5dfa9b2f0`.
It does not enable v3 publication, persistence, hosted composition or export.
All examples use existing independently invented fixtures and new invented XML.

The v2 and v3 facades share physical validation/closure and bounded wire mechanics.
Versioned facades select their own digest domain and closed schema. V3 rechecks
the exact checked definition without constructing v2 publication authority.
Profile slots/edges remain physical; computed declarations, groups, values and
contributors cannot enter the portable structure. Reuse previews identify affected
derivation IDs without importing donor dependencies or choosing siblings to meet
a computed minimum.

Server capture and composition independently reproject an actual current snapshot
against a separately expected revision/source pin. Capture copies explicit neutral
slots/labels and physical edges only, then checks encoding/re-import budgets.
Composition leaves all mapped fields unresolved and retains unselected current
entities. Whole/partial reuse tests supply explicit target decisions and materialize
actual XML; fresh groups replace donor membership and current siblings remain.

## Actual evidence

Core fixed12 manifest `es-v3-profile-core-candidate1-20260909.sha256`, SHA-256
`e9db45fb9c0ed4d4eb28158343e5247745bd55658b5595fc4cccb0c9942f8266`.
Lead reviewed all source/tests and extracted physical behavior against v2; no
material blocker found. Author scaffold RED has two assertion failures, zero
errors after test accessor setup was corrected. New10 plus old13 and architecture
control pass; full core251 and selected old bytes/history43 pass. An unchanged
base capture confirms the old digest; independent Python framing confirms new v3
and same-object v2 hashes. Seven isolated guard mutants each produce one assertion
failure, zero errors; restored24 pass. Author record
`es-v3-profile-core-evidence-20260909.md`, SHA-256
`7b4a25183f5ed94a009bd1f8cafe163e621eed6a48be64f4df3a60faa1e089a1`.

Lead adapter fixed7 manifest `es-v3-profile-adapter-candidate1-20260909.sha256`,
SHA-256 `96cf723bf0d664a398ff93ff2890c05ac613210cace4eee0b823720aa68a62dd`.
`es-profile-v3-adapter-red1-20260909.log` has three intended assertion failures,
zero errors against the unsupported scaffold. The shared v2 codec extraction
passes23 existing tests. First v3 integration then encountered six setup errors:
the packaged resource list omitted `profile-v3.schema.json`. Adding that explicit
schema resource corrects the packaging defect; no schema or version gate is weakened.

`es-profile-v3-adapter-green4-20260909.log` passes41 tests: 24 core, one parser,
eight v2 and eight v3 server cases, zero failures/errors/skips. V3 covers the
independent fixture digest and JSON/YAML round-trip, child-property capture with
donor canaries, same-decoded stale XML, closed version/derived-slot/edge refusal,
stale export digest, final cancellation, partial physical closure and whole/partial
reuse through actual final materialization. Exact 20,000-node portable capture
round-trips; one extra slot refuses RESOURCE_LIMIT; legal labels whose JSON
encoding exceeds 1 MiB refuse BYTE_LIMIT. These are adapter budgets, not heap
capacity measurements.

Full isolated `mvn -B -ntp -f backend/pom.xml verify` passes912 tests: 251 core,
seven parser, 486 server and 168 supervisor, zero failures/errors/skips. Distribution
checksum and hostile unrelated-directory launch checks pass. Duration 2m24s,
finished 18:23:08 BST; log `es-profile-v3-integrated-full1-20260909.log`.
The 24-file overlay on7111230 (including artifact evidence updates) is recorded in
`es-profile-v3-integrated-candidate1-20260909.sha256`, SHA-256
`f404a638c4e262be181dbc00433a6e49703cd9c545d0800cdc9116efb1d2d713`.
Independent review of the fixed adapter7 finds no material blocker within these
internal ports. Its final corrected selection passes53: 24 core, one parser and
28 server, including existing v2 bytes/history and two independent inventory,
exact-current-byte and cancellation controls. Independent test SHA-256
`a16dbcc885940d5897893d178e6ac566eb0b8c5cfe2f063d3ce09d99d27d7390` is integrated.
The first selection named a nonexistent old core test alongside a valid v3 test;
the final run selects the actual old profile/architecture classes. Three compiled
mutants each fail one assertion with zero errors: substituting the snapshot's own
revision admits stale composition; encoding v3 as v2 fails re-import; removing the
early checked-equality test still refuses at round-trip with a different safe code.
The last is a refusal-code kill and redundant barrier evidence, not an acceptance
bypass. Final53 pass after restoration; all seven hashes match before/after.
Review record `es-v3-profile-adapter-independent-review-20260909.md`, SHA-256
`7c28a6a50e98af07611d24789b69840f6f03050a782b5f18af29f1b3cda92aa1`.

The lead reviewed the independent test and its invented fixture provenance. The
full912 run precedes that test-only addition and the contract status update; the
reviewer's53 run includes both independent cases and all affected profile/history
controls. No later production change is claimed covered by the earlier freeze.

Business review preserves fresh target values and physical-only reuse. Engineering
review keeps graph/source ownership with the caller and explicit versioned codecs.
QA/RST covers stale evidence, malformed imports, mixed creation/reuse, unchanged
siblings, cancellation and portable boundaries. V3 history/publication/hosted
paths, combined resource qualification, native clients and full delivery remain
open. The exact7111230 image predates these profile changes.
