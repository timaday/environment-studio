package studio.environment.server.security;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import studio.environment.server.plan.PlanHttpSocketClient;
import studio.environment.server.workspace.SqliteDraftStore;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"studio.mode=hosted","studio.security.public-origin=http://localhost","studio.security.client-id=mock-client","studio.security.client-secret=mock-platform-secret","studio.security.allow-test-http=true","logging.level.org.springframework.web=DEBUG"})
@ActiveProfiles("oidc-test")
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
class V3ProfileBoundaryTest {
    static final MockIssuer issuer=new MockIssuer();
    static final Path workspace=workspace();
    static final tools.jackson.databind.json.JsonMapper JSON=tools.jackson.databind.json.JsonMapper.builder().build();
    @LocalServerPort int port;
    @org.springframework.beans.factory.annotation.Autowired studio.environment.server.workspace.WorkspaceRuntime runtime;
    @org.springframework.boot.test.context.TestConfiguration
    static class SocketConfiguration {
        @org.springframework.context.annotation.Bean org.springframework.boot.web.server.WebServerFactoryCustomizer<org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory> smallSendBuffer(){
            return factory->factory.addConnectorCustomizers(connector->connector.setProperty("socket.txBufSize","8192"));
        }
    }
    static Path workspace(){try{var result=Files.createTempDirectory("es-v3-profile-http-mock-");SqliteDraftStore.initializeV3(result);return result;}catch(Exception failure){throw new AssertionError("MOCK_SETUP_REFUSED");}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("studio.security.issuer",issuer::issuer);registry.add("studio.workspace.directory",workspace::toString);}
    @AfterAll static void close() throws Exception {
        issuer.close();
        try (var files = Files.list(workspace)) { for (var file : files.toList()) Files.delete(file); }
        Files.delete(workspace);
    }
    PlanHttpSocketClient login(String subject)throws Exception {
        issuer.subject=subject;issuer.mode=MockIssuer.TokenMode.VALID;
        var client=new PlanHttpSocketClient(port);var start=client.get("/oauth2/authorization/studio");assertEquals(302,start.status());
        var provider=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(start.headers().get("location"))).build(),HttpResponse.BodyHandlers.discarding());assertEquals(302,provider.statusCode());
        var callback=URI.create(provider.headers().firstValue("location").orElseThrow());assertEquals(302,client.get(callback.getRawPath()+"?"+callback.getRawQuery()).status());
        var session=client.get("/api/v1/session");assertEquals(200,session.status());var body=JSON.readTree(session.body());client.csrf(body.get("csrfHeaderName").asString(),body.get("csrfToken").asString());return client;
    }
    @Test void configuredAuthenticatedProfileListingIsAvailable() throws Exception {
        var client = login("profile-list-" + UUID.randomUUID());
        try {
            var result = client.get("/api/v3/profiles");
            assertEquals(200, result.status());
            assertEquals("{\"profiles\":[]}", result.body());
        } finally {
            logoutAfterWorkspaceSettles(client);
        }
    }

    @Test void actualProfileSavesReplayAndHistoryStayOwnedAndDebugRedacted(CapturedOutput output) throws Exception {
        String subject = "profile-http-" + UUID.randomUUID(); var client = login(subject);
        var owner = new studio.environment.core.session.Owner(issuer.issuer(), subject);
        var definition = studio.environment.server.workspace.V3ProfileHttpFixtures.definition(workspace, owner, false);
        String id = UUID.randomUUID().toString(), path = "/api/v3/profiles/" + id;
        String source = studio.environment.server.workspace.V3ProfileHttpFixtures.source().replace("First neutral slot", "INVENTED-PROFILE-HTTP-CANARY");
        assertTrue(source.contains("INVENTED-PROFILE-HTTP-CANARY"));
        var reference = new studio.environment.core.workspace.NativeCommand.Reference(definition.objectId(), "2");
        String body = new String(studio.environment.server.workspace.V3ProfileHttpFixtures.body(source, "JSON", "0", UUID.randomUUID().toString(), reference), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(403, client.request("PUT", path, body, false).status());
        var first = client.request("PUT", path, body, true); assertEquals(200, first.status());
        assertEquals(source, JSON.readTree(first.body()).get("source").asString());
        assertEquals("structurally-valid", JSON.readTree(first.body()).at("/projection/kind").asString());
        studio.environment.server.workspace.V3ProfileHttpFixtures.laterDefinition(workspace, owner, definition);
        String yaml = "---\n" + source + "\r\n";
        String next = new String(studio.environment.server.workspace.V3ProfileHttpFixtures.body(yaml, "YAML", "1", UUID.randomUUID().toString(), reference), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(200, client.request("PUT", path, next, true).status());
        assertEquals(yaml, JSON.readTree(client.get(path).body()).get("source").asString());
        assertEquals(first.body(), client.get(path + "/revisions/1").body());
        assertEquals(first.body(), client.request("PUT", path, body, true).body());
        assertEquals(400, client.request("POST", path + "/publish", "{}", true).status());
        assertEquals(403, client.request("POST", path + "/capture", "{}", true).status());
        assertEquals(400, client.request("PUT", path, body + " {}", true).status());
        var other = login("profile-foreign-" + UUID.randomUUID());
        assertEquals(404, other.get(path).status());
        assertEquals(404, other.request("PUT", "/api/v3/profiles/" + UUID.randomUUID(), body, true).status());
        logoutAfterWorkspaceSettles(other);
        logoutAfterWorkspaceSettles(client);
        assertEquals(401, client.get(path).status());
        assertFalse(output.getAll().contains("INVENTED-PROFILE-HTTP-CANARY"));
        assertFalse(output.getAll().contains("mock-platform-secret"));
        for (String token : issuer.issuedTokens) assertFalse(output.getAll().contains(token));
    }
    @Test void profilePartialBodyLogoutStopsOriginalWorkerAndRecovers() throws Exception {
        String subject = "profile-body-" + UUID.randomUUID(); var client = login(subject);
        try (var pending = client.begin("PUT", "/api/v3/profiles/" + UUID.randomUUID(), 1000, true)) {
            pending.write("{\"source\":\"".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            awaitCount(1);
            int logout = client.request("POST", "/api/v1/session/logout", "", true).status();
            assertTrue(logout == 204 || logout == 503);
            assertEquals(403, pending.response().status());
        }
        awaitCount(0);
        var again = login(subject); assertEquals(200, again.get("/api/v3/profiles").status());
        logoutAfterWorkspaceSettles(again);
    }
    @Test void definitionsAndProfilesShareFourBlockedTransfersAndRecovery() throws Exception {
        String subject = "profile-shared-" + UUID.randomUUID(); var client = login(subject);
        var owner = new studio.environment.core.session.Owner(issuer.issuer(), subject);
        var definition = studio.environment.server.workspace.V3ProfileHttpFixtures.definition(workspace, owner, false);
        var profile = studio.environment.server.workspace.V3ProfileHttpFixtures.save(workspace, owner, definition,
                "\t".repeat(600000) + studio.environment.server.workspace.V3ProfileHttpFixtures.source());
        String definitionId = UUID.randomUUID().toString();
        studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.seed(workspace, owner, definitionId,
                "\t".repeat(600000) + Files.readString(Path.of("../../fixtures/native-v3/definition.json")));
        var pending = new ArrayList<PlanHttpSocketClient.Pending>();
        try {
            for (String path : List.of("/api/v3/profiles/" + profile.objectId(), "/api/v3/definitions/" + definitionId,
                    "/api/v3/profiles/" + profile.objectId(), "/api/v3/definitions/" + definitionId)) {
                var request = client.begin("GET", path, 0, true, 1024); pending.add(request);
                assertEquals(200, request.headersOnly().status());
            }
            awaitCount(4);
            assertEquals(429, client.get("/api/v3/profiles").status());
            assertEquals(429, client.get("/api/v3/definitions").status());
        } finally { for (var request : pending) request.close(); }
        awaitCount(0);
        assertEquals(200, client.get("/api/v3/profiles").status());
        assertEquals(200, client.get("/api/v3/definitions").status());
        logoutAfterWorkspaceSettles(client);
    }
    @Test void profileRoutesRequireApprovedHostOriginAndAuthentication() throws Exception {
        assertEquals(401, new PlanHttpSocketClient(port).get("/api/v3/profiles").status());
        var foreign = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v3/profiles")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, foreign.statusCode()); assertTrue(foreign.body().contains("REQUEST_ORIGIN_DENIED"));
        try (var socket = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write("GET /api/v3/profiles HTTP/1.1\r\nHost: localhost\r\nOrigin: https://foreign.invalid\r\nConnection: close\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            String response = new String(socket.getInputStream().readNBytes(4096), java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(response.startsWith("HTTP/1.1 403")); assertTrue(response.contains("REQUEST_ORIGIN_DENIED"));
        }
    }
    private void logoutAfterWorkspaceSettles(PlanHttpSocketClient client) {
        // A complete HTTP response can precede removal of its original workspace record.
        // assertAll still attempts logout and retains both failures if settlement times out.
        assertAll("settled profile logout",
                () -> awaitCount(0),
                () -> assertEquals(204, client.request("POST", "/api/v1/session/logout", "", true).status()));
    }
    private void awaitCount(int expected) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime) != expected && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(expected, studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime));
    }
}
