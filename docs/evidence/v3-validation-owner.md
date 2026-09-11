# Validation presentation ownership

Base: `505f815552d4439220cc17e5c1ce120cff0df535`. Lead implementation and
self-review; the retired reviewer has not independently accepted this correction.

Acceptance: leaving and re-entering Validation must not reactivate an old
validate/page callback. Replacing the plan must expose neither the former summary
nor its concrete rule values, including the first render before effects run.
Fresh explicit validation and paging must still work.

Two regression cases failed against the original callback lifetime: an old
callback sent a third request where only two were authorized by the active view.
The correction binds callbacks and displayed state to the API, exact plan context
and enabled presentation. A third check inspects every replacement-plan render
after positively establishing a populated original page.

Actual local checks on 11 September 2026:

- RED: 2 failed, 29 passed in `es-validation-owner-red-20260911.log`.
- GREEN: all 446 frontend behavior tests and 61 schema tests passed in
  `es-validation-owner-all-20260911.log`.
- TypeScript/e2e type checking, Biome (98 files) and production build passed in
  `es-validation-owner-check-20260911.log` and `es-validation-owner-build-20260911.log`.

Logs are external under `/home/tim/.tmp/`. Self-review covered stale closures,
late responses, session expiry, first-render disclosure and positive paging.
The existing adverse tests retain fingerprint, UNKNOWN, bounded-page and explicit
smaller-retry behavior. There is no new visual design or backend authority change.
The base passed 1,724 host Java tests; that result applies to its unchanged Java
tree. An OCI gate for this correction and the complete Validation browser journey
remain required. This does not qualify production publication or export.
