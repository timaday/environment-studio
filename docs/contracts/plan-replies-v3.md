# Closed small v3 plan replies

This internal adapter prepares future v3 HTTP replies; it enables no route or
compiler qualification. V3PlanReply is closed to acknowledgement, operation status
and small summary records. It holds typed immutable backend results and exposes
only their declared wire fields plus an original-lease verification method. Its
diagnostic string is redacted. It carries no request, credential, connection,
executable callback, full graph or reusable authorization token.

Acknowledgements retain planId, decimal-string revision and optional operationId.
Status retains operationId, planId, lowercase phase, safe backend code, cleanup
complete/in-progress/inconclusive and optional decimal-string installedRevision.
Absent optional operation/revision fields are omitted, never fabricated or null.
Revisions above JavaScript's safe integer range remain exact strings.

Summary retains the existing physical metadata fields in their existing declaration
order and appends currentComputedCounts and targetComputedCounts, each required
null or the closed nodes/memberships/cooccurrences count object. Null means missing
complete evidence; present zero is a complete empty result. Physical totals remain
separate. Preserve observedDestination's null/value/evidenceValid semantics and
force exportAvailable=false. No adapter evidence map or contributor values appear.
Wire maps are unmodifiable, with deterministic field order; existing v1 bytes stay
unchanged. Use the reviewed bounded encoder and original transfer deadline later.

Immediately before encoding and throughout output, verify the original lease and
immutable V3 resource ownership. Acknowledgements use pure plan ownership and, when
present, original operation ownership; retained replay metadata remains valid after
replacement. Status uses pure operation ownership, never status()/cleanup polling
or credential consumption. Summary uses complete captured V3View equality under
the original captured plan ID. Wrong-version or forged nominal reply types cannot
authorize exposure. Missing/foreign/wrong-version remain NOT_FOUND; revoked original
lease SESSION_REQUIRED; changed same-plan summary CONFLICT. Verification is not
performed while holding an output/container lock and never substitutes current IDs.

Acceptance: literal acknowledgement/status bytes including large revisions and
optional omission; separate physical/computed metadata, null versus complete zero;
actual shared-owner version checks without reservation expiry or cleanup effects;
same-revision active-operation change invalidates summary; redacted diagnostics.
Typed invented DTO cases establish wire behavior only. Actual shared-owner tests
use explicit invented publication witnesses and do not qualify the current compiler,
HTTP route, database operation, export or release.
