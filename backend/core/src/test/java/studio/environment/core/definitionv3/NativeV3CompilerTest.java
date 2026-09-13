package studio.environment.core.definitionv3;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.definitionv3.NativeDefinition.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definitionv2.NativeDefinition.Binding;
import studio.environment.core.definitionv2.NativeDefinition.CountRule;
import studio.environment.core.definitionv2.NativeDefinition.Document;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.definitionv2.NativeDefinition.EntityType;
import studio.environment.core.definitionv2.NativeDefinition.ExpandedName;
import studio.environment.core.definitionv2.NativeDefinition.Field;
import studio.environment.core.definitionv2.NativeDefinition.FieldMapping;
import studio.environment.core.definitionv2.NativeDefinition.Identity;
import studio.environment.core.definitionv2.NativeDefinition.KeyType;
import studio.environment.core.definitionv2.NativeDefinition.Operation;
import studio.environment.core.definitionv2.NativeDefinition.Projection;
import studio.environment.core.definitionv2.NativeDefinition.Storage;

/** Independently invented compiler declarations; no XML, database or application inputs. */
class NativeV3CompilerTest {
    private final NativeDefinitionCompiler compiler = new NativeDefinitionCompiler();
    static NativeDefinition fixture() {
        var type = new EntityType("item", "Invented item", List.of(
                new Field("id", ValueType.TEXT, true, Classification.STRUCTURAL, Sensitivity.PUBLIC, true, false),
                new Field("tone", ValueType.TEXT, false, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true)), new Identity("id"));
        var logical = new Logical(List.of(type), List.of(), List.of(), List.of(Operation.RETAIN_ENTITY),
                List.of(new ComputedType("tone-group", "Invented group")),
                List.of(new Derivation("by-tone", "item", "tone", "tone-group", "has-tone")),
                List.of(new Cooccurrence("same-tone", "by-tone", "by-tone", BigInteger.ZERO, BigInteger.ONE)),
                List.of(new CountRule("groups", "tone-group", BigInteger.ZERO, BigInteger.TEN)));
        var projection = new Projection("items", "item", List.of(new ExpandedName("urn:mock", "items"), new ExpandedName("urn:mock", "item")),
                List.of(new FieldMapping("id", new ExpandedName("", "id")), new FieldMapping("tone", new ExpandedName("", "tone"))), List.of());
        return new NativeDefinition("mock-v3", BigInteger.ONE, logical, List.of(new Binding("mock-pg", Engine.POSTGRESQL, Storage.TEXT,
                "mock_schema", "mock_table", "mock_key", "mock_xml", KeyType.INT64, List.of(new Document("sheet", "1", List.of(projection))))));
    }
    private NativeCompilationResult.Checked checked(NativeDefinition definition) {
        return assertInstanceOf(NativeCompilationResult.Incomplete.class, compiler.compile(definition)).checked();
    }
    @Test void validDeclarationsHaveExplicitV3DependenciesButCannotBecomeReady() {
        var result = assertInstanceOf(NativeCompilationResult.Incomplete.class, compiler.compile(fixture()));
        assertEquals(List.of("MECHANISM_UNQUALIFIED"), result.diagnostics().stream().map(d -> d.code()).toList());
        assertEquals(BigInteger.ONE, result.checked().mechanisms().get("native-compiler-v3"));
        assertEquals(BigInteger.ONE, result.checked().mechanisms().get("derived-graph-v1"));
        assertFalse(result.checked().mechanisms().containsKey("native-compiler-v2"));
        assertFalse(result.checked().mechanisms().containsKey("xml-child-property-v1"));
        assertEquals(fixture(), result.checked().definition());
        assertEquals(result, compiler.compile(fixture()));
        assertFalse(result.toString().contains("Invented"));
    }
    @Test void everyIneligibleSourceIsRejectedRegardlessOfFieldClassification() {
        for (var classification : Classification.values()) {
            for (var sensitivity : List.of(Sensitivity.INTERNAL, Sensitivity.SECRET, Sensitivity.UNKNOWN))
                rejected(withField(f -> new Field(f.id(), f.valueType(), f.required(), classification, sensitivity, true, true)), "DERIVATION_INPUT_INELIGIBLE");
            for (var codec : List.of(ValueType.INTEGER, ValueType.BOOLEAN, ValueType.URI))
                rejected(withField(f -> new Field(f.id(), codec, f.required(), classification, Sensitivity.PUBLIC, true, true)), "DERIVATION_INPUT_INELIGIBLE");
            rejected(withField(f -> new Field(f.id(), f.valueType(), f.required(), classification, Sensitivity.PUBLIC, false, true)), "DERIVATION_INPUT_INELIGIBLE");
        }
    }
    @Test void unknownSourcesTargetsFieldsAndDerivedInputsCannotBecomeChecked() {
        rejected(withDerivation(new Derivation("by-tone", "missing", "tone", "tone-group", "has-tone")), "UNKNOWN_TYPE");
        rejected(withDerivation(new Derivation("by-tone", "tone-group", "tone", "tone-group", "has-tone")), "UNKNOWN_TYPE");
        rejected(withDerivation(new Derivation("by-tone", "item", "missing", "tone-group", "has-tone")), "UNKNOWN_FIELD");
        rejected(withDerivation(new Derivation("by-tone", "item", "tone", "missing", "has-tone")), "UNKNOWN_COMPUTED_TYPE");
    }
    @Test void computedTypesAreDisjointAndHaveExactlyOneDerivation() {
        var l = fixture().logical();
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                List.of(new ComputedType("item", "Collision")), l.derivations(), l.cooccurrences(), l.computedRules())), "TYPE_ID_COLLISION");
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                l.computedTypes(), List.of(), List.of(), l.computedRules())), "DERIVATION_MISSING");
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), l.computedTypes(),
                List.of(l.derivations().getFirst(), new Derivation("also-tone", "item", "id", "tone-group", "also-has-tone")),
                l.cooccurrences(), l.computedRules())), "COMPUTED_TYPE_MULTIPLE_DERIVATIONS");
    }
    @Test void relationAndRuleIdsShareDisjointPhysicalAndComputedNamespaces() {
        rejected(withDerivation(new Derivation("by-tone", "item", "tone", "tone-group", "same-tone")), "RELATION_ID_COLLISION");
        var l = fixture().logical();
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), List.of(new CountRule("groups", "item", BigInteger.ZERO, BigInteger.TEN)),
                l.operationCapabilities(), l.computedTypes(), l.derivations(), l.cooccurrences(), l.computedRules())), "RULE_ID_COLLISION");
    }
    @Test void duplicateDeclarationsAndWrongComputedRuleEndpointsAreRejected() {
        var l = fixture().logical();
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                List.of(l.computedTypes().getFirst(), l.computedTypes().getFirst()), l.derivations(), l.cooccurrences(), l.computedRules())), "DUPLICATE_ID");
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                l.computedTypes(), List.of(l.derivations().getFirst(), l.derivations().getFirst()), l.cooccurrences(), l.computedRules())), "DUPLICATE_ID");
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                l.computedTypes(), l.derivations(), List.of(l.cooccurrences().getFirst(), l.cooccurrences().getFirst()), l.computedRules())), "DUPLICATE_ID");
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                l.computedTypes(), l.derivations(), l.cooccurrences(), List.of(new CountRule("bad", "item", BigInteger.ZERO, BigInteger.ONE)))), "UNKNOWN_COMPUTED_TYPE");
    }
    @Test void cooccurrencesNeedDeclaredDerivationsAndOrderedBounds() {
        rejected(withCooccurrence(new Cooccurrence("same-tone", "by-tone", "missing", BigInteger.ZERO, BigInteger.ONE)), "UNKNOWN_DERIVATION");
        rejected(withCooccurrence(new Cooccurrence("same-tone", "by-tone", "by-tone", BigInteger.TEN, BigInteger.ONE)), "CARDINALITY_INVERTED");
        checked(fixture()); // Same-derivation self edges are deliberately supported semantics.
    }
    @Test void aPairCannotJoinDifferentPhysicalSourceTypesEvenWithTheSameFieldName() {
        var l = fixture().logical(); var original = l.entityTypes().getFirst();
        var other = new EntityType("other-item", "Other invented item", original.fields(), original.identity());
        var changed = new Logical(List.of(original, other), l.relations(), l.rules(), l.operationCapabilities(),
                List.of(l.computedTypes().getFirst(), new ComputedType("other-group", "Other group")),
                List.of(l.derivations().getFirst(), new Derivation("other-tone", "other-item", "tone", "other-group", "has-other")),
                List.of(new Cooccurrence("pair", "by-tone", "other-tone", BigInteger.ZERO, BigInteger.ONE)), l.computedRules());
        // Missing physical projection is a separate publication blocker; it cannot hide the semantic error.
        rejected(withLogical(changed), "COOCCURRENCE_SOURCE_MISMATCH");
    }
    @Test void physicalRelationIdsCannotBeReusedByMembershipOrCooccurrence() {
        var l = fixture().logical();
        for (String id : List.of("has-tone", "same-tone")) {
            var relation = new studio.environment.core.definition.DefinitionDraft.Relation(id, "item", "item",
                    studio.environment.core.definition.DefinitionDraft.RelationKind.REFERENCE, BigInteger.ZERO, BigInteger.ONE, false);
            rejected(withLogical(new Logical(l.entityTypes(), List.of(relation), l.rules(), l.operationCapabilities(),
                    l.computedTypes(), l.derivations(), l.cooccurrences(), l.computedRules())), "RELATION_ID_COLLISION");
        }
    }
    @Test void computedCountRulesRequireOrderedBoundsAndUniqueIds() {
        var l = fixture().logical();
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), l.computedTypes(),
                l.derivations(), l.cooccurrences(), List.of(new CountRule("groups", "tone-group", BigInteger.TEN, BigInteger.ONE)))), "CARDINALITY_INVERTED");
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), l.computedTypes(),
                l.derivations(), l.cooccurrences(), List.of(l.computedRules().getFirst(), l.computedRules().getFirst()))), "DUPLICATE_ID");
    }
    @Test void declarationBudgetAllows32DistinctDerivationsAndRefusesThe33rdBeforeDigesting() {
        var l = fixture().logical(); var types = new ArrayList<ComputedType>(); var derivations = new ArrayList<Derivation>();
        for (int i = 0; i < 32; i++) {
            types.add(new ComputedType("group-" + i, "Invented group"));
            derivations.add(new Derivation("derive-" + i, "item", "tone", "group-" + i, "member-" + i));
        }
        checked(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), types, derivations, List.of(), List.of())));
        types.add(new ComputedType("group-over", "Over limit"));
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), types, derivations, List.of(), List.of())), "RESOURCE_LIMIT");
        types.removeLast(); derivations.add(new Derivation("derive-over", "item", "tone", "group-0", "member-over"));
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), types, derivations, List.of(), List.of())), "RESOURCE_LIMIT");
        var pairs = new ArrayList<Cooccurrence>();
        for (int i = 0; i < 32; i++) pairs.add(new Cooccurrence("pair-" + i, "by-tone", "by-tone", BigInteger.ZERO, BigInteger.ONE));
        checked(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), l.computedTypes(), l.derivations(), pairs, l.computedRules())));
        pairs.add(new Cooccurrence("pair-over", "by-tone", "by-tone", BigInteger.ZERO, BigInteger.ONE));
        rejected(withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), l.computedTypes(), l.derivations(), pairs, l.computedRules())), "RESOURCE_LIMIT");
    }
    @Test void explicitlyEmptyDerivationsStillUseV3CompatibilityAndRemainUnqualified() {
        var d = fixture(); var l = d.logical();
        var empty = withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), List.of(), List.of(), List.of(), List.of()));
        var v3 = checked(empty);
        var physical = new studio.environment.core.definitionv2.NativeDefinition(d.id(), d.revision(),
                new studio.environment.core.definitionv2.NativeDefinition.Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities()), d.bindings());
        var v2 = assertInstanceOf(studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish.class,
                new studio.environment.core.definitionv2.NativeDefinitionCompiler().compile(physical)).checked();
        assertNotEquals(v2.logicalDigest(), v3.logicalDigest());
        assertNotEquals(v2.bindingDigests(), v3.bindingDigests());
        assertEquals(BigInteger.ONE, v3.mechanisms().get("derived-graph-v1"));
    }
    @Test void missingPhysicalMappingsStayIncompleteAndComputedTypesCannotBeProjected() {
        var d = fixture(); var b = d.bindings().getFirst(); var doc = b.documents().getFirst(); var p = doc.entities().getFirst();
        var missing = new Projection(p.id(), p.type(), p.path(), List.of(p.fields().getFirst()), p.references());
        var result = assertInstanceOf(NativeCompilationResult.Incomplete.class, compiler.compile(withProjection(missing)));
        assertTrue(result.diagnostics().stream().anyMatch(x -> x.code().equals("FIELD_MAPPING_MISSING")));
        rejected(withProjection(new Projection(p.id(), "tone-group", p.path(), List.of(), List.of())), "UNKNOWN_TYPE");
    }
    @Test void labelAndRevisionChangesPreserveDigestsButEligibilityAndRulesAffectLogicalCompatibility() {
        var original = checked(fixture()); var l = fixture().logical();
        var changedLabel = withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                List.of(new ComputedType("tone-group", "Other label 𐀀")), l.derivations(), l.cooccurrences(), l.computedRules()));
        assertEquals(original.logicalDigest(), checked(changedLabel).logicalDigest());
        assertEquals(original.bindingDigests(), checked(changedLabel).bindingDigests());
        assertEquals(original.logicalDigest(), checked(new NativeDefinition("other-id", BigInteger.TEN, l, fixture().bindings())).logicalDigest());
        assertNotEquals(original.logicalDigest(), checked(withField(f -> new Field(f.id(), f.valueType(), true, f.classification(), f.sensitivity(), true, true))).logicalDigest());
        assertNotEquals(original.logicalDigest(), checked(withCooccurrence(new Cooccurrence("same-tone", "by-tone", "by-tone", BigInteger.ZERO, BigInteger.TEN))).logicalDigest());
    }
    @Test void checkedCollectionsCannotBeChangedAndDiagnosticsNeverEchoDeclarationCanaries() {
        var source = fixture(); var l = source.logical(); var derived = new ArrayList<>(l.derivations());
        var value = withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), l.computedTypes(), derived, l.cooccurrences(), l.computedRules()));
        derived.clear(); var result = checked(value); assertEquals(1, result.definition().logical().derivations().size());
        assertThrows(UnsupportedOperationException.class, () -> result.bindingDigests().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.mechanisms().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.definition().logical().computedTypes().clear());
        var refused = rejected(withDerivation(new Derivation("by-tone", "invented-canary", "tone", "tone-group", "has-tone")), "UNKNOWN_TYPE");
        assertFalse(refused.toString().contains("invented-canary"));
    }
    private NativeCompilationResult.Rejected rejected(NativeDefinition value, String code) {
        var result = assertInstanceOf(NativeCompilationResult.Rejected.class, compiler.compile(value));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals(code)), result.diagnostics().toString());
        return result;
    }
    private static NativeDefinition withLogical(Logical logical) { var d = fixture(); return new NativeDefinition(d.id(), d.revision(), logical, d.bindings()); }
    private static NativeDefinition withField(UnaryOperator<Field> change) {
        var l = fixture().logical(); var t = l.entityTypes().getFirst();
        return withLogical(new Logical(List.of(new EntityType(t.id(), t.label(), List.of(t.fields().getFirst(), change.apply(t.fields().getLast())), t.identity())),
                l.relations(), l.rules(), l.operationCapabilities(), l.computedTypes(), l.derivations(), l.cooccurrences(), l.computedRules()));
    }
    private static NativeDefinition withDerivation(Derivation derivation) {
        var l = fixture().logical(); return withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                l.computedTypes(), List.of(derivation), l.cooccurrences(), l.computedRules()));
    }
    private static NativeDefinition withCooccurrence(Cooccurrence relation) {
        var l = fixture().logical(); return withLogical(new Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                l.computedTypes(), l.derivations(), List.of(relation), l.computedRules()));
    }
    private static NativeDefinition withProjection(Projection projection) {
        var d = fixture(); var b = d.bindings().getFirst(); var doc = b.documents().getFirst();
        return new NativeDefinition(d.id(), d.revision(), d.logical(), List.of(new Binding(b.id(), b.engine(), b.storage(), b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(),
                List.of(new Document(doc.id(), doc.key(), List.of(projection))))));
    }
}
