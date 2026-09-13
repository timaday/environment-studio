package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft;
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

/** Independently invented two-document parent/child-property XML, with literal expected output. */
class IndependentDerivedMaterializationTest {
    static final String LEFT = "<world><items id='left' xmlns:p='urn:props'><!-- keep \uD800\uDC00 -->\r\n"
            + "<item id='one' finish='x'><p:entry p:key='tone' p:value='al&#112;ha'/><extra>opaque</extra></item></items></world>";
    static final String RIGHT = "<world><items id='right' xmlns:p='urn:props'><item id='two' finish='y'><p:entry p:key='tone' p:value='beta'/></item></items></world>";
    static NativeCompilationResult.Checked model() {
        var d = definition(true).definition(); var l = d.logical(); var b = d.bindings().getFirst(); var item = b.documents().getFirst().entities().getFirst();
        var bucket = new EntityType("bucket", "Invented bucket", List.of(new Field("id", DefinitionDraft.ValueType.TEXT, true,
                DefinitionDraft.Classification.STRUCTURAL, DefinitionDraft.Sensitivity.PUBLIC, true, true)), new Identity("id"));
        var contains = new DefinitionDraft.Relation("contains", "bucket", "item", DefinitionDraft.RelationKind.CONTAINMENT, BigInteger.ZERO, BigInteger.TEN, false);
        var logical = new NativeDefinition.Logical(List.of(l.entityTypes().getFirst(), bucket), List.of(contains), l.rules(),
                List.of(Operation.RETAIN_ENTITY, Operation.BIND_FIELD, Operation.CREATE_ENTITY, Operation.REMOVE_ENTITY, Operation.MOVE_RELATION),
                l.computedTypes(), l.derivations(), l.cooccurrences(), l.computedRules());
        var documents = new ArrayList<Document>();
        for (String id : List.of("left", "right")) documents.add(new Document(id, id.equals("left") ? "1" : "2", List.of(
                new Projection("bucket-" + id, "bucket", List.of(new ExpandedName("", "world"), new ExpandedName("", "items")), List.of(new FieldMapping("id", new ExpandedName("", "id"))), List.of()),
                new Projection("items-" + id, "item", List.of(new ExpandedName("", "world"), new ExpandedName("", "items"), new ExpandedName("", "item")), item.fields(), List.of()))));
        var binding = new Binding(b.id(), b.engine(), b.storage(), b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(), documents);
        var compiled = assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(new NativeDefinition(d.id(), d.revision(), logical, List.of(binding))));
        assertTrue(compiled.diagnostics().stream().allMatch(x -> x.code().equals("MECHANISM_UNQUALIFIED")));
        return compiled.checked();
    }
    static DerivedInput.Pin pin(NativeCompilationResult.Checked d, String revision) {
        return new DerivedInput.Pin(revision, d.logicalDigest(), "mock-pg", d.bindingDigests().get("mock-pg"), Map.of("left", digest(LEFT), "right", digest(RIGHT)));
    }
    static DerivedTargetMaterializer.Result run(TargetIntent intent, List<TargetPlacement> placements, BooleanSupplier cancelled) {
        return run(model(), intent, placements, cancelled);
    }
    static DerivedTargetMaterializer.Result run(NativeCompilationResult.Checked d, TargetIntent intent,
            List<TargetPlacement> placements, BooleanSupplier cancelled) {
        var current = pin(d, "current"); var target = pin(d, "target");
        var result = new DerivedTargetMaterializer().materialize(d, current,
                new DerivedGraphProjectionAdapter.Snapshot(current.revisionToken(), current.logicalDigest(), current.bindingId(), current.bindingDigest(),
                        List.of(new DocumentSource("left", LEFT), new DocumentSource("right", RIGHT))), target,
                new DerivedTargetInputAdapter.Decisions(target, intent), Map.of("left", Optional.empty(), "right", Optional.empty()), placements, cancelled);
        return result;
    }
    static TargetIntent move() {
        return new TargetIntent(List.of(edit("one", new FieldValue.KeepObserved(), new FieldValue.KeepObserved())),
                List.of(new Containment("contains", new Ref.Existing(new ObservedGraph.Key("bucket", "right")), old("one"))));
    }
    static TargetPlacement placement(String digest) {
        return new TargetPlacement(old("one"), "right", "items-right", new TargetPlacement.Parent.Existing("right", digest, 1));
    }
    @Test void childPropertyMoveRetainsOriginalProofAndIndependentlyPinsFinalOccurrence() {
        var result = assertInstanceOf(DerivedTargetMaterializer.Complete.class, run(move(), List.of(placement(digest(RIGHT))), () -> false));
        String expectedLeft = "<world><items id='left' xmlns:p='urn:props'><!-- keep \uD800\uDC00 -->\r\n</items></world>";
        String expectedRight = "<world><items id='right' xmlns:p='urn:props'><item id='two' finish='y'><p:entry p:key='tone' p:value='beta'/></item>"
                + "<item id='one' finish='x' xmlns=\"\" xmlns:p=\"urn:props\"><p:entry p:key='tone' p:value='al&#112;ha'/><extra>opaque</extra></item></items></world>";
        assertEquals(List.of(expectedLeft, expectedRight), result.physical().documents().stream().map(TargetSource::source).toList());
        var actual = result.provenance().get(old("one"));
        assertEquals("right", actual.origin().documentId()); assertEquals("items-right", actual.origin().projectionId()); assertEquals(4, actual.origin().elementIndex());
        var before = result.preliminary().input().entities().stream().filter(e -> e.reference().equals(new DerivedInput.Ref.Target(old("one")))).findFirst().orElseThrow();
        var kept = ((DerivedInput.Proof.Target)((DerivedInput.FieldState.Present)before.fields().get("tone")).proof()).kept().orElseThrow();
        assertEquals("left", kept.source().origin().documentId()); assertEquals(2, kept.source().origin().elementIndex());
        var entity = result.finalProjection().input().entities().stream().filter(e -> e.reference().equals(actual)).findFirst().orElseThrow();
        var proof = (DerivedInput.Proof.Observed)((DerivedInput.FieldState.Present)entity.fields().get("tone")).proof();
        assertEquals(4, proof.location().selector().orElseThrow().parentElementIndex());
        assertEquals(5, proof.location().value().elementIndex());
        assertEquals("al&#112;ha", expectedRight.substring(proof.location().value().valueStart(), proof.location().value().valueEnd()));
        assertEquals(digest(expectedRight), proof.location().value().sourceDigest());
        assertEquals(List.of("alpha:x", "beta:y"), result.finalProjection().derived().graph().cooccurrences().stream()
                .map(e -> e.source().value() + ":" + e.target().value()).toList());
        assertThrows(UnsupportedOperationException.class, () -> result.provenance().clear());
    }
    @Test void removingLastContributorDropsOnlyItsExactSubtreeAndDerivedPartition() {
        var result = assertInstanceOf(DerivedTargetMaterializer.Complete.class,
                run(intent(new EntityDecision.Remove(old("one"))), List.of(), () -> false));
        assertEquals(List.of("<world><items id='left' xmlns:p='urn:props'><!-- keep \uD800\uDC00 -->\r\n</items></world>", RIGHT),
                result.physical().documents().stream().map(TargetSource::source).toList());
        assertEquals(List.of("y", "beta"), result.finalProjection().derived().graph().nodes().stream().map(n -> n.key().value()).toList());
        assertFalse(result.provenance().containsKey(old("one")));
    }
    @Test void unknownAndStaleParentCannotYieldAnyMaterializedResult() {
        assertEquals(new DerivedTargetMaterializer.Incomplete(List.of("by-tone")),
                run(intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.Unresolved())), List.of(), () -> false));
        assertEquals(new DerivedTargetMaterializer.Refused("STALE_PARENT"), run(move(), List.of(placement("0".repeat(64))), () -> false));
    }
    @Test void lastCancellationAfterActualRecomputationDiscardsOutput() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertInstanceOf(DerivedTargetMaterializer.Complete.class, run(move(), List.of(placement(digest(RIGHT))), () -> { calls.incrementAndGet(); return false; }));
        var next = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(new DerivedTargetMaterializer.Refused("CANCELLED"), run(move(), List.of(placement(digest(RIGHT))), () -> next.incrementAndGet() >= calls.get()));
    }
    @Test void failedTargetRuleCannotBecomeSuccessfulMaterialization() {
        var d = model().definition(); var l = d.logical();
        var logical = new NativeDefinition.Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                l.computedTypes(), l.derivations(), List.of(new NativeDefinition.Cooccurrence("pair", "by-tone", "by-finish", BigInteger.TWO, BigInteger.TWO)), l.computedRules());
        var checked = assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new NativeDefinitionCompiler().compile(new NativeDefinition(d.id(), d.revision(), logical, d.bindings()))).checked();
        assertEquals(new DerivedTargetMaterializer.Refused("DERIVED_RULE_FAILED"), run(checked, intent(), List.of(), () -> false));
    }
}
