package studio.environment.server.observation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import studio.environment.core.definitionv2.*;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.observation.*;
import studio.environment.core.observation.ObservationResult.*;
import studio.environment.server.definition.NativeDefinitionBytesCompiler;

/** Explicit disposable main, never a silently skipped JUnit test or production composition. */
public final class DisposableObservationQualification {
    static final String POSTGRES = "es-qual-postgres-082f7dff", ORACLE = "es-qual-oracle-374bf3f7";
    static final List<String> secrets = new ArrayList<>();
    static final String reader = "es_reader_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    static final String password = password();
    static final Map<String,String> xml = new LinkedHashMap<>();
    static NativeCompilationResult.ReadyToPublish ready;
    static int passed;
    static String password() { byte[] bytes = new byte[24]; new SecureRandom().nextBytes(bytes); String value="M"+HexFormat.of().formatHex(bytes); secrets.add(value); Arrays.fill(bytes,(byte)0); return value; }
    static String process(List<String> command, String input) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try(var stream=process.getOutputStream()) { stream.write(input.getBytes(StandardCharsets.UTF_8)); }
        byte[] bytes=process.getInputStream().readNBytes(8*1024*1024+1);
        if(!process.waitFor(20,TimeUnit.SECONDS) || bytes.length>8*1024*1024 || process.exitValue()!=0) throw new IllegalStateException("DISPOSABLE_COMMAND_FAILED_" + java.util.regex.Pattern.compile("ORA-[0-9]+|SP2-[0-9]+").matcher(new String(bytes,StandardCharsets.UTF_8)).results().map(java.util.regex.MatchResult::group).distinct().toList());
        String output=new String(bytes,StandardCharsets.UTF_8);
        for(String secret:secrets) if(output.contains(secret)) throw new IllegalStateException("SYNTHETIC_CREDENTIAL_OUTPUT_LEAK");
        if(output.contains("invented repeated values & untouched markup")) throw new IllegalStateException("SYNTHETIC_SOURCE_OUTPUT_LEAK");
        return output;
    }
    static String pg(String sql) throws Exception { return process(List.of("docker","exec","-i",POSTGRES,"psql","-U","postgres","-At","-v","ON_ERROR_STOP=1"),sql+"\n").strip(); }
    static String ora(String sql) throws Exception {
        String output=process(List.of("docker","exec","-i",ORACLE,"sqlplus","-s","/ as sysdba"),"set pagesize 0 heading off feedback off echo off verify off linesize 32767 trimspool on\nwhenever sqlerror exit failure\nalter session set container=FREEPDB1;\n"+sql+"\nexit\n").strip();
        if(output.contains("ORA-") || output.contains("SP2-")) throw new IllegalStateException("DISPOSABLE_ORACLE_SETUP_FAILED");
        return output;
    }
    static void oracleDdl(String sql) throws Exception {
        ora(sql);
        // Oracle read-only snapshots reject same-second table DDL (ORA-01466).
        // Settle disposable setup before a new operation; never retry runtime reads.
        ora("BEGIN DBMS_SESSION.SLEEP(1.1); END;\n/");
    }
    static void check(boolean condition,String code) { if(!condition) throw new IllegalStateException(code); passed++; }
    static String unicodeSql(String text) {
        var value=new StringBuilder("TO_CLOB(UNISTR('");
        for(char character:text.toCharArray()) value.append(String.format(Locale.ROOT,"\\%04x",(int)character));
        return value.append("'))").toString();
    }
    static void restore(Engine engine) throws Exception {
        if(engine==Engine.POSTGRESQL) {
            var sql=new StringBuilder("DELETE FROM mock_pg.mock_tiles;\n"); int key=1;
            for(String source:xml.values()) sql.append("INSERT INTO mock_pg.mock_tiles VALUES(").append(key++).append(",convert_from(decode('").append(HexFormat.of().formatHex(source.getBytes(StandardCharsets.UTF_8))).append("','hex'),'UTF8'));\n");
            pg(sql.toString());
        } else {
            var sql=new StringBuilder("DELETE FROM \"mock_oracle\".\"mock_tiles\";\n"); int key=1;
            for(String source:xml.values()) sql.append("INSERT INTO \"mock_oracle\".\"mock_tiles\" VALUES(").append(key++).append(',').append(unicodeSql(source)).append(");\n");
            ora(sql.append("COMMIT;").toString());
        }
    }
    static ObservationDestination destination(Engine engine) throws Exception {
        if(engine==Engine.POSTGRESQL) {
            var parts=pg("SELECT s.system_identifier::text||'|'||d.oid::text||'|'||d.datname FROM pg_control_system() s,pg_database d WHERE datname=current_database();").split("\\|");
            return new ObservationDestination("invented-pg",engine,"127.0.0.1",32771,"postgres",ObservationDestination.Transport.DISPOSABLE_LOOPBACK,"","disposable-loopback-pg-v1",Map.of("systemIdentifier",parts[0],"databaseOid",parts[1],"databaseName",parts[2]),"disposable-postgresql-policy-v1","postgresql-read-only-v1");
        }
        var parts=ora("SELECT TO_CHAR(d.dbid)||'|'||d.db_unique_name||'|'||TO_CHAR(c.con_id)||'|'||TO_CHAR(c.con_uid)||'|'||c.name||'|'||LOWER(RAWTOHEX(c.guid)) FROM v$database d CROSS JOIN v$containers c WHERE c.con_id=TO_NUMBER(SYS_CONTEXT('USERENV','CON_ID'));").split("\\|");
        String unresolved=ora("SELECT COUNT(*) FROM (SELECT p.owner,p.table_name,p.type,p.privilege FROM dba_tab_privs p LEFT JOIN dba_objects o ON p.type<>'USER' AND o.owner=p.owner AND o.object_name=p.table_name AND (o.object_type=p.type OR p.type='UNKNOWN') AND o.subobject_name IS NULL AND o.object_type NOT IN ('PACKAGE BODY','TYPE BODY') AND o.status='VALID' LEFT JOIN dba_users u ON p.type='USER' AND u.username=p.table_name WHERE p.grantee='PUBLIC' GROUP BY p.owner,p.table_name,p.type,p.privilege HAVING (p.type<>'USER' AND COUNT(DISTINCT o.object_id)<>1) OR (p.type='USER' AND COUNT(DISTINCT u.user_id)<>1 AND NOT(p.owner='SYS' AND p.table_name='PUBLIC' AND p.type='USER' AND p.privilege='INHERIT PRIVILEGES')));");
        check(unresolved.equals("0"),"ADMIN_BASELINE_UNRESOLVED_TARGET");
        String grants=ora("SELECT DISTINCT p.owner||'|'||p.table_name||'|'||CASE WHEN p.type='USER' THEN 'USER' ELSE o.object_type END||'|'||p.privilege||'|'||p.grantable||'|'||p.common||'|'||p.inherited||'|'||CASE WHEN p.owner='SYS' AND p.table_name='PUBLIC' AND p.type='USER' AND p.privilege='INHERIT PRIVILEGES' THEN 'PUBLIC_SPECIAL_ROLE' WHEN p.type='USER' THEN u.oracle_maintained ELSE o.oracle_maintained END FROM dba_tab_privs p LEFT JOIN dba_objects o ON p.type<>'USER' AND o.owner=p.owner AND o.object_name=p.table_name AND (o.object_type=p.type OR p.type='UNKNOWN') AND o.subobject_name IS NULL AND o.object_type NOT IN ('PACKAGE BODY','TYPE BODY') AND o.status='VALID' LEFT JOIN dba_users u ON p.type='USER' AND u.username=p.table_name WHERE p.grantee='PUBLIC';");
        var records=Arrays.stream(grants.split("\\R")).map(row->row.strip().split("\\|",-1)).sorted((a,b)->{for(int i=0;i<8;i++){int c=Arrays.compareUnsigned(a[i].getBytes(StandardCharsets.UTF_8),b[i].getBytes(StandardCharsets.UTF_8));if(c!=0)return c;}return 0;}).map(row->{check(row.length==8 && Set.of("Y","PUBLIC_SPECIAL_ROLE").contains(row[7]),"ADMIN_BASELINE_NOT_VENDOR_MAINTAINED");return Map.of("owner",row[0],"objectName",row[1],"objectType",row[2],"privilege",row[3],"grantable",row[4],"common",row[5],"inherited",row[6],"oracleMaintained",row[7]);}).toList();
        // Independent admin capture precedes all mock Oracle provisioning and adapter reads.
        String policy="oracle-account-read-only-v2:"+ObservationFingerprint.hash("ES-ORACLE-PUBLIC-GRANTS-2",records);
        System.out.println("Oracle independent pristine baseline entries="+records.size()+" digest="+policy.substring(policy.indexOf(':')+1));
        return new ObservationDestination("invented-oracle",engine,"127.0.0.1",32776,"FREEPDB1",ObservationDestination.Transport.DISPOSABLE_LOOPBACK,"","disposable-loopback-oracle-v1",Map.of("dbid",parts[0],"dbUniqueName",parts[1],"conId",parts[2],"conUid",parts[3],"conName",parts[4],"pdbGuid",parts[5]),"disposable-oracle-policy-v1",policy);
    }
    static ObservationResult observe(ObservationDestination destination) {
        var credentials=new TransientCredentials((destination.engine()==Engine.ORACLE?reader.toUpperCase(Locale.ROOT):reader).toCharArray(),password.toCharArray());
        var result=new JdbcObservation(destination).observe(new ObservationPort.Selection(ready,destination.engine()==Engine.POSTGRESQL?"mock-pg":"mock-oracle"),credentials,new ObservationPort.Cancellation());
        check(credentials.closed(),"CREDENTIALS_NOT_CLEARED");
        return result;
    }
    static void backendGone(Engine engine) throws Exception {
        String count="";
        for(int attempt=0;attempt<50;attempt++) {
            count=engine==Engine.POSTGRESQL?pg("SELECT count(*) FROM pg_stat_activity WHERE usename='"+reader+"';"):ora("SELECT count(*) FROM v$session WHERE username='"+reader.toUpperCase(Locale.ROOT)+"';");
            if(count.strip().equals("0"))break;
            Thread.sleep(100);
        }
        check(count.strip().equals("0"),"BACKEND_SESSION_NOT_GONE");
    }
    static void refusal(ObservationDestination destination,Code expected) throws Exception {
        var result=observe(destination);
        check(result instanceof Refused refused && refused.code()==expected && refused.cleanup()==Cleanup.COMPLETE,"EXPECTED_"+expected+"_ACTUAL_"+result);
        backendGone(destination.engine());
    }


    static final class PgLock implements AutoCloseable {
        final Process process;
        final OutputStream input;
        PgLock() throws Exception {
            process=new ProcessBuilder("docker","exec","-i",POSTGRES,"psql","-U","postgres","-At","-v","ON_ERROR_STOP=1").redirectErrorStream(true).start(); input=process.getOutputStream();
            input.write("SET statement_timeout='5000ms'; BEGIN; LOCK TABLE mock_pg.mock_tiles IN ACCESS EXCLUSIVE MODE; SELECT 'MOCK_LOCK_READY';\n".getBytes(StandardCharsets.UTF_8)); input.flush();
            var output=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));
            String line; boolean ready=false; while((line=output.readLine())!=null){if(line.equals("MOCK_LOCK_READY")){ready=true;break;}}
            check(ready,"ADMIN_LOCK_NOT_ESTABLISHED");
        }
        public void close() throws Exception { input.write("ROLLBACK;\n\\q\n".getBytes(StandardCharsets.UTF_8)); input.close(); check(process.waitFor(5,TimeUnit.SECONDS),"ADMIN_LOCK_NOT_RELEASED"); }
    }
    static void postgresCancellation(ObservationDestination destination) throws Exception {
        for(boolean disconnect:List.of(false,true)) {
            var cancellation=new ObservationPort.Cancellation();
            var credentials=new TransientCredentials(reader.toCharArray(),password.toCharArray());
            try(var lock=new PgLock(); var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
                var pending=executor.submit(()->new JdbcObservation(destination).observe(new ObservationPort.Selection(ready,"mock-pg"),credentials,cancellation));
                boolean waiting=false;
                for(int attempt=0;attempt<50;attempt++) {
                    waiting=pg("SELECT count(*) FROM pg_stat_activity WHERE usename='"+reader+"' AND wait_event_type='Lock';").equals("1");
                    if(waiting)break; Thread.sleep(20);
                }
                check(waiting,"READER_LOCK_WAIT_NOT_OBSERVED");
                if(disconnect)pg("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename='"+reader+"';");else cancellation.cancel();
                var result=pending.get(8,TimeUnit.SECONDS);
                check(result instanceof Refused,"INTERRUPTED_READ_AUTHORIZED");
                if(disconnect)check(result.cleanup()==Cleanup.INCONCLUSIVE && ((Refused)result).cleanupHandle().isPresent(),"DISCONNECT_CLEANUP_MUST_REMAIN_INCONCLUSIVE");
                else check(((Refused)result).code()==Code.CANCELLED && result.cleanup()==Cleanup.COMPLETE,"CANCEL_NOT_CONFIRMED_"+result);
                check(credentials.closed(),"INTERRUPTED_CREDENTIALS_RETAINED");
                backendGone(Engine.POSTGRESQL);
            }
        }
        System.out.println("POSTGRESQL actual lock-stall/cancel/backend disappearance/disconnect-quarantine PASS");
    }

    static NativeCompilationResult.ReadyToPublish compiledBinding(NativeDefinition.Binding binding) {
        var original=ready.checked().definition();
        var definition=new NativeDefinition(original.id(),original.revision(),original.logical(),List.of(binding));
        var result=new NativeDefinitionCompiler().compile(definition);
        check(result instanceof NativeCompilationResult.ReadyToPublish,"INDEPENDENT_BINDING_NOT_READY");
        return (NativeCompilationResult.ReadyToPublish)result;
    }
    static void postgresTextAndCapacity(ObservationDestination destination) throws Exception {
        var original=ready;
        var binding=ready.checked().definition().bindings().stream().filter(b->b.id().equals("mock-pg")).findFirst().orElseThrow();
        var textDocuments=List.of(new NativeDefinition.Document(binding.documents().get(0).id(),"01",binding.documents().get(0).entities()),new NativeDefinition.Document(binding.documents().get(1).id(),"1",binding.documents().get(1).entities()));
        var textBinding=new NativeDefinition.Binding(binding.id(),binding.engine(),binding.storage(),binding.schema(),"mock_text_tiles",binding.keyColumn(),binding.xmlColumn(),NativeDefinition.KeyType.TEXT,textDocuments);
        pg("CREATE TABLE IF NOT EXISTS mock_pg.mock_text_tiles(mock_key text PRIMARY KEY,mock_xml text NOT NULL); DELETE FROM mock_pg.mock_text_tiles; INSERT INTO mock_pg.mock_text_tiles SELECT CASE mock_key WHEN 1 THEN '01' ELSE '1' END,mock_xml FROM mock_pg.mock_tiles; GRANT SELECT ON mock_pg.mock_text_tiles TO "+reader+";");
        try {
            ready=compiledBinding(textBinding);
            var text=(Complete)observe(destination);
            check(text.observation().documents().get(0).key().value().equals("01") && text.observation().documents().get(1).key().value().equals("1"),"TEXT_KEYS_WERE_NORMALIZED");
            pg("UPDATE mock_pg.mock_text_tiles SET mock_key=repeat('a',257) WHERE mock_key='01';"); refusal(destination,Code.INVENTORY_MISMATCH);
        } finally{ready=original;}
        var documents=new ArrayList<NativeDefinition.Document>();
        for(int index=0;index<128;index++) {
            var template=binding.documents().get(index%2);
            var projections=template.entities().stream().map(p->new NativeDefinition.Projection(p.id()+"-"+documents.size(),p.type(),p.path(),p.fields(),p.references())).toList();
            documents.add(new NativeDefinition.Document("sheet-"+String.format(Locale.ROOT,"%03d",index),Integer.toString(index+1),projections));
        }
        var capacityBinding=new NativeDefinition.Binding(binding.id(),binding.engine(),binding.storage(),binding.schema(),"mock_capacity_tiles",binding.keyColumn(),binding.xmlColumn(),binding.keyType(),documents);
        pg("CREATE TABLE IF NOT EXISTS mock_pg.mock_capacity_tiles(mock_key bigint PRIMARY KEY,mock_xml text NOT NULL); DELETE FROM mock_pg.mock_capacity_tiles; INSERT INTO mock_pg.mock_capacity_tiles SELECT n,'<x>'||repeat('a',131065)||'</x>' FROM generate_series(1,128) n; GRANT SELECT ON mock_pg.mock_capacity_tiles TO "+reader+";");
        try {
            ready=compiledBinding(capacityBinding);
            long started=System.nanoTime(); var result=observe(destination);
            check(result instanceof Complete,"EXACT_TOTAL_SOURCE_BOUNDARY_REFUSED_"+result);
            check(((Complete)result).observation().documents().stream().mapToLong(Document::utf8Bytes).sum()==16L*1024*1024,"TOTAL_SOURCE_BOUNDARY_NOT_EXACT");
            System.out.println("POSTGRESQL 128 documents / 16 MiB exact UTF8 PASS in "+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)+" ms");
            backendGone(Engine.POSTGRESQL);
            pg("UPDATE mock_pg.mock_capacity_tiles SET mock_xml=mock_xml||' ' WHERE mock_key=1;"); refusal(destination,Code.RESOURCE_LIMIT);
        } finally{ready=original;}
    }
    static void postgresAdversaries(ObservationDestination destination) throws Exception {
        pg("DELETE FROM mock_pg.mock_tiles WHERE mock_key=2;"); refusal(destination,Code.INVENTORY_MISMATCH); restore(Engine.POSTGRESQL);
        pg("INSERT INTO mock_pg.mock_tiles VALUES(3,'<invented/>');"); refusal(destination,Code.INVENTORY_MISMATCH); restore(Engine.POSTGRESQL);
        pg("ALTER TABLE mock_pg.mock_tiles ENABLE ROW LEVEL SECURITY; CREATE POLICY invented_hidden ON mock_pg.mock_tiles USING(false);");
        refusal(destination,Code.VISIBILITY_UNQUALIFIED);
        pg("DROP POLICY invented_hidden ON mock_pg.mock_tiles; ALTER TABLE mock_pg.mock_tiles DISABLE ROW LEVEL SECURITY;");
        pg("GRANT UPDATE(mock_xml) ON mock_pg.mock_tiles TO "+reader+";"); refusal(destination,Code.ACCOUNT_NOT_READ_ONLY);
        pg("REVOKE UPDATE(mock_xml) ON mock_pg.mock_tiles FROM "+reader+";");
        String role="es_writer_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        pg("CREATE ROLE "+role+" NOLOGIN; GRANT UPDATE ON mock_pg.mock_tiles TO "+role+"; GRANT "+role+" TO "+reader+" WITH INHERIT FALSE;");
        refusal(destination,Code.ACCOUNT_NOT_READ_ONLY);
        pg("REVOKE "+role+" FROM "+reader+";");
        pg("REVOKE EXECUTE ON FUNCTION pg_control_system() FROM "+reader+";"); refusal(destination,Code.METADATA_UNAVAILABLE);
        pg("GRANT EXECUTE ON FUNCTION pg_control_system() TO "+reader+";");
        pg("REVOKE SELECT ON mock_pg.mock_tiles FROM "+reader+";"); refusal(destination,Code.DATABASE_FAILURE);
        pg("GRANT SELECT ON mock_pg.mock_tiles TO "+reader+";");
        pg("UPDATE mock_pg.mock_tiles SET mock_xml='' WHERE mock_key=1;"); refusal(destination,Code.EMPTY_SOURCE); restore(Engine.POSTGRESQL);
        pg("ALTER TABLE mock_pg.mock_tiles ALTER COLUMN mock_xml DROP NOT NULL; UPDATE mock_pg.mock_tiles SET mock_xml=NULL WHERE mock_key=1;");
        refusal(destination,Code.NULL_SOURCE); restore(Engine.POSTGRESQL); pg("ALTER TABLE mock_pg.mock_tiles ALTER COLUMN mock_xml SET NOT NULL;");
        var wrong=new HashMap<>(destination.expectedPhysicalIdentity()); wrong.put("systemIdentifier","0");
        refusal(new ObservationDestination(destination.id(),destination.engine(),destination.host(),destination.port(),destination.database(),destination.transport(),destination.trustMaterial(),destination.transportIdentity(),wrong,destination.provisioningPolicyVersion(),destination.accountPolicyVersion()),Code.DESTINATION_MISMATCH);
        pg("UPDATE mock_pg.mock_tiles SET mock_xml='<x>'||repeat('a',1048570)||'</x>' WHERE mock_key=1;"); refusal(destination,Code.RESOURCE_LIMIT);
        pg("UPDATE mock_pg.mock_tiles SET mock_xml='<x>'||repeat('a',1048569)||'</x>' WHERE mock_key=1;");
        check(observe(destination) instanceof Complete,"EXACT_CHARACTER_BOUNDARY_REFUSED"); backendGone(Engine.POSTGRESQL); restore(Engine.POSTGRESQL);
        var changed=new AtomicBoolean();
        var snapshotAdapter=new JdbcObservation(destination,()->{try{pg("UPDATE mock_pg.mock_tiles SET mock_xml='<changed/>' WHERE mock_key=1; INSERT INTO mock_pg.mock_tiles VALUES(3,'<extra/>');");changed.set(true);}catch(Exception failed){throw new IllegalStateException("MOCK_CONCURRENT_UPDATE_FAILED");}});
        var result=snapshotAdapter.observe(new ObservationPort.Selection(ready,"mock-pg"),new TransientCredentials(reader.toCharArray(),password.toCharArray()),new ObservationPort.Cancellation());
        check(changed.get() && result instanceof Complete,"CONCURRENT_SNAPSHOT_NOT_COMPLETE");
        check(((Complete)result).observation().documents().getFirst().xml().equals(xml.get("glyph-sheet")),"CONCURRENT_SNAPSHOT_MIXED_REVISIONS");
        backendGone(Engine.POSTGRESQL); restore(Engine.POSTGRESQL);
        var properties=new Properties(); properties.setProperty("user",reader); properties.setProperty("password",password); properties.setProperty("sslmode","disable"); properties.setProperty("connectTimeout","5"); properties.setProperty("socketTimeout","2");
        try(var connection=new org.postgresql.Driver().connect("jdbc:postgresql://127.0.0.1:32771/postgres",properties)) {
            properties.clear(); connection.setAutoCommit(false);
            for(String command:List.of("INSERT INTO mock_pg.mock_tiles VALUES(77,'<denied/>')","UPDATE mock_pg.mock_tiles SET mock_xml='<denied/>' WHERE mock_key=1","DELETE FROM mock_pg.mock_tiles WHERE mock_key=1")) {
                boolean denied=false;
                try(var statement=connection.createStatement()){statement.executeUpdate(command);}catch(java.sql.SQLException failure){denied="42501".equals(failure.getSQLState());}
                finally{connection.rollback();}
                check(denied,"ORDINARY_TRANSACTION_WRITE_NOT_DENIED");
            }
        } finally{properties.clear();}
        backendGone(Engine.POSTGRESQL);
        System.out.println("POSTGRESQL membership/RLS/column/role/metadata/SELECT/destination/null/empty/bounds/snapshot/ordinary-write-denial PASS");
    }
    static void oracleTextAndCapacity(ObservationDestination destination) throws Exception {
        var original=ready;String user=reader.toUpperCase(Locale.ROOT);
        var binding=ready.checked().definition().bindings().stream().filter(b->b.id().equals("mock-oracle")).findFirst().orElseThrow();
        if(ora("SELECT COUNT(*) FROM dba_tables WHERE owner='mock_oracle' AND table_name='mock_text_tiles';").equals("0"))oracleDdl("CREATE TABLE \"mock_oracle\".\"mock_text_tiles\"(\"mock_key\" VARCHAR2(1024 CHAR) PRIMARY KEY,\"mock_xml\" CLOB NOT NULL);");
        ora("DELETE FROM \"mock_oracle\".\"mock_text_tiles\";\nINSERT INTO \"mock_oracle\".\"mock_text_tiles\" SELECT CASE \"mock_key\" WHEN 1 THEN '01' ELSE '1' END,\"mock_xml\" FROM \"mock_oracle\".\"mock_tiles\";\nCOMMIT;\nGRANT SELECT ON \"mock_oracle\".\"mock_text_tiles\" TO "+user+";");
        var textDocuments=List.of(new NativeDefinition.Document(binding.documents().get(0).id(),"01",binding.documents().get(0).entities()),new NativeDefinition.Document(binding.documents().get(1).id(),"1",binding.documents().get(1).entities()));
        try {
            ready=compiledBinding(new NativeDefinition.Binding(binding.id(),binding.engine(),binding.storage(),binding.schema(),"mock_text_tiles",binding.keyColumn(),binding.xmlColumn(),NativeDefinition.KeyType.TEXT,textDocuments));
            var result=observe(destination);check(result instanceof Complete,"ORACLE_TEXT_BINDING_REFUSED_"+result);
            check(((Complete)result).observation().documents().getFirst().key().value().equals("01"),"ORACLE_TEXT_KEY_NORMALIZED");backendGone(Engine.ORACLE);
            ora("UPDATE \"mock_oracle\".\"mock_text_tiles\" SET \"mock_key\"=RPAD('a',257,'a') WHERE \"mock_key\"='01';\nCOMMIT;");refusal(destination,Code.INVENTORY_MISMATCH);
        } finally {ready=original;}
        if(ora("SELECT COUNT(*) FROM dba_tables WHERE owner='mock_oracle' AND table_name='mock_capacity_tiles';").equals("0"))oracleDdl("CREATE TABLE \"mock_oracle\".\"mock_capacity_tiles\"(\"mock_key\" NUMBER(19,0) PRIMARY KEY,\"mock_xml\" CLOB NOT NULL);");
        ora("GRANT SELECT ON \"mock_oracle\".\"mock_capacity_tiles\" TO "+user+";");
        var documents=new ArrayList<NativeDefinition.Document>();
        for(int index=0;index<128;index++) {
            var template=binding.documents().get(index%2);
            var projections=template.entities().stream().map(p->new NativeDefinition.Projection(p.id()+"-"+documents.size(),p.type(),p.path(),p.fields(),p.references())).toList();
            documents.add(new NativeDefinition.Document("sheet-"+String.format(Locale.ROOT,"%03d",index),Integer.toString(index+1),projections));
        }
        try {
            ready=compiledBinding(new NativeDefinition.Binding(binding.id(),binding.engine(),binding.storage(),binding.schema(),"mock_capacity_tiles",binding.keyColumn(),binding.xmlColumn(),binding.keyType(),documents));
            oracleCapacitySource(131072,"a");
            long started=System.nanoTime();var result=observe(destination);check(result instanceof Complete,"ORACLE_EXACT_TOTAL_SOURCE_BOUNDARY_REFUSED_"+result);
            check(((Complete)result).observation().documents().stream().mapToLong(Document::utf8Bytes).sum()==16L*1024*1024,"ORACLE_TOTAL_BOUNDARY_NOT_EXACT");backendGone(Engine.ORACLE);
            System.out.println("ORACLE 128 documents / 16 MiB exact UTF8 PASS in "+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)+" ms");
            ora("UPDATE \"mock_oracle\".\"mock_capacity_tiles\" SET \"mock_xml\"=\"mock_xml\"||' ' WHERE \"mock_key\"=1;\nCOMMIT;");refusal(destination,Code.RESOURCE_LIMIT);
            oracleCapacitySource(65536,"é");
            ora("UPDATE \"mock_oracle\".\"mock_capacity_tiles\" SET \"mock_xml\"=\"mock_xml\"||RPAD(' ',1000,' ') WHERE \"mock_key\"=1;\nCOMMIT;");refusal(destination,Code.RESOURCE_LIMIT);
        } finally {ready=original;}
        System.out.println("ORACLE exact text keys/pre-transfer key bounds/incremental multibyte-total refusal PASS");
    }
    static void oracleCapacitySource(int characters,String fill) throws Exception {
        ora("DECLARE c CLOB; remaining PLS_INTEGER := "+(characters-7)+"; n PLS_INTEGER; BEGIN DBMS_LOB.CREATETEMPORARY(c,TRUE); DBMS_LOB.WRITEAPPEND(c,3,'<x>'); WHILE remaining>0 LOOP n:=LEAST(remaining,16000); DBMS_LOB.WRITEAPPEND(c,n,RPAD('"+fill+"',n,'"+fill+"')); remaining:=remaining-n; END LOOP; DBMS_LOB.WRITEAPPEND(c,4,'</x>'); DELETE FROM \"mock_oracle\".\"mock_capacity_tiles\"; FOR k IN 1..128 LOOP INSERT INTO \"mock_oracle\".\"mock_capacity_tiles\" VALUES(k,c); END LOOP; COMMIT; DBMS_LOB.FREETEMPORARY(c); END;\n/");
    }

    static void oracleDriverCancellation(ObservationDestination destination) throws Exception {
        var cancellation=new ObservationPort.Cancellation();var credentials=new TransientCredentials(reader.toUpperCase(Locale.ROOT).toCharArray(),password.toCharArray());
        var substitutions=new java.util.concurrent.atomic.AtomicInteger();
        var lifecycleErrors=new java.util.concurrent.ConcurrentHashMap<String,Integer>();
        var adapter=new JdbcObservation(destination,c->{
            var connection=OracleReadOnlyAccountQualification.connect(reader.toUpperCase(Locale.ROOT));
            return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(DisposableObservationQualification.class.getClassLoader(),new Class<?>[]{java.sql.Connection.class},(proxy,method,args)->{
                try {
                    if(method.getName().equals("prepareStatement") && args[0].equals("SELECT version_full FROM sys.v_$instance")) {
                        substitutions.incrementAndGet();return connection.prepareStatement("BEGIN DBMS_SESSION.SLEEP(5); END;");
                    }
                    return method.invoke(connection,args);
                }catch(java.lang.reflect.InvocationTargetException failure){if(failure.getCause() instanceof java.sql.SQLException sql)lifecycleErrors.put(method.getName(),sql.getErrorCode());throw failure.getCause();}
            });
        },()->{},TimeUnit.SECONDS.toNanos(30),TimeUnit.SECONDS.toNanos(5));
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var future=executor.submit(()->adapter.observe(new ObservationPort.Selection(ready,"mock-oracle"),credentials,cancellation));
            boolean waiting=false;
            for(int attempt=0;attempt<50;attempt++) {
                waiting=ora("SELECT COUNT(*) FROM v$session WHERE username='"+reader.toUpperCase(Locale.ROOT)+"' AND event='PL/SQL lock timer';").equals("1");
                if(waiting)break;Thread.sleep(20);
            }
            check(waiting && substitutions.get()==1,"ORACLE_DRIVER_WAIT_CONTROL_NOT_OBSERVED");cancellation.cancel();
            var result=future.get(8,TimeUnit.SECONDS);
            backendGone(Engine.ORACLE);
            System.out.println("Driver lifecycle result="+result+" safeErrorCodes="+lifecycleErrors+" retainedStatus="+(result instanceof Refused r && r.cleanupHandle().isPresent()?r.cleanupHandle().get().status():result.cleanup()));
            check(result instanceof Refused r && r.code()==Code.CANCELLED && r.cleanup()==Cleanup.INCONCLUSIVE && r.cleanupHandle().isPresent(),"ORACLE_DRIVER_CANCEL_MUST_RETAIN_QUARANTINE_"+result);
            check(Objects.equals(lifecycleErrors.get("rollback"),17008),"ORACLE_DRIVER_CONTROL_ROLLBACK_FAILURE_NOT_OBSERVED");
            check(((Refused)result).cleanupHandle().orElseThrow().retry()==Cleanup.INCONCLUSIVE,"BACKEND_DISAPPEARANCE_PROMOTED_UNCONFIRMED_CLEANUP");
            check(credentials.closed(),"ORACLE_DRIVER_CONTROL_CREDENTIALS_RETAINED");backendGone(Engine.ORACLE);
        }
        System.out.println("ORACLE test-only vendor sleep / actual Statement.cancel / backend disappearance / retained INCONCLUSIVE PASS");
    }

    static void oracleCancellation(ObservationDestination destination) throws Exception {
        for(boolean disconnect:List.of(false,true)) {
            var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
            var cancellation=new ObservationPort.Cancellation();var credentials=new TransientCredentials(reader.toUpperCase(Locale.ROOT).toCharArray(),password.toCharArray());
            var adapter=new JdbcObservation(destination,()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("MOCK_BARRIER_TIMEOUT");}catch(InterruptedException failure){throw new IllegalStateException("MOCK_BARRIER_INTERRUPTED");}});
            try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
                var future=executor.submit(()->adapter.observe(new ObservationPort.Selection(ready,"mock-oracle"),credentials,cancellation));
                try {
                    check(entered.await(5,TimeUnit.SECONDS),"ORACLE_SOURCE_BARRIER_NOT_REACHED");check(credentials.closed(),"ORACLE_CREDENTIALS_RETAINED");
                    String session=ora("SELECT TO_CHAR(sid)||','||TO_CHAR(serial#) FROM v$session WHERE username='"+reader.toUpperCase(Locale.ROOT)+"';");
                    check(session.matches("[0-9]+,[0-9]+"),"ORACLE_ORIGINAL_SESSION_NOT_OBSERVED");
                    if(disconnect)ora("ALTER SYSTEM KILL SESSION '"+session+"' IMMEDIATE;");else cancellation.cancel();
                } finally {release.countDown();}
                var result=future.get(8,TimeUnit.SECONDS);check(result instanceof Refused,"ORACLE_INTERRUPTED_READ_AUTHORIZED");
                if(disconnect)check(result.cleanup()==Cleanup.INCONCLUSIVE && ((Refused)result).cleanupHandle().isPresent(),"ORACLE_DISCONNECT_NOT_QUARANTINED");
                else check(((Refused)result).code()==Code.CANCELLED && result.cleanup()==Cleanup.COMPLETE,"ORACLE_CANCEL_NOT_CONFIRMED_"+result);
                backendGone(Engine.ORACLE);
            }
        }
        System.out.println("ORACLE actual operation cancel/backend disappearance/disconnect-quarantine PASS");
    }

    static void oracleSource(int characters,String fill) throws Exception {
        ora("DECLARE c CLOB; remaining PLS_INTEGER := "+(characters-7)+"; n PLS_INTEGER; BEGIN DBMS_LOB.CREATETEMPORARY(c,TRUE); DBMS_LOB.WRITEAPPEND(c,3,'<x>'); WHILE remaining>0 LOOP n:=LEAST(remaining,16000); DBMS_LOB.WRITEAPPEND(c,n,RPAD('"+fill+"',n,'"+fill+"')); remaining:=remaining-n; END LOOP; DBMS_LOB.WRITEAPPEND(c,4,'</x>'); UPDATE \"mock_oracle\".\"mock_tiles\" SET \"mock_xml\"=c WHERE \"mock_key\"=1; COMMIT; DBMS_LOB.FREETEMPORARY(c); END;\n/");
    }
    static void oracleBoundsAndPolicy(ObservationDestination destination) throws Exception {
        String user=reader.toUpperCase(Locale.ROOT);
        oracleSource(1_048_576,"a"); check(observe(destination) instanceof Complete,"ORACLE_EXACT_CHARACTER_BOUNDARY_REFUSED");backendGone(Engine.ORACLE);
        oracleSource(1_048_577,"a");refusal(destination,Code.RESOURCE_LIMIT);restore(Engine.ORACLE);
        oracleSource(100_000,"é");var unicode=observe(destination);check(unicode instanceof Complete,"ORACLE_MULTIBYTE_CLOB_REFUSED");
        var document=((Complete)unicode).observation().documents().getFirst();check(document.characters()==100_000 && document.utf8Bytes()==199_993,"ORACLE_CLOB_COUNTS_NOT_STRICT");backendGone(Engine.ORACLE);restore(Engine.ORACLE);
        ora("ALTER USER "+user+" READ WRITE;");refusal(destination,Code.ACCOUNT_NOT_READ_ONLY);ora("ALTER USER "+user+" READ ONLY;");
        ora("CREATE OR REPLACE PROCEDURE \"mock_oracle\".MOCK_POLICY_CANARY AUTHID DEFINER AS BEGIN NULL; END;\n/\nGRANT EXECUTE ON \"mock_oracle\".MOCK_POLICY_CANARY TO PUBLIC;");
        refusal(destination,Code.ACCOUNT_NOT_READ_ONLY);ora("REVOKE EXECUTE ON \"mock_oracle\".MOCK_POLICY_CANARY FROM PUBLIC;");
        ora("GRANT EXECUTE ON \"mock_oracle\".MOCK_POLICY_CANARY TO "+user+";");refusal(destination,Code.ACCOUNT_NOT_READ_ONLY);ora("REVOKE EXECUTE ON \"mock_oracle\".MOCK_POLICY_CANARY FROM "+user+";");
        ora("CREATE OR REPLACE FUNCTION \"mock_oracle\".MOCK_HIDDEN_POLICY(s VARCHAR2,o VARCHAR2) RETURN VARCHAR2 AUTHID DEFINER AS BEGIN RETURN '1=0'; END;\n/\nBEGIN DBMS_RLS.ADD_POLICY(object_schema=>'\"mock_oracle\"',object_name=>'\"mock_tiles\"',policy_name=>'MOCK_HIDDEN',function_schema=>'\"mock_oracle\"',policy_function=>'MOCK_HIDDEN_POLICY',statement_types=>'SELECT'); END;\n/");
        refusal(destination,Code.VISIBILITY_UNQUALIFIED);
        ora("BEGIN DBMS_RLS.DROP_POLICY(object_schema=>'\"mock_oracle\"',object_name=>'\"mock_tiles\"',policy_name=>'MOCK_HIDDEN'); END;\n/");
        System.out.println("ORACLE exact/over UTF16, multibyte CLOB, account-mode, PUBLIC/direct EXECUTE drift and VPD PASS");
    }

    static void oracleAdversaries(ObservationDestination destination) throws Exception {
        String user=reader.toUpperCase(Locale.ROOT);
        ora("DELETE FROM \"mock_oracle\".\"mock_tiles\" WHERE \"mock_key\"=2;\nCOMMIT;"); refusal(destination,Code.INVENTORY_MISMATCH); restore(Engine.ORACLE);
        ora("INSERT INTO \"mock_oracle\".\"mock_tiles\" VALUES(3,'<invented/>');\nCOMMIT;"); refusal(destination,Code.INVENTORY_MISMATCH); restore(Engine.ORACLE);
        ora("GRANT UPDATE(\"mock_xml\") ON \"mock_oracle\".\"mock_tiles\" TO "+user+";"); refusal(destination,Code.ACCOUNT_NOT_READ_ONLY);
        ora("REVOKE UPDATE ON \"mock_oracle\".\"mock_tiles\" FROM "+user+";");
        String role="ES_WRITER_"+UUID.randomUUID().toString().replace("-", "").substring(0,12).toUpperCase(Locale.ROOT);
        ora("CREATE ROLE "+role+";\nGRANT UPDATE ON \"mock_oracle\".\"mock_tiles\" TO "+role+";\nGRANT "+role+" TO "+user+";\nALTER USER "+user+" DEFAULT ROLE NONE;"); refusal(destination,Code.ACCOUNT_NOT_READ_ONLY);
        ora("REVOKE "+role+" FROM "+user+";");
        ora("REVOKE SELECT ON SYS.DBA_POLICIES FROM "+user+";"); refusal(destination,Code.METADATA_UNAVAILABLE);
        ora("GRANT SELECT ON SYS.DBA_POLICIES TO "+user+";");
        ora("REVOKE SELECT ON \"mock_oracle\".\"mock_tiles\" FROM "+user+";"); refusal(destination,Code.ACCOUNT_NOT_READ_ONLY);
        ora("GRANT SELECT ON \"mock_oracle\".\"mock_tiles\" TO "+user+";");
        ora("UPDATE \"mock_oracle\".\"mock_tiles\" SET \"mock_xml\"=EMPTY_CLOB() WHERE \"mock_key\"=1;\nCOMMIT;"); refusal(destination,Code.EMPTY_SOURCE); restore(Engine.ORACLE);
        if(ora("SELECT nullable FROM dba_tab_cols WHERE owner='mock_oracle' AND table_name='mock_tiles' AND column_name='mock_xml';").equals("N"))oracleDdl("ALTER TABLE \"mock_oracle\".\"mock_tiles\" MODIFY \"mock_xml\" NULL;");
        ora("UPDATE \"mock_oracle\".\"mock_tiles\" SET \"mock_xml\"=NULL WHERE \"mock_key\"=1;\nCOMMIT;"); refusal(destination,Code.NULL_SOURCE); restore(Engine.ORACLE);
        oracleDdl("ALTER TABLE \"mock_oracle\".\"mock_tiles\" MODIFY \"mock_xml\" NOT NULL;");
        ora("UPDATE \"mock_oracle\".\"mock_tiles\" SET \"mock_xml\"='<unclosed>' WHERE \"mock_key\"=1;\nCOMMIT;"); refusal(destination,Code.INVALID_SOURCE); restore(Engine.ORACLE);
        var wrong=new HashMap<>(destination.expectedPhysicalIdentity()); wrong.put("pdbGuid","0".repeat(32));
        refusal(new ObservationDestination(destination.id(),destination.engine(),destination.host(),destination.port(),destination.database(),destination.transport(),destination.trustMaterial(),destination.transportIdentity(),wrong,destination.provisioningPolicyVersion(),destination.accountPolicyVersion()),Code.DESTINATION_MISMATCH);
        var changed=new AtomicBoolean();
        var snapshot=new JdbcObservation(destination,()->{try{ora("UPDATE \"mock_oracle\".\"mock_tiles\" SET \"mock_xml\"='<changed/>' WHERE \"mock_key\"=1;\nINSERT INTO \"mock_oracle\".\"mock_tiles\" VALUES(3,'<extra/>');\nCOMMIT;");changed.set(true);}catch(Exception failure){throw new IllegalStateException("MOCK_CONCURRENT_UPDATE_FAILED");}});
        var observed=snapshot.observe(new ObservationPort.Selection(ready,"mock-oracle"),new TransientCredentials(user.toCharArray(),password.toCharArray()),new ObservationPort.Cancellation());
        check(changed.get() && observed instanceof Complete,"ORACLE_CONCURRENT_SNAPSHOT_NOT_COMPLETE_"+observed);
        for(var document:((Complete)observed).observation().documents())check(document.xml().equals(xml.get(document.documentId())),"ORACLE_SNAPSHOT_MIXED_REVISIONS");
        backendGone(Engine.ORACLE); restore(Engine.ORACLE);
        var ddlChanged=new AtomicBoolean();
        var ddlAdapter=new JdbcObservation(destination,()->{try{ora("ALTER TABLE \"mock_oracle\".\"mock_tiles\" RENAME COLUMN \"mock_key\" TO \"mock_key_changed\";");ddlChanged.set(true);}catch(Exception failure){throw new IllegalStateException("MOCK_CONCURRENT_DDL_FAILED");}});
        try {
            var ddlResult=ddlAdapter.observe(new ObservationPort.Selection(ready,"mock-oracle"),new TransientCredentials(user.toCharArray(),password.toCharArray()),new ObservationPort.Cancellation());
            check(ddlChanged.get() && ddlResult instanceof Refused r && r.code()==Code.DATABASE_FAILURE && r.cleanup()==Cleanup.COMPLETE,"ORACLE_CONCURRENT_DDL_AUTHORIZED_"+ddlResult);backendGone(Engine.ORACLE);
        } finally {if(ddlChanged.get())oracleDdl("ALTER TABLE \"mock_oracle\".\"mock_tiles\" RENAME COLUMN \"mock_key_changed\" TO \"mock_key\";");}
        System.out.println("ORACLE membership/column/role/metadata/SELECT/destination/null/empty/invalid XML/snapshot/concurrent-DDL refusal PASS");
    }

    public static void main(String[] arguments) throws Exception {
        try { qualify(arguments); } catch(java.sql.SQLException failure) { throw new IllegalStateException("DISPOSABLE_JDBC_CODE_"+failure.getErrorCode()); }
    }
    static void qualify(String[] arguments) throws Exception {
        if(arguments.length<1 || arguments.length>2)throw new IllegalArgumentException("CHECKOUT_PATH_REQUIRED");
        Path checkout=Path.of(arguments[0]);
        xml.put("glyph-sheet",Files.readString(checkout.resolve("fixtures/db-observation/glyphs.xml")));
        xml.put("palette-sheet",Files.readString(checkout.resolve("fixtures/db-observation/palettes.xml")));
        var compiled=new NativeDefinitionBytesCompiler().compile(Files.readAllBytes(checkout.resolve("fixtures/native-v2/definition.json")),studio.environment.server.definition.DefinitionBytesCompiler.Format.JSON);
        check(compiled instanceof NativeCompilationResult.ReadyToPublish,"MOCK_DEFINITION_NOT_READY"); ready=(NativeCompilationResult.ReadyToPublish)compiled;
        pg("CREATE ROLE "+reader+" LOGIN PASSWORD '"+password+"'; REVOKE TEMP,CREATE ON DATABASE postgres FROM PUBLIC; CREATE SCHEMA IF NOT EXISTS mock_pg; CREATE TABLE IF NOT EXISTS mock_pg.mock_tiles(mock_key bigint PRIMARY KEY,mock_xml text NOT NULL); GRANT USAGE ON SCHEMA mock_pg TO "+reader+"; GRANT SELECT ON mock_pg.mock_tiles TO "+reader+"; REVOKE EXECUTE ON FUNCTION pg_control_system() FROM PUBLIC; GRANT EXECUTE ON FUNCTION pg_control_system() TO "+reader+";");
        boolean lifecycleOnly=arguments.length==2 && arguments[1].equals("postgresql-lifecycle");
        boolean postgresOnly=arguments.length==2 && (arguments[1].equals("postgresql") || arguments[1].equals("postgresql-privileges") || lifecycleOnly);
        if(lifecycleOnly){restore(Engine.POSTGRESQL);postgresCancellation(destination(Engine.POSTGRESQL));process(List.of("docker","logs","--tail","10000",POSTGRES),"");System.out.println("Targeted lifecycle assertions: "+passed+" PASS");return;}
        if(!postgresOnly) {
            // These identities were created only by this harness in the root-owned disposable PDB.
            String ownUsers=ora("SELECT DISTINCT p.table_name FROM dba_tab_privs p JOIN dba_users u ON u.username=p.table_name WHERE p.grantee='PUBLIC' AND p.type='USER' AND p.privilege='INHERIT PRIVILEGES' AND u.oracle_maintained='N' AND (u.username LIKE 'ES_READER_%' OR u.username='mock_oracle');");
            if(!ownUsers.isBlank()) for(String own:ownUsers.split("\\R"))ora("REVOKE INHERIT PRIVILEGES ON USER \""+own.strip()+"\" FROM PUBLIC;");
        }
        ObservationDestination oracle=postgresOnly?null:destination(Engine.ORACLE);
        if(!postgresOnly) {
        if(ora("SELECT count(*) FROM dba_users WHERE username='mock_oracle';").equals("0"))ora("CREATE USER \"mock_oracle\" IDENTIFIED BY \""+password()+"\" ACCOUNT LOCK QUOTA 256M ON USERS;\nREVOKE INHERIT PRIVILEGES ON USER \"mock_oracle\" FROM PUBLIC;");
        ora("ALTER USER \"mock_oracle\" QUOTA 256M ON USERS;");
        if(ora("SELECT count(*) FROM dba_tables WHERE owner='mock_oracle' AND table_name='mock_tiles';").equals("0"))oracleDdl("CREATE TABLE \"mock_oracle\".\"mock_tiles\"(\"mock_key\" NUMBER(19,0) PRIMARY KEY,\"mock_xml\" CLOB NOT NULL);");
        var grants=new StringBuilder("CREATE USER \"").append(reader.toUpperCase(Locale.ROOT)).append("\" IDENTIFIED BY \"").append(password).append("\";\nGRANT CREATE SESSION TO \"").append(reader.toUpperCase(Locale.ROOT)).append("\";\nGRANT SELECT ON \"mock_oracle\".\"mock_tiles\" TO \"").append(reader.toUpperCase(Locale.ROOT)).append("\";\n");
        for(String view:List.of("V_$DATABASE","V_$CONTAINERS","V_$INSTANCE","V_$OPTION","DBA_USERS","DBA_OBJECTS","DBA_SYS_PRIVS","DBA_ROLE_PRIVS","DBA_SCHEMA_PRIVS","DBA_TAB_PRIVS","DBA_COL_PRIVS","DBA_TABLES","DBA_EXTERNAL_TABLES","DBA_SYNONYMS","DBA_TRIGGERS","DBA_POLICIES","DBA_AUDIT_POLICIES","REDACTION_POLICIES","DBA_TAB_COLS","DBA_CONSTRAINTS","DBA_CONS_COLUMNS"))grants.append("GRANT SELECT ON SYS.").append(view).append(" TO \"").append(reader.toUpperCase(Locale.ROOT)).append("\";\n");
        ora(grants.toString());
        ora("REVOKE INHERIT PRIVILEGES ON USER \""+reader.toUpperCase(Locale.ROOT)+"\" FROM PUBLIC;\nALTER USER \""+reader.toUpperCase(Locale.ROOT)+"\" READ ONLY;");
        }
        var engines=postgresOnly?List.of(Engine.POSTGRESQL):List.of(Engine.POSTGRESQL,Engine.ORACLE);
        for(Engine engine:engines)restore(engine);
        var postgres=destination(Engine.POSTGRESQL);
        if(arguments.length==2 && arguments[1].equals("postgresql-privileges")){PostgresPrivilegeQualification.run(postgres);return;}
        if(arguments.length==2 && arguments[1].equals("oracle-driver-lifecycle")){oracleDriverCancellation(oracle);process(List.of("docker","logs","--tail","10000",ORACLE),"");System.out.println("Targeted Oracle driver lifecycle assertions: "+passed+" PASS");return;}
        for(var destination:postgresOnly?List.of(postgres):List.of(postgres,oracle)) {
            var result=observe(destination);
            check(result instanceof Complete,"BASELINE_"+destination.engine()+"_"+result);
            var observation=((Complete)result).observation();
            check(observation.documents().size()==2,"INVENTORY_SIZE");
            for(var document:observation.documents())check(document.xml().equals(xml.get(document.documentId())),"EXACT_SOURCE_MISMATCH");
            var second=(Complete)observe(destination);
            check(second.observation().fingerprint().equals(observation.fingerprint()),"NOOP_FINGERPRINT_CHANGED");
            backendGone(destination.engine());
            System.out.println(destination.engine()+" baseline/no-op/exact Unicode/backend disappearance PASS");
        }
        if(!postgresOnly){oracleAdversaries(oracle);oracleBoundsAndPolicy(oracle);oracleTextAndCapacity(oracle);oracleDriverCancellation(oracle);oracleCancellation(oracle);}
        PostgresPrivilegeQualification.run(postgres);
        postgresAdversaries(postgres);
        postgresTextAndCapacity(postgres);
        postgresCancellation(postgres);
        for(String container:List.of(POSTGRES,ORACLE))process(List.of("docker","logs","--tail","10000",container),"");
        System.out.println("D04 disposable qualification assertions: "+passed+" PASS");
    }
}
