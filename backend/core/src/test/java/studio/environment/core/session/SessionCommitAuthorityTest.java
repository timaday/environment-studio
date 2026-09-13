package studio.environment.core.session;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SessionCommitAuthorityTest {
    @Test void admittedCommitQuarantinesWithoutBlockingRevocationOrOtherOwners() throws Exception {
        var clock = new SessionLedgerTest.Time();
        var cleanups = new AtomicInteger();
        var ledger = new SessionLedger(clock, ignored -> cleanups.incrementAndGet());
        var owner = new Owner("https://commit-mock.invalid", "invented-owner");
        var lease = ((SessionLedger.Accepted) ledger.admit("one", owner)).lease();
        var commit = ledger.admitCommit(lease).orElseThrow();
        assertTrue(ledger.admitCommit(lease).isEmpty(), "only one admitted commit per lease");
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var report = threads.submit(() -> ledger.close(lease.id()).orElseThrow()).get(2, TimeUnit.SECONDS);
            assertEquals(SessionLedger.CleanupState.INCONCLUSIVE, report.state());
            assertEquals(1, report.attempts());
            assertEquals(1, cleanups.get());
            assertTrue(ledger.guard(lease, () -> true).isEmpty());
            assertTrue(ledger.admitCommit(lease).isEmpty());
            assertEquals(new SessionLedger.Denied(SessionLedger.Refusal.CLEANUP_INCONCLUSIVE), ledger.admit("retry", owner));
            assertInstanceOf(SessionLedger.Accepted.class, threads.submit(() -> ledger.admit("other", new Owner(owner.issuer(), "other-owner"))).get(2, TimeUnit.SECONDS));
            assertEquals(SessionLedger.CleanupState.INCONCLUSIVE, ledger.retryCleanup(lease.id()).orElseThrow().state());
            assertEquals(1, cleanups.get());
        }
        commit.complete();
        assertEquals(SessionLedger.CleanupState.COMPLETE, ledger.retryCleanup(lease.id()).orElseThrow().state());
        assertEquals(2, cleanups.get());
        commit.complete();
        assertInstanceOf(SessionLedger.Accepted.class, ledger.admit("fresh", owner));
        assertTrue(ledger.admitCommit(lease).isEmpty(), "old exact lease cannot acquire new authority");
    }

    @Test void commitChecksClockWithoutRenewalAndRevocationWinsBeforeAdmission() {
        var clock = new SessionLedgerTest.Time();
        var ledger = new SessionLedger(clock, ignored -> {});
        var owner = new Owner("https://commit-mock.invalid", "invented-owner");
        var lease = ((SessionLedger.Accepted) ledger.admit("one", owner)).lease();
        clock.now = clock.now.plusSeconds(1799);
        ledger.admitCommit(lease).orElseThrow().complete();
        clock.now = clock.now.plusSeconds(1);
        assertTrue(ledger.admitCommit(lease).isEmpty());
        ledger.expire();
        var absolute = ((SessionLedger.Accepted) ledger.admit("two", owner, clock.instant().plusSeconds(1))).lease();
        clock.now = clock.now.plusSeconds(1);
        assertTrue(ledger.admitCommit(absolute).isEmpty());
        ledger.expire();
        var revoked = ((SessionLedger.Accepted) ledger.admit("three", owner)).lease();
        ledger.close(revoked.id());
        assertTrue(ledger.admitCommit(revoked).isEmpty());
    }
}
