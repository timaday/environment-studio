# PostgreSQL16.11 candidate SQL

Lead implementation from d862c564e42ef0ef00d5b6518615bce8f75b06d6.
The user identified16.11 as today's required destination. A separate exact
`postgresql16-text-v1` tuple now generates the compatible constraint-catalog
guard and exact160011 server check.18.6 generation remains byte-identical for
the preserved identical input. No JDBC, hosted export, supervisor admission,
publication or production-readiness flag changes are included.

Author checks,11 September2026:

- `es-pg1611-template-red1-20260911.log`: intended16.11 admission failed, one
  assertion failure among2 cases; no compile error. The unchanged18 template
  also refused on the actual16.11 engine with independent original-state readback.
- `es-pg1611-template-green1-20260911.log`: focused Maven42 tests PASS
  (1 architecture,7 parser,34 package/template); zero failures/errors/skips.
- `es-pg1611-schema-green2-20260911.log`: all61 schema cases PASS, including
  exact16.11 tuple and wrong server/client/template refusals. G00/integrity and
 11 Python checks PASS. No full G01/G02/OCI campaign is claimed.

Actual disposable PostgreSQL and psql16.11 (Debian16.11-1.pgdg12+1), amd64,
image `postgres@sha256:a2420e9555e2224583fe84d0bb3f0b967e69354ae3a0be55a9c14e251388c4eb`.
Lead owns network-none container `es-pg1611-lead-20260911`,512MiB/1CPU/128PIDs,
256MiB tmpfs database, no host ports or real data. Generated SQL runs under a
new restricted NOLOGIN role through explicit test SET ROLE; administration and
fixture restoration are separate. This is plaintext disposable qualification,
not TLS/password/supervisor/privacy qualification.

Eight actual client cases PASS: complete candidate rollback; changed unchanged
sibling; extra/deleted row; wrong physical destination; disallowed trigger;
late SQL error rolling back the prior candidate writes; complete candidate commit
with independent exact target readback. The eight attempts took0.123–0.146s each
including independent readback. Test setup restored the original fixture after
the commit; that is not a generated recovery artifact.

Six guard-removal controls reach readiness only after removing their corresponding
guard: original bytes, complete membership, destination, constraint eligibility,
trigger eligibility and exact version. Intact controls refuse. Every mutant is
external SQL and rolled back; final independent readback confirms fixture recovery.
An initial mutation runner used the wrong trigger guard label and stopped before
that mutant. Correcting the label produced the recorded complete6-control result;
no production guard or test expectation was relaxed.

A separate actual three-row case passes signed int64 minimum/maximum/negative,
supplementary and combining Unicode, empty attribute, unchanged sibling, CDATA,
comments, CRLF and client-metacommand text. Complete expected target bytes are
checked inside the transaction, then original bytes independently after rollback.
These are independently invented test documents, not transformed application data.

External evidence: `/home/tim/.tmp/es-pg1611-qualification-20260911/` contains
baseline-red, candidate-results, mutant-results and unicode-results JSON, exact
inputs/SQL/digests. Saved runners are the similarly named `es-pg1611-qualification`,
`run-candidate`, `mutants` and `unicode` files under the same external parent.
Baseline candidate SQL SHA256:
`3895b3b6208e7c96008795de3f79b622b83073c443f76d3aac54972c3a1467bb`.
Unicode candidate SQL SHA256:
`5fa5a4d27c44b9e1851d581614500e1babf998b1e399e56acb5d1732535f3fe9`.

Unqualified:16.11 JDBC observation, maximum-size and complete lock/concurrency
matrix, installed supervisor TLS/auth/commit-loss/privacy, hosted plan-to-package
authority, post-commit recovery artifacts and exact combined release candidate.
These actual test results qualify only their stated template cases.

Independent source review of the eight-file candidate found no confirmed
production-source defect and identified two evidence corrections. Original
repeatable-read runs are preserved under `repeatable-read-evidence/`; they did
not exercise the required READ COMMITTED package bootstrap. The three runners
were repeated under READ COMMITTED, with exact expected program-marker assertions
added to each mutant. All8 client cases,6 mutants and the three-row boundary case
PASS again; current baseline attempts took0.137–0.143s. SQL digests are unchanged.
`es-pg1611-read-committed-green1-20260911.log` records those corrected actual runs.
The existing target/source byte and post-rollback witnesses remain intact.
