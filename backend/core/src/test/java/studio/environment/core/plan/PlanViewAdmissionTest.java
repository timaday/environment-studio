package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class PlanViewAdmissionTest {
    @Test void admissionPinsRevisionAndRetainsScratchUntilResponseWorkerReturns() throws Exception {
        var h=new PlanLifecycleTest.Harness(); h.inspect();
        var admission=h.service.reserveView(h.base.lease,h.created.planId());
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var result=executor.submit(()->admission.run(()->{
                admission.pin("2"); admission.verify(); entered.countDown(); PlanLifecycleTest.await(release); return true;
            }));
            try {
                PlanLifecycleTest.await(entered); admission.close();
                assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->h.service.reserveCommand(h.base.lease,h.created.planId())).code());
            } finally { release.countDown(); }
            assertTrue(result.get(5,TimeUnit.SECONDS));
        }
        try(var next=h.service.reserveView(h.base.lease,h.created.planId())) { next.run(()->{next.pin("2"); next.verify();return true;}); }
    }
    @Test void discardedAndExpiredEvidenceCannotPassFinalResponseCheck() {
        var h=new PlanLifecycleTest.Harness(); h.inspect();
        try(var admission=h.service.reserveView(h.base.lease,h.created.planId())) {
            admission.run(()->{
                admission.pin("2"); h.service.discard(h.base.lease,h.created.planId(),h.mutation());
                assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,admission::verify).code()); return true;
            });
        }
        var second=new PlanLifecycleTest.Harness(); second.inspect();
        try(var admission=second.service.reserveView(second.base.lease,second.created.planId())) {
            admission.run(()->{admission.pin("2");second.base.time.now=second.base.time.now.plusSeconds(1800);
                assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,admission::verify).code());return true;});
        }
    }
    @Test void failedReinspectionInvalidatesPreparedResponseEvenWithoutRevisionChange() {
        var h=new PlanLifecycleTest.Harness();h.inspect();
        try(var admission=h.service.reserveView(h.base.lease,h.created.planId())) {
            admission.run(()->{admission.pin("2");h.reject.set(true);h.inspect();
                assertEquals("2",h.service.summary(h.base.lease,h.created.planId()).revision());
                assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,admission::verify).code());return true;});
        }
    }
}
