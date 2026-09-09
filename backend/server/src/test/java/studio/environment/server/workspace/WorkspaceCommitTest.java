package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.workspace.WorkspaceRefusal;
import studio.environment.server.session.HostedSessions;

class WorkspaceCommitTest {
    final AtomicInteger cleanups = new AtomicInteger();
    final Clock clock = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC);
    final HostedSessions sessions = new HostedSessions(clock, List.of(ignored -> cleanups.incrementAndGet()));
    final MockHttpServletRequest request = new MockHttpServletRequest();
    SessionLedger.Lease login() {
        assertTrue(sessions.reserveLogin(request));
        sessions.authenticated(request.getSession(), new DefaultOidcUser(List.of(), new OidcIdToken("independent-commit-token", clock.instant(), clock.instant().plusSeconds(300), Map.of("iss", "https://commit-mock.invalid", "sub", "invented-owner"))));
        var lease = sessions.current(request).orElseThrow();
        request.setAttribute(HostedSessions.REQUEST_LEASE, lease);
        return lease;
    }
    @ParameterizedTest @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void completionRequiresObservedCloseIncludingFailedCommit(boolean commitFails, boolean closeFails) throws Exception {
        var lease = login();
        var closing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var commits = new AtomicInteger();
        var connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("commit")) { commits.incrementAndGet(); if (commitFails) throw new SQLException("INDEPENDENT_COMMIT_FAILURE"); return null; }
            if (method.getName().equals("close")) { closing.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new SQLException("INDEPENDENT_CLOSE_DEADLINE"); if (closeFails) throw new SQLException("INDEPENDENT_CLOSE_FAILURE"); return null; }
            throw new AssertionError("Unexpected JDBC operation");
        });
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var committing = threads.submit(() -> {
                try { WorkspaceCommit.authenticated(sessions, lease).commit(connection); return "complete"; }
                catch (SQLException unknown) { return "unknown"; }
                catch (WorkspaceRefusal refusal) { return refusal.code().name(); }
            });
            try {
                assertTrue(closing.await(2, TimeUnit.SECONDS));
                assertEquals(SessionLedger.CleanupState.INCONCLUSIVE, threads.submit(() -> sessions.logout(request).orElseThrow().state()).get(2, TimeUnit.SECONDS));
                assertEquals(1, cleanups.get());
                assertTrue(sessions.guard(lease, () -> true).isEmpty());
            } finally { release.countDown(); }
            assertEquals(closeFails ? "UNAVAILABLE" : commitFails ? "unknown" : "complete", committing.get(2, TimeUnit.SECONDS));
        }
        assertEquals(1, commits.get());
        assertEquals(closeFails ? SessionLedger.CleanupState.INCONCLUSIVE : SessionLedger.CleanupState.COMPLETE, sessions.retryCleanup(lease.id()).orElseThrow().state());
        assertEquals(1, cleanups.get());
    }
    @Test void revokedLeaseNeverSendsCommitToJdbc() throws Exception {
        var lease = login();
        sessions.logout(request);
        var connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> { throw new AssertionError("No JDBC operation admitted"); });
        assertEquals(WorkspaceRefusal.Code.FORBIDDEN, assertThrows(WorkspaceRefusal.class, () -> WorkspaceCommit.authenticated(sessions, lease).commit(connection)).code());
    }
    @Test void closeErrorRetiresAuthorityAndRemainsAnError() {
        var lease = login();
        var error = new UnsatisfiedLinkError("INDEPENDENT_CLOSE_ERROR");
        var connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("commit")) return null;
            if (method.getName().equals("close")) throw error;
            throw new AssertionError("Unexpected JDBC operation");
        });
        assertSame(error, assertThrows(UnsatisfiedLinkError.class, () -> WorkspaceCommit.authenticated(sessions, lease).commit(connection)));
        assertTrue(sessions.guard(lease, () -> true).isEmpty(), "close Error must revoke the original lease");
        assertEquals(SessionLedger.CleanupState.INCONCLUSIVE, sessions.retryCleanup(lease.id()).orElseThrow().state());
        assertEquals(1, cleanups.get());
    }

}
