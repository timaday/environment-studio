package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.workspace.*;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;
import studio.environment.server.session.HostedSessions;

class V3ProfileControllerTest {
    V3WorkspaceControllerTest fixture;
    V3WorkspaceControllerTest.Request original;
    SessionLedger.Lease lease;
    V3NativeRevision definition;
    @BeforeEach void setup() throws Exception {
        fixture = new V3WorkspaceControllerTest(); fixture.setup();
        original = fixture.request("profile-owner");
        lease = (SessionLedger.Lease) original.getAttribute(HostedSessions.REQUEST_LEASE);
        definition = V3ProfileHttpFixtures.definition(fixture.directory, lease.owner(), false);
    }
    @AfterEach void cleanup() throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (fixture.runtime.v3Operations().activeCount() != 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(0, fixture.runtime.v3Operations().activeCount());
        try (var files = Files.list(fixture.directory)) { for (var file : files.toList()) Files.delete(file); }
        Files.delete(fixture.directory);
    }
    V3WorkspaceControllerTest.Request request() {
        var request = new V3WorkspaceControllerTest.Request();
        request.setAsyncSupported(true); request.setContentType("application/json");
        request.setAttribute(HostedSessions.REQUEST_LEASE, lease); return request;
    }
    NativeCommand.Reference reference() { return new NativeCommand.Reference(definition.objectId(), "2"); }
    V3WorkspaceControllerTest.Request save(String id, byte[] bytes) throws Exception {
        var request = request(); request.setContent(bytes);
        fixture.controller.saveProfile(id, request, request.response); request.await(); return request;
    }
    @Test void jsonYamlExactHistoryReplayAndClosedListUseHistoricalStructuralProjection() throws Exception {
        for (String format : List.of("JSON", "YAML")) {
            String source = (format.equals("YAML") ? "---\n" : "") + V3ProfileHttpFixtures.source() + "\r\n";
            String id = UUID.randomUUID().toString();
            byte[] body = V3ProfileHttpFixtures.body(source, format, "0", UUID.randomUUID().toString(), reference());
            var first = save(id, body); assertEquals(200, first.response.getStatus());
            var json = V3NativeSnapshotCodec.JSON.readTree(first.response.getContentAsByteArray());
            assertEquals(source, json.get("source").asString()); assertEquals(format, json.get("format").asString());
            assertEquals("structurally-valid", json.at("/projection/kind").asString());
            assertEquals("1", json.at("/projection/model/revision").asString());
            assertFalse(json.has("publication")); assertFalse(json.has("canPublish"));
            assertEquals(Set.of("kind", "model", "contentDigest", "diagnostics"), json.get("projection").propertyNames());
            assertEquals(200, save(id, V3ProfileHttpFixtures.body(source + "\n", format, "1", UUID.randomUUID().toString(), reference())).response.getStatus());
            var replay = save(id, body); assertArrayEquals(first.response.getContentAsByteArray(), replay.response.getContentAsByteArray());
            var history = request(); fixture.controller.profileRevision(id, "1", history, history.response); history.await();
            assertArrayEquals(first.response.getContentAsByteArray(), history.response.getContentAsByteArray());
            var current = request(); fixture.controller.profile(id, current, current.response); current.await();
            assertEquals("2", V3NativeSnapshotCodec.JSON.readTree(current.response.getContentAsByteArray()).get("workspaceRevision").asString());
        }
        var list = request(); fixture.controller.profiles(list, list.response); list.await();
        var entries = V3NativeSnapshotCodec.JSON.readTree(list.response.getContentAsByteArray()).get("profiles");
        assertEquals(2, entries.size()); assertTrue(entries.get(0).get("objectId").asString().compareTo(entries.get(1).get("objectId").asString()) < 0);
        for (var entry : entries) assertEquals(Set.of("objectId", "workspaceRevision", "nativeId", "nativeRevision", "sourceDigest", "state", "contentDigest", "definition"), entry.propertyNames());
    }
    @Test void publishedHistoricalProfileRemainsDataAfterLaterDefinitionDraftAndRestart() throws Exception {
        var profile = V3ProfileHttpFixtures.save(fixture.directory, lease.owner(), definition, V3ProfileHttpFixtures.source());
        var two = new V3NativeRevision(profile.objectId(), "2", profile.format(), profile.source(), profile.sourceDigest(), profile.compilerVersion(), "3", profile.content(), Optional.empty());
        var publication = new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(two, "1", List.of()), "1", List.of());
        fixture.runtime.v3Store().append(lease.owner(), new NativeCommand.PublishProfile(profile.objectId(), "1", UUID.randomUUID().toString()),
                new V3NativeRevision(two.objectId(), "2", two.format(), two.source(), two.sourceDigest(), two.compilerVersion(), "3", two.content(), Optional.of(publication)));
        V3ProfileHttpFixtures.laterDefinition(fixture.directory, lease.owner(), definition);
        var restarted = new V3NativeWorkspaceController(new WorkspaceRuntime(new MockEnvironment().withProperty("studio.workspace.directory", fixture.directory.toString()), RuntimeMode.HOSTED), fixture.sessions);
        var request = request(); restarted.profile(profile.objectId(), request, request.response); request.await();
        assertEquals(200, request.response.getStatus());
        var view = V3NativeSnapshotCodec.JSON.readTree(request.response.getContentAsByteArray());
        assertEquals("published", view.get("state").asString());
        assertEquals(Set.of("digest", "sourceRevision"), view.get("publication").propertyNames());
        assertEquals("structurally-valid", view.at("/projection/kind").asString());
        assertEquals("2", view.at("/definition/workspaceRevision").asString());
    }
    @Test void schemaTwoAndUnconfiguredRemainUnavailableAndNoAutomaticUpgradeOccurs() throws Exception {
        var old = Files.createTempDirectory("es-profile-schema2-"); SqliteDraftStore.initialize(old);
        byte[] before = Files.readAllBytes(old.resolve(PrivateWorkspacePath.DATABASE));
        for (var environment : List.of(new MockEnvironment(), new MockEnvironment().withProperty("studio.workspace.directory", old.toString()))) {
            var controller = new V3NativeWorkspaceController(new WorkspaceRuntime(environment, RuntimeMode.HOSTED), fixture.sessions);
            var request = request(); controller.profiles(request, request.response); request.await(); assertEquals(503, request.response.getStatus());
        }
        assertArrayEquals(before, Files.readAllBytes(old.resolve(PrivateWorkspacePath.DATABASE)));
    }
    @Test void wrongOwnerKindVersionUnpublishedAndUnsupportedReferencesRefuse() throws Exception {
        String source = V3ProfileHttpFixtures.source();
        var foreign = V3ProfileHttpFixtures.definition(fixture.directory, new studio.environment.core.session.Owner(lease.owner().issuer(), "foreign-owner"), false);
        var unsupported = V3ProfileHttpFixtures.definition(fixture.directory, lease.owner(), true);
        var profile = V3ProfileHttpFixtures.save(fixture.directory, lease.owner(), definition, source);
        var references = List.of(new NativeCommand.Reference(foreign.objectId(), "2"), new NativeCommand.Reference(profile.objectId(), "1"),
                new NativeCommand.Reference(definition.objectId(), "1"), new NativeCommand.Reference(unsupported.objectId(), "2"));
        var expected = List.of(404, 404, 422, 422);
        for (int i = 0; i < references.size(); i++) assertEquals(expected.get(i), save(UUID.randomUUID().toString(),
                V3ProfileHttpFixtures.body(source, "JSON", "0", UUID.randomUUID().toString(), references.get(i))).response.getStatus());
        var wrongRead = request(); fixture.controller.profile(definition.objectId(), wrongRead, wrongRead.response); wrongRead.await(); assertEquals(404, wrongRead.response.getStatus());
        var old = fixture.runtime.nativeService().mutate(lease.owner(), new NativeCommand.SaveDefinition(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), DraftCommand.Format.JSON, Files.readString(Path.of("../../fixtures/native-v2/definition.json"))));
        assertEquals(404, save(UUID.randomUUID().toString(), V3ProfileHttpFixtures.body(source, "JSON", "0", UUID.randomUUID().toString(), new NativeCommand.Reference(old.objectId(), "1"))).response.getStatus());
        assertEquals(409, save(old.objectId(), V3ProfileHttpFixtures.body(source, "JSON", "0", UUID.randomUUID().toString(), reference())).response.getStatus());
    }
    @Test void sourceAndSnapshotBudgetsRefuseWithoutSaving() throws Exception {
        String source = V3ProfileHttpFixtures.source();
        for (String tooLarge : List.of(" ".repeat(1_048_577), "\t".repeat(1_048_576 - StrictUtf8.encode(source).length) + source)) {
            assertEquals(413, save(UUID.randomUUID().toString(), V3ProfileHttpFixtures.body(tooLarge, "JSON", "0", UUID.randomUUID().toString(), reference())).response.getStatus());
        }
        assertTrue(fixture.runtime.v3Store().list(lease.owner(), true).isEmpty());
    }

    @Test void revocationAfterCommitAtOutputAcquisitionDisclosesNoSourceAndOriginalCommandReplays() throws Exception {
        String id = UUID.randomUUID().toString();
        String source = V3ProfileHttpFixtures.source().replace("First neutral slot", "INVENTED-REVOKED-PROFILE");
        byte[] body = V3ProfileHttpFixtures.body(source, "JSON", "0", UUID.randomUUID().toString(), reference());
        var request = request(); request.setContent(body);
        var response = new V3WorkspaceControllerTest.Response() {
            boolean revoked;
            @Override public jakarta.servlet.ServletOutputStream getOutputStream() {
                if (!revoked) { revoked = true; fixture.sessions.logout(original); }
                return super.getOutputStream();
            }
        };
        fixture.controller.saveProfile(id, request, response); request.await();
        assertEquals(403, response.getStatus()); assertFalse(response.getContentAsString().contains("INVENTED-REVOKED-PROFILE"));
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (fixture.runtime.v3Operations().activeCount() != 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(0, fixture.runtime.v3Operations().activeCount());
        var authenticated = fixture.request("profile-owner");
        lease = (SessionLedger.Lease) authenticated.getAttribute(HostedSessions.REQUEST_LEASE);
        var replay = save(id, body); assertEquals(200, replay.response.getStatus());
        var json = V3NativeSnapshotCodec.JSON.readTree(replay.response.getContentAsByteArray());
        assertEquals("1", json.get("workspaceRevision").asString()); assertEquals(source, json.get("source").asString());
    }

    @Test void revocationDuringActualSqliteReplayWaitCannotCommitProfileOrReplay() throws Exception {
        String id = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        String source = V3ProfileHttpFixtures.source();
        byte[] body = V3ProfileHttpFixtures.body(source, "JSON", "0", requestId, reference());
        var command = new NativeCommand.SaveProfile(id, "0", requestId, DraftCommand.Format.JSON, source, reference());
        var request = request(); request.setContent(body);
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + fixture.directory.resolve(PrivateWorkspacePath.DATABASE));
                var statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
            try {
                fixture.controller.saveProfile(id, request, request.response);
                long deadline = System.nanoTime() + 3_000_000_000L;
                boolean reachedReplay = false;
                while (!reachedReplay && System.nanoTime() < deadline) {
                    reachedReplay = Thread.getAllStackTraces().entrySet().stream()
                            .filter(entry -> entry.getKey().getName().equals("hosted-v3-workspace"))
                            .anyMatch(entry -> Arrays.stream(entry.getValue()).anyMatch(frame ->
                                    frame.getClassName().equals(V3NativeSqliteStore.class.getName()) && frame.getMethodName().equals("replay")));
                    if (!reachedReplay) Thread.sleep(5);
                }
                assertTrue(reachedReplay, "Actual worker must reach replay while owned SQLite write lock is held");
                fixture.sessions.logout(original);
            } finally { statement.execute("ROLLBACK"); }
        }
        request.await(); assertEquals(403, request.response.getStatus());
        assertTrue(fixture.runtime.v3Store().list(lease.owner(), true).isEmpty());
        assertTrue(fixture.runtime.v3Store().replay(lease.owner(), command).isEmpty());
    }

    @Test void historicalProfileReadDoesNotRecompileItsUnsupportedDefinitionVector() throws Exception {
        var existing = V3ProfileHttpFixtures.save(fixture.directory, lease.owner(), definition, V3ProfileHttpFixtures.source());
        var unsupported = V3ProfileHttpFixtures.definition(fixture.directory, lease.owner(), true);
        var reference = new NativeCommand.Reference(unsupported.objectId(), "2");
        var content = new V3NativeRevision.Profile(((V3NativeRevision.Profile) existing.content()).checked(), reference);
        var historical = new V3NativeRevision(UUID.randomUUID().toString(), "1", existing.format(), existing.source(), existing.sourceDigest(),
                existing.compilerVersion(), "3", content, Optional.empty());
        fixture.runtime.v3Store().append(lease.owner(), new NativeCommand.SaveProfile(historical.objectId(), "0", UUID.randomUUID().toString(),
                historical.format(), historical.source(), reference), historical);
        var request = request(); fixture.controller.profile(historical.objectId(), request, request.response); request.await();
        assertEquals(200, request.response.getStatus());
        var view = V3NativeSnapshotCodec.JSON.readTree(request.response.getContentAsByteArray());
        assertEquals("structurally-valid", view.at("/projection/kind").asString());
        assertEquals(unsupported.objectId(), view.at("/definition/objectId").asString());
        assertFalse(view.has("canPublish")); assertFalse(view.has("publication"));
    }
}
