# TDD and quality gates

Tests support investigation; a green pipeline is not a proof of product quality.
RST means **Rapid Software Testing**, using risk, context and diverse oracles.

For each behavior: write an acceptance example and meaningful failing test,
observe RED for the intended reason, implement minimal behavior, observe GREEN,
refactor with the tests running. Add property/adverse examples for invariants.
If the toolchain cannot run, record NOT RUN and obtain actual CI evidence;
never fabricate the RED observation or downgrade a missing gate to PASS.

| Gate | Trigger | Mechanism | Failure behavior |
| --- | --- | --- | --- |
| G00 repository contract | Before commit/upload and every PR | Staged-content guard, independent mock provenance review; Python structure/links/status checks + mock schema tests | Fail on known prohibited artifacts, unregistered mock data, malformed contracts or false release readiness |
| G01 core architecture and units | Every PR | Maven/JUnit, ArchUnit; no tests is a build failure | Block merge/publication |
| G02 frontend | Every PR | TypeScript, formatting, component behavior tests, production build | Block merge/publication |
| G03 browser/accessibility | UI behavior slices and release | Playwright keyboard/task flows and axe/manual assessment | Block affected capability until implemented/tested |
| G04 XML conformance | Any mapping/writer change | Golden independent outputs, no-op, non-interference, Unicode/CDATA/namespaces | Block writer support |
| G05 DB/client qualification | Adapter change and release | Disposable real Oracle/Postgres, exact CLI fault/concurrency tests | Block that combination, never silently skip |
| G06 targeted mutation | Authority/writer changes and release | PIT/reference-gate mutation plus specified manual guard mutants | Investigate survivors; required semantic survivors block |
| G07 privacy/session | Before DB input + release | Synthetic canary tracing, cancel/timeout/restart, owner isolation | Block real data mode |
| G08 OCI artifact | Every PR/main | Multi-stage test/build, non-root startup/read-only rootfs/health smoke | Block publication |
| G09 release evidence | Version tags | Capability matrix + per-gate evidence for exact candidate tree | Missing/UNKNOWN evidence blocks versioned release |
| G10 HiveForge deployment | Release | Actual pull by digest, routing/TLS, lifecycle/storage/identity | No claim of HiveForge qualification without observation |

G00/G01/G02/G08 are wired into starter CI. G03–G07/G10 include implementation
work and initially **NOT RUN** evidence. G09 deliberately fails for the starter.
Main images are explicitly development/demo images; only version tags require
the complete release gate. There is no `continue-on-error`, zero-test success or
`Export anyway` route. CI never connects to a real environment or receives its
DB credentials. Disposable DB credentials may be generated per isolated CI run,
are synthetic and cannot grant access to a user environment. These real database
engines contain independently invented mock schemas and data only. Actual user
configuration, including its model, is never fetched into CI or the checkout.

Run the staged-content guard before GitHub upload; CI repeats it after checkout.
It detects known artifact paths, native model shapes and mock registration errors,
not arbitrary private meaning in prose/code/images. Passing it does not replace
the whole-diff provenance review. Actual application qualification and Q review
take place externally; only generic findings and mock evidence enter this repo.

## Test layers and oracles

Core: declared graph/reference rules, closure, duplicate/ambiguous mapping,
value-state distinctions, stale revision evidence and deterministic ordering.
Adapters: contract fixtures for full fidelity, scoped selectors and safe refusal.
DB: independent complete mock-state comparisons and actual CLI execution, including
unchanged read dependencies and new/deleted rows. UI: user behavior through
accessible names and navigation, not implementation snapshots. Release: RST
notes and operator explanations in addition to automated assertions.

Report line/branch coverage, but do not use an aggregate percentage as a release
verdict. Mutation focus begins with converting UNKNOWN to PASS, removing a
required category, accepting stale input, omitting a dependency, weakening a
baseline/row-count/destination guard and removing rollback. Equivalent mutants
need documented reasoning; unexplained survivors are open risks.

## Repository settings to configure on GitHub

Require the stable `quality` check on main; disallow force pushes/deletion and
require review as the owner prefers. Read-only token by default; packages:write
only in publication. Pin action SHAs; Dependabot updates dependencies/actions.
Protect version tags and review release evidence. These are requested settings,
not claimed to be configured by a file in this repository.
