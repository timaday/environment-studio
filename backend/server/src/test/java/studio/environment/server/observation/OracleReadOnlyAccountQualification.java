package studio.environment.server.observation;

import java.sql.*;
import java.util.*;
import static studio.environment.server.observation.DisposableObservationQualification.*;

/** Independent disposable investigation of account-level READ ONLY; never production authority. */
public final class OracleReadOnlyAccountQualification {
    static final List<String> DML = List.of(
        "INSERT INTO \"mock_oracle\".\"mock_tiles\" VALUES(99,'<denied/>')",
        "UPDATE \"mock_oracle\".\"mock_tiles\" SET \"mock_xml\"='<denied/>' WHERE \"mock_key\"=1",
        "DELETE FROM \"mock_oracle\".\"mock_tiles\" WHERE \"mock_key\"=1",
        "DELETE FROM XDB.XDB_INDEX_DDL_CACHE WHERE 1=0",
        "DELETE FROM XDB.RESOURCE_VIEW WHERE 1=0");
    static Connection connect(String user) throws SQLException {
        var properties=new Properties(); properties.setProperty("user",user); properties.setProperty("password",password);
        properties.setProperty("oracle.net.CONNECT_TIMEOUT","5000"); properties.setProperty("oracle.jdbc.ReadTimeout","2000");
        try { var connection=new oracle.jdbc.OracleDriver().connect("jdbc:oracle:thin:@//127.0.0.1:32776/FREEPDB1",properties); connection.setAutoCommit(false); return connection; }
        finally { properties.clear(); }
    }
    static int execute(Connection connection,String command) throws SQLException {
        try(var statement=connection.createStatement()){statement.execute(command);return 0;}
        catch(SQLException failure){return failure.getErrorCode();}
        finally{connection.rollback();}
    }
    public static void main(String[] arguments) throws Exception {
        try { qualify(); } catch(SQLException failure) { throw new IllegalStateException("DISPOSABLE_JDBC_CODE_"+failure.getErrorCode()); }
    }
    static void qualify() throws Exception {
        String user=reader.toUpperCase(Locale.ROOT);
        String witnessSql="SELECT TO_CHAR(\"mock_key\")||':'||TO_CHAR(DBMS_LOB.GETLENGTH(\"mock_xml\"))||':'||RAWTOHEX(STANDARD_HASH(DBMS_LOB.SUBSTR(\"mock_xml\",4000,1),'SHA256')) FROM \"mock_oracle\".\"mock_tiles\" ORDER BY \"mock_key\";";
        check(Integer.parseInt(ora("SELECT MAX(DBMS_LOB.GETLENGTH(\"mock_xml\")) FROM \"mock_oracle\".\"mock_tiles\";"))<=4000,"INDEPENDENT_CONTROL_WITNESS_BOUND_EXCEEDED");
        String managedWitness=ora(witnessSql);
        if(ora("SELECT COUNT(*) FROM dba_tables WHERE owner='mock_oracle' AND table_name='MOCK_ACCOUNT_CANARY';").equals("0"))ora("CREATE TABLE \"mock_oracle\".MOCK_ACCOUNT_CANARY(mock_id NUMBER PRIMARY KEY,mock_mark NUMBER);");
        ora("DELETE FROM \"mock_oracle\".MOCK_ACCOUNT_CANARY;\nINSERT INTO \"mock_oracle\".MOCK_ACCOUNT_CANARY VALUES(1,0);\nCOMMIT;\nCREATE OR REPLACE PROCEDURE \"mock_oracle\".MOCK_WRITE AUTHID DEFINER AS BEGIN UPDATE \"mock_oracle\".MOCK_ACCOUNT_CANARY SET mock_mark=1 WHERE mock_id=1; END;\n/\nCREATE OR REPLACE PROCEDURE \"mock_oracle\".MOCK_AUTO_WRITE AUTHID DEFINER AS PRAGMA AUTONOMOUS_TRANSACTION; BEGIN UPDATE \"mock_oracle\".MOCK_ACCOUNT_CANARY SET mock_mark=2 WHERE mock_id=1; COMMIT; END;\n/");
        ora("CREATE USER "+user+" IDENTIFIED BY \""+password+"\";\nGRANT CREATE SESSION TO "+user+";\nGRANT SELECT,INSERT,UPDATE,DELETE ON \"mock_oracle\".\"mock_tiles\" TO "+user+";\nGRANT SELECT ON SYS.DBA_USERS TO "+user+";\nGRANT SELECT ON \"mock_oracle\".MOCK_ACCOUNT_CANARY TO "+user+";\nGRANT EXECUTE ON \"mock_oracle\".MOCK_WRITE TO "+user+";\nGRANT EXECUTE ON \"mock_oracle\".MOCK_AUTO_WRITE TO "+user+";\nREVOKE INHERIT PRIVILEGES ON USER "+user+" FROM PUBLIC;");
        try(var connection=connect(user)) {
            for(String command:DML) {int code=execute(connection,command);check(code==0,"READ_WRITE_CONTROL_NOT_WORKING_"+code);}
            try(var statement=connection.createStatement()) {
                statement.execute("BEGIN \"mock_oracle\".MOCK_WRITE; END;");
                try(var rows=statement.executeQuery("SELECT mock_mark FROM \"mock_oracle\".MOCK_ACCOUNT_CANARY WHERE mock_id=1")){check(rows.next() && rows.getInt(1)==1,"DEFINER_CONTROL_NOT_WORKING");}
                connection.rollback();
                statement.execute("BEGIN \"mock_oracle\".MOCK_AUTO_WRITE; END;"); connection.rollback();
            }
        }
        check(ora("SELECT mock_mark FROM \"mock_oracle\".MOCK_ACCOUNT_CANARY WHERE mock_id=1;").equals("2"),"AUTONOMOUS_CONTROL_NOT_COMMITTED");
        ora("UPDATE \"mock_oracle\".MOCK_ACCOUNT_CANARY SET mock_mark=0;\nCOMMIT;\nALTER USER "+user+" READ ONLY;");
        check(ora("SELECT read_only||'|'||common FROM dba_users WHERE username='"+user+"';").equals("YES|NO"),"ADMIN_READ_ONLY_NOT_SET");
        try(var connection=connect(user)) {
            try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT read_only,common FROM sys.dba_users WHERE username=USER")){check(rows.next() && rows.getString(1).equals("YES") && rows.getString(2).equals("NO") && !rows.next(),"READER_ACCOUNT_EVIDENCE_MISSING");}
            try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM \"mock_oracle\".\"mock_tiles\"")){check(rows.next() && rows.getInt(1)==2,"READ_ONLY_SELECT_FAILED");}
            for(String command:DML){int code=execute(connection,command);check(code==28194,"ACCOUNT_READ_ONLY_WRITE_NOT_DENIED_"+code);}
            for(String command:List.of("BEGIN \"mock_oracle\".MOCK_WRITE; END;","BEGIN \"mock_oracle\".MOCK_AUTO_WRITE; END;")){int code=execute(connection,command);check(code==28194,"READ_ONLY_DEFINER_WRITE_NOT_DENIED_"+code);}
            int selfAlter=execute(connection,"ALTER USER "+user+" READ WRITE");check(selfAlter!=0,"SELF_ALTER_USER_READ_WRITE_ALLOWED");
            int sessionAlter=execute(connection,"ALTER SESSION SET READ_ONLY=FALSE");
            check(sessionAlter==0,"SESSION_MODE_DISABLE_CONTROL_NOT_WORKING");
            int after=execute(connection,DML.get(2));check(after==28194,"SESSION_ALTER_REMOVED_ACCOUNT_RESTRICTION");
            System.out.println("Oracle account READ ONLY: managed/persistent vendor/definer/autonomous writes denied ORA-28194; self-alter code="+selfAlter+", session-alter code="+sessionAlter+", subsequent DML code="+after);
        }
        check(ora("SELECT read_only||'|'||common FROM dba_users WHERE username='"+user+"';").equals("YES|NO"),"ACCOUNT_RESTRICTION_CHANGED");
        check(ora("SELECT mock_mark FROM \"mock_oracle\".MOCK_ACCOUNT_CANARY WHERE mock_id=1;").equals("0"),"READ_ONLY_CANARY_CHANGED");
        check(ora("SELECT COUNT(*) FROM \"mock_oracle\".\"mock_tiles\";").equals("2"),"MANAGED_ROWS_CHANGED");
        check(ora(witnessSql).equals(managedWitness),"MANAGED_SOURCE_WITNESS_CHANGED");
        backendGone(studio.environment.core.definitionv2.NativeDefinition.Engine.ORACLE);
        process(List.of("docker","logs","--tail","10000",ORACLE),"");
        System.out.println("Account-level read-only investigation assertions: "+passed+" PASS");
    }
}
