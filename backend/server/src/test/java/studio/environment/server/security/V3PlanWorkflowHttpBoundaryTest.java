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
import studio.environment.server.plan.V3WorkflowHttpSocketClient;
import studio.environment.server.plan.V3WorkflowHttpTestConfiguration;
import studio.environment.server.workspace.SqliteDraftStore;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"studio.mode=hosted",
        "studio.security.public-origin=http://localhost", "studio.security.client-id=mock-client",
        "studio.security.client-secret=mock-platform-secret", "studio.security.allow-test-http=true",
        "logging.level.org.springframework.web=DEBUG"})
@ActiveProfiles("oidc-test")
@Import(V3WorkflowHttpTestConfiguration.class)
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
class V3PlanWorkflowHttpBoundaryTest {
    static final MockIssuer issuer = new MockIssuer();
    static final Path workspace = initialize();
    static final JsonMapper JSON = JsonMapper.builder().build();
    static Path initialize() {
        try { var path = Files.createTempDirectory("es-v3-workflow-http-mock-"); SqliteDraftStore.initializeV3(path); return path; }
        catch (java.io.IOException failure) { throw new IllegalStateException("MOCK_STORAGE_UNAVAILABLE"); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("studio.security.issuer", issuer::issuer);
        properties.add("studio.workspace.directory", workspace::toString);
    }
    @LocalServerPort int port;
    final List<V3WorkflowHttpSocketClient> clients = new ArrayList<>();
    final List<String> codes = new ArrayList<>(), csrf = new ArrayList<>();
    @AfterAll static void stopIssuer() { issuer.close(); }
    @AfterEach void logoutAndCheckCanaries(CapturedOutput output) throws Exception {
        for (var client : clients) client.request("POST", "/api/v1/session/logout", "{}", true);
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
    V3WorkflowHttpSocketClient login(String owner) throws Exception {
        issuer.subject = owner; issuer.mode = MockIssuer.TokenMode.VALID;
        var client = new V3WorkflowHttpSocketClient(port); clients.add(client);
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
                "definition", Map.of("objectId", V3WorkflowHttpTestConfiguration.OBJECT, "workspaceRevision", "2"),
                "bindingId", "mock-pg", "destinationId", "mock-destination"));
    }
    String create(V3WorkflowHttpSocketClient client) throws Exception {
        var reply = client.request("POST", "/api/v3/plans", createBody(), true);
        assertEquals(201, reply.status());
        return JSON.readTree(reply.body()).get("planId").asString();
    }
    String reserve(V3WorkflowHttpSocketClient client, String plan, String revision) throws Exception {
        var reply = client.request("POST", "/api/v3/plans/"+plan+"/inspections", reservationBody(revision), true);
        assertEquals(202, reply.status());
        return JSON.readTree(reply.body()).get("operationId").asString();
    }
    static String reservationBody(String revision) {
        return JSON.writeValueAsString(Map.of("expectedRevision", revision, "requestId", UUID.randomUUID().toString(), "discardDraftOnSuccess", true));
    }
    static void early(V3WorkflowHttpSocketClient.Response response, int status, String code) {
        assertEquals(status, response.status()); assertEquals("", response.body());
        assertEquals(code, response.headers().get("x-environment-studio-code"));
        assertEquals("0", response.headers().get("content-length"));
        assertEquals("no-store", response.headers().get("cache-control"));
    }

