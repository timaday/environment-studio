# D01a definition inspector — local UI evidence

2026-09-08. Base: `b00e5776b074d271675d517bc106290b7f14be8f`.
The candidate is the writer's uncommitted assigned-file diff; integration and
independent review belong to the lead. This is local development proof, not
HiveGate execution or release qualification.

ES-03/04/09/14: a typed, read-only definition inspector displays unavailable,
loading, rejected and incomplete states. A rejected result has no model. An
incomplete projection displays declared types, fields and relations. The exact
decimal-string revision, complete blocker count and corrective diagnostics stay
visible through Model / Source / Diagnostics selection. Arrow keys wrap;
Home/End select first/last; Tab enters the readable, scrollable panel. Source
characters are rendered as text without browser persistence. The synthetic
Definitions screen explicitly disclaims live compilation, upload, save and
publication. Existing comparison document selection and concrete current/target
values remain available after navigation back; export stays disabled.

## Independent mock provenance

`frontend/src/components/DefinitionPreview.tsx` invents a single `glimmer` type
with a `shade` field and a mock document without mappings. It was authored solely
for this UI task, independently of private configuration, database metadata or
real application semantics. Its `PREVIEW_*` diagnostics are illustrative display
data, explicitly not a compiler response. Unit tests independently invent a
recursive `echo` relation, a large revision, safe root diagnostics and a source
string containing markup characters/whitespace. These are presentation cases;
the unit source string is not a compiler fixture. No external/private input was
read. Neither example establishes application qualification.

## Actual commands and results

All npm commands below ran in `/tmp/es-d01-ui-b00e577` via the same prefix:

```sh
docker run --rm -v /tmp/es-d01-ui-b00e577:/work -w /work node:24-bookworm-slim@sha256:ba849c60be29959425b8734d57b8b4b7d56f98edd9504c9af091d5281095a71e
```

Append each command to that prefix:

| Command | Observed result |
| --- | --- |
| `npm ci --prefix frontend` | Passed; 129 packages installed. Reported one moderate advisory in the unchanged lockfile; no dependency updates attempted. |
| `npm exec --prefix frontend -- vitest run --root frontend src/components/DefinitionInspector.test.tsx` | RED: 3/3 failed against a renderable empty inspector, missing Model panel and loading status. GREEN: 3/3 passed after implementation. |
| `npm exec --prefix frontend -- vitest run --root frontend src/App.test.tsx` | RED: new navigation case failed because Definitions control was absent; 3 existing comparison cases passed. |
| `npm run format --prefix frontend` | Applied formatting; first attempts reported static tab-panel focus lint. A narrow documented suppression on `tabIndex` preserves keyboard access; no gate was disabled. |
| `npm run check --prefix frontend` | Final PASS: TypeScript and Biome, 11 files. |
| `npm test --prefix frontend` | PASS: 7 component tests plus 4 schema contract tests. Includes navigation case that previously failed. |
| `npm run build --prefix frontend` | PASS: TypeScript and Vite production build, 26 modules. |
| `npm run test:coverage --prefix frontend` | PASS: 7 component cases, after strengthening persistence/focus/source-escaping assertions. Overall 100% lines / 93.33% branches; inspector 100% lines / 93.54% branches. |

The untested inspector branches are empty entity-type and empty-field summaries.
Coverage is not a release verdict. G03 browser/axe, zoom, narrow viewport,
long-name layout and screen-reader review are left for lead integration. No live
HTTP route exists; network/privacy/session authority is not implemented by this
presentation component. G07 real-data qualification and D01 publication remain
outside this slice. Frontend artifacts were built only in the assigned worktree.

Writer elapsed time: approximately 7 minutes through these checks; blocked on
contracts: 0; local lint/format rework: approximately 1 minute. Integration/review
time is not included. No speed-up claim or token measurement is available.
