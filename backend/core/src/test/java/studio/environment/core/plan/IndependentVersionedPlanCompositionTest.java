package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanPorts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.profile.*;

class IndependentVersionedPlanCompositionTest {
    @Test void freshReusingOriginalLiteralReceivesOnlyItsSelectedReferenceChange() {
        var definition = VersionedPlanCompositionTest.definition();
        var profile = VersionedPlanCompositionTest.profile(definition);
        var oldKey = VersionedPlanCompositionTest.FIRST;
        var hub = VersionedPlanCompositionTest.HUB;
        var renamed = new ObservedGraph.Key("unit", "renamed-original");
        var newHub = new ObservedGraph.Key("hub", "other-hub");
        var original = new TargetIntent.Ref.Existing(oldKey);
        var fresh = new TargetIntent.Ref.Fresh("earlier-fresh", "unit");
        var originalDecision = new TargetIntent.EntityDecision.Retain(original,
                Map.of("id", new TargetIntent.FieldValue.Entered(renamed.identity()),
                        "tone", new TargetIntent.FieldValue.KeepObserved()),
                Map.of("links", new TargetIntent.ReferenceValue.To(new TargetIntent.Ref.Existing(hub))));
        var freshDecision = new TargetIntent.EntityDecision.Create(fresh,
                Map.of("id", new TargetIntent.FieldValue.Entered(oldKey.identity()),
                        "tone", new TargetIntent.FieldValue.Entered("independent-entered-value")),
                Map.of("links", new TargetIntent.ReferenceValue.To(new TargetIntent.Ref.Existing(hub))));
        var placement = new Placement(fresh,"sheet","units",new Parent.Existing("sheet","a".repeat(64),0));
        var prior = new Draft(new TargetIntent(List.of(originalDecision,freshDecision),List.of()),List.of(placement));
        var graph = new ObservedGraph(List.of(
                entity(renamed,1),entity(oldKey,2),entity(hub,3),entity(newHub,4)),List.of(
                new ObservedGraph.Edge("links",renamed,hub),new ObservedGraph.Edge("links",oldKey,hub)));
        var content = new Content(List.of(),graph,Map.of(renamed,original,oldKey,fresh,
                hub,new TargetIntent.Ref.Existing(hub),newHub,new TargetIntent.Ref.Existing(newHub)));
        var composer = new V3ProfileComposer();
        var preview = assertInstanceOf(V3ProfileComposer.PreviewResult.Proposed.class,
                composer.preview(definition,profile,Set.of("first"))).preview();
        var prepared = assertInstanceOf(ProfileComposer.CompositionResult.NeedsResolution.class,
                composer.compose(definition,profile,preview,graph,List.of(
                        new ProfileComposer.Decision.UseExisting("first",oldKey),
                        new ProfileComposer.Decision.UseExisting("dependency",newHub))));
        assertEquals(List.of(new ProfileComposer.Conflict("RELATION_CARDINALITY","first","links")), prepared.conflicts());
        var proposal = prepared.draft();
        assertTrue(proposal.additions().isEmpty());
        var result = PlanComposition.merge(new PlanDefinition.V3(definition),prior,content,proposal);
        var indexed = new HashMap<TargetIntent.Ref,TargetIntent.EntityDecision>();
        result.intent().entities().forEach(e -> assertNull(indexed.put(e.entity(),e)));
        assertEquals(originalDecision,indexed.get(original));
        assertEquals(new TargetIntent.EntityDecision.Create(fresh,freshDecision.fields(),Map.of(
                "links",new TargetIntent.ReferenceValue.To(new TargetIntent.Ref.Existing(newHub)))),indexed.get(fresh));
        assertEquals(Set.of(original,fresh,new TargetIntent.Ref.Existing(newHub)),indexed.keySet());
        assertEquals(List.of(placement),result.placements());
        assertTrue(result.intent().containment().isEmpty());
    }
    private static ObservedGraph.Entity entity(ObservedGraph.Key key,int index) {
        return new ObservedGraph.Entity(key,Map.of("id",key.identity(),"tone","invented-current-tone"),
                new ObservedGraph.Origin("sheet",key.type()+"s","a".repeat(64),index,List.of(0)));
    }
}
