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
    private final AtomicBoolean workerClosed=new AtomicBoolean(),aborted=new AtomicBoolean(),reportedUncertainty=new AtomicBoolean();
    private final java.util.function.Consumer<Outcome> settlement;
    private boolean attempted;
    private Outcome outcome=Outcome.INCONCLUSIVE;

    OwnedAsyncCompletion(AsyncContext context) { this(context,ignored->{}); }
    OwnedAsyncCompletion(AsyncContext context,java.util.function.Consumer<Outcome> settlement) {
        this.context=Objects.requireNonNull(context);this.settlement=Objects.requireNonNull(settlement);
        try { context.addListener(this); }
        catch(RuntimeException refused) { throw new IllegalStateException("ASYNC_COMPLETION_REGISTRATION_REFUSED"); }
    }
    void workerClosed() { if(workerClosed.compareAndSet(false,true))notifySettlement(finish()); }
    void checkActive() {
        if(aborted.get() || unsupportedCycle.get() || containerComplete.get())
            throw new studio.environment.core.plan.PlanRefusal(studio.environment.core.plan.PlanRefusal.Code.CANCELLED);
    }
    private void notifySettlement(Outcome result) {
        if(!workerClosed.get())return;
        if(result==Outcome.INCONCLUSIVE || unsupportedCycle.get())reportedUncertainty.set(true);
        // A synchronous container callback can reenter while outer finish owns this lock.
        // Its outer worker/callback publishes the latched result after that attempt unlocks.
        if(attemptLock.isHeldByCurrentThread())return;
        settlement.accept(reportedUncertainty.get()?Outcome.INCONCLUSIVE:result);
    }
    Outcome finish() {
        if(!attemptLock.tryLock())return Outcome.IN_PROGRESS;
        try {
            if(unsupportedCycle.get() || reportedUncertainty.get())return Outcome.INCONCLUSIVE;
            if(attempted)return outcome;
            if(containerComplete.get())return Outcome.COMPLETE;
            attempted=true;
            // Servlet has no atomic complete-if-live API. A concurrent external
            // container completion may still refuse this single attempt.
            try { context.complete(); }
            catch(RuntimeException refused) { return Outcome.INCONCLUSIVE; }
            outcome=unsupportedCycle.get() || reportedUncertainty.get()?Outcome.INCONCLUSIVE:Outcome.COMPLETE;
            return outcome;
        } finally { attemptLock.unlock(); }
    }
    @Override public void onComplete(AsyncEvent event) { containerComplete.set(true); }
    @Override public void onError(AsyncEvent event) throws IOException { callback(); }
    @Override public void onTimeout(AsyncEvent event) throws IOException { callback(); }
    private void callback() throws IOException {
        // An in-flight attempt may subsequently be refused after this callback
        // returns. Do not wait under a container lock or claim it succeeded.
        aborted.set(true);Outcome result=finish();notifySettlement(result);
        if(result==Outcome.INCONCLUSIVE)throw new IOException("ASYNC_COMPLETION_REFUSED");
    }
    @Override public void onStartAsync(AsyncEvent event) throws IOException {
        aborted.set(true);unsupportedCycle.set(true);notifySettlement(Outcome.INCONCLUSIVE);
        throw new IOException("ASYNC_CYCLE_UNSUPPORTED");
    }
    @Override public String toString(){return "OwnedAsyncCompletion[redacted]";}
}
