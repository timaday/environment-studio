package studio.environment.server.plan;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.session.SessionCleanup;

/** Bounded HTTP ownership only; the caller proves original V3 authority before admission. */
final class V3PlanTransfers implements SessionCleanup {
    private enum Kind { METADATA, CREDENTIALS, SEMANTIC }
    private final Set<Operation> active=Collections.newSetFromMap(new IdentityHashMap<>());

    Operation admitMetadata(SessionLedger.Lease lease) {
        Objects.requireNonNull(lease);
        var slot=PlanController.metadata();
        boolean installed=false;
        try {
            synchronized(this) {
                if(active.size()>=9)throw new PlanRefusal(PlanRefusal.Code.CAPACITY);
                var operation=new Operation(lease,Kind.METADATA,slot);
                active.add(operation);installed=true;return operation;
            }
        } finally { if(!installed)slot.close(); }
    }
    synchronized Operation admitCredentials(SessionLedger.Lease lease) {
        Objects.requireNonNull(lease);
        if(active.size()>=9 || active.stream().filter(operation->operation.kind==Kind.CREDENTIALS).count()>=4)
            throw new PlanRefusal(PlanRefusal.Code.CAPACITY);
        var operation=new Operation(lease,Kind.CREDENTIALS,null);active.add(operation);return operation;
    }
    synchronized Operation admitSemantic(SessionLedger.Lease lease) {
        Objects.requireNonNull(lease);
        if(active.size()>=9 || active.stream().anyMatch(operation->operation.kind==Kind.SEMANTIC))
            throw new PlanRefusal(PlanRefusal.Code.CAPACITY);
        var operation=new Operation(lease,Kind.SEMANTIC,null);active.add(operation);return operation;
    }
    @Override public synchronized boolean awaitingWork(SessionLedger.Lease lease) {
        return active.stream().anyMatch(operation->operation.lease.equals(lease));
    }
    @Override public synchronized void invalidate(SessionLedger.Lease lease) {
        boolean pending=false;
        for(var operation:active)if(operation.lease.equals(lease)){operation.cancelled=true;pending=true;}
        if(pending)throw new PlanRefusal(PlanRefusal.Code.CLEANUP_INCONCLUSIVE);
    }
    final class Operation {
        private final SessionLedger.Lease lease;
        private final Kind kind;
        private final PlanController.MetadataSlot slot;
        private volatile boolean cancelled;
        private boolean settled,uncertain;
        private Operation(SessionLedger.Lease lease,Kind kind,PlanController.MetadataSlot slot){this.lease=lease;this.kind=kind;this.slot=slot;}
        boolean cancelled(){return cancelled;}
        /** Sole helper observer calls after original worker closure; no Servlet ownership here. */
        void settlement(OwnedAsyncCompletion.Outcome outcome,HostedSessions sessions) {
            Objects.requireNonNull(outcome);Objects.requireNonNull(sessions);
            boolean quarantine=false,last=false;
            synchronized(V3PlanTransfers.this) {
                if(settled || uncertain)return;
                switch(outcome) {
                    case IN_PROGRESS -> { return; }
                    case INCONCLUSIVE -> { uncertain=true;cancelled=true;quarantine=true; }
                    case COMPLETE -> {
                        settled=true;active.remove(this);if(slot!=null)slot.close();
                        last=active.stream().noneMatch(operation->operation.lease.equals(lease));
                    }
                }
            }
            if(quarantine)sessions.quarantine(lease);
            else if(last)sessions.resumeCleanupAfterWork(lease.id());
        }
        @Override public String toString(){return "V3PlanTransfer[redacted]";}
    }
}
