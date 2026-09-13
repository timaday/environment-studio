# Production JNI ownership contract review

The [private ownership contract](../../backend/tools/guarded-supervisor/docs/privacy-jni-ownership-v1.md)
and corresponding [ABI clarification](../../backend/tools/guarded-supervisor/docs/privacy-abi-v1.md)
are ready for implementation after fixed independent review. This is contract
evidence only; no new JNI implementation, memory measurement, client admission
or production qualification was produced by this work unit.

The existing nine method signatures remain unchanged. Exact closed Java results
retain affirmative pre-window arm failure without inventing successful disarm.
Unknown/acquired ownership still requires the original launcher finally path.
Four live launches share a maximum of256 issued tokens per invocation; retirement drains
references, primitive/signal work and finally obligations before late calls use
immutable tombstones. Fixed native storage remains bounded until JVM termination.
Proposed allocation caps must be asserted and measured by implementation.

Each launch must settle its owned descriptors and endpoints before COMPLETE.
The pre-existing installation base remains externally owned, with one separately
identified invocation-owned borrowed descriptor. Complete launch cleanup cannot
attest whole-JVM termination; the external process owner must establish that
termination before claiming invocation resource release. No new parent namespace
or destructor/reset path was added. Production compiled tables stay empty, and
missing complete mapped-image/loader identity must refuse before CHALLENGE or
FINAL_ADMITTED.

Candidate2 is two documents over 7de634f. File-manifest SHA256
`e2a9ed14ac825604e93007077f9534e78f38655fc39fc9251cec03ae50241b42`;
patch SHA256 `c12bd0ef4eee85d5abfe36756b1a8226a185c4a25bccd6588dafe32f719ebb02`.
External files use prefix `es-jni-contract-candidate2-20260910`;
`es-review-jni-contract-candidate2-20260910.md` records independent review against
existing C/Java ownership and verifies all1014 unchanged base files. No blocking
contract conflict was found. Author repository-integrity checks passed; no
implementation tests were run for these documents. Measured author contract
rework was2m32s, excluding idle message delivery; no speed-up is inferred.

Next implement the real bridge/registry/coordinator through the existing launch
owner, including JNI construction failures, actual Java FORK, native contention,
finally propagation, exact descriptor recovery and bounded tombstone retention.
Test-only credential-free compiled records must remain separately linked and
excluded from production artifacts. Complete identity/loader/exec, bootstrap,
crash privacy, client transaction/readback and release qualification stay open.
