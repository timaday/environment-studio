package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import studio.environment.server.session.HostedSessions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Independent mock declarations, actual compiler/SQLite/controllers and serialized v3 output. */
class NativeDefinitionCapabilitiesProjectionTest {
    private static final List<String> DECLARED = List.of("move-relation", "retain-entity", "create-entity", "bind-field", "remove-entity");

    @Test void v3SaveCurrentHistoryAndReplayPreserveDeclaredCapabilities() throws Exception {
        checkCurrentAndHistory(3);
    }

    @Test void v2SaveCurrentHistoryAndReplayPreserveDeclaredCapabilities() throws Exception {
        checkCurrentAndHistory(2);
    }

    private static void checkCurrentAndHistory(int version) throws Exception {
        var fixture = new V3WorkspaceControllerTest();
        fixture.setup();
        var login = fixture.request("independent-capabilities-" + version);
        var legacy = new NativeWorkspaceController(fixture.runtime, fixture.sessions);
        String id = UUID.randomUUID().toString();
        String source = source(version, DECLARED);
        byte[] command = V3WorkspaceControllerTest.body(source, "0", UUID.randomUUID().toString());
        JsonNode saved = save(version, fixture, legacy, id, copy(login, command));
        var owner = fixture.sessions.current(login).orElseThrow().owner();
        byte[] storedBefore = snapshot(version, fixture, owner, id);

        // A genuinely changed current revision must not replace the first revision's display.
        String emptySource = source(version, List.of());
        JsonNode second = save(version, fixture, legacy, id, copy(login,
                V3WorkspaceControllerTest.body(emptySource, "1", UUID.randomUUID().toString())));
        JsonNode current = read(version, fixture, legacy, id, null, copy(login, new byte[0]));
        JsonNode history = read(version, fixture, legacy, id, "1", copy(login, new byte[0]));
        JsonNode replay = save(version, fixture, legacy, id, copy(login, command));

        assertAll(
                () -> assertCapabilities(DECLARED, saved),
                () -> assertCapabilities(DECLARED, history),
                () -> assertCapabilities(DECLARED, replay),
                () -> assertCapabilities(List.of(), second),
                () -> assertCapabilities(List.of(), current),
                () -> assertEquals(source, saved.get("source").asString()),
                () -> assertEquals(emptySource, current.get("source").asString()),
                () -> assertEquals("2", current.get("workspaceRevision").asString()),
                () -> assertEquals(saved, history),
                () -> assertEquals(saved, replay),
                () -> assertArrayEquals(storedBefore, snapshot(version, fixture, owner, id)),
                () -> assertFalse(saved.has("publication")),
                () -> assertFalse(current.has("publication")));
        if (version == 3) {
            assertEquals("incomplete", saved.at("/projection/kind").asString());
            assertEquals("incomplete", current.at("/projection/kind").asString());
        }
    }

    @Test void originalV2HistoricalSnapshotKeepsExactBytesAndIndependentCapabilityOrder() throws Exception {
        byte[] original = Files.readAllBytes(Path.of("../../fixtures/native-v2/historical-direct-snapshot.json"));
        var codec = new NativeSnapshotCodec();
        var revision = codec.decode(original);
        JsonNode view = new NativeWorkspaceController(null, null).view(revision).body();
        assertAll(
                () -> assertCapabilities(List.of("retain-entity", "create-entity", "remove-entity", "bind-field", "move-relation"), view),
                () -> assertArrayEquals(original, codec.encode(revision)),
                () -> assertEquals(revision.source(), view.get("source").asString()),
                () -> assertEquals(((studio.environment.core.workspace.NativeRevision.Definition) revision.content())
                        .checked().logicalDigest(), view.at("/projection/logicalDigest").asString()));
    }

    private static String source(int version, List<String> capabilities) throws Exception {
        var tree = (ObjectNode) V3NativeSnapshotCodec.JSON.readTree(
                Files.readString(Path.of("../../fixtures/native-v" + version + "/definition.json")));
        var operations = ((ObjectNode) tree.get("logical")).putArray("operationCapabilities");
        capabilities.forEach(operations::add);
        return V3NativeSnapshotCodec.JSON.writeValueAsString(tree);
    }

    private static void assertCapabilities(List<String> expected, JsonNode response) {
        assertEquals(V3NativeSnapshotCodec.JSON.valueToTree(expected), response.at("/projection/model/logical/operationCapabilities"));
    }

    private static byte[] snapshot(int version, V3WorkspaceControllerTest fixture,
            studio.environment.core.session.Owner owner, String id) {
        if (version == 3) {
            var revision = fixture.runtime.v3Store().read(owner, id, Optional.of("1"), false);
            return new V3NativeSnapshotCodec().encode(revision);
        }
        return new NativeSnapshotCodec().encode(fixture.runtime.nativeStore().read(owner, id, Optional.of("1"), false));
    }

    private static V3WorkspaceControllerTest.Request copy(V3WorkspaceControllerTest.Request original, byte[] body) {
        var request = new V3WorkspaceControllerTest.Request();
        request.setAsyncSupported(true);
        request.setContentType("application/json");
        request.setSession((MockHttpSession) original.getSession());
        request.setAttribute(HostedSessions.REQUEST_LEASE, original.getAttribute(HostedSessions.REQUEST_LEASE));
        request.setContent(body);
        return request;
    }

    private static JsonNode save(int version, V3WorkspaceControllerTest fixture, NativeWorkspaceController legacy,
            String id, V3WorkspaceControllerTest.Request request) throws Exception {
        if (version == 2) return body(legacy.saveDefinition(id, request));
        fixture.controller.saveDefinition(id, request, request.response);
        request.await();
        assertEquals(200, request.response.getStatus());
        return V3NativeSnapshotCodec.JSON.readTree(request.response.getContentAsByteArray());
    }

    private static JsonNode read(int version, V3WorkspaceControllerTest fixture, NativeWorkspaceController legacy,
            String id, String revision, V3WorkspaceControllerTest.Request request) throws Exception {
        if (version == 2) return body(revision == null ? legacy.definition(id, request)
                : legacy.definitionRevision(id, revision, request));
        if (revision == null) fixture.controller.definition(id, request, request.response);
        else fixture.controller.definitionRevision(id, revision, request, request.response);
        request.await();
        assertEquals(200, request.response.getStatus());
        return V3NativeSnapshotCodec.JSON.readTree(request.response.getContentAsByteArray());
    }

    private static JsonNode body(ResponseEntity<?> response) {
        assertEquals(200, response.getStatusCode().value());
        return assertInstanceOf(NativeWorkspaceController.View.class, response.getBody()).body();
    }
}
