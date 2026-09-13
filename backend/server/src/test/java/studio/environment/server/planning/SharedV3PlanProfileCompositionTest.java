package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.profile.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.core.planning.TargetIntent.*;

/** Actual XML/capture/compose ports with a test-only qualified publication witness. */
class SharedV3PlanProfileCompositionTest {
    static final NativeCommand.Reference PROFILE=new NativeCommand.Reference("00000000-0000-4000-8000-000000000002","1");
    @Test void wholeAndPartialReuseEnterSharedLifecycleWithoutLegacyReadiness() {
        for(boolean whole:List.of(false,true)) {
            var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);
            f.profile=new PublishedProfile(PROFILE,"mock-profile-publication",service.capture(f.lease,id,"2",SharedV3PlanCaptureTest.command()).draft().checked());
            assertTrue(service.materialize(f.lease,id,"2").complete());
            var preview=assertDoesNotThrow(()->service.previewProfile(f.lease,id,"2",PROFILE,whole?List.of():List.of("slot-1")));
            assertEquals(whole?List.of("slot-1","slot-2","slot-3"):List.of("slot-1"),preview.roots());
            assertEquals(preview.roots(),preview.dependencies().included().stream().map(Profile.Entity::id).toList());
            assertEquals(List.of("by-finish","by-tone"),preview.v3().orElseThrow().affectedDerivations());
            var choices=new ArrayList<ProfileComposer.Decision>();
            for(String slot:preview.roots())choices.add(new ProfileComposer.Decision.Create(slot,"new-"+slot));
            var mutation=new HostedPlanService.Mutation("2",UUID.randomUUID().toString());
            var ack=assertDoesNotThrow(()->service.composeProfile(f.lease,id,mutation,preview,choices));
            assertEquals("3",ack.revision());assertEquals(ack,service.composeProfile(f.lease,id,mutation,preview,choices));
            var snapshot=f.snapshot(service,id,"3");assertTrue(snapshot.target().isEmpty());
            assertEquals(XML,snapshot.current().orElseThrow().sources().getFirst().xml());
            assertEquals(preview.roots().size(),snapshot.draft().intent().entities().size());
            for(var entity:snapshot.draft().intent().entities()){
                var created=assertInstanceOf(studio.environment.core.planning.TargetIntent.EntityDecision.Create.class,entity);
                assertTrue(created.fields().values().stream().allMatch(v->v instanceof studio.environment.core.planning.TargetIntent.FieldValue.Unresolved));
                assertDoesNotThrow(()->snapshot.displayHandle(created.entity()));
            }
            assertFalse(service.view(f.lease,Optional.of(id)).targetComplete());
        }
    }
    @Test void commandReuseThenExplicitValuesRecomputeFreshGroupsAndKeepSiblings() {
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);
        f.profile=new PublishedProfile(PROFILE,"mock-profile-publication",service.capture(f.lease,id,"2",SharedV3PlanCaptureTest.command()).draft().checked());
        assertTrue(service.materialize(f.lease,id,"2").complete());
        var preview=service.previewProfile(f.lease,id,"2",PROFILE,List.of("slot-1"));
        try(var view=service.reserveView(f.lease,id)) {
            assertEquals(preview,view.run(()->{view.pin("2");return view.preview(PROFILE,List.of("slot-1"));}));
        }
        var command=new PlanCommand(new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),
                new PlanCommand.Action.Compose(PROFILE,HostedPlanService.compositionPreviewDigest(preview),preview.roots(),
                        List.of(new PlanCommand.ProfileDecision.Create("slot-1","new-slot"))));
        var ack=service.command(f.lease,id,command);assertEquals("3",ack.revision());assertEquals(ack,service.command(f.lease,id,command));
        var unresolved=f.snapshot(service,id,"3");var fresh=new PlanCommand.Ref.Fresh("new-slot","item");
        assertTrue(unresolved.target().isEmpty());assertDoesNotThrow(()->unresolved.displayHandle(new Ref.Fresh("new-slot","item")));
        var source=unresolved.current().orElseThrow().sources().getFirst();
        var created=new PlanCommand.Change(new PlanCommand.Entity.Create(fresh,Map.of("id",new FieldValue.Entered("four"),"tone",new FieldValue.Entered("gamma"),"finish",new FieldValue.Entered("z")),Map.of()),
                List.of(new PlanCommand.Placement(fresh,"sheet","items",new PlanCommand.Parent.Existing("sheet",source.digest(),"0"))));
        service.command(f.lease,id,new PlanCommand(new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),new PlanCommand.Action.Upsert(created)));
        var target=f.snapshot(service,id,"4").target().orElseThrow();
        assertEquals("<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/><item xmlns=\"\" finish=\"z\" id=\"four\" tone=\"gamma\"/></items>",target.sources().getFirst().xml());
        assertEquals(new Ref.Fresh("new-slot","item"),target.provenance().get(old("four").key()));
        assertEquals(List.of("alpha:x","alpha:y","beta:x","gamma:z"),((PlanContentEvidence.V3Target)target.evidence()).derived().graph().cooccurrences().stream().map(e->e.source().value()+":"+e.target().value()).toList());
    }
    @Test void legacyAlteredDerivedAndStalePublicationPreviewsCannotCompose() {
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);
        f.profile=new PublishedProfile(PROFILE,"mock-profile-publication",service.capture(f.lease,id,"2",SharedV3PlanCaptureTest.command()).draft().checked());
        assertTrue(service.materialize(f.lease,id,"2").complete());
        var preview=service.previewProfile(f.lease,id,"2",PROFILE,List.of("slot-1"));
        var legacy=new HostedPlanService.CompositionPreview(preview.planId(),preview.revision(),preview.observationFingerprint(),preview.profile(),preview.publicationDigest(),preview.roots(),preview.rootsDigest(),preview.closureDigest(),preview.dependencies());
        var altered=new HostedPlanService.CompositionPreview(preview.planId(),preview.revision(),preview.observationFingerprint(),preview.profile(),preview.publicationDigest(),preview.roots(),preview.rootsDigest(),preview.closureDigest(),preview.dependencies(),Optional.of(new V3ProfileComposer.Preview(preview.dependencies(),List.of("by-tone"))));
        assertNotEquals(HostedPlanService.compositionPreviewDigest(preview),HostedPlanService.compositionPreviewDigest(legacy));
        assertNotEquals(HostedPlanService.compositionPreviewDigest(preview),HostedPlanService.compositionPreviewDigest(altered));
        for(var invalid:List.of(legacy,altered)) {
            assertEquals(PlanRefusal.Code.STALE_PREVIEW,assertThrows(PlanRefusal.class,()->service.composeProfile(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),invalid,List.of(new ProfileComposer.Decision.Create("slot-1","new-slot")))).code());
            var command=new PlanCommand(new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new PlanCommand.Action.Compose(PROFILE,HostedPlanService.compositionPreviewDigest(invalid),preview.roots(),List.of(new PlanCommand.ProfileDecision.Create("slot-1","new-slot"))));
            assertEquals(PlanRefusal.Code.STALE_PREVIEW,assertThrows(PlanRefusal.class,()->service.command(f.lease,id,command)).code());
        }
        f.profile=new PublishedProfile(PROFILE,"changed-publication",f.profile.checked());
        assertEquals(PlanRefusal.Code.STALE_PREVIEW,assertThrows(PlanRefusal.class,()->service.composeProfile(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),preview,List.of(new ProfileComposer.Decision.Create("slot-1","new-slot")))).code());
        assertEquals("2",service.summary(f.lease,id).revision());assertTrue(service.summary(f.lease,id).targetComplete());
    }
    @Test void existingReuseAfterIdentityAndChildFieldEditsKeepsOriginalProvenance() {
        for(boolean child:List.of(false,true)) {
            String xml=child?"<items xmlns:p='urn:props'><item id='one' finish='x'><p:entry p:key='tone' p:value='alpha'/></item><item id='two' finish='y'><p:entry p:key='tone' p:value='alpha'/></item><item id='three' finish='x'><p:entry p:key='tone' p:value='beta'/></item></items>":XML;
            var f=SharedV3PlanXmlTest.with(definition(child),xml);var service=f.service();String id=f.inspected(service);
            f.profile=new PublishedProfile(PROFILE,"mock-profile-publication",service.capture(f.lease,id,"2",SharedV3PlanCaptureTest.command()).draft().checked());
            service.replaceDraft(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new Draft(intent(edit("one",new FieldValue.Entered("renamed"),new FieldValue.Entered("beta"))),List.of()));
            var before=f.snapshot(service,id,"3");var target=before.target().orElseThrow();
            String expected=child?"<items xmlns:p='urn:props'><item id='renamed' finish='x'><p:entry p:key='tone' p:value='beta'/></item><item id='two' finish='y'><p:entry p:key='tone' p:value='alpha'/></item><item id='three' finish='x'><p:entry p:key='tone' p:value='beta'/></item></items>":"<items><!-- mock -->\r\n<item id='renamed' tone='beta' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>";
            assertEquals(expected,target.sources().getFirst().xml());
            var preview=service.previewProfile(f.lease,id,"3",PROFILE,List.of("slot-1"));
            var command=new PlanCommand(new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),new PlanCommand.Action.Compose(PROFILE,HostedPlanService.compositionPreviewDigest(preview),preview.roots(),List.of(new PlanCommand.ProfileDecision.UseExisting("slot-1",before.reference(old("one"))))));
            assertEquals("4",service.command(f.lease,id,command).revision());var after=f.snapshot(service,id,"4").target().orElseThrow();
            assertEquals(expected,after.sources().getFirst().xml());assertEquals(old("one"),after.provenance().get(old("renamed").key()));
            assertEquals(List.of("alpha:y","beta:x"),((PlanContentEvidence.V3Target)after.evidence()).derived().graph().cooccurrences().stream().map(e->e.source().value()+":"+e.target().value()).toList());
        }
    }
}
