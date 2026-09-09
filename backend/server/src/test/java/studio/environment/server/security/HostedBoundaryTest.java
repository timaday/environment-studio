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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"studio.mode=hosted", "studio.security.public-origin=http://localhost",
        "logging.level.org.springframework.web=DEBUG", "logging.level.org.springframework.web.client.DefaultRestClient=TRACE",
        "studio.security.client-id=mock-client", "studio.security.client-secret=mock-platform-secret", "studio.security.allow-test-http=true"})
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@ActiveProfiles("oidc-test")
@org.springframework.context.annotation.Import(studio.environment.server.plan.PlanHttpTestConfiguration.class)
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

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) { properties.add("studio.security.issuer", issuer::issuer); properties.add("studio.workspace.directory", () -> workspace.toString()); properties.add("studio.workspace.definition-publishers[0].issuer", issuer::issuer); properties.add("studio.workspace.definition-publishers[0].subject", () -> "workspace-maintainer");
        properties.add("studio.workspace.definition-publishers[1].issuer", issuer::issuer);properties.add("studio.workspace.definition-publishers[1].subject",()->"plan-maintainer");
        properties.add("studio.workspace.definition-publishers[2].issuer", issuer::issuer);properties.add("studio.workspace.definition-publishers[2].subject",()->"transport-maintainer");
        properties.add("studio.workspace.definition-publishers[3].issuer",issuer::issuer);properties.add("studio.workspace.definition-publishers[3].subject",()->"view-maintainer");
        properties.add("studio.workspace.definition-publishers[4].issuer",issuer::issuer);properties.add("studio.workspace.definition-publishers[4].subject",()->"binding-maintainer"); }
    @org.springframework.boot.test.web.server.LocalServerPort int port;
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
        studio.environment.server.plan.PlanHttpTestConfiguration.clock.reset();
        try {
            String persisted=new String(java.nio.file.Files.readAllBytes(workspace.resolve("studio-workspace.db")),java.nio.charset.StandardCharsets.ISO_8859_1);
            for(String canary:java.util.List.of("mock-platform-secret","mock-access-canary","Db-Password-Canary"))assertFalse(persisted.contains(canary),"Authentication canary entered workspace persistence");
            for(String token:issuer.issuedTokens)assertFalse(persisted.contains(token),"ID token entered workspace persistence");
        }catch(java.io.IOException failure){throw new AssertionError("Mock persistence canary scan unavailable");}

        for(String verifier:issuer.receivedVerifiers)assertFalse(output.getAll().contains(verifier),"PKCE verifier leaked to logs");
        for(String code:authorizationCodeCanaries)assertFalse(output.getAll().contains(code),"Authorization code leaked to logs");
        assertFalse(output.getAll().contains("code_verifier=["),"PKCE verifier form field leaked to logs");
        assertFalse(output.getAll().contains("Db-Password-Canary"),"Database credential canary leaked to logs");
        assertFalse(output.getAll().contains("second-palette"),"Entered plan value leaked to logs");
        assertFalse(output.getAll().contains("Hidden-View-Canary"),"Masked view value leaked to logs");
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
    final java.util.List<String> authorizationCodeCanaries=new java.util.ArrayList<>();
    final java.util.List<String> csrfCanaries = new java.util.ArrayList<>();
    @BeforeEach void setup() {
        assertFalse(org.apache.commons.logging.LogFactory.getLog("org.springframework.web.client.DefaultRestClient").isDebugEnabled(),"Sensitive child logger remains verbose");
        assertTrue(org.apache.commons.logging.LogFactory.getLog("org.springframework.web.servlet.DispatcherServlet").isDebugEnabled(),"Other request DEBUG coverage must remain active");
        issuer.mode = MockIssuer.TokenMode.VALID;
        issuer.subject = "invented-" + java.util.UUID.randomUUID();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stopIssuer() { issuer.close(); }
    studio.environment.server.plan.PlanHttpSocketClient socketLogin(String subject) throws Exception {return socketLogin(subject,302);}
    studio.environment.server.plan.PlanHttpSocketClient socketLogin(String subject,int callbackStatus) throws Exception {
        issuer.subject=subject;
        var client=new studio.environment.server.plan.PlanHttpSocketClient(port);
        var start=client.get("/oauth2/authorization/studio");assertEquals(302,start.status());
        var provider=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(start.headers().get("location"))).build(),HttpResponse.BodyHandlers.discarding());
        assertEquals(302,provider.statusCode());
        var callback=URI.create(provider.headers().firstValue("Location").orElseThrow());
        authorizationCodeCanaries.add(MockIssuer.parameters(callback.getRawQuery()).get("code"));
        assertEquals(callbackStatus,client.get(callback.getRawPath()+"?"+callback.getRawQuery()).status());
        if(callbackStatus!=302)return client;
        var session=client.get("/api/v1/session");assertEquals(200,session.status());
        var tree=tools.jackson.databind.json.JsonMapper.builder().build().readTree(session.body());
        String token=tree.get("csrfToken").asString();csrfCanaries.add(token);client.csrf(tree.get("csrfHeaderName").asString(),token);
        return client;
    }
    record SocketPlan(studio.environment.server.plan.PlanHttpSocketClient client,String planId) { }
    SocketPlan socketPlan(String owner) throws Exception {return socketPlan(owner,java.nio.file.Files.readString(java.nio.file.Path.of("../../fixtures/native-v2/definition.json")));}
    SocketPlan socketPlan(String owner,String source) throws Exception {
        var client=socketLogin(owner);var json=tools.jackson.databind.json.JsonMapper.builder().build();
        String objectId=java.util.UUID.randomUUID().toString(),path="/api/v2/definitions/"+objectId;
        var saved=client.request("PUT",path,json.writeValueAsString(Map.of("expectedRevision","0","requestId",java.util.UUID.randomUUID().toString(),"format","JSON","source",source)),true);
        assertEquals(200,saved.status());
        var definition=json.readTree(saved.body());var policies=new java.util.ArrayList<Map<String,String>>();
        for(var binding:definition.get("projection").get("model").get("bindings"))for(var document:binding.get("documents"))policies.add(Map.of("bindingId",binding.get("id").asString(),"documentId",document.get("id").asString(),"content","deny"));
        assertEquals(200,client.request("POST",path+"/publish",json.writeValueAsString(Map.of("expectedRevision","1","requestId",java.util.UUID.randomUUID().toString(),"exportPolicies",policies)),true).status());
        var created=client.request("POST","/api/v1/plans",json.writeValueAsString(Map.of("expectedRevision","0","requestId",java.util.UUID.randomUUID().toString(),"definition",Map.of("objectId",objectId,"workspaceRevision","2"),"bindingId","mock-pg","destinationId","mock-destination")),true);
        assertEquals(201,created.status());return new SocketPlan(client,json.readTree(created.body()).get("planId").asString());
    }
    String reserve(SocketPlan plan,String revision) throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var response=plan.client.request("POST","/api/v1/plans/"+plan.planId+"/inspections",json.writeValueAsString(Map.of("expectedRevision",revision,"requestId",java.util.UUID.randomUUID().toString(),"discardDraftOnSuccess",true)),true);
        assertEquals(202,response.status());return json.readTree(response.body()).get("operationId").asString();
    }
    @org.junit.jupiter.api.RepeatedTest(8) void repeatedIndependentMockCredentialBodiesStayExact() throws Exception {
        var plan=socketPlan("view-maintainer");
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        try {for(int index=0;index<32;index++) {
            var current=plan.client().get("/api/v1/plans/"+plan.planId());assertEquals(200,current.status());
            String revision=json.readTree(current.body()).get("revision").asString();
            String operation=reserve(plan,revision);
            int connections=studio.environment.server.plan.PlanHttpTestConfiguration.connections.get();
            studio.environment.server.plan.PlanHttpTestConfiguration.exactCredentials.set(false);
            var response=plan.client().request("POST","/api/v1/operations/"+operation+"/credentials",mockCredentials(),true);
            assertEquals(200,response.status(),"A fresh complete mock credential body must be accepted");
            assertTrue(studio.environment.server.plan.PlanHttpTestConfiguration.exactCredentials.get());
            assertEquals("succeeded",json.readTree(response.body()).get("phase").asString());
            assertEquals(connections+1,studio.environment.server.plan.PlanHttpTestConfiguration.connections.get());
        }
        } finally {plan.client().request("POST","/api/v1/session/logout","{}",true);}
    }
    static String mockCredentials() {return "{\"username\":\"MockReader\",\"password\":\"Db-Password-Canary-𐀀\"}";}
    @Test void actualHttpOneToTwoCrossDocumentTargetReplayForeignBodyAndFailedInspection() throws Exception {
        var plan=socketPlan("plan-maintainer");var client=plan.client;var json=tools.jackson.databind.json.JsonMapper.builder().build();
        String views="/api/v1/plans/"+plan.planId+"/views/";
        assertEquals(422,client.request("POST",views+"entities","{\"revision\":\"1\",\"side\":\"current\",\"offset\":0,\"limit\":1}",true).status());
        String operation=reserve(plan,"1");
        assertEquals(403,client.request("POST","/api/v1/operations/"+operation+"/credentials",mockCredentials(),false).status());
        var inspected=client.request("POST","/api/v1/operations/"+operation+"/credentials",mockCredentials(),true);assertEquals(200,inspected.status());
        assertEquals("succeeded",json.readTree(inspected.body()).get("phase").asString());
        assertTrue(studio.environment.server.plan.PlanHttpTestConfiguration.exactCredentials.get(),"Credential values changed across HTTP boundary");
        var inventory=client.request("POST",views+"documents","{\"revision\":\"2\"}",true);assertEquals(200,inventory.status());
        assertEquals(2,json.readTree(inventory.body()).get("documents").size());
        assertEquals(403,client.request("POST",views+"documents","{\"revision\":\"2\"}",false).status());
        assertEquals(403,client.get(views+"documents").status());
        var entityPage=json.readTree(client.request("POST",views+"entities","{\"revision\":\"2\",\"side\":\"current\",\"offset\":0,\"limit\":100}",true).body());
        assertEquals(3,entityPage.get("total").asInt());var mappings=new java.util.ArrayList<Map<String,Object>>();String selectedSlot=null;int slot=0;
        for(var entity:entityPage.get("items")){String slotId="neutral-"+slot++;mappings.add(Map.of("entity",Map.of("kind","existing","handle",entity.get("entity").get("handle").asString()),"slotId",slotId,"label","Neutral slot "+slot));if(entity.get("typeId").asString().equals("glyph"))selectedSlot=slotId;}
        String catalogBefore=client.get("/api/v2/profiles").body();
        var captured=client.request("POST","/api/v1/plans/"+plan.planId+"/profile-captures",json.writeValueAsString(Map.of("revision","2","profileId","neutral-capture","profileRevision","1","mappings",mappings)),true);assertEquals(200,captured.status());
        assertEquals(catalogBefore,client.get("/api/v2/profiles").body(),"Capture must not save a profile");
        var portable=json.readTree(captured.body());String portableSource=portable.get("source").asString();assertFalse(portableSource.contains("alpha"));assertFalse(portableSource.contains("shared"));assertFalse(portableSource.contains("warm"));
        String profileId=java.util.UUID.randomUUID().toString();var definitionRef=Map.of("objectId",portable.get("definition").get("objectId").asString(),"workspaceRevision",portable.get("definition").get("workspaceRevision").asString());
        assertEquals(200,client.request("PUT","/api/v2/profiles/"+profileId,json.writeValueAsString(Map.of("expectedRevision","0","requestId",java.util.UUID.randomUUID().toString(),"format","JSON","source",portableSource,"definition",definitionRef)),true).status());
        assertEquals(200,client.request("POST","/api/v2/profiles/"+profileId+"/publish",json.writeValueAsString(Map.of("expectedRevision","1","requestId",java.util.UUID.randomUUID().toString())),true).status());
        var profileRef=Map.of("objectId",profileId,"workspaceRevision","2");String previewPath="/api/v1/plans/"+plan.planId+"/profile-previews";
        var allPreview=json.readTree(client.request("POST",previewPath,json.writeValueAsString(Map.of("revision","2","profile",profileRef,"selection",Map.of("kind","all"),"section","included","offset",0,"limit",1)),true).body());
        assertEquals(3,allPreview.get("total").asInt());assertEquals(1,allPreview.get("nextOffset").asInt());assertEquals(1,allPreview.get("items").size());
        String previewToken=allPreview.get("previewDigest").asString();
        var secondPage=json.readTree(client.request("POST",previewPath,json.writeValueAsString(Map.of("revision","2","profile",profileRef,"selection",Map.of("kind","all"),"section","included","offset",1,"limit",1)),true).body());assertEquals(previewToken,secondPage.get("previewDigest").asString());
        for(String section:java.util.List.of("included","dependencies","relations","conflicts")){
            var partial=client.request("POST",previewPath,json.writeValueAsString(Map.of("revision","2","profile",profileRef,"selection",Map.of("kind","selected","roots",java.util.List.of(selectedSlot)),"section",section,"offset",0,"limit",100)),true);assertEquals(200,partial.status());
            int total=json.readTree(partial.body()).get("total").asInt();assertEquals(section.equals("included")?2:section.equals("conflicts")?0:1,total);
        }
        var parents=json.readTree(client.request("POST",views+"placements","{\"revision\":\"2\",\"documentId\":\"palette-sheet\",\"projectionId\":\"palettes\",\"offset\":0,\"limit\":100}",true).body());assertEquals(1,parents.get("total").asInt());assertEquals("0",parents.get("items").get(0).get("elementIndex").asString());


        var service=studio.environment.server.plan.PlanHttpTestConfiguration.installed;
        var lease=studio.environment.server.plan.PlanHttpTestConfiguration.leases.get("plan-maintainer");
        String alpha=service.entities(lease,plan.planId,"2",false,0,100).entities().stream().filter(e->e.type().equals("glyph") && e.fields().stream().anyMatch(f->f.field().equals("tag") && f.value().orElse("").equals("alpha"))).findFirst().orElseThrow().handle();
        String paletteSource=java.nio.file.Files.readString(java.nio.file.Path.of("../../fixtures/structural-target/palettes.xml"));
        String parentDigest=((studio.environment.server.xml.XmlResult.Accepted)new studio.environment.server.xml.LosslessXmlAdapter().project(paletteSource)).document().digest();
        var original=Map.of("kind","existing","handle",alpha);var fresh=Map.of("kind","fresh","slotId","new-palette","typeId","palette");
        var keep=Map.of("kind","keep-observed");
        var changes=java.util.List.of(
            Map.of("decision",Map.of("kind","retain","entity",original,"fields",Map.of("tag",keep,"tone",keep),"references",Map.of("uses",Map.of("kind","to","target",fresh))),"placements",java.util.List.of()),
            Map.of("decision",Map.of("kind","create","entity",fresh,"fields",Map.of("tag",Map.of("kind","entered","text","second-palette"),"shade",Map.of("kind","entered","text","cool & \t𐀀")),"references",Map.of()),
                "placements",java.util.List.of(Map.of("entity",fresh,"documentId","palette-sheet","projectionId","palettes","parent",Map.of("kind","existing","documentId","palette-sheet","sourceDigest",parentDigest,"elementIndex","0")))));
        String requestId=java.util.UUID.randomUUID().toString();String body=json.writeValueAsString(Map.of("kind","batch-upsert","expectedRevision","2","requestId",requestId,"changes",changes,"containment",java.util.List.of()));
        String commands="/api/v1/plans/"+plan.planId+"/commands";
        var changed=client.request("POST",commands,body,true);assertEquals(200,changed.status());assertEquals("3",json.readTree(changed.body()).get("revision").asString());
        var summary=json.readTree(client.get("/api/v1/plans/"+plan.planId).body());assertTrue(summary.get("targetComplete").asBoolean());assertEquals(4,summary.get("targetCounts").get("entities").asInt());
        for(var pair:java.util.List.of(java.util.List.of("glyph-sheet","expected-glyphs.xml"),java.util.List.of("palette-sheet","expected-palettes.xml")))
            assertEquals(java.nio.file.Files.readString(java.nio.file.Path.of("../../fixtures/structural-target",pair.get(1))),service.comparison(lease,plan.planId,"3",true,pair.getFirst(),studio.environment.core.plan.PlanPorts.ViewMode.RAW,true).text());
        for(String mode:java.util.List.of("raw","placeholders","formatted")){
            var document=client.request("POST",views+"document",json.writeValueAsString(Map.of("revision","3","side","target","documentId","palette-sheet","mode",mode,"completeDocumentDisclosure",true)),true);assertEquals(200,document.status());var display=json.readTree(document.body());
            assertTrue(display.get("unmappedConcreteMayRemain").asBoolean());if(mode.equals("raw"))assertEquals(java.nio.file.Files.readString(java.nio.file.Path.of("../../fixtures/structural-target/expected-palettes.xml")),display.get("text").asString());
        }
        assertEquals(400,client.request("POST",views+"document","{\"revision\":\"3\",\"side\":\"target\",\"documentId\":\"palette-sheet\",\"mode\":\"raw\",\"completeDocumentDisclosure\":false}",true).status());
        assertEquals(409,client.request("POST",previewPath,json.writeValueAsString(Map.of("revision","2","profile",profileRef,"selection",Map.of("kind","all"),"section","included","offset",0,"limit",1)),true).status());
        for(String section:java.util.List.of("draft","containment")){var page=json.readTree(client.request("POST",views+section,"{\"revision\":\"3\",\"offset\":0,\"limit\":100}",true).body());assertEquals(section.equals("draft")?2:0,page.get("total").asInt());}
        var relations=json.readTree(client.request("POST",views+"relations","{\"revision\":\"3\",\"side\":\"target\",\"offset\":0,\"limit\":100}",true).body());assertEquals(2,relations.get("total").asInt());
        assertTrue(relations.get("items").toString().contains("fresh"));
        var validation=json.readTree(client.request("POST","/api/v1/plans/"+plan.planId+"/validations","{\"revision\":\"3\"}",true).body());assertEquals(10,validation.get("checks").size());assertEquals(2,validation.get("applicationRules").size());assertFalse(validation.get("exportAvailable").asBoolean());
        assertEquals(200,client.request("POST","/api/v1/plans/"+plan.planId+"/materializations","{\"revision\":\"3\"}",true).status());
        assertEquals(changed.body(),client.request("POST",commands,body,true).body());
        assertEquals(409,client.request("POST",commands,json.writeValueAsString(Map.of("kind","discard","expectedRevision","2","requestId",requestId)),true).status());
        var foreign=socketLogin("foreign-plan-"+java.util.UUID.randomUUID());
        assertEquals(404,foreign.get("/api/v1/plans/"+plan.planId).status());
        try(var pending=foreign.begin("POST",views+"document",16_384,true)){assertEquals(404,pending.response().status());}

        long before=System.nanoTime();try(var pending=foreign.begin("POST","/api/v1/operations/"+operation+"/credentials",16_384,true)){assertEquals(404,pending.response().status());}
        assertTrue(System.nanoTime()-before<2_000_000_000L,"Foreign body was awaited");
        int calls=studio.environment.server.plan.PlanHttpTestConfiguration.connections.get();
        String malformed=reserve(plan,"3");
        assertEquals(400,client.request("POST","/api/v1/operations/"+malformed+"/credentials","{",true).status());
        assertEquals(409,client.request("POST","/api/v1/operations/"+malformed+"/credentials",mockCredentials(),true).status());
        assertEquals(calls,studio.environment.server.plan.PlanHttpTestConfiguration.connections.get());
        assertFalse(json.readTree(client.get("/api/v1/plans/"+plan.planId).body()).get("inspectionValid").asBoolean());
        assertEquals(200,client.request("POST",commands,json.writeValueAsString(Map.of("kind","discard","expectedRevision","3","requestId",java.util.UUID.randomUUID().toString())),true).status());
        assertEquals(404,client.get("/api/v1/plans/"+plan.planId).status());
        assertEquals(changed.body(),client.request("POST",commands,body,true).body(),"Retired replay must not resolve stale handles");
        assertEquals(200,client.get("/api/v1/operations/"+malformed).status());
        assertEquals(200,client.request("POST","/api/v1/operations/"+malformed+"/cancel","{}",true).status());
        assertEquals(204,client.request("POST","/api/v1/session/logout","{}",true).status());
        assertEquals(204,foreign.request("POST","/api/v1/session/logout","{}",true).status());
    }
    @Test void actualBindingPagesRequireLiveRevisionAndCompleteLocationEvidence() throws Exception {
        var plan=socketPlan("binding-maintainer");var client=plan.client;var json=tools.jackson.databind.json.JsonMapper.builder().build();String views="/api/v1/plans/"+plan.planId+"/views/";
        String operation=reserve(plan,"1");assertEquals(200,client.request("POST","/api/v1/operations/"+operation+"/credentials",mockCredentials(),true).status());
        var entities=json.readTree(client.request("POST",views+"entities","{\"revision\":\"2\",\"side\":\"current\",\"offset\":0,\"limit\":100}",true).body());String palette=null;
        for(var item:entities.get("items"))if(item.get("typeId").asString().equals("palette"))palette=item.get("entity").get("handle").asString();assertNotNull(palette);
        var ref=Map.of("kind","existing","handle",palette);String bindings=json.writeValueAsString(Map.of("revision","2","entity",ref,"offset",0,"limit",100));
        var response=client.request("POST",views+"bindings",bindings,true);assertEquals(200,response.status());assertEquals("no-store",response.headers().get("cache-control"));
        var page=json.readTree(response.body());assertEquals(2,page.get("total").asInt());var tag=page.get("items").get(1);assertEquals("[[value:"+palette+":tag]]",tag.get("token").asString());assertEquals(3,tag.get("currentLocations").get("total").asInt());assertEquals("unchanged",tag.get("change").asString());
        String locations=json.writeValueAsString(Map.of("revision","2","entity",ref,"fieldId","tag","side","current","offset",0,"limit",1,"completeDocumentDisclosure",true));
        var withoutDisclosure=new java.util.HashMap<String,Object>(Map.of("revision","2","entity",ref,"fieldId","tag","side","current","offset",0,"limit",1));
        assertEquals(400,client.request("POST",views+"binding-locations",json.writeValueAsString(withoutDisclosure),true).status());
        withoutDisclosure.put("completeDocumentDisclosure",false);assertEquals(400,client.request("POST",views+"binding-locations",json.writeValueAsString(withoutDisclosure),true).status());
        var location=client.request("POST",views+"binding-locations",locations,true);assertEquals(200,location.status());assertEquals(3,json.readTree(location.body()).get("total").asInt());assertEquals("reference",json.readTree(location.body()).get("items").get(0).get("role").asString());
        assertEquals(403,client.request("POST",views+"bindings",bindings,false).status());
        assertEquals(400,client.request("POST",views+"bindings",bindings.replace("\"offset\":0","\"offset\":257"),true).status());
        var foreign=socketLogin("foreign-binding-"+java.util.UUID.randomUUID());
        for(String suffix:java.util.List.of("bindings","binding-locations"))try(var pending=foreign.begin("POST",views+suffix,16_384,true)){long before=System.nanoTime();assertEquals(404,pending.response().status());assertTrue(System.nanoTime()-before<2_000_000_000L,"Foreign binding body was awaited");}
        assertEquals(204,foreign.request("POST","/api/v1/session/logout","{}",true).status());
        String fresh=reserve(plan,"2");var reinspected=client.request("POST","/api/v1/operations/"+fresh+"/credentials",mockCredentials(),true);
        assertEquals(200,reinspected.status(),()->{
            try {String code=json.readTree(reinspected.body()).path("code").asString();return java.util.Set.of("MALFORMED_BODY","BODY_TOO_LARGE","BODY_DEADLINE","CANCELLED").contains(code)?code:"UNRECOGNIZED_REFUSAL";}
            catch(RuntimeException unavailable){return "UNRECOGNIZED_REFUSAL";}
        });
        assertEquals(409,client.request("POST",views+"bindings",bindings,true).status());assertEquals(409,client.request("POST",views+"binding-locations",locations,true).status());
        assertEquals(404,client.request("POST",views+"bindings",bindings.replace("\"revision\":\"2\"","\"revision\":\"3\""),true).status());
        try(var pending=client.begin("POST",views+"binding-locations",16_384,true)) {
            pending.write("{".getBytes(java.nio.charset.StandardCharsets.UTF_8));long deadline=System.nanoTime()+2_000_000_000L;
            while(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.getName().equals("hosted-plan-body")) && System.nanoTime()<deadline)Thread.sleep(5);
            assertTrue(Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.getName().equals("hosted-plan-body")),"Binding reader was not admitted");
            int logout=client.request("POST","/api/v1/session/logout","{}",true).status();assertTrue(logout==204 || logout==503,"Logout must preserve cleanup outcome");assertEquals(401,pending.response().status());
        }
        assertEquals(401,client.request("POST",views+"bindings",bindings,true).status());
        long deadline=System.nanoTime()+3_000_000_000L;
        while(Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.getName().equals("hosted-plan-body")) && System.nanoTime()<deadline)Thread.sleep(5);
        assertFalse(Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.getName().equals("hosted-plan-body")),"Revoked binding reader did not finish");
        var lease=studio.environment.server.plan.PlanHttpTestConfiguration.leases.get("binding-maintainer");var sessions=context.getBean(studio.environment.server.session.HostedSessions.class);
        var quarantine=sessions.cleanupReports().stream().filter(r->r.sessionId().equals(lease.id())).findFirst();
        if(quarantine.isPresent())assertEquals(studio.environment.core.session.SessionLedger.CleanupState.COMPLETE,sessions.retryCleanup(lease.id()).orElseThrow().state());
    }
    @Test void maskedDraftChoicesSurviveEditsAndViewReadersRetainAdmissionUntilDeadlineOrLogout() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();var definition=json.readTree(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../../fixtures/native-v2/definition.json")));
        for(var type:definition.get("logical").get("entityTypes"))if(type.get("id").asString().equals("glyph"))for(var field:type.get("fields"))if(field.get("id").asString().equals("tone")){var value=(tools.jackson.databind.node.ObjectNode)field;value.put("readable",true);value.put("sensitivity","secret");}
        var plan=socketPlan("view-maintainer",json.writeValueAsString(definition));var client=plan.client;String path="/api/v1/plans/"+plan.planId,views=path+"/views/";
        String operation=reserve(plan,"1");var inspected=client.request("POST","/api/v1/operations/"+operation+"/credentials",mockCredentials(),true);
        assertEquals(200,inspected.status(),()->{
            try {
                String code=json.readTree(inspected.body()).path("code").asString();
                return java.util.Set.of("MALFORMED_BODY","BODY_TOO_LARGE","BODY_DEADLINE","CANCELLED").contains(code)?code:"UNRECOGNIZED_REFUSAL";
            } catch(RuntimeException unavailable) { return "UNRECOGNIZED_REFUSAL"; }
        });
        var entities=json.readTree(client.request("POST",views+"entities","{\"revision\":\"2\",\"side\":\"current\",\"offset\":0,\"limit\":100}",true).body());String alpha=null;
        for(var entity:entities.get("items"))if(entity.get("typeId").asString().equals("glyph"))for(var field:entity.get("fields")){if(field.get("fieldId").asString().equals("tone")){assertTrue(field.get("masked").asBoolean());assertTrue(field.get("value").isNull());}if(field.get("fieldId").asString().equals("tag") && field.get("value").asString().equals("alpha"))alpha=entity.get("entity").get("handle").asString();}
        var ref=Map.of("kind","existing","handle",alpha);var keep=Map.of("kind","keep-observed");
        assertEquals(200,client.request("POST",path+"/commands",json.writeValueAsString(Map.of("kind","upsert-entity","expectedRevision","2","requestId",java.util.UUID.randomUUID().toString(),"decision",Map.of("kind","retain","entity",ref,"fields",Map.of("tag",keep,"tone",Map.of("kind","entered","text","Hidden-View-Canary")),"references",Map.of("uses",keep)),"placements",java.util.List.of())),true).status());
        var draft=json.readTree(client.request("POST",views+"draft","{\"revision\":\"3\",\"offset\":0,\"limit\":100}",true).body());
        for(var field:draft.get("items").get(0).get("fields"))if(field.get("fieldId").asString().equals("tone")){assertEquals("entered",field.get("kind").asString());assertTrue(field.get("masked").asBoolean());assertTrue(field.get("value").isNull());}
        assertFalse(draft.toString().contains("Hidden-View-Canary"));
        assertEquals(200,client.request("POST",path+"/commands",json.writeValueAsString(Map.of("kind","bind-field","expectedRevision","3","requestId",java.util.UUID.randomUUID().toString(),"entity",ref,"fieldId","tag","state",keep)),true).status());
        var service=studio.environment.server.plan.PlanHttpTestConfiguration.installed;var lease=studio.environment.server.plan.PlanHttpTestConfiguration.leases.get("view-maintainer");
        try(var admission=service.reserveView(lease,plan.planId)){admission.run(()->{admission.pin("4");var target=admission.snapshot().selected(true);assertTrue(target.graph().entities().stream().anyMatch(e->e.key().identity().equals("alpha") && "Hidden-View-Canary".equals(e.fields().get("tone"))),"Unmentioned masked value was not preserved");return true;});}
        for(String mode:java.util.List.of("raw","formatted","placeholders")){var response=client.request("POST",views+"document",json.writeValueAsString(Map.of("revision","4","side","target","documentId","glyph-sheet","mode",mode,"completeDocumentDisclosure",true)),true);assertEquals(200,response.status());assertEquals(!mode.equals("placeholders"),response.body().contains("Hidden-View-Canary"));}
        studio.environment.server.plan.PlanHttpTestConfiguration.awaitViewScratch(false);
        long started=System.nanoTime();try(var pending=client.begin("POST",path+"/profile-captures",67_108_864,true)){
            pending.timeout(40_000);awaitViewCapacity(client,views,"4");
            try(var fifth=client.begin("POST",path+"/profile-previews",67_108_864,true)){assertEquals(429,fifth.response().status());}
            assertEquals(400,pending.response().status());double seconds=(System.nanoTime()-started)/1_000_000_000.0;assertTrue(seconds>=29 && seconds<34,"Collection deadline changed or renewed");
        }
        assertEquals(200,client.request("POST",views+"documents","{\"revision\":\"4\"}",true).status());
        studio.environment.server.plan.PlanHttpTestConfiguration.awaitViewScratch(false);
        try(var pending=client.begin("POST",path+"/profile-captures",67_108_864,true)){
            awaitViewCapacity(client,views,"4");int logout=client.request("POST","/api/v1/session/logout","{}",true).status();assertTrue(logout==503 || logout==204,"Logout must report its actual cleanup result");assertEquals(401,pending.response().status());
        }
        studio.environment.server.plan.PlanHttpTestConfiguration.awaitViewScratch(false);
        var hostedSessions=context.getBean(studio.environment.server.session.HostedSessions.class);
        var quarantine=hostedSessions.cleanupReports().stream().filter(report->report.sessionId().equals(lease.id())).findFirst();
        if(quarantine.isPresent()){
            assertEquals(studio.environment.core.session.SessionLedger.CleanupState.INCONCLUSIVE,quarantine.get().state());assertEquals(1,quarantine.get().attempts());
            socketLogin("view-maintainer",403);
            assertEquals(studio.environment.core.session.SessionLedger.CleanupState.COMPLETE,hostedSessions.retryCleanup(lease.id()).orElseThrow().state());
        }
        var fresh=socketLogin("view-maintainer");assertEquals(404,fresh.request("POST",views+"documents","{\"revision\":\"4\"}",true).status());assertEquals(204,fresh.request("POST","/api/v1/session/logout","{}",true).status());
    }
    private void awaitViewCapacity(studio.environment.server.plan.PlanHttpSocketClient client,String views,String revision)throws Exception {
        studio.environment.server.plan.PlanHttpTestConfiguration.awaitViewScratch(true);
        long end=System.nanoTime()+2_000_000_000L;
        while(System.nanoTime()<end){int status=client.request("POST",views+"documents","{\"revision\":\""+revision+"\"}",true).status();if(status==429)return;assertEquals(200,status);Thread.sleep(10);}
        fail("View scratch was not held by admitted reader");
    }
    @Test void actualQuietTrickleDisconnectCancelAndMetadataCapacityStayBounded() throws Exception {
        var plan=socketPlan("transport-maintainer");var client=plan.client;var json=tools.jackson.databind.json.JsonMapper.builder().build();
        int connections=studio.environment.server.plan.PlanHttpTestConfiguration.connections.get();
        String quiet=reserve(plan,"1");long start=System.nanoTime();
        try(var pending=client.begin("POST","/api/v1/operations/"+quiet+"/credentials",200,true)) {
            awaitPhase(client,quiet,"running");
            assertEquals(409,client.request("POST","/api/v1/operations/"+quiet+"/credentials",mockCredentials(),true).status());
            assertEquals(400,pending.response().status());
        }
        long elapsed=System.nanoTime()-start;assertTrue(elapsed>=9_000_000_000L && elapsed<13_000_000_000L,"Quiet reader deadline was not enforced");
        awaitPhase(client,quiet,"refused");
        String trickle=reserve(plan,"1");start=System.nanoTime();
        try(var pending=client.begin("POST","/api/v1/operations/"+trickle+"/credentials",200,true)) {
            pending.write("{\"username\":\"u\",\"password\":\"".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var stop=new java.util.concurrent.atomic.AtomicBoolean();
            var writer=new Thread(()->{
                try {while(!stop.get()){pending.write(new byte[]{'x'});Thread.sleep(200);}}
                catch(java.io.IOException disconnected) {stop.set(true);}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            },"mock-trickle-client");writer.start();
            try {assertEquals(400,pending.response().status());}
            finally {stop.set(true);writer.interrupt();writer.join(1000);assertFalse(writer.isAlive());}
        }
        elapsed=System.nanoTime()-start;assertTrue(elapsed>=9_000_000_000L && elapsed<13_000_000_000L,"Trickled bytes renewed the body deadline");
        String disconnected=reserve(plan,"1");
        try(var pending=client.begin("POST","/api/v1/operations/"+disconnected+"/credentials",200,true)) {pending.write("{\"username\":\"u\",\"password\":\"Aborted-Canary".getBytes(java.nio.charset.StandardCharsets.UTF_8));awaitPhase(client,disconnected,"running");}
        awaitPhase(client,disconnected,"refused");
        String cancelled=reserve(plan,"1");
        try(var pending=client.begin("POST","/api/v1/operations/"+cancelled+"/credentials",200,true)) {
            awaitPhase(client,cancelled,"running");
            assertEquals(200,client.request("POST","/api/v1/operations/"+cancelled+"/cancel","{}",true).status());
            assertEquals(409,pending.response().status());
        }
        awaitPhase(client,cancelled,"cancelled");assertEquals(connections,studio.environment.server.plan.PlanHttpTestConfiguration.connections.get());
        var pendingMetadata=new java.util.ArrayList<studio.environment.server.plan.PlanHttpSocketClient.Pending>();
        try {
            for(int i=0;i<4;i++)pendingMetadata.add(client.begin("POST","/api/v1/plans",200,true));
            // All four sockets are admitted independently; wait for their dedicated owned readers.
            long deadline=System.nanoTime()+2_000_000_000L;
            while(Thread.getAllStackTraces().keySet().stream().filter(thread->thread.getName().equals("hosted-plan-body")).count()<4 && System.nanoTime()<deadline)Thread.sleep(10);
            assertEquals(429,client.request("POST","/api/v1/plans","{}",true).status());
        } finally {for(var pending:pendingMetadata)pending.close();}
        long deadline=System.nanoTime()+2_000_000_000L;
        while(Thread.getAllStackTraces().keySet().stream().anyMatch(thread->thread.getName().equals("hosted-plan-body")) && System.nanoTime()<deadline)Thread.sleep(10);
        assertEquals(400,client.request("POST","/api/v1/plans","{}",true).status());
        String expired=reserve(plan,"1");
        try(var pending=client.begin("POST","/api/v1/operations/"+expired+"/credentials",200,true)) {
            awaitPhase(client,expired,"running");
            studio.environment.server.plan.PlanHttpTestConfiguration.clock.advance(1800);
            assertEquals(401,pending.response().status());
            assertEquals(401,client.get("/api/v1/operations/"+expired).status());
        } finally {studio.environment.server.plan.PlanHttpTestConfiguration.clock.reset();}
        assertEquals(connections,studio.environment.server.plan.PlanHttpTestConfiguration.connections.get());
        // Revocation may quarantine a reader that exits after its first cleanup attempt.
        // Observe completion, then use the explicit lifecycle retry; login cannot bypass it.
        deadline=System.nanoTime()+3_000_000_000L;
        while(Thread.getAllStackTraces().keySet().stream().anyMatch(thread->thread.getName().equals("hosted-plan-body")) && System.nanoTime()<deadline)Thread.sleep(10);
        assertFalse(Thread.getAllStackTraces().keySet().stream().anyMatch(thread->thread.getName().equals("hosted-plan-body")),"Expired owned reader did not finish");
        var lease=studio.environment.server.plan.PlanHttpTestConfiguration.leases.get("transport-maintainer");
        var hostedSessions=context.getBean(studio.environment.server.session.HostedSessions.class);
        var quarantine=hostedSessions.cleanupReports().stream().filter(report->report.sessionId().equals(lease.id())).findFirst();
        if(quarantine.isPresent()){
            assertEquals(studio.environment.core.session.SessionLedger.CleanupState.INCONCLUSIVE,quarantine.get().state());assertEquals(1,quarantine.get().attempts());
            socketLogin("transport-maintainer",403);
            assertEquals(studio.environment.core.session.SessionLedger.CleanupState.COMPLETE,hostedSessions.retryCleanup(lease.id()).orElseThrow().state());
        }
        var renewed=socketLogin("transport-maintainer");assertEquals(404,renewed.get("/api/v1/operations/"+expired).status());
        assertEquals(204,renewed.request("POST","/api/v1/session/logout","{}",true).status());
    }
    void awaitPhase(studio.environment.server.plan.PlanHttpSocketClient client,String operation,String phase) throws Exception {
        long deadline=System.nanoTime()+3_000_000_000L;
        while(System.nanoTime()<deadline) {
            var response=client.get("/api/v1/operations/"+operation);assertEquals(200,response.status());
            var tree=tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
            if(tree.get("phase").asString().equals(phase) && (phase.equals("running") || tree.get("cleanup").asString().equals("complete")))return;
            Thread.sleep(10);
        }
        fail("Owned operation did not reach expected bounded state");
    }
    @Test void planPollingFilterDoesNotRenewTheServletOrLedgerIdleDeadline() throws Exception {
        var instant=new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.now());
        var clock=new java.time.Clock(){public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}public java.time.Clock withZone(java.time.ZoneId ignored){return this;}public java.time.Instant instant(){return instant.get();}};
        var sessions=new studio.environment.server.session.HostedSessions(clock,java.util.List.of());
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/v1/plans/current");request.addHeader("Host","localhost");
        sessions.reserveLogin(request);
        var principal=new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(java.util.List.of(),new org.springframework.security.oauth2.core.oidc.OidcIdToken("controlled-filter-principal",instant.get(),instant.get().plusSeconds(3600),Map.of("iss",issuer.issuer(),"sub","polling-owner")));
        sessions.authenticated(request.getSession(),principal);
        var previous=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var security=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();security.setAuthentication(new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(principal,java.util.List.of(),"studio"));
        org.springframework.security.core.context.SecurityContextHolder.setContext(security);
        try {
            var filter=new HostedBoundaryFilter(context.getBean(HostedSettings.class),sessions);
            instant.set(instant.get().plusSeconds(1799));var first=new org.springframework.mock.web.MockHttpServletResponse();filter.doFilter(request,first,new org.springframework.mock.web.MockFilterChain());assertEquals(200,first.getStatus());
            instant.set(instant.get().plusSeconds(1));var expired=new org.springframework.mock.web.MockHttpServletResponse();filter.doFilter(request,expired,new org.springframework.mock.web.MockFilterChain());assertEquals(401,expired.getStatus());
        } finally {org.springframework.security.core.context.SecurityContextHolder.setContext(previous);}
    }
    @Test void actualServletCanCompleteBoundedMetadataBodyAfterGenuineOidcLogin() throws Exception {
        var client=socketLogin("socket-basic-"+java.util.UUID.randomUUID());
        assertEquals(200,client.get("/api/v1/destinations").status());
        assertEquals(400,client.request("POST","/api/v1/plans","{}",true).status());
        assertEquals(403,client.request("POST","/api/v1/plans","{}",false).status());
        assertEquals(204,client.request("POST","/api/v1/session/logout","{}",true).status());
    }
    @Test void realOidcSessionCanSeeOnlySafeInitialPlanRoutes() throws Exception {
        var login=login(false);assertEquals(302,login.callback.getResponse().getStatus());
        mvc.perform(get("/api/v1/destinations").header("Host","localhost").session(login.session))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.destinations[0].id").value("mock-destination"));
        mvc.perform(get("/api/v1/plans/current").header("Host","localhost").session(login.session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/plans/00000000-0000-4000-8000-000000000001/export").header("Host","localhost").session(login.session)).andExpect(status().isForbidden());
    }
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
        authorizationCodeCanaries.add(params.get("code"));
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
