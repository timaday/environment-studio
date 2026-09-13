package studio.environment.server.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import studio.environment.core.workspace.*;

/** Closed publication commands only; source and historical models are never inputs. */
final class V3PublicationRequestReader {
    private static final int MAX_BODY = 8 * 1024 * 1024;
    private static final JsonFactory JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(3).maxTokenCount(160016)
                    .maxStringLength(MAX_BODY).maxNameLength(64).maxDocumentLength(MAX_BODY).build()).build();

    NativeCommand.PublishDefinition definition(String id, InputStream body) {
        var fields = read(body, true);
        return new NativeCommand.PublishDefinition(id, fields.revision(), fields.request(), fields.policies());
    }
    NativeCommand.PublishProfile profile(String id, InputStream body) {
        var fields = read(body, false);
        return new NativeCommand.PublishProfile(id, fields.revision(), fields.request());
    }
    private record Fields(String revision, String request, List<NativeCommand.Policy> policies) {
        @Override public String toString() { return "PublicationFields[redacted]"; }
    }
    private static Fields read(InputStream body, boolean definition) {
        byte[] bytes = null;
        try {
            bytes = body.readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
            try (var parser = JSON.createParser(StrictUtf8.decode(bytes))) {
                if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
                var fields = new HashMap<String, String>();
                List<NativeCommand.Policy> policies = null;
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.PROPERTY_NAME) throw invalid();
                    String name = parser.currentName();
                    if (definition && name.equals("exportPolicies")) policies = policies(parser);
                    else {
                        if (!Set.of("expectedRevision", "requestId").contains(name)
                                || parser.nextToken() != JsonToken.VALUE_STRING) throw invalid();
                        fields.put(name, parser.getString());
                    }
                }
                if (fields.size() != 2 || (definition && policies == null) || parser.nextToken() != null) throw invalid();
                return new Fields(fields.get("expectedRevision"), fields.get("requestId"),
                        policies == null ? List.of() : policies);
            }
        } catch (tools.jackson.core.JacksonException | IllegalArgumentException malformed) {
            throw invalid();
        } catch (IOException unavailable) {
            throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE);
        } finally { if (bytes != null) Arrays.fill(bytes, (byte) 0); }
    }
    private static List<NativeCommand.Policy> policies(JsonParser parser) {
        if (parser.nextToken() != JsonToken.START_ARRAY) throw invalid();
        var policies = new ArrayList<NativeCommand.Policy>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (policies.size() == 20000 || parser.currentToken() != JsonToken.START_OBJECT) throw invalid();
            var fields = new HashMap<String, String>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.PROPERTY_NAME) throw invalid();
                String name = parser.currentName();
                if (!Set.of("bindingId", "documentId", "content").contains(name)
                        || parser.nextToken() != JsonToken.VALUE_STRING) throw invalid();
                fields.put(name, parser.getString());
            }
            if (fields.size() != 3) throw invalid();
            policies.add(new NativeCommand.Policy(fields.get("bindingId"), fields.get("documentId"), fields.get("content")));
        }
        return policies;
    }
    private static WorkspaceRefusal invalid() { return new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST); }
}
