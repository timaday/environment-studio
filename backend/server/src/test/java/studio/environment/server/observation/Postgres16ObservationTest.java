package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.observation.ObservationResult.*;

class Postgres16ObservationTest {
    private static final class Database extends ReadOperationPolicyTest.Database {
        private final String version;
        Database(String version) { super(Engine.POSTGRESQL); this.version = version; }
        @Override List<List<String>> rows(String sql) throws SQLException {
            if (sql.equals(ReadQuery.PG_SERVER_VERSION.text)) return List.of(java.util.Arrays.asList(version));
            return super.rows(sql);
        }
    }

    @Test void exact16VersionCompletesReadonlyAndBindsItsActualVersionIntoIdentity() {
        var database = new Database("160011");
        var result = assertInstanceOf(Complete.class, database.observe());
        assertEquals("16.11", result.observation().evidence().get("engineVersion"));
        assertEquals(ReadOperationPolicyTest.XML, result.observation().documents().getFirst().xml());
        assertTrue(database.statements.contains("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY"));
        assertEquals(1, database.opens); assertEquals(1, database.rollbacks); assertEquals(1, database.closes);
        var old = assertInstanceOf(Complete.class, new Database("180006").observe());
        assertEquals("18.6", old.observation().evidence().get("engineVersion"));
        assertNotEquals(old.observation().fingerprint(), result.observation().fingerprint());
    }

    @Test void unassignedOrMalformedVersionsRefuseBeforeSourcesAndSettleOriginalConnection() {
        for (String version : java.util.Arrays.asList("160010", "160012", "160015", "16.11", "0160011", "160011\n", "", null)) {
            var database = new Database(version);
            var refused = assertInstanceOf(Refused.class, database.observe());
            assertEquals(version == null ? Code.METADATA_UNAVAILABLE : Code.STORAGE_UNSUPPORTED, refused.code());
            assertEquals(Cleanup.COMPLETE, refused.cleanup());
            assertFalse(database.sourceRead());
            assertEquals(1, database.opens); assertEquals(1, database.rollbacks); assertEquals(1, database.closes);
        }
    }

    @Test void newlySupportedVersionCannotWaiveReadModeOrVisibilityChecks() {
        for (int risk = 0; risk < 3; risk++) {
            var database = new Database("160011");
            if (risk == 0) database.modeMismatch = true;
            if (risk == 1) database.rls = true;
            if (risk == 2) database.readAccess = false;
            var refused = assertInstanceOf(Refused.class, database.observe());
            assertEquals(risk == 2 ? Code.READ_ACCESS_DENIED : Code.VISIBILITY_UNQUALIFIED, refused.code());
            assertFalse(database.sourceRead()); assertEquals(Cleanup.COMPLETE, refused.cleanup());
        }
    }
}
