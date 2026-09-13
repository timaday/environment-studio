package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanDefinition.Version.V2;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.observation.*;
import studio.environment.core.session.SessionLedger;

/** Independent inter-admission controls on the fixed version partition; invented witnesses only. */
class IndependentPlanVersionTest {
    static final class Fixture {
        final HostedPlanServiceTest base=new HostedPlanServiceTest();
        final AtomicReference<Runnable> between=new AtomicReference<>();
        final AtomicInteger polls=new AtomicInteger();
        final AtomicInteger permits=new AtomicInteger();
        final AtomicBoolean complete=new AtomicBoolean();
        final HostedPlanService service;
        Fixture() {
            var authority=new PlanPorts.Authority(){
                public <T> Optional<T> guard(SessionLedger.Lease lease,java.util.function.Supplier<T> transition){
                    var result=base.authority.guard(lease,transition);var hook=between.getAndSet(null);if(hook!=null)hook.run();return result;
                }
            };
            var cleanup=new ObservationResult.CleanupHandle(){
                public ObservationResult.Cleanup status(){polls.incrementAndGet();return complete.get()?ObservationResult.Cleanup.COMPLETE:ObservationResult.Cleanup.INCONCLUSIVE;}
                public ObservationResult.Cleanup retry(){return status();}
                public void cancel(){}
            };
            var port=new ObservationPort(){
                public ObservationResult observe(Selection selected,TransientCredentials credentials,Cancellation control){throw new AssertionError("PERMIT_REQUIRED");}
                public Reservation reserve(Selection selected){permits.incrementAndGet();return new Reservation.Admitted(new Permit(){
                    public ObservationResult observe(TransientCredentials credentials,Cancellation control){credentials.close();return new ObservationResult.Refused(ObservationResult.Code.DATABASE_FAILURE,ObservationResult.Cleanup.INCONCLUSIVE,Optional.of(cleanup));}
                    public void close(){}
                });}
            };
            service=new HostedPlanService(authority,base.workspace,Map.of("destination",new PlanPorts.Destination("destination",studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,port)),base.content,System::nanoTime);
        }
        HostedPlanService.Ack create(){return service.create(base.lease,UUID.randomUUID().toString(),base.ref,"invented-binding","destination");}
    }
    @Test void currentSelectionCannotReplaceTheMatchedIdEvenWithAnotherV2Plan() {
        var f=new Fixture();var original=f.create();var replacement=new AtomicReference<String>();
        f.between.set(()->{
            f.service.discard(f.base.lease,original.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
            replacement.set(f.create().planId());
        });
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->f.service.view(f.base.lease,Optional.empty(),V2)).code());
        assertNotNull(replacement.get());assertNotEquals(original.planId(),replacement.get());
        assertEquals(replacement.get(),f.service.view(f.base.lease,Optional.empty(),V2).planId());
    }
    @Test void versionPreflightCannotAuthorizeCleanupPollingAfterOriginalLeaseRevocation() {
        var f=new Fixture();var created=f.create();
        String operation=f.service.reserve(f.base.lease,created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()),V2).operationId().orElseThrow();
        f.service.submit(f.base.lease,operation,(user,password)->{user[0]='m';password[0]='p';return new PlanPorts.CredentialLengths(1,1);});
        int before=f.polls.get();f.between.set(()->f.base.authority.close(f.base.lease.id()));
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->f.service.status(f.base.lease,operation,V2)).code());
        assertEquals(before,f.polls.get(),"The final original-lease check must precede the external cleanup callback");
        f.complete.set(true);assertDoesNotThrow(()->f.service.invalidate(f.base.lease));
        assertTrue(f.polls.get()>before);
    }
    @Test void wrongVersionCannotAllocateAPermitBeforeRefusingTheReservation() {
        var f=new Fixture();var created=f.create();var command=new HostedPlanService.Mutation("1",UUID.randomUUID().toString());
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->f.service.reserve(f.base.lease,created.planId(),command,PlanDefinition.Version.V3)).code());
        assertEquals(0,f.permits.get());
        var accepted=f.service.reserve(f.base.lease,created.planId(),command,V2);assertEquals(1,f.permits.get());
        assertEquals(accepted,f.service.reserve(f.base.lease,created.planId(),command,V2));assertEquals(1,f.permits.get());
        f.service.cancel(f.base.lease,accepted.operationId().orElseThrow(),V2);
    }
}
