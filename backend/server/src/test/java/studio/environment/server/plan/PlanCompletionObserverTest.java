package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanRefusal;

class PlanCompletionObserverTest {
    @Test void reentrantUnsupportedCycleDefersObserverUntilOriginalAttemptReturns() {
        var context=new OwnedAsyncCompletionTest.Context();var inside=new java.util.concurrent.atomic.AtomicBoolean();
        var observedInside=new ArrayList<Boolean>();var outcomes=new ArrayList<OwnedAsyncCompletion.Outcome>();
        var owned=new OwnedAsyncCompletion(context.context,result->{observedInside.add(inside.get());outcomes.add(result);});
        context.completion=()->{
            inside.set(true);
            try {assertEquals("ASYNC_CYCLE_UNSUPPORTED",assertThrows(java.io.IOException.class,()->context.listener.onStartAsync(context.event())).getMessage());}
            finally {inside.set(false);}
        };
        owned.workerClosed();
        assertEquals(List.of(false),observedInside,"observer must run after outer context attempt released its lock");
        assertEquals(List.of(OwnedAsyncCompletion.Outcome.INCONCLUSIVE),outcomes);assertEquals(1,context.calls.get());
    }
    @Test void callbackOwnedAttemptSuppliesFinalSettlementAfterWorkerAlreadyReturned() throws Exception {
        var context=new OwnedAsyncCompletionTest.Context();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        context.completion=()->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("CONTROL_TIMEOUT");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}};
        var outcomes=new java.util.concurrent.CopyOnWriteArrayList<OwnedAsyncCompletion.Outcome>();
        var owned=new OwnedAsyncCompletion(context.context,outcomes::add);
        var callback=new FutureTask<Void>(()->{context.listener.onTimeout(context.event());return null;});Thread thread=new Thread(callback);thread.start();
        try {assertTrue(entered.await(1,TimeUnit.SECONDS));assertTrue(outcomes.isEmpty());owned.workerClosed();
            assertEquals(List.of(OwnedAsyncCompletion.Outcome.IN_PROGRESS),outcomes);
        }finally{release.countDown();thread.join(1500);}
        assertFalse(thread.isAlive());callback.get();assertEquals(1,context.calls.get());
        assertEquals(List.of(OwnedAsyncCompletion.Outcome.IN_PROGRESS,OwnedAsyncCompletion.Outcome.COMPLETE),outcomes);
    }
    @Test void observerRunsAfterAttemptLockIsReleased() {
        var context=new OwnedAsyncCompletionTest.Context();var ref=new java.util.concurrent.atomic.AtomicReference<OwnedAsyncCompletion>();
        var outcomes=new ArrayList<OwnedAsyncCompletion.Outcome>();
        var owned=new OwnedAsyncCompletion(context.context,result->{
            var read=new FutureTask<>(()->ref.get().finish());Thread other=new Thread(read);other.start();
            try {assertEquals(OwnedAsyncCompletion.Outcome.COMPLETE,read.get(500,TimeUnit.MILLISECONDS));}
            catch(Exception failure){throw new AssertionError(failure);}outcomes.add(result);
        });ref.set(owned);owned.workerClosed();assertEquals(List.of(OwnedAsyncCompletion.Outcome.COMPLETE),outcomes);assertEquals(1,context.calls.get());
    }
    @Test void unsupportedCycleDuringCompletionCannotReportCompleteOrRenewContext() throws Exception {
        var context=new OwnedAsyncCompletionTest.Context();var outcomes=new ArrayList<OwnedAsyncCompletion.Outcome>();
        var owned=new OwnedAsyncCompletion(context.context,outcomes::add);
        context.completion=()->assertEquals("ASYNC_CYCLE_UNSUPPORTED",assertThrows(java.io.IOException.class,()->context.listener.onStartAsync(context.event())).getMessage());
        owned.workerClosed();assertFalse(outcomes.isEmpty());assertTrue(outcomes.stream().allMatch(value->value==OwnedAsyncCompletion.Outcome.INCONCLUSIVE));
        assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,owned::checkActive).code());
        context.listener.onComplete(context.event());assertEquals(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,owned.finish());assertEquals(1,context.calls.get());
    }
    @Test void errorCompletesInCallbackScopeButCannotSettleWorkerResourcesEarly() throws Exception {
        var context=new OwnedAsyncCompletionTest.Context();var outcomes=new ArrayList<OwnedAsyncCompletion.Outcome>();
        var owned=new OwnedAsyncCompletion(context.context,outcomes::add);owned.checkActive();
        context.listener.onError(context.event());context.errorReturned=true;
        assertEquals(1,context.calls.get());assertTrue(outcomes.isEmpty());
        assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,owned::checkActive).code());
        owned.workerClosed();owned.workerClosed();
        assertEquals(List.of(OwnedAsyncCompletion.Outcome.COMPLETE),outcomes);assertEquals(1,context.calls.get());
    }
    @Test void containerCompletionIsSignalOnlyUntilOriginalWorkerCloses() throws Exception {
        var context=new OwnedAsyncCompletionTest.Context();var outcomes=new ArrayList<OwnedAsyncCompletion.Outcome>();
        var owned=new OwnedAsyncCompletion(context.context,outcomes::add);context.listener.onComplete(context.event());
        assertTrue(outcomes.isEmpty());assertEquals(0,context.calls.get());
        assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,owned::checkActive).code());
        owned.workerClosed();assertEquals(List.of(OwnedAsyncCompletion.Outcome.COMPLETE),outcomes);assertEquals(0,context.calls.get());
    }
    @Test void refusalCannotBeReplacedByLateContainerCompletion() throws Exception {
        var context=new OwnedAsyncCompletionTest.Context();context.completion=()->{throw new IllegalStateException("invented-refusal");};
        var outcomes=new ArrayList<OwnedAsyncCompletion.Outcome>();var owned=new OwnedAsyncCompletion(context.context,outcomes::add);
        assertEquals("ASYNC_COMPLETION_REFUSED",assertThrows(java.io.IOException.class,()->context.listener.onTimeout(context.event())).getMessage());
        assertTrue(outcomes.isEmpty());context.listener.onComplete(context.event());owned.workerClosed();
        assertEquals(List.of(OwnedAsyncCompletion.Outcome.INCONCLUSIVE),outcomes);assertEquals(1,context.calls.get());
    }
    @Test void competingCallbackReturnsWhileWorkerCompletionIsHeldAndFinalRefusalSettles() throws Exception {
        var context=new OwnedAsyncCompletionTest.Context();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        context.completion=()->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("CONTROL_TIMEOUT");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}throw new IllegalStateException("invented-refusal");};
        var outcomes=new java.util.concurrent.CopyOnWriteArrayList<OwnedAsyncCompletion.Outcome>();
        var owned=new OwnedAsyncCompletion(context.context,outcomes::add);
        var worker=new FutureTask<Void>(()->{owned.workerClosed();return null;});
        var callback=new FutureTask<Void>(()->{context.listener.onTimeout(context.event());return null;});
        Thread wt=new Thread(worker),ct=new Thread(callback);boolean returned=false;
        try {wt.start();assertTrue(entered.await(1,TimeUnit.SECONDS));ct.start();
            try{callback.get(250,TimeUnit.MILLISECONDS);returned=true;}catch(TimeoutException expected){}
            assertFalse(worker.isDone());
        }finally{release.countDown();wt.join(1500);ct.join(1500);}
        assertFalse(wt.isAlive());assertFalse(ct.isAlive());worker.get();callback.get();assertTrue(returned);
        assertEquals(List.of(OwnedAsyncCompletion.Outcome.IN_PROGRESS,OwnedAsyncCompletion.Outcome.INCONCLUSIVE),outcomes);
        assertEquals(1,context.calls.get());assertEquals(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,owned.finish());
    }
}
