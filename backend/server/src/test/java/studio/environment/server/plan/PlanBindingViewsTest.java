package studio.environment.server.plan;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import static studio.environment.server.plan.PlanBindingLocationsTest.*;
class PlanBindingViewsTest {
    final PlanBindingLocationsTest fixture=new PlanBindingLocationsTest();
    static final PlanCommand.Ref.Existing REF=new PlanCommand.Ref.Existing(PALETTE_HANDLE);
    static List<?> items(Map<String,Object> page){return (List<?>)page.get("items");}
    static void shape(String schema,Map<String,Object> result){PlanViewWireAssertions.verify(schema,200,tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(result));}
    @Test void identityBindingPageCarriesConcreteChangesAndCompleteCounts() throws Exception {
        var view=fixture.snapshot(rename());var first=PlanBindingViews.bindings(view,REF,0,1);shape("bindingsResponse",first);assertEquals(2,first.get("total"));assertEquals(1,first.get("nextOffset"));assertEquals("shade",((Map<?,?>)items(first).getFirst()).get("fieldId"));
        var page=PlanBindingViews.bindings(view,REF,1,1);shape("bindingsResponse",page);assertNull(page.get("nextOffset"));var item=(Map<?,?>)items(page).getFirst();
        assertEquals("[[value:"+PALETTE_HANDLE+":tag]]",item.get("token"));assertEquals(Map.of("state","value","text","shared"),item.get("current"));assertEquals(Map.of("state","value","text","renamed"),item.get("target"));assertEquals("changed",item.get("change"));assertEquals(Map.of("state","complete","total",3),item.get("currentLocations"));assertEquals(Map.of("state","complete","total",3),item.get("targetLocations"));
    }
    @Test void everyLocationIsPageableIncludingMaximumLegalOffsetWithoutOverflow() throws Exception {
        var view=fixture.snapshot(rename());var visited=new ArrayList<>();
        for(int i=0;i<3;i++){var page=PlanBindingViews.locations(view,REF,"tag",false,i,1,true);shape("bindingLocationsResponse",page);assertEquals(3,page.get("total"));assertEquals(i<2?i+1:null,page.get("nextOffset"));visited.add(items(page).getFirst());}
        assertEquals(3,new HashSet<>(visited).size());
        var beyond=PlanBindingViews.locations(view,REF,"tag",false,Integer.MAX_VALUE,100,true);shape("bindingLocationsResponse",beyond);assertEquals(3,beyond.get("total"));assertEquals(List.of(),items(beyond));assertNull(beyond.get("nextOffset"));
        assertThrows(PlanRefusal.class,()->PlanBindingViews.locations(view,REF,"missing",false,0,100,true));
    }
    @Test void resolvedDraftValueNeverClaimsTargetLocations() throws Exception {
        var complete=fixture.snapshot(rename());var view=new HostedPlanService.ViewSnapshot(complete.revision(),complete.definition(),complete.binding(),complete.current(),Optional.empty(),complete.draft(),complete.references(),complete.displayHandles());
        var page=PlanBindingViews.bindings(view,REF,0,100);shape("bindingsResponse",page);var item=(Map<?,?>)items(page).get(1);assertEquals(Map.of("state","value","text","renamed"),item.get("target"));assertEquals(Map.of("state","unavailable","code","INCOMPLETE_TARGET"),item.get("targetLocations"));
        assertEquals(PlanRefusal.Code.INCOMPLETE_TARGET,assertThrows(PlanRefusal.class,()->PlanBindingViews.locations(view,REF,"tag",true,0,100,true)).code());
    }
    @Test void freshIncompleteDraftHasValuesWithoutClaimingEitherLocationSide() throws Exception {
        var base=fixture.snapshot(Draft.empty());
        var create=new studio.environment.core.planning.TargetIntent.EntityDecision.Create(FRESH,Map.of("tag",new studio.environment.core.planning.TargetIntent.FieldValue.Entered("new"),"shade",new studio.environment.core.planning.TargetIntent.FieldValue.Unresolved()),Map.of());
        var draft=new Draft(new studio.environment.core.planning.TargetIntent(List.of(create),List.of()),List.of());var handles=new HashMap<>(base.displayHandles());handles.put(FRESH,FRESH_HANDLE);
        var view=new HostedPlanService.ViewSnapshot(base.revision(),base.definition(),base.binding(),base.current(),Optional.empty(),draft,base.references(),handles);
        var ref=new PlanCommand.Ref.Fresh("fresh-palette","palette");var page=PlanBindingViews.bindings(view,ref,0,100);shape("bindingsResponse",page);
        var tag=(Map<?,?>)items(page).get(1);assertEquals(Map.of("state","unavailable"),tag.get("current"));assertEquals(Map.of("state","value","text","new"),tag.get("target"));assertEquals("added",tag.get("change"));assertEquals(Map.of("state","unavailable","code","CURRENT_ENTITY_ABSENT"),tag.get("currentLocations"));assertEquals(Map.of("state","unavailable","code","INCOMPLETE_TARGET"),tag.get("targetLocations"));
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->PlanBindingViews.locations(view,ref,"tag",false,0,100,true)).code());assertEquals(PlanRefusal.Code.INCOMPLETE_TARGET,assertThrows(PlanRefusal.class,()->PlanBindingViews.locations(view,ref,"tag",true,0,100,true)).code());
    }
    @Test void removedExistingEntityHasCompleteZeroTargetLocations() throws Exception {
        var draft=new Draft(new studio.environment.core.planning.TargetIntent(List.of(new studio.environment.core.planning.TargetIntent.EntityDecision.Remove(ALPHA)),List.of()),List.of());var view=fixture.snapshot(draft);
        var ref=new PlanCommand.Ref.Existing("00000000-0000-4000-8000-000000000011");var bindings=PlanBindingViews.bindings(view,ref,0,100);shape("bindingsResponse",bindings);
        for(var item:items(bindings)){var field=(Map<?,?>)item;assertEquals(Map.of("state","absent"),field.get("target"));assertEquals(Map.of("state","complete","total",0),field.get("targetLocations"));assertEquals("removed",field.get("change"));}
        var page=PlanBindingViews.locations(view,ref,"tone",true,0,100,true);shape("bindingLocationsResponse",page);assertEquals(0,page.get("total"));assertEquals(List.of(),items(page));assertNull(page.get("nextOffset"));
    }

}
