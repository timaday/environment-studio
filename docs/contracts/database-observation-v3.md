# Versioned v3 database observation

Internal extension of [complete database observation](database-observation.md)
for [native v3](native-definition-v3.md). V2 selection, framing, observations and
hosted routes retain their existing meaning. This extension creates no new HTTP
route, destination setting, credential channel or publication eligibility.

The trusted core port adds `V3Selection(Checked, bindingId)` and explicitly named
`reserveV3`/`observeV3` methods. A port without v3 support refuses as destination
unqualified; it must not reinterpret v3 as v2. Direct observation consumes and
closes operation credentials on every outcome, including unsupported selection.
Unused reservation closure releases its shared capacity exactly once.

The actual JDBC adapter validates the selected v3 checked model with the current
compiler before opening a connection, comparing the complete checked result,
digests and mechanism vector. The internal unqualified entry may accept only the
explicit MECHANISM_UNQUALIFIED publication blocker; any other diagnostic or
changed checked result refuses. This permits internal adapter qualification,
not hosted publication/inspection/export. Current operational v3 availability
remains disabled until its complete integrated paths qualify.

Require an exact declared binding, matching destination engine and existing
operation-policy/storage/visibility/TLS/destination checks. The adapter shares
the same four physical operations/quarantines across v2 and v3, with the same
one-shot reservations, deadlines, bounded XML/inventory, cancellation, rollback
and confirmed closure. Ordinary write-capable accounts remain eligible under the
closed read-only operation policy. No new SQL, grant probing, connection pool,
write execution or fallback conversion is introduced by version selection.

The result retains the exact v3 logical and selected binding digests. Its closed
fingerprint object has the same field structure, strict native framing and source
ordering as v2, with metadata `adapterVersion: jdbc-observation-v3` and domain
`ES-OBSERVATION-3` followed by a zero byte. Engine operation-policy identifiers,
storage mechanism versions, expected/observed physical identity, source digests
and successful cleanup semantics remain unchanged. Both the versioned domain
and adapter metadata distinguish v3 observations; no old fingerprint is promoted
or rehashed into new evidence. Only executed successful checks may construct a
complete observation. Independent XML/derived projection and final-target
validation remain mandatory after reading; DB observation alone establishes none
of the derived graph or contributor facts.

Acceptance requires actual adapter paths for both engines using independently
invented metadata/source cases, current-checked-model and binding/digest refusal,
no connection on invalid selection, exact v2 compatibility oracles, v3 independent
fingerprint oracle, mixed v2/v3 shared capacity/quarantine, one-shot credentials,
cancellation, rollback/close uncertainty and no source/credential logging. Actual
disposable PostgreSQL18.6/text and Oracle23.26.3/CLOB with ordinary write-capable
accounts must qualify v3 before hosted admission; mock JDBC checks do not replace
engine/TLS evidence. Preserve the existing read-operation policy qualification
and record unsupported combinations without weakening its guards.
