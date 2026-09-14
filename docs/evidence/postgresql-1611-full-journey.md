# PostgreSQL 16.11 full operator journey evidence

Date: 14 September 2026
Source checkout: `main` at `f769ecf82108785363265b85b77573292c54d27e`.
Environment: local no-OIDC hosted container on `127.0.0.1:18181` with disposable `postgres:16.11-bookworm` fixture container `es-e2e-pg16`.

## Browser journey

Command:

```sh
node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Desktop result: PASS.

Desktop evidence directory:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789412042783
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
/home/tim/.tmp/es-full-journey-20260914/journey-1789412042783/screenshots
```


Responsive narrow run:

```sh
ES_JOURNEY_WIDTH=390 ES_JOURNEY_HEIGHT=900 node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Narrow result: PASS. Evidence directory:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789412173685
```

The narrow run captured the same 24 page/view states with `uiGaps: []` and no horizontal overflow at the recorded 390px viewport. The narrow downloaded archive SHA-256 was:

```text
80e956ac94ca0ebc79d850dcb291eadd741e9503e29804ea92342b49e4c541e8
```

Captured views: home before definition, definition empty/current, upload before save, draft saved, ready to publish, published, plan create, inspection prerequisite, credentials form, current inspection valid, target structure loaded, target structure saved unresolved, target authored unresolved, values unresolved, values comparison before edit, values target saved, target-complete plan, Raw comparison, Placeholders comparison, Formatted comparison, validation summary, computed rules, export readiness and export downloaded.

Observed UI result: `uiGaps: []`. Screenshots have no horizontal overflow at the recorded desktop viewport. Native checkbox render boxes remain smaller than 44px, but they are within labelled rows; this is a browser rendering detail to revisit in visual hardening rather than a journey blocker.

## Downloaded package inspection

Downloaded archive:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789412042783/downloads/environment-studio-guarded-package.zip
```

SHA-256:

```text
d8debd9920314317ccddf81a306c7806d8401f43e4b4b44647952a75b5a4b920
```

Inspection artifact:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789412042783/archive-inspection.json
```

Confirmed contents:

- ZIP members: `manifest.json`, `payload.json`, `transaction.sql`, `instructions.txt`.
- Engine and client: PostgreSQL 16.11 with psql 16.11 on linux-amd64.
- Template: `postgresql16-text-v1`.
- Payload table: `mock_pg.mock_tiles`, key column `mock_key`, XML text column `mock_xml`.
- Records: 2 total, 1 changed, 1 unchanged.
- Changed record target contains the operator-entered target value.
- Unchanged record target bytes equal original bytes.
- SQL contains the PostgreSQL 16.11 server-version pin, physical destination checks, original/target digest checks, unsupported write-effect checks and program digest marker.
- Instructions state that `transaction.sql` must not be executed directly; the supervisor owns transaction control and commit acknowledgement.

## External PostgreSQL execution witness

A temporary same-package Java witness outside the repository checkout used the actual downloaded archive with the existing guarded `PackageCheck`, `SessionEngine`, `ClientProtocol` and `OwnedNativeProcess` classes. It used a transient write-capable role created only in the disposable PostgreSQL 16.11 container. The application inspection account remained read-only.

Witness result artifact:

```text
/home/tim/.tmp/es-full-journey-20260914/journey-1789412042783/supervisor-witness.json
```

Result: PASS.

Observed outcomes:

- Normal package application: `APPLIED`, cleanup `COMPLETE`, changed row contains the target value after commit acknowledgement.
- Injected pre-program write failure: `NOT_APPLIED`, cleanup `COMPLETE`, original row preserved.

This proves the downloaded package can be driven through the guarded PostgreSQL client protocol against the disposable PostgreSQL 16.11 fixture. It does not claim that the standalone native-supervisor launcher is production-admitted, because the current compiled runtime registry still fails closed without native privacy/runtime qualification.

## Verification commands

```sh
npm exec -- vitest run --config vite.config.ts src/hosted/V3TargetStructure.test.tsx
npm run check --prefix frontend
npm test --prefix frontend
python3 scripts/check_repository_content.py
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/check_repository.py
scripts/studio.sh fast-package
scripts/studio.sh hosted-up
node /home/tim/.tmp/es-full-journey-20260914/full-journey.cjs
```

Results: all passed in this workspace. `scripts/studio.sh fast-package` builds a runnable local Docker image while skipping Docker-stage Maven/UI tests for iteration speed; it does not replace full release-grade Docker test evidence.

## Remaining limitations

- The pilot scope is PostgreSQL 16.11 only. Oracle remains unavailable for this pilot.
- The downloaded package is an unqualified candidate; validation still reports unknown release-qualification checks for client capability, content policy and review.
- The standalone native-supervisor `Main` remains fail-closed behind runtime privacy/admission. The external witness uses the same transaction protocol classes, but it is not a production launcher admission claim.
- Actual HiveForge deployment, GHCR digest pull, platform routing/TLS and private production definition/data qualification are external and not claimed here.
