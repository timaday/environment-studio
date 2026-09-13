package studio.environment.core.derived;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.NativeDefinition.ExpandedName;
import studio.environment.core.definitionv3.NativeCompilationResult.Checked;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;

class DerivedTargetComparisonTest {
    record Fixture(Checked definition, DerivedInput typed, DerivedInput actual,
            Map<TargetIntent.Ref, DerivedInput.Ref.Observed> mapping) { }
    static Fixture fixture() {
        var d = DerivedGraphEngineTest.definition();
        var pin = DerivedGraphEngineTest.pin(d);
        var finalPin = new DerivedInput.Pin(pin.revisionToken(), pin.logicalDigest(), pin.bindingId(), pin.bindingDigest(), Map.of("sheet", "b".repeat(64)));
        var old = new TargetIntent.Ref.Existing(new ObservedGraph.Key("item", "old"));
        var fresh = new TargetIntent.Ref.Fresh("fresh", "item");
        var renamed = new DerivedInput.Ref.Observed(new ObservedGraph.Key("item", "renamed"), new ObservedGraph.Origin("sheet", "items", "b".repeat(64), 4, List.of(0)));
        var reused = new DerivedInput.Ref.Observed(new ObservedGraph.Key("item", "old"), new ObservedGraph.Origin("sheet", "items", "b".repeat(64), 2, List.of(0)));
        var typed = new DerivedInput(DerivedInput.Kind.TYPED_TARGET, pin, List.of(
                new DerivedInput.Entity(new DerivedInput.Ref.Target(old), Map.of("tone", DerivedGraphEngineTest.state("alpha"), "finish", DerivedGraphEngineTest.state("x"))),
                new DerivedInput.Entity(new DerivedInput.Ref.Target(fresh), Map.of("tone", DerivedGraphEngineTest.state("alpha"), "finish", DerivedGraphEngineTest.state("y")))), List.of());
        var actual = new DerivedInput(DerivedInput.Kind.OBSERVED, finalPin, List.of(
                new DerivedInput.Entity(reused, Map.of("tone", observed(finalPin, reused, "tone", "alpha", 30), "finish", observed(finalPin, reused, "finish", "y", 45))),
                new DerivedInput.Entity(renamed, Map.of("tone", observed(finalPin, renamed, "tone", "alpha", 90), "finish", observed(finalPin, renamed, "finish", "x", 105)))), List.of());
        return new Fixture(d, typed, actual, Map.of(old, renamed, fresh, reused));
    }
    static DerivedInput.FieldState observed(DerivedInput.Pin pin, DerivedInput.Ref.Observed ref, String field, String value, int start) {
        return new DerivedInput.FieldState.Present(value, new DerivedInput.Proof.Observed(new DerivedInput.Location(
                new DerivedInput.AttributePin("sheet", pin.documentDigests().get("sheet"), ref.origin().elementIndex(), new ExpandedName("", field), field, value, start, start + value.length(), '\''), Optional.empty())));
    }
    static DerivedResult.Complete evaluate(Fixture f, DerivedInput input) {
        return assertInstanceOf(DerivedResult.Complete.class, new DerivedGraphEngine().evaluate(f.definition(), input.pin(), input, () -> false));
    }
    static DerivedTargetComparison.Result compare(Fixture f) {
        return new DerivedTargetComparison().compare(f.definition(), f.typed().pin(), f.typed(), evaluate(f, f.typed()),
                f.actual().pin(), f.actual(), evaluate(f, f.actual()), f.mapping(), () -> false);
    }
    @Test void originalIdentityAndFreshLiteralReuseMapToReorderedActualOccurrences() {
        var f = fixture();
        assertEquals(List.of("alpha:x", "alpha:y"), evaluate(f, f.actual()).graph().cooccurrences().stream()
                .map(e -> e.source().value() + ":" + e.target().value()).toList());
        assertEquals(new DerivedTargetComparison.Matched(), compare(f));
    }
    @Test void sameCountsCannotHideSwappedMappings() {
        var f = fixture(); var keys = new ArrayList<>(f.mapping().keySet());
        var swapped = Map.of(keys.get(0), f.mapping().get(keys.get(1)), keys.get(1), f.mapping().get(keys.get(0)));
        assertEquals(new DerivedTargetComparison.Refused("TARGET_MISMATCH"), compare(new Fixture(f.definition(), f.typed(), f.actual(), swapped)));
    }
    @Test void keptOriginalSpanCanDifferFromFinalSpanWithoutLosingOrigin() {
        var f = fixture(); var first = f.typed().entities().getFirst();
        var old = ((TargetIntent.Ref.Existing)((DerivedInput.Ref.Target)first.reference()).reference()).key();
        var ref = new DerivedInput.Ref.Observed(old, new ObservedGraph.Origin("sheet", "items", "a".repeat(64), 1, List.of(0)));
        var original = (DerivedInput.FieldState.Present)observed(f.typed().pin(), ref, "tone", "alpha", 8);
        var kept = new DerivedInput.FieldState.Present("alpha", new DerivedInput.Proof.Target(new TargetIntent.FieldValue.KeepObserved(),
                Optional.of(new DerivedInput.Kept(ref, ((DerivedInput.Proof.Observed)original.proof()).location()))));
        var fields = new HashMap<>(first.fields()); fields.put("tone", kept);
        var typed = new DerivedInput(f.typed().kind(), f.typed().pin(), List.of(new DerivedInput.Entity(first.reference(), fields), f.typed().entities().getLast()), List.of());
        assertEquals(new DerivedTargetComparison.Matched(), compare(new Fixture(f.definition(), typed, f.actual(), f.mapping())));
    }
    @Test void missingExtraAndDuplicateMappingRefuse() {
        var f = fixture(); var map = new HashMap<>(f.mapping()); map.remove(map.keySet().iterator().next());
        assertEquals(new DerivedTargetComparison.Refused("INVALID_MAPPING"), compare(new Fixture(f.definition(), f.typed(), f.actual(), map)));
        map = new HashMap<>(f.mapping()); map.put(new TargetIntent.Ref.Fresh("extra", "item"), map.values().iterator().next());
        assertEquals(new DerivedTargetComparison.Refused("INVALID_MAPPING"), compare(new Fixture(f.definition(), f.typed(), f.actual(), map)));
        map = new HashMap<>(f.mapping()); var value = map.values().iterator().next(); map.replaceAll((k,v) -> value);
        assertEquals(new DerivedTargetComparison.Refused("INVALID_MAPPING"), compare(new Fixture(f.definition(), f.typed(), f.actual(), map)));
    }
    @Test void canonicalResultsRejectMissingDuplicatedAndSwappedRoleEvidence() {
        var f = fixture(); var before = evaluate(f, f.typed()); var after = evaluate(f, f.actual()); var graph = after.graph();
        var missing = new ComputedGraph(graph.pin(), graph.nodes().subList(1, graph.nodes().size()), graph.memberships(), graph.cooccurrences());
        var duplicate = new ArrayList<>(graph.nodes()); duplicate.add(graph.nodes().getFirst());
        var duplicated = new ComputedGraph(graph.pin(), duplicate, graph.memberships(), graph.cooccurrences());
        var edge = graph.cooccurrences().getFirst(); var c = edge.contributors().getFirst();
        var reversed = new ComputedGraph.Contributor(c.physical(), List.of(c.roles().getLast(), c.roles().getFirst()));
        var pairs = new ArrayList<>(graph.cooccurrences());
        pairs.set(0, new ComputedGraph.Cooccurrence(edge.relation(), edge.source(), edge.target(), List.of(reversed)));
        var swapped = new ComputedGraph(graph.pin(), graph.nodes(), graph.memberships(), pairs);
        for (var forged : List.of(missing, duplicated, swapped)) {
            assertEquals(new DerivedTargetComparison.Refused("INVALID_RESULT"), new DerivedTargetComparison().compare(
                    f.definition(), f.typed().pin(), f.typed(), before, f.actual().pin(), f.actual(),
                    new DerivedResult.Complete(forged, after.rules()), f.mapping(), () -> false));
        }
        assertEquals(new DerivedTargetComparison.Refused("INVALID_RESULT"), new DerivedTargetComparison().compare(
                f.definition(), f.typed().pin(), f.typed(), new DerivedResult.Complete(before.graph(), List.of()),
                f.actual().pin(), f.actual(), after, f.mapping(), () -> false));
    }
    @Test void expectedPinsAndSameDecisionRevisionAreIndependentGates() {
        var f = fixture(); var a = evaluate(f, f.typed()); var b = evaluate(f, f.actual());
        var stale = new DerivedInput.Pin("another", f.actual().pin().logicalDigest(), f.actual().pin().bindingId(), f.actual().pin().bindingDigest(), f.actual().pin().documentDigests());
        var actual = new DerivedInput(f.actual().kind(), stale, f.actual().entities(), f.actual().edges());
        assertEquals(new DerivedTargetComparison.Refused("STALE_INPUT"), new DerivedTargetComparison().compare(
                f.definition(), f.typed().pin(), f.typed(), a, stale, actual, evaluate(f, actual), f.mapping(), () -> false));
        assertEquals(new DerivedTargetComparison.Refused("STALE_INPUT"), new DerivedTargetComparison().compare(
                f.definition(), stale, f.typed(), a, f.actual().pin(), f.actual(), b, f.mapping(), () -> false));
    }
    @Test void unknownNeverBecomesAbsenceAndKnownAbsenceMustMatch() {
        var f = fixture(); var entities = new ArrayList<>(f.typed().entities()); var first = entities.getFirst();
        for (var state : List.of(new DerivedInput.FieldState.Unresolved(), new DerivedInput.FieldState.Absent())) {
            var fields = new HashMap<>(first.fields()); fields.put("tone", state);
            entities.set(0, new DerivedInput.Entity(first.reference(), fields));
            var typed = new DerivedInput(f.typed().kind(), f.typed().pin(), entities, List.of());
            var supplied = state instanceof DerivedInput.FieldState.Unresolved ? evaluate(f, f.typed()) : evaluate(f, typed);
            assertEquals(new DerivedTargetComparison.Refused(state instanceof DerivedInput.FieldState.Unresolved ? "INCOMPLETE_INPUT" : "TARGET_MISMATCH"),
                    new DerivedTargetComparison().compare(f.definition(), typed.pin(), typed, supplied, f.actual().pin(), f.actual(),
                            evaluate(f, f.actual()), f.mapping(), () -> false));
        }
    }
    @Test void failedRulesAreRefusedDespiteExactMatchingGraphs() {
        var f = fixture(); var d = f.definition().definition(); var l = d.logical();
        var logical = new studio.environment.core.definitionv3.NativeDefinition.Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                l.computedTypes(), l.derivations(), l.cooccurrences(), List.of(new studio.environment.core.definitionv2.NativeDefinition.CountRule(
                        "required", "tones", java.math.BigInteger.TEN, java.math.BigInteger.TEN)));
        var checked = assertInstanceOf(studio.environment.core.definitionv3.NativeCompilationResult.Incomplete.class,
                new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(new studio.environment.core.definitionv3.NativeDefinition(d.id(), d.revision(), logical, d.bindings()))).checked();
        var p = f.typed().pin(); var q = f.actual().pin();
        var tp = new DerivedInput.Pin(p.revisionToken(), checked.logicalDigest(), p.bindingId(), checked.bindingDigests().get(p.bindingId()), p.documentDigests());
        var fp = new DerivedInput.Pin(q.revisionToken(), checked.logicalDigest(), q.bindingId(), checked.bindingDigests().get(q.bindingId()), q.documentDigests());
        assertEquals(new DerivedTargetComparison.Refused("DERIVED_RULE_FAILED"), compare(new Fixture(checked,
                new DerivedInput(f.typed().kind(), tp, f.typed().entities(), List.of()), new DerivedInput(f.actual().kind(), fp, f.actual().entities(), List.of()), f.mapping())));
    }
    @Test void cancellationAtLastCheckAndNullArgumentsNeverYieldMatch() {
        var f = fixture(); var a = evaluate(f, f.typed()); var b = evaluate(f, f.actual()); var calls = new java.util.concurrent.atomic.AtomicInteger();
        var comparator = new DerivedTargetComparison();
        assertEquals(new DerivedTargetComparison.Matched(), comparator.compare(f.definition(), f.typed().pin(), f.typed(), a, f.actual().pin(), f.actual(), b,
                f.mapping(), () -> { calls.incrementAndGet(); return false; }));
        var next = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(new DerivedTargetComparison.Refused("CANCELLED"), comparator.compare(f.definition(), f.typed().pin(), f.typed(), a, f.actual().pin(), f.actual(), b,
                f.mapping(), () -> next.incrementAndGet() >= calls.get()));
        assertEquals(new DerivedTargetComparison.Refused("INVALID_INPUT"), comparator.compare(null, f.typed().pin(), f.typed(), a, f.actual().pin(), f.actual(), b, f.mapping(), () -> false));
    }
    @Test void completeTwentyThousandPhysicalMappingIsBoundedBeforeOverAllocation() {
        var f = fixture();
        var typed = new ArrayList<DerivedInput.Entity>();
        var actual = new ArrayList<DerivedInput.Entity>();
        var mapping = new HashMap<TargetIntent.Ref, DerivedInput.Ref.Observed>();
        Map<String, DerivedInput.FieldState> absent = Map.of("tone", new DerivedInput.FieldState.Absent(), "finish", new DerivedInput.FieldState.Absent());
        for (int i = 0; i < 20_000; i++) {
            var ref = new TargetIntent.Ref.Fresh("slot-" + i, "item");
            var observed = new DerivedInput.Ref.Observed(new ObservedGraph.Key("item", "identity-" + i),
                    new ObservedGraph.Origin("sheet", "items", "b".repeat(64), i + 1, List.of(0)));
            typed.add(new DerivedInput.Entity(new DerivedInput.Ref.Target(ref), absent));
            actual.add(new DerivedInput.Entity(observed, absent));
            mapping.put(ref, observed);
        }
        var full = new Fixture(f.definition(), new DerivedInput(f.typed().kind(), f.typed().pin(), typed, List.of()),
                new DerivedInput(f.actual().kind(), f.actual().pin(), actual, List.of()), mapping);
        assertEquals(new DerivedTargetComparison.Matched(), compare(full));
        mapping.put(new TargetIntent.Ref.Fresh("extra", "item"), mapping.values().iterator().next());
        assertEquals(new DerivedTargetComparison.Refused("RESOURCE_LIMIT"), compare(full));
    }
    @Test void oversizedForgedResultIsRefusedBeforeCanonicalAllocation() {
        var f = fixture(); var before = evaluate(f, f.typed()); var after = evaluate(f, f.actual());
        var graph = after.graph();
        var oversized = new ComputedGraph(graph.pin(), Collections.nCopies(20_001, graph.nodes().getFirst()), graph.memberships(), graph.cooccurrences());
        assertEquals(new DerivedTargetComparison.Refused("RESOURCE_LIMIT"), new DerivedTargetComparison().compare(
                f.definition(), f.typed().pin(), f.typed(), before, f.actual().pin(), f.actual(),
                new DerivedResult.Complete(oversized, after.rules()), f.mapping(), () -> false));
    }
    @Test void suppliedGraphPinCannotBeReplacedByExpectedAuthority() {
        var f = fixture(); var before = evaluate(f, f.typed()); var after = evaluate(f, f.actual());
        var g = after.graph();
        var forged = new ComputedGraph(f.typed().pin(), g.nodes(), g.memberships(), g.cooccurrences());
        assertEquals(new DerivedTargetComparison.Refused("INVALID_RESULT"), new DerivedTargetComparison().compare(
                f.definition(), f.typed().pin(), f.typed(), before, f.actual().pin(), f.actual(),
                new DerivedResult.Complete(forged, after.rules()), f.mapping(), () -> false));
    }
}
