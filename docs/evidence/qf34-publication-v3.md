# Internal v3 publication commands

Reviewed on 9 September 2026. Definition/profile publication commands now retain
separate v3 history and digests through the existing owned SQLite store. They
require current compiler qualification before creating an unseen publication.
The actual v3 compiler remains incomplete, so production compilation cannot
publish. This is internal application integration; no publication HTTP route,
hosted plan or operational readiness was enabled.

The [contract](../contracts/native-workspace-v3.md) was updated before the
implementation. Definition maintainer checks precede replay and repeat before
append. Exact replay returns its original revision. Unseen commands require an
owned current draft, exact revision, supported version, complete nonduplicate
document export policies and fresh equal compilation of the exact source.

Profiles resolve their exact immutable historical published definition, freshly
check that definition and compile the exact physical-only profile source and
reference. A later definition draft cannot silently replace that reference.
Historical content, original source and separate publication framing remain
unchanged. The existing store enforces atomic append, quotas and authenticated
commit; this class adds no transaction retry or transport/session authority.

## Test and review evidence

Author archive `es-v3-publication-io1m7cae`, base `7c615ec`; final four-file
candidate manifest SHA-256
`db06114d92a8f06b12687b6b5a8486e631abbd4a6e6aab8715b6da757812e54f`.
The actual RED reached the UNAVAILABLE scaffold at SQLite-backed publication:
one assertion failure, no errors. Positive tests explicitly use a test compiler
witness after actual parsing. That witness tests application sequencing and
storage, and is never a production qualification result.

After implementation, tests exposed three incorrect expectations: source-order
policies instead of command-order policies, empty replay for a stale losing
command instead of CONFLICT, and404 instead of the existing409 wrong-kind mutation
classification. Those expectations were corrected; production/store behavior was
preserved. The initial replay mutation caused an unhandled test error. Its
positive assertion was strengthened and rerun; only the resulting explicit
assertion failure is counted as a guard kill.

Twelve author tests cover actual compiler refusal, exact history/restart/replay,
owner/maintainer changes, policy coverage, exact profile reference, actual commit
refusal/recovery, concurrent publication, capacity and changed/null compiler
evidence. Eight compiled author mutations fail assertions with restored controls
passing. These remove replay, initial/final maintainer checks, current
qualification, checked equality, profile/reference equality and policy guards.

Independent review in `es-publication-review-u85i0xeg` found no material blocker
within this internal scope. Three additional tests cover unpublished or
unsupported referenced history, replay without extra reads/compilation and exact
source/reference passed to current compilers. Two additional compiled mutations
remove reference publication or substitute profile source; both fail explicit
assertions. The review's focused30 tests and restored7 tests pass. A selected
nonexistent test name contributed no tests and is not counted as evidence.

## Current integration

Base `73d8326`, including reviewed executable association, plus all five frozen
publication files was verified in `es-publication-final-2v34n2ye`:

```text
mvn -B -ntp -f backend/pom.xml verify
```

Pinned Maven3.9.16/JDK21.0.12: **1,115 tests pass** —273 core,7 qualified parser,
600 server and235 supervisor; zero failures, errors or skips. Distribution
verification and hostile-environment launcher controls also pass. The run ended
at21:01:15 BST. All five file hashes match manifest SHA-256
`7e918c1687827990ff3eb3e462ae00de5e97f9b48b5871a968752daa325c3b84`.
Earlier1098-test evidence predates the executable and independent publication
tests; it is retained with its original scope.

External records under `/home/tim/.tmp`:

- `es-v3-publication-author-evidence-20260909.md`, SHA-256
  `8d14c708e543402a7472f4b0ad65b84ccf1416024fa614e98884973b3cc5ae7a`.
- `es-v3-publication-independent-review-20260909.md`, SHA-256
  `58f72d6207e17a1c0dedba89b5ca3dada82f8530a1eaeff99161be175980fa19`.
- `es-publication-final-full1-20260909.log` and the five-file manifest above.

Business review preserves physical-only profiles and current qualification.
Engineering review covered inward ports, immutable references and atomic append.
QA/security review covered stale/concurrent commands, owner/role changes and
failed commit recovery using independently invented fixtures. No measured
parallel-development speed-up is claimed. Publication HTTP with its original
session lease, v3 observation/plan/package integration and the complete operator
workflow remain separate work. Current OCI, remote CI/GHCR/HiveForge, joint
resources and release qualification are not established by this Java run.
