package studio.environment.server.definition;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.server.xml.*;
import tools.jackson.databind.node.ObjectNode;

class XmlNamespaceReadinessTest {
    @org.junit.jupiter.api.Test void currentCompilationPinsCorrectedMechanismRevision() throws Exception {
        var result = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionBytesCompiler().compile(
            Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")), DefinitionBytesCompiler.Format.JSON));
        assertEquals(java.util.Map.of("native-compiler-v2", java.math.BigInteger.TWO, "xml-path-v1", java.math.BigInteger.ONE,
            "xml-span-v1", java.math.BigInteger.ONE, "generic-graph-v1", java.math.BigInteger.ONE), result.checked().mechanisms());
    }
    @ParameterizedTest @ValueSource(strings={"http://www.w3.org/2001/XInclude", "http://www.w3.org/2000/09/xmldsig#", "http://www.w3.org/2009/xmldsig11#", "http://www.w3.org/2001/04/xmlenc#", "http://www.w3.org/2009/xmlenc11#"})
    void attributesAndUnusedDeclarationsDoNotInvokeUnsupportedElementMechanisms(String namespace) throws Exception {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var definition = (ObjectNode) json.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")));
        for (var binding : definition.get("bindings")) for (var document : binding.get("documents")) for (var projection : document.get("entities")) {
            ((ObjectNode) projection.get("fields").get(1).get("attribute")).put("namespaceUri", namespace);
        }
        assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionBytesCompiler().compile(json.writeValueAsBytes(definition), DefinitionBytesCompiler.Format.JSON));
        for (String xml : new String[]{"<tiles xmlns='urn:mock:tiles' xmlns:unused='" + namespace + "'/>",
                "<tiles xmlns='urn:mock:tiles' xmlns:v='" + namespace + "'><glyph id='alpha' v:tone='mock'/></tiles>"}) {
            assertEquals(xml, assertInstanceOf(XmlResult.Accepted.class, new LosslessXmlAdapter().project(xml)).document().source());
        }
    }
    @ParameterizedTest @ValueSource(strings={"http://www.w3.org/2001/XInclude", "http://www.w3.org/2000/09/xmldsig#", "http://www.w3.org/2009/xmldsig11#", "http://www.w3.org/2001/04/xmlenc#", "http://www.w3.org/2009/xmlenc11#"})
    void everyElementPositionMustBeSupportedByRegisteredParser(String namespace) throws Exception {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        for (int position : new int[]{0, 1}) {
            var definition = (ObjectNode) json.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")));
            for (var binding : definition.get("bindings")) for (var document : binding.get("documents")) for (var projection : document.get("entities")) {
                ((ObjectNode) projection.get("path").get(position)).put("namespaceUri", namespace);
            }
            var compiled = new NativeDefinitionBytesCompiler().compile(json.writeValueAsBytes(definition), DefinitionBytesCompiler.Format.JSON);
            var incomplete = assertInstanceOf(NativeCompilationResult.Incomplete.class, compiled);
            assertTrue(incomplete.diagnostics().stream().anyMatch(d -> d.code().equals("XML_NAMESPACE_UNSUPPORTED") && d.pointer().equals("/bindings/0/documents/0/entities/0/path/" + position + "/namespaceUri")));
            String xml = position == 0 ? "<tiles xmlns='" + namespace + "'/>" : "<tiles xmlns='urn:mock:tiles'><glyph xmlns='" + namespace + "'/></tiles>";
            assertInstanceOf(XmlResult.Rejected.class, new LosslessXmlAdapter().project(xml));
        }
    }
}
