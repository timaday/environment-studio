package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.session.*;
import studio.environment.core.definitionv2.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.*;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.core.workspace.NativeCommand;
import static studio.environment.core.plan.PlanPorts.*;

class HostedPlanServiceTest {
    static final class Time extends Clock {
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    final Time time = new Time();
    final SessionLedger authority = new SessionLedger(time, ignored -> {});
    final SessionLedger.Lease lease = ((SessionLedger.Accepted)authority.admit("invented-lease", new Owner("https://invented.invalid", "owner-a"))).lease();
    final NativeCommand.Reference ref = new NativeCommand.Reference("00000000-0000-4000-8000-000000000001","2");
    final PublishedDefinition definition = definition();
    final Workspace workspace = new Workspace() {
        public PublishedDefinition definition(Owner owner, NativeCommand.Reference reference) { return definition; }
        public PublishedProfile profile(Owner owner, NativeCommand.Reference reference, PublishedDefinition definition) { throw new AssertionError("UNEXPECTED_PROFILE_READ"); }
    };
    final ContentAdapter content = new ContentAdapter() {
        public ContentResult project(PublishedDefinition definition,String binding,ObservationResult.Observation observed) { throw new AssertionError("UNEXPECTED_PROJECT"); }
        public ContentResult materialize(PublishedDefinition definition,String binding,Content current,Draft draft) { throw new AssertionError("UNEXPECTED_MATERIALIZE"); }
        public Capture capture(PublishedDefinition definition,String binding,Content current,ProfileCapture.Command command) { throw new AssertionError("UNEXPECTED_CAPTURE"); }
    };
    PublishedDefinition definition() {
        var binding = new Binding("invented-binding",Engine.POSTGRESQL,Storage.TEXT,"invented","sample","key","xml",KeyType.INT64,List.of(new NativeDefinition.Document("sample","1",List.of())));
        var nativeDefinition = new NativeDefinition("invented",BigInteger.ONE,new Logical(List.of(),List.of(),List.of(),List.of()),List.of(binding));
        var ready = new NativeCompilationResult.ReadyToPublish(new NativeCompilationResult.Checked(nativeDefinition,"logical",Map.of(binding.id(),"binding-digest"),Map.of()));
        return new PublishedDefinition(ref,"publication-digest",ready,List.of());
    }
    HostedPlanService service() {
        return new HostedPlanService(authority::guard,workspace,Map.of("destination",new Destination("destination",Engine.POSTGRESQL,(selection,credentials,cancel)->{throw new AssertionError("UNEXPECTED_OBSERVE");})),content,System::nanoTime);
    }
    @Test void retiredLeaseCannotCreateOrReadEvenWithoutRunningCleanupHook() {
        var service = service();
        var created = service.create(lease,UUID.randomUUID().toString(),ref,"invented-binding","destination");
        authority.close(lease.id());
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->service.summary(lease,created.planId())).code());
        assertThrows(PlanRefusal.class,()->service.create(lease,UUID.randomUUID().toString(),ref,"invented-binding","destination"));
    }
    @Test void sameOwnerDifferentLeaseCannotReadAndOneLivePlanIsAtomic() {
        var service = service();
        var created = service.create(lease,UUID.randomUUID().toString(),ref,"invented-binding","destination");
        assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->service.create(lease,UUID.randomUUID().toString(),ref,"invented-binding","destination")).code());
        var other = ((SessionLedger.Accepted)authority.admit("other-lease",new Owner(lease.owner().issuer(),"other-owner"))).lease();
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->service.summary(other,created.planId())).code());
    }
    @Test void oneShotInspectionInstallsOnlySuccessfulCompleteProjection() {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var port=new ObservationPort() {
            public ObservationResult observe(Selection selection,TransientCredentials credentials,Cancellation cancel) { throw new AssertionError("MUST_USE_RESERVED_PERMIT"); }
            public Reservation reserve(Selection selection) {
                return new Reservation.Admitted(new Permit() {
                    public ObservationResult observe(TransientCredentials credentials,Cancellation cancel) {
                        calls.incrementAndGet(); credentials.close();
                        return new ObservationResult.Complete(new ObservationResult.Observation("fingerprint","logical","binding-digest",List.of(),Map.of()));
                    }
                    public void close() { }
                });
            }
        };
        var adapter=new ContentAdapter() {
            public ContentResult project(PublishedDefinition definition,String binding,ObservationResult.Observation observed) {
                return new ContentResult.Complete(new Content(List.of(),new studio.environment.core.graph.ObservedGraph(List.of(),List.of()),Map.of()));
            }
            public ContentResult materialize(PublishedDefinition definition,String binding,Content current,Draft draft) { throw new AssertionError(); }
            public Capture capture(PublishedDefinition definition,String binding,Content current,ProfileCapture.Command command) { throw new AssertionError(); }
        };
        var service=new HostedPlanService(authority::guard,workspace,Map.of("destination",new Destination("destination",Engine.POSTGRESQL,port)),adapter,System::nanoTime);
        var created=service.create(lease,UUID.randomUUID().toString(),ref,"invented-binding","destination");
        var command=new HostedPlanService.Mutation("1",UUID.randomUUID().toString());
        var reserved=service.reserve(lease,created.planId(),command);
        var status=service.submit(lease,reserved.operationId().orElseThrow(),(user,password)->{
            user[0]='a';password[0]='X';return new CredentialLengths(1,1);
        });
        assertEquals(HostedPlanService.Phase.SUCCEEDED,status.phase());
        assertEquals(Optional.of("2"),status.installedRevision());
        assertEquals(1,calls.get());
        assertTrue(service.summary(lease,created.planId()).inspectionValid());
        assertEquals(reserved,service.reserve(lease,created.planId(),command));
        assertEquals(PlanRefusal.Code.CREDENTIALS_ALREADY_CONSUMED,assertThrows(PlanRefusal.class,()->service.submit(lease,status.operationId(),(u,p)->{throw new AssertionError("MUST_NOT_READ_TWICE");})).code());
        assertEquals(1,calls.get());
    }

    @Test void draftCannotBeAcceptedWithoutCompleteInspection() {
        var service=service();
        var created=service.create(lease,UUID.randomUUID().toString(),ref,"invented-binding","destination");
        assertEquals(PlanRefusal.Code.INSPECTION_REQUIRED,assertThrows(PlanRefusal.class,()->service.replaceDraft(lease,created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()),Draft.empty())).code());
        assertEquals("1",service.summary(lease,created.planId()).revision());
    }

}
