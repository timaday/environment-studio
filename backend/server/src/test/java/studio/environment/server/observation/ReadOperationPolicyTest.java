package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.lang.reflect.*;
import java.math.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.*;
import studio.environment.core.observation.ObservationResult.*;

/** Independently invented catalog/source facts. Actual engine grant evaluation requires disposable qualification. */
class ReadOperationPolicyTest {
    static final String XML="<mock><tile id=\"one\"/></mock>";
    static final Binding PG=new Binding("mock",Engine.POSTGRESQL,Storage.TEXT,"mock_owner","mock_tiles","mock_key","mock_xml",KeyType.INT64,List.of(new NativeDefinition.Document("mock-document","1",List.of())));
    static Binding binding(Engine engine) { return engine==Engine.POSTGRESQL?PG:new Binding(PG.id(),engine,Storage.CLOB,PG.schema(),PG.table(),PG.keyColumn(),PG.xmlColumn(),PG.keyType(),PG.documents()); }
    static ObservationPort.Selection selection(Binding binding) {
        var definition=new NativeDefinition("mock",BigInteger.ONE,new Logical(List.of(),List.of(),List.of(),List.of()),List.of(binding));
        return new ObservationPort.Selection(new NativeCompilationResult.ReadyToPublish(new NativeCompilationResult.Checked(definition,"mock-logical",Map.of("mock","mock-binding"),Map.of())),"mock");
    }
    static class Database {
        final Engine engine;
        final List<String> statements=new ArrayList<>();
        int opens,rollbacks,closes;
        boolean owner=true,readAccess=true,rls,fga,denyMetadata,setupFailure,modeMismatch,lateModeMismatch,autocommitMismatch;
        String user="mock_owner",common="NO",vendor="N";
        int modeChecks;
        Database(Engine engine) { this.engine=engine; }
        Map<String,String> identity() { return engine==Engine.POSTGRESQL?Map.of("systemIdentifier","11","databaseOid","22","databaseName","mock_db"):Map.of("dbid","11","dbUniqueName","MOCK","conId","3","conUid","44","conName","MOCKPDB","pdbGuid","a".repeat(32)); }
        ObservationDestination destination() { return new ObservationDestination("mock",engine,"localhost",1234,"mock_db",ObservationDestination.Transport.DISPOSABLE_LOOPBACK,"","mock-transport",identity(),"mock-provisioning-v1",engine==Engine.POSTGRESQL?"postgresql-read-operation-v1":"oracle-read-operation-v1"); }
        Connection open() {
            opens++;
            return proxy(Connection.class,(p,m,a)-> switch(m.getName()) {
                case "prepareStatement" -> statement((String)a[0]);
                case "getAutoCommit" -> autocommitMismatch;
                case "rollback" -> { rollbacks++;yield null; }
                case "close" -> { closes++;yield null; }
                case "getMetaData" -> proxy(DatabaseMetaData.class,(x,n,b)->n.getName().equals("getDriverVersion")?(engine==Engine.POSTGRESQL?"42.7.13":"23.26.3.0.0"):zero(n));
                default -> zero(m);
            });
        }
        PreparedStatement statement(String sql) throws SQLException {
            statements.add(sql);
            if(setupFailure && sql.startsWith("SET TRANSACTION")) throw new SQLException("mock-setup-denial");
            return proxy(PreparedStatement.class,(p,m,a)-> switch(m.getName()) {
                case "getFetchSize" -> 1;
                case "execute" -> { if(!sql.startsWith("SET TRANSACTION") && !sql.startsWith("SET LOCAL") && !sql.startsWith("LOCK TABLE")) throw new SQLException("unexpected-control"); yield false; }
                case "executeQuery" -> result(rows(sql));
                default -> zero(m);
            });
        }
        List<List<String>> rows(String original) throws SQLException {
            String s=original.toLowerCase(Locale.ROOT);
            if(s.equals("show transaction_isolation")) return one("repeatable read");
            if(s.equals("show transaction_read_only")) { modeChecks++;return one(modeMismatch || lateModeMismatch && modeChecks>=3?"off":"on"); }
            if(s.equals("show search_path")) return one("pg_catalog");
            if(s.equals("show row_security")) return one("off");
            if(s.equals("show server_version_num")) return one("180006");
            if(s.equals("show server_encoding") || s.equals("show client_encoding")) return one("UTF8");
            if(denyMetadata) throw new SQLException("mock-sensitive-metadata-denial");
            if(s.contains("pg_control_system()")) return List.of(List.of("11","22","mock_db"));
            if(s.contains("c.relrowsecurity::text")) return List.of(List.of("101","r","p",Boolean.toString(rls),"false",Boolean.toString(owner)));
            if(s.contains("has_table_privilege") || s.contains("has_schema_privilege")) return one(Boolean.toString(readAccess));
            if(s.contains("pg_catalog.pg_attribute a join pg_catalog.pg_type")) return List.of(List.of("mock_key","int8","pg_catalog","true","","","b","-1"),List.of("mock_xml","text","pg_catalog","false","","","b","-1"));
            if(s.contains("pg_catalog.pg_constraint")) return one("1");
            if(s.contains("pg_catalog.pg_inherits") || s.contains("pg_catalog.pg_policy") || s.contains("pg_catalog.pg_trigger") || s.contains("join pg_catalog.pg_am")) return List.of();
            if(s.contains("sys.v_$instance")) return one("23.26.3.0.0");
            if(s.contains("sys.nls_database_parameters")) return one("AL32UTF8");
            if(s.contains("sys.dba_audit_policies")) return fga?one("1"):List.of();
            if(s.contains("sys.v_$option")) return List.of(List.of("Oracle Label Security","FALSE"),List.of("Oracle Database Vault","FALSE"));
            if(s.equals("select sys_context('userenv','isdba') from sys.dual")) return one("FALSE");
            if(s.contains("'proxy_user'")) return List.of();
            if(s.equals("select sys_context('userenv','session_user') from sys.dual")) return one(user);
            if(s.contains("sys.v_$database")) return List.of(List.of("11","MOCK","3","44","MOCKPDB","a".repeat(32)));
            if(s.startsWith("select common,oracle_maintained")) return List.of(List.of(common,vendor));
            if(s.startsWith("select oracle_maintained")) return one("N");
            if(s.contains("sys.dba_tables")) return List.of(Arrays.asList("NO",null,"NO","N","N","VALID"));
            if(s.contains("sys.dba_external_tables") || s.contains("sys.dba_synonyms") || s.contains("sys.dba_triggers") || s.contains("sys.dba_policies") || s.contains("sys.redaction_policies")) return List.of();
            if(s.startsWith("select case when sys_context")) return one(readAccess?"1":"0");
            if(s.contains("sys.dba_tab_cols")) return List.of(Arrays.asList("mock_key","NUMBER",null,"19","0","N",null,null,"NO","NO","NO"),Arrays.asList("mock_xml","CLOB",null,null,null,"Y",null,null,"NO","NO","NO"));
            if(s.contains("sys.dba_constraints")) return one("1");
            if(s.equals("select count(*) from \"mock_owner\".\"mock_tiles\"")) return one("1");
            if(s.equals("select \"mock_key\",\"mock_xml\" from \"mock_owner\".\"mock_tiles\"")) return List.of(List.of("1",XML));
            if(s.equals("select \"mock_key\",pg_catalog.char_length(\"mock_xml\"),pg_catalog.octet_length(\"mock_xml\") from \"mock_owner\".\"mock_tiles\"") || s.equals("select \"mock_key\",sys.dbms_lob.getlength(\"mock_xml\"),sys.dbms_lob.getlength(\"mock_xml\") from \"mock_owner\".\"mock_tiles\"")) return List.of(List.of("1",Integer.toString(XML.length()),Integer.toString(XML.length())));
            throw new SQLException("unexpected-query");
        }
        boolean sourceRead() { return statements.stream().anyMatch(s->s.startsWith("SELECT") && s.contains("FROM \"mock_owner\".\"mock_tiles\"")); }
        ObservationResult observe() { return new JdbcObservation(destination(),c->open(),()->{},TimeUnit.SECONDS.toNanos(5),TimeUnit.SECONDS.toNanos(1)).observe(selection(binding(engine)),new TransientCredentials("mock_owner".toCharArray(),"mock-secret-canary".toCharArray()),new ObservationPort.Cancellation()); }
    }
    @Test void ordinaryOwnerMetadataCompletesWithoutAccountPurityQueriesAndBindsNewEvidence() {
        for(var engine:Engine.values()) {
            var database=new Database(engine);
            var complete=assertInstanceOf(Complete.class,database.observe());
            assertEquals(XML,complete.observation().documents().getFirst().xml());
            assertEquals(1,database.opens);assertEquals(1,database.rollbacks);assertEquals(1,database.closes);
            assertTrue(database.sourceRead());
            var metadata=(Map<?,?>)complete.observation().evidence().get("metadata");
            assertEquals("verified",metadata.get("readOnlyOperation"));assertFalse(metadata.containsKey("leastPrivilege"));
            assertEquals("jdbc-observation-v2",metadata.get("adapterVersion"));
        }
    }
    @Test void setupAndSnapshotMismatchNeverReachLockOrSourceAndNeverReconnect() {
        for(int adverse=0;adverse<3;adverse++) {
            var database=new Database(Engine.POSTGRESQL);database.setupFailure=adverse==0;database.modeMismatch=adverse==1;database.autocommitMismatch=adverse==2;
            assertInstanceOf(Refused.class,database.observe());assertFalse(database.sourceRead());
            assertFalse(database.statements.stream().anyMatch(s->s.startsWith("LOCK")));assertEquals(1,database.opens);assertEquals(1,database.rollbacks);assertEquals(1,database.closes);
        }
        var changed=new Database(Engine.POSTGRESQL);changed.lateModeMismatch=true;assertInstanceOf(Refused.class,changed.observe());assertFalse(changed.sourceRead());
    }
    @Test void missingAccessMetadataAndReadEffectPoliciesRefuseBeforeSources() {
        for(var engine:Engine.values()) for(int adverse=0;adverse<3;adverse++) {
            var database=new Database(engine);database.readAccess=adverse!=0;database.denyMetadata=adverse==1;database.rls=adverse==2;database.fga=adverse==2;
            var refused=assertInstanceOf(Refused.class,database.observe());assertEquals(adverse==0?Code.READ_ACCESS_DENIED:adverse==1?Code.METADATA_UNAVAILABLE:Code.VISIBILITY_UNQUALIFIED,refused.code());
            assertFalse(database.sourceRead());assertEquals(Cleanup.COMPLETE,refused.cleanup());assertFalse(refused.toString().contains("canary"));
        }
    }
    @Test void unsupportedOracleIdentityCannotReadSources() {
        for(int adverse=0;adverse<3;adverse++) {
            var database=new Database(Engine.ORACLE);if(adverse==0)database.user="SYS";if(adverse==1)database.common="YES";if(adverse==2)database.vendor="Y";
            assertEquals(Code.IDENTITY_UNSUPPORTED,assertInstanceOf(Refused.class,database.observe()).code());assertFalse(database.sourceRead());
        }
    }
    @Test void exactSetupPrecedesSnapshotAndRawSqlIsNotAnAvailableBoundary() throws Exception {
        var database=new Database(Engine.POSTGRESQL);assertInstanceOf(Complete.class,database.observe());
        assertEquals(List.of("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY","SET LOCAL search_path=pg_catalog","SET LOCAL row_security=off","SET LOCAL statement_timeout='2000ms'","SET LOCAL lock_timeout='2000ms'","SHOW transaction_isolation","SHOW transaction_read_only","SHOW search_path","SHOW row_security","LOCK TABLE \"mock_owner\".\"mock_tiles\" IN ACCESS SHARE MODE"),database.statements.subList(0,10));
        for(Method method:SqlRead.class.getDeclaredMethods()) if(!Modifier.isPrivate(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())) assertFalse(Arrays.asList(method.getParameterTypes()).contains(String.class),"No external SQL text accepted: "+method.getName());
        var read=new SqlRead(database.open(),new ObservationPort.Cancellation(),System.nanoTime()+TimeUnit.SECONDS.toNanos(5));
        read.begin(PG);int before=database.statements.size();assertThrows(ObservationFailure.class,()->read.begin(PG));assertEquals(before,database.statements.size());
        assertThrows(ObservationFailure.class,()->read.statement(ReadQuery.ORACLE_SERVER_VERSION));assertEquals(before,database.statements.size());
    }
    @Test void attemptedSetupReplayPermanentlyRefusesFurtherReads() throws Exception {
        var database=new Database(Engine.POSTGRESQL);
        var read=new SqlRead(database.open(),new ObservationPort.Cancellation(),System.nanoTime()+TimeUnit.SECONDS.toNanos(5));
        read.begin(PG);int before=database.statements.size();
        assertThrows(ObservationFailure.class,()->read.begin(PG));
        assertThrows(ObservationFailure.class,()->read.sourceRows(SqlRead.Source.COUNT));
        assertEquals(before,database.statements.size());
    }
    @Test void malformedIdentifierAndRetiredPolicyNeverAllocateAConnection() {
        var database=new Database(Engine.POSTGRESQL);
        for(String invalid:List.of("mock_tiles;DELETE", "mock_tiles\"", "mock_tiles FOR UPDATE", "mock_tiles()")) {
            var binding=new Binding(PG.id(),PG.engine(),PG.storage(),PG.schema(),invalid,PG.keyColumn(),PG.xmlColumn(),PG.keyType(),PG.documents());
            var credentials=new TransientCredentials("mock".toCharArray(),"mock-secret-canary".toCharArray());
            var result=new JdbcObservation(database.destination(),c->database.open(),()->{},TimeUnit.SECONDS.toNanos(5),TimeUnit.SECONDS.toNanos(1)).observe(selection(binding),credentials,new ObservationPort.Cancellation());
            assertEquals(Code.INVALID_SELECTION,assertInstanceOf(Refused.class,result).code());assertTrue(credentials.closed());
        }
        var original=database.destination();
        var retired=new ObservationDestination(original.id(),original.engine(),original.host(),original.port(),original.database(),original.transport(),original.trustMaterial(),original.transportIdentity(),original.expectedPhysicalIdentity(),original.provisioningPolicyVersion(),"postgresql-read-only-v1");
        var result=new JdbcObservation(retired,c->database.open(),()->{},TimeUnit.SECONDS.toNanos(5),TimeUnit.SECONDS.toNanos(1)).observe(selection(PG),new TransientCredentials("mock".toCharArray(),"mock-secret-canary".toCharArray()),new ObservationPort.Cancellation());
        assertEquals(Code.DESTINATION_UNQUALIFIED,assertInstanceOf(Refused.class,result).code());assertEquals(0,database.opens);
    }
    @Test void historicalProvisioningEntrypointsRefuseBeforeParsingInputs() {
        for(var main:List.<org.junit.jupiter.api.function.Executable>of(
                ()->DisposableObservationQualification.main(new String[0]),
                ()->OracleReadOnlyAccountQualification.main(new String[0]),
                ()->OracleFgaQualification.main(new String[0]))) {
            assertEquals("HISTORICAL_ACCOUNT_POLICY_QUALIFICATION_RETIRED",assertThrows(IllegalStateException.class,main).getMessage());
        }
    }
    static List<List<String>> one(String value) { return List.of(List.of(value)); }
    static ResultSet result(List<List<String>> data) {
        int[] cursor={-1};return proxy(ResultSet.class,(p,m,a)->switch(m.getName()) {
            case "next" -> ++cursor[0]<data.size();
            case "getMetaData" -> proxy(ResultSetMetaData.class,(x,n,b)->n.getName().equals("getColumnCount")?(data.isEmpty()?1:data.getFirst().size()):zero(n));
            case "getString" -> data.get(cursor[0]).get((Integer)a[0]-1);
            case "getBigDecimal" -> new BigDecimal(data.get(cursor[0]).get((Integer)a[0]-1));
            case "getCharacterStream" -> new StringReader(data.get(cursor[0]).get((Integer)a[0]-1));
            case "getClob" -> proxy(Clob.class,(x,n,b)->n.getName().equals("getCharacterStream")?new StringReader(data.get(cursor[0]).get((Integer)a[0]-1)):zero(n));
            default -> zero(m);
        });
    }
    static Object zero(Method method) { if(method.getReturnType()==boolean.class)return false;if(method.getReturnType()==int.class)return 0;if(method.getReturnType()==long.class)return 0L;return null; }
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,InvocationHandler handler) { return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler); }
}
