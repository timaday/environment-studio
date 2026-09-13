package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanDefinition.Version.*;
import static studio.environment.core.plan.PlanPorts.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.observation.*;
import studio.environment.core.session.*;

/** Shared-owner witnesses only; no publication/compiler qualification is asserted. */
class PlanVersionOperationTest {
    static final class Harness {
        final SharedV3PlanLifecycleTest fixture=new SharedV3PlanLifecycleTest();
        final AtomicLong nanos=new AtomicLong();
        final AtomicInteger permits=new AtomicInteger(),closes=new AtomicInteger();
        volatile ObservationResult.CleanupHandle cleanup;
        final AtomicBoolean pause=new AtomicBoolean();
        final CountDownLatch matched=new CountDownLatch(1),resume=new CountDownLatch(1);
        final HostedPlanService service;
        Harness() {
            var authority=new Authority(){public <T> Optional<T> guard(SessionLedger.Lease lease,java.util.function.Supplier<T> action) {
                var result=fixture.fixture.authority.guard(lease,action);
                if(pause.compareAndSet(true,false)){matched.countDown();PlanLifecycleTest.await(resume);}
                return result;
            }};
            var port=new ObservationPort(){
                public ObservationResult observe(Selection ignored,TransientCredentials credentials,Cancellation cancel){throw new AssertionError("PERMIT_REQUIRED");}
                public Reservation reserve(Selection selected){return permit();}
                public Reservation reserveV3(V3Selection selected){return permit();}
                private Reservation permit(){permits.incrementAndGet();return new Reservation.Admitted(new Permit(){
                    public ObservationResult observe(TransientCredentials credentials,Cancellation cancel){credentials.close();return cleanup==null?
                            new ObservationResult.Refused(ObservationResult.Code.DATABASE_FAILURE,ObservationResult.Cleanup.COMPLETE):
                            new ObservationResult.Refused(ObservationResult.Code.CLEANUP_INCONCLUSIVE,ObservationResult.Cleanup.INCONCLUSIVE,Optional.of(cleanup));}
                    public void close(){closes.incrementAndGet();}
                });}
            };
            service=new HostedPlanService(authority,fixture.workspace(),Map.of("destination",new Destination("destination",studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,port)),fixture.fixture.content,nanos::get);
        }
        SessionLedger.Lease lease(){return fixture.fixture.lease;}
        HostedPlanService.Ack create(PlanDefinition.Version version,String request){return create(lease(),version,request);}
        HostedPlanService.Ack create(SessionLedger.Lease lease,PlanDefinition.Version version,String request){return version==V3?
                service.createV3(lease,request,fixture.fixture.ref,"mock-binding","destination"):
                service.create(lease,request,fixture.fixture.ref,"invented-binding","destination");}
    }
    static void missing(Runnable action){PlanVersionAdmissionTest.missing(action);}
    static PlanCommand discard(){return new PlanCommand(new HostedPlanService.Mutation("1",UUID.randomUUID().toString()),new PlanCommand.Action.Discard());}
    @Test void retiredV2MetadataStaysV2AfterV3Replacement(){retired(V2,V3);}
    @Test void retiredV3MetadataStaysV3AfterV2Replacement(){retired(V3,V2);}
    private void retired(PlanDefinition.Version before,PlanDefinition.Version after) {
        var h=new Harness();String createRequest=UUID.randomUUID().toString();var old=h.create(before,createRequest);
        var reservation=new HostedPlanService.Mutation("1",UUID.randomUUID().toString());var reserved=h.service.reserve(h.lease(),old.planId(),reservation,before);
        String operation=reserved.operationId().orElseThrow();
        assertEquals(HostedPlanService.Phase.CANCELLED,h.service.cancel(h.lease(),operation,before).phase());
        var command=discard();HostedPlanService.Ack discarded;
        try(var owner=h.service.reserveCommand(h.lease(),old.planId(),before)){discarded=owner.execute(command);}
        var fresh=h.create(after,UUID.randomUUID().toString());
        assertEquals(old,h.create(before,createRequest));
        h.service.requireOwned(h.lease(),old.planId(),before);missing(()->h.service.requireOwned(h.lease(),old.planId(),after));
        assertEquals(reserved,h.service.reserve(h.lease(),old.planId(),reservation,before));
        missing(()->h.service.reserve(h.lease(),old.planId(),reservation,after));
        try(var owner=h.service.reserveCommand(h.lease(),old.planId(),before)){assertEquals(discarded,owner.execute(command));}
        try(var owner=h.service.reserveCommand(h.lease(),old.planId(),before)){missing(()->owner.execute(discard()));}
        missing(()->h.service.reserveCommand(h.lease(),old.planId(),after));
        assertEquals(HostedPlanService.Phase.CANCELLED,h.service.status(h.lease(),operation,before).phase());
        assertEquals(HostedPlanService.Phase.CANCELLED,h.service.cancel(h.lease(),operation,before).phase());
        missing(()->h.service.status(h.lease(),operation,after));missing(()->h.service.cancel(h.lease(),operation,after));
        missing(()->h.service.claimCredentials(h.lease(),operation,after));
        missing(()->h.service.view(h.lease(),Optional.empty(),before));
        assertEquals(fresh.planId(),h.service.view(h.lease(),Optional.empty(),after).planId());
        missing(()->h.service.view(h.lease(),Optional.of(old.planId()),before));
        assertEquals(1,h.permits.get());
    }
    @Test void wrongVersionNeverPollsOrCancelsOutstandingCleanupHandle() {
        var h=new Harness();var checks=new AtomicInteger();var cancels=new AtomicInteger();var complete=new AtomicBoolean();
        h.cleanup=new ObservationResult.CleanupHandle(){
            public ObservationResult.Cleanup status(){checks.incrementAndGet();return complete.get()?ObservationResult.Cleanup.COMPLETE:ObservationResult.Cleanup.INCONCLUSIVE;}
            public void cancel(){cancels.incrementAndGet();}
            public ObservationResult.Cleanup retry(){return status();}
        };
        var plan=h.create(V3,UUID.randomUUID().toString());var reserved=h.service.reserve(h.lease(),plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()),V3);
        String id=reserved.operationId().orElseThrow();
        try(var submission=h.service.claimCredentials(h.lease(),id,V3)){submission.process(PlanLifecycleTest::credentials);}
        int priorChecks=checks.get(),priorCancels=cancels.get();
        missing(()->h.service.status(h.lease(),id,V2));missing(()->h.service.cancel(h.lease(),id,V2));
        missing(()->h.service.claimCredentials(h.lease(),id,V2));
        assertEquals(priorChecks,checks.get());assertEquals(priorCancels,cancels.get());
        h.service.cancel(h.lease(),id,V3);assertTrue(cancels.get()>priorCancels);
        complete.set(true);assertEquals(HostedPlanService.Cleanup.COMPLETE,h.service.status(h.lease(),id,V3).cleanup());
    }
    @Test void currentVersionSelectionPinsMatchedIdAcrossReplacement()throws Exception {
        var h=new Harness();var old=h.create(V2,UUID.randomUUID().toString());
        h.pause.set(true);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var result=executor.submit(()->h.service.view(h.lease(),Optional.empty(),V2));
            try {
                assertTrue(h.matched.await(3,TimeUnit.SECONDS));
                h.service.discard(h.lease(),old.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
                h.create(V3,UUID.randomUUID().toString());
            }finally{h.resume.countDown();}
            var refusal=assertThrows(ExecutionException.class,()->result.get(3,TimeUnit.SECONDS));
            assertEquals(PlanRefusal.Code.NOT_FOUND,assertInstanceOf(PlanRefusal.class,refusal.getCause()).code());
        }
    }
    @Test void originalLeaseAndForeignOwnershipGateAllRetainedVersions() {
        var h=new Harness();var old=h.create(V3,UUID.randomUUID().toString());
        var foreign=h.fixture.lease("foreign-version");
        missing(()->h.service.requireOwned(foreign,old.planId(),V3));
        h.fixture.fixture.authority.close(h.lease().id());
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->h.service.requireOwned(h.lease(),old.planId(),V3)).code());
        var again=((SessionLedger.Accepted)h.fixture.fixture.authority.admit("same-owner-new-lease",h.lease().owner())).lease();
        missing(()->h.service.requireOwned(again,old.planId(),V3));
    }
    @Test void mixedVersionsStillShareFourPlansAndCreationReplayDomains() {
        var h=new Harness();String request=UUID.randomUUID().toString();h.create(V2,request);
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->h.create(V3,request)).code());
        for(int i=0;i<3;i++)h.create(h.fixture.lease("version-capacity-"+i),i%2==0?V3:V2,UUID.randomUUID().toString());
        var extra=h.fixture.lease("version-capacity-extra");
        assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->h.create(extra,V3,UUID.randomUUID().toString())).code());
        assertEquals(0,h.permits.get());
    }
}
