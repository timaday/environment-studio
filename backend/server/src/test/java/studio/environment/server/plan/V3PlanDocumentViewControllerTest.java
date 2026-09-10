package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class V3PlanDocumentViewControllerTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    @Test void rawRouteReturnsExactOriginalXmlOnlyWithExplicitDisclosure() throws Exception {
        var f=new V3PlanMaterializationControllerTest.Fixture();var controller=new V3PlanDocumentViewController(f.runtime,f.sessions);
        var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();var response=V3PlanTransportTest.response(output);
        assertDoesNotThrow(()->controller.document(f.plan,f.body(context,JSON.writeValueAsString(Map.of("revision","2","side","current","documentId","sheet","mode","raw","completeDocumentDisclosure",true))),response));
        assertTrue(context.complete.await(3,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
        assertEquals(200,response.getStatus());var value=JSON.readTree(output.bytes.toByteArray());assertEquals("<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>",value.get("text").asString());assertTrue(value.get("exact").asBoolean());
    }
    @Test void childMappingsUseExactLexicalSpansAndOpaqueTokensWithoutReplacingLookalikes() throws Exception {
        var f=new studio.environment.server.planning.V3DocumentWireFixtures();var sessions=new studio.environment.server.session.HostedSessions(java.time.Clock.systemUTC(),List.of());var runtime=new PlanRuntime(f.service,List.of(),(owner,id)->true);
        var controller=new V3PlanDocumentViewController(runtime,sessions);var structural=new V3PlanStructuralViewController(runtime,sessions);
        var one=(studio.environment.core.plan.PlanCommand.Ref.Existing)f.reference("one");var two=(studio.environment.core.plan.PlanCommand.Ref.Existing)f.reference("two");
        var ref=Map.of("kind","existing","handle",one.handle());
        var rail=V3PlanStructuralViewControllerTest.call(runtime,structural,f.lease,f.plan,"bindings",Map.of("revision","2","entity",ref,"offset",0,"limit",100),200);
        var other=V3PlanStructuralViewControllerTest.call(runtime,structural,f.lease,f.plan,"bindings",Map.of("revision","2","entity",Map.of("kind","existing","handle",two.handle()),"offset",0,"limit",100),200);
        String id=V3PlanStructuralViewControllerTest.field(rail,"id").get("token").asString(),tone=V3PlanStructuralViewControllerTest.field(rail,"tone").get("token").asString(),finish=V3PlanStructuralViewControllerTest.field(rail,"finish").get("token").asString();
        String otherId=V3PlanStructuralViewControllerTest.field(other,"id").get("token").asString(),otherFinish=V3PlanStructuralViewControllerTest.field(other,"finish").get("token").asString();
        var raw=call(runtime,controller,f.lease,f.plan,false,document("2","current","raw"),200);
        assertEquals(studio.environment.server.planning.V3DocumentWireFixtures.XML,raw.get("text").asString());
        var placeholders=call(runtime,controller,f.lease,f.plan,false,document("2","current","placeholders"),200);
        assertEquals("<items xmlns:p='urn:props'><item id='"+id+"' finish='"+finish+"' lookalike='alpha'><p:entry p:key='tone' p:value='"+tone+"'/></item><item id='"+otherId+"' finish='"+otherFinish+"'/></items>",placeholders.get("text").asString());
        assertFalse(placeholders.get("exact").asBoolean());assertTrue(placeholders.get("redacted").asBoolean());assertTrue(placeholders.get("unmappedConcreteMayRemain").asBoolean());
        var formatted=call(runtime,controller,f.lease,f.plan,false,document("2","current","formatted"),200);
        assertEquals("<items xmlns:p='urn:props'>\n  <item id='one' finish='x' lookalike='alpha'>\n    <p:entry p:key='tone' p:value='al&#112;ha'/>\n  </item>\n  <item id='two' finish='y'/>\n</items>",formatted.get("text").asString());assertFalse(formatted.get("exact").asBoolean());assertFalse(formatted.get("redacted").asBoolean());
        var locations=call(runtime,controller,f.lease,f.plan,true,locations("2",ref,"tone","current",0),200);
        assertEquals(1,locations.get("total").asInt());var location=locations.get("items").get(0);assertEquals("2",location.get("elementIndex").asString());assertEquals("urn:props",location.get("attribute").get("namespaceUri").asString());assertEquals("value",location.get("attribute").get("localName").asString());assertEquals("field",location.get("role").asString());
        int start=raw.get("text").asString().indexOf("al&#112;ha");assertEquals(start,location.get("span").get("start").asInt());assertEquals(start+"al&#112;ha".length(),location.get("span").get("end").asInt());
        var empty=call(runtime,controller,f.lease,f.plan,true,locations("2",Map.of("kind","existing","handle",two.handle()),"tone","current",0),200);assertEquals(0,empty.get("total").asInt());
        var beyond=call(runtime,controller,f.lease,f.plan,true,locations("2",ref,"tone","current",Integer.MAX_VALUE),200);assertEquals(1,beyond.get("total").asInt());assertTrue(beyond.get("items").isEmpty());assertTrue(beyond.get("nextOffset").isNull());
        assertEquals(studio.environment.server.planning.V3DocumentWireFixtures.XML,f.snapshot("2").current().orElseThrow().sources().getFirst().xml());assertTrue(f.snapshot("2").target().isEmpty());
    }
    @Test void consentIsRequiredEvenForEmptyLocationsAndClosedInputsCannotAuthorizeDisclosure() throws Exception {
        var f=new V3PlanMaterializationControllerTest.Fixture();var controller=new V3PlanDocumentViewController(f.runtime,f.sessions);var ref=Map.of("kind","existing","handle",f.core.handle(f.plan,"2","one"));
        for(boolean locations:List.of(false,true))for(Object consent:List.of(false,"true",1)) {
            var request=new LinkedHashMap<String,Object>(locations?locations("2",ref,"tone","current",Integer.MAX_VALUE):document("2","current","raw"));request.put("completeDocumentDisclosure",consent);
            assertEquals("MALFORMED_BODY",call(f.runtime,controller,f.core.lease,f.plan,locations,request,400).get("code").asString());
        }
        for(boolean locations:List.of(false,true)) {
            var request=new LinkedHashMap<String,Object>(locations?locations("2",ref,"tone","current",Integer.MAX_VALUE):document("2","current","placeholders"));request.remove("completeDocumentDisclosure");call(f.runtime,controller,f.core.lease,f.plan,locations,request,400);
        }
        call(f.runtime,controller,f.core.lease,f.plan,false,document("2","target","raw"),422);
        call(f.runtime,controller,f.core.lease,f.plan,true,locations("2",ref,"unknown","current",0),404);
        call(f.runtime,controller,f.core.lease,f.plan,true,locations("2",ref,"tone","target",0),422);
        call(f.runtime,controller,f.core.lease,f.plan,false,document("99","current","raw"),409);
        var invalid=new LinkedHashMap<String,Object>(document("2","current","raw"));invalid.put("revealSecrets",true);call(f.runtime,controller,f.core.lease,f.plan,false,invalid,400);
        assertTrue(f.core.snapshot(f.plan,"2").target().isEmpty());
    }
    @Test void mappedReferencesShareIdentityTokenAndExactCoordinatesWhileRawSecretNeedsConsent() throws Exception {
        var f=new studio.environment.server.planning.V3StructuralWireFixtures();var sessions=new studio.environment.server.session.HostedSessions(java.time.Clock.systemUTC(),List.of());var runtime=new PlanRuntime(f.service,List.of(),(owner,id)->true);var controller=new V3PlanDocumentViewController(runtime,sessions);
        var two=(studio.environment.core.plan.PlanCommand.Ref.Existing)f.reference("two");var ref=Map.of("kind","existing","handle",two.handle());
        var raw=call(runtime,controller,f.lease,f.plan,false,document("2","current","raw"),200);assertEquals(studio.environment.server.planning.V3StructuralWireFixtures.XML,raw.get("text").asString());assertTrue(raw.get("text").asString().contains("MOCK-STRUCTURAL-SECRET"));
        var rail=V3PlanStructuralViewControllerTest.call(runtime,new V3PlanStructuralViewController(runtime,sessions),f.lease,f.plan,"bindings",Map.of("revision","2","entity",ref,"offset",0,"limit",100),200);
        String token=V3PlanStructuralViewControllerTest.field(rail,"id").get("token").asString();
        var placeholders=call(runtime,controller,f.lease,f.plan,false,document("2","current","placeholders"),200).get("text").asString();
        assertTrue(placeholders.contains("next='"+token+"'"));assertTrue(placeholders.contains("id='"+token+"'"));assertFalse(placeholders.contains("MOCK-STRUCTURAL-SECRET"));
        var locations=call(runtime,controller,f.lease,f.plan,true,locations("2",ref,"id","current",0),200);assertEquals(2,locations.get("total").asInt());
        assertEquals(List.of("reference","field"),List.of(locations.get("items").get(0).get("role").asString(),locations.get("items").get(1).get("role").asString()));
        String xml=raw.get("text").asString();for(var location:locations.get("items")){int start=location.get("span").get("start").asInt(),end=location.get("span").get("end").asInt();assertEquals("two",xml.substring(start,end));assertEquals(f.snapshot("2").current().orElseThrow().sources().getFirst().digest(),location.get("sourceDigest").asString());}
    }
    @Test void materializedFreshHasOnlyTargetLocationsAndOriginalTokenDoesNotTransfer() throws Exception {
        var f=new V3PlanMaterializationControllerTest.Fixture();var controller=new V3PlanDocumentViewController(f.runtime,f.sessions);
        var fresh=new studio.environment.core.plan.PlanCommand.Ref.Fresh("new-one","item");var old=new studio.environment.core.plan.PlanCommand.Ref.Existing(f.core.handle(f.plan,"2","one"));var source=f.core.snapshot(f.plan,"2").current().orElseThrow().sources().getFirst();
        var fields=Map.<String,studio.environment.core.planning.TargetIntent.FieldValue>of("id",new studio.environment.core.planning.TargetIntent.FieldValue.Entered("one"),"tone",new studio.environment.core.planning.TargetIntent.FieldValue.Entered("gamma"),"finish",new studio.environment.core.planning.TargetIntent.FieldValue.Entered("z"));
        var create=new studio.environment.core.plan.PlanCommand.Change(new studio.environment.core.plan.PlanCommand.Entity.Create(fresh,fields,Map.of()),List.of(new studio.environment.core.plan.PlanCommand.Placement(fresh,"sheet","items",new studio.environment.core.plan.PlanCommand.Parent.Existing("sheet",source.digest(),"0"))));
        var remove=new studio.environment.core.plan.PlanCommand.Change(new studio.environment.core.plan.PlanCommand.Entity.Remove(old),List.of());
        try(var admission=f.core.service.reserveCommand(f.core.lease,f.plan,studio.environment.core.plan.PlanDefinition.Version.V3)){admission.execute(new studio.environment.core.plan.PlanCommand(new studio.environment.core.plan.HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new studio.environment.core.plan.PlanCommand.Action.Batch(List.of(remove,create),List.of())));}
        var freshRef=Map.of("kind","fresh","slotId","new-one","typeId","item");var oldRef=Map.of("kind","existing","handle",old.handle());
        call(f.runtime,controller,f.core.lease,f.plan,true,locations("3",freshRef,"id","current",0),404);
        var missing=call(f.runtime,controller,f.core.lease,f.plan,true,locations("3",oldRef,"id","target",0),200);assertEquals(0,missing.get("total").asInt());
        var location=call(f.runtime,controller,f.core.lease,f.plan,true,locations("3",freshRef,"id","target",0),200);assertEquals(1,location.get("total").asInt());
        var raw=call(f.runtime,controller,f.core.lease,f.plan,false,document("3","target","raw"),200).get("text").asString();
        assertEquals("<items><!-- mock -->\r\n<item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/><item xmlns=\"\" finish=\"z\" id=\"one\" tone=\"gamma\"/></items>",raw);
        var span=location.get("items").get(0).get("span");assertEquals("one",raw.substring(span.get("start").asInt(),span.get("end").asInt()));
        var structural=new V3PlanStructuralViewController(f.runtime,f.sessions);var bindings=V3PlanStructuralViewControllerTest.call(f.runtime,structural,f.core.lease,f.plan,"bindings",Map.of("revision","3","entity",freshRef,"offset",0,"limit",100),200);var removed=V3PlanStructuralViewControllerTest.call(f.runtime,structural,f.core.lease,f.plan,"bindings",Map.of("revision","3","entity",oldRef,"offset",0,"limit",100),200);
        String token=V3PlanStructuralViewControllerTest.field(bindings,"id").get("token").asString(),oldToken=V3PlanStructuralViewControllerTest.field(removed,"id").get("token").asString();assertNotEquals(oldToken,token);
        var placeholders=call(f.runtime,controller,f.core.lease,f.plan,false,document("3","target","placeholders"),200).get("text").asString();assertTrue(placeholders.contains(token));assertFalse(placeholders.contains(oldToken));
    }
    @Test void bothRoutesRollBackAndRejectV2BeforeReadingDisclosedBody() throws Exception {
        var f=new V3PlanMaterializationControllerTest.Fixture();var controller=new V3PlanDocumentViewController(f.runtime,f.sessions);
        var request=new org.springframework.mock.web.MockHttpServletRequest(){@Override public jakarta.servlet.ServletInputStream getInputStream(){throw new AssertionError("UNADMITTED_BODY");}@Override public jakarta.servlet.AsyncContext startAsync(){throw new AssertionError("UNADMITTED_ASYNC");}};
        request.setContentType("application/json");request.setAttribute(studio.environment.server.session.HostedSessions.REQUEST_LEASE,f.core.lease);var retained=f.runtime.transfers().admitSemantic(f.core.lease);
        try {for(boolean locations:List.of(false,true)){
            assertEquals(studio.environment.core.plan.PlanRefusal.Code.CAPACITY,assertThrows(studio.environment.core.plan.PlanRefusal.class,()->{if(locations)controller.bindingLocations(f.plan,request,new org.springframework.mock.web.MockHttpServletResponse());else controller.document(f.plan,request,new org.springframework.mock.web.MockHttpServletResponse());}).code());
            try(var recovered=assertDoesNotThrow(()->f.core.service.reserveView(f.core.lease,f.plan,studio.environment.core.plan.PlanDefinition.Version.V3))){assertTrue(recovered.live());}
        }}finally{retained.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);}
        var legacy=new PlanV1VersionBoundaryTest.Fixture();var old=legacy.create(false);var runtime=new PlanRuntime(legacy.service,List.of(),(owner,id)->true);var wrong=new V3PlanDocumentViewController(runtime,f.sessions);var held=runtime.transfers().admitSemantic(legacy.lease);
        try{for(boolean locations:List.of(false,true))assertEquals(studio.environment.core.plan.PlanRefusal.Code.NOT_FOUND,assertThrows(studio.environment.core.plan.PlanRefusal.class,()->{if(locations)wrong.bindingLocations(old.planId(),PlanV1VersionBoundaryTest.unread(legacy),new org.springframework.mock.web.MockHttpServletResponse());else wrong.document(old.planId(),PlanV1VersionBoundaryTest.unread(legacy),new org.springframework.mock.web.MockHttpServletResponse());}).code());}
        finally{held.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);}
    }
    @Test void heldDisclosedOutputStillLosesOriginalPinAndCannotAppendError() throws Exception {
        for(boolean locations:List.of(false,true)){
            var f=new V3PlanMaterializationControllerTest.Fixture();var controller=new V3PlanDocumentViewController(f.runtime,f.sessions);var ref=Map.of("kind","existing","handle",f.core.handle(f.plan,"2","one"));
            var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();output.ready=false;String operation=null;
            try{
                var request=f.body(context,JSON.writeValueAsString(locations?locations("2",ref,"id","current",0):document("2","current","raw")));
                if(locations)controller.bindingLocations(f.plan,request,V3PlanTransportTest.response(output));else controller.document(f.plan,request,V3PlanTransportTest.response(output));
                assertTrue(output.checked.await(3,TimeUnit.SECONDS));assertEquals(studio.environment.core.plan.PlanRefusal.Code.CAPACITY,assertThrows(studio.environment.core.plan.PlanRefusal.class,()->f.core.service.reserveView(f.core.lease,f.plan,studio.environment.core.plan.PlanDefinition.Version.V3)).code());
                operation=f.core.service.reserve(f.core.lease,f.plan,new studio.environment.core.plan.HostedPlanService.Mutation("2",UUID.randomUUID().toString()),studio.environment.core.plan.PlanDefinition.Version.V3).operationId().orElseThrow();
                assertTrue(context.complete.await(3,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);assertEquals(0,output.bytes.size());
            }finally{output.ready=true;if(operation!=null)f.core.service.cancel(f.core.lease,operation,studio.environment.core.plan.PlanDefinition.Version.V3);}
            try(var recovered=f.core.service.reserveView(f.core.lease,f.plan,studio.environment.core.plan.PlanDefinition.Version.V3)){assertTrue(recovered.live());}
        }
    }
    static Map<String,Object> document(String revision,String side,String mode){return Map.of("revision",revision,"side",side,"documentId","sheet","mode",mode,"completeDocumentDisclosure",true);}
    static Map<String,Object> locations(String revision,Map<String,?> ref,String field,String side,int offset){return Map.of("revision",revision,"entity",ref,"fieldId",field,"side",side,"offset",offset,"limit",100,"completeDocumentDisclosure",true);}
    static tools.jackson.databind.JsonNode call(PlanRuntime runtime,V3PlanDocumentViewController controller,studio.environment.core.session.SessionLedger.Lease lease,String plan,boolean locations,Map<String,?> body,int status)throws Exception {
        var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();var response=V3PlanTransportTest.response(output);var request=V3PlanTransportTest.bodyRequest(context,JSON.writeValueAsString(body));request.setAttribute(studio.environment.server.session.HostedSessions.REQUEST_LEASE,lease);
        if(locations)controller.bindingLocations(plan,request,response);else controller.document(plan,request,response);
        assertTrue(context.complete.await(3,TimeUnit.SECONDS));V3PlanTransportTest.settled(runtime.transfers(),lease);assertEquals(status,response.getStatus(),output.bytes.toString(java.nio.charset.StandardCharsets.UTF_8));return JSON.readTree(output.bytes.toByteArray());
    }
}
