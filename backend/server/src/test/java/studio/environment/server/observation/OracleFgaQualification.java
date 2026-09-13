package studio.environment.server.observation;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import studio.environment.core.definitionv2.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.*;
import studio.environment.core.observation.ObservationResult.*;
import studio.environment.server.definition.NativeDefinitionBytesCompiler;
import static studio.environment.server.observation.DisposableObservationQualification.*;

/** Explicit disposable mock qualification; never run automatically or used as authority. */
public final class OracleFgaQualification {
    static final String suffix=UUID.randomUUID().toString().replace("-", "").substring(0,8).toUpperCase(Locale.ROOT);
    static final String owner="ES_FGA_O_"+suffix, user="ES_FGA_R_"+suffix;
    static final String source="<invented value=\"unchanged\">λ😀</invented>";
    static final AtomicInteger sourceReads=new AtomicInteger();
    static ObservationDestination destination;
    static ObservationPort.Selection selection;
    static void policy(String statements,boolean handler) throws Exception {
        ora("BEGIN SYS.DBMS_FGA.ADD_POLICY(object_schema=>'"+owner+"',object_name=>'PACKETS',policy_name=>'MOCK_POLICY',statement_types=>'"+statements+"'"+(handler?",handler_schema=>'"+owner+"',handler_module=>'MARK_READ'":"")+"); END;\n/");
    }
    static void dropPolicy() throws Exception {ora("BEGIN SYS.DBMS_FGA.DROP_POLICY('"+owner+"','PACKETS','MOCK_POLICY'); END;\n/");}
    static ObservationResult observe() throws Exception {
        sourceReads.set(0);var credentials=new TransientCredentials(user.toCharArray(),password.toCharArray());
        var result=new JdbcObservation(destination,sourceReads::incrementAndGet).observe(selection,credentials,new ObservationPort.Cancellation());
        check(credentials.closed(),"CREDENTIALS_NOT_CLOSED");check(result.cleanup()==Cleanup.COMPLETE,"CLEANUP_NOT_COMPLETE");
        check(ora("SELECT COUNT(*) FROM v$session WHERE username='"+user+"';").equals("0"),"READER_SESSION_REMAINED");return result;
    }
    static void complete() throws Exception {var result=observe();check(result instanceof Complete,"CLEAN_EXPECTED_COMPLETE_"+result);check(sourceReads.get()==1,"SOURCE_BARRIER_NOT_REACHED");var docs=((Complete)result).observation().documents();check(docs.size()==2&&docs.stream().allMatch(d->d.xml().equals(source)),"EXACT_SOURCE_MISMATCH");}
    static void refuse(Code code) throws Exception {var result=observe();check(result instanceof Refused r&&r.code()==code,"EXPECTED_"+code+"_ACTUAL_"+result);check(sourceReads.get()==0,"SOURCE_READ_BEFORE_REFUSAL");}
    public static void main(String[] args) throws Exception {
        retiredPolicy();
        try{run();}catch(SQLException e){throw new IllegalStateException("MOCK_JDBC_CODE_"+e.getErrorCode());}
    }
    static void run() throws Exception {
        System.out.println("Independent mock owner="+owner+" reader="+user);
        ora("CREATE USER "+owner+" IDENTIFIED BY \""+password+"\" QUOTA 256M ON USERS;\nREVOKE INHERIT PRIVILEGES ON USER "+owner+" FROM PUBLIC;\nCREATE USER "+user+" IDENTIFIED BY \""+password+"\";\nREVOKE INHERIT PRIVILEGES ON USER "+user+" FROM PUBLIC;");
        try {
            String ddl="CREATE TABLE "+owner+".PACKETS(ID NUMBER(19,0) PRIMARY KEY,XML_DATA CLOB NOT NULL);\nINSERT INTO "+owner+".PACKETS VALUES(1,"+unicodeSql(source)+");\nINSERT INTO "+owner+".PACKETS VALUES(2,"+unicodeSql(source)+");\nCOMMIT;\nGRANT CREATE SESSION TO "+user+";\nGRANT SELECT ON "+owner+".PACKETS TO "+user+";\n";
            for(String view:List.of("V_$DATABASE","V_$CONTAINERS","V_$INSTANCE","V_$OPTION","DBA_USERS","DBA_OBJECTS","DBA_SYS_PRIVS","DBA_ROLE_PRIVS","DBA_SCHEMA_PRIVS","DBA_TAB_PRIVS","DBA_COL_PRIVS","DBA_TABLES","DBA_EXTERNAL_TABLES","DBA_SYNONYMS","DBA_TRIGGERS","DBA_POLICIES","REDACTION_POLICIES","DBA_TAB_COLS","DBA_CONSTRAINTS","DBA_CONS_COLUMNS","DBA_AUDIT_POLICIES"))ddl+="GRANT SELECT ON SYS."+view+" TO "+user+";\n";
            oracleDdl(ddl+"ALTER USER "+user+" READ ONLY;\nCREATE OR REPLACE PROCEDURE "+owner+".MARK_READ(a VARCHAR2,b VARCHAR2,c VARCHAR2) AUTHID DEFINER AS BEGIN SYS.DBMS_APPLICATION_INFO.SET_CLIENT_INFO('independent-fga-handler-called'); END;\n/");
            var parts=ora("SELECT TO_CHAR(d.dbid)||'|'||d.db_unique_name||'|'||TO_CHAR(c.con_id)||'|'||TO_CHAR(c.con_uid)||'|'||c.name||'|'||LOWER(RAWTOHEX(c.guid)) FROM v$database d CROSS JOIN v$containers c WHERE c.con_id=TO_NUMBER(SYS_CONTEXT('USERENV','CON_ID'));").split("\\|");
            destination=new ObservationDestination("mock-fga",Engine.ORACLE,"127.0.0.1",32776,"FREEPDB1",ObservationDestination.Transport.DISPOSABLE_LOOPBACK,"","mock-loopback-v1",Map.of("dbid",parts[0],"dbUniqueName",parts[1],"conId",parts[2],"conUid",parts[3],"conName",parts[4],"pdbGuid",parts[5]),"mock-policy-v1","oracle-account-read-only-v2:ef5326b6f4a158d1962f5397e10a7b5b38bbf20a7ba005fea394c02710db9a33");
            ready=(NativeCompilationResult.ReadyToPublish)new NativeDefinitionBytesCompiler().compile(Files.readAllBytes(Path.of("fixtures/native-v2/definition.json")),studio.environment.server.definition.DefinitionBytesCompiler.Format.JSON);
            var b=ready.checked().definition().bindings().stream().filter(x->x.engine()==Engine.ORACLE).findFirst().orElseThrow();
            var docs=new ArrayList<NativeDefinition.Document>();int n=0;for(var d:b.documents())docs.add(new NativeDefinition.Document(d.id(),Integer.toString(++n),d.entities()));
            ready=compiledBinding(new Binding(b.id(),b.engine(),b.storage(),owner,"PACKETS","ID","XML_DATA",KeyType.INT64,docs));selection=new ObservationPort.Selection(ready,b.id());
            complete();System.out.println("Clean exact Complete PASS");
            ora("REVOKE SELECT ON SYS.DBA_AUDIT_POLICIES FROM "+user+";");refuse(Code.METADATA_UNAVAILABLE);ora("GRANT SELECT ON SYS.DBA_AUDIT_POLICIES TO "+user+";");System.out.println("Denied catalog before source PASS");
            policy("SELECT",false);refuse(Code.VISIBILITY_UNQUALIFIED);dropPolicy();
            policy("UPDATE",false);refuse(Code.VISIBILITY_UNQUALIFIED);dropPolicy();System.out.println("Enabled no-handler and non-SELECT policies refused PASS");
            policy("SELECT",true);refuse(Code.VISIBILITY_UNQUALIFIED);
            var props=new Properties();props.setProperty("user",user);props.setProperty("password",password);props.setProperty("oracle.jdbc.ReadTimeout","5000");
            try(var connection=new oracle.jdbc.OracleDriver().connect("jdbc:oracle:thin:@//127.0.0.1:32776/FREEPDB1",props)){
                props.clear();connection.setAutoCommit(false);try(var statement=connection.createStatement()){
                    statement.execute("SET TRANSACTION READ ONLY");try(var rows=statement.executeQuery("SELECT XML_DATA FROM "+owner+".PACKETS ORDER BY ID")){while(rows.next())check(source.equals(rows.getString(1)),"CONTROL_SOURCE_MISMATCH");}
                    try(var rows=statement.executeQuery("SELECT SYS_CONTEXT('USERENV','CLIENT_INFO') FROM dual")){check(rows.next()&&"independent-fga-handler-called".equals(rows.getString(1)),"FGA_HANDLER_CONTROL_NOT_OBSERVED");}
                }finally{connection.rollback();}
            }finally{props.clear();}
            System.out.println("Direct read-only positive handler canary PASS");
            ora("BEGIN SYS.DBMS_FGA.DISABLE_POLICY('"+owner+"','PACKETS','MOCK_POLICY'); END;\n/");complete();dropPolicy();complete();
            System.out.println("Disabled policy then clean exact Complete PASS; assertions="+passed);
        } finally {ora("ALTER USER "+user+" ACCOUNT LOCK;\nALTER USER "+owner+" ACCOUNT LOCK;");check(ora("SELECT COUNT(*) FROM v$session WHERE username IN ('"+owner+"','"+user+"');").equals("0"),"OWNED_SESSIONS_REMAIN");System.out.println("Own accounts locked; sessions absent");}
    }
    private static void retiredPolicy() {
        throw new IllegalStateException("HISTORICAL_ACCOUNT_POLICY_QUALIFICATION_RETIRED");
    }
}
