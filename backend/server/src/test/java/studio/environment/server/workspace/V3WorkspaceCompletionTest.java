package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class V3WorkspaceCompletionTest {
    static class Context extends MockAsyncContext {
        AsyncListener listener;int attempts;boolean reject;
        Context(){super(new MockHttpServletRequest(),new MockHttpServletResponse());}
        @Override public void addListener(AsyncListener value){assertNull(listener);listener=value;}
        @Override public void complete(){attempts++;if(reject)throw new IllegalStateException("MOCK_PRIVATE_COMPLETION");}
        AsyncEvent event(){return new AsyncEvent(this);}
    }
    @Test void workerOwnsOnlyOneCompleteAttemptAndCallbacksDoNotClaimResourceCleanup()throws Exception {
        var context=new Context();var results=new ArrayList<V3WorkspaceCompletion.Outcome>();var completion=new V3WorkspaceCompletion(context,results::add);
        context.listener.onTimeout(context.event());assertEquals(1,context.attempts);assertTrue(results.isEmpty());
        completion.workerClosed();completion.workerClosed();assertEquals(1,context.attempts);assertEquals(List.of(V3WorkspaceCompletion.Outcome.COMPLETE,V3WorkspaceCompletion.Outcome.COMPLETE),results);
    }
    @Test void refusalIsStickyAndNeverReattemptedOrLeaked()throws Exception {
        var context=new Context();context.reject=true;var results=new ArrayList<V3WorkspaceCompletion.Outcome>();var completion=new V3WorkspaceCompletion(context,results::add);
        var failure=assertThrows(IOException.class,()->context.listener.onError(context.event()));assertEquals("WORKSPACE_ASYNC_COMPLETION_REFUSED",failure.getMessage());assertNull(failure.getCause());
        context.reject=false;context.listener.onComplete(context.event());completion.workerClosed();assertEquals(1,context.attempts);assertEquals(List.of(V3WorkspaceCompletion.Outcome.INCONCLUSIVE),results);
    }
    @Test void containerCompletionBeforeWorkerAvoidsAnIllegalApplicationAttempt()throws Exception {
        var context=new Context();var results=new ArrayList<V3WorkspaceCompletion.Outcome>();var completion=new V3WorkspaceCompletion(context,results::add);
        context.listener.onComplete(context.event());assertTrue(results.isEmpty());completion.workerClosed();assertEquals(0,context.attempts);assertEquals(List.of(V3WorkspaceCompletion.Outcome.COMPLETE),results);
    }
    @Test void callbackCannotWaitUnderSocketLockNeededByWorkerComplete()throws Exception {
        try(var callbacks=Executors.newSingleThreadExecutor()){
            var context=new Context(){@Override public void complete(){super.complete();try{callbacks.submit(()->{try{listener.onError(event());}catch(IOException e){throw new AssertionError();}}).get(500,TimeUnit.MILLISECONDS);}catch(Exception failure){throw new IllegalStateException("MOCK_SOCKET_INVERSION");}}};
            var results=new CopyOnWriteArrayList<V3WorkspaceCompletion.Outcome>();var completion=new V3WorkspaceCompletion(context,results::add);completion.workerClosed();
            assertEquals(1,context.attempts);assertEquals(V3WorkspaceCompletion.Outcome.COMPLETE,results.getLast());
        }
    }
    @Test void callbackOwnerEventuallySettlesAlreadyClosedWorkerWithoutAnotherAttempt()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var context=new Context(){@Override public void complete(){super.complete();entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new IllegalStateException("MOCK_TIMEOUT");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("MOCK_INTERRUPTED");}}};
        var results=new CopyOnWriteArrayList<V3WorkspaceCompletion.Outcome>();var completion=new V3WorkspaceCompletion(context,results::add);
        try(var callbacks=Executors.newSingleThreadExecutor()){
            var callback=callbacks.submit(()->{try{context.listener.onError(context.event());}catch(IOException e){throw new AssertionError();}});
            try{assertTrue(entered.await(1,TimeUnit.SECONDS));completion.workerClosed();assertEquals(List.of(V3WorkspaceCompletion.Outcome.IN_PROGRESS),results);}finally{release.countDown();}
            callback.get(1,TimeUnit.SECONDS);assertEquals(V3WorkspaceCompletion.Outcome.COMPLETE,results.getLast());assertEquals(1,context.attempts);
        }
    }
    @Test void secondCycleIsAnExplicitRefusal()throws Exception {
        var context=new Context();var results=new ArrayList<V3WorkspaceCompletion.Outcome>();var completion=new V3WorkspaceCompletion(context,results::add);
        assertThrows(IOException.class,()->context.listener.onStartAsync(context.event()));completion.workerClosed();assertEquals(0,context.attempts);assertEquals(List.of(V3WorkspaceCompletion.Outcome.INCONCLUSIVE),results);
    }
    @Test void unsupportedCycleAloneCannotReleaseAPendingCompleteNotification()throws Exception {
        var operations=new V3WorkspaceOperations();var sessions=new studio.environment.server.session.HostedSessions(java.time.Clock.systemUTC(),List.of(operations));
        var lease=new studio.environment.core.session.SessionLedger.Lease("mock-cycle",new studio.environment.core.session.Owner("https://mock.invalid","owner"),java.time.Instant.MAX);var operation=operations.admit(lease);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var context=new Context();
        var completion=new V3WorkspaceCompletion(context,outcome->{if(outcome==V3WorkspaceCompletion.Outcome.COMPLETE){entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new AssertionError("MOCK_RELEASE_TIMEOUT");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError();}operation.complete(sessions);}else if(outcome==V3WorkspaceCompletion.Outcome.INCONCLUSIVE)operation.inconclusive(sessions);});
        try(var worker=Executors.newSingleThreadExecutor()){
            var task=worker.submit(completion::workerClosed);
            try{assertTrue(entered.await(1,TimeUnit.SECONDS));assertThrows(IOException.class,()->context.listener.onStartAsync(context.event()));}finally{release.countDown();}
            task.get(1,TimeUnit.SECONDS);assertEquals(1,operations.activeCount());assertEquals(1,context.attempts);
        }
    }

}
