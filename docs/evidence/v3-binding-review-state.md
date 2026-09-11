# Complete selected-item binding review state

Base `60b6d0b8d684f03f4d82942e3451b5d8cd281c8b`. This nonvisual prerequisite
reads existing v3 binding pages for one explicit Existing/Fresh reference. It
adds no renderer, command, materialization, XML disclosure or export authority.

Acceptance: load the complete bounded collection (at most256 fields, pages100),
require consistent original scope/total, unique field IDs/tokens and matching
field-token coordinates, then require a fresh complete plan summary before
exposing rows. Preserve value/empty text/masked/absent/unresolved/unavailable and
complete/unavailable location counts. A complete empty list differs from unknown
or a failed collection. Retire values and callbacks on owner/plan/entity/
presentation/session changes. No automatic request, retry or local persistence.
See the [binding contract](../contracts/hosted-plan-structural-views-v3.md).

Callable-empty red1 failed both meaningful behaviors: no101-row complete result
and no tail-refusal error. Green1 passes both. Adverse1 passes21; focused2 passes60
across binding21, target-value28 and unchanged inventory11 cases. Check1 passes
TypeScript/e2e checking and Biome86files. Build1 passes. External logs are
`es-binding-review-{red1,green1,adverse1,focused2,check1,build1}-20260911.log`.

RST cases cover page-tail refusal, mixed totals/revisions, duplicate fields,
field-token mismatch, oversized collection, illegal masked-text disclosure,
changed final context, fresh-reference state distinctions, empty collection,
reload failure, held-page retirement by plan/entity/API/inactive/unmount,
retired callbacks, overlapping loads, held final summary and session refusal.
Only independently invented wire fixtures are used. The backend PlanBindings
implementation confirms the token field suffix; opaque identity is never inferred
from display labels. Author state tests are not actual HTTP/database evidence.

Fixed independent source review verifies three-file manifest
`787acdde26801fa0b32f7d0a3d42dd8a909ebb7b190f827003d37bc35d285191`, finding no
confirmed correction. Lead accepts this bounded nonvisual source prerequisite.
Report: external `es-binding-review-source1-20260911.md`; reviewer execution NONE.
The future renderer must propagate host session retirement and show whether the
surrounding plan inspection is current. Reading retained evidence grants no new
mutation authority. Integration gates and actual approved Values/placeholder
journeys remain required; this change is excluded from remote b74 qualification.
