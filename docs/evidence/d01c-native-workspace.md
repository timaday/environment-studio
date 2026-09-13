# D01c/D05b native workspace — 8 September 2026

Candidate base: `2dfbca7518258b7ce87deae0b893c1e311c447ee`, isolated worktree
`/tmp/es-d01c-native-workspace`. This implements owned native v2 drafts,
maintainer definition publication and owner profile publication. It grants no
observation, transformation, export, deployment or managed-database write authority.

## Scope and provenance

Core owns closed decoded commands, immutable typed revision contents, publication
policy checks and native-framed command/publication identities. Server adapters
own strict input/output codecs, the private exact OIDC publisher allowlist,
portable profile acceptance, SQLite and HTTP. Source/projection DTOs print safe
fixed text under framework DEBUG. All external schema loading is disabled.

The native and profile input families are the existing independently invented
`fixtures/native-v2` and `fixtures/profile-v2`, with their existing provenance
manifests. New owner names, UUIDs, neutral labels and canaries were independently
invented for these tests. No real, renamed, masked or reconstructed private model
was consulted. Database files are private temporary test stores outside the
checkout; no private data or credential fixtures were added. Mock OIDC keys and
tokens stay in test memory. The bounded profile BYTE_LIMIT distinction changes
only the refusal reason; private Accepted construction and every structural,
portable round-trip, digest and value-free boundary remain enforced.

Schema 2 adds artifact membership, native revision and replay tables referencing
the existing shared UUID/owner catalog. It leaves the v1 table definitions,
snapshot bytes, digests and replay encoding unchanged. Runtime opens only schema
2. The sole offline upgrade validates a complete schema-1 store, repeats checks
inside an IMMEDIATE transaction, adds the metadata tables, checks schema 2 before
commit and returns without starting Spring/web/IdP services. Unknown, corrupt or
already upgraded stores refuse. New initialization never overwrites a store.

The current publication registry is separate from the typed historical codec.
Historical results decode their original readiness, diagnostics and positive
mechanism versions without compiler invocation. Publication still requires the
exact four currently supported mechanism version-1 entries. Known-model shape
validation on read is not recompilation or a new readiness decision. The integrity
envelope binds authenticated owner, UUID, artifact kind, revision and the complete
encoded immutable result; publication/reference and replay chains are also
checked. This detects inconsistent corruption, not a malicious whole-database
rewrite or rollback with recomputed unkeyed hashes.

## Observed TDD and corrections

All Maven invocations below used the pinned host Maven/JDK helper. No test
selection, skip, failIfNoSpecifiedTests or other gate-disabling flags were used.

- `/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml test`,
  `/tmp/es-d01c-schema-red.log`: initializer behavior failed **expected schema 2,
  actual schema 1**. An earlier test setup probe used an incorrect database
  filename and was corrected before this recorded behavior RED.
- Same command, `/tmp/es-d01c-authority-red.log`: three publication assertions
  failed against the explicit unavailable service stub: next immutable published
  revision, exact document-policy coverage and revoked publisher refusal before
  replay. Core publication rules were then implemented.
- Same command, `/tmp/es-d01c-integration.log`: storage/owner/profile/race tests
  passed; closed definition inspection schema exposed missing fixed identity
  scope/normalization and count-rule kind properties. The rendering adapter was
  corrected; stored typed history was unchanged.
- Same command, `/tmp/es-d01c-http.log`: genuine OIDC workspace flow exposed
  missing explicit Spring path-variable names. These annotations were fixed;
  build compiler flags were not changed.
- Same command, `/tmp/es-d01c-profile-byte-red.log`: two actual profile cases
  expected BYTE_LIMIT but received RESOURCE_LIMIT: oversized source bytes and
  structurally valid capture whose escaped portable JSON exceeds 1 MiB. The
  adapter now preserves typed BYTE_LIMIT versus structural/numeric RESOURCE_LIMIT;
  the workspace maps only byte refusal to 413.
- Same command, `/tmp/es-d01c-config-red.log`: unknown publisher-entry property
  was silently ignored by default binding. Strict unbound-property refusal now
  rejects it with a safe configuration code.

## Final checks and adverse matrix

`/tmp/es-lead-toolchain/maven/bin/mvn -B -ntp -f backend/pom.xml verify`
completed successfully in `/tmp/es-d01c-freeze-verify.log`: **256 tests**,
84 core, 7 qualified parser and 165 server, no failures/errors/skips. This worker
base contains integrated D05 but not the concurrent D04 additions; root must
verify the final combined candidate separately.

The actual tests cover:

- Native save/publish/fork and exact original replay after later edits/restart;
  preserved exact source, projection and immutable historical revisions.
- Live maintainer refusal before replay; exact issuer+subject matching, absent
  deny, unknown private configuration property refusal and forged role-header
  denial. Profile publication remains an owner operation.
