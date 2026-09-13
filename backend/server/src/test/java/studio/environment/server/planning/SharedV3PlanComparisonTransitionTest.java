package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.XML;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;

/** Independently invented XML; hold real comparison output across observation transitions. */
class SharedV3PlanComparisonTransitionTest {
    @Test void pendingReservationChangesDirectComparisonContext() throws Exception { transition(false,false,false); }
    @Test void successfulReplacementSignalsOriginalDirectWork() throws Exception { transition(false,true,false); }
    @Test void failedReplacementSignalsViewBeforeClose() throws Exception { transition(true,true,true); }
    @Test void repeatedFailedInspectionCannotRestoreAnOldComparison() throws Exception { transition(false,true,true); }

    @Test void cancelledReservationSignalsHeldDirectWork() throws Exception { cancelledOrExpired(false); }
    @Test void expiredReservationSignalsHeldDirectWork() throws Exception { cancelledOrExpired(true); }
    private static void cancelledOrExpired(boolean expiry) throws Exception {
        var fixture=new SharedV3PlanXmlTest();var adapter=new SharedV3PlanComparisonTest.HeldComparison();
        var clock=new java.util.concurrent.atomic.AtomicLong(1);
        var service=fixture.service(adapter,clock::get);String id=fixture.inspected(service);
        var result=new AtomicReference<DocumentView>();var failure=new AtomicReference<Throwable>();
        var worker=new Thread(()->{try {result.set(service.comparison(fixture.lease,id,"2",false,"sheet",ViewMode.RAW,true));}
            catch(Throwable refused){failure.set(refused);}});
        worker.start();
        try {
            assertTrue(adapter.entered.await(5,TimeUnit.SECONDS));String operation=reserve(fixture,service,id);
            HostedPlanService.Status status;
            if(expiry){clock.addAndGet(TimeUnit.MINUTES.toNanos(10));status=service.status(fixture.lease,operation);}
            else status=service.cancel(fixture.lease,operation);
            assertEquals(expiry?HostedPlanService.Phase.EXPIRED:HostedPlanService.Phase.CANCELLED,status.phase());
            assertTrue(adapter.control.get().cancelled());
        } finally {adapter.released.countDown();worker.join(5000);}
        assertFalse(worker.isAlive());assertNull(result.get());
        assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.get()).code());
    }

    private static void transition(boolean admitted,boolean complete,boolean fail) throws Exception {
        var fixture=new SharedV3PlanXmlTest();
        var adapter=new SharedV3PlanComparisonTest.HeldComparison();
        var service=fixture.service(adapter);String id=fixture.inspected(service);
        if(fail && !admitted) {
            fixture.xml="<items><item id='one' tone='' finish='x'/></items>";
            assertEquals(HostedPlanService.Phase.REFUSED,submit(fixture,service,reserve(fixture,service,id)).phase());
        }
        var result=new AtomicReference<DocumentView>();var failure=new AtomicReference<Throwable>();
        var view=admitted?service.reserveView(fixture.lease,id):null;
        var worker=new Thread(()->{
            try { result.set(view==null?service.comparison(fixture.lease,id,"2",false,"sheet",ViewMode.RAW,true)
                    :view.run(()->{view.pin("2");return view.document(false,"sheet",ViewMode.RAW,true);})); }
            catch(Throwable refused){failure.set(refused);}
        });
        String operation=null;worker.start();
        try {
            assertTrue(adapter.entered.await(5,TimeUnit.SECONDS));
            fixture.xml=fail?"<items><item id='one' tone='' finish='x'/></items>":XML;
            operation=reserve(fixture,service,id);
            if(complete) {
                assertEquals(fail?HostedPlanService.Phase.REFUSED:HostedPlanService.Phase.SUCCEEDED,
                        submit(fixture,service,operation).phase());
                assertTrue(adapter.control.get().cancelled(),"must signal before caller closes view");
            }
        } finally {
            adapter.released.countDown();worker.join(5000);
            if(view!=null)view.close();
        }
        assertFalse(worker.isAlive());assertNull(result.get());
        assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.get()).code());
        if(!complete)service.cancel(fixture.lease,operation);
        if(fail) {
            // Stable retained current remains inspectable after failure without restoring target authority.
            assertEquals(XML,service.comparison(fixture.lease,id,"2",false,"sheet",ViewMode.RAW,true).text());
            assertFalse(service.summary(fixture.lease,id).inspectionValid());
        }
    }
    private static String reserve(SharedV3PlanXmlTest f,HostedPlanService service,String id) {
        return service.reserve(f.lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString())).operationId().orElseThrow();
    }
    private static HostedPlanService.Status submit(SharedV3PlanXmlTest f,HostedPlanService service,String operation) {
        return service.submit(f.lease,operation,(user,password)->{user[0]='a';password[0]='X';return new CredentialLengths(1,1);});
    }
}
