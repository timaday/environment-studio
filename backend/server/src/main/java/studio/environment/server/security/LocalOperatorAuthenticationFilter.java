package studio.environment.server.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.HostedSessions;

final class LocalOperatorAuthenticationFilter extends OncePerRequestFilter {
    private static final String BASIC = "Basic ";
    private final HostedSettings hosted;
    private final HostedSessions sessions;
    private final LocalOperatorSettings local;
    LocalOperatorAuthenticationFilter(HostedSettings hosted, HostedSessions sessions, LocalOperatorSettings local) {
        this.hosted = hosted; this.sessions = sessions; this.local = local;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BASIC) && !acceptsBasic(request)) {
            chain.doFilter(request, response); return;
        }
        if (header == null || !header.startsWith(BASIC)) {
            var lease = sessions.current(request);
            if (lease.isPresent() && lease.get().owner().equals(local.owner())) {
                authenticateLocalOperator();
                chain.doFilter(request, response); return;
            }
            if (request.getMethod().equals("GET") && request.getRequestURI().equals("/oauth2/authorization/studio")) { challenge(response, "AUTHENTICATION_REQUIRED"); return; }
            chain.doFilter(request, response); return;
        }
        var credentials = parse(header.substring(BASIC.length()));
        if (credentials == null || !constantEquals(credentials.username, local.username()) || !constantEquals(credentials.password, local.password())) {
            var session = request.getSession(false); if (session != null) session.invalidate();
            SecurityContextHolder.clearContext(); challenge(response, "LOCAL_OPERATOR_AUTHENTICATION_FAILED"); return;
        }
        var lease = sessions.current(request);
        if (lease.isEmpty()) {
            if (!sessions.reserveLogin(request)) { SafeResponses.refuse(response, 403, "SESSION_CAPACITY"); return; }
            var admission = sessions.replaceAuthenticated(request.getSession(), local.owner());
            if (admission instanceof SessionLedger.Denied denied) {
                request.getSession().invalidate(); SecurityContextHolder.clearContext();
                SafeResponses.refuse(response, 403, "SESSION_" + denied.reason().name()); return;
            }
            request.changeSessionId();
            lease = sessions.current(request);
        }
        if (lease.isEmpty() || !lease.get().owner().equals(local.owner())) {
            var session = request.getSession(false); if (session != null) session.invalidate();
            SecurityContextHolder.clearContext(); SafeResponses.refuse(response, 401, "AUTHENTICATION_REQUIRED"); return;
        }
        authenticateLocalOperator();
        if (request.getMethod().equals("GET") && request.getRequestURI().equals("/oauth2/authorization/studio")) {
            response.setHeader("Cache-Control", "no-store"); response.sendRedirect(hosted.origin() + "/"); return;
        }
        chain.doFilter(request, response);
    }
    private boolean acceptsBasic(HttpServletRequest request) {
        var path = request.getRequestURI();
        return path.equals("/api/v1/session") || path.equals("/oauth2/authorization/studio")
                || (path.startsWith("/api/") && !path.equals("/api/v1/capabilities"));
    }
    private void authenticateLocalOperator() {
        var authentication = new UsernamePasswordAuthenticationToken(new LocalOperatorPrincipal(local.username(), local.owner()), "PROTECTED", AuthorityUtils.createAuthorityList("ROLE_OPERATOR"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    private static Credentials parse(String encoded) {
        try {
            var decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int split = decoded.indexOf(':');
            if (split < 1) return null;
            return new Credentials(decoded.substring(0, split), decoded.substring(split + 1));
        } catch (IllegalArgumentException exception) { return null; }
    }
    private static boolean constantEquals(String candidate, String expected) {
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
    private static void challenge(HttpServletResponse response, String code) throws IOException {
        response.setHeader("WWW-Authenticate", "Basic realm=\"Environment Studio\", charset=\"UTF-8\"");
        SafeResponses.refuse(response, 401, code);
    }
    private record Credentials(String username, String password) { }
}
