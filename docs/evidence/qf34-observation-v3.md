# Internal v3 database observation

Reviewed internal JDBC extension, 9 September 2026. V3 observations now retain
their exact logical/binding digests and a separate fingerprint domain. They use
the existing read-only operation, inventory, XML, destination, TLS and cleanup
checks. Ordinary write-capable accounts remain supported. No hosted v3 route,
publication readiness or export authority was enabled.

The [contract](../contracts/database-observation-v3.md) preceded implementation.
Explicit `V3Selection`, `reserveV3` and `observeV3` methods avoid treating v3 as a
v2 publication. Unsupported ports refuse and close credentials. JDBC freshly
compiles the complete checked definition, compares all checked metadata and
allows only the internal MECHANISM_UNQUALIFIED blocker. Other diagnostics or
changed evidence refuse before connecting.

Both versions share four reservation/physical-operation/quarantine slots and the
existing worker. The operation deadline starts before selection validation;
expired validation cannot start a worker. Compilation remains synchronous and
has no new preemptive interruption. V3 uses `ES-OBSERVATION-3` and
`jdbc-observation-v3`; v2 bytes retain their existing meaning. Independent
whole-observation fingerprint oracles pass for both versions and engines.

## Tests and independent review

Author archive `es-v3-observation-l9nep378`, base `1ea5381`. Four-file manifest
SHA-256 `9ce31269d340d3f2b460f16dbe1f4fbe03d9b9ff6960fcaa4b0261453fa6f2db`.
Actual RED reached the unsupported JDBC v3 boundary after successful current
fixture compilation: one assertion failure, no errors. The default unsupported
port control passed. Implementation and expanded controls then pass32 focused
tests, including seven new v3 cases and existing read-policy/lifecycle checks.

Tests cover both mock JDBC engines, complete inventory, exact versioned digests,
bad checked evidence without a connection, shared mixed-version reservations,
one-shot permits, delayed original cleanup/quarantine, cancellation, missing
controls and read denial. Five compiled author guard mutations each produce one
assertion failure with no errors: checked equality, digest domain, adapter
metadata version, shared capacity and unsupported-port credential closure. The
restored nine-test selection passes.

Independent review in `es-observation-review-nuetb_mo` found no material blocker.
Four added tests cover a freshly compiled definition with FIELD_MAPPING_MISSING,
expired v2/v3 startup, closed-unused permit reuse and worker allocation in a
separate warmed JVM. The final focused36 tests pass. Three independent guard
mutations have explicit assertion failures with restored controls passing.

Initially, removing only the pre-worker expiry guard survived because caller
cancellation won the connection race. A stronger isolated JVM test measures the
total number of started Java threads. The same mutation then reports
EXPIRED_WORKER_ALLOCATED. The earlier survivor remains recorded; it was not
retroactively relabelled. An intermediate generic failure preceded the final
specific allocation assertion and is also retained.

## Actual database and projection checks

The owned disposable PostgreSQL18.6/text and Oracle23.26.3/CLOB lab was checked
for its exact ownership labels, running state and disabled Docker logging.
Existing mock certificates remain valid through11 September2026. Fresh ordinary
write-capable owners and independently invented two-document fixtures were used.
Credentials stayed in memory; only public trust material was passed by path.

The external helper executes these checks against the current candidate classes:

| Case | Observed result on both engines |
| --- | --- |
| Owner READ WRITE control followed by production read-only transaction setup | Independent writes commit; attempted DML under read-only setup refuses |
| V3 observation over verified TLS | Complete exact XML and digests; independent committed-state witness unchanged; credentials closed and no remaining owner session |
| Present empty derived identity | Database read completes; derived projection refuses INVALID_DERIVED_IDENTITY |
| Explicit nonempty mock value written by the test owner, then fresh observation/projection | Three physical entities, two computed nodes/memberships; exact Unicode values and contributor identities/origins/roles/attribute spans match independent expectations |
| Connected cancellation before source transfer | CANCELLED with complete cleanup and unchanged committed state |
| Wrong CA and wrong hostname | Refusal with inconclusive cleanup retained; no promotion to successful cleanup |
| Owned diagnostics, base logs and the actual final helper log | Current credential/content canaries absent |

The original external test incorrectly expected an empty text identity to form
a group. Production correctly refused it. Attempts1/2 are retained as incorrect
test expectations. The corrected helper preserves that adverse case, then uses
the test owner's separate connection to commit the independently invented
nonempty fixture. The observation adapter performs no writes.

Review strengthened the first helper's count/value checks with exact contributor
identity, document/projection/element, source digest, role, attribute name/quote,
decoded value and raw XML span assertions. It also added a canary scan of the
actual final log, which the reused lab's base-directory scan did not cover.
Wrong-host resolution is explicitly loopback; public leaf/CA validity, trusted
signature, wrong signature and PKCS12 contents are checked. The PostgreSQL log
also records hostname-verifier refusal. The public JDBC result intentionally
does not expose causal exceptions: specific TLS causes are inferred from these
controlled inputs, not from CLEANUP_INCONCLUSIVE alone. No arbitrary DNS/TLS
deployment or certificate rotation qualification is claimed.

## Exact integrated candidate

Base `f99aaad` plus the five reviewed files passes:

```text
mvn -B -ntp -f backend/pom.xml verify
```

**1,126 tests pass**:273 core,7 qualified parser,611 server and235 supervisor,
with zero failures/errors/skips. Distribution and hostile-environment launcher
checks pass. The run ends at21:08:24 BST. All files match manifest SHA-256
`3a45de90ed5f38ec25a87c9e5943e757e662adce7516277b3882895fe5b25cc1`.
The database helper was recompiled and rerun against that archive's exact current
class directories; compile and execution both exit0. Root integration adds only
status/evidence prose, including removing “Planned” from the contract introduction.

External local evidence under `/home/tim/.tmp` includes:

- `es-v3-observation-author-evidence-20260909.md` and the candidate manifests.
- `es-v3-observation-independent-review-20260909.md`, SHA-256
  `af9ada54fd7572eb0b56c99a403a5cf725873bf04618a7775d23abc8914de0ea`.
- `es-observation-current-full1-20260909.log` and
  `es-observation-mutants-results-20260909.json`.
- `V3ActualObservationProbe-20260909.java`, SHA-256
  `e7dd9d5ceecee1f4365af8e5dbcaf735ddfeb2320787c8df22a8312b221bb401`.
- `es-v3-actual-current-runner-20260909.py` and its result JSON; final database
  log SHA-256 `c7be6cae59fb5c8c56f881fc8a48105cd2f7d805624b98dadd9858b27c76e584`.
- `es-v3-actual-observation-static-addendum-20260909.md`, SHA-256
  `24472ad566e199321251e7eb223d85a0eab41f4a699653345f9c0ef796004119`;
  independent source review of the strengthened helper, not an engine rerun.

Business review preserves fresh recomputation and operation-scoped credentials.
Engineering/security review covers shared ownership, stale evidence, deadline
and cleanup. QA review combines mock adverse cases with actual small database
fixtures. The actual Oracle witness is bounded to these small CLOBs; maximum
sizes, additional account variants, combined heap/native resources, full v3 plans,
operator workflow and current OCI/remote CI/GHCR/HiveForge remain separate work.
No private application input or configuration model was used. No measured
parallel-development speed-up or release qualification is claimed.
