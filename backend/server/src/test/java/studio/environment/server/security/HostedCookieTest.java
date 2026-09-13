package studio.environment.server.security;

import static org.junit.jupiter.api.Assertions.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "studio.mode=hosted", "studio.security.public-origin=http://localhost", "studio.security.client-id=mock-client",
        "studio.security.client-secret=mock-platform-secret", "studio.security.allow-test-http=true"})
@ActiveProfiles("oidc-test")
class HostedCookieTest {
    static final MockIssuer issuer = new MockIssuer();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) { properties.add("studio.security.issuer", issuer::issuer); }
    @LocalServerPort int port;
    @AfterAll static void stopIssuer() { issuer.close(); }
    @Test void actualServletLoginCookieIsSecureHttpOnlySameSiteAndNeverCarriesProviderToken() throws Exception {
        try (var socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET /oauth2/authorization/studio HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String cookie = response.lines().filter(line -> line.toLowerCase(java.util.Locale.ROOT).startsWith("set-cookie:")).findFirst().orElseThrow();
            assertTrue(cookie.contains("JSESSIONID="));
            assertTrue(cookie.contains("Secure"));
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("SameSite=Lax"));
            assertFalse(response.contains("mock-platform-secret"));
            assertFalse(response.contains("mock-access-canary"));
        }
    }
}