    void inspect(V3WorkflowHttpSocketClient client, String plan) throws Exception {
        String operation = reserve(client,plan,"1");
        var response = client.request("POST","/api/v3/operations/"+operation+"/credentials",
                JSON.writeValueAsString(Map.of("username","MockV3Reader","password","MockV3-Password-𐀀")),true);
        assertEquals(200,response.status()); assertEquals("succeeded",JSON.readTree(response.body()).get("phase").asString(),response.body());
        assertEquals("2",JSON.readTree(response.body()).get("installedRevision").asString());
    }
    static String revision(String value) { return JSON.writeValueAsString(Map.of("revision",value)); }
    static String path(String plan,String view) {return "/api/v3/plans/"+plan+"/views/"+view;}
    static String page(String revision,String side,int offset,int limit) {return JSON.writeValueAsString(Map.of("revision",revision,"side",side,"offset",offset,"limit",limit));}
    static tools.jackson.databind.JsonNode ok(V3WorkflowHttpSocketClient.Response response) {
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
    tools.jackson.databind.JsonNode document(V3WorkflowHttpSocketClient client,String plan,String revision,String side,String id,String mode)throws Exception{
        var response=client.request("POST",path(plan,"document"),docBody(revision,side,id,mode),true);assertEquals(200,response.status());assertEquals("no-store",response.headers().get("cache-control"));var value=JSON.readTree(response.body());
        assertEquals(revision,value.get("revision").asString());assertEquals(id,value.get("documentId").asString());assertEquals(side,value.get("side").asString());assertEquals(mode,value.get("mode").asString());assertEquals(mode.equals("raw"),value.get("exact").asBoolean());assertEquals(mode.equals("placeholders"),value.get("redacted").asBoolean());assertTrue(value.get("unmappedConcreteMayRemain").asBoolean());assertTrue(value.get("omissions").isEmpty());assertEquals(9,value.size());return value;
    }
    static tools.jackson.databind.JsonNode binding(tools.jackson.databind.JsonNode page,String id){for(var value:page.get("items"))if(value.get("fieldId").asString().equals(id))return value;throw new AssertionError("MOCK_BINDING_MISSING");}
    tools.jackson.databind.JsonNode bindings(V3WorkflowHttpSocketClient client,String plan,String revision,tools.jackson.databind.JsonNode entity)throws Exception{return ok(client.request("POST",path(plan,"bindings"),JSON.writeValueAsString(Map.of("revision",revision,"entity",entity,"offset",0,"limit",100)),true));}
    static String token(tools.jackson.databind.JsonNode page,String id){return binding(page,id).get("token").asString();}
    static String slice(String source,tools.jackson.databind.JsonNode location){return source.substring(location.get("span").get("start").asInt(),location.get("span").get("end").asInt());}
    static String computedPath(String plan,String route){return "/api/v3/plans/"+plan+"/views/computed/"+route;}
    static Map<String,Object> key(String type,String derivation,String value){return Map.of("computedType",type,"derivation",derivation,"value",value);}
    static String contributors(String revision,String side,Object selector,int offset,int limit){return JSON.writeValueAsString(Map.of("revision",revision,"side",side,"selector",selector,"offset",offset,"limit",limit,"completeDocumentDisclosure",true));}
    static Map<String,Object> nodeSelector(String value){return Map.of("kind","node","key",key("tones","by-tone",value));}
    tools.jackson.databind.JsonNode computed(V3WorkflowHttpSocketClient client,String plan,String revision,String side,String route,int offset,int limit)throws Exception{return ok(client.request("POST",computedPath(plan,route),page(revision,side,offset,limit),true));}
    @Test void captureAndValidationUseTheActualOriginalXmlAndCompleteTargetRules()throws Exception {
        var client=login("mock-workflow-"+UUID.randomUUID());String plan=create(client);inspect(client,plan);
        var physical=ok(client.request("POST",path(plan,"entities"),page("2","current",0,100),true));
        var mappings=new ArrayList<Map<String,Object>>();int index=0;
        for(String identity:List.of("one","two","three"))mappings.add(Map.of("entity",item(physical,identity).get("entity"),"slotId","slot-"+(++index),"label","Neutral "+index));
        var capture=ok(client.request("POST","/api/v3/plans/"+plan+"/profile-captures"," ".repeat(20000)+JSON.writeValueAsString(Map.of("revision","2","profileId","mock-profile","profileRevision","1","mappings",mappings)),true));
        assertEquals(4,capture.size());assertEquals("json",capture.get("format").asString());
        var source=JSON.readTree(capture.get("source").asString());assertEquals("3",source.get("schemaVersion").asString());
        assertEquals(3,source.get("entities").size());assertFalse(capture.get("source").asString().contains("alpha"));
        var summary=ok(client.request("POST","/api/v3/plans/"+plan+"/validations",revision("2"),true));
        assertEquals(7,summary.size());assertEquals(10,summary.get("checks").size());assertFalse(summary.get("targetComplete").asBoolean());assertTrue(summary.get("computedRuleCount").isNull());assertFalse(summary.get("exportAvailable").asBoolean());
        ok(client.request("POST","/api/v3/plans/"+plan+"/materializations",revision("2"),true));
        var complete=ok(client.request("POST","/api/v3/plans/"+plan+"/validations",revision("2"),true));
        assertTrue(complete.get("targetComplete").asBoolean());assertEquals(2,complete.get("computedRuleCount").asInt());
        var request=new LinkedHashMap<String,Object>(Map.of("revision","2","section","computed-rules","inputFingerprint",complete.get("inputFingerprint").asString(),"offset",0,"limit",1));
        var first=ok(client.request("POST","/api/v3/plans/"+plan+"/validations",JSON.writeValueAsString(request),true));assertEquals(2,first.get("total").asInt());assertEquals(1,first.get("nextOffset").asInt());assertEquals(1,first.get("items").size());
        request.put("inputFingerprint",summary.get("inputFingerprint").asString());assertEquals(409,client.request("POST","/api/v3/plans/"+plan+"/validations",JSON.writeValueAsString(request),true).status());
    }
    @Test void validationSummaryIsAvailableWithoutMaterializingATarget()throws Exception {
        var client=login("mock-workflow-validation-"+UUID.randomUUID());String plan=create(client);inspect(client,plan);
        var summary=ok(client.request("POST","/api/v3/plans/"+plan+"/validations",revision("2"),true));
        assertEquals(10,summary.get("checks").size());assertTrue(summary.get("computedRuleCount").isNull());
    }
    @Test void previewReachesOwnedPublicationLookupAndReportsMissingProfile()throws Exception {
        var client=login("mock-workflow-preview-"+UUID.randomUUID());String plan=create(client);inspect(client,plan);
        ok(client.request("POST","/api/v3/plans/"+plan+"/materializations",revision("2"),true));
        var request=Map.of("revision","2","profile",Map.of("objectId",UUID.randomUUID().toString(),"workspaceRevision","1"),"selection",Map.of("kind","all"),"section","included","offset",0,"limit",100);
        var response=client.request("POST","/api/v3/plans/"+plan+"/profile-previews",JSON.writeValueAsString(request),true);
        assertEquals(404,response.status());assertEquals("NOT_FOUND",JSON.readTree(response.body()).get("code").asString());
    }

    @Test void wholeAndPartialCaptureSaveHistoryPreviewAndExplicitReuseRemainOwned()throws Exception {
        String subject="mock-workflow-reuse-"+UUID.randomUUID();var client=login(subject);
        for(boolean whole:List.of(false,true)){
            String plan=create(client);inspect(client,plan);
            var owner=V3WorkflowHttpTestConfiguration.leases.get(subject).owner();
            if(!whole)studio.environment.server.workspace.V3WorkflowStorageFixtures.definition(workspace,owner,V3WorkflowHttpTestConfiguration.OBJECT);
            var physical=ok(client.request("POST",path(plan,"entities"),page("2","current",0,100),true));
            var mappings=new ArrayList<Map<String,Object>>();int index=0;
            for(String identity:List.of("one","two","three"))mappings.add(Map.of("entity",item(physical,identity).get("entity"),"slotId","slot-"+(++index),"label","Neutral "+index));
            var capture=ok(client.request("POST","/api/v3/plans/"+plan+"/profile-captures",JSON.writeValueAsString(Map.of("revision","2","profileId","mock-profile","profileRevision","1","mappings",mappings)),true));
            String profile=UUID.randomUUID().toString(),profilePath="/api/v3/profiles/"+profile;
            String save=JSON.writeValueAsString(Map.of("expectedRevision","0","requestId",UUID.randomUUID().toString(),"format","JSON","source",capture.get("source").asString(),"definition",capture.get("definition")));
            var saved=ok(client.request("PUT",profilePath,save,true));assertEquals(capture.get("source"),saved.get("source"));
            assertEquals(saved,ok(client.get(profilePath+"/revisions/1")));assertEquals(saved,ok(client.request("PUT",profilePath,save,true)));
            var checked=assertInstanceOf(studio.environment.server.profile.V3ProfileBytesAdapter.Result.Accepted.class,new studio.environment.server.profile.V3ProfileBytesAdapter().read(studio.environment.server.planning.V3WorkflowHttpWitnesses.definition(),capture.get("source").asString().getBytes(java.nio.charset.StandardCharsets.UTF_8),studio.environment.server.definition.BoundedDocumentParser.Format.JSON)).checked();
            // Explicit test-only current publication witness; source hash is not production publication admission.
            V3WorkflowHttpTestConfiguration.profiles.put(owner,new studio.environment.core.plan.PlanPorts.PublishedProfile(new studio.environment.core.workspace.NativeCommand.Reference(profile,"1"),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(capture.get("source").asString().getBytes(java.nio.charset.StandardCharsets.UTF_8))),checked));
            ok(client.request("POST","/api/v3/plans/"+plan+"/materializations",revision("2"),true));
            var selection=whole?Map.of("kind","all"):Map.of("kind","selected","roots",List.of("slot-1"));
            var previewRequest=new LinkedHashMap<String,Object>(Map.of("revision","2","profile",Map.of("objectId",profile,"workspaceRevision","1"),"selection",selection,"section","included","offset",0,"limit",100));
            var preview=ok(client.request("POST","/api/v3/plans/"+plan+"/profile-previews",JSON.writeValueAsString(previewRequest),true));
            assertEquals(whole?3:2,preview.get("total").asInt());assertEquals(JSON.valueToTree(List.of("by-finish","by-tone")),preview.get("affectedDerivations"));assertEquals(9,preview.size());
            previewRequest.put("section","dependencies");var dependencyPage=ok(client.request("POST","/api/v3/plans/"+plan+"/profile-previews",JSON.writeValueAsString(previewRequest),true));assertEquals(whole?0:1,dependencyPage.get("total").asInt());if(!whole){assertEquals("slot-2",dependencyPage.get("items").get(0).get("slotId").asString());assertEquals("declared-reuse-target",dependencyPage.get("items").get(0).get("reason").asString());}
            previewRequest.put("section","included");
            previewRequest.put("offset",50000);var beyond=ok(client.request("POST","/api/v3/plans/"+plan+"/profile-previews",JSON.writeValueAsString(previewRequest),true));assertTrue(beyond.get("items").isEmpty());assertEquals(preview.get("previewDigest"),beyond.get("previewDigest"));assertEquals(preview.get("affectedDerivations"),beyond.get("affectedDerivations"));
            var choices=new ArrayList<Map<String,Object>>();
            for(var row:preview.get("items"))choices.add(Map.of("kind","use-existing","slotId",row.get("slotId").asString(),"target",item(physical,List.of("one","two","three").get(Integer.parseInt(row.get("slotId").asString().substring(5))-1)).get("entity")));
            var command=new LinkedHashMap<String,Object>(Map.of("kind","compose-profile","expectedRevision","2","requestId",UUID.randomUUID().toString(),"profile",preview.get("pins").get("profile"),"previewDigest",preview.get("previewDigest").asString(),"selectedRoots",preview.get("pins").get("selectedRoots"),"decisions",choices));
            var changed=ok(client.request("POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(command),true));assertEquals("3",changed.get("revision").asString());assertEquals(changed,ok(client.request("POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(command),true)));
            var unresolved=ok(client.request("POST","/api/v3/plans/"+plan+"/validations",revision("3"),true));assertFalse(unresolved.get("targetComplete").asBoolean());assertTrue(unresolved.get("computedRuleCount").isNull());
            var changes=new ArrayList<Map<String,Object>>();
            for(var choice:choices){var fields=new LinkedHashMap<String,Object>();for(String field:List.of("id","tone","finish","secret","optional","extra"))fields.put(field,Map.of("kind","keep-observed"));changes.add(Map.of("decision",Map.of("kind","retain","entity",choice.get("target"),"fields",fields,"references",Map.of("link",Map.of("kind","keep-observed"))),"placements",List.of()));}
            ok(client.request("POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(Map.of("expectedRevision","3","requestId",UUID.randomUUID().toString(),"kind","batch-upsert","changes",changes,"containment",List.of())),true));
            assertEquals(studio.environment.server.planning.V3WorkflowHttpWitnesses.SHEET,document(client,plan,"4","target","sheet","raw").get("text").asString());assertEquals(studio.environment.server.planning.V3WorkflowHttpWitnesses.TAIL,document(client,plan,"4","target","tail","raw").get("text").asString());
            var validation=ok(client.request("POST","/api/v3/plans/"+plan+"/validations",revision("4"),true));assertTrue(validation.get("targetComplete").asBoolean());assertFalse(validation.get("exportAvailable").asBoolean());
            if(whole)splitThroughReturnedProfileAndPlacement(client,plan,profile,physical);
            var foreign=login("mock-workflow-foreign-"+UUID.randomUUID());assertEquals(404,foreign.get(profilePath).status());early(foreign.request("POST","/api/v3/plans/"+plan+"/profile-previews","{",true),404,"NOT_FOUND");
            ok(client.request("POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(Map.of("expectedRevision",whole?"6":"4","requestId",UUID.randomUUID().toString(),"kind","discard")),true));
        }
    }

    @Test void workflowOwnershipCsrfAndStalledBodyKeepTheOriginalScratch()throws Exception {
        String subject="mock-workflow-held-"+UUID.randomUUID();var client=login(subject);String plan=create(client);inspect(client,plan);
        var other=login("mock-workflow-other-"+UUID.randomUUID());String otherPlan=create(other);inspect(other,otherPlan);
        for(String route:List.of("profile-captures","profile-previews","validations")){
            String url="/api/v3/plans/"+plan+"/"+route;
            early(other.request("POST",url,"{",true),404,"NOT_FOUND");assertEquals(403,client.request("POST",url,revision("2"),false).status());assertEquals(400,client.request("POST",url,"{",true).status());
        }
        V3WorkflowHttpTestConfiguration.awaitRecords(0);
        try(var pending=client.begin("POST","/api/v3/plans/"+plan+"/profile-captures",100000,true)){
            pending.write(new byte[]{'{'});V3WorkflowHttpTestConfiguration.awaitRecords(1);
            early(other.request("POST","/api/v3/plans/"+otherPlan+"/validations",revision("2"),true),429,"CAPACITY");
            assertEquals(200,other.get("/api/v3/plans/"+otherPlan).status());
            var logout=client.request("POST","/api/v1/session/logout","{}",true);assertTrue(logout.status()==204 || logout.status()==503);
            V3WorkflowHttpTestConfiguration.awaitRecords(0);assertEquals("",pending.responseAfterCancellation().body());
        }
        ok(other.request("POST","/api/v3/plans/"+otherPlan+"/validations",revision("2"),true));early(login(subject).get("/api/v3/plans/current"),404,"NOT_FOUND");
    }

    void splitThroughReturnedProfileAndPlacement(V3WorkflowHttpSocketClient client,String plan,String profile,tools.jackson.databind.JsonNode original)throws Exception {
        var preview=ok(client.request("POST","/api/v3/plans/"+plan+"/profile-previews",JSON.writeValueAsString(Map.of("revision","4","profile",Map.of("objectId",profile,"workspaceRevision","1"),"selection",Map.of("kind","selected","roots",List.of("slot-1")),"section","included","offset",0,"limit",100)),true));
        assertEquals(2,preview.get("total").asInt());
        var choices=List.of(Map.of("kind","create","slotId","slot-1","targetSlotId","split-a"),Map.of("kind","create","slotId","slot-2","targetSlotId","split-b"));
        ok(client.request("POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(Map.of("expectedRevision","4","requestId",UUID.randomUUID().toString(),"kind","compose-profile","profile",preview.get("pins").get("profile"),"previewDigest",preview.get("previewDigest").asString(),"selectedRoots",preview.get("pins").get("selectedRoots"),"decisions",choices)),true));
        var draft=ok(client.request("POST",path(plan,"draft"),JSON.writeValueAsString(Map.of("revision","5","offset",0,"limit",100)),true));
        var fresh=new HashMap<String,tools.jackson.databind.JsonNode>();for(var row:draft.get("items")){var entity=row.get("entity");if(entity.get("kind").asString().equals("fresh"))fresh.put(entity.get("slotId").asString(),entity);}
        assertEquals(Set.of("split-a","split-b"),fresh.keySet());
        var sheet=ok(client.request("POST",path(plan,"placements"),JSON.writeValueAsString(Map.of("revision","5","documentId","sheet","projectionId","items","offset",0,"limit",100)),true)).get("items").get(0);
        var tail=ok(client.request("POST",path(plan,"placements"),JSON.writeValueAsString(Map.of("revision","5","documentId","tail","projectionId","tail-items","offset",0,"limit",100)),true)).get("items").get(0);
        var changes=new ArrayList<Map<String,Object>>();int i=0;
        for(String slot:List.of("split-a","split-b")){
            var fields=new LinkedHashMap<String,Object>();fields.put("id",Map.of("kind","entered","text",slot));fields.put("tone",Map.of("kind","entered","text",i==0?"gamma":"delta"));fields.put("finish",Map.of("kind","entered","text",i==0?"x":"y"));for(String field:List.of("secret","optional","extra"))fields.put(field,Map.of("kind","absent"));
            var coordinate=i==0?sheet:tail;String document=i==0?"sheet":"tail",projection=i==0?"items":"tail-items";
            var parent=Map.of("kind","existing","documentId",coordinate.get("documentId").asString(),"sourceDigest",coordinate.get("sourceDigest").asString(),"elementIndex",coordinate.get("elementIndex").asString());
            var refs=Map.of("link",Map.of("kind","to","target",item(original,"two").get("entity")));
            changes.add(Map.of("decision",Map.of("kind","create","entity",fresh.get(slot),"fields",fields,"references",refs),"placements",List.of(Map.of("entity",fresh.get(slot),"documentId",document,"projectionId",projection,"parent",parent))));i++;
        }
        changes.add(Map.of("decision",Map.of("kind","remove","entity",item(original,"one").get("entity")),"placements",List.of()));
        ok(client.request("POST","/api/v3/plans/"+plan+"/commands",JSON.writeValueAsString(Map.of("expectedRevision","5","requestId",UUID.randomUUID().toString(),"kind","batch-upsert","changes",changes,"containment",List.of())),true));
        String expectedSheet="<items><!-- mock -->\r\n<unmapped sample='alpha'/><item xmlns=\"\" finish=\"x\" id=\"split-a\" next=\"two\" tone=\"gamma\"/></items>";
        String expectedTail="<items><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x' next='two'/><item xmlns=\"\" finish=\"y\" id=\"split-b\" next=\"two\" tone=\"delta\"/></items>";
        assertEquals(expectedSheet,document(client,plan,"6","target","sheet","raw").get("text").asString());assertEquals(expectedTail,document(client,plan,"6","target","tail","raw").get("text").asString());
        assertEquals(studio.environment.server.planning.V3WorkflowHttpWitnesses.SHEET,document(client,plan,"6","current","sheet","raw").get("text").asString());assertEquals(studio.environment.server.planning.V3WorkflowHttpWitnesses.TAIL,document(client,plan,"6","current","tail","raw").get("text").asString());
        assertFalse(document(client,plan,"6","target","sheet","placeholders").get("text").asString().contains("gamma"));assertTrue(document(client,plan,"6","target","tail","formatted").get("text").asString().contains("split-b"));
        var entities=ok(client.request("POST",path(plan,"entities"),page("6","target",0,100),true));assertEquals(4,entities.get("total").asInt());assertEquals(item(original,"three").get("entity"),item(entities,"three").get("entity"));
        var locations=ok(client.request("POST",path(plan,"binding-locations"),locations("6",fresh.get("split-a"),"tone","target",0,100),true));assertTrue(locations.get("total").asInt()>0);
        var contributors=ok(client.request("POST",computedPath(plan,"contributors"),contributors("6","target",nodeSelector("gamma"),0,100),true));assertEquals(fresh.get("split-a"),contributors.get("items").get(0).get("physical"));assertEquals(digest(expectedSheet),contributors.get("items").get(0).get("origin").get("sourceDigest").asString());
        var validation=ok(client.request("POST","/api/v3/plans/"+plan+"/validations",revision("6"),true));assertTrue(validation.get("targetComplete").asBoolean());assertEquals(4,validation.get("computedRuleCount").asInt());
    }

    @Test void actualXmlValidationReachesTailAbove50000AndRecoversOversizedPagesAtSameOffset()throws Exception {
        for(boolean wide:List.of(false,true)){
            String subject="mock-workflow-large-"+UUID.randomUUID();var client=login(subject);
            V3WorkflowHttpTestConfiguration.largeOwners.put(new studio.environment.core.session.Owner(issuer.issuer(),subject),wide);
            String plan=create(client);inspect(client,plan);
            var materialized=ok(client.request("POST","/api/v3/plans/"+plan+"/materializations",revision("2"),true));assertTrue(materialized.get("complete").asBoolean());
            var summary=ok(client.request("POST","/api/v3/plans/"+plan+"/validations",revision("2"),true));
            int total=wide?96:64000;assertEquals(total,summary.get("computedRuleCount").asInt());
            var request=new LinkedHashMap<String,Object>(Map.of("revision","2","section","computed-rules","inputFingerprint",summary.get("inputFingerprint").asString(),"offset",wide?0:63999,"limit",100));
            if(wide){var refused=client.request("POST","/api/v3/plans/"+plan+"/validations",JSON.writeValueAsString(request),true);assertEquals(422,refused.status());assertEquals("RESOURCE_LIMIT",JSON.readTree(refused.body()).get("code").asString());request.put("limit",1);}
            var page=ok(client.request("POST","/api/v3/plans/"+plan+"/validations",JSON.writeValueAsString(request),true));assertEquals(total,page.get("total").asInt());assertEquals(1,page.get("items").size());assertEquals(summary.get("inputFingerprint"),page.get("inputFingerprint"));
            if(wide){assertEquals(0,page.get("offset").asInt());assertEquals(1,page.get("nextOffset").asInt());request.put("offset",95);var last=ok(client.request("POST","/api/v3/plans/"+plan+"/validations",JSON.writeValueAsString(request),true));assertEquals(1,last.get("items").size());assertTrue(last.get("nextOffset").isNull());}
            else{assertEquals(63999,page.get("offset").asInt());assertTrue(page.get("nextOffset").isNull());assertEquals("pair-9",page.get("items").get(0).get("declaration").asString());assertTrue(page.get("items").get(0).get("source").get("value").asString().endsWith("1999"));}
        }
    }

}
