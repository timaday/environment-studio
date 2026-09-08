package studio.environment.core.plan;

import java.util.*;
import studio.environment.core.planning.TargetIntent;
import static studio.environment.core.plan.PlanPorts.*;

/** Closed semantic command encoding; presentation and map insertion order cannot affect replay. */
final class DraftEncoding {
    private DraftEncoding() { }
    static Object encode(Draft draft) {
        return Map.of("entities",draft.intent().entities().stream().map(DraftEncoding::entity).toList(),
                "containment",draft.intent().containment().stream().map(edge->Map.of("relation",edge.relation(),"parent",reference(edge.parent()),"child",reference(edge.child()))).toList(),
                "placements",draft.placements().stream().map(placement->Map.of("entity",reference(placement.entity()),"documentId",placement.documentId(),"projectionId",placement.projectionId(),"parent",parent(placement.parent()))).toList());
    }
    static Object reference(TargetIntent.Ref reference) {
        return switch(reference) {
            case TargetIntent.Ref.Existing old -> Map.of("kind","existing","type",old.type(),"identity",old.key().identity());
            case TargetIntent.Ref.Fresh fresh -> Map.of("kind","fresh","type",fresh.type(),"slot",fresh.slot());
        };
    }
    private static Object parent(Parent parent) {
        return switch(parent) {
            case Parent.Existing old -> Map.of("kind","existing","documentId",old.documentId(),"sourceDigest",old.sourceDigest(),"elementIndex",Integer.toString(old.elementIndex()));
            case Parent.Created fresh -> Map.of("kind","created","entity",reference(fresh.entity()));
        };
    }
    private static Object entity(TargetIntent.EntityDecision entity) {
        return switch(entity) {
            case TargetIntent.EntityDecision.Remove remove -> Map.of("kind","remove","entity",reference(remove.entity()));
            case TargetIntent.EntityDecision.Retain retain -> Map.of("kind","retain","entity",reference(retain.entity()),"fields",fields(retain.fields()),"references",references(retain.references()));
            case TargetIntent.EntityDecision.Create create -> Map.of("kind","create","entity",reference(create.entity()),"fields",fields(create.fields()),"references",references(create.references()));
        };
    }
    private static Object fields(Map<String,TargetIntent.FieldValue> values) {
        var result=new TreeMap<String,Object>(); values.forEach((key,value)->result.put(key,switch(value) {
            case TargetIntent.FieldValue.Entered entered -> Map.of("kind","entered","text",entered.text());
            case TargetIntent.FieldValue.KeepObserved ignored -> Map.of("kind","keep-observed");
            case TargetIntent.FieldValue.ExplicitlyAbsent ignored -> Map.of("kind","absent");
            case TargetIntent.FieldValue.Unresolved ignored -> Map.of("kind","unresolved");
        })); return result;
    }
    private static Object references(Map<String,TargetIntent.ReferenceValue> values) {
        var result=new TreeMap<String,Object>(); values.forEach((key,value)->result.put(key,switch(value) {
            case TargetIntent.ReferenceValue.To to -> Map.of("kind","to","target",reference(to.target()));
            case TargetIntent.ReferenceValue.KeepObserved ignored -> Map.of("kind","keep-observed");
            case TargetIntent.ReferenceValue.ExplicitlyAbsent ignored -> Map.of("kind","absent");
            case TargetIntent.ReferenceValue.Unresolved ignored -> Map.of("kind","unresolved");
        })); return result;
    }
    static long enteredBytes(Draft draft) {
        long bytes=0;
        for(var decision:draft.intent().entities()) {
            var fields=decision instanceof TargetIntent.EntityDecision.Create created?created.fields():decision instanceof TargetIntent.EntityDecision.Retain retained?retained.fields():Map.<String,TargetIntent.FieldValue>of();
            for(var field:fields.values()) if(field instanceof TargetIntent.FieldValue.Entered entered) {
                bytes+=HostedPlanService.utf8(entered.text());
                if(bytes>16L*1_048_576) throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT);
            }
        }
        return bytes;
    }
}
