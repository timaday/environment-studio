package studio.environment.server.workspace;

import jakarta.servlet.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/** Sole async cycle/attempt. No callback waits for a lock held across Servlet complete. */
final class V3WorkspaceCompletion implements AsyncListener {
    enum Outcome {COMPLETE,IN_PROGRESS,INCONCLUSIVE}
    private final AsyncContext context;
    private final Consumer<Outcome> settlement;
    private final ReentrantLock lock=new ReentrantLock();
    private final AtomicBoolean observed=new AtomicBoolean(),unsupported=new AtomicBoolean(),workerClosed=new AtomicBoolean();
    private final AtomicBoolean aborted=new AtomicBoolean();
    private boolean attempted;
    private volatile Outcome outcome=Outcome.IN_PROGRESS;
    V3WorkspaceCompletion(AsyncContext context,Consumer<Outcome> settlement){this.context=context;this.settlement=settlement;context.addListener(this);}
    private Outcome attempt(){
        if(!lock.tryLock())return Outcome.IN_PROGRESS;
        try{if(unsupported.get()){outcome=Outcome.INCONCLUSIVE;return outcome;}if(attempted)return outcome;
            if(observed.get()){outcome=Outcome.COMPLETE;return outcome;}attempted=true;
            try{context.complete();outcome=unsupported.get()?Outcome.INCONCLUSIVE:Outcome.COMPLETE;}
            catch(RuntimeException refused){outcome=Outcome.INCONCLUSIVE;}return outcome;
        }finally{lock.unlock();}
    }
    void workerClosed(){workerClosed.set(true);Outcome result=attempt();settlement.accept(result);}
    void check(){if(aborted.get()||unsupported.get()||observed.get())throw new studio.environment.core.workspace.WorkspaceRefusal(studio.environment.core.workspace.WorkspaceRefusal.Code.UNAVAILABLE);}
    private void callback()throws IOException{aborted.set(true);Outcome result=attempt();if(workerClosed.get())settlement.accept(result);if(result==Outcome.INCONCLUSIVE)throw new IOException("WORKSPACE_ASYNC_COMPLETION_REFUSED");}
    @Override public void onComplete(AsyncEvent event){observed.set(true);}
    @Override public void onError(AsyncEvent event)throws IOException{callback();}
    @Override public void onTimeout(AsyncEvent event)throws IOException{callback();}
    @Override public void onStartAsync(AsyncEvent event)throws IOException{unsupported.set(true);if(workerClosed.get())settlement.accept(Outcome.INCONCLUSIVE);throw new IOException("WORKSPACE_ASYNC_CYCLE_UNSUPPORTED");}
    @Override public String toString(){return "V3WorkspaceCompletion[redacted]";}
}
