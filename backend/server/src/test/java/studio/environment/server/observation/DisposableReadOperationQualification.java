package studio.environment.server.observation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import studio.environment.core.definitionv2.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.*;
import studio.environment.core.observation.ObservationResult.*;
import studio.environment.server.definition.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Explicit new-policy qualification. Only the newly owned labelled disposable mock lab is admitted. */
public final class DisposableReadOperationQualification {
    private final JsonMapper json=JsonMapper.builder().build();
    private final Map<String,Object> state;
    private final Path base,checkout,probeState;
    private final List<String> canaries=new ArrayList<>();
    private final String suffix=UUID.randomUUID().toString().replace("-","").substring(0,8);
    private final String pgOwner="mock_po_"+suffix,oracleOwner="MOCK_OO_"+suffix.toUpperCase(Locale.ROOT);
    private final String pgPassword=password(),oraclePassword=password();
    private final Map<String,String> sources=new LinkedHashMap<>();
    private NativeCompilationResult.ReadyToPublish ready;
    private String definition;
    private int checks;
    @SuppressWarnings("unchecked") private DisposableReadOperationQualification(Path stateFile,Path checkout) throws Exception {
        this.state=json.readValue(Files.readString(stateFile),Map.class);this.base=Path.of((String)state.get("base"));this.checkout=checkout;this.probeState=base.resolve("observation-state.json");
        check(base.isAbsolute() && base.getFileName().toString().matches("es-read-policy-[a-f0-9]{8}"),"LAB_PATH_REFUSED");
        String expectedSuffix=base.getFileName().toString().substring("es-read-policy-".length());
        for(String engine:List.of("pg","oracle")) {
            check(state.get(engine+"Name").equals("es-read-policy-"+engine+"-"+expectedSuffix),"LAB_NAME_REFUSED");
            String label=capture(List.of("docker","inspect","--format","{{index .Config.Labels \"studio.qualification\"}}|{{index .Config.Labels \"studio.work-unit\"}}",name(engine)),new byte[0],10).strip();
            check(label.equals("independent-mock|20260909-d04-read-operation"),"LAB_OWNERSHIP_REFUSED");
        }
        sources.put("glyph-sheet",Files.readString(checkout.resolve("fixtures/native-v2/xml/glyphs.xml")));
        sources.put("palette-sheet",Files.readString(checkout.resolve("fixtures/native-v2/xml/palettes.xml")));
        canaries.add("invented repeated values & untouched markup");
        var tree=(ObjectNode)json.readTree(Files.readString(checkout.resolve("fixtures/native-v2/definition.json")));
        for(var binding:tree.get("bindings")) ((ObjectNode)binding).put("schema",binding.get("engine").asString().equals("postgresql")?pgOwner:oracleOwner);
        definition=json.writeValueAsString(tree);
        ready=(NativeCompilationResult.ReadyToPublish)new NativeDefinitionBytesCompiler().compile(definition.getBytes(StandardCharsets.UTF_8),DefinitionBytesCompiler.Format.JSON);
    }
    private String password() { byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);String result="Q9"+HexFormat.of().formatHex(bytes);Arrays.fill(bytes,(byte)0);canaries.add(result);return result; }
    private String name(String engine) { return (String)state.get(engine+"Name"); }
    private void check(boolean value,String code) { if(!value)throw new IllegalStateException(code);checks++; }
    private String capture(List<String> command,byte[] input,int seconds) throws Exception {
        var process=new ProcessBuilder(command).redirectErrorStream(true).start();
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var output=workers.submit(()->{try(var stream=process.getInputStream()){byte[] value=stream.readNBytes(1_048_577);if(value.length>1_048_576)throw new IOException("MOCK_OUTPUT_LIMIT");return value;}});
            var writer=workers.submit(()->{try(var stream=process.getOutputStream()){stream.write(input);}return true;});
            try {
                if(!process.waitFor(seconds,TimeUnit.SECONDS))throw new IllegalStateException("MOCK_PROCESS_DEADLINE");
                writer.get(2,TimeUnit.SECONDS);String text=new String(output.get(2,TimeUnit.SECONDS),StandardCharsets.UTF_8);
                for(String secret:canaries)check(!text.contains(secret),"MOCK_DIAGNOSTIC_CANARY_LEAK");
                if(process.exitValue()!=0){var oracleCode=java.util.regex.Pattern.compile("ORA-([0-9]{5})").matcher(text);throw new IllegalStateException(oracleCode.find()?"MOCK_PROCESS_ORA_"+oracleCode.group(1):"MOCK_PROCESS_REFUSED");}return text;
            } finally { process.destroyForcibly();process.getOutputStream().close();process.getInputStream().close();Arrays.fill(input,(byte)0); }
        }
    }
    private String pg(String sql) throws Exception { return capture(List.of("docker","exec","-i","--user","999",name("pg"),"psql","-X","-U","postgres","-d","postgres","-At","-v","ON_ERROR_STOP=1"),(sql+"\n").getBytes(StandardCharsets.UTF_8),20).strip(); }
    private String oracle(String sql) throws Exception {
        return capture(List.of("docker","exec","-i",name("oracle"),"sqlplus","-s","/ as sysdba"),("set pagesize 0 heading off feedback off echo off verify off linesize 32767 trimspool on\nwhenever sqlerror exit failure\nalter session set container=FREEPDB1;\n"+sql+"\nexit\n").getBytes(StandardCharsets.UTF_8),30).strip();
    }
    private void provision() throws Exception {
        pg("CREATE ROLE "+pgOwner+" LOGIN PASSWORD '"+pgPassword+"'; CREATE SCHEMA "+pgOwner+" AUTHORIZATION "+pgOwner+"; CREATE TABLE "+pgOwner+".mock_tiles(mock_key bigint PRIMARY KEY,mock_xml text); ALTER TABLE "+pgOwner+".mock_tiles OWNER TO "+pgOwner+"; GRANT EXECUTE ON FUNCTION pg_catalog.pg_control_system() TO "+pgOwner+";");
        oracle("CREATE USER "+oracleOwner+" IDENTIFIED BY \""+oraclePassword+"\" DEFAULT TABLESPACE USERS QUOTA 64M ON USERS;\nGRANT CREATE SESSION TO "+oracleOwner+";\nCREATE TABLE "+oracleOwner+".\"mock_tiles\"(\"mock_key\" NUMBER(19,0) PRIMARY KEY,\"mock_xml\" CLOB);");
        for(String catalog:List.of("V_$INSTANCE","NLS_DATABASE_PARAMETERS","DBA_AUDIT_POLICIES","V_$OPTION","V_$DATABASE","V_$CONTAINERS","DBA_USERS","DBA_TABLES","DBA_EXTERNAL_TABLES","DBA_SYNONYMS","DBA_TRIGGERS","DBA_POLICIES","REDACTION_POLICIES","DBA_TAB_PRIVS","SESSION_ROLES","SESSION_PRIVS","SESSION_SCHEMA_PRIVS","DBA_TAB_COLS","DBA_CONSTRAINTS","DBA_CONS_COLUMNS")) oracle("GRANT SELECT ON SYS."+catalog+" TO "+oracleOwner+";");
        var pgIds=pg("SELECT s.system_identifier::text||'|'||d.oid::text||'|'||d.datname FROM pg_catalog.pg_control_system() s,pg_catalog.pg_database d WHERE d.datname=current_database();").split("\\|");
        check(pgIds.length==3,"PG_IDENTITY_WITNESS_FAILED");state.put("pgIdentity",Map.of("systemIdentifier",pgIds[0],"databaseOid",pgIds[1],"databaseName",pgIds[2]));
        var oracleIds=oracle("SELECT TO_CHAR(d.dbid)||'|'||d.db_unique_name||'|'||TO_CHAR(c.con_id)||'|'||TO_CHAR(c.con_uid)||'|'||c.name||'|'||LOWER(RAWTOHEX(c.guid)) FROM SYS.V_$DATABASE d CROSS JOIN SYS.V_$CONTAINERS c WHERE c.con_id=TO_NUMBER(SYS_CONTEXT('USERENV','CON_ID'));").split("\\|");
        check(oracleIds.length==6,"ORACLE_IDENTITY_WITNESS_FAILED");state.put("oracleIdentity",Map.of("dbid",oracleIds[0],"dbUniqueName",oracleIds[1],"conId",oracleIds[2],"conUid",oracleIds[3],"conName",oracleIds[4],"pdbGuid",oracleIds[5]));
        Files.writeString(probeState,json.writeValueAsString(state));
        for(var engine:Engine.values()) try(var connection=connect(engine,owner(engine),secret(engine))) {
            var binding=binding(engine);
            check(binding.schema().equals(owner(engine)),"MOCK_BINDING_OWNER_MISMATCH");
            if(engine==Engine.ORACLE) {
                try(var diagnostic=connection.prepareStatement("SELECT SYS_CONTEXT('USERENV','SESSION_USER'),SYS_CONTEXT('USERENV','CON_NAME') FROM SYS.DUAL");var rows=diagnostic.executeQuery()) {
                    check(rows.next() && rows.getString(1).equals(oracleOwner) && rows.getString(2).equals("FREEPDB1"),"MOCK_ORACLE_SESSION_MISMATCH");
                }
                try(var diagnostic=connection.prepareStatement("SELECT COUNT(*) FROM SYS.DBA_TABLES WHERE owner=? AND table_name=?")) {
                    diagnostic.setString(1,binding.schema());diagnostic.setString(2,binding.table());
                    try(var rows=diagnostic.executeQuery()){check(rows.next() && rows.getInt(1)==1,"MOCK_ORACLE_SOURCE_TABLE_MISSING");}
                }
            }
            try(var insert=connection.prepareStatement("INSERT INTO "+quoted(binding.schema())+"."+quoted(binding.table())+"("+quoted(binding.keyColumn())+","+quoted(binding.xmlColumn())+") VALUES(?,?)")) {
                for(var document:binding.documents()) { insert.setLong(1,Long.parseLong(document.key()));insert.setString(2,sources.get(document.id()));check(insert.executeUpdate()==1,"MOCK_INSERT_COUNT"); }
            }
            connection.commit();
        }
        Thread.sleep(1200); // Oracle same-second table DDL must precede the read-only snapshot.
        System.out.println("LAB_PROVISIONED:2_ENGINES:2_DOCUMENTS_EACH");
    }
    private static String quoted(String identifier) { return SqlRead.quoted(identifier); }
    private Binding binding(Engine engine) { return ready.checked().definition().bindings().stream().filter(b->b.engine()==engine).findFirst().orElseThrow(); }
    private String owner(Engine engine) { return engine==Engine.POSTGRESQL?pgOwner:oracleOwner; }
    private String secret(Engine engine) { return engine==Engine.POSTGRESQL?pgPassword:oraclePassword; }
    @SuppressWarnings("unchecked") private ObservationDestination destination(Engine engine) {
        String prefix=engine==Engine.POSTGRESQL?"pg":"oracle";boolean tls=Boolean.TRUE.equals(state.get("tlsReady"));
        return new ObservationDestination("mock-policy",engine,"localhost",((Number)state.get(prefix+(tls&&engine==Engine.ORACLE?"TlsPort":"Port"))).intValue(),engine==Engine.POSTGRESQL?"postgres":"FREEPDB1",tls?ObservationDestination.Transport.VERIFIED_TLS:ObservationDestination.Transport.DISPOSABLE_LOOPBACK,tls?(String)state.get(prefix+"Trust"):"","mock-independent-transport",(Map<String,String>)state.get(prefix+"Identity"),"mock-policy-witness-v1",engine==Engine.POSTGRESQL?"postgresql-read-operation-v1":"oracle-read-operation-v1");
    }
    private Connection connect(Engine engine,String user,String password) throws SQLException {
        var d=destination(engine);var properties=new Properties();properties.setProperty("user",user);properties.setProperty("password",password);
        try {
            Connection connection;
            if(engine==Engine.POSTGRESQL) {
                properties.setProperty("connectTimeout","5");properties.setProperty("socketTimeout","2");properties.setProperty("sslmode",d.transport()==ObservationDestination.Transport.VERIFIED_TLS?"verify-full":"disable");
                if(d.transport()==ObservationDestination.Transport.VERIFIED_TLS)properties.setProperty("sslrootcert",d.trustMaterial());
                connection=new org.postgresql.Driver().connect("jdbc:postgresql://localhost:"+d.port()+"/postgres",properties);
            } else {
                properties.setProperty("oracle.net.CONNECT_TIMEOUT","5000");properties.setProperty("oracle.jdbc.ReadTimeout","2000");
                if(d.transport()==ObservationDestination.Transport.VERIFIED_TLS) { properties.setProperty("oracle.net.ssl_server_dn_match","true");properties.setProperty("javax.net.ssl.trustStore",d.trustMaterial()); }
                connection=new oracle.jdbc.OracleDriver().connect("jdbc:oracle:thin:@(DESCRIPTION=(RETRY_COUNT=0)(ADDRESS=(PROTOCOL="+(d.transport()==ObservationDestination.Transport.VERIFIED_TLS?"tcps":"tcp")+")(HOST=localhost)(PORT="+d.port()+"))(CONNECT_DATA=(SERVICE_NAME=FREEPDB1)))",properties);
            }
            connection.setAutoCommit(false);return connection;
        } finally {properties.clear();}
    }
    private String witness(Engine engine) throws Exception {
        if(engine==Engine.POSTGRESQL)return pg("SELECT mock_key::text||':'||encode(sha256(convert_to(mock_xml,'UTF8')),'hex') FROM "+pgOwner+".mock_tiles ORDER BY mock_key;");
        return oracle("SELECT TO_CHAR(\"mock_key\")||':'||LOWER(RAWTOHEX(STANDARD_HASH(SYS.DBMS_LOB.SUBSTR(\"mock_xml\",4000,1),'SHA256'))) FROM "+oracleOwner+".\"mock_tiles\" ORDER BY \"mock_key\";");
    }
    private void baseline(Engine engine) throws Exception {
        var bytes=new ByteArrayOutputStream();try(var output=new DataOutputStream(bytes)){output.writeUTF(owner(engine));output.writeUTF(secret(engine));output.writeUTF(definition);output.writeUTF(binding(engine).id());}
        String classpath=base.resolve("baseline-classes")+":"+Files.readString(base.resolve("classpath.txt"));
        String result=capture(List.of("java","-XX:ErrorFile=/dev/null","-XX:-CreateCoredumpOnCrash","-XX:-HeapDumpOnOutOfMemoryError","-cp",classpath,"studio.environment.server.observation.BaselineReadProbe",probeState.toString()),bytes.toByteArray(),40).strip();
        check(result.equals("BASELINE_REFUSED:"+(engine==Engine.POSTGRESQL?"VISIBILITY_UNQUALIFIED":"ACCOUNT_NOT_READ_ONLY")+":COMPLETE"),"BASELINE_DID_NOT_REACH_OLD_PURITY_GUARD");
        System.out.println(engine+":RED_OLD_OWNER_POLICY_REFUSED");
    }
    private record Account(Engine engine,String user,String password,String label) {
        @Override public String toString() { return "MockAccount[REDACTED]"; }
    }
    private final List<Account> accounts=new ArrayList<>();
    private String oracleMetadataRole;
    private void provisionAccounts() throws Exception {
        String pgRole="mock_pw_"+suffix;
        pg("CREATE ROLE "+pgRole+" NOLOGIN; GRANT USAGE ON SCHEMA "+pgOwner+" TO "+pgRole+"; GRANT SELECT,UPDATE ON "+pgOwner+".mock_tiles TO "+pgRole+"; GRANT EXECUTE ON FUNCTION pg_catalog.pg_control_system() TO "+pgRole+";");
        for(String label:List.of("direct","column","role")) {
            var account=new Account(Engine.POSTGRESQL,"mock_p"+label.charAt(0)+"_"+suffix,password(),label);accounts.add(account);
            pg("CREATE ROLE "+account.user()+" LOGIN PASSWORD '"+account.password()+"';");
            if(label.equals("role"))pg("GRANT "+pgRole+" TO "+account.user()+";");
            else pg("GRANT USAGE ON SCHEMA "+pgOwner+" TO "+account.user()+"; GRANT SELECT ON "+pgOwner+".mock_tiles TO "+account.user()+"; GRANT "+(label.equals("column")?"UPDATE(mock_xml)":"UPDATE")+" ON "+pgOwner+".mock_tiles TO "+account.user()+"; GRANT EXECUTE ON FUNCTION pg_catalog.pg_control_system() TO "+account.user()+";");
        }
        oracleMetadataRole="MOCK_META_"+suffix.toUpperCase(Locale.ROOT);
        var grants=new StringBuilder("CREATE ROLE "+oracleMetadataRole+";\n");
        for(String catalog:List.of("V_$INSTANCE","NLS_DATABASE_PARAMETERS","DBA_AUDIT_POLICIES","V_$OPTION","V_$DATABASE","V_$CONTAINERS","DBA_USERS","DBA_TABLES","DBA_EXTERNAL_TABLES","DBA_SYNONYMS","DBA_TRIGGERS","DBA_POLICIES","REDACTION_POLICIES","DBA_TAB_PRIVS","SESSION_ROLES","SESSION_PRIVS","SESSION_SCHEMA_PRIVS","DBA_TAB_COLS","DBA_CONSTRAINTS","DBA_CONS_COLUMNS"))grants.append("GRANT SELECT ON SYS.").append(catalog).append(" TO ").append(oracleMetadataRole).append(";\n");
        String oracleRole="MOCK_RW_"+suffix.toUpperCase(Locale.ROOT);
        grants.append("CREATE ROLE ").append(oracleRole).append(";\nGRANT SELECT,UPDATE ON ").append(oracleOwner).append(".\"mock_tiles\" TO ").append(oracleRole).append(";\n");oracle(grants.toString());
        for(String label:List.of("direct","column","role","schema","system")) {
            var account=new Account(Engine.ORACLE,"MOCK_O"+label.toUpperCase(Locale.ROOT)+"_"+suffix.toUpperCase(Locale.ROOT),password(),label);accounts.add(account);
            oracle("CREATE USER "+account.user()+" IDENTIFIED BY \""+account.password()+"\";\nGRANT CREATE SESSION,"+oracleMetadataRole+" TO "+account.user()+";");
            if(label.equals("role"))oracle("GRANT "+oracleRole+" TO "+account.user()+";");
            else if(label.equals("schema"))oracle("GRANT SELECT ANY TABLE ON SCHEMA "+oracleOwner+" TO "+account.user()+";\nGRANT UPDATE ON "+oracleOwner+".\"mock_tiles\" TO "+account.user()+";");
            else if(label.equals("system"))oracle("GRANT READ ANY TABLE TO "+account.user()+";\nGRANT UPDATE ON "+oracleOwner+".\"mock_tiles\" TO "+account.user()+";");
            else oracle("GRANT SELECT,"+(label.equals("column")?"UPDATE(\"mock_xml\")":"UPDATE")+" ON "+oracleOwner+".\"mock_tiles\" TO "+account.user()+";");
        }
        Thread.sleep(1200);
    }
    private void update(Connection connection,Engine engine,String source) throws SQLException {
        var binding=binding(engine);
        try(var statement=connection.prepareStatement("UPDATE "+quoted(binding.schema())+"."+quoted(binding.table())+" SET "+quoted(binding.xmlColumn())+"=? WHERE "+quoted(binding.keyColumn())+"=1")) {
            statement.setString(1,source);check(statement.executeUpdate()==1,"WRITE_CONTROL_ROW_COUNT");
        }
    }
    private void readAnyWriteControl(Account account) throws Exception {
        String before=witness(Engine.ORACLE);String table=quoted(oracleOwner)+".\"mock_tiles\"";
        try(var connection=connect(Engine.ORACLE,account.user(),account.password())) {
            try(var start=connection.createStatement()){start.execute("SET TRANSACTION READ WRITE");}
            try(var update=connection.prepareStatement("UPDATE "+table+" SET \"mock_xml\"=?")){update.setString(1,"<mock-write-control/>");check(update.executeUpdate()==2,"READ_ANY_WRITE_CONTROL_COUNT");}connection.commit();
        }
        check(!before.equals(witness(Engine.ORACLE)),"READ_ANY_WRITE_CONTROL_NOT_COMMITTED");
        try(var connection=connect(Engine.ORACLE,oracleOwner,oraclePassword);var update=connection.prepareStatement("UPDATE "+table+" SET \"mock_xml\"=? WHERE \"mock_key\"=?")) {
            for(var document:binding(Engine.ORACLE).documents()){update.setString(1,sources.get(document.id()));update.setLong(2,Long.parseLong(document.key()));check(update.executeUpdate()==1,"READ_ANY_RESTORE_COUNT");}connection.commit();
        }
        check(before.equals(witness(Engine.ORACLE)),"READ_ANY_RESTORE_FAILED");boolean refused=false;
        try(var connection=connect(Engine.ORACLE,account.user(),account.password())) {
            var read=new SqlRead(connection,new ObservationPort.Cancellation(),System.nanoTime()+TimeUnit.SECONDS.toNanos(30));read.begin(binding(Engine.ORACLE));
            try(var update=connection.prepareStatement("UPDATE "+table+" SET \"mock_xml\"=?")){update.setString(1,"<mock-write-control/>");update.executeUpdate();}
            catch(SQLException expected){check(expected.getErrorCode()==1456,"READ_ANY_WRONG_READ_ONLY_REFUSAL");refused=true;}
            finally {connection.rollback();}
        }
        check(refused,"READ_ANY_READ_ONLY_DML_NOT_REFUSED");check(before.equals(witness(Engine.ORACLE)),"READ_ANY_READ_ONLY_CHANGED_STATE");
        System.out.println("ORACLE:system:UNFILTERED_READ_WRITE_COMMIT_AND_READ_ONLY_DML_DENIAL_PASS");
    }
    private void writeControl(Account account) throws Exception {
        if(account.engine()==Engine.ORACLE && account.label().equals("system")){readAnyWriteControl(account);return;}
        String before=witness(account.engine());
        try(var connection=connect(account.engine(),account.user(),account.password())) {
            try(var statement=connection.createStatement()){statement.execute("SET TRANSACTION READ WRITE");}
            update(connection,account.engine(),sources.get("glyph-sheet")+"\n");connection.commit();
        }
        check(!before.equals(witness(account.engine())),"READ_WRITE_CONTROL_DID_NOT_COMMIT");
        try(var connection=connect(account.engine(),account.user(),account.password())) {
            try(var statement=connection.createStatement()){statement.execute("SET TRANSACTION READ WRITE");}
            update(connection,account.engine(),sources.get("glyph-sheet"));connection.commit();
        }
        check(before.equals(witness(account.engine())),"WRITE_CONTROL_RESTORE_FAILED");
        boolean refused=false;
        try(var connection=connect(account.engine(),account.user(),account.password())) {
            var read=new SqlRead(connection,new ObservationPort.Cancellation(),System.nanoTime()+TimeUnit.SECONDS.toNanos(30));read.begin(binding(account.engine()));
            try { update(connection,account.engine(),sources.get("glyph-sheet")+"\n"); }
            catch(SQLException expected) { check(account.engine()==Engine.POSTGRESQL?"25006".equals(expected.getSQLState()):expected.getErrorCode()==1456,"WRONG_READ_ONLY_DML_REFUSAL");refused=true; }
            finally {connection.rollback();}
        }
        check(refused,"READ_ONLY_TRANSACTION_ACCEPTED_DML");check(before.equals(witness(account.engine())),"READ_ONLY_DML_CHANGED_COMMITTED_STATE");
        System.out.println(account.engine()+":"+account.label()+":READ_WRITE_COMMIT_AND_READ_ONLY_DML_DENIAL_PASS");
    }
    private String sessions(Engine engine,String user) throws Exception {
        return engine==Engine.POSTGRESQL?pg("SELECT count(*) FROM pg_catalog.pg_stat_activity WHERE usename='"+user+"';"):oracle("SELECT COUNT(*) FROM SYS.V_$SESSION WHERE username='"+user+"';");
    }
    private ObservationResult observe(Account account,java.util.concurrent.atomic.AtomicInteger barrier) throws Exception {
        var credentials=new TransientCredentials(account.user().toCharArray(),account.password().toCharArray());
        var result=new JdbcObservation(destination(account.engine()),()->{
            barrier.incrementAndGet();
            try {check(sessions(account.engine(),account.user()).equals("1"),"INSPECTION_PHYSICAL_CONNECTION_COUNT");}
            catch(Exception failure){throw new IllegalStateException("INSPECTION_SESSION_WITNESS_FAILED");}
        }).observe(new ObservationPort.Selection(ready,binding(account.engine()).id()),credentials,new ObservationPort.Cancellation());
        check(credentials.closed(),"CREDENTIALS_NOT_CLOSED");return result;
    }
    private void accountGreen(Account account) throws Exception {
        writeControl(account);String before=witness(account.engine());
        var barrier=new java.util.concurrent.atomic.AtomicInteger();var result=observe(account,barrier);
        check(result instanceof Complete,"WRITE_CAPABLE_ACCOUNT_NOT_COMPLETE_"+(result instanceof Refused r?r.code():"UNKNOWN"));
        for(var document:((Complete)result).observation().documents())check(document.xml().equals(sources.get(document.documentId())),"ACCOUNT_EXACT_SOURCE_MISMATCH");
        check(barrier.get()==1,"SOURCE_BARRIER_COUNT");check(before.equals(witness(account.engine())),"ACCOUNT_INSPECTION_CHANGED_STATE");check(sessions(account.engine(),account.user()).equals("0"),"ACCOUNT_SESSION_REMAINED");
        System.out.println(account.engine()+":"+account.label()+":INSPECTION_COMPLETE_UNCHANGED_CLEANUP_PASS");
    }
    private void redundantAndDenied() throws Exception {
        var direct=accounts.stream().filter(a->a.engine()==Engine.ORACLE && a.label().equals("direct")).findFirst().orElseThrow();
        String role="MOCK_READS_"+suffix.toUpperCase(Locale.ROOT);
        oracle("CREATE ROLE "+role+";\nGRANT SELECT,READ ON "+oracleOwner+".\"mock_tiles\" TO "+role+";\nGRANT READ ON "+oracleOwner+".\"mock_tiles\" TO "+direct.user()+";\nGRANT "+role+" TO "+direct.user()+";");
        check(Integer.parseInt(oracle("SELECT COUNT(*) FROM SYS.DBA_TAB_PRIVS WHERE owner='"+oracleOwner+"' AND table_name='mock_tiles' AND grantee IN ('"+role+"','"+direct.user()+"') AND privilege IN ('SELECT','READ');"))==4,"REDUNDANT_READ_PATH_CONTROL");
        accountGreen(new Account(direct.engine(),direct.user(),direct.password(),"redundant-read-paths"));
        var inactive=new Account(Engine.ORACLE,"MOCK_OI_"+suffix.toUpperCase(Locale.ROOT),password(),"inactive-only-read");
        oracle("CREATE USER "+inactive.user()+" IDENTIFIED BY \""+inactive.password()+"\";\nGRANT CREATE SESSION,"+oracleMetadataRole+","+role+" TO "+inactive.user()+";\nGRANT UPDATE ON "+oracleOwner+".\"mock_tiles\" TO "+inactive.user()+";\nALTER USER "+inactive.user()+" DEFAULT ROLE "+oracleMetadataRole+";");
        var barrier=new java.util.concurrent.atomic.AtomicInteger();var result=observe(inactive,barrier);
        check(result instanceof Refused r && r.code()==Code.READ_ACCESS_DENIED && r.cleanup()==Cleanup.COMPLETE,"INACTIVE_READ_ROLE_NOT_REFUSED");check(barrier.get()==0,"INACTIVE_ROLE_REACHED_SOURCE");check(sessions(Engine.ORACLE,inactive.user()).equals("0"),"INACTIVE_SESSION_REMAINED");
        System.out.println("ORACLE:INACTIVE_ONLY_READ_ROLE_REFUSED_BEFORE_SOURCE");
    }
    private Account ownerAccount(Engine engine) { return new Account(engine,owner(engine),secret(engine),"owner"); }
    private void refused(Account account,Code code,boolean beforeSource) throws Exception {
        String before=witness(account.engine());var barrier=new java.util.concurrent.atomic.AtomicInteger();var result=observe(account,barrier);
        check(result instanceof Refused r && r.code()==code && r.cleanup()==Cleanup.COMPLETE,"ADVERSE_REFUSAL_MISMATCH_"+(result instanceof Refused r?r.code():"COMPLETE"));
        if(beforeSource)check(barrier.get()==0,"ADVERSE_REACHED_SOURCE");
        check(before.equals(witness(account.engine())),"ADVERSE_CHANGED_SOURCE");check(sessions(account.engine(),account.user()).equals("0"),"ADVERSE_SESSION_REMAINED");
    }
    private void visibilityAndInventory() throws Exception {
        pg("ALTER TABLE "+pgOwner+".mock_tiles ENABLE ROW LEVEL SECURITY;");
        refused(ownerAccount(Engine.POSTGRESQL),Code.VISIBILITY_UNQUALIFIED,true);
        pg("CREATE POLICY mock_hidden ON "+pgOwner+".mock_tiles USING(false); ALTER TABLE "+pgOwner+".mock_tiles DISABLE ROW LEVEL SECURITY;");
        refused(ownerAccount(Engine.POSTGRESQL),Code.VISIBILITY_UNQUALIFIED,true);
        pg("DROP POLICY mock_hidden ON "+pgOwner+".mock_tiles;");
        System.out.println("POSTGRESQL:RLS_OWNER_BYPASS_AND_DISABLED_POLICY_REFUSED_BEFORE_SOURCE");
        var denied=new Account(Engine.ORACLE,"MOCK_ODENIED_"+suffix.toUpperCase(Locale.ROOT),password(),"metadata-denied");
        oracle("CREATE USER "+denied.user()+" IDENTIFIED BY \""+denied.password()+"\";\nGRANT CREATE SESSION TO "+denied.user()+";\nGRANT SELECT,UPDATE ON "+oracleOwner+".\"mock_tiles\" TO "+denied.user()+";");
        refused(denied,Code.METADATA_UNAVAILABLE,true);
        System.out.println("ORACLE:METADATA_DENIED_REFUSED_BEFORE_SOURCE");
        for(var engine:Engine.values()) {
            String original=witness(engine);var b=binding(engine);String table=quoted(b.schema())+"."+quoted(b.table());
            try(var connection=connect(engine,owner(engine),secret(engine));var insert=connection.prepareStatement("INSERT INTO "+table+" VALUES(99,?)")) {
                insert.setString(1,"<mock-extra/>");check(insert.executeUpdate()==1,"EXTRA_INSERT_COUNT");connection.commit();
            }
            refused(ownerAccount(engine),Code.INVENTORY_MISMATCH,false);
            try(var connection=connect(engine,owner(engine),secret(engine));var remove=connection.createStatement()) {
                check(remove.executeUpdate("DELETE FROM "+table+" WHERE "+quoted(b.keyColumn())+"=99")==1,"EXTRA_REMOVE_COUNT");
                check(remove.executeUpdate("DELETE FROM "+table+" WHERE "+quoted(b.keyColumn())+"=1")==1,"MISSING_REMOVE_COUNT");connection.commit();
            }
            refused(ownerAccount(engine),Code.INVENTORY_MISMATCH,false);
            try(var connection=connect(engine,owner(engine),secret(engine));var insert=connection.prepareStatement("INSERT INTO "+table+" VALUES(1,?)")) {
                insert.setString(1,sources.get("glyph-sheet"));check(insert.executeUpdate()==1,"MISSING_RESTORE_COUNT");connection.commit();
            }
            check(original.equals(witness(engine)),"INVENTORY_RESTORE_FAILED");
            System.out.println(engine+":EXTRA_AND_MISSING_COMMITTED_ROWS_REFUSED_UNCHANGED_CLEANUP");
        }
    }
    private void oracleShadow() throws Exception {
        oracle("CREATE TABLE "+oracleOwner+".mock_shadow_counter(n NUMBER);\nINSERT INTO "+oracleOwner+".mock_shadow_counter VALUES(0);\nCOMMIT;\nCREATE TABLE "+oracleOwner+".DUAL(dummy VARCHAR2(1));\nCREATE VIEW "+oracleOwner+".NLS_DATABASE_PARAMETERS AS SELECT 'NLS_CHARACTERSET' parameter,'MOCK_WRONG' value FROM SYS.DUAL;\nCREATE OR REPLACE PACKAGE "+oracleOwner+".DBMS_LOB AS FUNCTION GETLENGTH(value CLOB) RETURN NUMBER; END;\n/\nCREATE OR REPLACE PACKAGE BODY "+oracleOwner+".DBMS_LOB AS FUNCTION GETLENGTH(value CLOB) RETURN NUMBER IS PRAGMA AUTONOMOUS_TRANSACTION; result NUMBER; BEGIN UPDATE mock_shadow_counter SET n=n+1; COMMIT; result:=SYS.DBMS_LOB.GETLENGTH(value); RETURN result; END; END;\n/");
        // Independent adverse control: an owner-shadow autonomous function can commit even under read-only.
        try(var connection=connect(Engine.ORACLE,oracleOwner,oraclePassword)) {
            try(var start=connection.createStatement()){start.execute("SET TRANSACTION READ ONLY");}
            try(var query=connection.createStatement();var rows=query.executeQuery("SELECT "+oracleOwner+".DBMS_LOB.GETLENGTH(\"mock_xml\") FROM "+oracleOwner+".\"mock_tiles\"")) {int count=0;while(rows.next()){check(rows.getLong(1)>0,"SHADOW_CONTROL_LENGTH");count++;}check(count==2,"SHADOW_CONTROL_ROWS");}connection.rollback();
        }
        check(Integer.parseInt(oracle("SELECT n FROM "+oracleOwner+".mock_shadow_counter;"))>=2,"SHADOW_CONTROL_DID_NOT_COMMIT");
        oracle("UPDATE "+oracleOwner+".mock_shadow_counter SET n=0;\nCOMMIT;");
        String before=witness(Engine.ORACLE);var barrier=new java.util.concurrent.atomic.AtomicInteger();var result=observe(ownerAccount(Engine.ORACLE),barrier);
        check(result instanceof Complete,"SYS_QUALIFIED_SHADOW_OBSERVATION_FAILED");check(barrier.get()==1,"SHADOW_SOURCE_BARRIER");
        check(oracle("SELECT n FROM "+oracleOwner+".mock_shadow_counter;").equals("0"),"SHADOW_PACKAGE_INVOKED");check(before.equals(witness(Engine.ORACLE)),"SHADOW_INSPECTION_CHANGED_SOURCE");
        check(sessions(Engine.ORACLE,oracleOwner).equals("0"),"SHADOW_SESSION_REMAINED");
        System.out.println("ORACLE:AUTONOMOUS_SHADOW_CONTROL_COMMITS_BUT_SYS_QUALIFIED_INSPECTION_NEVER_INVOKES");
    }
    private void oracleAdminRefusal() throws Exception {
        String bootstrap=Files.readString(Path.of((String)state.get("ram")).resolve("oracle-bootstrap")).strip();canaries.add(bootstrap);
        int before=Integer.parseInt(sessions(Engine.ORACLE,"SYS"));var barrier=new java.util.concurrent.atomic.AtomicInteger();
        var credentials=new TransientCredentials("SYS".toCharArray(),bootstrap.toCharArray());
        JdbcObservation.ConnectionFactory factory=ignored->{
            var properties=new Properties();properties.setProperty("user","SYS");properties.setProperty("password",bootstrap);properties.setProperty("internal_logon","sysdba");
            properties.setProperty("oracle.net.CONNECT_TIMEOUT","5000");properties.setProperty("oracle.jdbc.ReadTimeout","2000");
            try {return new oracle.jdbc.OracleDriver().connect("jdbc:oracle:thin:@//localhost:"+destination(Engine.ORACLE).port()+"/FREEPDB1",properties);}finally{properties.clear();}
        };
        check(destination(Engine.ORACLE).transport()==ObservationDestination.Transport.DISPOSABLE_LOOPBACK,"ADMIN_TLS_PROBE_NOT_QUALIFIED");
        var result=new JdbcObservation(destination(Engine.ORACLE),factory,barrier::incrementAndGet,TimeUnit.SECONDS.toNanos(30),TimeUnit.SECONDS.toNanos(2)).observe(
            new ObservationPort.Selection(ready,binding(Engine.ORACLE).id()),credentials,new ObservationPort.Cancellation());
        check(result instanceof Refused r && r.code()==Code.IDENTITY_UNSUPPORTED && r.cleanup()==Cleanup.COMPLETE,"ACTUAL_SYS_IDENTITY_NOT_REFUSED");
        check(credentials.closed() && barrier.get()==0,"SYS_REACHED_SOURCES_OR_RETAINED_CREDENTIALS");check(Integer.parseInt(sessions(Engine.ORACLE,"SYS"))==before,"SYS_SESSION_REMAINED");
        System.out.println("ORACLE:ACTUAL_SYSDBA_IDENTITY_REFUSED_BEFORE_SOURCE_CLEANUP");
    }
    private void oraclePolicies() throws Exception {
        oracle("CREATE TABLE "+oracleOwner+".mock_policy_counter(n NUMBER);\nINSERT INTO "+oracleOwner+".mock_policy_counter VALUES(0);\nCOMMIT;\nCREATE OR REPLACE FUNCTION "+oracleOwner+".mock_predicate(object_schema VARCHAR2,object_name VARCHAR2) RETURN VARCHAR2 IS PRAGMA AUTONOMOUS_TRANSACTION; BEGIN UPDATE mock_policy_counter SET n=n+1; COMMIT; RETURN '1=0'; END;\n/\nBEGIN SYS.DBMS_RLS.ADD_POLICY(object_schema=>'"+oracleOwner+"',object_name=>'\"mock_tiles\"',policy_name=>'MOCK_VPD',function_schema=>'"+oracleOwner+"',policy_function=>'MOCK_PREDICATE',statement_types=>'SELECT'); END;\n/");
        refused(ownerAccount(Engine.ORACLE),Code.VISIBILITY_UNQUALIFIED,true);
        check(oracle("SELECT n FROM "+oracleOwner+".mock_policy_counter;").equals("0"),"VPD_PREDICATE_INVOKED_BY_INSPECTION");
        try(var connection=connect(Engine.ORACLE,oracleOwner,oraclePassword);var query=connection.createStatement();var rows=query.executeQuery("SELECT COUNT(*) FROM "+oracleOwner+".\"mock_tiles\"")) {check(rows.next() && rows.getInt(1)==0,"VPD_INDEPENDENT_CONTROL_NOT_HIDDEN");connection.rollback();}
        check(Integer.parseInt(oracle("SELECT n FROM "+oracleOwner+".mock_policy_counter;"))>0,"VPD_CONTROL_DID_NOT_INVOKE");
        oracle("BEGIN SYS.DBMS_RLS.DROP_POLICY(object_schema=>'"+oracleOwner+"',object_name=>'\"mock_tiles\"',policy_name=>'MOCK_VPD'); END;\n/\nUPDATE "+oracleOwner+".mock_policy_counter SET n=0;\nCOMMIT;\nCREATE OR REPLACE PROCEDURE "+oracleOwner+".mock_audit_handler(object_schema VARCHAR2,object_name VARCHAR2,policy_name VARCHAR2) IS PRAGMA AUTONOMOUS_TRANSACTION; BEGIN UPDATE mock_policy_counter SET n=n+1; COMMIT; END;\n/\nBEGIN SYS.DBMS_FGA.ADD_POLICY(object_schema=>'"+oracleOwner+"',object_name=>'\"mock_tiles\"',policy_name=>'MOCK_FGA',handler_schema=>'"+oracleOwner+"',handler_module=>'MOCK_AUDIT_HANDLER',statement_types=>'SELECT',enable=>TRUE); END;\n/");
        refused(ownerAccount(Engine.ORACLE),Code.VISIBILITY_UNQUALIFIED,true);
        check(oracle("SELECT n FROM "+oracleOwner+".mock_policy_counter;").equals("0"),"FGA_HANDLER_INVOKED_BY_INSPECTION");
        try(var connection=connect(Engine.ORACLE,oracleOwner,oraclePassword);var query=connection.createStatement();var rows=query.executeQuery("SELECT \"mock_key\" FROM "+oracleOwner+".\"mock_tiles\"")) {int count=0;while(rows.next())count++;check(count==2,"FGA_CONTROL_ROWS");connection.rollback();}
        check(Integer.parseInt(oracle("SELECT n FROM "+oracleOwner+".mock_policy_counter;"))>0,"FGA_CONTROL_DID_NOT_INVOKE");
        oracle("BEGIN SYS.DBMS_FGA.DROP_POLICY(object_schema=>'"+oracleOwner+"',object_name=>'\"mock_tiles\"',policy_name=>'MOCK_FGA'); END;\n/");
        System.out.println("ORACLE:VPD_AND_FGA_REFUSED_BEFORE_SOURCE_WITH_INDEPENDENT_EFFECT_CONTROLS");
    }
    private void snapshotConsistency() throws Exception {
        for(var engine:Engine.values()) {
            String original=witness(engine);String table=quoted(owner(engine))+".\"mock_tiles\"";var barrier=new java.util.concurrent.atomic.AtomicInteger();
            var credentials=new TransientCredentials(owner(engine).toCharArray(),secret(engine).toCharArray());
            var result=new JdbcObservation(destination(engine),()->{
                barrier.incrementAndGet();
                try(var writer=connect(engine,owner(engine),secret(engine));var insert=writer.prepareStatement("INSERT INTO "+table+" VALUES(99,?)")) {
                    update(writer,engine,sources.get("glyph-sheet")+"\n");insert.setString(1,"<mock-later-commit/>");check(insert.executeUpdate()==1,"SNAPSHOT_CONTROL_INSERT");writer.commit();
                }catch(Exception failure){throw new IllegalStateException("SNAPSHOT_WRITE_CONTROL_FAILED");}
            }).observe(new ObservationPort.Selection(ready,binding(engine).id()),credentials,new ObservationPort.Cancellation());
            check(result instanceof Complete,"SNAPSHOT_INSPECTION_NOT_COMPLETE_"+(result instanceof Refused r?r.code():"UNKNOWN"));
            check(barrier.get()==1 && credentials.closed(),"SNAPSHOT_BARRIER_OR_CREDENTIALS");
            var documents=((Complete)result).observation().documents();check(documents.size()==2,"SNAPSHOT_INVENTORY_CHANGED");
            for(var document:documents)check(document.xml().equals(sources.get(document.documentId())),"SNAPSHOT_MIXED_COMMITTED_VERSIONS");
            check(!original.equals(witness(engine)),"SNAPSHOT_WRITE_CONTROL_NOT_COMMITTED");
            refused(ownerAccount(engine),Code.INVENTORY_MISMATCH,false);
            try(var writer=connect(engine,owner(engine),secret(engine));var remove=writer.createStatement()) {
                check(remove.executeUpdate("DELETE FROM "+table+" WHERE \"mock_key\"=99")==1,"SNAPSHOT_RESTORE_DELETE");update(writer,engine,sources.get("glyph-sheet"));writer.commit();
            }
            check(original.equals(witness(engine)),"SNAPSHOT_RESTORE_STATE");check(sessions(engine,owner(engine)).equals("0"),"SNAPSHOT_SESSION_REMAINED");
            System.out.println(engine+":SNAPSHOT_RETAINS_ORIGINAL_XML_AND_INVENTORY_ACROSS_INDEPENDENT_COMMIT");
        }
    }
    private void cancellationAndLock() throws Exception {
        for(var engine:Engine.values()) {
            String before=witness(engine);var cancellation=new ObservationPort.Cancellation();var barrier=new java.util.concurrent.atomic.AtomicInteger();
            var credentials=new TransientCredentials(owner(engine).toCharArray(),secret(engine).toCharArray());
            var result=new JdbcObservation(destination(engine),()->{
                barrier.incrementAndGet();try{check(sessions(engine,owner(engine)).equals("1"),"CANCEL_CONNECTION_NOT_PRESENT");}catch(Exception failure){throw new IllegalStateException("CANCEL_WITNESS_FAILED");}cancellation.cancel();
            }).observe(new ObservationPort.Selection(ready,binding(engine).id()),credentials,cancellation);
            check(result instanceof Refused r && r.code()==Code.CANCELLED && r.cleanup()==Cleanup.COMPLETE,"CONNECTED_CANCEL_NOT_COMPLETE");
            check(barrier.get()==1 && credentials.closed(),"CANCEL_BARRIER_OR_CREDENTIALS");check(before.equals(witness(engine)),"CANCEL_CHANGED_SOURCE");check(sessions(engine,owner(engine)).equals("0"),"CANCEL_SESSION_REMAINED");
            System.out.println(engine+":CONNECTED_PRETRANSFER_CANCEL_REFUSED_UNCHANGED_CLEANUP");
        }
        String before=witness(Engine.POSTGRESQL);
        try(var blocker=connect(Engine.POSTGRESQL,pgOwner,pgPassword);var lock=blocker.createStatement()) {
            lock.execute("LOCK TABLE "+pgOwner+".mock_tiles IN ACCESS EXCLUSIVE MODE");
            var cancellation=new ObservationPort.Cancellation();var credentials=new TransientCredentials(pgOwner.toCharArray(),pgPassword.toCharArray());
            try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
                var pending=workers.submit(()->new JdbcObservation(destination(Engine.POSTGRESQL)).observe(new ObservationPort.Selection(ready,binding(Engine.POSTGRESQL).id()),credentials,cancellation));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<deadline && !pending.isDone()) {
                    waiting=pg("SELECT count(*) FROM pg_catalog.pg_stat_activity WHERE usename='"+pgOwner+"' AND wait_event_type='Lock';").equals("1");
                    if(waiting)break;Thread.sleep(20);
                }
                check(waiting,"ACCESS_SHARE_LOCK_WAIT_NOT_WITNESSED");cancellation.cancel();var result=pending.get(8,TimeUnit.SECONDS);
                check(result instanceof Refused,"LOCK_CANCEL_NOT_REFUSED");var refused=(Refused)result;
                check(refused.code()==Code.CANCELLED,"LOCK_CANCEL_WRONG_CODE");
                check(refused.cleanup()==Cleanup.COMPLETE,"LOCK_CANCEL_CLEANUP_INCONCLUSIVE");check(credentials.closed(),"LOCK_CANCEL_CREDENTIALS_RETAINED");
                check(sessions(Engine.POSTGRESQL,pgOwner).equals("1"),"LOCK_CANCEL_PHYSICAL_BACKEND_REMAINED");
            }finally{blocker.rollback();}
        }
        check(before.equals(witness(Engine.POSTGRESQL)),"LOCK_CANCEL_CHANGED_SOURCE");check(sessions(Engine.POSTGRESQL,pgOwner).equals("0"),"LOCK_CONTROL_SESSION_REMAINED");
        System.out.println("POSTGRESQL:ACTUAL_ACCESS_SHARE_LOCK_STALL_CANCELLED_WITH_CONFIRMED_CLEANUP");
    }
    private void oracleRedaction() throws Exception {
        oracle("BEGIN SYS.DBMS_REDACT.ADD_POLICY(object_schema=>'"+oracleOwner+"',object_name=>'\"mock_tiles\"',policy_name=>'MOCK_REDACTION',column_name=>'\"mock_key\"',function_type=>SYS.DBMS_REDACT.FULL,expression=>'1=1'); END;\n/");
        refused(ownerAccount(Engine.ORACLE),Code.VISIBILITY_UNQUALIFIED,true);
        try(var connection=connect(Engine.ORACLE,oracleOwner,oraclePassword);var query=connection.createStatement();var rows=query.executeQuery("SELECT \"mock_key\" FROM "+oracleOwner+".\"mock_tiles\"")) {
            int count=0;while(rows.next()){check(rows.getLong(1)==0,"REDACTION_CONTROL_VALUE");count++;}check(count==2,"REDACTION_CONTROL_ROWS");connection.rollback();
        }
        oracle("BEGIN SYS.DBMS_REDACT.DROP_POLICY(object_schema=>'"+oracleOwner+"',object_name=>'\"mock_tiles\"',policy_name=>'MOCK_REDACTION'); END;\n/");
        System.out.println("ORACLE:REDACTION_REFUSED_BEFORE_SOURCE_WITH_ACTUAL_ZERO_VALUE_CONTROL");
    }
    private void diagnosticCanaries() throws Exception {
        for(String engine:List.of("pg","oracle")) {
            check(capture(List.of("docker","inspect","--format","{{.HostConfig.LogConfig.Type}}",name(engine)),new byte[0],10).strip().equals("none"),"OWNED_CONTAINER_LOGGING_CHANGED");
        }
        String scan="grep -r -l -F -f /dev/stdin /opt/oracle/diag /opt/oracle/admin 2>/dev/null; result=$?; if [ \"$result\" -eq 1 ]; then exit 0; else exit 2; fi";
        check(capture(List.of("docker","exec","-i",name("oracle"),"/bin/sh","-c",scan),(String.join("\n",canaries)+"\n").getBytes(StandardCharsets.UTF_8),40).isBlank(),"ORACLE_DIAGNOSTIC_CANARY_FOUND");
        try(var files=Files.list(base)) {
            for(var file:files.filter(p->p.getFileName().toString().endsWith(".log")).toList()) {
                check(Files.size(file)<=1_048_576,"OWNED_LOG_TOO_LARGE");String log=Files.readString(file);
                for(String canary:canaries)check(!log.contains(canary),"OWNED_LOG_CANARY_FOUND");
            }
        }
        System.out.println("OWNED_DIAGNOSTIC_AND_LOG_CANARIES_ABSENT");
    }
    private void ownerGreen(Engine engine) throws Exception {
        writeControl(new Account(engine,owner(engine),secret(engine),"owner"));String before=witness(engine);baseline(engine);check(before.equals(witness(engine)),"BASELINE_CHANGED_SOURCE");
        var credentials=new TransientCredentials(owner(engine).toCharArray(),secret(engine).toCharArray());
        var result=new JdbcObservation(destination(engine)).observe(new ObservationPort.Selection(ready,binding(engine).id()),credentials,new ObservationPort.Cancellation());
        check(result instanceof Complete,"NEW_OWNER_OBSERVATION_NOT_COMPLETE_"+(result instanceof Refused r?r.code():"UNKNOWN"));check(credentials.closed(),"CREDENTIALS_NOT_CLOSED");
        var observation=((Complete)result).observation();check(observation.documents().size()==2,"DOCUMENT_COUNT_CHANGED");
        for(var document:observation.documents())check(document.xml().equals(sources.get(document.documentId())),"EXACT_SOURCE_MISMATCH");
        check(before.equals(witness(engine)),"INSPECTION_CHANGED_COMMITTED_SOURCE");
        String sessions=engine==Engine.POSTGRESQL?pg("SELECT count(*) FROM pg_catalog.pg_stat_activity WHERE usename='"+pgOwner+"';"):oracle("SELECT COUNT(*) FROM SYS.V_$SESSION WHERE username='"+oracleOwner+"';");
        check(sessions.equals("0"),"PHYSICAL_SESSION_REMAINED");
        System.out.println(engine+":GREEN_OWNER_COMPLETE_UNCHANGED_CLEANUP");
    }
    public static void main(String[] args) {
        try {
            if(args.length<2 || args.length>3)throw new IllegalStateException("QUALIFICATION_ARGUMENTS_REQUIRED");
            var qualification=new DisposableReadOperationQualification(Path.of(args[0]),Path.of(args[1]));qualification.provision();
            for(var engine:Engine.values())qualification.ownerGreen(engine);
            if(args.length==3) {
                if(args[2].equals("shadow"))qualification.oracleShadow();
                else if(args[2].equals("oracle-policies")){qualification.oracleAdminRefusal();qualification.oraclePolicies();}
                else if(args[2].equals("snapshot"))qualification.snapshotConsistency();
                else if(args[2].equals("cancel"))qualification.cancellationAndLock();
                else if(args[2].equals("redaction"))qualification.oracleRedaction();
                else if(!args[2].equals("owners"))throw new IllegalStateException("QUALIFICATION_MODE_REFUSED");
                qualification.diagnosticCanaries();
                System.out.println("POLICY_SELECTED_CHECKS_PASS:"+qualification.checks);return;
            }
            qualification.provisionAccounts();for(var account:qualification.accounts)qualification.accountGreen(account);qualification.redundantAndDenied();
            qualification.visibilityAndInventory();
            qualification.oracleShadow();
            qualification.oracleAdminRefusal();qualification.oraclePolicies();qualification.oracleRedaction();qualification.snapshotConsistency();qualification.cancellationAndLock();qualification.diagnosticCanaries();
            System.out.println("POLICY_OWNER_CHECKS_PASS:"+qualification.checks);
        } catch(Throwable failure) {
            String code=failure instanceof IllegalStateException && failure.getMessage()!=null && failure.getMessage().matches("[A-Z0-9_]+")?failure.getMessage():failure instanceof SQLException sql?"JDBC_"+sql.getErrorCode()+"_"+String.valueOf(sql.getSQLState()).replaceAll("[^A-Za-z0-9]",""):"QUALIFICATION_REFUSED";
            System.out.println("QUALIFICATION_FAILED:"+code);System.exit(2);
        }
    }
}
