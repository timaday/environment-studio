package studio.environment.server.security;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "studio.mode=hosted", "studio.security.public-origin=http://localhost", "studio.security.client-id=mock-client",
        "studio.security.client-secret=mock-platform-secret", "studio.security.allow-test-http=true"})
@ActiveProfiles("oidc-test")
class HostedHealthProbeTest {
    static final MockIssuer issuer = new MockIssuer();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) { properties.add("studio.security.issuer", issuer::issuer); }
    @LocalServerPort int port;
    @TempDir Path classes;
    @AfterAll static void stopIssuer() { issuer.close(); }

    @Test void packagedProbeReachesActualHostedReadinessUsingApprovedHost() throws Exception {
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(),
                Path.of("../../deploy/HealthProbe.java").toString()));
        assertEquals(0, probe("http://localhost"), "Approved hosted probe must be healthy");
        assertEquals(1, probe("https://different.invalid"), "Ordinary Host policy must still refuse a wrong origin");
        assertEquals(1, probe("https://userinfo@localhost"), "Probe must refuse malformed origin locally");
    }

    private int probe(String origin) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-cp", classes.toString(), "HealthProbe");
        process.environment().put("STUDIO_MODE", "hosted");
        process.environment().put("STUDIO_SECURITY_PUBLIC_ORIGIN", origin);
        process.environment().put("SERVER_PORT", Integer.toString(port));
        process.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        var child = process.start();
        try {
            assertTrue(child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS), "Probe must finish within its budget");
            return child.exitValue();
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
}
