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
class V3PlanCommandHttpBoundaryTest {
    static final MockIssuer issuer = new MockIssuer();
    static final Path workspace = initialize();
    static final JsonMapper JSON = JsonMapper.builder().build();
    static Path initialize() {
        try { var path = Files.createTempDirectory("es-v3-command-http-independent-mock-"); SqliteDraftStore.initializeV3(path); return path; }
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

    static String command(String revision, String action) {
        return "{\"expectedRevision\":\""+revision+"\",\"requestId\":\""+UUID.randomUUID()+"\","+action+"}";
    }
    static String discard(String revision) { return command(revision, "\"kind\":\"discard\""); }
    void inspect(PlanHttpSocketClient client, String plan) throws Exception {
        String operation = reserve(client, plan, "1");
        var response = client.request("POST", "/api/v3/operations/"+operation+"/credentials",
                JSON.writeValueAsString(Map.of("username", "MockV3Reader", "password", "MockV3-Password-𐀀")), true);
        assertEquals(200, response.status());
        assertEquals("succeeded", JSON.readTree(response.body()).get("phase").asString());
        assertEquals("2", JSON.readTree(response.body()).get("installedRevision").asString());
    }

    @Test void actualSemanticEditRecomputesTargetAndReplaySurvivesDiscardAndReplacement() throws Exception {
        String subject = "mock-v3-command-"+UUID.randomUUID();
        var client = login(subject); String plan = create(client); inspect(client, plan);
        var service = V3PlanHttpTestConfiguration.service;
        var lease = V3PlanHttpTestConfiguration.leases.get(subject);
        // Original opaque handle comes from the real core page; no v3 HTTP view route is claimed.
        String handle = service.entities(lease, plan, "2", false, 0, 100).entities().stream()
                .filter(entity -> entity.fields().stream().anyMatch(field -> field.field().equals("id") && field.value().orElse("").equals("one")))
                .findFirst().orElseThrow().handle();
        String change = command("2", "\"kind\":\"upsert-entity\",\"decision\":{\"kind\":\"retain\",\"entity\":{\"kind\":\"existing\",\"handle\":\""+handle
                +"\"},\"fields\":{\"id\":{\"kind\":\"keep-observed\"},\"tone\":{\"kind\":\"entered\",\"text\":\"beta\"},\"finish\":{\"kind\":\"keep-observed\"}},\"references\":{}},\"placements\":[]");
        String path = "/api/v3/plans/"+plan+"/commands";
        var changed = client.request("POST", path, change, true); assertEquals(200, changed.status());
        var acknowledgement = JSON.readTree(changed.body());
        assertEquals(plan, acknowledgement.get("planId").asString()); assertEquals("3", acknowledgement.get("revision").asString());
        var summary = JSON.readTree(client.get("/api/v3/plans/"+plan).body());
        assertEquals(JSON.readTree("{\"nodes\":4,\"memberships\":6,\"cooccurrences\":3}"), summary.get("currentComputedCounts"));
        assertEquals(JSON.readTree("{\"nodes\":4,\"memberships\":6,\"cooccurrences\":2}"), summary.get("targetComputedCounts"));
        assertTrue(summary.get("targetComplete").asBoolean()); assertFalse(summary.get("exportAvailable").asBoolean());
        try (var view = service.reserveView(lease, plan, studio.environment.core.plan.PlanDefinition.Version.V3)) {
            var snapshot = view.run(() -> { view.pin("3"); return view.snapshot(); });
            assertEquals("<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>", snapshot.current().orElseThrow().sources().getFirst().xml());
            assertEquals("<items><!-- mock -->\r\n<item id='one' tone='beta' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>", snapshot.target().orElseThrow().sources().getFirst().xml());
        }
        assertEquals(acknowledgement, JSON.readTree(client.request("POST", path, change, true).body()));
        String collision = change.replace("\"text\":\"beta\"", "\"text\":\"gamma\"");
        var conflict = client.request("POST", path, collision, true); assertEquals(409, conflict.status());
        assertEquals("CONFLICT", JSON.readTree(conflict.body()).get("code").asString());
        var discarded = client.request("POST", path, discard("3"), true); assertEquals(200, discarded.status());
        assertEquals("3", JSON.readTree(discarded.body()).get("revision").asString());
        String replacement = create(client); assertNotEquals(plan, replacement);
        var replay = client.request("POST", path, change, true); assertEquals(200, replay.status());
        assertEquals(acknowledgement, JSON.readTree(replay.body()));
        assertEquals(409, client.request("POST", path, collision, true).status());
        var current = JSON.readTree(client.get("/api/v3/plans/current").body());
        assertEquals(replacement, current.get("planId").asString()); assertEquals("1", current.get("revision").asString());
    }

    @Test void actualCommandOwnerVersionCsrfAndClosedGrammarRefusalsLeaveStateUnchanged() throws Exception {
        var owner = login("mock-v3-command-boundaries-"+UUID.randomUUID()); String plan = create(owner);
        var foreign = login("mock-v3-command-foreign-"+UUID.randomUUID());
        String path = "/api/v3/plans/"+plan+"/commands";
        early(foreign.request("POST", path, "{", true), 404, "NOT_FOUND");
        assertEquals(403, owner.request("POST", path, discard("1"), false).status());
        for (String body : List.of("{", discard("1")+"{}", discard("1").replace("\"kind\":\"discard\"", "\"kind\":\"discard\",\"modelVersion\":3"))) {
            var refused = owner.request("POST", path, body, true); assertEquals(400, refused.status());
            assertEquals("1", JSON.readTree(owner.get("/api/v3/plans/"+plan).body()).get("revision").asString());
        }
        String subject = "mock-v2-command-partition-"+UUID.randomUUID(); var legacy = login(subject);
        var published = studio.environment.server.workspace.V3PlanRuntimeFixtures.v2(workspace,
                new studio.environment.core.session.Owner(issuer.issuer(), subject));
        var created = legacy.request("POST", "/api/v1/plans", JSON.writeValueAsString(Map.of("expectedRevision", "0", "requestId", UUID.randomUUID().toString(),
                "definition", Map.of("objectId", published.objectId(), "workspaceRevision", "2"), "bindingId", "mock-pg", "destinationId", "mock-destination")), true);
        assertEquals(201, created.status()); String v2 = JSON.readTree(created.body()).get("planId").asString();
        early(legacy.request("POST", "/api/v3/plans/"+v2+"/commands", "{", true), 404, "NOT_FOUND");
        assertEquals("1", JSON.readTree(legacy.get("/api/v1/plans/current").body()).get("revision").asString());
    }

    @Test void actualPartialSemanticBodyBlocksCommandsButAllowsMetadataAndRecoversAfterLogout() throws Exception {
        String subject = "mock-v3-command-held-"+UUID.randomUUID();
        var first = login(subject); String a = create(first);
        var second = login("mock-v3-command-waiting-"+UUID.randomUUID()); String b = create(second);
        V3PlanHttpTestConfiguration.awaitRecords(0);
        byte[] body = discard("1").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (var pending = first.begin("POST", "/api/v3/plans/"+a+"/commands", body.length, true)) {
            pending.write(new byte[]{'{'}); V3PlanHttpTestConfiguration.awaitRecords(1);
            early(second.request("POST", "/api/v3/plans/"+b+"/commands", discard("1"), true), 429, "CAPACITY");
            var metadata = second.get("/api/v3/plans/"+b); assertEquals(200, metadata.status());
            assertEquals("1", JSON.readTree(metadata.body()).get("revision").asString());
            var logout = first.request("POST", "/api/v1/session/logout", "{}", true);
            if (logout.status()==204) assertEquals("", logout.body());
            else { assertEquals(503, logout.status()); assertEquals("SESSION_CLEANUP_INCONCLUSIVE", JSON.readTree(logout.body()).get("code").asString()); }
            V3PlanHttpTestConfiguration.awaitRecords(0); assertEquals("", pending.response().body());
        }
        var recovered = second.request("POST", "/api/v3/plans/"+b+"/commands", discard("1"), true);
        assertEquals(200, recovered.status()); assertEquals("1", JSON.readTree(recovered.body()).get("revision").asString());
        var renewed = login(subject); early(renewed.get("/api/v3/plans/current"), 404, "NOT_FOUND");
    }
}
