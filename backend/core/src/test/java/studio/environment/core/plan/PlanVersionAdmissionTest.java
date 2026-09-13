package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanDefinition.Version.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlanVersionAdmissionTest {
    static void missing(Runnable action) { assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,action::run).code()); }
    @Test void sealedVariantsDeriveTheirOwnModelVersion() {
        var old=new HostedPlanServiceTest();var newer=new SharedV3PlanLifecycleTest();
        assertEquals(V2,old.definition.model().version());assertEquals(V3,newer.v3.model().version());
    }
    @Test void wrongOwnedVersionDoesNotFallBack() {
        var h=new PlanLifecycleTest.Harness();missing(()->h.service.requireOwned(h.base.lease,h.created.planId(),V3));
        assertDoesNotThrow(()->h.service.requireOwned(h.base.lease,h.created.planId(),V2));
    }
    @Test void currentAndExplicitSummaryAreVersionFilteredBeforeExpiry() {
        var h=new PlanLifecycleTest.Harness();h.reserve();h.nanos.set(61_000_000_000L);
        missing(()->h.service.view(h.base.lease,Optional.empty(),V3));
        missing(()->h.service.view(h.base.lease,Optional.of(h.created.planId()),V3));
        assertEquals(0,h.closes.get());
        assertEquals(h.created.planId(),h.service.view(h.base.lease,Optional.empty(),V2).planId());assertEquals(1,h.closes.get());
    }
    @Test void finalSummaryVerificationRejectsWrongVersion() {
        var h=new PlanLifecycleTest.Harness();var view=h.service.view(h.base.lease,Optional.empty());
        missing(()->h.service.verifySummary(h.base.lease,view,V3));h.service.verifySummary(h.base.lease,view,V2);
    }
    @Test void wrongVersionDoesNotReserveSharedViewOrCommandScratch() {
        var h=new PlanLifecycleTest.Harness();
        missing(()->h.service.reserveView(h.base.lease,h.created.planId(),V3));
        try(var view=h.service.reserveView(h.base.lease,h.created.planId(),V2)) {
            missing(()->h.service.reserveCommand(h.base.lease,h.created.planId(),V3));
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->h.service.reserveCommand(h.base.lease,h.created.planId(),V2)).code());
        }
        missing(()->h.service.reserveCommand(h.base.lease,h.created.planId(),V3));
        try(var command=h.service.reserveCommand(h.base.lease,h.created.planId(),V2)){assertTrue(command.live());}
    }
    @Test void wrongReservationVersionDoesNotAllocatePermit() {
        var h=new PlanLifecycleTest.Harness();var mutation=h.mutation();
        missing(()->h.service.reserve(h.base.lease,h.created.planId(),mutation,V3));
        assertTrue(h.service.reserve(h.base.lease,h.created.planId(),mutation,V2).operationId().isPresent());
    }
    @Test void wrongCredentialVersionDoesNotConsumeAttempt() {
        var h=new PlanLifecycleTest.Harness();String id=h.reserve();
        missing(()->h.service.claimCredentials(h.base.lease,id,V3));
        try(var claim=h.service.claimCredentials(h.base.lease,id,V2)){assertFalse(claim.cancelled());}
    }
    @Test void wrongOperationVersionDoesNotExpireOrCancelReservation() {
        var h=new PlanLifecycleTest.Harness();String id=h.reserve();h.nanos.set(61_000_000_000L);
        missing(()->h.service.status(h.base.lease,id,V3));missing(()->h.service.cancel(h.base.lease,id,V3));assertEquals(0,h.closes.get());
        assertEquals(HostedPlanService.Phase.EXPIRED,h.service.status(h.base.lease,id,V2).phase());assertEquals(1,h.closes.get());
    }
    @Test void nullExpectedVersionIsExplicitlyInvalidForEveryPort() {
        var h=new PlanLifecycleTest.Harness();var view=h.service.view(h.base.lease,Optional.empty());String id=h.reserve();
        var calls=List.<Runnable>of(()->h.service.requireOwned(h.base.lease,h.created.planId(),null),
            ()->h.service.view(h.base.lease,Optional.empty(),null),()->h.service.verifySummary(h.base.lease,view,null),
            ()->h.service.reserveView(h.base.lease,h.created.planId(),null),()->h.service.reserveCommand(h.base.lease,h.created.planId(),null),
            ()->h.service.reserve(h.base.lease,h.created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()),null),
            ()->h.service.claimCredentials(h.base.lease,id,null),()->h.service.status(h.base.lease,id,null),()->h.service.cancel(h.base.lease,id,null));
        for(var call:calls)assertEquals(PlanRefusal.Code.INVALID_REQUEST,assertThrows(PlanRefusal.class,call::run).code());
    }
}
