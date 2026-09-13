# Shared versioned plan runtime

The actual hosted composition root now supplies the shared plan owner with both
the existing v2 publication adapter and the real v3 adapter. Explicit v3 lookup
uses current compilers and the same configured owned workspace. Schema3 storage
is acquired only for that versioned lookup, preserving schema2 startup and v2
creation/replay. No migration, connection, credential reservation, new HTTP route
or availability flag is introduced. See the [contract](../contracts/plan-runtime-v3.md).

Actual current v3 compilation remains unqualified. Missing/foreign history,
unpublished history and current compilation refusal remain distinct, and rejected
creation installs no plan. Test-produced historical-ready records cannot override
current qualification. The same runtime sees later appended exact history without
caching missing results. A retired original lease refuses before deferred storage
access. Existing destination allowlists and shared session/capacity ownership remain.

## Fixed review and checks

Author six-file manifest SHA
`8e6e6868d8d93ced8179eda5ae514830f0b291ae79c2c1de7f235a2d6202d74e`,
base `047d1b025e74d929dd874dd9b48ad92c954455af`. Independent report SHA
`72e21298f6072ae5bf413862505747447c8cef73a4302030235bcae42c56eb00`.
The separate reviewer read all six and added two actual runtime/storage controls;
no material blocker was confirmed. Maven3.9.16/JDK21.0.12 results:

- Initial meaningful RED: one assertion/zero errors. Actual runtime createV3 for
  a missing owned reference hit the default UNSUPPORTED_DEFINITION port instead
  of the real store's NOT_FOUND. This preceded the runtime wiring change.
- Initial GREEN9 pass. A new actual schema2 compatibility case then exposed the
  first candidate's eager schema3-store construction, which refused existing
  schema2 configuration. The contract was clarified before deferring access.
  Corrected22 pass, including v2 creation/replay on schema2 and after explicit
  offline schema3 upgrade with a new runtime instance.
- Final author focused24 pass. Four new runtime cases exercise missing/foreign/
  unpublished/history qualification, later drafts/restart, unchanged workspace
  counts, no failed plan, original lease and configured owner/destination rules.
  The same request ID remains usable for legitimate v2 creation after refused v3
  creation. Full1,324 pass (297 core,7 parser,710 server,310 supervisor), zero
  failures/errors/skips, assembly and hostile-environment distribution pass,
  10 September2026 at00:55:05BST.
- Three compiled author mutants remove version dispatch, eagerly acquire schema3
  storage or ignore current compiler diagnostics;3/1/1 assertions fail with zero
  errors. Exact six files restored and6 controls pass.
- Independent14 pass (1 core,1 parser,12 server), including two new cases for
  same-runtime missing-to-appended history and retired-lease-before-store refusal.
  Eager acquisition mutation compiles and fails one assertion/zero errors.
  Restored14 and all six hashes pass,10 September00:57:07BST.

The first author setup used a nonexistent workspace subdirectory; correcting it
to the actual private temporary directory is not a behavior RED. The eager-store
compatibility failure initially surfaced as an error; the subsequent explicit
nonthrowing-construction assertion catches that mutation without counting errors
as mutation evidence. An independent hypothesis expected an old schema2 runtime
to change schema identity after offline upgrade without restart. It was rejected:
the existing store correctly retains its admitted schema identity. The original
test/log remain preserved; no production change followed that mistaken oracle.

The combined publication/runtime twenty-four-file manifest is
`d29cdf82af1114afe5fd0fddbe0ab0d3a3c51f9f814922f4903358e6b8670b41`,
base `69752c8568e56d6cb506774cb6ab0ed4addc5fed`. Full Maven verification passes
1,343 tests (297 core,7 parser,729 server,310 supervisor), zero failures/errors/
skips, assembly and hostile-environment launcher,10 September2026 at01:02:27BST.
The exact combined archive also passes Node24.20.0 clean install, frontend checking,
40 frontend tests,42 schema tests and production build. No remote CI, browser or
updated image result is inferred from these local gates.

This is actual composition/storage evidence with independently invented fixtures,
not successful current v3 publication or inspection admission. Business/UX still
needs the full operator flow; engineering preserves versioned authority and
schema compatibility; QA covers refusal, restart, stale leases and later history.
Versioned plan HTTP/transfer, combined retained resources, native clients/readback
and deployment remain open. No new browser, managed database execution or image
qualification is claimed by these runtime tests.
