package studio.environment.core.plan;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;

class PlanHandlesTest {
    @Test void collisionRefusesWithoutChangingAnyPreviouslyAdmittedHandle(){
        var existing=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        var created=new TargetIntent.Ref.Fresh("new","glyph");
        var id=UUID.fromString("00000000-0000-4000-8000-000000000001");
        var previous=Map.<TargetIntent.Ref,String>of(existing,id.toString());
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->PlanHandles.allocate(previous,Set.of(existing,created),()->id)).code());
        assertEquals(Map.of(existing,id.toString()),previous);
        assertEquals(previous,PlanHandles.allocate(previous,Set.of(existing),()->{throw new AssertionError("RETAINED_HANDLE_REALLOCATED");}));
    }
    @Test void collisionsWithinANewBatchRefuseTheWholeAllocation(){
        var one=new TargetIntent.Ref.Fresh("one","glyph");var two=new TargetIntent.Ref.Fresh("two","glyph");
        var id=UUID.fromString("00000000-0000-4000-8000-000000000002");
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->PlanHandles.allocate(Map.of(),Set.of(one,two),()->id)).code());
    }
    @Test void fullCombinedHandleBudgetIsBoundedBeforeAnyAllocation(){
        var oversized=new HashSet<TargetIntent.Ref>();for(int i=0;i<40_001;i++)oversized.add(new TargetIntent.Ref.Fresh("slot-"+i,"glyph"));
        assertEquals(PlanRefusal.Code.RESOURCE_LIMIT,assertThrows(PlanRefusal.class,()->PlanHandles.allocate(Map.of(),oversized,()->{throw new AssertionError("ALLOCATED_BEFORE_LIMIT");})).code());
        var twentyThousand=new ArrayList<TargetIntent.EntityDecision>();for(int i=0;i<20_000;i++)twentyThousand.add(new TargetIntent.EntityDecision.Create(new TargetIntent.Ref.Fresh("slot-"+i,"glyph"),Map.of(),Map.of()));
        var handles=PlanHandles.draft(Map.of(),new PlanPorts.Draft(new TargetIntent(twentyThousand,List.of()),List.of()));
        assertEquals(20_000,handles.size());assertEquals(20_000,new HashSet<>(handles.values()).size());
        assertTrue(PlanHandles.draft(handles,PlanPorts.Draft.empty()).isEmpty(),"Removed creations must not accumulate history");
    }
    @Test void missingAndExtraObservedProvenanceCannotCreateDisplayAuthority(){
        var key=new ObservedGraph.Key("glyph","alpha");var ref=new TargetIntent.Ref.Existing(key);
        var entity=new ObservedGraph.Entity(key,Map.of("tag","alpha"),new ObservedGraph.Origin("sheet","glyphs","mock-source",1,List.of(0)));
        var missing=new PlanPorts.Content(List.of(),new ObservedGraph(List.of(entity),List.of()),Map.of());
        assertThrows(PlanRefusal.class,()->PlanHandles.observed(missing));
        var extra=new PlanPorts.Content(List.of(),new ObservedGraph(List.of(),List.of()),Map.of(key,ref));
        assertThrows(PlanRefusal.class,()->PlanHandles.observed(extra));
    }
}
