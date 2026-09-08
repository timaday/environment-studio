package studio.environment.server.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Set;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import studio.environment.core.workspace.DraftCommand;
import studio.environment.core.workspace.WorkspaceRefusal;

public final class DraftRequestReader {
    static final int MAX_BODY = 8 * 1024 * 1024;
    private static final JsonFactory JSON = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2).maxTokenCount(16)
                    .maxStringLength(MAX_BODY).maxNameLength(64).maxDocumentLength(MAX_BODY).build()).build();
    public DraftCommand read(String objectId, InputStream body) {
        try {
            byte[] bytes = body.readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
            try (var parser = JSON.createParser(StrictUtf8.decode(bytes))) {
                if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
                var values = new HashMap<String, String>();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.PROPERTY_NAME) throw invalid();
                    String name = parser.currentName();
                    if (!Set.of("expectedRevision", "requestId", "format", "source").contains(name)) throw invalid();
                    if (parser.nextToken() != JsonToken.VALUE_STRING) throw invalid();
                    values.put(name, parser.getString());
                }
                if (values.size() != 4 || parser.nextToken() != null) throw invalid();
                if (StrictUtf8.encode(values.get("source")).length > 1_048_576) throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
                return new DraftCommand(objectId, values.get("expectedRevision"), values.get("requestId"),
                        DraftCommand.Format.valueOf(values.get("format")), values.get("source"));
            }
        } catch (IOException | tools.jackson.core.JacksonException | IllegalArgumentException invalid) { throw invalid(); }
    }
    private static WorkspaceRefusal invalid() { return new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST); }
}
