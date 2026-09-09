package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.observation.ObservationPort;

/** Actual XML and shared ownership; only publication/observation admission is a mock witness. */
class SharedV3PlanValidationAuthorityTest {
    @Test void everyValidationRechecksTheEntireSelectedPublication() {
        var fixture=new SharedV3PlanXmlTest();var service=fixture.service();String plan=fixture.inspected(service);
        service.validateV3(fixture.lease,plan,"2");
        fixture.definitionLookup=p->{throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);};
        refused(PlanRefusal.Code.UNSUPPORTED_DEFINITION,()->service.validateV3(fixture.lease,plan,"2"));
        fixture.definitionLookup=p->new PublishedDefinition(p.reference(),p.publicationDigest()+"-changed",p.model(),p.policies());
        refused(PlanRefusal.Code.UNSUPPORTED_DEFINITION,()->service.validateV3(fixture.lease,plan,"2"));
        fixture.definitionLookup=p->new PublishedDefinition(p.reference(),p.publicationDigest(),
                new PlanDefinition.V3(DerivedTargetInputAdapterTest.definition(true)),p.policies());
        refused(PlanRefusal.Code.UNSUPPORTED_DEFINITION,()->service.validateV3(fixture.lease,plan,"2"));
        fixture.definitionLookup=java.util.function.UnaryOperator.identity();
        assertFalse(service.validateV3(fixture.lease,plan,"2").targetComplete());
        refused(PlanRefusal.Code.CONFLICT,()->service.validateV3(fixture.lease,plan,"1"));
    }
    @Test void omittedTargetRuleProofRefusesAndCannotBeConfusedWithMissingTarget() {
        var fixture=new SharedV3PlanXmlTest();var adapter=new SharedV3PlanCompositionAuthorityTest.ControlledContent();
        var service=fixture.service(adapter);String plan=fixture.inspected(service);
        assertFalse(service.validateV3(fixture.lease,plan,"2").targetComplete());
        adapter.forge=true;assertTrue(service.materialize(fixture.lease,plan,"2").complete());
        refused(PlanRefusal.Code.PROJECTION_REFUSED,()->service.validateV3(fixture.lease,plan,"2"));
        assertTrue(service.summary(fixture.lease,plan).targetComplete());
    }
    @Test void failedReinspectionLeavesCurrentReadableButValidationRequiresInspection() {
        var fixture=new SharedV3PlanXmlTest();var service=fixture.service();String plan=fixture.inspected(service);
        service.validateV3(fixture.lease,plan,"2");
        failInspection(fixture,service,plan);
        refused(PlanRefusal.Code.INSPECTION_REQUIRED,()->service.validateV3(fixture.lease,plan,"2"));
        assertEquals(DerivedTargetInputAdapterTest.XML,service.comparison(fixture.lease,plan,"2",false,"sheet",ViewMode.RAW,true).text());
    }
    @Test void closingOriginalOwnerDuringFreshLookupOrProofPreventsLateSuccessAndEarlyScratchReuse() throws Exception {
        for(boolean lookup:List.of(false,true)) {
            var fixture=new SharedV3PlanXmlTest();var adapter=new SharedV3PlanCompositionAuthorityTest.ControlledContent();
            var service=fixture.service(adapter);String plan=fixture.inspected(service);
            var view=service.reserveView(fixture.lease,plan);var entered=new CountDownLatch(1);var released=new CountDownLatch(1);
            if(lookup)fixture.definitionLookup=p->{entered.countDown();await(released);return p;};else adapter.hold=true;
            var result=new AtomicReference<Object>();var failure=new AtomicReference<Throwable>();
            var original=new AtomicReference<ObservationPort.Cancellation>();
            var worker=new Thread(()->{try{result.set(view.run(()->{view.pin("2");
                return view.read((snapshot,control)->{original.set(control);return view.validationV3();});
            }));}catch(Throwable error){failure.set(error);}});worker.start();
            try {
                assertTrue((lookup?entered:adapter.entered).await(5,TimeUnit.SECONDS));
                if(!lookup)assertSame(original.get(),adapter.control.get());
                view.close();assertTrue(original.get().cancelled());
                refused(PlanRefusal.Code.CAPACITY,()->service.reserveView(fixture.lease,plan));
            }finally{released.countDown();adapter.released.countDown();worker.join(5000);view.close();}
            assertFalse(worker.isAlive());assertNull(result.get());
            assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.get()).code());
            fixture.definitionLookup=java.util.function.UnaryOperator.identity();adapter.hold=false;
            assertFalse(service.validateV3(fixture.lease,plan,"2").targetComplete());
        }
    }
    @Test void failedReinspectionWhileValidationProofIsHeldInvalidatesOriginalControl() throws Exception {
        var fixture=new SharedV3PlanXmlTest();var adapter=new SharedV3PlanCompositionAuthorityTest.ControlledContent();
        var service=fixture.service(adapter);String plan=fixture.inspected(service);adapter.hold=true;
        var result=new AtomicReference<Object>();var failure=new AtomicReference<Throwable>();
        var worker=new Thread(()->{try{result.set(service.validateV3(fixture.lease,plan,"2"));}catch(Throwable error){failure.set(error);}});
        worker.start();
        try{assertTrue(adapter.entered.await(5,TimeUnit.SECONDS));failInspection(fixture,service,plan);assertTrue(adapter.control.get().cancelled());}
        finally{adapter.released.countDown();worker.join(5000);}
        assertFalse(worker.isAlive());assertNull(result.get());
        assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.get()).code());
        refused(PlanRefusal.Code.INSPECTION_REQUIRED,()->service.validateV3(fixture.lease,plan,"2"));
    }
    private static void failInspection(SharedV3PlanXmlTest fixture,HostedPlanService service,String plan) {
        fixture.xml="<items><item id='one' tone='' finish='x'/></items>";
        var reserved=service.reserve(fixture.lease,plan,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()));
        var status=service.submit(fixture.lease,reserved.operationId().orElseThrow(),(user,password)->{user[0]='a';password[0]='X';return new CredentialLengths(1,1);});
        assertEquals(HostedPlanService.Phase.REFUSED,status.phase());assertEquals("2",service.summary(fixture.lease,plan).revision());
    }
    private static void await(CountDownLatch latch) {
        try{assertTrue(latch.await(5,TimeUnit.SECONDS));}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
    }
    private static void refused(PlanRefusal.Code code,org.junit.jupiter.api.function.Executable action){assertEquals(code,assertThrows(PlanRefusal.class,action).code());}
}
