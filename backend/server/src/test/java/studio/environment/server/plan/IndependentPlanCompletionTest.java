package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanRefusal;

class IndependentPlanCompletionTest {
    static final class Context {
        final AtomicInteger attempts=new AtomicInteger();
        AsyncListener listener;
        Runnable complete=()->{};
        final AsyncContext value=(AsyncContext)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{AsyncContext.class},(proxy,method,args)->{
            if(method.getName().equals("addListener")){listener=(AsyncListener)args[0];return null;}
            if(method.getName().equals("complete")){attempts.incrementAndGet();complete.run();return null;}
            throw new AssertionError("UNEXPECTED_CONTEXT_CALL");
        });
        AsyncEvent event(){return new AsyncEvent(value);}
    }
    @Test void callbackRefusalAfterObservedCompletionRemainsUncertainAndCannotAffectSibling() throws Exception {
        var context=new Context();var sibling=new Context();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        context.complete=()->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("MOCK_TIMEOUT");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}throw new IllegalStateException("invented-refusal");};
        var outcomes=new CopyOnWriteArrayList<OwnedAsyncCompletion.Outcome>();var other=new CopyOnWriteArrayList<OwnedAsyncCompletion.Outcome>();
        var owner=new OwnedAsyncCompletion(context.value,outcomes::add);var independent=new OwnedAsyncCompletion(sibling.value,other::add);
        var callback=new FutureTask<String>(()->assertThrows(IOException.class,()->context.listener.onError(context.event())).getMessage());
        Thread thread=new Thread(callback);thread.start();
        try {
            assertTrue(entered.await(1,TimeUnit.SECONDS));assertTrue(outcomes.isEmpty());
            owner.workerClosed();context.listener.onComplete(context.event());
            assertEquals(List.of(OwnedAsyncCompletion.Outcome.IN_PROGRESS),outcomes);
            independent.checkActive();assertTrue(other.isEmpty());independent.workerClosed();
            assertEquals(List.of(OwnedAsyncCompletion.Outcome.COMPLETE),other);
        } finally {release.countDown();thread.join(1500);}
        assertFalse(thread.isAlive());assertEquals("ASYNC_COMPLETION_REFUSED",callback.get());
        assertEquals(List.of(OwnedAsyncCompletion.Outcome.IN_PROGRESS,OwnedAsyncCompletion.Outcome.INCONCLUSIVE),outcomes);
        owner.workerClosed();assertEquals(1,context.attempts.get());assertEquals(1,sibling.attempts.get());
        assertEquals(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,owner.finish());
        assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,owner::checkActive).code());
    }
    @Test void timeoutBeforeWorkerClosureAbortsImmediatelyWithoutPublishingSettlement() throws Exception {
        var context=new Context();var outcomes=new CopyOnWriteArrayList<OwnedAsyncCompletion.Outcome>();
        var owner=new OwnedAsyncCompletion(context.value,outcomes::add);
        context.listener.onTimeout(context.event());
        assertTrue(outcomes.isEmpty());assertEquals(1,context.attempts.get());
        assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,owner::checkActive).code());
        context.listener.onComplete(context.event());assertTrue(outcomes.isEmpty());
        owner.workerClosed();assertEquals(List.of(OwnedAsyncCompletion.Outcome.COMPLETE),outcomes);
        owner.workerClosed();assertEquals(1,context.attempts.get());
    }
    @Test void reentrantErrorCannotNotifyObserverWhileOuterAttemptStillOwnsItsLock() throws Exception {
        var context=new Context();
        var selected=new java.util.concurrent.atomic.AtomicReference<OwnedAsyncCompletion>();
        var lockOutcomes=new CopyOnWriteArrayList<OwnedAsyncCompletion.Outcome>();
        var owner=new OwnedAsyncCompletion(context.value,outcome->{
            var query=new FutureTask<>(()->selected.get().finish());Thread other=new Thread(query);other.start();
            try {lockOutcomes.add(query.get(500,TimeUnit.MILLISECONDS));other.join(500);}
            catch(Exception failure){throw new AssertionError(failure);}
        });selected.set(owner);
        context.complete=()->assertThrows(IOException.class,()->context.listener.onError(context.event()));
        owner.workerClosed();
        assertFalse(lockOutcomes.isEmpty());
        assertFalse(lockOutcomes.contains(OwnedAsyncCompletion.Outcome.IN_PROGRESS),
                "observer called while original complete still holds attempt lock");
        assertEquals(1,context.attempts.get());
    }
    @Test void rawFinishCannotContradictPreviouslyObservedReentrantErrorUncertainty() {
        var context=new Context();var outcomes=new CopyOnWriteArrayList<OwnedAsyncCompletion.Outcome>();
        var owner=new OwnedAsyncCompletion(context.value,outcomes::add);
        context.complete=()->assertEquals("ASYNC_COMPLETION_REFUSED",assertThrows(IOException.class,
                ()->context.listener.onError(context.event())).getMessage());
        owner.workerClosed();
        assertEquals(List.of(OwnedAsyncCompletion.Outcome.INCONCLUSIVE),outcomes);
        assertEquals(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,owner.finish(),
                "raw finish must preserve the uncertainty reported by the same owner");
        assertEquals(1,context.attempts.get());
    }
}
