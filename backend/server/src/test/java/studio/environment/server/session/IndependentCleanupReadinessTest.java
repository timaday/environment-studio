package studio.environment.server.session;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.core.session.SessionLedger;

class IndependentCleanupReadinessTest {
    static final class Fixture {
        final HostedSessions sessions;final DefaultOidcUser principal;
        final MockHttpServletRequest request=new MockHttpServletRequest();final SessionLedger.Lease lease;
        Fixture(SessionCleanup hook) {
            Instant now=Instant.parse("2026-09-10T00:00:00Z");sessions=new HostedSessions(Clock.fixed(now,ZoneOffset.UTC),List.of(hook));
            principal=new DefaultOidcUser(List.of(),new OidcIdToken("independent-invented",now,now.plusSeconds(600),Map.of("iss","https://independent.invalid","sub","owner")));
            assertTrue(sessions.reserveLogin(request));lease=assertInstanceOf(SessionLedger.Accepted.class,sessions.authenticated(request.getSession(),principal)).lease();
            request.setAttribute(HostedSessions.REQUEST_LEASE,lease);
        }
        SessionLedger.Admission login() {
            var next=new MockHttpServletRequest();assertTrue(sessions.reserveLogin(next));return sessions.authenticated(next.getSession(),principal);
        }
    }
    @Test void optimisticPassiveReplyCannotReplaceActualCleanupOrItsThreeAttemptCeiling() {
        var calls=new AtomicInteger();SessionCleanup hook=new SessionCleanup() {
            public boolean awaitingWork(SessionLedger.Lease lease){return false;}
            public void invalidate(SessionLedger.Lease lease){calls.incrementAndGet();throw new IllegalStateException("invented-unsettled");}
        };
        var f=new Fixture(hook);f.sessions.logout(f.request);
        for(int i=0;i<8;i++)f.sessions.resumeCleanupAfterWork(f.lease.id());
        assertEquals(3,calls.get());var report=f.sessions.cleanupReports().getFirst();
        assertEquals(SessionLedger.CleanupState.INCONCLUSIVE,report.state());assertEquals(3,report.attempts());
        assertEquals(new SessionLedger.Denied(SessionLedger.Refusal.CLEANUP_INCONCLUSIVE),f.login());
        assertTrue(f.sessions.guard(f.lease,()->true).isEmpty());
    }
    @Test void delayedOldLeaseReadinessCannotBlockManualCleanupOrAReplacementLogin() throws Exception {
        var pending=new AtomicBoolean(true);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var invalidations=new AtomicInteger();
        SessionCleanup hook=new SessionCleanup() {
            public boolean awaitingWork(SessionLedger.Lease lease) {
                entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("CONTROL_TIMEOUT");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return false;
            }
            public void invalidate(SessionLedger.Lease lease){invalidations.incrementAndGet();if(pending.get())throw new IllegalStateException("invented-pending");}
        };
        var f=new Fixture(hook);f.sessions.logout(f.request);
        var old=new FutureTask<>(()->f.sessions.resumeCleanupAfterWork(f.lease.id()));Thread notifier=new Thread(old);notifier.start();
        var replace=new FutureTask<SessionLedger.Lease>(()->{
            pending.set(false);assertEquals(SessionLedger.CleanupState.COMPLETE,f.sessions.retryCleanup(f.lease.id()).orElseThrow().state());
            return assertInstanceOf(SessionLedger.Accepted.class,f.login()).lease();
        });Thread replacing=new Thread(replace);SessionLedger.Lease fresh;
        try {assertTrue(entered.await(1,TimeUnit.SECONDS));replacing.start();fresh=assertDoesNotThrow(()->replace.get(500,TimeUnit.MILLISECONDS));}
        finally{release.countDown();notifier.join(1500);replacing.join(1500);}
        assertFalse(notifier.isAlive());assertFalse(replacing.isAlive());assertTrue(old.get().isEmpty());
        assertNotEquals(f.lease.id(),fresh.id());assertEquals(Optional.of(fresh),f.sessions.guard(fresh,()->fresh));
        assertTrue(f.sessions.guard(f.lease,()->true).isEmpty());assertTrue(f.sessions.cleanupReports().isEmpty());assertEquals(2,invalidations.get());
    }
}
