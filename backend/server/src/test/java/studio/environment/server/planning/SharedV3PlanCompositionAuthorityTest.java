package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.environment.core.derived.*;
import studio.environment.core.observation.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.profile.*;

class SharedV3PlanCompositionAuthorityTest {
    @Test void originalDirectViewAndCommandCancellationPreventsLatePreviewOrInstall() throws Exception {
        for(int mode:List.of(0,1,2)) {
            var f=new SharedV3PlanXmlTest();var adapter=new ControlledContent();var service=f.service(adapter);String id=f.inspected(service);
            var reference=SharedV3PlanProfileCompositionTest.PROFILE;
            f.profile=new PublishedProfile(reference,"mock-publication",service.capture(f.lease,id,"2",SharedV3PlanCaptureTest.command()).draft().checked());
            assertTrue(service.materialize(f.lease,id,"2").complete());
            var preview=service.previewProfile(f.lease,id,"2",reference,List.of("slot-1"));
            var command=new PlanCommand(new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new PlanCommand.Action.Compose(reference,HostedPlanService.compositionPreviewDigest(preview),preview.roots(),List.of(new PlanCommand.ProfileDecision.Create("slot-1","new-slot"))));
            var view=mode==1?service.reserveView(f.lease,id):null;
            var admission=mode==2?service.reserveCommand(f.lease,id):null;
            var failure=new AtomicReference<Throwable>();var result=new AtomicReference<Object>();adapter.hold=true;
            var worker=new Thread(()->{try{
                result.set(view!=null?view.run(()->{view.pin("2");return view.preview(reference,List.of("slot-1"));})
                        :admission!=null?admission.execute(command):service.previewProfile(f.lease,id,"2",reference,List.of("slot-1")));
            }catch(Throwable refused){failure.set(refused);}});worker.start();
            try {
                assertTrue(adapter.entered.await(5,TimeUnit.SECONDS));
                if(view!=null)view.close();else if(admission!=null)admission.close();
                else service.discard(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()));
                assertTrue(adapter.control.get().cancelled(),"original control mode "+mode);
            }finally{adapter.released.countDown();worker.join(5000);if(view!=null)view.close();if(admission!=null)admission.close();}
            assertFalse(worker.isAlive());assertNull(result.get());
            assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.get()).code());
            if(mode!=0){assertEquals("2",service.summary(f.lease,id).revision());assertTrue(service.summary(f.lease,id).targetComplete());}
        }
    }
    @Test void forgedRetainedTargetCannotEnterPreviewOrComposition() {
        var f=new SharedV3PlanXmlTest();var adapter=new ControlledContent();var service=f.service(adapter);String id=f.inspected(service);
        var reference=SharedV3PlanProfileCompositionTest.PROFILE;
        f.profile=new PublishedProfile(reference,"mock-publication",service.capture(f.lease,id,"2",SharedV3PlanCaptureTest.command()).draft().checked());
        assertTrue(service.materialize(f.lease,id,"2").complete());
        var preview=service.previewProfile(f.lease,id,"2",reference,List.of("slot-1"));
        adapter.forge=true;assertTrue(service.materialize(f.lease,id,"2").complete());
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->service.previewProfile(f.lease,id,"2",reference,List.of("slot-1"))).code());
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->service.composeProfile(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),preview,List.of(new ProfileComposer.Decision.Create("slot-1","new-slot")))).code());
        assertEquals("2",service.summary(f.lease,id).revision());
    }
    @Test void commandMaterializationRetainsOriginalCompositionControl() throws Exception {
        var f=new SharedV3PlanXmlTest();var adapter=new ControlledContent();var service=f.service(adapter);String id=f.inspected(service);
        var reference=SharedV3PlanProfileCompositionTest.PROFILE;
        f.profile=new PublishedProfile(reference,"mock-publication",service.capture(f.lease,id,"2",SharedV3PlanCaptureTest.command()).draft().checked());
        assertTrue(service.materialize(f.lease,id,"2").complete());var preview=service.previewProfile(f.lease,id,"2",reference,List.of("slot-1"));
        var command=new PlanCommand(new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new PlanCommand.Action.Compose(reference,HostedPlanService.compositionPreviewDigest(preview),preview.roots(),List.of(new PlanCommand.ProfileDecision.Create("slot-1","new-slot"))));
        var admission=service.reserveCommand(f.lease,id);adapter.holdMaterialization=true;var failure=new AtomicReference<Throwable>();
        var worker=new Thread(()->{try{admission.execute(command);}catch(Throwable refused){failure.set(refused);}});worker.start();
        try {
            assertTrue(adapter.entered.await(5,TimeUnit.SECONDS));assertSame(adapter.control.get(),adapter.materializationControl.get());
            admission.close();assertTrue(adapter.materializationControl.get().cancelled());
        }finally{adapter.released.countDown();worker.join(5000);admission.close();}
        assertFalse(worker.isAlive());assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.get()).code());
        // Draft committed before this cancellation; neither a late target nor a second composition is admitted.
        assertEquals("3",service.summary(f.lease,id).revision());assertFalse(service.summary(f.lease,id).targetComplete());
        assertEquals("3",service.command(f.lease,id,command).revision());assertEquals(1,f.snapshot(service,id,"3").draft().intent().entities().size());
    }
    static final class ControlledContent implements ContentAdapter {
        final studio.environment.server.plan.PlanContentAdapter actual=new studio.environment.server.plan.PlanContentAdapter();
        boolean hold,forge,holdMaterialization;
        final CountDownLatch entered=new CountDownLatch(1),released=new CountDownLatch(1);
        final AtomicReference<ObservationPort.Cancellation> control=new AtomicReference<>();
        final AtomicReference<ObservationPort.Cancellation> materializationControl=new AtomicReference<>();
        public ContentResult project(PublishedDefinition d,String b,ObservationResult.Observation o){return actual.project(d,b,o);}
        public ContentResult project(PublishedDefinition d,String b,ObservationResult.Observation o,ObservationPort.Cancellation c){return actual.project(d,b,o,c);}
        public ContentResult materialize(PublishedDefinition d,String b,Content c,Draft draft){return actual.materialize(d,b,c,draft);}
        public V3PlanContent.Result materializeV3(PublishedDefinition d,DerivedInput.Pin original,Content current,DerivedInput.Pin next,Draft draft,ObservationPort.Cancellation c){
            var result=actual.materializeV3(d,original,current,next,draft,c);
            if(holdMaterialization){materializationControl.set(c);waitForRelease();}
            if(!forge)return result;
            var target=((V3PlanContent.Result.Complete)result).content();var p=(PlanContentEvidence.V3Target)target.evidence();
            assertFalse(p.derived().rules().isEmpty());
            return new V3PlanContent.Result.Complete(new Content(target.sources(),target.graph(),target.provenance(),new PlanContentEvidence.V3Target(p.observationFingerprint(),p.originalPin(),p.physicalExpected(),p.preliminary(),p.preliminaryDerived(),p.input(),new DerivedResult.Complete(p.derived().graph(),List.of()))));
        }
        public Capture capture(PublishedDefinition d,String b,Content c,ProfileCapture.Command command){return actual.capture(d,b,c,command);}
        public Capture captureV3(PublishedDefinition d,DerivedInput.Pin p,Content c,ProfileCapture.Command command,ObservationPort.Cancellation flag){return actual.captureV3(d,p,c,command,flag);}
        public void verifyV3(HostedPlanService.ViewSnapshot snapshot,boolean target,ObservationPort.Cancellation c){
            actual.verifyV3(snapshot,target,c);control.set(c);
            if(hold)waitForRelease();
        }
        private void waitForRelease(){entered.countDown();try{assertTrue(released.await(5,TimeUnit.SECONDS));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}}
    }
}
