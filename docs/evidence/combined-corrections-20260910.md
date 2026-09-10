# Combined correction candidate — 10 September 2026

This is a separate review candidate on base
`41effa61303172ebdcebbc1b282a3b067b09bcec`, not accepted root integration or a
release-qualified artifact. It combines the completed slices below without
IDE2's incomplete native closure WIP. Root remains integration lead; IDE2
retains exclusive native implementation ownership.

| Original immutable candidate | Scope |
| --- | --- |
| 01b6485bbef25e47e9c71299778904c4cccdddfd + 3a84c2c432aec05d256b0f5e85cfd4636ee789b8 | JNI-QA-001 original absolute startup deadline through both cleanup locks |
| 2d185bd0a88ae83a5788340d8fde2f9b8c5753bb | OBS-QA-001 cancellation during final JDBC cleanup |
| a6291d2605b87d3730b2166e36f5aa7e03d253d7 | OBS-QA-002 qualified redundant PostgreSQL uniqueness |
| 59fef10bb7e87f7c3d99038d82b2beea315aaaa3 | Closed physical view client |
| 06b0888726b3bbd43e98498af18a8a2fee57c55e | Exact computed view client |
| 954ded65e27117c0dd963cad9d1a52cedd34a06f | Definition/history discovery client |
| 32499697b263c63e22ad928fb9616928e9645938 | Owned review/content-policy API and proof invalidation |
| 6e3acf6ec2fbc60256feb13ada90ec053dccad9f | Typed review browser request/receipt |
| 2ff18692bea00ea4d75b9dc0297a124b18f452af | Closed compiler result compatibility; production remains incomplete |
| 0aaaa769a24e46a058bdb3d132ecc12b26986f3c | TEST-QA-001 actual settlement in sequential HTTP tests |

The eleven picks end at144a6928ef350721bcd439193a05d546b0d02e23.
One integration reconciliation changes six ordinary requests in the newly added
review HTTP method to its existing sequentialRequest helper. Request arguments,
assertions, policy outcomes, retirement replay and all four immediate429 held-body
controls remain unchanged. Production record lifetime/capacity is unchanged.

## Acceptance and executed checks

Late cancelled observation cannot publish success; qualified duplicate PostgreSQL
keys remain readable; native cleanup cannot renew startup after either lock.
Review binds the original owner/revision/fingerprint and all affected-document
policies, preserves exact replay and cannot authorize export. Client decoding
stays closed and correlated. Both v3 compiler-result states retain complete checked
model equality while physical blockers refuse; the actual compiler still emits
Incomplete. Ordinary sequential tests wait for actual registry removal, while
concurrent semantic requests still refuse429. These are the combined focused
acceptance examples; individual slice evidence retains its original RED/mutations.

A controlled external real review HTTP test reproduced expected200/actual429
when materialization's original worker was held after resource close and before
workerClosed. With the six-call correction, that same test passes after its
actual zero-record barrier releases the hold. No response, guard, clock or record
is fabricated. RED teardown also releases the hold; its barrier marker alone is
not a success oracle. Original/instrumented sources, exact commands and log hashes
are frozen in `es-combined-review-settlement-controls-20260910` outside the repo.
A compiled guard mutation removing the helper wait fails the same200/429
assertion after successful compilation. Its first attempt overlapped Maven
recompilation and failed Spring setup with a missing class; that attempt is
preserved as setup failure, not a killed mutant. The completed mutant2 run was
serialized after Maven and reached the intended assertion.

Combined focused Maven **351 tests pass:70 core,1 parser,236 server,44 supervisor**,
zero failures/errors/skips,76 seconds. This includes all16 HTTP boundary cases,
review/proof/transport/cancellation, compiler-consumer compatibility, observation
corrections and both native launch/bridge classes. JDK21 and Maven3.9.16 ran
`mvn -B -ntp -f backend/pom.xml -pl server,tools/guarded-supervisor -am`
with explicit affected class selectors and `test`; exact command and output are
retained in `es-combined-focused1-20260910.log` and the session command record.
The earlier `-DskipTests test` was compilation setup only.

Combined G02 passes **136 frontend tests,59 schema tests**, TypeScript including
E2E types, Biome46 files and production build. Node24.20.0 ran fresh `npm ci`,
then `npm run check`, `npm test` and `npm run build` with `--prefix frontend`.
Exact log: `es-combined-g02-20260910.log`. This is typed API/client evidence;
no new React journey, browser or accessibility qualification is claimed.

## Fixed review and limits

Non-author fixed source review independently verified all1103 source hashes,
eleven-pick provenance, the exact six-call patch and unchanged immediate capacity
controls. No confirmed blocking integration finding. Frozen manifest SHA256:
`79994a2388fc3a316aab955a7abc1259874d7e4edffddda071fbfd65a62abe7a`;
external report `es-combined-fixed1-review-20260910.md`. Separate completed fixed
reviews cover the native deadline correction and complete view/discovery client
stack. Reviewers executed no tests, builds or probes.

G00 whole-diff generic provenance/content/integrity/whitespace and11 Python tests
pass before committing this candidate. All fixtures are independently
invented; native host-derived closure evidence remains external and excluded.
No database/container/full-build slot was used. Focused native tests used isolated
outputs in this separate worktree; no IDE2 output was reused.

Remote baseline30446's six HTTP failures remain an actual failed full gate.
Controlled scheduling evidence establishes a concrete test defect; it does not
prove the cause of every original failure. Required independent corrected-probe
verification and the combined full Maven/OCI gates remain pending. The prior
c3b891a image does not contain these changes. No production compiler/publication,
native/client/export, resource allocation or release readiness was enabled.
The user published exact875a257d659d3139a2c180cc587531ddcd095c96 on the combined
review branch; lead remote-ref inspection and fetch confirm it. Issue9
assignment5621471846 and reviewer acknowledgement5621531344 activate the existing
remote review lane on clean checkouts. The first full Maven checkpoint5621613092
reports core335/parser7 pass and two V3ProfileBoundaryTest logout204/503 failures.
The lead acknowledges these as actual gate failures with cause needing
reproduction; the reviewer owns bounded isolation. No duplicate full campaign,
G08 run, accepted integration or release qualification is claimed.
