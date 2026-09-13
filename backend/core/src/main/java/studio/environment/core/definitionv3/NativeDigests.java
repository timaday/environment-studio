package studio.environment.core.definitionv3;

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
import studio.environment.core.definitionv2.NativeDefinition.Binding;
import studio.environment.core.definitionv2.NativeDefinition.Document;
import studio.environment.core.definitionv2.NativeDefinition.Projection;
import studio.environment.core.definitionv2.NativeDefinition.EntityType;
import studio.environment.core.definitionv2.NativeDefinition.Field;
import studio.environment.core.definitionv2.NativeDefinition.CountRule;
import studio.environment.core.definitionv2.NativeDefinition.FieldMapping;
import studio.environment.core.definitionv2.NativeDefinition.DirectAttribute;
import studio.environment.core.definitionv2.NativeDefinition.ChildProperty;
import studio.environment.core.definitionv2.NativeDefinition.ReferenceMapping;
import studio.environment.core.definitionv2.NativeDefinition.ExpandedName;
import static studio.environment.core.definitionv3.NativeDefinition.*;

/** Explicit ES-LOGICAL-3 / ES-BINDING-3 framing; no parser serialization enters a digest. */
final class NativeDigests {
    private NativeDigests() { }
    static NativeCompilationResult.Checked checked(NativeDefinition definition) {
        String logical = hash("ES-LOGICAL-3", object("logical", logical(definition.logical()), "derivedSemantics", semantics()));
        Map<String, String> bindings = new LinkedHashMap<>();
        definition.bindings().stream().sorted(Comparator.comparing(Binding::id)).forEach(binding ->
                bindings.put(binding.id(), hash("ES-BINDING-3", object("logicalDigest", logical,
                        "mechanisms", NativeMechanisms.required(binding), "binding", binding(binding)))));
        return new NativeCompilationResult.Checked(definition, logical, bindings, NativeMechanisms.required(definition));
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
                "operationCapabilities", logical.operationCapabilities().stream().map(NativeDigests::token).sorted().toList(),
                "computedTypes", sorted(logical.computedTypes(), ComputedType::id, type -> object("id", type.id())),
                "derivations", sorted(logical.derivations(), Derivation::id, d -> object("id", d.id(), "sourceType", d.sourceType(),
                        "sourceField", d.sourceField(), "computedType", d.computedType(), "membershipRelation", d.membershipRelation())),
                "cooccurrences", sorted(logical.cooccurrences(), Cooccurrence::id, c -> object("id", c.id(), "fromDerivation", c.fromDerivation(),
                        "toDerivation", c.toDerivation(), "minimum", c.minimum(), "maximum", c.maximum())),
                "computedRules", sorted(logical.computedRules(), CountRule::id, rule -> object("id", rule.id(), "kind", "entity-count", "type", rule.type(),
                        "minimum", rule.minimum(), "maximum", rule.maximum())));
    }
    private static Map<String, Object> semantics() {
        return object("version", BigInteger.valueOf(DerivedSemantics.VERSION), "profileMode", "physical-only-v3",
                "maxDerivations", BigInteger.valueOf(DerivedSemantics.MAX_DERIVATIONS),
                "maxCooccurrences", BigInteger.valueOf(DerivedSemantics.MAX_COOCCURRENCES),
                "maxTotalNodes", BigInteger.valueOf(DerivedSemantics.MAX_TOTAL_NODES),
                "maxTotalEdges", BigInteger.valueOf(DerivedSemantics.MAX_TOTAL_EDGES),
                "maxContributorLinks", BigInteger.valueOf(DerivedSemantics.MAX_CONTRIBUTOR_LINKS),
                "maxIdentityUtf8Bytes", BigInteger.valueOf(DerivedSemantics.MAX_IDENTITY_UTF8_BYTES));
    }
    private static Map<String, Object> binding(Binding binding) {
        return object("engine", token(binding.engine()), "storage", token(binding.storage()), "schema", binding.schema(), "table", binding.table(),
                "keyColumn", binding.keyColumn(), "xmlColumn", binding.xmlColumn(), "keyType", token(binding.keyType()),
                "documents", sorted(binding.documents(), Document::id, document -> object("id", document.id(), "key", document.key(),
                        "entities", sorted(document.entities(), Projection::id, projection -> object("id", projection.id(), "type", projection.type(),
                                "path", projection.path().stream().map(NativeDigests::name).toList(),
                                "fields", sorted(projection.fields(), FieldMapping::field, NativeDigests::field),
                                "references", sorted(projection.references(), ReferenceMapping::relation, mapping -> object("relation", mapping.relation(), "attribute", name(mapping.attribute()))))))));
    }
    private static Map<String, Object> field(FieldMapping mapping) {
        return switch (mapping.locator()) {
            case DirectAttribute direct -> object("field", mapping.field(), "attribute", name(direct.attribute()));
            case ChildProperty child -> object("field", mapping.field(), "childProperty", object(
                    "element", name(child.element()), "discriminatorAttribute", name(child.discriminatorAttribute()),
                    "discriminatorValue", child.discriminatorValue(), "valueAttribute", name(child.valueAttribute())));
        };
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
            digest.update(utf8(domain)); digest.update((byte) 0); frame(digest, value);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("Required native digest is unavailable."); }
    }
    private static void frame(MessageDigest digest, Object value) {
        if (value instanceof String text) {
            byte[] bytes = utf8(text);
            ascii(digest, "S" + bytes.length + ":"); digest.update(bytes);
        } else if (value instanceof BigInteger number) ascii(digest, "I" + number + ";");
        else if (value instanceof Boolean flag) ascii(digest, flag ? "T" : "F");
        else if (value instanceof List<?> list) {
            ascii(digest, "A" + list.size() + ":"); list.forEach(item -> frame(digest, item));
        } else if (value instanceof Map<?, ?> map) {
            ascii(digest, "O" + map.size() + ":");
            map.entrySet().stream().sorted((left, right) -> Arrays.compareUnsigned(utf8((String) left.getKey()),
                    utf8((String) right.getKey()))).forEach(entry -> { frame(digest, entry.getKey()); frame(digest, entry.getValue()); });
        } else throw new IllegalArgumentException("Unsupported native digest frame type.");
    }
    private static byte[] utf8(String value) {
        for (int i = 0; i < value.length(); i++) {
            char unit = value.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw new IllegalArgumentException("Native digest text must contain valid Unicode.");
            } else if (Character.isLowSurrogate(unit)) throw new IllegalArgumentException("Native digest text must contain valid Unicode.");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }
    private static void ascii(MessageDigest digest, String value) { digest.update(value.getBytes(StandardCharsets.US_ASCII)); }
}
