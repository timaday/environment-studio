# D06b4 live entity handles — reviewed prerequisite

The stable-token portion of [the plan context contract](../contracts/hosted-plan-context-v1.md)
now has a backend identity prerequisite. Existing handles are prepared from complete
observed provenance before installation. Fresh display handles are allocated when
creation intent is admitted, including incomplete drafts, and survive field edits
and failed/repeated materialization. Forgetting a creation retires its handle;
recreating that slot or reinspection receives a new identity. Dropping target XML
alone preserves live draft identity. Pages do not allocate identities.

Only live provenance remains: at most 20,000 Existing and 20,000 Fresh handles.
Collisions refuse before installing a partial map. Current projection requires a
complete unique Existing provenance bijection. Retirement releases both handle maps
once the existing owned-reader/cleanup conditions allow retained memory release.
No new HTTP route, display feature, export capability or release claim is enabled.
Binding values, exact locations and document placeholders remain separate active work.

## Actual RED, correction and verification

Author base: `78c48602b35bf91583c19231f4e9935059608a05`, isolated tree
`/home/tim/.tmp/es-plan-bindings-20260909`. Invented graph fixtures use the real
definition compiler with narrow mock observation/materialization ports. They prove
plan lifecycle behavior, not JDBC or qualified XML writing.

- `es-plan-handles-red2-20260909.log`: two actual assertion failures, zero errors;
  editing and failed materialization regenerated a live Fresh handle.
- `es-plan-handles-view-red-20260909.log`: two actual PROJECTION_REFUSED behavior
  errors and two passing controls; incomplete creations lacked an admitted display
  handle. Earlier compile/setup attempts are retained separately, not claimed RED.
- `es-plan-handles-full1-20260909.log`: initial candidate passed 527 Java tests
  (144 core, 7 parser, 328 server, 48 supervisor), assembly and launch checks.
- Independent fixed-candidate review found a repair-flow regression: an unconditional
  display-handle requirement also rejected unresolved references, containment and
  created-parent placement after Forget. The actual Forget command preserves that
  inbound intent while retiring the creation's display identity. Three independent
  assertions failed with zero errors in `es-handles-independent-review3-20260909.log`.
- The correction preserves explicit Fresh slot/type serialization for unresolved
  intent; tokens still require a live display handle. Existing opaque references
  still require admitted handles. All three independent regression tests and the
  actual Forget reachability control are now included.
- Independent re-review accepted the exact seven-file corrected candidate; 17
  focused tests passed in `es-handles-independent-green-20260909.log`. Root's
  focused correction run passed 22 tests, including five separate in-progress
  binding-value tests, in `es-plan-handles-review-fix-20260909.log`.
- Root copied only the reviewed seven files onto `744e981` plus the independently
  checked privacy primitive. Integrated `mvn -B -ntp -f backend/pom.xml -pl server
  -am verify` passed **483 tests** (145 core, 7 parser, 331 server), including actual
  hosted boundary/deadline tests. Log: `es-plan-handles-integration-20260909.log`.
  The unchanged supervisor's 55 tests had passed in the preceding independent
  privacy full-reactor run; they were not rerun for this plan-only correction.

All logs are external under `/home/tim/.tmp/`. Pinned Maven 3.9.16/JDK21 were used.
Repository integrity, full staged provenance review, limited content scan, Python11
and diff checks passed before integration commits. No private configuration,
credential, screenshot, published image or remote CI result is represented here.

## Fixed review evidence

Original five-file manifest: `/home/tim/.tmp/es-plan-handles-candidate-20260909.sha256`,
SHA-256 `627cd91338d3d6533cdd010eaa53add0f6cb8bec84fb8da842528612478afa76`.
Corrected seven-file manifest: `/home/tim/.tmp/es-plan-handles-candidate2-20260909.sha256`,
SHA-256 `abe87782808d515873192b0b4928533d7467124765500313ea2c51f50ef60e5e`.
The reviewer did not author the initial production change. Root owned correction
and integration. No elapsed-time speed-up claim is made.

The binding/location API, placeholder rendering, full browser workflow and maximum
heap/backpressure/cleanup qualification remain unfinished. This slice does not
substitute handle-count limits for measured memory evidence or resolve those gates.
