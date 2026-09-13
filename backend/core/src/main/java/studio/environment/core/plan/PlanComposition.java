package studio.environment.core.plan;

import java.util.*;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.definitionv2.NativeDefinition.Logical;
import studio.environment.core.profile.ProfileComposer;
import studio.environment.core.planning.TargetIntent;
import static studio.environment.core.plan.PlanPorts.*;

/** Compose into original-observation intent. Target graph keys never fabricate Existing authority. */
public final class PlanComposition {
    private PlanComposition() { }
    public static Draft merge(PlanDefinition definition,Draft existing,Content target,ProfileComposer.Draft proposal) {
        return mergePhysical(definition.physical(),existing,target,proposal);
    }
    static Draft merge(ReadyToPublish definition,Draft existing,Content target,ProfileComposer.Draft proposal) {
        return merge(new PlanDefinition.V2(definition),existing,target,proposal);
    }
    private static Draft mergePhysical(Logical definition,Draft existing,Content target,ProfileComposer.Draft proposal) {
        var entities=new LinkedHashMap<TargetIntent.Ref,TargetIntent.EntityDecision>();
        existing.intent().entities().forEach(entity->entities.put(entity.entity(),entity));
        var slots=new HashSet<String>();
        entities.keySet().forEach(reference->{if(reference instanceof TargetIntent.Ref.Fresh fresh) slots.add(fresh.slot());});
        for(var addition:proposal.additions()) {
            if(!slots.add(addition.slot())) throw new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED);
        }
        // Selecting a profile slot declares shape, never a concrete-value choice.
        for(var selected:proposal.unresolvedFields()) {
            var reference=reference(target,selected.target());
            entities.computeIfAbsent(reference,ignored->unresolvedDecision(definition,reference));
        }
        var containment=new ArrayList<>(existing.intent().containment());
        var targetEdges=new HashSet<>(target.graph().edges());
        var declarations=new HashMap<String,studio.environment.core.definition.DefinitionDraft.Relation>();
        definition.relations().forEach(relation->declarations.put(relation.id(),relation));
        for(var relation:proposal.relationProposals()) {
            var from=reference(target,relation.from()); var to=reference(target,relation.to());
            var declaration=declarations.get(relation.relation());
            if(declaration==null) throw new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED);
            if(declaration.kind()==RelationKind.CONTAINMENT) {
                if(relation.from() instanceof ProfileComposer.Target.Existing parent && relation.to() instanceof ProfileComposer.Target.Existing child
                        && targetEdges.contains(new studio.environment.core.graph.ObservedGraph.Edge(relation.relation(),parent.key(),child.key()))) continue;
                if(to instanceof TargetIntent.Ref.Existing old) entities.putIfAbsent(to,unresolvedDecision(definition,old));
                var assignment=new TargetIntent.Containment(relation.relation(),from,to);
                // Keep unrelated assignments; an explicit included relation replaces only the same child's relation.
                containment.removeIf(edge->edge.relation().equals(assignment.relation()) && edge.child().equals(assignment.child()));
                containment.add(assignment);
            } else {
                var decision=entities.get(from);
                if(decision==null && from instanceof TargetIntent.Ref.Existing old) decision=unresolvedDecision(definition,old);
                var references=new TreeMap<String,TargetIntent.ReferenceValue>();
                if(decision instanceof TargetIntent.EntityDecision.Retain retained) {
                    references.putAll(retained.references()); references.put(relation.relation(),new TargetIntent.ReferenceValue.To(to));
                    entities.put(from,new TargetIntent.EntityDecision.Retain(retained.entity(),retained.fields(),references));
                } else if(decision instanceof TargetIntent.EntityDecision.Create created) {
                    references.putAll(created.references()); references.put(relation.relation(),new TargetIntent.ReferenceValue.To(to));
                    entities.put(from,new TargetIntent.EntityDecision.Create(created.entity(),created.fields(),references));
                } else throw new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED);
            }
        }
        return new Draft(new TargetIntent(List.copyOf(entities.values()),containment),existing.placements());
    }
    private static TargetIntent.EntityDecision unresolvedDecision(Logical definition,TargetIntent.Ref reference) {
        var type=definition.entityTypes().stream().filter(candidate->candidate.id().equals(reference.type())).findFirst().orElseThrow(()->new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED));
        var fields=new TreeMap<String,TargetIntent.FieldValue>();
        type.fields().forEach(field->fields.put(field.id(),new TargetIntent.FieldValue.Unresolved()));
        var references=new TreeMap<String,TargetIntent.ReferenceValue>();
        definition.relations().stream().filter(relation->relation.kind()==RelationKind.REFERENCE && relation.fromType().equals(reference.type()))
                .forEach(relation->references.put(relation.id(),new TargetIntent.ReferenceValue.Unresolved()));
        return switch(reference) {
            case TargetIntent.Ref.Existing old -> new TargetIntent.EntityDecision.Retain(old,fields,references);
            case TargetIntent.Ref.Fresh fresh -> new TargetIntent.EntityDecision.Create(fresh,fields,references);
        };
    }
    private static TargetIntent.Ref reference(Content target,ProfileComposer.Target selected) {
        return switch(selected) {
            case ProfileComposer.Target.New fresh -> new TargetIntent.Ref.Fresh(fresh.slot(),fresh.type());
            case ProfileComposer.Target.Existing old -> {
                var original=target.provenance().get(old.key());
                if(original==null) throw new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED);
                yield original;
            }
        };
    }
}
