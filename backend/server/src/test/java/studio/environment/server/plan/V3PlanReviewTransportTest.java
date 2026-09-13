package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.plan.V3PlanTransportTest.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.AsyncEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import studio.environment.core.Outcome;
import studio.environment.core.plan.*;
import studio.environment.server.planning.V3ReviewTransferFixture;
import studio.environment.server.session.HostedSessions;

class V3PlanReviewTransportTest {
    private static final class Transfer {
        final V3ReviewTransferFixture f=new V3ReviewTransferFixture();
        final HostedSessions sessions=new HostedSessions(Clock.systemUTC(),List.of());
        final V3PlanTransfers transfers=new V3PlanTransfers();
        final Context context=new Context();final Output output=new Output();
        final MockHttpServletResponse response=response(output);
        V3PlanTransfers.Operation operation;
        void start(String body) {
            var admission=f.service.reserveView(f.lease,f.plan,PlanDefinition.Version.V3);operation=transfers.admitSemantic(f.lease);
            new V3PlanTransport(f.service,sessions).workflowBody(f.lease,f.plan,bodyRequest(context,body),response,operation,admission,V3PlanWorkflowReader.Route.REVIEW);
        }
        String finish()throws Exception {
            assertTrue(context.complete.await(3,TimeUnit.SECONDS));settled(transfers,f.lease);
            return output.bytes.toString(StandardCharsets.UTF_8);
        }
    }
    @Test void originalAsyncAbortDuringFreshLookupPreventsRecordAndReplayWithoutReleasingWorkerCapacity()throws Exception {
        for(String abort:List.of("error","timeout","complete","new-cycle")) {
            var t=new Transfer();t.f.holdLookup=true;t.start(t.f.body());
            try {
                assertTrue(t.f.lookupEntered.await(3,TimeUnit.SECONDS));var event=new AsyncEvent(t.context.value);
                switch(abort){case "error"->t.context.listener.onError(event);case "timeout"->t.context.listener.onTimeout(event);case "complete"->t.context.listener.onComplete(event);default->assertThrows(java.io.IOException.class,()->t.context.listener.onStartAsync(event));}
                assertTrue(t.transfers.awaitingWork(t.f.lease));
                assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->t.f.service.reserveView(t.f.lease,t.f.plan)).code());
                assertEquals(0,t.output.bytes.size());
            } finally {t.f.holdLookup=false;t.f.lookupRelease.countDown();}
            if(abort.equals("new-cycle")) {
                long until=System.nanoTime()+3_000_000_000L;
                while(!t.operation.cancelled() && System.nanoTime()<until)Thread.sleep(5);
                assertTrue(t.operation.cancelled());assertTrue(t.transfers.awaitingWork(t.f.lease));
            } else settled(t.transfers,t.f.lease);
            assertEquals(0,t.output.bytes.size());assertEquals(0,t.output.flushes.get());
            assertEquals(Outcome.UNKNOWN,t.f.reviewOutcome("2"));int lookups=t.f.lookups;
            t.f.service.reviewV3(t.f.lease,t.f.plan,t.f.command);
            assertEquals(lookups+1,t.f.lookups,"aborted command must not leave a successful replay receipt");
            assertEquals(Outcome.PASS,t.f.reviewOutcome("2"));
        }
    }
    @Test void responseLossAfterCommitKeepsExactReceiptAndDoesNotRenewOrRevalidateIt()throws Exception {
        var t=new Transfer();t.output.ready=false;t.start(t.f.body());
        try {
            assertTrue(t.output.checked.await(3,TimeUnit.SECONDS));
            t.context.listener.onError(new AsyncEvent(t.context.value));settled(t.transfers,t.f.lease);
            assertEquals(0,t.output.bytes.size());assertEquals(0,t.output.flushes.get());
            assertEquals(Outcome.PASS,t.f.reviewOutcome("2"));int lookups=t.f.lookups;
            var next=new Context();var bytes=new Output();var response=response(bytes);var record=t.transfers.admitSemantic(t.f.lease);
            var admission=t.f.service.reserveView(t.f.lease,t.f.plan,PlanDefinition.Version.V3);
            new V3PlanTransport(t.f.service,t.sessions).workflowBody(t.f.lease,t.f.plan,bodyRequest(next,t.f.body()),response,record,admission,V3PlanWorkflowReader.Route.REVIEW);
            assertTrue(next.complete.await(3,TimeUnit.SECONDS));settled(t.transfers,t.f.lease);
            assertEquals(200,response.getStatus());assertEquals("{\"planId\":\""+t.f.plan+"\",\"revision\":\"2\"}",bytes.bytes.toString(StandardCharsets.UTF_8));
            assertEquals(lookups,t.f.lookups,"replay must not re-run proof or publication lookup");
            assertEquals(List.of(30_000L,0L),next.timeouts);assertEquals(1,next.calls.get());
        } finally {t.output.ready=true;}
    }
    @Test void oversizedAndAuthorityBearingBodiesRefuseWithoutInstallingReview()throws Exception {
        for(boolean oversized:List.of(false,true)) {
            var t=new Transfer();t.start(oversized?" ".repeat(16385)+t.f.body():t.f.body().replace("\"artifactIntent\"","\"claimedPolicy\":\"PASS\",\"artifactIntent\""));
            assertEquals("{\"code\":\""+(oversized?"BODY_TOO_LARGE":"MALFORMED_BODY")+"\"}",t.finish());
            assertEquals(oversized?413:400,t.response.getStatus());assertEquals("close",t.response.getHeader("Connection"));
            assertEquals(0,t.f.lookups);assertEquals(Outcome.UNKNOWN,t.f.reviewOutcome("2"));
            t.f.service.reviewV3(t.f.lease,t.f.plan,t.f.command);assertEquals(Outcome.PASS,t.f.reviewOutcome("2"));
        }
    }
    @Test void originalLeaseLossDuringOutputSuppressesReceiptBytesAndFutureReplay()throws Exception {
        var t=new Transfer();t.output.ready=false;t.start(t.f.body());
        try {
            assertTrue(t.output.checked.await(3,TimeUnit.SECONDS));t.f.revokeLease();
            assertTrue(t.context.complete.await(3,TimeUnit.SECONDS));settled(t.transfers,t.f.lease);
            assertEquals(0,t.output.bytes.size());assertEquals(0,t.output.flushes.get());
            assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->t.f.service.reviewV3(t.f.lease,t.f.plan,t.f.command)).code());
        } finally {t.output.ready=true;t.f.service.invalidate(t.f.lease);}
    }
}
