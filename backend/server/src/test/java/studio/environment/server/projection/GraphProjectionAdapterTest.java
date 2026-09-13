package studio.environment.server.projection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.server.definition.DefinitionBytesCompiler;
import studio.environment.server.definition.NativeDefinitionBytesCompiler;
import static org.junit.jupiter.api.Assertions.*;

class GraphProjectionAdapterTest {
    @Test void historicalCompilerMechanismCannotAuthorizeFreshProjection() throws Exception {
        var checked = definition().checked(); var versions = new java.util.TreeMap<>(checked.mechanisms());
        versions.put("native-compiler-v2", java.math.BigInteger.ONE);
        var historical = new NativeCompilationResult.ReadyToPublish(new NativeCompilationResult.Checked(
            checked.definition(), checked.logicalDigest(), checked.bindingDigests(), versions));
        refused(adapter.project(historical, "mock-pg", sources()), "UNSUPPORTED_MECHANISM");
    }
    private final GraphProjectionAdapter adapter = new GraphProjectionAdapter();
    private NativeCompilationResult.ReadyToPublish definition() throws Exception {
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionBytesCompiler().compile(
                Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")), DefinitionBytesCompiler.Format.JSON));
    }
    private List<DocumentSource> sources() throws Exception {
        return List.of(new DocumentSource("palette-sheet", Files.readString(Path.of("../../fixtures/native-v2/xml/palettes.xml"))),
                new DocumentSource("glyph-sheet", Files.readString(Path.of("../../fixtures/native-v2/xml/glyphs.xml"))));
    }
    @Test void projectsCompleteTwoToOneGraphWithExactValuesAndOrigins() throws Exception {
        var result = assertInstanceOf(ProjectionResult.Accepted.class, adapter.project(definition(), "mock-pg", sources()));
        assertEquals(3, result.graph().entities().size());
        var first = result.graph().entities().get(0);
        assertEquals("glyph", first.key().type()); assertEquals("alpha", first.key().identity());
        assertEquals("  blue & 𐀀\t", first.fields().get("tone"));
        assertEquals("glyph-sheet", first.origin().documentId()); assertEquals("glyphs", first.origin().projectionId());
        assertEquals(1, first.origin().elementIndex()); assertEquals(List.of(0), first.origin().ancestry());
        assertEquals("beta", result.graph().entities().get(1).key().identity());
        assertEquals("", result.graph().entities().get(1).fields().get("tone"));
        assertEquals("shared", result.graph().entities().get(2).key().identity());
        assertEquals("warm", result.graph().entities().get(2).fields().get("shade"));
        assertEquals(2, result.graph().edges().size());
        for (var edge : result.graph().edges()) {
            assertEquals("uses", edge.relation()); assertEquals("palette", edge.target().type()); assertEquals("shared", edge.target().identity());
        }
        assertEquals(List.of("alpha", "beta"), result.graph().edges().stream().map(e -> e.source().identity()).toList());
        for (var source : sources()) {
            var retained = result.projection().documents().stream().filter(d -> d.documentId().equals(source.documentId())).findFirst().orElseThrow();
            assertEquals(source.source(), retained.source());
        }
    }
    @Test void incompleteInventoryHasNoGraphAndSafeDiagnostics() throws Exception {
        var result = assertInstanceOf(ProjectionResult.Rejected.class, adapter.project(definition(), "mock-pg", sources().subList(0, 1)));
        assertEquals("INVENTORY_MISMATCH", result.diagnostics().getFirst().code());
    }
    private tools.jackson.databind.node.ObjectNode declaration() throws Exception {
        return (tools.jackson.databind.node.ObjectNode) tools.jackson.databind.json.JsonMapper.builder().build().readTree(
                Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
    }
    private NativeCompilationResult.ReadyToPublish compile(tools.jackson.databind.node.ObjectNode node) {
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionBytesCompiler().compile(
                tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(node), DefinitionBytesCompiler.Format.JSON));
    }
    private static tools.jackson.databind.node.ObjectNode object(tools.jackson.databind.JsonNode node, String pointer) {
        return (tools.jackson.databind.node.ObjectNode) node.at(pointer);
    }
    private static tools.jackson.databind.node.ArrayNode array(tools.jackson.databind.JsonNode node, String pointer) {
        return (tools.jackson.databind.node.ArrayNode) node.at(pointer);
    }
    private List<DocumentSource> glyphs(String source) throws Exception {
        return List.of(sources().getFirst(), new DocumentSource("glyph-sheet", source));
    }
    private static void refused(ProjectionResult result, String code) {
        var refusal = assertInstanceOf(ProjectionResult.Rejected.class, result);
        assertTrue(refusal.diagnostics().stream().anyMatch(d -> d.code().equals(code)), refusal::toString);
    }
    @Test void duplicateExtraUnknownAndOversizedInventoriesRefuseWithoutEchoingInputIds() throws Exception {
        refused(adapter.project(definition(), "mock-pg", List.of(sources().getFirst(), sources().getFirst())), "INVENTORY_MISMATCH");
        var result = adapter.project(definition(), "mock-pg", List.of(sources().getFirst(), new DocumentSource("raw-canary-id", "raw-canary-xml")));
        refused(result, "INVENTORY_MISMATCH"); assertFalse(result.toString().contains("canary"));
        refused(adapter.project(definition(), "mock-pg", List.of(sources().getFirst(), sources().getLast(), sources().getFirst())), "INVENTORY_MISMATCH");
        refused(adapter.project(definition(), "raw-canary-binding", sources()), "UNKNOWN_BINDING");
        var virtual = new java.util.AbstractList<DocumentSource>() {
            @Override public int size() { return Integer.MAX_VALUE; }
            @Override public DocumentSource get(int index) { throw new AssertionError("Must refuse before copying/iterating."); }
        };
        refused(adapter.project(definition(), "mock-pg", virtual), "RESOURCE_LIMIT");
    }
    @Test void requiredScalarIdentityAndReferenceFailuresNeverProducePartialGraphs() throws Exception {
        String source = sources().getLast().source();
        for (var entry : java.util.Map.of(
                "DUPLICATE_IDENTITY", source.replace("id=\"beta\"", "id=\"alpha\""),
                "INVALID_IDENTITY", source.replace("id=\"alpha\"", "id=\"\""),
                "REQUIRED_FIELD_MISSING", source.replace("tone=\"\"", ""),
                "UNRESOLVED_REFERENCE", source.replace("palette=\"shared\"", "palette=\"raw-canary\""),
                "RELATION_CARDINALITY", source.replace("palette=\"shared\"", "")).entrySet()) {
            var result = adapter.project(definition(), "mock-pg", glyphs(entry.getValue()));
            refused(result, entry.getKey()); assertFalse(result.toString().contains("raw-canary"));
        }
        refused(adapter.project(definition(), "mock-pg", glyphs(source.replace("palette=\"shared\"", "palette=\"\""))), "UNRESOLVED_REFERENCE");
        for (String codec : List.of("integer", "boolean", "uri")) {
            var node = declaration(); object(node, "/logical/entityTypes/0/fields/1").put("valueType", codec);
            refused(adapter.project(compile(node), "mock-pg", sources()), "INVALID_SCALAR");
        }
        var noRules = declaration(); array(noRules, "/logical/rules").removeAll();
        refused(adapter.project(compile(noRules), "mock-pg", glyphs(source.replace("tone=\"\"", ""))), "REQUIRED_FIELD_MISSING");
        refused(adapter.project(definition(), "mock-pg", glyphs("<tiles xmlns='urn:mock:tiles'/>")), "ENTITY_COUNT");
    }
    @Test void optionalAbsencePresentEmptyNamespaceAliasesAndExactUnicodeRemainDistinct() throws Exception {
        var node = declaration(); object(node, "/logical/entityTypes/0/fields/1").put("required", false);
        object(node, "/logical/relations/0").put("minimum", 0);
        String source = "<r:tiles xmlns:r='urn:mock:tiles' xmlns:x='urn:mock:tiles'><x:glyph id='alpha'/><r:glyph id='beta' tone=''/></r:tiles>";
        var result = assertInstanceOf(ProjectionResult.Accepted.class, adapter.project(compile(node), "mock-pg", glyphs(source)));
        assertFalse(result.graph().entities().get(0).fields().containsKey("tone"));
        assertTrue(result.graph().entities().get(1).fields().containsKey("tone"));
        assertEquals("", result.graph().entities().get(1).fields().get("tone")); assertTrue(result.graph().edges().isEmpty());
        // Default namespace does not apply to attributes; the unmapped namespaced attribute is not a field.
        refused(adapter.project(definition(), "mock-pg", glyphs(source.replace("tone=''", "x:tone=''"))), "REQUIRED_FIELD_MISSING");
        source = "<tiles xmlns='urn:mock:tiles'><glyph id=' 𐀀 ' tone='&#13;&#10;&#9;' palette='shared'/><glyph id='𐀀' tone='' palette='shared'/></tiles>";
        result = assertInstanceOf(ProjectionResult.Accepted.class, adapter.project(definition(), "mock-pg", glyphs(source)));
        assertEquals(" 𐀀 ", result.graph().entities().get(0).key().identity());
        assertEquals("\r\n\t", result.graph().entities().get(0).fields().get("tone"));
    }
    @Test void sourceOrderingDigestsAndSafeRepresentationsAreStable() throws Exception {
        var first = assertInstanceOf(ProjectionResult.Accepted.class, adapter.project(definition(), "mock-pg", sources()));
        var reversed = new java.util.ArrayList<>(sources()); java.util.Collections.reverse(reversed);
        assertEquals(first, adapter.project(definition(), "mock-pg", reversed));
        var oracle = assertInstanceOf(ProjectionResult.Accepted.class, adapter.project(definition(), "mock-oracle", sources()));
        assertEquals(first.graph(), oracle.graph()); assertEquals(first.logicalDigest(), oracle.logicalDigest());
        assertNotEquals(first.bindingDigest(), oracle.bindingDigest());
        for (var source : first.projection().documents()) {
            String expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.source().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertEquals(expected, source.digest());
            for (var origin : first.graph().origins()) if (origin.documentId().equals(source.documentId())) assertEquals(expected, origin.sourceDigest());
        }
        assertEquals(List.of("glyph-sheet", "glyph-sheet", "palette-sheet"), first.graph().origins().stream().map(o -> o.documentId()).toList());
        for (Object value : List.of(first, first.graph(), first.projection(), first.projection().documents().getFirst(), sources().getLast(),
                first.graph().entities().getFirst(), first.graph().entities().getFirst().key(), first.graph().edges().getFirst())) {
            assertFalse(value.toString().contains("alpha")); assertFalse(value.toString().contains("blue")); assertFalse(value.toString().contains("<"));
        }
        assertThrows(UnsupportedOperationException.class, () -> first.graph().entities().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.graph().edges().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.graph().entities().getFirst().fields().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.graph().entities().getFirst().origin().ancestry().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.projection().documents().clear());
    }
    @Test void malformedXmlUnicodeAndPerDocumentLimitsRefuseSafely() throws Exception {
        for (String xml : List.of("<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///raw-canary'>]><x>&a;</x>", "<raw-canary>", "<x>\ud800</x>")) {
            var result = adapter.project(definition(), "mock-pg", glyphs(xml));
            assertInstanceOf(ProjectionResult.Rejected.class, result); assertFalse(result.toString().contains("raw-canary"));
        }
        refused(adapter.project(definition(), "mock-pg", glyphs("x".repeat(1_048_577))), "RESOURCE_LIMIT");
        refused(adapter.project(definition(), "mock-pg", glyphs("<x>".repeat(129) + "</x>".repeat(129))), "RESOURCE_LIMIT");
    }
    @Test void wholeScopeUtf8LimitRefusesBeforeXmlParsing() throws Exception {
        var node = declaration();
        var documents = array(node, "/bindings/0/documents");
        var prototype = (tools.jackson.databind.node.ObjectNode) documents.get(documents.size() - 1).deepCopy();
        var input = new java.util.ArrayList<DocumentSource>(sources());
        // Four-byte UTF-8 characters make this exceed 16MiB while each document is under its UTF-16 limit.
        String large = "<tiles xmlns='urn:mock:tiles'>" + "𐀀".repeat(500_000) + "</tiles>";
        for (int i = 0; i < 9; i++) {
            var document = prototype.deepCopy(); document.put("id", "extra" + i).put("key", Integer.toString(i + 3));
            object(document, "/entities/0").put("id", "extra" + i); documents.add(document);
            input.add(new DocumentSource("extra" + i, large));
        }
        refused(adapter.project(compile(node), "mock-pg", input), "RESOURCE_LIMIT");
    }

    @Test void containmentUsesSelectedPhysicalAncestorAcrossNamespaceAliases() throws Exception {
        var node = declaration();
        object(node, "/logical/relations/0").put("kind", "containment").put("fromType", "palette").put("toType", "glyph").put("minimum", 2).put("maximum", 2);
        for (int binding = 0; binding < 2; binding++) {
            String base = "/bindings/" + binding + "/documents/";
            var palette = object(node, base + "1/entities/0").deepCopy(); palette.put("id", "nested-palettes");
            array(node, base + "0/entities").add(palette);
            array(node, base + "0/entities/0/path").insert(1, object(node, base + "1/entities/0/path/1").deepCopy());
            array(node, base + "0/entities/0/references").removeAll();
        }
        String xml = "<r:tiles xmlns:r='urn:mock:tiles'><r:palette id='shared' shade='warm'><r:glyph id='alpha' tone='a'/><r:glyph id='beta' tone='b'/></r:palette></r:tiles>";
        var sources = List.of(new DocumentSource("glyph-sheet", xml), new DocumentSource("palette-sheet", "<tiles xmlns='urn:mock:tiles'/>"));
        var result = assertInstanceOf(ProjectionResult.Accepted.class, adapter.project(compile(node), "mock-pg", sources));
        assertEquals(2, result.graph().edges().size());
        for (var edge : result.graph().edges()) assertEquals("shared", edge.source().identity());
        assertEquals(List.of("alpha", "beta"), result.graph().edges().stream().map(e -> e.target().identity()).toList());
        assertEquals(List.of(0, 1), result.graph().entities().getFirst().origin().ancestry());
    }
    @Test void overlappingProjectionAndUnknownMechanismCannotAssertCompleteness() throws Exception {
        var checked = definition().checked(); var def = checked.definition(); var binding = def.bindings().getFirst();
        var doc = binding.documents().getFirst(); var projection = doc.entities().getFirst();
        var duplicate = new studio.environment.core.definitionv2.NativeDefinition.Projection("overlap", projection.type(), projection.path(), projection.fields(), projection.references());
        var changedDoc = new studio.environment.core.definitionv2.NativeDefinition.Document(doc.id(), doc.key(), List.of(projection, duplicate));
        var changedBinding = new studio.environment.core.definitionv2.NativeDefinition.Binding(binding.id(), binding.engine(), binding.storage(), binding.schema(), binding.table(), binding.keyColumn(), binding.xmlColumn(), binding.keyType(), List.of(changedDoc, binding.documents().getLast()));
        var changedDef = new studio.environment.core.definitionv2.NativeDefinition(def.id(), def.revision(), def.logical(), List.of(changedBinding));
        var injected = new NativeCompilationResult.ReadyToPublish(new NativeCompilationResult.Checked(changedDef, checked.logicalDigest(), checked.bindingDigests(), checked.mechanisms()));
        refused(adapter.project(injected, "mock-pg", sources()), "AMBIGUOUS_PROJECTION");
        injected = new NativeCompilationResult.ReadyToPublish(new NativeCompilationResult.Checked(def, checked.logicalDigest(), checked.bindingDigests(), java.util.Map.of()));
        refused(adapter.project(injected, "mock-pg", sources()), "UNSUPPORTED_MECHANISM");
    }

    @Test void completeInventoryAccepts128DocumentsAndRefuses129BeforeCopying() throws Exception {
        var node = declaration(); var docs = array(node, "/bindings/0/documents");
        var prototype = object(node, "/bindings/0/documents/1").deepCopy();
        var inputs = new java.util.ArrayList<>(sources());
        for (int i = 2; i < 128; i++) {
            var doc = prototype.deepCopy(); doc.put("id", "doc" + i).put("key", Integer.toString(i + 1));
            object(doc, "/entities/0").put("id", "projection" + i); docs.add(doc);
            inputs.add(new DocumentSource("doc" + i, "<tiles xmlns='urn:mock:tiles'/>"));
        }
        var result = assertInstanceOf(ProjectionResult.Accepted.class, adapter.project(compile(node), "mock-pg", inputs));
        assertEquals(128, result.projection().documents().size());
        var doc = prototype.deepCopy(); doc.put("id", "extra").put("key", "1000"); object(doc, "/entities/0").put("id", "extra"); docs.add(doc);
        refused(adapter.project(compile(node), "mock-pg", inputs), "RESOURCE_LIMIT");
    }
    @Test void aggregateEntityLimitIncludesEveryDocumentAndNeverReturnsTruncatedSuccess() throws Exception {
        var node = declaration(); object(node, "/logical/rules/0").put("maximum", 30_000);
        var text = new StringBuilder("<tiles xmlns='urn:mock:tiles'>");
        for (int i = 0; i < 19_999; i++) text.append("<glyph id='g").append(i).append("' tone='' palette='shared'/>");
        text.append("</tiles>"); assertTrue(text.length() < 1_048_576);
        var inputs = glyphs(text.toString());
        var ready = compile(node);
        var result = assertInstanceOf(ProjectionResult.Accepted.class, adapter.project(ready, "mock-pg", inputs));
        assertEquals(20_000, result.graph().entities().size());
        var changed = new java.util.ArrayList<>(inputs);
        changed.set(0, new DocumentSource("palette-sheet", "<tiles xmlns='urn:mock:tiles'><palette id='shared' shade=''/><palette id='other' shade=''/></tiles>"));
        refused(adapter.project(ready, "mock-pg", changed), "RESOURCE_LIMIT");
    }
    @Test void duplicateIdentityAcrossDocumentsRefusesAndNamespacedAttributeCodecPreservesText() throws Exception {
        var node = declaration(); var copy = object(node, "/bindings/0/documents/0/entities/0").deepCopy(); copy.put("id", "other-glyphs");
        array(node, "/bindings/0/documents/1/entities").add(copy);
        var input = new java.util.ArrayList<>(sources());
        input.set(0, new DocumentSource("palette-sheet", "<tiles xmlns='urn:mock:tiles'><palette id='shared' shade=''/><glyph id='alpha' tone='' palette='shared'/></tiles>"));
        refused(adapter.project(compile(node), "mock-pg", input), "DUPLICATE_IDENTITY");
        node = declaration();
        object(node, "/bindings/0/documents/0/entities/0/fields/1/attribute").put("namespaceUri", "urn:mock:field");
        String source = "<tiles xmlns='urn:mock:tiles' xmlns:f='urn:mock:field'><glyph id='alpha' f:tone='-123456789012345678901234567890' palette='shared'/><glyph id='beta' f:tone='0' palette='shared'/></tiles>";
        object(node, "/logical/entityTypes/0/fields/1").put("valueType", "integer");
        var result = assertInstanceOf(ProjectionResult.Accepted.class, adapter.project(compile(node), "mock-pg", glyphs(source)));
        assertEquals("-123456789012345678901234567890", result.graph().entities().getFirst().fields().get("tone"));
        refused(adapter.project(compile(node), "mock-pg", glyphs(source.replace("f:tone='0'", "f:tone='01'"))), "INVALID_SCALAR");
    }

    @Test void compilerReadyFifthEditionNamesProjectWithExactSources() throws Exception {
        var node = declaration();
        object(node, "/bindings/0/documents/0/entities/0/path/1").put("localName", "Ϳglyph");
        object(node, "/bindings/0/documents/0/entities/0/fields/1/attribute").put("localName", "豈tone");
        String xml = "<𐀀:tiles xmlns:𐀀='urn:mock:tiles'><?‌target untouched?><𐀀:Ϳglyph id='alpha' 豈tone=' 𐀀 ' palette='shared'/><𐀀:Ϳglyph id='beta' 豈tone='' palette='shared'/></𐀀:tiles>";
        var ready = compile(node);
        var result = assertInstanceOf(ProjectionResult.Accepted.class, adapter.project(ready, "mock-pg", glyphs(xml)));
        assertEquals(3, result.graph().entities().size());
        assertEquals(" 𐀀 ", result.graph().entities().getFirst().fields().get("tone"));
        assertEquals(xml, result.projection().documents().getFirst().source());
    }

}
