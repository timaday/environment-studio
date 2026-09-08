package studio.environment.server.plan;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.networknt.schema.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Validate actual socket results against packaged closed contracts without printing instance values. */
final class PlanViewWireAssertions {
    private static final String PREFIX="https://environment.studio/schemas/";
    private static final Map<String,String> ROUTES=Map.ofEntries(
        Map.entry("/materializations","materializationResponse"),Map.entry("/views/documents","documentsResponse"),Map.entry("/views/entities","entitiesResponse"),
        Map.entry("/views/relations","relationsResponse"),Map.entry("/views/draft","draftResponse"),Map.entry("/views/containment","containmentResponse"),
        Map.entry("/views/placements","placementsResponse"),Map.entry("/views/document","documentResponse"),Map.entry("/profile-captures","captureResponse"),
        Map.entry("/profile-previews","previewResponse"),Map.entry("/validations","validationResponse"));
    private static final SchemaRegistry REGISTRY=SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,builder->builder.schemas(Map.of(PREFIX+"plan-command-v1",resource("plan-command-v1"),PREFIX+"plan-view-v1",resource("plan-view-v1"))).schemaLoader(loader->loader.fetchRemoteResources(false)));
    static String schema(String path){if(!path.startsWith("/api/v1/plans/"))return null;return ROUTES.entrySet().stream().filter(e->path.endsWith(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);}
    static void verify(String schema,int status,String body){
        if(schema==null || status!=200)return;
        var validator=REGISTRY.getSchema(SchemaLocation.of(PREFIX+"plan-view-v1#/$defs/"+schema));
        assertTrue(validator.validate(JsonMapper.builder().build().readTree(body)).isEmpty(),"Actual plan view response violated its closed schema");
    }
    private static String resource(String name){try(var input=PlanViewWireAssertions.class.getResourceAsStream("/schemas/"+name+".schema.json")){if(input==null)throw new IllegalStateException("MOCK_VIEW_SCHEMA_UNAVAILABLE");return new String(input.readAllBytes(),StandardCharsets.UTF_8);}catch(IOException failure){throw new IllegalStateException("MOCK_VIEW_SCHEMA_UNAVAILABLE");}}
}
