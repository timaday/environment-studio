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

    @Test void localOperatorModeSkipsOidcDiscoveryButRequiresExplicitLocalOwner() {
        var environment = new MockEnvironment()
                .withProperty("studio.security.public-origin", "http://127.0.0.1:18181")
                .withProperty("studio.security.local-operator.enabled", "true")
                .withProperty("server.servlet.session.cookie.secure", "false");
        assertEquals("http://127.0.0.1:18181", new HostedSettings(environment).origin());
        assertEquals("LOCAL_OPERATOR_CONFIGURATION_REQUIRED", assertThrows(IllegalStateException.class,
                () -> new LocalOperatorSettings(environment)).getMessage());
        var local = new LocalOperatorSettings(environment
                .withProperty("studio.security.local-operator.username", "operator")
                .withProperty("studio.security.local-operator.password", "local-password-canary")
                .withProperty("studio.security.local-operator.issuer", "https://local-operator.invalid")
                .withProperty("studio.security.local-operator.subject", "pilot-operator"));
        assertEquals("pilot-operator", local.owner().subject());
    }

    @Test void localOperatorHttpOriginIsLoopbackOnly() {
        var environment = new MockEnvironment()
                .withProperty("studio.security.local-operator.enabled", "true")
                .withProperty("studio.security.public-origin", "http://studio.invalid");
        assertEquals("INVALID_HOSTED_URI", assertThrows(IllegalStateException.class,
                () -> new HostedSettings(environment)).getMessage());
    }

    @Test void localOperatorDoesNotPermitInsecureCookiesForHttpsOrRemoteHttp() {
        var https = new MockEnvironment()
                .withProperty("studio.security.local-operator.enabled", "true")
                .withProperty("studio.security.public-origin", "https://studio.invalid")
                .withProperty("server.servlet.session.cookie.secure", "false");
        assertEquals("UNSUPPORTED_SESSION_CONFIGURATION", assertThrows(IllegalStateException.class,
                () -> new HostedSettings(https)).getMessage());
    }

    @Test void localOperatorAcceptsEquivalentLoopbackAliasesOnTheSamePortOnly() {
        var settings = new HostedSettings(new MockEnvironment()
                .withProperty("studio.security.local-operator.enabled", "true")
                .withProperty("studio.security.public-origin", "http://localhost:18181")
                .withProperty("server.servlet.session.cookie.secure", "false"));
        assertTrue(settings.allowsHost("localhost:18181"));
        assertTrue(settings.allowsHost("127.0.0.1:18181"));
        assertTrue(settings.allowsOrigin("http://localhost:18181"));
        assertTrue(settings.allowsOrigin("http://127.0.0.1:18181"));
        assertFalse(settings.allowsHost("localhost:18180"));
        assertFalse(settings.allowsHost("studio.invalid:18181"));
        assertFalse(settings.allowsOrigin("http://127.0.0.1:18180"));
        assertFalse(settings.allowsOrigin("http://studio.invalid:18181"));
        assertFalse(settings.allowsOrigin("null"));
    }

    @Test void oidcHostedModeKeepsExactHostAndOriginMatching() {
        var settings = new HostedSettings(valid().withProperty("studio.security.public-origin", "https://studio.invalid:8443"));
        assertTrue(settings.allowsHost("studio.invalid:8443"));
        assertTrue(settings.allowsOrigin("https://studio.invalid:8443"));
        assertFalse(settings.allowsHost("127.0.0.1:8443"));
        assertFalse(settings.allowsOrigin("https://127.0.0.1:8443"));
    }

}
