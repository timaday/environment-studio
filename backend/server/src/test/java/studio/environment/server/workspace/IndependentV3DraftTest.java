package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

/** Lead-authored integration controls: actual compiler, service and private SQLite. */
class IndependentV3DraftTest {
    Path directory;
    final Owner owner = new Owner("https://invented.invalid", "draft-owner");
    @BeforeEach void setup() throws Exception {
        directory = Files.createTempDirectory("es-v3-draft-independent-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        SqliteDraftStore.initializeV3(directory);
    }
    @AfterEach void cleanup() throws Exception {
        try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
        Files.delete(directory);
    }
    NativeCommand.SaveDefinition command(String id, String expected, DraftCommand.Format format, String source) {
        return new NativeCommand.SaveDefinition(id, expected, UUID.randomUUID().toString(), format, source);
    }
    @Test void exactHistoricalReplaySurvivesRevisionsRestartAndUnavailableCompiler() throws Exception {
        String json = Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
        // JSON is valid YAML; retain the requested format and literal source including whitespace.
        String yaml = "---\n" + json + "\r\n";
        var store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var service = new V3NativeWorkspace(store, new V3NativeWorkspaceCompiler());
        var original = command(UUID.randomUUID().toString(), "0", DraftCommand.Format.YAML, yaml);
        var first = service.saveDefinition(owner, original);
        var second = service.saveDefinition(owner, command(first.objectId(), "1", DraftCommand.Format.JSON, json));
        assertEquals("2", second.workspaceRevision()); assertEquals(yaml, first.source());
        assertEquals(DraftCommand.Format.YAML, first.format()); assertTrue(first.publication().isEmpty());
        assertFalse(((V3NativeRevision.Definition) first.content()).historicalReady());
        var restarted = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var noCompiler = new V3NativeWorkspace(restarted, ignored -> { throw new AssertionError("Historical replay recompiled."); });
        assertEquals(first, noCompiler.saveDefinition(owner, original));
        var collision = new NativeCommand.SaveDefinition(original.objectId(), original.expectedRevision(), original.requestId(), original.format(), yaml + "\n");
        assertEquals(WorkspaceRefusal.Code.CONFLICT, assertThrows(WorkspaceRefusal.class,
                () -> noCompiler.saveDefinition(owner, collision)).code());
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND, assertThrows(WorkspaceRefusal.class,
                () -> noCompiler.saveDefinition(new Owner(owner.issuer(), "foreign"), original)).code());
        assertEquals(List.of(second), restarted.list(owner, false));
    }
    @Test void realRejectionAndRevokedCommitCannotLeaveDraftOrSuccessfulReplay() throws Exception {
        var shared = new SqliteDraftStore(directory); var store = new V3NativeSqliteStore(shared);
        var service = new V3NativeWorkspace(store, new V3NativeWorkspaceCompiler());
        String id = UUID.randomUUID().toString();
        assertThrows(WorkspaceRejection.class, () -> service.saveDefinition(owner, command(id, "0", DraftCommand.Format.JSON, "{}")));
        assertTrue(store.list(owner, false).isEmpty());
        var valid = command(id, "0", DraftCommand.Format.JSON, Files.readString(Path.of("../../fixtures/native-v3/definition.json")));
        var revoked = new V3NativeWorkspace(new V3NativeSqliteStore(shared.withCommit(connection -> {
            throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);
        })), new V3NativeWorkspaceCompiler());
        assertEquals(WorkspaceRefusal.Code.FORBIDDEN, assertThrows(WorkspaceRefusal.class,
                () -> revoked.saveDefinition(owner, valid)).code());
        var restarted = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        assertTrue(restarted.list(owner, false).isEmpty()); assertTrue(restarted.replay(owner, valid).isEmpty());
        assertEquals("1", new V3NativeWorkspace(restarted, new V3NativeWorkspaceCompiler()).saveDefinition(owner, valid).workspaceRevision());
    }
}
