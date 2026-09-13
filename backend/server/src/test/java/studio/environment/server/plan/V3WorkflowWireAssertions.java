package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.regex.GraalJSRegularExpressionFactory;
import tools.jackson.databind.json.JsonMapper;

/** Validate actual socket responses against canonical repo schemas without printing instance values. */
final class V3WorkflowWireAssertions {
    private static final String PREFIX = "https://environment.studio/schemas/";
    private static final Map<String, String> RESPONSES = Map.of(
            "profile-captures", "captureResponse",
            "profile-previews", "previewResponse",
            "validations", "validationResponse");
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12, builder -> builder
                    .schemaRegistryConfig(SchemaRegistryConfig.builder()
                            .regularExpressionFactory(GraalJSRegularExpressionFactory.getInstance()).build())
                    .schemas(Map.of(
                    PREFIX + "plan-command-v1", resource("plan-command-v1"),
                    PREFIX + "plan-review-v3", resource("plan-review-v3"),
                    PREFIX + "plan-workflow-v3", resource("plan-workflow-v3")))
                    .schemaLoader(loader -> loader.fetchRemoteResources(false)));

    static void verify(String path, int status, String body) {
        if (path.startsWith("/api/v3/plans/")) {
            boolean review=path.endsWith("/reviews");
            String name = review?"response":RESPONSES.get(path.substring(path.lastIndexOf('/') + 1));
            if (name != null && status == 200) {
                var schema = REGISTRY.getSchema(SchemaLocation.of(PREFIX + (review?"plan-review-v3":"plan-workflow-v3")+"#/$defs/" + name));
                var errors = schema.validate(JsonMapper.builder().build().readTree(body));
                assertTrue(errors.isEmpty(), () -> "Actual v3 workflow response violated its closed schema: "
                        + errors.stream().map(error -> error.getSchemaLocation().toString()).distinct().toList());
            }
            return;
        }
        PlanViewWireAssertions.verify(PlanViewWireAssertions.schema(path), status, body);
    }

    private static String resource(String name) {
        try {
            return Files.readString(Path.of("../../schemas/" + name + ".schema.json"), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("MOCK_WORKFLOW_SCHEMA_UNAVAILABLE");
        }
    }
}
