package studio.environment.server.session;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "studio.mode", havingValue = "hosted")
public final class SessionController {
    private final HostedSessions sessions;
    public SessionController(HostedSessions sessions) { this.sessions = sessions; }
    public record SessionView(boolean authenticated, String csrfHeaderName, String csrfToken,
                              int idleTimeoutSeconds, Instant absoluteExpiresAt) {
        // Spring's DEBUG response-converter logging calls toString; never expose the session CSRF token.
        @Override public String toString() { return "SessionView[authenticated=true, csrfToken=REDACTED]"; }
    }
    @GetMapping("/api/v1/session")
    public SessionView session(HttpServletRequest request, CsrfToken csrf) {
        var lease = sessions.current(request).orElseThrow(() -> new IllegalStateException("SESSION_AUTHORITY_REQUIRED"));
        return new SessionView(true, csrf.getHeaderName(), csrf.getToken(), 1800, lease.absoluteExpiresAt());
    }
}
