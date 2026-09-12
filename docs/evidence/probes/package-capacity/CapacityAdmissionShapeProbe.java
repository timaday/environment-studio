import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import studio.environment.core.Outcome;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.derived.*;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.observation.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.planning.TargetIntent.*;
import studio.environment.core.session.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.plan.*;
import studio.environment.server.projection.*;
import tools.jackson.databind.json.JsonMapper;

/** External invented application-port control. Workspace and DB evidence are explicit mocks. */
public final class CapacityAdmissionShapeProbe {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final CapacityShapeProbe SHAPE = new CapacityShapeProbe(Boolean.getBoolean("es.probe.large"));
    static final Map<String,String> CURRENT = SHAPE.sources(false);
    static final Map<String,String> TARGET = SHAPE.sources(true);
    static final NativeCommand.Reference REFERENCE = new NativeCommand.Reference("00000000-0000-4000-8000-000000000929", "2");
    final NativeCompilationResult.Checked checked = SHAPE.definition();
    final Set<Owner> allowedOwners = new HashSet<>();
    final List<SessionLedger.Lease> leases = new ArrayList<>();
    final AtomicReference<HostedPlanService> holder = new AtomicReference<>();
    final SessionLedger ledger = new SessionLedger(Clock.systemUTC(), lease -> holder.get().invalidate(lease));
    final AtomicInteger observations = new AtomicInteger(), completed = new AtomicInteger(), closes = new AtomicInteger();
    final HostedPlanService service;
    record Plan(SessionLedger.Lease lease, String id, String revision, String observationFingerprint) { }

