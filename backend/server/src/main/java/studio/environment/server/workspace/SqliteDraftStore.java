package studio.environment.server.workspace;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;
import studio.environment.core.definition.DefinitionResult;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

/** Single-replica private store. Every mutation owns an IMMEDIATE SQLite transaction. */
public final class SqliteDraftStore implements DraftStore {
    static final int MAX_PAGES = 30_720; // 120 MiB leaves a full rollback journal plus framing below 256 MiB.
    private static final int RECORD_LIMIT = 2 * 1024 * 1024;
    static final List<String> DDL = List.of(
        "CREATE TABLE catalog(object_id TEXT PRIMARY KEY,issuer TEXT NOT NULL,subject TEXT NOT NULL,native_id TEXT NOT NULL,current_revision INTEGER NOT NULL CHECK(current_revision BETWEEN 1 AND 32))",
        "CREATE TABLE revisions(object_id TEXT NOT NULL,revision INTEGER NOT NULL,format TEXT NOT NULL,source BLOB NOT NULL,source_digest TEXT NOT NULL,projection BLOB NOT NULL,compiler_version TEXT NOT NULL,schema_version TEXT NOT NULL,snapshot_digest TEXT NOT NULL,PRIMARY KEY(object_id,revision),FOREIGN KEY(object_id) REFERENCES catalog(object_id))",
        "CREATE TABLE replays(object_id TEXT NOT NULL,request_id TEXT NOT NULL,request_digest TEXT NOT NULL,revision INTEGER NOT NULL,PRIMARY KEY(object_id,request_id),FOREIGN KEY(object_id,revision) REFERENCES revisions(object_id,revision))");
    final PrivateWorkspacePath paths;
    private final int storageVersion;
    private final WorkspaceCommit commit;
    private final SnapshotCodec codec = new SnapshotCodec();

    public SqliteDraftStore(Path directory) { this(directory, registeredVersion(directory)); }
    int storageVersion() { return storageVersion; }
    private static int registeredVersion(Path directory) {
        var paths = new PrivateWorkspacePath(directory); paths.validateFiles(true);
        try (var connection = connect(paths); var statement = connection.createStatement();
                var rows = statement.executeQuery("PRAGMA user_version")) {
            if (!rows.next()) throw unavailable();
            int version = rows.getInt(1);
            if (rows.next() || version != 2 && version != 3) throw unavailable();
            return version;
        } catch (SQLException failure) { throw unavailable(); }
    }
    private SqliteDraftStore(Path directory,int storageVersion) {
        this.storageVersion=storageVersion;
        this.commit=Connection::commit;
        paths = new PrivateWorkspacePath(directory);
        try (var connection = open()) {
            validate(connection);
        } catch (SQLException failure) { throw unavailable(); }
    }

    private SqliteDraftStore(SqliteDraftStore original, WorkspaceCommit commit) {
        this.paths = original.paths;
        this.storageVersion = original.storageVersion;
        this.commit = Objects.requireNonNull(commit);
    }
    SqliteDraftStore withCommit(WorkspaceCommit commit) { return new SqliteDraftStore(this, commit); }
    void commit(Connection connection) throws SQLException { commit.commit(connection); }

