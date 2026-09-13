package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.core.oidc.*;
import org.springframework.security.oauth2.core.oidc.user.*;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.HostedSessions;

class IndependentV3HttpReviewTest {
    @Test void inputAcquisitionFailureIsSafe503AndSettlesTheOriginalOperation() throws Exception {
        var fixture=new V3WorkspaceControllerTest();fixture.setup();var original=fixture.request("independent-input-owner");
        original.setContent(V3WorkspaceControllerTest.body(Files.readString(Path.of("../../fixtures/native-v3/definition.json")),"0",UUID.randomUUID().toString()));
        var broken=new HttpServletRequestWrapper(original){@Override public ServletInputStream getInputStream() throws IOException {throw new IOException("INDEPENDENT_PRIVATE_TRANSPORT_CAUSE");}};
        assertDoesNotThrow(()->fixture.controller.saveDefinition(UUID.randomUUID().toString(),broken,original.response));
        original.await();assertEquals(503,original.response.getStatus());
        assertEquals("UNAVAILABLE",V3NativeSnapshotCodec.JSON.readTree(original.response.getContentAsByteArray()).get("code").asString());
        assertFalse(original.response.getContentAsString().contains("INDEPENDENT_PRIVATE_TRANSPORT_CAUSE"));
        assertEquals(0,fixture.runtime.v3Operations().activeCount());
        var lease=(SessionLedger.Lease)original.getAttribute(HostedSessions.REQUEST_LEASE);
        assertTrue(fixture.runtime.v3Store().list(lease.owner(),false).isEmpty());
    }
    @Test void staleCompleteNotificationCannotEraseAConcurrentTerminalRefusal() throws Exception {
        var operations=new V3WorkspaceOperations();var clock=Clock.systemUTC();var sessions=new HostedSessions(clock,List.of(operations));
        var request=new MockHttpServletRequest();assertTrue(sessions.reserveLogin(request));
        var user=new DefaultOidcUser(List.of(),new OidcIdToken("independent-mock-token",clock.instant(),clock.instant().plusSeconds(300),Map.of("iss","https://independent-cleanup.invalid","sub","invented-owner")));
        assertInstanceOf(SessionLedger.Accepted.class,sessions.authenticated(request.getSession(),user));var lease=sessions.current(request).orElseThrow();
        var operation=operations.admit(lease);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pause=new AtomicBoolean(true);
        var context=new V3WorkspaceCompletionTest.Context();
        var completion=new V3WorkspaceCompletion(context,outcome->{
            if(outcome==V3WorkspaceCompletion.Outcome.COMPLETE){
                if(pause.compareAndSet(true,false)){entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("INDEPENDENT_RELEASE_TIMEOUT");}catch(InterruptedException interrupted){throw new AssertionError(interrupted);}}
                operation.complete(sessions);
            } else if(outcome==V3WorkspaceCompletion.Outcome.INCONCLUSIVE)operation.inconclusive(sessions);
        });
        try(var worker=Executors.newSingleThreadExecutor()){
            var task=worker.submit(completion::workerClosed);
            try{
                assertTrue(entered.await(2,TimeUnit.SECONDS));
                assertThrows(IOException.class,()->context.listener.onStartAsync(context.event()));
                assertThrows(IOException.class,()->context.listener.onError(context.event()));
                assertEquals(1,operations.activeCount());assertTrue(sessions.guard(lease,()->true).isEmpty());
            }finally{release.countDown();}
            task.get(2,TimeUnit.SECONDS);
        }
        assertEquals(1,operations.activeCount(),"A stale COMPLETE must not discard the newer terminal cleanup obligation");
        var fresh=new MockHttpServletRequest();assertTrue(sessions.reserveLogin(fresh));assertInstanceOf(SessionLedger.Denied.class,sessions.authenticated(fresh.getSession(),user));
        assertEquals(1,context.attempts);
    }
    private static V3WorkspaceControllerTest.Request login(HostedSessions sessions,Clock clock,String subject){
        var request=new V3WorkspaceControllerTest.Request();request.setAsyncSupported(true);request.setContentType("application/json");assertTrue(sessions.reserveLogin(request));
        var user=new DefaultOidcUser(List.of(),new OidcIdToken("independent-transfer-token",clock.instant(),clock.instant().plusSeconds(300),Map.of("iss","https://independent-transfer.invalid","sub",subject)));
        assertInstanceOf(SessionLedger.Accepted.class,sessions.authenticated(request.getSession(),user));request.setAttribute(HostedSessions.REQUEST_LEASE,sessions.current(request).orElseThrow());return request;
    }
    @Test void revokedOutputAcquisitionReleasesNoSourceAndCommittedCommandReplaysAfterReauthentication() throws Exception {
        var fixture=new V3WorkspaceControllerTest();fixture.setup();var sessions=new HostedSessions(fixture.clock,List.of(fixture.runtime::cleanupV3));
        var controller=new V3NativeWorkspaceController(fixture.runtime,sessions);var request=login(sessions,fixture.clock,"independent-transfer-owner");
        String source=Files.readString(Path.of("../../fixtures/native-v3/definition.json")).replace("Glyph","Independent-Source-Transfer-Canary");assertTrue(source.contains("Independent-Source-Transfer-Canary"));
        String id=UUID.randomUUID().toString();byte[] command=V3WorkspaceControllerTest.body(source,"0",UUID.randomUUID().toString());request.setContent(command);
        var lease=(SessionLedger.Lease)request.getAttribute(HostedSessions.REQUEST_LEASE);var once=new AtomicBoolean();
        var response=new HttpServletResponseWrapper(request.response){@Override public ServletOutputStream getOutputStream() throws IOException {
            if(once.compareAndSet(false,true))sessions.quarantine(lease);return super.getOutputStream();
        }};
        controller.saveDefinition(id,request,response);request.await();assertTrue(once.get());assertEquals(403,request.response.getStatus());
        assertFalse(request.response.getContentAsString().contains("Independent-Source-Transfer-Canary"));
        long deadline=System.nanoTime()+2_000_000_000L;while(fixture.runtime.v3Operations().activeCount()!=0&&System.nanoTime()<deadline)Thread.sleep(5);
        assertEquals(0,fixture.runtime.v3Operations().activeCount());assertEquals(1,fixture.runtime.v3Store().list(lease.owner(),false).size());
        var fresh=login(sessions,fixture.clock,"independent-transfer-owner");fresh.setContent(command);controller.saveDefinition(id,fresh,fresh.response);fresh.await();assertEquals(200,fresh.response.getStatus());
        var replay=V3NativeSnapshotCodec.JSON.readTree(fresh.response.getContentAsByteArray());assertEquals("1",replay.get("workspaceRevision").asString());assertEquals(source,replay.get("source").asString());
        assertEquals(1,fixture.runtime.v3Store().list(lease.owner(),false).size());
    }

}
