package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;
import studio.environment.server.definition.DefinitionBytesCompiler;

class SqliteDraftStoreTest {
    Path directory;
    final Owner owner = new Owner("https://mock-issuer.invalid", "invented-owner");
    final String id = "00000000-0000-4000-8000-000000000001";
    // Invented here, independently of all private database/application models.
    static String source(String nativeId, int revision) {
        return "{\"schemaVersion\":\"1\",\"id\":\"" + nativeId + "\",\"revision\":" + revision
                + ",\"status\":\"draft\",\"entityTypes\":[{\"id\":\"mote\",\"label\":\"Invented mote\",\"fields\":[]}],"
                + "\"relations\":[],\"documents\":[{\"id\":\"sample\",\"logicalStore\":\"mock-store\",\"recordKey\":\"mock-key\",\"namespaces\":{},\"mappings\":[]}],"
                + "\"requiredRules\":[\"mock-rule\"],\"operationCapabilities\":[]}";
    }
    @BeforeEach void privateDirectory() throws Exception {
        directory = Files.createTempDirectory("es-workspace-mock-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }
    DraftCommand command(String expected, int nativeRevision) { return new DraftCommand(id, expected, UUID.randomUUID().toString(), DraftCommand.Format.JSON, source("invented", nativeRevision)); }
    DraftWorkspace service(SqliteDraftStore store) {
        return new DraftWorkspace(store, command -> new DefinitionBytesCompiler().compile(StrictUtf8.encode(command.source()), DefinitionBytesCompiler.Format.valueOf(command.format().name())));
    }
    SavedDraft put(DraftWorkspace workspace, DraftCommand command) { return ((DraftWorkspace.Saved) workspace.put(owner, command)).draft(); }
    @Test void initializerCreatesExplicitSchemaTwo() throws Exception {
        SqliteDraftStore.initialize(directory);
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve(PrivateWorkspacePath.DATABASE));
                var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(rows.next()); assertEquals(2, rows.getInt(1));
        }
    }
    @Test void persistsCreateUpdateOriginalReplayAndOwnerIsolationAcrossRestart() {
        SqliteDraftStore.initialize(directory);
        var store = new SqliteDraftStore(directory);
        var firstCommand = command("0", 1);
        var first = put(service(store), firstCommand);
        assertEquals("1", first.workspaceRevision());
        assertEquals(firstCommand.source(), first.source());
        assertEquals("2", put(service(store), command("1", 2)).workspaceRevision());
        var reopened = new SqliteDraftStore(directory);
        assertEquals(first, put(service(reopened), firstCommand));
        assertEquals(first, reopened.read(owner, id, Optional.of("1")));
        assertEquals("2", reopened.read(owner, id, Optional.empty()).workspaceRevision());
        assertEquals(1, reopened.list(owner).size());
        assertTrue(reopened.list(new Owner(owner.issuer(), "other-owner")).isEmpty());
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND, assertThrows(WorkspaceRefusal.class,
                () -> reopened.read(new Owner(owner.issuer(), "other-owner"), id, Optional.empty())).code());
    }
    @Test void refusesAbsentStoreAndNeverOverwritesInitializedStorage() {
        assertThrows(WorkspaceRefusal.class, () -> new SqliteDraftStore(directory));
        SqliteDraftStore.initialize(directory);
        assertThrows(WorkspaceRefusal.class, () -> SqliteDraftStore.initialize(directory));
    }
    @Test void conflictsCannotChangeHistoryAndRejectedCompilationPersistsNothing() {
        SqliteDraftStore.initialize(directory);
        var store = new SqliteDraftStore(directory);
        var first = command("0", 1);
        put(service(store), first);
        assertEquals(WorkspaceRefusal.Code.CONFLICT, assertThrows(WorkspaceRefusal.class, () -> put(service(store), command("0", 2))).code());
        var changedRequest = new DraftCommand(id, "0", first.requestId(), first.format(), source("invented", 2));
        assertEquals(WorkspaceRefusal.Code.CONFLICT, assertThrows(WorkspaceRefusal.class, () -> put(service(store), changedRequest)).code());
        var changedNative = new DraftCommand(id, "1", UUID.randomUUID().toString(), first.format(), source("different", 2));
        assertEquals(WorkspaceRefusal.Code.CONFLICT, assertThrows(WorkspaceRefusal.class, () -> put(service(store), changedNative)).code());
        var invalid = new DraftCommand(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), first.format(), "{}");
        assertInstanceOf(DraftWorkspace.Rejected.class, service(store).put(owner, invalid));
        assertEquals(1, store.list(owner).size());
    }

    @Test void corruptedCatalogIdentityAndReplayDigestRefuseStartup() throws Exception {
        SqliteDraftStore.initialize(directory);
        put(service(new SqliteDraftStore(directory)), command("0", 1));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve(PrivateWorkspacePath.DATABASE)); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE catalog SET native_id='corrupted'");
            statement.executeUpdate("UPDATE replays SET request_digest='broken'");
        }
        assertEquals(WorkspaceRefusal.Code.UNAVAILABLE, assertThrows(WorkspaceRefusal.class, () -> new SqliteDraftStore(directory)).code());
    }
    @Test void concurrentExpectedRevisionHasExactlyOneWinner() throws Exception {
        SqliteDraftStore.initialize(directory);
        var firstStore = new SqliteDraftStore(directory);
        var secondStore = new SqliteDraftStore(directory);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<java.util.concurrent.Future<String>>();
            for (var store : List.of(firstStore, secondStore)) tasks.add(executor.submit(() -> {
                start.await();
                try { return put(service(store), command("0", 1)).workspaceRevision(); }
                catch (WorkspaceRefusal refusal) { return refusal.code().name(); }
            }));
            start.countDown();
            var results = new ArrayList<String>();
            for (var task : tasks) results.add(task.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(Set.of("1", "CONFLICT"), new HashSet<>(results));
            assertEquals(1, firstStore.list(owner).size());
        }
    }
    @Test void permissionsAndUnexpectedSidecarsFailClosed() throws Exception {
        SqliteDraftStore.initialize(directory);
        Path database = directory.resolve(PrivateWorkspacePath.DATABASE);
        Files.setPosixFilePermissions(database, PosixFilePermissions.fromString("rw-r-----"));
        assertThrows(WorkspaceRefusal.class, () -> new SqliteDraftStore(directory));
        Files.setPosixFilePermissions(database, PosixFilePermissions.fromString("rw-------"));
        Files.createFile(directory.resolve("studio-workspace.db-wal"));
        assertThrows(WorkspaceRefusal.class, () -> new SqliteDraftStore(directory));
    }
    @Test void revisionCapacityPreservesLastCommittedDraft() {
        SqliteDraftStore.initialize(directory);
        var store = new SqliteDraftStore(directory);
        for (int revision = 0; revision < 32; revision++) put(service(store), command(Integer.toString(revision), revision + 1));
        assertEquals(WorkspaceRefusal.Code.CAPACITY, assertThrows(WorkspaceRefusal.class, () -> put(service(store), command("32", 33))).code());
        assertEquals("32", store.read(owner, id, Optional.empty()).workspaceRevision());
    }

    @Test void processCrashRollsBackCatalogRevisionReplayTogether() throws Exception {
        SqliteDraftStore.initialize(directory);
        var command = command("0", 1);
        var first = put(service(new SqliteDraftStore(directory)), command);
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"), CrashBeforeCommit.class.getName(), directory.resolve(PrivateWorkspacePath.DATABASE).toString()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(17, process.exitValue());
        assertEquals(0, process.getInputStream().readNBytes(4096).length);
        assertTrue(Files.exists(directory.resolve(PrivateWorkspacePath.DATABASE + "-journal")));
        var recovered = new SqliteDraftStore(directory);
        assertEquals(first, recovered.read(owner, id, Optional.empty()));
        assertEquals(first, put(service(recovered), command));
        assertEquals("2", put(service(recovered), command("1", 2)).workspaceRevision());
    }
    @Test void hundredObjectsAreAtomicOwnerQuotaAndDoNotBlockAnotherOwner() {
        SqliteDraftStore.initialize(directory);
        var store = new SqliteDraftStore(directory);
        var service = service(store);
        for (int index = 0; index < 100; index++) put(service, new DraftCommand(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), DraftCommand.Format.JSON, source("invented", 1)));
        var overflow = new DraftCommand(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), DraftCommand.Format.JSON, source("invented", 1));
        assertEquals(WorkspaceRefusal.Code.CAPACITY, assertThrows(WorkspaceRefusal.class, () -> put(service, overflow)).code());
        assertEquals(100, store.list(owner).size());
        assertInstanceOf(DraftWorkspace.Saved.class, service.put(new Owner(owner.issuer(), "other-owner"), overflow));
    }
    @Test void staleRevisionIsRefusedBeforeCompilingInvalidReplacement() {
        SqliteDraftStore.initialize(directory);
        var store = new SqliteDraftStore(directory);
        put(service(store), command("0", 1));
        var invalidStale = new DraftCommand(id, "0", UUID.randomUUID().toString(), DraftCommand.Format.JSON, "{}");
        assertEquals(WorkspaceRefusal.Code.CONFLICT, assertThrows(WorkspaceRefusal.class, () -> service(store).put(owner, invalidStale)).code());
    }

    @Test void symlinkStoreAndOrphanJournalInitializerAreRefused() throws Exception {
        Path target = Files.createTempFile("es-independent-symlink-target-", ".db");
        Files.createSymbolicLink(directory.resolve(PrivateWorkspacePath.DATABASE), target);
        assertThrows(WorkspaceRefusal.class, () -> new SqliteDraftStore(directory));
        var orphan = Files.createTempDirectory("es-orphan-journal-mock-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Files.createFile(orphan.resolve(PrivateWorkspacePath.DATABASE + "-journal"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        assertThrows(WorkspaceRefusal.class, () -> SqliteDraftStore.initialize(orphan));
        assertFalse(Files.exists(orphan.resolve(PrivateWorkspacePath.DATABASE)));
    }

    @Test void unknownJournalHeaderIsNotSilentlyIgnored() throws Exception {
        SqliteDraftStore.initialize(directory);
        Path journal = directory.resolve(PrivateWorkspacePath.DATABASE + "-journal");
        Files.writeString(journal, "independently-invented-invalid-journal-header");
        Files.setPosixFilePermissions(journal, PosixFilePermissions.fromString("rw-------"));
        assertThrows(WorkspaceRefusal.class, () -> new SqliteDraftStore(directory));
    }

    @Test void persistentWalModeIsRefusedRatherThanChanged() throws Exception {
        SqliteDraftStore.initialize(directory);
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve(PrivateWorkspacePath.DATABASE)); var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
        }
        assertThrows(WorkspaceRefusal.class, () -> new SqliteDraftStore(directory));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve(PrivateWorkspacePath.DATABASE)); var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA journal_mode")) {
            assertTrue(rows.next()); assertEquals("wal", rows.getString(1));
        }
    }

    @Test void singleFieldOwnerCorruptionCannotReassignHistoricalAuthority() throws Exception {
        for (String column : List.of("issuer", "subject")) {
            var privatePath = Files.createTempDirectory("es-owner-integrity-mock-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            SqliteDraftStore.initialize(privatePath);
            put(service(new SqliteDraftStore(privatePath)), command("0", 1));
            try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + privatePath.resolve(PrivateWorkspacePath.DATABASE)); var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE catalog SET " + column + "='invented-reassigned-owner'");
            }
            assertEquals(WorkspaceRefusal.Code.UNAVAILABLE, assertThrows(WorkspaceRefusal.class, () -> new SqliteDraftStore(privatePath)).code());
        }
    }
    @Test void consistentObjectReassignmentCannotTransferSnapshot() throws Exception {
        SqliteDraftStore.initialize(directory);
        put(service(new SqliteDraftStore(directory)), command("0", 1));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve(PrivateWorkspacePath.DATABASE)); var statement = connection.createStatement()) {
            for (String table : List.of("catalog", "revisions", "replays")) statement.executeUpdate("UPDATE " + table + " SET object_id='00000000-0000-4000-8000-000000000077'");
        }
        assertEquals(WorkspaceRefusal.Code.UNAVAILABLE, assertThrows(WorkspaceRefusal.class, () -> new SqliteDraftStore(directory)).code());
    }
    @Test void directoryTraversalStopsAtFirstUnexpectedEntryAndMapsTraversalIoFailure() {
        var validator = new PrivateWorkspacePath(directory);
        var remaining = java.util.stream.Stream.<Path>generate(() -> { throw new AssertionError("Traversal must stop at first forbidden entry"); });
        try (var entries = java.util.stream.Stream.concat(java.util.stream.Stream.of(directory.resolve("unexpected")), remaining)) {
            assertEquals(WorkspaceRefusal.Code.UNAVAILABLE, assertThrows(WorkspaceRefusal.class, () -> validator.validateEntries(entries)).code());
        }
        try (var entries = java.util.stream.Stream.<Path>generate(() -> { throw new java.io.UncheckedIOException(new java.io.IOException("invented-traversal-canary")); })) {
            assertEquals(WorkspaceRefusal.Code.UNAVAILABLE, assertThrows(WorkspaceRefusal.class, () -> validator.validateEntries(entries)).code());
        }
    }
}
