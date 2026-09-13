# V3 publication HTTP and concurrent cleanup

Two exact POST routes now expose the existing owned definition/profile publication
commands through actual runtime compilers, authenticated commit admission and the
original bounded workspace transfer owner. New actual publication still refuses
DEFINITION_INCOMPLETE. A successful historical replay returns the exact immutable
revision; it confers no current qualification. See the
[publication contract](../contracts/workspace-publication-http-v3.md).

Definition publication requires the configured maintainer even for replay. Profile
publication retains its owner-only rule. Closed decoding preserves submitted
policies for complete coverage/duplicate checks, enforces UUID/revision/Unicode/
shape limits and wipes mutable bytes. Body authority failures retain their original
typed refusal. No caller can submit a checked model or readiness claim.

The actual four-upload/logout/relogin test exposed a cleanup defect in the existing
workspace registry: each same-lease completion could consume one of the session's
three cleanup attempts while siblings remained. All workspace slots eventually
closed, but the retired owner stayed quarantined and re-login returned403. A
deterministic four-operation control reproduced this. The registry now removes
only the completed operation and notifies session cleanup outside its lock after
the last active/inconclusive workspace obligation for that lease settles. Other
leases remain independent. Retry limits, terminal uncertainty and stale callback
guards remain unchanged. The transfer contract was amended before this correction.

## Fixed review and tests

Author twelve-file manifest SHA
`f91ac0022d7775f77496e707f406e1e2ea296ea5c760653ae6a48d9687728b0b`,
base `9ded177`; four lead-owned contract files accompany it. Independent report SHA
`b7b1d7c89a284e613376f719f28af4862a935e017b709982ed96d6613efc1c49`.
The independent reviewer read all sixteen and added two tests; no material source
blocker was confirmed. Actual Maven3.9.16/JDK21.0.12 and Node24.20.0 evidence:

- Initial authenticated HTTP RED: one assertion/zero errors, expected the actual
  compiler's422 refusal but the unlisted route returned403. It preceded production
  changes. GREEN then reaches DEFINITION_INCOMPLETE with unchanged stored history.
- Fifteen new cases: four parser, six controller and five actual local HTTP/OIDC
  tests. Exact20,000 policies/8 MiB wrapper, malformed/duplicate/trailing input,
  canaries, policy completeness, schema2 refusal, owner/kind/version isolation,
  CSRF/Host/Origin, historical replay after YAML edits, revoked output and recovery
  all pass. Shared slots are exercised with four partial publication bodies.
- Final author focused42 pass; full1,309 pass (293 core,7 parser,709 server,
  300 supervisor), zero failures/errors/skips, assembly and hostile-environment
  launcher pass,10 September2026 at00:55:17BST. Schema42 pass.
- Six compiled author guard mutants each fail assertions: original early cleanup
  notification, omitted publisher role, accepted trailing JSON, duplicate keys,
  collapsed policy list and ignored expected revision. Failure counts1/1/1/1/2/1,
  zero errors. Exact source restored and27 controls pass, including the actual
  ten-case V3WorkspaceTransferTest.
- Independent17 pass (4 core,1 parser,12 server). Reordered policy/JSON field
  order preserves exact decoded-command replay, while changed policy under the
  same request ID conflicts without altering history. A held final cleanup
  callback permits unrelated owned operation admission; stale callbacks cannot
  notify again or release another slot. Two compiled mutants move notification
  under the registry lock or replace submitted content policy with deny; each
  fails one assertion/zero errors. Restored17 and all candidate hashes pass,
  10 September00:56:27BST.

Setup/oracle corrections are retained in external author/reviewer records. The
first deterministic cleanup fixture omitted the request lease; a later positive
oracle expected a retained COMPLETE report, but successful cleanup removes the
report. The corrected regression and real re-login failure establish the defect.
A failed contract-copy assertion left one subsequent test run on the old registry;
that run is not reported as a passing correction. The old exact-eight OpenAPI
enumeration was updated to the ten contracted operations after its expected
failure. Nonexistent BodyTest/OutputTest selectors ran nothing and are excluded;
the actual transfer class ran in restored/full gates.

The independent test first needed a Java compound-var syntax correction. Its
simple class selector also ran three existing core cases with the same simple
name, so the actual total is17 rather than the initially expected14. The mutation
runner finished successful restoration before failing that metadata assertion;
the result index corrects totals from actual logs without inventing another run.
No compilation or oracle mistake counts as behavior RED or a mutation kill.

## Integration and limits

The combined publication/runtime twenty-four-file manifest is
`d29cdf82af1114afe5fd0fddbe0ab0d3a3c51f9f814922f4903358e6b8670b41`,
base `69752c8568e56d6cb506774cb6ab0ed4addc5fed`. Full Maven verification passes
1,343 tests (297 core,7 parser,729 server,310 supervisor), zero failures/errors/
skips, assembly and hostile-environment launcher,10 September2026 at01:02:27BST.
On that exact combined archive, Node24.20.0 npm clean install, frontend checking,
40 frontend tests,42 schema tests and production build pass. These are fresh local
checks; no remote CI or browser qualification is inferred.

Business/UX still requires the approved full operator flow. Engineering/security
retains current compilation, immutable history, owned transfer and final commit.
QA/RST covers stale replay, mixed owners, cancellation, partial bodies and recovery.
All models and publication histories are independently invented; positive200
tests replay labelled test-produced history through actual runtime code. They do
not demonstrate a newly qualified publication. Versioned plan HTTP, browser,
combined resource limits, native clients/readback and deployment remain open.
The retained047d1b0 image predates these routes and this cleanup correction.
