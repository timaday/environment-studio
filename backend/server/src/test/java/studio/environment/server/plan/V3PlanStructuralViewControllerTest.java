package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class V3PlanStructuralViewControllerTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    @Test void actualEmptyExplicitDraftIsACompletePage() throws Exception {
        var f=new V3PlanMaterializationControllerTest.Fixture();
        var controller=new V3PlanStructuralViewController(f.runtime,f.sessions);
        var context=new V3PlanTransportTest.Context(); var output=new V3PlanTransportTest.Output(); var response=V3PlanTransportTest.response(output);
        assertDoesNotThrow(()->controller.draft(f.plan,f.body(context,"{\"revision\":\"2\",\"offset\":0,\"limit\":100}"),response));
        assertTrue(context.complete.await(3,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
        assertEquals(200,response.getStatus());assertEquals(JSON.readTree("{\"revision\":\"2\",\"total\":0,\"offset\":0,\"nextOffset\":null,\"items\":[]}"),JSON.readTree(output.bytes.toByteArray()));
    }
    @Test void returnedPlacementCreatesFreshReplacementWithSeparateBindingTokensAndLiteralXml() throws Exception {
        var f=new V3PlanMaterializationControllerTest.Fixture();var controller=new V3PlanStructuralViewController(f.runtime,f.sessions);
        var one=Map.of("kind","existing","handle",f.core.handle(f.plan,"2","one"));
        var before=call(f,controller,"bindings",Map.of("revision","2","entity",one,"offset",0,"limit",100),200);
        var id=field(before,"id");assertEquals("one",id.get("current").get("text").asString());
        assertEquals("unavailable",id.get("targetLocations").get("state").asString());
        var places=call(f,controller,"placements",Map.of("revision","2","documentId","sheet","projectionId","items","offset",0,"limit",100),200);
        assertEquals(1,places.get("total").asInt());var coordinate=places.get("items").get(0);
        assertEquals("0",coordinate.get("elementIndex").asString());assertEquals("sheet",coordinate.get("documentId").asString());
        assertEquals(f.core.snapshot(f.plan,"2").current().orElseThrow().sources().getFirst().digest(),coordinate.get("sourceDigest").asString());
        var fresh=Map.of("kind","fresh","slotId","replacement","typeId","item");
        var parent=new LinkedHashMap<String,Object>();parent.put("kind","existing");
        for(String key:List.of("documentId","sourceDigest","elementIndex"))parent.put(key,coordinate.get(key).asString());
        var fields=Map.of("id",Map.of("kind","entered","text","one"),"tone",Map.of("kind","entered","text","gamma"),"finish",Map.of("kind","entered","text","z"));
        var create=Map.of("decision",Map.of("kind","create","entity",fresh,"fields",fields,"references",Map.of()),"placements",List.of(Map.of("entity",fresh,"documentId","sheet","projectionId","items","parent",parent)));
        var remove=Map.of("decision",Map.of("kind","remove","entity",one),"placements",List.of());
        var command=Map.of("expectedRevision","2","requestId",UUID.randomUUID().toString(),"kind","batch-upsert","changes",List.of(remove,create),"containment",List.of());
        try(var admission=f.core.service.reserveCommand(f.core.lease,f.plan,studio.environment.core.plan.PlanDefinition.Version.V3)) {
            assertEquals("3",admission.execute(new PlanCommandReader().read(new java.io.ByteArrayInputStream(JSON.writeValueAsBytes(command)))).revision());
        }
        assertEquals("<items><!-- mock -->\r\n<item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/><item xmlns=\"\" finish=\"z\" id=\"one\" tone=\"gamma\"/></items>",f.core.snapshot(f.plan,"3").target().orElseThrow().sources().getFirst().xml());
        var removed=field(call(f,controller,"bindings",Map.of("revision","3","entity",one,"offset",0,"limit",100),200),"id");
        var replacement=field(call(f,controller,"bindings",Map.of("revision","3","entity",fresh,"offset",0,"limit",100),200),"id");
        assertEquals(id.get("token"),removed.get("token"));assertNotEquals(id.get("token"),replacement.get("token"));
        assertEquals("absent",removed.get("target").get("state").asString());assertEquals("removed",removed.get("change").asString());
        assertEquals(0,removed.get("targetLocations").get("total").asInt());
        assertEquals("unavailable",replacement.get("current").get("state").asString());assertEquals("CURRENT_ENTITY_ABSENT",replacement.get("currentLocations").get("code").asString());
        assertEquals("one",replacement.get("target").get("text").asString());assertEquals("added",replacement.get("change").asString());
        var draft=call(f,controller,"draft",Map.of("revision","3","offset",0,"limit",100),200);
        assertEquals(2,draft.get("total").asInt());var dispositions=new HashSet<String>();for(var row:draft.get("items"))dispositions.add(row.get("disposition").asString());assertEquals(Set.of("remove","create"),dispositions);
        var first=call(f,controller,"draft",Map.of("revision","3","offset",0,"limit",1),200);var second=call(f,controller,"draft",Map.of("revision","3","offset",1,"limit",1),200);
        assertEquals(draft.get("items").get(0),first.get("items").get(0));assertEquals(draft.get("items").get(1),second.get("items").get(0));
        var beyond=call(f,controller,"draft",Map.of("revision","3","offset",50000,"limit",1),200);assertEquals(2,beyond.get("total").asInt());assertTrue(beyond.get("items").isEmpty());assertTrue(beyond.get("nextOffset").isNull());
        for(String side:List.of("current","target"))assertEquals(0,call(f,controller,"relations",Map.of("revision","3","side",side,"offset",0,"limit",100),200).get("total").asInt());
        assertEquals(0,call(f,controller,"containment",Map.of("revision","3","offset",0,"limit",100),200).get("total").asInt());
    }
    @Test void realRelationEndpointsAndBindingTokensSurviveIdentityEditWithMaskedAndIncompleteStates() throws Exception {
        var f=new studio.environment.server.planning.V3StructuralWireFixtures();
        var sessions=new studio.environment.server.session.HostedSessions(java.time.Clock.systemUTC(),List.of());
        var runtime=new PlanRuntime(f.service,List.of(),(owner,id)->true);var controller=new V3PlanStructuralViewController(runtime,sessions);
        var one=(studio.environment.core.plan.PlanCommand.Ref.Existing)f.reference("one");var two=(studio.environment.core.plan.PlanCommand.Ref.Existing)f.reference("two");
        var oneRef=Map.of("kind","existing","handle",one.handle());var twoRef=Map.of("kind","existing","handle",two.handle());
        var original=call(runtime,controller,f.lease,f.plan,"relations",Map.of("revision","2","side","current","offset",0,"limit",100),200);
        assertEquals(JSON.valueToTree(List.of(Map.of("relationId","link","from",oneRef,"to",twoRef))),original.get("items"));
        var before=call(runtime,controller,f.lease,f.plan,"bindings",Map.of("revision","2","entity",oneRef,"offset",0,"limit",100),200);
        assertEquals("masked",field(before,"secret").get("current").get("state").asString());assertFalse(before.toString().contains("MOCK-STRUCTURAL-SECRET"));
        assertEquals(1,field(before,"secret").get("currentLocations").get("total").asInt());
        assertEquals("",field(before,"optional").get("current").get("text").asString());
        var twoBefore=call(runtime,controller,f.lease,f.plan,"bindings",Map.of("revision","2","entity",twoRef,"offset",0,"limit",100),200);
        assertEquals("absent",field(twoBefore,"optional").get("current").get("state").asString());assertEquals(2,field(twoBefore,"id").get("currentLocations").get("total").asInt());
        var keep=new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved();
        var firstFields=Map.<String,studio.environment.core.planning.TargetIntent.FieldValue>of("id",keep,"tone",keep,"finish",keep,"secret",keep,"optional",keep);
        var secondFields=new LinkedHashMap<>(firstFields);secondFields.put("id",new studio.environment.core.planning.TargetIntent.FieldValue.Entered("renamed"));
        var first=new studio.environment.core.plan.PlanCommand.Change(new studio.environment.core.plan.PlanCommand.Entity.Retain(one,firstFields,Map.of("link",new studio.environment.core.plan.PlanCommand.ReferenceState.To(two))),List.of());
        var second=new studio.environment.core.plan.PlanCommand.Change(new studio.environment.core.plan.PlanCommand.Entity.Retain(two,secondFields,Map.of("link",new studio.environment.core.plan.PlanCommand.ReferenceState.KeepObserved())),List.of());
        try(var admission=f.service.reserveCommand(f.lease,f.plan,studio.environment.core.plan.PlanDefinition.Version.V3)) {
            assertEquals("3",admission.execute(new studio.environment.core.plan.PlanCommand(new studio.environment.core.plan.HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new studio.environment.core.plan.PlanCommand.Action.Batch(List.of(first,second),List.of()))).revision());
        }
        var target=call(runtime,controller,f.lease,f.plan,"relations",Map.of("revision","3","side","target","offset",0,"limit",100),200);
        assertEquals(original.get("items"),target.get("items"));
        assertEquals("<items><item id='one' tone='alpha' finish='x' next='renamed' secret='MOCK-STRUCTURAL-SECRET' optional=''/><item id='renamed' tone='beta' finish='y'/></items>",f.snapshot("3").target().orElseThrow().sources().getFirst().xml());
        var after=call(runtime,controller,f.lease,f.plan,"bindings",Map.of("revision","3","entity",twoRef,"offset",0,"limit",100),200);
        assertEquals(field(twoBefore,"id").get("token"),field(after,"id").get("token"));assertEquals("renamed",field(after,"id").get("target").get("text").asString());assertEquals(2,field(after,"id").get("targetLocations").get("total").asInt());
        secondFields.put("tone",new studio.environment.core.planning.TargetIntent.FieldValue.Unresolved());
        try(var admission=f.service.reserveCommand(f.lease,f.plan,studio.environment.core.plan.PlanDefinition.Version.V3)) {
            admission.execute(new studio.environment.core.plan.PlanCommand(new studio.environment.core.plan.HostedPlanService.Mutation("3",UUID.randomUUID().toString()),new studio.environment.core.plan.PlanCommand.Action.Upsert(new studio.environment.core.plan.PlanCommand.Change(new studio.environment.core.plan.PlanCommand.Entity.Retain(two,secondFields,Map.of("link",new studio.environment.core.plan.PlanCommand.ReferenceState.KeepObserved())),List.of()))));
        }
        var incomplete=call(runtime,controller,f.lease,f.plan,"bindings",Map.of("revision","4","entity",twoRef,"offset",0,"limit",100),200);
        assertEquals("unresolved",field(incomplete,"tone").get("target").get("state").asString());assertEquals("unavailable",field(incomplete,"tone").get("targetLocations").get("state").asString());
        assertEquals(2,call(runtime,controller,f.lease,f.plan,"draft",Map.of("revision","4","offset",0,"limit",100),200).get("total").asInt());
        assertEquals("INCOMPLETE_TARGET",call(runtime,controller,f.lease,f.plan,"relations",Map.of("revision","4","side","target","offset",0,"limit",100),422).get("code").asString());
    }
    @Test void everyStructuralRouteRollsBackBeforeBodyAndRejectsWrongVersionFirst() throws Exception {
        var f=new V3PlanMaterializationControllerTest.Fixture();var controller=new V3PlanStructuralViewController(f.runtime,f.sessions);
        var request=new org.springframework.mock.web.MockHttpServletRequest(){
            @Override public jakarta.servlet.ServletInputStream getInputStream(){throw new AssertionError("BODY_BEFORE_ADMISSION");}
            @Override public jakarta.servlet.AsyncContext startAsync(){throw new AssertionError("ASYNC_BEFORE_ADMISSION");}
        };request.setContentType("application/json");request.setAttribute(studio.environment.server.session.HostedSessions.REQUEST_LEASE,f.core.lease);
        var held=f.runtime.transfers().admitSemantic(f.core.lease);
        try {
            for(String route:List.of("relations","draft","containment","placements","bindings")) {
                assertEquals(studio.environment.core.plan.PlanRefusal.Code.CAPACITY,assertThrows(studio.environment.core.plan.PlanRefusal.class,()->invoke(controller,f.plan,route,request,new org.springframework.mock.web.MockHttpServletResponse())).code());
                try(var recovered=assertDoesNotThrow(()->f.core.service.reserveView(f.core.lease,f.plan,studio.environment.core.plan.PlanDefinition.Version.V3))){assertTrue(recovered.live());}
            }
        } finally {held.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);}
        var legacy=new PlanV1VersionBoundaryTest.Fixture();var old=legacy.create(false);var runtime=new PlanRuntime(legacy.service,List.of(),(owner,id)->true);var wrong=new V3PlanStructuralViewController(runtime,f.sessions);var slot=runtime.transfers().admitSemantic(legacy.lease);
        try {for(String route:List.of("relations","draft","containment","placements","bindings"))assertEquals(studio.environment.core.plan.PlanRefusal.Code.NOT_FOUND,assertThrows(studio.environment.core.plan.PlanRefusal.class,()->invoke(wrong,old.planId(),route,PlanV1VersionBoundaryTest.unread(legacy),new org.springframework.mock.web.MockHttpServletResponse())).code());}
        finally {slot.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);}
    }
    @Test void heldBindingOutputAbortsAfterInspectionChangeAndClosedSelectorsCannotMutate() throws Exception {
        var f=new V3PlanMaterializationControllerTest.Fixture();var controller=new V3PlanStructuralViewController(f.runtime,f.sessions);
        var ref=Map.of("kind","existing","handle",f.core.handle(f.plan,"2","one"));
        assertEquals("MALFORMED_BODY",call(f,controller,"bindings",Map.of("revision","2","entity",ref,"offset",257,"limit",1),400).get("code").asString());
        assertEquals("MALFORMED_BODY",call(f,controller,"draft",Map.of("revision","2","offset",0,"limit",1,"filter","inferred"),400).get("code").asString());
        call(f,controller,"placements",Map.of("revision","2","documentId","unknown","projectionId","items","offset",0,"limit",1),422);
        call(f,controller,"containment",Map.of("revision","99","offset",0,"limit",1),409);
        var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();output.ready=false;String operation=null;
        try {
            controller.bindings(f.plan,f.body(context,JSON.writeValueAsString(Map.of("revision","2","entity",ref,"offset",0,"limit",100))),V3PlanTransportTest.response(output));
            assertTrue(output.checked.await(3,TimeUnit.SECONDS));assertEquals(studio.environment.core.plan.PlanRefusal.Code.CAPACITY,assertThrows(studio.environment.core.plan.PlanRefusal.class,()->f.core.service.reserveView(f.core.lease,f.plan,studio.environment.core.plan.PlanDefinition.Version.V3)).code());
            operation=f.core.service.reserve(f.core.lease,f.plan,new studio.environment.core.plan.HostedPlanService.Mutation("2",UUID.randomUUID().toString()),studio.environment.core.plan.PlanDefinition.Version.V3).operationId().orElseThrow();
            assertTrue(context.complete.await(3,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);assertEquals(0,output.bytes.size());
        } finally {output.ready=true;if(operation!=null)f.core.service.cancel(f.core.lease,operation,studio.environment.core.plan.PlanDefinition.Version.V3);}
        try(var recovered=f.core.service.reserveView(f.core.lease,f.plan,studio.environment.core.plan.PlanDefinition.Version.V3)){assertTrue(recovered.live());}
    }
    static void invoke(V3PlanStructuralViewController controller,String plan,String route,jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response) {
        switch(route){case "draft"->controller.draft(plan,request,response);case "relations"->controller.relations(plan,request,response);case "containment"->controller.containment(plan,request,response);case "placements"->controller.placements(plan,request,response);case "bindings"->controller.bindings(plan,request,response);default->throw new AssertionError("route");}
    }
    static tools.jackson.databind.JsonNode field(tools.jackson.databind.JsonNode page,String id) {
        for(var item:page.get("items"))if(id.equals(item.get("fieldId").asString()))return item;
        throw new AssertionError("missing field "+id);
    }
    static tools.jackson.databind.JsonNode call(V3PlanMaterializationControllerTest.Fixture f,V3PlanStructuralViewController controller,String route,Map<String,?> body,int status)throws Exception {
        return call(f.runtime,controller,f.core.lease,f.plan,route,body,status);
    }
    static tools.jackson.databind.JsonNode call(PlanRuntime runtime,V3PlanStructuralViewController controller,studio.environment.core.session.SessionLedger.Lease lease,String plan,String route,Map<String,?> body,int status)throws Exception {
        var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();var response=V3PlanTransportTest.response(output);var request=V3PlanTransportTest.bodyRequest(context,JSON.writeValueAsString(body));request.setAttribute(studio.environment.server.session.HostedSessions.REQUEST_LEASE,lease);
        invoke(controller,plan,route,request,response);
        assertTrue(context.complete.await(3,TimeUnit.SECONDS));V3PlanTransportTest.settled(runtime.transfers(),lease);assertEquals(status,response.getStatus(),output.bytes.toString(java.nio.charset.StandardCharsets.UTF_8));return JSON.readTree(output.bytes.toByteArray());
    }
}
