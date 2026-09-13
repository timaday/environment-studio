package studio.environment.core.derived;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.derived.DerivedInput.*;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.planning.TargetIntent;

/** Independent mock evidence for consistency only; these pins do not establish XML facts. */
class DerivedRetainedOriginTest {
    static NativeCompilationResult.Checked definition(boolean child) {
        var d = DerivedInputValidatorTest.definition(child).definition(); var l = d.logical();
        var type = l.entityTypes().getFirst(); var b = d.bindings().getFirst(); var p = b.documents().getFirst().entities().getFirst();
        var fields = List.of(type.fields().getFirst(), type.fields().getLast(),
                new Field("finish", ValueType.TEXT, false, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true));
        FieldLocator locator = child ? new ChildProperty(new ExpandedName("urn:mock:props", "entry"), new ExpandedName("", "key"), "finish", new ExpandedName("", "value"))
                : new DirectAttribute(new ExpandedName("", "finish"));
        var projection = new Projection(p.id(), p.type(), p.path(), List.of(p.fields().getFirst(), p.fields().getLast(), new FieldMapping("finish", locator)), List.of());
        var logical = new NativeDefinition.Logical(List.of(new EntityType(type.id(), type.label(), fields, type.identity())), l.relations(), l.rules(), l.operationCapabilities(),
                List.of(l.computedTypes().getFirst(), new NativeDefinition.ComputedType("finishes", "Finishes")),
                List.of(l.derivations().getFirst(), new NativeDefinition.Derivation("by-finish", "item", "finish", "finishes", "has-finish")),
                List.of(new NativeDefinition.Cooccurrence("pair", "by-tone", "by-finish", BigInteger.ZERO, BigInteger.TEN)), List.of());
        var binding = new Binding(b.id(), b.engine(), b.storage(), b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(), List.of(new Document("sheet", "1", List.of(projection))));
        return assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(new NativeDefinition(d.id(), d.revision(), logical, List.of(binding)))).checked();
    }
    static Location location(String field, String value, int entity, int attribute, boolean child) {
        var pin = DerivedInputValidatorTest.attribute(child ? "value" : field, value, attribute);
        return new Location(pin, child ? Optional.of(new ChildSelector(entity, new ExpandedName("urn:mock:props", "entry"),
                DerivedInputValidatorTest.attribute("key", field, attribute))) : Optional.empty());
    }
    static FieldState kept(String identity, String field, String value, int entity, int attribute, boolean child) {
        return new FieldState.Present(value, new Proof.Target(new TargetIntent.FieldValue.KeepObserved(),
                Optional.of(new Kept(DerivedInputValidatorTest.observed(identity, entity), location(field, value, entity, attribute, child)))));
    }
    static Entity entity(String identity, FieldState tone, FieldState finish) {
        return new Entity(new Ref.Target(new TargetIntent.Ref.Existing(DerivedInputValidatorTest.observed(identity, 1).key())), Map.of("tone", tone, "finish", finish));
    }
    static DerivedInput input(NativeCompilationResult.Checked definition, Entity... entities) {
        return new DerivedInput(Kind.TYPED_TARGET, DerivedInputValidatorTest.pin(definition), List.of(entities), List.of());
    }
    private DerivedResult evaluate(NativeCompilationResult.Checked definition, Entity... entities) {
        return new DerivedGraphEngine().evaluate(definition, DerivedInputValidatorTest.pin(definition), input(definition, entities), () -> false);
    }
    @Test void keptFieldsFromDifferentOccurrencesCannotInventOneCooccurrence() {
        var definition = definition(false);
        var spliced = entity("one", kept("one", "tone", "alpha", 1, 1, false), kept("one", "finish", "beta", 2, 2, false));
        assertEquals(new DerivedResult.Refused("INVALID_PROVENANCE"), evaluate(definition, spliced));
    }
    @Test void distinctExistingIdentitiesCannotClaimOneOriginalPhysicalOccurrence() {
        var definition = definition(false);
        var first = entity("one", kept("one", "tone", "alpha", 1, 1, false), new FieldState.Absent());
        var second = entity("two", new FieldState.Absent(), kept("two", "finish", "beta", 1, 1, false));
        assertEquals(new DerivedResult.Refused("INVALID_PROVENANCE"), evaluate(definition, first, second));
        assertEquals(new DerivedResult.Refused("INVALID_PROVENANCE"), evaluate(definition, second, first));
    }
    @Test void consistentOriginAndWrongIdentityControlsRemainDistinct() {
        var definition = definition(false);
        var consistent = entity("one", kept("one", "tone", "alpha", 1, 1, false), kept("one", "finish", "beta", 1, 1, false));
        var complete = assertInstanceOf(DerivedResult.Complete.class, evaluate(definition, consistent));
        assertEquals(1, complete.graph().cooccurrences().size());
        var pair = complete.graph().cooccurrences().getFirst(); assertEquals("alpha", pair.source().value()); assertEquals("beta", pair.target().value());
        assertEquals(List.of("tone", "finish"), pair.contributors().getFirst().roles().stream().map(ComputedGraph.FieldRole::field).toList());
        assertEquals(new DerivedResult.Refused("INVALID_PROVENANCE"), evaluate(definition,
                entity("one", kept("one", "tone", "alpha", 1, 1, false), kept("other", "finish", "beta", 1, 1, false))));
    }
    @Test void distinctChildLocationsWithinOneOriginRemainLegitimate() {
        var definition = definition(true);
        var tone = kept("one", "tone", "alpha", 1, 2, true); var finish = kept("one", "finish", "beta", 1, 3, true);
        var complete = assertInstanceOf(DerivedResult.Complete.class, evaluate(definition, entity("one", tone, finish)));
        assertEquals(1, complete.graph().cooccurrences().size());
        assertEquals(new DerivedResult.Refused("INVALID_PROVENANCE"), evaluate(definition,
                entity("one", tone, kept("one", "finish", "beta", 4, 5, true))));
    }
    @Test void enteredAndFreshFieldsHaveNoInventedOriginalOrigin() {
        var definition = definition(false);
        var entered = new FieldState.Present("beta", new Proof.Target(new TargetIntent.FieldValue.Entered("beta"), Optional.empty()));
        var existing = entity("one", kept("one", "tone", "alpha", 7, 7, false), entered);
        var fresh = new Entity(new Ref.Target(new TargetIntent.Ref.Fresh("new-item", "item")), Map.of("tone", new FieldState.Absent(), "finish", entered));
        var complete = assertInstanceOf(DerivedResult.Complete.class, evaluate(definition, existing, fresh));
        assertEquals(1, complete.graph().cooccurrences().size());
        assertEquals(3, complete.graph().memberships().size());
    }
    @Test void separateOriginalOccurrencesMayContributeEqualValues() {
        var definition = definition(false);
        var first = entity("one", kept("one", "tone", "alpha", 1, 1, false), kept("one", "finish", "beta", 1, 1, false));
        var second = entity("two", kept("two", "tone", "alpha", 2, 2, false), kept("two", "finish", "beta", 2, 2, false));
        var complete = assertInstanceOf(DerivedResult.Complete.class, evaluate(definition, first, second));
        assertEquals(1, complete.graph().cooccurrences().size()); assertEquals(2, complete.graph().cooccurrences().getFirst().contributors().size());
    }
}
