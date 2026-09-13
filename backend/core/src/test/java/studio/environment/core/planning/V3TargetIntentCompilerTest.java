package studio.environment.core.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.graph.ObservedGraph;

/** Independently invented physical expectations; no XML or publication authority. */
class V3TargetIntentCompilerTest {
    static NativeCompilationResult.Checked definition() {
        var old = TargetIntentCompilerTest.definition().checked().definition();
        var l = old.logical();
        var logical = new NativeDefinition.Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                List.of(new NativeDefinition.ComputedType("names", "Names")),
                List.of(new NativeDefinition.Derivation("by-name", "a", "id", "names", "named")), List.of(), List.of());
        return assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(
                new NativeDefinition("mock-target", BigInteger.ONE, logical, old.bindings()))).checked();
    }
    static TargetIntent.Ref.Existing old(String type, String id) {
        return new TargetIntent.Ref.Existing(new ObservedGraph.Key(type, id));
    }
    static Map<String, TargetIntent.FieldValue> keep() {
        return Map.of("id", new TargetIntent.FieldValue.KeepObserved(), "value", new TargetIntent.FieldValue.KeepObserved());
    }
    static ExpectedTarget.Entity entity(TargetIntent.Ref reference, String id, String value) {
        return new ExpectedTarget.Entity(reference, new ObservedGraph.Key(reference.type(), id), Map.of("id", id, "value", value));
    }
    static TargetIntent.EntityDecision.Create create(TargetIntent.Ref.Fresh ref, String id) {
        return new TargetIntent.EntityDecision.Create(ref, Map.of("id", new TargetIntent.FieldValue.Entered(id),
                "value", new TargetIntent.FieldValue.Entered("fresh exact 𐀀")), Map.of());
    }
    private ExpectedTarget compile(TargetIntent intent) {
        return assertInstanceOf(TargetCompilationResult.Expected.class,
                new V3TargetIntentCompiler().compile(definition(), TargetIntentCompilerTest.graph(), intent)).target();
    }
    @Test void exactPhysicalExpectationEditsCreatesAndRebindsWithoutComputedEntities() {
        var a = old("a", "one");
        var b = old("a", "two");
        var shared = old("b", "shared");
        var fresh = new TargetIntent.Ref.Fresh("neutral", "b");
        var edit = new TargetIntent.EntityDecision.Retain(a,
                Map.of("id", new TargetIntent.FieldValue.KeepObserved(), "value", new TargetIntent.FieldValue.Entered("changed")),
                Map.of("uses", new TargetIntent.ReferenceValue.To(fresh)));
        var expected = new ExpectedTarget(List.of(entity(a, "one", "changed"), entity(b, "two", "observed-canary"),
                entity(shared, "shared", "observed-canary"), entity(fresh, "new", "fresh exact 𐀀")),
                List.of(new ExpectedTarget.Edge("uses", a, fresh), new ExpectedTarget.Edge("uses", b, shared)),
                Set.of(), Set.of(a, shared, fresh));
        assertEquals(expected, compile(new TargetIntent(List.of(edit, create(fresh, "new")), List.of())));
    }
    @Test void v3UsesUnsignedUtf8ReferenceOrderingAndV2KeepsItsHistoricalOrdering() {
        var bmp = TargetIntentCompilerTest.entity("b", "\uE000", 1);
        var supplementary = TargetIntentCompilerTest.entity("b", "𐀀", 2);
        var graph = new GraphValidationResult.Accepted(new ObservedGraph(List.of(supplementary, bmp), List.of()));
        var intent = new TargetIntent(List.of(), List.of());
        var v3 = assertInstanceOf(TargetCompilationResult.Expected.class,
                new V3TargetIntentCompiler().compile(definition(), graph, intent)).target();
        assertEquals(List.of(bmp.key(), supplementary.key()), v3.entities().stream().map(ExpectedTarget.Entity::identity).toList());
        var v2 = assertInstanceOf(TargetCompilationResult.Expected.class,
                new TargetIntentCompiler().compile(TargetIntentCompilerTest.definition(), graph, intent)).target();
        assertEquals(List.of(supplementary.key(), bmp.key()), v2.entities().stream().map(ExpectedTarget.Entity::identity).toList());
    }
    private String refusal(NativeCompilationResult.Checked definition, TargetIntent intent) {
        return assertInstanceOf(TargetCompilationResult.Rejected.class,
                new V3TargetIntentCompiler().compile(definition, TargetIntentCompilerTest.graph(), intent)).codes().getFirst();
    }
    @Test void removalRequiresExplicitDependentsAndReturnsExactSurvivingPhysicalGraph() {
        var a = old("a", "one");
        var b = old("a", "two");
        var shared = old("b", "shared");
        assertEquals("RETAINED_REFERENCE_CHANGED", refusal(definition(),
                new TargetIntent(List.of(new TargetIntent.EntityDecision.Remove(shared)), List.of())));
        var fresh = new TargetIntent.Ref.Fresh("replacement", "b");
        var intent = new TargetIntent(List.of(new TargetIntent.EntityDecision.Remove(a),
                new TargetIntent.EntityDecision.Remove(b), new TargetIntent.EntityDecision.Remove(shared),
                create(fresh, "new")), List.of());
        assertEquals(new ExpectedTarget(List.of(entity(fresh, "new", "fresh exact 𐀀")), List.of(),
                Set.of(a, b, shared), Set.of(a, b, shared, fresh)), compile(intent));
    }
    @Test void editedIdentityDoesNotRenameExistingReferenceOrCaptureIncomingLinks() {
        var shared = old("b", "shared");
        var fresh = new TargetIntent.Ref.Fresh("replacement", "b");
        var rename = new TargetIntent.EntityDecision.Retain(shared,
                Map.of("id", new TargetIntent.FieldValue.Entered("renamed"), "value", new TargetIntent.FieldValue.KeepObserved()), Map.of());
        assertEquals("RETAINED_REFERENCE_CHANGED", refusal(definition(),
                new TargetIntent(List.of(rename, create(fresh, "shared")), List.of())));
        var a = old("a", "one");
        var b = old("a", "two");
        var intent = new TargetIntent(List.of(rename, create(fresh, "shared"),
                new TargetIntent.EntityDecision.Retain(a, keep(), Map.of("uses", new TargetIntent.ReferenceValue.To(shared))),
                new TargetIntent.EntityDecision.Retain(b, keep(), Map.of("uses", new TargetIntent.ReferenceValue.To(fresh)))), List.of());
        var expected = new ExpectedTarget(List.of(entity(a, "one", "observed-canary"), entity(b, "two", "observed-canary"),
                entity(shared, "renamed", "observed-canary"), entity(fresh, "shared", "fresh exact 𐀀")),
                List.of(new ExpectedTarget.Edge("uses", a, shared), new ExpectedTarget.Edge("uses", b, fresh)),
                Set.of(), Set.of(a, b, shared, fresh));
        assertEquals(expected, compile(intent));
    }
    @Test void checkedDigestAndMechanismMetadataMustMatchExactRecompilation() {
        var original = definition();
        var intent = new TargetIntent(List.of(), List.of());
        for (var forged : List.of(
                new NativeCompilationResult.Checked(original.definition(), "0".repeat(64), original.bindingDigests(), original.mechanisms()),
                new NativeCompilationResult.Checked(original.definition(), original.logicalDigest(), Map.of("mock", "0".repeat(64)), original.mechanisms()),
                new NativeCompilationResult.Checked(original.definition(), original.logicalDigest(), original.bindingDigests(), Map.of()))) {
            assertEquals("INVALID_DEFINITION", refusal(forged, intent));
        }
        assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(original.definition()));
        assertInstanceOf(TargetCompilationResult.Expected.class,
                new V3TargetIntentCompiler().compile(original, TargetIntentCompilerTest.graph(), intent));
    }
    @Test void genuinelyIncompletePhysicalMappingsCannotUseTheUnqualifiedMechanismException() {
        var original = definition().definition();
        var binding = original.bindings().getFirst();
        var document = binding.documents().getFirst();
        var projection = document.entities().getFirst();
        var missing = new studio.environment.core.definitionv2.NativeDefinition.Projection(projection.id(), projection.type(),
                projection.path(), projection.fields().stream().filter(f -> !f.field().equals("value")).toList(), projection.references());
        var changed = new studio.environment.core.definitionv2.NativeDefinition.Binding(binding.id(), binding.engine(), binding.storage(),
                binding.schema(), binding.table(), binding.keyColumn(), binding.xmlColumn(), binding.keyType(),
                List.of(new studio.environment.core.definitionv2.NativeDefinition.Document(document.id(), document.key(),
                        List.of(missing, document.entities().getLast()))));
        var result = assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(
                new NativeDefinition(original.id(), original.revision(), original.logical(), List.of(changed))));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("FIELD_MAPPING_MISSING")));
        assertEquals("INVALID_DEFINITION", refusal(result.checked(), new TargetIntent(List.of(), List.of())));
    }
    @Test void ineligibleSourceDeclarationCannotReuseOldCheckedMetadata() {
        var original = definition();
        var d = original.definition();
        var l = d.logical();
        var type = l.entityTypes().getFirst();
        var field = type.fields().getFirst();
        var secret = new studio.environment.core.definitionv2.NativeDefinition.Field(field.id(), field.valueType(), field.required(),
                field.classification(), studio.environment.core.definition.DefinitionDraft.Sensitivity.SECRET, field.readable(), field.editable());
        var changedType = new studio.environment.core.definitionv2.NativeDefinition.EntityType(type.id(), type.label(),
                List.of(secret, type.fields().getLast()), type.identity());
        var changedLogical = new NativeDefinition.Logical(List.of(changedType, l.entityTypes().getLast()), l.relations(), l.rules(),
                l.operationCapabilities(), l.computedTypes(), l.derivations(), l.cooccurrences(), l.computedRules());
        var changed = new NativeDefinition(d.id(), d.revision(), changedLogical, d.bindings());
        assertInstanceOf(NativeCompilationResult.Rejected.class, new NativeDefinitionCompiler().compile(changed));
        assertEquals("INVALID_DEFINITION", refusal(new NativeCompilationResult.Checked(changed, original.logicalDigest(),
                original.bindingDigests(), original.mechanisms()), new TargetIntent(List.of(), List.of())));
    }
    @Test void unresolvedPhysicalDecisionsRemainExplicitRefusals() {
        var shared = old("b", "shared");
        assertEquals("UNRESOLVED_FIELD", refusal(definition(), new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(shared,
                Map.of("id", new TargetIntent.FieldValue.KeepObserved(), "value", new TargetIntent.FieldValue.Unresolved()), Map.of())), List.of())));
        assertEquals("UNRESOLVED_REFERENCE", refusal(definition(), new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(old("a", "one"),
                keep(), Map.of("uses", new TargetIntent.ReferenceValue.Unresolved()))), List.of())));
        assertEquals("INVALID_INPUT", assertInstanceOf(TargetCompilationResult.Rejected.class,
                new V3TargetIntentCompiler().compile(null, TargetIntentCompilerTest.graph(), new TargetIntent(List.of(), List.of()))).codes().getFirst());
    }
}
