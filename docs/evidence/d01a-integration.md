# D01a integration — 8 September 2026

The version 1 draft compiler and synthetic definition inspector are implemented.
The compiler cannot return a published or export-ready model. Runtime upload,
publication, workspace persistence and database capabilities remain unavailable.
All examples were invented independently; no external application material was
used. See [engine evidence](d01a-engine.md) and [UI evidence](d01a-ui.md).

## Fixed candidates and review

Engine candidate: 12-file manifest SHA-256
`2cc1ed7e727136ad141536e8a937a05a62246467ce3314f38d22d0a74c4aa242`.
UI patch SHA-256
`d4a9d17fb9c0e5eb740fd3cf0201edb91b06abeb2a4442fc415b910cef724e10`.
Lead integration manifest SHA-256
`546ff59761275c96a7f4b67fa1cb53d525281d35804d3078e72ed400348786ec`.
The independent reviewer verified each frozen candidate and reported no material
findings. This was static review; executed checks below are the lead's evidence.

The lead copied only manifest-verified engine files and applied the unchanged UI
patch. Maven now packages the canonical schema in the server jar, including
container builds. The separate browser target keeps Chromium out of the runtime
image. CI requires that target to succeed before publication.

## Observed checks

Exact host tools: Node 24.20.0/npm from the pinned Node image, Maven 3.9.16 from
the pinned Maven image, JDK 21.0.12. Docker uses the repository's pinned images.

| Check | Actual result |
| --- | --- |
| `mvn -B -ntp -f backend/pom.xml verify` | PASS: 17 core and 31 server tests |
| `npm run check --prefix frontend` | PASS: application and browser TypeScript, Biome |
| `npm test --prefix frontend` | PASS: 7 component tests and 4 schema tests |
| `npm run test:e2e --prefix frontend` | Observed RED on baseline UI: Definitions control absent; GREEN after integration: desktop and 390px projects pass |
| `docker build --target runtime -t environment-studio:d01a-review .` | PASS: frontend checks/build, Java verify and runtime image |
| `bash scripts/container_smoke.sh environment-studio:d01a-review` | PASS: startup, static UI, health, demo capabilities and mutation/export denials |
| `docker build --target browser-check -t environment-studio:browser-check .` | PASS: pinned container toolchain, Chromium install and both browser projects |

The lead visually inspected both browser screenshots: readable source/model,
persistent blockers, visible focus and no horizontal page overflow at the tested
widths. Keyboard checks start with programmatic focus and exercise Enter,
ArrowRight, End and Home. Axe checks the Model panel. Zoom, screen readers,
complete task navigation and a representative operator rehearsal were not tested.
The in-app browser was unavailable; the committed Playwright harness ran actual
headless Chromium against a fresh local production preview server.

## Investigation and limits

Business: arbitrary entity names compile without built-in application hierarchy;
the UI labels its source synthetic and never implies publication. Engineering:
the domain model stays framework-free, resource budgets precede model assembly,
and Java owns rejection. QA: malformed numbers, Unicode, YAML features,
duplicate/unknown fields, missing references and diagnostic redaction challenge
the parser independently of happy-path schema checks.

The root integration exposed Vitest collecting Playwright tests. Its explicit
source-test include now separates the two runners; all existing component tests
still execute. The browser baseline RED confirmed that the acceptance flow was
absent before integration. Remaining publication semantics are explicit blockers.

Worker elapsed estimates: engine 14 minutes and UI 7 minutes, each approximately
1 minute rework and no reported blocked time. Lead integration/review/check work
was approximately 18 minutes, including runner separation and browser setup.
These overlap; no serial baseline was measured and no speed-up is claimed.
CI and published-image evidence for this slice will be linked after upload.
