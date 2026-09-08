package studio.environment.core.definition;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static studio.environment.core.definition.DefinitionDraft.*;

/** All declarations in this test were invented independently for generic compiler behavior. */
class DefinitionCompilerTest {
    private final DefinitionCompiler compiler = new DefinitionCompiler();
    static DefinitionDraft draft(List<EntityType> types, List<Relation> relations, List<Document> documents) {
        return new DefinitionDraft("1", "mock-orbit", BigInteger.ONE, Status.PUBLISHED,
                types, relations, documents, List.of("mock-rule"), List.of());
    }
    static EntityType type(String id) {
        return new EntityType(id, "Invented label", List.of(new Field("tone", ValueType.TEXT, true,
                Classification.ENVIRONMENT, Sensitivity.UNKNOWN)));
    }
    static Document document(List<Mapping> mappings) {
        return new Document("sheet", "mock-store", "mock-key", new LinkedHashMap<>(), mappings);
    }
    @Test void arbitraryRecursiveDeclarationRemainsIncompleteEvenWhenUploadedAsPublished() {
        var draft = draft(List.of(type("orb")), List.of(new Relation("loop", "orb", "orb",
                RelationKind.CONTAINMENT, BigInteger.ZERO, BigInteger.TEN, true)), List.of(document(List.of())));
        var result = assertInstanceOf(DefinitionResult.Incomplete.class, compiler.compile(draft));
        assertEquals(draft, result.draft());
        assertEquals(List.of("IDENTITY_UNDECLARED", "INVENTORY_UNDECLARED", "OPERATIONS_UNQUALIFIED",
                "SELECTORS_UNQUALIFIED", "WRITERS_UNQUALIFIED", "SENSITIVITY_UNKNOWN", "RULE_UNBOUND"),
                result.diagnostics().stream().map(DefinitionDiagnostic::code).toList());
        assertEquals(result, compiler.compile(draft));
    }
    @Test void rejectsDuplicateIdsDanglingReferencesAndInvertedCardinality() {
        var first = type("orb");
        var document = document(List.of(new Mapping("orb", "missing", "/mock", "@mock", BigInteger.ONE, "mock-writer")));
        var relation = new Relation("edge", "absent", "orb", RelationKind.REFERENCE, BigInteger.TEN, BigInteger.ONE, true);
        var result = assertInstanceOf(DefinitionResult.Rejected.class,
                compiler.compile(draft(List.of(first, first), List.of(relation, relation), List.of(document, document))));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("DUPLICATE_ID") && d.pointer().equals("/entityTypes/1/id")));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("UNKNOWN_TYPE") && d.pointer().equals("/relations/0/fromType")));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("CARDINALITY_INVERTED")));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("UNKNOWN_FIELD")));
    }
    @Test void nestedCollectionsAreDefensivelyCopied() {
        var fields = new ArrayList<>(type("orb").fields());
        var entity = new EntityType("orb", "Orb", fields);
        var namespaces = new LinkedHashMap<String, String>(); namespaces.put("m", "urn:mock");
        var mappings = new ArrayList<Mapping>();
        var doc = new Document("sheet", "mock-store", "mock-key", namespaces, mappings);
        var types = new ArrayList<>(List.of(entity));
        var draft = draft(types, new ArrayList<>(), new ArrayList<>(List.of(doc)));
        fields.clear(); types.clear(); namespaces.clear(); mappings.add(new Mapping("orb", "tone", "/m", "@m", BigInteger.ONE, "mock-writer"));
        assertEquals(1, draft.entityTypes().getFirst().fields().size());
        assertEquals("urn:mock", draft.documents().getFirst().namespaces().get("m"));
        assertTrue(draft.documents().getFirst().mappings().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> draft.entityTypes().clear());
        assertThrows(UnsupportedOperationException.class, () -> entity.fields().clear());
        assertThrows(UnsupportedOperationException.class, () -> doc.namespaces().clear());
        assertThrows(UnsupportedOperationException.class, () -> doc.mappings().clear());
    }
    @Test void fieldUniquenessIsScopedToEachTypeAndDanglingMappingTypesReject() {
        var field = type("orb").fields().getFirst();
        var duplicateFields = new EntityType("orb", "Orb", List.of(field, field));
        var result = assertInstanceOf(DefinitionResult.Rejected.class,
                compiler.compile(draft(List.of(duplicateFields), List.of(), List.of(document(List.of())))));
        assertEquals(List.of(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.SEMANTIC, "DUPLICATE_ID",
                "/entityTypes/0/fields/1/id", "Use a unique identifier within this declaration collection.")), result.diagnostics());
        assertInstanceOf(DefinitionResult.Incomplete.class,
                compiler.compile(draft(List.of(type("orb"), type("arc")), List.of(), List.of(document(List.of())))));
        var mapping = new Mapping("missing", "tone", "/mock", "@mock", BigInteger.ONE, "mock-writer");
        result = assertInstanceOf(DefinitionResult.Rejected.class,
                compiler.compile(draft(List.of(type("orb")), List.of(), List.of(document(List.of(mapping))))));
        assertEquals(List.of(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.SEMANTIC, "UNKNOWN_TYPE",
                "/documents/0/mappings/0/entityType", "Reference a declared entity type.")), result.diagnostics());
    }
    @Test void resultsEnforceDisjointStatesAndSortDeduplicateDiagnostics() {
        var first = new DefinitionDiagnostic(DefinitionDiagnostic.Phase.SEMANTIC, "B", "/a", "Correct the declaration.");
        var second = new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PARSE, "A", "", "Correct the input.");
        var input = new ArrayList<>(List.of(first, second, first));
        var result = new DefinitionResult.Rejected(input); input.clear();
        assertEquals(List.of(second, first), result.diagnostics());
        assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().clear());
        assertThrows(IllegalArgumentException.class, () -> new DefinitionResult.Rejected(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DefinitionResult.Incomplete(
                draft(List.of(type("orb")), List.of(), List.of(document(List.of()))), List.of(first)));
    }
}
