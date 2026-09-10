package studio.environment.server.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import studio.environment.server.session.HostedSessions;

final class HostedBoundaryFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final HostedSettings settings;
    private final HostedSessions sessions;
    HostedBoundaryFilter(HostedSettings settings, HostedSessions sessions) { this.settings = settings; this.sessions = sessions; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        var hosts = Collections.list(request.getHeaders("Host"));
        if (hosts.size() != 1 || !settings.host().equalsIgnoreCase(hosts.getFirst())) {
            SafeResponses.refuse(response, 403, "REQUEST_ORIGIN_DENIED"); return;
        }
        var origins = Collections.list(request.getHeaders("Origin"));
        if ((!SAFE.contains(request.getMethod()) && origins.size() != 1)
                || (!origins.isEmpty() && (origins.size() != 1 || !settings.origin().equals(origins.getFirst())))) {
            SafeResponses.refuse(response, 403, "REQUEST_ORIGIN_DENIED"); return;
        }
        boolean protectedApi = request.getRequestURI().startsWith("/api/")
                && !request.getRequestURI().equals("/api/v1/capabilities");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String path=request.getRequestURI();
        boolean planPolling=request.getMethod().equals("GET") && (path.equals("/api/v1/destinations") || path.startsWith("/api/v1/plans/") || path.startsWith("/api/v1/operations/") || path.startsWith("/api/v3/plans/") || path.startsWith("/api/v3/operations/"));
        var lease = planPolling?sessions.capture(request):sessions.current(request);
        if (lease.isPresent() && (request.getRequestURI().equals("/oauth2/authorization/studio")
                || request.getRequestURI().equals("/login/oauth2/code/studio"))) {
            SafeResponses.refuse(response, 403, "SESSION_OWNER_ACTIVE"); return;
        }
        boolean matchingOwner = authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser user
                && lease.isPresent() && lease.get().owner().issuer().equals(user.getIssuer().toString())
                && lease.get().owner().subject().equals(user.getSubject());
        if ((protectedApi || !SAFE.contains(request.getMethod())) && !matchingOwner) {
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            SafeResponses.refuse(response, 401, "AUTHENTICATION_REQUIRED"); return;
        }
        if (matchingOwner) request.setAttribute(HostedSessions.REQUEST_LEASE, lease.orElseThrow());
        if (request.getMethod().equals("GET") && request.getRequestURI().equals("/oauth2/authorization/studio")
                && !sessions.reserveLogin(request)) {
            SafeResponses.refuse(response, 403, "SESSION_CAPACITY"); return;
        }
        chain.doFilter(request, response);
    }
}
