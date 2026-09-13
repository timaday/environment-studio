package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.observation.ObservationResult.*;

/** Invented catalog counts; actual redundant-constraint reachability was independently observed in PostgreSQL18.6. */
class PostgresUniqueKeyTest {
    static class Database extends ReadOperationPolicyTest.Database {
        final String constraints;
        Database(String constraints){super(Engine.POSTGRESQL);this.constraints=constraints;}
        @Override List<List<String>> rows(String sql)throws SQLException {
            if(sql.equals(ReadQuery.PG_UNIQUE_KEY.text))return ReadOperationPolicyTest.one(constraints);
            return super.rows(sql);
        }
    }
    @ParameterizedTest @ValueSource(strings={"1","2","3","9223372036854775807"})
    void redundantQualifiedConstraintsPreserveCompleteReadAndOriginalCleanup(String count) {
        var database=new Database(count);
        var result=assertInstanceOf(Complete.class,database.observe());
        assertEquals(ReadOperationPolicyTest.XML,result.observation().documents().getFirst().xml());
        assertEquals(Cleanup.COMPLETE,result.cleanup());assertTrue(database.sourceRead());
        assertEquals(1,database.opens);assertEquals(1,database.rollbacks);assertEquals(1,database.closes);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","+1","01"," 1","1 ","1.0","1e1","", "9223372036854775808","99999999999999999999"})
    void absentOrMalformedQualifiedCountRefusesBeforeSourceRead(String count) {
        var database=new Database(count);
        var result=assertInstanceOf(Refused.class,database.observe());
        assertEquals(Code.STORAGE_UNSUPPORTED,result.code());assertEquals(Cleanup.COMPLETE,result.cleanup());
        assertFalse(database.sourceRead());
        assertEquals(1,database.opens);assertEquals(1,database.rollbacks);assertEquals(1,database.closes);
    }
    @Test void deniedOrIncompleteCountMetadataNeverBecomesPermission() {
        for(int shape=0;shape<4;shape++) {
            final int selected=shape;
            var database=new Database("2") {
                @Override List<List<String>> rows(String sql)throws SQLException {
                    if(sql.equals(ReadQuery.PG_UNIQUE_KEY.text)) {
                        return switch(selected) {
                            case 0 -> throw new SQLException("mock-count-metadata-denied");
                            case 1 -> List.of();
                            case 2 -> List.of(List.of("2"),List.of("2"));
                            default -> List.of(java.util.Arrays.asList((String)null));
                        };
                    }
                    return super.rows(sql);
                }
            };
            var result=assertInstanceOf(Refused.class,database.observe());
            assertEquals(Code.METADATA_UNAVAILABLE,result.code());assertEquals(Cleanup.COMPLETE,result.cleanup());
            assertFalse(database.sourceRead());assertEquals(1,database.opens);assertEquals(1,database.rollbacks);assertEquals(1,database.closes);
        }
    }
    @Test void redundantUniquenessCannotMaskDuplicateOrUnexpectedSourceKeys() {
        for(String key:List.of("1","2")) {
            var database=new Database("2") {
                @Override List<List<String>> rows(String sql)throws SQLException {
                    if(sql.equals("SELECT \"mock_key\",\"mock_xml\" FROM \"mock_owner\".\"mock_tiles\""))
                        return List.of(List.of("1",ReadOperationPolicyTest.XML),List.of(key,ReadOperationPolicyTest.XML));
                    return super.rows(sql);
                }
            };
            var result=assertInstanceOf(Refused.class,database.observe());
            assertEquals(Code.INVENTORY_MISMATCH,result.code());assertEquals(Cleanup.COMPLETE,result.cleanup());
            assertTrue(database.sourceRead());assertEquals(1,database.opens);assertEquals(1,database.rollbacks);assertEquals(1,database.closes);
        }
    }

}
