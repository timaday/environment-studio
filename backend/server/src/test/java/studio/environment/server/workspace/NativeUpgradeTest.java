package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.workspace.*;
import studio.environment.server.EnvironmentStudioApplication;

class NativeUpgradeTest {
    /** Independently assembled schema-1 store; exact original snapshots are never recompiled by upgrade. */
    @Test void explicitUpgradePreservesLegacyBytesHistoryAndReplayAndRefusesRepeat()throws Exception {
        var fixture=new SqliteDraftStoreTest();fixture.privateDirectory();var directory=fixture.directory;
        // Obtain eligible, invented legacy compiler snapshots through the unchanged v1 draft service.
        SqliteDraftStore.initialize(directory);var legacy=new SqliteDraftStore(directory);var command=fixture.command("0",1);
        var first=fixture.put(fixture.service(legacy),command);fixture.put(fixture.service(legacy),fixture.command("1",2));
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve(PrivateWorkspacePath.DATABASE));var statement=connection.createStatement()) {
            statement.execute("DROP TABLE native_replays");statement.execute("DROP TABLE native_revisions");statement.execute("DROP TABLE artifact_types");statement.execute("PRAGMA user_version=1");
        }
        var before=legacyRows(directory);
        var child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
            CrashWorkspaceUpgrade.class.getName(),directory.resolve(PrivateWorkspacePath.DATABASE).toString()).redirectErrorStream(true).start();
        assertTrue(child.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));assertEquals(17,child.exitValue());
        // Production private-path/open checks recover the hot journal but must not auto-upgrade schema 1.
        assertThrows(WorkspaceRefusal.class,()->new SqliteDraftStore(directory));
        assertEquals(before,legacyRows(directory));
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve(PrivateWorkspacePath.DATABASE));var st=connection.createStatement();var rows=st.executeQuery("PRAGMA user_version")){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}

        assertThrows(WorkspaceRefusal.class,()->new SqliteDraftStore(directory));
        EnvironmentStudioApplication.main(new String[]{"--upgrade-workspace="+directory});
        assertEquals(before,legacyRows(directory));
        var upgraded=new SqliteDraftStore(directory);assertEquals(first,fixture.put(fixture.service(upgraded),command));
        assertEquals(first,upgraded.read(fixture.owner,first.objectId(),Optional.of("1")));
        byte[] bytes=Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE));
        assertThrows(WorkspaceRefusal.class,()->SqliteDraftStore.upgrade(directory));
        assertArrayEquals(bytes,Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE)));
        assertThrows(IllegalArgumentException.class,()->EnvironmentStudioApplication.main(new String[]{"--upgrade-workspace="+directory,"--server.port=0"}));
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve(PrivateWorkspacePath.DATABASE));var statement=connection.createStatement()){statement.execute("PRAGMA user_version=99");}
        byte[] unknown=Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE));
        assertThrows(WorkspaceRefusal.class,()->SqliteDraftStore.upgrade(directory));assertThrows(WorkspaceRefusal.class,()->new SqliteDraftStore(directory));
        assertArrayEquals(unknown,Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE)));
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve(PrivateWorkspacePath.DATABASE));var statement=connection.createStatement()) {
            statement.execute("DROP TABLE native_replays");statement.execute("DROP TABLE native_revisions");statement.execute("DROP TABLE artifact_types");statement.execute("PRAGMA user_version=1");statement.execute("UPDATE revisions SET source_digest='00'");
        }
        byte[] corrupt=Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE));assertThrows(WorkspaceRefusal.class,()->SqliteDraftStore.upgrade(directory));
        assertArrayEquals(corrupt,Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE)));

    }
    static List<String> legacyRows(Path directory)throws Exception {
        var values=new ArrayList<String>();
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve(PrivateWorkspacePath.DATABASE));var statement=connection.createStatement()) {
            for(String table:List.of("catalog","revisions","replays"))try(var rows=statement.executeQuery("SELECT * FROM "+table+" ORDER BY 1,2")) {
                while(rows.next())for(int i=1;i<=rows.getMetaData().getColumnCount();i++)values.add(HexFormat.of().formatHex(rows.getBytes(i)));
            }
        }return List.copyOf(values);
    }
}
