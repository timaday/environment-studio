package studio.environment.core.derived;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.planning.TargetIntent;

class DerivedGraphEngineTest {
    private final DerivedGraphEngine engine = new DerivedGraphEngine();
    static NativeCompilationResult.Checked definition() {
        var fields = List.of(new Field("id", ValueType.TEXT, true, Classification.STRUCTURAL, Sensitivity.PUBLIC, true, true),
                new Field("tone", ValueType.TEXT, false, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true),
                new Field("finish", ValueType.TEXT, false, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true));
        var logical = new NativeDefinition.Logical(List.of(new EntityType("item", "Invented item", fields, new Identity("id"))),
                List.of(), List.of(), List.of(Operation.RETAIN_ENTITY),
                List.of(new NativeDefinition.ComputedType("tones", "Tones"), new NativeDefinition.ComputedType("finishes", "Finishes")),
                List.of(new NativeDefinition.Derivation("by-tone", "item", "tone", "tones", "has-tone"),
                        new NativeDefinition.Derivation("by-finish", "item", "finish", "finishes", "has-finish")),
                List.of(new NativeDefinition.Cooccurrence("pair", "by-tone", "by-finish", BigInteger.ZERO, BigInteger.TEN)),
                List.of(new CountRule("tone-count", "tones", BigInteger.ZERO, BigInteger.TEN)));
        var projection = new Projection("items", "item", List.of(new ExpandedName("urn:mock", "items"), new ExpandedName("urn:mock", "item")),
                List.of(new FieldMapping("id", new ExpandedName("", "id")), new FieldMapping("tone", new ExpandedName("", "tone")),
                        new FieldMapping("finish", new ExpandedName("", "finish"))), List.of());
        var binding = new Binding("mock-pg", Engine.POSTGRESQL, Storage.TEXT, "mock_schema", "mock_table", "mock_key", "mock_xml",
                KeyType.INT64, List.of(new Document("sheet", "1", List.of(projection))));
        return assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(new NativeDefinition("mock-derived", BigInteger.ONE, logical, List.of(binding)))).checked();
    }
    static DerivedInput.Pin pin(NativeCompilationResult.Checked definition) {
        return new DerivedInput.Pin("invented-revision", definition.logicalDigest(), "mock-pg", definition.bindingDigests().get("mock-pg"), Map.of("sheet", "a".repeat(64)));
    }
    static DerivedInput.FieldState state(String value) {
        return value == null ? new DerivedInput.FieldState.Absent() : new DerivedInput.FieldState.Present(value,
                new DerivedInput.Proof.Target(new TargetIntent.FieldValue.Entered(value), Optional.empty()));
    }
    static DerivedInput input(NativeCompilationResult.Checked definition, String[][] pairs) {
        var entities = new ArrayList<DerivedInput.Entity>();
        for (int i = 0; i < pairs.length; i++) entities.add(new DerivedInput.Entity(new DerivedInput.Ref.Target(new TargetIntent.Ref.Fresh("slot-" + i, "item")),
                Map.of("tone", state(pairs[i][0]), "finish", state(pairs[i][1]))));
        return new DerivedInput(DerivedInput.Kind.TYPED_TARGET, pin(definition), entities, List.of());
    }
    private DerivedResult.Complete complete(NativeCompilationResult.Checked definition, String[][] pairs) {
        return assertInstanceOf(DerivedResult.Complete.class, engine.evaluate(definition, pin(definition), input(definition, pairs), () -> false));
    }
    @Test void sameOccurrencePairsHaveExactGroupsEdgesAndAllContributors() {
        var graph = complete(definition(), new String[][] {{"alpha", "x"}, {"alpha", "y"}, {"beta", "x"}, {"alpha", "x"}}).graph();
        assertEquals(Set.of("alpha", "beta", "x", "y"), new HashSet<>(graph.nodes().stream().map(n -> n.key().value()).toList()));
        assertEquals(8, graph.memberships().size());
        assertEquals(Set.of("alpha:x", "alpha:y", "beta:x"), new HashSet<>(graph.cooccurrences().stream().map(e -> e.source().value() + ":" + e.target().value()).toList()));
        var pair = graph.cooccurrences().stream().filter(e -> e.source().value().equals("alpha") && e.target().value().equals("x")).findFirst().orElseThrow();
        assertEquals(2, pair.contributors().size());
        assertEquals(List.of("tone", "finish"), pair.contributors().getFirst().roles().stream().map(ComputedGraph.FieldRole::field).toList());
    }
    @Test void lastContributorRemovalAndOptionalAbsenceAreExact() {
        assertEquals(1, complete(definition(), new String[][] {{"alpha", "x"}, {"alpha", "x"}}).graph().cooccurrences().size());
        assertEquals(1, complete(definition(), new String[][] {{"alpha", "x"}}).graph().cooccurrences().size());
        assertTrue(complete(definition(), new String[][] {{"alpha", null}, {null, "x"}}).graph().cooccurrences().isEmpty());
        assertTrue(complete(definition(), new String[0][]).graph().nodes().isEmpty());
    }
    private static NativeCompilationResult.Checked withRules(NativeCompilationResult.Checked definition,
            List<NativeDefinition.Cooccurrence> pairs, List<CountRule> rules) {
        var d = definition.definition(); var l = d.logical();
        var changed = new NativeDefinition(d.id(), d.revision(), new NativeDefinition.Logical(l.entityTypes(), l.relations(), l.rules(),
                l.operationCapabilities(), l.computedTypes(), l.derivations(), pairs, rules), d.bindings());
        return assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(changed)).checked();
    }
    @Test void selfEdgesRetainBothOrderedFieldRolesAndDistinctOutgoingTargets() {
        var definition = withRules(definition(), List.of(new NativeDefinition.Cooccurrence("self", "by-tone", "by-tone", BigInteger.ONE, BigInteger.ONE)), List.of());
        var result = complete(definition, new String[][] {{"alpha", null}, {"alpha", null}});
        var edge = result.graph().cooccurrences().getFirst();
        assertEquals(edge.source(), edge.target());
        assertEquals(2, edge.contributors().size());
        assertEquals(List.of("tone", "tone"), edge.contributors().getFirst().roles().stream().map(ComputedGraph.FieldRole::field).toList());
        assertEquals(BigInteger.ONE, result.rules().getFirst().actual());
        assertEquals(studio.environment.core.Outcome.PASS, result.rules().getFirst().outcome());
    }
    @Test void scalarUnicodeOrderingIsExactAndIndependentOfInputOrder() {
        var definition = definition();
        String[][] values = {{"𐀀", null}, {"é", null}, {"alpha", null}, {"\uE000", null}, {"e\u0301", null}, {"Alpha", null}, {" ", null}};
        var input = input(definition, values);
        var result = assertInstanceOf(DerivedResult.Complete.class, engine.evaluate(definition, pin(definition), input, () -> false));
        assertEquals(List.of(" ", "Alpha", "alpha", "e\u0301", "é", "\uE000", "𐀀"), result.graph().nodes().stream().map(n -> n.key().value()).toList());
        var reversed = new ArrayList<>(input.entities()); Collections.reverse(reversed);
        assertEquals(result, engine.evaluate(definition, pin(definition), new DerivedInput(input.kind(), input.pin(), reversed, input.edges()), () -> false));
    }
    @Test void emptyInputCountMinimumAndZeroOutgoingObligationAreNotConfused() {
        var definition = withRules(definition(), List.of(new NativeDefinition.Cooccurrence("pair", "by-tone", "by-finish", BigInteger.ONE, BigInteger.TEN)),
                List.of(new CountRule("required-tone", "tones", BigInteger.ONE, BigInteger.TEN)));
        var empty = complete(definition, new String[0][]);
        assertEquals(1, empty.rules().size());
        assertEquals(BigInteger.ZERO, empty.rules().getFirst().actual());
        assertEquals(studio.environment.core.Outcome.FAIL, empty.rules().getFirst().outcome());
        var zero = complete(definition, new String[][] {{"alpha", null}});
        var pair = zero.rules().stream().filter(r -> r.declaration().equals("pair")).findFirst().orElseThrow();
        assertEquals(BigInteger.ZERO, pair.actual());
        assertEquals(studio.environment.core.Outcome.FAIL, pair.outcome());
    }
    @Test void optionalUnknownAndBadPinsNeverYieldACompleteGraph() {
        var definition = definition(); var seed = input(definition, new String[][] {{"alpha", null}});
        var entity = seed.entities().getFirst();
        var unknown = new DerivedInput(seed.kind(), seed.pin(), List.of(new DerivedInput.Entity(entity.reference(),
                Map.of("tone", new DerivedInput.FieldState.Unresolved(), "finish", state(null)))), List.of());
        assertInstanceOf(DerivedResult.Incomplete.class, engine.evaluate(definition, pin(definition), unknown, () -> false));
        var stale = new DerivedInput.Pin("stale-revision", seed.pin().logicalDigest(), "mock-pg", seed.pin().bindingDigest(), seed.pin().documentDigests());
        assertInstanceOf(DerivedResult.Refused.class, engine.evaluate(definition, stale, seed, () -> false));
        for (String bad : List.of("", "\uD800")) {
            assertInstanceOf(DerivedResult.Refused.class, engine.evaluate(definition, pin(definition), input(definition, new String[][] {{bad, null}}), () -> false));
        }
    }
    @Test void cancellationAndImmutableRedactedResultsPreserveNoPartialSuccess() {
        var definition = definition(); var input = input(definition, new String[][] {{"invented-value-canary", "x"}});
        assertEquals(new DerivedResult.Refused("CANCELLED"), engine.evaluate(definition, pin(definition), input, () -> true));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(new DerivedResult.Refused("CANCELLED"), engine.evaluate(definition, pin(definition), input, () -> calls.incrementAndGet() > 3));
        var result = assertInstanceOf(DerivedResult.Complete.class, engine.evaluate(definition, pin(definition), input, () -> false));
        assertThrows(UnsupportedOperationException.class, () -> result.graph().nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.graph().nodes().getFirst().contributors().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.graph().cooccurrences().getFirst().contributors().getFirst().roles().clear());
        for (Object object : List.of(result, result.graph(), result.graph().nodes(), result.graph().memberships(), result.graph().cooccurrences(), result.rules())) {
            assertFalse(object.toString().contains("invented-value-canary"));
        }
    }
    private void resourceRefusal(NativeCompilationResult.Checked definition, DerivedInput input) {
        assertEquals(new DerivedResult.Refused("RESOURCE_LIMIT"), engine.evaluate(definition, pin(definition), input, () -> false));
    }
    @Test void sharedNodeBudgetCountsEveryPhysicalEntityBeforeDerivedNodes() {
        var definition = definition();
        var values = new String[19999][2]; values[0][0] = "alpha";
        assertEquals(1, complete(definition, values).graph().nodes().size());
        var over = new String[20000][2]; over[0][0] = "alpha";
        resourceRefusal(definition, input(definition, over));
    }
    @Test void identityBudgetCountsStrictUtf8PerDistinctTupleAtExactAndOneOver() {
        var definition = definition();
        var values = new String[8192][2];
        for (int i = 0; i < values.length; i++) values[i][0] = "\uE000".repeat(339) + String.format(java.util.Locale.ROOT, "%07d", i);
        assertEquals(8192, complete(definition, values).graph().nodes().size());
        values[0][0] += "x";
        resourceRefusal(definition, input(definition, values));
    }
    private static NativeCompilationResult.Checked linkDefinition() {
        var definition = definition(); var d = definition.definition(); var l = d.logical();
        var types = new ArrayList<>(l.computedTypes());
        types.add(new NativeDefinition.ComputedType("tones-two", "Other tones"));
        types.add(new NativeDefinition.ComputedType("finishes-two", "Other finishes"));
        var derivations = new ArrayList<>(l.derivations());
        derivations.add(new NativeDefinition.Derivation("by-tone-two", "item", "tone", "tones-two", "has-tone-two"));
        derivations.add(new NativeDefinition.Derivation("by-finish-two", "item", "finish", "finishes-two", "has-finish-two"));
        var pairs = List.of(new NativeDefinition.Cooccurrence("pair", "by-tone", "by-finish", BigInteger.ZERO, BigInteger.TEN),
                new NativeDefinition.Cooccurrence("pair-two", "by-tone-two", "by-finish-two", BigInteger.ZERO, BigInteger.TEN),
                new NativeDefinition.Cooccurrence("self", "by-tone", "by-tone", BigInteger.ZERO, BigInteger.ONE));
        var model = new NativeDefinition(d.id(), d.revision(), new NativeDefinition.Logical(l.entityTypes(), l.relations(), l.rules(),
                l.operationCapabilities(), types, derivations, pairs, l.computedRules()), d.bindings());
        return assertInstanceOf(NativeCompilationResult.Incomplete.class, new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(model)).checked();
    }
    @Test void contributorBudgetChargesEveryNodeAndEdgeAssociationDespiteDeduplication() {
        var definition = linkDefinition();
        var exact = new String[9092][2];
        for (int i = 0; i < 9090; i++) { exact[i][0] = "alpha"; exact[i][1] = "x"; }
        exact[9090][0] = "alpha"; exact[9091][0] = "alpha";
        var graph = complete(definition, exact).graph();
        long links = graph.nodes().stream().mapToLong(n -> n.contributors().size()).sum()
                + graph.memberships().stream().mapToLong(e -> e.contributors().size()).sum()
                + graph.cooccurrences().stream().mapToLong(e -> e.contributors().size()).sum();
        assertEquals(100000, links); // 9090*11 + 2*5; same-field pair keeps two roles but charges one association.
        var over = new String[9091][2];
        for (var row : over) { row[0] = "alpha"; row[1] = "x"; }
        resourceRefusal(definition, input(definition, over)); // 9091*11 = 100001.
    }
    private static NativeCompilationResult.Checked physicalEdgesDefinition() {
        var d = definition().definition(); var l = d.logical(); var b = d.bindings().getFirst();
        var p = b.documents().getFirst().entities().getFirst();
        var relations = new ArrayList<studio.environment.core.definition.DefinitionDraft.Relation>();
        var references = new ArrayList<ReferenceMapping>();
        for (int i = 0; i < 5; i++) {
            relations.add(new studio.environment.core.definition.DefinitionDraft.Relation("edge-" + i, "item", "item",
                    studio.environment.core.definition.DefinitionDraft.RelationKind.REFERENCE, BigInteger.ZERO, BigInteger.ONE, false));
            references.add(new ReferenceMapping("edge-" + i, new ExpandedName("", "ref" + i)));
        }
        var projection = new Projection(p.id(), p.type(), p.path(), p.fields(), references);
        var binding = new Binding(b.id(), b.engine(), b.storage(), b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(),
                List.of(new Document("sheet", "1", List.of(projection))));
        return assertInstanceOf(NativeCompilationResult.Incomplete.class, new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(
                new NativeDefinition(d.id(), d.revision(), new NativeDefinition.Logical(l.entityTypes(), relations, l.rules(), l.operationCapabilities(),
                        l.computedTypes(), l.derivations(), l.cooccurrences(), l.computedRules()), List.of(binding)))).checked();
    }
    @Test void actualPhysicalEdgeInventorySharesTheDerivedEdgeLimit() {
        var definition = physicalEdgesDefinition(); var values = new String[10000][2]; values[0][0] = "alpha";
        var seed = input(definition, values); var edges = new ArrayList<DerivedInput.Edge>();
        for (var entity : seed.entities()) for (int i = 0; i < 5; i++) {
            edges.add(new DerivedInput.Edge("edge-" + i, entity.reference(), seed.entities().getFirst().reference()));
        }
        var last = edges.removeLast();
        var exact = new DerivedInput(seed.kind(), seed.pin(), seed.entities(), edges);
        assertEquals(1, assertInstanceOf(DerivedResult.Complete.class, engine.evaluate(definition, pin(definition), exact, () -> false)).graph().memberships().size());
        edges.add(last);
        resourceRefusal(definition, new DerivedInput(seed.kind(), seed.pin(), seed.entities(), edges));
    }
    private static DerivedInput.Location location(int element, String value) {
        return new DerivedInput.Location(new DerivedInput.AttributePin("sheet", "a".repeat(64), element,
                new ExpandedName("", "tone"), "tone", value, element * 20, element * 20 + value.length(), '\''), Optional.empty());
    }
    @Test void observedContributorsRetainExactPinsAndSortByActualOrigin() {
        var definition = definition(); var entities = new ArrayList<DerivedInput.Entity>();
        for (int index : List.of(3, 1, 2)) {
            var ref = new DerivedInput.Ref.Observed(new studio.environment.core.graph.ObservedGraph.Key("item", "id-" + index),
                    new studio.environment.core.graph.ObservedGraph.Origin("sheet", "items", "a".repeat(64), index, List.of(0)));
            entities.add(new DerivedInput.Entity(ref, Map.of("tone", new DerivedInput.FieldState.Present("alpha",
                    new DerivedInput.Proof.Observed(location(index, "alpha"))), "finish", state(null))));
        }
        var input = new DerivedInput(DerivedInput.Kind.OBSERVED, pin(definition), entities, List.of());
        var graph = assertInstanceOf(DerivedResult.Complete.class, engine.evaluate(definition, pin(definition), input, () -> false)).graph();
        var contributors = graph.nodes().getFirst().contributors();
        assertEquals(List.of(1, 2, 3), contributors.stream().map(c -> ((DerivedInput.Ref.Observed)c.physical()).origin().elementIndex()).toList());
        for (int i = 0; i < 3; i++) {
            assertEquals(new DerivedInput.Proof.Observed(location(i + 1, "alpha")), contributors.get(i).roles().getFirst().proof());
        }
    }
    @Test void typedExistingAndFreshKeepSeparateIdentityAndProofWithoutFakeOrigins() {
        var definition = definition(); var original = new studio.environment.core.graph.ObservedGraph.Key("item", "𐀀");
        var origin = new studio.environment.core.graph.ObservedGraph.Origin("sheet", "items", "a".repeat(64), 1, List.of(0));
        var proof = new DerivedInput.Proof.Target(new TargetIntent.FieldValue.KeepObserved(),
                Optional.of(new DerivedInput.Kept(new DerivedInput.Ref.Observed(original, origin), location(1, "alpha"))));
        var existing = new DerivedInput.Entity(new DerivedInput.Ref.Target(new TargetIntent.Ref.Existing(original)),
                Map.of("tone", new DerivedInput.FieldState.Present("alpha", proof), "finish", state(null)));
        var fresh = input(definition, new String[][] {{"alpha", null}}).entities().getFirst();
        var earlier = new DerivedInput.Entity(new DerivedInput.Ref.Target(new TargetIntent.Ref.Existing(
                new studio.environment.core.graph.ObservedGraph.Key("item", "\uE000"))), Map.of("tone", state("alpha"), "finish", state(null)));
        var input = new DerivedInput(DerivedInput.Kind.TYPED_TARGET, pin(definition), List.of(fresh, existing, earlier), List.of());
        var contributors = assertInstanceOf(DerivedResult.Complete.class, engine.evaluate(definition, pin(definition), input, () -> false))
                .graph().nodes().getFirst().contributors();
        assertEquals(earlier.reference(), contributors.getFirst().physical());
        assertEquals(existing.reference(), contributors.get(1).physical());
        assertEquals(proof, contributors.get(1).roles().getFirst().proof());
        assertEquals(fresh.reference(), contributors.getLast().physical());
        assertTrue(((DerivedInput.Proof.Target)contributors.getLast().roles().getFirst().proof()).kept().isEmpty());
    }
    @Test void observedChildValueRetainsSeparateEntityAndSelectorProvenance() {
        var d = definition().definition(); var b = d.bindings().getFirst(); var p = b.documents().getFirst().entities().getFirst();
        var child = new ChildProperty(new ExpandedName("urn:mock:properties", "entry"), new ExpandedName("", "key"), "tone", new ExpandedName("", "value"));
        var fields = new ArrayList<>(p.fields()); fields.set(1, new FieldMapping("tone", child));
        var projection = new Projection(p.id(), p.type(), p.path(), fields, p.references());
        var binding = new Binding(b.id(), b.engine(), b.storage(), b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(),
                List.of(new Document("sheet", "1", List.of(projection))));
        var definition = assertInstanceOf(NativeCompilationResult.Incomplete.class, new studio.environment.core.definitionv3.NativeDefinitionCompiler()
                .compile(new NativeDefinition(d.id(), d.revision(), d.logical(), List.of(binding)))).checked();
        var origin = new studio.environment.core.graph.ObservedGraph.Origin("sheet", "items", "a".repeat(64), 1, List.of(0));
        var ref = new DerivedInput.Ref.Observed(new studio.environment.core.graph.ObservedGraph.Key("item", "one"), origin);
        var value = new DerivedInput.AttributePin("sheet", "a".repeat(64), 2, child.valueAttribute(), "value", "alpha", 90, 95, '\'');
        var discriminator = new DerivedInput.AttributePin("sheet", "a".repeat(64), 2, child.discriminatorAttribute(), "key", "tone", 80, 84, '\'');
        var selector = new DerivedInput.ChildSelector(1, child.element(), discriminator);
        var proof = new DerivedInput.Proof.Observed(new DerivedInput.Location(value, Optional.of(selector)));
        var input = new DerivedInput(DerivedInput.Kind.OBSERVED, pin(definition), List.of(new DerivedInput.Entity(ref,
                Map.of("tone", new DerivedInput.FieldState.Present("alpha", proof), "finish", state(null)))), List.of());
        var graph = assertInstanceOf(DerivedResult.Complete.class, engine.evaluate(definition, pin(definition), input, () -> false)).graph();
        var contributor = graph.nodes().getFirst().contributors().getFirst();
        assertEquals(ref, contributor.physical());
        assertEquals(proof, contributor.roles().getFirst().proof());
        assertEquals(1, ((DerivedInput.Ref.Observed) contributor.physical()).origin().elementIndex());
        assertEquals(2, ((DerivedInput.Proof.Observed) contributor.roles().getFirst().proof()).location().value().elementIndex());
    }
}
