import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Process readiness only; it does not validate plans or database capabilities. */
public final class HealthProbe {
    public static void main(String[] args) {
        boolean healthy = false;
        try {
            int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
            if (port < 1 || port > 65535) throw new IllegalArgumentException();
            String mode = System.getenv().getOrDefault("STUDIO_MODE", "demo");
            String host = "127.0.0.1:" + port;
            if ("hosted".equals(mode)) {
                String configured = System.getenv("STUDIO_SECURITY_PUBLIC_ORIGIN");
                if (configured == null || configured.length() > 2048) throw new IllegalArgumentException();
                var origin = URI.create(configured);
                if (!("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))
                        || origin.getHost() == null || origin.getRawUserInfo() != null
                        || origin.getRawQuery() != null || origin.getRawFragment() != null
                        || !(origin.getRawPath().isEmpty() || "/".equals(origin.getRawPath()))
                        || origin.getPort() > 65535 || origin.getPort() == 0) throw new IllegalArgumentException();
                host = origin.getRawAuthority();
            } else if (!"demo".equals(mode)) throw new IllegalArgumentException();
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
                socket.getOutputStream().write(("GET /actuator/health/readiness HTTP/1.1\r\nHost: " + host
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                long deadline = System.nanoTime() + 2_000_000_000L;
                var status = new StringBuilder();
                for (int i = 0; i < 128; i++) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) break;
                    socket.setSoTimeout((int) Math.max(1, remaining / 1_000_000));
                    int c = socket.getInputStream().read();
                    if (c < 0) break;
                    if (c == '\n') {
                        healthy = status.toString().matches("HTTP/1\\.[01] 200(?: [^\\r\\n]*)?\\r");
                        break;
                    }
                    status.append((char) c);
                }
            }
        } catch (java.io.IOException | IllegalArgumentException ignored) {
            // A process probe reports only failure; configuration and network details stay private.
        }
        System.exit(healthy ? 0 : 1);
    }
}
