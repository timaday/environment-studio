# Small hosted v3 resource prerequisite — 10 September 2026

An external four-owner control passes against exact application candidate
`875a257d659d3139a2c180cc587531ddcd095c96`. Independent fixed-source review found
no confirmed issue within its stated scope. This extends the
[direct semantic control](v3-combined-capacity-prerequisite.md); it does not
qualify publication, HTTP transfers or a deployment allocation.

## Acceptance and actual results

One actual HostedPlanService and SessionLedger retain four separately owned
plans. Each starts at revision1, processes an admitted mock observation to
revision2, and applies typed alpha-to-omega target decisions at revision3.
Both literal XML documents, physical graph, computed inputs/results, all origins
and retained provenance are rechecked after each later admission. Independently
predicted observation fingerprints distinguish owners even when XML is identical.
Actual Raw views preserve both current and target; original pins remain linked.

Actual validation reports64 complete zero-pair rules per owner and no export
authority. The real request reader, lazy page encoder and bounded output encoder
return all rules in seven-row pages, including the partial tail and an empty
beyond-tail page. Fingerprints, offsets, continuation, every rule identity and
its bounds/outcome are checked. A stale fingerprint refuses CONFLICT; a foreign
owner receives NOT_FOUND; a fifth retained plan refuses CAPACITY.

A real thread holds the first owner's pinned view. Peer view admission refuses
CAPACITY while peer metadata remains available. Logout reports INCONCLUSIVE and
retains owned work/capacity. After the held work observes revoked authority and
closes, ledger retry reports COMPLETE. A fresh session for the same owner creates,
observes and edits a replacement plan; all four retained plans/pages still pass.
All five started mock observations close their transient credentials; no unused
permit is closed. Final cleanup checks every admitted lease, retaining a primary
failure if cleanup also fails. PASS is printed only after successful cleanup.

## Attribution and investigation

Control5 javac and JVM exit0. It reuses only the reviewed fresh exact-875 classes
from the direct control:1015 classes and120 dependency jars, checked before/after.
The runner copies13 schema resources from exact Git blobs using the committed
server POM's resource includes. New probe classes, resources and logs are hashed.
Probe SHA256 is `30029b3500a18b10cdb60d817ff72e1f186bde537a0c513f226832aeb1874811`;
result manifest SHA256 is
`055c6c9d55f96d203d1f1a91874b997d23a6a1a76222ae043ef3681daf4927bf`.
Sources, runners, commands and all attempts remain external in
`es-v3-hosted-capacity-small-20260910`.

The first attempt lacked packaged schema resources. The second reached the
behavior assertions but incorrectly expected Permit.close after started work;
the port contract assigns that cleanup to observe itself. These are harness
corrections, not product defects or production TDD RED. Control4 moved PASS after
final cleanup; control5 added independent owner fingerprints. Fixed control4
review and the control5 delta addendum verify the attribution chain and these
oracles; reviewers ran no tests. Reports are
`es-v3-hosted-capacity-small-fixed-review-20260910.md` and
`es-v3-hosted-capacity-small-fixed5-review-20260910.md`.

Two separately compiled external harness faults exit1 at their intended checks:
wrong expected owner fingerprint gives OWNER_CURRENT_OBSERVATION; replaying the
first page for offset7 gives PAGE_OFFSET. Neither prints PASS. These demonstrate
oracle sensitivity, not production guard mutation coverage.

Workspace publication and observation/destination evidence are explicit mock
ports. The actual compiler remains Incomplete with MECHANISM_UNQUALIFIED.
No servlet, socket, transfer registry, actual database, OCI artifact, large
source/graph/value shape, concurrent incoming observations, heap measurement or
whole-process/native resource campaign ran. The1GiB JVM maximum is only a test
setting. Those qualification requirements remain open.
