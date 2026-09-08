package studio.environment.core.session;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SessionLedgerTest {
    static final class Time extends Clock {
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    final Time clock = new Time();
    final List<SessionLedger.Lease> cleaned = new ArrayList<>();
    final SessionLedger ledger = new SessionLedger(clock, cleaned::add);
    final Owner owner = new Owner("https://mock.invalid", "invented-a");

    @Test void enforcesOwnerCapacityAndReleasesOnlyClosedAuthority() {
        assertInstanceOf(SessionLedger.Accepted.class, ledger.admit("a", owner));
        assertEquals(new SessionLedger.Denied(SessionLedger.Refusal.OWNER_ACTIVE), ledger.admit("b", owner));
        ledger.close("a");
        ledger.close("a");
        assertEquals(1, cleaned.size());
        assertInstanceOf(SessionLedger.Accepted.class, ledger.admit("b", owner));
    }
    @Test void idleBoundaryExpiresAndCleansBeforeReAdmission() {
        ledger.admit("a", owner);
        clock.now = clock.now.plusSeconds(1800);
        assertTrue(ledger.touch("a").isEmpty());
        assertEquals(List.of("a"), cleaned.stream().map(SessionLedger.Lease::id).toList());
        assertInstanceOf(SessionLedger.Accepted.class, ledger.admit("b", owner));
    }
    @Test void activeRequestsNeverExtendAbsoluteLifetime() {
        var first = clock.now;
        ledger.admit("a", owner);
        for (int minute = 20; minute < 480; minute += 20) {
            clock.now = first.plusSeconds(minute * 60L);
            assertTrue(ledger.touch("a").isPresent());
        }
        clock.now = first.plusSeconds(8 * 3600);
        ledger.expire();
        assertTrue(ledger.touch("a").isEmpty());
        assertEquals(1, cleaned.size());
    }
    @Test void globalCapacityRefusesWithoutEvictingAnyLiveOwner() {
        for (int i = 0; i < 64; i++) assertInstanceOf(SessionLedger.Accepted.class,
                ledger.admit("session-" + i, new Owner(owner.issuer(), "mock-" + i)));
        assertEquals(new SessionLedger.Denied(SessionLedger.Refusal.CAPACITY), ledger.admit("overflow", owner));
        assertTrue(ledger.touch("session-0").isPresent());
        assertTrue(cleaned.isEmpty());
    }
    @Test void concurrentSameOwnerAdmissionsHaveExactlyOneWinner() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<SessionLedger.Admission>>();
            for (int i = 0; i < 20; i++) {
                String id = "parallel-" + i;
                tasks.add(() -> ledger.admit(id, owner));
            }
            long accepted = 0;
            for (var future : executor.invokeAll(tasks)) if (future.get() instanceof SessionLedger.Accepted) accepted++;
            assertEquals(1, accepted);
        }
    }
    @Test void freshProcessHasNoPriorAuthorityAndIssuerIsPartOfOwner() {
        ledger.admit("a", owner);
        assertTrue(new SessionLedger(clock, ignored -> {}).touch("a").isEmpty());
        assertInstanceOf(SessionLedger.Accepted.class, ledger.admit("b", new Owner("https://other.invalid", owner.subject())));
    }
    @Test void oneFailedExpiredCleanupMustNotStrandTheOtherRevokedLease() {
        var attempted = new ArrayList<String>();
        var ledger = new SessionLedger(clock, lease -> {
            attempted.add(lease.id());
            if (lease.id().equals("first")) throw new IllegalStateException("synthetic-failure-canary");
        });
        ledger.admit("first", owner);
        ledger.admit("second", new Owner(owner.issuer(), "independent-owner"));
        clock.now = clock.now.plusSeconds(1800);
        assertDoesNotThrow(ledger::expire);
        assertEquals(List.of("first", "second"), attempted);
        assertTrue(ledger.touch("first").isEmpty());
        assertTrue(ledger.touch("second").isEmpty());
    }

    @Test void failedCleanupQuarantinesOwnerAndCapacityAndBoundsExplicitRetries() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var ledger = new SessionLedger(clock, lease -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("synthetic-failure-canary");
        });
        ledger.admit("failed", owner);
        var first = ledger.close("failed").orElseThrow();
        assertEquals(SessionLedger.CleanupState.INCONCLUSIVE, first.state());
        assertEquals(1, first.attempts());
        assertEquals(new SessionLedger.Denied(SessionLedger.Refusal.CLEANUP_INCONCLUSIVE), ledger.admit("replacement", owner));
        for (int i = 0; i < 63; i++) ledger.admit("other-" + i, new Owner(owner.issuer(), "other-" + i));
        assertEquals(new SessionLedger.Denied(SessionLedger.Refusal.CAPACITY), ledger.admit("overflow", new Owner(owner.issuer(), "overflow")));
        for (int i = 0; i < 6; i++) ledger.retryCleanup("failed");
        assertEquals(3, attempts.get());
        assertEquals(3, ledger.cleanupReports().getFirst().attempts());
        assertTrue(ledger.touch("failed").isEmpty());
        assertFalse(ledger.cleanupReports().toString().contains("synthetic-failure-canary"));
    }
    @Test void successfulExplicitRetryReleasesCapacityWithoutRestoringOldAuthentication() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var ledger = new SessionLedger(clock, lease -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("synthetic-failure-canary");
        });
        ledger.admit("old", owner);
        ledger.close("old");
        assertEquals(SessionLedger.CleanupState.COMPLETE, ledger.retryCleanup("old").orElseThrow().state());
        assertTrue(ledger.touch("old").isEmpty());
        assertTrue(ledger.cleanupReports().isEmpty());
        assertInstanceOf(SessionLedger.Accepted.class, ledger.admit("new", owner));
    }

}
