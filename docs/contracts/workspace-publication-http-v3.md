# Hosted v3 publication commands

Extend the existing [owned workspace transfer](workspace-http-v3.md) with exactly
POST `/api/v3/definitions/{objectId}/publish` and
POST `/api/v3/profiles/{objectId}/publish`. All existing v1/v2 meanings, v3 draft/
history response shapes, original-session ownership, private schema3 storage,
four shared operation slots, body/output clocks and completion cleanup remain.
These routes add access to the existing V3PublicationWorkspace application
commands, not new compiler qualification or plan/export authority.

The actual compiler remains incomplete. New production publication therefore
refuses under the existing application contract. A stored historical publication
or successful replay is inspectable history and cannot confer current readiness.
Do not add a production flag, test-only compiler toggle or caller-supplied checked
model to bypass current qualification.

## Requests and authority

Require hosted mode, configured audited schema3 storage, original authenticated
lease, approved Host/Origin and the existing CSRF header. Admit only the two named
POST routes; demo and other methods/paths stay denied. Schema2 is unavailable503
without migration. Definition publication additionally requires the existing
deployment-owned maintainer predicate; profile publication requires its owner,
preserving the existing distinction. The application checks current definition
maintainer authority even before successful replay and again before append.

Content-Type is exactly one application/json media type, allowing parameters as
in the existing v3 body contract. Read through its original bounded nonblocking
body owner. Definition JSON has exactly expectedRevision, requestId, exportPolicies;
profile JSON has exactly expectedRevision and requestId. Object/request IDs are
UUIDs and expectedRevision uses existing canonical ExpectedRevision syntax and
1024-digit bound. No source, format, definition reference, model, readiness or
computed field is accepted here.

exportPolicies is an array of at most20,000 closed entries containing bindingId,
documentId and content. IDs follow the existing declared-ID contract; content is
exactly deny or protected-self-contained. Preserve every submitted entry for the
application's complete coverage/duplicate checks; never deduplicate or infer
policies. The existing NativeCommand ordering preserves exact command identity.

The typed definition-publication browser facade prepares a detached immutable
objectId/command pair and validates that closed pair again before one POST.
It preserves all submitted policies and their order, including duplicates, for
the backend's authoritative coverage checks. It never inserts default policies
or derives permission from historical-ready data. Successful replies must decode
as the same object's published DefinitionRevision, with sourceRevision equal to
the submitted expectedRevision and policies exactly equal to the submitted list
after stable bindingId/documentId ordering, including content and multiplicity.
The complete existing history decoder still enforces published policy coverage.
Explicit replay after response uncertainty retains the original destination,
request ID and body even after newer history; no automatic retry or persistence.
Existing HostedApi session, CSRF and late-response revocation checks apply.

Reject duplicate/unknown keys at all levels, wrong shapes/types, malformed or
non-scalar Unicode, trailing JSON and overflow. Use strict UTF-8 and the shared
8 MiB wrapper cap; wipe mutable decoding bytes on every outcome. Body I/O,
original lease loss or deadline must retain their existing safe failure behavior.
Parsing cannot swallow a WorkspaceRefusal and turn an unavailable/expired
operation into a successful or retryable publication.

After parse and body close, invoke the corresponding V3PublicationWorkspace
command using a store with the existing authenticated commit admission and actual
current definition/profile compiler ports. Original lease checks surround
application execution and response transfer. Keep storage/compiler/I/O work outside
the session authority monitor. No new service cache, automatic migration,
background publication retry, unbounded queue or extra transfer owner is added.

## Results and replay

Return200 only for the immutable revision actually returned by the application,
using the existing definition/profile v3 history encoding and guarded no-store
transfer. Publication details retain their exact sourceRevision/digest/policies;
profile publication has no document policy list. An exact stored successful
replay returns its historical revision even after a later edit or loss of current
compiler qualification. Definition maintainer authority and original live lease
still apply. A changed command sharing requestId conflicts, and failure cannot
create a revision or replay entry.

Existing application checks remain authoritative: exact owned kind/revision,
supported compiler/schema, current recompilation equality, full definition policy
coverage, exact eligible profile definition reference, immutable bounded append
and final authenticated commit. Preserve DEFINITION_INCOMPLETE,
DEFINITION_NOT_PUBLISHED, CURRENT_COMPILATION_MISMATCH and policy diagnostics as
safe422 Rejected results. A new actual valid-source attempt currently refuses
DEFINITION_INCOMPLETE; historical-ready data cannot bypass this.

Keep400 malformed input,401 unauthenticated,403 forbidden,404 missing/foreign,
409 conflict,413 byte limit,422 semantic refusal,429 capacity and503 unavailable
distinct. Revocation and committed-partial-response behavior remain as specified
in workspace-http-v3.md. No raw exception, source, path or credential enters errors.

## Acceptance and remaining work

Use only independently invented fixtures. Demonstrate an actual HTTP request
reaches the application refusal rather than an unlisted-route denial. Test both
routes through real local HTTP/session/security and schema3 SQLite, definition
maintainer versus profile-owner behavior, original lease revocation, strict body
decoding, policy completeness, foreign/kind isolation, schema2 no-upgrade and
unchanged store/replay after refusal. Existing operation/transfer adverse controls
remain required. Exact historical replay can supply200 coverage using explicitly
labelled test-produced history and the actual production runtime/compiler.

Positive new publication application/store tests already use explicit compiler
witnesses; those do not qualify a new actual publication. Do not weaken the
current compiler to manufacture HTTP success. Freeze and independently review
the candidate, run relevant guard mutants and integrated gates. Current v3
compiler integration qualification, full plan HTTP/operator workflow, response
resources and release/deployment evidence remain separate work.
