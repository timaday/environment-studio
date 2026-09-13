package studio.environment.server.plan;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.definition.*;

/** Independently invented mock: raw offsets also expose lengths of earlier secrets. */
class PlanBindingDisclosureTest {
    @Test void allRawCoordinatesRequireDisclosureWhileBindingValuesStayMasked() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var declaration=json.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")));
        var field=(tools.jackson.databind.node.ObjectNode)declaration.get("logical").get("entityTypes").get(0).get("fields").get(1);
        field.put("sensitivity","secret");
        var compiled=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,new NativeDefinitionBytesCompiler().compile(json.writeValueAsBytes(declaration),DefinitionBytesCompiler.Format.JSON));
        var definition=new PublishedDefinition(new NativeCommand.Reference("00000000-0000-4000-8000-000000000001","2"),"independent-secret-coordinates",compiled,List.of());
        var fixture=new PlanContentAdapterTest();
        var content=assertInstanceOf(ContentResult.Complete.class,fixture.adapter.project(definition,"mock-pg",fixture.observation(definition))).content();
        var refs=new HashMap<TargetIntent.Ref,PlanCommand.Ref>();var handles=new HashMap<TargetIntent.Ref,String>();
        for(var ref:content.provenance().values()){String handle=UUID.randomUUID().toString();refs.put(ref,new PlanCommand.Ref.Existing(handle));handles.put(ref,handle);}
        var view=new HostedPlanService.ViewSnapshot("2",definition,"mock-pg",Optional.of(content),Optional.of(content),Draft.empty(),refs,handles);
        var ref=refs.get(new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha")));
        var bindings=PlanBindingViews.bindings(view,ref,0,100);
        var tone=(Map<?,?>)((List<?>)bindings.get("items")).stream().filter(item->((Map<?,?>)item).get("fieldId").equals("tone")).findFirst().orElseThrow();
        assertEquals(Map.of("state","masked"),tone.get("current"));assertEquals(Map.of("state","masked"),tone.get("target"));
        assertEquals("unresolved",tone.get("change"));assertEquals(Map.of("state","complete","total",1),tone.get("currentLocations"));
        for(boolean target:new boolean[]{false,true})for(String fieldId:new String[]{"tone","tag"})for(int offset:new int[]{0,Integer.MAX_VALUE}) {
            assertEquals(PlanRefusal.Code.DISCLOSURE_REQUIRED,assertThrows(PlanRefusal.class,()->PlanBindingViews.locations(view,ref,fieldId,target,offset,100,false)).code());
        }
        var disclosed=PlanBindingViews.locations(view,ref,"tone",false,0,100,true);
        assertEquals(1,disclosed.get("total"));
        var location=(Map<?,?>)((List<?>)disclosed.get("items")).getFirst();
        var source=content.sources().stream().filter(s->s.documentId().equals(location.get("documentId"))).findFirst().orElseThrow().xml();
        var span=(Map<?,?>)location.get("span");
        assertEquals("  blue &amp; 𐀀&#9;",source.substring((Integer)span.get("start"),(Integer)span.get("end")));
        assertEquals(bindings,PlanBindingViews.bindings(view,ref,0,100),"Disclosure cannot reveal values in the masked rail");
    }
}
