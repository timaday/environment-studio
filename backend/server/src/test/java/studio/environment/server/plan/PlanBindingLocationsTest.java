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
import studio.environment.server.xml.XmlDocument;

class PlanBindingLocationsTest {
    static final TargetIntent.Ref.Existing PALETTE=new TargetIntent.Ref.Existing(new ObservedGraph.Key("palette","shared"));
    static final TargetIntent.Ref.Existing ALPHA=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
    static final TargetIntent.Ref.Fresh FRESH=new TargetIntent.Ref.Fresh("fresh-palette","palette");
    static final String PALETTE_HANDLE="00000000-0000-4000-8000-000000000013",FRESH_HANDLE="00000000-0000-4000-8000-000000000014";
    final PlanContentAdapterTest fixture=new PlanContentAdapterTest();
    PublishedDefinition definition() throws Exception {
        // Independently invented fixture: explicitly permit identity edits and removal under test.
        var source=Files.readString(Path.of("../../fixtures/native-v2/definition.json")).replace("\"editable\": false","\"editable\": true").replace("\"minimum\": 2","\"minimum\": 0");
        var ready=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,new NativeDefinitionBytesCompiler().compile(source.getBytes(java.nio.charset.StandardCharsets.UTF_8),DefinitionBytesCompiler.Format.JSON));
        return new PublishedDefinition(new NativeCommand.Reference("00000000-0000-4000-8000-000000000001","2"),"mock-bindings",ready,List.of());
    }
    HostedPlanService.ViewSnapshot snapshot(Draft draft) throws Exception {
        var definition=definition();var current=assertInstanceOf(ContentResult.Complete.class,fixture.adapter.project(definition,"mock-pg",fixture.observation(definition))).content();
        var target=assertInstanceOf(ContentResult.Complete.class,fixture.adapter.materialize(definition,"mock-pg",current,draft)).content();
        var refs=new HashMap<TargetIntent.Ref,PlanCommand.Ref>();var handles=new HashMap<TargetIntent.Ref,String>();
        for(var ref:current.provenance().values()){
            var old=(TargetIntent.Ref.Existing)ref;String handle=old.equals(PALETTE)?PALETTE_HANDLE:old.equals(ALPHA)?"00000000-0000-4000-8000-000000000011":"00000000-0000-4000-8000-000000000012";
            handles.put(ref,handle);refs.put(ref,new PlanCommand.Ref.Existing(handle));
        }
        if(draft.intent().entities().stream().anyMatch(e->e.entity().equals(FRESH)))handles.put(FRESH,FRESH_HANDLE);
        return new HostedPlanService.ViewSnapshot("8",definition,"mock-pg",Optional.of(current),Optional.of(target),draft,refs,handles);
    }
    static List<PlanBindingLocations.Location> locations(HostedPlanService.ViewSnapshot snapshot,boolean target,TargetIntent.Ref ref,String field){
        var list=new ArrayList<PlanBindingLocations.Location>();PlanBindingLocations.scan(snapshot,target,item->{if(item.entity().equals(ref) && item.fieldId().equals(field))list.add(item.location());});return list;
    }
    static Source source(Content content,String id){return content.sources().stream().filter(s->s.documentId().equals(id)).findFirst().orElseThrow();}
    static Draft rename(){
        var decisions=new ArrayList<TargetIntent.EntityDecision>();
        decisions.add(new TargetIntent.EntityDecision.Retain(PALETTE,Map.of("tag",new TargetIntent.FieldValue.Entered("renamed"),"shade",new TargetIntent.FieldValue.KeepObserved()),Map.of()));
        // Renaming does not authorize an inferred rewrite: select both referring fields explicitly.
        for(var identity:List.of("alpha","beta"))decisions.add(new TargetIntent.EntityDecision.Retain(new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph",identity)),Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.KeepObserved()),Map.of("uses",new TargetIntent.ReferenceValue.To(PALETTE))));
        return new Draft(new TargetIntent(decisions,List.of()),List.of());
    }
    @Test void identityRenameMapsEveryInboundReferenceAndExactAttributeSpan() throws Exception {
        var view=snapshot(rename());
        for(boolean target:List.of(false,true)){
            var hits=locations(view,target,PALETTE,"tag");assertEquals(3,hits.size());assertEquals(List.of("glyph-sheet","glyph-sheet","palette-sheet"),hits.stream().map(PlanBindingLocations.Location::documentId).toList());assertEquals(List.of("reference","reference","field"),hits.stream().map(PlanBindingLocations.Location::role).toList());
            var content=view.selected(target);var glyphs=source(content,"glyph-sheet");var palettes=source(content,"palette-sheet");String value=target?"renamed":"shared";
            int first=glyphs.xml().indexOf("palette=\""+value+"\"")+9;int second=glyphs.xml().indexOf("palette=\""+value+"\"",first)+9;int own=palettes.xml().indexOf("id='"+value+"'")+4;
            assertEquals(List.of(new XmlDocument.Span(first,first+value.length()),new XmlDocument.Span(second,second+value.length()),new XmlDocument.Span(own,own+value.length())),hits.stream().map(PlanBindingLocations.Location::span).toList());
            assertEquals(List.of(1,2,1),hits.stream().map(PlanBindingLocations.Location::elementIndex).toList());assertEquals(List.of("uses","uses","tag"),hits.stream().map(PlanBindingLocations.Location::declarationId).toList());
            for(var hit:hits){var raw=source(content,hit.documentId());assertEquals(raw.digest(),hit.sourceDigest());assertEquals(value,raw.xml().substring(hit.span().start(),hit.span().end()));assertEquals("",hit.attribute().namespaceUri());}
        }
        assertTrue(source(view.selected(true),"glyph-sheet").xml().contains("<![CDATA[<glyph palette=\"shared\"/>]]>"));
    }
    @Test void retargetedReferenceMovesToFreshIdentityAndUnchangedSiblingStillCounts() throws Exception {
        var base=snapshot(Draft.empty());var paletteSource=source(base.selected(false),"palette-sheet");
        var draft=new Draft(new TargetIntent(List.of(
            new TargetIntent.EntityDecision.Retain(ALPHA,Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.KeepObserved()),Map.of("uses",new TargetIntent.ReferenceValue.To(FRESH))),
            new TargetIntent.EntityDecision.Create(FRESH,Map.of("tag",new TargetIntent.FieldValue.Entered("new"),"shade",new TargetIntent.FieldValue.Entered("cool")),Map.of())),List.of()),List.of(new Placement(FRESH,"palette-sheet","palettes",new Parent.Existing("palette-sheet",paletteSource.digest(),0))));
        var view=snapshot(draft);assertEquals(3,locations(view,false,PALETTE,"tag").size());assertEquals(2,locations(view,true,PALETTE,"tag").size());assertEquals(2,locations(view,true,FRESH,"tag").size());assertEquals(0,locations(view,false,FRESH,"tag").size());
        assertEquals(List.of("glyph-sheet","palette-sheet"),locations(view,true,FRESH,"tag").stream().map(PlanBindingLocations.Location::documentId).toList());
        var empty=locations(view,false,new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","beta")),"tone");assertEquals(1,empty.size());assertEquals(empty.getFirst().span().start(),empty.getFirst().span().end());
    }
    @Test void oneParentWithTwoChildrenDoesNotInventAttributeReferences() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var declaration=(tools.jackson.databind.node.ObjectNode)json.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")));
        var logical=(tools.jackson.databind.node.ObjectNode)declaration.get("logical");
        var relation=logical.putArray("relations").addObject();relation.put("id","contains");relation.put("fromType","palette");relation.put("toType","glyph");relation.put("kind","containment");relation.put("minimum",0);relation.put("maximum",10);relation.put("includeTargetOnReuse",false);
        for(var binding:declaration.get("bindings")) {
            var old=binding.get("documents");var glyph=(tools.jackson.databind.node.ObjectNode)old.get(0).get("entities").get(0).deepCopy();var palette=old.get(1).get("entities").get(0).deepCopy();
            glyph.putArray("references");var path=glyph.putArray("path");path.add(palette.get("path").get(0));path.add(palette.get("path").get(1));path.addObject().put("namespaceUri","urn:mock:tiles").put("localName","glyph");
            var document=((tools.jackson.databind.node.ObjectNode)binding).putArray("documents").addObject();document.put("id","tree");document.put("key","1");document.putArray("entities").add(palette).add(glyph);
        }
        var ready=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,new NativeDefinitionBytesCompiler().compile(json.writeValueAsBytes(declaration),DefinitionBytesCompiler.Format.JSON));
        String xml="<tiles xmlns='urn:mock:tiles'><palette id='shared' shade='warm'><glyph id='alpha' tone='one'/><glyph id='beta' tone='two'/></palette></tiles>";
        var projected=assertInstanceOf(studio.environment.server.projection.ProjectionResult.Accepted.class,new studio.environment.server.projection.GraphProjectionAdapter().project(ready,"mock-pg",List.of(new studio.environment.server.projection.DocumentSource("tree",xml))));
        var provenance=new HashMap<ObservedGraph.Key,TargetIntent.Ref>();var handles=new HashMap<TargetIntent.Ref,String>();var refs=new HashMap<TargetIntent.Ref,PlanCommand.Ref>();
        int count=10;for(var entity:projected.graph().entities()){var ref=new TargetIntent.Ref.Existing(entity.key());String handle=new UUID(0,++count).toString();provenance.put(entity.key(),ref);handles.put(ref,handle);refs.put(ref,new PlanCommand.Ref.Existing(handle));}
        var content=new Content(List.of(new Source("tree",xml,projected.projection().documents().getFirst().digest())),projected.graph(),provenance);
        var def=new PublishedDefinition(new NativeCommand.Reference("00000000-0000-4000-8000-000000000001","2"),"mock-tree",ready,List.of());
        var view=new HostedPlanService.ViewSnapshot("8",def,"mock-pg",Optional.of(content),Optional.of(content),Draft.empty(),refs,handles);
        assertEquals(2,content.graph().edges().size());var hits=locations(view,false,PALETTE,"tag");assertEquals(1,hits.size());assertEquals("field",hits.getFirst().role());
    }
    @Test void qualifiedLookalikeAttributeIsNeitherMappedNorReplaced() throws Exception {
        var base=snapshot(Draft.empty());var inputs=base.selected(false).sources().stream().map(s->new studio.environment.server.projection.DocumentSource(s.documentId(),s.xml().replace("id=\"alpha\"","id=\"alpha\" t:palette=\"shared\""))).toList();
        var projection=assertInstanceOf(studio.environment.server.projection.ProjectionResult.Accepted.class,new studio.environment.server.projection.GraphProjectionAdapter().project(base.definition().compiled(),base.binding(),inputs));
        var content=new Content(projection.projection().documents().stream().map(d->new Source(d.documentId(),d.source(),d.digest())).toList(),projection.graph(),base.selected(false).provenance());
        var view=new HostedPlanService.ViewSnapshot(base.revision(),base.definition(),base.binding(),Optional.of(content),Optional.of(content),base.draft(),base.references(),base.displayHandles());
        var hits=locations(view,false,PALETTE,"tag");assertEquals(3,hits.size());assertTrue(hits.stream().allMatch(l->l.attribute().namespaceUri().isEmpty()));
        String text=fixture.adapter.compare(view,false,"glyph-sheet",ViewMode.PLACEHOLDERS).text();assertTrue(text.contains("t:palette=\"shared\""));assertTrue(text.contains(" palette=\"[[value:"+PALETTE_HANDLE+":tag]]\""));
    }
    @Test void missingAndInconsistentProjectionEvidenceRefusesCompleteMapping() throws Exception {
        var view=snapshot(Draft.empty());var current=view.selected(false);
        var sources=new ArrayList<>(current.sources());var old=sources.getFirst();sources.set(0,new Source(old.documentId(),old.xml(),"0".repeat(64)));
        var missing=new HashMap<>(current.provenance());missing.remove(PALETTE.key());
        var alias=new HashMap<>(current.provenance());alias.put(PALETTE.key(),ALPHA);
        var extra=new HashMap<>(current.provenance());extra.put(new ObservedGraph.Key("glyph","not-present"),new TargetIntent.Ref.Fresh("not-present","glyph"));
        var entities=new ArrayList<>(current.graph().entities());var original=entities.getFirst();entities.set(0,new ObservedGraph.Entity(original.key(),Map.of("tag",original.key().identity(),"tone","inconsistent"),original.origin()));
        for(var invalid:List.of(new Content(sources,current.graph(),current.provenance()),new Content(current.sources(),current.graph(),missing),new Content(current.sources(),current.graph(),alias),new Content(current.sources(),current.graph(),extra),new Content(current.sources(),new ObservedGraph(entities,current.graph().edges()),current.provenance()),new Content(current.sources().subList(0,1),current.graph(),current.provenance()))){
            var bad=new HostedPlanService.ViewSnapshot(view.revision(),view.definition(),view.binding(),Optional.of(invalid),view.target(),view.draft(),view.references(),view.displayHandles());
            assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->locations(bad,false,PALETTE,"tag")).code());
        }
    }
}
