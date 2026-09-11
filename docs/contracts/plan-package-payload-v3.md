# V3 plan-to-package payload prerequisite

The internal server adapter prepares only the existing closed payload-v1 bytes
from a retained V3 plan view. It has no database connection, client launch,
workspace write, HTTP/download route or export capability. The result is explicitly
an unqualified candidate. It does not accept caller-supplied target XML, locators
or document values separate from that view.

Before copying any values, require a V3 definition, complete current and target,
and successful existing full original/target/provenance verification through
`PlanContentAdapter.verifyV3`. This reprojects original XML and rematerializes the
declared target decisions; a retained graph or claimed digest alone is insufficient.
Require one protected-self-contained policy for every selected-binding document.
Missing, denied, duplicate or extraneous selected-binding policies refuse. Other
bindings cannot supply a missing policy. Denied whole documents remain denied
even when selected field values are PUBLIC or the document is unchanged.

Use the selected definition's exact engine/storage/table/key/XML-column metadata
and declared record keys. Include every document, including unchanged and unmapped
siblings, sorted by the existing unsigned UTF-8 document-ID order. Encode complete
original and target strict UTF-8 bytes as lowercase hex; preserve original XML
characters and do not render placeholders or formatted XML. Use existing canonical
JSON and payload limits. Retain no payload in a plan, session, database or queue.
Cancellation is checked through existing proof work, each document and final
encoding. Return no candidate after observed cancellation. Candidate byte access
returns a defensive copy; string representations contain no source material.

The future application export owner must reserve bounded generation resources,
freshly verify original lease/revision/observation/publication/review/all checks
and qualified client before and after this work, and bind the exact resulting
payload into the execution manifest. This adapter cannot establish those facts
from a ViewSnapshot. Payload generation is not permission to download or execute.
Actual compiler publication and hosted export remain closed.

The owned preparation overload accepts an already executing, revision-pinned
`ViewAdmission` and the exact validation input fingerprint the caller reviewed.
It recomputes V3 validation through that original admission, including fresh
publication lookup, rejects a fingerprint mismatch, then prepares bytes through
the admission's cancellable read and final authority check. It never reserves a
replacement session or silently chooses a newer revision. An expired lease,
cancelled or changed admission, or changed publication returns a safe refusal.
The caller retains responsibility for closing the admission.

This overload still returns only an unqualified payload candidate. Matching a
validation fingerprint does not imply that its required checks passed; in
particular the client capability can remain UNKNOWN. No candidate may be served
or executed on this basis. Final export admission and combined memory accounting
remain required, and a returned candidate carries no continuing live authority.

Acceptance uses actual reviewed mock projection and materialization: an explicit
environment value changes only its qualified span; payload original/target bytes
and declared keys/table agree with independent expectations. Verify unchanged
sibling inclusion, canonical deterministic output, no-op targets, missing target,
wrong version/pins/proofs, source/draft corruption, policy denials and cancellation.
Run affected combined checks and fixed non-author review before integration.
