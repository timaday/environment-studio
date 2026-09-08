package studio.environment.core.definition;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shape-checked declarations only; this record conveys no publication authority. */
public record DefinitionDraft(String schemaVersion, String id, BigInteger revision, Status status,
        List<EntityType> entityTypes, List<Relation> relations, List<Document> documents,
        List<String> requiredRules, List<String> operationCapabilities) {
    public enum Status { DRAFT, PUBLISHED }
    public enum ValueType { TEXT, INTEGER, BOOLEAN, URI }
    public enum Classification { ENVIRONMENT, STRUCTURAL }
    public enum Sensitivity { PUBLIC, INTERNAL, SECRET, UNKNOWN }
    public enum RelationKind { REFERENCE, CONTAINMENT }
    public DefinitionDraft {
        Objects.requireNonNull(schemaVersion); Objects.requireNonNull(id);
        Objects.requireNonNull(revision); Objects.requireNonNull(status);
        entityTypes = List.copyOf(entityTypes); relations = List.copyOf(relations);
        documents = List.copyOf(documents); requiredRules = List.copyOf(requiredRules);
        operationCapabilities = List.copyOf(operationCapabilities);
    }
    public record EntityType(String id, String label, List<Field> fields) {
        public EntityType { Objects.requireNonNull(id); Objects.requireNonNull(label); fields = List.copyOf(fields); }
    }
    public record Field(String id, ValueType valueType, boolean required,
            Classification classification, Sensitivity sensitivity) {
        public Field { Objects.requireNonNull(id); Objects.requireNonNull(valueType);
            Objects.requireNonNull(classification); Objects.requireNonNull(sensitivity); }
    }
    public record Relation(String id, String fromType, String toType, RelationKind kind,
            BigInteger minimum, BigInteger maximum, boolean includeTargetOnReuse) {
        public Relation { Objects.requireNonNull(id); Objects.requireNonNull(fromType);
            Objects.requireNonNull(toType); Objects.requireNonNull(kind);
            Objects.requireNonNull(minimum); Objects.requireNonNull(maximum); }
    }
    public record Document(String id, String logicalStore, String recordKey,
            Map<String, String> namespaces, List<Mapping> mappings) {
        public Document { Objects.requireNonNull(id); Objects.requireNonNull(logicalStore);
            Objects.requireNonNull(recordKey);
            namespaces.forEach((key, value) -> { Objects.requireNonNull(key); Objects.requireNonNull(value); });
            namespaces = Collections.unmodifiableMap(new LinkedHashMap<>(namespaces)); mappings = List.copyOf(mappings); }
    }
    public record Mapping(String entityType, String field, String contextXPath, String valueXPath,
            BigInteger expectedMatches, String writerCapability) {
        public Mapping { Objects.requireNonNull(entityType); Objects.requireNonNull(field);
            Objects.requireNonNull(contextXPath); Objects.requireNonNull(valueXPath);
            Objects.requireNonNull(expectedMatches); Objects.requireNonNull(writerCapability); }
    }
}
