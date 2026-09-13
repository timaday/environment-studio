# TEST-QA-003 — upload test settlement

Base: f012956de159a194b0badf0cb91eb1a33498bc64. Lead owns the test and
evidence correction; native IDE2 ownership is unchanged. No production/API,
design, persistence or build configuration changes.

The independent first full G02 run failed one of325 tests: Source was still
empty after file selection. Issue9 comments5633001255/5633014722 preserve that
failure and a controlled held-FileReader reproduction. Finding accepted:
`findByRole` can find the existing Source textbox before its read completes.
No application upload-loss defect was established.

Acceptance: wait for the exact uploaded source, then require enabled Save and
no PUT. Under a held native read, require empty Source, disabled Save and no PUT;
release the original reader/blob and require the same successful assertions.
The original native read performs decoding; no fabricated result, timer, sleep
or production edit is used. Existing read-error and retired-reader cases remain.

Correction: replace element-presence waiting with bounded `waitFor` on the exact
source value. Parameterize the original workflow over ordinary and held reads.
The independently invented two-character JSON fixture remains unchanged.

Author checks on Node24.20.0, Linux, isolated worktree:

- Focused HostedWorkspace:4 PASS. The initial command also named a nonexistent
  `useV3Definitions.test.tsx`; it ran only HostedWorkspace and is not evidence
  for the hook suite. The full run below includes the existing suites.
- `npm run check --prefix frontend`: PASS, TypeScript and69 formatted files.
- `npm test --prefix frontend`:326 component tests and59 schema tests PASS.
- `npm run build --prefix frontend`: PASS.

RED provenance is the independent first gate failure and controlled reproduction.
An author pre-correction held/released control also passed on this host; it did
not reproduce the unscheduled race and is not labelled RED despite its log name.
RST focus: asynchronous completion versus existing-element presence, exact source,
disabled Save before completion, and no implicit persistence at either boundary.

G00 results and fixed non-author review are recorded in the candidate handoff.
Combined required gates still apply to the exact corrected candidate. Unchanged
production assets do not establish OCI or release acceptance. Keep the first
G02 failure and separate unresolved d67 browser visibility evidence.
