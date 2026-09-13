package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.profile.ProfileComposer;
import static studio.environment.core.plan.PlanPorts.*;

class PlanCompositionTest {
    @Test void reuseOfPreviouslyCreatedEntityPreservesFreshProvenanceAndEnteredCase() {
        var original=new TargetIntent.Ref.Fresh("invented-slot","invented-kind");
        var key=new ObservedGraph.Key("invented-kind","MiXeD-Identity");
        var retained=new TargetIntent.EntityDecision.Create(original,Map.of("label",new TargetIntent.FieldValue.Entered("Canary-MiXeD")),Map.of());
        var existing=new Draft(new TargetIntent(List.of(retained),List.of()),List.of());
        var target=new Content(List.of(),new ObservedGraph(List.of(),List.of()),Map.of(key,original));
        var proposal=new ProfileComposer.Draft(List.of(),List.of(key),List.of(),List.of(),List.of(new ProfileComposer.UnresolvedFields(new ProfileComposer.Target.Existing(key),List.of("label"))));
        var merged=PlanComposition.merge(new HostedPlanServiceTest().definition.compiled(),existing,target,proposal);
        assertEquals(existing,merged);
        assertInstanceOf(TargetIntent.EntityDecision.Create.class,merged.intent().entities().getFirst());
        assertFalse(merged.toString().contains("Canary"));
    }
    @Test void repeatedProfileCompositionCannotReuseAnExistingFreshSlotForNewCreation() {
        var fresh=new TargetIntent.Ref.Fresh("same-slot","invented-kind");
        var existing=new Draft(new TargetIntent(List.of(new TargetIntent.EntityDecision.Create(fresh,Map.of(),Map.of())),List.of()),List.of());
        var proposal=new ProfileComposer.Draft(List.of(new ProfileComposer.Target.New("same-slot","invented-kind")),List.of(),List.of(),List.of(),List.of());
        assertEquals(PlanRefusal.Code.PROFILE_REFUSED,assertThrows(PlanRefusal.class,()->PlanComposition.merge(new HostedPlanServiceTest().definition.compiled(),existing,new Content(List.of(),new ObservedGraph(List.of(),List.of()),Map.of()),proposal)).code());
    }
    @Test void containmentReuseSeedsAnExplicitCompleteOriginalChildDisposition() {
        var field=new studio.environment.core.definitionv2.NativeDefinition.Field("id",studio.environment.core.definition.DefinitionDraft.ValueType.TEXT,true,studio.environment.core.definition.DefinitionDraft.Classification.STRUCTURAL,studio.environment.core.definition.DefinitionDraft.Sensitivity.PUBLIC,true,true);
        var type=new studio.environment.core.definitionv2.NativeDefinition.EntityType("node","Invented node",List.of(field),new studio.environment.core.definitionv2.NativeDefinition.Identity("id"));
        var contains=new studio.environment.core.definition.DefinitionDraft.Relation("contains","node","node",studio.environment.core.definition.DefinitionDraft.RelationKind.CONTAINMENT,java.math.BigInteger.ZERO,java.math.BigInteger.TEN,false);
        var points=new studio.environment.core.definition.DefinitionDraft.Relation("points","node","node",studio.environment.core.definition.DefinitionDraft.RelationKind.REFERENCE,java.math.BigInteger.ZERO,java.math.BigInteger.ONE,false);
        var definition=new studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish(new studio.environment.core.definitionv2.NativeCompilationResult.Checked(
                new studio.environment.core.definitionv2.NativeDefinition("invented",java.math.BigInteger.ONE,new studio.environment.core.definitionv2.NativeDefinition.Logical(List.of(type),List.of(contains,points),List.of(),List.of(studio.environment.core.definitionv2.NativeDefinition.Operation.values())),List.of()),"logical",Map.of(),Map.of()));
        var parent=new ObservedGraph.Key("node","parent"); var child=new ObservedGraph.Key("node","child");
        var graph=new ObservedGraph(List.of(
                new ObservedGraph.Entity(parent,Map.of("id","parent"),new ObservedGraph.Origin("document","nodes","digest",1,List.of(0))),
                new ObservedGraph.Entity(child,Map.of("id","child"),new ObservedGraph.Origin("document","nodes","digest",2,List.of(0,1)))),
                List.of());
        var target=new Content(List.of(),graph,Map.of(parent,new TargetIntent.Ref.Existing(parent),child,new TargetIntent.Ref.Existing(child)));
        var proposal=new ProfileComposer.Draft(List.of(),List.of(parent,child),graph.edges(),List.of(new ProfileComposer.RelationProposal("contains",new ProfileComposer.Target.Existing(parent),new ProfileComposer.Target.Existing(child))),List.of(
                new ProfileComposer.UnresolvedFields(new ProfileComposer.Target.Existing(parent),List.of("id")),
                new ProfileComposer.UnresolvedFields(new ProfileComposer.Target.Existing(child),List.of("id"))));
        var noOpTarget=new Content(List.of(),new ObservedGraph(graph.entities(),List.of(new ObservedGraph.Edge("contains",parent,child))),target.provenance());
        var noOp=PlanComposition.merge(definition,Draft.empty(),noOpTarget,proposal);
        assertTrue(noOp.intent().containment().isEmpty(),"An already present containment edge is not a physical move");
        assertEquals(2,noOp.intent().entities().size());
        var relationFree=new ProfileComposer.Draft(List.of(),List.of(child),List.of(),List.of(),List.of(new ProfileComposer.UnresolvedFields(new ProfileComposer.Target.Existing(child),List.of("id"))));
        assertEquals(1,PlanComposition.merge(definition,Draft.empty(),target,relationFree).intent().entities().size());
        var merged=PlanComposition.merge(definition,Draft.empty(),target,proposal);
        var decision=assertInstanceOf(TargetIntent.EntityDecision.Retain.class,merged.intent().entities().getLast());
        assertEquals(new TargetIntent.Ref.Existing(child),decision.entity());
        assertEquals(Map.of("id",new TargetIntent.FieldValue.Unresolved()),decision.fields());
        assertEquals(Map.of("points",new TargetIntent.ReferenceValue.Unresolved()),decision.references());
    }

}