    CapacityAdmissionShapeProbe() throws Exception {
        var publication = new PublishedDefinition(REFERENCE, digest("external-mock-publication-only"), new PlanDefinition.V3(checked), checked.definition().bindings().getFirst().documents().stream().map(d -> new NativeCommand.Policy("mock-pg", d.id(), "protected-self-contained")).toList());
        var workspace = new Workspace() {
            public PublishedDefinition definition(Owner owner, NativeCommand.Reference reference) { throw new AssertionError("LEGACY_DEFINITION"); }
            public PublishedProfile profile(Owner owner, NativeCommand.Reference reference, PublishedDefinition definition) { throw new AssertionError("UNEXPECTED_PROFILE"); }
            public PublishedDefinition definitionV3(Owner owner, NativeCommand.Reference reference) {
                check(allowedOwners.contains(owner), "MOCK_UNKNOWN_OWNER"); equal(REFERENCE, reference, "MOCK_DEFINITION_REFERENCE"); return publication;
            }
        };
        var observation = new ObservationPort() {
            public ObservationResult observe(Selection selected, TransientCredentials credentials, Cancellation cancelled) { throw new AssertionError("LEGACY_OBSERVATION"); }
            public Reservation reserveV3(V3Selection selected) {
                equal(checked, selected.compiled(), "OBSERVATION_DEFINITION"); equal("mock-pg", selected.bindingId(), "OBSERVATION_BINDING");
                return new Reservation.Admitted(new Permit() {
                    boolean used, closed;
                    public ObservationResult observe(TransientCredentials credentials, Cancellation cancelled) {
                        check(!used && !closed && !cancelled.cancelled(), "ORIGINAL_OBSERVATION_LIFETIME"); used = true;
                        try {
                            var documents = new ArrayList<ObservationResult.Document>();
                            for (int i = 0; i < SHAPE.documents; i++) {
                                String id = CapacityShapeProbe.document(i), xml = CURRENT.get(id);
                                documents.add(new ObservationResult.Document(id, new ObservationResult.Key("int64", Integer.toString(i + 1)), xml,
                                        xml.getBytes(StandardCharsets.UTF_8).length, xml.length(), digest(xml)));
                            }
                            observations.incrementAndGet();
                            return new ObservationResult.Complete(new ObservationResult.Observation(digest("external-mock-observation-" + observations.get()),
                                    checked.logicalDigest(), checked.bindingDigests().get("mock-pg"), documents, evidence()));
                        } finally { credentials.close(); closed = true; completed.incrementAndGet(); }
                    }
                    public void close() { check(!used && !closed, "MOCK_UNUSED_PERMIT_ONLY"); closed = true; closes.incrementAndGet(); }
                });
            }
        };
        service = new HostedPlanService(ledger::guard, workspace, Map.of("destination", new Destination("destination", Engine.POSTGRESQL, observation)),
                new PlanContentAdapter(), System::nanoTime);
        holder.set(service);
    }
    static void check(boolean condition, String code) { CapacityShapeProbe.check(condition, code); }
    static void equal(Object expected, Object actual, String code) { CapacityShapeProbe.equal(expected, actual, code); }
    static String digest(String text) { try { return CapacityShapeProbe.digest(text); } catch (Exception failure) { throw new AssertionError(failure); } }
    static String requestId() { return UUID.randomUUID().toString(); }
    static void refuses(PlanRefusal.Code expected, Runnable operation) {
        try { operation.run(); throw new AssertionError("EXPECTED_REFUSAL_" + expected); }
        catch (PlanRefusal failure) { equal(expected, failure.code(), "WRONG_REFUSAL"); }
    }
    static Map<String,Object> evidence() {
        var identity = Map.of("systemIdentifier", "731", "databaseOid", "19", "databaseName", "invented_db");
        return Map.of("engine", "postgresql", "cleanup", "complete", "destination", Map.of("id", "destination", "host", "invented.invalid", "port", 5432,
                "database", "invented_db", "transportIdentity", "c".repeat(64), "provisioningPolicyVersion", "mock-v1",
                "observedPhysicalIdentity", identity, "expectedPhysicalIdentity", identity),
                "metadata", Map.of("adapterVersion", "jdbc-observation-v3", "operationPolicyVersion", "postgresql-read-operation-v1", "visibility", "complete",
                        "readOnlyOperation", "verified", "snapshot", "repeatable-read-read-only"));
    }
    SessionLedger.Lease admit(Owner owner) {
        allowedOwners.add(owner); var result = ledger.admit(requestId(), owner);
        check(result instanceof SessionLedger.Accepted, "SESSION_ADMISSION"); var lease = ((SessionLedger.Accepted) result).lease(); leases.add(lease); return lease;
    }
    static void stage(String name) { System.out.println("STAGE="+name+" heapUsed="+java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()); }
    Plan create(SessionLedger.Lease lease) {
        stage("CREATE_OWNER_"+leases.size());
        var created = service.createV3(lease, requestId(), REFERENCE, "mock-pg", "destination"); equal("1", created.revision(), "CREATE_REVISION");
        var reservation = service.reserve(lease, created.planId(), new HostedPlanService.Mutation("1", requestId()));
        String expectedObservation = digest("external-mock-observation-" + (observations.get() + 1));
        stage("OBSERVE_BEGIN");
        try (var submission = service.claimCredentials(lease, reservation.operationId().orElseThrow(), PlanDefinition.Version.V3)) {
            var status = submission.process((username, password) -> { username[0] = 'u'; password[0] = 'p'; return new CredentialLengths(1, 1); });
            equal(HostedPlanService.Phase.SUCCEEDED, status.phase(), "INSPECTION_STATUS");
            equal(HostedPlanService.Cleanup.COMPLETE, status.cleanup(), "INSPECTION_CLEANUP"); equal(Optional.of("2"), status.installedRevision(), "INSPECTION_REVISION");
        }
        stage("OBSERVE_COMPLETE");
        var entities = new ArrayList<EntityDecision>();
        for (int i = 0; i < SHAPE.physical; i++) entities.add(new EntityDecision.Retain(new Ref.Existing(new ObservedGraph.Key("item", CapacityShapeProbe.identity(i))),
                Map.of("id", new FieldValue.KeepObserved(), "tone", new FieldValue.Entered(SHAPE.value(i % SHAPE.groups, true)), "optional", new FieldValue.KeepObserved()), Map.of()));
        stage("MATERIALIZE_BEGIN");
        var changed = service.replaceDraft(lease, created.planId(), new HostedPlanService.Mutation("2", requestId()), new Draft(new TargetIntent(entities, List.of()), List.of()));
        stage("MATERIALIZE_COMPLETE");
        equal("3", changed.revision(), "TARGET_REVISION"); var plan = new Plan(lease, changed.planId(), changed.revision(), expectedObservation); verify(plan); return plan;
    }
    void content(Content content, Map<String,String> expected) {
        var evidence = (PlanContentEvidence.V3) content.evidence();
        equal(checked.logicalDigest(), evidence.input().pin().logicalDigest(), "PIN_LOGICAL_DIGEST");
        equal("mock-pg", evidence.input().pin().bindingId(), "PIN_BINDING_ID");
        equal(checked.bindingDigests().get("mock-pg"), evidence.input().pin().bindingDigest(), "PIN_BINDING_DIGEST");
        var actual = new TreeMap<String,String>();
        for (var source : content.sources()) { check(actual.put(source.documentId(), source.xml()) == null, "DUPLICATE_SOURCE"); equal(digest(source.xml()), source.digest(), "CONTENT_DIGEST"); }
        equal(expected, actual, "COMPLETE_RETAINED_XML");
        stage(expected == CURRENT ? "ORACLE_CURRENT_REPROJECT" : "ORACLE_TARGET_REPROJECT");
        var projected = new DerivedGraphProjectionAdapter().project(checked, evidence.input().pin(), CapacityShapeProbe.snapshot(evidence.input().pin(), actual), () -> false);
        check(projected instanceof DerivedGraphProjectionAdapter.Complete, "RETAINED_REPROJECTION"); var complete = (DerivedGraphProjectionAdapter.Complete) projected;
        equal(content.graph(), complete.physical(), "RETAINED_PHYSICAL"); equal(evidence.input(), complete.input(), "RETAINED_INPUT"); equal(evidence.derived(), complete.derived(), "RETAINED_DERIVED");
        try { SHAPE.verify(complete, expected, expected == TARGET); } catch (Exception failure) { throw new AssertionError(failure); }
        equal(SHAPE.physical, content.provenance().size(), "CONTENT_PROVENANCE_COUNT");
        for (int i = 0; i < SHAPE.physical; i++) { var key = new ObservedGraph.Key("item", CapacityShapeProbe.identity(i)); equal(new Ref.Existing(key), content.provenance().get(key), "CONTENT_PROVENANCE"); }
    }
    void verify(Plan plan) {
        try (var view = service.reserveView(plan.lease(), plan.id(), PlanDefinition.Version.V3)) {
            view.run(() -> {
                view.pin(plan.revision()); var snapshot = view.snapshot();
                var current = snapshot.current().orElseThrow(); var target = snapshot.target().orElseThrow();
                equal(plan.observationFingerprint(), ((PlanContentEvidence.V3Observed) current.evidence()).observationFingerprint(), "OWNER_CURRENT_OBSERVATION");
                equal(plan.observationFingerprint(), ((PlanContentEvidence.V3Observed) current.evidence()).input().pin().revisionToken(), "OWNER_CURRENT_PIN");
                equal(plan.observationFingerprint(), ((PlanContentEvidence.V3Target) target.evidence()).observationFingerprint(), "OWNER_TARGET_OBSERVATION");
                content(current, CURRENT); content(target, TARGET);
                equal(((PlanContentEvidence.V3Observed) current.evidence()).input().pin(), ((PlanContentEvidence.V3Target) target.evidence()).originalPin(), "ORIGINAL_TARGET_PIN");
                stage("VALIDATION_BEGIN");
                var validation = view.validationV3(); check(validation.targetComplete() && !validation.exportAvailable(), "VALIDATION_AUTHORITY");
                equal(SHAPE.groups * 32, validation.computedRules().orElseThrow().size(), "VALIDATION_RULES");
                check(validation.computedRules().orElseThrow().stream().allMatch(r -> r.outcome() == Outcome.PASS), "VALIDATION_RULE_OUTCOMES");
                stage("VERIFY_COMPLETE");
                return true;
            });
        }
    }
    void heldLogout(Plan first, Plan peer, SessionLedger.Lease fifth) throws Exception {
        var reached = new CountDownLatch(1); var release = new CountDownLatch(1); var failure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> {
            try (var view = service.reserveView(first.lease(), first.id(), PlanDefinition.Version.V3)) {
                view.run(() -> { view.pin("3"); view.snapshot(); reached.countDown();
                    try { check(release.await(5, TimeUnit.SECONDS), "HELD_RELEASE_TIMEOUT"); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                    refuses(PlanRefusal.Code.SESSION_REQUIRED, view::verify); return true; });
            } catch (Throwable caught) { failure.set(caught); }
        }, "external-mock-owned-view");
        worker.start();
        try {
            check(reached.await(5, TimeUnit.SECONDS), "HELD_PIN_TIMEOUT");
            refuses(PlanRefusal.Code.CAPACITY, () -> { try (var ignored = service.reserveView(peer.lease(), peer.id(), PlanDefinition.Version.V3)) { } });
            equal("3", service.summary(peer.lease(), peer.id()).revision(), "PEER_METADATA");
            equal(SessionLedger.CleanupState.INCONCLUSIVE, ledger.close(first.lease().id()).orElseThrow().state(), "HELD_LOGOUT_STATE");
            check(service.awaitingCleanupWork(first.lease()), "ORIGINAL_WORK_RETAINED");
            refuses(PlanRefusal.Code.CAPACITY, () -> service.createV3(fifth, requestId(), REFERENCE, "mock-pg", "destination"));
        } finally { release.countDown(); worker.join(5000); check(!worker.isAlive(), "HELD_WORKER_NOT_FINISHED"); }
        if (failure.get() != null) throw new AssertionError("HELD_WORKER_FAILURE", failure.get());
        equal(SessionLedger.CleanupState.COMPLETE, ledger.resumeCleanup(first.lease().id()).orElseThrow().state(), "DEFERRED_CLEANUP_COMPLETE");
        check(!service.awaitingCleanupWork(first.lease()), "DEFERRED_WORK_DRAINED"); check(ledger.guard(first.lease(), () -> true).isEmpty(), "OLD_AUTHORITY_RESTORED");
    }

