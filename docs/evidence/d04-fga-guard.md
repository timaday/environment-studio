# D04 Oracle FGA read barrier correction

Base: `0df7e694466b873afcc5e55d059d3d9574d7fa3a`. This bounded correction implements the frozen database-observation contract's complete FGA catalog check before bound source reads. It adds one parameterized `SYS.DBA_AUDIT_POLICIES` query to Oracle metadata admission. Any enabled policy on the bound owner/table refuses with `VISIBILITY_UNQUALIFIED`, regardless of statement type, handler or condition. Failure to read the complete catalog remains `METADATA_UNAVAILABLE`. A disabled policy does not bypass the other metadata checks.

The public API and PostgreSQL behavior are unchanged. No managed SQL writer, HTTP authority, dependency, publication or export capability is added. Required reader catalog grants now include direct access to this view; missing access fails closed. This remains a metadata observation at the existing transaction boundary, not a new guarantee against concurrent privileged metadata changes.

## Observed checks

- Actual behavior RED: `/tmp/es-d04-fga-behavior-red.log`: three intended assertion failures, zero errors. Without the query the enabled-policy case returned the wrong refusal and complete-catalog checks were never performed. An earlier constructor compilation error in `/tmp/es-d04-fga-red.log` was test construction work, not behavior RED.
- Focused GREEN: `/tmp/es-d04-fga-green.log`, three FGA JUnit tests plus actual prerequisite architecture/classifier tests. Enabled policies refuse before the source barrier; denied catalog exceptions do not leak canary data; disabled policies continue to remaining metadata qualification.
- Targeted guard mutation: removing only the FGA query produced three assertion failures, zero errors (`/tmp/es-d04-fga-mutant.log`). The exact source bytes were restored before the full build; restored SHA-256 `3ebc85012a9db7fdfffb9a39d36de8311da9b1886f56d0af0923b06085fb18a7`.
- Full pinned Maven 3.9.16/Java 21 reactor `verify`: **360 tests PASS** (124 core, 7 qualified parser, 229 server), zero failures/errors/skips, 19.294 seconds (`/tmp/es-d04-fga-full-verify.log`). The explicit disposable main is additional manual evidence, not a silently skipped JUnit test.
- Repository integrity/content checks PASS; Python tests **10 PASS**. Node 24 schema checks **17 PASS**. The first schema invocation lacked local Ajv and did not run tests; the successful retry read existing installed dependencies via `NODE_PATH=/home/tim/IdeaProjects/environment-studio/frontend/node_modules`, without installing or modifying shared dependencies. Logs: `/tmp/es-d04-fga-{repository,content,python}.log`, `/tmp/es-d04-fga-schema-green.log`.

## Actual independent Oracle case

Saved executable test source: `OracleFgaQualification.java` in the server observation test package. The runner was compiled and executed against this worktree's production classes, with existing pinned runtime dependencies. Safe output: `/tmp/es-d04-fga-actual.log`. Two initial scratch compilation corrections concerned the existing compiler format argument; neither is claimed as runtime RED.

Only the designated disposable Oracle container `es-qual-oracle-374bf3f7`, Oracle 23.26.3, service FREEPDB1/port 32776 was used. Independently invented owner `ES_FGA_O_91ED3F3A` and reader `ES_FGA_R_91ED3F3A` contain a new generic two-row table and harmless session-info handler. The owner has a bounded 256 MiB quota. Automatic PUBLIC INHERIT grants were immediately revoked for these two newly created users only. The existing pinned PUBLIC baseline was used unchanged; no old D04/D07 objects, users or global/vendor grants were modified.

Actual outcomes, 42 assertions before the additional final cleanup assertion:

1. Clean reader with direct catalog/table grants and account READ ONLY returned Complete with both independent exact XML strings, including Greek and supplementary Unicode characters.
2. Revoking this reader's access to `SYS.DBA_AUDIT_POLICIES` returned `METADATA_UNAVAILABLE` before the source barrier. Restoring only that direct grant allowed later checks.
3. Enabled SELECT policy without a handler and enabled UPDATE-only policy each returned `VISIBILITY_UNQUALIFIED` before source reads.
4. Enabled SELECT policy with a harmless `DBMS_APPLICATION_INFO.SET_CLIENT_INFO` handler likewise refused before source reads.
5. Independent direct JDBC SELECT under account READ ONLY and `SET TRANSACTION READ ONLY` actually invoked the handler: the same-session client-info witness matched the independent canary. This establishes feature availability and distinguishes a working handler from a policy ignored by this server edition. It made no persistent handler writes.
6. Disabled policy and subsequent removed policy both returned exact Complete again. Each adapter attempt closed credentials, confirmed cleanup and left no reader session. Direct control rolled back and closed. The final cleanup locked both new accounts and independently confirmed no sessions for either. No OS signals were used.

Passwords were generated and held only in process memory and passed through process stdin/JDBC properties, never argv, environment variables or persisted transcripts. Output was checked for password canaries before release. The small mock XML and handler are independently invented in this test source; the existing public invented native definition supplies generic projection declarations only. No real application configuration or private observation material is included.

Oracle's documented [FGA handler API](https://docs.oracle.com/en/database/oracle/oracle-database/26/arpls/DBMS_FGA.html) informed the independent control. Admission intentionally does not evaluate conditions or infer that particular handlers are harmless.

The lead owns independent review/integration and image gates. This slice used about 15 minutes of active correction/qualification work, including approximately 2 minutes of harness/API/dependency setup correction; it was interrupted for the separate D07b int64 review fix. No speed-up or production readiness is claimed.
