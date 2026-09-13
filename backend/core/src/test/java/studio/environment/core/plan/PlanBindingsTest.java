package studio.environment.core.plan;

import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanPorts.*;
import static studio.environment.core.plan.PlanBindings.*;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definitionv2.*;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.workspace.NativeCommand;

class PlanBindingsTest {
    static final ObservedGraph.Key OLD=new ObservedGraph.Key("glyph","alpha");
    static final TargetIntent.Ref.Existing EXISTING=new TargetIntent.Ref.Existing(OLD);
    static final TargetIntent.Ref.Fresh FRESH=new TargetIntent.Ref.Fresh("new-slot","glyph");
    static final String HANDLE="00000000-0000-4000-8000-000000000011",FRESH_HANDLE="00000000-0000-4000-8000-000000000012";
    static final PlanCommand.Ref.Existing WIRE=new PlanCommand.Ref.Existing(HANDLE);
    static final PlanCommand.Ref.Fresh NEW_WIRE=new PlanCommand.Ref.Fresh("new-slot","glyph");
    static PublishedDefinition definition(){
        var fields=List.of(field("tag",true,Sensitivity.PUBLIC,true),field("tone",false,Sensitivity.PUBLIC,true),field("secret",false,Sensitivity.SECRET,true),field("unknown",false,Sensitivity.SECRET,true),field("hidden",false,Sensitivity.SECRET,true));
        var type=new NativeDefinition.EntityType("glyph","Invented glyph",fields,new NativeDefinition.Identity("tag"));
        var projection=new NativeDefinition.Projection("glyphs","glyph",List.of(new NativeDefinition.ExpandedName("urn:mock:bindings","sheet"),new NativeDefinition.ExpandedName("urn:mock:bindings","glyph")),fields.stream().map(f->new NativeDefinition.FieldMapping(f.id(),new NativeDefinition.ExpandedName("",f.id()))).toList(),List.of());
        var binding=new NativeDefinition.Binding("mock",NativeDefinition.Engine.POSTGRESQL,NativeDefinition.Storage.TEXT,"mock_schema","mock_table","mock_key","mock_xml",NativeDefinition.KeyType.INT64,List.of(new NativeDefinition.Document("sheet","1",List.of(projection))));
        var declared=new NativeDefinition("mock-bindings",BigInteger.ONE,new NativeDefinition.Logical(List.of(type),List.of(),List.of(),List.of(NativeDefinition.Operation.RETAIN_ENTITY,NativeDefinition.Operation.CREATE_ENTITY,NativeDefinition.Operation.REMOVE_ENTITY,NativeDefinition.Operation.BIND_FIELD)),List.of(binding));
        var ready=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,new NativeDefinitionCompiler().compile(declared));
        return new PublishedDefinition(new NativeCommand.Reference("00000000-0000-4000-8000-000000000001","2"),"mock-publication",ready,List.of());
    }
    static NativeDefinition.Field field(String id,boolean required,Sensitivity sensitivity,boolean readable){return new NativeDefinition.Field(id,ValueType.TEXT,required,Classification.STRUCTURAL,sensitivity,readable,true);}
    static Content content(TargetIntent.Ref ref,Map<String,String> fields){
        var key=new ObservedGraph.Key("glyph",fields.get("tag"));
        var entity=new ObservedGraph.Entity(key,fields,new ObservedGraph.Origin("sheet","glyphs","mock-digest",1,List.of(0)));
        return new Content(List.of(),new ObservedGraph(List.of(entity),List.of()),Map.of(key,ref));
    }
    static HostedPlanService.ViewSnapshot snapshot(Optional<Content> target,TargetIntent.EntityDecision... decisions){
        var handles=new HashMap<TargetIntent.Ref,String>();handles.put(EXISTING,HANDLE);
        for(var decision:decisions)if(decision instanceof TargetIntent.EntityDecision.Create)handles.put(FRESH,FRESH_HANDLE);
        return new HostedPlanService.ViewSnapshot("9",definition(),"mock",Optional.of(content(EXISTING,Map.of("tag","alpha","tone","","secret","Mock-Secret-Canary","unknown","Mock-Unknown-Canary","hidden","Mock-Hidden-Canary"))),target,new Draft(new TargetIntent(List.of(decisions),List.of()),List.of()),Map.of(EXISTING,WIRE),handles);
    }
    static Field get(Entity entity,String id){return entity.fields().stream().filter(f->f.fieldId().equals(id)).findFirst().orElseThrow();}
    @Test void completeIdentityChangeKeepsTokenAndMasksHaveNoEqualityOracle(){
        var view=snapshot(Optional.of(content(EXISTING,Map.of("tag","beta","secret","Mock-Secret-Canary","unknown","different","hidden","different"))));
        var result=resolve(view,WIRE);assertEquals(List.of("hidden","secret","tag","tone","unknown"),result.fields().stream().map(Field::fieldId).toList());
        var tag=get(result,"tag");assertEquals("[[value:"+HANDLE+":tag]]",tag.token());assertEquals(Optional.of("alpha"),tag.current().text());assertEquals(Optional.of("beta"),tag.target().text());assertEquals(Change.CHANGED,tag.change());
        assertEquals(State.VALUE,get(result,"tone").current().state());assertEquals(Optional.of(""),get(result,"tone").current().text());assertEquals(State.ABSENT,get(result,"tone").target().state());assertEquals(Change.REMOVED,get(result,"tone").change());
        for(var id:List.of("hidden","secret","unknown")){var field=get(result,id);assertEquals(State.MASKED,field.current().state());assertEquals(Optional.empty(),field.current().text());assertEquals(State.MASKED,field.target().state());assertEquals(Change.UNRESOLVED,field.change());}
        assertFalse(result.toString().contains("Canary"));assertFalse(tag.toString().contains("alpha"));
    }
    @Test void incompleteSelectedFieldsDistinguishEmptyAbsentKeepObservedAndMissing(){
        var retain=new TargetIntent.EntityDecision.Retain(EXISTING,Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.Entered(""),"secret",new TargetIntent.FieldValue.ExplicitlyAbsent(),"unknown",new TargetIntent.FieldValue.Unresolved()),Map.of());
        var result=resolve(snapshot(Optional.empty(),retain),WIRE);
        assertEquals(Change.UNCHANGED,get(result,"tag").change());assertEquals(Optional.of(""),get(result,"tone").target().text());assertEquals(Change.UNCHANGED,get(result,"tone").change());
        assertEquals(State.ABSENT,get(result,"secret").target().state());assertEquals(Change.REMOVED,get(result,"secret").change());assertEquals(State.UNRESOLVED,get(result,"unknown").target().state());assertEquals(State.UNRESOLVED,get(result,"hidden").target().state());
        var unselected=resolve(snapshot(Optional.empty()),WIRE);assertEquals(Change.UNCHANGED,get(unselected,"tag").change());assertEquals(Change.UNRESOLVED,get(unselected,"secret").change());
    }
    @Test void freshCurrentIsUnavailableWhileResolvedTargetIsAnAddition(){
        var create=new TargetIntent.EntityDecision.Create(FRESH,Map.of("tag",new TargetIntent.FieldValue.Entered("new"),"tone",new TargetIntent.FieldValue.ExplicitlyAbsent()),Map.of());
        var result=resolve(snapshot(Optional.empty(),create),NEW_WIRE);
        assertEquals(State.UNAVAILABLE,get(result,"tag").current().state());assertEquals(Change.ADDED,get(result,"tag").change());assertEquals("[[value:"+FRESH_HANDLE+":tag]]",get(result,"tag").token());assertEquals(Change.UNCHANGED,get(result,"tone").change());assertEquals(Change.UNRESOLVED,get(result,"secret").change());
    }
    @Test void explicitRemovalAndCompleteAbsentEntityAreConfirmedAbsence(){
        var removed=resolve(snapshot(Optional.empty(),new TargetIntent.EntityDecision.Remove(EXISTING)),WIRE);
        var empty=new Content(List.of(),new ObservedGraph(List.of(),List.of()),Map.of());var complete=resolve(snapshot(Optional.of(empty),new TargetIntent.EntityDecision.Remove(EXISTING)),WIRE);
        for(var result:List.of(removed,complete))for(var field:result.fields()){assertEquals(State.ABSENT,field.target().state());assertEquals(Change.REMOVED,field.change());}
    }
    @Test void invalidLiveProvenanceAndFreshKeepObservedRefuse(){
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->resolve(snapshot(Optional.empty()),NEW_WIRE)).code());
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->resolve(snapshot(Optional.empty()),new PlanCommand.Ref.Existing(FRESH_HANDLE))).code());
        var invalid=new TargetIntent.EntityDecision.Create(FRESH,Map.of("tag",new TargetIntent.FieldValue.KeepObserved()),Map.of());assertThrows(PlanRefusal.class,()->resolve(snapshot(Optional.empty(),invalid),NEW_WIRE));
        var unknownField=new TargetIntent.EntityDecision.Retain(EXISTING,Map.of("invented-unknown",new TargetIntent.FieldValue.Entered("bad")),Map.of());assertThrows(PlanRefusal.class,()->resolve(snapshot(Optional.empty(),unknownField),WIRE));
    }
}