    void payload(Plan plan) {
        try (var view = service.reserveView(plan.lease(), plan.id(), PlanDefinition.Version.V3)) {
            view.run(() -> {
                view.pin(plan.revision());
                stage("PAYLOAD_FRESH_VALIDATION_BEGIN");
                var validation = view.validationV3(); check(!validation.exportAvailable(), "NO_EXPORT_AUTHORITY");
                stage("PAYLOAD_PREPARE_BEGIN");
                byte[] actual = prepareBytes(view,validation.inputFingerprint());
                stage("PAYLOAD_EXACT_ORACLE_BEGIN");
                int at = chunk(actual,0,"{\"bindingId\":\"mock-pg\",\"engine\":\"postgresql\",\"records\":[");
                for (int i=0; i<SHAPE.documents; i++) {
                    if(i>0) at=chunk(actual,at,",");
                    String id=CapacityShapeProbe.document(i);
                    at=chunk(actual,at,"{\"documentId\":\""+id+"\",\"key\":{\"type\":\"int64\",\"value\":\""+(i+1)+"\"},\"originalHex\":\"");
                    at=hex(actual,at,CURRENT.get(id));
                    at=chunk(actual,at,"\",\"targetHex\":\"");
                    at=hex(actual,at,TARGET.get(id));
                    at=chunk(actual,at,"\"}");
                }
                at=chunk(actual,at,"],\"schemaVersion\":\"1\",\"storage\":\"text\",\"table\":{\"keyColumn\":\"mock_key\",\"keyType\":\"int64\",\"name\":\"mock_table\",\"schema\":\"mock_schema\",\"xmlColumn\":\"mock_xml\"}}");
                equal(actual.length,at,"COMPLETE_CANONICAL_PAYLOAD");
                admit(actual, plan, validation.inputFingerprint(), view.snapshot().definition().publicationDigest());
                view.verify(); stage("PAYLOAD_VERIFIED");
                System.out.println("PAYLOAD_BYTES="+actual.length+" qualified=false exactCanonical=true");
                return true;
            });
        }
    }
    static int chunk(byte[] actual,int at,String expected) {
        for(byte value:expected.getBytes(StandardCharsets.UTF_8)) {check(at<actual.length && actual[at]==value,"CANONICAL_BYTE_MISMATCH");at++;}
        return at;
    }
    static int hex(byte[] actual,int at,String expected) {
        String alphabet="0123456789abcdef";
        for(byte value:expected.getBytes(StandardCharsets.UTF_8)) {
            check(at+1<actual.length && actual[at]==alphabet.charAt((value&255)>>>4) && actual[at+1]==alphabet.charAt(value&15),"COMPLETE_XML_HEX_MISMATCH");at+=2;
        }
        return at;
    }


