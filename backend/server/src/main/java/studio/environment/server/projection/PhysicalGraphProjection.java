package studio.environment.server.projection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.graph.GraphDiagnostic;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.graph.GraphValidator;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.server.xml.LosslessXmlAdapter;
import studio.environment.server.xml.XmlDocument;
import studio.environment.server.xml.XmlResult;

/** Version-independent physical mechanism; callers establish checked metadata, never availability here. */
final class PhysicalGraphProjection {
    private static final int MAX_DOCUMENTS = 128;
    private static final long MAX_UTF8_BYTES = 16L * 1024 * 1024;
    private final LosslessXmlAdapter xml = new LosslessXmlAdapter();
    private final GraphValidator validator = new GraphValidator();
    ProjectionResult project(NativeDefinition.Logical logical, NativeDefinition.Binding binding,
            String logicalDigest, String bindingDigest, List<DocumentSource> documents,
            java.util.function.BooleanSupplier cancelled) {
        if (documents.size() > MAX_DOCUMENTS || binding.documents().size() > MAX_DOCUMENTS) return rejected("RESOURCE_LIMIT", "", "");
        if (documents.size() != binding.documents().size()) return rejected("INVENTORY_MISMATCH", "", "");
        Set<String> inventory = new HashSet<>(); binding.documents().forEach(document -> inventory.add(document.id()));
        Set<String> supplied = new HashSet<>();
        long totalBytes = 0;
        // Check the entire inventory and strict Unicode sizes before copying sources or parsing any XML.
        for (DocumentSource document : documents) {
            if (cancelled.getAsBoolean()) return rejected("CANCELLED", "", "");
            if (document == null || document.source() == null) return rejected("INVALID_INPUT", "", "");
            if (!inventory.contains(document.documentId()) || !supplied.add(document.documentId())) return rejected("INVENTORY_MISMATCH", "", "");
            if (document.source().length() > 1_048_576) return rejected("RESOURCE_LIMIT", document.documentId(), "");
            long bytes = utf8Size(document.source(), cancelled);
            if (bytes == -2) return rejected("CANCELLED", "", "");
            if (bytes < 0) return rejected("INVALID_UNICODE", document.documentId(), "");
            totalBytes += bytes;
            if (totalBytes > MAX_UTF8_BYTES) return rejected("RESOURCE_LIMIT", "", "");
        }
        Map<String, DocumentSource> sources = new TreeMap<>(); documents.forEach(document -> sources.put(document.documentId(), document));
        Map<String, NativeDefinition.Document> declarations = new HashMap<>(); binding.documents().forEach(document -> declarations.put(document.id(), document));
        List<ObservedGraph.Occurrence> occurrences = new ArrayList<>();
        List<ProjectionResult.ExactDocument> exact = new ArrayList<>();
        for (var entry : sources.entrySet()) {
            if (cancelled.getAsBoolean()) return rejected("CANCELLED", "", "");
            String documentId = entry.getKey();
            XmlResult parsed = xml.project(entry.getValue().source());
            if (cancelled.getAsBoolean()) return rejected("CANCELLED", "", "");
            if (parsed instanceof XmlResult.Rejected refusal) return rejected(refusal.diagnostics().getFirst().code(), documentId, "");
            XmlDocument document = ((XmlResult.Accepted) parsed).document();
            var locator=new FieldLocatorResolver(document);
            Map<List<XmlDocument.ExpandedName>, NativeDefinition.Projection> paths = new HashMap<>();
            for (var projection : declarations.get(documentId).entities()) {
                List<XmlDocument.ExpandedName> path = projection.path().stream().map(PhysicalGraphProjection::name).toList();
                if (paths.putIfAbsent(path, projection) != null) return rejected("AMBIGUOUS_PROJECTION", documentId, projection.id());
            }
            var invalid=locator.validate(declarations.get(documentId));
            if(invalid.isPresent())return rejected(invalid.orElseThrow().code(),documentId,"");
            for (var element : document.elements()) {
                if (cancelled.getAsBoolean()) return rejected("CANCELLED", "", "");
                List<XmlDocument.ExpandedName> path = new ArrayList<>(element.ancestry().size() + 1);
                element.ancestry().forEach(index -> path.add(document.elements().get(index).name())); path.add(element.name());
                var projection = paths.get(path);
                if (projection == null) continue;
                if (occurrences.size() == ObservedGraph.MAX_ENTITIES) return rejected("RESOURCE_LIMIT", documentId, projection.id());
                Map<XmlDocument.ExpandedName, String> attributes = new HashMap<>();
                element.attributes().forEach(attribute -> attributes.put(attribute.name(), attribute.value()));
                Map<String, String> fields = new TreeMap<>();
                for (var mapping : projection.fields()) {
                    var located=locator.resolve(element,mapping.locator());
                    if(located instanceof FieldLocatorResolver.Refused refused)return rejected(refused.code(),documentId,projection.id());
                    if(located instanceof FieldLocatorResolver.Located value)fields.put(mapping.field(),value.attribute().value());
                }
                Map<String, String> references = new TreeMap<>();
                for (var mapping : projection.references()) {
                    String value = attributes.get(name(mapping.attribute())); if (value != null) references.put(mapping.relation(), value);
                }
                occurrences.add(new ObservedGraph.Occurrence(projection.type(), fields, references,
                        new ObservedGraph.Origin(documentId, projection.id(), document.digest(), element.index(), element.ancestry())));
            }
            exact.add(new ProjectionResult.ExactDocument(documentId, document.source(), document.digest()));
        }
        if (cancelled.getAsBoolean()) return rejected("CANCELLED", "", "");
        var result = validator.validate(logical, occurrences);
        if (cancelled.getAsBoolean()) return rejected("CANCELLED", "", "");
        if (result instanceof GraphValidationResult.Rejected refusal) return new ProjectionResult.Rejected(refusal.diagnostics());
        return new ProjectionResult.Accepted(((GraphValidationResult.Accepted) result).graph(), new ProjectionResult.TransientProjection(exact),
                logicalDigest, bindingDigest);
    }
    private static XmlDocument.ExpandedName name(NativeDefinition.ExpandedName name) {
        return new XmlDocument.ExpandedName(name.namespaceUri(), name.localName());
    }
    private static long utf8Size(String source, java.util.function.BooleanSupplier cancelled) {
        long bytes = 0;
        for (int i = 0; i < source.length(); i++) {
            if ((i & 4095) == 0 && cancelled.getAsBoolean()) return -2;
            char value = source.charAt(i);
            if (Character.isHighSurrogate(value)) {
                if (++i == source.length() || !Character.isLowSurrogate(source.charAt(i))) return -1;
                bytes += 4;
            } else if (Character.isLowSurrogate(value)) return -1;
            else bytes += value < 0x80 ? 1 : value < 0x800 ? 2 : 3;
        }
        return bytes;
    }
    private static ProjectionResult rejected(String code, String document, String projection) {
        return new ProjectionResult.Rejected(List.of(new GraphDiagnostic(code, document, projection, "")));
    }
}
