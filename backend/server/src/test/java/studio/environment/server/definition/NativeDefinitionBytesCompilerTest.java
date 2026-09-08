package studio.environment.server.definition;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.definition.DefinitionBytesCompiler.Format.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.definitionv2.*;

class NativeDefinitionBytesCompilerTest {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private final NativeDefinitionBytesCompiler compiler = new NativeDefinitionBytesCompiler();
    private ObjectNode fixture() throws Exception {
        return (ObjectNode) JSON_MAPPER.readTree(Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
    }
    private NativeCompilationResult compile(JsonNode node) {
        return compiler.compile(JSON_MAPPER.writeValueAsBytes(node), JSON);
    }
    @Test void inventedTwoBindingFamilyMatchesIndependentPythonDigestOracle() throws Exception {
        var result = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(fixture()));
        var expected = JSON_MAPPER.readTree(Files.readString(Path.of("../../fixtures/native-v2/expected-digests.json")));
        assertEquals(expected.get("logicalDigest").asString(), result.checked().logicalDigest());
        for (var binding : expected.get("bindingDigests").properties())
            assertEquals(binding.getValue().asString(), result.checked().bindingDigests().get(binding.getKey()));
        assertNotEquals(result.checked().bindingDigests().get("mock-pg"), result.checked().bindingDigests().get("mock-oracle"));
        assertEquals(2, result.checked().definition().logical().entityTypes().size());
    }
    @Test void missingReadableMappingIsIncompleteWithAnInspectableDraft() throws Exception {
        var input = fixture();
        ((tools.jackson.databind.node.ArrayNode) input.get("bindings").get(0).get("documents").get(0).get("entities").get(0).get("fields")).removeAll();
        var result = assertInstanceOf(NativeCompilationResult.Incomplete.class, compile(input));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("FIELD_MAPPING_MISSING")));
    }
    private static ObjectNode object(JsonNode node, String pointer) { return (ObjectNode) node.at(pointer); }
    private static tools.jackson.databind.node.ArrayNode array(JsonNode node, String pointer) {
        return (tools.jackson.databind.node.ArrayNode) node.at(pointer);
    }
    private void rejected(NativeCompilationResult result, String code) {
        var refusal = assertInstanceOf(NativeCompilationResult.Rejected.class, result);
        assertTrue(refusal.diagnostics().stream().anyMatch(d -> d.code().equals(code)), () -> refusal.diagnostics().toString());
    }
    private void incomplete(NativeCompilationResult result, String code) {
        var refusal = assertInstanceOf(NativeCompilationResult.Incomplete.class, result);
        assertTrue(refusal.diagnostics().stream().anyMatch(d -> d.code().equals(code)), () -> refusal.diagnostics().toString());
    }
    @Test void unicodeAndUnboundedIntegerDigestFramesMatchASecondIndependentOracle() throws Exception {
        var input = JSON_MAPPER.readTree(Files.readString(Path.of("../../fixtures/native-v2/unicode-integer-definition.json")));
        var expected = JSON_MAPPER.readTree(Files.readString(Path.of("../../fixtures/native-v2/unicode-integer-expected-digests.json")));
        var result = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input)).checked();
        assertEquals(expected.get("logicalDigest").asString(), result.logicalDigest());
        for (var binding : expected.get("bindingDigests").properties())
            assertEquals(binding.getValue().asString(), result.bindingDigests().get(binding.getKey()));
        assertEquals(new java.math.BigInteger("123456789012345678901234567890"), result.definition().logical().rules().getFirst().maximum());
    }
    @Test void semanticErrorsNeverExposePartialModelsOrUploadedIdentifiers() throws Exception {
        record Case(java.util.function.Consumer<ObjectNode> mutate, String code) { }
        var cases = java.util.List.of(
            new Case(n -> object(n, "/logical/entityTypes/1").put("id", "glyph"), "DUPLICATE_ID"),
            new Case(n -> object(n, "/logical/entityTypes/0/fields/1").put("id", "tag"), "DUPLICATE_ID"),
            new Case(n -> object(n, "/logical/rules/1").put("id", "glyph-count"), "DUPLICATE_ID"),
            new Case(n -> object(n, "/bindings/1").put("id", "mock-pg"), "DUPLICATE_ID"),
            new Case(n -> object(n, "/bindings/0/documents/1").put("id", "glyph-sheet"), "DUPLICATE_ID"),
            new Case(n -> object(n, "/bindings/0/documents/1/entities/0").put("id", "glyphs"), "DUPLICATE_ID"),
            new Case(n -> object(n, "/bindings/0/documents/1").put("key", "1"), "DUPLICATE_DOCUMENT_KEY"),
            new Case(n -> object(n, "/bindings/0/documents/0").put("key", "9223372036854775808"), "INVALID_DOCUMENT_KEY"),
            new Case(n -> object(n, "/bindings/0").put("xmlColumn", "mock_key"), "COLUMN_COLLISION"),
            new Case(n -> object(n, "/bindings/0").put("storage", "clob"), "ENGINE_STORAGE_MISMATCH"),
            new Case(n -> object(n, "/logical/entityTypes/0/identity").put("field", "missing-canary"), "INVALID_IDENTITY"),
            new Case(n -> object(n, "/logical/entityTypes/0/fields/0").put("sensitivity", "secret"), "INVALID_IDENTITY"),
            new Case(n -> object(n, "/logical/entityTypes/0/fields/0").put("required", false), "INVALID_IDENTITY"),
            new Case(n -> object(n, "/logical/relations/0").put("fromType", "missing-canary"), "UNKNOWN_TYPE"),
            new Case(n -> object(n, "/logical/rules/0").put("type", "missing-canary"), "UNKNOWN_TYPE"),
            new Case(n -> object(n, "/logical/relations/0").put("minimum", 2), "CARDINALITY_INVERTED"),
            new Case(n -> object(n, "/logical/rules/0").put("minimum", 5), "CARDINALITY_INVERTED"),
            new Case(n -> object(n, "/bindings/0/documents/0/entities/0").put("type", "missing-canary"), "UNKNOWN_TYPE"),
            new Case(n -> object(n, "/bindings/0/documents/0/entities/0/fields/0").put("field", "missing-canary"), "UNKNOWN_FIELD"),
            new Case(n -> object(n, "/bindings/0/documents/0/entities/0/references/0").put("relation", "missing-canary"), "UNKNOWN_RELATION"),
            new Case(n -> object(n, "/bindings/0/documents/0/entities/0/fields/1/attribute").put("localName", "id"), "ATTRIBUTE_COLLISION"),
            new Case(n -> object(n, "/bindings/0/documents/0/entities/0/references/0/attribute").put("localName", "tone"), "ATTRIBUTE_COLLISION"),
            new Case(n -> object(n, "/bindings/0/documents/0/entities/0/fields/0/attribute").put("localName", "xmlns"), "INVALID_XML_NAME"),
            new Case(n -> object(n, "/bindings/0/documents/0/entities/0/path/0").put("namespaceUri", "http://www.w3.org/2000/xmlns/"), "INVALID_XML_NAME"));
        for (Case test : cases) {
            var input = fixture(); test.mutate().accept(input);
            var result = compile(input); rejected(result, test.code());
            assertFalse(result.toString().contains("missing-canary"));
            assertEquals(result, compile(input));
        }
    }
    @Test void missingOrRecognizedUnsupportedMechanismsRemainIncomplete() throws Exception {
        var input = fixture();
        array(input, "/bindings/0/documents/0/entities/0/references").removeAll();
        incomplete(compile(input), "REFERENCE_MAPPING_MISSING");
        input = fixture(); array(input, "/bindings/0/documents").remove(1);
        incomplete(compile(input), "TYPE_PROJECTION_MISSING");
        input = fixture(); object(input, "/logical/entityTypes/0/fields/1").put("readable", false);
        incomplete(compile(input), "FIELD_UNREADABLE");
        input = fixture(); object(input, "/logical/entityTypes/0/fields/1").put("sensitivity", "unknown");
        incomplete(compile(input), "SENSITIVITY_UNKNOWN");
        input = fixture(); object(input, "/logical/relations/0").put("maximum", 2);
        incomplete(compile(input), "REFERENCE_CODEC_UNSUPPORTED");
        input = fixture(); array(input, "/bindings/0/documents/0/entities/0/path").remove(1);
        incomplete(compile(input), "ROOT_OPERATION_UNSUPPORTED");
    }
    @Test void duplicateMappingsOverlappingPathsAndWrongReferenceSourcesReject() throws Exception {
        var input = fixture();
        var fields = array(input, "/bindings/0/documents/0/entities/0/fields"); fields.add(fields.get(0).deepCopy());
        rejected(compile(input), "DUPLICATE_FIELD_MAPPING");
        input = fixture(); var refs = array(input, "/bindings/0/documents/0/entities/0/references"); refs.add(refs.get(0).deepCopy());
        rejected(compile(input), "DUPLICATE_REFERENCE_MAPPING");
        input = fixture(); var projection = object(input, "/bindings/0/documents/0/entities/0").deepCopy().put("id", "extra");
        array(input, "/bindings/0/documents/0/entities").add(projection);
        rejected(compile(input), "OVERLAPPING_PROJECTIONS");
        input = fixture(); array(input, "/bindings/0/documents/1/entities/0/references").add(input.at("/bindings/0/documents/0/entities/0/references/0").deepCopy());
        rejected(compile(input), "INVALID_REFERENCE_BINDING");
    }
    @Test void canonicalProjectionsCanRepeatATypeAcrossDocumentsWithoutFirstMatchSemantics() throws Exception {
        var input = fixture();
        var doc = object(input, "/bindings/0/documents/0").deepCopy().put("id", "extra-sheet").put("key", "3");
        object(doc, "/entities/0").put("id", "extra-glyphs");
        array(input, "/bindings/0/documents").add(doc);
        var result = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input));
        assertEquals(3, result.checked().definition().bindings().getFirst().documents().size());
    }
    @Test void containmentNeedsExactlyOneSameDocumentAncestorAndRecursionStaysLogical() throws Exception {
        var input = fixture(); object(input, "/logical/relations/0").put("kind", "containment");
        for (var binding : input.get("bindings")) array(binding, "/documents/0/entities/0/references").removeAll();
        incomplete(compile(input), "CONTAINMENT_BINDING_INCOMPLETE");
        for (var binding : input.get("bindings")) {
            var child = object(binding, "/documents/1/entities/0").deepCopy();
            var path = array(child, "/path"); path.insert(1, binding.at("/documents/0/entities/0/path/1").deepCopy());
            array(binding, "/documents/0/entities").add(child);
            array(binding, "/documents").remove(1);
        }
        var baseline = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input)).checked();
        for (var binding : input.get("bindings")) reverse(array(binding, "/documents/0/entities"));
        var reordered = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input)).checked();
        assertEquals(baseline.logicalDigest(), reordered.logicalDigest());
        assertEquals(baseline.bindingDigests(), reordered.bindingDigests());
        object(input, "/logical/relations/0").put("toType", "glyph");
        incomplete(compile(input), "CONTAINMENT_BINDING_INCOMPLETE");
    }
    @Test void metadataAndDeclarationOrderingDoNotAffectContractDigests() throws Exception {
        var input = fixture();
        var baseline = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input)).checked();
        input.put("id", "renamed-metadata").put("revision", new java.math.BigInteger("1234567890123456789012345678901234567890"));
        object(input, "/logical/entityTypes/0").put("label", "Different label 😀");
        object(input, "/bindings/0").put("id", "renamed-binding");
        reverse(array(input, "/logical/entityTypes")); reverse(array(input, "/logical/rules")); reverse(array(input, "/logical/operationCapabilities"));
        for (var type : input.at("/logical/entityTypes")) reverse(array(type, "/fields"));
        for (var binding : input.get("bindings")) {
            reverse(array(binding, "/documents"));
            for (var document : binding.get("documents")) for (var projection : document.get("entities")) reverse(array(projection, "/fields"));
        }
        var result = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(reverseObjects(input))).checked();
        assertEquals(baseline.logicalDigest(), result.logicalDigest());
        assertEquals(baseline.bindingDigests().get("mock-pg"), result.bindingDigests().get("renamed-binding"));
        assertEquals(baseline.bindingDigests().get("mock-oracle"), result.bindingDigests().get("mock-oracle"));
        assertEquals(new java.math.BigInteger("1234567890123456789012345678901234567890"), result.definition().revision());
    }
    @Test void logicalChangesAndBindingChangesAlterOnlyTheirIntendedDigests() throws Exception {
        var input = fixture(); var before = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input)).checked();
        object(input, "/bindings/0").put("schema", "other_mock_schema");
        var changed = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input)).checked();
        assertEquals(before.logicalDigest(), changed.logicalDigest());
        assertNotEquals(before.bindingDigests().get("mock-pg"), changed.bindingDigests().get("mock-pg"));
        assertEquals(before.bindingDigests().get("mock-oracle"), changed.bindingDigests().get("mock-oracle"));
        for (var mutate : java.util.List.<java.util.function.Consumer<ObjectNode>>of(
                n -> object(n, "/logical/entityTypes/0/fields/1").put("editable", false),
                n -> object(n, "/logical/entityTypes/0/identity").put("field", "tone"),
                n -> object(n, "/logical/rules/0").put("maximum", 5))) {
            input = fixture(); mutate.accept(input);
            changed = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input)).checked();
            assertNotEquals(before.logicalDigest(), changed.logicalDigest());
            assertNotEquals(before.bindingDigests().get("mock-pg"), changed.bindingDigests().get("mock-pg"));
        }
    }
    @Test void jsonYamlAndMathematicallyIntegralDecimalsRemainEquivalent() throws Exception {
        var input = fixture();
        var baseline = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input)).checked();
        // JSON is a YAML 1.2 subset; prepend a YAML document marker and use an integral decimal.
        String yaml = "---\n" + JSON_MAPPER.writeValueAsString(input).replace("\"revision\":1", "\"revision\":1.0");
        var result = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compiler.compile(yaml.getBytes(StandardCharsets.UTF_8), YAML));
        assertEquals(baseline, result.checked());
    }
    @Test void allNestedCollectionsAndCapabilityMetadataAreImmutable() throws Exception {
        var result = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(fixture())).checked();
        assertThrows(UnsupportedOperationException.class, () -> result.mechanisms().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.bindingDigests().clear());
        assertEquals(java.util.List.of("generic-graph-v1", "native-compiler-v2", "xml-path-v1", "xml-span-v1"), java.util.List.copyOf(result.mechanisms().keySet()));
        var definition = result.definition();
        assertThrows(UnsupportedOperationException.class, () -> definition.bindings().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.logical().entityTypes().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.logical().entityTypes().getFirst().fields().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.logical().relations().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.logical().rules().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.logical().operationCapabilities().clear());
        var document = definition.bindings().getFirst().documents().getFirst();
        assertThrows(UnsupportedOperationException.class, () -> definition.bindings().getFirst().documents().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.entities().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.entities().getFirst().path().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.entities().getFirst().fields().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.entities().getFirst().references().clear());
    }
    @Test void malformedUnknownAndOversizedInputsReturnOnlySafeRefusals() throws Exception {
        for (String source : java.util.List.of("{} {}", "{\"schemaVersion\":\"2\",\"schemaVersion\":\"2\"}", "{\"x\":1e1000000000}"))
            assertInstanceOf(NativeCompilationResult.Rejected.class, compiler.compile(source.getBytes(StandardCharsets.UTF_8), JSON));
        rejected(compiler.compile(new byte[1048577], JSON), "RESOURCE_LIMIT");
        rejected(compiler.compile(new byte[] {(byte) 0xc3, 0x28}, JSON), "INVALID_UTF8");
        for (String yaml : java.util.List.of("x: !!str hidden-canary", "x: &a value", "x: *a", "---\na: b\n---\na: c")) {
            var result = compiler.compile(yaml.getBytes(StandardCharsets.UTF_8), YAML);
            rejected(result, "INVALID_SYNTAX"); assertFalse(result.toString().contains("hidden-canary"));
        }
        var input = fixture(); object(input, "/bindings/0/documents/0/entities/0").put("hidden/~canary", "private-value");
        var result = compile(input); rejected(result, "SCHEMA_VIOLATION");
        assertFalse(result.toString().contains("canary")); assertFalse(result.toString().contains("private-value"));
        assertEquals("/bindings/0/documents/0/entities/0", result.diagnostics().getFirst().pointer());
        input = fixture(); input.set("mechanisms", JSON_MAPPER.createObjectNode().put("xml-span-v1", 999));
        rejected(compile(input), "SCHEMA_VIOLATION");
        input = fixture(); array(input, "/logical/operationCapabilities").add("execute-sql");
        rejected(compile(input), "SCHEMA_VIOLATION");
    }
    @Test void packagedV2SchemaMatchesItsOnlyCanonicalSource() throws Exception {
        try (var resource = getClass().getResourceAsStream("/schemas/definition-v2.schema.json")) {
            assertNotNull(resource); assertArrayEquals(Files.readAllBytes(Path.of("../../schemas/definition-v2.schema.json")), resource.readAllBytes());
        }
    }
    private static void reverse(tools.jackson.databind.node.ArrayNode array) {
        var values = new java.util.ArrayList<JsonNode>(); array.forEach(values::add); java.util.Collections.reverse(values);
        array.removeAll(); values.forEach(array::add);
    }
    private static JsonNode reverseObjects(JsonNode node) {
        if (node.isObject()) {
            var reversed = JSON_MAPPER.createObjectNode(); var entries = new java.util.ArrayList<>(node.properties());
            java.util.Collections.reverse(entries); entries.forEach(entry -> reversed.set(entry.getKey(), reverseObjects(entry.getValue()))); return reversed;
        }
        if (node.isArray()) { var array = JSON_MAPPER.createArrayNode(); node.forEach(item -> array.add(reverseObjects(item))); return array; }
        return node;
    }
    @Test void astralExpandedNamesAndNumericExponentBoundaryStayExact() throws Exception {
        var input = fixture();
        object(input, "/bindings/0/documents/0/entities/0/path/0").put("localName", "𐀀glyph");
        var result = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input));
        assertEquals("𐀀glyph", result.checked().definition().bindings().getFirst().documents().getFirst().entities().getFirst().path().getFirst().localName());
        String text = JSON_MAPPER.writeValueAsString(fixture()).replace("\"revision\":1", "\"revision\":1e1023");
        result = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compiler.compile(text.getBytes(StandardCharsets.UTF_8), JSON));
        assertEquals(java.math.BigInteger.TEN.pow(1023), result.checked().definition().revision());
        rejected(compiler.compile(text.replace("1e1023", "1e1024").getBytes(StandardCharsets.UTF_8), JSON), "RESOURCE_LIMIT");
    }
    @Test void xmlDepthLimitBlocksPublicationOnlyBeyondSupportedBoundary() throws Exception {
        var input = fixture();
        var path = array(input, "/bindings/0/documents/0/entities/0/path");
        while (path.size() < 128) path.add(path.get(0).deepCopy());
        assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input));
        path.add(path.get(0).deepCopy());
        incomplete(compile(input), "XML_DEPTH_UNSUPPORTED");
    }
    @Test void requiredAttributesAndReferencesRespectXmlAttributeBudget() throws Exception {
        var input = attributeFixture(255, false);
        assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input)); // 255 fields + required reference
        input = attributeFixture(256, false);
        incomplete(compile(input), "XML_ATTRIBUTES_UNSUPPORTED");
        object(input, "/logical/relations/0").put("minimum", 0);
        assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input));
        input = attributeFixture(257, false);
        object(input, "/logical/relations/0").put("minimum", 0);
        incomplete(compile(input), "XML_ATTRIBUTES_UNSUPPORTED");
        object(input, "/logical/entityTypes/0/fields/256").put("required", false);
        assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input));
    }
    @Test void creationBudgetIncludesNecessaryNamespaceDeclarations() throws Exception {
        var input = attributeFixture(254, true);
        assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input)); // 255 attributes + root namespace
        input = attributeFixture(255, true);
        incomplete(compile(input), "XML_ATTRIBUTES_UNSUPPORTED");
        input = attributeFixture(254, true);
        object(input, "/bindings/0/documents/0/entities/0/fields/1/attribute").put("namespaceUri", "urn:mock:other");
        incomplete(compile(input), "XML_ATTRIBUTES_UNSUPPORTED");
        object(input, "/bindings/0/documents/0/entities/0/fields/1/attribute").put("namespaceUri", "http://www.w3.org/XML/1998/namespace");
        assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input));
        input = attributeFixture(255, true);
        object(input, "/bindings/0/documents/0/entities/0/path/1").put("namespaceUri", "");
        incomplete(compile(input), "XML_ATTRIBUTES_UNSUPPORTED");
    }
    @Test void documentRootBudgetCountsDeclarationsButNonRootMayInheritThem() throws Exception {
        var input = attributeFixture(255, false);
        var path = array(input, "/bindings/0/documents/0/entities/0/path");
        path.remove(0);
        incomplete(compile(input), "XML_ATTRIBUTES_UNSUPPORTED");
        object(input, "/bindings/0/documents/0/entities/0/path/0").put("namespaceUri", "");
        assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input));
        object(input, "/bindings/0/documents/0/entities/0/references/0/attribute").put("namespaceUri", "urn:mock:reference");
        incomplete(compile(input), "XML_ATTRIBUTES_UNSUPPORTED");
        object(input, "/logical/relations/0").put("minimum", 0);
        assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, compile(input));
    }
    private ObjectNode attributeFixture(int fieldCount, boolean create) throws Exception {
        var input = fixture();
        if (!create) array(input, "/logical/operationCapabilities").removeAll().add("retain-entity");
        var fields = array(input, "/logical/entityTypes/0/fields");
        while (fields.size() < fieldCount) {
            String id = "field" + fields.size();
            var field = (ObjectNode) fields.get(1).deepCopy(); field.put("id", id); fields.add(field);
            for (int binding = 0; binding < 2; binding++) {
                var mappings = array(input, "/bindings/" + binding + "/documents/0/entities/0/fields");
                var mapping = (ObjectNode) mappings.get(1).deepCopy(); mapping.put("field", id);
                ((ObjectNode) mapping.get("attribute")).put("localName", id); mappings.add(mapping);
            }
        }
        return input;
    }

}
