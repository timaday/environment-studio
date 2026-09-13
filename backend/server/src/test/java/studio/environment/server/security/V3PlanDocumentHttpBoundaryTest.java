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
class V3PlanDocumentHttpBoundaryTest {
    static final MockIssuer issuer = new MockIssuer();
    static final Path workspace = initialize();
    static final JsonMapper JSON = JsonMapper.builder().build();
    static Path initialize() {
        try { var path = Files.createTempDirectory("es-v3-document-http-independent-mock-"); SqliteDraftStore.initializeV3(path); return path; }
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
    @Test void actualMultiDocumentModesAndLocationsPreserveExactXmlAndQualifiedChildMappings() throws Exception{
        var client=login("mock-document-workflow-"+UUID.randomUUID());String plan=create(client);inspect(client,plan);
        var entities=ok(sequentialRequest(client,"POST",path(plan,"entities"),page("2","current",0,100),true));var one=item(entities,"one").get("entity");var two=item(entities,"two").get("entity");var oneBindings=bindings(client,plan,"2",one);var twoBindings=bindings(client,plan,"2",two);
        String sheet=studio.environment.server.planning.V3DocumentHttpWitnesses.SHEET,tail=studio.environment.server.planning.V3DocumentHttpWitnesses.TAIL;
        assertEquals(sheet,document(client,plan,"2","current","sheet","raw").get("text").asString());assertEquals(tail,document(client,plan,"2","current","tail","raw").get("text").asString());
        String placeholder="<items><!-- mock -->\r\n<item id='%s' tone='%s' finish='%s' next='%s' secret='%s' optional='%s'><prop key='extra' value='%s'/><prop key='other' value='alpha'/></item><unmapped sample='alpha'/></items>".formatted(token(oneBindings,"id"),token(oneBindings,"tone"),token(oneBindings,"finish"),token(twoBindings,"id"),token(oneBindings,"secret"),token(oneBindings,"optional"),token(oneBindings,"extra"));
        assertEquals(placeholder,document(client,plan,"2","current","sheet","placeholders").get("text").asString());
        String formatted="<items><!-- mock -->\r\n\n  <item id='one' tone='al&#112;ha' finish='x' next='two' secret='MOCK-DOC-SECRET' optional=''>\n    <prop key='extra' value='al&#112;ha'/>\n    <prop key='other' value='alpha'/>\n  </item>\n  <unmapped sample='alpha'/>\n</items>";
        assertEquals(formatted,document(client,plan,"2","current","sheet","formatted").get("text").asString());assertEquals("masked",binding(oneBindings,"secret").get("current").get("state").asString());assertFalse(oneBindings.toString().contains("MOCK-DOC-SECRET"));
        var sources=Map.of("sheet",sheet,"tail",tail);var documentIds=new ArrayList<String>();var roles=new ArrayList<String>();
        for(int offset=0;offset<3;offset++){
            var page=ok(sequentialRequest(client,"POST",path(plan,"binding-locations"),locations("2",two,"id","current",offset,1),true));assertEquals(3,page.get("total").asInt());assertEquals(1,page.get("items").size());if(offset==2)assertTrue(page.get("nextOffset").isNull());else assertEquals(offset+1,page.get("nextOffset").asInt());var location=page.get("items").get(0);String id=location.get("documentId").asString();documentIds.add(id);roles.add(location.get("role").asString());assertEquals("two",slice(sources.get(id),location));assertEquals(digest(sources.get(id)),location.get("sourceDigest").asString());
        }
        assertEquals(List.of("sheet","tail","tail"),documentIds);assertEquals(List.of("reference","field","reference"),roles);assertEquals(3,binding(twoBindings,"id").get("currentLocations").get("total").asInt());
        var child=ok(sequentialRequest(client,"POST",path(plan,"binding-locations"),locations("2",one,"extra","current",0,100),true)).get("items").get(0);assertEquals("2",child.get("elementIndex").asString());assertEquals("value",child.get("attribute").get("localName").asString());assertEquals("extra",child.get("declarationId").asString());assertEquals("al&#112;ha",slice(sheet,child));assertEquals(sheet.indexOf("al&#112;ha",sheet.indexOf("key='extra'")),child.get("span").get("start").asInt());
        var fields=new LinkedHashMap<String,Object>();for(String id:List.of("id","finish","secret","optional"))fields.put(id,Map.of("kind","keep-observed"));fields.put("tone",Map.of("kind","entered","text","beta"));fields.put("extra",Map.of("kind","entered","text","gamma & 𐀀"));
        var command=sequentialRequest(client,"POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(Map.of("expectedRevision","2","requestId",UUID.randomUUID().toString(),"kind","upsert-entity","decision",Map.of("kind","retain","entity",one,"fields",fields,"references",Map.of("link",Map.of("kind","keep-observed"))),"placements",List.of())),true);assertEquals(200,command.status());assertEquals("3",JSON.readTree(command.body()).get("revision").asString());
        String expected="<items><!-- mock -->\r\n<item id='one' tone='beta' finish='x' next='two' secret='MOCK-DOC-SECRET' optional=''><prop key='extra' value='gamma &amp; 𐀀'/><prop key='other' value='alpha'/></item><unmapped sample='alpha'/></items>";
        assertEquals(expected,document(client,plan,"3","target","sheet","raw").get("text").asString());assertEquals(sheet,document(client,plan,"3","current","sheet","raw").get("text").asString());assertEquals(tail,document(client,plan,"3","target","tail","raw").get("text").asString());assertEquals(placeholder,document(client,plan,"3","target","sheet","placeholders").get("text").asString());
        var targetLocation=ok(sequentialRequest(client,"POST",path(plan,"binding-locations"),locations("3",one,"extra","target",0,100),true)).get("items").get(0);assertEquals("gamma &amp; 𐀀",slice(expected,targetLocation));assertEquals(digest(expected),targetLocation.get("sourceDigest").asString());assertEquals(targetLocation.get("span").get("start").asInt()+"gamma &amp; 𐀀".length(),targetLocation.get("span").get("end").asInt());
        assertEquals("masked",binding(bindings(client,plan,"3",one),"secret").get("target").get("state").asString());
    }
    @Test void disclosureAndFreshLocationBoundariesApplyBeforeEmptyOrUnavailableResults() throws Exception{
        var owner=login("mock-document-boundary-"+UUID.randomUUID());String plan=create(owner);inspect(owner,plan);var entities=ok(sequentialRequest(owner,"POST",path(plan,"entities"),page("2","current",0,100),true));var one=item(entities,"one").get("entity");var foreign=login("mock-document-foreign-"+UUID.randomUUID());
        for(String route:List.of("document","binding-locations")){early(sequentialRequest(foreign,"POST",path(plan,route),"{",true),404,"NOT_FOUND");assertEquals(403,sequentialRequest(owner,"POST",path(plan,route),"{}",false).status());assertEquals(400,sequentialRequest(owner,"POST",path(plan,route),"{",true).status());}
        for(String mode:List.of("raw","placeholders","formatted")){
            assertEquals(422,sequentialRequest(owner,"POST",path(plan,"document"),docBody("2","target","sheet",mode),true).status());
            for(Object disclosure:List.of(false,"true",1))assertEquals(400,sequentialRequest(owner,"POST",path(plan,"document"),JSON.writeValueAsString(Map.of("revision","2","side","current","documentId","sheet","mode",mode,"completeDocumentDisclosure",disclosure)),true).status());
            assertEquals(400,sequentialRequest(owner,"POST",path(plan,"document"),JSON.writeValueAsString(Map.of("revision","2","side","current","documentId","sheet","mode",mode)),true).status());
        }
        assertEquals(400,sequentialRequest(owner,"POST",path(plan,"binding-locations"),JSON.writeValueAsString(Map.of("revision","2","entity",one,"fieldId","extra","side","current","offset",Integer.MAX_VALUE,"limit",1)),true).status());
        assertEquals(400,sequentialRequest(owner,"POST",path(plan,"binding-locations"),JSON.writeValueAsString(Map.of("revision","2","entity",one,"fieldId","extra","side","current","offset",Integer.MAX_VALUE,"limit",1,"completeDocumentDisclosure",false)),true).status());
        var beyond=ok(sequentialRequest(owner,"POST",path(plan,"binding-locations"),locations("2",one,"extra","current",Integer.MAX_VALUE,100),true));assertEquals(1,beyond.get("total").asInt());assertTrue(beyond.get("items").isEmpty());assertTrue(beyond.get("nextOffset").isNull());
        assertEquals(422,sequentialRequest(owner,"POST",path(plan,"binding-locations"),locations("2",one,"extra","target",0,1),true).status());assertEquals(404,sequentialRequest(owner,"POST",path(plan,"binding-locations"),locations("2",one,"unknown","current",0,1),true).status());
        assertEquals(409,sequentialRequest(owner,"POST",path(plan,"document"),docBody("1","current","sheet","raw"),true).status());assertEquals(409,sequentialRequest(owner,"POST",path(plan,"binding-locations"),locations("1",one,"id","current",0,1),true).status());
        var places=ok(sequentialRequest(owner,"POST",path(plan,"placements"),JSON.writeValueAsString(Map.of("revision","2","documentId","sheet","projectionId","items","offset",0,"limit",100)),true));var coordinate=places.get("items").get(0);var fresh=Map.of("kind","fresh","slotId","fresh-child","typeId","item");var freshFields=new LinkedHashMap<String,Object>();for(String id:List.of("secret","optional"))freshFields.put(id,Map.of("kind","absent"));freshFields.put("id",Map.of("kind","entered","text","fresh"));freshFields.put("tone",Map.of("kind","entered","text","gamma"));freshFields.put("finish",Map.of("kind","entered","text","z"));freshFields.put("extra",Map.of("kind","entered","text","fresh-𐀀"));
        var placement=Map.of("entity",fresh,"documentId","sheet","projectionId","items","parent",Map.of("kind","existing","documentId",coordinate.get("documentId").asString(),"sourceDigest",coordinate.get("sourceDigest").asString(),"elementIndex",coordinate.get("elementIndex").asString()));
        var ack=sequentialRequest(owner,"POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(Map.of("expectedRevision","2","requestId",UUID.randomUUID().toString(),"kind","upsert-entity","decision",Map.of("kind","create","entity",fresh,"fields",freshFields,"references",Map.of("link",Map.of("kind","absent"))),"placements",List.of(placement))),true);assertEquals(200,ack.status());var freshRef=JSON.valueToTree(fresh);
        assertEquals(404,sequentialRequest(owner,"POST",path(plan,"binding-locations"),locations("3",freshRef,"extra","current",0,1),true).status());
        var created=ok(sequentialRequest(owner,"POST",path(plan,"binding-locations"),locations("3",freshRef,"extra","target",0,100),true));assertEquals(1,created.get("total").asInt());String raw=document(owner,plan,"3","target","sheet","raw").get("text").asString();assertEquals("fresh-𐀀",slice(raw,created.get("items").get(0)));assertEquals("3",JSON.readTree(owner.get("/api/v3/plans/"+plan).body()).get("revision").asString());
    }
    @Test void partialDocumentRequestRetainsOriginalScratchUntilLogoutWorkerClosure() throws Exception{
        String subject="mock-document-held-"+UUID.randomUUID();var first=login(subject);String a=create(first);inspect(first,a);var second=login("mock-document-waiter-"+UUID.randomUUID());String b=create(second);inspect(second,b);
        V3DocumentHttpTestConfiguration.awaitRecords(0);byte[] body=docBody("2","current","sheet","raw").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var pending=first.begin("POST",path(a,"document"),body.length,true)){
            pending.write(new byte[]{'{'});V3DocumentHttpTestConfiguration.awaitRecords(1);early(second.request("POST",path(b,"document"),docBody("2","current","tail","raw"),true),429,"CAPACITY");assertEquals(200,second.get("/api/v3/plans/"+b).status());var logout=first.request("POST","/api/v1/session/logout","{}",true);if(logout.status()==204)assertEquals("",logout.body());else{assertEquals(503,logout.status());assertEquals("SESSION_CLEANUP_INCONCLUSIVE",JSON.readTree(logout.body()).get("code").asString());}V3DocumentHttpTestConfiguration.awaitRecords(0);assertEquals("",pending.response().body());
        }
        assertEquals(studio.environment.server.planning.V3DocumentHttpWitnesses.TAIL,document(second,b,"2","current","tail","raw").get("text").asString());var renewed=login(subject);early(renewed.get("/api/v3/plans/current"),404,"NOT_FOUND");
    }
}
