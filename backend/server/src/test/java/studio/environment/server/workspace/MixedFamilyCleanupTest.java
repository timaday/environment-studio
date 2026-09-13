package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.HostedSessions;

/** Actual session/registry composition with explicit legacy-style completion notifications, no HTTP claim. */
class MixedFamilyCleanupTest {
    static final class Fixture {
        final V3WorkspaceOperations workspace = new V3WorkspaceOperations();
        final AtomicInteger synchronousCleanups = new AtomicInteger();
        final HostedSessions sessions = new HostedSessions(Clock.systemUTC(),
                List.of(lease -> synchronousCleanups.incrementAndGet(), workspace));
        final DefaultOidcUser principal = principal("same-owner");
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final SessionLedger.Lease lease;
        Fixture() {
            assertTrue(sessions.reserveLogin(request));
            lease = assertInstanceOf(SessionLedger.Accepted.class,
                    sessions.authenticated(request.getSession(), principal)).lease();
            request.setAttribute(HostedSessions.REQUEST_LEASE, lease);
        }
        SessionLedger.CleanupReport report() {
            return sessions.cleanupReports().stream().filter(r -> r.sessionId().equals(lease.id())).findFirst().orElseThrow();
        }
        SessionLedger.Admission login(DefaultOidcUser who) {
            var next = new MockHttpServletRequest(); assertTrue(sessions.reserveLogin(next));
            return sessions.authenticated(next.getSession(), who);
        }
        void redundantLegacyNotifications() {
            // Models V1 metadata worker callbacks after their own plan obligation is already clear.
            // No call here claims an actual PlanController or actual network worker was run.
            for (int i = 0; i < 4; i++) sessions.resumeCleanupAfterWork(lease.id());
        }
    }
    static DefaultOidcUser principal(String subject) {
        Instant now = Instant.now();
        return new DefaultOidcUser(List.of(), new OidcIdToken("invented-mixed-token", now, now.plusSeconds(600),
                Map.of("iss", "https://mixed-cleanup.invalid", "sub", subject)));
    }
    @Test void pendingWorkspaceMustNotSpendRetriesOnOtherFamilyNotifications() {
        var f = new Fixture(); var held = f.workspace.admit(f.lease);
        f.sessions.logout(f.request); assertEquals(1, f.report().attempts());
        try {
            f.redundantLegacyNotifications();
            assertEquals(1, f.synchronousCleanups.get(), "already completed hook must never rerun");
            assertEquals(1, f.workspace.activeCount());
            assertEquals(1, f.report().attempts(), "passive pending work must not consume cleanup attempts");
        } finally { held.complete(f.sessions); }
    }
    @Test void finalWorkspaceClosureMustRecoverOriginalOwnerAfterOtherFamilyNotifications() {
        var f = new Fixture(); var held = f.workspace.admit(f.lease);
        f.sessions.logout(f.request); f.redundantLegacyNotifications();
        held.complete(f.sessions); assertEquals(0, f.workspace.activeCount());
        var admission = f.login(f.principal);
        assertAll(() -> assertTrue(f.sessions.cleanupReports().isEmpty(), "all actual obligations have settled"),
                () -> assertInstanceOf(SessionLedger.Accepted.class, admission));
    }
    @Test void heldWorkRemainsOwnerScopedAndStaleClosureCannotRecreateCleanup() {
        var f = new Fixture(); var held = f.workspace.admit(f.lease);
        f.sessions.logout(f.request);
        assertInstanceOf(SessionLedger.Accepted.class, f.login(principal("different-owner")));
        assertInstanceOf(SessionLedger.Denied.class, f.login(f.principal));
        held.complete(f.sessions);
        assertTrue(f.sessions.cleanupReports().isEmpty());
        held.complete(f.sessions); held.inconclusive(f.sessions);
        assertTrue(f.sessions.cleanupReports().isEmpty());
        assertInstanceOf(SessionLedger.Accepted.class, f.login(f.principal));
        assertEquals(1, f.synchronousCleanups.get());
    }
    @Test void actualSecurityWorkspaceHookForwardsRetainedAndTerminalWork() throws Exception {
        var runtime=new WorkspaceRuntime(new org.springframework.mock.env.MockEnvironment(),
                studio.environment.server.security.RuntimeConfiguration.RuntimeMode.DEMO);
        var beans=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beans.registerSingleton("workspaceRuntime",runtime);
        var method=studio.environment.server.security.HostedSecurity.class.getDeclaredMethod("v3WorkspaceCleanup",org.springframework.beans.factory.ObjectProvider.class);
        method.setAccessible(true);
        var hook=(studio.environment.server.session.SessionCleanup)method.invoke(new studio.environment.server.security.HostedSecurity(),beans.getBeanProvider(WorkspaceRuntime.class));
        var f=new Fixture(); assertFalse(hook.awaitingWork(f.lease));
        var operation=runtime.v3Operations().admit(f.lease);assertTrue(hook.awaitingWork(f.lease));
        operation.inconclusive(f.sessions);operation.complete(f.sessions);
        assertTrue(hook.awaitingWork(f.lease));assertThrows(IllegalStateException.class,()->hook.invalidate(f.lease));
        assertEquals(1,runtime.v3Operations().activeCount());
    }
}
