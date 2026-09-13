package studio.environment.server.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import studio.environment.core.workspace.*;

/** Closed v3 profile command wrapper; never reads a caller-supplied checked model. */
final class V3ProfileRequestReader {
    private static final int MAX_BODY = 8 * 1024 * 1024;
    private static final JsonFactory JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2).maxTokenCount(24)
                    .maxStringLength(MAX_BODY).maxNameLength(64).maxDocumentLength(MAX_BODY).build()).build();

    NativeCommand.SaveProfile read(String objectId, InputStream body) {
        byte[] bytes = null;
        try {
            bytes = body.readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
            try (var parser = JSON.createParser(StrictUtf8.decode(bytes))) {
                if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
                var values = new HashMap<String, String>();
                NativeCommand.Reference definition = null;
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.PROPERTY_NAME) throw invalid();
                    String name = parser.currentName();
                    if (name.equals("definition")) {
                        definition = reference(parser);
                    } else {
                        if (!Set.of("expectedRevision", "requestId", "format", "source").contains(name)
                                || parser.nextToken() != JsonToken.VALUE_STRING) throw invalid();
                        values.put(name, parser.getString());
                    }
                }
                if (values.size() != 4 || definition == null || parser.nextToken() != null) throw invalid();
                byte[] source = StrictUtf8.encode(values.get("source"));
                try {
                    if (source.length > 1_048_576) throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
                } finally { Arrays.fill(source, (byte) 0); }
                return new NativeCommand.SaveProfile(objectId, values.get("expectedRevision"), values.get("requestId"),
                        DraftCommand.Format.valueOf(values.get("format")), values.get("source"), definition);
            }
        } catch (IOException | tools.jackson.core.JacksonException | IllegalArgumentException refusal) {
            throw invalid();
        } finally { if (bytes != null) Arrays.fill(bytes, (byte) 0); }
    }

    private static NativeCommand.Reference reference(JsonParser parser) {
        if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
        var fields = new HashMap<String, String>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) throw invalid();
            String name = parser.currentName();
            if (!Set.of("objectId", "workspaceRevision").contains(name)
                    || parser.nextToken() != JsonToken.VALUE_STRING) throw invalid();
            fields.put(name, parser.getString());
        }
        if (fields.size() != 2) throw invalid();
        return new NativeCommand.Reference(fields.get("objectId"), fields.get("workspaceRevision"));
    }

    private static WorkspaceRefusal invalid() { return new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST); }
}
