package studio.environment.core.plan;

import java.util.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.planning.TargetIntent.FieldValue;
import studio.environment.core.workspace.NativeCommand;

/** Closed command values retain opaque handles until the application admits the exact command. */
public record PlanCommand(HostedPlanService.Mutation mutation,Action action) {
    @Override public String toString() { return "PlanCommand[redacted]"; }
    public sealed interface Ref {
        record Existing(String handle) implements Ref { @Override public String toString() { return "Handle[redacted]"; } }
        record Fresh(String slotId,String typeId) implements Ref { }
    }
    public sealed interface ReferenceState {
        record To(Ref target) implements ReferenceState { }
        record KeepObserved() implements ReferenceState { }
        record Absent() implements ReferenceState { }
        record Unresolved() implements ReferenceState { }
    }
    public sealed interface Entity {
        Ref entity();
        record Retain(Ref.Existing entity,Map<String,FieldValue> fields,Map<String,ReferenceState> references) implements Entity {
            public Retain { fields=boundedMap(fields); references=boundedMap(references); }
            @Override public String toString() { return "RetainCommand[redacted]"; }
        }
        record Create(Ref.Fresh entity,Map<String,FieldValue> fields,Map<String,ReferenceState> references) implements Entity {
            public Create { fields=boundedMap(fields); references=boundedMap(references); }
            @Override public String toString() { return "CreateCommand[redacted]"; }
        }
        record Remove(Ref.Existing entity) implements Entity { }
    }
    public sealed interface Parent {
        record Existing(String documentId,String sourceDigest,String elementIndex) implements Parent { }
        record Fresh(Ref.Fresh entity) implements Parent { }
    }
    public record Placement(Ref entity,String documentId,String projectionId,Parent parent) { }
    public record Containment(String relationId,Ref parent,Ref child) { }
    public record Change(Entity decision,List<Placement> placements) {
        public Change { placements=bounded(placements,20_000); }
        @Override public String toString() { return "EntityChange[redacted]"; }
    }
    public record Draft(List<Entity> entities,List<Containment> containment,List<Placement> placements) {
        public Draft { entities=bounded(entities,20_000); containment=bounded(containment,20_000); placements=bounded(placements,20_000); }
        @Override public String toString() { return "CommandDraft[redacted]"; }
    }
    public sealed interface ProfileDecision {
        String slotId();
        record Create(String slotId,String targetSlotId) implements ProfileDecision { }
        record UseExisting(String slotId,Ref target) implements ProfileDecision { }
        record Cancel(String slotId) implements ProfileDecision { }
    }
    public sealed interface Action {
        record Replace(Draft draft) implements Action { }
        record Batch(List<Change> changes,List<Containment> containment) implements Action {
            public Batch { changes=bounded(changes,20_000); containment=bounded(containment,20_000); if(changes.isEmpty()) invalid(); }
        }
        record Upsert(Change change) implements Action { }
        record Forget(Ref entity) implements Action { }
        record BindField(Ref entity,String fieldId,FieldValue state) implements Action { @Override public String toString() { return "BindField[redacted]"; } }
        record BindReference(Ref entity,String relationId,ReferenceState state) implements Action { }
        record Move(Containment decision,List<Placement> placements) implements Action { public Move { placements=bounded(placements,20_000); } }
        record Compose(NativeCommand.Reference profile,String previewDigest,List<String> selectedRoots,List<ProfileDecision> decisions) implements Action {
            public Compose { selectedRoots=bounded(selectedRoots,20_000); decisions=bounded(decisions,20_000); if(selectedRoots.isEmpty()) invalid(); }
        }
        record Discard() implements Action { }
    }
    PlanPorts.Draft apply(PlanPorts.Draft original,java.util.function.Function<Ref,TargetIntent.Ref> resolve) {
        var entities=new LinkedHashMap<TargetIntent.Ref,TargetIntent.EntityDecision>();
        original.intent().entities().forEach(entity->entities.put(entity.entity(),entity));
        var containment=new ArrayList<>(original.intent().containment());
        var placements=new ArrayList<>(original.placements());
        switch(action) {
            case Action.Replace replacement -> {
                entities.clear(); containment.clear(); placements.clear();
                for(var entity:replacement.draft().entities()) {
                    var decoded=entity(entity,resolve);
                    if(entities.putIfAbsent(decoded.entity(),decoded)!=null) invalid();
                }
                replaceContainment(containment,replacement.draft().containment(),resolve);
                placements.addAll(placements(replacement.draft().placements(),resolve));
            }
            case Action.Batch batch -> applyChanges(entities,containment,placements,batch.changes(),batch.containment(),resolve);
            case Action.Upsert upsert -> applyChanges(entities,containment,placements,List.of(upsert.change()),List.of(),resolve);
            case Action.Forget forget -> {
                var ref=resolve.apply(forget.entity()); entities.remove(ref); placements.removeIf(p->p.entity().equals(ref));
            }
            case Action.BindField bind -> {
                var ref=resolve.apply(bind.entity()); var existing=entities.get(ref);
                if(existing==null || existing instanceof TargetIntent.EntityDecision.Remove) invalid();
                var fields=new TreeMap<>(fields(existing)); if(!fields.containsKey(bind.fieldId())) invalid();
                fields.put(bind.fieldId(),bind.state()); entities.put(ref,with(existing,fields,references(existing)));
            }
            case Action.BindReference bind -> {
                var ref=resolve.apply(bind.entity()); var existing=entities.get(ref);
                if(existing==null || existing instanceof TargetIntent.EntityDecision.Remove) invalid();
                var references=new TreeMap<>(references(existing)); if(!references.containsKey(bind.relationId())) invalid();
                references.put(bind.relationId(),reference(bind.state(),resolve)); entities.put(ref,with(existing,fields(existing),references));
            }
            case Action.Move move -> {
                var child=resolve.apply(move.decision().child());
                var replacement=placements(move.placements(),resolve);
                if(replacement.stream().anyMatch(p->!p.entity().equals(child))) invalid();
                placements.removeIf(p->p.entity().equals(child)); placements.addAll(replacement);
                replaceContainment(containment,List.of(move.decision()),resolve);
            }
            default -> invalid();
        }
        return new PlanPorts.Draft(new TargetIntent(List.copyOf(entities.values()),containment),placements);
    }
    private static void applyChanges(Map<TargetIntent.Ref,TargetIntent.EntityDecision> entities,List<TargetIntent.Containment> containment,List<PlanPorts.Placement> placements,
            List<Change> changes,List<Containment> edges,java.util.function.Function<Ref,TargetIntent.Ref> resolve) {
        var changed=new HashSet<TargetIntent.Ref>(); var replacement=new ArrayList<PlanPorts.Placement>();
        for(var change:changes) {
            var entity=entity(change.decision(),resolve);
            if(!changed.add(entity.entity())) invalid();
            var assigned=placements(change.placements(),resolve);
            if(assigned.stream().anyMatch(p->!p.entity().equals(entity.entity()))) invalid();
            entities.put(entity.entity(),entity); replacement.addAll(assigned);
        }
        placements.removeIf(p->changed.contains(p.entity())); placements.addAll(replacement);
        replaceContainment(containment,edges,resolve);
    }
    private record ContainmentKey(String relation,TargetIntent.Ref child) { }
    private record PlacementKey(TargetIntent.Ref entity,String document,String projection) { }
    private static void replaceContainment(List<TargetIntent.Containment> existing,List<Containment> replacements,java.util.function.Function<Ref,TargetIntent.Ref> resolve) {
        var seen=new HashSet<ContainmentKey>(); var edges=new ArrayList<TargetIntent.Containment>();
        for(var relation:replacements) {
            var child=resolve.apply(relation.child());
            if(!seen.add(new ContainmentKey(relation.relationId(),child))) invalid();
            edges.add(new TargetIntent.Containment(relation.relationId(),resolve.apply(relation.parent()),child));
        }
        existing.removeIf(edge->seen.contains(new ContainmentKey(edge.relation(),edge.child()))); existing.addAll(edges);
    }
    private static List<PlanPorts.Placement> placements(List<Placement> values,java.util.function.Function<Ref,TargetIntent.Ref> resolve) {
        var result=new ArrayList<PlanPorts.Placement>(); var keys=new HashSet<PlacementKey>();
        for(var placement:values) {
            var ref=resolve.apply(placement.entity());
            if(!keys.add(new PlacementKey(ref,placement.documentId(),placement.projectionId()))) invalid();
            PlanPorts.Parent parent=switch(placement.parent()) {
                case Parent.Existing p -> new PlanPorts.Parent.Existing(p.documentId(),p.sourceDigest(),Integer.parseInt(p.elementIndex()));
                case Parent.Fresh p -> new PlanPorts.Parent.Created((TargetIntent.Ref.Fresh)resolve.apply(p.entity()));
            };
            result.add(new PlanPorts.Placement(ref,placement.documentId(),placement.projectionId(),parent));
        }
        return result;
    }
    private static TargetIntent.EntityDecision entity(Entity entity,java.util.function.Function<Ref,TargetIntent.Ref> resolve) {
        return switch(entity) {
            case Entity.Retain e -> new TargetIntent.EntityDecision.Retain((TargetIntent.Ref.Existing)resolve.apply(e.entity()),e.fields(),references(e.references(),resolve));
            case Entity.Create e -> new TargetIntent.EntityDecision.Create((TargetIntent.Ref.Fresh)resolve.apply(e.entity()),e.fields(),references(e.references(),resolve));
            case Entity.Remove e -> new TargetIntent.EntityDecision.Remove((TargetIntent.Ref.Existing)resolve.apply(e.entity()));
        };
    }
    private static Map<String,TargetIntent.ReferenceValue> references(Map<String,ReferenceState> states,java.util.function.Function<Ref,TargetIntent.Ref> resolve) {
        var result=new TreeMap<String,TargetIntent.ReferenceValue>(); states.forEach((id,state)->result.put(id,reference(state,resolve))); return result;
    }
    private static TargetIntent.ReferenceValue reference(ReferenceState state,java.util.function.Function<Ref,TargetIntent.Ref> resolve) {
        return switch(state) {
            case ReferenceState.To to -> new TargetIntent.ReferenceValue.To(resolve.apply(to.target()));
            case ReferenceState.KeepObserved ignored -> new TargetIntent.ReferenceValue.KeepObserved();
            case ReferenceState.Absent ignored -> new TargetIntent.ReferenceValue.ExplicitlyAbsent();
            case ReferenceState.Unresolved ignored -> new TargetIntent.ReferenceValue.Unresolved();
        };
    }
    private static Map<String,FieldValue> fields(TargetIntent.EntityDecision entity) {
        return entity instanceof TargetIntent.EntityDecision.Retain e?e.fields():entity instanceof TargetIntent.EntityDecision.Create e?e.fields():Map.of();
    }
    private static Map<String,TargetIntent.ReferenceValue> references(TargetIntent.EntityDecision entity) {
        return entity instanceof TargetIntent.EntityDecision.Retain e?e.references():entity instanceof TargetIntent.EntityDecision.Create e?e.references():Map.of();
    }
    private static TargetIntent.EntityDecision with(TargetIntent.EntityDecision entity,Map<String,FieldValue> fields,Map<String,TargetIntent.ReferenceValue> references) {
        return entity instanceof TargetIntent.EntityDecision.Retain e?new TargetIntent.EntityDecision.Retain(e.entity(),fields,references):new TargetIntent.EntityDecision.Create(((TargetIntent.EntityDecision.Create)entity).entity(),fields,references);
    }
    Object encoding() {
        var result=new TreeMap<String,Object>();
        result.put("expectedRevision",mutation.expectedRevision()); result.put("requestId",mutation.requestId());
        result.putAll(actionEncoding(action)); return result;
    }
    private static Map<String,Object> actionEncoding(Action action) {
        return switch(action) {
            case Action.Replace a -> Map.of("kind","replace-draft","draft",encode(a.draft()));
            case Action.Batch a -> Map.of("kind","batch-upsert","changes",encode(a.changes()),"containment",encode(a.containment()));
            case Action.Upsert a -> Map.of("kind","upsert-entity","decision",encode(a.change().decision()),"placements",encode(a.change().placements()));
            case Action.Forget a -> Map.of("kind","forget-entity-decision","entity",encode(a.entity()));
            case Action.BindField a -> Map.of("kind","bind-field","entity",encode(a.entity()),"fieldId",a.fieldId(),"state",encode(a.state()));
            case Action.BindReference a -> Map.of("kind","bind-reference","entity",encode(a.entity()),"relationId",a.relationId(),"state",encode(a.state()));
            case Action.Move a -> Map.of("kind","move-containment","decision",encode(a.decision()),"placements",encode(a.placements()));
            case Action.Compose a -> Map.of("kind","compose-profile","profile",Map.of("objectId",a.profile().objectId(),"workspaceRevision",a.profile().workspaceRevision()),"previewDigest",a.previewDigest(),"selectedRoots",a.selectedRoots(),"decisions",encode(a.decisions()));
            case Action.Discard ignored -> Map.of("kind","discard");
        };
    }
    private static Object encode(Object value) {
        return switch(value) {
            case String text -> text;
            case List<?> list -> list.stream().map(PlanCommand::encode).toList();
            case Map<?,?> map -> { var result=new TreeMap<String,Object>(); map.forEach((key,item)->result.put((String)key,encode(item))); yield result; }
            case Ref.Existing r -> Map.of("kind","existing","handle",r.handle());
            case Ref.Fresh r -> Map.of("kind","fresh","slotId",r.slotId(),"typeId",r.typeId());
            case Entity.Retain e -> Map.of("kind","retain","entity",encode(e.entity()),"fields",encode(e.fields()),"references",encode(e.references()));
            case Entity.Create e -> Map.of("kind","create","entity",encode(e.entity()),"fields",encode(e.fields()),"references",encode(e.references()));
            case Entity.Remove e -> Map.of("kind","remove","entity",encode(e.entity()));
            case FieldValue.Entered field -> Map.of("kind","entered","text",field.text());
            case FieldValue.KeepObserved ignored -> Map.of("kind","keep-observed");
            case FieldValue.Unresolved ignored -> Map.of("kind","unresolved");
            case FieldValue.ExplicitlyAbsent ignored -> Map.of("kind","absent");
            case ReferenceState.To ref -> Map.of("kind","to","target",encode(ref.target()));
            case ReferenceState.KeepObserved ignored -> Map.of("kind","keep-observed");
            case ReferenceState.Unresolved ignored -> Map.of("kind","unresolved");
            case ReferenceState.Absent ignored -> Map.of("kind","absent");
            case Parent.Existing parent -> Map.of("kind","existing","documentId",parent.documentId(),"sourceDigest",parent.sourceDigest(),"elementIndex",parent.elementIndex());
            case Parent.Fresh parent -> Map.of("kind","fresh","entity",encode(parent.entity()));
            case Placement p -> Map.of("entity",encode(p.entity()),"documentId",p.documentId(),"projectionId",p.projectionId(),"parent",encode(p.parent()));
            case Containment c -> Map.of("relationId",c.relationId(),"parent",encode(c.parent()),"child",encode(c.child()));
            case Change c -> Map.of("decision",encode(c.decision()),"placements",encode(c.placements()));
            case Draft d -> Map.of("entities",encode(d.entities()),"containment",encode(d.containment()),"placements",encode(d.placements()));
            case ProfileDecision.Create d -> Map.of("kind","create","slotId",d.slotId(),"targetSlotId",d.targetSlotId());
            case ProfileDecision.UseExisting d -> Map.of("kind","use-existing","slotId",d.slotId(),"target",encode(d.target()));
            case ProfileDecision.Cancel d -> Map.of("kind","cancel","slotId",d.slotId());
            default -> throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST);
        };
    }
    private static <T> List<T> bounded(List<T> values,int max) { if(values.size()>max) invalid(); return List.copyOf(values); }
    private static <T> Map<String,T> boundedMap(Map<String,T> values) { if(values.size()>256) invalid(); return Collections.unmodifiableMap(new TreeMap<>(values)); }
    private static void invalid() { throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST); }
}
