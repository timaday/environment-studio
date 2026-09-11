# Canonical payload memory correction

Status: local correction; immutable candidate integration pending. Lead owns
implementation, checks and self-review; no new independent acceptance is claimed.
Base: `ae0388db498a7e36d67b3b1e50c24b67392dc838`.

## Behavior and scope

Canonical JSON must retain its exact UTF-8 bytes, escaping, unsigned UTF-8 key
order and byte-size refusal while fitting the existing payload resource envelope.
The encoder now measures the existing serialization and then fills one exactly
sized array. It avoids geometric buffer growth and a final full-size copy.
The returned array remains newly owned by the caller. No schema, SQL template,
XML edit, budget, publication or export-authority behavior changes.

Acceptance includes literal escaping and supplementary Unicode, exact byte limit
versus one byte short, malformed surrogate refusal, independent returned arrays,
existing package/SQL golden outputs and large owned payload generation. The latter
must preserve every original and target document byte and leave all four plans
intact after the original view closes. Full qualified export remains separate.

## Actual RED and focused results

The new external test uses the same reviewed scalable shape and optimized oracle
as the prior hosted resource probe. It explicitly supplies mock protected-document
policies, creates four owners and verifies each plan, prepares one unqualified
payload under its original pinned admission, checks every canonical output byte,
rechecks all four plans, and completes ledger cleanup. It does not repeat the
separate fifth-owner/logout-recovery campaign already verified by the prior probe.
The payload oracle walks expected UTF-8 hex bytes one document at a time; it does
not create another complete expected payload or parse a second full JSON tree.

Against the exact `8b85011` image, the small control passes in 2.092 seconds.
The large case fails with Java heap exhaustion before payload bytes are returned:
exit 3 after 107.097 seconds. Docker reports no kernel OOM kill; container removed.
Application cleanup is not claimed after VM termination. Source, hashes and results:
`/home/tim/.tmp/es-v3-payload-capacity-20260911/`.

The additional literal encoding tests pass on the prior implementation; these are
compatibility controls, not a claimed RED run. Their initial selection runs six
Java tests. The resource failure above is the observed RED. After the correction,
52 affected Java tests pass (3 core, 1 parser, 48 server), including the existing
package admission, assembly, payload and transaction template checks. Logs:
`es-payload-json-boundary-before-20260911.log` and
`es-payload-json-focused-20260911.log`.

Compiled UTF-16 byte charging and removed maximum checks both fail the actual
literal oracle; the fixed control passes. Manifest:
`es-payload-json-faults-20260911/result.json`. These are lead-run mutation controls.

The same resource case with only the canonical encoder classes replaced passes:
small 2.077 seconds; large 159.100 seconds. All 67,121,255 canonical payload bytes
match the independent expected header, document keys and complete XML bytes.
Candidate remains unqualified; all owner cleanup completes and containers are
removed. Original image and base classes remain unchanged. Exact source hash:
`950678913fd9311c989d21a28d2090573c0ccbacfba035b0eaf7c7c05ed77e26`.
Overlay evidence: `es-v3-payload-capacity-exact-buffer-20260911`.

## Qualification limits

Both runs use 1 GiB total, one CPU, 256 PIDs, the image's 65% heap, no swap/network
and the unchanged 300-second deadline. Observation/publication/policies are mock
ports; actual compiler publication remains incomplete and export stays unavailable.
The mock observation reuses immutable original fixture strings across owners.
This does not qualify worst-case distinct source storage, simultaneous database
buffers, HTTP encoders, full package admission/assembly or native client resources.
No real or transformed application material was used or persisted.

Next: full exact-candidate OCI gates, then the same payload probe against the new
packaged classes without overlays. Native access, UX approvals, operational export
and final release evidence remain separate unresolved prerequisites.
