package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.HostedSessions;

class IndependentV3PublicationTest {
    @Test void decodedPolicyOrderReplaysExactlyButChangedPolicyCannotReuseTheRequest()throws Exception {
        var f=new V3PublicationControllerTest();f.setup();
        try {
            var history=V3PublicationHttpFixtures.history(f.fixture.directory,f.lease.owner(),false);
            var original=(studio.environment.core.workspace.NativeCommand.PublishDefinition)history.command();
            assertTrue(original.exportPolicies().size()>1);
            var body=new LinkedHashMap<String,Object>();body.put("exportPolicies",original.exportPolicies().reversed());
            body.put("requestId",original.requestId());body.put("expectedRevision",original.expectedRevision());
            var response=f.publish(history,V3NativeSnapshotCodec.JSON.writeValueAsString(body));
            assertEquals(200,response.response.getStatus());
            var tree=V3NativeSnapshotCodec.JSON.readTree(response.response.getContentAsByteArray());
            assertEquals("2",tree.get("workspaceRevision").asString());
            assertEquals(history.revision().publication().orElseThrow().digest(),tree.at("/publication/digest").asString());
            assertEquals(history.revision().source(),tree.get("source").asString());
            var policies=new ArrayList<>(original.exportPolicies());var first=policies.getFirst();
            policies.set(0,new studio.environment.core.workspace.NativeCommand.Policy(first.bindingId(),first.documentId(),"protected-self-contained"));
            body.put("exportPolicies",policies);
            assertEquals(409,f.publish(history,V3NativeSnapshotCodec.JSON.writeValueAsString(body)).response.getStatus());
            var stored=f.fixture.runtime.v3Store().read(f.lease.owner(),original.objectId(),Optional.empty(),false);
            assertEquals(history.revision(),stored);
            assertEquals(200,f.publish(history,history.body()).response.getStatus());
        }finally{f.close();}
    }

    @Test void finalCleanupNotificationRunsOutsideRegistryLockAndStaleClosuresCannotNotifyAgain()throws Exception {
        var registry=new V3WorkspaceOperations();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var holds=new AtomicBoolean();var visits=new AtomicInteger();
        var sessions=new HostedSessions(Clock.systemUTC(),List.of(registry,lease->{
            visits.incrementAndGet();
            if(registry.activeCount()>0)throw new IllegalStateException("MOCK_OBLIGATION_PENDING");
            if(holds.compareAndSet(false,true)) {
                entered.countDown();
                try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("MOCK_HOLD_TIMEOUT");}
                catch(InterruptedException stopped){Thread.currentThread().interrupt();throw new IllegalStateException("MOCK_HOLD_INTERRUPTED");}
            }
        }));
        var first=request(sessions,"first");var other=request(sessions,"other");
        var owner=(SessionLedger.Lease)first.getAttribute(HostedSessions.REQUEST_LEASE);
        var second=(SessionLedger.Lease)other.getAttribute(HostedSessions.REQUEST_LEASE);
        var operations=new ArrayList<V3WorkspaceOperations.Operation>();
        for(int index=0;index<4;index++)operations.add(registry.admit(owner));
        sessions.logout(first);
        for(int index=0;index<3;index++)operations.get(index).complete(sessions);
        assertEquals(1,visits.get());
        var failure=new AtomicReference<Throwable>();
        var worker=new Thread(()->{try{operations.getLast().complete(sessions);}catch(Throwable error){failure.set(error);}});
        var executor=Executors.newSingleThreadExecutor();V3WorkspaceOperations.Operation independent=null;
        worker.start();
        try {
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            var future=executor.submit(()->registry.admit(second));
            independent=assertDoesNotThrow(()->future.get(1,TimeUnit.SECONDS),"External session cleanup must not hold the registry monitor");
            for(var operation:operations){operation.complete(sessions);operation.inconclusive(sessions);}
            assertEquals(2,visits.get());assertEquals(1,registry.activeCount());
        }finally{release.countDown();worker.join(3000);executor.shutdownNow();}
        assertFalse(worker.isAlive());assertNull(failure.get());
        assertTrue(sessions.cleanupReports().isEmpty());
        assertNotNull(independent);independent.complete(sessions);sessions.logout(other);
        assertEquals(0,registry.activeCount());assertTrue(sessions.cleanupReports().isEmpty());
    }
    private static MockHttpServletRequest request(HostedSessions sessions,String subject) {
        var request=new MockHttpServletRequest();assertTrue(sessions.reserveLogin(request));
        var now=Instant.now();var principal=new DefaultOidcUser(List.of(),new OidcIdToken("mock-independent-token",now,now.plusSeconds(600),
                Map.of("iss","https://independent-publication.invalid","sub",subject)));
        var lease=assertInstanceOf(SessionLedger.Accepted.class,sessions.authenticated(request.getSession(),principal)).lease();
        request.setAttribute(HostedSessions.REQUEST_LEASE,lease);return request;
    }
}
