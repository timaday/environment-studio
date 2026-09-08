package studio.environment.server.session;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.core.session.SessionLedger;

class HostedSessionsTest {
    static final class Time extends Clock {
        volatile Instant now = Instant.parse("2026-09-08T10:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    final Time clock = new Time();
    final List<SessionLedger.Lease> cleanups = new ArrayList<>();
    final HostedSessions sessions = new HostedSessions(clock, List.of(cleanups::add));
    // Controlled principal is only for adapter lifecycle units; real protocol is tested separately.
    final DefaultOidcUser user = new DefaultOidcUser(List.of(), new OidcIdToken("mock-token-canary", clock.now,
            clock.now.plusSeconds(300), Map.of("iss", "https://mock.invalid", "sub", "synthetic-owner")));
    MockHttpServletRequest authenticated() {
        var request = new MockHttpServletRequest();
        assertTrue(sessions.reserveLogin(request));
        assertInstanceOf(SessionLedger.Accepted.class, sessions.authenticated(request.getSession(), user));
        return request;
    }
    @Test void idleExpiryInvalidatesServletSessionAndInvokesCleanupExactlyOnce() {
        var request = authenticated();
        var session = (MockHttpSession) request.getSession(false);
        clock.now = clock.now.plusSeconds(1800);
        sessions.expire();
        assertTrue(session.isInvalid());
        assertTrue(sessions.current(request).isEmpty());
        sessions.expire();
        assertEquals(1, cleanups.size());
    }
    @Test void absoluteExpiryWinsDespiteActivityAndNewOwnerUsesFreshAuthority() {
        var request = authenticated();
        Instant start = clock.now;
        for (int minute = 20; minute < 480; minute += 20) {
            clock.now = start.plusSeconds(minute * 60L);
            assertEquals("synthetic-owner", sessions.requireOwner(request).subject());
        }
        clock.now = start.plusSeconds(8 * 3600);
        assertTrue(sessions.current(request).isEmpty());
        assertEquals(1, cleanups.size());
        var fresh = authenticated();
        assertTrue(sessions.current(fresh).isPresent());
    }
    @Test void servletInvalidationTriggersCleanupAndRestartCannotRestoreAuthority() {
        var request = authenticated();
        assertTrue(new HostedSessions(clock, List.of()).current(request).isEmpty());
        request.getSession().invalidate();
        assertEquals(1, cleanups.size());
        assertTrue(sessions.current(request).isEmpty());
    }
    @Test void pendingLoginsShareCapacityAndExpiryAndNeverAllocateOnRefusal() {
        var requests = new ArrayList<MockHttpServletRequest>();
        for (int i = 0; i < 64; i++) {
            var request = new MockHttpServletRequest();
            assertTrue(sessions.reserveLogin(request));
            assertTrue(sessions.reserveLogin(request));
            requests.add(request);
        }
        var refused = new MockHttpServletRequest();
        assertFalse(sessions.reserveLogin(refused));
        assertNull(refused.getSession(false));
        requests.getFirst().getSession().invalidate();
        assertTrue(sessions.reserveLogin(refused));
        clock.now = clock.now.plusSeconds(1800);
        sessions.expire();
        assertTrue(sessions.reserveLogin(new MockHttpServletRequest()));
    }

    @Test void firstFailedHookMustNotSkipIndependentHookOrRestoreExpiredAuthority() {
        var attempted = new ArrayList<String>();
        var sessions = new HostedSessions(clock, List.of(
                lease -> { attempted.add("first"); throw new IllegalStateException("synthetic-failure-canary"); },
                lease -> attempted.add("second")));
        var request = new MockHttpServletRequest();
        sessions.reserveLogin(request);
        sessions.authenticated(request.getSession(), user);
        clock.now = clock.now.plusSeconds(1800);
        assertDoesNotThrow(sessions::expire);
        assertEquals(List.of("first", "second"), attempted);
        assertTrue(sessions.current(request).isEmpty());
    }

    @Test void retryRunsOnlyUnfinishedHookAndRetainsOwnerUntilConclusive() {
        var firstAttempts = new java.util.concurrent.atomic.AtomicInteger();
        var secondAttempts = new java.util.concurrent.atomic.AtomicInteger();
        var sessions = new HostedSessions(clock, List.of(lease -> {
            if (firstAttempts.incrementAndGet() == 1) throw new IllegalStateException("synthetic-failure-canary");
        }, lease -> secondAttempts.incrementAndGet()));
        var request = new MockHttpServletRequest();
        sessions.reserveLogin(request);
        sessions.authenticated(request.getSession(), user);
        request.getSession().invalidate();
        var report = sessions.cleanupReports().getFirst();
        assertEquals(SessionLedger.CleanupState.INCONCLUSIVE, report.state());
        var next = new MockHttpServletRequest();
        sessions.reserveLogin(next);
        assertEquals(new SessionLedger.Denied(SessionLedger.Refusal.CLEANUP_INCONCLUSIVE), sessions.authenticated(next.getSession(), user));
        next.getSession().invalidate();
        assertEquals(SessionLedger.CleanupState.COMPLETE, sessions.retryCleanup(report.sessionId()).orElseThrow().state());
        assertEquals(2, firstAttempts.get());
        assertEquals(1, secondAttempts.get());
        assertTrue(sessions.cleanupReports().isEmpty());
        assertTrue(sessions.current(request).isEmpty());
    }
    @Test void servletFailureStillAttemptsAllHooksAndRetainsGlobalSlotAfterBoundedRetries() {
        var invalidations = new java.util.concurrent.atomic.AtomicInteger();
        var firstAttempts = new java.util.concurrent.atomic.AtomicInteger();
        var secondAttempts = new java.util.concurrent.atomic.AtomicInteger();
        var sessions = new HostedSessions(clock, List.of(lease -> firstAttempts.incrementAndGet(), lease -> secondAttempts.incrementAndGet()));
        var request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession() {
            @Override public void invalidate() { invalidations.incrementAndGet(); throw new IllegalStateException("synthetic-failure-canary"); }
        });
        sessions.reserveLogin(request);
        sessions.authenticated(request.getSession(), user);
        clock.now = clock.now.plusSeconds(1800);
        sessions.expire();
        assertTrue(sessions.current(request).isEmpty());
        assertEquals(1, firstAttempts.get());
        assertEquals(1, secondAttempts.get());
        var id = sessions.cleanupReports().getFirst().sessionId();
        for (int i = 0; i < 6; i++) sessions.retryCleanup(id);
        assertEquals(3, invalidations.get());
        assertEquals(1, firstAttempts.get());
        assertEquals(1, secondAttempts.get());
        assertEquals(3, sessions.cleanupReports().getFirst().attempts());
        for (int i = 0; i < 63; i++) assertTrue(sessions.reserveLogin(new MockHttpServletRequest()));
        var refused = new MockHttpServletRequest();
        assertFalse(sessions.reserveLogin(refused));
        assertNull(refused.getSession(false));
        assertFalse(sessions.cleanupReports().toString().contains("synthetic-failure-canary"));
    }

