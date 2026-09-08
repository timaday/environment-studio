package studio.environment.server.definition;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import studio.environment.core.definition.DefinitionDiagnostic;

/** Only the packaged canonical schema can be selected; all resource resolution is denied. */
final class DefinitionShapeValidator {
    private final JsonNode schemaTree;
    private final Schema schema;
    enum Version { V1, V2 }
    DefinitionShapeValidator() { this(Version.V1); }
    DefinitionShapeValidator(Version version) {
        String resource = switch (version) {
            case V1 -> "/schemas/definition.schema.json";
            case V2 -> "/schemas/definition-v2.schema.json";
        };
        try (InputStream source = DefinitionShapeValidator.class.getResourceAsStream(resource)) {
            if (source == null) throw new IllegalStateException("The packaged definition schema is unavailable.");
            schemaTree = JsonMapper.builder().build().readTree(source);
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7,
                    builder -> builder.schemaLoader(loader -> loader.fetchRemoteResources(false).block(iri -> true)))
                    .getSchema(schemaTree);
        } catch (IOException exception) {
            throw new IllegalStateException("The packaged definition schema could not be loaded.");
        }
    }
    List<DefinitionDiagnostic> validate(JsonNode tree) {
        return DefinitionDiagnostic.ordered(schema.validate(tree).stream().map(error ->
                new DefinitionDiagnostic(DefinitionDiagnostic.Phase.SHAPE, "SCHEMA_VIOLATION",
                        safePointer(error.getInstanceLocation().toString()),
                        "Conform this value to the definition schema's required properties, types and constraints.")).toList());
    }
    private String safePointer(String pointer) {
        if (pointer.isEmpty()) return "";
        JsonNode scope = schemaTree;
        StringBuilder safe = new StringBuilder();
        for (String encoded : pointer.substring(1).split("/")) {
            String segment = encoded.replace("~1", "/").replace("~0", "~");
            if (scope.has("items")) {
                if (!segment.matches("0|[1-9][0-9]*")) break;
                scope = scope.get("items");
            } else {
                JsonNode properties = scope.get("properties");
                if (properties == null || !properties.has(segment)) break;
                scope = properties.get(segment);
            }
            safe.append('/').append(encoded);
        }
        return safe.toString();
    }
}
