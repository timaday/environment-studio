package studio.environment.core.plan;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.graph.ObservedGraph;
class PlanHandleForgetReachabilityReviewTest {
    @Test void actualForgetKeepsUnresolvedInboundIntentButRetiresDisplayHandle() {
        var original=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        var fresh=new TargetIntent.Ref.Fresh("forgotten-slot","glyph");
        var originalHandle=UUID.randomUUID().toString();
        var freshHandle=UUID.randomUUID().toString();
        var retain=new TargetIntent.EntityDecision.Retain(original,Map.of(),Map.of("uses",new TargetIntent.ReferenceValue.To(fresh)));
        var create=new TargetIntent.EntityDecision.Create(fresh,Map.of(),Map.of());
        var containment=new TargetIntent.Containment("mock-parent",fresh,original);
        var placement=new PlanPorts.Placement(original,"sheet","glyphs",new PlanPorts.Parent.Created(fresh));
        var before=new PlanPorts.Draft(new TargetIntent(List.of(retain,create),List.of(containment)),List.of(placement));
        var command=new PlanCommand(new HostedPlanService.Mutation("1",UUID.randomUUID().toString()),new PlanCommand.Action.Forget(new PlanCommand.Ref.Fresh(fresh.slot(),fresh.type())));
        var after=command.apply(before,ref -> ref instanceof PlanCommand.Ref.Fresh f ? new TargetIntent.Ref.Fresh(f.slotId(),f.typeId()) : original);
        assertEquals(List.of(retain),after.intent().entities());
        assertEquals(List.of(containment),after.intent().containment());
        assertEquals(List.of(placement),after.placements());
        var handles=PlanHandles.draft(Map.of(original,originalHandle,fresh,freshHandle),after);
        assertFalse(handles.containsKey(fresh));
        assertEquals(originalHandle,handles.get(original));
    }
}
