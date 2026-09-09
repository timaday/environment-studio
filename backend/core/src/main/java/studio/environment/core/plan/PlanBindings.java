package studio.environment.core.plan;

import java.util.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.graph.ObservedGraph;
import static studio.environment.core.plan.PlanRefusal.Code.*;

/** Field intent and display identity only; XML locations belong to the qualified adapter. */
public final class PlanBindings {
    private PlanBindings() { }
    public enum State { VALUE, MASKED, ABSENT, UNRESOLVED, UNAVAILABLE }
    public enum Change { UNCHANGED, CHANGED, ADDED, REMOVED, UNRESOLVED }
    public record Value(State state,Optional<String> text) {
        public Value { Objects.requireNonNull(state);Objects.requireNonNull(text);if((state==State.VALUE)!=text.isPresent())throw new IllegalArgumentException("INVALID_BINDING_VALUE"); }
        @Override public String toString(){return "BindingValue[redacted]";}
    }
    public record Field(String fieldId,String token,Value current,Value target,Change change) {
        @Override public String toString(){return "BindingField[redacted]";}
    }
    public record Entity(TargetIntent.Ref reference,List<Field> fields) {
        public Entity {fields=List.copyOf(fields);}
        @Override public String toString(){return "BindingEntity[redacted]";}
    }
    public static Entity resolve(HostedPlanService.ViewSnapshot snapshot,PlanCommand.Ref requested) {
        var current=index(snapshot,snapshot.selected(false));
        TargetIntent.Ref ref=switch(requested) {
            case PlanCommand.Ref.Existing existing -> snapshot.references().entrySet().stream().filter(e->e.getValue().equals(existing)).map(Map.Entry::getKey).findFirst().orElseThrow(()->new PlanRefusal(NOT_FOUND));
            case PlanCommand.Ref.Fresh fresh -> new TargetIntent.Ref.Fresh(fresh.slotId(),fresh.typeId());
        };
        if(!snapshot.displayHandles().containsKey(ref))throw new PlanRefusal(NOT_FOUND);
        var type=snapshot.definition().compiled().checked().definition().logical().entityTypes().stream().filter(t->t.id().equals(ref.type())).findFirst().orElseThrow(()->new PlanRefusal(PROJECTION_REFUSED));
        var decisions=new HashMap<TargetIntent.Ref,TargetIntent.EntityDecision>();
        for(var decision:snapshot.draft().intent().entities())if(decisions.putIfAbsent(decision.entity(),decision)!=null)throw new PlanRefusal(PROJECTION_REFUSED);
        var decision=decisions.get(ref);boolean fresh=ref instanceof TargetIntent.Ref.Fresh;
        if(fresh && !(decision instanceof TargetIntent.EntityDecision.Create))throw new PlanRefusal(NOT_FOUND);
        var observed=current.get(ref);if(!fresh && observed==null)throw new PlanRefusal(PROJECTION_REFUSED);
        Map<String,TargetIntent.FieldValue> values=switch(decision) {
            case null -> Map.of();
            case TargetIntent.EntityDecision.Remove ignored -> Map.of();
            case TargetIntent.EntityDecision.Retain retained -> retained.fields();
            case TargetIntent.EntityDecision.Create created -> created.fields();
        };
        var declared=new HashSet<String>();type.fields().forEach(f->declared.add(f.id()));
        if(!declared.containsAll(values.keySet()) || fresh && values.values().stream().anyMatch(v->v instanceof TargetIntent.FieldValue.KeepObserved))throw new PlanRefusal(PROJECTION_REFUSED);
        boolean complete=snapshot.target().isPresent();
        var target=complete?index(snapshot,snapshot.target().orElseThrow()).get(ref):null;
        if(complete && fresh && target==null)throw new PlanRefusal(PROJECTION_REFUSED);
        var fields=new ArrayList<Field>();
        for(var field:type.fields().stream().sorted(Comparator.comparing(NativeDefinition.Field::id)).toList()) {
            var before=fresh?state(State.UNAVAILABLE):observed(field,observed);
            Value after;
            if(complete)after=observed(field,target);
            else if(decision instanceof TargetIntent.EntityDecision.Remove)after=state(State.ABSENT);
            else if(decision==null)after=before;
            else after=switch(values.get(field.id())) {
                case null -> state(State.UNRESOLVED);
                case TargetIntent.FieldValue.Unresolved ignored -> state(State.UNRESOLVED);
                case TargetIntent.FieldValue.ExplicitlyAbsent ignored -> state(State.ABSENT);
                case TargetIntent.FieldValue.Entered entered -> present(field,entered.text());
                case TargetIntent.FieldValue.KeepObserved ignored -> before;
            };
            fields.add(new Field(field.id(),token(snapshot,ref,field.id()),before,after,change(before,after,fresh)));
        }
        return new Entity(ref,fields);
    }
    public static String token(HostedPlanService.ViewSnapshot snapshot,TargetIntent.Ref ref,String fieldId) {
        var handle=snapshot.displayHandle(ref);
        if(!handle.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") || !fieldId.matches("[a-z][a-z0-9.-]{0,63}"))throw new PlanRefusal(PROJECTION_REFUSED);
        return "[[value:"+handle+":"+fieldId+"]]";
    }
    private static Map<TargetIntent.Ref,ObservedGraph.Entity> index(HostedPlanService.ViewSnapshot snapshot,PlanPorts.Content content) {
        var result=new HashMap<TargetIntent.Ref,ObservedGraph.Entity>();var keys=new HashSet<ObservedGraph.Key>();
        for(var entity:content.graph().entities()) {
            var provenance=content.provenance().get(entity.key());
            if(provenance==null || !provenance.type().equals(entity.key().type()) || !keys.add(entity.key()) || result.putIfAbsent(provenance,entity)!=null)throw new PlanRefusal(PROJECTION_REFUSED);
            snapshot.displayHandle(provenance);
        }
        if(keys.size()!=content.provenance().size())throw new PlanRefusal(PROJECTION_REFUSED);
        return result;
    }
    private static Value observed(NativeDefinition.Field field,ObservedGraph.Entity entity) {
        return entity==null || !entity.fields().containsKey(field.id())?state(State.ABSENT):present(field,entity.fields().get(field.id()));
    }
    private static Value present(NativeDefinition.Field field,String text) {
        return !field.readable() || field.sensitivity()==Sensitivity.SECRET || field.sensitivity()==Sensitivity.UNKNOWN?state(State.MASKED):new Value(State.VALUE,Optional.of(text));
    }
    private static Value state(State state){return new Value(state,Optional.empty());}
    private static Change change(Value before,Value after,boolean fresh) {
        var from=fresh && before.state()==State.UNAVAILABLE?State.ABSENT:before.state();var to=after.state();
        if(from==State.UNAVAILABLE || to==State.UNAVAILABLE || from==State.UNRESOLVED || to==State.UNRESOLVED)return Change.UNRESOLVED;
        if(from==State.ABSENT)return to==State.ABSENT?Change.UNCHANGED:Change.ADDED;
        if(to==State.ABSENT)return Change.REMOVED;
        if(from==State.MASKED || to==State.MASKED)return Change.UNRESOLVED;
        return before.text().equals(after.text())?Change.UNCHANGED:Change.CHANGED;
    }
}
