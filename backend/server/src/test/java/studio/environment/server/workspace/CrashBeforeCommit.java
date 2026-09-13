package studio.environment.server.workspace;

/** Independent synthetic process crash; intentionally bypasses adapter cache policy to force a hot journal. */
public final class CrashBeforeCommit {
    public static void main(String[] arguments) throws Exception {
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + arguments[0]); var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=DELETE");
            statement.execute("PRAGMA synchronous=EXTRA");
            statement.execute("PRAGMA cache_size=1");
            statement.execute("PRAGMA cache_spill=ON");
            connection.setAutoCommit(false);
            statement.executeUpdate("UPDATE catalog SET current_revision=2");
            statement.executeUpdate("INSERT INTO revisions SELECT object_id,2,format,source,source_digest,projection,compiler_version,schema_version,snapshot_digest FROM revisions WHERE revision=1");
            statement.executeUpdate("INSERT INTO replays SELECT object_id,'00000000-0000-4000-8000-000000000099',request_digest,2 FROM replays WHERE revision=1");
            statement.execute("CREATE TABLE interrupted_mock(payload BLOB)");
            statement.execute("INSERT INTO interrupted_mock VALUES(zeroblob(262144))");
            Runtime.getRuntime().halt(17);
        }
    }
}