    @Test void expiryDuringAdmissionCannotLoseTheNewlyAssignedLease() throws Exception {
        assertAdmissionRetirementRace(false);
    }
    @Test void servletDestructionDuringAdmissionCleansTheAssignedLease() throws Exception {
        assertAdmissionRetirementRace(true);
    }
    private void assertAdmissionRetirementRace(boolean destroy) throws Exception {
        var insideAdmission = new java.util.concurrent.CountDownLatch(1);
        var releaseAdmission = new java.util.concurrent.CountDownLatch(1);
        var hooks = new java.util.concurrent.atomic.AtomicInteger();
        var sessions = new HostedSessions(clock, List.of(lease -> hooks.incrementAndGet()));
        var request = new MockHttpServletRequest();
        sessions.reserveLogin(request);
        var servlet = (MockHttpSession) request.getSession(false);
        var principal = new DefaultOidcUser(List.of(), user.getIdToken()) {
            @Override public String getSubject() {
                insideAdmission.countDown();
                try { assertTrue(releaseAdmission.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                return super.getSubject();
            }
        };
        var admission = new java.util.concurrent.FutureTask<>(() -> sessions.authenticated(servlet, principal));
        var retirement = new java.util.concurrent.FutureTask<Void>(() -> {
            if (destroy) servlet.invalidate(); else sessions.expire();
            return null;
        });
        var admitting = new Thread(admission, "mock-admission");
        var retiring = new Thread(retirement, "mock-retirement");
        admitting.start();
        try {
            assertTrue(insideAdmission.await(5, java.util.concurrent.TimeUnit.SECONDS));
            clock.now = clock.now.plusSeconds(1800);
            retiring.start();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (retiring.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.onSpinWait();
            assertEquals(Thread.State.BLOCKED, retiring.getState(), "Retirement must reach the admission-held slot monitor");
        } finally { releaseAdmission.countDown(); }
        assertInstanceOf(SessionLedger.Accepted.class, admission.get(5, java.util.concurrent.TimeUnit.SECONDS));
        retirement.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(servlet.isInvalid());
        assertEquals(1, hooks.get(), "Every admitted lease must be cleaned exactly once");
        assertTrue(sessions.cleanupReports().isEmpty());
        var fresh = new MockHttpServletRequest();
        assertTrue(sessions.reserveLogin(fresh));
        assertInstanceOf(SessionLedger.Accepted.class, sessions.authenticated(fresh.getSession(), user));
    }

}
