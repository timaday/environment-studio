package studio.environment.server.definition;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.definitionv2.*;

/** Independent mock extension of the invented native-v2 fixture; no private inputs. */
class ChildPropertyDefinitionTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static ObjectNode fixture() throws Exception {
        return (ObjectNode) JSON.readTree(Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
    }
    private static ObjectNode child(ObjectNode input) {
        var mapping=(ObjectNode)input.at("/bindings/0/documents/0/entities/0/fields/1");
        mapping.remove("attribute");
        var child=mapping.putObject("childProperty");
        child.putObject("element").put("namespaceUri","urn:mock:properties").put("localName","entry");
        child.putObject("discriminatorAttribute").put("namespaceUri","urn:mock:selector").put("localName","key");
        child.put("discriminatorValue","tone");
        child.putObject("valueAttribute").put("namespaceUri","").put("localName","value");
        return child;
    }
    private static NativeCompilationResult compile(ObjectNode input) {
        return new NativeDefinitionBytesCompiler().compile(JSON.writeValueAsBytes(input),DefinitionBytesCompiler.Format.JSON);
    }
    @Test void qualifiedChildFormUsesServerOwnedDependencies() throws Exception {
        var input=fixture();child(input);
        var result=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,compile(input));
        assertTrue(NativeMechanisms.eligible(result.checked()));
        var claimed=input.deepCopy();claimed.putObject("mechanisms").put("xml-child-property-v1",1);
        assertInstanceOf(NativeCompilationResult.Rejected.class,compile(claimed));
        assertEquals(java.math.BigInteger.ONE,result.checked().mechanisms().get("xml-child-property-v1"));
    }
    @Test void unchangedBindingAndLogicalDigestsSurviveAdditiveDependency() throws Exception {
        var input=fixture();
        var baseline=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,compile(input)).checked();
        child(input);
        var changed=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,compile(input)).checked();
        assertEquals(baseline.logicalDigest(),changed.logicalDigest());
        assertEquals(baseline.bindingDigests().get("mock-oracle"),changed.bindingDigests().get("mock-oracle"));
        assertNotEquals(baseline.bindingDigests().get("mock-pg"),changed.bindingDigests().get("mock-pg"));
    }
    @Test void childElementUnsupportedNamespaceIsIncompleteRatherThanUnknownVocabulary() throws Exception {
        for(String namespace:java.util.List.of("http://www.w3.org/2001/XInclude","http://www.w3.org/2000/09/xmldsig#","http://www.w3.org/2009/xmldsig11#","http://www.w3.org/2001/04/xmlenc#","http://www.w3.org/2009/xmlenc11#")) {
            var input=fixture();((ObjectNode)child(input).get("element")).put("namespaceUri",namespace);
            var result=assertInstanceOf(NativeCompilationResult.Incomplete.class,compile(input));
            assertTrue(result.diagnostics().stream().anyMatch(d->d.code().equals("XML_NAMESPACE_UNSUPPORTED")));
        }
    }
    @Test void mappingFormsAreClosedAndMutuallyExclusive() throws Exception {
        for(String invalid:java.util.List.of("both","neither","xpath","unknown-child")) {
            var input=fixture();child(input);
            var mapping=(ObjectNode)input.at("/bindings/0/documents/0/entities/0/fields/1");
            switch(invalid) {
                case "both" -> mapping.putObject("attribute").put("namespaceUri","").put("localName","tone");
                case "neither" -> mapping.remove("childProperty");
                case "xpath" -> mapping.put("xpath","entry[1]/@value");
                case "unknown-child" -> ((ObjectNode)mapping.get("childProperty")).put("descendants",true);
                default -> throw new AssertionError();
            }
            assertInstanceOf(NativeCompilationResult.Rejected.class,compile(input),invalid);
        }
    }
    @Test void staticAliasesAndDiscriminatorLocationsCannotAcquireValueAuthority() throws Exception {
        for (boolean discriminator : java.util.List.of(false,true)) {
            var input=fixture();var property=child(input);
            var mappings=(tools.jackson.databind.node.ArrayNode)input.at("/bindings/0/documents/0/entities/0/fields");
            var first=(ObjectNode)mappings.get(0);first.remove("attribute");first.set("childProperty",property.deepCopy());
            if(discriminator) ((ObjectNode)first.get("childProperty")).set("valueAttribute",property.get("discriminatorAttribute").deepCopy());
            var result=assertInstanceOf(NativeCompilationResult.Rejected.class,compile(input));
            String code=discriminator?"DISCRIMINATOR_MAPPING_CONFLICT":"ATTRIBUTE_COLLISION";
            assertTrue(result.diagnostics().stream().anyMatch(d->d.code().equals(code)));
        }
    }
    @Test void childSelectorValuesAreExactXmlTextAndMayExplicitlyBeEmpty() throws Exception {
        var input=fixture();child(input).put("discriminatorValue", "");
        assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,compile(input));
        input=fixture();child(input).put("discriminatorValue",String.valueOf((char)1));
        var result=assertInstanceOf(NativeCompilationResult.Rejected.class,compile(input));
        assertTrue(result.diagnostics().stream().anyMatch(d->d.code().equals("INVALID_XML_VALUE")));
    }
    @Test void physicalSelectorChangesAlterOnlyTheSelectedBinding() throws Exception {
        var input=fixture();var selector=child(input);
        var before=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,compile(input)).checked();
        selector.put("discriminatorValue","Tone");
        var changed=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,compile(input)).checked();
        assertEquals(before.logicalDigest(),changed.logicalDigest());
        assertEquals(before.bindingDigests().get("mock-oracle"),changed.bindingDigests().get("mock-oracle"));
        assertNotEquals(before.bindingDigests().get("mock-pg"),changed.bindingDigests().get("mock-pg"));
    }
    @Test void childDepthIsCheckedAfterEntityPathAndNamespaceDeclarationsAreNeverValues() throws Exception {
        var input=fixture();var selector=child(input);
        var path=(tools.jackson.databind.node.ArrayNode)input.at("/bindings/0/documents/0/entities/0/path");
        while(path.size()<128)path.add(path.get(1).deepCopy());
        var result=assertInstanceOf(NativeCompilationResult.Incomplete.class,compile(input));
        assertTrue(result.diagnostics().stream().anyMatch(d->d.code().equals("XML_DEPTH_UNSUPPORTED")));
        input=fixture();selector=child(input);((ObjectNode)selector.get("valueAttribute")).put("localName","xmlns");
        var rejected=assertInstanceOf(NativeCompilationResult.Rejected.class,compile(input));
        assertTrue(rejected.diagnostics().stream().anyMatch(d->d.code().equals("INVALID_XML_NAME")));
    }

    @Test void childBindingMatchesIndependentPythonGoldenAndVersionVector() throws Exception {
        var input=fixture();child(input);
        var checked=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,compile(input)).checked();
        var expected=JSON.readTree(Files.readString(Path.of("../../fixtures/native-v2/child-property-expected-digests.json")));
        assertEquals(expected.get("logicalDigest").asString(),checked.logicalDigest());
        for(var entry:expected.get("bindingDigests").properties())assertEquals(entry.getValue().asString(),checked.bindingDigests().get(entry.getKey()));
        assertEquals(5,NativeMechanisms.required(checked.definition().bindings().get(0)).size());
        assertEquals(4,NativeMechanisms.required(checked.definition().bindings().get(1)).size());
        assertTrue(NativeMechanisms.matchesDependencies(checked));
        var missing=new java.util.TreeMap<>(checked.mechanisms());missing.remove("xml-child-property-v1");
        var tampered=new NativeCompilationResult.Checked(checked.definition(),checked.logicalDigest(),checked.bindingDigests(),missing);
        assertFalse(NativeMechanisms.matchesDependencies(tampered));
        assertFalse(NativeMechanisms.eligible(tampered));
        var direct=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,compile(fixture())).checked();
        var unneeded=new NativeCompilationResult.Checked(direct.definition(),direct.logicalDigest(),direct.bindingDigests(),checked.mechanisms());
        assertFalse(NativeMechanisms.eligible(unneeded));
    }

    @Test void creationCannotEmitAnUndeclaredFreshEntityFromAPropertyChild() throws Exception {
        var input=fixture();var property=child(input);
        var binding=(ObjectNode)input.at("/bindings/0");
        var nested=((ObjectNode)binding.at("/documents/1/entities/0")).deepCopy();
        var childPath=nested.putArray("path");binding.at("/documents/0/entities/0/path").forEach(n->childPath.add(n.deepCopy()));
        childPath.add(property.get("element").deepCopy());
        ((tools.jackson.databind.node.ArrayNode)binding.at("/documents/0/entities")).add(nested);
        ((tools.jackson.databind.node.ArrayNode)binding.get("documents")).remove(1);
        var refused=assertInstanceOf(NativeCompilationResult.Incomplete.class,compile(input));
        assertTrue(refused.diagnostics().stream().anyMatch(d->d.code().equals("CHILD_ENTITY_CREATION_UNSUPPORTED")));
        var operations=(tools.jackson.databind.node.ArrayNode)input.at("/logical/operationCapabilities");
        for(int index=operations.size()-1;index>=0;index--)if(operations.get(index).asString().equals("create-entity"))operations.remove(index);
        var scalarOnly=compile(input);
        assertFalse(scalarOnly instanceof NativeCompilationResult.Rejected);
        assertFalse(scalarOnly.diagnostics().stream().anyMatch(d->d.code().equals("CHILD_ENTITY_CREATION_UNSUPPORTED")));
    }

}
