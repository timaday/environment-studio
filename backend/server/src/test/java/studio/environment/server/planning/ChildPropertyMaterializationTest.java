package studio.environment.server.planning;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.projection.ChildPropertyProjectionTest;
import studio.environment.server.xml.*;

class ChildPropertyMaterializationTest {
    private static final String CHILD="<p:entry k:key='tone' value='old'/><!--property-tail-->";
    private List<TargetSource> sources() throws Exception {
        return ChildPropertyProjectionTest.sources(CHILD).stream().map(s->new TargetSource(s.documentId(),s.source(),Optional.empty())).toList();
    }
    @Test void scalarEditsOnlyLocatedChildValueWithExactUtf8OutsideSpan() throws Exception {
        var old=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        var intent=new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(old,Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.Entered("new &\t𐀀")),Map.of("uses",new TargetIntent.ReferenceValue.KeepObserved()))),List.of());
        var inputs=sources();
        var result=assertInstanceOf(MaterializationResult.Complete.class,new StructuralTargetAdapter().materialize(ChildPropertyProjectionTest.definition(true),"mock-pg",inputs,intent,List.of()));
        String expected=inputs.get(1).source().replace("value='old'","value='new &amp;&#9;𐀀'");
        var actual=result.documents().stream().filter(d->d.documentId().equals("glyph-sheet")).findFirst().orElseThrow().source();
        assertArrayEquals(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(inputs.getFirst().source(),result.documents().stream().filter(d->d.documentId().equals("palette-sheet")).findFirst().orElseThrow().source());
    }
    @Test void explicitEntityCreationProducesPropertyChildAndCompleteProvenance() throws Exception {
        var fresh=new TargetIntent.Ref.Fresh("third","glyph");
        var palette=new TargetIntent.Ref.Existing(new ObservedGraph.Key("palette","shared"));
        var intent=new TargetIntent(List.of(new TargetIntent.EntityDecision.Create(fresh,Map.of("tag",new TargetIntent.FieldValue.Entered("gamma"),"tone",new TargetIntent.FieldValue.Entered("A & B")),Map.of("uses",new TargetIntent.ReferenceValue.To(palette)))),List.of());
        var inputs=sources();var xml=assertInstanceOf(XmlResult.Accepted.class,new LosslessXmlAdapter().project(inputs.get(1).source())).document();
        var placement=new TargetPlacement(fresh,"glyph-sheet","glyphs",new TargetPlacement.Parent.Existing("glyph-sheet",xml.digest(),0));
        var result=assertInstanceOf(MaterializationResult.Complete.class,new StructuralTargetAdapter().materialize(ChildPropertyProjectionTest.definition(true),"mock-pg",inputs,intent,List.of(placement)));
        String added="<ns2:glyph xmlns:ns0=\"urn:mock:keys\" xmlns:ns1=\"urn:mock:properties\" xmlns:ns2=\"urn:mock:tiles\" id=\"gamma\" palette=\"shared\"><ns1:entry value=\"A &amp; B\" ns0:key=\"tone\"/></ns2:glyph>";
        String expected=inputs.get(1).source().replace("</tiles>",added+"</tiles>");
        assertEquals(expected,result.documents().stream().filter(d->d.documentId().equals("glyph-sheet")).findFirst().orElseThrow().source());
        assertEquals(4,result.graph().entities().size());
    }

    @Test void scalarCannotCreateAbsentPropertyAndOptionalFreshAbsenceCreatesNoChild() throws Exception {
        var ready=ChildPropertyProjectionTest.definition(false);
        var input=ChildPropertyProjectionTest.sources("").stream().map(d->new TargetSource(d.documentId(),d.source(),Optional.empty())).toList();
        var alpha=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        var scalar=new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(alpha,Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.Entered("new")),Map.of("uses",new TargetIntent.ReferenceValue.KeepObserved()))),List.of());
        assertInstanceOf(MaterializationResult.Rejected.class,new StructuralTargetAdapter().materialize(ready,"mock-pg",input,scalar,List.of()));
        var fresh=new TargetIntent.Ref.Fresh("third","glyph");
        var create=new TargetIntent(List.of(new TargetIntent.EntityDecision.Create(fresh,Map.of("tag",new TargetIntent.FieldValue.Entered("gamma"),"tone",new TargetIntent.FieldValue.ExplicitlyAbsent()),Map.of("uses",new TargetIntent.ReferenceValue.To(new TargetIntent.Ref.Existing(new ObservedGraph.Key("palette","shared")))))),List.of());
        var xml=assertInstanceOf(XmlResult.Accepted.class,new LosslessXmlAdapter().project(input.get(1).source())).document();
        var placement=new TargetPlacement(fresh,"glyph-sheet","glyphs",new TargetPlacement.Parent.Existing("glyph-sheet",xml.digest(),0));
        var result=assertInstanceOf(MaterializationResult.Complete.class,new StructuralTargetAdapter().materialize(ready,"mock-pg",input,create,List.of(placement)));
        var actual=result.documents().stream().filter(d->d.documentId().equals("glyph-sheet")).findFirst().orElseThrow().source();
        assertEquals(input.get(1).source().replace("</tiles>","<ns0:glyph xmlns:ns0=\"urn:mock:tiles\" id=\"gamma\" palette=\"shared\"/></tiles>"),actual);
    }

    @Test void containmentMoveCarriesPropertyAndAppliesChildScalarBeforeFinalReprojection() throws Exception {
        var ready=ChildPropertyProjectionTest.definition(true,root->{
            var logical=(tools.jackson.databind.node.ObjectNode)root.get("logical");
            logical.putArray("relations").addObject().put("id","contains").put("fromType","palette").put("toType","glyph").put("kind","containment").put("minimum",0).put("maximum",10).put("includeTargetOnReuse",false);
            for(var binding:root.get("bindings")) {
                var old=binding.get("documents");var glyph=(tools.jackson.databind.node.ObjectNode)old.get(0).get("entities").get(0).deepCopy();var palette=old.get(1).get("entities").get(0).deepCopy();
                glyph.putArray("references");var path=glyph.putArray("path");path.add(palette.get("path").get(0));path.add(palette.get("path").get(1));path.addObject().put("namespaceUri","urn:mock:tiles").put("localName","glyph");
                var document=((tools.jackson.databind.node.ObjectNode)binding).putArray("documents").addObject();document.put("id","tree").put("key","1");document.putArray("entities").add(palette).add(glyph);
            }
        });
        String prefix="<tiles xmlns='urn:mock:tiles' xmlns:p='urn:mock:properties' xmlns:k='urn:mock:keys'>";
        String alpha="<glyph id='alpha'><p:entry k:key='tone' value='old'/><!--keep--></glyph>";
        String beta="<glyph id='beta'><p:entry k:key='tone' value='same'/></glyph>";
        String source=prefix+"<palette id='p1' shade='warm'>\r\n"+alpha+beta+"</palette><palette id='p2' shade='cool'/></tiles>";
        var child=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));var parent=new TargetIntent.Ref.Existing(new ObservedGraph.Key("palette","p2"));
        var intent=new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(child,Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.Entered("new\t")),Map.of())),List.of(new TargetIntent.Containment("contains",parent,child)));
        var xml=assertInstanceOf(XmlResult.Accepted.class,new LosslessXmlAdapter().project(source)).document();
        int index=xml.elements().stream().filter(e->e.attributes().stream().anyMatch(a->a.name().localName().equals("id")&&a.value().equals("p2"))).findFirst().orElseThrow().index();
        var placement=new TargetPlacement(child,"tree","glyphs",new TargetPlacement.Parent.Existing("tree",xml.digest(),index));
        var result=assertInstanceOf(MaterializationResult.Complete.class,new StructuralTargetAdapter().materialize(ready,"mock-pg",List.of(new TargetSource("tree",source,Optional.empty())),intent,List.of(placement)));
        String moved="<glyph id='alpha' xmlns=\"urn:mock:tiles\" xmlns:k=\"urn:mock:keys\" xmlns:p=\"urn:mock:properties\"><p:entry k:key='tone' value='new&#9;'/><!--keep--></glyph>";
        String expected=prefix+"<palette id='p1' shade='warm'>\r\n"+beta+"</palette><palette id='p2' shade='cool'>"+moved+"</palette></tiles>";
        assertEquals(expected,result.documents().getFirst().source());
    }
}
