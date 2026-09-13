package studio.environment.server.plan;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.profile.*;
import studio.environment.core.workspace.NativeCommand;
class PlanViewPreviewTest {
    @Test void aPossiblyCappedConflictListCannotMasqueradeAsACompleteTotal(){
        var conflicts=new ArrayList<ProfileComposer.Conflict>();for(int i=0;i<256;i++)conflicts.add(new ProfileComposer.Conflict("RELATION_CARDINALITY","slot-"+i,"uses"));
        var preview=preview(conflicts);assertEquals(PlanRefusal.Code.RESOURCE_LIMIT,assertThrows(PlanRefusal.class,()->PlanViewResults.preview(preview,PlanViewRequest.Section.CONFLICTS,0,100)).code());
        var page=PlanViewResults.preview(preview(conflicts.subList(0,255)),PlanViewRequest.Section.CONFLICTS,100,100);
        assertEquals(255,page.get("total"));assertEquals(200,page.get("nextOffset"));assertEquals(100,((List<?>)page.get("items")).size());
    }
    @Test void conflictCoordinatesAreExplicitAndUnknownCodesRefuse(){
        var page=PlanViewResults.preview(preview(List.of(new ProfileComposer.Conflict("ENTITY_COUNT","","count-rule"))),PlanViewRequest.Section.CONFLICTS,0,100);
        var item=(Map<?,?>)((List<?>)page.get("items")).getFirst();assertNull(item.get("slotId"));assertNull(item.get("relationId"));assertEquals("count-rule",item.get("ruleId"));
        assertThrows(PlanRefusal.class,()->PlanViewResults.preview(preview(List.of(new ProfileComposer.Conflict("FUTURE_CODE","",""))),PlanViewRequest.Section.CONFLICTS,0,100));
    }
    private HostedPlanService.CompositionPreview preview(List<ProfileComposer.Conflict> conflicts){
        var dependencies=new ProfileComposer.Preview("neutral",java.math.BigInteger.ONE,"a".repeat(64),"b".repeat(64),List.of("one"),List.of(),List.of(),List.of(),conflicts);
        return new HostedPlanService.CompositionPreview("00000000-0000-4000-8000-000000000001","2","c".repeat(64),new NativeCommand.Reference("00000000-0000-4000-8000-000000000002","1"),"d".repeat(64),List.of("one"),"e".repeat(64),"f".repeat(64),dependencies);
    }
}
