package studio.environment.server.plan;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.projection.*;
import tools.jackson.databind.node.ObjectNode;

class ChildPropertyViewsTest {
    private HostedPlanService.ViewSnapshot snapshot(NativeCompilationResult.ReadyToPublish ready,String child) throws Exception {
        var projected=assertInstanceOf(ProjectionResult.Accepted.class,new GraphProjectionAdapter().project(ready,"mock-pg",ChildPropertyProjectionTest.sources(child)));
        var provenance=new HashMap<ObservedGraph.Key,TargetIntent.Ref>();var handles=new HashMap<TargetIntent.Ref,String>();var refs=new HashMap<TargetIntent.Ref,PlanCommand.Ref>();
        int id=10;for(var entity:projected.graph().entities()){var ref=new TargetIntent.Ref.Existing(entity.key());String handle=new UUID(0,++id).toString();provenance.put(entity.key(),ref);handles.put(ref,handle);refs.put(ref,new PlanCommand.Ref.Existing(handle));}
        var content=new Content(projected.projection().documents().stream().map(d->new Source(d.documentId(),d.source(),d.digest())).toList(),projected.graph(),provenance);
        var definition=new PublishedDefinition(new NativeCommand.Reference("00000000-0000-4000-8000-000000000001","2"),"mock-child",ready,List.of());
        return new HostedPlanService.ViewSnapshot("3",definition,"mock-pg",Optional.of(content),Optional.of(content),Draft.empty(),refs,handles);
    }
    @Test void childLocationsAndPlaceholdersUseActualAttributeWhileSelectorStaysExact() throws Exception {
        var view=snapshot(ChildPropertyProjectionTest.definition(true),"<p:entry k:key='tone' value='one &amp; two'/><!--tone one-->");
        var alpha=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        var locations=PlanBindingLocationsTest.locations(view,false,alpha,"tone");assertEquals(1,locations.size());
        var hit=locations.getFirst();assertEquals(2,hit.elementIndex());assertEquals("value",hit.attribute().localName());
        var source=view.selected(false).sources().stream().filter(s->s.documentId().equals("glyph-sheet")).findFirst().orElseThrow();
        assertEquals("one &amp; two",source.xml().substring(hit.span().start(),hit.span().end()));
        var text=PlanDocumentViews.render(view,false,"glyph-sheet",ViewMode.PLACEHOLDERS).text();
        assertTrue(text.contains("k:key='tone' value='[[value:"+view.displayHandle(alpha)+":tone]]'"));assertTrue(text.contains("<!--tone one-->"));
        assertEquals(source.xml(),PlanDocumentViews.render(view,false,"glyph-sheet",ViewMode.RAW).text());
    }
    @Test void nonReadableChildValueBlocksConcreteDisclosure() throws Exception {
        var ready=ChildPropertyProjectionTest.definition(true,root->((ObjectNode)root.at("/logical/entityTypes/0/fields/1")).put("readable",false));
        var view=snapshot(ready,"<p:entry k:key='tone' value='opaque'/>");
        assertEquals(PlanRefusal.Code.DISCLOSURE_REQUIRED,assertThrows(PlanRefusal.class,()->PlanDocumentViews.render(view,false,"glyph-sheet",ViewMode.RAW)).code());
    }
    @Test void locationsStayInLexicalOrderAcrossInterveningProjectedChildEntity() throws Exception {
        var ready=ChildPropertyProjectionTest.definition(true,root->{for(var binding:root.get("bindings")) {
            var palette=(ObjectNode)binding.at("/documents/1/entities/0").deepCopy();palette.put("id","inner-palettes");
            var path=palette.putArray("path");for(var name:binding.at("/documents/0/entities/0/path"))path.add(name);
            path.addObject().put("namespaceUri","urn:mock:tiles").put("localName","palette");
            ((tools.jackson.databind.node.ArrayNode)binding.at("/documents/0/entities")).add(palette);
        }});
        var view=snapshot(ready,"<palette id='inner' shade='warm'/><p:entry k:key='tone' value='later'/>");
        var locations=new ArrayList<PlanBindingLocations.Occurrence>();PlanBindingLocations.scan(view,false,locations::add);
        int previous=-1;for(var item:locations)if(item.location().documentId().equals("glyph-sheet")){assertTrue(item.location().span().start()>=previous);previous=item.location().span().start();}
        assertDoesNotThrow(()->PlanDocumentViews.render(view,false,"glyph-sheet",ViewMode.PLACEHOLDERS));
    }
}
