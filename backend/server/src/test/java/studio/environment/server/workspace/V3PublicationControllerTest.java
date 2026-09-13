package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.workspace.*;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;
import studio.environment.server.session.HostedSessions;

class V3PublicationControllerTest {
    V3WorkspaceControllerTest fixture;
    V3WorkspaceControllerTest.Request original;
    SessionLedger.Lease lease;
    @BeforeEach void setup()throws Exception {
        fixture=new V3WorkspaceControllerTest();fixture.setup();original=fixture.request("publisher");
        lease=(SessionLedger.Lease)original.getAttribute(HostedSessions.REQUEST_LEASE);
        fixture.runtime=new WorkspaceRuntime(new MockEnvironment().withProperty("studio.workspace.directory",fixture.directory.toString())
                .withProperty("studio.workspace.definition-publishers[0].issuer",lease.owner().issuer())
                .withProperty("studio.workspace.definition-publishers[0].subject",lease.owner().subject()),RuntimeMode.HOSTED);
        fixture.sessions.logout(original);
        fixture.sessions=new HostedSessions(fixture.clock,List.of(fixture.runtime::cleanupV3));
        original=fixture.request("publisher");lease=(SessionLedger.Lease)original.getAttribute(HostedSessions.REQUEST_LEASE);
        fixture.controller=new V3NativeWorkspaceController(fixture.runtime,fixture.sessions);
    }
    V3WorkspaceControllerTest.Request request() {
        var request=new V3WorkspaceControllerTest.Request();request.setAsyncSupported(true);request.setContentType("application/json");
        request.setAttribute(HostedSessions.REQUEST_LEASE,lease);return request;
    }
    @Test void fourSettledOperationsMustNotExhaustRetiredOwnerCleanupBeforeLastClosure() {
        // Use fixture's original cleanup-composed runtime, retained by its session cleanup lambda.
        var registry=new V3WorkspaceOperations();
        var sessions=new HostedSessions(fixture.clock,List.of(registry));
        var ownerRequest=new V3WorkspaceControllerTest.Request();ownerRequest.setAsyncSupported(true);
        assertTrue(sessions.reserveLogin(ownerRequest));
        sessions.authenticated(ownerRequest.getSession(),new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(List.of(),
                new org.springframework.security.oauth2.core.oidc.OidcIdToken("invented-cleanup-token",fixture.clock.instant(),fixture.clock.instant().plusSeconds(300),Map.of("iss","https://publication.invalid","sub","mock"))));
        var owner=sessions.current(ownerRequest).orElseThrow();ownerRequest.setAttribute(HostedSessions.REQUEST_LEASE,owner);var operations=new ArrayList<V3WorkspaceOperations.Operation>();
        for(int i=0;i<4;i++)operations.add(registry.admit(owner));
        sessions.logout(ownerRequest);
        for(int i=0;i<3;i++){operations.get(i).complete(sessions);assertEquals(3-i,registry.activeCount());}
        operations.get(3).complete(sessions);assertEquals(0,registry.activeCount());
        assertTrue(sessions.cleanupReports().isEmpty(),"Settled retirement must leave no pending cleanup report");
    }
    @Test void lastCompletionIsLeaseScopedAndTerminalUncertaintyCannotBeReleasedBySiblingOrStaleCalls() {
        var second=fixture.request("other-owner");var other=(SessionLedger.Lease)second.getAttribute(HostedSessions.REQUEST_LEASE);
        var registry=fixture.runtime.v3Operations();
        var a=registry.admit(lease);var b=registry.admit(lease);var c=registry.admit(other);var d=registry.admit(other);
        fixture.sessions.logout(original);fixture.sessions.logout(second);
        a.complete(fixture.sessions);assertEquals(1,report(lease).attempts());
        b.complete(fixture.sessions);assertTrue(fixture.sessions.cleanupReports().stream().noneMatch(r->r.sessionId().equals(lease.id())));
        assertEquals(SessionLedger.CleanupState.INCONCLUSIVE,report(other).state());assertEquals(1,report(other).attempts());
        d.inconclusive(fixture.sessions);c.complete(fixture.sessions);int attempts=report(other).attempts();
        d.complete(fixture.sessions);a.inconclusive(fixture.sessions);a.complete(fixture.sessions);b.complete(fixture.sessions);
        assertEquals(1,registry.activeCount());assertEquals(attempts,report(other).attempts());
        assertEquals(SessionLedger.CleanupState.INCONCLUSIVE,report(other).state());
        assertTrue(fixture.sessions.cleanupReports().stream().noneMatch(r->r.sessionId().equals(lease.id())));
    }
    private SessionLedger.CleanupReport report(SessionLedger.Lease owner) {
        return fixture.sessions.cleanupReports().stream().filter(r->r.sessionId().equals(owner.id())).findFirst().orElseThrow();
    }
    @AfterEach void close()throws Exception {
        fixture.sessions.logout(original);
        try(var files=Files.list(fixture.directory)){for(var file:files.toList())Files.delete(file);}Files.delete(fixture.directory);
    }
    V3WorkspaceControllerTest.Request publish(V3PublicationHttpFixtures.History history,String body)throws Exception {
        var request=request();request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(history.command().profile())fixture.controller.publishProfile(history.command().objectId(),request,request.response);
        else fixture.controller.publishDefinition(history.command().objectId(),request,request.response);
        request.await();return request;
    }
    @Test void schemaTwoAndAbsentStoreDoNotUpgradeOrPublish()throws Exception {
        var directory=Files.createTempDirectory("es-publication-schema2-");SqliteDraftStore.initialize(directory);
        byte[] before=Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE));
        try {
            for(var environment:List.of(new MockEnvironment(),new MockEnvironment().withProperty("studio.workspace.directory",directory.toString()))) {
                var controller=new V3NativeWorkspaceController(new WorkspaceRuntime(environment,RuntimeMode.HOSTED),fixture.sessions);
                for(boolean profile:List.of(false,true)) {
                    var request=request();request.setContent((profile?V3PublicationRequestReaderTest.PROFILE:V3PublicationRequestReaderTest.definition("")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if(profile)controller.publishProfile(UUID.randomUUID().toString(),request,request.response);else controller.publishDefinition(UUID.randomUUID().toString(),request,request.response);
                    request.await();assertEquals(503,request.response.getStatus());
                }
            }
            assertArrayEquals(before,Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE)));
        }finally{try(var files=Files.list(directory)){for(var file:files.toList())Files.delete(file);}Files.delete(directory);}
    }
    @Test void policiesRemainCompleteAndDuplicateSensitiveAndHistoricalReadyCannotQualify()throws Exception {
        var history=V3PublicationHttpFixtures.history(fixture.directory,lease.owner(),false);
        V3PublicationHttpFixtures.later(fixture.directory,lease.owner(),history);
        var original=JSON(history.body());original.put("expectedRevision","3");original.put("requestId",UUID.randomUUID().toString());
        var missing=original.deepCopy();missing.putArray("exportPolicies");
        var result=publish(history,missing.toString());assertEquals(422,result.response.getStatus());assertEquals("INCOMPLETE_DOCUMENT_POLICY",code(result));
        var duplicate=original.deepCopy();var policies=(tools.jackson.databind.node.ArrayNode)duplicate.get("exportPolicies");policies.add(policies.get(0));
        result=publish(history,duplicate.toString());assertEquals(422,result.response.getStatus());assertEquals("DUPLICATE_DOCUMENT_POLICY",code(result));
        result=publish(history,original.toString());assertEquals(422,result.response.getStatus());assertEquals("DEFINITION_INCOMPLETE",code(result));
        assertEquals("3",fixture.runtime.v3Store().read(lease.owner(),history.revision().objectId(),Optional.empty(),false).workspaceRevision());
    }
    @Test void wrongKindForeignOwnerAndOldVersionRemainSeparated()throws Exception {
        for(boolean profile:List.of(false,true)) {
            var history=V3PublicationHttpFixtures.history(fixture.directory,lease.owner(),profile);
            var request=request();request.setContent((profile?V3PublicationRequestReaderTest.definition(""):V3PublicationRequestReaderTest.PROFILE).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(profile)fixture.controller.publishDefinition(history.revision().objectId(),request,request.response);else fixture.controller.publishProfile(history.revision().objectId(),request,request.response);
            request.await();assertEquals(409,request.response.getStatus());
        }
        var old=fixture.runtime.nativeService().mutate(lease.owner(),new NativeCommand.SaveDefinition(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,Files.readString(Path.of("../../fixtures/native-v2/definition.json"))));
        var request=request();request.setContent(V3PublicationRequestReaderTest.definition("").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        fixture.controller.publishDefinition(old.objectId(),request,request.response);request.await();assertEquals(409,request.response.getStatus());
        var foreign=V3PublicationHttpFixtures.history(fixture.directory,new studio.environment.core.session.Owner(lease.owner().issuer(),"foreign"),false);
        assertEquals(404,publish(foreign,foreign.body()).response.getStatus());
    }
    @Test void replayOutputRevocationCannotDiscloseHistoryAndReauthenticationRecoversExactResult()throws Exception {
        var history=V3PublicationHttpFixtures.history(fixture.directory,lease.owner(),false);
        var request=request();request.setContent(history.body().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=new V3WorkspaceControllerTest.Response(){boolean retired;
            @Override public jakarta.servlet.ServletOutputStream getOutputStream(){if(!retired){retired=true;fixture.sessions.logout(original);}return super.getOutputStream();}};
        fixture.controller.publishDefinition(history.revision().objectId(),request,response);request.await();
        assertEquals(403,response.getStatus());assertFalse(response.getContentAsString().contains("logicalDigest"));assertFalse(response.getContentAsString().contains("source"));
        long deadline=System.nanoTime()+3_000_000_000L;while(fixture.runtime.v3Operations().activeCount()!=0&&System.nanoTime()<deadline)Thread.sleep(5);
        assertEquals(0,fixture.runtime.v3Operations().activeCount());
        original=fixture.request("publisher");lease=(SessionLedger.Lease)original.getAttribute(HostedSessions.REQUEST_LEASE);
        var replay=publish(history,history.body());assertEquals(200,replay.response.getStatus());
        assertEquals("2",V3NativeSnapshotCodec.JSON.readTree(replay.response.getContentAsByteArray()).get("workspaceRevision").asString());
        assertEquals(history.revision().source(),V3NativeSnapshotCodec.JSON.readTree(replay.response.getContentAsByteArray()).get("source").asString());
    }
    private static tools.jackson.databind.node.ObjectNode JSON(String text){return (tools.jackson.databind.node.ObjectNode)V3NativeSnapshotCodec.JSON.readTree(text);}
    private static String code(V3WorkspaceControllerTest.Request request){return V3NativeSnapshotCodec.JSON.readTree(request.response.getContentAsByteArray()).at("/diagnostics/0/code").asString();}
}
