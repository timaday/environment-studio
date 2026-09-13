# Planned inspection/composition view fixtures

`shapes.json` covers the seven distinct request shapes and every successful
response family, including all four preview sections. The eleven POST operations
share these closed definitions in `schemas/plan-view-v1.schema.json` and the
existing `openapi-plans-v1.json`; the aggregate OpenAPI links those path items.

Run with Node 24 after `npm ci --prefix frontend`:

```sh
node --test scripts/schema.test.mjs
```

Schema rejection tests cover caller authority, masked/absent values, null target
state, false disclosure consent, Fresh capture mappings, preview section mismatch,
missing/reordered checks, noncanonical revisions, page bounds and repeated roots.
Strict raw JSON decoding, byte/deadline limits, full counts, actual portable source,
identity binding and lease/revision races require production adapter tests.
