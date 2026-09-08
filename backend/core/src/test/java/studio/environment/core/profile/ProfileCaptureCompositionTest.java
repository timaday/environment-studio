package studio.environment.core.profile;

import java.math.BigInteger;
import java.util.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.definitionv2.NativeDefinitionCompiler;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.graph.ObservedGraph;
import static org.junit.jupiter.api.Assertions.*;

class ProfileCaptureCompositionTest {
    private final ProfileCapture capture = new ProfileCapture();
    private final ProfileComposer composer = new ProfileComposer();
    static NativeCompilationResult.ReadyToPublish definition() {
        var fields = List.of(new NativeDefinition.Field("id", DefinitionDraft.ValueType.TEXT, true, DefinitionDraft.Classification.STRUCTURAL, DefinitionDraft.Sensitivity.PUBLIC, true, false),
                new NativeDefinition.Field("value", DefinitionDraft.ValueType.TEXT, true, DefinitionDraft.Classification.ENVIRONMENT, DefinitionDraft.Sensitivity.SECRET, true, true));
        var types = List.of(new NativeDefinition.EntityType("a", "A", fields, new NativeDefinition.Identity("id")), new NativeDefinition.EntityType("b", "B", fields, new NativeDefinition.Identity("id")));
        var relation = new DefinitionDraft.Relation("uses", "a", "b", DefinitionDraft.RelationKind.REFERENCE, BigInteger.ONE, BigInteger.ONE, false);
        var root = new NativeDefinition.ExpandedName("", "root");
        var mappings = List.of(new NativeDefinition.FieldMapping("id", new NativeDefinition.ExpandedName("", "id")), new NativeDefinition.FieldMapping("value", new NativeDefinition.ExpandedName("", "value")));
        var projections = List.of(new NativeDefinition.Projection("as", "a", List.of(root, new NativeDefinition.ExpandedName("", "a")), mappings,
                List.of(new NativeDefinition.ReferenceMapping("uses", new NativeDefinition.ExpandedName("", "ref")))),
                new NativeDefinition.Projection("bs", "b", List.of(root, new NativeDefinition.ExpandedName("", "b")), mappings, List.of()));
        var binding = new NativeDefinition.Binding("mock", NativeDefinition.Engine.POSTGRESQL, NativeDefinition.Storage.TEXT, "mock", "mock", "id", "xml", NativeDefinition.KeyType.INT64,
                List.of(new NativeDefinition.Document("doc", "1", projections)));
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(new NativeDefinition("mock", BigInteger.ONE,
                new NativeDefinition.Logical(types, List.of(relation), List.of(), List.of()), List.of(binding))));
    }
    static ObservedGraph.Entity entity(String type, String identity, int index) {
        return new ObservedGraph.Entity(new ObservedGraph.Key(type, identity), Map.of("id", identity, "value", "donor-secret-canary"),
                new ObservedGraph.Origin("raw-document-canary", "projection", "raw-source-digest-canary", index, List.of(0)));
    }
    static GraphValidationResult.Accepted donor() {
        var one = entity("a", "donor-one-canary", 1); var two = entity("a", "donor-two-canary", 2); var shared = entity("b", "donor-shared-canary", 3);
        return new GraphValidationResult.Accepted(new ObservedGraph(List.of(one, two, shared), List.of(new ObservedGraph.Edge("uses", one.key(), shared.key()), new ObservedGraph.Edge("uses", two.key(), shared.key()))));
    }
    static ProfileCapture.Command command() {
        return new ProfileCapture.Command("mock-profile", BigInteger.ONE, List.of(new ProfileCapture.SlotMapping(donor().graph().entities().get(0).key(), "first", "First neutral slot"),
                new ProfileCapture.SlotMapping(donor().graph().entities().get(1).key(), "second", "Second neutral slot"), new ProfileCapture.SlotMapping(donor().graph().entities().get(2).key(), "shared", "Shared neutral slot")));
    }
    @Test void captureIsValueFreeAndPartialReuseAddsOnlyRequiredSharedTarget() {
        var profile = assertInstanceOf(ProfileResult.StructurallyValid.class, capture.capture(definition(), donor(), command())).checked();
        assertEquals(List.of("first", "second", "shared"), profile.profile().entities().stream().map(Profile.Entity::id).toList());
        assertEquals(List.of("id", "value"), profile.profile().entities().getFirst().requiredInputs());
        assertFalse(profile.toString().contains("donor"));
        var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition(), profile, Set.of("first"))).preview();
        assertEquals(List.of("first", "shared"), preview.included().stream().map(Profile.Entity::id).toList());
        assertEquals(List.of("shared"), preview.dependencies().stream().map(ProfileComposer.Dependency::slot).toList());
        assertEquals(List.of(new Profile.Relation("uses", "first", "shared")), preview.relations());
    }
    @Test void compositionCreatesOrExplicitlyReusesDependenciesWithoutCopyingValuesOrDeletingSiblings() {
        var profile = assertInstanceOf(ProfileResult.StructurallyValid.class, capture.capture(definition(), donor(), command())).checked();
        var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition(), profile, Set.of("first"))).preview();
        var existing = entity("b", "target-existing", 1); var unrelated = entity("b", "target-unselected", 2);
        var target = new GraphValidationResult.Accepted(new ObservedGraph(List.of(existing, unrelated), List.of()));
        var result = assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class, composer.compose(definition(), profile, preview, target,
                List.of(new ProfileComposer.Decision.Create("first", "new-first"), new ProfileComposer.Decision.UseExisting("shared", existing.key())))).draft();
        assertEquals(List.of(new ProfileComposer.Target.New("new-first", "a")), result.additions());
        assertEquals(List.of(existing.key(), unrelated.key()), result.retainedExisting());
        assertEquals(2, result.unresolvedFields().size());
        assertTrue(result.unresolvedFields().stream().allMatch(fields -> fields.fields().equals(List.of("id", "value"))));
        assertFalse(result.toString().contains("donor-secret-canary"));
        result = assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class, composer.compose(definition(), profile, preview, target,
                List.of(new ProfileComposer.Decision.Create("first", "new-first"), new ProfileComposer.Decision.Create("shared", "new-shared")))).draft();
        assertEquals(2, result.additions().size()); assertEquals(2, result.retainedExisting().size());
    }

    private static NativeCompilationResult.ReadyToPublish variant(DefinitionDraft.RelationKind kind, int minimum, int maximum, boolean include) {
        var original = definition().checked().definition(); var old = original.bindings().getFirst(); var projections = new ArrayList<>(old.documents().getFirst().entities());
        var a = projections.getFirst(); var b = projections.get(1);
        if (kind == DefinitionDraft.RelationKind.CONTAINMENT) {
            projections.set(0, new NativeDefinition.Projection(a.id(), a.type(), a.path(), a.fields(), List.of()));
            projections.set(1, new NativeDefinition.Projection(b.id(), b.type(), List.of(a.path().getFirst(), a.path().get(1), b.path().get(1)), b.fields(), List.of()));
        }
        var binding = new NativeDefinition.Binding(old.id(), old.engine(), old.storage(), old.schema(), old.table(), old.keyColumn(), old.xmlColumn(), old.keyType(), List.of(new NativeDefinition.Document("doc", "1", projections)));
        var logical = new NativeDefinition.Logical(original.logical().entityTypes(), List.of(new DefinitionDraft.Relation("uses", "a", "b", kind, BigInteger.valueOf(minimum), BigInteger.valueOf(maximum), include)), List.of(), List.of());
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(new NativeDefinition("mock", BigInteger.ONE, logical, List.of(binding))));
    }
    private static ProfileResult.Checked structure(NativeCompilationResult.ReadyToPublish definition) {
        return assertInstanceOf(ProfileResult.StructurallyValid.class, new ProfileValidator().validate(definition, new Profile("mock", BigInteger.ONE, definition.checked().logicalDigest(),
            List.of(new Profile.Entity("parent", "a", "Neutral parent", List.of("id", "value")), new Profile.Entity("one", "b", "Neutral child one", List.of("id", "value")), new Profile.Entity("two", "b", "Neutral child two", List.of("id", "value"))),
            List.of(new Profile.Relation("uses", "parent", "one"), new Profile.Relation("uses", "parent", "two"))))).checked();
    }
    private static GraphValidationResult.Accepted empty() { return new GraphValidationResult.Accepted(new ObservedGraph(List.of(), List.of())); }
    @Test void containmentAddsParentReportsMinimumConflictAndNeverArbitrarilyAddsSibling() {
        var definition = variant(DefinitionDraft.RelationKind.CONTAINMENT, 2, 2, false); var profile = structure(definition);
        var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, Set.of("one"))).preview();
        assertEquals(List.of("one", "parent"), preview.included().stream().map(Profile.Entity::id).toList());
        assertEquals(List.of(new ProfileComposer.Dependency("parent", "one", "uses", ProfileComposer.Reason.CONTAINMENT_PARENT)), preview.dependencies());
        assertEquals(List.of(new ProfileComposer.Conflict("RELATION_CARDINALITY", "parent", "uses")), preview.conflicts());
        var unresolved = assertInstanceOf(ProfileComposer.CompositionResult.NeedsResolution.class, composer.compose(definition, profile, preview, empty(),
            List.of(new ProfileComposer.Decision.Create("one", "new-child"), new ProfileComposer.Decision.Create("parent", "new-parent"))));
        assertEquals(2, unresolved.draft().additions().size()); assertFalse(unresolved.conflicts().isEmpty());
        var parent = entity("a", "existing-parent", 1); var child = entity("b", "existing-child", 2); var other = entity("b", "existing-other", 3);
        var target = new GraphValidationResult.Accepted(new ObservedGraph(List.of(parent, child, other), List.of(new ObservedGraph.Edge("uses", parent.key(), child.key()), new ObservedGraph.Edge("uses", parent.key(), other.key()))));
        var kept = assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class, composer.compose(definition, profile, preview, target,
            List.of(new ProfileComposer.Decision.UseExisting("one", child.key()), new ProfileComposer.Decision.UseExisting("parent", parent.key()))));
        assertEquals(target.graph().edges(), kept.draft().retainedRelations()); assertEquals(3, kept.draft().retainedExisting().size());
    }
    @Test void declaredReuseTargetFlagIncludesContainmentChildrenFromNewlyAddedParent() {
        var definition = variant(DefinitionDraft.RelationKind.CONTAINMENT, 2, 2, true); var profile = structure(definition);
        var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, Set.of("one"))).preview();
        assertEquals(List.of("one", "parent", "two"), preview.included().stream().map(Profile.Entity::id).toList());
        assertTrue(preview.dependencies().contains(new ProfileComposer.Dependency("two", "parent", "uses", ProfileComposer.Reason.DECLARED_REUSE_TARGET)));
        assertTrue(preview.conflicts().isEmpty());
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, composer.compose(definition, profile, preview, empty(), List.of(new ProfileComposer.Decision.Create("one", "child"), new ProfileComposer.Decision.Create("parent", "parent"))));
    }
    @Test void optionalReferencesEnterClosureOnlyWhenDeclaredAndWholeReuseUsesSameAlgorithm() {
        for (boolean include : List.of(false, true)) {
            var definition = variant(DefinitionDraft.RelationKind.REFERENCE, 0, 1, include);
            var profile = assertInstanceOf(ProfileResult.StructurallyValid.class, capture.capture(definition, donor(), command())).checked();
            var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, Set.of("first"))).preview();
            assertEquals(include ? List.of("first", "shared") : List.of("first"), preview.included().stream().map(Profile.Entity::id).toList());
            var all = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, Set.of("first", "second", "shared"))).preview();
            assertEquals(3, all.included().size()); assertEquals(2, all.relations().size()); assertTrue(all.dependencies().isEmpty());
        }
    }
    @Test void compositionRejectsMissingExtraDuplicateCollapsingStaleAndIncompatibleDecisions() {
        var definition = definition(); var profile = assertInstanceOf(ProfileResult.StructurallyValid.class, capture.capture(definition, donor(), command())).checked();
        var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, Set.of("first"))).preview();
        var a = new ProfileComposer.Decision.Create("first", "new-a"); var b = new ProfileComposer.Decision.Create("shared", "new-b");
        for (List<ProfileComposer.Decision> decisions : List.<List<ProfileComposer.Decision>>of(List.of(a), List.of(a, a, b), List.of(a, b, new ProfileComposer.Decision.Create("second", "new-c")), List.of(a, new ProfileComposer.Decision.Create("shared", "new-a")), List.of(a, new ProfileComposer.Decision.UseExisting("shared", new ObservedGraph.Key("b", "absent-canary")))))
            assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, composer.compose(definition, profile, preview, empty(), decisions));
        assertInstanceOf(ProfileComposer.CompositionResult.Cancelled.class, composer.compose(definition, profile, preview, empty(), List.of(a, new ProfileComposer.Decision.Cancel("shared"))));
        var changed = new Profile(profile.profile().id(), BigInteger.TWO, profile.profile().logicalDefinitionDigest(), profile.profile().entities(), profile.profile().relations());
        var changedChecked = assertInstanceOf(ProfileResult.StructurallyValid.class, new ProfileValidator().validate(definition, changed)).checked();
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, composer.compose(definition, changedChecked, preview, empty(), List.of(a, b)));
        var edited = new ProfileComposer.Preview(preview.profileId(), preview.revision(), preview.contentDigest(), preview.logicalDigest(), preview.selected(), preview.included(), List.of(), preview.relations(), preview.conflicts());
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, composer.compose(definition, profile, edited, empty(), List.of(a, b)));
        assertInstanceOf(ProfileComposer.PreviewResult.Rejected.class, composer.preview(definition, new ProfileResult.Checked(profile.profile(), "0".repeat(64)), Set.of("first")));
        assertInstanceOf(ProfileComposer.PreviewResult.Rejected.class, composer.preview(variant(DefinitionDraft.RelationKind.REFERENCE, 0, 1, false), profile, Set.of("first")));
        assertInstanceOf(ProfileComposer.PreviewResult.Rejected.class, composer.preview(definition, profile, Set.of()));
        assertInstanceOf(ProfileComposer.PreviewResult.Rejected.class, composer.preview(definition, profile, Set.of("unknown")));
        var existingA = entity("a", "existing-a", 1); var target = new GraphValidationResult.Accepted(new ObservedGraph(List.of(existingA), List.of()));
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, composer.compose(definition, profile, preview, target, List.of(a, new ProfileComposer.Decision.UseExisting("shared", existingA.key()))));
    }
    @Test void reuseNeverSilentlyMovesExistingReferenceAndDistinctSlotsCannotCollapse() {
        var definition = definition(); var profile = assertInstanceOf(ProfileResult.StructurallyValid.class, capture.capture(definition, donor(), command())).checked();
        var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, Set.of("first"))).preview();
        var source = entity("a", "existing-source", 1); var old = entity("b", "existing-old", 2); var target = new GraphValidationResult.Accepted(new ObservedGraph(List.of(source, old), List.of(new ObservedGraph.Edge("uses", source.key(), old.key()))));
        var conflict = assertInstanceOf(ProfileComposer.CompositionResult.NeedsResolution.class, composer.compose(definition, profile, preview, target, List.of(new ProfileComposer.Decision.UseExisting("first", source.key()), new ProfileComposer.Decision.Create("shared", "new-target"))));
        assertEquals(target.graph().edges(), conflict.draft().retainedRelations()); assertTrue(conflict.conflicts().stream().anyMatch(c -> c.code().equals("RELATION_CARDINALITY")));
        var whole = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, Set.of("first", "second"))).preview();
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, composer.compose(definition, profile, whole, target, List.of(new ProfileComposer.Decision.UseExisting("first", source.key()), new ProfileComposer.Decision.UseExisting("second", source.key()), new ProfileComposer.Decision.UseExisting("shared", old.key()))));
    }
    @Test void fullCaptureRejectsNonBijectionsAndInvalidNeutralDeclarations() {
        var original = command().mappings();
        for (var mappings : List.of(original.subList(0, 2), List.of(original.getFirst(), original.getFirst(), original.get(2)), List.of(original.getFirst(), original.get(1), new ProfileCapture.SlotMapping(new ObservedGraph.Key("b", "extra-donor-canary"), "third", "Neutral")), List.of(original.getFirst(), original.get(1), new ProfileCapture.SlotMapping(original.get(2).observed(), "first", "Neutral")), List.of(original.getFirst(), original.get(1), new ProfileCapture.SlotMapping(original.get(2).observed(), "bad\n", "Neutral")), List.of(original.getFirst(), original.get(1), new ProfileCapture.SlotMapping(original.get(2).observed(), "third", "")))) {
            var rejected = assertInstanceOf(ProfileResult.Rejected.class, capture.capture(definition(), donor(), new ProfileCapture.Command("mock", BigInteger.ONE, mappings)));
            assertFalse(rejected.toString().contains("canary"));
        }
    }
    @Test void captureReportsStructuralValidityIndependentlyOfWireBudgetsAndKeepsNestedImmutability() {
        var definition = definition();
        for (String label : List.of("Neutral", "\u0000".repeat(128))) {
            int count = label.equals("Neutral") ? 1818 : 1800;
            var entities = new ArrayList<ObservedGraph.Entity>(); var mappings = new ArrayList<ProfileCapture.SlotMapping>();
            for (int i = 0; i < count; i++) { var entity = entity("b", "donor-" + i, i); entities.add(entity); mappings.add(new ProfileCapture.SlotMapping(entity.key(), "slot-" + i, label)); }
            assertInstanceOf(ProfileResult.StructurallyValid.class, capture.capture(definition, new GraphValidationResult.Accepted(new ObservedGraph(entities, List.of())), new ProfileCapture.Command("mock", BigInteger.ONE, mappings)));
        }
        var mutable = new ArrayList<>(command().mappings()); var command = new ProfileCapture.Command("mock", BigInteger.ONE, mutable); mutable.clear(); assertEquals(3, command.mappings().size());
        var profile = assertInstanceOf(ProfileResult.StructurallyValid.class, capture.capture(definition, donor(), command)).checked();
        assertThrows(UnsupportedOperationException.class, () -> profile.profile().entities().clear());
        assertThrows(UnsupportedOperationException.class, () -> profile.profile().entities().getFirst().requiredInputs().clear());
        var virtual = new AbstractList<ProfileCapture.SlotMapping>() { public int size() { return Integer.MAX_VALUE; } public ProfileCapture.SlotMapping get(int index) { throw new AssertionError("Must bound before iteration"); } };
        assertThrows(IllegalArgumentException.class, () -> new ProfileCapture.Command("mock", BigInteger.ONE, virtual));
    }

    @Test void completedTargetCountRulesAndOptionalFieldDecisionsRemainExplicit() {
        var original = definition().checked().definition();
        var types = original.logical().entityTypes().stream().map(t -> new NativeDefinition.EntityType(t.id(), t.label(),
            t.fields().stream().map(f -> new NativeDefinition.Field(f.id(), f.valueType(), f.id().equals("id"), f.classification(), f.sensitivity(), f.readable(), f.editable())).toList(), t.identity())).toList();
        var logical = new NativeDefinition.Logical(types, original.logical().relations(), List.of(new NativeDefinition.CountRule("at-most-two", "a", BigInteger.ZERO, BigInteger.TWO)), List.of());
        var definition = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(new NativeDefinition("mock", BigInteger.ONE, logical, original.bindings())));
        var profile = assertInstanceOf(ProfileResult.StructurallyValid.class, capture.capture(definition, donor(), command())).checked();
        assertEquals(List.of("id"), profile.profile().entities().getFirst().requiredInputs());
        var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, Set.of("first"))).preview();
        var result = assertInstanceOf(ProfileComposer.CompositionResult.NeedsResolution.class, composer.compose(definition, profile, preview, donor(),
            List.of(new ProfileComposer.Decision.Create("first", "new-first"), new ProfileComposer.Decision.Create("shared", "new-shared"))));
        assertEquals(List.of(new ProfileComposer.Conflict("ENTITY_COUNT", "", "at-most-two")), result.conflicts());
        assertTrue(result.draft().unresolvedFields().stream().allMatch(f -> f.fields().equals(List.of("id", "value"))));
        // A reusable subset does not itself have to satisfy an environment count rule.
        assertInstanceOf(ProfileResult.StructurallyValid.class, new ProfileValidator().validate(definition, new Profile("subset", BigInteger.ONE, definition.checked().logicalDigest(), preview.included(), preview.relations())));
    }
    @Test void containmentMultipleParentsMissingParentAndCycleGuardRefuse() {
        var definition = variant(DefinitionDraft.RelationKind.CONTAINMENT, 0, 2, false); var profile = structure(definition).profile();
        var entities = new ArrayList<>(profile.entities()); entities.add(new Profile.Entity("other-parent", "a", "Another neutral parent", List.of("id", "value")));
        var edges = new ArrayList<>(profile.relations()); edges.add(new Profile.Relation("uses", "other-parent", "one"));
        assertEquals("MULTIPLE_CONTAINMENT_PARENTS", assertInstanceOf(ProfileResult.Rejected.class, new ProfileValidator().validate(definition, new Profile("mock", BigInteger.ONE, profile.logicalDefinitionDigest(), entities, edges))).diagnostics().getFirst().code());
        assertEquals("CONTAINMENT_PARENT_MISSING", assertInstanceOf(ProfileResult.Rejected.class, new ProfileValidator().validate(definition, new Profile("mock", BigInteger.ONE, profile.logicalDefinitionDigest(), profile.entities(), profile.relations().subList(0, 1)))).diagnostics().getFirst().code());
        // Native XML ancestor qualification precludes cyclic containment declarations; challenge the defensive iterative graph guard directly.
        assertTrue(ProfileValidator.cyclic(Map.of("one", "two", "two", "one")));
        assertTrue(ProfileValidator.cyclic(Map.of("one", "one")));
        assertFalse(ProfileValidator.cyclic(Map.of("one", "parent", "two", "parent")));
    }
    @Test void compositionBoundsVirtualDecisionsBeforeAccessAndPreviewCopiesNestedLists() {
        var definition = definition(); var profile = assertInstanceOf(ProfileResult.StructurallyValid.class, capture.capture(definition, donor(), command())).checked();
        var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, Set.of("first"))).preview();
        var virtual = new AbstractList<ProfileComposer.Decision>() { public int size() { return Integer.MAX_VALUE; } public ProfileComposer.Decision get(int i) { throw new AssertionError("Must bound before iteration"); } };
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, composer.compose(definition, profile, preview, empty(), virtual));
        assertThrows(UnsupportedOperationException.class, () -> preview.selected().clear());
        assertThrows(UnsupportedOperationException.class, () -> preview.dependencies().clear());
        assertThrows(UnsupportedOperationException.class, () -> preview.relations().clear());
        var fields = new ArrayList<>(List.of("id")); var unresolved = new ProfileComposer.UnresolvedFields(new ProfileComposer.Target.New("new-slot", "b"), fields); fields.clear(); assertEquals(List.of("id"), unresolved.fields());
    }

    @Test void requiredReferenceCycleReachesStableClosureWithoutDuplicatingSlots() {
        var original = definition().checked().definition(); var binding = original.bindings().getFirst(); var projections = new ArrayList<>(binding.documents().getFirst().entities());
        var targetProjection = projections.get(1); projections.set(1, new NativeDefinition.Projection(targetProjection.id(), targetProjection.type(), targetProjection.path(), targetProjection.fields(), List.of(new NativeDefinition.ReferenceMapping("returns", new NativeDefinition.ExpandedName("", "back")))));
        var relations = new ArrayList<>(original.logical().relations()); relations.add(new DefinitionDraft.Relation("returns", "b", "a", DefinitionDraft.RelationKind.REFERENCE, BigInteger.ONE, BigInteger.ONE, false));
        var rebound = new NativeDefinition.Binding(binding.id(), binding.engine(), binding.storage(), binding.schema(), binding.table(), binding.keyColumn(), binding.xmlColumn(), binding.keyType(), List.of(new NativeDefinition.Document("doc", "1", projections)));
        var definition = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(new NativeDefinition("mock", BigInteger.ONE, new NativeDefinition.Logical(original.logical().entityTypes(), relations, List.of(), List.of()), List.of(rebound))));
        var profile = assertInstanceOf(ProfileResult.StructurallyValid.class, new ProfileValidator().validate(definition, new Profile("cycle", BigInteger.ONE, definition.checked().logicalDigest(),
            List.of(new Profile.Entity("one", "a", "Neutral one", List.of("id", "value")), new Profile.Entity("two", "b", "Neutral two", List.of("id", "value"))),
            List.of(new Profile.Relation("uses", "one", "two"), new Profile.Relation("returns", "two", "one"))))).checked();
        var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, Set.of("one"))).preview();
        assertEquals(List.of("one", "two"), preview.included().stream().map(Profile.Entity::id).toList()); assertEquals(1, preview.dependencies().size()); assertEquals(2, preview.relations().size());
        assertEquals(preview, assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, composer.preview(definition, profile, new HashSet<>(Set.of("one")))).preview());
    }
}
