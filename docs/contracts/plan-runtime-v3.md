# Shared versioned plan runtime composition

The hosted composition root supplies one HostedPlanService with
VersionedPlanWorkspace, the existing PlanWorkspaceBridge and the actual
V3PlanWorkspaceBridge. The v3 bridge reads the same configured owned workspace
through V3NativeSqliteStore and its real current definition/profile compilers.
Acquire the schema3 store only when an explicit v3 lookup runs; composing the
shared service must not eagerly require schema3 and break a schema2 workspace.
This deferred store acquisition is version dispatch, not fallback or migration.
No test compiler, readiness property, historical-ready promotion, fallback store
or lookup cache is installed. The v2 delegate and its digest behavior remain
unchanged. VersionedPlanWorkspace itself still owns no runtime authority.

Keep existing composition requirements: hosted mode, explicitly opened workspace
and configured destinations. Destination allowlists, verified transport/read-only
operation settings, shared plan capacity, cleanup and session authority remain
unchanged. Construction creates no managed database connection, observation permit,
credential reservation or plan. It does not initialize, upgrade or mutate a
workspace. Explicit schema2/schema3 administration remains required; versioned
store reads preserve their existing wrong-version refusal behavior.

Internal createV3 can now reach the actual immutable v3 lookup through the shared
runtime. Missing/foreign objects return the owned store's NOT_FOUND; unpublished
owned history refuses PUBLICATION_REQUIRED. Actual current compiler diagnostics
still refuse UNSUPPORTED_DEFINITION even for test-produced historical-ready
publication. No v3 creation can succeed while required mechanisms remain
unqualified. Failed lookup leaves workspace revisions/replay and shared plan
state unchanged. Original ownership and final installation checks remain in the
shared service. No new HTTP path, browser availability or export is enabled by
this composition prerequisite. Future v3 HTTP routes must enforce version before
reading credentials or reserving content transfer.

Acceptance uses actual owned invented SQLite history and the actual runtime
constructor. Distinguish missing, foreign, unpublished and historical-ready/current
unqualified states; repeat after later drafts and restart without substitution.
Preserve v2 creation and exact replay on explicit schema2 and upgraded schema3
workspaces. Verify failed v3 work does not consume the shared live-plan slot or
reserve an observation. Missing configuration/demo behavior stays unchanged.
