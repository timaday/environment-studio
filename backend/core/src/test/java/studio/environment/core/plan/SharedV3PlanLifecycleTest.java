package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanPorts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.session.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.core.observation.*;
import studio.environment.core.derived.*;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.profile.ProfileCapture;

/** Core controls use explicit trusted-port witnesses, never runtime qualification. */
class SharedV3PlanLifecycleTest {
    final HostedPlanServiceTest fixture=new HostedPlanServiceTest();
    final java.util.concurrent.atomic.AtomicInteger definitions=new java.util.concurrent.atomic.AtomicInteger();
    java.util.function.Consumer<ObservationPort.Cancellation> targetHook=ignored->{};
    java.util.function.UnaryOperator<Content> targetChange=java.util.function.UnaryOperator.identity();
    java.util.function.UnaryOperator<Content> projectionChange=java.util.function.UnaryOperator.identity();
    java.util.function.UnaryOperator<V3PlanContent.Result> targetOutcome=java.util.function.UnaryOperator.identity();
    record Flow(HostedPlanService service,String planId,Content original){}
    static String digest(String source){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    final PublishedDefinition v3=new PublishedDefinition(fixture.ref,"v3-publication-witness",new PlanDefinition.V3(VersionedPlanCompositionTest.definition()),List.of());
    Workspace workspace() {return new Workspace(){
        public PublishedDefinition definition(Owner owner,NativeCommand.Reference reference){return fixture.definition;}
        public PublishedDefinition definitionV3(Owner owner,NativeCommand.Reference reference){definitions.incrementAndGet();return v3;}
        public PublishedProfile profile(Owner owner,NativeCommand.Reference reference,PublishedDefinition definition){throw new AssertionError("UNEXPECTED_PROFILE");}
    };}
    HostedPlanService service(Workspace workspace) {
        return new HostedPlanService(fixture.authority::guard,workspace,Map.of("destination",new Destination("destination",studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,
                (selection,credentials,cancel)->{throw new AssertionError("UNEXPECTED_OBSERVATION");})),fixture.content,System::nanoTime);
    }
    SessionLedger.Lease lease(String id){return ((SessionLedger.Accepted)fixture.authority.admit(id,new Owner("https://invented.invalid",id))).lease();}
    @Test void explicitV3CreateSharesOneLeaseAndFourGlobalSlotsWithV2() {
        var service=service(workspace());String request=UUID.randomUUID().toString();
        var created=assertDoesNotThrow(()->service.createV3(fixture.lease,request,fixture.ref,"mock-binding","destination"));
        assertEquals("1",created.revision());assertTrue(created.operationId().isEmpty());assertEquals(1,definitions.get());
        assertEquals(created,service.createV3(fixture.lease,request,fixture.ref,"mock-binding","destination"));assertEquals(1,definitions.get());
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->service.create(fixture.lease,request,fixture.ref,"mock-binding","destination")).code());
        assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->service.create(fixture.lease,UUID.randomUUID().toString(),fixture.ref,"invented-binding","destination")).code());
        for(int i=0;i<3;i++){var other=lease("mixed-"+i);if(i==1)service.createV3(other,UUID.randomUUID().toString(),fixture.ref,"mock-binding","destination");else service.create(other,UUID.randomUUID().toString(),fixture.ref,"invented-binding","destination");}
        var extra=lease("extra");assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->service.createV3(extra,UUID.randomUUID().toString(),fixture.ref,"mock-binding","destination")).code());
        service.discard(fixture.lease,created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
        assertDoesNotThrow(()->service.createV3(extra,UUID.randomUUID().toString(),fixture.ref,"mock-binding","destination"));
    }
    static Map<String,Object> evidence() {
        var result=new HashMap<String,Object>(PlanObservedDestinationTest.evidence());
        @SuppressWarnings("unchecked") var metadata=new HashMap<String,Object>((Map<String,Object>)result.get("metadata"));
        metadata.put("adapterVersion","jdbc-observation-v3");result.put("metadata",metadata);return result;
    }
    @Test void v3ObservationUsesOriginalPermitAndCancellationThenInstallsFullEvidenceOnce() {
        observedFlow(false);
    }
    @Test void v3MaterializationUsesIndependentPlanDecisionPinAndRetainsCompleteTargetEvidence() {
        observedFlow(true);
    }
    Flow observedFlow(boolean materialize) { return observedFlow(materialize,HostedPlanService.Phase.SUCCEEDED); }
    Flow observedFlow(boolean materialize,HostedPlanService.Phase expectedPhase) {
        var cancelSeen=new java.util.concurrent.atomic.AtomicReference<ObservationPort.Cancellation>();
        var calls=new java.util.concurrent.atomic.AtomicInteger();String xml="<root/>";String sha=digest(xml);
        var model=(PlanDefinition.V3)v3.model();String fingerprint="c".repeat(64);
        var observation=new ObservationResult.Observation(fingerprint,model.logicalDigest(),model.bindingDigests().get("mock-binding"),
                List.of(new ObservationResult.Document("sheet",new ObservationResult.Key("int64","1"),xml,7,7,sha)),evidence());
        var input=new DerivedInput(DerivedInput.Kind.OBSERVED,new DerivedInput.Pin(fingerprint,model.logicalDigest(),"mock-binding",model.bindingDigests().get("mock-binding"),Map.of("sheet",sha)),List.of(),List.of());
        var computed=assertInstanceOf(DerivedResult.Complete.class,new DerivedGraphEngine().evaluate(model.checked(),input.pin(),input,()->false));
        var full=new Content(List.of(new Source("sheet",xml,sha)),new ObservedGraph(List.of(),List.of()),Map.of(),new PlanContentEvidence.V3Observed(fingerprint,input,computed));
        var port=new ObservationPort() {
            public ObservationResult observe(Selection selected,TransientCredentials credentials,Cancellation cancel){throw new AssertionError("MUST_RESERVE_V3");}
            public Reservation reserveV3(V3Selection selected){assertEquals(model.checked(),selected.compiled());assertEquals("mock-binding",selected.bindingId());return new Reservation.Admitted(new Permit(){
                public ObservationResult observe(TransientCredentials credentials,Cancellation cancel){calls.incrementAndGet();cancelSeen.set(cancel);credentials.close();return new ObservationResult.Complete(observation);}
                public void close(){}
            });}
        };
        var adapter=new ContentAdapter(){
            public ContentResult project(PublishedDefinition d,String binding,ObservationResult.Observation o){throw new AssertionError("MUST_PASS_ORIGINAL_CONTROL");}
            public ContentResult project(PublishedDefinition d,String binding,ObservationResult.Observation o,ObservationPort.Cancellation control){assertSame(cancelSeen.get(),control);assertSame(observation,o);return new ContentResult.Complete(projectionChange.apply(full));}
            public ContentResult materialize(PublishedDefinition d,String binding,Content current,Draft draft){return new ContentResult.Rejected(List.of("LEGACY_TARGET"));}
            public V3PlanContent.Result materializeV3(PublishedDefinition d,DerivedInput.Pin before,Content current,DerivedInput.Pin next,Draft draft,ObservationPort.Cancellation cancellation){
                assertEquals(input.pin(),before);assertSame(full,current);assertFalse(cancellation.cancelled());assertNotEquals(before.revisionToken(),next.revisionToken());
                assertEquals(before.documentDigests(),next.documentDigests());assertEquals(before.logicalDigest(),next.logicalDigest());assertEquals(before.bindingDigest(),next.bindingDigest());
                targetHook.accept(cancellation);
                var preliminary=new DerivedInput(DerivedInput.Kind.TYPED_TARGET,next,List.of(),List.of());
                var derived=assertInstanceOf(DerivedResult.Complete.class,new DerivedGraphEngine().evaluate(model.checked(),next,preliminary,()->false));
                var finalInput=new DerivedInput(DerivedInput.Kind.OBSERVED,next,List.of(),List.of());
                var expected=new studio.environment.core.planning.ExpectedTarget(List.of(),List.of(),Set.of(),Set.of());
                return targetOutcome.apply(new V3PlanContent.Result.Complete(targetChange.apply(new Content(full.sources(),full.graph(),full.provenance(),new PlanContentEvidence.V3Target(fingerprint,before,expected,preliminary,derived,finalInput,derived)))));
            }
            public Capture capture(PublishedDefinition d,String binding,Content current,ProfileCapture.Command command){throw new AssertionError("UNEXPECTED_CAPTURE");}
        };
        var service=new HostedPlanService(fixture.authority::guard,workspace(),Map.of("destination",new Destination("destination",studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,port)),adapter,System::nanoTime);
        var created=service.createV3(fixture.lease,UUID.randomUUID().toString(),fixture.ref,"mock-binding","destination");
        var reserved=assertDoesNotThrow(()->service.reserve(fixture.lease,created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())));
        var status=assertDoesNotThrow(()->service.submit(fixture.lease,reserved.operationId().orElseThrow(),(user,password)->{user[0]='a';password[0]='X';return new CredentialLengths(1,1);}));
        assertEquals(expectedPhase,status.phase());assertEquals(1,calls.get());
        if(expectedPhase!=HostedPlanService.Phase.SUCCEEDED){assertTrue(status.installedRevision().isEmpty());return new Flow(service,created.planId(),full);}
        assertEquals(Optional.of("2"),status.installedRevision());
        try(var view=service.reserveView(fixture.lease,created.planId())){view.run(()->{view.pin("2");assertSame(full,view.snapshot().current().orElseThrow());return true;});}
        if(materialize) {
            var result=service.materialize(fixture.lease,created.planId(),"2");assertTrue(result.complete(),result.diagnostics().toString());
            try(var view=service.reserveView(fixture.lease,created.planId())){view.run(()->{view.pin("2");var target=view.snapshot().target().orElseThrow();
                assertInstanceOf(PlanContentEvidence.V3Target.class,target.evidence());assertEquals(full.sources(),target.sources());return true;});}
        }
        assertEquals(PlanRefusal.Code.CREDENTIALS_ALREADY_CONSUMED,assertThrows(PlanRefusal.class,()->service.submit(fixture.lease,status.operationId(),(u,p)->{throw new AssertionError("REPEATED_READ");})).code());
        return new Flow(service,created.planId(),full);
    }
    @Test void defaultWorkspaceWrongVersionReferenceAndRevokedOriginalLeaseCannotCreateV3() {
        var defaultService=service(fixture.workspace);
        assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,()->defaultService.createV3(fixture.lease,UUID.randomUUID().toString(),fixture.ref,"mock-binding","destination")).code());
        for(var bad:List.of(fixture.definition,new PublishedDefinition(new NativeCommand.Reference(UUID.randomUUID().toString(),"2"),v3.publicationDigest(),v3.model(),v3.policies()))) {
            var ports=new Workspace(){public PublishedDefinition definition(Owner o,NativeCommand.Reference r){return fixture.definition;}
                public PublishedDefinition definitionV3(Owner o,NativeCommand.Reference r){return bad;}
                public PublishedProfile profile(Owner o,NativeCommand.Reference r,PublishedDefinition d){throw new AssertionError();}};
            var service=service(ports);assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,()->service.createV3(fixture.lease,UUID.randomUUID().toString(),fixture.ref,"mock-binding","destination")).code());
            assertDoesNotThrow(()->service.create(fixture.lease,UUID.randomUUID().toString(),fixture.ref,"invented-binding","destination"));
        }
        var revoked=new Workspace(){public PublishedDefinition definition(Owner o,NativeCommand.Reference r){throw new AssertionError();}
            public PublishedDefinition definitionV3(Owner o,NativeCommand.Reference r){fixture.authority.close(fixture.lease.id());return v3;}
            public PublishedProfile profile(Owner o,NativeCommand.Reference r,PublishedDefinition d){throw new AssertionError();}};
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->service(revoked).createV3(fixture.lease,UUID.randomUUID().toString(),fixture.ref,"mock-binding","destination")).code());
    }
    @Test void incompleteAndRefusedTargetsStayDistinctAndCannotRetainPriorSuccessfulTarget() {
        var flow=observedFlow(true);
        targetOutcome=ignored->new V3PlanContent.Result.Incomplete(List.of("by-tone"));
        var incomplete=flow.service().materialize(fixture.lease,flow.planId(),"2");
        assertFalse(incomplete.complete());assertEquals(HostedPlanService.Materialization.State.INCOMPLETE,incomplete.state());assertEquals(List.of("by-tone"),incomplete.diagnostics());
        assertFalse(flow.service().summary(fixture.lease,flow.planId()).targetComplete());
        targetOutcome=ignored->new V3PlanContent.Result.Refused("DERIVED_RULE_FAILED");
        var refused=flow.service().materialize(fixture.lease,flow.planId(),"2");assertEquals(HostedPlanService.Materialization.State.REFUSED,refused.state());assertEquals(List.of("DERIVED_RULE_FAILED"),refused.diagnostics());
        targetOutcome=java.util.function.UnaryOperator.identity();assertTrue(flow.service().materialize(fixture.lease,flow.planId(),"2").complete());
        targetChange=value->new Content(value.sources(),value.graph(),value.provenance());
        assertFalse(flow.service().materialize(fixture.lease,flow.planId(),"2").complete());assertFalse(flow.service().summary(fixture.lease,flow.planId()).targetComplete());
    }
    @Test void discardingDuringMaterializationSignalsOriginalControlAndReleasesSharedSlotOnlyAfterWork() throws Exception {
        var flow=observedFlow(false);var entered=new java.util.concurrent.CountDownLatch(1);var released=new java.util.concurrent.CountDownLatch(1);
        var control=new java.util.concurrent.atomic.AtomicReference<ObservationPort.Cancellation>();var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        targetHook=cancel->{control.set(cancel);entered.countDown();try{assertTrue(released.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException interrupted){throw new AssertionError(interrupted);}};
        var worker=new Thread(()->{try{flow.service().materialize(fixture.lease,flow.planId(),"2");}catch(Throwable refused){failure.set(refused);}});worker.start();
        try {
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));flow.service().discard(fixture.lease,flow.planId(),new HostedPlanService.Mutation("2",UUID.randomUUID().toString()));
            assertTrue(control.get().cancelled());assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->flow.service().createV3(fixture.lease,UUID.randomUUID().toString(),fixture.ref,"mock-binding","destination")).code());
        }finally{released.countDown();worker.join(5_000);}
        assertFalse(worker.isAlive());assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.get()).code());
        assertDoesNotThrow(()->flow.service().createV3(fixture.lease,UUID.randomUUID().toString(),fixture.ref,"mock-binding","destination"));
    }
    @Test void foreignOriginalTypedAndFinalPinsCannotInstallACompleteTarget() {
        var flow=observedFlow(true);
        for(int fault=0;fault<5;fault++) {
            final int selected=fault;
            targetChange=value->{
                var proof=(PlanContentEvidence.V3Target)value.evidence();
                var original=proof.originalPin();var typed=proof.preliminary();var finalInput=proof.input();
                if(selected==1)original=changedPin(original,"foreign-original",original.documentDigests());
                if(selected==2)typed=new DerivedInput(DerivedInput.Kind.OBSERVED,typed.pin(),typed.entities(),typed.edges());
                if(selected==3)finalInput=new DerivedInput(finalInput.kind(),changedPin(finalInput.pin(),"foreign-target",finalInput.pin().documentDigests()),finalInput.entities(),finalInput.edges());
                if(selected==4)finalInput=new DerivedInput(finalInput.kind(),changedPin(finalInput.pin(),finalInput.pin().revisionToken(),Map.of("sheet","d".repeat(64))),finalInput.entities(),finalInput.edges());
                return new Content(value.sources(),value.graph(),value.provenance(),new PlanContentEvidence.V3Target(selected==0?"d".repeat(64):proof.observationFingerprint(),original,proof.physicalExpected(),typed,proof.preliminaryDerived(),finalInput,proof.derived()));
            };
            var result=flow.service().materialize(fixture.lease,flow.planId(),"2");
            assertEquals(HostedPlanService.Materialization.State.REFUSED,result.state(),"fault "+fault);
            assertFalse(flow.service().summary(fixture.lease,flow.planId()).targetComplete());
            targetChange=java.util.function.UnaryOperator.identity();assertTrue(flow.service().materialize(fixture.lease,flow.planId(),"2").complete());
        }
    }
    private static DerivedInput.Pin changedPin(DerivedInput.Pin pin,String revision,Map<String,String> documents) {
        return new DerivedInput.Pin(revision,pin.logicalDigest(),pin.bindingId(),pin.bindingDigest(),documents);
    }
    @Test void missingAndForeignObservedProofCannotInstallOriginalContent() {
        projectionChange=value->new Content(value.sources(),value.graph(),value.provenance());
        var legacy=observedFlow(false,HostedPlanService.Phase.REFUSED);
        assertEquals(PlanRefusal.Code.INSPECTION_REQUIRED,assertThrows(PlanRefusal.class,()->legacy.service().materialize(fixture.lease,legacy.planId(),"1")).code());
        projectionChange=value->{
            var proof=(PlanContentEvidence.V3Observed)value.evidence();
            var input=new DerivedInput(proof.input().kind(),changedPin(proof.input().pin(),"foreign-observation",proof.input().pin().documentDigests()),proof.input().entities(),proof.input().edges());
            return new Content(value.sources(),value.graph(),value.provenance(),new PlanContentEvidence.V3Observed(proof.observationFingerprint(),input,proof.derived()));
        };
        observedFlow(false,HostedPlanService.Phase.REFUSED);
    }
    @Test void originalObservationIsNotACompleteTargetUntilActualMaterialization() {
        var flow=observedFlow(false);
        assertFalse(flow.service().summary(fixture.lease,flow.planId()).targetComplete());
        var view=flow.service().view(fixture.lease,Optional.of(flow.planId()));
        assertFalse(view.targetComplete());assertTrue(view.blockers().contains("TARGET_INCOMPLETE"));
        assertTrue(flow.service().materialize(fixture.lease,flow.planId(),"2").complete());
        assertTrue(flow.service().summary(fixture.lease,flow.planId()).targetComplete());
    }
    @Test void incompleteDerivationReferencesCannotBreakReadablePlanSummary() {
        var flow=observedFlow(true);targetOutcome=ignored->new V3PlanContent.Result.Incomplete(List.of("by-tone"));
        var result=flow.service().materialize(fixture.lease,flow.planId(),"2");assertEquals(List.of("by-tone"),result.diagnostics());
        var view=assertDoesNotThrow(()->flow.service().view(fixture.lease,Optional.of(flow.planId())));
        assertFalse(view.targetComplete());assertTrue(view.blockers().contains("TARGET_INCOMPLETE"));assertFalse(view.blockers().contains("by-tone"));
    }
}
