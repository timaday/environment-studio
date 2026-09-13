package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanDefinition.Version.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.observation.ObservationResult;

class PlanV3MetadataTest {
    @Test void smallV3SummaryRefusesAV2PlanBeforeExpiringItsReservation() {
        var f=new PlanLifecycleTest.Harness();f.reserve();f.nanos.set(61_000_000_000L);
        for(var id:List.of(Optional.<String>empty(),Optional.of(f.created.planId())))
            assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->f.service.viewV3(f.base.lease,id)).code());
        assertEquals(0,f.closes.get());
        assertEquals(f.created.planId(),f.service.view(f.base.lease,Optional.empty(),V2).planId());assertEquals(1,f.closes.get());
    }
    @Test void pureOperationOwnershipNeitherConsumesNorExpiresTheOriginalReservation() {
        var f=new PlanLifecycleTest.Harness();String operation=f.reserve();
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->f.service.requireOperationOwned(f.base.lease,operation,V3)).code());
        f.service.requireOperationOwned(f.base.lease,operation,V2);assertEquals(0,f.closes.get());
        try(var claim=f.service.claimCredentials(f.base.lease,operation,V2)){assertFalse(claim.cancelled());}
        var other=new PlanLifecycleTest.Harness();String old=other.reserve();other.nanos.set(61_000_000_000L);
        other.service.requireOperationOwned(other.base.lease,old,V2);assertEquals(0,other.closes.get());
        assertEquals(HostedPlanService.Phase.EXPIRED,other.service.status(other.base.lease,old,V2).phase());assertEquals(1,other.closes.get());
        other.service.requireOperationOwned(other.base.lease,old,V2);
    }
    @Test void pureOperationPreflightNeverPollsPendingCleanupAndRetainsAllAuthorityChecks() {
        var f=new PlanVersionOperationTest.Harness();var polls=new java.util.concurrent.atomic.AtomicInteger();
        f.cleanup=new ObservationResult.CleanupHandle(){
            public ObservationResult.Cleanup status(){polls.incrementAndGet();return ObservationResult.Cleanup.INCONCLUSIVE;}
            public ObservationResult.Cleanup retry(){throw new AssertionError("UNEXPECTED_RETRY");}
            public void cancel(){throw new AssertionError("UNEXPECTED_CANCEL");}
        };
        var created=f.create(V3,UUID.randomUUID().toString());
        String id=f.service.reserve(f.lease(),created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()),V3).operationId().orElseThrow();
        f.service.submit(f.lease(),id,PlanLifecycleTest::credentials);int before=polls.get();
        f.service.requireOperationOwned(f.lease(),id,V3);assertEquals(before,polls.get());
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->f.service.requireOperationOwned(f.lease(),id,V2)).code());
        assertEquals(PlanRefusal.Code.INVALID_REQUEST,assertThrows(PlanRefusal.class,()->f.service.requireOperationOwned(f.lease(),id,null)).code());
        var foreign=f.fixture.lease("foreign-metadata");
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->f.service.requireOperationOwned(foreign,id,V3)).code());
        f.fixture.fixture.authority.close(f.lease().id());
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->f.service.requireOperationOwned(f.lease(),id,V3)).code());
        assertEquals(before,polls.get());
    }
    @Test void pureTerminalOwnershipUsesOriginalVersionAfterOtherVersionReplacement() {
        for(var original:List.of(V2,V3)) {
            var f=new PlanVersionOperationTest.Harness();var created=f.create(original,UUID.randomUUID().toString());
            String id=f.service.reserve(f.lease(),created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()),original).operationId().orElseThrow();
            f.service.cancel(f.lease(),id,original);f.service.discard(f.lease(),created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
            var other=original==V2?V3:V2;f.create(other,UUID.randomUUID().toString());
            f.service.requireOperationOwned(f.lease(),id,original);
            assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->f.service.requireOperationOwned(f.lease(),id,other)).code());
        }
    }
}
