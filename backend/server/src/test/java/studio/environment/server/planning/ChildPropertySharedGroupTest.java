package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.projection.*;
import studio.environment.server.xml.*;

class ChildPropertySharedGroupTest {
    @ParameterizedTest @ValueSource(strings={"mock-pg","mock-oracle"})
    void sharedPropertyChildCarriesIdentityAndNamespacedValueWithExactUnicodeSelector(String binding) throws Exception {
        var definition=ChildPropertyProjectionTest.definition(true,root->{for(var item:root.get("bindings")) {
            var tone=(ObjectNode)item.at("/documents/0/entities/0/fields/1/childProperty");
            tone.put("discriminatorValue","é𐀀\t");((ObjectNode)tone.get("valueAttribute")).put("namespaceUri","urn:mock:values");
            var tag=(ObjectNode)item.at("/documents/0/entities/0/fields/0");tag.remove("attribute");
            var selector=tone.deepCopy();((ObjectNode)selector.get("valueAttribute")).put("namespaceUri","").put("localName","id");tag.set("childProperty",selector);
        }});
        String glyphs="<tiles xmlns='urn:mock:tiles' xmlns:p='urn:mock:properties' xmlns:k='urn:mock:keys' xmlns:v='urn:mock:values'>\r\n"
            +"<glyph palette='shared'><p:entry id='alpha' k:key='é𐀀&#9;' v:value='old'/><!--keep--></glyph>\r\n"
            +"<glyph palette='shared'><p:entry id='beta' k:key='é𐀀&#9;' v:value=''/></glyph></tiles>";
        var palette=ChildPropertyProjectionTest.sources("").getFirst();
        var sources=List.of(new DocumentSource("glyph-sheet",glyphs),palette);
        var current=assertInstanceOf(ProjectionResult.Accepted.class,new GraphProjectionAdapter().project(definition,binding,sources));
        assertEquals("old",current.graph().entities().getFirst().fields().get("tone"));
        var fresh=new TargetIntent.Ref.Fresh("fresh-group","glyph");
        var target=new TargetIntent(List.of(
            new TargetIntent.EntityDecision.Retain(new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha")),
                Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.Entered("<&\r\n𐀀")),Map.of("uses",new TargetIntent.ReferenceValue.KeepObserved())),
            new TargetIntent.EntityDecision.Create(fresh,Map.of("tag",new TargetIntent.FieldValue.Entered("gamma"),"tone",new TargetIntent.FieldValue.Entered("fresh")),
                Map.of("uses",new TargetIntent.ReferenceValue.To(new TargetIntent.Ref.Existing(new ObservedGraph.Key("palette","shared")))))),List.of());
        var xml=assertInstanceOf(XmlResult.Accepted.class,new LosslessXmlAdapter().project(glyphs)).document();
        var placement=new TargetPlacement(fresh,"glyph-sheet","glyphs",new TargetPlacement.Parent.Existing("glyph-sheet",xml.digest(),0));
        var result=assertInstanceOf(MaterializationResult.Complete.class,new StructuralTargetAdapter().materialize(definition,binding,
            sources.stream().map(s->new TargetSource(s.documentId(),s.source(),Optional.empty())).toList(),target,List.of(placement)));
        String added="<ns2:glyph xmlns:ns0=\"urn:mock:keys\" xmlns:ns1=\"urn:mock:properties\" xmlns:ns2=\"urn:mock:tiles\" xmlns:ns3=\"urn:mock:values\" palette=\"shared\"><ns1:entry id=\"gamma\" ns0:key=\"é𐀀&#9;\" ns3:value=\"fresh\"/></ns2:glyph>";
        String expected=glyphs.replace("v:value='old'","v:value='&lt;&amp;&#13;&#10;𐀀'").replace("</tiles>",added+"</tiles>");
        var actual=result.documents().stream().filter(d->d.documentId().equals("glyph-sheet")).findFirst().orElseThrow().source();
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));
        assertEquals(palette.source(),result.documents().stream().filter(d->d.documentId().equals("palette-sheet")).findFirst().orElseThrow().source());
        var reprojected=assertInstanceOf(XmlResult.Accepted.class,new LosslessXmlAdapter().project(actual)).document();
        assertEquals(3,reprojected.elements().stream().filter(e->e.name().localName().equals("entry")).count());
        assertEquals(4,result.graph().entities().size());
    }
}
