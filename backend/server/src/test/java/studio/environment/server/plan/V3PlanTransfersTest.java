package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.session.*;
import studio.environment.server.session.HostedSessions;

class V3PlanTransfersTest {
    final SessionLedger.Lease lease=new SessionLedger.Lease("invented-transfer",new Owner("https://transfers.invalid","owner"),Instant.MAX);
    final HostedSessions sessions=new HostedSessions(Clock.systemUTC(),List.of());
    @Test void metadataUsesTheExistingFourSlotsAndSettlesExactlyOnce(){
        var registry=new V3PlanTransfers();var held=new ArrayList<PlanController.MetadataSlot>();
        V3PlanTransfers.Operation admitted=null;
        try{
            for(int i=0;i<3;i++)held.add(PlanController.metadata());
            admitted=assertDoesNotThrow(()->registry.admitMetadata(lease));
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,PlanController::metadata).code());
            admitted.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
            admitted.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
            held.add(PlanController.metadata());
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,PlanController::metadata).code());
        }finally{if(admitted!=null)admitted.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);held.forEach(PlanController.MetadataSlot::close);}
    }
    @Test void credentialRecordsHaveTheirOwnFourBoundAndRetainProgress(){
        var registry=new V3PlanTransfers();var records=new ArrayList<V3PlanTransfers.Operation>();
        try{
            for(int i=0;i<4;i++)records.add(assertDoesNotThrow(()->registry.admitCredentials(lease)));
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->registry.admitCredentials(lease)).code());
            records.getFirst().settlement(OwnedAsyncCompletion.Outcome.IN_PROGRESS,sessions);
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->registry.admitCredentials(lease)).code());
            assertTrue(registry.awaitingWork(lease));
        }finally{records.forEach(record->record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions));}
        assertFalse(registry.awaitingWork(lease));
    }
    @Test void fourMetadataAndFourCredentialRecordsRemainIndependent() {
        var registry=new V3PlanTransfers();var records=new ArrayList<V3PlanTransfers.Operation>();
        try {
            for(int i=0;i<4;i++)records.add(registry.admitMetadata(lease));
            for(int i=0;i<4;i++)records.add(registry.admitCredentials(lease));
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->registry.admitMetadata(lease)).code());
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->registry.admitCredentials(lease)).code());
            records.getLast().settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
            records.add(registry.admitCredentials(lease));
            assertThrows(PlanRefusal.class,PlanController::metadata);
        } finally {records.forEach(record->record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions));}
        assertFalse(registry.awaitingWork(lease));
    }
    @Test void terminalMetadataUncertaintyRetainsItsExactSlotAndCannotBeClearedByLateSuccess() throws Exception {
        var registry=new V3PlanTransfers();var operation=registry.admitMetadata(lease);
        var field=operation.getClass().getDeclaredField("slot");field.setAccessible(true);
        var exactFixtureSlot=(PlanController.MetadataSlot)field.get(operation);
        var other=new ArrayList<PlanController.MetadataSlot>();
        try {
            operation.settlement(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,sessions);
            operation.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
            operation.settlement(OwnedAsyncCompletion.Outcome.IN_PROGRESS,sessions);
            assertTrue(operation.cancelled());assertTrue(registry.awaitingWork(lease));
            for(int i=0;i<3;i++)other.add(PlanController.metadata());
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,PlanController::metadata).code());
            assertThrows(PlanRefusal.class,()->registry.invalidate(lease));
        } finally {
            other.forEach(PlanController.MetadataSlot::close);
            // Only restore this synthetic test's exact acquired slot; this is not production recovery.
            exactFixtureSlot.close();
        }
        assertTrue(registry.awaitingWork(lease));assertTrue(operation.cancelled());
    }
    @Test void cancellationAndStaleSettlementRemainExactLeaseAndRecordScoped() {
        var registry=new V3PlanTransfers();var other=new SessionLedger.Lease("other",lease.owner(),Instant.MAX);
        var first=registry.admitCredentials(lease);var second=registry.admitCredentials(other);
        assertThrows(PlanRefusal.class,()->registry.invalidate(lease));
        assertTrue(first.cancelled());assertFalse(second.cancelled());
        first.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
        first.settlement(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,sessions);
        first.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
        assertFalse(registry.awaitingWork(lease));assertTrue(registry.awaitingWork(other));assertFalse(second.cancelled());
        second.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);assertFalse(registry.awaitingWork(other));
    }
    @Test void helperCallbackCannotReleaseRecordBeforeWorkerCloses() throws Exception {
        var registry=new V3PlanTransfers();var operation=registry.admitCredentials(lease);
        var context=new OwnedAsyncCompletionTest.Context();
        var completion=new OwnedAsyncCompletion(context.context,result->operation.settlement(result,sessions));
        context.listener.onTimeout(context.event());assertTrue(registry.awaitingWork(lease));assertEquals(1,context.calls.get());
        completion.workerClosed();assertFalse(registry.awaitingWork(lease));assertEquals(1,context.calls.get());
    }
    @Test void lastSameLeaseNotificationIsOutsideRegistryLockAndIntermediateClosuresSpendNoRetry() throws Exception {
        var registry=new V3PlanTransfers();var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var hosted=new HostedSessions(Clock.systemUTC(),List.of(registry,original->{
            if(registry.awaitingWork(original))throw new IllegalStateException("MOCK_PENDING");
            entered.countDown();try{if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("MOCK_TIMEOUT");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("MOCK_INTERRUPTED");}
        }));
        var request=new org.springframework.mock.web.MockHttpServletRequest();assertTrue(hosted.reserveLogin(request));Instant now=Instant.now();
        var principal=new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(List.of(),new org.springframework.security.oauth2.core.oidc.OidcIdToken("mock-transfer-token",now,now.plusSeconds(600),Map.of("iss","https://transfer.invalid","sub","owner")));
        var original=assertInstanceOf(SessionLedger.Accepted.class,hosted.authenticated(request.getSession(),principal)).lease();request.setAttribute(HostedSessions.REQUEST_LEASE,original);
        var first=registry.admitCredentials(original);var last=registry.admitCredentials(original);hosted.logout(request);
        first.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,hosted);
        assertEquals(1,hosted.cleanupReports().getFirst().attempts());
        var result=new java.util.concurrent.FutureTask<Void>(()->{last.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,hosted);return null;});
        Thread worker=new Thread(result);worker.start();var executor=java.util.concurrent.Executors.newSingleThreadExecutor();V3PlanTransfers.Operation unrelated=null;
        try {
            assertTrue(entered.await(1,java.util.concurrent.TimeUnit.SECONDS));
            unrelated=assertDoesNotThrow(()->executor.submit(()->registry.admitCredentials(lease)).get(500,java.util.concurrent.TimeUnit.MILLISECONDS));
        } finally {release.countDown();worker.join(1500);executor.shutdownNow();if(unrelated!=null)unrelated.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
        assertFalse(worker.isAlive());result.get();assertTrue(hosted.cleanupReports().isEmpty());
    }
    @Test void terminalCredentialRecordRemainsCountedAfterOtherRecordsRecycle() {
        var registry=new V3PlanTransfers();
        var uncertain=registry.admitCredentials(lease);
        uncertain.settlement(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,sessions);
        uncertain.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
        for(int cycle=0;cycle<2;cycle++) {
            var records=new ArrayList<V3PlanTransfers.Operation>();
            try {
                for(int i=0;i<3;i++)records.add(registry.admitCredentials(lease));
                assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->registry.admitCredentials(lease)).code());
            } finally {records.forEach(record->record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions));}
        }
        assertTrue(registry.awaitingWork(lease));assertTrue(uncertain.cancelled());
    }

}
