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
class V3PublicationBoundaryTest {
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
    static Path workspace(){try{var result=Files.createTempDirectory("es-v3-publication-http-mock-");SqliteDraftStore.initializeV3(result);return result;}catch(Exception failure){throw new AssertionError("MOCK_SETUP_REFUSED");}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("studio.security.issuer",issuer::issuer);registry.add("studio.workspace.directory",workspace::toString);registry.add("studio.workspace.definition-publishers[0].issuer",issuer::issuer);registry.add("studio.workspace.definition-publishers[0].subject",()->"publication-maintainer");}
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
    @Test void actualIncompleteDefinitionReachesPublicationApplicationWithoutChangingHistory(CapturedOutput output) throws Exception {
        var client=login("publication-maintainer");
        try {
            String id=UUID.randomUUID().toString(), path="/api/v3/definitions/"+id;
            String source=Files.readString(Path.of("../../fixtures/native-v3/definition.json")).replace("Glyph","INVENTED-PUBLICATION-CANARY");assertTrue(source.contains("INVENTED-PUBLICATION-CANARY"));
            String draft=JSON.writeValueAsString(Map.of("expectedRevision","0","requestId",UUID.randomUUID().toString(),"format","JSON","source",source));
            var saved=client.request("PUT",path,draft,true);assertEquals(200,saved.status());
            var model=JSON.readTree(saved.body()).at("/projection/model");
            var policies=new ArrayList<Map<String,String>>();
            for(var binding:model.get("bindings"))for(var doc:binding.get("documents"))policies.add(Map.of("bindingId",binding.get("id").asString(),"documentId",doc.get("id").asString(),"content","deny"));
            String command=JSON.writeValueAsString(Map.of("expectedRevision","1","requestId",UUID.randomUUID().toString(),"exportPolicies",policies));
            var refused=client.request("POST",path+"/publish",command,true);
            assertEquals(422,refused.status());
            assertEquals("DEFINITION_INCOMPLETE",JSON.readTree(refused.body()).at("/diagnostics/0/code").asString());
            assertEquals(saved.body(),client.get(path).body());
            assertEquals(422,client.request("POST",path+"/publish",command,true).status());
            assertEquals(404,client.get(path+"/revisions/2").status());
            assertFalse(output.getAll().contains("INVENTED-PUBLICATION-CANARY"));assertFalse(output.getAll().contains("mock-platform-secret"));
            for(String token:issuer.issuedTokens)assertFalse(output.getAll().contains(token));
        } finally {assertEquals(204,client.request("POST","/api/v1/session/logout","",true).status());}
    }
    @Test void exactHistoricalReplayUsesActualRuntimeAfterLaterYamlDraftWithoutNewAuthority() throws Exception {
        var client=login("publication-maintainer");
        var owner=new studio.environment.core.session.Owner(issuer.issuer(),"publication-maintainer");
        try {
            for(boolean profile:List.of(false,true)) {
                var history=studio.environment.server.workspace.V3PublicationHttpFixtures.history(workspace,owner,profile);
                String path="/api/v3/"+(profile?"profiles/":"definitions/")+history.revision().objectId();
                var before=client.get(path+"/revisions/2");assertEquals(200,before.status());
                studio.environment.server.workspace.V3PublicationHttpFixtures.later(workspace,owner,history);
                var current=client.get(path);assertEquals("3",JSON.readTree(current.body()).get("workspaceRevision").asString());
                var replay=client.request("POST",path+"/publish",history.body(),true);
                assertEquals(200,replay.status());assertEquals(before.body(),replay.body());
                assertEquals("no-store",replay.headers().get("cache-control"));
                assertEquals(current.body(),client.get(path).body());
                assertEquals(409,client.request("POST",path+"/publish",history.body().replace("\"1\"","\"3\""),true).status());
                var next=JSON.readTree(history.body()).deepCopy();((tools.jackson.databind.node.ObjectNode)next).put("expectedRevision","3").put("requestId",UUID.randomUUID().toString());
                var refused=client.request("POST",path+"/publish",JSON.writeValueAsString(next),true);
                assertEquals(422,refused.status());assertEquals("DEFINITION_INCOMPLETE",JSON.readTree(refused.body()).at("/diagnostics/0/code").asString());
                assertEquals(404,client.get(path+"/revisions/4").status());
            }
        } finally {assertEquals(204,client.request("POST","/api/v1/session/logout","",true).status());}
    }
    @Test void definitionMaintainerRequiredEvenForReplayButProfileOwnerNeedsNoMaintainer() throws Exception {
        String subject="publication-ordinary";var client=login(subject);
        var owner=new studio.environment.core.session.Owner(issuer.issuer(),subject);
        try {
            for(boolean profile:List.of(false,true)) {
                var history=studio.environment.server.workspace.V3PublicationHttpFixtures.history(workspace,owner,profile);
                String path="/api/v3/"+(profile?"profiles/":"definitions/")+history.revision().objectId()+"/publish";
                assertEquals(403,client.request("POST",path,history.body(),false).status());
                assertEquals(profile?200:403,client.request("POST",path,history.body(),true).status());
                var foreign=login("publication-foreign");
                try {assertEquals(profile?404:403,foreign.request("POST",path,history.body(),true).status());}
                finally {assertEquals(204,foreign.request("POST","/api/v1/session/logout","",true).status());}
            }
        } finally {assertEquals(204,client.request("POST","/api/v1/session/logout","",true).status());}
    }
    @Test void publicationPartialBodyLogoutAndSharedCapacityRetainOriginalOperation() throws Exception {
        String subject="publication-capacity";var client=login(subject);
        var held=new ArrayList<PlanHttpSocketClient.Pending>();
        try {
            for(String path:List.of("definitions/","profiles/","definitions/","profiles/")) {
                var pending=client.begin("POST","/api/v3/"+path+UUID.randomUUID()+"/publish",1000,true);held.add(pending);
                pending.write("{\"expectedRevision\":\"".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            awaitCount(4);
            assertEquals(429,client.get("/api/v3/definitions").status());
            assertEquals(429,client.get("/api/v3/profiles").status());
            int logout=client.request("POST","/api/v1/session/logout","",true).status();assertTrue(logout==204||logout==503);
            for(var pending:held)assertEquals(403,pending.response().status());
        } finally {for(var pending:held)pending.close();}
        awaitCount(0);var again=login(subject);assertEquals(200,again.get("/api/v3/profiles").status());
        assertEquals(204,again.request("POST","/api/v1/session/logout","",true).status());
    }
    @Test void unlistedMethodsAuthenticationHostOriginAndMalformedCommandsStayClosed() throws Exception {
        String path="/api/v3/profiles/"+UUID.randomUUID()+"/publish";
        assertEquals(401,new PlanHttpSocketClient(port).request("POST",path,"{}",true).status());
        var client=login("publication-malformed");
        try {
            assertEquals(400,client.request("POST",path,"{}",true).status());
            assertEquals(403,client.request("GET",path,"",true).status());
            assertEquals(403,client.request("PUT",path,"{}",true).status());
            assertEquals(403,client.request("POST",path+"/extra","{}",true).status());
            var foreign=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(403,foreign.statusCode());assertTrue(foreign.body().contains("REQUEST_ORIGIN_DENIED"));
            try(var socket=new java.net.Socket(java.net.InetAddress.getLoopbackAddress(),port)) {
                socket.setSoTimeout(3000);socket.getOutputStream().write(("POST "+path+" HTTP/1.1\r\nHost: localhost\r\nOrigin: https://foreign.invalid\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                String response=new String(socket.getInputStream().readNBytes(4096),java.nio.charset.StandardCharsets.US_ASCII);
                assertTrue(response.startsWith("HTTP/1.1 403"));assertTrue(response.contains("REQUEST_ORIGIN_DENIED"));
            }
        } finally {assertEquals(204,client.request("POST","/api/v1/session/logout","",true).status());}
    }
    private void awaitCount(int expected)throws Exception {
        long deadline=System.nanoTime()+5_000_000_000L;
        while(studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime)!=expected&&System.nanoTime()<deadline)Thread.sleep(10);
        assertEquals(expected,studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime));
    }
}
