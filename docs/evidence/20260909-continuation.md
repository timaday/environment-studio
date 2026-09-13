# 9 September continuation — active work

Base: local and remote `8125e57127b1583664d368b97b82d08ec103fe5d`, verified with
`git rev-parse HEAD` and `git ls-remote origin refs/heads/implementation/d01-definition-compilation`.
D08a's 21-file and D07c2's complete 46-file WIP manifests matched every file;
no wholesale merge was performed. Both remain unreviewed beyond their recorded bases.

## Reviewed contract correction

The user explicitly replaced external account-purity requirements with qualified
read-only operations that accept ordinary owners and direct/column/role write grants.
The lead updated observation, hosted destinations, security and decision contracts
before implementation. Operation policy, adapter and fingerprint identifiers change;
old configuration/evidence is refused rather than silently reinterpreted. No account
or grant changes for eligibility, arbitrary SQL, runtime write probes or reconnection
are authorized by this policy. Workspace metadata persistence remains in scope.

An independent reviewer inspected a fixed six-file contract candidate. No blocking
safety defect was found. Two refinements were applied: snapshot-free SHOW verification
before PostgreSQL's lock, and at most one workspace commit *in flight* per lease.
Unsupported identity/read-access refusal names were clarified. This review is not
implementation or physical database qualification.

Workspace mutation contracts now define final lease/expiry admission immediately
before durable commit, outside a global I/O lock. Revocation during request reading,
compilation or store waits must roll back. Already admitted commits are ordered before
later revocation and retain capacity through confirmed connection cleanup; unknown
completion quarantines and does not invent a successful result.

## Actual reproduced findings

- Workspace writer: real SQLite v1/v2 saves, each with logout and expiry during a
  deliberately held request body, produced **4 intended failures, 0 errors**: all
  returned Saved after revocation. Pinned Maven 3.9.16 command:
  `mvn -B -ntp -f backend/pom.xml -pl server -am -Dtest=WorkspaceLiveAuthorityTest,SessionLedgerTest,MinimalRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`.
  Initial post-fix focused GREEN is author evidence; final independent review and
  integrated verification remain pending.
- D04 writer: new configuration acceptance and independently calculated
  ES-OBSERVATION-2 digest produced **1 assertion failure and 2 configuration errors**
  among 7 selected server tests under Maven 3.9.16. Implementation is in progress.
  Earlier host-Maven/enforcer/intermediate-module selection failures were tooling
  failures, not behavior RED.
- Independent XML reviewer: moving the invented glyph while changing its destination
  ancestor's xml:lang, xml:space or xml:base returned Complete despite changed final
  inherited context. All five parser-denied element namespaces compiled ReadyToPublish
  while the corresponding XML parsed Rejected. Scratch probes used existing independent
  mock fixtures; the exercised source files matched this base byte-for-byte. Contracts
  and regression implementation follow; no fix is claimed yet.

## Environment and remaining work

Old disposable DB containers and RAM TLS material are absent; both saved worktrees
and public native artifacts survive. Official Maven 3.9.16 SHA-512 and Node 24.20.0
SHA-256 downloads were verified. The previously pinned PostgreSQL, Oracle and Maven
image digests are available again. No new DB qualification/provisioning has run.

The named enterprise-ux-design skill is absent from installed/catalogued skill paths;
its location has been requested. Approved Midnight tokens and SVG assets are present.
Backend work continues; new visual designs still need the requested image approval.

D08a uncertainty/reconciliation and published-definition selection, complete profiles/
editing/export/readback UI, remaining UX data contracts, native privacy/terminal/TLS/
commit evidence, full heap/backpressure/recovery and actual deployment qualification
remain work. Existing inspection/export flags stay false; native registry stays empty.
HiveMind recorded the user correction as local-development context; historical evidence
was not promoted. No new CI, publication, deployment or release-completion claim.
