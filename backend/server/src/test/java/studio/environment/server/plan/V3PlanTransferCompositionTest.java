package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import studio.environment.core.plan.*;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.workspace.WorkspaceRuntime;

class V3PlanTransferCompositionTest {
    @Test void serviceRefusalStillCancelsRegistryAndBothRemainOwnedUntilTheirWorkersClose() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var plan=f.create(false);
        var runtime=new PlanRuntime(f.service,List.of(),(owner,id)->true);var registry=runtime.transfers();assertSame(registry,runtime.transfers());
        var record=registry.admitCredentials(f.lease);var read=f.service.reserveView(f.lease,plan.planId());
        var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        try {
            assertEquals(PlanRefusal.Code.CLEANUP_INCONCLUSIVE,assertThrows(PlanRefusal.class,()->runtime.cleanup(f.lease)).code());
            assertTrue(record.cancelled());
            // This fixture keeps its authority port live; cleanup retains the original reader until close.
            assertTrue(read.live());assertTrue(runtime.awaitingCleanupWork(f.lease));
        } finally {read.close();record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
        assertFalse(runtime.awaitingCleanupWork(f.lease));runtime.cleanup(f.lease);
    }
    @Test void registryRefusalDoesNotSkipSynchronousPhysicalReservationClosure() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var plan=f.create(false);
        f.service.reserve(f.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
        var runtime=new PlanRuntime(f.service,List.of(),(owner,id)->true);var record=runtime.transfers().admitCredentials(f.lease);
        var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        try {
            assertThrows(PlanRefusal.class,()->runtime.cleanup(f.lease));assertEquals(1,f.closes.get());
            assertTrue(record.cancelled());assertTrue(runtime.awaitingCleanupWork(f.lease));
        } finally {record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
        runtime.cleanup(f.lease);assertFalse(runtime.awaitingCleanupWork(f.lease));assertEquals(1,f.closes.get());
    }
    @Test void disabledServiceStillOwnsItsTransferRegistry() {
        var environment=new MockEnvironment();var workspace=new WorkspaceRuntime(environment,RuntimeMode.DEMO);
        var runtime=new PlanRuntime(environment,RuntimeMode.DEMO,workspace,new DefaultListableBeanFactory().getBeanProvider(HostedSessions.class));
        assertFalse(runtime.inspectionApiConfigured());
        var lease=new studio.environment.core.session.SessionLedger.Lease("disabled-mock",new studio.environment.core.session.Owner("https://disabled.invalid","owner"),java.time.Instant.MAX);
        var record=runtime.transfers().admitCredentials(lease);assertTrue(runtime.awaitingCleanupWork(lease));
        assertThrows(PlanRefusal.class,()->runtime.cleanup(lease));assertTrue(record.cancelled());
        record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,new HostedSessions(Clock.systemUTC(),List.of()));
        assertFalse(runtime.awaitingCleanupWork(lease));runtime.cleanup(lease);
    }
    @Test void exhaustedHttpRecordsDoNotConsumePhysicalCredentialAttemptOrCreatePermits() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var plan=f.create(true);
        var runtime=new PlanRuntime(f.service,List.of(),(owner,id)->true);var registry=runtime.transfers();
        var records=new ArrayList<V3PlanTransfers.Operation>();var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        try {
            for(int i=0;i<4;i++)records.add(registry.admitCredentials(f.lease));
            for(int i=0;i<4;i++)records.add(registry.admitMetadata(f.lease));
            assertEquals(0,f.reservations.get());
            String operation=f.service.reserve(f.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())).operationId().orElseThrow();
            f.service.requireOperationOwned(f.lease,operation,PlanDefinition.Version.V3);
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->registry.admitCredentials(f.lease)).code());
            assertEquals(1,f.reservations.get());assertEquals(0,f.closes.get());
            try(var claim=f.service.claimCredentials(f.lease,operation,PlanDefinition.Version.V3)){assertFalse(claim.cancelled());}
            assertEquals(1,f.closes.get());
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->registry.admitCredentials(f.lease)).code());
        } finally {records.forEach(record->record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions));}
    }
}
