package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;

/** Actual shared service and XML adapters; definition/observation admission remains a mock witness. */
class SharedV3PlanComparisonTest {
    @Test void rawComparisonPreservesOriginalAndEditedTargetExactly(){
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);
        var raw=assertDoesNotThrow(()->service.comparison(f.lease,id,"2",false,"sheet",ViewMode.RAW,true));
        assertEquals(XML,raw.text());assertTrue(raw.exact());assertFalse(raw.redacted());
        var draft=new Draft(intent(edit("one",new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved(),new studio.environment.core.planning.TargetIntent.FieldValue.Entered("changed"))),List.of());
        service.replaceDraft(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),draft);
        var target=service.comparison(f.lease,id,"3",true,"sheet",ViewMode.RAW,true);
        assertEquals("<items><!-- mock -->\r\n<item id='one' tone='changed' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>",target.text());
        assertEquals(XML,service.comparison(f.lease,id,"3",false,"sheet",ViewMode.RAW,true).text());
        assertEquals(PlanRefusal.Code.DISCLOSURE_REQUIRED,assertThrows(PlanRefusal.class,()->service.comparison(f.lease,id,"3",true,"sheet",ViewMode.RAW,false)).code());
    }
    @Test void placeholdersCoverEveryQualifiedValueAndFormattedIsOnlyDisplay(){
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);
        var snap=f.snapshot(service,id,"2");var out=new StringBuilder("<items><!-- mock -->\r\n");
        for(String identity:List.of("one","two","three")){
            var ref=old(identity);out.append("<item id='").append(PlanBindings.token(snap,ref,"id"))
                .append("' tone='").append(PlanBindings.token(snap,ref,"tone"))
                .append("' finish='").append(PlanBindings.token(snap,ref,"finish")).append("'/>");
        }
        out.append("</items>");
        var placeholders=service.comparison(f.lease,id,"2",false,"sheet",ViewMode.PLACEHOLDERS,true);
        assertEquals(out.toString(),placeholders.text());assertTrue(placeholders.redacted());assertFalse(placeholders.exact());assertTrue(placeholders.unmappedConcreteMayRemain());
        var formatted=service.comparison(f.lease,id,"2",false,"sheet",ViewMode.FORMATTED,true);
        assertEquals("<items><!-- mock -->\r\n\n  <item id='one' tone='al&#112;ha' finish='x'/>\n  <item id='two' tone='alpha' finish='y'/>\n  <item id='three' tone='beta' finish='x'/>\n</items>",formatted.text());
        assertFalse(formatted.exact());assertFalse(formatted.redacted());
        try(var view=service.reserveView(f.lease,id)) {
            assertEquals(XML,view.run(()->{view.pin("2");return view.document(false,"sheet",ViewMode.RAW,true).text();}));
        }
    }
    @Test void legacyMissingPinsForeignOriginalRuleEvidenceAndChangedDraftRefuseBeforeComparison(){
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);service.materialize(f.lease,id,"2");
        var snap=f.snapshot(service,id,"2");var adapter=new studio.environment.server.plan.PlanContentAdapter();
        assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,()->adapter.compare(snap,true,"sheet",ViewMode.RAW)).code());
        var missing=new HostedPlanService.ViewSnapshot(snap.revision(),snap.definition(),snap.binding(),snap.current(),snap.target(),snap.draft(),snap.references(),snap.displayHandles());
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->adapter.compare(missing,true,"sheet",ViewMode.RAW,new studio.environment.core.observation.ObservationPort.Cancellation())).code());
        var original=snap.current().orElseThrow();var proof=(PlanContentEvidence.V3Observed)original.evidence();
        assertFalse(proof.derived().rules().isEmpty());
        var changedResult=new studio.environment.core.derived.DerivedResult.Complete(proof.derived().graph(),List.of());
        var forged=new Content(original.sources(),original.graph(),original.provenance(),new PlanContentEvidence.V3Observed(proof.observationFingerprint(),proof.input(),changedResult));
        var changedOriginal=new HostedPlanService.ViewSnapshot(snap.revision(),snap.definition(),snap.binding(),Optional.of(forged),snap.target(),snap.draft(),snap.references(),snap.displayHandles(),snap.v3Pins());
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->adapter.compare(changedOriginal,false,"sheet",ViewMode.RAW,new studio.environment.core.observation.ObservationPort.Cancellation())).code());
        var wrongProvenance=new HashMap<>(original.provenance());wrongProvenance.put(old("one").key(),new studio.environment.core.planning.TargetIntent.Ref.Fresh("invented-foreign","item"));
        var foreign=new Content(original.sources(),original.graph(),wrongProvenance,original.evidence());
        var changedProvenance=new HostedPlanService.ViewSnapshot(snap.revision(),snap.definition(),snap.binding(),Optional.of(foreign),snap.target(),snap.draft(),snap.references(),snap.displayHandles(),snap.v3Pins());
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->adapter.compare(changedProvenance,false,"sheet",ViewMode.RAW,new studio.environment.core.observation.ObservationPort.Cancellation())).code());
        var draft=new Draft(intent(edit("one",new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved(),new studio.environment.core.planning.TargetIntent.FieldValue.Entered("changed"))),List.of());
        var changedDraft=new HostedPlanService.ViewSnapshot(snap.revision(),snap.definition(),snap.binding(),snap.current(),snap.target(),draft,snap.references(),snap.displayHandles(),snap.v3Pins());
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->adapter.compare(changedDraft,true,"sheet",ViewMode.RAW,new studio.environment.core.observation.ObservationPort.Cancellation())).code());
        var cancelled=new studio.environment.core.observation.ObservationPort.Cancellation();cancelled.cancel();
        assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,()->adapter.compare(snap,false,"sheet",ViewMode.RAW,cancelled)).code());
        assertEquals(XML,adapter.compare(snap,true,"sheet",ViewMode.RAW,new studio.environment.core.observation.ObservationPort.Cancellation()).text());
    }
    @Test void closingOriginalViewOrRetiringPlanCancelsSameComparisonControlAndRejectsLateResult() throws Exception {
        for(boolean closeView:List.of(false,true)) {
            var f=new SharedV3PlanXmlTest();var adapter=new HeldComparison();var service=f.service(adapter);String id=f.inspected(service);
            var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var view=closeView?service.reserveView(f.lease,id):null;
            var worker=new Thread(()->{try {
                if(view==null)service.comparison(f.lease,id,"2",false,"sheet",ViewMode.RAW,true);
                else view.run(()->{view.pin("2");return view.document(false,"sheet",ViewMode.RAW,true);});
            }catch(Throwable refused){failure.set(refused);}});
            worker.start();
            try {
                assertTrue(adapter.entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
                if(view!=null)view.close();else service.discard(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()));
                assertTrue(adapter.control.get().cancelled());
                assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->service.reserveView(f.lease,id)).code());
            } finally {adapter.released.countDown();worker.join(5_000);if(view!=null)view.close();}
            assertFalse(worker.isAlive());assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.get()).code());
        }
    }
    static final class HeldComparison implements ContentAdapter {
        final studio.environment.server.plan.PlanContentAdapter actual=new studio.environment.server.plan.PlanContentAdapter();
        final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),released=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<studio.environment.core.observation.ObservationPort.Cancellation> control=new java.util.concurrent.atomic.AtomicReference<>();
        public ContentResult project(PublishedDefinition d,String b,studio.environment.core.observation.ObservationResult.Observation o){return actual.project(d,b,o);}
        public ContentResult project(PublishedDefinition d,String b,studio.environment.core.observation.ObservationResult.Observation o,studio.environment.core.observation.ObservationPort.Cancellation c){return actual.project(d,b,o,c);}
        public ContentResult materialize(PublishedDefinition d,String b,Content c,Draft draft){return actual.materialize(d,b,c,draft);}
        public Capture capture(PublishedDefinition d,String b,Content c,studio.environment.core.profile.ProfileCapture.Command command){return actual.capture(d,b,c,command);}
        public DocumentView compare(HostedPlanService.ViewSnapshot s,boolean target,String document,ViewMode mode,studio.environment.core.observation.ObservationPort.Cancellation cancellation){
            var result=actual.compare(s,target,document,mode,cancellation);control.set(cancellation);entered.countDown();
            try{assertTrue(released.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}
            return result;
        }
    }
    @Test void childLocatorsKeepDiscriminatorsAndOriginalTokensAfterIdentityEditsWhileUnresolvedTargetStaysUnavailable(){
        String xml="<items xmlns:p='urn:props'><item id='one' finish='x'><p:entry p:key='tone' p:value='alpha'/></item><item id='two' finish='y'/></items>";
        var f=SharedV3PlanXmlTest.with(definition(true),xml);var service=f.service();String id=f.inspected(service);var original=f.snapshot(service,id,"2");
        var one=old("one");var two=old("two");
        String expected="<items xmlns:p='urn:props'><item id='"+PlanBindings.token(original,one,"id")+"' finish='"+PlanBindings.token(original,one,"finish")+"'><p:entry p:key='tone' p:value='"+PlanBindings.token(original,one,"tone")+"'/></item><item id='"+PlanBindings.token(original,two,"id")+"' finish='"+PlanBindings.token(original,two,"finish")+"'/></items>";
        assertEquals(expected,service.comparison(f.lease,id,"2",false,"sheet",ViewMode.PLACEHOLDERS,true).text());
        var draft=new Draft(intent(edit("one",new studio.environment.core.planning.TargetIntent.FieldValue.Entered("renamed"),new studio.environment.core.planning.TargetIntent.FieldValue.Entered("new&tone"))),List.of());
        service.replaceDraft(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),draft);
        assertEquals("<items xmlns:p='urn:props'><item id='renamed' finish='x'><p:entry p:key='tone' p:value='new&amp;tone'/></item><item id='two' finish='y'/></items>",service.comparison(f.lease,id,"3",true,"sheet",ViewMode.RAW,true).text());
        assertEquals(expected,service.comparison(f.lease,id,"3",true,"sheet",ViewMode.PLACEHOLDERS,true).text());
        var unresolved=new Draft(intent(edit("one",new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved(),new studio.environment.core.planning.TargetIntent.FieldValue.Unresolved())),List.of());
        service.replaceDraft(f.lease,id,new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),unresolved);
        assertEquals(xml,service.comparison(f.lease,id,"4",false,"sheet",ViewMode.RAW,true).text());
        assertEquals(PlanRefusal.Code.INCOMPLETE_TARGET,assertThrows(PlanRefusal.class,()->service.comparison(f.lease,id,"4",true,"sheet",ViewMode.RAW,true)).code());
    }
}
