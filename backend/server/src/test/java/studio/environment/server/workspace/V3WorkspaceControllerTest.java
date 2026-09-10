package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;
import studio.environment.server.session.HostedSessions;

class V3WorkspaceControllerTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"),ZoneOffset.UTC);
    HostedSessions sessions;
    Path directory;
    WorkspaceRuntime runtime;
    V3NativeWorkspaceController controller;
    @BeforeEach void setup()throws Exception {
        directory=Files.createTempDirectory("es-v3-http-mock-");
        SqliteDraftStore.initializeV3(directory);
        runtime=new WorkspaceRuntime(new MockEnvironment().withProperty("studio.workspace.directory",directory.toString()),RuntimeMode.HOSTED);
        sessions=new HostedSessions(clock,List.of(runtime::cleanupV3));
        controller=new V3NativeWorkspaceController(runtime,sessions);
    }
    static class Response extends MockHttpServletResponse {
        @Override public ServletOutputStream getOutputStream() {
            var delegate=super.getOutputStream();
            return new ServletOutputStream() {
                public boolean isReady(){return true;}
                public void setWriteListener(WriteListener listener){}
                public void write(int value)throws IOException{delegate.write(value);}
                public void write(byte[] value,int offset,int length)throws IOException{delegate.write(value,offset,length);}
                public void flush()throws IOException{delegate.flush();}
            };
        }
    }
    static class Request extends MockHttpServletRequest {
        final Response response=new Response();
        final CountDownLatch completed=new CountDownLatch(1);
        @Override public AsyncContext startAsync(){
            setAsyncStarted(true);
            return new MockAsyncContext(this,response){@Override public void complete(){super.complete();completed.countDown();}};
        }
        @Override public ServletInputStream getInputStream(){
            var bytes=new ByteArrayInputStream(getContentAsByteArray());
            return new ServletInputStream(){
                public int read(){return bytes.read();}
                public int read(byte[] target,int offset,int length){return bytes.read(target,offset,length);}
                public boolean isFinished(){return bytes.available()==0;}
                public boolean isReady(){return true;}
                public void setReadListener(ReadListener listener){}
            };
        }
        void await()throws Exception {if(isAsyncStarted())assertTrue(completed.await(10,TimeUnit.SECONDS));}
    }
    Request request(String owner){
        var request=new Request();request.setAsyncSupported(true);request.setContentType("application/json");
        assertTrue(sessions.reserveLogin(request));
        sessions.authenticated(request.getSession(),new DefaultOidcUser(List.of(),new OidcIdToken("invented-v3-http-token",clock.instant(),clock.instant().plusSeconds(300),Map.of("iss","https://v3-mock.invalid","sub",owner))));
        request.setAttribute(HostedSessions.REQUEST_LEASE,sessions.current(request).orElseThrow());return request;
    }
    static byte[] body(String source,String revision,String requestId){return V3NativeSnapshotCodec.JSON.writeValueAsBytes(Map.of("source",source,"expectedRevision",revision,"requestId",requestId,"format","JSON"));}
    @Test void savesActualSchemaThreeSourceAndHistoricalProjection()throws Exception {
        var request=request("mock-owner");String source=Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
        request.setContent(body(source,"0",UUID.randomUUID().toString()));String id=UUID.randomUUID().toString();
        controller.saveDefinition(id,request,request.response);request.await();
        assertEquals(200,request.response.getStatus());
        var result=V3NativeSnapshotCodec.JSON.readTree(request.response.getContentAsByteArray());
        assertEquals(source,result.get("source").asString());assertEquals("3",result.get("schemaVersion").asString());
        assertEquals("incomplete",result.get("projection").get("kind").asString());
        assertFalse(result.has("canPublish"));assertEquals("no-store",request.response.getHeader("Cache-Control"));
        assertEquals(1,new V3NativeSqliteStore(new SqliteDraftStore(directory)).list(sessions.current(request).orElseThrow().owner(),false).size());
    }
    @Test void exactReplayPrecedesLaterRevisionAndHistoricalReadsRemainExact()throws Exception {
        String source=Files.readString(Path.of("../../fixtures/native-v3/definition.json"));String id=UUID.randomUUID().toString();var first=request("replay-owner");
        byte[] command=body(source,"0",UUID.randomUUID().toString());first.setContent(command);controller.saveDefinition(id,first,first.response);first.await();assertEquals(200,first.response.getStatus());
        var next=new Request();next.setAsyncSupported(true);next.setContentType("application/json");next.setSession((MockHttpSession)first.getSession());next.setAttribute(HostedSessions.REQUEST_LEASE,first.getAttribute(HostedSessions.REQUEST_LEASE));
        next.setContent(body(source.replace("Glyph","Glyph-é-𐀀"),"1",UUID.randomUUID().toString()));controller.saveDefinition(id,next,next.response);next.await();assertEquals(200,next.response.getStatus());
        var replay=new Request();replay.setAsyncSupported(true);replay.setContentType("application/json");replay.setAttribute(HostedSessions.REQUEST_LEASE,first.getAttribute(HostedSessions.REQUEST_LEASE));replay.setContent(command);
        controller.saveDefinition(id,replay,replay.response);replay.await();assertArrayEquals(first.response.getContentAsByteArray(),replay.response.getContentAsByteArray());
        var historical=new Request();historical.setAsyncSupported(true);historical.setAttribute(HostedSessions.REQUEST_LEASE,first.getAttribute(HostedSessions.REQUEST_LEASE));
        controller.definitionRevision(id,"1",historical,historical.response);historical.await();assertArrayEquals(first.response.getContentAsByteArray(),historical.response.getContentAsByteArray());
    }
    @Test void definitionCommittedBeforeOutputRevocationReturnsForbiddenAndReplaysExactly()throws Exception {
        var original=request("definition-postcommit-owner");
        var lease=(studio.environment.core.session.SessionLedger.Lease)original.getAttribute(HostedSessions.REQUEST_LEASE);
        String source=Files.readString(Path.of("../../fixtures/native-v3/definition.json")).replace("Glyph","POSTCOMMIT-SOURCE-CANARY");
        String id=UUID.randomUUID().toString();byte[] command=body(source,"0",UUID.randomUUID().toString());original.setContent(command);
        var response=new Response(){
            boolean revoked;
            @Override public ServletOutputStream getOutputStream(){
                if(!revoked){revoked=true;sessions.logout(original);}
                return super.getOutputStream();
            }
        };
        controller.saveDefinition(id,original,response);original.await();
        assertEquals(403,response.getStatus());
        assertFalse(response.getContentAsString().contains("POSTCOMMIT-SOURCE-CANARY"));
        assertEquals("FORBIDDEN",V3NativeSnapshotCodec.JSON.readTree(response.getContentAsByteArray()).get("code").asString());
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(runtime.v3Operations().activeCount()!=0 && System.nanoTime()<deadline)Thread.sleep(10);
        assertEquals(0,runtime.v3Operations().activeCount());
        var committed=runtime.v3Store().read(lease.owner(),id,Optional.empty(),false);
        assertEquals("1",committed.workspaceRevision());assertEquals(source,committed.source());
        var replay=request("definition-postcommit-owner");replay.setContent(command);
        controller.saveDefinition(id,replay,replay.response);replay.await();assertEquals(200,replay.response.getStatus());
        var received=V3NativeSnapshotCodec.JSON.readTree(replay.response.getContentAsByteArray());
        assertEquals("1",received.get("workspaceRevision").asString());assertEquals(source,received.get("source").asString());
        assertEquals(committed,runtime.v3Store().read(lease.owner(),id,Optional.empty(),false));
    }
    @Test void schemaTwoIsNotUpgradedAndMissingConfigurationIsUnavailable()throws Exception {
        var old=Files.createTempDirectory("es-v3-old-schema-mock-");SqliteDraftStore.initialize(old);byte[] before=Files.readAllBytes(old.resolve("studio-workspace.db"));
        for(var environment:List.of(new MockEnvironment(),new MockEnvironment().withProperty("studio.workspace.directory",old.toString()))){
            var disabled=new V3NativeWorkspaceController(new WorkspaceRuntime(environment,RuntimeMode.HOSTED),sessions);var request=request("disabled-"+UUID.randomUUID());
            disabled.definitions(request,request.response);request.await();assertEquals(503,request.response.getStatus());
        }assertArrayEquals(before,Files.readAllBytes(old.resolve("studio-workspace.db")));
    }
    @Test void malformedTrailingUnknownAndOldVersionNeverAppend()throws Exception {
        String source=Files.readString(Path.of("../../fixtures/native-v3/definition.json"));String id=UUID.randomUUID().toString();
        byte[] valid=body(source,"0",UUID.randomUUID().toString());
        for(byte[] invalid:List.of((new String(valid,java.nio.charset.StandardCharsets.UTF_8)+" {}").getBytes(java.nio.charset.StandardCharsets.UTF_8),"{\"unknown\":1}".getBytes(),new byte[]{(byte)0xff},body(Files.readString(Path.of("../../fixtures/native-v2/definition.json")),"0",UUID.randomUUID().toString()))){
            var request=request("malformed-"+UUID.randomUUID());request.setContent(invalid);controller.saveDefinition(id,request,request.response);request.await();assertTrue(Set.of(400,422).contains(request.response.getStatus()));
            assertTrue(new V3NativeSqliteStore(new SqliteDraftStore(directory)).list(((studio.environment.core.session.SessionLedger.Lease)request.getAttribute(HostedSessions.REQUEST_LEASE)).owner(),false).isEmpty());
        }
    }
    @Test void oversizedBodyIsTypedAndDoesNotPersist()throws Exception {
        var request=request("oversized-owner");request.setContent(new byte[DraftRequestReader.MAX_BODY+1]);controller.saveDefinition(UUID.randomUUID().toString(),request,request.response);request.await();assertEquals(413,request.response.getStatus());
    }

    @Test void timeoutAtAsyncRegistrationCannotStartAPayloadMutation()throws Exception {
        var authenticated=request("startup-owner");
        var request=new Request(){@Override public AsyncContext startAsync(){setAsyncStarted(true);return new MockAsyncContext(this,response){AsyncListener listener;@Override public void addListener(AsyncListener value){listener=value;super.addListener(value);}@Override public void setTimeout(long timeout){super.setTimeout(timeout);if(timeout==0){assertNotNull(listener);try{listener.onTimeout(new AsyncEvent(this));}catch(IOException failure){throw new AssertionError();}}}@Override public void complete(){super.complete();completed.countDown();}};}};
        request.setAsyncSupported(true);request.setContentType("application/json");request.setAttribute(HostedSessions.REQUEST_LEASE,authenticated.getAttribute(HostedSessions.REQUEST_LEASE));
        request.setContent(body(Files.readString(Path.of("../../fixtures/native-v3/definition.json")),"0",UUID.randomUUID().toString()));
        controller.saveDefinition(UUID.randomUUID().toString(),request,request.response);request.await();
        long deadline=System.nanoTime()+2_000_000_000L;while(runtime.v3Operations().activeCount()>0&&System.nanoTime()<deadline)Thread.sleep(10);
        assertEquals(0,runtime.v3Operations().activeCount());assertTrue(runtime.v3Store().list(((studio.environment.core.session.SessionLedger.Lease)request.getAttribute(HostedSessions.REQUEST_LEASE)).owner(),false).isEmpty());
    }

    @Test void yamlBytesAreRetainedWithoutSubstitutingJsonSource()throws Exception {
        var tree=V3NativeSnapshotCodec.JSON.readTree(Files.readString(Path.of("../../fixtures/native-v3/definition.json")));var yaml=new StringBuilder();
        tree.properties().forEach(entry->yaml.append(entry.getKey()).append(": ").append(V3NativeSnapshotCodec.JSON.writeValueAsString(entry.getValue())).append('\n'));
        var request=request("yaml-owner");request.setContent(V3NativeSnapshotCodec.JSON.writeValueAsBytes(Map.of("source",yaml.toString(),"expectedRevision","0","requestId",UUID.randomUUID().toString(),"format","YAML")));
        controller.saveDefinition(UUID.randomUUID().toString(),request,request.response);request.await();assertEquals(200,request.response.getStatus());
        var result=V3NativeSnapshotCodec.JSON.readTree(request.response.getContentAsByteArray());assertEquals(yaml.toString(),result.get("source").asString());assertEquals("YAML",result.get("format").asString());
    }
    @Test void historicalReadyIsDataOnlyAndWrongVersionOwnedUuidConflicts()throws Exception {
        var request=request("history-owner");var owner=((studio.environment.core.session.SessionLedger.Lease)request.getAttribute(HostedSessions.REQUEST_LEASE)).owner();
        String source=Files.readString(Path.of("../../fixtures/native-v3/definition.json"));String id=UUID.randomUUID().toString();
        var command=new studio.environment.core.workspace.NativeCommand.SaveDefinition(id,"0",UUID.randomUUID().toString(),studio.environment.core.workspace.DraftCommand.Format.JSON,source);
        var checked=new V3NativeWorkspaceCompiler().definition(command).checked();
        var revision=new studio.environment.core.workspace.V3NativeRevision(id,"1",command.format(),source,WorkspaceDigests.sha256(StrictUtf8.encode(source)),"native-compiler-v3","3",new studio.environment.core.workspace.V3NativeRevision.Definition(checked,List.of()),Optional.empty());
        new V3NativeSqliteStore(new SqliteDraftStore(directory)).append(owner,command,revision);
        controller.definition(id,request,request.response);request.await();assertEquals(200,request.response.getStatus());
        var view=V3NativeSnapshotCodec.JSON.readTree(request.response.getContentAsByteArray());assertEquals("historical-ready",view.get("projection").get("kind").asString());assertFalse(view.has("canPublish"));assertFalse(view.has("publication"));
        String oldId=UUID.randomUUID().toString();runtime.nativeService().mutate(owner,new studio.environment.core.workspace.NativeCommand.SaveDefinition(oldId,"0",UUID.randomUUID().toString(),studio.environment.core.workspace.DraftCommand.Format.JSON,Files.readString(Path.of("../../fixtures/native-v2/definition.json"))));
        var collision=new Request();collision.setAsyncSupported(true);collision.setContentType("application/json");collision.setAttribute(HostedSessions.REQUEST_LEASE,request.getAttribute(HostedSessions.REQUEST_LEASE));collision.setContent(body(source,"0",UUID.randomUUID().toString()));
        controller.saveDefinition(oldId,collision,collision.response);collision.await();assertEquals(409,collision.response.getStatus());
    }

    @Test void completionRefusalRetainsItsOperationAndOriginalSessionQuarantine()throws Exception {
        var authenticated=request("uncertain-owner");
        var request=new Request(){@Override public AsyncContext startAsync(){setAsyncStarted(true);return new MockAsyncContext(this,response){@Override public void complete(){completed.countDown();throw new IllegalStateException("MOCK_PRIVATE_COMPLETION");}};}};
        request.setAsyncSupported(true);request.setAttribute(HostedSessions.REQUEST_LEASE,authenticated.getAttribute(HostedSessions.REQUEST_LEASE));
        controller.definitions(request,request.response);request.await();
        var lease=(studio.environment.core.session.SessionLedger.Lease)request.getAttribute(HostedSessions.REQUEST_LEASE);
        long deadline=System.nanoTime()+2_000_000_000L;while(sessions.guard(lease,()->true).isPresent()&&System.nanoTime()<deadline)Thread.sleep(10);
        assertTrue(sessions.guard(lease,()->true).isEmpty());assertEquals(1,runtime.v3Operations().activeCount());
        assertTrue(sessions.cleanupReports().stream().anyMatch(report->report.state()==studio.environment.core.session.SessionLedger.CleanupState.INCONCLUSIVE));
        assertFalse(request.response.getContentAsString().contains("MOCK_PRIVATE_COMPLETION"));
    }

}
