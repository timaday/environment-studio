# Internal guarded package assembly candidate

The assembler connects mechanically admitted execution/payload content to the
existing deterministic SQL templates and strict four-member ZIP writer. It
canonicalizes and re-admits inputs before SQL generation, so the program digest
binds the exact canonical payload emitted. It derives manifest lengths/hashes/
counts and uses fixed instructions; no arbitrary SQL or manifest input.

Results remain explicitly unqualified. No download endpoint, publication, review
authority, native client launch or export capability was enabled. Cancellation
and output failure refuse; partial bytes belong to the caller and must be
discarded. The stream is neither closed nor flushed. Existing member/total limits
apply; combined memory admission remains required from the future hosted owner.

Author RED:3tests failed against the placeholder assembler, no compile errors
(es-package-assembly-red1-20260911.log). First GREEN attempt exposed a test oracle
error: denied policies cannot form an Accepted input because the manifest schema
already refuses them. Corrected the test to require that existing refusal, then
check rejected/null input emits nothing; no production guard was relaxed.

Combined affected48Java PASS (1core/7parser/40server), including3new tests, exact
independent canonical program digest fixture, independent ZIP member decoding and
SHA256, byte-identical repeat, policy/cancel/partial-output failures. Log:
es-package-assembly-green2-20260911.log. Full frontend432/schema61 PASS on the
integrated tree (es-assembly-integrated-frontend-20260911.log). G00/Python11 PASS.

Actual earlier verified two-document plan payload for PostgreSQL16.11 assembles
to a12277-byte external mock ZIP, SHA256
3bc7164d081c9cc2ea9159d9961785faea2571c25795c1772a5732cf0ac38744.
The current supervisor PackageCheck and its three helpers were freshly compiled
into separate external output; regeneration accepted the exact SQL as unqualified
and rejected an incorrect archive hash. No client was launched in this assembly
check. External source manifest/artifacts: es-package-assembly-check-20260911.
The underlying payload SQL previously passed five actual16.11 rollback/baseline/
commit/exact-readback/replay cases in es-plan-payload-pg1611-20260911. These use
independently invented test compiler/observation witnesses, not live publication.

Independent review is pending: both reviewers were retired by user instruction.
Full integrated backend/OCI, combined resource and production export/native/client/
release qualification are not established by these focused results.

## Exact candidate integration results

Source commit6574e9e04b4e9eb56b9c38dcd0fcfb09b782ff77 passed the complete
author host Maven verify:1721tests, zero failures/errors/skips,4m38s, including
standalone supervisor distribution verification. Full frontend432/schema61 PASS.
Three external compiled faults (canonical binding, SQL member, count) were each
detected by the unchanged archive oracle; fixed controls passed and source hash
remained unchanged. Evidence: es-assembly-mutants-20260911/results.json.

The exact pinned OCI build also passed1721Java and432frontend/schema61 tests.
Local runtime image/index digest:
sha256:a8f17641b7447fe0fd15a4a4e8dbf439872978893ca2b90190e3b3a7ccdded16.
Its OCI revision label matches6574e9e and user is10001:10001. Protected smoke
passed static UI, readiness, demo capability/denial, private workspace permissions,
overwrite refusal, schema2 initialization/legacy upgrade and schema3 upgrade/
fresh initialization/refusal. Cached export of the matching supervisor artifact
passed all29 checksums. No independent review of this new assembler is implied.

External OCI evidence directory es-assembly-oci-20260911 contains results.json,
image identity, logs and supervisor artifacts. Build log SHA256
c3717e9dbafaf4fdee32d7ac55cb6cb03881ff6e1005a072dd5d39bc21776eda; smoke log
45222001bbf6ae154f65907b4a7ae509fbbea3cb9f92222837a341726644261b.
The image is local, not published to GHCR or qualified on HiveForge.
Release-readiness deliberately remains BLOCKED: source/evidence fingerprint and
required release capabilities are incomplete. The1GiB smoke proves startup,
not combined maximum workload/resource/privacy/client qualification.

Both reviewers are retired; the prior9085c443 candidate has independently passed
focused Windows25tests and two negative ownership controls. Its remote full
Maven was interrupted at1321 recorded completed cases for retirement, not accepted
as a complete gate. Those observations do not cover this new assembly code.
Lead stopped the owned disposable PostgreSQL container after preserving all
external evidence; its database used tmpfs. Runtime smoke resources were cleaned
by their original owners. Frozen native and Reuse WIP remain untouched.
