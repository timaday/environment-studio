package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.oidc.*;
import org.springframework.security.oauth2.core.oidc.user.*;
import studio.environment.core.session.*;
import studio.environment.core.workspace.*;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;
import studio.environment.server.session.HostedSessions;

public class V3WorkspaceLiveAuthorityTest {
    public static void seed(Path directory,Owner owner,String id,String source){
        new V3NativeWorkspace(new V3NativeSqliteStore(new SqliteDraftStore(directory)),new V3NativeWorkspaceCompiler()).saveDefinition(owner,new NativeCommand.SaveDefinition(id,"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,source));
    }
    public static int activeCount(WorkspaceRuntime runtime){return runtime.v3Operations().activeCount();}
    @Test void finalCommitUsesOriginalLeaseAndRollsBackRevisionAndReplay()throws Exception {
        var directory=Files.createTempDirectory("es-v3-commit-mock-");SqliteDraftStore.initializeV3(directory);
        var clock=Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"),ZoneOffset.UTC);var sessions=new HostedSessions(clock,List.of());
        var request=new MockHttpServletRequest();assertTrue(sessions.reserveLogin(request));sessions.authenticated(request.getSession(),new DefaultOidcUser(List.of(),new OidcIdToken("mock-commit-token",clock.instant(),clock.instant().plusSeconds(300),Map.of("iss","https://v3-commit.invalid","sub","mock-owner"))));
        var lease=sessions.current(request).orElseThrow();request.setAttribute(HostedSessions.REQUEST_LEASE,lease);
        var runtime=new WorkspaceRuntime(new MockEnvironment().withProperty("studio.workspace.directory",directory.toString()),RuntimeMode.HOSTED);
        var reached=new CountDownLatch(1);var release=new CountDownLatch(1);
        WorkspaceCommit commit=connection->{reached.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new java.sql.SQLException("MOCK_TIMEOUT");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new java.sql.SQLException("MOCK_INTERRUPTED");}WorkspaceCommit.authenticated(sessions,lease).commit(connection);};
        var command=new NativeCommand.SaveDefinition(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,Files.readString(Path.of("../../fixtures/native-v3/definition.json")));
        try(var executor=Executors.newSingleThreadExecutor()){
            var task=executor.submit(()->assertThrows(WorkspaceRefusal.class,()->runtime.v3Service(commit).saveDefinition(lease.owner(),command)).code());
            assertTrue(reached.await(3,TimeUnit.SECONDS));try{sessions.logout(request);}finally{release.countDown();}
            assertEquals(WorkspaceRefusal.Code.FORBIDDEN,task.get(5,TimeUnit.SECONDS));
        }
        var store=new V3NativeSqliteStore(new SqliteDraftStore(directory));assertTrue(store.list(lease.owner(),false).isEmpty());assertTrue(store.replay(lease.owner(),command).isEmpty());
    }
    @Test void fourOperationsIncludeUnsettledCleanupAndNoCallbackCanReleaseTwice(){
        var operations=new V3WorkspaceOperations();var lease=new SessionLedger.Lease("mock-session",new Owner("https://mock.invalid","owner"),Instant.MAX);
        var admitted=new ArrayList<V3WorkspaceOperations.Operation>();for(int i=0;i<4;i++)admitted.add(operations.admit(lease));
        assertEquals(WorkspaceRefusal.Code.CAPACITY,assertThrows(WorkspaceRefusal.class,()->operations.admit(lease)).code());assertThrows(IllegalStateException.class,()->operations.invalidate(lease));
        var sessions=new HostedSessions(Clock.systemUTC(),List.of());admitted.getFirst().complete(sessions);admitted.getFirst().complete(sessions);assertEquals(3,operations.activeCount());
        operations.admit(lease);assertEquals(4,operations.activeCount());
    }
    @Test void ordinaryRetirementRetainsOriginalOwnerUntilItsActualWorkerObligationIsSettled(){
        var operations=new V3WorkspaceOperations();var clock=Clock.systemUTC();var sessions=new HostedSessions(clock,List.of(operations));var request=new MockHttpServletRequest();assertTrue(sessions.reserveLogin(request));
        var user=new DefaultOidcUser(List.of(),new OidcIdToken("mock-quarantine-token",clock.instant(),clock.instant().plusSeconds(300),Map.of("iss","https://v3-cleanup.invalid","sub","owner")));
        sessions.authenticated(request.getSession(),user);var lease=sessions.current(request).orElseThrow();var operation=operations.admit(lease);
        sessions.quarantine(lease);assertTrue(sessions.guard(lease,()->true).isEmpty());assertEquals(1,operations.activeCount());
        assertTrue(sessions.cleanupReports().stream().anyMatch(report->report.state()==SessionLedger.CleanupState.INCONCLUSIVE));
        var other=new MockHttpServletRequest();assertTrue(sessions.reserveLogin(other));assertInstanceOf(SessionLedger.Denied.class,sessions.authenticated(other.getSession(),user));
        operation.complete(sessions);assertEquals(0,operations.activeCount());
        var fresh=new MockHttpServletRequest();assertTrue(sessions.reserveLogin(fresh));assertInstanceOf(SessionLedger.Accepted.class,sessions.authenticated(fresh.getSession(),user));
    }

}
