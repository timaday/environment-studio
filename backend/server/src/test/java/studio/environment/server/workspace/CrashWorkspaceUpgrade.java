package studio.environment.server.workspace;

/** Independent SQLite interruption at the explicit schema-upgrade transaction boundary. */
public final class CrashWorkspaceUpgrade {
    public static void main(String[] args)throws Exception {
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+args[0]);var statement=connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=DELETE");statement.execute("PRAGMA synchronous=EXTRA");statement.execute("PRAGMA cache_size=1");statement.execute("PRAGMA cache_spill=ON");
            connection.setAutoCommit(false);
            for(String sql:NativeSqliteStore.DDL)statement.execute(sql);
            statement.execute("PRAGMA user_version=2");
            statement.execute("CREATE TABLE incomplete_upgrade_mock(payload BLOB)");statement.execute("INSERT INTO incomplete_upgrade_mock VALUES(zeroblob(262144))");
            Runtime.getRuntime().halt(17);
        }
    }
}
