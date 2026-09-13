package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.HostedSessions;

/** Independent invented identities exercise the real session ledger, not a quarantine spy. */
class IndependentV3TransferOwnershipTest {
    static final class Fixture {
        final Instant now=Instant.parse("2026-09-10T00:00:00Z");
        final V3PlanTransfers transfers=new V3PlanTransfers();
        final HostedSessions sessions=new HostedSessions(Clock.fixed(now,ZoneOffset.UTC),List.of(transfers));
        final MockHttpServletRequest original=new MockHttpServletRequest();
        final SessionLedger.Lease lease;
        Fixture(){lease=assertInstanceOf(SessionLedger.Accepted.class,login(original,"first")).lease();original.setAttribute(HostedSessions.REQUEST_LEASE,lease);}
        SessionLedger.Admission login(MockHttpServletRequest request,String subject) {
            assertTrue(sessions.reserveLogin(request));
            var principal=new DefaultOidcUser(List.of(),new OidcIdToken("invented-independent-transfer",now,now.plusSeconds(600),Map.of("iss","https://transfer-review.invalid","sub",subject)));
            return sessions.authenticated(request.getSession(),principal);
        }
    }
    @Test void unrequestedCompletionUncertaintyRevokesOnlyOriginalOwnerAndLateSuccessCannotRepairIt() {
        var f=new Fixture();var original=f.transfers.admitCredentials(f.lease);
        var otherRequest=new MockHttpServletRequest();
        var other=assertInstanceOf(SessionLedger.Accepted.class,f.login(otherRequest,"second")).lease();
        var otherRecord=f.transfers.admitCredentials(other);
        try {
            original.settlement(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,f.sessions);
            assertTrue(f.sessions.guard(f.lease,()->true).isEmpty());
            assertEquals(Optional.of(other),f.sessions.guard(other,()->other));assertFalse(otherRecord.cancelled());
            assertTrue(original.cancelled());assertTrue(f.transfers.awaitingWork(f.lease));
            assertEquals(1,f.sessions.cleanupReports().getFirst().attempts());
            original.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);
            for(int i=0;i<5;i++)f.sessions.resumeCleanupAfterWork(f.lease.id());
            assertEquals(1,f.sessions.cleanupReports().getFirst().attempts());
            assertEquals(new SessionLedger.Denied(SessionLedger.Refusal.CLEANUP_INCONCLUSIVE),f.login(new MockHttpServletRequest(),"first"));
            assertTrue(f.transfers.awaitingWork(f.lease));
        } finally {otherRecord.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);}
        assertTrue(f.sessions.guard(other,()->true).orElse(false));
    }
    @Test void settledTransferCannotRevokeItsStillLiveOriginalSessionOnALateNotification() {
        var f=new Fixture();var completed=f.transfers.admitCredentials(f.lease);
        completed.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);
        assertFalse(f.transfers.awaitingWork(f.lease));assertFalse(completed.cancelled());
        completed.settlement(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,f.sessions);
        completed.settlement(OwnedAsyncCompletion.Outcome.IN_PROGRESS,f.sessions);
        assertEquals(Optional.of(f.lease),f.sessions.guard(f.lease,()->f.lease));
        assertFalse(completed.cancelled());assertTrue(f.sessions.cleanupReports().isEmpty());
    }
    @Test void lateOriginalNotificationsCannotQuarantineReplacementOrRemoveItsRecord() {
        var f=new Fixture();var old=f.transfers.admitCredentials(f.lease);
        assertEquals(SessionLedger.CleanupState.INCONCLUSIVE,f.sessions.logout(f.original).orElseThrow().state());
        old.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);
        assertTrue(f.sessions.cleanupReports().isEmpty());
        var fresh=assertInstanceOf(SessionLedger.Accepted.class,f.login(new MockHttpServletRequest(),"first")).lease();
        assertNotEquals(f.lease.id(),fresh.id());var retained=f.transfers.admitCredentials(fresh);
        try {
            old.settlement(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,f.sessions);
            old.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);
            old.settlement(OwnedAsyncCompletion.Outcome.IN_PROGRESS,f.sessions);
            assertEquals(Optional.of(fresh),f.sessions.guard(fresh,()->fresh));
            assertTrue(f.transfers.awaitingWork(fresh));assertFalse(retained.cancelled());
            assertFalse(f.transfers.awaitingWork(f.lease));assertTrue(f.sessions.cleanupReports().isEmpty());
        } finally {retained.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);}
    }
}
