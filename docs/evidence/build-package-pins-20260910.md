# Build prerequisite correction — BUILD-QA-001

The Java build stage refreshes five unavailable exact Ubuntu package pins:
libc6-dev2.39-0ubuntu8.8 →2.39-0ubuntu8.9 and the four Python3.12 interpreter/
minimal/stdlib/library pins3.12.3-1ubuntu0.16 →3.12.3-1ubuntu0.17. All package names,
other versions, install flags and base image digests remain unchanged. The generic
python3/python3-minimal family retains its separate3.12.3-0ubuntu2.1 version.

## Actual failure and proposed correction

[Remote reviewer5622285265](https://github.com/timaday/environment-studio/issues/9#issuecomment-5622285265)
ran exact39a9009fc93f22bb65a2eb992718c96cb67b0d0b with the unchanged Dockerfile.
G08 failed APT exit100 before tests/assembly because the old versions were absent.
In the same pinned Maven base, a separate disposable metadata-only container
successfully updated APT indexes and reported the replacement versions above.
This establishes an unavailable-package failure, not failed index downloads.
Earlier Buildx/credential-helper setup failures are separately preserved.

Independent read-only investigation confirmed the proposed amd64 files in the
live official [glibc archive](https://archive.ubuntu.com/ubuntu/pool/main/g/glibc/)
and [Python3.12 archive](https://archive.ubuntu.com/ubuntu/pool/main/p/python3.12/).
The Python family moves together because its components have exact-version
dependencies. Other explicit GCC/OpenSSL/generic-Python pins remain available.
Cached package pages still showed older Python data; the live archive checks and
exact-base remote APT metadata are distinguished in the external investigation
`es-build-pin-investigation-20260910.md`. Published files alone do not establish
successful installation or a passing image build.

These packages belong to java-build. The runtime OS base and copied artifact paths
are unchanged; build-stage system libraries/Python/GCC are not copied into runtime.
Rebuilt application and separate supervisor outputs still need new identities
and actual verification; no binary-equivalence or native qualification is inferred.

## Gates and limits

The failure is actual build RED. The proposed correction requires remote GREEN
on the exact corrected candidate: whole pinned package-set resolution, unchanged
multi-stage frontend/Maven/native/distribution gates, protected nonroot/read-only
startup and workspace smoke, and separate supervisor export/checksums. No skipped
test, floating pin, base replacement or release-readiness exception is introduced.
Local package installation, full build and OCI smoke are NOT_RUN; the remote
review machine owns the serialized expensive checks once the candidate is fetchable.

Fixed non-author source review verifies both file hashes and exactly five pin
substitutions, with no blocking finding. The reviewer ran no builds or tests.
Report: `es-build-pins-fixed1-review-20260910.md`. Local G00 integrity/content/
whitespace and11Python pass; they do not establish APT installation or build GREEN.

The preceding39 tree passed1,698 Java tests and136frontend/59schema G02 remotely.
The separate [definition-save client](v3-definition-save-client.md) now passes
fixed source review and local145frontend/59schema/check/build; this pin correction
follows it on the candidate branch. Neither result clears G08. IDE2 closure WIP,
actual installed clients, publication/resources/operator/deployment qualification
and release admission remain excluded or incomplete.
