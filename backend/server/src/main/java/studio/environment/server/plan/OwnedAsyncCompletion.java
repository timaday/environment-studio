package studio.environment.server.plan;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

/** Sole completion owner for one async cycle; register before initial dispatch returns.
 * Call finish only after worker-owned application resources have been closed.
 * Callbacks settle HTTP completion, never application cleanup or request authority.
 * No callback or competing worker waits on the attempt lock. Tomcat callbacks
 * may own state-machine or socket locks needed by the in-flight complete call.
 */
final class OwnedAsyncCompletion implements AsyncListener {
    /** COMPLETE means the request was accepted or container completion observed;
     * it does not prove response delivery, flushing, or application cleanup.
     * IN_PROGRESS makes no settled completion claim; INCONCLUSIVE is sticky
     * after refusal. Neither result authorizes a second context attempt. */
    enum Outcome { COMPLETE, IN_PROGRESS, INCONCLUSIVE }
    private final AsyncContext context;
    private final ReentrantLock attemptLock=new ReentrantLock();
    private final AtomicBoolean containerComplete=new AtomicBoolean();
    private final AtomicBoolean unsupportedCycle=new AtomicBoolean();
    private boolean attempted;
    private Outcome outcome=Outcome.INCONCLUSIVE;

    OwnedAsyncCompletion(AsyncContext context) {
        this.context=Objects.requireNonNull(context);
        try { context.addListener(this); }
        catch(RuntimeException refused) { throw new IllegalStateException("ASYNC_COMPLETION_REGISTRATION_REFUSED"); }
    }
    Outcome finish() {
        if(!attemptLock.tryLock())return Outcome.IN_PROGRESS;
        try {
            if(unsupportedCycle.get())return Outcome.INCONCLUSIVE;
            if(attempted)return outcome;
            if(containerComplete.get())return Outcome.COMPLETE;
            attempted=true;
            // Servlet has no atomic complete-if-live API. A concurrent external
            // container completion may still refuse this single attempt.
            try { context.complete(); }
            catch(RuntimeException refused) { return Outcome.INCONCLUSIVE; }
            outcome=unsupportedCycle.get()?Outcome.INCONCLUSIVE:Outcome.COMPLETE;
            return outcome;
        } finally { attemptLock.unlock(); }
    }
    @Override public void onComplete(AsyncEvent event) { containerComplete.set(true); }
    @Override public void onError(AsyncEvent event) throws IOException { callback(); }
    @Override public void onTimeout(AsyncEvent event) throws IOException { callback(); }
    private void callback() throws IOException {
        // An in-flight attempt may subsequently be refused after this callback
        // returns. Do not wait under a container lock or claim it succeeded.
        if(finish()==Outcome.INCONCLUSIVE)throw new IOException("ASYNC_COMPLETION_REFUSED");
    }
    @Override public void onStartAsync(AsyncEvent event) throws IOException {
        unsupportedCycle.set(true);
        throw new IOException("ASYNC_CYCLE_UNSUPPORTED");
    }
    @Override public String toString(){return "OwnedAsyncCompletion[redacted]";}
}
