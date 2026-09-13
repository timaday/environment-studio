package studio.environment.core.profile;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.graph.ObservedGraph;

/** Independent neutral slots and mock donor canaries; no application configuration. */
class V3ProfileTest {
    static NativeCompilationResult.Checked definition() {
        var original = ProfileCaptureCompositionTest.definition().checked().definition();
        var types = original.logical().entityTypes().stream().map(t -> new EntityType(t.id(), t.label(), t.fields().stream()
                .map(f -> new Field(f.id(), f.valueType(), f.required(), f.classification(), Sensitivity.PUBLIC, f.readable(), f.editable())).toList(), t.identity())).toList();
        var logical = new NativeDefinition.Logical(types, original.logical().relations(), List.of(), List.of(),
                List.of(new NativeDefinition.ComputedType("a-values", "A values"), new NativeDefinition.ComputedType("b-values", "B values")),
                List.of(new NativeDefinition.Derivation("by-a", "a", "value", "a-values", "has-a"),
                        new NativeDefinition.Derivation("by-b", "b", "value", "b-values", "has-b")), List.of(),
                List.of(new CountRule("minimum-a", "a-values", BigInteger.TEN, BigInteger.TEN)));
        var result = assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new NativeDefinitionCompiler().compile(new NativeDefinition("mock-v3", BigInteger.ONE, logical, original.bindings())));
        assertTrue(result.diagnostics().stream().allMatch(d -> d.code().equals("MECHANISM_UNQUALIFIED")));
        return result.checked();
    }
    static Profile profile(NativeCompilationResult.Checked d) {
        return new Profile("neutral", BigInteger.ONE, d.logicalDigest(), List.of(
                new Profile.Entity("first", "a", "First", List.of("id", "value")),
                new Profile.Entity("second", "a", "Second", List.of("id", "value")),
                new Profile.Entity("shared", "b", "Shared", List.of("id", "value"))),
                List.of(new Profile.Relation("uses", "first", "shared"), new Profile.Relation("uses", "second", "shared")));
    }
    @Test void physicalCaptureDropsAllDonorValuesAndComputations() {
        var result = assertInstanceOf(ProfileResult.StructurallyValid.class, new V3ProfileCapture().capture(definition(),
                ProfileCaptureCompositionTest.donor().graph(), ProfileCaptureCompositionTest.command())).checked();
        assertEquals(List.of("first", "second", "shared"), result.profile().entities().stream().map(Profile.Entity::id).toList());
        assertEquals(List.of(new Profile.Relation("uses", "first", "shared"), new Profile.Relation("uses", "second", "shared")), result.profile().relations());
        assertEquals(new Profile("mock-profile", BigInteger.ONE, definition().logicalDigest(), List.of(
                new Profile.Entity("first", "a", "First neutral slot", List.of("id", "value")),
                new Profile.Entity("second", "a", "Second neutral slot", List.of("id", "value")),
                new Profile.Entity("shared", "b", "Shared neutral slot", List.of("id", "value"))),
                List.of(new Profile.Relation("uses", "first", "shared"), new Profile.Relation("uses", "second", "shared"))), result.profile());
        assertFalse(result.toString().contains("canary"));
    }
    @Test void partialPhysicalClosureDoesNotSelectSiblingsToMeetDerivedMinimum() {
        var d = definition();
        var checked = assertInstanceOf(ProfileResult.StructurallyValid.class, new V3ProfileValidator().validate(d, profile(d))).checked();
        var result = assertInstanceOf(V3ProfileComposer.PreviewResult.Proposed.class, new V3ProfileComposer().preview(d, checked, Set.of("first"))).preview();
        assertEquals(List.of("first", "shared"), result.physical().included().stream().map(Profile.Entity::id).toList());
        assertEquals(List.of("by-a", "by-b"), result.affectedDerivations());
        assertTrue(result.physical().conflicts().isEmpty());
        var composed = assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class, new V3ProfileComposer().compose(d, checked, result,
                new ObservedGraph(List.of(), List.of()), List.of(new ProfileComposer.Decision.Create("first", "new-a"), new ProfileComposer.Decision.Create("shared", "new-b"))));
        assertEquals(2, composed.draft().additions().size());
        assertTrue(composed.draft().unresolvedFields().stream().allMatch(f -> f.fields().equals(List.of("id", "value"))));
    }
    static ProfileResult.Checked checked(NativeCompilationResult.Checked d) {
        return assertInstanceOf(ProfileResult.StructurallyValid.class, new V3ProfileValidator().validate(d, profile(d))).checked();
    }
    static V3ProfileComposer.Preview preview(NativeCompilationResult.Checked d, ProfileResult.Checked profile) {
        return assertInstanceOf(V3ProfileComposer.PreviewResult.Proposed.class,
                new V3ProfileComposer().preview(d, profile, Set.of("first"))).preview();
    }
    static List<ProfileComposer.Decision> decisions() {
        return List.of(new ProfileComposer.Decision.Create("first", "new-a"), new ProfileComposer.Decision.Create("shared", "new-b"));
    }
    static ObservedGraph empty() { return new ObservedGraph(List.of(), List.of()); }
    @Test void computedTypesMembershipAndCooccurrenceNeverBecomePortableStructure() {
        var d = definition(); var p = profile(d);
        var computed = new Profile(p.id(), p.revision(), p.logicalDefinitionDigest(), List.of(new Profile.Entity("group", "a-values", "Neutral", List.of())), List.of());
        assertEquals(ProfileResult.rejected("INVALID_ENTITY", ""), new V3ProfileValidator().validate(d, computed));
        for (String relation : List.of("has-a", "computed-pair")) {
            var edges = new ArrayList<>(p.relations()); edges.add(new Profile.Relation(relation, "first", "shared"));
            assertEquals(ProfileResult.rejected("INVALID_RELATION", ""), new V3ProfileValidator().validate(d,
                    new Profile(p.id(), p.revision(), p.logicalDefinitionDigest(), p.entities(), edges)));
        }
        var entities = new ArrayList<>(p.entities()); entities.set(0, new Profile.Entity("first", "a", "First", List.of("id")));
        assertEquals(ProfileResult.rejected("REQUIRED_INPUTS_MISMATCH", ""), new V3ProfileValidator().validate(d,
                new Profile(p.id(), p.revision(), p.logicalDefinitionDigest(), entities, p.relations())));
    }
    @Test void captureRequiresAllPhysicalEntitiesWithOneToOneNeutralMappings() {
        var d = definition(); var graph = ProfileCaptureCompositionTest.donor().graph(); var command = ProfileCaptureCompositionTest.command();
        for (var mappings : List.of(command.mappings().subList(0, 2),
                List.of(command.mappings().getFirst(), command.mappings().getFirst(), command.mappings().getLast()))) {
            assertEquals(ProfileResult.rejected("MAPPING_NOT_BIJECTIVE", ""), new V3ProfileCapture().capture(d, graph,
                    new ProfileCapture.Command(command.id(), command.revision(), mappings)));
        }
        assertEquals(ProfileResult.rejected("INVALID_PROFILE", ""), new V3ProfileCapture().capture(d, empty(),
                new ProfileCapture.Command("empty", BigInteger.ONE, List.of())));
        var fakeComputed = ProfileCaptureCompositionTest.entity("a-values", "computed-value-canary", 1);
        assertEquals(ProfileResult.rejected("MAPPING_NOT_BIJECTIVE", ""), new V3ProfileCapture().capture(d,
                new ObservedGraph(List.of(fakeComputed), List.of()), new ProfileCapture.Command("mock", BigInteger.ONE,
                        List.of(new ProfileCapture.SlotMapping(fakeComputed.key(), "neutral", "Neutral")))));
    }
    @Test void forgedCheckedMetadataCannotEnterAnyV3Port() {
        var d = definition(); var p = checked(d); var preview = preview(d, p);
        var forged = new NativeCompilationResult.Checked(d.definition(), "0".repeat(64), d.bindingDigests(), d.mechanisms());
        assertEquals(ProfileResult.rejected("INVALID_DEFINITION", ""), new V3ProfileValidator().validate(forged, p.profile()));
        assertEquals(ProfileResult.rejected("INVALID_DEFINITION", ""), new V3ProfileCapture().capture(forged,
                ProfileCaptureCompositionTest.donor().graph(), ProfileCaptureCompositionTest.command()));
        assertInstanceOf(V3ProfileComposer.PreviewResult.Rejected.class, new V3ProfileComposer().preview(forged, p, Set.of("first")));
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, new V3ProfileComposer().compose(forged, p, preview, empty(), decisions()));
        assertEquals(ProfileResult.rejected("INCOMPATIBLE_DEFINITION", ""), new V3ProfileValidator().validate(d,
                new Profile("neutral", BigInteger.ONE, "0".repeat(64), p.profile().entities(), p.profile().relations())));
    }
    @Test void stalePhysicalOrAffectedPreviewAndProfileDigestAreRejected() {
        var d = definition(); var p = checked(d); var preview = preview(d, p); var composer = new V3ProfileComposer();
        for (var changed : List.of(List.<String>of(), List.of("by-b", "by-a"), List.of("by-a", "by-b", "by-b"))) {
            assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class,
                    composer.compose(d, p, new V3ProfileComposer.Preview(preview.physical(), changed), empty(), decisions()));
        }
        var old = preview.physical();
        var forged = new ProfileComposer.Preview(old.profileId(), old.revision(), old.contentDigest(), old.logicalDigest(), old.selected(),
                old.included(), List.of(), old.relations(), old.conflicts());
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class,
                composer.compose(d, p, new V3ProfileComposer.Preview(forged, preview.affectedDerivations()), empty(), decisions()));
        assertInstanceOf(V3ProfileComposer.PreviewResult.Rejected.class,
                composer.preview(d, new ProfileResult.Checked(p.profile(), "0".repeat(64)), Set.of("first")));
        assertThrows(UnsupportedOperationException.class, () -> preview.affectedDerivations().clear());
    }
    @Test void allExistingEntitiesAreRetainedInScalarOrderAndEveryMappedFieldStartsUnresolved() {
        var d = definition(); var p = checked(d); var preview = preview(d, p);
        var bmp = ProfileCaptureCompositionTest.entity("b", "\uE000", 1);
        var supplementary = ProfileCaptureCompositionTest.entity("b", "\uD800\uDC00", 2);
        var current = new ObservedGraph(List.of(supplementary, bmp), List.of());
        var mappings = List.<ProfileComposer.Decision>of(new ProfileComposer.Decision.Create("first", "new-a"),
                new ProfileComposer.Decision.UseExisting("shared", supplementary.key()));
        var draft = assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class,
                new V3ProfileComposer().compose(d, p, preview, current, mappings)).draft();
        assertEquals(List.of(bmp.key(), supplementary.key()), draft.retainedExisting());
        assertEquals(List.of("id", "value"), draft.unresolvedFields().getFirst().fields());
        assertEquals(List.of("id", "value"), draft.unresolvedFields().getLast().fields());
        assertFalse(draft.toString().contains("donor-secret-canary"));
        var oldD = ProfileCaptureCompositionTest.definition();
        var oldP = assertInstanceOf(ProfileResult.StructurallyValid.class, new ProfileCapture().capture(oldD,
                ProfileCaptureCompositionTest.donor(), ProfileCaptureCompositionTest.command())).checked();
        var oldPreview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, new ProfileComposer().preview(oldD, oldP, Set.of("first"))).preview();
        var oldDraft = assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class, new ProfileComposer().compose(oldD, oldP, oldPreview,
                new studio.environment.core.graph.GraphValidationResult.Accepted(current), mappings)).draft();
        assertEquals(List.of(supplementary.key(), bmp.key()), oldDraft.retainedExisting());
    }
    @Test void explicitCancelMissingMappingsAndComputedCurrentRefuseWithoutInventingDecisions() {
        var d = definition(); var p = checked(d); var preview = preview(d, p); var composer = new V3ProfileComposer();
        assertInstanceOf(ProfileComposer.CompositionResult.Cancelled.class, composer.compose(d, p, preview, empty(),
                List.of(new ProfileComposer.Decision.Cancel("first"), new ProfileComposer.Decision.Create("shared", "new-b"))));
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, composer.compose(d, p, preview, empty(), List.of()));
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, composer.compose(d, p, preview,
                new ObservedGraph(List.of(ProfileCaptureCompositionTest.entity("a-values", "computed-canary", 1)), List.of()), decisions()));
    }
    @Test void versionedDigestOracleInputsAndRevisionInvariance() {
        var d = definition(); var p = checked(d);
        var old = assertInstanceOf(ProfileResult.StructurallyValid.class, new ProfileCapture().capture(ProfileCaptureCompositionTest.definition(),
                ProfileCaptureCompositionTest.donor(), ProfileCaptureCompositionTest.command())).checked();
        assertEquals("b210db307a2125d647b7140b04b18312f1cccc142d48acc724abe5ab8b33cfc2", d.logicalDigest());
        assertEquals("e47038377f17faa7a8fd75d77bea12e8cf66f200df7dcf7440017941fdab61fa", old.contentDigest());
        assertEquals("a1823f38f7b7639109f155af938ba27ec4e662f8e96b3253a1902eb32e28b2a2", p.contentDigest());
        assertEquals("5dc101ded6cad8c6d934a64ec9cac5631577c4cd923b0248189f62c73d426b12", ProfileEncoding.digest(p.profile()));
        assertNotEquals(ProfileEncoding.digest(p.profile()), p.contentDigest());
        var revised = new Profile(p.profile().id(), BigInteger.TEN, p.profile().logicalDefinitionDigest(), p.profile().entities(), p.profile().relations());
        assertEquals(p.contentDigest(), assertInstanceOf(ProfileResult.StructurallyValid.class, new V3ProfileValidator().validate(d, revised)).checked().contentDigest());
    }
    @Test void genuinelyUnsupportedPhysicalDeclarationCannotUseQualificationException() {
        var d = definition().definition(); var b = d.bindings().getFirst(); var doc = b.documents().getFirst();
        var projections = new ArrayList<>(doc.entities()); var first = projections.getFirst();
        projections.set(0, new Projection(first.id(), first.type(), first.path(), first.fields().subList(0, 1), first.references()));
        var binding = new Binding(b.id(), b.engine(), b.storage(), b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(),
                List.of(new Document(doc.id(), doc.key(), projections)));
        var compiled = assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new NativeDefinitionCompiler().compile(new NativeDefinition(d.id(), d.revision(), d.logical(), List.of(binding))));
        assertTrue(compiled.diagnostics().stream().anyMatch(x -> x.code().equals("FIELD_MAPPING_MISSING")));
        assertEquals(ProfileResult.rejected("INVALID_DEFINITION", ""), new V3ProfileValidator().validate(compiled.checked(), profile(compiled.checked())));
    }
}
