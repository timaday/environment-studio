package studio.environment.server.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(properties = {
        "studio.mode=hosted",
        "studio.security.public-origin=http://localhost:18181",
        "studio.security.local-operator.enabled=true",
        "studio.security.local-operator.username=operator",
        "studio.security.local-operator.password=local-password-canary",
        "server.servlet.session.cookie.secure=false",
        "studio.security.local-operator.issuer=https://local-operator.invalid",
        "studio.security.local-operator.subject=pilot-operator" })
class LocalOperatorSecurityTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void missingCredentialsChallengeWithoutOidcDiscovery() throws Exception {
        mvc.perform(get("/api/v1/session").header("Host", "localhost:18181"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Environment Studio")))
                .andExpect(content().string(containsString("AUTHENTICATION_REQUIRED")));
    }

    @Test void localBasicCredentialsCreateHostedSessionAndCsrfAuthority() throws Exception {
        mvc.perform(get("/api/v1/session").header("Host", "localhost:18181")
                        .with(httpBasic("operator", "local-password-canary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.csrfHeaderName").exists())
                .andExpect(jsonPath("$.csrfToken").exists());
    }

    @Test void localSigninPathUsesBasicThenReturnsToOriginAndStoresSession() throws Exception {
        var result = mvc.perform(get("/oauth2/authorization/studio").header("Host", "localhost:18181")
                        .with(httpBasic("operator", "local-password-canary")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:18181/"))
                .andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/v1/session").header("Host", "localhost:18181").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test void localLoopbackAliasCanLoadPublicCapabilities() throws Exception {
        mvc.perform(get("/api/v1/capabilities").header("Host", "127.0.0.1:18181")
                        .header("Origin", "http://127.0.0.1:18181"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("hosted"));
        mvc.perform(get("/api/v1/capabilities").header("Host", "localhost:18181")
                        .header("Origin", "http://evil.invalid"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("REQUEST_ORIGIN_DENIED")));
    }

    @Test void wrongLocalCredentialsDoNotCreateSession() throws Exception {
        mvc.perform(get("/api/v1/session").header("Host", "localhost:18181")
                        .with(httpBasic("operator", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Environment Studio")))
                .andExpect(content().string(containsString("LOCAL_OPERATOR_AUTHENTICATION_FAILED")));
    }
}
