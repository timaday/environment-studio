package studio.environment.core.planning;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.definitionv2.NativeDefinitionCompiler;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.graph.ObservedGraph;
import static org.junit.jupiter.api.Assertions.*;

class TargetIntentCompilerTest {
    static NativeCompilationResult.ReadyToPublish definition() {
        var fields = List.of(new NativeDefinition.Field("id", DefinitionDraft.ValueType.TEXT, true, DefinitionDraft.Classification.STRUCTURAL, DefinitionDraft.Sensitivity.PUBLIC, true, true),
            new NativeDefinition.Field("value", DefinitionDraft.ValueType.TEXT, true, DefinitionDraft.Classification.ENVIRONMENT, DefinitionDraft.Sensitivity.SECRET, true, true));
        var types = List.of(new NativeDefinition.EntityType("a", "A", fields, new NativeDefinition.Identity("id")), new NativeDefinition.EntityType("b", "B", fields, new NativeDefinition.Identity("id")));
        var root = new NativeDefinition.ExpandedName("", "root");
        var mapped = List.of(new NativeDefinition.FieldMapping("id", new NativeDefinition.ExpandedName("", "id")), new NativeDefinition.FieldMapping("value", new NativeDefinition.ExpandedName("", "value")));
        var projections = List.of(new NativeDefinition.Projection("as", "a", List.of(root, new NativeDefinition.ExpandedName("", "a")), mapped, List.of(new NativeDefinition.ReferenceMapping("uses", new NativeDefinition.ExpandedName("", "ref")))),
            new NativeDefinition.Projection("bs", "b", List.of(root, new NativeDefinition.ExpandedName("", "b")), mapped, List.of()));
        var binding = new NativeDefinition.Binding("mock", NativeDefinition.Engine.POSTGRESQL, NativeDefinition.Storage.TEXT, "mock", "mock", "id", "xml", NativeDefinition.KeyType.INT64, List.of(new NativeDefinition.Document("doc", "1", projections)));
        var relation = new DefinitionDraft.Relation("uses", "a", "b", DefinitionDraft.RelationKind.REFERENCE, BigInteger.ONE, BigInteger.ONE, false);
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(new NativeDefinition("mock", BigInteger.ONE,
            new NativeDefinition.Logical(types, List.of(relation), List.of(), List.of(NativeDefinition.Operation.values())), List.of(binding))));
    }
    static ObservedGraph.Entity entity(String type, String identity, int index) {
        return new ObservedGraph.Entity(new ObservedGraph.Key(type, identity), Map.of("id", identity, "value", "observed-canary"), new ObservedGraph.Origin("doc", type, "source-canary", index, List.of(0)));
    }
    static GraphValidationResult.Accepted graph() {
        var a = entity("a", "one", 1); var b = entity("a", "two", 2); var target = entity("b", "shared", 3);
        return new GraphValidationResult.Accepted(new ObservedGraph(List.of(a, b, target), List.of(new ObservedGraph.Edge("uses", a.key(), target.key()), new ObservedGraph.Edge("uses", b.key(), target.key()))));
    }
    @Test void independentlyResolvesFreshValuesAndOneReferenceWhileRetainingOtherEntities() {
        var fresh = new TargetIntent.Ref.Fresh("neutral", "b"); var existing = new TargetIntent.Ref.Existing(new ObservedGraph.Key("a", "one"));
        var intent = new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(existing, Map.of("id", new TargetIntent.FieldValue.KeepObserved(), "value", new TargetIntent.FieldValue.KeepObserved()), Map.of("uses", new TargetIntent.ReferenceValue.To(fresh))),
            new TargetIntent.EntityDecision.Create(fresh, Map.of("id", new TargetIntent.FieldValue.Entered("new-identity"), "value", new TargetIntent.FieldValue.Entered("fresh exact 𐀀\t")), Map.of())), List.of());
        var expected = assertInstanceOf(TargetCompilationResult.Expected.class, new TargetIntentCompiler().compile(definition(), graph(), intent)).target();
        assertEquals(4, expected.entities().size());
        assertEquals(Map.of("id", "new-identity", "value", "fresh exact 𐀀\t"), expected.entities().stream().filter(e -> e.reference().equals(fresh)).findFirst().orElseThrow().fields());
        assertTrue(expected.edges().contains(new ExpectedTarget.Edge("uses", existing, fresh)));
        assertTrue(expected.edges().contains(new ExpectedTarget.Edge("uses", new TargetIntent.Ref.Existing(new ObservedGraph.Key("a", "two")), new TargetIntent.Ref.Existing(new ObservedGraph.Key("b", "shared")))));
    }

    private static TargetIntent.Ref.Existing old(String type, String id) { return new TargetIntent.Ref.Existing(new ObservedGraph.Key(type, id)); }
    private static Map<String, TargetIntent.FieldValue> keep() { return Map.of("id", new TargetIntent.FieldValue.KeepObserved(), "value", new TargetIntent.FieldValue.KeepObserved()); }
    private static String rejection(NativeCompilationResult.ReadyToPublish definition, TargetIntent intent) {
        return assertInstanceOf(TargetCompilationResult.Rejected.class, new TargetIntentCompiler().compile(definition, graph(), intent)).codes().getFirst();
    }
    private static NativeCompilationResult.ReadyToPublish without(NativeDefinition.Operation operation) {
        var nativeDefinition = definition().checked().definition(); var logical = nativeDefinition.logical();
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(new NativeDefinition(nativeDefinition.id(), nativeDefinition.revision(),
            new NativeDefinition.Logical(logical.entityTypes(), logical.relations(), logical.rules(), logical.operationCapabilities().stream().filter(o -> o != operation).toList()), nativeDefinition.bindings())));
    }
    @Test void retainedReferenceCannotBeCapturedByFreshEntityReusingRenamedIdentity() {
        var shared = old("b", "shared"); var fresh = new TargetIntent.Ref.Fresh("fresh", "b");
        var rename = new TargetIntent.EntityDecision.Retain(shared, Map.of("id", new TargetIntent.FieldValue.Entered("renamed"), "value", new TargetIntent.FieldValue.KeepObserved()), Map.of());
        var create = new TargetIntent.EntityDecision.Create(fresh, Map.of("id", new TargetIntent.FieldValue.Entered("shared"), "value", new TargetIntent.FieldValue.Entered("fresh")), Map.of());
        assertEquals("RETAINED_REFERENCE_CHANGED", rejection(definition(), new TargetIntent(List.of(rename, create), List.of())));
        var rebound = new TargetIntent(List.of(rename, create,
            new TargetIntent.EntityDecision.Retain(old("a", "one"), keep(), Map.of("uses", new TargetIntent.ReferenceValue.To(shared))),
            new TargetIntent.EntityDecision.Retain(old("a", "two"), keep(), Map.of("uses", new TargetIntent.ReferenceValue.To(fresh)))), List.of());
        var result = assertInstanceOf(TargetCompilationResult.Expected.class, new TargetIntentCompiler().compile(definition(), graph(), rebound)).target();
        assertTrue(result.edges().contains(new ExpectedTarget.Edge("uses", old("a", "one"), shared)));
        assertTrue(result.edges().contains(new ExpectedTarget.Edge("uses", old("a", "two"), fresh)));
    }
    @Test void selectedEntitiesRequireEveryFieldAndReferenceDecision() {
        assertEquals("FIELD_DECISIONS_INCOMPLETE", rejection(definition(), new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(old("a", "one"), Map.of(), Map.of())), List.of())));
        assertEquals("REFERENCE_DECISIONS_INCOMPLETE", rejection(definition(), new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(old("a", "one"), keep(), Map.of())), List.of())));
        assertEquals("UNRESOLVED_REFERENCE", rejection(definition(), new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(old("a", "one"), keep(), Map.of("uses", new TargetIntent.ReferenceValue.Unresolved()))), List.of())));
        assertEquals("UNRESOLVED_FIELD", rejection(definition(), new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(old("b", "shared"), Map.of("id", new TargetIntent.FieldValue.KeepObserved(), "value", new TargetIntent.FieldValue.Unresolved()), Map.of())), List.of())));
    }
    @Test void unknownDuplicateAndDanglingDecisionsRejectWithoutTarget() {
        var remove = new TargetIntent.EntityDecision.Remove(old("b", "shared"));
        assertEquals("CONFLICTING_DECISIONS", rejection(definition(), new TargetIntent(List.of(remove, remove), List.of())));
        assertEquals("UNKNOWN_ENTITY", rejection(definition(), new TargetIntent(List.of(new TargetIntent.EntityDecision.Remove(old("b", "missing"))), List.of())));
        assertEquals("RETAINED_REFERENCE_CHANGED", rejection(definition(), new TargetIntent(List.of(remove), List.of())));
        var fresh = new TargetIntent.Ref.Fresh("fresh", "b");
        assertEquals("DUPLICATE_IDENTITY", rejection(definition(), new TargetIntent(List.of(new TargetIntent.EntityDecision.Create(fresh, Map.of("id", new TargetIntent.FieldValue.Entered("shared"), "value", new TargetIntent.FieldValue.Entered("fresh")), Map.of())), List.of())));
        assertEquals("KEEP_OBSERVED_UNAVAILABLE", rejection(definition(), new TargetIntent(List.of(new TargetIntent.EntityDecision.Create(fresh, keep(), Map.of())), List.of())));
    }
    @Test void undeclaredOperationsCannotBeInferredFromTypedIntent() {
        assertEquals("OPERATION_NOT_DECLARED", rejection(without(NativeDefinition.Operation.RETAIN_ENTITY), new TargetIntent(List.of(), List.of())));
        var fresh = new TargetIntent.Ref.Fresh("fresh", "b"); var create = new TargetIntent.EntityDecision.Create(fresh, Map.of("id", new TargetIntent.FieldValue.Entered("new"), "value", new TargetIntent.FieldValue.Entered("fresh")), Map.of());
        assertEquals("OPERATION_NOT_DECLARED", rejection(without(NativeDefinition.Operation.CREATE_ENTITY), new TargetIntent(List.of(create), List.of())));
        assertEquals("OPERATION_NOT_DECLARED", rejection(without(NativeDefinition.Operation.REMOVE_ENTITY), new TargetIntent(List.of(new TargetIntent.EntityDecision.Remove(old("a", "one"))), List.of())));
        assertEquals("OPERATION_NOT_DECLARED", rejection(without(NativeDefinition.Operation.BIND_FIELD), new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(old("b", "shared"), Map.of("id", new TargetIntent.FieldValue.KeepObserved(), "value", new TargetIntent.FieldValue.Entered("changed")), Map.of())), List.of())));
        assertEquals("OPERATION_NOT_DECLARED", rejection(without(NativeDefinition.Operation.MOVE_RELATION), new TargetIntent(List.of(create, new TargetIntent.EntityDecision.Retain(old("a", "one"), keep(), Map.of("uses", new TargetIntent.ReferenceValue.To(fresh)))), List.of())));
    }
    @Test void boundsPrecedeVirtualListIterationAndEnteredValuesRejectInvalidUnicode() {
        var oversized = new java.util.AbstractList<TargetIntent.EntityDecision>() {
            public int size() { return Integer.MAX_VALUE; }
            public TargetIntent.EntityDecision get(int index) { throw new AssertionError("must reject before traversal"); }
        };
        assertThrows(IllegalArgumentException.class, () -> new TargetIntent(oversized, List.of()));
        for (String value : List.of("\uD800", "\uDC00", "x".repeat(1_048_577))) {
            var intent = new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(old("b", "shared"), Map.of("id", new TargetIntent.FieldValue.KeepObserved(), "value", new TargetIntent.FieldValue.Entered(value)), Map.of())), List.of());
            assertInstanceOf(TargetCompilationResult.Rejected.class, new TargetIntentCompiler().compile(definition(), graph(), intent));
        }
    }
    @Test void inputAndExpectedCollectionsAreDeeplyImmutableAndRenderSafely() {
        var fields = new java.util.HashMap<>(keep()); var decision = new TargetIntent.EntityDecision.Retain(old("b", "shared"), fields, Map.of()); fields.clear();
        var list = new java.util.ArrayList<TargetIntent.EntityDecision>(); list.add(decision); var intent = new TargetIntent(list, List.of()); list.clear();
        var expected = assertInstanceOf(TargetCompilationResult.Expected.class, new TargetIntentCompiler().compile(definition(), graph(), intent));
        assertEquals(3, expected.target().entities().size());
        assertThrows(UnsupportedOperationException.class, () -> decision.fields().clear());
        assertThrows(UnsupportedOperationException.class, () -> expected.target().entities().getFirst().fields().clear());
        assertThrows(UnsupportedOperationException.class, () -> expected.target().edges().clear());
        for (Object safe : List.of(intent, decision, expected, expected.target(), expected.target().entities().getFirst())) assertFalse(safe.toString().contains("observed-canary"));
    }
    @Test void containmentIntentUsesTheTwentyThousandDecisionBudget() {
        var oversized = new java.util.AbstractList<TargetIntent.Containment>() {
            public int size() { return 20_001; }
            public TargetIntent.Containment get(int index) { throw new AssertionError("must reject before traversal"); }
        };
        assertThrows(IllegalArgumentException.class, () -> new TargetIntent(List.of(), oversized));
    }
}
