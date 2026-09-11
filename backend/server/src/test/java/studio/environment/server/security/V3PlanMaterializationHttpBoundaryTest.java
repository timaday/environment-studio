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
class V3PlanMaterializationHttpBoundaryTest {
    static final MockIssuer issuer = new MockIssuer();
    static final Path workspace = initialize();
    static final JsonMapper JSON = JsonMapper.builder().build();
    static Path initialize() {
        try { var path = Files.createTempDirectory("es-v3-materialization-http-independent-mock-"); SqliteDraftStore.initializeV3(path); return path; }
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
        Throwable cleanupFailure = null;
        try { V3PlanHttpTestConfiguration.awaitRecords(0); }
        catch (Exception | AssertionError failure) { cleanupFailure = failure; }
        for (var client : clients) {
            try { client.request("POST", "/api/v1/session/logout", "{}", true); }
            catch (Exception | AssertionError failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else cleanupFailure.addSuppressed(failure);
            }
        }
        try { V3PlanHttpTestConfiguration.awaitRecords(0); }
        catch (Exception | AssertionError failure) {
            if (cleanupFailure == null) cleanupFailure = failure;
            else cleanupFailure.addSuppressed(failure);
        }
        if (cleanupFailure instanceof Exception failure) throw failure;
        if (cleanupFailure instanceof AssertionError failure) throw failure;
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
    /** Sequential assertions await original HTTP settlement; held-capacity probes stay raw. */
    PlanHttpSocketClient.Response sequentialRequest(PlanHttpSocketClient client, String method, String path, String body, boolean token) throws Exception {
        V3PlanHttpTestConfiguration.awaitRecords(0);
        return client.request(method, path, body, token);
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
        var reply = sequentialRequest(client,"POST", "/api/v3/plans", createBody(), true);
        assertEquals(201, reply.status());
        return JSON.readTree(reply.body()).get("planId").asString();
    }
    String reserve(PlanHttpSocketClient client, String plan, String revision) throws Exception {
        var reply = sequentialRequest(client,"POST", "/api/v3/plans/"+plan+"/inspections", reservationBody(revision), true);
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

    void inspect(PlanHttpSocketClient client, String plan) throws Exception {
        String operation = reserve(client,plan,"1");
        var response = sequentialRequest(client,"POST","/api/v3/operations/"+operation+"/credentials",
                JSON.writeValueAsString(Map.of("username","MockV3Reader","password","MockV3-Password-𐀀")),true);
        assertEquals(200,response.status()); assertEquals("succeeded",JSON.readTree(response.body()).get("phase").asString());
        assertEquals("2",JSON.readTree(response.body()).get("installedRevision").asString());
    }
    static String revision(String value) { return JSON.writeValueAsString(Map.of("revision",value)); }
    static String path(String plan) { return "/api/v3/plans/"+plan+"/materializations"; }
    static String edit(String revision, String handle, Map<String,Object> tone) {
        return JSON.writeValueAsString(Map.of("expectedRevision",revision,"requestId",UUID.randomUUID().toString(),"kind","upsert-entity",
                "decision",Map.of("kind","retain","entity",Map.of("kind","existing","handle",handle),"fields",Map.of(
                        "id",Map.of("kind","keep-observed"),"tone",tone,"finish",Map.of("kind","keep-observed")),"references",Map.of()),"placements",List.of()));
    }
    static void materialized(PlanHttpSocketClient.Response response,String revision,String state,List<String> diagnostics) {
        assertEquals(200,response.status()); assertEquals("no-store",response.headers().get("cache-control"));
        assertEquals(JSON.readTree(JSON.writeValueAsString(Map.of("revision",revision,"state",state,"complete",state.equals("COMPLETE"),"diagnostics",diagnostics))),JSON.readTree(response.body()));
    }

    @Test void actualMaterializationPreservesRevisionAndDistinguishesAllThreeEngineOutcomes() throws Exception {
        String subject="mock-v3-materialize-"+UUID.randomUUID(); var client=login(subject);String plan=create(client);inspect(client,plan);
        assertFalse(JSON.readTree(client.get("/api/v3/plans/"+plan).body()).get("targetComplete").asBoolean());
        materialized(sequentialRequest(client,"POST",path(plan),revision("2"),true),"2","COMPLETE",List.of());
        materialized(sequentialRequest(client,"POST",path(plan),revision("2"),true),"2","COMPLETE",List.of());
        var summary=JSON.readTree(client.get("/api/v3/plans/"+plan).body());
        assertEquals("2",summary.get("revision").asString()); assertTrue(summary.get("targetComplete").asBoolean());
        assertEquals(JSON.readTree("{\"nodes\":4,\"memberships\":6,\"cooccurrences\":3}"),summary.get("targetComputedCounts"));
        assertFalse(summary.get("exportAvailable").asBoolean());
        var service=V3PlanHttpTestConfiguration.service;var lease=V3PlanHttpTestConfiguration.leases.get(subject);
        try(var view=service.reserveView(lease,plan,studio.environment.core.plan.PlanDefinition.Version.V3)) {
            var snapshot=view.run(()->{view.pin("2");return view.snapshot();});
            assertEquals("<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>",snapshot.target().orElseThrow().sources().getFirst().xml());
        }
        // This core page supplies an actual opaque handle; it does not qualify an HTTP view route.
        String handle=service.entities(lease,plan,"2",false,0,100).entities().stream().filter(e->e.fields().stream()
                .anyMatch(f->f.field().equals("id")&&f.value().orElse("").equals("one"))).findFirst().orElseThrow().handle();
        var unresolved=sequentialRequest(client,"POST","/api/v3/plans/"+plan+"/commands",edit("2",handle,Map.of("kind","unresolved")),true);
        assertEquals(200,unresolved.status());assertEquals("3",JSON.readTree(unresolved.body()).get("revision").asString());
        materialized(sequentialRequest(client,"POST",path(plan),revision("3"),true),"3","INCOMPLETE",List.of("by-tone"));
        assertTrue(JSON.readTree(client.get("/api/v3/plans/"+plan).body()).get("targetComputedCounts").isNull());
        var invalid=sequentialRequest(client,"POST","/api/v3/plans/"+plan+"/commands",edit("3",handle,Map.of("kind","entered","text","")),true);
        assertEquals(200,invalid.status());assertEquals("4",JSON.readTree(invalid.body()).get("revision").asString());
        materialized(sequentialRequest(client,"POST",path(plan),revision("4"),true),"4","REFUSED",List.of("INVALID_DERIVED_IDENTITY"));
        assertFalse(JSON.readTree(client.get("/api/v3/plans/"+plan).body()).get("targetComplete").asBoolean());
        var repaired=sequentialRequest(client,"POST","/api/v3/plans/"+plan+"/commands",edit("4",handle,Map.of("kind","entered","text","beta")),true);
        assertEquals(200,repaired.status());assertEquals("5",JSON.readTree(repaired.body()).get("revision").asString());
        materialized(sequentialRequest(client,"POST",path(plan),revision("5"),true),"5","COMPLETE",List.of());
        assertEquals(JSON.readTree("{\"nodes\":4,\"memberships\":6,\"cooccurrences\":2}"),JSON.readTree(client.get("/api/v3/plans/"+plan).body()).get("targetComputedCounts"));
    }

    @Test void actualMissingInspectionStaleVersionOwnerCsrfAndClosedBodiesRefuseWithoutTarget() throws Exception {
        var owner=login("mock-v3-materialization-refusal-"+UUID.randomUUID());String plan=create(owner);
        var missing=sequentialRequest(owner,"POST",path(plan),revision("1"),true);assertEquals(422,missing.status());
        assertEquals("INSPECTION_REQUIRED",JSON.readTree(missing.body()).get("code").asString());
        var foreign=login("mock-v3-materialization-foreign-"+UUID.randomUUID());early(sequentialRequest(foreign,"POST",path(plan),"{",true),404,"NOT_FOUND");
        inspect(owner,plan);
        assertEquals(403,sequentialRequest(owner,"POST",path(plan),revision("2"),false).status());
        var stale=sequentialRequest(owner,"POST",path(plan),revision("1"),true);assertEquals(409,stale.status());
        assertEquals("CONFLICT",JSON.readTree(stale.body()).get("code").asString());
        for(String body:List.of("{",revision("2")+"{}","{\"revision\":2}","{\"revision\":\"2\",\"complete\":true}"))
            assertEquals(400,sequentialRequest(owner,"POST",path(plan),body,true).status());
        var summary=JSON.readTree(owner.get("/api/v3/plans/"+plan).body());assertEquals("2",summary.get("revision").asString());assertFalse(summary.get("targetComplete").asBoolean());
        String subject="mock-v2-materialization-partition-"+UUID.randomUUID();var legacy=login(subject);
        var published=studio.environment.server.workspace.V3PlanRuntimeFixtures.v2(workspace,new studio.environment.core.session.Owner(issuer.issuer(),subject));
        var created=sequentialRequest(legacy,"POST","/api/v1/plans",JSON.writeValueAsString(Map.of("expectedRevision","0","requestId",UUID.randomUUID().toString(),
                "definition",Map.of("objectId",published.objectId(),"workspaceRevision","2"),"bindingId","mock-pg","destinationId","mock-destination")),true);
        assertEquals(201,created.status());String v2=JSON.readTree(created.body()).get("planId").asString();
        early(sequentialRequest(legacy,"POST",path(v2),"{",true),404,"NOT_FOUND");assertEquals(200,legacy.get("/api/v1/plans/current").status());
    }

    @Test void actualPartialViewBodyRetainsFullScratchAndLogoutRestoresAnotherOwnersMaterialization() throws Exception {
        String subject="mock-v3-materialization-held-"+UUID.randomUUID();var first=login(subject);String a=create(first);inspect(first,a);
        var second=login("mock-v3-materialization-waiting-"+UUID.randomUUID());String b=create(second);inspect(second,b);
        V3PlanHttpTestConfiguration.awaitRecords(0);byte[] body=revision("2").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var pending=first.begin("POST",path(a),body.length,true)) {
            pending.write(new byte[]{'{'});V3PlanHttpTestConfiguration.awaitRecords(1);
            early(second.request("POST",path(b),revision("2"),true),429,"CAPACITY");
            var summary=second.get("/api/v3/plans/"+b);assertEquals(200,summary.status());assertFalse(JSON.readTree(summary.body()).get("targetComplete").asBoolean());
            var logout=first.request("POST","/api/v1/session/logout","{}",true);
            if(logout.status()==204)assertEquals("",logout.body());
            else {assertEquals(503,logout.status());assertEquals("SESSION_CLEANUP_INCONCLUSIVE",JSON.readTree(logout.body()).get("code").asString());}
            V3PlanHttpTestConfiguration.awaitRecords(0);assertEquals("",pending.response().body());
        }
        materialized(second.request("POST",path(b),revision("2"),true),"2","COMPLETE",List.of());
        var renewed=login(subject);early(renewed.get("/api/v3/plans/current"),404,"NOT_FOUND");
    }
}
