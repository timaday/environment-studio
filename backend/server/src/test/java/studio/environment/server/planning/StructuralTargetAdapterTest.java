package studio.environment.server.planning;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.definitionv2.NativeDefinitionCompiler;
import studio.environment.core.definition.DefinitionDraft;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.definition.DefinitionBytesCompiler;
import studio.environment.server.definition.NativeDefinitionBytesCompiler;
import studio.environment.server.xml.LosslessXmlAdapter;
import studio.environment.server.xml.XmlResult;
import static org.junit.jupiter.api.Assertions.*;

class StructuralTargetAdapterTest {
    private final StructuralTargetAdapter adapter = new StructuralTargetAdapter();
    private NativeCompilationResult.ReadyToPublish definition() throws Exception {
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionBytesCompiler().compile(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")), DefinitionBytesCompiler.Format.JSON));
    }
    private List<TargetSource> sources() throws Exception {
        return List.of(new TargetSource("glyph-sheet", Files.readString(Path.of("../../fixtures/structural-target/glyphs.xml")), Optional.empty()), new TargetSource("palette-sheet", Files.readString(Path.of("../../fixtures/structural-target/palettes.xml")), Optional.empty()));
    }
    @Test void createsSecondPaletteAndMovesExactlyOneGlyphAcrossBothBindings() throws Exception {
        var fresh = new TargetIntent.Ref.Fresh("new-palette", "palette");
        var glyph = new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph", "alpha"));
        var intent = new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(glyph, Map.of("tag", new TargetIntent.FieldValue.KeepObserved(), "tone", new TargetIntent.FieldValue.KeepObserved()), Map.of("uses", new TargetIntent.ReferenceValue.To(fresh))),
            new TargetIntent.EntityDecision.Create(fresh, Map.of("tag", new TargetIntent.FieldValue.Entered("second-palette"), "shade", new TargetIntent.FieldValue.Entered("cool & \t𐀀")), Map.of())), List.of());
        var sources = sources();
        String digest = assertInstanceOf(XmlResult.Accepted.class, new LosslessXmlAdapter().project(sources.get(1).source())).document().digest();
        var placement = new TargetPlacement(fresh, "palette-sheet", "palettes", new TargetPlacement.Parent.Existing("palette-sheet", digest, 0));
        for (String binding : List.of("mock-pg", "mock-oracle")) {
            var result = assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(definition(), binding, sources, intent, List.of(placement)));
            assertEquals(Files.readString(Path.of("../../fixtures/structural-target/expected-glyphs.xml")), result.documents().get(0).source());
            assertEquals(Files.readString(Path.of("../../fixtures/structural-target/expected-palettes.xml")), result.documents().get(1).source());
            assertEquals(4, result.graph().entities().size());
            assertEquals(List.of("second-palette", "shared"), result.graph().edges().stream().map(e -> e.target().identity()).toList());
            assertEquals("cool & \t𐀀", result.graph().entities().stream().filter(e -> e.key().identity().equals("second-palette")).findFirst().orElseThrow().fields().get("shade"));
        }
    }

    private NativeCompilationResult.ReadyToPublish containmentDefinition() throws Exception {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var declaration = (tools.jackson.databind.node.ObjectNode)json.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")));
        var logical = (tools.jackson.databind.node.ObjectNode)declaration.get("logical");
        var relation = logical.putArray("relations").addObject(); relation.put("id", "contains"); relation.put("fromType", "palette"); relation.put("toType", "glyph"); relation.put("kind", "containment"); relation.put("minimum", 0); relation.put("maximum", 10); relation.put("includeTargetOnReuse", false);
        for (var rule : logical.get("rules")) { ((tools.jackson.databind.node.ObjectNode)rule).put("minimum", 0); ((tools.jackson.databind.node.ObjectNode)rule).put("maximum", 10); }
        for (var binding : declaration.get("bindings")) {
            var old = binding.get("documents"); var glyph = (tools.jackson.databind.node.ObjectNode)old.get(0).get("entities").get(0).deepCopy(); var palette = old.get(1).get("entities").get(0).deepCopy();
            glyph.putArray("references"); var path = glyph.putArray("path"); path.add(palette.get("path").get(0)); path.add(palette.get("path").get(1)); var name = path.addObject(); name.put("namespaceUri", "urn:mock:tiles"); name.put("localName", "glyph");
            String key = old.get(0).get("key").asString(); var document = ((tools.jackson.databind.node.ObjectNode)binding).putArray("documents").addObject(); document.put("id", "tree"); document.put("key", key); document.putArray("entities").add(palette).add(glyph);
        }
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionBytesCompiler().compile(json.writeValueAsBytes(declaration), DefinitionBytesCompiler.Format.JSON));
    }
    private static TargetPlacement.Parent.Existing parent(String source, String identity) {
        var document = assertInstanceOf(XmlResult.Accepted.class, new LosslessXmlAdapter().project(source)).document();
        var selected = document.elements().stream().filter(e -> e.attributes().stream().anyMatch(a -> a.name().namespaceUri().isEmpty() && a.name().localName().equals("id") && a.value().equals(identity))).toList();
        assertEquals(1, selected.size()); return new TargetPlacement.Parent.Existing("tree", document.digest(), selected.getFirst().index());
    }
    @Test void movesCompleteSubtreeWithInnerScalarEditAndPreservesUnmappedContent() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><palette id='p1' shade='warm'>\r\n<!--within--><glyph id='alpha' tone='old'><extra><![CDATA[p:q & <]]></extra><?mock untouched?></glyph><glyph id='beta' tone='keep'/>\r\n</palette><palette id='p2' shade='cool'/></tiles>";
        String expected = "<tiles xmlns='urn:mock:tiles'><palette id='p1' shade='warm'>\r\n<!--within--><glyph id='beta' tone='keep'/>\r\n</palette><palette id='p2' shade='cool'><glyph id='alpha' tone='new&#9;' xmlns=\"urn:mock:tiles\"><extra><![CDATA[p:q & <]]></extra><?mock untouched?></glyph></palette></tiles>";
        var child = new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph", "alpha")); var destination = new TargetIntent.Ref.Existing(new ObservedGraph.Key("palette", "p2"));
        var intent = new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(child, Map.of("tag", new TargetIntent.FieldValue.KeepObserved(), "tone", new TargetIntent.FieldValue.Entered("new\t")), Map.of())), List.of(new TargetIntent.Containment("contains", destination, child)));
        var result = assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(containmentDefinition(), "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())), intent,
            List.of(new TargetPlacement(child, "tree", "glyphs", parent(source, "p2")))));
        assertEquals(expected, result.documents().getFirst().source());
    }

    private static TargetIntent.Ref.Existing existing(String type, String identity) { return new TargetIntent.Ref.Existing(new ObservedGraph.Key(type, identity)); }
    private static TargetIntent.EntityDecision.Retain retainGlyph(TargetIntent.Ref.Existing glyph) {
        return new TargetIntent.EntityDecision.Retain(glyph, Map.of("tag", new TargetIntent.FieldValue.KeepObserved(), "tone", new TargetIntent.FieldValue.KeepObserved()), Map.of());
    }
    @Test void extractsRelocatedChildBeforeRemovingAncestorAndRequiresEveryOtherDescendantRemoval() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><palette id='p1' shade='warm'><glyph id='alpha' tone='old'/><glyph id='beta' tone='keep'/></palette><palette id='p2' shade='cool'/></tiles>";
        var alpha = existing("glyph", "alpha"); var beta = existing("glyph", "beta"); var p1 = existing("palette", "p1"); var p2 = existing("palette", "p2");
        var decisions = List.<TargetIntent.EntityDecision>of(retainGlyph(alpha), new TargetIntent.EntityDecision.Remove(p1), new TargetIntent.EntityDecision.Remove(beta));
        var containment = List.of(new TargetIntent.Containment("contains", p2, alpha));
        var placements = List.of(new TargetPlacement(alpha, "tree", "glyphs", parent(source, "p2")));
        var input = List.of(new TargetSource("tree", source, Optional.empty()));
        var result = assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(containmentDefinition(), "mock-pg", input, new TargetIntent(decisions, containment), placements));
        assertEquals("<tiles xmlns='urn:mock:tiles'><palette id='p2' shade='cool'><glyph id='alpha' tone='old' xmlns=\"urn:mock:tiles\"/></palette></tiles>", result.documents().getFirst().source());
        assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(containmentDefinition(), "mock-pg", input,
            new TargetIntent(List.of(retainGlyph(alpha), new TargetIntent.EntityDecision.Remove(p1)), containment), placements));
    }
    @Test void createsNestedEntitiesAndKeepsGroupedInsertionOrder() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><palette id='old' shade='keep'/></tiles>";
        var palette = new TargetIntent.Ref.Fresh("new-parent", "palette"); var first = new TargetIntent.Ref.Fresh("first", "glyph"); var second = new TargetIntent.Ref.Fresh("second", "glyph");
        var intent = new TargetIntent(List.of(
            new TargetIntent.EntityDecision.Create(palette, Map.of("tag", new TargetIntent.FieldValue.Entered("new"), "shade", new TargetIntent.FieldValue.Entered("fresh")), Map.of()),
            new TargetIntent.EntityDecision.Create(first, Map.of("tag", new TargetIntent.FieldValue.Entered("one"), "tone", new TargetIntent.FieldValue.Entered("A")), Map.of()),
            new TargetIntent.EntityDecision.Create(second, Map.of("tag", new TargetIntent.FieldValue.Entered("two"), "tone", new TargetIntent.FieldValue.Entered("B")), Map.of())),
            List.of(new TargetIntent.Containment("contains", palette, first), new TargetIntent.Containment("contains", palette, second)));
        var doc = assertInstanceOf(XmlResult.Accepted.class, new LosslessXmlAdapter().project(source)).document();
        var placements = List.of(new TargetPlacement(palette, "tree", "palettes", new TargetPlacement.Parent.Existing("tree", doc.digest(), 0)),
            new TargetPlacement(second, "tree", "glyphs", new TargetPlacement.Parent.Created(palette)), new TargetPlacement(first, "tree", "glyphs", new TargetPlacement.Parent.Created(palette)));
        var result = assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(containmentDefinition(), "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())), intent, placements));
        assertEquals("<tiles xmlns='urn:mock:tiles'><palette id='old' shade='keep'/><ns0:palette xmlns:ns0=\"urn:mock:tiles\" id=\"new\" shade=\"fresh\"><ns0:glyph xmlns:ns0=\"urn:mock:tiles\" id=\"two\" tone=\"B\"/><ns0:glyph xmlns:ns0=\"urn:mock:tiles\" id=\"one\" tone=\"A\"/></ns0:palette></tiles>", result.documents().getFirst().source());
    }
    @Test void refusesChangedUnusedNamespaceAndInheritedXmlContexts() throws Exception {
        for (String[] attributes : List.of(new String[]{"xmlns:q='urn:first'", "xmlns:q='urn:second'"}, new String[]{"", "xmlns:q='urn:extra'"},
            new String[]{"xml:lang='en'", ""}, new String[]{"xml:space='preserve'", "xml:space='default'"},
            new String[]{"xml:base='https://one.invalid/'", "xml:base='https://two.invalid/'"})) {
            String source = "<tiles xmlns='urn:mock:tiles'><palette id='p1' shade='warm' " + attributes[0] + "><glyph id='alpha' tone='old'/></palette><palette id='p2' shade='cool' " + attributes[1] + "/></tiles>";
            var alpha = existing("glyph", "alpha"); var p2 = existing("palette", "p2");
            var intent = new TargetIntent(List.of(retainGlyph(alpha)), List.of(new TargetIntent.Containment("contains", p2, alpha)));
            var result = assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(containmentDefinition(), "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())), intent,
                List.of(new TargetPlacement(alpha, "tree", "glyphs", parent(source, "p2")))));
            assertTrue(result.codes().getFirst().startsWith("MOVE_"));
        }
    }
    @Test void refusesUnknownRelativeBaseButAcceptsEqualExplicitDocumentContext() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles' xml:base='relative/'><palette id='p1' shade='warm'><glyph id='alpha' tone='old'/></palette><palette id='p2' shade='cool'/></tiles>";
        var alpha = existing("glyph", "alpha"); var intent = new TargetIntent(List.of(retainGlyph(alpha)), List.of(new TargetIntent.Containment("contains", existing("palette", "p2"), alpha)));
        var placements = List.of(new TargetPlacement(alpha, "tree", "glyphs", parent(source, "p2")));
        assertEquals(List.of("MOVE_BASE_CONTEXT_UNKNOWN"), assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(containmentDefinition(), "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())), intent, placements)).codes());
        assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(containmentDefinition(), "mock-pg", List.of(new TargetSource("tree", source, Optional.of("https://mock.invalid/document.xml"))), intent, placements));
    }
    @Test void noOpPreservesEverySourceAndRenderingOmitsCanaryValues() throws Exception {
        var input = sources(); var result = assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(definition(), "mock-pg", input, new TargetIntent(List.of(), List.of()), List.of()));
        assertEquals(input, result.documents()); assertTrue(result.affectedDocuments().isEmpty()); assertTrue(result.affectedEntities().isEmpty());
        for (Object safe : List.of(result, result.graph(), input.getFirst(), new TargetIntent.FieldValue.Entered("PRIVATE_CANARY"), existing("palette", "PRIVATE_CANARY"))) assertFalse(safe.toString().contains("PRIVATE_CANARY"));
    }

    private NativeCompilationResult.ReadyToPublish groupedDefinition() throws Exception {
        var declaration = containmentDefinition().checked().definition(); var logical = declaration.logical();
        var palette = logical.entityTypes().stream().filter(t -> t.id().equals("palette")).findFirst().orElseThrow();
        var types = new java.util.ArrayList<>(logical.entityTypes()); types.add(new NativeDefinition.EntityType("group", "Group", palette.fields(), palette.identity()));
        var relations = new java.util.ArrayList<>(logical.relations()); relations.add(new DefinitionDraft.Relation("groups", "group", "palette", DefinitionDraft.RelationKind.CONTAINMENT, java.math.BigInteger.ZERO, java.math.BigInteger.TEN, false));
        var bindings = new java.util.ArrayList<NativeDefinition.Binding>();
        for (var binding : declaration.bindings()) {
            var original = binding.documents().getFirst(); var oldPalette = original.entities().getFirst(); var projections = new java.util.ArrayList<NativeDefinition.Projection>();
            var groupName = new NativeDefinition.ExpandedName("urn:mock:tiles", "group");
            projections.add(new NativeDefinition.Projection("groups", "group", List.of(oldPalette.path().getFirst(), groupName), oldPalette.fields(), List.of()));
            for (var projection : original.entities()) {
                var path = new java.util.ArrayList<>(projection.path()); path.add(1, groupName);
                projections.add(new NativeDefinition.Projection(projection.id(), projection.type(), path, projection.fields(), projection.references()));
            }
            bindings.add(new NativeDefinition.Binding(binding.id(), binding.engine(), binding.storage(), binding.schema(), binding.table(), binding.keyColumn(), binding.xmlColumn(), binding.keyType(), List.of(new NativeDefinition.Document("tree", original.key(), projections))));
        }
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(new NativeDefinition(declaration.id(), declaration.revision(), new NativeDefinition.Logical(types, relations, logical.rules(), logical.operationCapabilities()), bindings)));
    }
    @Test void movingParentPreservesAndValidatesRetainedModeledDescendantExactlyOnce() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><group id='g1' shade='one'><palette id='p1' shade='warm'><glyph id='alpha' tone='keep'/></palette></group><group id='g2' shade='two'/></tiles>";
        var palette = existing("palette", "p1");
        var intent = new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(palette, Map.of("tag", new TargetIntent.FieldValue.KeepObserved(), "shade", new TargetIntent.FieldValue.KeepObserved()), Map.of())), List.of(new TargetIntent.Containment("groups", existing("group", "g2"), palette)));
        var result = assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(groupedDefinition(), "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())), intent, List.of(new TargetPlacement(palette, "tree", "palettes", parent(source, "g2")))));
        assertEquals("<tiles xmlns='urn:mock:tiles'><group id='g1' shade='one'></group><group id='g2' shade='two'><palette id='p1' shade='warm' xmlns=\"urn:mock:tiles\"><glyph id='alpha' tone='keep'/></palette></group></tiles>", result.documents().getFirst().source());
        assertTrue(result.affectedEntities().contains(existing("glyph", "alpha")));
        assertEquals(4, result.graph().entities().size());
    }
    @Test void fullBaseChainRejectsEqualNearestRelativeBaseUnderDifferentAbsoluteAncestors() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><group id='g1' shade='one' xml:base='https://one.invalid/'><palette id='p1' shade='warm' xml:base='same/'><glyph id='alpha' tone='keep'/></palette></group><group id='g2' shade='two' xml:base='https://two.invalid/'><palette id='p2' shade='cold' xml:base='same/'/></group></tiles>";
        var alpha = existing("glyph", "alpha"); var intent = new TargetIntent(List.of(retainGlyph(alpha)), List.of(new TargetIntent.Containment("contains", existing("palette", "p2"), alpha)));
        assertEquals(List.of("MOVE_BASE_CONTEXT_MISMATCH"), assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(groupedDefinition(), "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())), intent, List.of(new TargetPlacement(alpha, "tree", "glyphs", parent(source, "p2"))))).codes());
    }
    @Test void staleSourceBoundParentAndWrongPhysicalParentRefuseNoPartialTarget() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><palette id='p1' shade='warm'><glyph id='alpha' tone='old'/></palette><palette id='p2' shade='cool'/></tiles>";
        var alpha = existing("glyph", "alpha"); var intent = new TargetIntent(List.of(retainGlyph(alpha)), List.of(new TargetIntent.Containment("contains", existing("palette", "p2"), alpha)));
        var actualParent = parent(source, "p2");
        for (var parent : List.of(new TargetPlacement.Parent.Existing("tree", "stale", actualParent.elementIndex()), new TargetPlacement.Parent.Existing("tree", actualParent.sourceDigest(), Integer.MAX_VALUE), parent(source, "p1"))) {
            assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(containmentDefinition(), "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())), intent, List.of(new TargetPlacement(alpha, "tree", "glyphs", parent))));
        }
    }
    @Test void sourceAndPlacementBoundsRejectVirtualListsBeforeIteration() throws Exception {
        var oversizedSources = new java.util.AbstractList<TargetSource>() { public int size() { return Integer.MAX_VALUE; } public TargetSource get(int i) { throw new AssertionError("unexpected traversal"); } };
        var oversizedPlacements = new java.util.AbstractList<TargetPlacement>() { public int size() { return Integer.MAX_VALUE; } public TargetPlacement get(int i) { throw new AssertionError("unexpected traversal"); } };
        assertEquals(List.of("RESOURCE_LIMIT"), assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(definition(), "mock-pg", oversizedSources, new TargetIntent(List.of(), List.of()), List.of())).codes());
        assertEquals(List.of("RESOURCE_LIMIT"), assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(definition(), "mock-pg", sources(), new TargetIntent(List.of(), List.of()), oversizedPlacements)).codes());
        assertEquals(List.of("RESOURCE_LIMIT"), assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(definition(), "mock-pg", List.of(new TargetSource("glyph-sheet", "x".repeat(1_048_577), Optional.empty())), new TargetIntent(List.of(), List.of()), List.of())).codes());
    }
    @Test void workDoesNotRetainScopeWideParsedTreeOrFragmentCaches() {
        var work = java.util.Arrays.stream(PhysicalTargetMaterializer.class.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("Work")).findFirst().orElseThrow();
        for (var field : work.getDeclaredFields()) {
            String type = field.getGenericType().getTypeName();
            assertFalse(type.contains("java.util.Map<java.lang.String, studio.environment.server.xml.XmlDocument>"), "scope-wide parsed source cache");
            assertFalse(type.contains("java.util.Map<java.lang.String, studio.environment.server.planning.XmlAssembly$Fragment>"), "scope-wide parsed result cache");
        }
    }
    @Test void fullDocumentCountWithTwentyThousandMostlyUnmodeledElementsProcessesSequentially() throws Exception {
        var declaration = containmentDefinition().checked().definition(); var logical = declaration.logical(); var originalBinding = declaration.bindings().getFirst();
        var paletteProjection = originalBinding.documents().getFirst().entities().getFirst();
        var documents = new java.util.ArrayList<NativeDefinition.Document>(); var inputs = new java.util.ArrayList<TargetSource>();
        String source = "<tiles xmlns='urn:mock:tiles'>" + "<u/>".repeat(19_999) + "</tiles>";
        for (int i = 0; i < 128; i++) {
            String id = "doc-" + i;
            var projection = new NativeDefinition.Projection("projection-" + i, paletteProjection.type(), paletteProjection.path(), paletteProjection.fields(), List.of());
            documents.add(new NativeDefinition.Document(id, Integer.toString(i), List.of(projection)));
            inputs.add(new TargetSource(id, source, Optional.empty()));
        }
        var binding = new NativeDefinition.Binding(originalBinding.id(), originalBinding.engine(), originalBinding.storage(), originalBinding.schema(), originalBinding.table(), originalBinding.keyColumn(), originalBinding.xmlColumn(), originalBinding.keyType(), documents);
        var palette = logical.entityTypes().stream().filter(t -> t.id().equals("palette")).findFirst().orElseThrow();
        var ready = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(new NativeDefinition(declaration.id(), declaration.revision(), new NativeDefinition.Logical(List.of(palette), List.of(), List.of(), logical.operationCapabilities()), List.of(binding))));
        var result = assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(ready, binding.id(), inputs, new TargetIntent(List.of(), List.of()), List.of()));
        assertEquals(128, result.documents().size()); assertTrue(result.graph().entities().isEmpty()); assertTrue(result.affectedDocuments().isEmpty());
        assertTrue(result.documents().stream().allMatch(d -> d.source().equals(source)));
    }

    private NativeCompilationResult.ReadyToPublish fieldDefinition(String fieldId, DefinitionDraft.ValueType codec, boolean required, boolean editable) throws Exception {
        var declaration = definition().checked().definition(); var logical = declaration.logical();
        var types = logical.entityTypes().stream().map(t -> new NativeDefinition.EntityType(t.id(), t.label(), t.fields().stream().map(f -> f.id().equals(fieldId) ? new NativeDefinition.Field(f.id(), codec, required, f.classification(), f.sensitivity(), f.readable(), editable) : f).toList(), t.identity())).toList();
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(new NativeDefinition(declaration.id(), declaration.revision(), new NativeDefinition.Logical(types, logical.relations(), logical.rules(), logical.operationCapabilities()), declaration.bindings())));
    }
    private static List<TargetSource> simpleSources(String glyphs) {
        return List.of(new TargetSource("glyph-sheet", glyphs, Optional.empty()), new TargetSource("palette-sheet", "<tiles xmlns='urn:mock:tiles'><palette id='shared' shade='warm'/></tiles>", Optional.empty()));
    }
    private static TargetIntent selectGlyph(TargetIntent.FieldValue tag, TargetIntent.FieldValue tone) {
        return new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(existing("glyph", "alpha"), Map.of("tag", tag, "tone", tone), Map.of("uses", new TargetIntent.ReferenceValue.KeepObserved()))), List.of());
    }
    @Test void optionalAbsenceRemainsDistinctFromEmptyAndPresenceChangesRefuse() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><glyph id='alpha' palette='shared'/><glyph id='beta' tone='' palette='shared'/></tiles>";
        var definition = fieldDefinition("tone", DefinitionDraft.ValueType.TEXT, false, true);
        for (TargetIntent.FieldValue choice : List.of(new TargetIntent.FieldValue.KeepObserved(), new TargetIntent.FieldValue.ExplicitlyAbsent())) {
            var result = assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(definition, "mock-pg", simpleSources(source), selectGlyph(new TargetIntent.FieldValue.KeepObserved(), choice), List.of()));
            assertEquals(source, result.documents().getFirst().source());
            assertFalse(result.graph().entities().getFirst().fields().containsKey("tone"));
            assertEquals("", result.graph().entities().get(1).fields().get("tone"));
        }
        assertEquals(List.of("ATTRIBUTE_PRESENCE_UNSUPPORTED"), assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(definition, "mock-pg", simpleSources(source), selectGlyph(new TargetIntent.FieldValue.KeepObserved(), new TargetIntent.FieldValue.Entered("")), List.of())).codes());
    }
    @Test void scalarEscapingPreservesLexicalContextAndNonEditableFieldsRequireKeepObserved() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'>\r\n<glyph id = 'alpha' tone = 'same' palette='shared'/><!--same--><glyph id='beta' tone='same' palette='shared'/></tiles>";
        var result = assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(definition(), "mock-pg", simpleSources(source), selectGlyph(new TargetIntent.FieldValue.KeepObserved(), new TargetIntent.FieldValue.Entered("a&<'\"\t\r\n𐀀")), List.of()));
        assertEquals("<tiles xmlns='urn:mock:tiles'>\r\n<glyph id = 'alpha' tone = 'a&amp;&lt;&apos;\"&#9;&#13;&#10;𐀀' palette='shared'/><!--same--><glyph id='beta' tone='same' palette='shared'/></tiles>", result.documents().getFirst().source());
        assertEquals(List.of("FIELD_NOT_EDITABLE"), assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(definition(), "mock-pg", simpleSources(source), selectGlyph(new TargetIntent.FieldValue.Entered("alpha"), new TargetIntent.FieldValue.KeepObserved()), List.of())).codes());
        assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(definition(), "mock-pg", simpleSources(source), selectGlyph(new TargetIntent.FieldValue.KeepObserved(), new TargetIntent.FieldValue.Entered("bad\u0001")), List.of()));
    }
    @Test void integerCodecRejectsNoncanonicalEnteredTextAndPreservesLargeExactIntegers() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><glyph id='alpha' tone='1' palette='shared'/><glyph id='beta' tone='2' palette='shared'/></tiles>";
        var definition = fieldDefinition("tone", DefinitionDraft.ValueType.INTEGER, true, true);
        for (String value : List.of("1.0", "01", "1e3", "9".repeat(1025))) assertEquals(List.of("INVALID_SCALAR"), assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(definition, "mock-pg", simpleSources(source), selectGlyph(new TargetIntent.FieldValue.KeepObserved(), new TargetIntent.FieldValue.Entered(value)), List.of())).codes());
        String huge = "9".repeat(1024); var result = assertInstanceOf(MaterializationResult.Complete.class, adapter.materialize(definition, "mock-pg", simpleSources(source), selectGlyph(new TargetIntent.FieldValue.KeepObserved(), new TargetIntent.FieldValue.Entered(huge)), List.of()));
        assertEquals(huge, result.graph().entities().getFirst().fields().get("tone"));
    }
    @Test void nestedMoveSourcesAreExplicitlyUnsupported() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><group id='g1' shade='one'><palette id='p1' shade='warm'><glyph id='alpha' tone='keep'/></palette></group><group id='g2' shade='two'><palette id='p2' shade='cool'/></group></tiles>";
        var palette = existing("palette", "p1"); var alpha = existing("glyph", "alpha");
        var intent = new TargetIntent(List.of(new TargetIntent.EntityDecision.Retain(palette, Map.of("tag", new TargetIntent.FieldValue.KeepObserved(), "shade", new TargetIntent.FieldValue.KeepObserved()), Map.of()), retainGlyph(alpha)),
            List.of(new TargetIntent.Containment("groups", existing("group", "g2"), palette), new TargetIntent.Containment("contains", existing("palette", "p2"), alpha)));
        assertEquals(List.of("NESTED_MOVE_SOURCES"), assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(groupedDefinition(), "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())), intent,
            List.of(new TargetPlacement(palette, "tree", "palettes", parent(source, "g2")), new TargetPlacement(alpha, "tree", "glyphs", parent(source, "p2"))))).codes());
    }
    @Test void completeScopeByteBudgetPrecedesInventoryParsing() throws Exception {
        var inputs = new java.util.ArrayList<TargetSource>();
        for (int i = 0; i < 17; i++) inputs.add(new TargetSource("doc-" + i, "a".repeat(1_048_576), Optional.empty()));
        assertEquals(List.of("RESOURCE_LIMIT"), assertInstanceOf(MaterializationResult.Rejected.class, adapter.materialize(definition(), "mock-pg", inputs, new TargetIntent(List.of(), List.of()), List.of())).codes());
    }
    @Test void incompleteTypedContainmentRefusesSafelyWithoutAnException() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><palette id='p1' shade='warm'><glyph id='alpha' tone='old'/></palette><palette id='p2' shade='cool'/></tiles>";
        var definition = containmentDefinition(); var alpha = existing("glyph", "alpha");
        for (var containment : List.of(new TargetIntent.Containment("contains", null, alpha), new TargetIntent.Containment("contains", existing("palette", "p2"), null))) {
            var intent = new TargetIntent(List.of(retainGlyph(alpha)), List.of(containment));
            assertEquals(List.of("INVALID_CONTAINMENT"), assertInstanceOf(MaterializationResult.Rejected.class, assertDoesNotThrow(() -> adapter.materialize(definition, "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())), intent, List.of()))).codes());
        }
    }
}
