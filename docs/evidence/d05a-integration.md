# D05a profile integration — 8 September 2026

Value-free capture, portable import/serialization and whole/partial composition
are integrated mechanisms. Owned persistence/publication and hosted plan binding
remain separate work. No observation, profile or closure grants export authority.

The corrected worker candidate has 19 files; manifest SHA-256 is
`7baf7542b0b1f16bccea5db37cedd77ab82a8295174df7184a340d78e6dc3190`.
The lead verified every hash and copied 18 files byte-for-byte, merging only the
server POM's profile schema resource into the newer integrated POM. The Docker
build copies the independently invented profile fixture family for its Java tests.
No private model or renamed real material was used. The provenance manifest,
independent digest oracle, complete candidate and cumulative staged diff were
reviewed; the content guard remains a limited pattern check.

Independent review found the initial external JSON serializer in core violated
the documented domain/adapter boundary. The author observed one architecture
assertion failure in a 77-test run, then moved wire encoding and portable limits
to the server adapter. Core returns StructurallyValid; server Accepted has a
private constructor and requires bounded encoding, strict reparse, schema checks
and exact structural/digest round-trip equality. Capture checks node capacity
before structural allocation. Import, capture and serialization cannot bypass
portable acceptance. The core retains only canonical digest framing.

The reviewer verified both corrected manifests and independently passed 21
profile behavior tests and the architecture test. A first combined JShell run
lacked ArchUnit in the server classpath; the corrected core-classpath run passed.
This invocation correction was not a behavior failure. There are no remaining
material findings in the reviewed profile candidate. Worker TDD, eight killed
guard mutants, canaries and exact boundary cases are recorded in
[D05a evidence](d05a-profiles.md); those mutant runs were not repeated by the lead.

Actual lead checks:

- `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml -Dmaven.jar.forceCreation=true verify`:
  233 Java tests passed (77 core, 7 qualified parser, 149 server), no failures,
  errors or skips; final corrected run finished at 18:15:44 UTC.
- With the supported Node 24 toolchain, `npm run check --prefix frontend` and
  `npm test --prefix frontend`: type/format checks, 7 component tests and 10
  schema tests passed. No UI behavior changed in this slice.
- `python3 fixtures/profile-v2/digest-oracle.py`: independently reproduced
  `5c72829f3788e522c8d6571b979480ecf4f6be000c493ed55f086fbf9b431683`.
- `docker build -t environment-studio:d05a-review .`: corrected candidate built
  successfully, including the image's frontend/schema and full Java gates.
- `bash scripts/container_smoke.sh environment-studio:d05a-review`: protected
  non-root/read-only startup, static UI/health/capability denial and private
  workspace initializer/permissions/overwrite refusal passed.
- Repository integrity, full staged-content guard and 10 Python script tests
  passed before the integration commit.

The original integration run passed 231 Java tests before the architecture
correction; the final 233-test result supersedes it. The author's correction
interval was 7 minutes 25 seconds plus evidence preparation, with no blocked
time. Lead integration overlapped contracts and other work; no isolated active
time or serial comparison was recorded, so no speed-up is claimed.

G00/G01/G02/G08 and the local profile canary/mutation evidence support this
mechanism only. Actual persistence canaries, hosted ownership/plan revisions,
operator workflow and release/deployment qualification remain outstanding.
The image is local and unpublished. Automatic approval review still blocks the
authorized branch push; no new GitHub CI, PR or GHCR digest is claimed.
