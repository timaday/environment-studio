# PostgreSQL 16.11 full operator journey evidence

Date: 14 September 2026
Source checkout: `main` at `60b0069c89e6bbf2624ba6bee483f521d4b054fd`.
Environment: local no-OIDC hosted container on `127.0.0.1:18181` with disposable `postgres:16.11-bookworm` fixture container `es-e2e-pg16`.

## Browser journey

Command:

```sh
node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Desktop result: PASS.

Desktop evidence directory:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789417419148
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
/home/tim/.tmp/es-full-journey-20260914/journey-1789417419148/screenshots
```


Responsive narrow run:

```sh
ES_JOURNEY_WIDTH=390 ES_JOURNEY_HEIGHT=900 node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Narrow result: PASS. Evidence directory:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789417426526
```

The narrow run captured the same 24 page/view states with `uiGaps: []` and no horizontal overflow at the recorded 390px viewport. The narrow downloaded archive SHA-256 was:

```text
96a8e9c61b077f33549e79fa76d4ee616e8b4b393587e3f4c1425f2e76589c1c
```

Captured views: home before definition, definition empty/current, upload before save, draft saved, ready to publish, published, plan create, inspection prerequisite, credentials form, current inspection valid, target structure loaded, target structure saved unresolved, target authored unresolved, values unresolved, values comparison before edit, values target saved, target-complete plan, Raw comparison, Placeholders comparison, Formatted comparison, validation summary, computed rules, export readiness and export downloaded.

Observed UI result: `uiGaps: []`. Screenshots have no horizontal overflow at the recorded desktop or 390px narrow viewport. The comparison screenshots show the many-CLOB navigator, Raw / Placeholders / Formatted modes, loaded-document search, selected binding rail, whole-plan mapped-location totals and Raw-mode mapped-span highlight. Native checkbox render boxes remain smaller than 44px, but they are within labelled rows; this is a browser rendering detail to revisit in visual hardening rather than a journey blocker.

## Downloaded package inspection

Downloaded archive:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789417419148/downloads/environment-studio-guarded-package.zip
```

SHA-256:

```text
e9cb2dcc76cc2510518c96b6cf8b7e9f0c254dd518b1755f2be512e63c966ca8
```

Inspection artifact:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789417419148/archive-inspection.json
```

Confirmed contents:

- ZIP members: `manifest.json`, `payload.json`, `transaction.sql`, `instructions.txt`.
- Engine and client: PostgreSQL 16.11 with psql 16.11 on linux-amd64.
- Template: `postgresql16-text-v1`.
- Payload table: `mock_pg.mock_tiles`, key column `mock_key`, XML text column `mock_xml`.
- Records: 2 total, 1 changed, 1 unchanged.
- Changed record target contains the operator-entered target value.
- Unchanged record target bytes equal original bytes.
- SQL contains the PostgreSQL 16.11 server-version pin, physical destination checks, byte-level original/target checks, unsupported write-effect checks and program digest marker.
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
