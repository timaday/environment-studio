# Internal fresh readback comparison v1

This implements the comparison prerequisite of ES-15 and
[hosted fresh verification](hosted-plans-v1.md#validation-review-export-and-fresh-readback).
It is a framework-free, non-authorizing calculation, with no HTTP route, database
call, artifact download or change to the editing baseline. Constructing its input
or obtaining Matches grants no publication, execution or export capability.

The future operation owner must select the live exported artifact's immutable
complete target inventory, logical/binding digests and independently configured
destination witness, then obtain a new observation through the registered read-only
adapter under the original lease. This comparator cannot establish freshness,
origin, XML validity, complete database visibility or operation ownership itself.
It does not accept user-uploaded manifests as authority. Those integration gates
remain required before exposing readback; native qualification is unchanged.

## Closed calculation

Expected holds logical/binding SHA-256 strings, model version 2 or 3, an engine
(postgresql or oracle), destination ID/host/port/database/transport-policy identity/
provisioning-policy version/physical identity, and complete target Document values
using the existing ObservationResult document and typed-key representation.
Document IDs use the native lowercase ID grammar (1–64 ASCII characters).
Destination text fields are nonempty scalar/control-free strings, at most 256
code points (host 253); ports are 1–65535, physical identity follows the existing
closed engine-specific observation shape. Expected inputs are detached and immutable; their toString omits content/identity.
Take a bounded detached document snapshot before validating its entries; validate
exactly the inventory retained by Expected. Concurrent caller changes must never
replace an already-validated entry in the retained snapshot. A concurrent structural
change detected while copying refuses the expectation, without exposing collection
diagnostics. Malformed expected inputs throw only INVALID_READBACK_EXPECTATION.

One comparison takes Expected, an ObservationResult and the original cancellation predicate.
Return only Matches, Differs or Unknown: no raw values, keys, difference excerpts
or execution/health verdict. Any null/unavailable/refused observation, cancellation,
inconclusive cleanup, malformed source, duplicate document ID/typed key, invalid
length/digest, mismatched logical/binding digest, destination or required metadata
returns Unknown. A refused observation remains Unknown even if cleanup later
settles; this pure calculation never invokes cleanup retry or any external action.
Check cancellation before work, between bounded document chunks and before returning
any definite result. Exceptions from the cancellation owner are not converted to a
positive result.

A complete usable observation must retain exact expected engine, endpoint and both
physical identity witnesses. Required closed destination/metadata fields follow
[database observation](database-observation.md); adapterVersion must be
jdbc-observation-v2/v3 for the selected model, operationPolicyVersion must be the
engine's read-operation-v1, visibility complete, readOnlyOperation verified,
cleanup complete and the correct read-only snapshot token. Observation fingerprint
must be a lowercase SHA-256 string, but is not used as proof of fresh execution or
compared to the artifact's original fingerprint. The registered adapter remains
responsible for its complete canonical observation frame and fingerprint.

Independently validate each inventory: 1–128 documents, unique declared IDs and
uniform exact key type, canonical signed int64 keys or scalar text keys of 1–256
code points. Documents contain nonempty scalar XML strings of at most 1,048,576
UTF-16 units each, at most 16 MiB strict UTF-8 per inventory. Count strict UTF-8,
check characters against UTF-16 length and recompute each SHA-256 incrementally;
do not normalize, parse/reserialize or replace characters. Bound work before
large allocations. Retain no encoded full-document copies.

After both inventories and required evidence are valid, Matches requires identical
sets of document IDs, exact typed keys and exact XML strings. Input order has no
meaning. Different complete valid membership, keys or characters returns Differs,
including same-count substitutions, unchanged-dependency changes, whitespace,
CDATA/entity spelling or newline differences. Invalid/ambiguous inventory is
Unknown, never evidence of a known difference. Adapter-level INVENTORY_MISMATCH
is a refused observation and remains Unknown.

Acceptance uses independently invented two-document literals, reordered equality,
missing/extra/same-count replacement, typed-key differences, lexical and unmapped
changes, malformed/duplicate/oversized inventory, wrong evidence and cancellation.
Matches establishes only equality of the supplied checked data; it does not prove
COMMIT, identify the writer, imply application health or authorize automatic retry.
