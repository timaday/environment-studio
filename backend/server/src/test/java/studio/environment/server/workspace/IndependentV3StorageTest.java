package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

class IndependentV3StorageTest {
    Path directory;
    final Owner owner = new Owner("https://independent.invalid", "invented-owner");
    @BeforeEach void setup() throws Exception {
        directory = Files.createTempDirectory("es-independent-storage-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }
    @AfterEach void cleanup() throws Exception {
        try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
        Files.delete(directory);
    }
    @Test void upgradeCommitBlockedByAnOwnedReaderRollsBackActualDdlAndPreservesOldReplay() throws Exception {
        SqliteDraftStore.initialize(directory);
        var old = new NativeSqliteStore(new SqliteDraftStore(directory));
        var command = new NativeCommand.SaveDefinition(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(),
                DraftCommand.Format.JSON, Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
        var saved = new NativeWorkspace(old, new NativeWorkspaceCompiler(), ignored -> false).mutate(owner, command);
        Path database = directory.resolve(PrivateWorkspacePath.DATABASE);
        byte[] original = Files.readAllBytes(database);
        try (var watcher = FileSystems.getDefault().newWatchService();
                var reader = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
            reader.setAutoCommit(false);
            try (var statement = reader.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM catalog")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
            }
            V3NativeSqliteStoreTest.refusal(WorkspaceRefusal.Code.UNAVAILABLE, () -> SqliteDraftStore.upgradeV3(directory));
            boolean journalCreated = false;
            WatchKey key;
            while ((key = watcher.poll()) != null) {
                for (var event : key.pollEvents()) if (event.context().toString().equals(PrivateWorkspacePath.DATABASE + "-journal")) journalCreated = true;
                key.reset();
            }
            assertTrue(journalCreated, "Upgrade must reach real SQLite writes before blocked commit.");
            reader.rollback();
        }
        assertArrayEquals(original, Files.readAllBytes(database));
        var reopened = new SqliteDraftStore(directory);
        assertEquals(2, reopened.storageVersion());
        assertEquals(saved, new NativeSqliteStore(reopened).replay(owner, command).orElseThrow());
        SqliteDraftStore.upgradeV3(directory);
        assertEquals(3, new SqliteDraftStore(directory).storageVersion());
    }
    @Test void alreadyConstructedV3StoreReauditsCorruptionBeforeReadListAndReplay() throws Exception {
        SqliteDraftStore.initializeV3(directory);
        var store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var revision = V3NativeSnapshotCodecTest.draft();
        var command = V3NativeSqliteStoreTest.command(revision.objectId(), "0", revision.source());
        store.append(owner, command, revision);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve(PrivateWorkspacePath.DATABASE));
                var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE v3_native_replays SET request_digest='00'");
        }
        V3NativeSqliteStoreTest.refusal(WorkspaceRefusal.Code.UNAVAILABLE, () -> store.read(owner, revision.objectId(), Optional.empty(), false));
        V3NativeSqliteStoreTest.refusal(WorkspaceRefusal.Code.UNAVAILABLE, () -> store.list(owner, false));
        V3NativeSqliteStoreTest.refusal(WorkspaceRefusal.Code.UNAVAILABLE, () -> store.replay(owner, command));
    }
    @Test void revisionSourceAndFormatMustMatchTheActualCommandBeforeAnyAppend() throws Exception {
        SqliteDraftStore.initializeV3(directory);
        var store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var original = V3NativeSnapshotCodecTest.draft();
        var command = V3NativeSqliteStoreTest.command(original.objectId(), "0", original.source());
        var changed = V3NativeSnapshotCodecTest.changed(original, original.source() + " ", original.content(), "1", Optional.empty());
        V3NativeSqliteStoreTest.refusal(WorkspaceRefusal.Code.CONFLICT, () -> store.append(owner, command, changed));
        var otherFormat = new V3NativeRevision(original.objectId(), "1", DraftCommand.Format.YAML, original.source(),
                original.sourceDigest(), original.compilerVersion(), original.schemaVersion(), original.content(), Optional.empty());
        V3NativeSqliteStoreTest.refusal(WorkspaceRefusal.Code.CONFLICT, () -> store.append(owner, command, otherFormat));
        assertTrue(store.list(owner, false).isEmpty()); assertTrue(store.replay(owner, command).isEmpty());
        assertEquals(original, store.append(owner, command, original));
    }
}
