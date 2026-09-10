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
import studio.environment.server.plan.V3PhysicalHttpTestConfiguration;
import studio.environment.server.workspace.SqliteDraftStore;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"studio.mode=hosted",
        "studio.security.public-origin=http://localhost", "studio.security.client-id=mock-client",
        "studio.security.client-secret=mock-platform-secret", "studio.security.allow-test-http=true",
        "logging.level.org.springframework.web=DEBUG"})
@ActiveProfiles("oidc-test")
@Import(V3PhysicalHttpTestConfiguration.class)
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
class V3PlanPhysicalHttpBoundaryTest {
    static final MockIssuer issuer = new MockIssuer();
    static final Path workspace = initialize();
    static final JsonMapper JSON = JsonMapper.builder().build();
    static Path initialize() {
        try { var path = Files.createTempDirectory("es-v3-physical-http-independent-mock-"); SqliteDraftStore.initializeV3(path); return path; }
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
                "definition", Map.of("objectId", V3PhysicalHttpTestConfiguration.OBJECT, "workspaceRevision", "2"),
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

    void inspect(PlanHttpSocketClient client, String plan) throws Exception {
        String operation = reserve(client,plan,"1");
        var response = client.request("POST","/api/v3/operations/"+operation+"/credentials",
                JSON.writeValueAsString(Map.of("username","MockV3Reader","password","MockV3-Password-𐀀")),true);
        assertEquals(200,response.status()); assertEquals("succeeded",JSON.readTree(response.body()).get("phase").asString(),response.body());
        assertEquals("2",JSON.readTree(response.body()).get("installedRevision").asString());
    }
    static String revision(String value) { return JSON.writeValueAsString(Map.of("revision",value)); }
    static String path(String plan,String view) {return "/api/v3/plans/"+plan+"/views/"+view;}
    static String page(String revision,String side,int offset,int limit) {return JSON.writeValueAsString(Map.of("revision",revision,"side",side,"offset",offset,"limit",limit));}
    static tools.jackson.databind.JsonNode ok(PlanHttpSocketClient.Response response) {
        assertEquals(200,response.status());assertEquals("no-store",response.headers().get("cache-control"));
        for(String privateValue:List.of("MOCK-SECRET-VIEW","MOCK-UNKNOWN-VIEW","MOCK-DENIED-VIEW"))assertFalse(response.body().contains(privateValue));
        return JSON.readTree(response.body());
    }
    static tools.jackson.databind.JsonNode field(tools.jackson.databind.JsonNode item,String name) {
        for(var value:item.get("fields"))if(value.get("fieldId").asString().equals(name))return value;
        throw new AssertionError("MOCK_FIELD_MISSING");
    }
    static tools.jackson.databind.JsonNode item(tools.jackson.databind.JsonNode page,String identity) {
        for(var value:page.get("items"))if(field(value,"id").get("value").asString().equals(identity))return value;
        throw new AssertionError("MOCK_ENTITY_MISSING");
    }
    static String digest(String source) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void actualPagesKeepPhysicalReferencesAndMaskingAcrossIdentityAndPublicValueChanges() throws Exception {
        var client=login("mock-v3-physical-"+UUID.randomUUID());String plan=create(client);inspect(client,plan);
        var inventory=ok(client.request("POST",path(plan,"documents"),revision("2"),true));
        var document=inventory.get("documents").get(0);assertEquals(1,inventory.get("documents").size());
        assertEquals("sheet",document.get("documentId").asString());assertEquals(digest(studio.environment.server.planning.V3PhysicalHttpWitnesses.XML),document.get("currentDigest").asString());
        assertTrue(document.get("targetDigest").isNull());assertTrue(document.get("changed").isNull());
        var first=ok(client.request("POST",path(plan,"entities"),page("2","current",0,2),true));
        assertEquals(3,first.get("total").asInt());assertEquals(2,first.get("items").size());assertEquals(2,first.get("nextOffset").asInt());
        var all=ok(client.request("POST",path(plan,"entities"),page("2","current",0,100),true));
        var one=item(all,"one");assertEquals("item",one.get("typeId").asString());assertEquals("existing",one.get("entity").get("kind").asString());assertEquals(5,one.get("fields").size());
        for(String name:List.of("secret")){var hidden=field(one,name);assertTrue(hidden.get("present").asBoolean());assertTrue(hidden.get("masked").asBoolean());assertTrue(hidden.get("value").isNull());}
        var empty=field(one,"optional");assertTrue(empty.get("present").asBoolean());assertFalse(empty.get("masked").asBoolean());assertEquals("",empty.get("value").asString());
        var absent=field(item(all,"two"),"optional");assertFalse(absent.get("present").asBoolean());assertTrue(absent.get("value").isNull());
        var last=ok(client.request("POST",path(plan,"entities"),page("2","current",2,2),true));assertEquals(3,last.get("total").asInt());assertEquals(1,last.get("items").size());assertTrue(last.get("nextOffset").isNull());assertEquals(3,first.get("items").size()+last.get("items").size());
        var beyond=ok(client.request("POST",path(plan,"entities"),page("2","current",50000,100),true));assertEquals(3,beyond.get("total").asInt());assertTrue(beyond.get("items").isEmpty());assertTrue(beyond.get("nextOffset").isNull());
        var missing=client.request("POST",path(plan,"entities"),page("2","target",0,100),true);assertEquals(422,missing.status());assertEquals("INCOMPLETE_TARGET",JSON.readTree(missing.body()).get("code").asString());
        var fields=new LinkedHashMap<String,Object>();for(String name:List.of("id","tone","finish","secret","optional"))fields.put(name,Map.of("kind","keep-observed"));
        fields.put("id",Map.of("kind","entered","text","renamed"));fields.put("tone",Map.of("kind","entered","text","beta"));
        String command=JSON.writeValueAsString(Map.of("expectedRevision","2","requestId",UUID.randomUUID().toString(),"kind","upsert-entity","decision",Map.of("kind","retain","entity",one.get("entity"),"fields",fields,"references",Map.of()),"placements",List.of()));
        var ack=client.request("POST","/api/v3/plans/"+plan+"/commands",command,true);assertEquals(200,ack.status());assertEquals("3",JSON.readTree(ack.body()).get("revision").asString());
        var target=ok(client.request("POST",path(plan,"entities"),page("3","target",0,100),true));assertEquals(3,target.get("total").asInt());var renamed=item(target,"renamed");assertEquals(one.get("entity"),renamed.get("entity"));assertEquals("beta",field(renamed,"tone").get("value").asString());
        var original=ok(client.request("POST",path(plan,"entities"),page("3","current",0,100),true));assertEquals(one,item(original,"one"));
        String expected="<items><!-- mock -->\r\n<item id='renamed' tone='beta' finish='x' secret='MOCK-SECRET-VIEW' optional=''/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>";
        var changed=ok(client.request("POST",path(plan,"documents"),revision("3"),true)).get("documents").get(0);assertTrue(changed.get("changed").asBoolean());assertEquals(digest(expected),changed.get("targetDigest").asString());assertEquals(document.get("currentDigest"),changed.get("currentDigest"));
        assertEquals("3",JSON.readTree(client.get("/api/v3/plans/"+plan).body()).get("revision").asString());
    }
    @Test void actualPhysicalRoutesRejectForeignVersionsAndClosedOrStaleRequestsWithoutMutation() throws Exception {
        var owner=login("mock-physical-owner-"+UUID.randomUUID());String plan=create(owner);inspect(owner,plan);var stranger=login("mock-physical-stranger-"+UUID.randomUUID());
        for(String view:List.of("documents","entities")){
            early(stranger.request("POST",path(plan,view),"{",true),404,"NOT_FOUND");
            assertEquals(403,owner.request("POST",path(plan,view),"{}",false).status());
            String stale=view.equals("documents")?revision("1"):page("1","current",0,100);assertEquals(409,owner.request("POST",path(plan,view),stale,true).status());
            assertEquals(400,owner.request("POST",path(plan,view),"{",true).status());
        }
        for(String body:List.of("{\"revision\":\"2\",\"side\":\"current\",\"offset\":0.0,\"limit\":1}","{\"revision\":\"2\",\"side\":\"current\",\"offset\":0,\"limit\":1,\"revealSecrets\":true}",page("2","current",0,101),page("2","current",0,1)+"{}"))assertEquals(400,owner.request("POST",path(plan,"entities"),body,true).status());
        var unchanged=JSON.readTree(owner.get("/api/v3/plans/"+plan).body());assertEquals("2",unchanged.get("revision").asString());assertFalse(unchanged.get("targetComplete").asBoolean());
        String subject="mock-v2-physical-"+UUID.randomUUID();var legacy=login(subject);var published=studio.environment.server.workspace.V3PlanRuntimeFixtures.v2(workspace,new studio.environment.core.session.Owner(issuer.issuer(),subject));
        var created=legacy.request("POST","/api/v1/plans",JSON.writeValueAsString(Map.of("expectedRevision","0","requestId",UUID.randomUUID().toString(),"definition",Map.of("objectId",published.objectId(),"workspaceRevision","2"),"bindingId","mock-pg","destinationId","mock-destination")),true);assertEquals(201,created.status());String v2=JSON.readTree(created.body()).get("planId").asString();
        for(String view:List.of("documents","entities"))early(legacy.request("POST",path(v2,view),"{",true),404,"NOT_FOUND");assertEquals(200,legacy.get("/api/v1/plans/current").status());
    }
    @Test void actualPartialPhysicalBodyRetainsScratchAndSemanticRecordUntilLogoutWorkerClosure() throws Exception {
        String subject="mock-physical-held-"+UUID.randomUUID();var first=login(subject);String a=create(first);inspect(first,a);var second=login("mock-physical-waiter-"+UUID.randomUUID());String b=create(second);inspect(second,b);
        V3PhysicalHttpTestConfiguration.awaitRecords(0);byte[] body=page("2","current",0,100).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var pending=first.begin("POST",path(a,"entities"),body.length,true)){
            pending.write(new byte[]{'{'});V3PhysicalHttpTestConfiguration.awaitRecords(1);early(second.request("POST",path(b,"documents"),revision("2"),true),429,"CAPACITY");assertEquals(200,second.get("/api/v3/plans/"+b).status());
            var logout=first.request("POST","/api/v1/session/logout","{}",true);if(logout.status()==204)assertEquals("",logout.body());else{assertEquals(503,logout.status());assertEquals("SESSION_CLEANUP_INCONCLUSIVE",JSON.readTree(logout.body()).get("code").asString());}
            V3PhysicalHttpTestConfiguration.awaitRecords(0);assertEquals("",pending.response().body());
        }
        ok(second.request("POST",path(b,"entities"),page("2","current",0,100),true));var renewed=login(subject);early(renewed.get("/api/v3/plans/current"),404,"NOT_FOUND");
    }
}
