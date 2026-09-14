# PostgreSQL 16.11 full operator journey evidence

Date: 14 September 2026
Source checkout: `main` at `64474ee061e0c6922832f08702fb0b3cbc408990`.
Environment: local no-OIDC hosted container on `127.0.0.1:18181` with disposable `postgres:16.11-bookworm` fixture container `es-e2e-pg16`.


## Connection-string hosted rerun — 15 September 2026 early

Source checkout: `main` at `d8a4795b0f68ac03d9408026e02205d0c6e021e5`.
Environment: local no-OIDC hosted container on `127.0.0.1:18181` with disposable `postgres:16.11-bookworm` fixture container `es-e2e-pg16`.

This rerun verifies the simplified PostgreSQL 16.11 connection flow. The hosted
deployment starts without stored database target facts or database credentials.
The operator publishes a definition, enters the JDBC target
`jdbc:postgresql://es-e2e-pg16:5432/appdb` in the plan setup screen, then enters
username/password only for the read-only inspection operation. The application
stores only host, port and database metadata for the target.

Desktop command:

```sh
node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Desktop result: PASS. Evidence directory:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789427120405
```

Desktop screenshots: 24 complete page/view captures. Downloaded guarded package
SHA-256: `9140f58539a69dba9701c498c9fc1246fe19bb9abb0671ba035484885db43625`.
Archive inspection artifact:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789427120405/archive-inspection.json
```

Narrow command:

```sh
ES_JOURNEY_WIDTH=390 ES_JOURNEY_HEIGHT=900 node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Narrow result: PASS. Evidence directory:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789427147449
```

Narrow screenshots: 24 complete page/view captures with `uiGaps: []`. Downloaded
guarded package SHA-256: `40c34b042bc5551f2584c18326b7d550dd87072079e5cc120f03b7a414eae149`.
Archive inspection artifact:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789427147449/archive-inspection.json
```

Both archive inspections confirm exact ZIP members `manifest.json`,
`payload.json`, `transaction.sql` and `instructions.txt`; PostgreSQL 16.11 and
psql 16.11/linux-amd64 pins; template `postgresql16-text-v1`; operator-supplied
plaintext pilot transport; observed destination host `es-e2e-pg16`, port `5432`,
database `appdb`; two XML text records with one changed and one unchanged; the
operator-entered target value in the changed target XML; unchanged target bytes
equal to original bytes; SQL server-version, physical-destination, original-byte
and target-byte guards; no COMMIT/ROLLBACK in `transaction.sql`; and instructions
that `transaction.sql` must not be executed directly.

Validation result in both browser runs: target complete; SCOPE, DEFINITION,
MAPPING, VALUES, SEMANTICS, XML_FIDELITY and DESTINATION pass; CLIENT_CAPABILITY,
CONTENT_POLICY and REVIEW remain UNKNOWN, so `exportAvailable` remains false and
the downloaded ZIP is an unqualified package candidate for external review.

## Browser journey

Command:

```sh
node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Desktop result: PASS.

Desktop evidence directory:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789420057378
```

The browser journey uses Basic local-operator auth and performs the ordinary UI flow:

1. Open the hosted workspace.
2. Upload the PostgreSQL-only v3 definition.
3. Save and publish the definition.
4. Create a plan for the published definition, binding and PostgreSQL 16.11 destination.
5. Reserve the current inspection.
6. Submit one-use read-only database credentials.
7. Confirm the current inspection succeeds and cleanup is complete.
8. Open the target-structure authoring view.
9. Save a retained target structure with one required target value unresolved.
10. Return to the plan and confirm the saved target draft is reflected.
11. Enter the target value.
12. Inspect Raw, Placeholders and Formatted current/target XML views.
13. Validate the plan and inspect computed-rule results.
14. Download the guarded package candidate.

The desktop run captured 24 page/view screenshots under:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789420057378/screenshots
```


Responsive narrow run:

```sh
ES_JOURNEY_WIDTH=390 ES_JOURNEY_HEIGHT=900 node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Narrow result: PASS. Evidence directory:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789420082532
```

The narrow run captured the same 24 page/view states with `uiGaps: []` and no horizontal overflow at the recorded 390px viewport. The narrow downloaded archive SHA-256 was:

```text
f8a98f62d4233a2545a36ad51cd5f2424d131aad53165772ffd741c8afc9a547
```

Captured views: home before definition, definition empty/current, upload before save, draft saved, ready to publish, published, plan create, inspection prerequisite, credentials form, current inspection valid, target structure loaded, target structure saved unresolved, target authored unresolved, values unresolved, values comparison before edit, values target saved, target-complete plan, Raw comparison, Placeholders comparison, Formatted comparison, validation summary, computed rules, export readiness and export downloaded.

Observed UI result: `uiGaps: []`. Screenshots have no horizontal overflow at the recorded desktop or 390px narrow viewport. The refreshed implementation keeps prior inspection context visible while plan/document refreshes are in flight and marks those regions busy instead of blanking the workspace. The comparison screenshots show the many-CLOB navigator, Raw / Placeholders / Formatted modes, loaded-document search, selected binding rail, whole-plan mapped-location totals and Raw-mode mapped-span highlight. Native checkbox render boxes remain smaller than 44px, but they are within labelled rows; this is a browser rendering detail to revisit in visual hardening rather than a journey blocker.

## Downloaded package inspection

Downloaded archive:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789420057378/downloads/environment-studio-guarded-package.zip
```

SHA-256:

```text
aa4cc4e0dff8e55d87fa3c3ebd682e2407d95a54024c011bd0cd1295570c8b33
```

