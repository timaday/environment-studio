package studio.environment.server.projection;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.server.definition.*;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/** Independently invented child-property XML; no application model or donor input. */
public class ChildPropertyProjectionTest {
    public static NativeCompilationResult.ReadyToPublish definition(boolean required) throws Exception { return definition(required,root->{}); }
    public static NativeCompilationResult.ReadyToPublish definition(boolean required,java.util.function.Consumer<ObjectNode> edit) throws Exception {
        var json=JsonMapper.builder().build();
        var root=(ObjectNode)json.readTree(Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
        ((ObjectNode)root.at("/logical/entityTypes/0/fields/1")).put("required",required);
        for(var binding:root.get("bindings")) {
            var field=(ObjectNode)binding.at("/documents/0/entities/0/fields/1");field.remove("attribute");
            var child=field.putObject("childProperty");
            child.putObject("element").put("namespaceUri","urn:mock:properties").put("localName","entry");
            child.putObject("discriminatorAttribute").put("namespaceUri","urn:mock:keys").put("localName","key");
            child.put("discriminatorValue","tone");
            child.putObject("valueAttribute").put("namespaceUri","").put("localName","value");
        }
        edit.accept(root);
        var result=new NativeDefinitionBytesCompiler().compile(json.writeValueAsBytes(root),DefinitionBytesCompiler.Format.JSON);
        assertFalse(result instanceof NativeCompilationResult.Rejected,"Child declaration must be recognized before adapter qualification");
        // Adapter-only qualification: never publication authority.
        return result instanceof NativeCompilationResult.ReadyToPublish ready?ready:
            new NativeCompilationResult.ReadyToPublish(((NativeCompilationResult.Incomplete)result).checked());
    }
    public static List<DocumentSource> sources(String children) throws Exception {
        return List.of(new DocumentSource("palette-sheet",Files.readString(Path.of("../../fixtures/native-v2/xml/palettes.xml"))),
            new DocumentSource("glyph-sheet","<tiles xmlns='urn:mock:tiles' xmlns:p='urn:mock:properties' xmlns:k='urn:mock:keys'>\r\n<!--outside--><glyph id='alpha' palette='shared'>"+children+"</glyph><glyph id='beta' palette='shared'><p:entry k:key='tone' value=''/></glyph></tiles>"));
    }
    @Test void mixedDirectChildAndEmptyFieldsUseExactDecodedNamespaces() throws Exception {
        var result=assertInstanceOf(ProjectionResult.Accepted.class,new GraphProjectionAdapter().project(definition(true),"mock-pg",sources("<p:entry k:key='tone' value='  blue &amp; 𐀀&#9;'/><!--untouched-->")));
        assertEquals("  blue & 𐀀\t",result.graph().entities().getFirst().fields().get("tone"));
        assertEquals("",result.graph().entities().get(1).fields().get("tone"));
        assertEquals(1,result.graph().entities().getFirst().origin().elementIndex());
        assertEquals(3,result.graph().entities().size());
    }
    @Test void duplicateMatchesRefuseEvenWhenValuesAgree() throws Exception {
        var result=new GraphProjectionAdapter().project(definition(true),"mock-pg",sources("<p:entry k:key='tone' value='x'/><p:entry k:key='tone' value='x'/>"));
        assertInstanceOf(ProjectionResult.Rejected.class,result);
    }
    @Test void wrongNamespaceAndNestedChildrenAreExplicitOptionalAbsence() throws Exception {
        var result=assertInstanceOf(ProjectionResult.Accepted.class,new GraphProjectionAdapter().project(definition(false),"mock-pg",sources("<p:entry key='tone' value='wrong'/><box><p:entry k:key='tone' value='nested'/></box>")));
        assertFalse(result.graph().entities().getFirst().fields().containsKey("tone"));
    }

    @Test void missingChildAndMissingValueRespectRequiredPresence() throws Exception {
        for(String child:List.of("", "<p:entry k:key='tone'/>")) {
            assertInstanceOf(ProjectionResult.Rejected.class,new GraphProjectionAdapter().project(definition(true),"mock-pg",sources(child)));
            var absent=assertInstanceOf(ProjectionResult.Accepted.class,new GraphProjectionAdapter().project(definition(false),"mock-pg",sources(child)));
            assertFalse(absent.graph().entities().getFirst().fields().containsKey("tone"));
        }
    }
    @Test void equivalentPrefixesAndDuplicateMissingValuesAreNotSpecialCases() throws Exception {
        var result=assertInstanceOf(ProjectionResult.Accepted.class,new GraphProjectionAdapter().project(definition(true),"mock-pg",sources("<other:entry xmlns:other='urn:mock:properties' xmlns:key='urn:mock:keys' key:key='tone' value='same'/>")));
        assertEquals("same",result.graph().entities().getFirst().fields().get("tone"));
        assertInstanceOf(ProjectionResult.Rejected.class,new GraphProjectionAdapter().project(definition(false),"mock-pg",sources("<p:entry k:key='tone'/><p:entry k:key='tone'/>")));
    }
    @Test void runtimePhysicalAliasesRefuseUnderTypedAdverseDeclaration() throws Exception {
        var declaration=definition(true).checked().definition().bindings().getFirst().documents().getFirst();
        var projection=declaration.entities().getFirst();var fields=new ArrayList<>(projection.fields());
        fields.set(0,new studio.environment.core.definitionv2.NativeDefinition.FieldMapping("tag",fields.get(1).locator()));
        var altered=new studio.environment.core.definitionv2.NativeDefinition.Document(declaration.id(),declaration.key(),List.of(new studio.environment.core.definitionv2.NativeDefinition.Projection(projection.id(),projection.type(),projection.path(),fields,projection.references())));
        var parsed=(studio.environment.server.xml.XmlResult.Accepted)new studio.environment.server.xml.LosslessXmlAdapter().project(sources("<p:entry k:key='tone' value='x'/>").get(1).source());
        var refused=new FieldLocatorResolver(parsed.document()).validate(altered);assertTrue(refused.isPresent());assertEquals("ATTRIBUTE_ALIAS",refused.orElseThrow().code());
    }
    @Test void resolverRejectsForeignSourceAndReturnsActualChildAttributeOrigin() throws Exception {
        var parser=new studio.environment.server.xml.LosslessXmlAdapter();
        var doc=((studio.environment.server.xml.XmlResult.Accepted)parser.project(sources("<p:entry k:key='tone' value='x'/>").get(1).source())).document();
        var changed=((studio.environment.server.xml.XmlResult.Accepted)parser.project(sources("<p:entry k:key='tone' value='y'/>").get(1).source())).document();
        var mapping=definition(true).checked().definition().bindings().getFirst().documents().getFirst().entities().getFirst().fields().get(1);
        var resolver=new FieldLocatorResolver(doc);
        assertInstanceOf(FieldLocatorResolver.Refused.class,resolver.resolve(changed.elements().get(1),mapping.locator()));
        var actual=assertInstanceOf(FieldLocatorResolver.Located.class,resolver.resolve(doc.elements().get(1),mapping.locator()));
        assertEquals(1,actual.entity().index());assertEquals(2,actual.attribute().elementIndex());
        assertEquals(doc.digest(),actual.attribute().sourceDigest());assertEquals("tone",actual.selector().orElseThrow().discriminator().value());
        assertEquals("x",doc.source().substring(actual.attribute().valueSpan().start(),actual.attribute().valueSpan().end()));
    }

    @Test void discriminatorCannotBecomeAnEditableFieldEvenWhenCurrentValueMatches() throws Exception {
        var declaration=definition(true).checked().definition().bindings().getFirst().documents().getFirst();
        var projection=declaration.entities().getFirst();var fields=new ArrayList<>(projection.fields());
        var child=(studio.environment.core.definitionv2.NativeDefinition.ChildProperty)fields.get(1).locator();
        fields.set(1,new studio.environment.core.definitionv2.NativeDefinition.FieldMapping("tone",new studio.environment.core.definitionv2.NativeDefinition.ChildProperty(child.element(),child.discriminatorAttribute(),child.discriminatorValue(),child.discriminatorAttribute())));
        var altered=new studio.environment.core.definitionv2.NativeDefinition.Document(declaration.id(),declaration.key(),List.of(new studio.environment.core.definitionv2.NativeDefinition.Projection(projection.id(),projection.type(),projection.path(),fields,projection.references())));
        var parsed=(studio.environment.server.xml.XmlResult.Accepted)new studio.environment.server.xml.LosslessXmlAdapter().project(sources("<p:entry k:key='tone' value='x'/>").get(1).source());
        var refused=new FieldLocatorResolver(parsed.document()).validate(altered);assertTrue(refused.isPresent());assertEquals("SELECTOR_ATTRIBUTE_CONFLICT",refused.orElseThrow().code());
    }
    @Test void allUnsupportedElementVocabulariesRefuseChildObservation() throws Exception {
        for(String namespace:List.of("http://www.w3.org/2001/XInclude","http://www.w3.org/2000/09/xmldsig#","http://www.w3.org/2009/xmldsig11#","http://www.w3.org/2001/04/xmlenc#","http://www.w3.org/2009/xmlenc11#")) {
            var ready=definition(true,root->{for(var binding:root.get("bindings"))((ObjectNode)binding.at("/documents/0/entities/0/fields/1/childProperty/element")).put("namespaceUri",namespace);});
            var input=sources("<p:entry k:key='tone' value='x'/>").stream().map(d->new DocumentSource(d.documentId(),d.source().replace("urn:mock:properties",namespace))).toList();
            assertInstanceOf(ProjectionResult.Rejected.class,new GraphProjectionAdapter().project(ready,"mock-pg",input));
        }
    }

    @Test void unnamespacedChildResetsDefaultNamespaceAndEmptyDiscriminatorIsExact() throws Exception {
        var ready=definition(true,root->{for(var binding:root.get("bindings")) {
            var child=(ObjectNode)binding.at("/documents/0/entities/0/fields/1/childProperty");child.put("discriminatorValue","");((ObjectNode)child.get("element")).put("namespaceUri","");
        }});
        var input=sources("<p:entry k:key='tone' value='x'/>").stream().map(d->new DocumentSource(d.documentId(),d.source().replace("<p:entry","<entry xmlns='' ").replace("k:key='tone'","k:key=''"))).toList();
        var result=assertInstanceOf(ProjectionResult.Accepted.class,new GraphProjectionAdapter().project(ready,"mock-pg",input));
        assertEquals("x",result.graph().entities().getFirst().fields().get("tone"));assertEquals("",result.graph().entities().get(1).fields().get("tone"));
    }
}
