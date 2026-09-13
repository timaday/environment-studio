package studio.environment.core.definitionv2;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definition.DefinitionDraft.Relation;

/** Native declarations carry no workspace publication or execution authority. */
public record NativeDefinition(String id, BigInteger revision, Logical logical, List<Binding> bindings) {
    public NativeDefinition { Objects.requireNonNull(id); Objects.requireNonNull(revision); Objects.requireNonNull(logical); bindings = List.copyOf(bindings); }
    public enum Operation { RETAIN_ENTITY, CREATE_ENTITY, REMOVE_ENTITY, BIND_FIELD, MOVE_RELATION }
    public enum Engine { POSTGRESQL, ORACLE }
    public enum Storage { TEXT, CLOB }
    public enum KeyType { TEXT, INT64 }
    public record Logical(List<EntityType> entityTypes, List<Relation> relations, List<CountRule> rules, List<Operation> operationCapabilities) {
        public Logical { entityTypes = List.copyOf(entityTypes); relations = List.copyOf(relations); rules = List.copyOf(rules); operationCapabilities = List.copyOf(operationCapabilities); }
    }
    public record EntityType(String id, String label, List<Field> fields, Identity identity) {
        public EntityType { Objects.requireNonNull(id); Objects.requireNonNull(label); fields = List.copyOf(fields); Objects.requireNonNull(identity); }
    }
    public record Identity(String field) { public Identity { Objects.requireNonNull(field); } }
    public record Field(String id, ValueType valueType, boolean required, Classification classification,
            Sensitivity sensitivity, boolean readable, boolean editable) {
        public Field { Objects.requireNonNull(id); Objects.requireNonNull(valueType); Objects.requireNonNull(classification); Objects.requireNonNull(sensitivity); }
    }
    public record CountRule(String id, String type, BigInteger minimum, BigInteger maximum) {
        public CountRule { Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(minimum); Objects.requireNonNull(maximum); }
    }
    public record Binding(String id, Engine engine, Storage storage, String schema, String table,
            String keyColumn, String xmlColumn, KeyType keyType, List<Document> documents) {
        public Binding { Objects.requireNonNull(id); Objects.requireNonNull(engine); Objects.requireNonNull(storage);
            Objects.requireNonNull(schema); Objects.requireNonNull(table); Objects.requireNonNull(keyColumn);
            Objects.requireNonNull(xmlColumn); Objects.requireNonNull(keyType); documents = List.copyOf(documents); }
    }
    public record Document(String id, String key, List<Projection> entities) {
        public Document { Objects.requireNonNull(id); Objects.requireNonNull(key); entities = List.copyOf(entities); }
    }
    public record ExpandedName(String namespaceUri, String localName) {
        public ExpandedName { Objects.requireNonNull(namespaceUri); Objects.requireNonNull(localName); }
    }
    public record Projection(String id, String type, List<ExpandedName> path, List<FieldMapping> fields, List<ReferenceMapping> references) {
        public Projection { Objects.requireNonNull(id); Objects.requireNonNull(type); path = List.copyOf(path); fields = List.copyOf(fields); references = List.copyOf(references); }
    }
    public sealed interface FieldLocator permits DirectAttribute, ChildProperty { }
    public record DirectAttribute(ExpandedName attribute) implements FieldLocator {
        public DirectAttribute { Objects.requireNonNull(attribute); }
    }
    public record ChildProperty(ExpandedName element, ExpandedName discriminatorAttribute,
            String discriminatorValue, ExpandedName valueAttribute) implements FieldLocator {
        public ChildProperty { Objects.requireNonNull(element); Objects.requireNonNull(discriminatorAttribute);
            Objects.requireNonNull(discriminatorValue); Objects.requireNonNull(valueAttribute); }
        @Override public String toString() { return "ChildProperty[redacted]"; }
    }
    public record FieldMapping(String field, FieldLocator locator) {
        public FieldMapping { Objects.requireNonNull(field); Objects.requireNonNull(locator); }
        public FieldMapping(String field, ExpandedName attribute) { this(field, new DirectAttribute(attribute)); }
    }
    public record ReferenceMapping(String relation, ExpandedName attribute) {
        public ReferenceMapping { Objects.requireNonNull(relation); Objects.requireNonNull(attribute); }
    }
}