Inspection artifact:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789420057378/archive-inspection.json
```

Confirmed contents:

- ZIP members: `manifest.json`, `payload.json`, `transaction.sql`, `instructions.txt`.
- Engine and client: PostgreSQL 16.11 with psql 16.11 on linux-amd64.
- Template: `postgresql16-text-v1`.
- Payload table: `mock_pg.mock_tiles`, key column `mock_key`, XML text column `mock_xml`.
- Records: 2 total, 1 changed, 1 unchanged.
- Changed record target contains the operator-entered target value.
- Unchanged record target bytes equal original bytes.
- SQL contains the PostgreSQL 16.11 `server_version_num` pin, physical destination checks, byte-level original/target checks and program digest marker. Transaction commit/rollback control remains outside `transaction.sql` and is owned by the supervisor, as stated in `instructions.txt`.
- Instructions state that `transaction.sql` must not be executed directly; the supervisor owns transaction control and commit acknowledgement.

## External PostgreSQL execution witness

Historical execution witness from the earlier 14 September full-journey run: a temporary same-package Java witness outside the repository checkout used that run's downloaded archive with the existing guarded `PackageCheck`, `SessionEngine`, `ClientProtocol` and `OwnedNativeProcess` classes. It used a transient write-capable role created only in the disposable PostgreSQL 16.11 container. The application inspection account remained read-only. This witness was not rerun for the CLOB-comparison polish; the fresh committed rerun above covers browser journey and archive inspection only.

Historical witness result artifact:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789412042783/supervisor-witness.json
```

Historical result: PASS.

Observed outcomes in that earlier run:

- Normal package application: `APPLIED`, cleanup `COMPLETE`, changed row contains the target value after commit acknowledgement.
- Injected pre-program write failure: `NOT_APPLIED`, cleanup `COMPLETE`, original row preserved.

This proves that earlier downloaded package could be driven through the guarded PostgreSQL client protocol against the disposable PostgreSQL 16.11 fixture. It does not prove execution of the fresh CLOB-polish rerun package. Standalone launcher admission is covered separately by the PostgreSQL 16.11 supervisor-admission evidence.

## Verification commands

```sh
npx vitest run src/hosted/V3PlanInspection.test.tsx src/hosted/useV3PlanInspection.test.ts
npm run check --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
git diff --check
python3 scripts/check_repository.py
python3 scripts/check_repository_content.py
python3 -m unittest discover -s scripts -p 'test_*.py'
scripts/studio.sh fast-package
scripts/studio.sh hosted-up
node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
ES_JOURNEY_WIDTH=390 ES_JOURNEY_HEIGHT=900 node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Results: all passed in this workspace. `scripts/studio.sh fast-package` builds a runnable local Docker image while skipping Docker-stage Maven/UI tests for iteration speed; it does not replace full release-grade Docker test evidence.

## Remaining limitations

- The pilot scope is PostgreSQL 16.11 only. Oracle remains unavailable for this pilot.
- The downloaded package is an unqualified candidate; validation still reports unknown release-qualification checks for client capability, content policy and review.
- The standalone native-supervisor admission path is available only for the pinned PostgreSQL 16.11/psql 16.11/linux-amd64 path covered by separate supervisor-admission evidence. The fresh CLOB-polish package was not externally executed through that launcher.
- Actual HiveForge deployment, GHCR digest pull, platform routing/TLS and private production definition/data qualification are external and not claimed here.

## CLOB diff and relationship-map UI rerun — 14 September 2026 late

Source checkout: `main` at `d6fd482706feb810a7d8f32428f9017d48d7c7c6` with working-tree UI changes for XML/CLOB line highlighting and the definition-derived relationship map.

Implemented UI behavior:

- Raw XML/CLOB comparison marks changed current lines with the current-side red treatment and changed target lines with the target-side teal treatment while preserving exact loaded text.
- Placeholder comparison also marks changed mapped-token lines from the binding rail's returned change state, so stable placeholder tokens still show concrete value differences.
- Target structure now includes a collapsible Current/Target relationship map derived from the published v3 definition's entity types, declared relationships, derived groups and returned current/draft pages. It does not infer hidden page totals or fabricate target decisions.

Focused author checks:

```sh
npm run check --prefix frontend
npm test --prefix frontend -- V3PlanInspection.test.tsx V3TargetStructure.test.tsx
```

Results: PASS. The test command ran the frontend suite with 477 passing Vitest tests and 61 passing schema contract tests.

Packaged hosted image check:

```sh
scripts/studio.sh fast-package
scripts/studio.sh hosted-up
```

Results: PASS. The fast package path built the runtime image, skipped Docker-stage Maven/UI tests by design, verified guarded-supervisor SHA256SUMS, and passed container smoke, private workspace initialization, schema2 initializer/upgrade/refusal and schema3 initialization/refusal checks. Hosted no-OIDC service became healthy on `127.0.0.1:18181`.

Fresh desktop browser journey:

```sh
node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Result: PASS.
Evidence directory:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789421854785
```

Screenshots: 24. Downloaded guarded package SHA-256: `d7618adffa6b0945df6c0cba94b69670df2d0e7592d92143a33f342c0bf3acbf`.

Fresh narrow browser journey:

```sh
ES_JOURNEY_WIDTH=390 ES_JOURNEY_HEIGHT=900 node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Result: PASS.
Evidence directory:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789421866532
```

Screenshots: 24. Downloaded guarded package SHA-256: `c15da79ab496285ce73288af7ed68a6a931aa775c1a8e0f2056604613e0ad14f`.

Observed UI result: `uiGaps: []` for both desktop and narrow. Manual visual spot-check confirmed Raw and Placeholder comparison line highlighting and the side-by-side relationship map. This is a UI/presentation rerun over the PostgreSQL 16.11 pilot workflow; release qualification limitations above remain unchanged.
