# Shared internal v3 plan lifecycle

Reviewed internal slice on 9 September 2026, based on `38ab89f`. One
HostedPlanService now owns explicit v2/v3 creation, observation, target work and
physical commands/entity pages. This does not wire v3 into runtime composition
or enable publication, HTTP plans, profiles, export or browser availability.
See the [contract](../contracts/shared-plan-lifecycle-v3.md).

Explicit createV3 uses current versioned publication lookup through a trusted
port and shares one-plan-per-lease/four-total capacity with v2. Replay identity
distinguishes versions. Reservation uses the original v3 observation permit and
cancellation; complete observed pins and destination context precede installation.
Target materialization uses independent original/decision pins and retains full
typed/final proofs. Opaque commands use physical declarations; incomplete field
decisions and computed entity edits refuse. Existing handles survive identity
edits; Fresh replacement never acquires a removed entity's origin.

Before integration the lead found two gaps missed by the first fixed review:
inherited v2 installation advertised observed v3 content as a complete target,
and incomplete derivation references broke safe plan-summary diagnostics. Both
reproduced as two assertions with zero errors. V3 inspection now retains current
only, accounts for one retained source and requires actual materialization before
target completeness. Incomplete results retain declaration references while the
summary uses TARGET_INCOMPLETE. V2 behavior remains unchanged. The reviewer
corrected the initial target assertion and re-reviewed the exact amendment.

Actual TDD includes separate creation, reservation, materialization, XML dispatch,
opaque-command and physical-page RED runs with assertion failures and zero errors.
Full corrected verification passes **1,203 Java tests**:289 core,7 qualified parser,
643 server and264 supervisor, zero failures/errors/skips, finishing22:55:38 BST.
Assembly and hostile-environment launcher controls pass. Focused corrected review
passes25 tests. Eight author guard mutants and the independent handle-retirement
mutant compile and fail meaningful assertions; restored controls pass.

Actual XML examples include exact encoded scalar changes, derived recomputation,
unresolved draft retention, stable handles after identity changes, and explicit
remove/Fresh replacement with independent whole-XML output. Missing/foreign
observed and target proofs refuse. Concurrent retirement signals original work
and holds shared capacity until it leaves. Independent reinspection repeats the
same XML/fingerprint: old decisions reset and old handles refuse even with the
new correct revision; new handles still edit successfully.

Test corrections are preserved, not counted as product RED: ViewAdmission must
pin inside run; generated fragments reset the default namespace and sort attributes.
Earlier value-edit tests were already green despite historical filenames saying
red. Core metadata/observation ports are explicit witnesses; server XML adapters
are real. No synthetic publication witness qualifies the actual compiler.

External `/home/tim/.tmp` records:

- Fixed9 `es-shared-v3-candidate2-20260909.sha256`, SHA256
  `493ce5be09fb23f56cd937404f86f79f429d2f858a20c25dc52716a0bfcf3bc6`,
  archive `es-shared-v3-correction-ko7zkc9b`.
- `es-shared-v3-author-evidence-20260909.md`, actual commands, RED/GREEN and
  correction history; `es-shared-v3-candidate-full2-20260909.log`.
- `es-shared-v3-independent-review-20260909.md` and its preserved limitation;
  `es-shared-v3-independent-corrected-review-20260909.md`, SHA256
  `4cbef73af7b0751c7af4503bb85be0e8803615c4683e816b292f9a45634743e5`.
- `es-shared-v3-mutations-result-20260909.json`,
  `es-shared-v3-correction-mutations-result-20260909.json` and independent logs.

Comparison and profile capture are separate candidates. Actual shared lifecycle
on owned databases, versioned APIs, full proof-retention resource measurements,
browser/OCI and release qualification remain open. The retained image is still
the exact76ab78b [content artifact](plan-content-artifacts.md). No speed-up or
release readiness is claimed. All fixtures are independently invented.
