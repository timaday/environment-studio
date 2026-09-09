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
class V3WorkspaceBoundaryTest {
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
    static Path workspace(){try{var result=Files.createTempDirectory("es-v3-real-http-mock-");SqliteDraftStore.initializeV3(result);return result;}catch(Exception failure){throw new AssertionError("MOCK_SETUP_REFUSED");}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("studio.security.issuer",issuer::issuer);registry.add("studio.workspace.directory",workspace::toString);}
    @AfterAll static void close(){issuer.close();}
    PlanHttpSocketClient login(String subject)throws Exception {
        issuer.subject=subject;issuer.mode=MockIssuer.TokenMode.VALID;
        var client=new PlanHttpSocketClient(port);var start=client.get("/oauth2/authorization/studio");assertEquals(302,start.status());
        var provider=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(start.headers().get("location"))).build(),HttpResponse.BodyHandlers.discarding());assertEquals(302,provider.statusCode());
        var callback=URI.create(provider.headers().firstValue("location").orElseThrow());assertEquals(302,client.get(callback.getRawPath()+"?"+callback.getRawQuery()).status());
        var session=client.get("/api/v1/session");assertEquals(200,session.status());var body=JSON.readTree(session.body());client.csrf(body.get("csrfHeaderName").asString(),body.get("csrfToken").asString());return client;
    }
    @Test void actualOidcHttpPersistsOnlyDraftAndReplaysExactHistory(CapturedOutput output)throws Exception {
        var client=login("v3-http-owner-"+UUID.randomUUID());String id=UUID.randomUUID().toString(),path="/api/v3/definitions/"+id;
        String source=Files.readString(Path.of("../../fixtures/native-v3/definition.json")).replace("Glyph", "V3-Private-Source-Canary");
        assertTrue(source.contains("V3-Private-Source-Canary"));
        String body=JSON.writeValueAsString(Map.of("expectedRevision","0","requestId",UUID.randomUUID().toString(),"format","JSON","source",source));
        assertEquals(403,client.request("PUT",path,body,false).status());
        var saved=client.request("PUT",path,body,true);assertEquals(200,saved.status());assertEquals(source,JSON.readTree(saved.body()).get("source").asString());
        assertEquals("incomplete",JSON.readTree(saved.body()).get("projection").get("kind").asString());
        assertEquals(saved.body(),client.request("PUT",path,body,true).body());
        var second=client.request("PUT",path,JSON.writeValueAsString(Map.of("expectedRevision","1","requestId",UUID.randomUUID().toString(),"format","JSON","source",source)),true);assertEquals(200,second.status());
        assertEquals(saved.body(),client.get(path+"/revisions/1").body());assertEquals("2",JSON.readTree(client.get(path).body()).get("workspaceRevision").asString());
        assertFalse(JSON.readTree(client.get("/api/v3/definitions").body()).has("canPublish"));
        assertEquals(403,client.request("POST",path+"/publish","{}",true).status());
        assertEquals(403,client.request("PUT","/api/v3/profiles/"+UUID.randomUUID(),body,true).status());
        var other=login("v3-other-"+UUID.randomUUID());assertEquals(404,other.get(path).status());
        assertEquals(204,client.request("POST","/api/v1/session/logout","",true).status());assertEquals(401,client.get(path).status());
        assertEquals(204,other.request("POST","/api/v1/session/logout","",true).status());
        assertFalse(output.getAll().contains("V3-Private-Source-Canary"));assertFalse(output.getAll().contains("mock-platform-secret"));
        for(String token:issuer.issuedTokens)assertFalse(output.getAll().contains(token));
    }
    @Test void stoppedBodyIsRevokedWithoutReadingMorePayloadOrPersisting()throws Exception {
        var client=login("v3-stalled-owner-"+UUID.randomUUID());String path="/api/v3/definitions/"+UUID.randomUUID();
        try(var pending=client.begin("PUT",path,1000,true)){
            pending.write("{\"source\":\"".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long admitted=System.nanoTime()+3_000_000_000L;
            while(studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime)==0&&System.nanoTime()<admitted)Thread.sleep(10);
            assertEquals(1,studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime));
            var logout=client.request("POST","/api/v1/session/logout","",true);assertTrue(logout.status()==204||logout.status()==503);
            var refused=pending.response();assertEquals(403,refused.status());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"disconnect","logout","deadline"})
    void actualUnreadOutputRetainsCapacityUntilItsOriginalWorkerStops(String trigger)throws Exception {
        String owner="v3-output-"+UUID.randomUUID();var client=login(owner);String id=UUID.randomUUID().toString();
        String source="\t".repeat(600000)+Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
        studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.seed(workspace,new studio.environment.core.session.Owner(issuer.issuer(),owner),id,source);
        try(var pending=client.begin("GET","/api/v3/definitions/"+id,0,true,1024)){
            assertEquals(200,pending.headersOnly().status());
            Thread.sleep(150);
            assertTrue(studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime)>0,"Actual unread response must still own its slot");
            long started=System.nanoTime();
            if(trigger.equals("disconnect"))pending.close();
            if(trigger.equals("logout")){int status=client.request("POST","/api/v1/session/logout","",true).status();assertTrue(status==204||status==503);}
            long deadline=System.nanoTime()+(trigger.equals("deadline")?34_000_000_000L:5_000_000_000L);
            while(studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime)!=0&&System.nanoTime()<deadline)Thread.sleep(20);
            assertEquals(0,studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime));
            if(trigger.equals("deadline"))assertTrue(System.nanoTime()-started>=25_000_000_000L);
            if(!trigger.equals("logout")){assertEquals(200,client.get("/api/v3/definitions").status());assertEquals(204,client.request("POST","/api/v1/session/logout","",true).status());}
        }
    }

    @Test void fourActualBlockedTransfersRefuseFifthWithoutQueueAndRecover()throws Exception {
        String owner="v3-four-"+UUID.randomUUID();var client=login(owner);String id=UUID.randomUUID().toString();
        studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.seed(workspace,new studio.environment.core.session.Owner(issuer.issuer(),owner),id,"\t".repeat(600000)+Files.readString(Path.of("../../fixtures/native-v3/definition.json")));
        var pending=new ArrayList<PlanHttpSocketClient.Pending>();
        try{
            for(int i=0;i<4;i++){var request=client.begin("GET","/api/v3/definitions/"+id,0,true,1024);pending.add(request);assertEquals(200,request.headersOnly().status());}
            assertEquals(4,studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime));
            assertEquals(429,client.get("/api/v3/definitions").status());
        }finally{for(var request:pending)request.close();}
        long deadline=System.nanoTime()+5_000_000_000L;while(studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime)>0&&System.nanoTime()<deadline)Thread.sleep(20);
        assertEquals(0,studio.environment.server.workspace.V3WorkspaceLiveAuthorityTest.activeCount(runtime));assertEquals(200,client.get("/api/v3/definitions").status());assertEquals(204,client.request("POST","/api/v1/session/logout","",true).status());
    }
    @Test void newRoutesStillRequireApprovedHostOriginAndAuthentication()throws Exception {
        assertEquals(401,new PlanHttpSocketClient(port).get("/api/v3/definitions").status());
        var foreign=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v3/definitions")).build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,foreign.statusCode());assertTrue(foreign.body().contains("REQUEST_ORIGIN_DENIED"));
        try(var socket=new java.net.Socket(java.net.InetAddress.getLoopbackAddress(),port)){
            socket.setSoTimeout(3000);socket.getOutputStream().write("GET /api/v3/definitions HTTP/1.1\r\nHost: localhost\r\nOrigin: https://foreign.invalid\r\nConnection: close\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            String response=new String(socket.getInputStream().readNBytes(4096),java.nio.charset.StandardCharsets.US_ASCII);assertTrue(response.startsWith("HTTP/1.1 403"));assertTrue(response.contains("REQUEST_ORIGIN_DENIED"));
        }
    }

}
