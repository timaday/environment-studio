package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.SharedV3PlanReviewTest.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import studio.environment.core.Outcome;
import studio.environment.core.RequiredCheck;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.observation.*;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.plan.PlanContentAdapter;

class SharedV3PlanReviewProofFailureTest {
    @ParameterizedTest @ValueSource(strings={"capture-view","capture-direct","document-view","document-direct","preview-direct"})
    void typedFullSourceRefusalCannotReviveReviewAcrossContentRoutes(String route) {
        var f=allowed();var content=new CorruptingContent();var service=f.service(content);String plan=f.inspected(service);
        service.materialize(f.lease,plan,"2");var before=service.validateV3(f.lease,plan,"2");var request=command("2",before.inputFingerprint());
        var ack=service.reviewV3(f.lease,plan,request);content.corrupt=true;
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->{
            switch(route) {
                case "capture-direct"->service.capture(f.lease,plan,"2",SharedV3PlanCaptureTest.command());
                case "document-direct"->service.comparison(f.lease,plan,"2",true,"sheet",ViewMode.RAW,true);
                case "preview-direct"->service.previewProfile(f.lease,plan,"2",new NativeCommand.Reference(UUID.randomUUID().toString(),"1"),List.of());
                default->{try(var view=service.reserveView(f.lease,plan,PlanDefinition.Version.V3)){view.run(()->{view.pin("2");return route.equals("capture-view")?view.capture(SharedV3PlanCaptureTest.command()):view.document(true,"sheet",ViewMode.RAW,true);});}}
            }
        }).code());content.corrupt=false;
        var recovered=service.validateV3(f.lease,plan,"2");assertEquals(before.inputFingerprint(),recovered.inputFingerprint());
        assertEquals(Outcome.UNKNOWN,outcome(recovered,RequiredCheck.REVIEW),route);
        assertEquals(ack,service.reviewV3(f.lease,plan,request));assertEquals(Outcome.UNKNOWN,outcome(service.validateV3(f.lease,plan,"2"),RequiredCheck.REVIEW));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualPhysicalAndComputedReadProofFailuresInvalidateReview(boolean computed) {
        var f=allowed();var content=new SharedV3PlanCompositionAuthorityTest.ControlledContent();var service=f.service(content);String plan=f.inspected(service);
        service.materialize(f.lease,plan,"2");var before=service.validateV3(f.lease,plan,"2");var request=command("2",before.inputFingerprint());service.reviewV3(f.lease,plan,request);
        content.forge=true;assertTrue(service.materialize(f.lease,plan,"2").complete());
        try(var view=service.reserveView(f.lease,plan,PlanDefinition.Version.V3)) {
            assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->view.run(()->{view.pin("2");return computed
                    ?studio.environment.server.plan.V3PlanComputedViews.rules(view,true,0,100)
                    :studio.environment.server.plan.V3PlanPhysicalViews.documents(view);})).code());
        }
        content.forge=false;assertTrue(service.materialize(f.lease,plan,"2").complete());
        var recovered=service.validateV3(f.lease,plan,"2");assertEquals(before.inputFingerprint(),recovered.inputFingerprint());
        assertEquals(Outcome.UNKNOWN,outcome(recovered,RequiredCheck.REVIEW));
    }
    @Test void ordinaryBadSelectorsAndProfileRequestsDoNotEraseCurrentReview() {
        var f=allowed();var service=f.service();String plan=f.inspected(service);service.materialize(f.lease,plan,"2");
        service.reviewV3(f.lease,plan,command("2",service.validateV3(f.lease,plan,"2").inputFingerprint()));
        try(var view=service.reserveView(f.lease,plan,PlanDefinition.Version.V3)) {
            view.run(()->{view.pin("2");assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->view.document(true,"missing",ViewMode.RAW,true)).code());return true;});
        }
        var mappings=SharedV3PlanCaptureTest.command().mappings().subList(0,1);
        assertEquals(PlanRefusal.Code.PROFILE_REFUSED,assertThrows(PlanRefusal.class,()->service.capture(f.lease,plan,"2",new ProfileCapture.Command("mock-partial",java.math.BigInteger.ONE,mappings))).code());
        assertEquals(Outcome.PASS,outcome(service.validateV3(f.lease,plan,"2"),RequiredCheck.REVIEW));
    }
    @Test void fullProofFailureInPreviewCannotReviveReviewAfterSameRevisionRecovery() {
        var f=allowed();var content=new CorruptingContent();var service=f.service(content);String plan=f.inspected(service);
        service.materialize(f.lease,plan,"2");var before=service.validateV3(f.lease,plan,"2");var request=command("2",before.inputFingerprint());
        var ack=service.reviewV3(f.lease,plan,request);content.corrupt=true;
        try(var view=service.reserveView(f.lease,plan,PlanDefinition.Version.V3)) {
            assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->view.run(()->{
                view.pin("2");return view.preview(new NativeCommand.Reference(UUID.randomUUID().toString(),"1"),List.of());
            })).code());
        }
        content.corrupt=false;
        var recovered=service.validateV3(f.lease,plan,"2");assertEquals(before.inputFingerprint(),recovered.inputFingerprint());
        assertEquals(Outcome.UNKNOWN,outcome(recovered,RequiredCheck.REVIEW),"failed preview proof must invalidate the old review");
        assertEquals(ack,service.reviewV3(f.lease,plan,request));assertEquals(Outcome.UNKNOWN,outcome(service.validateV3(f.lease,plan,"2"),RequiredCheck.REVIEW));
        service.reviewV3(f.lease,plan,command("2",before.inputFingerprint()));assertEquals(Outcome.PASS,outcome(service.validateV3(f.lease,plan,"2"),RequiredCheck.REVIEW));
    }
    private static Content corrupt(Content content) {
        var sources=new ArrayList<>(content.sources());var first=sources.getFirst();
        sources.set(0,new Source(first.documentId(),first.xml()+" ",first.digest()));
        return new Content(sources,content.graph(),content.provenance(),content.evidence());
    }
    private static HostedPlanService.ViewSnapshot corrupt(HostedPlanService.ViewSnapshot s) {
        return new HostedPlanService.ViewSnapshot(s.revision(),s.definition(),s.binding(),s.current(),Optional.of(corrupt(s.selected(true))),s.draft(),s.references(),s.displayHandles(),s.v3Pins());
    }
    private static final class CorruptingContent implements ContentAdapter {
        final PlanContentAdapter actual=new PlanContentAdapter();boolean corrupt;
        public ContentResult project(PublishedDefinition d,String b,ObservationResult.Observation o){return actual.project(d,b,o);}
        public ContentResult project(PublishedDefinition d,String b,ObservationResult.Observation o,ObservationPort.Cancellation c){return actual.project(d,b,o,c);}
        public ContentResult materialize(PublishedDefinition d,String b,Content c,Draft draft){return actual.materialize(d,b,c,draft);}
        public V3PlanContent.Result materializeV3(PublishedDefinition d,DerivedInput.Pin original,Content current,DerivedInput.Pin next,Draft draft,ObservationPort.Cancellation c){return actual.materializeV3(d,original,current,next,draft,c);}
        public Capture capture(PublishedDefinition d,String b,Content c,ProfileCapture.Command command){return actual.capture(d,b,c,command);}
        public Capture captureV3(PublishedDefinition d,DerivedInput.Pin pin,Content c,ProfileCapture.Command command,ObservationPort.Cancellation flag){return actual.captureV3(d,pin,corrupt?corrupt(c):c,command,flag);}
        public DocumentView compare(HostedPlanService.ViewSnapshot snapshot,boolean target,String documentId,ViewMode mode,ObservationPort.Cancellation c){return actual.compare(corrupt?corrupt(snapshot):snapshot,target,documentId,mode,c);}
        public void verifyV3(HostedPlanService.ViewSnapshot snapshot,boolean target,ObservationPort.Cancellation c){actual.verifyV3(corrupt?corrupt(snapshot):snapshot,target,c);}
    }
}
