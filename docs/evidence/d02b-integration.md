# D02b integration — 8 September 2026

Hosted v1 definition drafts now save to a private initialized SQLite workspace,
with owner scoping, immutable revisions and exact replay. This integrates with
the existing OIDC boundary and preserves native v2 compiler code. V2 upload,
publication, profiles, database observation and export remain subsequent work.
See [worker evidence](d02b-workspace.md) and the
[workspace contract](../contracts/definition-workspace.md).

The 24-file worker manifest SHA-256 was
`6882f97bf9cc23ca44ffb195883d3843e690d09ade1b74695188387504652838`.
Independent review reproduced owner reassignment after directly corrupting one
catalog field and identified eager directory-list materialization. The four-file
correction manifest was
`fce0bb5cf8be799005e8b3f2bfcbed2d3d5819f93bf6f774233ce331554a18b8`.
The reviewer reran the owner-corruption and lazy-iteration traps against those
fixed hashes and closed both findings. Owner-bound snapshot hashes detect
accidental metadata corruption; they do not authenticate a hostile complete
database rewrite. No HTTP ownership bypass was found.

The lead checked every transferred file hash and merged only SQLite JDBC 3.53.4.0
into the current server POM, retaining both definition-schema resources. Reviewed
HTTP DTOs use lowercase enums and decimal-string integers under the committed
OpenAPI/inspection schema. Runtime SQLite databases and canaries were independently
invented and remained outside the checkout; no actual model/configuration was used.

The protected OCI initializer test first failed with exit 1 before native-library
packaging. A controlled comparison of the same image succeeded with executable
`/tmp` and failed with noexec `/tmp`. The production correction preserves noexec:
the Java build extracts the linux/amd64 library from the pinned SQLite JAR into
the image's root-owned `/opt/studio/native` and sets `org.sqlite.lib.path`.
The actual cached loader bytecode was inspected to verify that property and
packaged path. No downloaded alternative library or writable executable directory
was introduced. The corrected initializer smoke passed.

The independent reviewer verified the lead's three-file OCI manifest
`d07e19f8cc092067f6a6bec15e4cd2c234e4c5afaef9aaf642bbe2b438028ac6`
and found no material issue. The smoke owns a unique container/volume, checks
0700/0600 UID/GID 10001 permissions, initialization without output and overwrite
refusal with unchanged database digest. Cleanup targets only its own resources.

| Actual integrated command | Result |
| --- | --- |
| `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml -Dmaven.jar.forceCreation=true verify` | PASS: 57 core + 121 server = **178 tests**, zero failures/errors/skips |
| `docker build --target runtime -t environment-studio:d02b-review .` | PASS with final reviewed storage corrections; Java/React checks/tests/build included |
| `bash scripts/container_smoke.sh environment-studio:d02b-review` | PASS: protected startup/health/UI/denials plus private workspace initializer/overwrite checks |
| `python3 scripts/check_repository.py` | PASS |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | PASS: ten tests |
| `git diff --check` | PASS |

Logs: `/tmp/es-d02b-reviewed-integrated.log`, `/tmp/es-d02b-final-container.log`,
`/tmp/es-d02b-final-smoke.log`, and `/tmp/es-d02b-native-behavior-{red,green}.log`.
The content guard and whole staged provenance review remain required before
commit. Integration/review occupied approximately 13 minutes, including about
four minutes of worker correction; these overlap and are not a speed-up measure.

This proves local process-crash recovery and the stated protected-image behavior.
Actual volume power loss/fsync, restored-backup freshness, IdP/TLS/proxy and
HiveForge lifecycle remain external qualification. The UI is still synthetic.
The automatic approval review continues to block the authorized Git push because
approval is required while the session policy is Never; no new remote CI,
published image digest or deployment is claimed.
