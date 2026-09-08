package studio.environment.server.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.net.URI;
import java.net.http.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {"studio.mode=hosted", "studio.security.public-origin=http://localhost",
        "studio.security.client-id=mock-client", "studio.security.client-secret=mock-platform-secret", "studio.security.allow-test-http=true"})
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@ActiveProfiles("oidc-test")
class HostedBoundaryTest {
    static final MockIssuer issuer = new MockIssuer();
    static final java.nio.file.Path workspace = initializeMockWorkspace();
    static java.nio.file.Path initializeMockWorkspace() {
        try {
            var directory = java.nio.file.Files.createTempDirectory("es-protocol-workspace-mock-", java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
            studio.environment.server.workspace.SqliteDraftStore.initialize(directory);
            return directory;
        } catch (java.io.IOException failure) { throw new IllegalStateException("MOCK_WORKSPACE_UNAVAILABLE"); }
    }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) { properties.add("studio.security.issuer", issuer::issuer); properties.add("studio.workspace.directory", () -> workspace.toString()); properties.add("studio.workspace.definition-publishers[0].issuer", issuer::issuer); properties.add("studio.workspace.definition-publishers[0].subject", () -> "workspace-maintainer"); }
    @Autowired WebApplicationContext context;
    @Autowired CleanupProbe cleanupProbe;
    @org.springframework.boot.test.context.TestConfiguration
    static class CleanupTestConfiguration {
        @org.springframework.context.annotation.Bean CleanupProbe cleanupProbe() { return new CleanupProbe(); }
    }
    static final class CleanupProbe implements studio.environment.server.session.SessionCleanup {
        volatile boolean fail;
        public void invalidate(studio.environment.core.session.SessionLedger.Lease lease) {
            if (fail) throw new IllegalStateException("synthetic-cleanup-canary");
        }
    }
    @AfterEach void providerCredentialsNeverEnterCapturedLogs(org.springframework.boot.test.system.CapturedOutput output) {
        cleanupProbe.fail = false;
        try {
            String persisted=new String(java.nio.file.Files.readAllBytes(workspace.resolve("studio-workspace.db")),java.nio.charset.StandardCharsets.ISO_8859_1);
            for(String canary:java.util.List.of("mock-platform-secret","mock-access-canary"))assertFalse(persisted.contains(canary),"Authentication canary entered workspace persistence");
            for(String token:issuer.issuedTokens)assertFalse(persisted.contains(token),"ID token entered workspace persistence");
        }catch(java.io.IOException failure){throw new AssertionError("Mock persistence canary scan unavailable");}

        assertFalse(output.getAll().contains("workspace-source-canary"), "Synthetic workspace source leaked to logs");
        for (String token : csrfCanaries) assertFalse(output.getAll().contains(token.substring(0, 12)), "Session CSRF token prefix leaked to logs");
        assertFalse(output.getAll().contains("mock-platform-secret"), "Synthetic client secret leaked to logs");
        assertFalse(output.getAll().contains("mock-access-canary"), "Synthetic access token leaked to logs");
        String basic = java.util.Base64.getEncoder().encodeToString("mock-client:mock-platform-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(output.getAll().contains(basic), "Encoded synthetic client authentication leaked to logs");
        for (String token : issuer.issuedTokens) {
            assertFalse(output.getAll().contains(token), "Synthetic ID token leaked to logs");
            assertFalse(output.getAll().contains(token.substring(0, 80)), "Truncated synthetic ID token leaked to logs");
        }
    }
    MockMvc mvc;
    final java.util.List<String> csrfCanaries = new java.util.ArrayList<>();
    @BeforeEach void setup() {
        issuer.mode = MockIssuer.TokenMode.VALID;
        issuer.subject = "invented-" + java.util.UUID.randomUUID();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stopIssuer() { issuer.close(); }
    @Test void nativeWorkspaceUsesRealOidcSessionCsrfOwnerAndMaintainerAuthority() throws Exception {
        issuer.subject="workspace-maintainer";
        var login=login(false);assertEquals(302,login.callback.getResponse().getStatus());assertTrue(issuer.verifiedPkce);
        var session=mvc.perform(get("/api/v1/session").header("Host","localhost").session(login.session)).andExpect(status().isOk()).andReturn();
        var csrf=(org.springframework.security.web.csrf.CsrfToken)session.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());csrfCanaries.add(csrf.getToken());
        String id=java.util.UUID.randomUUID().toString(),path="/api/v2/definitions/"+id;
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        String source=java.nio.file.Files.readString(java.nio.file.Path.of("../../fixtures/native-v2/definition.json")).replaceFirst("\"label\": \"[^\"]+\"","\"label\": \"workspace-source-canary\"");
        String command=json.writeValueAsString(Map.of("expectedRevision","0","requestId",java.util.UUID.randomUUID().toString(),"format","JSON","source",source));
        mvc.perform(put(path).header("Host","localhost").header("Origin","http://localhost").session(login.session).contentType("application/json").content(command)).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Host","localhost").header("Origin","https://wrong.invalid").header(csrf.getHeaderName(),csrf.getToken()).session(login.session).contentType("application/json").content(command)).andExpect(status().isForbidden());
        var saved=mvc.perform(put(path).header("Host","localhost").header("Origin","http://localhost").header(csrf.getHeaderName(),csrf.getToken()).session(login.session).contentType("application/json").content(command))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.state").value("draft")).andExpect(jsonPath("$.workspaceRevision").value("1")).andReturn();
        var body=json.readTree(saved.getResponse().getContentAsString());
        var policies=new java.util.ArrayList<Map<String,String>>();for(var binding:body.get("projection").get("model").get("bindings"))for(var document:binding.get("documents"))policies.add(Map.of("bindingId",binding.get("id").asString(),"documentId",document.get("id").asString(),"content","deny"));
        String publication=json.writeValueAsString(Map.of("expectedRevision","1","requestId",java.util.UUID.randomUUID().toString(),"exportPolicies",policies));
        mvc.perform(post(path+"/publish").header("Host","localhost").header("Origin","http://localhost").header(csrf.getHeaderName(),csrf.getToken()).session(login.session).contentType("application/json").content(publication))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("published")).andExpect(jsonPath("$.publication.sourceRevision").value("1"));
        mvc.perform(get("/api/v2/definitions").header("Host","localhost").session(login.session)).andExpect(status().isOk()).andExpect(jsonPath("$.canPublish").value(true));
        String profile=java.nio.file.Files.readString(java.nio.file.Path.of("../../fixtures/profile-v2/profile.json"));
        var profileTree=(tools.jackson.databind.node.ObjectNode)json.readTree(profile);profileTree.put("logicalDefinitionDigest",body.get("projection").get("logicalDigest").asString());
        String profileId=java.util.UUID.randomUUID().toString();String profilePath="/api/v2/profiles/"+profileId;
        String profileCommand=json.writeValueAsString(Map.of("expectedRevision","0","requestId",java.util.UUID.randomUUID().toString(),"format","JSON","source",json.writeValueAsString(profileTree),"definition",Map.of("objectId",id,"workspaceRevision","2")));
        mvc.perform(put(profilePath).header("Host","localhost").header("Origin","http://localhost").header(csrf.getHeaderName(),csrf.getToken()).session(login.session).contentType("application/json").content(profileCommand)).andExpect(status().isOk()).andExpect(jsonPath("$.projection.model.revision").value("1"));
        mvc.perform(post(profilePath+"/publish").header("Host","localhost").header("Origin","http://localhost").header(csrf.getHeaderName(),csrf.getToken()).session(login.session).contentType("application/json").content(json.writeValueAsString(Map.of("expectedRevision","1","requestId",java.util.UUID.randomUUID().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("published"));
        issuer.subject="workspace-nonmaintainer-"+java.util.UUID.randomUUID();var other=login(false);
        var otherSession=mvc.perform(get("/api/v1/session").header("Host","localhost").session(other.session)).andReturn();var otherCsrf=(org.springframework.security.web.csrf.CsrfToken)otherSession.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());csrfCanaries.add(otherCsrf.getToken());
        mvc.perform(get(path).header("Host","localhost").session(other.session)).andExpect(status().isNotFound());
        mvc.perform(post(path+"/publish").header("Host","localhost").header("Origin","http://localhost").header(otherCsrf.getHeaderName(),otherCsrf.getToken()).header("X-Role","maintainer").session(other.session).contentType("application/json").content(publication)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/definitions").header("Host","localhost").session(other.session)).andExpect(status().isOk()).andExpect(jsonPath("$.canPublish").value(false));
    }
    @Test void anonymousSessionApiReturnsSafeJson401() throws Exception {
        mvc.perform(get("/api/v1/session").header("Host", "localhost"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"AUTHENTICATION_REQUIRED\"}"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
    record Login(MockHttpSession session, String oldId, MvcResult callback) { }
    Login login(boolean corruptState) throws Exception {
        var start = mvc.perform(get("/oauth2/authorization/studio").header("Host", "localhost")
                .header("X-Forwarded-Host", "attacker.invalid").header("X-User", "forged"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var session = (MockHttpSession) start.getRequest().getSession(false);
        String oldId = session.getId();
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(start.getResponse().getRedirectedUrl())).build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(302, response.statusCode());
        var callback = URI.create(response.headers().firstValue("Location").orElseThrow());
        assertEquals("http://localhost/login/oauth2/code/studio", callback.getScheme() + "://" + callback.getAuthority() + callback.getPath());
        Map<String, String> params = MockIssuer.parameters(callback.getRawQuery());
        var completed = mvc.perform(get(callback.getPath()).header("Host", "localhost").session(session)
                .param("code", params.get("code")).param("state", corruptState ? "wrong" : params.get("state"))).andReturn();
        return new Login(session, oldId, completed);
    }
    @Test void realCodeExchangeVerifiesPkceRotatesSessionAndLogoutRequiresOriginAndCsrf() throws Exception {
        var login = login(false);
        assertEquals(302, login.callback.getResponse().getStatus());
        assertNotEquals(login.oldId, login.session.getId());
        assertTrue(issuer.verifiedPkce);
        mvc.perform(post("/api/v1/session/logout").header("Host", "localhost").header("Origin", "http://localhost").session(login.session)).andExpect(status().isForbidden());
        var result = mvc.perform(get("/api/v1/session").header("Host", "localhost").session(login.session))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.idleTimeoutSeconds").value(1800))
                .andExpect(jsonPath("$.absoluteExpiresAt").exists()).andExpect(jsonPath("$.csrfToken").exists()).andReturn();
        var csrf = (org.springframework.security.web.csrf.CsrfToken) result.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        csrfCanaries.add(csrf.getToken());
        assertFalse(result.getResponse().getContentAsString().contains("mock-access-canary"));
        assertFalse(result.getResponse().getContentAsString().contains("mock-platform-secret"));
        mvc.perform(post("/api/v1/session/logout").header("Host", "localhost").header("Origin", "https://wrong.invalid")
                .header(csrf.getHeaderName(), csrf.getToken()).session(login.session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/session/logout").header("Host", "localhost").header(csrf.getHeaderName(), csrf.getToken()).session(login.session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/session/logout").header("Host", "localhost").header("Origin", "http://localhost")
                .header(csrf.getHeaderName(), "wrong").session(login.session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/session/logout").header("Host", "localhost").header("Origin", "http://localhost")
                .header(csrf.getHeaderName(), csrf.getToken()).session(login.session)).andExpect(status().isNoContent());
        assertTrue(login.session.isInvalid());
    }
    @Test void invalidStateAndAllIdTokenAuthorityFailuresAreRefused() throws Exception {
        assertEquals(401, login(true).callback.getResponse().getStatus());
        for (var mode : MockIssuer.TokenMode.values()) {
            if (mode == MockIssuer.TokenMode.VALID) continue;
            issuer.mode = mode;
            var rejected = login(false);
            assertEquals(401, rejected.callback.getResponse().getStatus(), mode.name());
            assertEquals("{\"code\":\"LOGIN_FAILED\"}", rejected.callback.getResponse().getContentAsString());
            assertTrue(rejected.session.isInvalid());
        }
    }
    @Test void hostAndForgedIdentityDoNotSupplyAuthorityAndUnknownApiIsDenied() throws Exception {
        mvc.perform(get("/api/v1/session").header("Host", "attacker.invalid")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/session").header("Host", "localhost").header("X-User", "invented-owner")
                .header("X-Forwarded-User", "invented-owner")).andExpect(status().isUnauthorized());
        var login = login(false);
        mvc.perform(get("/api/v1/unimplemented").header("Host", "localhost").session(login.session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/capabilities").header("Host", "localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("hosted"))
                .andExpect(jsonPath("$.inspectionEnabled").value(false)).andExpect(jsonPath("$.exportEnabled").value(false));
    }
    @Test void unsolicitedCallbackAndReloginCannotInvalidateActiveWork() throws Exception {
        var active = login(false);
        mvc.perform(get("/oauth2/authorization/studio").header("Host", "localhost").session(active.session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/login/oauth2/code/studio").header("Host", "localhost").session(active.session)
                .param("code", "unsolicited").param("state", "unsolicited"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/session").header("Host", "localhost").session(active.session)).andExpect(status().isOk());
    }
    @Test void secondLiveSessionForSameOwnerIsRefusedWithoutClosingFirst() throws Exception {
        var first = login(false);
        var second = login(false);
        assertEquals(403, second.callback.getResponse().getStatus());
        assertTrue(second.session.isInvalid());
        mvc.perform(get("/api/v1/session").header("Host", "localhost").session(first.session)).andExpect(status().isOk());
    }
    @Test void fullCapacityRefusesBeforeAllocationAndFailedCallbackReleasesReservation() throws Exception {
        var pending = new java.util.ArrayList<MockHttpSession>();
        try {
            for (int i = 0; i < 64; i++) {
                var started = mvc.perform(get("/oauth2/authorization/studio").header("Host", "localhost")).andReturn();
                if (started.getResponse().getStatus() == 403) break;
                assertEquals(302, started.getResponse().getStatus());
                pending.add((MockHttpSession) started.getRequest().getSession(false));
            }
            var refused = mvc.perform(get("/oauth2/authorization/studio").header("Host", "localhost"))
                    .andExpect(status().isForbidden()).andExpect(content().json("{\"code\":\"SESSION_CAPACITY\"}"))
                    .andReturn();
            assertNull(refused.getRequest().getSession(false));
            assertNull(refused.getResponse().getHeader("Set-Cookie"));
            var existing = pending.getFirst();
            mvc.perform(get("/oauth2/authorization/studio").header("Host", "localhost").session(existing)).andExpect(status().is3xxRedirection());
            mvc.perform(get("/login/oauth2/code/studio").header("Host", "localhost").session(existing)
                    .param("code", "invalid").param("state", "invalid")).andExpect(status().isUnauthorized());
            assertTrue(existing.isInvalid());
            var replacement = mvc.perform(get("/oauth2/authorization/studio").header("Host", "localhost"))
                    .andExpect(status().is3xxRedirection()).andReturn();
            pending.add((MockHttpSession) replacement.getRequest().getSession(false));
        } finally { pending.forEach(session -> { if (!session.isInvalid()) session.invalidate(); }); }
    }
    @Test void anonymousUnsafeAndDefaultLoginPageCannotCreateUnbudgetedSessions() throws Exception {
        var post = mvc.perform(post("/unimplemented").header("Host", "localhost").header("Origin", "http://localhost"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertNull(post.getRequest().getSession(false));
        var page = mvc.perform(get("/login").header("Host", "localhost")).andExpect(status().isUnauthorized()).andReturn();
        assertNull(page.getRequest().getSession(false));
    }

    @Test void logoutReportsInconclusiveCleanupWhileRevokingSessionAndQuarantiningOwner() throws Exception {
        var login = login(false);
        var info = mvc.perform(get("/api/v1/session").header("Host", "localhost").session(login.session))
                .andExpect(status().isOk()).andReturn();
        var csrf = (org.springframework.security.web.csrf.CsrfToken) info.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        csrfCanaries.add(csrf.getToken());
        cleanupProbe.fail = true;
        mvc.perform(post("/api/v1/session/logout").header("Host", "localhost").header("Origin", "http://localhost")
                .header(csrf.getHeaderName(), csrf.getToken()).session(login.session))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"code\":\"SESSION_CLEANUP_INCONCLUSIVE\"}"));
        assertTrue(login.session.isInvalid());
        assertEquals(403, login(false).callback.getResponse().getStatus());
    }


    @Test void workspaceUsesRealSessionCsrfOriginAndSafeBodyLogging() throws Exception {
        String path = "/api/v1/definitions/" + java.util.UUID.randomUUID();
        mvc.perform(get(path).header("Host", "localhost")).andExpect(status().isUnauthorized());
        var login = login(false);
        var info = mvc.perform(get("/api/v1/session").header("Host", "localhost").session(login.session)).andReturn();
        var csrf = (org.springframework.security.web.csrf.CsrfToken) info.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        csrfCanaries.add(csrf.getToken());
        // Independently invented declarations; no application/private model provenance.
        String source = "{\"schemaVersion\":\"1\",\"id\":\"workspace-source-canary\",\"revision\":1,\"status\":\"draft\",\"entityTypes\":[{\"id\":\"mote\",\"label\":\"Invented\",\"fields\":[]}],\"relations\":[],\"documents\":[{\"id\":\"sample\",\"logicalStore\":\"invented-store\",\"recordKey\":\"invented-key\",\"namespaces\":{},\"mappings\":[]}],\"requiredRules\":[\"mock-rule\"],\"operationCapabilities\":[]}";
        String body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("expectedRevision", "0", "requestId", java.util.UUID.randomUUID().toString(), "format", "JSON", "source", source));
        mvc.perform(put(path).header("Host", "localhost").header("Origin", "http://localhost").session(login.session).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Host", "localhost").header("Origin", "https://wrong.invalid").header(csrf.getHeaderName(), csrf.getToken()).session(login.session).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Host", "localhost").header("Origin", "http://localhost").header(csrf.getHeaderName(), csrf.getToken()).session(login.session).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.projection.kind").value("incomplete")).andExpect(jsonPath("$.workspaceRevision").value("1"));
        mvc.perform(get(path).header("Host", "localhost").session(login.session)).andExpect(status().isOk()).andExpect(jsonPath("$.source").value(source));
        mvc.perform(get("/api/v1/capabilities").header("Host", "localhost")).andExpect(jsonPath("$.definitionWorkspaceEnabled").value(true)).andExpect(jsonPath("$.exportEnabled").value(false));
    }
}
