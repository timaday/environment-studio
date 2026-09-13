package studio.environment.server.workspace;

import java.util.Set;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.module.SimpleModule;
import static studio.environment.core.definitionv2.NativeDefinition.*;

/** Preserve historical direct-field bytes while encoding the closed new variant explicitly. */
final class NativeFieldMappingCodec {
    private NativeFieldMappingCodec() { }
    static SimpleModule module() {
        return new SimpleModule().addSerializer(FieldMapping.class, new Writer())
                .addDeserializer(FieldMapping.class, new Reader());
    }
    private static final class Writer extends ValueSerializer<FieldMapping> {
        @Override public void serialize(FieldMapping mapping, JsonGenerator out, SerializationContext context) {
            out.writeStartObject(mapping); out.writeStringProperty("field", mapping.field());
            switch (mapping.locator()) {
                case DirectAttribute direct -> { out.writeName("attribute"); context.writeValue(out, direct.attribute()); }
                case ChildProperty child -> { out.writeName("childProperty"); context.writeValue(out, child); }
            }
            out.writeEndObject();
        }
    }
    private static final class Reader extends ValueDeserializer<FieldMapping> {
        @Override public FieldMapping deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode node = context.readTree(parser);
            if (!node.isObject() || !node.has("field") || !node.get("field").isString()) throw invalid();
            String field = node.get("field").asString();
            if (node.propertyNames().equals(Set.of("field", "attribute")))
                return new FieldMapping(field, context.readTreeAsValue(node.get("attribute"), ExpandedName.class));
            if (node.propertyNames().equals(Set.of("field", "childProperty")))
                return new FieldMapping(field, context.readTreeAsValue(node.get("childProperty"), ChildProperty.class));
            throw invalid();
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid stored field mapping."); }
}
