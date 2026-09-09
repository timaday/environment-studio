# Versioned shared plan publication ports

VersionedPlanWorkspace adapts the existing v2 Workspace and the separate
V3PlanWorkspace into the shared lifecycle's explicit versioned methods. The
caller supplies both qualified delegates; this adapter has no storage, compiler,
cache, publication or runtime registration authority.

Legacy definition/profile calls preserve v2 delegate objects and bytes. V3
definition lookup delegates the original owner/reference and transfers every
returned pin: exact reference, publication digest, checked v3 model and policies.
V3 profile lookup reconstructs the selected V3PlanWorkspace.Definition from all
of those retained pins and delegates fresh selected/original qualification to
V3PlanWorkspace. Return the exact checked physical profile and publication pins.
Never route v3 through a v2 Ready result or substitute the newest revision.

Refuse null/wrong-reference results and definition-version mismatches. A v3
selected definition cannot call the v2 profile method; a v2 definition cannot
call the v3 method. Propagate original typed workspace/publication refusals
without fallback, retry, draft promotion, writes or a readiness upgrade.

Acceptance uses owned invented SQLite history and the actual v3 bridge: preserve
all exact pins under a test-only current qualification witness, reject forged
selected publication metadata, foreign owner and unsupported actual compiler,
and verify no revision/replay changes. The current compiler still returns
MECHANISM_UNQUALIFIED; adding this adapter does not enable operational v3.
