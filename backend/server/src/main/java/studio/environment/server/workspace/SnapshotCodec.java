package studio.environment.server.workspace;

import java.math.BigInteger;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import studio.environment.core.definition.DefinitionResult;
import studio.environment.core.workspace.WorkspaceRefusal;

final class SnapshotCodec {
    static final String COMPILER = "definition-compiler-d01a";
    static final String SCHEMA = "1";
    private static final SimpleModule NUMBERS = new SimpleModule().addSerializer(BigInteger.class, ToStringSerializer.instance);
    private static final JsonMapper JSON = JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder()
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(64)
                    .maxStringLength(1_048_576).maxNumberLength(1024).maxTokenCount(100_000)
                    .maxDocumentLength(2_097_152).build()).build()).addModule(NUMBERS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    byte[] encode(DefinitionResult.Incomplete projection) {
        try { return JSON.writeValueAsBytes(projection); }
        catch (RuntimeException invalid) { throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE); }
    }
    DefinitionResult.Incomplete decode(byte[] bytes) {
        try {
            var projection = JSON.readValue(bytes, DefinitionResult.Incomplete.class);
            if (!SCHEMA.equals(projection.draft().schemaVersion()) || projection.draft().revision().signum() <= 0) throw new IllegalArgumentException();
            return projection;
        } catch (RuntimeException invalid) { throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE); }
    }
    JsonNode view(DefinitionResult.Incomplete projection) { return JSON.valueToTree(projection); }
}
