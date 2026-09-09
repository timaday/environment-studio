package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import java.math.BigInteger;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.planning.TargetIntent.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import studio.environment.server.plan.V3PlanPhysicalViews;

class SharedV3PlanPhysicalViewsTest {
    @Test void originalBindingValuesHaveCompleteLocationsWhileTargetIsUnavailable() {
        var fixture=new SharedV3PlanXmlTest();var service=fixture.service();String plan=fixture.inspected(service);
        try(var view=service.reserveView(fixture.lease,plan)) {
            view.run(()->{
                view.pin("2");var snapshot=view.snapshot();var ref=snapshot.reference(old("one"));
                assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,()->PlanBindings.resolve(snapshot,ref)).code());
                var page=assertDoesNotThrow(()->V3PlanPhysicalViews.bindings(view,ref,0,100));
                assertEquals("2",page.get("revision"));assertEquals(3,page.get("total"));assertNull(page.get("nextOffset"));
                var rows=rows(page);assertEquals(List.of("finish","id","tone"),rows.stream().map(r->r.get("fieldId")).toList());
                assertEquals(List.of(Map.of("state","value","text","x"),Map.of("state","value","text","one"),Map.of("state","value","text","alpha")),rows.stream().map(r->r.get("current")).toList());
                for(var row:rows){assertEquals(Map.of("state","complete","total",1),row.get("currentLocations"));assertEquals(Map.of("state","unavailable","code","INCOMPLETE_TARGET"),row.get("targetLocations"));}
                assertTrue(snapshot.target().isEmpty());return true;
            });
        }
    }
    @Test void originalPhysicalPagesAndParentChoicesDoNotExposeComputedEntitiesOrInventTarget() {
        var fixture=new SharedV3PlanXmlTest();var service=fixture.service();String plan=fixture.inspected(service);
        try(var view=service.reserveView(fixture.lease,plan)) {
            view.run(()->{
                view.pin("2");var snapshot=view.snapshot();String digest=snapshot.current().orElseThrow().sources().getFirst().digest();
                var documents=assertDoesNotThrow(()->V3PlanPhysicalViews.documents(view));
                @SuppressWarnings("unchecked") var inventory=(List<Map<String,Object>>)documents.get("documents");
                assertEquals(1,inventory.size());assertEquals("sheet",inventory.getFirst().get("documentId"));
                assertEquals(digest,inventory.getFirst().get("currentDigest"));assertNull(inventory.getFirst().get("targetDigest"));assertNull(inventory.getFirst().get("changed"));
                var entities=V3PlanPhysicalViews.entities(view,false,0,2);assertEquals(3,entities.get("total"));assertEquals(2,entities.get("nextOffset"));
                assertEquals(List.of("item","item"),rows(entities).stream().map(e->e.get("typeId")).toList());
                assertEquals(1,rows(V3PlanPhysicalViews.entities(view,false,2,2)).size());
                assertEquals(0,V3PlanPhysicalViews.relations(view,false,0,100).get("total"));
                assertEquals(0,V3PlanPhysicalViews.draft(view,0,100).get("total"));assertEquals(0,V3PlanPhysicalViews.containment(view,0,100).get("total"));
                assertEquals(List.of(Map.of("documentId","sheet","sourceDigest",digest,"elementIndex","0")),rows(V3PlanPhysicalViews.placements(view,"sheet","items",0,100)));
                assertEquals(studio.environment.core.plan.PlanRefusal.Code.INCOMPLETE_TARGET,assertThrows(studio.environment.core.plan.PlanRefusal.class,()->V3PlanPhysicalViews.entities(view,true,0,100)).code());
                return true;
            });
        }
    }
    @Test void childLocationsTrackOriginalTokensAndDistinguishAbsentFromUnresolved() {
        String xml="<items xmlns:p='urn:props'><item id='one' finish='x'><p:entry p:key='tone' p:value='alpha'/></item><item id='two' finish='y'/></items>";
        var f=SharedV3PlanXmlTest.with(definition(true),xml);var service=f.service();String plan=f.inspected(service);var original=f.snapshot(service,plan,"2");
        var one=original.reference(old("one"));var two=original.reference(old("two"));String token=PlanBindings.token(original,old("one"),"tone");
        try(var view=service.reserveView(f.lease,plan)){view.run(()->{view.pin("2");
            var absent=field(V3PlanPhysicalViews.bindings(view,two,0,100),"tone");assertEquals(Map.of("state","absent"),absent.get("current"));assertEquals(Map.of("state","complete","total",0),absent.get("currentLocations"));
            var locations=V3PlanPhysicalViews.locations(view,one,"tone",false,0,100,true);assertEquals(1,locations.get("total"));var location=rows(locations).getFirst();
            assertEquals("2",location.get("elementIndex"));assertEquals(Map.of("namespaceUri","urn:props","localName","value"),location.get("attribute"));
            assertEquals("alpha",locatedText(xml,location));assertEquals("field",location.get("role"));assertEquals("tone",location.get("declarationId"));
            assertEquals(PlanRefusal.Code.DISCLOSURE_REQUIRED,assertThrows(PlanRefusal.class,()->V3PlanPhysicalViews.locations(view,one,"tone",false,0,100,false)).code());return true;});}
        service.replaceDraft(f.lease,plan,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new Draft(intent(edit("one",new FieldValue.Entered("renamed"),new FieldValue.Entered("new&tone"))),List.of()));
        try(var view=service.reserveView(f.lease,plan)){view.run(()->{view.pin("3");var target=view.snapshot().target().orElseThrow();
            var tone=field(V3PlanPhysicalViews.bindings(view,one,0,100),"tone");assertEquals(token,tone.get("token"));assertEquals("changed",tone.get("change"));assertEquals(Map.of("state","value","text","new&tone"),tone.get("target"));
            var location=rows(V3PlanPhysicalViews.locations(view,one,"tone",true,0,1,true)).getFirst();assertEquals("new&amp;tone",locatedText(target.sources().getFirst().xml(),location));
            assertEquals(List.of(),rows(V3PlanPhysicalViews.locations(view,one,"tone",true,1,1,true)));return true;});}
        service.replaceDraft(f.lease,plan,new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),new Draft(intent(edit("one",new FieldValue.KeepObserved(),new FieldValue.Unresolved())),List.of()));
        try(var view=service.reserveView(f.lease,plan)){view.run(()->{view.pin("4");var tone=field(V3PlanPhysicalViews.bindings(view,one,0,100),"tone");
            assertEquals(Map.of("state","unresolved"),tone.get("target"));assertEquals("unresolved",tone.get("change"));assertEquals(Map.of("state","unavailable","code","INCOMPLETE_TARGET"),tone.get("targetLocations"));
            assertEquals(PlanRefusal.Code.INCOMPLETE_TARGET,assertThrows(PlanRefusal.class,()->V3PlanPhysicalViews.locations(view,one,"tone",true,0,100,true)).code());
            @SuppressWarnings("unchecked") var fields=(List<Map<String,Object>>)rows(V3PlanPhysicalViews.draft(view,0,100)).getFirst().get("fields");
            assertEquals("unresolved",fields.stream().filter(x->x.get("fieldId").equals("tone")).findFirst().orElseThrow().get("kind"));return true;});}
    }
    @Test void sameLiteralFreshReplacementHasSeparateCurrentAndTargetBindingOrigins() {
        var f=new SharedV3PlanXmlTest();var service=f.service();String plan=f.inspected(service);var original=f.snapshot(service,plan,"2");
        var one=(PlanCommand.Ref.Existing)original.reference(old("one"));var fresh=new PlanCommand.Ref.Fresh("replacement","item");var source=original.current().orElseThrow().sources().getFirst();
        var create=new PlanCommand.Change(new PlanCommand.Entity.Create(fresh,Map.of("id",new FieldValue.Entered("one"),"tone",new FieldValue.Entered("gamma"),"finish",new FieldValue.Entered("z")),Map.of()),List.of(new PlanCommand.Placement(fresh,"sheet","items",new PlanCommand.Parent.Existing("sheet",source.digest(),"0"))));
        service.command(f.lease,plan,new PlanCommand(new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new PlanCommand.Action.Batch(List.of(new PlanCommand.Change(new PlanCommand.Entity.Remove(one),List.of()),create),List.of())));
        try(var view=service.reserveView(f.lease,plan)){view.run(()->{view.pin("3");
            var removed=field(V3PlanPhysicalViews.bindings(view,one,0,100),"id");var replacement=field(V3PlanPhysicalViews.bindings(view,fresh,0,100),"id");
            assertEquals(Map.of("state","absent"),removed.get("target"));assertEquals("removed",removed.get("change"));assertEquals(Map.of("state","complete","total",0),removed.get("targetLocations"));
            assertEquals(Map.of("state","unavailable"),replacement.get("current"));assertEquals(Map.of("state","unavailable","code","CURRENT_ENTITY_ABSENT"),replacement.get("currentLocations"));
            assertEquals(Map.of("state","value","text","one"),replacement.get("target"));assertEquals("added",replacement.get("change"));assertNotEquals(removed.get("token"),replacement.get("token"));
            assertEquals(3,V3PlanPhysicalViews.entities(view,true,0,100).get("total"));assertEquals(1,V3PlanPhysicalViews.locations(view,fresh,"id",true,0,100,true).get("total"));
            assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->V3PlanPhysicalViews.locations(view,fresh,"id",false,0,100,true)).code());return true;});}
    }
    @Test void completeTargetProofCannotBeReplacedWithPhysicalGraphOnly() {
        var f=new SharedV3PlanXmlTest();var adapter=new SharedV3PlanCompositionAuthorityTest.ControlledContent();var service=f.service(adapter);String plan=f.inspected(service);
        adapter.forge=true;assertTrue(service.materialize(f.lease,plan,"2").complete());
        try(var view=service.reserveView(f.lease,plan)){view.run(()->{view.pin("2");
            assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->V3PlanPhysicalViews.entities(view,true,0,100)).code());
            assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->V3PlanPhysicalViews.documents(view)).code());return true;});}
    }
    @Test void heldActualBindingPageCannotOutliveCloseOrSameRevisionFailedInspection() throws Exception {
        for(boolean reinspect:List.of(false,true)) {
            var f=new SharedV3PlanXmlTest();var service=f.service();String plan=f.inspected(service);var view=service.reserveView(f.lease,plan);
            var entered=new CountDownLatch(1);var released=new CountDownLatch(1);var flag=new AtomicReference<studio.environment.core.observation.ObservationPort.Cancellation>();
            var result=new AtomicReference<Object>();var failure=new AtomicReference<Throwable>();
            var worker=new Thread(()->{try{result.set(view.run(()->{view.pin("2");return view.read((snapshot,control)->{
                var page=V3PlanPhysicalViews.bindings(view,snapshot.reference(old("one")),0,100);flag.set(control);entered.countDown();
                try{assertTrue(released.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}return page;
            });}));}catch(Throwable e){failure.set(e);}});worker.start();
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                if(reinspect){f.xml="<items><item id='one' tone='' finish='x'/></items>";var operation=service.reserve(f.lease,plan,new HostedPlanService.Mutation("2",UUID.randomUUID().toString())).operationId().orElseThrow();
                    assertEquals(HostedPlanService.Phase.REFUSED,service.submit(f.lease,operation,(u,p)->{u[0]='u';p[0]='p';return new CredentialLengths(1,1);}).phase());
                    assertEquals("2",service.summary(f.lease,plan).revision());
                }else view.close();
                assertTrue(flag.get().cancelled());assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->service.reserveView(f.lease,plan)).code());
            }finally{released.countDown();worker.join(5000);view.close();}
            assertFalse(worker.isAlive());assertNull(result.get());assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.get()).code());
            if(reinspect){try(var later=service.reserveView(f.lease,plan)){later.run(()->{later.pin("2");assertEquals(3,V3PlanPhysicalViews.entities(later,false,0,100).get("total"));return true;});}assertFalse(service.summary(f.lease,plan).inspectionValid());}
        }
    }
    @Test void everyPhysicalReferenceAcrossDocumentsIsCountedAndPrivateValuesStayMasked() {
        var base=definition(false).definition();var logical=base.logical();var binding=base.bindings().getFirst();var projection=binding.documents().getFirst().entities().getFirst();
        var originalType=logical.entityTypes().getFirst();var declarations=new ArrayList<>(originalType.fields());
        declarations.add(new Field("note",ValueType.TEXT,false,Classification.ENVIRONMENT,Sensitivity.SECRET,true,true));
        var type=new EntityType(originalType.id(),originalType.label(),declarations,originalType.identity());
        var mappings=new ArrayList<>(projection.fields());mappings.add(new FieldMapping("note",new ExpandedName("","note")));
        var mapped=new Projection(projection.id(),projection.type(),projection.path(),mappings,List.of(new ReferenceMapping("link",new ExpandedName("","next"))));
        var relation=new Relation("link","item","item",RelationKind.REFERENCE,BigInteger.ZERO,BigInteger.ONE,false);
        var operations=new ArrayList<>(logical.operationCapabilities());operations.add(Operation.MOVE_RELATION);
        var v3=new studio.environment.core.definitionv3.NativeDefinition.Logical(List.of(type),List.of(relation),logical.rules(),operations,logical.computedTypes(),logical.derivations(),logical.cooccurrences(),logical.computedRules());
        var multi=new Binding(binding.id(),binding.engine(),binding.storage(),binding.schema(),binding.table(),binding.keyColumn(),binding.xmlColumn(),binding.keyType(),List.of(new Document("sheet","1",List.of(mapped)),new Document("tail","2",List.of(new Projection("tail-items",mapped.type(),mapped.path(),mapped.fields(),mapped.references())))));
        var compilation=new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(new studio.environment.core.definitionv3.NativeDefinition(base.id(),base.revision(),v3,List.of(multi)));
        var checked=assertInstanceOf(studio.environment.core.definitionv3.NativeCompilationResult.Incomplete.class,compilation,compilation instanceof studio.environment.core.definitionv3.NativeCompilationResult.Rejected rejected?rejected.diagnostics().stream().map(d->d.code()).toList().toString():"expected incomplete").checked();
        String sheet="<items><item id='one' tone='alpha' next='two'/></items>";String tail="<items><item id='two' tone='beta' note='PRIVATE-MOCK'/><item id='three' tone='gamma' next='two'/></items>";
        var f=SharedV3PlanXmlTest.with(checked,sheet);f.additionalXml=Map.of("tail",tail);var service=f.service();String plan=f.inspected(service);var original=f.snapshot(service,plan,"2");var two=original.reference(old("two"));
        var choice=new EntityDecision.Retain(old("two"),Map.of("id",new FieldValue.Entered("renamed"),"tone",new FieldValue.KeepObserved(),"finish",new FieldValue.KeepObserved(),"note",new FieldValue.Entered("PRIVATE-CHANGED")),Map.of("link",new ReferenceValue.KeepObserved()));
        service.replaceDraft(f.lease,plan,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new Draft(intent(choice),List.of()));
        var materialized=service.materialize(f.lease,plan,"3");assertFalse(materialized.complete());assertEquals(List.of("RETAINED_REFERENCE_CHANGED"),materialized.diagnostics());
        var complete=new ArrayList<EntityDecision>();complete.add(choice);
        for(String sourceId:List.of("one","three"))complete.add(new EntityDecision.Retain(old(sourceId),Map.of("id",new FieldValue.KeepObserved(),"tone",new FieldValue.KeepObserved(),"finish",new FieldValue.KeepObserved(),"note",new FieldValue.KeepObserved()),Map.of("link",new ReferenceValue.To(old("two")))));
        service.replaceDraft(f.lease,plan,new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),new Draft(new studio.environment.core.planning.TargetIntent(complete,List.of()),List.of()));
        materialized=service.materialize(f.lease,plan,"4");assertTrue(materialized.complete(),materialized.diagnostics().toString());
        try(var view=service.reserveView(f.lease,plan)){view.run(()->{view.pin("4");
            var bindings=V3PlanPhysicalViews.bindings(view,two,0,100);var identity=field(bindings,"id");assertEquals(Map.of("state","complete","total",3),identity.get("currentLocations"));assertEquals(Map.of("state","complete","total",3),identity.get("targetLocations"));
            assertEquals(Map.of("state","masked"),field(bindings,"note").get("current"));assertEquals(Map.of("state","masked"),field(bindings,"note").get("target"));assertEquals("unresolved",field(bindings,"note").get("change"));
            var target=view.snapshot().target().orElseThrow();var targetText=new HashMap<String,String>();target.sources().forEach(source->targetText.put(source.documentId(),source.xml()));
            var coordinates=new ArrayList<String>();var roles=new ArrayList<String>();
            for(int offset=0;offset<3;offset++){var page=V3PlanPhysicalViews.locations(view,two,"id",true,offset,1,true);assertEquals(3,page.get("total"));assertEquals(offset==2?null:offset+1,page.get("nextOffset"));var location=rows(page).getFirst();
                assertEquals("renamed",locatedText(targetText.get(location.get("documentId")),location));coordinates.add(location.get("documentId")+":"+location.get("elementIndex"));roles.add((String)location.get("role"));}
            assertEquals(List.of("sheet:1","tail:1","tail:2"),coordinates);assertEquals(List.of("reference","field","reference"),roles);
            var beyond=V3PlanPhysicalViews.locations(view,two,"id",true,Integer.MAX_VALUE,100,true);assertEquals(3,beyond.get("total"));assertTrue(rows(beyond).isEmpty());assertNull(beyond.get("nextOffset"));
            assertEquals(2,V3PlanPhysicalViews.relations(view,true,0,100).get("total"));
            for(var entity:rows(V3PlanPhysicalViews.entities(view,true,0,100))){@SuppressWarnings("unchecked") var fields=(List<Map<String,Object>>)entity.get("fields");var note=fields.stream().filter(x->x.get("fieldId").equals("note")).findFirst().orElseThrow();assertEquals(true,note.get("masked"));assertNull(note.get("value"));}
            @SuppressWarnings("unchecked") var draftFields=(List<Map<String,Object>>)rows(V3PlanPhysicalViews.draft(view,0,100)).stream().filter(row->row.get("entity").equals(Map.of("kind","existing","handle",((PlanCommand.Ref.Existing)two).handle()))).findFirst().orElseThrow().get("fields");var note=draftFields.stream().filter(x->x.get("fieldId").equals("note")).findFirst().orElseThrow();assertEquals(true,note.get("masked"));assertNull(note.get("value"));
            assertFalse(bindings.toString().contains("PRIVATE-"));return true;});}
    }
    @Test void fullOriginalProofIsRequiredBeforePhysicalInventoryCanBePresented() {
        var actual=new studio.environment.server.plan.PlanContentAdapter();
        var adapter=new ContentAdapter(){
            public ContentResult project(PublishedDefinition d,String b,studio.environment.core.observation.ObservationResult.Observation o){return actual.project(d,b,o);}
            public ContentResult project(PublishedDefinition d,String b,studio.environment.core.observation.ObservationResult.Observation o,studio.environment.core.observation.ObservationPort.Cancellation control){
                var result=(ContentResult.Complete)actual.project(d,b,o,control);var c=result.content();var proof=(PlanContentEvidence.V3Observed)c.evidence();assertFalse(proof.derived().rules().isEmpty());
                return new ContentResult.Complete(new Content(c.sources(),c.graph(),c.provenance(),new PlanContentEvidence.V3Observed(proof.observationFingerprint(),proof.input(),new studio.environment.core.derived.DerivedResult.Complete(proof.derived().graph(),List.of()))));
            }
            public ContentResult materialize(PublishedDefinition d,String b,Content c,Draft draft){return actual.materialize(d,b,c,draft);}
            public Capture capture(PublishedDefinition d,String b,Content c,studio.environment.core.profile.ProfileCapture.Command command){return actual.capture(d,b,c,command);}
        };
        var f=new SharedV3PlanXmlTest();var service=f.service(adapter);String plan=f.inspected(service);
        try(var view=service.reserveView(f.lease,plan)){view.run(()->{view.pin("2");
            assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->V3PlanPhysicalViews.entities(view,false,0,100)).code());
            assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->V3PlanPhysicalViews.placements(view,"sheet","items",0,100)).code());return true;});}
    }
    static Map<String,Object> field(Map<String,Object> page,String field){return rows(page).stream().filter(x->x.get("fieldId").equals(field)).findFirst().orElseThrow();}
    @SuppressWarnings("unchecked") static String locatedText(String xml,Map<String,Object> location){var span=(Map<String,Object>)location.get("span");return xml.substring((int)span.get("start"),(int)span.get("end"));}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> rows(Map<String,Object> page){return (List<Map<String,Object>>)page.get("items");}
}
