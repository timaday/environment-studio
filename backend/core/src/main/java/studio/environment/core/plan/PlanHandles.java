package studio.environment.core.plan;

import java.util.*;
import java.util.function.Supplier;
import studio.environment.core.planning.TargetIntent;
import static studio.environment.core.plan.PlanRefusal.Code.*;

/** Live display identity belongs to admitted provenance, never to a view or XML row. */
final class PlanHandles {
    private PlanHandles() { }
    static Map<TargetIntent.Ref,String> observed(PlanPorts.Content content) {
        var required=new HashSet<TargetIntent.Ref>();
        for(var entity:content.graph().entities()) {
            var ref=content.provenance().get(entity.key());
            if(!(ref instanceof TargetIntent.Ref.Existing original) || !original.key().equals(entity.key()) || !required.add(ref))throw new PlanRefusal(PROJECTION_REFUSED);
        }
        if(required.size()!=content.provenance().size() || required.size()>20_000)throw new PlanRefusal(PROJECTION_REFUSED);
        return allocate(Map.of(),required,UUID::randomUUID);
    }
    static Map<TargetIntent.Ref,String> draft(Map<TargetIntent.Ref,String> previous,PlanPorts.Draft draft) {
        var required=new HashSet<TargetIntent.Ref>();
        previous.keySet().stream().filter(ref->ref instanceof TargetIntent.Ref.Existing).forEach(required::add);
        int fresh=0;
        for(var decision:draft.intent().entities())if(decision instanceof TargetIntent.EntityDecision.Create created) {
            if(!required.add(created.entity()))throw new PlanRefusal(INVALID_REQUEST);
            if(++fresh>20_000)throw new PlanRefusal(RESOURCE_LIMIT);
        }
        return allocate(previous,required,UUID::randomUUID);
    }
    static Map<TargetIntent.Ref,String> allocate(Map<TargetIntent.Ref,String> previous,Set<TargetIntent.Ref> required,Supplier<UUID> random) {
        if(required.size()>40_000)throw new PlanRefusal(RESOURCE_LIMIT);
        var result=new HashMap<TargetIntent.Ref,String>();var occupied=new HashSet<String>(previous.values());
        if(occupied.size()!=previous.size())throw new PlanRefusal(PROJECTION_REFUSED);
        for(var ref:required) {
            var handle=previous.get(ref);
            if(handle==null) {
                handle=Objects.requireNonNull(random.get()).toString();
                if(!occupied.add(handle))throw new PlanRefusal(PROJECTION_REFUSED);
            }
            result.put(ref,handle);
        }
        return Map.copyOf(result);
    }
}
