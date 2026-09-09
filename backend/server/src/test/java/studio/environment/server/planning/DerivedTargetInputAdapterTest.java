package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.planning.TargetIntent.*;
import studio.environment.server.projection.DerivedGraphProjectionAdapter;
import studio.environment.server.projection.DocumentSource;

/** Independently invented items and values; no application model or database input. */
class DerivedTargetInputAdapterTest {
    static final String XML = "<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x'/>"
            + "<item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>";
    static NativeCompilationResult.Checked definition(boolean child) {
        var fields = List.of(new Field("id", ValueType.TEXT, true, Classification.STRUCTURAL, Sensitivity.PUBLIC, true, true),
                new Field("tone", ValueType.TEXT, false, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true),
                new Field("finish", ValueType.TEXT, false, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true));
        var logical = new NativeDefinition.Logical(List.of(new EntityType("item", "Invented item", fields, new Identity("id"))),
                List.of(), List.of(), List.of(Operation.RETAIN_ENTITY, Operation.BIND_FIELD, Operation.CREATE_ENTITY, Operation.REMOVE_ENTITY),
                List.of(new NativeDefinition.ComputedType("tones", "Tones"), new NativeDefinition.ComputedType("finishes", "Finishes")),
                List.of(new NativeDefinition.Derivation("by-tone", "item", "tone", "tones", "has-tone"),
                        new NativeDefinition.Derivation("by-finish", "item", "finish", "finishes", "has-finish")),
                List.of(new NativeDefinition.Cooccurrence("pair", "by-tone", "by-finish", BigInteger.ZERO, BigInteger.TEN)), List.of());
        FieldLocator locator = child ? new ChildProperty(new ExpandedName("urn:props", "entry"), new ExpandedName("urn:props", "key"),
                "tone", new ExpandedName("urn:props", "value")) : new DirectAttribute(new ExpandedName("", "tone"));
        var projection = new Projection("items", "item", List.of(new ExpandedName("", "items"), new ExpandedName("", "item")),
                List.of(new FieldMapping("id", new ExpandedName("", "id")), new FieldMapping("tone", locator),
                        new FieldMapping("finish", new ExpandedName("", "finish"))), List.of());
        var binding = new Binding("mock-pg", Engine.POSTGRESQL, Storage.TEXT, "mock_schema", "mock_table", "mock_key", "mock_xml", KeyType.INT64,
                List.of(new Document("sheet", "1", List.of(projection))));
        return assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(
                new NativeDefinition("mock-target", BigInteger.ONE, logical, List.of(binding)))).checked();
    }
    static String digest(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static DerivedInput.Pin pin(NativeCompilationResult.Checked definition, String xml, String revision) {
        return new DerivedInput.Pin(revision, definition.logicalDigest(), "mock-pg", definition.bindingDigests().get("mock-pg"), Map.of("sheet", digest(xml)));
    }
    static DerivedGraphProjectionAdapter.Snapshot snapshot(DerivedInput.Pin pin, String xml) {
        return new DerivedGraphProjectionAdapter.Snapshot(pin.revisionToken(), pin.logicalDigest(), pin.bindingId(), pin.bindingDigest(), List.of(new DocumentSource("sheet", xml)));
    }
    static Ref.Existing old(String id) { return new Ref.Existing(new ObservedGraph.Key("item", id)); }
    static EntityDecision.Retain edit(String id, FieldValue identity, FieldValue tone) {
        return new EntityDecision.Retain(old(id), Map.of("id", identity, "tone", tone, "finish", new FieldValue.KeepObserved()), Map.of());
    }
    static TargetIntent intent(EntityDecision... decisions) { return new TargetIntent(List.of(decisions), List.of()); }
    static DerivedTargetInputAdapter.Result prepare(NativeCompilationResult.Checked definition, String xml, TargetIntent intent) {
        var current = pin(definition, xml, "current-1"); var target = pin(definition, xml, "target-2");
        return new DerivedTargetInputAdapter().prepare(definition, current, snapshot(current, xml), target,
                new DerivedTargetInputAdapter.Decisions(target, intent), () -> false);
    }
    @Test void editsRecomputePairsWhileKeepUsesActualOriginalXmlProof() {
        var result = assertInstanceOf(DerivedTargetInputAdapter.Complete.class, prepare(definition(false), XML,
                intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.Entered("beta")))));
        assertEquals(List.of("alpha:x", "alpha:y", "beta:x"), result.current().derived().graph().cooccurrences().stream()
                .map(e -> e.source().value() + ":" + e.target().value()).toList());
        assertEquals(List.of("alpha:y", "beta:x"), result.derived().graph().cooccurrences().stream()
                .map(e -> e.source().value() + ":" + e.target().value()).toList());
        var one = result.input().entities().stream().filter(e -> e.reference().equals(new DerivedInput.Ref.Target(old("one")))).findFirst().orElseThrow();
        var entered = assertInstanceOf(DerivedInput.FieldState.Present.class, one.fields().get("tone"));
        assertEquals(new DerivedInput.Proof.Target(new FieldValue.Entered("beta"), Optional.empty()), entered.proof());
        var kept = assertInstanceOf(DerivedInput.Proof.Target.class, assertInstanceOf(DerivedInput.FieldState.Present.class, one.fields().get("finish")).proof()).kept().orElseThrow();
        assertEquals(old("one").key(), kept.source().key()); assertEquals("x", XML.substring(kept.location().value().valueStart(), kept.location().value().valueEnd()));
        assertEquals("current-1", result.current().input().pin().revisionToken()); assertEquals("target-2", result.input().pin().revisionToken());
    }
    @Test void identityChangeAndFreshReuseKeepDistinctPhysicalReferences() {
        var fresh = new Ref.Fresh("new-item", "item");
        var result = assertInstanceOf(DerivedTargetInputAdapter.Complete.class, prepare(definition(false), XML,
                intent(edit("one", new FieldValue.Entered("renamed"), new FieldValue.KeepObserved()),
                        new EntityDecision.Create(fresh, Map.of("id", new FieldValue.Entered("one"), "tone", new FieldValue.Entered("alpha"), "finish", new FieldValue.Entered("x")), Map.of()))));
        var alpha = result.derived().graph().nodes().stream().filter(n -> n.key().value().equals("alpha")).findFirst().orElseThrow();
        assertEquals(3, alpha.contributors().size());
        assertEquals(List.of(old("one"), old("two"), fresh), alpha.contributors().stream().map(c -> ((DerivedInput.Ref.Target)c.physical()).reference()).toList());
        assertEquals("renamed", result.physical().entities().stream().filter(e -> e.reference().equals(old("one"))).findFirst().orElseThrow().identity().identity());
    }
    @Test void unknownOptionalInputIsIncompleteAndAbsenceCannotBecomeEmpty() {
        assertEquals(new DerivedTargetInputAdapter.Incomplete(List.of("by-tone")), prepare(definition(false), XML,
                intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.Unresolved()))));
        assertInstanceOf(DerivedTargetInputAdapter.Refused.class, prepare(definition(false), XML,
                intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.Entered("")))));
        var missing = "<items><item id='one' finish='x'/></items>";
        var result = assertInstanceOf(DerivedTargetInputAdapter.Complete.class, prepare(definition(false), missing,
                intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.ExplicitlyAbsent()))));
        assertEquals(1, result.derived().graph().nodes().size()); assertTrue(result.derived().graph().cooccurrences().isEmpty());
    }
    @Test void removingLastContributorDropsOnlyItsComputedResults() {
        var result = assertInstanceOf(DerivedTargetInputAdapter.Complete.class, prepare(definition(false), XML,
                intent(new EntityDecision.Remove(old("one")), new EntityDecision.Remove(old("two")))));
        assertEquals(List.of("x", "beta"), result.derived().graph().nodes().stream().map(n -> n.key().value()).toList());
        assertEquals(1, result.derived().graph().cooccurrences().size());
        assertEquals(XML, result.current().sources().documents().getFirst().source());
    }
    @Test void namespacedChildKeepRetainsBothActualSelectorAndValuePins() {
        String xml = "<items xmlns:p='urn:props'><item id='one' finish='x'><p:entry p:key='tone' p:value='al&#112;ha'/></item></items>";
        var result = assertInstanceOf(DerivedTargetInputAdapter.Complete.class, prepare(definition(true), xml, intent()));
        var tone = assertInstanceOf(DerivedInput.FieldState.Present.class, result.input().entities().getFirst().fields().get("tone"));
        var kept = assertInstanceOf(DerivedInput.Proof.Target.class, tone.proof()).kept().orElseThrow();
        assertEquals(1, kept.location().selector().orElseThrow().parentElementIndex());
        assertEquals("al&#112;ha", xml.substring(kept.location().value().valueStart(), kept.location().value().valueEnd()));
    }
    @Test void independentTargetRevisionAndOriginalSourcesAreRequired() {
        var definition = definition(false); var current = pin(definition, XML, "current-1"); var target = pin(definition, XML, "target-2");
        var adapter = new DerivedTargetInputAdapter();
        assertEquals(new DerivedTargetInputAdapter.Refused("STALE_INPUT"), adapter.prepare(definition, current, snapshot(current, XML), target,
                new DerivedTargetInputAdapter.Decisions(pin(definition, XML, "target-old"), intent()), () -> false));
        var changed = pin(definition, XML + " ", "target-2");
        assertEquals(new DerivedTargetInputAdapter.Refused("STALE_INPUT"), adapter.prepare(definition, current, snapshot(current, XML), changed,
                new DerivedTargetInputAdapter.Decisions(changed, intent()), () -> false));
        assertEquals(new DerivedTargetInputAdapter.Refused("STALE_INPUT"), adapter.prepare(definition, current, snapshot(current, XML + " "), target,
                new DerivedTargetInputAdapter.Decisions(target, intent()), () -> false));
    }
    @Test void failedRulesRemainInspectableAndASeparateTargetCanRepairCurrentFailure() {
        var d = definition(false).definition(); var l = d.logical();
        var logical = new NativeDefinition.Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                l.computedTypes(), l.derivations(), List.of(new NativeDefinition.Cooccurrence("pair", "by-tone", "by-finish", BigInteger.ZERO, BigInteger.ONE)), l.computedRules());
        var definition = assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(
                new NativeDefinition(d.id(), d.revision(), logical, d.bindings()))).checked();
        var unchanged = assertInstanceOf(DerivedTargetInputAdapter.Complete.class, prepare(definition, XML, intent()));
        var alpha = unchanged.derived().rules().stream().filter(r -> r.source().orElseThrow().value().equals("alpha")).findFirst().orElseThrow();
        assertEquals(BigInteger.TWO, alpha.actual()); assertEquals(studio.environment.core.Outcome.FAIL, alpha.outcome());
        var repaired = assertInstanceOf(DerivedTargetInputAdapter.Complete.class, prepare(definition, XML, intent(new EntityDecision.Remove(old("two")))));
        assertTrue(repaired.current().derived().rules().stream().anyMatch(r -> r.outcome() == studio.environment.core.Outcome.FAIL));
        assertTrue(repaired.derived().rules().stream().allMatch(r -> r.outcome() == studio.environment.core.Outcome.PASS));
    }
    @Test void allPhysicalReferencesReachTargetInputAndUnknownReferenceIsIncomplete() {
        var d = definition(false).definition(); var l = d.logical(); var b = d.bindings().getFirst(); var p = b.documents().getFirst().entities().getFirst();
        var relation = new studio.environment.core.definition.DefinitionDraft.Relation("link", "item", "item",
                studio.environment.core.definition.DefinitionDraft.RelationKind.REFERENCE, BigInteger.ZERO, BigInteger.ONE, false);
        var projection = new Projection(p.id(), p.type(), p.path(), p.fields(), List.of(new ReferenceMapping("link", new ExpandedName("", "next"))));
        var binding = new Binding(b.id(), b.engine(), b.storage(), b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(), List.of(new Document("sheet", "1", List.of(projection))));
        var logical = new NativeDefinition.Logical(l.entityTypes(), List.of(relation), l.rules(), l.operationCapabilities(), l.computedTypes(), l.derivations(), l.cooccurrences(), l.computedRules());
        var definition = assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(new NativeDefinition(d.id(), d.revision(), logical, List.of(binding)))).checked();
        String xml = "<items><item id='one' tone='alpha' next='two'/><item id='two' tone='beta'/></items>";
        var complete = assertInstanceOf(DerivedTargetInputAdapter.Complete.class, prepare(definition, xml, intent()));
        assertEquals(List.of(new DerivedInput.Edge("link", new DerivedInput.Ref.Target(old("one")), new DerivedInput.Ref.Target(old("two")))), complete.input().edges());
        var decision = new EntityDecision.Retain(old("one"), Map.of("id", new FieldValue.KeepObserved(), "tone", new FieldValue.KeepObserved(), "finish", new FieldValue.KeepObserved()),
                Map.of("link", new ReferenceValue.Unresolved()));
        assertEquals(new DerivedTargetInputAdapter.Incomplete(List.of("link")), prepare(definition, xml, intent(decision)));
    }
    @Test void knownInvalidDecisionAndUnsupportedPresenceChangesRefuseWithoutPartialTarget() {
        assertEquals(new DerivedTargetInputAdapter.Refused("ATTRIBUTE_PRESENCE_UNSUPPORTED"), prepare(definition(false), XML,
                intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.ExplicitlyAbsent()))));
        assertEquals(new DerivedTargetInputAdapter.Refused("RESOURCE_LIMIT"), prepare(definition(false), XML,
                intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.Entered("x".repeat(1_048_577))))));
        assertEquals(new DerivedTargetInputAdapter.Incomplete(List.of("item/id")), prepare(definition(false), XML,
                intent(edit("one", new FieldValue.Unresolved(), new FieldValue.KeepObserved()))));
        var fresh = new Ref.Fresh("new-item", "item");
        assertEquals(new DerivedTargetInputAdapter.Refused("KEEP_OBSERVED_UNAVAILABLE"), prepare(definition(false), XML,
                intent(new EntityDecision.Create(fresh, Map.of("id", new FieldValue.Entered("fresh"), "tone", new FieldValue.KeepObserved(), "finish", new FieldValue.ExplicitlyAbsent()), Map.of()))));
    }
    @Test void cancellationInvalidRevisionAndRedactionApplyAtTargetBoundary() {
        var definition = definition(false); var current = pin(definition, XML, "current-1"); var target = pin(definition, XML, "target-2");
        var adapter = new DerivedTargetInputAdapter(); var decisions = new DerivedTargetInputAdapter.Decisions(target, intent());
        assertEquals(new DerivedTargetInputAdapter.Refused("CANCELLED"), adapter.prepare(definition, current, snapshot(current, XML), target, decisions, () -> true));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertInstanceOf(DerivedTargetInputAdapter.Complete.class, adapter.prepare(definition, current, snapshot(current, XML), target, decisions, () -> { calls.incrementAndGet(); return false; }));
        var actual = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(new DerivedTargetInputAdapter.Refused("CANCELLED"), adapter.prepare(definition, current, snapshot(current, XML), target, decisions, () -> actual.incrementAndGet() >= calls.get()));
        var invalid = pin(definition, XML, "");
        assertEquals(new DerivedTargetInputAdapter.Refused("INVALID_PIN"), adapter.prepare(definition, current, snapshot(current, XML), invalid, new DerivedTargetInputAdapter.Decisions(invalid, intent()), () -> false));
        var complete = assertInstanceOf(DerivedTargetInputAdapter.Complete.class, prepare(definition, XML, intent()));
        assertEquals("DerivedTypedTarget[redacted]", complete.toString()); assertEquals("DerivedTargetDecisions[redacted]", decisions.toString());
        assertThrows(UnsupportedOperationException.class, () -> complete.input().entities().clear());
    }
}
