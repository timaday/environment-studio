package studio.environment.server.plan;

import java.util.*;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.session.SessionLedger;
import static studio.environment.core.plan.PlanDefinition.Version.V3;

/** Closed small metadata replies; transfer rechecks original authority outside output locks. */
sealed interface V3PlanReply {
    Map<String,Object> wire();
    void verify(HostedPlanService service,SessionLedger.Lease lease);
    record Acknowledgement(HostedPlanService.Ack value) implements V3PlanReply {
        public Acknowledgement { Objects.requireNonNull(value); }
        public Map<String,Object> wire() {
            var result=new LinkedHashMap<String,Object>();
            result.put("planId",value.planId());result.put("revision",value.revision());
            value.operationId().ifPresent(id->result.put("operationId",id));
            return Collections.unmodifiableMap(result);
        }
        public void verify(HostedPlanService service,SessionLedger.Lease lease) {
            service.requireOwned(lease,value.planId(),V3);
            value.operationId().ifPresent(id->service.requireOperationOwned(lease,id,V3));
        }
        @Override public String toString(){return "V3PlanReply.Acknowledgement[redacted]";}
    }
    record Materialized(String planId,String revision,HostedPlanService.Materialization value) implements V3PlanReply {
        public Materialized {Objects.requireNonNull(planId);Objects.requireNonNull(revision);Objects.requireNonNull(value);}
        public Map<String,Object> wire(){
            var result=new LinkedHashMap<String,Object>();
            result.put("revision",revision);result.put("state",value.state().name());
            result.put("complete",value.complete());result.put("diagnostics",value.diagnostics());
            return Collections.unmodifiableMap(result);
        }
        public void verify(HostedPlanService service,SessionLedger.Lease lease){service.requireOwned(lease,planId,V3);}
        @Override public String toString(){return "V3PlanReply.Materialized[redacted]";}
    }
    record Status(HostedPlanService.Status value) implements V3PlanReply {
        public Status { Objects.requireNonNull(value); }
        public Map<String,Object> wire() {
            var result=new LinkedHashMap<String,Object>();
            result.put("operationId",value.operationId());result.put("planId",value.planId());
            result.put("phase",value.phase().name().toLowerCase(Locale.ROOT));result.put("code",value.code());
            result.put("cleanup",value.cleanup().name().toLowerCase(Locale.ROOT).replace('_','-'));
            value.installedRevision().ifPresent(revision->result.put("installedRevision",revision));
            return Collections.unmodifiableMap(result);
        }
        public void verify(HostedPlanService service,SessionLedger.Lease lease) {
            service.requireOperationOwned(lease,value.operationId(),V3);
        }
        @Override public String toString(){return "V3PlanReply.Status[redacted]";}
    }
    record Summary(HostedPlanService.V3View value) implements V3PlanReply {
        public Summary { Objects.requireNonNull(value); }
        public Map<String,Object> wire() {
            var view=value.summary();var result=new LinkedHashMap<String,Object>();
            result.put("planId",view.planId());result.put("revision",view.revision());
            var definition=new LinkedHashMap<String,Object>();
            definition.put("objectId",view.definition().objectId());definition.put("workspaceRevision",view.definition().workspaceRevision());
            result.put("definition",Collections.unmodifiableMap(definition));
            result.put("bindingId",view.bindingId());result.put("destinationId",view.destinationId());
            result.put("currentCounts",view.currentCounts());result.put("targetCounts",view.targetCounts());
            result.put("observedDestination",view.observedDestination().orElse(null));
            result.put("inspectionValid",view.inspectionValid());result.put("targetComplete",view.targetComplete());
            result.put("exportAvailable",false);result.put("blockers",view.blockers());
            view.activeOperationId().ifPresent(id->result.put("activeOperationId",id));
            result.put("currentComputedCounts",value.currentComputedCounts().orElse(null));
            result.put("targetComputedCounts",value.targetComputedCounts().orElse(null));
            return Collections.unmodifiableMap(result);
        }
        public void verify(HostedPlanService service,SessionLedger.Lease lease) { service.verifySummaryV3(lease,value); }
        @Override public String toString(){return "V3PlanReply.Summary[redacted]";}
    }
}
