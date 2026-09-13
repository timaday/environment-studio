package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.environment.core.definitionv2.*;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.definition.*;
import studio.environment.server.xml.*;
import tools.jackson.databind.node.ObjectNode;

/** Independently invented mock contexts and literal expected XML, never private model inputs. */
class FinalMoveContextTest {
    static final String XML = "http://www.w3.org/XML/1998/namespace";
    static NativeCompilationResult.ReadyToPublish definition(String context) throws Exception {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var declaration = (ObjectNode) json.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")));
        var logical = (ObjectNode) declaration.get("logical");
        var relation = logical.putArray("relations").addObject();
        relation.put("id", "contains"); relation.put("fromType", "palette"); relation.put("toType", "glyph"); relation.put("kind", "containment");
        relation.put("minimum", 0); relation.put("maximum", 10); relation.put("includeTargetOnReuse", false);
        for (var rule : logical.get("rules")) { ((ObjectNode) rule).put("minimum", 0); ((ObjectNode) rule).put("maximum", 10); }
        for (var binding : declaration.get("bindings")) {
            var old = binding.get("documents");
            var glyph = (ObjectNode) old.get(0).get("entities").get(0).deepCopy();
            var palette = (ObjectNode) old.get(1).get("entities").get(0).deepCopy();
            for (var field : palette.get("fields")) if (field.get("field").asString().equals("shade")) {
                ((ObjectNode) field.get("attribute")).put("namespaceUri", XML).put("localName", context);
            }
            glyph.putArray("references");
            var path = glyph.putArray("path"); path.add(palette.get("path").get(0)); path.add(palette.get("path").get(1));
            path.addObject().put("namespaceUri", "urn:mock:tiles").put("localName", "glyph");
            String key = old.get(0).get("key").asString();
            var document = ((ObjectNode) binding).putArray("documents").addObject();
            document.put("id", "tree"); document.put("key", key); document.putArray("entities").add(palette).add(glyph);
        }
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionBytesCompiler().compile(json.writeValueAsBytes(declaration), DefinitionBytesCompiler.Format.JSON));
    }
    static TargetIntent.Ref.Existing entity(String type, String id) { return new TargetIntent.Ref.Existing(new ObservedGraph.Key(type, id)); }
    static NativeCompilationResult.ReadyToPublish groupedDefinition(String context) throws Exception {
        var declaration = definition(context).checked().definition(); var logical = declaration.logical();
        var palette = logical.entityTypes().stream().filter(t -> t.id().equals("palette")).findFirst().orElseThrow();
        var fields = palette.fields().stream().map(f -> new NativeDefinition.Field(f.id(), f.valueType(), !f.id().equals("shade"),
            f.classification(), f.sensitivity(), f.readable(), f.editable())).toList();
        var types = new ArrayList<>(logical.entityTypes().stream().filter(t -> !t.id().equals("palette")).toList());
        types.add(new NativeDefinition.EntityType("palette", "Palette", fields, palette.identity()));
        types.add(new NativeDefinition.EntityType("group", "Group", fields, palette.identity()));
        var relations = new ArrayList<>(logical.relations());
        relations.add(new studio.environment.core.definition.DefinitionDraft.Relation("groups", "group", "palette",
            studio.environment.core.definition.DefinitionDraft.RelationKind.CONTAINMENT, java.math.BigInteger.ZERO, java.math.BigInteger.TEN, false));
        var bindings = new ArrayList<NativeDefinition.Binding>();
        for (var binding : declaration.bindings()) {
            var original = binding.documents().getFirst(); var oldPalette = original.entities().getFirst();
            var projections = new ArrayList<NativeDefinition.Projection>(); var groupName = new NativeDefinition.ExpandedName("urn:mock:tiles", "group");
            projections.add(new NativeDefinition.Projection("groups", "group", List.of(oldPalette.path().getFirst(), groupName), oldPalette.fields(), List.of()));
            for (var projection : original.entities()) {
                var path = new ArrayList<>(projection.path()); path.add(1, groupName);
                projections.add(new NativeDefinition.Projection(projection.id(), projection.type(), path, projection.fields(), projection.references()));
            }
            bindings.add(new NativeDefinition.Binding(binding.id(), binding.engine(), binding.storage(), binding.schema(), binding.table(), binding.keyColumn(),
                binding.xmlColumn(), binding.keyType(), List.of(new NativeDefinition.Document("tree", original.key(), projections))));
        }
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionCompiler().compile(new NativeDefinition(declaration.id(), declaration.revision(),
            new NativeDefinition.Logical(types, relations, logical.rules(), logical.operationCapabilities()), bindings)));
    }
    static TargetIntent.EntityDecision.Retain edit(String type, String id, String value) {
        return new TargetIntent.EntityDecision.Retain(entity(type, id), Map.of("tag", new TargetIntent.FieldValue.KeepObserved(), "shade", new TargetIntent.FieldValue.Entered(value)), Map.of());
    }
    static TargetPlacement.Parent.Existing parent(String source, String id) {
        var doc = assertInstanceOf(XmlResult.Accepted.class, new LosslessXmlAdapter().project(source)).document();
        var selected = doc.elements().stream().filter(e -> e.attributes().stream().anyMatch(a -> a.name().namespaceUri().isEmpty() && a.name().localName().equals("id") && a.value().equals(id))).toList();
        assertEquals(1, selected.size());
        return new TargetPlacement.Parent.Existing("tree", doc.digest(), selected.getFirst().index());
    }
    static MaterializationResult move(NativeCompilationResult.ReadyToPublish definition, String source, List<TargetIntent.EntityDecision> edits) {
        var child = entity("glyph", "alpha"); var destination = entity("palette", "p2");
        var decisions = new ArrayList<>(edits);
        decisions.add(new TargetIntent.EntityDecision.Retain(child, Map.of("tag", new TargetIntent.FieldValue.KeepObserved(), "tone", new TargetIntent.FieldValue.KeepObserved()), Map.of()));
        return new StructuralTargetAdapter().materialize(definition, "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())),
            new TargetIntent(decisions, List.of(new TargetIntent.Containment("contains", destination, child))),
            List.of(new TargetPlacement(child, "tree", "glyphs", parent(source, "p2"))));
    }
    @ParameterizedTest @CsvSource({"lang,en,fr", "space,preserve,default", "base,https://one.invalid/,https://two.invalid/"})
    void editedDestinationMustPreserveOriginalInheritedContext(String attribute, String original, String changed) throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><palette id='p1' xml:" + attribute + "='" + original + "'><glyph id='alpha' tone='old'/></palette><palette id='p2' xml:" + attribute + "='" + original + "'/></tiles>";
        var result = assertInstanceOf(MaterializationResult.Rejected.class, move(definition(attribute), source, List.of(edit("palette", "p2", changed))));
        assertEquals(List.of(attribute.equals("base") ? "MOVE_BASE_CONTEXT_MISMATCH" : "MOVE_INHERITED_XML_CONTEXT_MISMATCH"), result.codes());
    }
    @ParameterizedTest @CsvSource({"lang,en,fr", "space,preserve,default", "base,https://one.invalid/,https://two.invalid/"})
    void finalEqualContextAcceptsOriginallyDifferentDestination(String attribute, String original, String changed) throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><palette id='p1' xml:" + attribute + "='" + original + "'><glyph id='alpha' tone='old'/></palette><palette id='p2' xml:" + attribute + "='" + changed + "'/></tiles>";
        var result = assertInstanceOf(MaterializationResult.Complete.class, move(definition(attribute), source, List.of(edit("palette", "p2", original))));
        assertEquals("<tiles xmlns='urn:mock:tiles'><palette id='p1' xml:" + attribute + "='" + original + "'></palette><palette id='p2' xml:" + attribute + "='" + original + "'><glyph id='alpha' tone='old' xmlns=\"urn:mock:tiles\"/></palette></tiles>", result.documents().getFirst().source());
    }
    @ParameterizedTest @CsvSource({"lang,en,fr", "space,preserve,default", "base,https://one.invalid/,https://two.invalid/"})
    void editingBothParentsCannotReplaceOriginalSourceContext(String attribute, String original, String changed) throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><palette id='p1' xml:" + attribute + "='" + original + "'><glyph id='alpha' tone='old'/></palette><palette id='p2' xml:" + attribute + "='" + original + "'/></tiles>";
        var result = assertInstanceOf(MaterializationResult.Rejected.class, move(definition(attribute), source,
            List.of(edit("palette", "p1", changed), edit("palette", "p2", changed))));
        assertEquals(List.of(attribute.equals("base") ? "MOVE_BASE_CONTEXT_MISMATCH" : "MOVE_INHERITED_XML_CONTEXT_MISMATCH"), result.codes());
    }
    @ParameterizedTest @CsvSource({"lang,en,fr", "space,preserve,default", "base,https://one.invalid/,https://two.invalid/"})
    void destinationAncestorEditIsCheckedBeyondImmediateParent(String attribute, String original, String changed) throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><group id='g1' xml:" + attribute + "='" + original + "'><palette id='p1'><glyph id='alpha' tone='old'/></palette></group><group id='g2' xml:" + attribute + "='" + original + "'><palette id='p2'/></group></tiles>";
        var result = assertInstanceOf(MaterializationResult.Rejected.class, move(groupedDefinition(attribute), source, List.of(edit("group", "g2", changed))));
        assertEquals(List.of(attribute.equals("base") ? "MOVE_BASE_CONTEXT_MISMATCH" : "MOVE_INHERITED_XML_CONTEXT_MISMATCH"), result.codes());
    }
    @ParameterizedTest @CsvSource({"lang,en,fr", "space,preserve,default"})
    void unchangedNearestDeclarationMayHideEditedAncestor(String attribute, String original, String changed) throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><group id='g1' xml:" + attribute + "='" + original + "'><palette id='p1'><glyph id='alpha' tone='old'/></palette></group><group id='g2' xml:" + attribute + "='" + original + "'><palette id='p2' xml:" + attribute + "='" + original + "'/></group></tiles>";
        var result = assertInstanceOf(MaterializationResult.Complete.class, move(groupedDefinition(attribute), source, List.of(edit("group", "g2", changed))));
        assertEquals("<tiles xmlns='urn:mock:tiles'><group id='g1' xml:" + attribute + "='" + original + "'><palette id='p1'></palette></group><group id='g2' xml:" + attribute + "='" + changed + "'><palette id='p2' xml:" + attribute + "='" + original + "'><glyph id='alpha' tone='old' xmlns=\"urn:mock:tiles\"/></palette></group></tiles>", result.documents().getFirst().source());
    }
    static MaterializationResult moveIntoCreatedParent(String attribute, String source, List<TargetIntent.EntityDecision> edits) throws Exception {
        var child = entity("glyph", "alpha"); var fresh = new TargetIntent.Ref.Fresh("new-palette", "palette");
        var decisions = new ArrayList<>(edits);
        decisions.add(new TargetIntent.EntityDecision.Create(fresh, Map.of("tag", new TargetIntent.FieldValue.Entered("p2"), "shade", new TargetIntent.FieldValue.ExplicitlyAbsent()), Map.of()));
        decisions.add(new TargetIntent.EntityDecision.Retain(child, Map.of("tag", new TargetIntent.FieldValue.KeepObserved(), "tone", new TargetIntent.FieldValue.KeepObserved()), Map.of()));
        var intent = new TargetIntent(decisions, List.of(new TargetIntent.Containment("groups", entity("group", "g2"), fresh), new TargetIntent.Containment("contains", fresh, child)));
        return new StructuralTargetAdapter().materialize(groupedDefinition(attribute), "mock-pg", List.of(new TargetSource("tree", source, Optional.empty())), intent,
            List.of(new TargetPlacement(fresh, "tree", "palettes", parent(source, "g2")), new TargetPlacement(child, "tree", "glyphs", new TargetPlacement.Parent.Created(fresh))));
    }
    @ParameterizedTest @CsvSource({"lang,en,fr", "space,preserve,default", "base,https://one.invalid/,https://two.invalid/"})
    void createdParentMustUseFinallyEditedExistingAncestor(String attribute, String original, String changed) throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles' xmlns:ns0='urn:mock:tiles'><group id='g1' xml:" + attribute + "='" + original + "'><palette id='p1'><glyph id='alpha' tone='old'/></palette></group><group id='g2' xml:" + attribute + "='" + original + "'/></tiles>";
        var result = assertInstanceOf(MaterializationResult.Rejected.class, moveIntoCreatedParent(attribute, source, List.of(edit("group", "g2", changed))));
        assertEquals(List.of(attribute.equals("base") ? "MOVE_BASE_CONTEXT_MISMATCH" : "MOVE_INHERITED_XML_CONTEXT_MISMATCH"), result.codes());
    }
    @org.junit.jupiter.api.Test void createdParentNewNamespaceBindingRefusesEvenWhenUnused() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles'><group id='g1' xml:lang='en'><palette id='p1'><glyph id='alpha' tone='old'/></palette></group><group id='g2' xml:lang='en'/></tiles>";
        assertEquals(List.of("MOVE_NAMESPACE_CONTEXT_MISMATCH"), assertInstanceOf(MaterializationResult.Rejected.class, moveIntoCreatedParent("lang", source, List.of())).codes());
    }
    @org.junit.jupiter.api.Test void createdParentWithExactCompleteContextPreservesMovedSubtree() throws Exception {
        String source = "<tiles xmlns='urn:mock:tiles' xmlns:ns0='urn:mock:tiles'><group id='g1' xml:lang='en'><palette id='p1'><glyph id='alpha' tone='old'/></palette></group><group id='g2' xml:lang='en'/></tiles>";
        var result = assertInstanceOf(MaterializationResult.Complete.class, moveIntoCreatedParent("lang", source, List.of()));
        assertEquals("<tiles xmlns='urn:mock:tiles' xmlns:ns0='urn:mock:tiles'><group id='g1' xml:lang='en'><palette id='p1'></palette></group><group id='g2' xml:lang='en'><ns0:palette xmlns:ns0=\"urn:mock:tiles\" id=\"p2\"><glyph id='alpha' tone='old' xmlns=\"urn:mock:tiles\" xmlns:ns0=\"urn:mock:tiles\"/></ns0:palette></group></tiles>", result.documents().getFirst().source());
    }
}
