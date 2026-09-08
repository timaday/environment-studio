package studio.environment.core.graph;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definitionv2.NativeDefinition.EntityType;
import studio.environment.core.definitionv2.NativeDefinition.Identity;
import studio.environment.core.definitionv2.NativeDefinition.Field;
import studio.environment.core.definitionv2.NativeDefinition.Logical;
import studio.environment.core.definitionv2.NativeDefinition.CountRule;
import static studio.environment.core.graph.ObservedGraph.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphValidatorTest {
    private final GraphValidator validator = new GraphValidator();
    private EntityType type(String id) {
        return new EntityType(id, id, List.of(new Field("id", ValueType.TEXT, true, Classification.STRUCTURAL, Sensitivity.PUBLIC, true, false),
                new Field("value", ValueType.TEXT, true, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true)), new Identity("id"));
    }
    private Logical logical(List<Relation> relations) { return new Logical(List.of(type("a"), type("b"), type("c")), relations, List.of(), List.of()); }
    private Relation relation(String id, String from, String to, RelationKind kind, int min, int max) {
        return new Relation(id, from, to, kind, BigInteger.valueOf(min), BigInteger.valueOf(max), true);
    }
    private Occurrence occurrence(String type, String id, int index, List<Integer> ancestry, Map<String, String> references) {
        return new Occurrence(type, Map.of("id", id, "value", "raw-canary"), references, new Origin("doc", "projection", "digest", index, ancestry));
    }
    private static void refused(GraphValidationResult result, String code) {
        var rejected = assertInstanceOf(GraphValidationResult.Rejected.class, result);
        assertTrue(rejected.diagnostics().stream().anyMatch(d -> d.code().equals(code)), rejected::toString);
        assertFalse(result.toString().contains("raw-canary"));
    }
    @Test void mandatoryGuardsApplyEvenWithoutCountRules() {
        var valid = occurrence("a", "one", 0, List.of(), Map.of());
        var absent = new Occurrence("a", Map.of("id", "one"), Map.of(), valid.origin());
        refused(validator.validate(logical(List.of()), List.of(absent)), "REQUIRED_FIELD_MISSING");
        refused(validator.validate(logical(List.of()), List.of(valid, occurrence("a", "one", 1, List.of(), Map.of()))), "DUPLICATE_IDENTITY");
        var ref = relation("link", "a", "b", RelationKind.REFERENCE, 0, 1);
        refused(validator.validate(logical(List.of(ref)), List.of(occurrence("a", "one", 0, List.of(), Map.of("link", "raw-canary")))), "UNRESOLVED_REFERENCE");
    }
    @Test void physicalOverlapAndMissingContainmentParentRefuse() {
        refused(validator.validate(logical(List.of()), List.of(occurrence("a", "one", 0, List.of(), Map.of()),
                occurrence("b", "two", 0, List.of(), Map.of()))), "AMBIGUOUS_PROJECTION");
        var relation = relation("holds", "a", "b", RelationKind.CONTAINMENT, 0, 1);
        refused(validator.validate(logical(List.of(relation)), List.of(occurrence("b", "two", 1, List.of(0), Map.of()))), "CONTAINMENT_PARENT_MISSING");
    }
    @Test void nearestCompatibleAncestorIsTheParentAndOutgoingZeroIsChecked() {
        var relation = relation("holds", "a", "b", RelationKind.CONTAINMENT, 0, 1);
        var inputs = List.of(occurrence("a", "outer", 0, List.of(), Map.of()), occurrence("a", "inner", 1, List.of(0), Map.of()),
                occurrence("b", "child", 2, List.of(0, 1), Map.of()));
        var graph = assertInstanceOf(GraphValidationResult.Accepted.class, validator.validate(logical(List.of(relation)), inputs)).graph();
        assertEquals("inner", graph.edges().getFirst().source().identity());
        refused(validator.validate(logical(List.of(relation("holds", "a", "b", RelationKind.CONTAINMENT, 1, 1))), inputs), "RELATION_CARDINALITY");
        inputs = List.of(occurrence("a", "parent", 0, List.of(), Map.of()), occurrence("b", "one", 1, List.of(0), Map.of()), occurrence("b", "two", 2, List.of(0), Map.of()));
        refused(validator.validate(logical(List.of(relation)), inputs), "RELATION_CARDINALITY");
    }
    @Test void distinctContainmentParentsAndCyclesRefuse() {
        var relations = List.of(relation("ac", "a", "c", RelationKind.CONTAINMENT, 0, 1), relation("bc", "b", "c", RelationKind.CONTAINMENT, 0, 1));
        refused(validator.validate(logical(relations), List.of(occurrence("a", "one", 0, List.of(), Map.of()),
                occurrence("b", "two", 1, List.of(0), Map.of()), occurrence("c", "three", 2, List.of(0, 1), Map.of()))), "MULTIPLE_CONTAINMENT_PARENTS");
        relations = List.of(relation("ab", "a", "b", RelationKind.CONTAINMENT, 1, 1), relation("ba", "b", "a", RelationKind.CONTAINMENT, 1, 1));
        refused(validator.validate(logical(relations), List.of(occurrence("a", "one", 0, List.of(1), Map.of()),
                occurrence("b", "two", 1, List.of(0), Map.of()))), "CONTAINMENT_CYCLE");
    }
    @Test void entityCountsIncludeZeroAndArbitraryPrecisionBounds() {
        var base = logical(List.of());
        var rules = List.of(new CountRule("minimum", "b", BigInteger.ONE, BigInteger.TEN.pow(100)));
        refused(validator.validate(new Logical(base.entityTypes(), base.relations(), rules, List.of()), List.of()), "ENTITY_COUNT");
        rules = List.of(new CountRule("maximum", "a", BigInteger.ZERO, BigInteger.ONE));
        refused(validator.validate(new Logical(base.entityTypes(), base.relations(), rules, List.of()), List.of(
                occurrence("a", "one", 0, List.of(), Map.of()), occurrence("a", "two", 1, List.of(), Map.of()))), "ENTITY_COUNT");
    }
    @Test void entityAndEdgeLimitsRefuseBeforeOversizedCopiesAndNeverTruncate() {
        var virtual = new java.util.AbstractList<Occurrence>() {
            @Override public int size() { return Integer.MAX_VALUE; }
            @Override public Occurrence get(int index) { throw new AssertionError("Must check size before iteration."); }
        };
        refused(validator.validate(logical(List.of()), virtual), "RESOURCE_LIMIT");
        List<Occurrence> entities = new ArrayList<>();
        for (int i = 0; i < MAX_ENTITIES; i++) entities.add(occurrence("a", "id" + i, i, List.of(), Map.of()));
        assertEquals(MAX_ENTITIES, assertInstanceOf(GraphValidationResult.Accepted.class, validator.validate(logical(List.of()), entities)).graph().entities().size());
        entities.add(occurrence("a", "extra", MAX_ENTITIES, List.of(), Map.of()));
        refused(validator.validate(logical(List.of()), entities), "RESOURCE_LIMIT");
        List<Relation> relations = new ArrayList<>(); Map<String, String> references = new HashMap<>();
        for (int i = 0; i < 250; i++) { relations.add(relation("link" + i, "a", "b", RelationKind.REFERENCE, 0, 1)); references.put("link" + i, "target"); }
        entities = new ArrayList<>(); entities.add(occurrence("b", "target", 0, List.of(), Map.of()));
        for (int i = 0; i < 200; i++) entities.add(occurrence("a", "id" + i, i + 1, List.of(), references));
        assertEquals(MAX_EDGES, assertInstanceOf(GraphValidationResult.Accepted.class, validator.validate(logical(relations), entities)).graph().edges().size());
        entities.add(occurrence("a", "extra", 201, List.of(), references));
        refused(validator.validate(logical(relations), entities), "RESOURCE_LIMIT");
    }
}
