package studio.environment.core.definitionv2;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import studio.environment.core.definition.DefinitionDraft.Relation;
import static studio.environment.core.definitionv2.NativeDefinition.*;

/** Frozen ES-LOGICAL-2 / ES-BINDING-2 framing; no parser serialization enters a digest. */
final class NativeDigests {
    static final Map<String, BigInteger> MECHANISMS = Map.of("native-compiler-v2", BigInteger.TWO,
            "xml-path-v1", BigInteger.ONE, "xml-span-v1", BigInteger.ONE, "generic-graph-v1", BigInteger.ONE);
    private NativeDigests() { }
    static NativeCompilationResult.Checked checked(NativeDefinition definition) {
        String logical = hash("ES-LOGICAL-2", logical(definition.logical()));
        Map<String, String> bindings = new LinkedHashMap<>();
        definition.bindings().stream().sorted(Comparator.comparing(Binding::id)).forEach(binding ->
                bindings.put(binding.id(), hash("ES-BINDING-2", object("logicalDigest", logical,
                        "mechanisms", MECHANISMS, "binding", binding(binding)))));
        return new NativeCompilationResult.Checked(definition, logical, bindings, MECHANISMS);
    }
    private static Map<String, Object> logical(Logical logical) {
        return object("entityTypes", sorted(logical.entityTypes(), EntityType::id, type -> object("id", type.id(),
                "fields", sorted(type.fields(), Field::id, field -> object("id", field.id(), "valueType", token(field.valueType()),
                        "required", field.required(), "classification", token(field.classification()), "sensitivity", token(field.sensitivity()),
                        "readable", field.readable(), "editable", field.editable())),
                "identity", object("field", type.identity().field(), "scope", "type", "normalization", "exact"))),
                "relations", sorted(logical.relations(), Relation::id, relation -> object("id", relation.id(), "fromType", relation.fromType(),
                        "toType", relation.toType(), "kind", token(relation.kind()), "minimum", relation.minimum(), "maximum", relation.maximum(),
                        "includeTargetOnReuse", relation.includeTargetOnReuse())),
                "rules", sorted(logical.rules(), CountRule::id, rule -> object("id", rule.id(), "kind", "entity-count", "type", rule.type(),
                        "minimum", rule.minimum(), "maximum", rule.maximum())),
                "operationCapabilities", logical.operationCapabilities().stream().map(NativeDigests::token).sorted().toList());
    }
    private static Map<String, Object> binding(Binding binding) {
        return object("engine", token(binding.engine()), "storage", token(binding.storage()), "schema", binding.schema(), "table", binding.table(),
                "keyColumn", binding.keyColumn(), "xmlColumn", binding.xmlColumn(), "keyType", token(binding.keyType()),
                "documents", sorted(binding.documents(), Document::id, document -> object("id", document.id(), "key", document.key(),
                        "entities", sorted(document.entities(), Projection::id, projection -> object("id", projection.id(), "type", projection.type(),
                                "path", projection.path().stream().map(NativeDigests::name).toList(),
                                "fields", sorted(projection.fields(), FieldMapping::field, mapping -> object("field", mapping.field(), "attribute", name(mapping.attribute()))),
                                "references", sorted(projection.references(), ReferenceMapping::relation, mapping -> object("relation", mapping.relation(), "attribute", name(mapping.attribute()))))))));
    }
    private static Map<String, Object> name(ExpandedName name) { return object("namespaceUri", name.namespaceUri(), "localName", name.localName()); }
    private static String token(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    private static <T> List<Map<String, Object>> sorted(List<T> values, Function<T, String> key, Function<T, Map<String, Object>> map) {
        return values.stream().sorted(Comparator.comparing(key)).map(map).toList();
    }
    private static Map<String, Object> object(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
    private static String hash(String domain, Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(domain.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0); frame(digest, value);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("Required native digest is unavailable."); }
    }
    private static void frame(MessageDigest digest, Object value) {
        if (value instanceof String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            ascii(digest, "S" + bytes.length + ":"); digest.update(bytes);
        } else if (value instanceof BigInteger number) ascii(digest, "I" + number + ";");
        else if (value instanceof Boolean flag) ascii(digest, flag ? "T" : "F");
        else if (value instanceof List<?> list) {
            ascii(digest, "A" + list.size() + ":"); list.forEach(item -> frame(digest, item));
        } else if (value instanceof Map<?, ?> map) {
            ascii(digest, "O" + map.size() + ":");
            map.entrySet().stream().sorted((left, right) -> Arrays.compareUnsigned(((String) left.getKey()).getBytes(StandardCharsets.UTF_8),
                    ((String) right.getKey()).getBytes(StandardCharsets.UTF_8))).forEach(entry -> { frame(digest, entry.getKey()); frame(digest, entry.getValue()); });
        } else throw new IllegalArgumentException("Unsupported native digest frame type.");
    }
    private static void ascii(MessageDigest digest, String value) { digest.update(value.getBytes(StandardCharsets.US_ASCII)); }
}
