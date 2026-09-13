package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.session.SessionLedger;

class PlanCleanupReadinessTest {
    @Test void idleStateIsNotWorkAndReadersRemainPendingUntilActuallyClosed() {
        var f = new PlanLifecycleTest.Harness();
        assertFalse(f.service.awaitingCleanupWork(f.base.lease));
        var read = f.service.reserveView(f.base.lease, f.created.planId());
        assertTrue(f.service.awaitingCleanupWork(f.base.lease));
        assertThrows(PlanRefusal.class, () -> f.service.invalidate(f.base.lease));
        assertTrue(f.service.awaitingCleanupWork(f.base.lease));
        read.close();
        assertFalse(f.service.awaitingCleanupWork(f.base.lease), "idle retained retired state must not suppress final invalidation");
        f.service.invalidate(f.base.lease); assertFalse(f.service.awaitingCleanupWork(f.base.lease));
    }
    @Test void expiredReservationCheckDoesNotExpireCloseOrClaimAnything() {
        var f = new PlanLifecycleTest.Harness(); String id = f.reserve();
        f.nanos.set(61_000_000_000L);
        for (int i=0;i<4;i++) assertFalse(f.service.awaitingCleanupWork(f.base.lease));
        assertEquals(0, f.closes.get()); assertEquals(0, f.opens.get());
        assertEquals(HostedPlanService.Phase.EXPIRED, f.service.status(f.base.lease,id).phase());
        assertEquals(1,f.closes.get()); assertFalse(f.service.awaitingCleanupWork(f.base.lease));
    }
    @Test void uncertainOperationQueriesNeverInvokeExternalCleanupHandles() {
        var f = new PlanLifecycleTest.Harness(); var polls = new AtomicInteger();
        f.handle = new ObservationResult.CleanupHandle() {
            public ObservationResult.Cleanup status(){polls.incrementAndGet();return ObservationResult.Cleanup.INCONCLUSIVE;}
            public ObservationResult.Cleanup retry(){throw new AssertionError("UNEXPECTED_RETRY");}
            public void cancel(){throw new AssertionError("UNEXPECTED_CANCEL");}
        };
        f.inspect(); int before=polls.get();
        for(int i=0;i<4;i++)assertTrue(f.service.awaitingCleanupWork(f.base.lease));
        assertEquals(before,polls.get());
        var foreign=new SessionLedger.Lease("different",f.base.lease.owner(),f.base.lease.absoluteExpiresAt());
        assertFalse(f.service.awaitingCleanupWork(foreign));
        var forged=new SessionLedger.Lease(f.base.lease.id(),f.base.lease.owner(),f.base.lease.absoluteExpiresAt().minusSeconds(1));
        assertThrows(PlanRefusal.class,()->f.service.awaitingCleanupWork(forged));
    }
    @Test void claimedSubmissionRetainsAsyncOwnershipUntilActuallyClosed() {
        var f=new PlanLifecycleTest.Harness();String id=f.reserve();
        var submission=f.service.claimCredentials(f.base.lease,id);
        assertTrue(f.service.awaitingCleanupWork(f.base.lease));
        submission.close();assertFalse(f.service.awaitingCleanupWork(f.base.lease));
        assertEquals(1,f.closes.get());
    }
    @Test void heldDirectRenderingIsPendingWithoutReadersOrCleanupCallbacks() throws Exception {
        var f=new PlanLifecycleTest.Harness();f.inspect();
        f.renderEntered=new java.util.concurrent.CountDownLatch(1);f.renderRelease=new java.util.concurrent.CountDownLatch(1);
        var result=new java.util.concurrent.FutureTask<>(()->f.service.materialize(f.base.lease,f.created.planId(),"2"));
        Thread worker=new Thread(result);worker.start();
        try {
            assertTrue(f.renderEntered.await(1,java.util.concurrent.TimeUnit.SECONDS));
            for(int i=0;i<4;i++)assertTrue(f.service.awaitingCleanupWork(f.base.lease));
            assertEquals(1,f.renders.get());
        } finally {f.renderRelease.countDown();worker.join(1500);}
        assertFalse(worker.isAlive());assertTrue(result.get().complete());
        assertFalse(f.service.awaitingCleanupWork(f.base.lease));
    }
}
