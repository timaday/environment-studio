package studio.environment.server.plan;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.plan.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.graph.ObservedGraph;
class PlanHandleDanglingReferenceReviewTest {
    @Test void danglingFreshReferenceRemainsInspectableWithoutAllocatingDisplayIdentity() throws Exception {
        var base=new PlanViewProjectionTest().snapshot();
        var original=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        var forgotten=new TargetIntent.Ref.Fresh("forgotten-slot","glyph");
        var decision=new TargetIntent.EntityDecision.Retain(original,Map.of("tag",new TargetIntent.FieldValue.KeepObserved()),Map.of("uses",new TargetIntent.ReferenceValue.To(forgotten)));
        var draft=new PlanPorts.Draft(new TargetIntent(List.of(decision),List.of()),List.of());
        var snapshot=new HostedPlanService.ViewSnapshot(base.revision(),base.definition(),base.binding(),base.current(),Optional.empty(),draft,base.references(),base.displayHandles());
        assertThrows(PlanRefusal.class,()->snapshot.displayHandle(forgotten));
        var page=assertDoesNotThrow(()->PlanViewProjection.draft(snapshot,0,100),"Unresolved draft reference must remain inspectable after its Fresh creation is forgotten");
        assertEquals(1,page.get("total"));
    }
    @Test void danglingContainmentRemainsInspectableWithoutAllocatingDisplayIdentity() throws Exception {
        var base=new PlanViewProjectionTest().snapshot();
        var original=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        var forgotten=new TargetIntent.Ref.Fresh("forgotten-slot","glyph");
        var draft=new PlanPorts.Draft(new TargetIntent(List.of(),List.of(new TargetIntent.Containment("mock-parent",forgotten,original))),List.of());
        var snapshot=new HostedPlanService.ViewSnapshot(base.revision(),base.definition(),base.binding(),base.current(),Optional.empty(),draft,base.references(),base.displayHandles());
        assertThrows(PlanRefusal.class,()->snapshot.displayHandle(forgotten));
        var page=assertDoesNotThrow(()->PlanViewProjection.containment(snapshot,0,100),"Unresolved containment must remain inspectable after its Fresh parent is forgotten");
        assertEquals(1,page.get("total"));
    }
    @Test void danglingCreatedParentPlacementRemainsInspectable() throws Exception {
        var base=new PlanViewProjectionTest().snapshot();
        var original=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        var forgotten=new TargetIntent.Ref.Fresh("forgotten-slot","glyph");
        var placement=new PlanPorts.Placement(original,"sheet","glyphs",new PlanPorts.Parent.Created(forgotten));
        assertThrows(PlanRefusal.class,()->base.displayHandle(forgotten));
        assertDoesNotThrow(()->PlanViewProjection.placement(base,placement),"An unresolved created-parent decision must remain inspectable");
    }
}