- Exact explicit document policies and sorted publication output; incomplete
  and unsupported-mechanism publication refusal. A historical mechanism version
  remains readable/replayable without new compilation, but cannot newly publish.
- Profile validation against the owned exact historical published definition;
  draft reference refusal, cross-owner 404 and later definition draft not
  invalidating a pinned profile publication.
- Two stale publishers: exactly one commit and one conflict. Ninety-nine v1
  objects plus two simultaneous native creates: exactly one new object and one
  capacity refusal, proving the shared 100-object owner quota.
- All 32 immutable revisions retained; new revision refusal at capacity and
  original successful replay still available. Artifact-kind collision refusal.
- Source/wrapper bounds, duplicate keys, unknown properties, lone surrogates,
  trailing roots and decoded whitespace/property-order equivalence.
- Corrupted owner issuer/subject, native ID, artifact kind, snapshot digest,
  replay kind/digest, reference columns, compiler version and publication policy
  all refuse startup. Existing v1 permission/symlink/journal/quota/crash checks
  continue to run against the same protected store adapter.
- An actual schema-1-format store with v1 records, revisions and replays upgrades
  through the public offline CLI with every legacy table value byte-for-byte
  unchanged and original replay/history results preserved. The test constructs
  that format by removing only empty schema-2 extension tables from independently
  created v1 snapshots; it does not claim execution of a historical binary.
- An independent JVM halts with exit 17 inside the matching SQLite metadata
  transaction, forcing a hot journal; recovery retains schema 1 and all legacy
  bytes, then the real offline upgrade succeeds. This is a SQLite transaction
  interruption control, not a claim that the production CLI was killed at an
  instrumented instruction. Repeat upgrade, extra CLI arguments, schema 99 and
  corrupt schema-1 source digest refuse with unchanged database bytes.
- The existing genuine mock-provider OIDC authorization-code/PKCE/nonce exchange
  creates the session used for v2 definition/profile save and publication. No
  oidcLogin helper substitutes for this flow. Missing CSRF and wrong Origin deny;
  reads are no-store, ownership is enforced and forged maintainer identity denies.
- Existing global DEBUG capture assertions continue to check original/encoded
  client secret, access token, ID-token prefixes, CSRF prefixes and workspace
  source canaries. Additional SQLite byte scans reject provider credential/token
  canaries in persistence. Profile donor-value exclusion remains covered by the
  original D05 capture/portable tests, not inferred from a safe DTO toString.

Independent Python implementations of native framing produced the constants
asserted in core tests (not values copied from the Java implementation):

| Identity | SHA-256 |
| --- | --- |
| UTF-8 command with Ω and newline | `5585a03f4392a1bd60257f3bf5a050adc8e1190ae28fe9b06e3ac91df9d13c96` |
| Definition publication | `43a73feed4d8d4bd5d3588a38fee3ee6d33c495569515164173952ed82647866` |
| Profile publication | `83fcf370acb42924478d03ad0ef2d412b2b622f075ad2642e69e7d7707726a5a` |

The independent oracle framed UTF-8 strings as `S<byte length>:<bytes>`, arrays
as `A<count>:` plus ordered items and objects as `O<count>:` plus UTF-8 sorted
key/value frames, after the exact domain and NUL byte. Tests spell out the
independent command and publication inputs. Existing native/profile content
digest fixture-oracle tests also remain in the full reactor.

Five targeted guard mutants were each restored immediately after a full-reactor
`mvn ... test` behavior failure. Logs `/tmp/es-d01c-mutant-{maintainer,policies,
reference,replay,owner}.log` record all five **killed**: removal of maintainer
check, document coverage check, published-reference requirement, early successful
replay and owner comparison. No mutation survivor was accepted as qualified.
The final verify ran after restoration and later codec/upgrade checks.

`python3 scripts/check_repository.py` and
`python3 scripts/check_repository_content.py` passed. The content guard is a
limited pattern check, not provenance proof. `python3 -m unittest discover -s
scripts -p 'test_*.py'` passed all 10 script tests.

## Remaining qualification and timing

Worker G08 image build/smoke: **NOT RUN**, explicitly assigned to root for the
combined reviewed image including D04. `scripts/workspace_smoke.py` now preserves
existing protected initializer/permission/overwrite checks and adds schema-2
header inspection, an independently derived empty schema-1 mock in memory,
actual offline upgrade and checksum-preserving repeat/extra-argument refusal.
It touches only the invocation-owned volume and transfers no credentials/model.
Root must run this harness before claiming combined OCI qualification.

No hosted capture/plan binding, browser publication workflow, actual operator
allowlist, actual IdP/HiveForge storage, deployment rollback or private application
qualification is claimed. Root owns those integrations and capability advertising.
No Git state was mutated by this writer; root will hash-check, review and integrate
this candidate. No parallel speed-up or isolated active-time total is claimed.