    private String legacyPredicate(String alias) {
        String result = "NOT EXISTS(SELECT 1 FROM artifact_types a WHERE a.object_id=" + alias + ".object_id)";
        if (storageVersion == 3) result += " AND NOT EXISTS(SELECT 1 FROM v3_artifact_types v WHERE v.object_id=" + alias + ".object_id)";
        return result;
    }
    private String legacyFilter(String alias) { return storageVersion == 1 ? "" : " WHERE " + legacyPredicate(alias); }
    void validate(Connection connection) throws SQLException {
            verifySchema(connection);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA integrity_check")) {
                if (!rows.next() || !"ok".equals(rows.getString(1)) || rows.next()) throw unavailable();
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA foreign_key_check")) {
                if (rows.next()) throw unavailable();
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT r.*,c.issuer,c.subject,length(r.source) AS source_length,length(r.projection) AS projection_length FROM revisions r JOIN catalog c USING(object_id)")) {
                while (rows.next()) snapshot(rows);
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT c.object_id,c.native_id,r.revision FROM catalog c JOIN revisions r USING(object_id)")) {
                while (rows.next()) if (!rows.getString(2).equals(read(connection, rows.getString(1), rows.getInt(3)).projection().draft().id())) throw unavailable();
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM replays")) {
                while (rows.next()) {
                    var saved = read(connection, rows.getString("object_id"), rows.getInt("revision"));
                    if (!DraftCommand.uuid(rows.getString("request_id"))) throw unavailable();
                    var command = new DraftCommand(saved.objectId(), Integer.toString(rows.getInt("revision") - 1), rows.getString("request_id"), saved.format(), saved.source());
                    if (!WorkspaceDigests.command(command).equals(rows.getString("request_digest"))) throw unavailable();
                }
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT issuer,subject,COUNT(*) FROM catalog GROUP BY issuer,subject")) {
                while (rows.next()) if (rows.getString(1).isBlank() || rows.getString(2).isBlank() || rows.getInt(3) > 100) throw unavailable();
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT r.object_id,r.revision,COUNT(p.request_id) FROM revisions r LEFT JOIN replays p USING(object_id,revision) GROUP BY r.object_id,r.revision")) {
                while (rows.next()) if (rows.getInt(3) != 1) throw unavailable();
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT c.current_revision,COUNT(r.revision),MAX(r.revision),MIN(r.revision) FROM catalog c LEFT JOIN revisions r USING(object_id)" + legacyFilter("c") + " GROUP BY c.object_id")) {
                while (rows.next()) if (rows.getInt(1) != rows.getInt(2) || rows.getInt(1) != rows.getInt(3) || rows.getInt(4) != 1) throw unavailable();
            }
            if(storageVersion>=2)NativeSqliteStore.validate(connection);
            if(storageVersion==3)V3NativeSqliteStore.validate(connection);
    }

    public static void initialize(Path directory) {
        var paths = new PrivateWorkspacePath(directory);
        paths.createDatabaseFile();
        try (var connection = connect(paths)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                for (String sql : DDL) statement.execute(sql);
                for(String sql:NativeSqliteStore.DDL)statement.execute(sql);
                statement.execute("PRAGMA application_id=1163084875");
                statement.execute("PRAGMA user_version=2");
            }
            connection.commit();
        } catch (SQLException failure) { throw unavailable(); }
        new SqliteDraftStore(directory);
    }

    public static void initializeV3(Path directory) {
        var paths = new PrivateWorkspacePath(directory); paths.createDatabaseFile();
        try (var connection = connect(paths)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                for (String sql : DDL) statement.execute(sql);
                for (String sql : NativeSqliteStore.DDL) statement.execute(sql);
                for (String sql : V3NativeSqliteStore.DDL) statement.execute(sql);
                statement.execute("PRAGMA application_id=1163084875");
                statement.execute("PRAGMA user_version=3");
            }
            verifySchema(connection,3); NativeSqliteStore.validate(connection); V3NativeSqliteStore.validate(connection);
            paths.validateFiles(true); connection.commit(); paths.validateFiles(true);
        } catch (SQLException failure) { throw unavailable(); }
        new SqliteDraftStore(directory);
    }
    public static void upgradeV3(Path directory) {
        var previous = new SqliteDraftStore(directory,2);
        try (var connection = previous.open()) {
            connection.setAutoCommit(false); previous.validate(connection);
            try (var statement = connection.createStatement()) {
                for (String sql : V3NativeSqliteStore.DDL) statement.execute(sql);
                statement.execute("PRAGMA user_version=3");
            }
            verifySchema(connection,3); NativeSqliteStore.validate(connection); V3NativeSqliteStore.validate(connection);
            previous.paths.validateFiles(true); connection.commit(); previous.paths.validateFiles(true);
        } catch (SQLException failure) { throw unavailable(); }
        new SqliteDraftStore(directory);
    }

    public static void upgrade(Path directory) {
        var legacy=new SqliteDraftStore(directory,1);
        try(var connection=legacy.open()) {
            connection.setAutoCommit(false);
            legacy.validate(connection);
            try(var statement=connection.createStatement()) {
                for(String sql:NativeSqliteStore.DDL)statement.execute(sql);
                statement.execute("PRAGMA user_version=2");
            }
            verifySchema(connection,2);NativeSqliteStore.validate(connection);legacy.paths.validateFiles(true);
            connection.commit();legacy.paths.validateFiles(true);
        } catch(SQLException failure) {throw unavailable();}
        new SqliteDraftStore(directory);
    }

    Connection open() throws SQLException {
        paths.validateFiles(true);
        var connection = connect(paths);
        try { verifySchema(connection); return connection; }
        catch (RuntimeException | SQLException failure) { connection.close(); throw failure; }
    }
    private static Connection connect(PrivateWorkspacePath paths) throws SQLException {
        var config = new SQLiteConfig();
        config.resetOpenMode(SQLiteOpenMode.CREATE);
        config.setOpenMode(SQLiteOpenMode.READWRITE);
        config.enableLoadExtension(false);
        config.setBusyTimeout(2000);
        config.enforceForeignKeys(true);
        config.setTempStore(SQLiteConfig.TempStore.MEMORY);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        config.setCacheSize(-4096);
        var connection = config.createConnection("jdbc:sqlite:" + paths.database());
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA synchronous=EXTRA");
            statement.execute("PRAGMA cache_spill=OFF");
            statement.execute("PRAGMA trusted_schema=OFF");
            statement.execute("PRAGMA mmap_size=0");
            statement.execute("PRAGMA max_page_count=" + MAX_PAGES);
            for (var entry : Map.of("synchronous", "3", "journal_mode", "delete", "foreign_keys", "1", "temp_store", "2", "page_size", "4096", "max_page_count", Integer.toString(MAX_PAGES)).entrySet()) {
                try (var rows = statement.executeQuery("PRAGMA " + entry.getKey())) {
                    if (!rows.next() || !entry.getValue().equals(rows.getString(1))) throw unavailable();
                }
            }
            return connection;
        } catch (RuntimeException | SQLException failure) { connection.close(); throw failure; }
    }
    private void verifySchema(Connection connection) throws SQLException {verifySchema(connection,storageVersion);}
    private static void verifySchema(Connection connection,int storageVersion) throws SQLException {
        try (var statement = connection.createStatement()) {
            for (var entry : Map.of("application_id", "1163084875", "user_version", Integer.toString(storageVersion)).entrySet()) {
                try (var rows = statement.executeQuery("PRAGMA " + entry.getKey())) {
                    if (!rows.next() || !entry.getValue().equals(rows.getString(1))) throw unavailable();
                }
            }
            var actual = new HashSet<String>();
            try (var rows = statement.executeQuery("SELECT sql FROM sqlite_schema WHERE sql IS NOT NULL")) {
                while (rows.next()) actual.add(rows.getString(1));
            }
            var expected=new HashSet<>(DDL);if(storageVersion>=2)expected.addAll(NativeSqliteStore.DDL);
            if(storageVersion==3)expected.addAll(V3NativeSqliteStore.DDL);
            if (!actual.equals(expected)) throw unavailable();
        }
    }
    @Override public Optional<SavedDraft> replay(Owner owner, DraftCommand command) {
        try (var connection = open()) { return replay(connection, owner, command); }
        catch (SQLException failure) { throw unavailable(); }
    }
    private Optional<SavedDraft> replay(Connection connection, Owner owner, DraftCommand command) throws SQLException {
        var existing = catalog(connection, owner, command.objectId());
        if (existing.isEmpty()) {
            if (!"0".equals(command.expectedRevision())) throw refusal(WorkspaceRefusal.Code.CONFLICT);
            return Optional.empty();
        }
        try (var query = connection.prepareStatement("SELECT request_digest,revision FROM replays WHERE object_id=? AND request_id=?")) {
            query.setString(1, command.objectId()); query.setString(2, command.requestId());
            try (var rows = query.executeQuery()) {
                if (!rows.next()) {
                    if (!Integer.toString(existing.orElseThrow().revision()).equals(command.expectedRevision())) throw refusal(WorkspaceRefusal.Code.CONFLICT);
                    return Optional.empty();
                }
                if (!WorkspaceDigests.command(command).equals(rows.getString(1))) throw refusal(WorkspaceRefusal.Code.CONFLICT);
                return Optional.of(read(connection, command.objectId(), rows.getInt(2)));
            }
        }
    }
    private record Catalog(String nativeId, int revision) { }
    private Optional<Catalog> catalog(Connection connection, Owner owner, String id) throws SQLException {
        try (var query = connection.prepareStatement("SELECT issuer,subject,native_id,current_revision FROM catalog WHERE object_id=?")) {
            query.setString(1, id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                if (!owner.issuer().equals(rows.getString(1)) || !owner.subject().equals(rows.getString(2))) throw refusal(WorkspaceRefusal.Code.NOT_FOUND);
                if(storageVersion>=2)try(var kind=connection.prepareStatement("SELECT kind FROM artifact_types WHERE object_id=?")) {
                    kind.setString(1,id);try(var kinds=kind.executeQuery()) {if(kinds.next())throw refusal(WorkspaceRefusal.Code.CONFLICT);}
                }
                if(storageVersion==3)try(var kind=connection.prepareStatement("SELECT kind FROM v3_artifact_types WHERE object_id=?")) {
                    kind.setString(1,id);try(var kinds=kind.executeQuery()) {if(kinds.next())throw refusal(WorkspaceRefusal.Code.CONFLICT);}
                }
                return Optional.of(new Catalog(rows.getString(3), rows.getInt(4)));
            }
        }
    }
    @Override public SavedDraft save(Owner owner, DraftCommand command, DefinitionResult.Incomplete projection) {
        byte[] source = StrictUtf8.encode(command.source());
        byte[] encoded = codec.encode(projection);
        if (source.length > 1024 * 1024 || (long) source.length + encoded.length + StrictUtf8.encode(owner.issuer()).length + StrictUtf8.encode(owner.subject()).length + StrictUtf8.encode(projection.draft().id()).length + 4096 > RECORD_LIMIT) throw refusal(WorkspaceRefusal.Code.TOO_LARGE);
        try (var connection = open()) {
            connection.setAutoCommit(false);
            var replay = replay(connection, owner, command);
            if (replay.isPresent()) { commit(connection); return replay.orElseThrow(); }
            var existing = catalog(connection, owner, command.objectId());
            int previous = existing.map(Catalog::revision).orElse(0);
            if (!Integer.toString(previous).equals(command.expectedRevision()) || existing.isPresent() && !existing.orElseThrow().nativeId().equals(projection.draft().id())) throw refusal(WorkspaceRefusal.Code.CONFLICT);
            if (previous >= 32 || count(connection, "SELECT COUNT(*) FROM replays WHERE object_id=?", command.objectId()) >= 256) throw refusal(WorkspaceRefusal.Code.CAPACITY);
            if (existing.isEmpty()) {
                try (var query = connection.prepareStatement("SELECT COUNT(*) FROM catalog WHERE issuer=? AND subject=?")) {
                    query.setString(1, owner.issuer()); query.setString(2, owner.subject());
                    try (var rows = query.executeQuery()) { if (!rows.next() || rows.getInt(1) >= 100) throw refusal(WorkspaceRefusal.Code.CAPACITY); }
                }
            }
            // Remaining database growth plus the original-page journal is at most MAX_PAGES pages,
            // with a further MiB reserved for per-page records and journal headers.
            try {
                if (java.nio.file.Files.getFileStore(paths.database()).getUsableSpace() < (long) MAX_PAGES * 4096 + 1024 * 1024) throw refusal(WorkspaceRefusal.Code.CAPACITY);
            } catch (java.io.IOException failure) { throw unavailable(); }
            int revision = previous + 1;
            if (existing.isEmpty()) {
                try (var insert = connection.prepareStatement("INSERT INTO catalog VALUES(?,?,?,?,?)")) {
                    insert.setString(1, command.objectId()); insert.setString(2, owner.issuer()); insert.setString(3, owner.subject()); insert.setString(4, projection.draft().id()); insert.setInt(5, revision); insert.executeUpdate();
                }
            } else {
                try (var update = connection.prepareStatement("UPDATE catalog SET current_revision=? WHERE object_id=?")) { update.setInt(1, revision); update.setString(2, command.objectId()); update.executeUpdate(); }
            }
            String sourceDigest = WorkspaceDigests.sha256(source);
            String snapshotDigest = snapshotDigest(owner.issuer(), owner.subject(), command.objectId(), revision, command.format().name(), source, encoded, sourceDigest, SnapshotCodec.COMPILER, SnapshotCodec.SCHEMA);
            try (var insert = connection.prepareStatement("INSERT INTO revisions VALUES(?,?,?,?,?,?,?,?,?)")) {
                insert.setString(1, command.objectId()); insert.setInt(2, revision); insert.setString(3, command.format().name()); insert.setBytes(4, source); insert.setString(5, sourceDigest); insert.setBytes(6, encoded); insert.setString(7, SnapshotCodec.COMPILER); insert.setString(8, SnapshotCodec.SCHEMA); insert.setString(9, snapshotDigest); insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("INSERT INTO replays VALUES(?,?,?,?)")) {
                insert.setString(1, command.objectId()); insert.setString(2, command.requestId()); insert.setString(3, WorkspaceDigests.command(command)); insert.setInt(4, revision); insert.executeUpdate();
            }
            commit(connection);
            paths.validateFiles(true);
            return new SavedDraft(command.objectId(), Integer.toString(revision), sourceDigest, command.format(), command.source(), projection, SnapshotCodec.COMPILER, SnapshotCodec.SCHEMA);
        } catch (SQLException failure) { throw unavailable(); }
    }
    private static int count(Connection connection, String sql, String id) throws SQLException {
        try (var query = connection.prepareStatement(sql)) { query.setString(1, id); try (var rows = query.executeQuery()) { if (!rows.next()) throw unavailable(); return rows.getInt(1); } }
    }
    @Override public SavedDraft read(Owner owner, String id, Optional<String> revision) {
        if (!canonicalId(id) || revision.isPresent() && (revision.orElseThrow().length() > 1024 || !revision.orElseThrow().matches("[1-9][0-9]*"))) throw refusal(WorkspaceRefusal.Code.INVALID_REQUEST);
        try (var connection = open()) {
            var catalog = catalog(connection, owner, id).orElseThrow(() -> refusal(WorkspaceRefusal.Code.NOT_FOUND));
            if (revision.isPresent() && (revision.orElseThrow().length() > 2 || Integer.parseInt(revision.orElseThrow()) > 32)) throw refusal(WorkspaceRefusal.Code.NOT_FOUND);
            return read(connection, id, revision.map(Integer::parseInt).orElse(catalog.revision()));
        } catch (SQLException failure) { throw unavailable(); }
    }
    private SavedDraft read(Connection connection, String id, int revision) throws SQLException {
        try (var query = connection.prepareStatement("SELECT r.*,c.issuer,c.subject,length(r.source) AS source_length,length(r.projection) AS projection_length FROM revisions r JOIN catalog c USING(object_id) WHERE r.object_id=? AND r.revision=?")) {
            query.setString(1, id); query.setInt(2, revision);
            try (var rows = query.executeQuery()) { if (!rows.next()) throw refusal(WorkspaceRefusal.Code.NOT_FOUND); return snapshot(rows); }
        }
    }
    private SavedDraft snapshot(ResultSet rows) throws SQLException {
        long sourceLength = rows.getLong("source_length"), projectionLength = rows.getLong("projection_length");
        if (sourceLength < 1 || projectionLength < 1 || sourceLength > 1_048_576 || sourceLength + projectionLength + 4096 > RECORD_LIMIT) throw unavailable();
        byte[] source = rows.getBytes("source"), projection = rows.getBytes("projection");
        String id = rows.getString("object_id"), format = rows.getString("format"), sourceDigest = rows.getString("source_digest"), compiler = rows.getString("compiler_version"), schema = rows.getString("schema_version");
        int revision = rows.getInt("revision");
        if (source == null || projection == null || source.length > 1024 * 1024 || source.length + projection.length + 4096 > RECORD_LIMIT || !canonicalId(id) || revision < 1 || revision > 32
                || !SnapshotCodec.COMPILER.equals(compiler) || !SnapshotCodec.SCHEMA.equals(schema) || !WorkspaceDigests.sha256(source).equals(sourceDigest)
                || !snapshotDigest(rows.getString("issuer"), rows.getString("subject"), id, revision, format, source, projection, sourceDigest, compiler, schema).equals(rows.getString("snapshot_digest"))) throw unavailable();
        try { return new SavedDraft(id, Integer.toString(revision), sourceDigest, DraftCommand.Format.valueOf(format), StrictUtf8.decode(source), codec.decode(projection), compiler, schema); }
        catch (IllegalArgumentException | WorkspaceRefusal failure) { throw unavailable(); }
    }
    private static String snapshotDigest(String issuer, String subject, String id, int revision, String format, byte[] source, byte[] projection, String digest, String compiler, String schema) {
        return WorkspaceDigests.fields(issuer, subject, id, Integer.toString(revision), format, WorkspaceDigests.sha256(source), WorkspaceDigests.sha256(projection), digest, compiler, schema);
    }
    @Override public List<Summary> list(Owner owner) {
        try (var connection = open(); var query = connection.prepareStatement("SELECT object_id,current_revision FROM catalog WHERE issuer=? AND subject=? AND "+legacyPredicate("catalog")+" ORDER BY object_id")) {
            query.setString(1, owner.issuer()); query.setString(2, owner.subject());
            var summaries = new ArrayList<Summary>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    if (summaries.size() >= 100) throw unavailable();
                    var draft = read(connection, rows.getString(1), rows.getInt(2));
                    summaries.add(new Summary(draft.objectId(), draft.workspaceRevision(), draft.projection().draft().id(), draft.projection().draft().revision().toString(), draft.sourceDigest()));
                }
            }
            return List.copyOf(summaries);
        } catch (SQLException failure) { throw unavailable(); }
    }
    private static boolean canonicalId(String id) { try { return UUID.fromString(id).toString().equals(id); } catch (RuntimeException invalid) { return false; } }
    private static WorkspaceRefusal refusal(WorkspaceRefusal.Code code) { return new WorkspaceRefusal(code); }
    private static WorkspaceRefusal unavailable() { return refusal(WorkspaceRefusal.Code.UNAVAILABLE); }
}
