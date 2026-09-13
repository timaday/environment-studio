package studio.environment.supervisor;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import studio.environment.server.export.PackageData;

/** Fixed candidate native grammar; no ordinary runtime qualification is implied. */
final class ClientProtocol implements SessionEngine.Wire {
    private final NativeProcess child;private final Transcript transcript;private final boolean pg;
    private final String nonce,archive,program,username,descriptor;private final BoundedSecret password;
    private final PackageCheck.Regenerated input;
    private long transactionDeadline,cleanupDeadline;private boolean usable;
    ClientProtocol(NativeProcess child,PackageCheck.Regenerated input,String nonce,String username,BoundedSecret password,String oracleDescriptor){
        Refusal.require(nonce.matches("[0-9a-f]{32}"),"INVALID_NONCE");this.child=child;this.transcript=new Transcript(child);this.input=input;pg=input.inputs().execution().engine()==PackageData.Engine.POSTGRESQL;
        account(pg,username);this.username=username;this.password=password;this.nonce=nonce;archive=input.archiveDigest();program=input.inputs().programDigest();descriptor=oracleDescriptor;
    }
    static void account(boolean pg,String username){Refusal.require(username.matches(pg?"[A-Za-z_][A-Za-z0-9_$]{0,62}":"[A-Z][A-Z0-9_$#]{0,127}"),"AUTHENTICATION_UNSUPPORTED");}
    private static long deadline(int seconds){return System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);}
    private void send(String sql,long deadline){child.write(sql.getBytes(StandardCharsets.US_ASCII),deadline);}
    private String frame(String phase){return "ES_"+phase+"|"+nonce+(Set.of("READY","COMMITTED").contains(phase)?"|"+archive+"|"+program:"");}
    @Override public void authenticate(){
        long end=deadline(10);
        if(pg)transcript.exact("Password: ",end);else{transcript.oracleBanner(end);send("CONNECT "+username+"@\""+descriptor+"\"\n",end);transcript.exact("Enter password: ",end);}
        byte[] line=password.line();try{child.write(line,end);}finally{Arrays.fill(line,(byte)0);password.close();}
        if(pg){send("\\echo ES_SETTINGS "+nonce+" :ON_ERROR_STOP :ON_ERROR_ROLLBACK\n",end);transcript.line("ES_SETTINGS "+nonce+" on off",end);}
        else {transcript.exact("\n",end);transcript.line("Connected.",end);transcript.exact("SQL> ",end);}
    }
    private static final String PG_PREDICATE="pg_catalog.current_setting('search_path')='pg_catalog' AND pg_catalog.current_setting('client_encoding')='UTF8' AND pg_catalog.current_setting('standard_conforming_strings')='on' AND pg_catalog.current_setting('row_security')='off' AND pg_catalog.current_setting('synchronous_commit')='on' AND pg_catalog.current_setting('server_encoding')='UTF8' AND pg_catalog.current_setting('transaction_isolation')='read committed' AND pg_catalog.current_setting('transaction_read_only')='off'";
    private static final String[] ORACLE_SETTINGS={"echo OFF","feedback OFF SQL_ID OFF","heading OFF","verify OFF","define OFF","autocommit OFF","exitcommit OFF","sqlprompt \"\"","sqlnumber OFF","pagesize 0","linesize 32767","serveroutput OFF","trimout ON","tab OFF","wrap : lines will be truncated"};
    @Override public void bootstrap(){
        if(!pg){long end=deadline(10);send("SET SQLPROMPT \"\"\nSET ECHO OFF\nSET FEEDBACK OFF\nSET HEADING OFF\nSET VERIFY OFF\nSET DEFINE OFF\nSET AUTOCOMMIT OFF\nSET EXITCOMMIT OFF\nSET SQLNUMBER OFF\nSET SERVEROUTPUT OFF\nSET PAGESIZE 0\nSET LINESIZE 32767\nSET TRIMOUT ON\nSET TAB OFF\nSET WRAP OFF\nWHENEVER SQLERROR EXIT FAILURE ROLLBACK\nWHENEVER OSERROR EXIT FAILURE ROLLBACK\nVARIABLE es_program_digest VARCHAR2(64)\nVARIABLE es_original_blob BLOB\nVARIABLE es_target_blob BLOB\n",end);for(String line:ORACLE_SETTINGS)send("SHOW "+line.substring(0,line.indexOf(' '))+"\n",end);for(String line:ORACLE_SETTINGS)transcript.line(line,end);}
        transactionDeadline=deadline(120);usable=true;
        if(pg)send("BEGIN ISOLATION LEVEL READ COMMITTED READ WRITE;\nSET LOCAL search_path=pg_catalog;\nSET LOCAL client_encoding='UTF8';\nSET LOCAL standard_conforming_strings=on;\nSET LOCAL row_security=off;\nSET LOCAL synchronous_commit=on;\nSELECT CASE WHEN "+PG_PREDICATE+" THEN '"+frame("BOOTSTRAP")+"' END;\n",transactionDeadline);
        else send("SET TRANSACTION READ WRITE;\nSELECT CASE WHEN (SELECT value FROM SYS.nls_database_parameters WHERE parameter='NLS_CHARACTERSET')='AL32UTF8' AND (SELECT value FROM SYS.nls_session_parameters WHERE parameter='NLS_COMP')='BINARY' AND (SELECT value FROM SYS.nls_session_parameters WHERE parameter='NLS_SORT')='BINARY' THEN '"+frame("BOOTSTRAP")+"' END FROM SYS.DUAL;\n",transactionDeadline);
        transcript.line(frame("BOOTSTRAP"),transactionDeadline);
    }
    @Override public void program(){byte[] bytes=input.program().bytes();try{if(pg)child.write(bytes,transactionDeadline);else for(var block:input.program().blocks()){byte[] slice=Arrays.copyOfRange(bytes,block.offset(),block.offset()+block.length());try{child.write(slice,transactionDeadline);send("/\n",transactionDeadline);}finally{Arrays.fill(slice,(byte)0);}}}finally{Arrays.fill(bytes,(byte)0);}}
    @Override public void readiness(){send(pg?"SELECT CASE WHEN pg_catalog.current_setting('environment_studio.program_digest',true)='"+program+"' AND "+PG_PREDICATE+" THEN '"+frame("READY")+"' END;\n":"SELECT CASE WHEN :es_program_digest='"+program+"' THEN '"+frame("READY")+"' END FROM SYS.DUAL;\n",transactionDeadline);transcript.line(frame("READY"),transactionDeadline);Refusal.require(child.alive(),"CLIENT_EXITED_BEFORE_COMMIT");}
    @Override public void commit(){cleanupDeadline=deadline(10);send(pg?"COMMIT;\n":"COMMIT WRITE IMMEDIATE WAIT;\n",cleanupDeadline);}
    @Override public void acknowledgement(){send("SELECT '"+frame("COMMITTED")+"'"+(pg?"":" FROM SYS.DUAL")+";\n",cleanupDeadline);transcript.line(frame("COMMITTED"),cleanupDeadline);}
    @Override public void cleanExit(){send(pg?"\\quit\n":"EXIT SUCCESS ROLLBACK\n",cleanupDeadline);transcript.eof(cleanupDeadline);Refusal.require(child.exit(cleanupDeadline)==0,"CLIENT_EXIT_FAILED");}
    @Override public SessionEngine.Cleanup rollbackAndCleanup(){
        if(!usable||transcript.failed()||!child.alive()){terminate();return SessionEngine.Cleanup.INCONCLUSIVE;}
        cleanupDeadline=deadline(10);if(!pg)send(ORACLE_CLEANUP,cleanupDeadline);send("ROLLBACK;\nSELECT '"+frame("ROLLED_BACK")+"'"+(pg?"":" FROM SYS.DUAL")+";\n",cleanupDeadline);transcript.line(frame("ROLLED_BACK"),cleanupDeadline);cleanExit();return SessionEngine.Cleanup.COMPLETE;
    }
    private static final String ORACLE_CLEANUP="DECLARE failed BOOLEAN := FALSE; BEGIN :es_program_digest := NULL; BEGIN IF :es_original_blob IS NOT NULL AND SYS.DBMS_LOB.ISTEMPORARY(:es_original_blob)=1 THEN SYS.DBMS_LOB.FREETEMPORARY(:es_original_blob); END IF; EXCEPTION WHEN OTHERS THEN failed := TRUE; END; BEGIN IF :es_target_blob IS NOT NULL AND SYS.DBMS_LOB.ISTEMPORARY(:es_target_blob)=1 THEN SYS.DBMS_LOB.FREETEMPORARY(:es_target_blob); END IF; EXCEPTION WHEN OTHERS THEN failed := TRUE; END; IF (:es_original_blob IS NOT NULL AND SYS.DBMS_LOB.ISTEMPORARY(:es_original_blob)=1) OR (:es_target_blob IS NOT NULL AND SYS.DBMS_LOB.ISTEMPORARY(:es_target_blob)=1) THEN failed := TRUE; END IF; IF failed THEN RAISE_APPLICATION_ERROR(-20001,'ES_CLEANUP_FAILED'); END IF; END;\n/\n";
    @Override public void terminate(){child.terminate();}
    @Override public void close(){password.close();child.close();}
    @Override public String toString(){return "ClientProtocol[redacted]";}
}
