package studio.environment.core.definitionv2;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.definitionv2.NativeDefinition.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;

class NativeModelTest {
    @Test void callerCollectionsCannotChangeDeclaredContractsOrCheckedDigests() {
        var fields = new ArrayList<>(List.of(new NativeDefinition.Field("tag", ValueType.TEXT, true, Classification.STRUCTURAL, Sensitivity.PUBLIC, true, false)));
        var type = new EntityType("glyph", "Invented glyph", fields, new Identity("tag"));
        var types = new ArrayList<>(List.of(type));
        var operations = new ArrayList<>(List.of(Operation.RETAIN_ENTITY));
        var logical = new Logical(types, new ArrayList<>(), new ArrayList<>(), operations);
        var path = new ArrayList<>(List.of(new ExpandedName("urn:mock", "tiles"), new ExpandedName("urn:mock", "glyph")));
        var mappings = new ArrayList<>(List.of(new FieldMapping("tag", new ExpandedName("", "id"))));
        var references = new ArrayList<ReferenceMapping>();
        var projection = new Projection("glyphs", "glyph", path, mappings, references);
        var projections = new ArrayList<>(List.of(projection));
        var document = new Document("sheet", "1", projections);
        var documents = new ArrayList<>(List.of(document));
        var binding = new Binding("mock-binding", Engine.POSTGRESQL, Storage.TEXT, "mock_schema", "mock_table", "mock_key", "mock_xml", KeyType.INT64, documents);
        var bindings = new ArrayList<>(List.of(binding));
        var definition = new NativeDefinition("mock-definition", BigInteger.ONE, logical, bindings);
        var checked = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(definition)).checked();
        fields.clear(); types.clear(); operations.clear(); path.clear(); mappings.clear(); references.clear(); projections.clear(); documents.clear(); bindings.clear();
        assertEquals(1, definition.logical().entityTypes().size());
        assertEquals(1, definition.logical().entityTypes().getFirst().fields().size());
        assertEquals(2, definition.bindings().getFirst().documents().getFirst().entities().getFirst().path().size());
        assertEquals(1, definition.bindings().getFirst().documents().getFirst().entities().getFirst().fields().size());
        assertEquals(checked, assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(definition)).checked());
        var digests = new LinkedHashMap<>(checked.bindingDigests()); var mechanisms = new LinkedHashMap<>(checked.mechanisms());
        var copy = new NativeCompilationResult.Checked(definition, checked.logicalDigest(), digests, mechanisms);
        digests.clear(); mechanisms.clear();
        assertEquals(checked, copy);
    }
    @Test void rejectedResultsCopySortAndDeduplicateSafeDiagnostics() {
        var later = new DefinitionDiagnostic(DefinitionDiagnostic.Phase.SEMANTIC, "LATER", "/logical", "Correct the declaration.");
        var earlier = new DefinitionDiagnostic(DefinitionDiagnostic.Phase.SHAPE, "EARLIER", "", "Correct the shape.");
        var diagnostics = new ArrayList<>(List.of(later, earlier, later));
        var result = new NativeCompilationResult.Rejected(diagnostics); diagnostics.clear();
        assertEquals(List.of(earlier, later), result.diagnostics());
        assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().clear());
        assertThrows(IllegalArgumentException.class, () -> new NativeCompilationResult.Rejected(List.of()));
    }
}
