package studio.environment.server.security;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import studio.environment.server.plan.PlanHttpSocketClient;
import studio.environment.server.plan.V3PlanHttpTestConfiguration;
import studio.environment.server.workspace.SqliteDraftStore;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"studio.mode=hosted",
        "studio.security.public-origin=http://localhost", "studio.security.client-id=mock-client",
        "studio.security.client-secret=mock-platform-secret", "studio.security.allow-test-http=true",
        "logging.level.org.springframework.web=DEBUG"})
@ActiveProfiles("oidc-test")
@Import(V3PlanHttpTestConfiguration.class)
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
class V3PlanHttpBoundaryTest {
    static final MockIssuer issuer = new MockIssuer();
    static final Path workspace = initialize();
    static final JsonMapper JSON = JsonMapper.builder().build();
    static Path initialize() {
        try { var path = Files.createTempDirectory("es-v3-http-independent-mock-"); SqliteDraftStore.initializeV3(path); return path; }
        catch (java.io.IOException failure) { throw new IllegalStateException("MOCK_STORAGE_UNAVAILABLE"); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("studio.security.issuer", issuer::issuer);
        properties.add("studio.workspace.directory", workspace::toString);
    }
    @LocalServerPort int port;
    final List<PlanHttpSocketClient> clients = new ArrayList<>();
    final List<String> codes = new ArrayList<>(), csrf = new ArrayList<>();
    @AfterAll static void stopIssuer() { issuer.close(); }
    @AfterEach void logoutAndCheckCanaries(CapturedOutput output) throws Exception {
        for (var client : clients) client.request("POST", "/api/v1/session/logout", "{}", true);
        var forbidden = new ArrayList<>(List.of("MockV3-Password", "MockV3Reader", "mock-platform-secret", "mock-access-canary",
                Base64.getEncoder().encodeToString("mock-client:mock-platform-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        forbidden.addAll(codes); forbidden.addAll(csrf); forbidden.addAll(issuer.receivedVerifiers); forbidden.addAll(issuer.issuedTokens);
        for (String value : forbidden) assertFalse(output.getAll().contains(value), "Mock credential entered logs");
        try (var files = Files.list(workspace)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                String stored = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1);
                for (String value : forbidden) assertFalse(stored.contains(value), "Mock credential entered storage");
            }
        }
    }
    PlanHttpSocketClient login(String owner) throws Exception {
        issuer.subject = owner; issuer.mode = MockIssuer.TokenMode.VALID;
        var client = new PlanHttpSocketClient(port); clients.add(client);
        var start = client.get("/oauth2/authorization/studio"); assertEquals(302, start.status());
        var redirected = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(start.headers().get("location"))).build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(302, redirected.statusCode());
        var callback = URI.create(redirected.headers().firstValue("Location").orElseThrow());
        codes.add(MockIssuer.parameters(callback.getRawQuery()).get("code"));
        assertEquals(302, client.get(callback.getRawPath()+"?"+callback.getRawQuery()).status());
        var session = client.get("/api/v1/session"); assertEquals(200, session.status());
        var tree = JSON.readTree(session.body()); String token = tree.get("csrfToken").asString(); csrf.add(token);
        client.csrf(tree.get("csrfHeaderName").asString(), token); return client;
    }
    static String createBody() {
        return JSON.writeValueAsString(Map.of("expectedRevision", "0", "requestId", UUID.randomUUID().toString(),
                "definition", Map.of("objectId", V3PlanHttpTestConfiguration.OBJECT, "workspaceRevision", "2"),
                "bindingId", "mock-pg", "destinationId", "mock-destination"));
    }
    @Test void actualOidcAndCsrfReachTheExplicitV3CreationWitness() throws Exception {
        var client = login("mock-v3-http-"+UUID.randomUUID());
        var result = client.request("POST", "/api/v3/plans", createBody(), true);
        assertEquals(201, result.status());
        assertEquals("1", JSON.readTree(result.body()).get("revision").asString());
    }
    String create(PlanHttpSocketClient client) throws Exception {
        var reply = client.request("POST", "/api/v3/plans", createBody(), true);
        assertEquals(201, reply.status());
        return JSON.readTree(reply.body()).get("planId").asString();
    }
    String reserve(PlanHttpSocketClient client, String plan, String revision) throws Exception {
        var reply = client.request("POST", "/api/v3/plans/"+plan+"/inspections", reservationBody(revision), true);
        assertEquals(202, reply.status());
        return JSON.readTree(reply.body()).get("operationId").asString();
    }
    static String reservationBody(String revision) {
        return JSON.writeValueAsString(Map.of("expectedRevision", revision, "requestId", UUID.randomUUID().toString(), "discardDraftOnSuccess", true));
    }
    static void early(PlanHttpSocketClient.Response response, int status, String code) {
        assertEquals(status, response.status()); assertEquals("", response.body());
        assertEquals(code, response.headers().get("x-environment-studio-code"));
        assertEquals("0", response.headers().get("content-length"));
        assertEquals("no-store", response.headers().get("cache-control"));
    }

    @Test void actualInspectionKeepsPhysicalAndComputedCountsDistinctAndRetainsClosedStatus() throws Exception {
        var owner = login("mock-v3-inspection-"+UUID.randomUUID());
        String plan = create(owner);
        var initial = owner.get("/api/v3/plans/current"); assertEquals(200, initial.status());
        var before = JSON.readTree(initial.body());
        assertTrue(before.get("currentComputedCounts").isNull()); assertTrue(before.get("targetComputedCounts").isNull());
        String operation = reserve(owner, plan, "1");
        int previous = V3PlanHttpTestConfiguration.observations.get();
        var observed = owner.request("POST", "/api/v3/operations/"+operation+"/credentials",
                JSON.writeValueAsString(Map.of("username", "MockV3Reader", "password", "MockV3-Password-𐀀")), true);
        assertEquals(200, observed.status());
        var status = JSON.readTree(observed.body());
        assertEquals("succeeded", status.get("phase").asString());
        assertEquals("complete", status.get("cleanup").asString());
        assertEquals("2", status.get("installedRevision").asString());
        assertEquals(previous+1, V3PlanHttpTestConfiguration.observations.get()); assertTrue(V3PlanHttpTestConfiguration.exactCredentials.get());
        for (String path : List.of("/api/v3/plans/current", "/api/v3/plans/"+plan)) {
            var reply = owner.get(path); assertEquals(200, reply.status());
            var tree = JSON.readTree(reply.body());
            assertEquals(JSON.readTree("{\"documents\":1,\"entities\":3,\"relations\":0}"), tree.get("currentCounts"));
            assertEquals(JSON.readTree("{\"nodes\":4,\"memberships\":6,\"cooccurrences\":3}"), tree.get("currentComputedCounts"));
            assertTrue(tree.get("targetComputedCounts").isNull());
            assertFalse(tree.get("targetComplete").asBoolean()); assertFalse(tree.get("exportAvailable").asBoolean());
            assertTrue(tree.get("inspectionValid").asBoolean());
        }
        var poll = owner.get("/api/v3/operations/"+operation); assertEquals(200, poll.status());
        assertEquals(status, JSON.readTree(poll.body()));
        early(owner.request("POST", "/api/v3/operations/"+operation+"/credentials", "{}", true), 409, "CREDENTIALS_ALREADY_CONSUMED");
        String next = reserve(owner, plan, "2");
        var cancelled = owner.request("POST", "/api/v3/operations/"+next+"/cancel", "{}", true);
        assertEquals(200, cancelled.status()); assertEquals("cancelled", JSON.readTree(cancelled.body()).get("phase").asString());
    }

    @Test void actualOwnerVersionCsrfAndLogoutBoundariesRemainClosed() throws Exception {
        String subject = "mock-v3-boundaries-"+UUID.randomUUID();
        var owner = login(subject); String plan = create(owner); String operation = reserve(owner, plan, "1");
        var foreign = login("mock-v3-foreign-"+UUID.randomUUID());
        early(foreign.get("/api/v3/plans/"+plan), 404, "NOT_FOUND");
        early(foreign.get("/api/v3/operations/"+operation), 404, "NOT_FOUND");
        early(foreign.request("POST", "/api/v3/operations/"+operation+"/credentials", "{}", true), 404, "NOT_FOUND");
        assertEquals(403, owner.request("POST", "/api/v3/operations/"+operation+"/cancel", "{}", false).status());
        assertEquals("reserved", JSON.readTree(owner.get("/api/v3/operations/"+operation).body()).get("phase").asString());
        assertEquals(404, owner.get("/api/v1/plans/"+plan).status());
        assertEquals(404, owner.get("/api/v1/operations/"+operation).status());
        assertEquals(200, owner.request("POST", "/api/v3/operations/"+operation+"/cancel", "{}", true).status());
        assertEquals(204, owner.request("POST", "/api/v1/session/logout", "{}", true).status());
        assertEquals(401, owner.get("/api/v3/plans/"+plan).status());
    }

    @Test void actualV2CurrentCannotEnterV3AndMalformedMetadataLeavesNoPlan() throws Exception {
        String subject = "mock-v2-partition-"+UUID.randomUUID();
        var owner = login(subject);
        var invalid = owner.request("POST", "/api/v3/plans", createBody().replace("\"expectedRevision\":\"0\"", "\"expectedRevision\":\"0\",\"unexpected\":true"), true);
        assertEquals(400, invalid.status());
        assertEquals(String.valueOf(invalid.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length), invalid.headers().get("content-length"));
        assertNotEquals("0", invalid.headers().get("content-length"));
        early(owner.get("/api/v3/plans/current"), 404, "NOT_FOUND");
        var published = studio.environment.server.workspace.V3PlanRuntimeFixtures.v2(workspace,
                new studio.environment.core.session.Owner(issuer.issuer(), subject));
        String command = JSON.writeValueAsString(Map.of("expectedRevision", "0", "requestId", UUID.randomUUID().toString(),
                "definition", Map.of("objectId", published.objectId(), "workspaceRevision", "2"),
                "bindingId", "mock-pg", "destinationId", "mock-destination"));
        var created = owner.request("POST", "/api/v1/plans", command, true); assertEquals(201, created.status());
        String plan = JSON.readTree(created.body()).get("planId").asString();
        early(owner.get("/api/v3/plans/current"), 404, "NOT_FOUND");
        early(owner.get("/api/v3/plans/"+plan), 404, "NOT_FOUND");
        early(owner.request("POST", "/api/v3/plans/"+plan+"/inspections", "{}", true), 404, "NOT_FOUND");
        assertEquals(200, owner.get("/api/v1/plans/current").status());
    }

    @Test void fourActualPartialBodiesRetainSharedMetadataCapacityUntilTheirWorkersFinish() throws Exception {
        var client = login("mock-v3-capacity-"+UUID.randomUUID()); String plan = create(client);
        V3PlanHttpTestConfiguration.awaitRecords(0);
        var pending = new ArrayList<PlanHttpSocketClient.Pending>(); var bodies = new ArrayList<byte[]>();
        String operation = null;
        try {
            for (int index=0; index<4; index++) {
                byte[] body = reservationBody("1").getBytes(java.nio.charset.StandardCharsets.UTF_8); bodies.add(body);
                var request = client.begin("POST", "/api/v3/plans/"+plan+"/inspections", body.length, true);
                pending.add(request); request.write(new byte[]{'{'});
            }
            V3PlanHttpTestConfiguration.awaitRecords(4);
            early(client.get("/api/v3/plans/current"), 429, "CAPACITY");
            for (int index=0; index<4; index++) {
                var bytes = bodies.get(index); pending.get(index).write(Arrays.copyOfRange(bytes, 1, bytes.length));
                var reply = pending.get(index).response();
                if (index==0) { assertEquals(202, reply.status()); operation=JSON.readTree(reply.body()).get("operationId").asString(); }
                else assertEquals(409, reply.status());
            }
            V3PlanHttpTestConfiguration.awaitRecords(0);
            assertEquals(200, client.get("/api/v3/plans/current").status());
        } finally {
            for (var request : pending) request.close();
            if (operation!=null) client.request("POST", "/api/v3/operations/"+operation+"/cancel", "{}", true);
        }
    }

    @Test void actualWrongOriginAndUnimplementedRoutesStayDenied() throws Exception {
        var client = login("mock-v3-closed-"+UUID.randomUUID()); String plan = create(client);
        for (String suffix : List.of("views/documents", "profile-captures", "validations", "export")) {
            assertEquals(403, client.request("POST", "/api/v3/plans/"+plan+"/"+suffix, "{}", true).status());
        }
        try (var socket = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(3000);
            String request = "POST /api/v3/plans HTTP/1.1\r\nHost: localhost\r\nOrigin: http://foreign.invalid\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}";
            socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); socket.getOutputStream().flush();
            String response = new String(socket.getInputStream().readNBytes(8193), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(response.length()<=8192);
            assertTrue(response.startsWith("HTTP/1.1 403")); assertTrue(response.contains("REQUEST_ORIGIN_DENIED"));
        }
    }

    @Test void nonWitnessDraftUsesActualStoredHistoryAndStillNeedsQualifiedPublication() throws Exception {
        var client = login("mock-v3-real-history-"+UUID.randomUUID());
        String id = UUID.randomUUID().toString();
        String source = Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
        var saved = client.request("PUT", "/api/v3/definitions/"+id, JSON.writeValueAsString(Map.of(
                "expectedRevision", "0", "requestId", UUID.randomUUID().toString(), "format", "JSON", "source", source)), true);
        assertEquals(200, saved.status());
        String command = JSON.writeValueAsString(Map.of("expectedRevision", "0", "requestId", UUID.randomUUID().toString(),
                "definition", Map.of("objectId", id, "workspaceRevision", "1"), "bindingId", "mock-pg", "destinationId", "mock-destination"));
        var refused = client.request("POST", "/api/v3/plans", command, true);
        assertEquals(422, refused.status()); assertEquals("PUBLICATION_REQUIRED", JSON.readTree(refused.body()).get("code").asString());
        early(client.get("/api/v3/plans/current"), 404, "NOT_FOUND");
    }

    @Test void logoutDuringActualPartialBodyReleasesOnlyAfterOriginalWorkerStops() throws Exception {
        String subject = "mock-v3-held-logout-"+UUID.randomUUID();
        var client = login(subject); String plan = create(client);
        byte[] body = reservationBody("1").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (var pending = client.begin("POST", "/api/v3/plans/"+plan+"/inspections", body.length, true)) {
            pending.write(new byte[]{'{'}); V3PlanHttpTestConfiguration.awaitRecords(1);
            var logout = client.request("POST", "/api/v1/session/logout", "{}", true);
            // Retirement wakes the reader; it may close before the logout cleanup check.
            if (logout.status() == 204) {
                assertEquals("", logout.body());
            } else {
                assertEquals(503, logout.status());
                assertEquals("SESSION_CLEANUP_INCONCLUSIVE", JSON.readTree(logout.body()).get("code").asString());
            }
            V3PlanHttpTestConfiguration.awaitRecords(0);
            var ended = pending.response(); assertEquals("", ended.body());
        }
        var renewed = login(subject);
        early(renewed.get("/api/v3/plans/current"), 404, "NOT_FOUND");
    }

}
