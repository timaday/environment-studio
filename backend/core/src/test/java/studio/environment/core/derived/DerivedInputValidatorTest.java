package studio.environment.core.derived;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.derived.DerivedInput.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;

/** Independently invented internal pins; these are not actual XML/DB qualification evidence. */
class DerivedInputValidatorTest {
    static final String SOURCE = "a".repeat(64);
    static NativeCompilationResult.Checked definition(boolean child) {
        var fields = List.of(new Field("id", ValueType.TEXT, true, Classification.STRUCTURAL, Sensitivity.PUBLIC, true, false),
                new Field("tone", ValueType.TEXT, false, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true));
        var type = new EntityType("item", "Invented item", fields, new Identity("id"));
        var logical = new NativeDefinition.Logical(List.of(type), List.of(), List.of(),
                List.of(Operation.RETAIN_ENTITY, Operation.CREATE_ENTITY, Operation.BIND_FIELD),
                List.of(new NativeDefinition.ComputedType("group", "Invented group")),
                List.of(new NativeDefinition.Derivation("by-tone", "item", "tone", "group", "membership")), List.of(), List.of());
        FieldLocator locator = child ? new ChildProperty(new ExpandedName("urn:mock:props", "entry"),
                new ExpandedName("", "key"), "tone", new ExpandedName("", "value")) : new DirectAttribute(new ExpandedName("", "tone"));
        var projection = new Projection("items", "item", List.of(new ExpandedName("urn:mock", "items"), new ExpandedName("urn:mock", "item")),
                List.of(new FieldMapping("id", new ExpandedName("", "id")), new FieldMapping("tone", locator)), List.of());
        var binding = new Binding("mock-pg", Engine.POSTGRESQL, Storage.TEXT, "mock_schema", "mock_table", "mock_key", "mock_xml", KeyType.INT64,
                List.of(new Document("sheet", "1", List.of(projection))));
        return assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new NativeDefinitionCompiler().compile(new NativeDefinition("mock", BigInteger.ONE, logical, List.of(binding)))).checked();
    }
    static Pin pin(NativeCompilationResult.Checked definition) {
        return new Pin("revision-1", definition.logicalDigest(), "mock-pg", definition.bindingDigests().get("mock-pg"), Map.of("sheet", SOURCE));
    }
    static Ref.Observed observed(String id, int index) {
        return new Ref.Observed(new ObservedGraph.Key("item", id), new ObservedGraph.Origin("sheet", "items", SOURCE, index, List.of(0)));
    }
    static AttributePin attribute(String name, String text, int index) {
        return new AttributePin("sheet", SOURCE, index, new ExpandedName("", name), name, text, 10, 10 + text.length(), '"');
    }
    static Location direct(String text, int index) { return new Location(attribute("tone", text, index), Optional.empty()); }
    static DerivedInput observedInput(NativeCompilationResult.Checked definition) {
        return new DerivedInput(Kind.OBSERVED, pin(definition), List.of(new Entity(observed("one", 1),
                Map.of("tone", new FieldState.Present("alpha", new Proof.Observed(direct("alpha", 1)))))), List.of());
    }
    private void valid(NativeCompilationResult.Checked definition, DerivedInput input) {
        assertInstanceOf(DerivedInputValidator.Valid.class, DerivedInputValidator.check(definition, pin(definition), input));
    }
    private void refused(NativeCompilationResult.Checked definition, DerivedInput input, String code) {
        var check = assertInstanceOf(DerivedInputValidator.Refused.class, DerivedInputValidator.check(definition, pin(definition), input));
        assertEquals(code, check.code());
    }
    @Test void acceptsConsistentObservedPinsAndExplicitOptionalAbsence() {
        var definition = definition(false); valid(definition, observedInput(definition));
        valid(definition, withFields(definition, observed("one", 1), Map.of("tone", new FieldState.Absent()), Kind.OBSERVED));
    }
    @Test void staleSelectionRevisionDocumentDigestAndMetadataPinsRefuse() {
        var definition = definition(false); var input = observedInput(definition); var original = pin(definition);
        for (var pin : List.of(new Pin("other-revision", original.logicalDigest(), original.bindingId(), original.bindingDigest(), original.documentDigests()),
                new Pin(original.revisionToken(), "b".repeat(64), original.bindingId(), original.bindingDigest(), original.documentDigests()),
                new Pin(original.revisionToken(), original.logicalDigest(), original.bindingId(), original.bindingDigest(), Map.of("sheet", "b".repeat(64)))))
            refused(definition, new DerivedInput(input.kind(), pin, input.entities(), input.edges()), "STALE_INPUT");
    }
    @Test void wrongSourceElementAttributeDigestAndValueProofRefuse() {
        var definition = definition(false); var original = attribute("tone", "alpha", 1);
        for (var bad : List.of(attribute("tone", "alpha", 2), attribute("wrong", "alpha", 1), attribute("tone", "beta", 1),
                new AttributePin("sheet", "b".repeat(64), 1, original.name(), "tone", "alpha", 10, 15, '"'),
                new AttributePin("foreign", SOURCE, 1, original.name(), "tone", "alpha", 10, 15, '"'),
                new AttributePin("sheet", SOURCE, 1, original.name(), "wrong", "alpha", 10, 15, '"'),
                new AttributePin("sheet", SOURCE, 1, original.name(), "tone", "alpha", 15, 10, '"')))
            refused(definition, withFields(definition, observed("one", 1), Map.of("tone",
                    new FieldState.Present("alpha", new Proof.Observed(new Location(bad, Optional.empty())))), Kind.OBSERVED), "INVALID_PROVENANCE");
    }
    @Test void aChildProofRequiresTheActualDiscriminatorAndDirectParentRole() {
        var definition = definition(true);
        var selector = new ChildSelector(1, new ExpandedName("urn:mock:props", "entry"), attribute("key", "tone", 2));
        var location = new Location(attribute("value", "alpha", 2), Optional.of(selector));
        valid(definition, withFields(definition, observed("one", 1), Map.of("tone", new FieldState.Present("alpha", new Proof.Observed(location))), Kind.OBSERVED));
        for (var changed : List.of(new Location(location.value(), Optional.empty()),
                new Location(location.value(), Optional.of(new ChildSelector(3, selector.element(), selector.discriminator()))),
                new Location(location.value(), Optional.of(new ChildSelector(1, selector.element(), attribute("key", "wrong", 2))))))
            refused(definition, withFields(definition, observed("one", 1), Map.of("tone", new FieldState.Present("alpha", new Proof.Observed(changed))), Kind.OBSERVED), "INVALID_PROVENANCE");
    }
    @Test void missingStateIsNotAnAbsentOptionalValueAndUnknownObservedInputRefuses() {
        var definition = definition(false);
        refused(definition, withFields(definition, observed("one", 1), Map.of(), Kind.OBSERVED), "MISSING_FIELD_STATE");
        refused(definition, withFields(definition, observed("one", 1), Map.of("tone", new FieldState.Unresolved()), Kind.OBSERVED), "INVALID_FIELD_STATE");
    }
    @Test void unknownOptionalTargetInputIsIncompleteWithoutAValidatedInput() {
        var definition = definition(false); var ref = new Ref.Target(new TargetIntent.Ref.Fresh("neutral", "item"));
        var input = withFields(definition, ref, Map.of("tone", new FieldState.Unresolved()), Kind.TYPED_TARGET);
        var check = assertInstanceOf(DerivedInputValidator.Incomplete.class, DerivedInputValidator.check(definition, pin(definition), input));
        assertEquals(List.of("by-tone"), check.derivations());
    }
    @Test void targetEnteredAndKeptProofsRetainTheirDistinctAuthorityBoundary() {
        var definition = definition(false); var old = observed("one", 1); var target = new Ref.Target(new TargetIntent.Ref.Existing(old.key()));
        var entered = new Proof.Target(new TargetIntent.FieldValue.Entered("alpha"), Optional.empty());
        valid(definition, withFields(definition, target, Map.of("tone", new FieldState.Present("alpha", entered)), Kind.TYPED_TARGET));
        refused(definition, withFields(definition, target, Map.of("tone", new FieldState.Present("beta", entered)), Kind.TYPED_TARGET), "INVALID_PROVENANCE");
        var kept = new Proof.Target(new TargetIntent.FieldValue.KeepObserved(), Optional.of(new Kept(old, direct("alpha", 1))));
        valid(definition, withFields(definition, target, Map.of("tone", new FieldState.Present("alpha", kept)), Kind.TYPED_TARGET));
        refused(definition, withFields(definition, new Ref.Target(new TargetIntent.Ref.Fresh("neutral", "item")),
                Map.of("tone", new FieldState.Present("alpha", kept)), Kind.TYPED_TARGET), "INVALID_PROVENANCE");
        refused(definition, withFields(definition, target, Map.of("tone", new FieldState.Present("alpha", new Proof.Observed(direct("alpha", 1)))), Kind.TYPED_TARGET), "INVALID_PROVENANCE");
    }
    @Test void duplicatePhysicalKeysOriginsAndForeignEdgesRefuse() {
        var definition = definition(false); var input = observedInput(definition); var first = input.entities().getFirst();
        refused(definition, new DerivedInput(input.kind(), input.pin(), List.of(first, first), List.of()), "INVALID_GRAPH");
        refused(definition, new DerivedInput(input.kind(), input.pin(), List.of(first),
                List.of(new Edge("not-declared", first.reference(), observed("foreign", 2)))), "INVALID_GRAPH");
        refused(definition, new DerivedInput(Kind.TYPED_TARGET, input.pin(), input.entities(), List.of()), "INVALID_GRAPH");
    }
    @Test void forgedDefinitionOrMechanismMetadataCannotAuthorizeComputation() {
        var original = definition(false); var forged = new NativeCompilationResult.Checked(original.definition(), original.logicalDigest(), original.bindingDigests(), Map.of());
        refused(forged, observedInput(original), "INVALID_DEFINITION");
        var logical = original.definition().logical(); var type = logical.entityTypes().getFirst(); var secret = new Field("tone", ValueType.TEXT, false, Classification.STRUCTURAL, Sensitivity.SECRET, true, true);
        var changedLogical = new NativeDefinition.Logical(List.of(new EntityType(type.id(), type.label(), List.of(type.fields().getFirst(), secret), type.identity())),
                logical.relations(), logical.rules(), logical.operationCapabilities(), logical.computedTypes(), logical.derivations(), logical.cooccurrences(), logical.computedRules());
        var changed = new NativeDefinition(original.definition().id(), original.definition().revision(), changedLogical, original.definition().bindings());
        refused(new NativeCompilationResult.Checked(changed, original.logicalDigest(), original.bindingDigests(), original.mechanisms()), observedInput(original), "INVALID_DEFINITION");
    }
    @Test void inputCopiesCollectionsAndValueProofsRenderSafely() {
        var definition = definition(false); var entities = new ArrayList<>(observedInput(definition).entities());
        var input = new DerivedInput(Kind.OBSERVED, pin(definition), entities, List.of()); entities.clear();
        assertEquals(1, input.entities().size());
        assertThrows(UnsupportedOperationException.class, () -> input.entities().getFirst().fields().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.pin().documentDigests().clear());
        assertFalse(input.toString().contains("alpha")); assertFalse(input.entities().getFirst().toString().contains("alpha"));
        assertFalse(input.entities().getFirst().fields().get("tone").toString().contains("alpha"));
    }
    @Test void expectedPinsMustAlsoNameTheSelectedDefinitionAndCompleteInventory() {
        var definition = definition(false); var original = observedInput(definition); var pin = pin(definition);
        for (var invalid : List.of(new Pin("", pin.logicalDigest(), pin.bindingId(), pin.bindingDigest(), pin.documentDigests()),
                new Pin("r", pin.logicalDigest(), pin.bindingId(), pin.bindingDigest(), Map.of()),
                new Pin("r", pin.logicalDigest(), pin.bindingId(), pin.bindingDigest(), Map.of("sheet", "not-a-digest")),
                new Pin("r", "b".repeat(64), pin.bindingId(), pin.bindingDigest(), pin.documentDigests()),
                new Pin("r", pin.logicalDigest(), "unknown", pin.bindingDigest(), pin.documentDigests()))) {
            var input = new DerivedInput(original.kind(), invalid, original.entities(), original.edges());
            assertEquals("INVALID_PIN", assertInstanceOf(DerivedInputValidator.Refused.class,
                    DerivedInputValidator.check(definition, invalid, input)).code());
        }
    }
    @Test void impossibleSpansAndMalformedUnicodeAreRefusedBeforeGrouping() {
        var definition = definition(false); var ref = observed("one", 1);
        var impossible = new AttributePin("sheet", SOURCE, 1, new ExpandedName("", "tone"), "tone", "alpha", 1_048_577, 1_048_582, '"');
        refused(definition, withFields(definition, ref, Map.of("tone", new FieldState.Present("alpha",
                new Proof.Observed(new Location(impossible, Optional.empty())))), Kind.OBSERVED), "INVALID_PROVENANCE");
        for (var value : List.of("bad" + (char) 0xd800, "bad" + (char) 0, "bad" + (char) 0xfffe))
            refused(definition, withFields(definition, ref, Map.of("tone", new FieldState.Present(value,
                    new Proof.Observed(direct(value, 1)))), Kind.OBSERVED), "INVALID_FIELD_STATE");
    }
    @Test void exactOccurrenceAndKeyUniquenessAndLegalFreshSlotsAreRequired() {
        var definition = definition(false); var input = observedInput(definition); var first = input.entities().getFirst();
        for (var duplicate : List.of(observed("one", 2), observed("two", 1)))
            refused(definition, new DerivedInput(Kind.OBSERVED, pin(definition), List.of(first,
                    new Entity(duplicate, Map.of("tone", new FieldState.Absent()))), List.of()), "INVALID_GRAPH");
        refused(definition, withFields(definition, new Ref.Target(new TargetIntent.Ref.Fresh("not a slot", "item")),
                Map.of("tone", new FieldState.Absent()), Kind.TYPED_TARGET), "INVALID_GRAPH");
        var wrongOrigin = new Ref.Observed(new ObservedGraph.Key("item", "one"), new ObservedGraph.Origin("sheet", "items", SOURCE, 3, List.of(2)));
        refused(definition, withFields(definition, wrongOrigin, Map.of("tone", new FieldState.Absent()), Kind.OBSERVED), "INVALID_PROVENANCE");
    }
    @Test void unknownTargetDoesNotHideAnInvalidKnownContributor() {
        var definition = definition(false);
        var unknown = new Entity(new Ref.Target(new TargetIntent.Ref.Fresh("a", "item")), Map.of("tone", new FieldState.Unresolved()));
        var invalid = new Entity(new Ref.Target(new TargetIntent.Ref.Fresh("b", "item")), Map.of("tone", new FieldState.Present("alpha",
                new Proof.Target(new TargetIntent.FieldValue.Entered("beta"), Optional.empty()))));
        refused(definition, new DerivedInput(Kind.TYPED_TARGET, pin(definition), List.of(unknown, invalid), List.of()), "INVALID_PROVENANCE");
    }
    @Test void requiredSourceAbsenceDoesNotBecomeAnEmptyComputedPartition() {
        var before = definition(false).definition(); var old = before.logical(); var type = old.entityTypes().getFirst(); var tone = type.fields().getLast();
        var required = new Field(tone.id(), tone.valueType(), true, tone.classification(), tone.sensitivity(), tone.readable(), tone.editable());
        var logical = new NativeDefinition.Logical(List.of(new EntityType(type.id(), type.label(), List.of(type.fields().getFirst(), required), type.identity())),
                old.relations(), old.rules(), old.operationCapabilities(), old.computedTypes(), old.derivations(), old.cooccurrences(), old.computedRules());
        var definition = assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(
                new NativeDefinition(before.id(), before.revision(), logical, before.bindings()))).checked();
        refused(definition, withFields(definition, observed("one", 1), Map.of("tone", new FieldState.Absent()), Kind.OBSERVED), "REQUIRED_FIELD_MISSING");
    }
    static DerivedInput withFields(NativeCompilationResult.Checked definition, Ref reference, Map<String, FieldState> fields, Kind kind) {
        return new DerivedInput(kind, pin(definition), List.of(new Entity(reference, fields)), List.of());
    }
}
