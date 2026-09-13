package studio.environment.server.workspace;

import java.util.*;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.workspace.WorkspaceRefusal;
import studio.environment.server.session.*;

/** Four process-owned operations, including unsettled cleanup; no request queue. */
final class V3WorkspaceOperations implements SessionCleanup {
    private final Set<Operation> active=Collections.newSetFromMap(new IdentityHashMap<>());
    synchronized Operation admit(SessionLedger.Lease lease) {
        if(active.size()>=4)throw new WorkspaceRefusal(WorkspaceRefusal.Code.CAPACITY);
        var result=new Operation(lease);active.add(result);return result;
    }
    @Override public synchronized void invalidate(SessionLedger.Lease lease) {
        if(active.stream().anyMatch(op->op.lease.equals(lease)))throw new IllegalStateException("WORKSPACE_CLEANUP_INCONCLUSIVE");
    }
    @Override public synchronized boolean awaitingWork(SessionLedger.Lease lease) {
        return active.stream().anyMatch(operation->operation.lease.equals(lease));
    }
    synchronized int activeCount(){return active.size();}
    final class Operation {
        final SessionLedger.Lease lease;
        private boolean settled,terminalUncertainty;
        Operation(SessionLedger.Lease lease){this.lease=lease;}
        void complete(HostedSessions sessions) {
            boolean last;
            synchronized(V3WorkspaceOperations.this){
                if(settled||terminalUncertainty)return;
                settled=true;active.remove(this);
                last=active.stream().noneMatch(operation->operation.lease.equals(lease));
            }
            if(last)sessions.resumeCleanupAfterWork(lease.id());
        }
        void inconclusive(HostedSessions sessions){
            synchronized(V3WorkspaceOperations.this){if(settled||terminalUncertainty)return;terminalUncertainty=true;}
            sessions.quarantine(lease);
        }
        @Override public String toString(){return "V3WorkspaceOperation[redacted]";}
    }
}
