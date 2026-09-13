package studio.environment.server.security;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.HostedSessions;

class V3PlanPollingBoundaryTest {
    @Test void allV3GetPollingKeepsBothOriginalIdleDeadlines() throws Exception {
        for (String path : List.of("/api/v3/plans/current", "/api/v3/plans/mock-plan",
                "/api/v3/operations/mock-operation")) {
            var fixture = new Fixture();
            fixture.instant.set(fixture.start.plusSeconds(1799));
            assertEquals(200, fixture.call("GET", path));
            fixture.instant.set(fixture.start.plusSeconds(1800));
            assertEquals(401, fixture.call("GET", path));
            assertTrue(fixture.sessions.guard(fixture.lease, () -> true).isEmpty());
        }
    }


    @Test void validUnsafeActivityStillTouchesWhileWrongHostCannotRenew() throws Exception {
        var mutation = new Fixture();
        mutation.instant.set(mutation.start.plusSeconds(1799));
        assertEquals(200, mutation.call("POST", "/api/v3/plans/mock-plan/inspections"));
        mutation.instant.set(mutation.start.plusSeconds(1800));
        assertEquals(200, mutation.call("GET", "/api/v3/plans/current"));
        mutation.instant.set(mutation.start.plusSeconds(3599));
        assertEquals(401, mutation.call("GET", "/api/v3/plans/current"));

        var rejected = new Fixture();
        rejected.instant.set(rejected.start.plusSeconds(1799));
        assertEquals(403, rejected.call("POST", "/api/v3/plans", "foreign.invalid", rejected.principal));
        rejected.instant.set(rejected.start.plusSeconds(1800));
        assertEquals(401, rejected.call("GET", "/api/v3/plans/current"));
    }

    @Test void foreignAuthenticatedPrincipalCannotObtainOriginalLeaseAndLegacyPollingStaysUnchanged() throws Exception {
        var fixture = new Fixture();
        var foreign = new DefaultOidcUser(List.of(), new OidcIdToken("mock-foreign", fixture.start,
                fixture.start.plusSeconds(7200), Map.of("iss", "https://mock-issuer.invalid", "sub", "other-owner")));
        assertEquals(401, fixture.call("GET", "/api/v3/operations/mock-operation", "mock-ui.invalid", foreign));
        var legacy = new Fixture();
        legacy.instant.set(legacy.start.plusSeconds(1799));
        assertEquals(200, legacy.call("GET", "/api/v1/plans/current"));
        legacy.instant.set(legacy.start.plusSeconds(1800));
        assertEquals(401, legacy.call("GET", "/api/v1/plans/current"));
    }

    static final class Fixture {
        final Instant start = Instant.parse("2026-09-10T00:00:00Z");
        final AtomicReference<Instant> instant = new AtomicReference<>(start);
        final Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return instant.get(); }
        };
        final HostedSessions sessions = new HostedSessions(clock, List.of());
        final MockHttpServletRequest login = new MockHttpServletRequest();
        final DefaultOidcUser principal = new DefaultOidcUser(List.of(), new OidcIdToken(
                "mock-polling-token", start, start.plusSeconds(7200),
                Map.of("iss", "https://mock-issuer.invalid", "sub", "mock-owner")));
        final SessionLedger.Lease lease;
        final HostedBoundaryFilter filter;
        Fixture() {
            assertTrue(sessions.reserveLogin(login));
            lease = assertInstanceOf(SessionLedger.Accepted.class,
                    sessions.authenticated(login.getSession(), principal)).lease();
            var settings = new HostedSettings(new MockEnvironment()
                    .withProperty("studio.security.public-origin", "https://mock-ui.invalid")
                    .withProperty("studio.security.issuer", "https://mock-issuer.invalid")
                    .withProperty("studio.security.client-id", "mock-client")
                    .withProperty("studio.security.client-secret", "mock-secret"));
            filter = new HostedBoundaryFilter(settings, sessions);
        }
        int call(String method, String path) throws Exception {
            return call(method, path, "mock-ui.invalid", principal);
        }
        int call(String method, String path, String host, DefaultOidcUser authenticated) throws Exception {
            var request = new MockHttpServletRequest(method, path);
            request.setSession((org.springframework.mock.web.MockHttpSession) login.getSession());
            request.addHeader("Host", host);
            if (!method.equals("GET")) request.addHeader("Origin", "https://mock-ui.invalid");
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new OAuth2AuthenticationToken(authenticated, List.of(), "studio"));
            var previous = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(context);
            try {
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, (incoming, outgoing) -> {
                    assertEquals(lease, request.getAttribute(HostedSessions.REQUEST_LEASE));
                });
                return response.getStatus();
            } finally { SecurityContextHolder.setContext(previous); }
        }
    }
}
