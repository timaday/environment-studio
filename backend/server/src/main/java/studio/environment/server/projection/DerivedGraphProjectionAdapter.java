package studio.environment.server.projection;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.derived.DerivedResult;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.derived.DerivedGraphEngine;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.server.xml.LosslessXmlAdapter;
import studio.environment.server.xml.XmlDocument;
import studio.environment.server.xml.XmlResult;

/** Internal v3 observation only; no publication, live-owner or export authority. */
public final class DerivedGraphProjectionAdapter {
    public record Snapshot(String revisionToken, String logicalDigest, String bindingId, String bindingDigest,
            List<DocumentSource> documents) {
        public Snapshot {
            Objects.requireNonNull(revisionToken); Objects.requireNonNull(logicalDigest);
            Objects.requireNonNull(bindingId); Objects.requireNonNull(bindingDigest);
            if (documents.size() > 128) throw new IllegalArgumentException("Document limit exceeded.");
            documents = List.copyOf(documents);
        }
        @Override public String toString() { return "DerivedSourceSnapshot[redacted]"; }
    }
    public sealed interface Result permits Complete, Refused { }
    public record Complete(ObservedGraph physical, ProjectionResult.TransientProjection sources,
            DerivedInput input, DerivedResult.Complete derived) implements Result {
        public Complete { Objects.requireNonNull(physical); Objects.requireNonNull(sources); Objects.requireNonNull(input); Objects.requireNonNull(derived); }
        @Override public String toString() { return "DerivedProjection[redacted]"; }
    }
    public record Refused(String code) implements Result { public Refused { Objects.requireNonNull(code); } }
    public Result project(NativeCompilationResult.Checked definition, DerivedInput.Pin expected,
            Snapshot supplied, BooleanSupplier cancelled) {
        if (definition == null || expected == null || supplied == null || cancelled == null) return new Refused("INVALID_INPUT");
        try { return observe(definition, expected, supplied, cancelled); }
        catch (Failure failure) { return new Refused(failure.code); }
    }
    private Complete observe(NativeCompilationResult.Checked definition, DerivedInput.Pin expected,
            Snapshot supplied, BooleanSupplier cancelled) {
        cancellation(cancelled);
        var recompiled = new NativeDefinitionCompiler().compile(definition.definition());
        if (!(recompiled instanceof NativeCompilationResult.Incomplete checked) || !checked.checked().equals(definition)
                || checked.diagnostics().stream().anyMatch(d -> !d.code().equals("MECHANISM_UNQUALIFIED"))) fail("INVALID_DEFINITION");
        if (!expected.revisionToken().equals(supplied.revisionToken()) || !expected.logicalDigest().equals(supplied.logicalDigest())
                || !expected.bindingId().equals(supplied.bindingId()) || !expected.bindingDigest().equals(supplied.bindingDigest())) fail("STALE_INPUT");
        var binding = definition.definition().bindings().stream().filter(b -> b.id().equals(supplied.bindingId())).findFirst().orElse(null);
        if (binding == null || !definition.logicalDigest().equals(supplied.logicalDigest())
                || !Objects.equals(definition.bindingDigests().get(supplied.bindingId()), supplied.bindingDigest())) fail("INVALID_PIN");
        Map<String, NativeDefinition.Document> declarations = new TreeMap<>(); binding.documents().forEach(d -> declarations.put(d.id(), d));
        if (!declarations.keySet().equals(expected.documentDigests().keySet())
                || expected.documentDigests().values().stream().anyMatch(d -> !d.matches("[0-9a-f]{64}"))) fail("INVALID_PIN");
        cancellation(cancelled);
        var logical = definition.definition().logical();
        // Reuse physical vocabulary, never a v2 digest or a manufactured ready/publication result.
        var physicalLogical = new NativeDefinition.Logical(logical.entityTypes(), logical.relations(), logical.rules(), logical.operationCapabilities());
        var projected = new PhysicalGraphProjection().project(physicalLogical, binding, definition.logicalDigest(),
                definition.bindingDigests().get(binding.id()), supplied.documents(), cancelled);
        if (projected instanceof ProjectionResult.Rejected rejected) fail(rejected.diagnostics().getFirst().code());
        var physical = (ProjectionResult.Accepted) projected;
        Map<String, String> actualDigests = new TreeMap<>();
        for (var source : physical.projection().documents()) {
            cancellation(cancelled);
            if (!source.digest().equals(expected.documentDigests().get(source.documentId()))) fail("STALE_INPUT");
            actualDigests.put(source.documentId(), source.digest());
        }
        var actualPin = new DerivedInput.Pin(supplied.revisionToken(), supplied.logicalDigest(), supplied.bindingId(), supplied.bindingDigest(), actualDigests);
        var input = inputs(definition, binding, actualPin, physical, cancelled);
        var evaluated = new DerivedGraphEngine().evaluate(definition, expected, input, cancelled);
        if (evaluated instanceof DerivedResult.Refused refused) fail(refused.code());
        if (!(evaluated instanceof DerivedResult.Complete)) fail("INCOMPLETE_OBSERVATION");
        cancellation(cancelled);
        return new Complete(physical.graph(), physical.projection(), input, (DerivedResult.Complete) evaluated);
    }
    private DerivedInput inputs(NativeCompilationResult.Checked definition, NativeDefinition.Binding binding,
            DerivedInput.Pin pin, ProjectionResult.Accepted physical, BooleanSupplier cancelled) {
        var sourceFields = new TreeMap<String, TreeSet<String>>();
        definition.definition().logical().derivations().forEach(d -> sourceFields.computeIfAbsent(d.sourceType(), ignored -> new TreeSet<>()).add(d.sourceField()));
        Map<String, List<ObservedGraph.Entity>> byDocument = new TreeMap<>();
        Map<ObservedGraph.Key, DerivedInput.Ref.Observed> references = new HashMap<>();
        for (var entity : physical.graph().entities()) {
            cancellation(cancelled);
            byDocument.computeIfAbsent(entity.origin().documentId(), ignored -> new ArrayList<>()).add(entity);
            references.put(entity.key(), new DerivedInput.Ref.Observed(entity.key(), entity.origin()));
        }
        Map<String, NativeDefinition.Document> declarations = new HashMap<>(); binding.documents().forEach(d -> declarations.put(d.id(), d));
        var entities = new ArrayList<DerivedInput.Entity>(); var xml = new LosslessXmlAdapter();
        for (var source : physical.projection().documents()) {
            cancellation(cancelled);
            var reparsed = xml.project(source.source());
            cancellation(cancelled);
            if (!(reparsed instanceof XmlResult.Accepted)) fail("PROJECTION_MISMATCH");
            var document = ((XmlResult.Accepted) reparsed).document();
            if (!source.digest().equals(document.digest())) fail("STALE_INPUT");
            var locator = new FieldLocatorResolver(document);
            Map<String, NativeDefinition.Projection> projections = new HashMap<>();
            declarations.get(source.documentId()).entities().forEach(p -> projections.put(p.id(), p));
            for (var entity : byDocument.getOrDefault(source.documentId(), List.of())) {
                cancellation(cancelled);
                var origin = entity.origin();
                if (origin.elementIndex() < 0 || origin.elementIndex() >= document.elements().size()) fail("PROJECTION_MISMATCH");
                var element = document.elements().get(origin.elementIndex());
                if (!origin.ancestry().equals(element.ancestry()) || !origin.sourceDigest().equals(document.digest())) fail("PROJECTION_MISMATCH");
                var projection = projections.get(origin.projectionId());
                if (projection == null || !projection.type().equals(entity.key().type())) fail("PROJECTION_MISMATCH");
                Map<String, DerivedInput.FieldState> fields = new TreeMap<>();
                for (var field : sourceFields.getOrDefault(entity.key().type(), new TreeSet<>())) {
                    cancellation(cancelled);
                    var mapping = projection.fields().stream().filter(f -> f.field().equals(field)).findFirst().orElse(null);
                    if (mapping == null) fail("PROJECTION_MISMATCH");
                    var located = locator.resolve(element, mapping.locator());
                    if (located instanceof FieldLocatorResolver.Refused refused) fail(refused.code());
                    if (located instanceof FieldLocatorResolver.Absent) {
                        if (entity.fields().containsKey(field)) fail("PROJECTION_MISMATCH");
                        fields.put(field, new DerivedInput.FieldState.Absent());
                    } else {
                        var value = (FieldLocatorResolver.Located) located;
                        if (!value.attribute().value().equals(entity.fields().get(field))) fail("PROJECTION_MISMATCH");
                        Optional<DerivedInput.ChildSelector> selector = value.selector().map(s -> new DerivedInput.ChildSelector(
                                value.entity().index(), name(s.child().name()), attribute(source.documentId(), s.discriminator())));
                        var location = new DerivedInput.Location(attribute(source.documentId(), value.attribute()), selector);
                        fields.put(field, new DerivedInput.FieldState.Present(value.attribute().value(), new DerivedInput.Proof.Observed(location)));
                    }
                }
                entities.add(new DerivedInput.Entity(references.get(entity.key()), fields));
            }
        }
        var edges = new ArrayList<DerivedInput.Edge>();
        for (var edge : physical.graph().edges()) {
            cancellation(cancelled);
            edges.add(new DerivedInput.Edge(edge.relation(), references.get(edge.source()), references.get(edge.target())));
        }
        return new DerivedInput(DerivedInput.Kind.OBSERVED, pin, entities, edges);
    }
    private static DerivedInput.AttributePin attribute(String document, XmlDocument.AttributeRef attribute) {
        return new DerivedInput.AttributePin(document, attribute.sourceDigest(), attribute.elementIndex(), name(attribute.name()),
                attribute.qualifiedName(), attribute.value(), attribute.valueSpan().start(), attribute.valueSpan().end(), attribute.quote());
    }
    private static NativeDefinition.ExpandedName name(XmlDocument.ExpandedName name) {
        return new NativeDefinition.ExpandedName(name.namespaceUri(), name.localName());
    }
    private static void cancellation(BooleanSupplier cancelled) { if (cancelled.getAsBoolean()) fail("CANCELLED"); }
    private static void fail(String code) { throw new Failure(code); }
    private static final class Failure extends RuntimeException {
        private final String code;
        private Failure(String code) { super(null, null, false, false); this.code = code; }
    }

}
