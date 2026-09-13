package studio.environment.server.projection;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.derived.DerivedInput;

/** Every declaration/source is independently invented; no private model or DB input. */
class DerivedGraphProjectionAdapterTest {
    static final String DIRECT = "<items xmlns='urn:mock'>\r\n<!-- invented -->\r\n"
            + "<item id='one' tone='al&#x70;ha' finish='x'/>\r\n"
            + "<item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/>"
            + "<item id='four' tone='alpha' finish='x'/></items>";
    static final String CHILD = "<m:items xmlns:m=\"urn:mock\" xmlns:p=\"urn:props\"><m:item id=\"one\" finish=\"x\">"
            + "<p:entry p:key=\"tone\" p:value=\"al&#x70;ha\"/></m:item></m:items>";
    private final DerivedGraphProjectionAdapter adapter = new DerivedGraphProjectionAdapter();
    static NativeCompilationResult.Checked definition(boolean child, int documentCount, boolean required) {
        var fields = List.of(new Field("id", ValueType.TEXT, true, Classification.STRUCTURAL, Sensitivity.PUBLIC, true, false),
                new Field("tone", ValueType.TEXT, required, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true),
                new Field("finish", ValueType.TEXT, false, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true));
        var logical = new NativeDefinition.Logical(List.of(new EntityType("item", "Invented item", fields, new Identity("id"))),
                List.of(), List.of(), List.of(Operation.RETAIN_ENTITY),
                List.of(new NativeDefinition.ComputedType("tones", "Tones"), new NativeDefinition.ComputedType("finishes", "Finishes")),
                List.of(new NativeDefinition.Derivation("by-tone", "item", "tone", "tones", "has-tone"),
                        new NativeDefinition.Derivation("by-finish", "item", "finish", "finishes", "has-finish")),
                List.of(new NativeDefinition.Cooccurrence("pair", "by-tone", "by-finish", BigInteger.ZERO, BigInteger.TEN)), List.of());
        FieldLocator locator = child ? new ChildProperty(new ExpandedName("urn:props", "entry"), new ExpandedName("urn:props", "key"),
                "tone", new ExpandedName("urn:props", "value")) : new DirectAttribute(new ExpandedName("", "tone"));
        var projection = new Projection("items", "item", List.of(new ExpandedName("urn:mock", "items"), new ExpandedName("urn:mock", "item")),
                List.of(new FieldMapping("id", new ExpandedName("", "id")), new FieldMapping("tone", locator),
                        new FieldMapping("finish", new ExpandedName("", "finish"))), List.of());
        var documents = new ArrayList<Document>();
        for (int i = 0; i < documentCount; i++) documents.add(new Document("sheet-" + i, Integer.toString(i + 1), List.of(new Projection("items-" + i,
                projection.type(), projection.path(), projection.fields(), projection.references()))));
        var binding = new Binding("mock-pg", Engine.POSTGRESQL, Storage.TEXT, "mock_schema", "mock_table", "mock_key", "mock_xml", KeyType.INT64, documents);
        return assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(
                new NativeDefinition("mock-source", BigInteger.ONE, logical, List.of(binding)))).checked();
    }
    static String digest(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static DerivedInput.Pin pin(NativeCompilationResult.Checked definition, List<DocumentSource> sources) {
        var digests = new TreeMap<String, String>(); sources.forEach(d -> digests.put(d.documentId(), digest(d.source())));
        return new DerivedInput.Pin("observation-1", definition.logicalDigest(), "mock-pg", definition.bindingDigests().get("mock-pg"), digests);
    }
    static DerivedGraphProjectionAdapter.Snapshot snapshot(DerivedInput.Pin pin, List<DocumentSource> sources) {
        return new DerivedGraphProjectionAdapter.Snapshot(pin.revisionToken(), pin.logicalDigest(), pin.bindingId(), pin.bindingDigest(), sources);
    }
    private DerivedGraphProjectionAdapter.Complete project(NativeCompilationResult.Checked definition, String source) {
        var sources = List.of(new DocumentSource("sheet-0", source)); var pin = pin(definition, sources);
        return assertInstanceOf(DerivedGraphProjectionAdapter.Complete.class, adapter.project(definition, pin, snapshot(pin, sources), () -> false));
    }
    private void refused(NativeCompilationResult.Checked definition, String source, String code) {
        var sources = List.of(new DocumentSource("sheet-0", source)); var pin = pin(definition, sources);
        assertEquals(new DerivedGraphProjectionAdapter.Refused(code), adapter.project(definition, pin, snapshot(pin, sources), () -> false));
    }
    @Test void actualDirectXmlProducesCompletePhysicalAndComputedPartitionsWithExactSourceProofs() {
        var result = project(definition(false, 1, false), DIRECT);
        assertEquals(4, result.physical().entities().size());
        assertEquals(List.of("alpha:x", "alpha:y", "beta:x"), result.derived().graph().cooccurrences().stream()
                .map(e -> e.source().value() + ":" + e.target().value()).toList());
        assertEquals(DIRECT, result.sources().documents().getFirst().source());
        assertEquals(digest(DIRECT), result.input().pin().documentDigests().get("sheet-0"));
        var alpha = result.derived().graph().nodes().stream().filter(n -> n.key().value().equals("alpha")).findFirst().orElseThrow();
        assertEquals(List.of("one", "two", "four"), alpha.contributors().stream()
                .map(c -> ((DerivedInput.Ref.Observed)c.physical()).key().identity()).toList());
        var location = ((DerivedInput.Proof.Observed)alpha.contributors().getFirst().roles().getFirst().proof()).location();
        assertEquals("tone", location.value().qualifiedName()); assertEquals("alpha", location.value().decodedValue());
        assertEquals("al&#x70;ha", DIRECT.substring(location.value().valueStart(), location.value().valueEnd()));
        assertEquals(1, location.value().elementIndex()); assertEquals('\'', location.value().quote());
        assertTrue(location.selector().isEmpty());
    }
    @Test void actualChildProofUsesNamespacedValueAndDiscriminatorOnTheSelectedChild() {
        for (String source : List.of(CHILD, CHILD.replace("xmlns:p=", "xmlns:q=").replace("p:", "q:"))) {
            var result = project(definition(true, 1, false), source);
            var tone = result.input().entities().getFirst().fields().get("tone");
            var location = ((DerivedInput.Proof.Observed)((DerivedInput.FieldState.Present)tone).proof()).location();
            assertEquals(new ExpandedName("urn:props", "value"), location.value().name());
            assertEquals("al&#x70;ha", source.substring(location.value().valueStart(), location.value().valueEnd()));
            assertEquals(2, location.value().elementIndex());
            var selector = location.selector().orElseThrow(); assertEquals(1, selector.parentElementIndex());
            assertEquals(new ExpandedName("urn:props", "entry"), selector.element());
            assertEquals(new ExpandedName("urn:props", "key"), selector.discriminator().name());
            assertEquals("tone", source.substring(selector.discriminator().valueStart(), selector.discriminator().valueEnd()));
            assertEquals(2, selector.discriminator().elementIndex());
            assertEquals("alpha", result.derived().graph().cooccurrences().getFirst().source().value());
        }
    }
    @Test void multipleDocumentsRetainAllContributorsAndCanonicalOriginOrder() {
        var definition = definition(false, 2, false);
        var sources = List.of(new DocumentSource("sheet-1", "<items xmlns='urn:mock'><item id='later' tone='alpha' finish='x'/></items>"),
                new DocumentSource("sheet-0", "<items xmlns='urn:mock'><item id='earlier' tone='alpha' finish='x'/></items>"));
        var pin = pin(definition, sources);
        var result = assertInstanceOf(DerivedGraphProjectionAdapter.Complete.class, adapter.project(definition, pin, snapshot(pin, sources), () -> false));
        assertEquals(List.of("sheet-0", "sheet-1"), result.derived().graph().cooccurrences().getFirst().contributors().stream()
                .map(c -> ((DerivedInput.Ref.Observed)c.physical()).origin().documentId()).toList());
        assertEquals(List.of("sheet-0", "sheet-1"), result.sources().documents().stream().map(ProjectionResult.ExactDocument::documentId).toList());
    }
    @Test void optionalMissingNestedAndWrongNamespaceChildrenRemainExplicitlyAbsent() {
        var definition = definition(true, 1, false);
        for (var source : List.of(CHILD.replace("<p:entry", "<m:wrapper><p:entry").replace("/></m:item>", "/></m:wrapper></m:item>"),
                CHILD.replace("urn:props", "urn:wrong"), CHILD.replace(" p:value=\"al&#x70;ha\"", ""))) {
            var result = project(definition, source);
            assertInstanceOf(DerivedInput.FieldState.Absent.class, result.input().entities().getFirst().fields().get("tone"));
            assertTrue(result.derived().graph().cooccurrences().isEmpty());
        }
        refused(definition(true, 1, true), CHILD.replace(" p:value=\"al&#x70;ha\"", ""), "REQUIRED_FIELD_MISSING");
    }
    @Test void ambiguousChildAndEmptyDerivedValueRefuseRatherThanDroppingContributors() {
        refused(definition(true, 1, false), CHILD.replace("/></m:item>", "/><p:entry p:key='tone' p:value='beta'/></m:item>"), "AMBIGUOUS_CHILD_PROPERTY");
        refused(definition(false, 1, false), "<items xmlns='urn:mock'><item id='one' tone=''/></items>", "INVALID_DERIVED_IDENTITY");
        refused(definition(false, 1, false), "<items xmlns='urn:mock'><item id='same' tone='alpha'/><item id='same' tone='beta'/></items>", "DUPLICATE_IDENTITY");
    }
    @Test void independentSnapshotMetadataCannotBeReplacedWithExpectedPins() {
        var definition = definition(false, 1, false); var sources = List.of(new DocumentSource("sheet-0", DIRECT)); var pin = pin(definition, sources);
        var stale = new DerivedGraphProjectionAdapter.Snapshot("observation-older", pin.logicalDigest(), pin.bindingId(), pin.bindingDigest(), sources);
        assertEquals(new DerivedGraphProjectionAdapter.Refused("STALE_INPUT"), adapter.project(definition, pin, stale, () -> false));
        var changed = List.of(new DocumentSource("sheet-0", DIRECT.replace("<!-- invented -->", "<!-- changed -->")));
        assertEquals(new DerivedGraphProjectionAdapter.Refused("STALE_INPUT"), adapter.project(definition, pin, snapshot(pin, changed), () -> false));
    }
    @Test void fullInventoryAndPhysicalValidationPrecedeDerivedSuccess() {
        var definition = definition(false, 1, false); var sources = List.of(new DocumentSource("sheet-0", DIRECT)); var pin = pin(definition, sources);
        for (var incomplete : List.of(List.<DocumentSource>of(), List.of(new DocumentSource("foreign", DIRECT)), List.of(sources.getFirst(), sources.getFirst())))
            assertEquals(new DerivedGraphProjectionAdapter.Refused("INVENTORY_MISMATCH"), adapter.project(definition, pin, snapshot(pin, incomplete), () -> false));
        refused(definition, "<items xmlns='urn:mock'><item tone='alpha'/></items>", "INVALID_IDENTITY");
    }
    @Test void hostileIneligibleDeclarationIsRefusedBeforeMalformedXmlIsParsed() {
        var original = definition(false, 1, false); var d = original.definition(); var l = d.logical(); var type = l.entityTypes().getFirst();
        var fields = new ArrayList<>(type.fields()); var tone = fields.get(1);
        fields.set(1, new Field(tone.id(), tone.valueType(), tone.required(), tone.classification(), Sensitivity.SECRET, true, true));
        var logical = new NativeDefinition.Logical(List.of(new EntityType(type.id(), type.label(), fields, type.identity())),
                l.relations(), l.rules(), l.operationCapabilities(), l.computedTypes(), l.derivations(), l.cooccurrences(), l.computedRules());
        var forged = new NativeCompilationResult.Checked(new NativeDefinition(d.id(), d.revision(), logical, d.bindings()),
                original.logicalDigest(), original.bindingDigests(), original.mechanisms());
        var sources = List.of(new DocumentSource("sheet-0", "not XML")); var pin = pin(original, sources);
        assertEquals(new DerivedGraphProjectionAdapter.Refused("INVALID_DEFINITION"), adapter.project(forged, pin, snapshot(pin, sources), () -> false));
    }
    @Test void cancellationAndSourceBudgetRefuseWithoutPartialPartitions() {
        var definition = definition(false, 1, false); var sources = List.of(new DocumentSource("sheet-0", DIRECT)); var pin = pin(definition, sources);
        assertEquals(new DerivedGraphProjectionAdapter.Refused("CANCELLED"), adapter.project(definition, pin, snapshot(pin, sources), () -> true));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(new DerivedGraphProjectionAdapter.Refused("CANCELLED"), adapter.project(definition, pin, snapshot(pin, sources), () -> calls.incrementAndGet() > 4));
        refused(definition, "x".repeat(1_048_577), "RESOURCE_LIMIT");
    }
    @Test void retainedSourceAndResultWrappersAreImmutableAndRedacted() {
        var definition = definition(false, 1, false); var sources = new ArrayList<>(List.of(new DocumentSource("sheet-0", DIRECT)));
        var pin = pin(definition, sources); var snapshot = snapshot(pin, sources); sources.clear();
        assertEquals(1, snapshot.documents().size()); assertFalse(snapshot.toString().contains("alpha"));
        var result = assertInstanceOf(DerivedGraphProjectionAdapter.Complete.class, adapter.project(definition, pin, snapshot, () -> false));
        assertThrows(UnsupportedOperationException.class, () -> result.sources().documents().clear());
        assertFalse(result.toString().contains("alpha"));
    }
    @Test void actualPhysicalReferencesAreValidatedAndIncludedInTheSharedInput() {
        var original = definition(false, 1, false).definition(); var l = original.logical(); var b = original.bindings().getFirst();
        var p = b.documents().getFirst().entities().getFirst();
        var relation = new studio.environment.core.definition.DefinitionDraft.Relation("link", "item", "item",
                studio.environment.core.definition.DefinitionDraft.RelationKind.REFERENCE, BigInteger.ZERO, BigInteger.ONE, false);
        var projection = new Projection(p.id(), p.type(), p.path(), p.fields(), List.of(new ReferenceMapping("link", new ExpandedName("", "next"))));
        var binding = new Binding(b.id(), b.engine(), b.storage(), b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(),
                List.of(new Document("sheet-0", "1", List.of(projection))));
        var logical = new NativeDefinition.Logical(l.entityTypes(), List.of(relation), l.rules(), l.operationCapabilities(),
                l.computedTypes(), l.derivations(), l.cooccurrences(), l.computedRules());
        var definition = assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(
                new NativeDefinition(original.id(), original.revision(), logical, List.of(binding)))).checked();
        var source = "<items xmlns='urn:mock'><item id='one' tone='alpha' next='two'/><item id='two' tone='beta'/></items>";
        var result = project(definition, source);
        assertEquals(1, result.input().edges().size());
        var edge = result.input().edges().getFirst();
        assertEquals("link", edge.relation());
        assertEquals("one", ((DerivedInput.Ref.Observed)edge.source()).key().identity());
        assertEquals("two", ((DerivedInput.Ref.Observed)edge.target()).key().identity());
        assertEquals(2, result.derived().graph().memberships().size());
        refused(definition, source.replace("next='two'", "next='missing'"), "UNRESOLVED_REFERENCE");
    }
    @Test void computedRuleFailureRemainsVisibleInACompleteObservedPartition() {
        var original = definition(false, 1, false).definition(); var l = original.logical();
        var relation = new NativeDefinition.Cooccurrence("pair", "by-tone", "by-finish", BigInteger.ZERO, BigInteger.ONE);
        var logical = new NativeDefinition.Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(),
                l.computedTypes(), l.derivations(), List.of(relation), l.computedRules());
        var definition = assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeDefinitionCompiler().compile(
                new NativeDefinition(original.id(), original.revision(), logical, original.bindings()))).checked();
        var result = project(definition, DIRECT);
        var alpha = result.derived().rules().stream().filter(r -> r.source().orElseThrow().value().equals("alpha")).findFirst().orElseThrow();
        assertEquals(BigInteger.TWO, alpha.actual());
        assertEquals(studio.environment.core.Outcome.FAIL, alpha.outcome());
    }
    @Test void cancellationAtTheLastObservedPollDiscardsTheEntireResult() {
        var definition = definition(false, 1, false); var sources = List.of(new DocumentSource("sheet-0", DIRECT)); var pin = pin(definition, sources);
        var baselineCalls = new java.util.concurrent.atomic.AtomicInteger();
        assertInstanceOf(DerivedGraphProjectionAdapter.Complete.class, adapter.project(definition, pin, snapshot(pin, sources),
                () -> { baselineCalls.incrementAndGet(); return false; }));
        var faultCalls = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(new DerivedGraphProjectionAdapter.Refused("CANCELLED"), adapter.project(definition, pin, snapshot(pin, sources),
                () -> faultCalls.incrementAndGet() >= baselineCalls.get()));
    }

}
