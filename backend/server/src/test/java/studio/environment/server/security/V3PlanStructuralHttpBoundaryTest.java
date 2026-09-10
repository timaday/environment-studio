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
import studio.environment.server.plan.V3StructuralHttpTestConfiguration;
import studio.environment.server.workspace.SqliteDraftStore;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"studio.mode=hosted",
        "studio.security.public-origin=http://localhost", "studio.security.client-id=mock-client",
        "studio.security.client-secret=mock-platform-secret", "studio.security.allow-test-http=true",
        "logging.level.org.springframework.web=DEBUG"})
@ActiveProfiles("oidc-test")
@Import(V3StructuralHttpTestConfiguration.class)
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
class V3PlanStructuralHttpBoundaryTest {
    static final MockIssuer issuer = new MockIssuer();
    static final Path workspace = initialize();
    static final JsonMapper JSON = JsonMapper.builder().build();
    static Path initialize() {
        try { var path = Files.createTempDirectory("es-v3-structural-http-independent-mock-"); SqliteDraftStore.initializeV3(path); return path; }
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
                "definition", Map.of("objectId", V3StructuralHttpTestConfiguration.OBJECT, "workspaceRevision", "2"),
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
        for(String privateValue:List.of("MOCK-SECRET-VIEW","MOCK-SECRET-DRAFT","MOCK-UNKNOWN-VIEW","MOCK-DENIED-VIEW"))assertFalse(response.body().contains(privateValue));
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
    static tools.jackson.databind.JsonNode row(tools.jackson.databind.JsonNode page,String name) {
        for(var value:page.get("items"))if(value.has("fieldId")&&value.get("fieldId").asString().equals(name))return value;
        throw new AssertionError("MOCK_BINDING_MISSING");
    }
    static String bindings(String revision,tools.jackson.databind.JsonNode entity,int offset,int limit){return JSON.writeValueAsString(Map.of("revision",revision,"entity",entity,"offset",offset,"limit",limit));}
    static String draft(String revision,int offset,int limit){return JSON.writeValueAsString(Map.of("revision",revision,"offset",offset,"limit",limit));}
    static String placements(String revision,String projection){return JSON.writeValueAsString(Map.of("revision",revision,"documentId","sheet","projectionId",projection,"offset",0,"limit",100));}
    void command(PlanHttpSocketClient client,String plan,String expected,String next,Map<String,Object> action)throws Exception{
        var wire=new LinkedHashMap<String,Object>(action);wire.put("expectedRevision",expected);wire.put("requestId",UUID.randomUUID().toString());
        var response=client.request("POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(wire),true);assertEquals(200,response.status());assertEquals(next,JSON.readTree(response.body()).get("revision").asString());
    }
    @Test void returnedPlacementsReferencesAndBindingsDriveExplicitStructuralWorkflow() throws Exception {
        var client=login("mock-structural-workflow-"+UUID.randomUUID());String plan=create(client);inspect(client,plan);
        var current=ok(client.request("POST",path(plan,"entities"),page("2","current",0,100),true));var one=item(current,"one").get("entity");var two=item(current,"two").get("entity");var three=item(current,"three").get("entity");
        var relations=ok(client.request("POST",path(plan,"relations"),page("2","current",0,100),true));assertEquals(1,relations.get("total").asInt());var relation=relations.get("items").get(0);assertEquals("link",relation.get("relationId").asString());assertEquals(one,relation.get("from"));assertEquals(two,relation.get("to"));
        var eligible=ok(client.request("POST",path(plan,"placements"),placements("2","items"),true));assertEquals(1,eligible.get("total").asInt());var coordinate=eligible.get("items").get(0);assertEquals("sheet",coordinate.get("documentId").asString());assertEquals("0",coordinate.get("elementIndex").asString());assertEquals(digest(studio.environment.server.planning.V3StructuralHttpWitnesses.XML),coordinate.get("sourceDigest").asString());
        var before=ok(client.request("POST",path(plan,"bindings"),bindings("2",two,0,100),true));var token=row(before,"id").get("token");assertEquals("two",row(before,"id").get("current").get("text").asString());
        var fields=new LinkedHashMap<String,Object>();for(String id:List.of("id","finish","optional"))fields.put(id,Map.of("kind","keep-observed"));fields.put("tone",Map.of("kind","unresolved"));fields.put("secret",Map.of("kind","entered","text","MOCK-SECRET-DRAFT"));
        command(client,plan,"2","3",Map.of("kind","upsert-entity","decision",Map.of("kind","retain","entity",one,"fields",fields,"references",Map.of("link",Map.of("kind","to","target",two))),"placements",List.of()));
        var explicit=ok(client.request("POST",path(plan,"draft"),draft("3",0,100),true));assertEquals(1,explicit.get("total").asInt());var retained=explicit.get("items").get(0);assertEquals(one,retained.get("entity"));assertEquals("retain",retained.get("disposition").asString());assertEquals("unresolved",field(retained,"tone").get("kind").asString());assertTrue(field(retained,"tone").get("value").isNull());assertTrue(field(retained,"secret").get("masked").asBoolean());assertTrue(field(retained,"secret").get("value").isNull());
        var unresolved=ok(client.request("POST",path(plan,"bindings"),bindings("3",one,0,100),true));assertEquals("unresolved",row(unresolved,"tone").get("target").get("state").asString());assertEquals("masked",row(unresolved,"secret").get("target").get("state").asString());assertEquals("complete",row(unresolved,"secret").get("currentLocations").get("state").asString());assertEquals(1,row(unresolved,"secret").get("currentLocations").get("total").asInt());
        assertEquals(422,client.request("POST",path(plan,"relations"),page("3","target",0,100),true).status());
        command(client,plan,"3","4",Map.of("kind","bind-field","entity",one,"fieldId","tone","state",Map.of("kind","entered","text","beta")));
        var unselected=client.request("POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(Map.of("expectedRevision","4","requestId",UUID.randomUUID().toString(),"kind","bind-field","entity",two,"fieldId","id","state",Map.of("kind","entered","text","renamed"))),true);assertEquals(422,unselected.status());assertEquals("4",JSON.readTree(client.get("/api/v3/plans/"+plan).body()).get("revision").asString());
        var secondFields=new LinkedHashMap<String,Object>();for(String id:List.of("tone","finish","secret","optional"))secondFields.put(id,Map.of("kind","keep-observed"));secondFields.put("id",Map.of("kind","entered","text","renamed"));
        command(client,plan,"4","5",Map.of("kind","upsert-entity","decision",Map.of("kind","retain","entity",two,"fields",secondFields,"references",Map.of("link",Map.of("kind","keep-observed"))),"placements",List.of()));
        var after=ok(client.request("POST",path(plan,"bindings"),bindings("5",two,0,100),true));assertEquals(token,row(after,"id").get("token"));assertEquals("renamed",row(after,"id").get("target").get("text").asString());assertEquals(2,row(after,"id").get("targetLocations").get("total").asInt());
        var finalRelation=ok(client.request("POST",path(plan,"relations"),page("5","target",0,100),true));assertEquals(relations.get("items"),finalRelation.get("items"));
        var fresh=JSON.readTree("{\"kind\":\"fresh\",\"slotId\":\"new-four\",\"typeId\":\"item\"}");
        var parent=Map.of("kind","existing","documentId",coordinate.get("documentId").asString(),"sourceDigest",coordinate.get("sourceDigest").asString(),"elementIndex",coordinate.get("elementIndex").asString());
        var placement=Map.of("entity",fresh,"documentId","sheet","projectionId","items","parent",parent);
        command(client,plan,"5","6",Map.of("kind","upsert-entity","decision",Map.of("kind","create","entity",fresh,"fields",Map.of("id",Map.of("kind","entered","text","four"),"tone",Map.of("kind","entered","text","gamma"),"finish",Map.of("kind","entered","text","z"),"secret",Map.of("kind","absent"),"optional",Map.of("kind","absent")),"references",Map.of("link",Map.of("kind","absent"))),"placements",List.of(placement)));
        var freshBindings=ok(client.request("POST",path(plan,"bindings"),bindings("6",fresh,0,100),true));assertEquals("unavailable",row(freshBindings,"id").get("current").get("state").asString());assertEquals("four",row(freshBindings,"id").get("target").get("text").asString());
        var target=ok(client.request("POST",path(plan,"entities"),page("6","target",0,100),true));assertEquals(4,target.get("total").asInt());assertEquals(fresh,item(target,"four").get("entity"));
        var joined=JSON.createArrayNode();for(int offset=0;offset<4;offset++){var part=ok(client.request("POST",path(plan,"entities"),page("6","target",offset,1),true));assertEquals(4,part.get("total").asInt());joined.add(part.get("items").get(0));}assertEquals(target.get("items"),joined);
        assertEquals(0,ok(client.request("POST",path(plan,"containment"),draft("6",0,100),true)).get("total").asInt());
        command(client,plan,"6","7",Map.of("kind","upsert-entity","decision",Map.of("kind","remove","entity",three),"placements",List.of()));
        var all=ok(client.request("POST",path(plan,"draft"),draft("7",0,100),true));assertEquals(4,all.get("total").asInt());boolean removed=false;for(var entry:all.get("items"))if(entry.get("entity").equals(three)){removed=true;assertEquals("remove",entry.get("disposition").asString());assertTrue(entry.get("fields").isEmpty());assertTrue(entry.get("references").isEmpty());assertTrue(entry.get("placements").isEmpty());}assertTrue(removed);
        String expected="<items><!-- mock -->\r\n<item id='one' tone='beta' finish='x' secret='MOCK-SECRET-DRAFT' optional='' next='renamed'/><item id='renamed' tone='alpha' finish='y'/><item xmlns=\"\" finish=\"z\" id=\"four\" tone=\"gamma\"/></items>";
        var inventory=ok(client.request("POST",path(plan,"documents"),revision("7"),true));assertEquals(digest(expected),inventory.get("documents").get(0).get("targetDigest").asString());
    }
    @Test void structuralRoutesRetainClosedRevisionAndOwnershipBoundaries() throws Exception {
        var owner=login("mock-structural-boundary-"+UUID.randomUUID());String plan=create(owner);inspect(owner,plan);var foreign=login("mock-structural-foreign-"+UUID.randomUUID());
        var entity=item(ok(owner.request("POST",path(plan,"entities"),page("2","current",0,100),true)),"one").get("entity");
        for(String view:List.of("relations","draft","containment","placements","bindings")){
            early(foreign.request("POST",path(plan,view),"{",true),404,"NOT_FOUND");assertEquals(403,owner.request("POST",path(plan,view),"{}",false).status());assertEquals(400,owner.request("POST",path(plan,view),"{",true).status());
            String stale=switch(view){case "relations"->page("1","current",0,100);case "placements"->placements("1","items");case "bindings"->bindings("1",entity,0,100);default->draft("1",0,100);};assertEquals(409,owner.request("POST",path(plan,view),stale,true).status());
        }
        var beyond=ok(owner.request("POST",path(plan,"bindings"),bindings("2",entity,256,100),true));assertEquals(5,beyond.get("total").asInt());assertTrue(beyond.get("items").isEmpty());assertTrue(beyond.get("nextOffset").isNull());assertEquals(400,owner.request("POST",path(plan,"bindings"),bindings("2",entity,257,1),true).status());
        assertEquals(400,owner.request("POST",path(plan,"draft"),"{\"revision\":\"2\",\"offset\":0,\"limit\":1,\"revealSecrets\":true}",true).status());
        var summary=JSON.readTree(owner.get("/api/v3/plans/"+plan).body());assertEquals("2",summary.get("revision").asString());assertFalse(summary.get("targetComplete").asBoolean());
    }
    @Test void heldBindingRequestPreservesSingleScratchAndCrossOwnerRecovery() throws Exception {
        String subject="mock-structural-held-"+UUID.randomUUID();var owner=login(subject);String plan=create(owner);inspect(owner,plan);var entity=item(ok(owner.request("POST",path(plan,"entities"),page("2","current",0,100),true)),"one").get("entity");var other=login("mock-structural-waiter-"+UUID.randomUUID());String b=create(other);inspect(other,b);
        V3StructuralHttpTestConfiguration.awaitRecords(0);byte[] body=bindings("2",entity,0,100).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var pending=owner.begin("POST",path(plan,"bindings"),body.length,true)){
            pending.write(new byte[]{'{'});V3StructuralHttpTestConfiguration.awaitRecords(1);early(other.request("POST",path(b,"draft"),draft("2",0,100),true),429,"CAPACITY");assertEquals(200,other.get("/api/v3/plans/"+b).status());
            var logout=owner.request("POST","/api/v1/session/logout","{}",true);if(logout.status()==204)assertEquals("",logout.body());else{assertEquals(503,logout.status());assertEquals("SESSION_CLEANUP_INCONCLUSIVE",JSON.readTree(logout.body()).get("code").asString());}
            V3StructuralHttpTestConfiguration.awaitRecords(0);assertEquals("",pending.response().body());
        }
        assertEquals(0,ok(other.request("POST",path(b,"draft"),draft("2",0,100),true)).get("total").asInt());var renewed=login(subject);early(renewed.get("/api/v3/plans/current"),404,"NOT_FOUND");
    }
}
