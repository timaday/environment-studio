package studio.environment.server.definition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import studio.environment.core.definitionv2.NativeDefinition.CountRule;
import studio.environment.core.definitionv3.NativeDefinition;
import static studio.environment.core.definitionv3.NativeDefinition.*;

/** Reads only a tree already admitted by the closed v3 shape; never grants v2 authority. */
final class NativeV3DefinitionReader {
    private NativeV3DefinitionReader() { }
    static NativeDefinition read(JsonNode root) {
        // V3 preserves these physical value types. This reader returns data, not a v2 compilation result.
        var physical = NativeDefinitionReader.read(root);
        var model = physical.logical();
        var logical = root.get("logical");
        return new NativeDefinition(physical.id(), physical.revision(), new Logical(
                model.entityTypes(), model.relations(), model.rules(), model.operationCapabilities(),
                list(logical.get("computedTypes"), node -> new ComputedType(text(node, "id"), text(node, "label"))),
                list(logical.get("derivations"), node -> new Derivation(text(node, "id"), text(node, "sourceType"),
                        text(node, "sourceField"), text(node, "computedType"), text(node, "membershipRelation"))),
                list(logical.get("cooccurrences"), node -> new Cooccurrence(text(node, "id"), text(node, "fromDerivation"),
                        text(node, "toDerivation"), node.get("minimum").bigIntegerValue(), node.get("maximum").bigIntegerValue())),
                list(logical.get("computedRules"), node -> new CountRule(text(node, "id"), text(node, "type"),
                        node.get("minimum").bigIntegerValue(), node.get("maximum").bigIntegerValue()))), physical.bindings());
    }
    private static String text(JsonNode node, String field) { return node.get(field).asString(); }
    private static <T> List<T> list(JsonNode array, Function<JsonNode, T> reader) {
        List<T> result = new ArrayList<>();
        array.forEach(node -> result.add(reader.apply(node)));
        return List.copyOf(result);
    }
}
