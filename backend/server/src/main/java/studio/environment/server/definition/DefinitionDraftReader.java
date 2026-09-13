package studio.environment.server.definition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import studio.environment.core.definition.DefinitionDraft;
import static studio.environment.core.definition.DefinitionDraft.*;

/** Called only after canonical schema validation; no coercion or fallback for absent fields. */
final class DefinitionDraftReader {
    private DefinitionDraftReader() { }
    static DefinitionDraft read(JsonNode root) {
        return new DefinitionDraft(text(root, "schemaVersion"), text(root, "id"), root.get("revision").bigIntegerValue(),
                enumeration(root, "status", Status.class), list(root.get("entityTypes"), node -> new EntityType(
                        text(node, "id"), text(node, "label"), list(node.get("fields"), field -> new Field(
                                text(field, "id"), enumeration(field, "valueType", ValueType.class), field.get("required").asBoolean(),
                                enumeration(field, "classification", Classification.class), enumeration(field, "sensitivity", Sensitivity.class))))),
                list(root.get("relations"), node -> new Relation(text(node, "id"), text(node, "fromType"), text(node, "toType"),
                        enumeration(node, "kind", RelationKind.class), node.get("minimum").bigIntegerValue(),
                        node.get("maximum").bigIntegerValue(), node.get("includeTargetOnReuse").asBoolean())),
                list(root.get("documents"), DefinitionDraftReader::document),
                list(root.get("requiredRules"), JsonNode::asString), list(root.get("operationCapabilities"), JsonNode::asString));
    }
    private static Document document(JsonNode node) {
        var namespaces = new LinkedHashMap<String, String>();
        node.get("namespaces").properties().forEach(entry -> namespaces.put(entry.getKey(), entry.getValue().asString()));
        return new Document(text(node, "id"), text(node, "logicalStore"), text(node, "recordKey"), namespaces,
                list(node.get("mappings"), mapping -> new Mapping(text(mapping, "entityType"), text(mapping, "field"),
                        text(mapping, "contextXPath"), text(mapping, "valueXPath"), mapping.get("expectedMatches").bigIntegerValue(),
                        text(mapping, "writerCapability"))));
    }
    private static String text(JsonNode node, String field) { return node.get(field).asString(); }
    private static <E extends Enum<E>> E enumeration(JsonNode node, String field, Class<E> type) {
        return Enum.valueOf(type, text(node, field).toUpperCase(Locale.ROOT));
    }
    private static <T> List<T> list(JsonNode array, Function<JsonNode, T> convert) {
        List<T> result = new ArrayList<>();
        array.forEach(node -> result.add(convert.apply(node)));
        return List.copyOf(result);
    }
}
