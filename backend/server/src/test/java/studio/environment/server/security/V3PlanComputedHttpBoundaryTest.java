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
import studio.environment.server.plan.V3DocumentHttpTestConfiguration;
import studio.environment.server.workspace.SqliteDraftStore;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"studio.mode=hosted",
        "studio.security.public-origin=http://localhost", "studio.security.client-id=mock-client",
        "studio.security.client-secret=mock-platform-secret", "studio.security.allow-test-http=true",
        "logging.level.org.springframework.web=DEBUG"})
@ActiveProfiles("oidc-test")
@Import(V3DocumentHttpTestConfiguration.class)
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
class V3PlanComputedHttpBoundaryTest {
    static final MockIssuer issuer = new MockIssuer();
    static final Path workspace = initialize();
    static final JsonMapper JSON = JsonMapper.builder().build();
    static Path initialize() {
        try { var path = Files.createTempDirectory("es-v3-computed-http-mock-"); SqliteDraftStore.initializeV3(path); return path; }
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
        try { V3DocumentHttpTestConfiguration.awaitRecords(0); }
        finally {
            for (var client : clients) client.request("POST", "/api/v1/session/logout", "{}", true);
        }
        V3DocumentHttpTestConfiguration.awaitRecords(0);
        var forbidden = new ArrayList<>(List.of("MOCK-DOC-SECRET", "MockV3-Password", "MockV3Reader", "mock-platform-secret", "mock-access-canary",
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
    /** Sequential journeys await actual settlement; contention probes keep raw requests. */
    PlanHttpSocketClient.Response sequentialRequest(PlanHttpSocketClient client, String method, String path, String body, boolean token) throws Exception {
        V3DocumentHttpTestConfiguration.awaitRecords(0);
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
                "definition", Map.of("objectId", V3DocumentHttpTestConfiguration.OBJECT, "workspaceRevision", "2"),
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
        assertEquals(200,response.status()); assertEquals("succeeded",JSON.readTree(response.body()).get("phase").asString(),response.body());
        assertEquals("2",JSON.readTree(response.body()).get("installedRevision").asString());
    }
    static String revision(String value) { return JSON.writeValueAsString(Map.of("revision",value)); }
    static String path(String plan,String view) {return "/api/v3/plans/"+plan+"/views/"+view;}
    static String page(String revision,String side,int offset,int limit) {return JSON.writeValueAsString(Map.of("revision",revision,"side",side,"offset",offset,"limit",limit));}
    static tools.jackson.databind.JsonNode ok(PlanHttpSocketClient.Response response) {
        assertEquals(200,response.status());assertEquals("no-store",response.headers().get("cache-control"));
        for(String privateValue:List.of("MOCK-DOC-SECRET","MOCK-SECRET-VIEW","MOCK-UNKNOWN-VIEW","MOCK-DENIED-VIEW"))assertFalse(response.body().contains(privateValue));
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
    static String docBody(String revision,String side,String document,String mode){return JSON.writeValueAsString(Map.of("revision",revision,"side",side,"documentId",document,"mode",mode,"completeDocumentDisclosure",true));}
    static String locations(String revision,tools.jackson.databind.JsonNode entity,String field,String side,int offset,int limit){return JSON.writeValueAsString(Map.of("revision",revision,"entity",entity,"fieldId",field,"side",side,"offset",offset,"limit",limit,"completeDocumentDisclosure",true));}
    tools.jackson.databind.JsonNode document(PlanHttpSocketClient client,String plan,String revision,String side,String id,String mode)throws Exception{
        var response=sequentialRequest(client,"POST",path(plan,"document"),docBody(revision,side,id,mode),true);assertEquals(200,response.status());assertEquals("no-store",response.headers().get("cache-control"));var value=JSON.readTree(response.body());
        assertEquals(revision,value.get("revision").asString());assertEquals(id,value.get("documentId").asString());assertEquals(side,value.get("side").asString());assertEquals(mode,value.get("mode").asString());assertEquals(mode.equals("raw"),value.get("exact").asBoolean());assertEquals(mode.equals("placeholders"),value.get("redacted").asBoolean());assertTrue(value.get("unmappedConcreteMayRemain").asBoolean());assertTrue(value.get("omissions").isEmpty());assertEquals(9,value.size());return value;
    }
    static tools.jackson.databind.JsonNode binding(tools.jackson.databind.JsonNode page,String id){for(var value:page.get("items"))if(value.get("fieldId").asString().equals(id))return value;throw new AssertionError("MOCK_BINDING_MISSING");}
    tools.jackson.databind.JsonNode bindings(PlanHttpSocketClient client,String plan,String revision,tools.jackson.databind.JsonNode entity)throws Exception{return ok(sequentialRequest(client,"POST",path(plan,"bindings"),JSON.writeValueAsString(Map.of("revision",revision,"entity",entity,"offset",0,"limit",100)),true));}
    static String token(tools.jackson.databind.JsonNode page,String id){return binding(page,id).get("token").asString();}
    static String slice(String source,tools.jackson.databind.JsonNode location){return source.substring(location.get("span").get("start").asInt(),location.get("span").get("end").asInt());}
    static String computedPath(String plan,String route){return "/api/v3/plans/"+plan+"/views/computed/"+route;}
    static Map<String,Object> key(String type,String derivation,String value){return Map.of("computedType",type,"derivation",derivation,"value",value);}
    static String contributors(String revision,String side,Object selector,int offset,int limit){return JSON.writeValueAsString(Map.of("revision",revision,"side",side,"selector",selector,"offset",offset,"limit",limit,"completeDocumentDisclosure",true));}
    static Map<String,Object> nodeSelector(String value){return Map.of("kind","node","key",key("tones","by-tone",value));}
    tools.jackson.databind.JsonNode computed(PlanHttpSocketClient client,String plan,String revision,String side,String route,int offset,int limit)throws Exception{return ok(sequentialRequest(client,"POST",computedPath(plan,route),page(revision,side,offset,limit),true));}
    @Test void computedCollectionsPreserveCompleteOrderingAndActualContributorLocations()throws Exception{
        var client=login("mock-computed-"+UUID.randomUUID());String plan=create(client);inspect(client,plan);
        var nodes=computed(client,plan,"2","current","nodes",0,100);assertEquals(4,nodes.get("total").asInt());
        assertEquals(JSON.valueToTree(List.of(Map.of("key",key("finishes","by-finish","x"),"contributorTotal",2),Map.of("key",key("finishes","by-finish","y"),"contributorTotal",1),Map.of("key",key("tones","by-tone","alpha"),"contributorTotal",2),Map.of("key",key("tones","by-tone","beta"),"contributorTotal",1))),nodes.get("items"));
        var joined=JSON.createArrayNode();for(int i=0;i<4;i++){var p=computed(client,plan,"2","current","nodes",i,1);joined.add(p.get("items").get(0));assertEquals(4,p.get("total").asInt());if(i==3)assertTrue(p.get("nextOffset").isNull());else assertEquals(i+1,p.get("nextOffset").asInt());}assertEquals(nodes.get("items"),joined);
        assertEquals(6,computed(client,plan,"2","current","memberships",0,100).get("total").asInt());assertEquals(3,computed(client,plan,"2","current","cooccurrences",0,100).get("total").asInt());
        var rules=computed(client,plan,"2","current","rules",0,100);assertEquals(2,rules.get("total").asInt());for(var rule:rules.get("items")){assertEquals("COOCCURRENCE",rule.get("kind").asString());assertEquals("pair",rule.get("declaration").asString());assertEquals("PASS",rule.get("outcome").asString());assertEquals("0",rule.get("minimum").asString());assertEquals("10",rule.get("maximum").asString());}assertEquals("2",rules.get("items").get(0).get("actual").asString());assertEquals("1",rules.get("items").get(1).get("actual").asString());
        var physical=ok(sequentialRequest(client,"POST",path(plan,"entities"),page("2","current",0,100),true));var one=item(physical,"one").get("entity");var two=item(physical,"two").get("entity");
        var alpha=ok(sequentialRequest(client,"POST",computedPath(plan,"contributors"),contributors("2","current",nodeSelector("alpha"),0,100),true));assertEquals(2,alpha.get("total").asInt());assertEquals(one,alpha.get("items").get(0).get("physical"));assertEquals(two,alpha.get("items").get(1).get("physical"));
        var sources=Map.of("sheet",studio.environment.server.planning.V3DocumentHttpWitnesses.SHEET,"tail",studio.environment.server.planning.V3DocumentHttpWitnesses.TAIL);
        for(var contributor:alpha.get("items")){var origin=contributor.get("origin");String id=origin.get("documentId").asString();assertEquals("1",origin.get("elementIndex").asString());assertEquals(digest(sources.get(id)),origin.get("sourceDigest").asString());assertEquals(JSON.valueToTree(List.of("0")),origin.get("ancestry"));assertEquals(1,contributor.get("roles").size());var role=contributor.get("roles").get(0);assertEquals("tone",role.get("field").asString());var location=role.get("location");assertTrue(location.get("selector").isNull());var pin=location.get("value");assertEquals("alpha",pin.get("decodedValue").asString());assertEquals("'",pin.get("quote").asString());assertEquals(id.equals("sheet")?"al&#112;ha":"alpha",sources.get(id).substring(pin.get("valueStart").asInt(),pin.get("valueEnd").asInt()));}
        var pair=Map.of("kind","cooccurrence","relation","pair","source",key("tones","by-tone","alpha"),"target",key("finishes","by-finish","x"));var evidence=ok(sequentialRequest(client,"POST",computedPath(plan,"contributors"),contributors("2","current",pair,0,100),true));assertEquals(1,evidence.get("total").asInt());assertEquals(one,evidence.get("items").get(0).get("physical"));assertEquals("tone",evidence.get("items").get(0).get("roles").get(0).get("field").asString());assertEquals("finish",evidence.get("items").get(0).get("roles").get(1).get("field").asString());
    }
    @Test void targetEditsPreserveReferencesAndRemoveOnlyLastComputedContributor()throws Exception{
        var client=login("mock-computed-edits-"+UUID.randomUUID());String plan=create(client);inspect(client,plan);var physical=ok(sequentialRequest(client,"POST",path(plan,"entities"),page("2","current",0,100),true));var one=item(physical,"one").get("entity");var three=item(physical,"three").get("entity");
        assertEquals(422,sequentialRequest(client,"POST",computedPath(plan,"nodes"),page("2","target",0,100),true).status());
        var fields=new LinkedHashMap<String,Object>();for(String id:List.of("id","finish","secret","optional","extra"))fields.put(id,Map.of("kind","keep-observed"));fields.put("tone",Map.of("kind","entered","text","beta"));
        var decision=Map.of("kind","retain","entity",one,"fields",fields,"references",Map.of("link",Map.of("kind","keep-observed")));var ack=sequentialRequest(client,"POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(Map.of("expectedRevision","2","requestId",UUID.randomUUID().toString(),"kind","upsert-entity","decision",decision,"placements",List.of())),true);assertEquals(200,ack.status());
        var beta=ok(sequentialRequest(client,"POST",computedPath(plan,"contributors"),contributors("3","target",nodeSelector("beta"),0,100),true));assertEquals(2,beta.get("total").asInt());assertEquals(one,beta.get("items").get(0).get("physical"));var original=ok(sequentialRequest(client,"POST",computedPath(plan,"contributors"),contributors("3","current",nodeSelector("alpha"),0,100),true));assertEquals(2,original.get("total").asInt());
        var pair=Map.of("kind","cooccurrence","relation","pair","source",key("tones","by-tone","beta"),"target",key("finishes","by-finish","x"));assertEquals(2,ok(sequentialRequest(client,"POST",computedPath(plan,"contributors"),contributors("3","target",pair,0,100),true)).get("total").asInt());
        fields.put("id",Map.of("kind","entered","text","renamed"));assertEquals(200,sequentialRequest(client,"POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(Map.of("expectedRevision","3","requestId",UUID.randomUUID().toString(),"kind","upsert-entity","decision",decision,"placements",List.of())),true).status());
        assertEquals(one,ok(sequentialRequest(client,"POST",computedPath(plan,"contributors"),contributors("4","target",nodeSelector("beta"),0,100),true)).get("items").get(0).get("physical"));
        int revision=4;for(var ref:List.of(one,three)){var removed=sequentialRequest(client,"POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(Map.of("expectedRevision",Integer.toString(revision),"requestId",UUID.randomUUID().toString(),"kind","upsert-entity","decision",Map.of("kind","remove","entity",ref),"placements",List.of())),true);assertEquals(200,removed.status());revision++;if(revision==5)assertEquals(1,ok(sequentialRequest(client,"POST",computedPath(plan,"contributors"),contributors("5","target",nodeSelector("beta"),0,100),true)).get("total").asInt());}
        assertEquals(404,sequentialRequest(client,"POST",computedPath(plan,"contributors"),contributors("6","target",nodeSelector("beta"),0,100),true).status());assertEquals(2,computed(client,plan,"6","target","nodes",0,100).get("total").asInt());assertEquals(4,computed(client,plan,"6","current","nodes",0,100).get("total").asInt());
    }
    @Test void computedConsentOwnershipAndHeldBodyRetainOriginalCapacity()throws Exception{
        String subject="mock-computed-held-"+UUID.randomUUID();var owner=login(subject);String plan=create(owner);inspect(owner,plan);var foreign=login("mock-computed-other-"+UUID.randomUUID());String other=create(foreign);inspect(foreign,other);
        for(String route:List.of("nodes","memberships","cooccurrences","rules","contributors")){String request=route.equals("contributors")?contributors("2","current",nodeSelector("alpha"),0,100):page("2","current",0,100);early(sequentialRequest(foreign,"POST",computedPath(plan,route),"{",true),404,"NOT_FOUND");assertEquals(403,sequentialRequest(owner,"POST",computedPath(plan,route),request,false).status());assertEquals(400,sequentialRequest(owner,"POST",computedPath(plan,route),"{",true).status());assertEquals(409,sequentialRequest(owner,"POST",computedPath(plan,route),request.replace("\"revision\":\"2\"","\"revision\":\"1\""),true).status());}
        var base=new LinkedHashMap<String,Object>(Map.of("revision","2","side","current","selector",nodeSelector("missing"),"offset",Integer.MAX_VALUE,"limit",1));for(Object consent:List.of(false,"true",1)){base.put("completeDocumentDisclosure",consent);assertEquals(400,sequentialRequest(owner,"POST",computedPath(plan,"contributors"),JSON.writeValueAsString(base),true).status());}base.remove("completeDocumentDisclosure");assertEquals(400,sequentialRequest(owner,"POST",computedPath(plan,"contributors"),JSON.writeValueAsString(base),true).status());
        var beyond=ok(sequentialRequest(owner,"POST",computedPath(plan,"contributors"),contributors("2","current",nodeSelector("alpha"),Integer.MAX_VALUE,100),true));assertEquals(2,beyond.get("total").asInt());assertTrue(beyond.get("items").isEmpty());assertTrue(beyond.get("nextOffset").isNull());assertEquals(404,sequentialRequest(owner,"POST",computedPath(plan,"contributors"),contributors("2","current",nodeSelector("x".repeat(20_000)),0,1),true).status());
        V3DocumentHttpTestConfiguration.awaitRecords(0);byte[] body=contributors("2","current",nodeSelector("alpha"),0,100).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var pending=owner.begin("POST",computedPath(plan,"contributors"),body.length,true)){pending.write(new byte[]{'{'});V3DocumentHttpTestConfiguration.awaitRecords(1);early(foreign.request("POST",computedPath(other,"nodes"),page("2","current",0,100),true),429,"CAPACITY");assertEquals(200,foreign.get("/api/v3/plans/"+other).status());var logout=owner.request("POST","/api/v1/session/logout","{}",true);if(logout.status()==204)assertEquals("",logout.body());else{assertEquals(503,logout.status());assertEquals("SESSION_CLEANUP_INCONCLUSIVE",JSON.readTree(logout.body()).get("code").asString());}V3DocumentHttpTestConfiguration.awaitRecords(0);assertEquals("",pending.response().body());}
        assertEquals(4,computed(foreign,other,"2","current","nodes",0,100).get("total").asInt());early(login(subject).get("/api/v3/plans/current"),404,"NOT_FOUND");
    }
}
