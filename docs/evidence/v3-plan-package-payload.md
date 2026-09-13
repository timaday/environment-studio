# Verified v3 plan payload prerequisite

The internal adapter prepares deterministic payload-v1 bytes from retained plan
sources and actual target decisions. It reprojects original content and
rematerializes the target through the existing full v3 verification. Every
selected document needs an exact protected-self-contained policy, including
unchanged/unmapped XML. Selected missing/denied/duplicate/extraneous policies
refuse; another binding cannot provide authority. Original/target UTF-8 bytes,
physical keys and declared table metadata are preserved; output stays unqualified.
No route, database/client operation, capability or publication flag is added.

Acceptance uses the existing independently invented items fixture and a new
unmapped XML string invented only for this test: comments, CRLF, CDATA, combining
and supplementary characters. Current projection and target materialization are
actual adapters. Tests check explicit span changes, no-op exactness, all documents,
policy failures, replaced source/target/draft/pins, cancellation, deterministic
canonical bytes and defensive output access. Actual resulting payload passes the
existing mechanical v3 pin reader and PG16.11 template with an explicitly
constructed test compiler witness. The actual compiler remains Incomplete. This
test does not establish live publication or execute its generated SQL.

Author RED: initial expected candidate refused UNAVAILABLE; expanded four tests
then failed on the placeholder adapter with zero compile errors. GREEN: six new
tests and affected existing content/package/template checks total46Java PASS
(1core+7parser+38server), no skipped tests in selected classes. G00/Python11 PASS.
External logs: es-v3-plan-payload-red1/red2/green1/green2/green3-20260911.log.
Three separate compiled guard-removal controls were detected: bypass full proof,
accept denied document policy, and replace original payload bytes with target
bytes. Each fixed control passed and its mutant raised AssertionFailedError;
production source hashes remained unchanged. External manifest/results directory:
es-v3-plan-payload-mutants-20260911.

Independent fixed non-author source review found no confirmed findings;
manifest a82fed078c9081b86a8561dcc1f16c35f3f25c86153c1604dd07252494ad65b2,
external es-v3-plan-payload-source-review1-20260911.md, reviewer execution NONE.
Combined integration remains required. This bounded
writer check does not qualify maximum combined memory, live cancellation during
encoding, native client, external real application, operational export or release.
The future export owner must reserve actual resources and recheck lease, revision,
observation, publication, review and full validation before/after generation.
