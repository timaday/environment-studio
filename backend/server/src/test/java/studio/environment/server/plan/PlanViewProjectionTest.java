package studio.environment.server.plan;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.planning.TargetIntent;
class PlanViewProjectionTest {
    HostedPlanService.ViewSnapshot snapshot() throws Exception {
        var fixture=new PlanContentAdapterTest();var definition=fixture.definition();
        var current=assertInstanceOf(ContentResult.Complete.class,fixture.adapter.project(definition,"mock-pg",fixture.observation(definition))).content();
        var refs=new HashMap<TargetIntent.Ref,PlanCommand.Ref>();int count=0;
        for(var ref:current.provenance().values())refs.put(ref,new PlanCommand.Ref.Existing(new UUID(0,++count).toString()));
        var handles=new HashMap<TargetIntent.Ref,String>();refs.forEach((ref,wire)->handles.put(ref,((PlanCommand.Ref.Existing)wire).handle()));
        return new HostedPlanService.ViewSnapshot("2",definition,"mock-pg",Optional.of(current),Optional.empty(),Draft.empty(),refs,handles);
    }
    @Test void completeInventoryNeverLabelsAnAbsentTargetUnchanged() throws Exception {
        var result=PlanViewProjection.documents(snapshot());
        assertEquals("2",result.get("revision"));var documents=(List<?>)result.get("documents");assertEquals(2,documents.size());
        for(var item:documents){var document=(Map<?,?>)item;assertNull(document.get("targetDigest"));assertNull(document.get("changed"));}
    }
    @Test void graphTotalIsCompleteAndMissingTargetRefuses() throws Exception {
        var snapshot=snapshot();var first=PlanViewProjection.entities(snapshot,false,0,1);
        assertEquals(3,first.get("total"));assertEquals(1,first.get("nextOffset"));assertEquals(1,((List<?>)first.get("items")).size());
        assertThrows(PlanRefusal.class,()->PlanViewProjection.entities(snapshot,true,0,1));
    }
    @Test void eligibleCoordinatesComeFromExactOriginalParentPath() throws Exception {
        var page=PlanViewProjection.placements(snapshot(),"palette-sheet","palettes",0,100);
        assertEquals(1,page.get("total"));var parent=(Map<?,?>)((List<?>)page.get("items")).getFirst();
        assertEquals("0",parent.get("elementIndex"));assertEquals("palette-sheet",parent.get("documentId"));
        assertThrows(PlanRefusal.class,()->PlanViewProjection.placements(snapshot(),"glyph-sheet","palettes",0,100));
    }
    @Test void secretEnteredFieldsStayMaskedWithoutMutatingTheExplicitDraft() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();var tree=json.readTree(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../../fixtures/native-v2/definition.json")));
        for(var type:tree.get("logical").get("entityTypes"))if(type.get("id").asString().equals("glyph"))for(var field:type.get("fields"))if(field.get("id").asString().equals("tone"))((tools.jackson.databind.node.ObjectNode)field).put("sensitivity","secret");
        var base=snapshot();var compiled=assertInstanceOf(studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish.class,new studio.environment.server.definition.NativeDefinitionBytesCompiler().compile(json.writeValueAsBytes(tree),studio.environment.server.definition.DefinitionBytesCompiler.Format.JSON));
        var definition=new PublishedDefinition(base.definition().reference(),"mock-secret-publication",compiled,List.of());
        var alpha=new TargetIntent.Ref.Existing(new studio.environment.core.graph.ObservedGraph.Key("glyph","alpha"));
        var decision=new TargetIntent.EntityDecision.Retain(alpha,Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.Entered("Masked-Projection-Canary")),Map.of("uses",new TargetIntent.ReferenceValue.KeepObserved()));
        var draft=new Draft(new TargetIntent(List.of(decision),List.of()),List.of());
        var view=new HostedPlanService.ViewSnapshot("2",definition,base.binding(),base.current(),base.target(),draft,base.references(),base.displayHandles());
        assertFalse(PlanViewProjection.draft(view,0,100).toString().contains("Masked-Projection-Canary"));
        var item=(Map<?,?>)((List<?>)PlanViewProjection.draft(view,0,100).get("items")).getFirst();
        for(var value:(List<?>)item.get("fields")){var field=(Map<?,?>)value;if(field.get("fieldId").equals("tone")){assertEquals(true,field.get("masked"));assertNull(field.get("value"));assertEquals("entered",field.get("kind"));}}
        assertTrue(decision.fields().get("tone") instanceof TargetIntent.FieldValue.Entered entered && entered.text().equals("Masked-Projection-Canary"));
    }
}
