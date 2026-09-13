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

class CleanupReadinessTest {
    static final class Hook implements SessionCleanup {
        final AtomicBoolean pending=new AtomicBoolean(true),failed=new AtomicBoolean();
        final AtomicInteger calls=new AtomicInteger(),checks=new AtomicInteger();
        volatile CountDownLatch entered,release;
        public void invalidate(SessionLedger.Lease lease){calls.incrementAndGet();if(pending.get())throw new IllegalStateException("MOCK_PENDING");}
        public boolean awaitingWork(SessionLedger.Lease lease){
            checks.incrementAndGet();
            if(entered!=null){entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("MOCK_TIMEOUT");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("MOCK_INTERRUPTED");}}
            if(failed.get())throw new IllegalStateException("invented-private-cause");return pending.get();
        }
    }
    static final class Fixture {
        final HostedSessions sessions; final MockHttpServletRequest request=new MockHttpServletRequest();final SessionLedger.Lease lease;
        Fixture(SessionCleanup... hooks){
            sessions=new HostedSessions(Clock.systemUTC(),List.of(hooks));assertTrue(sessions.reserveLogin(request));Instant now=Instant.now();
            var principal=new DefaultOidcUser(List.of(),new OidcIdToken("mock-readiness-token",now,now.plusSeconds(600),Map.of("iss","https://readiness.invalid","sub","owner")));
            lease=assertInstanceOf(SessionLedger.Accepted.class,sessions.authenticated(request.getSession(),principal)).lease();request.setAttribute(HostedSessions.REQUEST_LEASE,lease);
        }
        int attempts(){return sessions.cleanupReports().stream().filter(r->r.sessionId().equals(lease.id())).findFirst().orElseThrow().attempts();}
    }
    @Test void allFamiliesMustSettleAndFailedReadinessDefersWithoutRepeatingCompletedHooks(){
        var a=new Hook();var b=new Hook();var completed=new Hook();completed.pending.set(false);var f=new Fixture(a,b,completed);
        f.sessions.logout(f.request);a.pending.set(false);b.failed.set(true);
        for(int i=0;i<4;i++)f.sessions.resumeCleanupAfterWork(f.lease.id());
        assertEquals(1,f.attempts());assertEquals(1,completed.calls.get());assertEquals(0,completed.checks.get());
        b.failed.set(false);b.pending.set(false);f.sessions.resumeCleanupAfterWork(f.lease.id());
        assertTrue(f.sessions.cleanupReports().isEmpty());assertEquals(1,completed.calls.get());
        f.sessions.resumeCleanupAfterWork(f.lease.id());assertEquals(2,a.calls.get());assertEquals(2,b.calls.get());
    }
    @Test void passiveQueryDoesNotOwnSlotMonitorAndManualRetryStillHasOriginalThreeAttemptBound()throws Exception{
        var hook=new Hook();var f=new Fixture(hook);f.sessions.logout(f.request);
        hook.entered=new CountDownLatch(1);hook.release=new CountDownLatch(1);
        var notify=new FutureTask<>(()->f.sessions.resumeCleanupAfterWork(f.lease.id()));Thread worker=new Thread(notify);worker.start();
        var executor=Executors.newSingleThreadExecutor();
        try{
            assertTrue(hook.entered.await(1,TimeUnit.SECONDS));
            assertDoesNotThrow(()->executor.submit(()->f.sessions.retryCleanup(f.lease.id())).get(500,TimeUnit.MILLISECONDS));
            assertEquals(2,f.attempts());
        }finally{hook.release.countDown();worker.join(1500);executor.shutdownNow();}
        assertFalse(worker.isAlive());notify.get();assertEquals(2,f.attempts());
        f.sessions.retryCleanup(f.lease.id());f.sessions.retryCleanup(f.lease.id());assertEquals(3,f.attempts());assertEquals(3,hook.calls.get());
    }
    @Test void falseReadinessCannotBypassOriginalCommitPermit(){
        var hook=new Hook();hook.pending.set(false);var f=new Fixture(hook);
        var commit=f.sessions.admitCommit(f.lease).orElseThrow();f.sessions.logout(f.request);
        for(int i=0;i<4;i++)f.sessions.resumeCleanupAfterWork(f.lease.id());
        assertEquals(1,f.attempts());assertTrue(f.sessions.guard(f.lease,()->true).isEmpty());
        commit.complete();f.sessions.resumeCleanupAfterWork(f.lease.id());assertTrue(f.sessions.cleanupReports().isEmpty());
    }
    @Test void notificationStartedDuringOriginalInvalidationIsNotLost() throws Exception {
        var calls=new AtomicInteger();var sessions=new AtomicReference<HostedSessions>();
        var notifier=new AtomicReference<Thread>();var error=new AtomicReference<Throwable>();
        SessionCleanup hook=lease->{
            if(calls.incrementAndGet()==1){
                Thread thread=new Thread(()->{try{sessions.get().resumeCleanupAfterWork(lease.id());}catch(Throwable failure){error.set(failure);}});
                notifier.set(thread);thread.start();throw new IllegalStateException("MOCK_FIRST_UNSETTLED");
            }
        };
        var f=new Fixture(hook);sessions.set(f.sessions);f.sessions.logout(f.request);
        notifier.get().join(1500);assertFalse(notifier.get().isAlive());assertNull(error.get());
        assertTrue(f.sessions.cleanupReports().isEmpty());assertEquals(2,calls.get());
    }
}
