package studio.environment.server.security;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class HostedSettingsTest {
    MockEnvironment valid() { return new MockEnvironment()
            .withProperty("studio.security.public-origin", "https://studio.invalid")
            .withProperty("studio.security.issuer", "https://issuer.invalid/realm")
            .withProperty("studio.security.client-id", "invented-client")
            .withProperty("studio.security.client-secret", "mock-secret-canary"); }
    @Test void invalidPublicOriginsAndMissingConfigurationRefuseWithoutEchoingInput() {
        for (String uri : new String[]{"http://studio.invalid", "https://user@studio.invalid", "https://studio.invalid/path",
                "https://studio.invalid?value=canary", "https://studio.invalid#canary", "not a uri", "https://studio.invalid:70000"}) {
            var failure = assertThrows(IllegalStateException.class, () -> new HostedSettings(valid().withProperty("studio.security.public-origin", uri)));
            assertEquals("INVALID_HOSTED_URI", failure.getMessage());
        }
        assertEquals("HOSTED_CONFIGURATION_REQUIRED", assertThrows(IllegalStateException.class, () -> new HostedSettings(new MockEnvironment())).getMessage());
    }
    @Test void forwardingAndTestHttpCannotBeEnabledOutsideExplicitTestProfile() {
        assertThrows(IllegalStateException.class, () -> new HostedSettings(valid().withProperty("server.forward-headers-strategy", "framework")));
        assertThrows(IllegalStateException.class, () -> new HostedSettings(valid().withProperty("studio.security.allow-test-http", "true")));
        var test = valid().withProperty("studio.security.allow-test-http", "true")
                .withProperty("studio.security.public-origin", "http://localhost");
        test.setActiveProfiles("oidc-test");
        assertEquals("http://localhost", new HostedSettings(test).origin());
        assertThrows(IllegalStateException.class, () -> new HostedSettings(test.withProperty("studio.security.issuer", "http://remote.invalid")));
    }
    @Test void unknownModeCannotChooseAnonymousOrDemoFallback() {
        assertThrows(IllegalStateException.class, () -> new RuntimeConfiguration().runtimeMode("production"));
        assertEquals(RuntimeConfiguration.RuntimeMode.HOSTED, new RuntimeConfiguration().runtimeMode("hosted"));
    }
    @Test void insecureOrPersistentSessionOverridesRefuseStartup() {
        for (var setting : java.util.Map.of("server.servlet.session.cookie.secure", "false",
                "server.servlet.session.cookie.http-only", "false", "server.servlet.session.cookie.same-site", "none",
                "server.servlet.session.persistent", "true", "server.servlet.session.tracking-modes", "url").entrySet()) {
            assertEquals("UNSUPPORTED_SESSION_CONFIGURATION", assertThrows(IllegalStateException.class,
                    () -> new HostedSettings(valid().withProperty(setting.getKey(), setting.getValue()))).getMessage());
        }
    }

    @Test void detailedRequestLoggingRefusesHostedStartup() {
        for (String property : java.util.List.of("spring.http.log-request-details", "spring.mvc.log-request-details")) {
            assertEquals("UNSUPPORTED_REQUEST_LOGGING", assertThrows(IllegalStateException.class,
                    () -> new HostedSettings(valid().withProperty(property, "true"))).getMessage());
        }
    }

}
