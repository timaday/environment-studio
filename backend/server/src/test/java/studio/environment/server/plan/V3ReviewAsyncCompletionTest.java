package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.server.planning.V3ReviewTransferFixture;

class V3ReviewAsyncCompletionTest {
    @Test void synchronousRegistrationAbortSeesTheOriginalGate() {
        var f=new V3ReviewTransferFixture();var context=new OwnedAsyncCompletionTest.Context();
        context.registered=()->assertDoesNotThrow(()->context.listener.onComplete(context.event()));
        try(var view=f.service.reserveView(f.lease,f.plan,PlanDefinition.Version.V3)) {
            var completion=new OwnedAsyncCompletion(context.context,ignored->{},Optional.of(view));
            assertThrows(PlanRefusal.class,completion::checkActive);
            assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,()->view.run(()->view.reviewV3(f.command))).code());
        }
        assertEquals(studio.environment.core.Outcome.UNKNOWN,f.reviewOutcome("2"));
    }
    @Test void abortSignalDoesNotWaitForTheOriginalApplicationGuard() throws Exception {
        var f=new V3ReviewTransferFixture();var context=new OwnedAsyncCompletionTest.Context();
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(var view=f.service.reserveView(f.lease,f.plan,PlanDefinition.Version.V3)) {
            new OwnedAsyncCompletion(context.context,ignored->{},Optional.of(view));
            var guard=pool.submit(()->f.holdLeaseGuard(entered,release));assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));
            try {assertTrue(pool.submit(()->{context.listener.onError(context.event());return true;}).get(1,java.util.concurrent.TimeUnit.SECONDS));}
            finally {release.countDown();guard.get(3,java.util.concurrent.TimeUnit.SECONDS);}
            assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,()->view.run(()->view.reviewV3(f.command))).code());
        } finally {release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS));}
    }
    @Test void everyOriginalAbortSignalsTheReviewBeforeItsWorkerCanCommit() throws Exception {
        for(String event:List.of("error","timeout","complete","new-cycle")) {
            var f=new V3ReviewTransferFixture();var context=new OwnedAsyncCompletionTest.Context();
            try(var view=f.service.reserveView(f.lease,f.plan,PlanDefinition.Version.V3)) {
                new OwnedAsyncCompletion(context.context,ignored->{},Optional.of(view));
                switch(event){case "error"->context.listener.onError(context.event());case "timeout"->context.listener.onTimeout(context.event());case "complete"->context.listener.onComplete(context.event());default->assertThrows(java.io.IOException.class,()->context.listener.onStartAsync(context.event()));}
                assertTrue(view.live(),"callback cannot close worker resources");
                assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,()->view.run(()->view.reviewV3(f.command)),event).code());
            }
            assertEquals(studio.environment.core.Outcome.UNKNOWN,f.reviewOutcome("2"));
        }
    }
    @Test void normalCompletionAndLateAbortPreserveTheCommittedReceipt()throws Exception {
        var f=new V3ReviewTransferFixture();var context=new OwnedAsyncCompletionTest.Context();HostedPlanService.Ack receipt;
        try(var view=f.service.reserveView(f.lease,f.plan,PlanDefinition.Version.V3)) {
            var completion=new OwnedAsyncCompletion(context.context,ignored->{},Optional.of(view));
            receipt=view.run(()->{var ack=view.reviewV3(f.command);assertDoesNotThrow(()->context.listener.onError(context.event()));view.verifyReview();return ack;});
            completion.workerClosed();context.listener.onComplete(context.event());assertEquals(1,context.calls.get());
        }
        assertEquals(receipt,f.service.reviewV3(f.lease,f.plan,f.command));assertEquals(studio.environment.core.Outcome.PASS,f.reviewOutcome("2"));
    }
}
