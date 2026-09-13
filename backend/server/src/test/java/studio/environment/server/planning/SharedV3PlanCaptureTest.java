package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.profile.ProfileCapture;

class SharedV3PlanCaptureTest {
    static ProfileCapture.Command command(){
        var mappings=new ArrayList<ProfileCapture.SlotMapping>();int index=0;
        for(String identity:List.of("one","two","three"))mappings.add(new ProfileCapture.SlotMapping(old(identity).key(),"slot-"+(++index),"Neutral "+index));
        return new ProfileCapture.Command("invented-profile",BigInteger.ONE,mappings);
    }
    @Test void originalPhysicalCaptureProducesSchema3WithoutDonorValuesOrComputedMembership() {
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);
        var capture=assertDoesNotThrow(()->service.capture(f.lease,id,"2",command()));
        assertEquals(f.reference,capture.definition());
        var parsed=tools.jackson.databind.json.JsonMapper.builder().build().readTree(capture.draft().source());
        assertEquals("3",parsed.get("schemaVersion").asString());
        var checked=capture.draft().checked();assertEquals(3,checked.profile().entities().size());
        assertEquals(Set.of("item"),checked.profile().entities().stream().map(studio.environment.core.profile.Profile.Entity::type).collect(java.util.stream.Collectors.toSet()));
        var portable=new studio.environment.server.profile.V3ProfileBytesAdapter().read(f.model.checked(),capture.draft().source().getBytes(java.nio.charset.StandardCharsets.UTF_8),studio.environment.server.definition.BoundedDocumentParser.Format.JSON);
        assertEquals(checked,assertInstanceOf(studio.environment.server.profile.V3ProfileBytesAdapter.Result.Accepted.class,portable).checked());
        var changed=SharedV3PlanXmlTest.with(definition(false),XML.replace("al&#112;ha","violet").replace("alpha","amber").replace("beta","bronze"));
        var second=changed.service();String secondId=changed.inspected(second);
        assertEquals(capture.draft().source(),second.capture(changed.lease,secondId,"2",command()).draft().source());
    }
    @Test void targetChoicesDoNotAlterOriginalCaptureAndAdmittedViewUsesTheSameVersion() {
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);
        var before=service.capture(f.lease,id,"2",command());
        var draft=new Draft(intent(edit("one",new studio.environment.core.planning.TargetIntent.FieldValue.Entered("renamed"),new studio.environment.core.planning.TargetIntent.FieldValue.Entered("different"))),List.of());
        service.replaceDraft(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),draft);
        assertEquals(before,service.capture(f.lease,id,"3",command()));
        try(var view=service.reserveView(f.lease,id)){
            assertEquals(before,view.run(()->{view.pin("3");return view.capture(command());}));
        }
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->service.capture(f.lease,id,"2",command())).code());
        var partial=new ProfileCapture.Command("invented-profile",BigInteger.ONE,command().mappings().subList(0,1));
        assertEquals(PlanRefusal.Code.PROFILE_REFUSED,assertThrows(PlanRefusal.class,()->service.capture(f.lease,id,"3",partial)).code());
        var computed=new ProfileCapture.Command("invented-profile",BigInteger.ONE,List.of(new ProfileCapture.SlotMapping(new studio.environment.core.graph.ObservedGraph.Key("tones","alpha"),"slot-1","Neutral")));
        assertEquals(PlanRefusal.Code.PROFILE_REFUSED,assertThrows(PlanRefusal.class,()->service.capture(f.lease,id,"3",computed)).code());
        assertEquals(before,service.capture(f.lease,id,"3",command()));
    }
    @Test void sourceAndFullOriginalProofMustMatchIndependentPlanPinBeforeCapture() {
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);var snapshot=f.snapshot(service,id,"2");
        var original=snapshot.current().orElseThrow();var proof=(PlanContentEvidence.V3Observed)original.evidence();var pin=snapshot.v3Pins().orElseThrow().original();
        assertFalse(proof.derived().rules().isEmpty());
        var forged=new Content(original.sources(),original.graph(),original.provenance(),new PlanContentEvidence.V3Observed(proof.observationFingerprint(),proof.input(),new studio.environment.core.derived.DerivedResult.Complete(proof.derived().graph(),List.of())));
        var adapter=new studio.environment.server.plan.PlanContentAdapter();
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->adapter.captureV3(snapshot.definition(),pin,forged,command(),new studio.environment.core.observation.ObservationPort.Cancellation())).code());
        var foreign=new studio.environment.core.derived.DerivedInput.Pin("foreign",pin.logicalDigest(),pin.bindingId(),pin.bindingDigest(),pin.documentDigests());
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->adapter.captureV3(snapshot.definition(),foreign,original,command(),new studio.environment.core.observation.ObservationPort.Cancellation())).code());
        var legacy=new Content(original.sources(),original.graph(),original.provenance());
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->adapter.captureV3(snapshot.definition(),pin,legacy,command(),new studio.environment.core.observation.ObservationPort.Cancellation())).code());
        var cancelled=new studio.environment.core.observation.ObservationPort.Cancellation();cancelled.cancel();
        assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,()->adapter.captureV3(snapshot.definition(),pin,original,command(),cancelled)).code());
        assertEquals(service.capture(f.lease,id,"2",command()).draft(),adapter.captureV3(snapshot.definition(),pin,original,command(),new studio.environment.core.observation.ObservationPort.Cancellation()));
    }
    @Test void retiredDirectCaptureAndClosedViewCannotReturnLatePortableResult() throws Exception {
        for(boolean closeView:List.of(false,true)){
            var f=new SharedV3PlanXmlTest();var adapter=new HeldCapture();var service=f.service(adapter);String id=f.inspected(service);
            var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var view=closeView?service.reserveView(f.lease,id):null;
            var worker=new Thread(()->{try{
                if(view==null)service.capture(f.lease,id,"2",command());else view.run(()->{view.pin("2");return view.capture(command());});
            }catch(Throwable refused){failure.set(refused);}});worker.start();
            try{
                assertTrue(adapter.entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
                if(view==null)service.discard(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()));else view.close();
                assertTrue(adapter.control.get().cancelled());
            }finally{adapter.released.countDown();worker.join(5_000);if(view!=null)view.close();}
            assertFalse(worker.isAlive());assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.get()).code());
        }
    }
    static final class HeldCapture implements ContentAdapter {
        final boolean cancelOnly;
        HeldCapture(){this(false);}
        HeldCapture(boolean cancelOnly){this.cancelOnly=cancelOnly;}
        final studio.environment.server.plan.PlanContentAdapter actual=new studio.environment.server.plan.PlanContentAdapter();
        final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),released=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<studio.environment.core.observation.ObservationPort.Cancellation> control=new java.util.concurrent.atomic.AtomicReference<>();
        public ContentResult project(PublishedDefinition d,String b,studio.environment.core.observation.ObservationResult.Observation o){return actual.project(d,b,o);}
        public ContentResult project(PublishedDefinition d,String b,studio.environment.core.observation.ObservationResult.Observation o,studio.environment.core.observation.ObservationPort.Cancellation c){return actual.project(d,b,o,c);}
        public ContentResult materialize(PublishedDefinition d,String b,Content c,Draft draft){return actual.materialize(d,b,c,draft);}
        public Capture capture(PublishedDefinition d,String b,Content c,ProfileCapture.Command command){throw new AssertionError("VERSION_REQUIRED");}
        public Capture captureV3(PublishedDefinition d,studio.environment.core.derived.DerivedInput.Pin pin,Content current,ProfileCapture.Command command,studio.environment.core.observation.ObservationPort.Cancellation cancellation){
            var result=actual.captureV3(d,pin,current,command,cancellation);
            if(cancelOnly){cancellation.cancel();return result;}
            control.set(cancellation);entered.countDown();
            try{assertTrue(released.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}return result;
        }
    }
    @Test void cancellationAfterAdapterSuccessStillRefusesUnderTheUnchangedLiveLease() {
        var f=new SharedV3PlanXmlTest();var service=f.service(new HeldCapture(true));String id=f.inspected(service);
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->service.capture(f.lease,id,"2",command())).code());
        assertTrue(service.live(f.lease));assertTrue(service.summary(f.lease,id).inspectionValid());assertEquals("2",service.summary(f.lease,id).revision());
    }
}
