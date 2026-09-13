package studio.environment.core.session;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class SessionCleanupCompletionTest {
    @Test void completionDuringOriginalCleanupCoalescesUntilThatAttemptReturns()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        var ledger=new SessionLedger(Clock.systemUTC(),lease->{
            if(calls.incrementAndGet()==1){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("TEST_TIMEOUT");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("TEST_INTERRUPTED");}throw new IllegalStateException("ORIGINAL_WORK_WAS_PENDING");}
        });
        var owner=new Owner("https://invented.invalid","cleanup-owner");assertInstanceOf(SessionLedger.Accepted.class,ledger.admit("old",owner));
        try(var executor=Executors.newSingleThreadExecutor()) {
            var original=executor.submit(()->ledger.close("old"));
            try {assertTrue(entered.await(1,TimeUnit.SECONDS));for(int i=0;i<32;i++)assertEquals(SessionLedger.CleanupState.IN_PROGRESS,ledger.resumeCleanup("old").orElseThrow().state());assertEquals(1,calls.get());}
            finally {release.countDown();}
            assertEquals(SessionLedger.CleanupState.COMPLETE,original.get(2,TimeUnit.SECONDS).orElseThrow().state());
        }
        assertEquals(2,calls.get());assertTrue(ledger.resumeCleanup("old").isEmpty());assertEquals(2,calls.get());assertInstanceOf(SessionLedger.Accepted.class,ledger.admit("fresh",owner));assertTrue(ledger.touch("old").isEmpty());
    }
    @Test void completionNotificationsCannotExceedTheExistingAttemptLimit() {
        var reference=new AtomicReference<SessionLedger>();var calls=new AtomicInteger();
        var ledger=new SessionLedger(Clock.systemUTC(),lease->{calls.incrementAndGet();reference.get().resumeCleanup(lease.id());throw new IllegalStateException("STILL_PENDING");});reference.set(ledger);
        var owner=new Owner("https://invented.invalid","pending-owner");ledger.admit("old",owner);
        assertEquals(SessionLedger.CleanupState.INCONCLUSIVE,ledger.close("old").orElseThrow().state());
        assertEquals(3,calls.get());for(int i=0;i<16;i++)assertEquals(3,ledger.resumeCleanup("old").orElseThrow().attempts());assertEquals(3,calls.get());assertInstanceOf(SessionLedger.Denied.class,ledger.admit("fresh",owner));
    }
    @Test void liveAndUnknownSessionsCannotBeRetiredByCompletionNotification() {
        var calls=new AtomicInteger();var ledger=new SessionLedger(Clock.systemUTC(),lease->calls.incrementAndGet());ledger.admit("live",new Owner("https://invented.invalid","live-owner"));
        assertTrue(ledger.resumeCleanup("live").isEmpty());assertTrue(ledger.resumeCleanup("unknown").isEmpty());assertEquals(0,calls.get());assertTrue(ledger.touch("live").isPresent());
    }
    @Test void notificationCannotReleaseAnOutstandingCommitOrSpendItsRetryBudget() {
        var calls=new AtomicInteger();var ledger=new SessionLedger(Clock.systemUTC(),lease->calls.incrementAndGet());
        var owner=new Owner("https://invented.invalid","commit-owner");
        var lease=((SessionLedger.Accepted)ledger.admit("old",owner)).lease();
        var commit=ledger.admitCommit(lease).orElseThrow();
        assertEquals(SessionLedger.CleanupState.INCONCLUSIVE,ledger.close("old").orElseThrow().state());
        for(int i=0;i<32;i++){
            var report=ledger.resumeCleanup("old").orElseThrow();
            assertEquals(SessionLedger.CleanupState.INCONCLUSIVE,report.state());assertEquals(1,report.attempts());
        }
        assertEquals(1,calls.get());assertTrue(ledger.hasOutstandingCommit(lease));
        assertInstanceOf(SessionLedger.Denied.class,ledger.admit("fresh",owner));
        commit.complete();assertEquals(SessionLedger.CleanupState.COMPLETE,ledger.resumeCleanup("old").orElseThrow().state());
        assertEquals(2,calls.get());assertInstanceOf(SessionLedger.Accepted.class,ledger.admit("fresh",owner));
    }
    @Test void notificationDuringSuccessfulCleanupCannotTriggerAnotherAttempt() {
        var reference=new AtomicReference<SessionLedger>();var calls=new AtomicInteger();
        var ledger=new SessionLedger(Clock.systemUTC(),lease->{calls.incrementAndGet();reference.get().resumeCleanup(lease.id());});reference.set(ledger);
        ledger.admit("old",new Owner("https://invented.invalid","successful-owner"));
        assertEquals(SessionLedger.CleanupState.COMPLETE,ledger.close("old").orElseThrow().state());
        assertEquals(1,calls.get());assertTrue(ledger.resumeCleanup("old").isEmpty());assertEquals(1,calls.get());
    }
}
