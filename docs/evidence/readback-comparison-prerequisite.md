# Fresh readback comparison prerequisite — 10 September 2026

The [internal comparison contract](../contracts/plan-readback-v1.md) adds the ES-15
state-comparison prerequisite on base e338ec9950693ec831836a46b6f983af705fc6bf.
It checks a detached complete expected target against supplied complete observation
data, with exact inventory/typed keys/XML, required destination and metadata,
strict UTF-8 lengths/digests and cancellation. Matches, Differs and Unknown convey
no execution, application health, freshness, live-artifact or export authority.
No database, HTTP, browser, native or availability behavior changed.

Initial meaningful RED: all three tests failed against simple list equality:
reordered exact inventory returned Differs; missing destination evidence and
cancelled work returned Matches. The guarded comparison made all three pass.
Adverse coverage then exposed two implementation defects: valid scalar text keys
containing a newline were rejected, and odd UTF-16 alignment could skip chunk
cancellation checks. RED2 records one failed assertion and one unexpected
INVALID_READBACK_EXPECTATION error; these are production behavior failures, not
compiler/setup errors. Scalar keys now preserve exact content, and chunk checks
count code points rather than testing a UTF-16 offset modulus.

Final 15 tests cover two independently literal documents; ordering, missing/extra/
same-count replacements; changed keys, unchanged dependencies, CRLF and CDATA
spelling; duplicates, malformed scalars, lengths/digests and key ranges; inclusive
128-document/1-Mi UTF-16 per-document/16-MiB total bounds; exact UTF-8 widths;
all required destination/metadata fields; both model versions and engine identity
shapes; every observation refusal, unavailable inputs, original cancellation and
safe detached data. Expected XML/hash oracles use literals and the independent
standard UTF-8 encoder. These are invented in-process data, not actual database
readback or XML parser qualification. A malformed/ambiguous observation remains
Unknown; a complete valid different inventory returns Differs.

Actual Java21.0.12/Maven3.9.16 `mvn -B -ntp -f backend/pom.xml -pl core test`
passes 350 tests, zero failures/errors/skips, including architecture checks.
Ten distinct source substitutions compile and fail the intended assertion:
destination, metadata, binding digest, source digest, extra inventory, exact key,
exact XML, total byte limit, final cancellation and duplicate key. No setup/compiler
failure is counted. Restored final source passes all 15 focused tests again.
Logs and frozen RED sources remain external as `es-readback-red1-source-20260910`,
`es-readback-red2-source-20260910`, `es-readback-core-20260910.log`,
`es-readback-final-20260910.log` and `es-readback-mutations-20260910/results.json`.

Fixed non-author source review found no confirmed issues. It verified all four
frozen hashes and inspected author logs/mutations without executing tests. Report:
`es-readback-fixed1-review-20260910.md`. Production SHA256:
`1a4c388078ca9df35aed41c9e8300a8ee35e291dfdab3540fd2ce5c1617992ee`; test SHA256:
`120151a1f8a551e3da385dc2b6e03e0cf0c9efeb1787c6d71c4770b5c95c9d45`.
Only this evidence status changed after review; combined integration gates remain
pending. G00 integrity/content and 11 Python checks pass; final staged checks
are required before commit/upload. This slice
has no server route or exported-artifact owner. It cannot independently establish
freshness, complete database visibility, canonical observation framing or XML
validity; those remain responsibilities of qualified adapters and the future live
artifact operation. The original editing baseline is never passed or modified.
Remote41c8 OCI verification and native IDE2 ownership remain reserved; no duplicate
full reactor, container or database campaign ran. Actual publication, full operator
journey and all release qualification remain open.
