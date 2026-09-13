package studio.environment.server.security;

import java.net.URI;
import org.springframework.core.env.Environment;
import studio.environment.core.session.Owner;

final class LocalOperatorSettings {
    private final String username;
    private final String password;
    private final Owner owner;
    LocalOperatorSettings(Environment environment) {
        username = required(environment, "studio.security.local-operator.username");
        password = required(environment, "studio.security.local-operator.password");
        owner = new Owner(parseIssuer(required(environment, "studio.security.local-operator.issuer")),
                required(environment, "studio.security.local-operator.subject"));
    }
    private static String required(Environment environment, String key) {
        var value = environment.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("LOCAL_OPERATOR_CONFIGURATION_REQUIRED");
        return value;
    }
    private static String parseIssuer(String value) {
        URI uri;
        try { uri = URI.create(value); }
        catch (IllegalArgumentException exception) { throw new IllegalStateException("INVALID_LOCAL_OPERATOR_ISSUER"); }
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535
                || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback(uri.getHost()))))
            throw new IllegalStateException("INVALID_LOCAL_OPERATOR_ISSUER");
        return uri.toASCIIString();
    }
    static boolean loopback(String host) { return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host); }
    String username() { return username; }
    String password() { return password; }
    Owner owner() { return owner; }
}
