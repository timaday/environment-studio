# PostgreSQL 16.11 observation

Lead correction from `083d35019ea2f609990cba107616355bc35d635e` adds exact
server-version admission and retains the actual version in the existing
fingerprint frame. The same read-only transaction, closed query vocabulary,
driver pin, destination/visibility/inventory checks and cleanup owner remain.
Other versions refuse; no hosted/native/export or TLS qualification is added.

Local TDD and adverse checks on 11 September 2026:

- `es-pg1611-observation-red1-20260911.log`: the new complete16 case and later
  visibility refusal failed because16 was still unsupported. A third failure was
  a test-oracle error: a null metadata scalar already means METADATA_UNAVAILABLE,
  not STORAGE_UNSUPPORTED. The expectation was corrected to preserve that rule.
- Actual old-code16.11 attempt returned STORAGE_UNSUPPORTED with complete cleanup,
  closed credential buffers, unchanged database state and no retained session:
  `es-pg1611-observation-db-red2-20260911.log`. The first launcher attempt had an
  empty classpath entry and never ran Java; it is not behavioral RED evidence.
- `es-pg1611-observation-green1-20260911.log`: focused Maven36 tests PASS
  (1 architecture,7 parser,28 observation), no failures/errors/skips. Covers actual
  version in identity, unchanged18.6 facts, malformed/unassigned versions, null
  metadata and retained mode/visibility/read-denial checks.

Actual disposable PostgreSQL16.11/pgJDBC42.7.13/Java21.0.12 checks use the existing
lead-owned network-none `es-pg1611-lead-20260911` fixture. The Java runner shares
that isolated network namespace and mounts only exact eligible compiled classes,
dependency jars and independently invented fixtures read-only. It uses the
existing image's JRE solely as a test runtime, not as an application candidate.
SCRAM authentication uses a new random test password per operation over stdin;
no password is written to the launcher, command line, environment or result log.
This is loopback plaintext testing, not production transport qualification.

`es-pg1611-observation-adverse2-20260911.log` records six expected outcomes:

- Complete exact two-document observation, correct16.11 fingerprint metadata,
  closed credentials and complete cleanup on an account with UPDATE privileges.
- Wrong destination, denied SELECT and pre-cancellation refuse with their
  respective codes and complete cleanup.
- A test-only write probe on the adapter's original connection receives SQLSTATE
  25006. The adapter subsequently refuses the aborted transaction and closes it.
- Wrong password refuses with CLEANUP_INCONCLUSIVE and closed credentials. The
  existing adapter cannot prove cleanup after a failed connection open. An earlier
  runner incorrectly expected complete cleanup; that failed assertion is preserved
  in `es-pg1611-observation-adverse1-20260911.log`. No production cleanup rule was
  relaxed. External observation of no retained server session does not promote the
  application result to complete.

Every final case compares complete exact row bytes before/after and independently
checks zero remaining sessions for the test account. The initial single-case
checks used a state digest; the final adverse runner uses complete bytes. These
are author checks; they do not constitute independent execution acceptance or
whole-process credential erasure proof. Maximum-size, cancellation-in-flight,
complete16.11 TLS/privacy/concurrency and integrated release gates remain open.

Saved external source: `es-pg1611-observe-20260911.py` and
`es-pg1611-observation-runtime-20260911/Observe16Main.java` under `/home/tim/.tmp/`.
Only explicit test code can inject the negative write probe; production source
continues to expose the closed read-only adapter.
