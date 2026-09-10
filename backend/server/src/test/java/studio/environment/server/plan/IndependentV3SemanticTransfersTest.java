package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.session.SessionLedger;

class IndependentV3SemanticTransfersTest {
    @Test void concurrentSemanticContendersHaveExactlyOneWinnerAlongsideEightOtherRecords() throws Exception {
        for (int otherCount : List.of(0, 4)) {
            var fixture = new IndependentV3TransferOwnershipTest.Fixture();
            var records = new ArrayList<V3PlanTransfers.Operation>();
            var winners = new ConcurrentLinkedQueue<V3PlanTransfers.Operation>();
            var ready = new CountDownLatch(12);
            var start = new CountDownLatch(1);
            var workers = Executors.newFixedThreadPool(12);
            try {
                for (int i = 0; i < otherCount; i++) records.add(fixture.transfers.admitMetadata(fixture.lease));
                for (int i = 0; i < otherCount; i++) records.add(fixture.transfers.admitCredentials(fixture.lease));
                var results = new ArrayList<Future<Boolean>>();
                for (int i = 0; i < 12; i++) results.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(3, TimeUnit.SECONDS)) throw new AssertionError("START_TIMEOUT");
                    try {
                        winners.add(fixture.transfers.admitSemantic(fixture.lease));
                        return true;
                    } catch (PlanRefusal refusal) {
                        assertEquals(PlanRefusal.Code.CAPACITY, refusal.code());
                        return false;
                    }
                }));
                assertTrue(ready.await(3, TimeUnit.SECONDS)); start.countDown();
                int admitted = 0;
                for (var result : results) if (result.get(3, TimeUnit.SECONDS)) admitted++;
                assertEquals(1, admitted); assertEquals(1, winners.size());
                assertTrue(fixture.transfers.awaitingWork(fixture.lease));
            } finally {
                start.countDown(); workers.shutdownNow(); assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
                winners.forEach(record -> record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, fixture.sessions));
                records.forEach(record -> record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, fixture.sessions));
            }
            assertFalse(fixture.transfers.awaitingWork(fixture.lease));
            var next = fixture.transfers.admitSemantic(fixture.lease);
            next.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, fixture.sessions);
            assertTrue(fixture.sessions.guard(fixture.lease, () -> true).orElse(false));
        }
    }

    @Test void mixedSemanticRetirementWaitsForFinalOriginalRecordWithoutConsumingRetries() {
        var fixture = new IndependentV3TransferOwnershipTest.Fixture();
        var semantic = fixture.transfers.admitSemantic(fixture.lease);
        var metadata = fixture.transfers.admitMetadata(fixture.lease);
        var credentials = fixture.transfers.admitCredentials(fixture.lease);
        try {
            assertEquals(SessionLedger.CleanupState.INCONCLUSIVE, fixture.sessions.logout(fixture.original).orElseThrow().state());
            assertTrue(semantic.cancelled()); assertTrue(metadata.cancelled()); assertTrue(credentials.cancelled());
            semantic.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, fixture.sessions);
            for (int i = 0; i < 5; i++) fixture.sessions.resumeCleanupAfterWork(fixture.lease.id());
            assertEquals(1, fixture.sessions.cleanupReports().getFirst().attempts());
            metadata.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, fixture.sessions);
            assertEquals(1, fixture.sessions.cleanupReports().getFirst().attempts());
            credentials.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, fixture.sessions);
            assertTrue(fixture.sessions.cleanupReports().isEmpty());
            var replacement = assertInstanceOf(SessionLedger.Accepted.class,
                    fixture.login(new MockHttpServletRequest(), "first")).lease();
            semantic.settlement(OwnedAsyncCompletion.Outcome.INCONCLUSIVE, fixture.sessions);
            assertTrue(fixture.sessions.guard(replacement, () -> true).orElse(false));
        } finally {
            semantic.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, fixture.sessions);
            metadata.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, fixture.sessions);
            credentials.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, fixture.sessions);
        }
    }
}
