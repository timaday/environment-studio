package studio.environment.server.definition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definitionv2.NativeDefinition;
import static studio.environment.core.definitionv2.NativeDefinition.*;

/** No default/coercion: invoked only for a tree accepted by the packaged closed v2 schema. */
final class NativeDefinitionReader {
    private NativeDefinitionReader() { }
    static NativeDefinition read(JsonNode root) {
        JsonNode logical = root.get("logical");
        return new NativeDefinition(text(root, "id"), root.get("revision").bigIntegerValue(), new Logical(
                list(logical.get("entityTypes"), type -> new EntityType(text(type, "id"), text(type, "label"),
                        list(type.get("fields"), field -> new Field(text(field, "id"), enumeration(field, "valueType", ValueType.class),
                                field.get("required").asBoolean(), enumeration(field, "classification", Classification.class),
                                enumeration(field, "sensitivity", Sensitivity.class), field.get("readable").asBoolean(), field.get("editable").asBoolean())),
                        new Identity(text(type.get("identity"), "field")))),
                list(logical.get("relations"), relation -> new Relation(text(relation, "id"), text(relation, "fromType"), text(relation, "toType"),
                        enumeration(relation, "kind", RelationKind.class), relation.get("minimum").bigIntegerValue(), relation.get("maximum").bigIntegerValue(),
                        relation.get("includeTargetOnReuse").asBoolean())),
                list(logical.get("rules"), rule -> new CountRule(text(rule, "id"), text(rule, "type"), rule.get("minimum").bigIntegerValue(), rule.get("maximum").bigIntegerValue())),
                list(logical.get("operationCapabilities"), capability -> Enum.valueOf(Operation.class, capability.asString().toUpperCase(Locale.ROOT).replace('-', '_')))),
                list(root.get("bindings"), NativeDefinitionReader::binding));
    }
    private static Binding binding(JsonNode binding) {
        return new Binding(text(binding, "id"), enumeration(binding, "engine", Engine.class), enumeration(binding, "storage", Storage.class),
                text(binding, "schema"), text(binding, "table"), text(binding, "keyColumn"), text(binding, "xmlColumn"), enumeration(binding, "keyType", KeyType.class),
                list(binding.get("documents"), document -> new Document(text(document, "id"), text(document, "key"),
                        list(document.get("entities"), projection -> new Projection(text(projection, "id"), text(projection, "type"), list(projection.get("path"), NativeDefinitionReader::name),
                                list(projection.get("fields"), field -> new FieldMapping(text(field, "field"), name(field.get("attribute")))),
                                list(projection.get("references"), reference -> new ReferenceMapping(text(reference, "relation"), name(reference.get("attribute")))))))));
    }
    private static ExpandedName name(JsonNode name) { return new ExpandedName(text(name, "namespaceUri"), text(name, "localName")); }
    private static String text(JsonNode node, String field) { return node.get(field).asString(); }
    private static <E extends Enum<E>> E enumeration(JsonNode node, String field, Class<E> type) {
        return Enum.valueOf(type, text(node, field).toUpperCase(Locale.ROOT).replace('-', '_'));
    }
    private static <T> List<T> list(JsonNode array, Function<JsonNode, T> reader) {
        List<T> result = new ArrayList<>(); array.forEach(node -> result.add(reader.apply(node))); return List.copyOf(result);
    }
}
