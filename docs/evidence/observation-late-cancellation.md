# Final JDBC cancellation check — 10 September 2026

Status: implemented and author-checked; independent review, integration and
combined gates pending. Base `9b1b7e369e7c55781d1cc5bb3d3e2bba2dc3892d` has the same observation source as
reviewed baseline `30446e206b5489c23f98e9c966056a7f678de8bd`.
[Issue9](https://github.com/timaday/environment-studio/issues/9) tracks the exact
candidate, fetch status and remote verification. This addresses ES-09/ES-11 and
accepted finding OBS-QA-001; the lead is its sole correction writer.

The remote reviewer reproduced cancellation inside rollback/close returning
`Complete` on both invented JDBC engine paths. The plan service separately checks
cancellation before installing observations; no hosted authority bypass was
established. The adapter now checks the original token when selecting the final
completed-work result. A previously selected terminal reason remains unchanged,
including deadline cancellation initiated by the adapter itself. Actual cleanup
COMPLETE/INCONCLUSIVE, quarantine ownership and all existing deadlines are retained.
The [observation contract](../contracts/database-observation.md) now makes this
publication check explicit. No connection statement, driver, fingerprint, public
result shape or export availability changes.

## Actual checks and independent oracles

Meaningful RED: the unchanged production adapter failed all six new cases across
PostgreSQL/Oracle and cancellation before rollback, before close and after the
real mock close. Both uncancelled controls passed. All cases executed complete
invented metadata/source reads; exactly one open/rollback/close and credential
clearing passed. Eight server tests ran with six assertion failures and no errors;
four core and one parser control passed. Exact four-file source/manifest is retained
externally in `es-observation-late-cancel-red-source-20260910`; log
`es-observation-late-cancel-red1-20260910.log`. The shell's trailing status-report
assignment then failed because zsh reserves that name; the Maven log and Surefire
reports independently record the actual six assertion failures. No setup failure
is counted as RED. The same13 focused tests then passed after the production fix.

Final focused Maven verify: **55 tests:4 core,1 parser,50 server;zero failures,
errors or skips**. This includes15 new JUnit cases and the existing observation,
read-operation and SQL boundary suites. The new tests include both versioned
observation paths, exact uncancelled source, cleanup cancellation, original deadline
precedence, held close with three other reserved slots, and exactly one recovered
slot after the original close completes. Repeated consumed-permit close and cleanup
handle cancel/retry cannot release a second slot. Existing v2/v3 independent
fingerprint controls and typed access/metadata refusals also pass.

An isolated child JVM exercises four deliberately failed rollback/close cases,
one of each per engine. They remain CANCELLED/INCONCLUSIVE with cleared credentials,
original cleanup handles and all four quarantines retained, even when the JDBC
double's abort returns. The parent JVM's capacity is unaffected. These are
independently invented JDBC facts and lifecycle interventions, not real database
session-disappearance proof.

Command: `mvn -B -ntp -f backend/pom.xml -pl server -am
-Dtest=HostedPlanServiceTest,FifthEditionClassifierTest,ObservationCancellationPublicationTest,*Observation*Test,ReadOperationPolicyTest,SqlReadTest verify`
(the test argument is shell-quoted). JDK21.0.12, Maven3.9.16; isolated candidate
worktree/output. Final log `es-observation-late-cancel-green3-20260910.log`, SHA256
`27133fbba48b6768984454f103477c3589863f689fcc5c8647be7539a9f4dd68`.
No native build, database or container resource was used; the remote reviewer owns
the already underway disposable PostgreSQL investigation.

Four separate manual production guard mutations compiled and failed specific
assertions: remove the final cancellation check; overwrite the selected deadline;
promote failed cleanup; release an inconclusive quarantine. Mutations used external
source/class directories with the unchanged candidate test classes, and the exact
substitutions, compiler/JVM commands, source hashes and assertion logs are retained
in `es-observation-late-cancel-mutations-20260910/results.json`. No mutated source
or class replaces the candidate. This is targeted guard investigation, not aggregate
mutation coverage.

Staged whole-diff provenance review, content guard, repository integrity, whitespace
and11 Python checks pass.

## Limits and next gate

The non-author source analysis informed precedence/cleanup controls; it is not a
fixed candidate review. Remote verification must identify the exact fetchable
correction and rerun its original reproduction. The prior published baseline's
56-test/38-rule/2,961-decision campaign does not test this correction. Full integrated
Java/frontend/OCI and applicable real-engine qualification remain required; none
is claimed here. The final token check defines its observation point and does not
retract results after transfer; hosted ownership/cancellation checks remain separate.

The source-only cancel-worker admission race raised during analysis is a separate,
unreproduced hypothesis; no fix or safety conclusion is claimed for it. Native
JNI-QA-001, complete closure/client/resource/operator and release qualification
remain open. New fixtures contain only original invented catalog plumbing and
synthetic lifecycle interventions; no private or transformed application material.
