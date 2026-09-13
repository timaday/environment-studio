package studio.environment.server.security;

import java.net.URI;
import java.util.Arrays;
import org.springframework.core.env.Environment;

public final class HostedSettings {
    private final URI origin;
    private final URI issuer;
    private final String clientId;
    private final String clientSecret;
    private final boolean testHttp;
    private final boolean localOperator;

    public HostedSettings(Environment environment) {
        testHttp = Arrays.asList(environment.getActiveProfiles()).contains("oidc-test")
                && environment.getProperty("studio.security.allow-test-http", Boolean.class, false);
        localOperator = environment.getProperty("studio.security.local-operator.enabled", Boolean.class, false);
        if (environment.getProperty("studio.security.allow-test-http", Boolean.class, false) && !testHttp)
            throw new IllegalStateException("UNSUPPORTED_TRUST_CONFIGURATION");
        if (!"none".equals(environment.getProperty("server.forward-headers-strategy", "none")))
            throw new IllegalStateException("UNSUPPORTED_TRUST_CONFIGURATION");
        if (environment.getProperty("spring.http.log-request-details", Boolean.class, false)
                || environment.getProperty("spring.mvc.log-request-details", Boolean.class, false))
            throw new IllegalStateException("UNSUPPORTED_REQUEST_LOGGING");
        origin = parse(required(environment, "studio.security.public-origin"), true);
        boolean loopbackLocalHttp = localOperator && "http".equals(origin.getScheme()) && LocalOperatorSettings.loopback(origin.getHost());
        if ((!environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, true) && !loopbackLocalHttp)
                || !environment.getProperty("server.servlet.session.cookie.http-only", Boolean.class, true)
                || !"lax".equalsIgnoreCase(environment.getProperty("server.servlet.session.cookie.same-site", "lax"))
                || environment.getProperty("server.servlet.session.persistent", Boolean.class, false)
                || !"cookie".equals(environment.getProperty("server.servlet.session.tracking-modes", "cookie")))
            throw new IllegalStateException("UNSUPPORTED_SESSION_CONFIGURATION");
        if (localOperator) {
            issuer = null;
            clientId = null;
            clientSecret = null;
        } else {
            issuer = parse(required(environment, "studio.security.issuer"), false);
            clientId = required(environment, "studio.security.client-id");
            clientSecret = required(environment, "studio.security.client-secret");
        }
    }
    private static String required(Environment environment, String key) {
        var value = environment.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("HOSTED_CONFIGURATION_REQUIRED");
        return value;
    }
    private URI parse(String value, boolean originOnly) {
        URI uri;
        try { uri = URI.create(value); }
        catch (IllegalArgumentException exception) { throw new IllegalStateException("INVALID_HOSTED_URI"); }
        boolean secure = "https".equals(uri.getScheme());
        boolean localTest = testHttp && "http".equals(uri.getScheme())
                && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
        boolean localPilot = localOperator && originOnly && "http".equals(uri.getScheme())
                && LocalOperatorSettings.loopback(uri.getHost());
        if ((!secure && !localTest && !localPilot) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getPort() == 0
                || uri.getPort() > 65535 || (originOnly && !uri.getRawPath().isEmpty()))
            throw new IllegalStateException("INVALID_HOSTED_URI");
        return uri;
    }
    public void validateProviderEndpoint(String endpoint) { parse(endpoint, false); }
    public String origin() { return origin.toASCIIString(); }
    public String host() { return origin.getRawAuthority(); }
    boolean allowsHost(String value) {
        if (host().equalsIgnoreCase(value)) return true;
        if (!localOperator || !isLoopbackLocalOrigin()) return false;
        var candidate = parseAuthority(value);
        return candidate != null && sameLocalOperatorEndpoint(candidate);
    }
    boolean allowsOrigin(String value) {
        if (origin().equals(value)) return true;
        if (!localOperator || !isLoopbackLocalOrigin()) return false;
        URI candidate;
        try { candidate = URI.create(value); }
        catch (IllegalArgumentException exception) { return false; }
        return "http".equals(candidate.getScheme()) && candidate.getRawPath().isEmpty()
                && candidate.getUserInfo() == null && candidate.getQuery() == null && candidate.getFragment() == null
                && sameLocalOperatorEndpoint(candidate);
    }
    private boolean isLoopbackLocalOrigin() {
        return "http".equals(origin.getScheme()) && LocalOperatorSettings.loopback(origin.getHost());
    }
    private boolean sameLocalOperatorEndpoint(URI candidate) {
        return LocalOperatorSettings.loopback(candidate.getHost()) && effectivePort(candidate) == effectivePort(origin);
    }
    private int effectivePort(URI uri) { return uri.getPort() == -1 ? defaultPort(uri.getScheme()) : uri.getPort(); }
    private int defaultPort(String scheme) { return "https".equals(scheme) ? 443 : 80; }
    private URI parseAuthority(String value) {
        if (value == null || value.isBlank() || value.contains("/")) return null;
        try { return URI.create("http://" + value); }
        catch (IllegalArgumentException exception) { return null; }
    }
    public String issuer() { return issuer.toASCIIString(); }
    public String clientId() { return clientId; }
    String clientSecret() { return clientSecret; }
    boolean localOperator() { return localOperator; }
}
