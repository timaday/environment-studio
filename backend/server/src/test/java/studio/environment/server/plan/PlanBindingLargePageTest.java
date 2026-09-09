package studio.environment.server.plan;

import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import studio.environment.core.definitionv2.*;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.projection.*;

/** Accepted independent maximum-edge shape; this is pagination proof, not heap qualification. */
class PlanBindingLargePageTest {
    @Test void completeCountIncludesAllFiftyThousandInboundReferencesAndOwnField() throws Exception {
        var original=new PlanContentAdapterTest().definition();var declared=original.compiled().checked().definition();
        var relations=new ArrayList<Relation>();var mappings=new ArrayList<NativeDefinition.ReferenceMapping>();
        for(int i=0;i<5;i++){String id="uses-"+i;relations.add(new Relation(id,"glyph","palette",RelationKind.REFERENCE,BigInteger.ONE,BigInteger.ONE,true));mappings.add(new NativeDefinition.ReferenceMapping(id,new NativeDefinition.ExpandedName("","palette-"+i)));}
        var oldBinding=declared.bindings().getFirst();var oldProjection=oldBinding.documents().getFirst().entities().getFirst();var projection=new NativeDefinition.Projection("glyphs","glyph",oldProjection.path(),oldProjection.fields(),mappings);
        var documents=new ArrayList<NativeDefinition.Document>();var sources=new ArrayList<DocumentSource>();
        for(int page=0;page<5;page++) {
            String id="glyphs-"+page;documents.add(new NativeDefinition.Document(id,Integer.toString(page+1),List.of(new NativeDefinition.Projection("glyphs-"+page,"glyph",projection.path(),projection.fields(),projection.references()))));var xml=new StringBuilder("<tiles xmlns='urn:mock:tiles'>");
            for(int row=0;row<2000;row++){xml.append("<glyph id='g").append(page*2000+row).append("' tone='x'");for(int relation=0;relation<5;relation++)xml.append(" palette-").append(relation).append("='shared'");xml.append("/>");}xml.append("</tiles>");sources.add(new DocumentSource(id,xml.toString()));
        }
        var palette=oldBinding.documents().get(1);documents.add(new NativeDefinition.Document("palette-sheet","6",palette.entities()));sources.add(new DocumentSource("palette-sheet","<tiles xmlns='urn:mock:tiles'><palette id='shared' shade='warm'/></tiles>"));
        var binding=new NativeDefinition.Binding("mock-pg",oldBinding.engine(),oldBinding.storage(),oldBinding.schema(),oldBinding.table(),oldBinding.keyColumn(),oldBinding.xmlColumn(),oldBinding.keyType(),documents);
        var logical=new NativeDefinition.Logical(declared.logical().entityTypes(),relations,List.of(new NativeDefinition.CountRule("glyph-count","glyph",BigInteger.valueOf(10000),BigInteger.valueOf(10000)),new NativeDefinition.CountRule("palette-count","palette",BigInteger.ONE,BigInteger.ONE)),declared.logical().operationCapabilities());
        var ready=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,new NativeDefinitionCompiler().compile(new NativeDefinition("mock-many-links",BigInteger.ONE,logical,List.of(binding))));
        var projected=assertInstanceOf(ProjectionResult.Accepted.class,new GraphProjectionAdapter().project(ready,"mock-pg",sources));assertEquals(10001,projected.graph().entities().size());assertEquals(50000,projected.graph().edges().size());
        var provenance=new HashMap<ObservedGraph.Key,TargetIntent.Ref>();var handles=new HashMap<TargetIntent.Ref,String>();var refs=new HashMap<TargetIntent.Ref,PlanCommand.Ref>();
        int sequence=1;PlanCommand.Ref paletteRef=null;
        for(var entity:projected.graph().entities()) {var ref=new TargetIntent.Ref.Existing(entity.key());String handle=new UUID(0,sequence++).toString();var wire=new PlanCommand.Ref.Existing(handle);provenance.put(entity.key(),ref);handles.put(ref,handle);refs.put(ref,wire);if(entity.key().type().equals("palette"))paletteRef=wire;}
        var content=new Content(projected.projection().documents().stream().map(d->new Source(d.documentId(),d.source(),d.digest())).toList(),projected.graph(),provenance);
        var definition=new PublishedDefinition(original.reference(),"independent-large-mapping",ready,List.of());var snapshot=new HostedPlanService.ViewSnapshot("2",definition,"mock-pg",Optional.of(content),Optional.of(content),Draft.empty(),refs,handles);
        var last=PlanBindingViews.locations(snapshot,paletteRef,"tag",false,49999,2,true);PlanBindingViewsTest.shape("bindingLocationsResponse",last);
        assertEquals(50001,last.get("total"));assertNull(last.get("nextOffset"));var items=(List<?>)last.get("items");assertEquals(2,items.size());assertEquals("reference",((Map<?,?>)items.get(0)).get("role"));assertEquals("field",((Map<?,?>)items.get(1)).get("role"));assertEquals("palette-sheet",((Map<?,?>)items.get(1)).get("documentId"));
        var beyond=PlanBindingViews.locations(snapshot,paletteRef,"tag",false,50001,100,true);assertEquals(50001,beyond.get("total"));assertEquals(List.of(),beyond.get("items"));assertNull(beyond.get("nextOffset"));
    }
}