    byte[] prepareBytes(HostedPlanService.ViewAdmission view,String fingerprint) {
        var result = new studio.environment.server.export.V3PlanPackagePayload().prepare(view,fingerprint);
        check(result instanceof studio.environment.server.export.V3PlanPackagePayload.Result.Candidate,"PAYLOAD_CANDIDATE_REQUIRED");
        var candidate=(studio.environment.server.export.V3PlanPackagePayload.Result.Candidate)result;
        check(!candidate.qualified(),"CANDIDATE_NOT_QUALIFIED");stage("PAYLOAD_COPY_BEGIN");return candidate.bytes();
    }
    void admit(byte[] payload,Plan plan,String fingerprint,String publicationDigest) {
        try {
            var execution=(tools.jackson.databind.node.ObjectNode)JSON.readTree(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("/probe/execution-template.json"))).get("execution").deepCopy();
            execution.put("bindingId","mock-pg").put("logicalDigest",checked.logicalDigest()).put("bindingDigest",checked.bindingDigests().get("mock-pg"));
            execution.put("planId",plan.id()).put("planRevision",plan.revision()).put("planInputFingerprint",fingerprint).put("observationFingerprint",plan.observationFingerprint()).put("definitionPublicationDigest",publicationDigest);
            execution.put("serverVersion","16.11").put("templateVersion","postgresql16-text-v1");
            ((tools.jackson.databind.node.ObjectNode)execution.get("client")).put("version","16.11");
            var mechanisms=execution.putObject("mechanisms");
            for(String key:List.of("native-compiler-v3","derived-graph-v1","xml-path-v1","xml-span-v1","generic-graph-v1","structural-target-v1","plan-validation-v3"))mechanisms.put(key,"1");
            var policies=execution.putArray("exportPolicies");for(int i=0;i<SHAPE.documents;i++)policies.addObject().put("documentId",CapacityShapeProbe.document(i)).put("content","protected-self-contained");
            stage("PACKAGE_ADMISSION_BEGIN");
            // Explicit test-only compiler witness; no publication, native launch or export authority.
            var decoded=new studio.environment.server.export.PackageAdmission().readPinnedV3(new NativeCompilationResult.ReadyToPublish(checked),"mock-pg",JSON.writeValueAsBytes(execution),payload);
            check(decoded instanceof studio.environment.server.export.PackageAdmission.Result.Accepted,"PINNED_PACKAGE_ADMISSION_REQUIRED");
            var accepted=(studio.environment.server.export.PackageAdmission.Result.Accepted)decoded;
            equal(SHAPE.documents,accepted.counts().records(),"ADMITTED_RECORDS");equal(SHAPE.documents,accepted.counts().changedRecords(),"ADMITTED_CHANGED_RECORDS");
            equal((long)SHAPE.documents*SHAPE.documentBytes,accepted.counts().originalBytes(),"ADMITTED_ORIGINAL_BYTES");equal((long)SHAPE.documents*SHAPE.documentBytes,accepted.counts().targetBytes(),"ADMITTED_TARGET_BYTES");
            for(var record:accepted.payload().records()) {
                equal(java.util.HexFormat.of().formatHex(CURRENT.get(record.documentId()).getBytes(StandardCharsets.UTF_8)),record.originalHex(),"ADMITTED_COMPLETE_ORIGINAL");
                equal(java.util.HexFormat.of().formatHex(TARGET.get(record.documentId()).getBytes(StandardCharsets.UTF_8)),record.targetHex(),"ADMITTED_COMPLETE_TARGET");
            }
            check(!new studio.environment.server.export.GuardedPackageInspector().generationAvailable(),"NO_GENERATION_AUTHORITY");stage("PACKAGE_ADMISSION_VERIFIED");
        } catch(java.io.IOException failure) {throw new AssertionError("MOCK_TEMPLATE_READ_FAILURE",failure);}
    }

    void run() throws Exception {
        var plans = new ArrayList<Plan>();
        Throwable primaryFailure = null;
        try {
            for (int i = 0; i < 4; i++) plans.add(create(admit(new Owner("https://mock.invalid", "capacity-" + i))));
            payload(plans.getFirst());
            for (var plan : plans) verify(plan);
            equal(4, observations.get(), "EXACT_OBSERVATIONS"); equal(4, completed.get(), "EXACT_STARTED_CLEANUP"); equal(0, closes.get(), "NO_UNUSED_PERMITS");

        } catch (Throwable failure) { primaryFailure = failure; throw failure; } finally {
            var failures = new ArrayList<Throwable>();
            for (var lease : leases) try {
                var report = ledger.close(lease.id()); check(report.isEmpty() || report.orElseThrow().state() == SessionLedger.CleanupState.COMPLETE, "FINAL_CLEANUP_STATE");
                check(!service.awaitingCleanupWork(lease) && ledger.guard(lease, () -> true).isEmpty(), "FINAL_OWNERSHIP_REMAINS");
            } catch (Throwable failure) { failures.add(failure); }
            if (!failures.isEmpty()) { var failure = new AssertionError("FINAL_CLEANUP_FAILED"); failures.forEach(failure::addSuppressed); if (primaryFailure != null) primaryFailure.addSuppressed(failure); else throw failure; }
        }
        System.out.println("ADMISSION_SHAPE_PASS retainedOwners=4 recoveredSameOwner=NOT_RUN documentsPerOwner="+SHAPE.documents+" physicalPerOwner="+SHAPE.physical+" rulesPerOwner="+(SHAPE.groups*32)+" currentTarget=exact heldLogout=NOT_RUN publication=MOCK compiler=INCOMPLETE wirePaging=NOT_RUN");
    }
    public static void main(String[] args) throws Exception { new CapacityAdmissionShapeProbe().run(); }
}
