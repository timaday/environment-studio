package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.planning.TargetIntent.FieldValue;

/** Real invented XML/projection with explicitly test-only publication/observation witnesses. */
class SharedV3PlanSummaryTest {
    @Test void currentAndTargetComputedCountsAreSeparateFromPhysicalCountsAndFromMissingEvidence() {
        var f=new SharedV3PlanXmlTest();var service=f.service();
        var created=service.createV3(f.lease,UUID.randomUUID().toString(),f.reference,"mock-pg","destination");
        var missing=service.viewV3(f.lease,Optional.empty());assertEquals(created.planId(),missing.summary().planId());
        assertTrue(missing.currentComputedCounts().isEmpty());assertTrue(missing.targetComputedCounts().isEmpty());
        service.discard(f.lease,created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
        String id=f.inspected(service);var observed=service.viewV3(f.lease,Optional.of(id));
        assertEquals(new HostedPlanService.Counts(1,3,0),observed.summary().currentCounts());
        assertEquals(Optional.of(new HostedPlanService.ComputedCounts(4,6,3)),observed.currentComputedCounts());
        assertTrue(observed.targetComputedCounts().isEmpty());service.verifySummaryV3(f.lease,observed);
        assertTrue(service.materialize(f.lease,id,"2").complete());
        var target=service.viewV3(f.lease,Optional.of(id));assertEquals(observed.currentComputedCounts(),target.targetComputedCounts());
        assertEquals(observed.summary().currentCounts(),target.summary().targetCounts());
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->service.verifySummaryV3(f.lease,observed)).code());
        service.verifySummaryV3(f.lease,target);
    }
    @Test void targetFieldDecisionsRecomputeCountsAndUnresolvedTargetIsAbsent() {
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);
        var observed=service.viewV3(f.lease,Optional.of(id));
        var changed=new PlanPorts.Draft(intent(edit("one",new FieldValue.KeepObserved(),new FieldValue.Entered("gamma"))),List.of());
        service.replaceDraft(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),changed);
        var target=service.viewV3(f.lease,Optional.of(id));
        assertEquals(observed.currentComputedCounts(),target.currentComputedCounts());
        assertEquals(Optional.of(new HostedPlanService.ComputedCounts(5,6,3)),target.targetComputedCounts());
        assertEquals(target.summary().currentCounts(),target.summary().targetCounts());
        var unresolved=new PlanPorts.Draft(intent(edit("one",new FieldValue.KeepObserved(),new FieldValue.Unresolved())),List.of());
        service.replaceDraft(f.lease,id,new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),unresolved);
        var incomplete=service.viewV3(f.lease,Optional.of(id));
        assertEquals(target.currentComputedCounts(),incomplete.currentComputedCounts());assertTrue(incomplete.targetComputedCounts().isEmpty());
        assertFalse(incomplete.summary().targetComplete());assertFalse(incomplete.summary().exportAvailable());
    }
    @Test void completeEmptyComputedGraphsHavePresentZeroCountsAndMetadataNeedsNoLargeScratch() {
        var f=new SharedV3PlanXmlTest();f.xml="<items/>";var service=f.service();String id=f.inspected(service);
        var zero=Optional.of(new HostedPlanService.ComputedCounts(0,0,0));
        var current=service.viewV3(f.lease,Optional.of(id));assertEquals(zero,current.currentComputedCounts());assertTrue(current.targetComputedCounts().isEmpty());
        assertEquals(new HostedPlanService.Counts(1,0,0),current.summary().currentCounts());
        assertTrue(service.materialize(f.lease,id,"2").complete());
        try(var held=service.reserveView(f.lease,id)) {
            assertTrue(held.live());var target=service.viewV3(f.lease,Optional.empty());
            assertEquals(zero,target.currentComputedCounts());assertEquals(zero,target.targetComputedCounts());service.verifySummaryV3(f.lease,target);
            assertTrue(held.live());
        }
    }
    @Test void fullSummaryEqualityIncludesComputedCountsAndOriginalPlanIdentity() {
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);
        var captured=service.viewV3(f.lease,Optional.of(id));
        var altered=new HostedPlanService.V3View(captured.summary(),Optional.of(new HostedPlanService.ComputedCounts(0,0,0)),captured.targetComputedCounts());
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->service.verifySummaryV3(f.lease,altered)).code());
        service.discard(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()));
        String replacement=f.inspected(service);assertNotEquals(id,replacement);
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->service.verifySummaryV3(f.lease,captured)).code());
        assertEquals(replacement,service.viewV3(f.lease,Optional.empty()).summary().planId());
    }
    @Test void failedReinspectionRetainsCountsAsInvalidContextAndInvalidatesCapturedSummary() {
        var f=new SharedV3PlanXmlTest();var service=f.service();String id=f.inspected(service);
        var captured=service.viewV3(f.lease,Optional.of(id));f.xml="<items>";
        String operation=service.reserve(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString())).operationId().orElseThrow();
        var status=service.submit(f.lease,operation,(user,password)->{user[0]='m';password[0]='p';return new PlanPorts.CredentialLengths(1,1);});
        assertEquals(HostedPlanService.Phase.REFUSED,status.phase());
        var invalid=service.viewV3(f.lease,Optional.of(id));assertEquals("2",invalid.summary().revision());
        assertEquals(captured.currentComputedCounts(),invalid.currentComputedCounts());assertFalse(invalid.summary().inspectionValid());
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->service.verifySummaryV3(f.lease,captured)).code());
        service.verifySummaryV3(f.lease,invalid);
    }
}
