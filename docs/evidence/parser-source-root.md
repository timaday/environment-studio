# Parser build source-root correction — 8 September 2026

Two direct integration Maven runs failed after an extra generated test class
appeared with binary name `src.test.java.studio.environment.buildxml...` and an
immediately thrown package-mismatch Error. Independent investigation confirmed
that the parser POM registered the entire module as its test source root.
Package-aware IDE compilation consequently interprets `src/test/java` as package
segments. Maven's custom testIncludes does not correct the IDE source-root model.
JDT language-server processes were active; attribution to a specific writing
process was not traced and remains inferred.

The correction moves the source-launch helper unchanged into the normal test
package, removes the module-wide test source override/custom includes and updates
the generate-sources launch path. No dependency or runtime behavior changes.
The helper remains test/build-only. Its exact source SHA-256 before and after is
`b1f6b49c593622e8f26b53869bc65d2506eb72b81715db371275fc61ac4e066d`.
The module README documents the build boundary before the POM change.

The first root retry after this correction still failed: the active IDE retained
the old imported model, recreated the incorrect classes and also interfered with
generated parser classes. This is not a passing watched-root result. No IDE
process or global configuration was changed. Reimporting the corrected Maven
model in that already open IDE remains an external local-tooling action.

In a fresh isolated worktree containing the exact correction,
`/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -Dmaven.jar.forceCreation=true -f backend/pom.xml verify`
passed twice: **302 tests** per run, 94 core, 7 parser and 201 server, no failures,
errors or skips. No classes with the wrong source-path package prefix appeared.
The two logs are `/tmp/es-parser-source-root-isolated1.log` and
`/tmp/es-parser-source-root-isolated2.log`. These prove the corrected Maven build,
not automatic reimport in the original IDE. The failed watched-root attempt is
recorded in `/tmp/es-parser-source-root-green1.log`; its filename is not a claim
that the command succeeded.

Existing independent parser source/checksum/archive/classifier/runtime tests
remain unchanged and passed. No new assertion merely mirrors the POM path.
The isolated Docker build `environment-studio:parser-source-root-review` passed
the complete 302-test reactor, 7 UI and 14 schema tests. Protected container smoke
passed startup/health/demo denial and private initializer/offline upgrade checks.
The local runtime manifest is
`sha256:29123fb20bb0128fae5b01dd992e325c85fb6428bdd0f6562523997bf4755833`.
The independent reviewer checked all candidate hashes, both passing logs, the
failed root log and the produced parser JAR: 238 entries with no helper/test/studio
classes. Staged provenance/content checks and 10 Python tests passed before
commit. This fix does not add XML, application or SQL-client support.
